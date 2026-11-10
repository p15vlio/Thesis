package net.kustax.opendelivery.payment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import com.ftpos.apiservice.aidl.IServiceManager
import com.ftpos.apiservice.aidl.device.IDevice
import com.ftpos.apiservice.aidl.emv.IEMV
import com.ftpos.apiservice.aidl.emv.IEMVData
import com.ftpos.apiservice.aidl.emv.ISearchCardListener
import com.ftpos.apiservice.aidl.errcode.ErrCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

// The device's built-in card reader service, reached over AIDL (classes from libs/reader-sdk.jar). On a tablet
// without that service the bind fails and awaitCard reports CardEvent.Unavailable, so the payment screen shows
// the reader as unavailable; the AIDL classes are bundled in the APK, so nothing fails at class-load time.
// One searchCard call watches chip, contactless and magnetic stripe at once; it is single shot, so every
// awaitCard call arms it again.
//
// Every AIDL call (binding, searching, cancelling) runs on one dedicated reader thread, never on the main
// thread, and in the order it was issued so a cancel can not overtake the search that follows it.
class DeviceCardTerminal(context: Context) : CardTerminal {

    private val appContext = context.applicationContext
    private val readerDispatcher = Executors.newSingleThreadExecutor { Thread(it, "card-reader") }.asCoroutineDispatcher()
    private val readerScope = CoroutineScope(SupervisorJob() + readerDispatcher)

    // Reset from binder threads when the service goes away
    @Volatile private var emv: IEMV? = null
    @Volatile private var device: IDevice? = null
    @Volatile private var connection: ServiceConnection? = null

    private sealed interface Search {
        data class Done(val event: CardEvent) : Search
        // Timeout, busy service or lost connection: arm the search again
        data object Retry : Search
    }

    override suspend fun awaitCard(): CardEvent = withContext(readerDispatcher) {
        var result: CardEvent? = null
        while (result == null) {
            result = if (!connect()) {
                CardEvent.Unavailable
            } else when (val search = searchOnce()) {
                is Search.Done -> search.event
                Search.Retry -> {
                    delay(RETRY_DELAY_MS)
                    null
                }
            }
        }
        result
    }

    override fun release() {
        readerScope.launch {
            runCatching { device?.cancel() }
            disconnect()
        }
    }

    // Runs on the reader thread
    private suspend fun connect(): Boolean {
        if (emv != null) return true
        disconnect()
        val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IBinder?> { cont ->
                val conn = object : ServiceConnection {
                    // Arrives on the main thread: only hand the binder over, the AIDL calls follow on the reader thread
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (cont.isActive) cont.resume(binder)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        emv = null
                        device = null
                    }
                }
                val intent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)
                val bound = try {
                    appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                } catch (_: SecurityException) {
                    false
                }
                connection = conn
                if (!bound) cont.resume(null)
            }
        }
        if (binder != null) {
            try {
                val manager = IServiceManager.Stub.asInterface(binder)
                emv = IEMV.Stub.asInterface(manager.emv)
                device = IDevice.Stub.asInterface(manager.device)
            } catch (_: RemoteException) {
                emv = null
                device = null
            }
        }
        if (emv == null) disconnect()
        return emv != null
    }

    private fun disconnect() {
        connection?.let { runCatching { appContext.unbindService(it) } }
        connection = null
        emv = null
        device = null
    }

    private suspend fun searchOnce(): Search {
        val service = emv ?: return Search.Retry
        val bundle = Bundle().apply {
            putBoolean(IEMVData.SUPPORT_IC_CARD, true)
            putBoolean(IEMVData.SUPPORT_RF_CARD, true)
            putBoolean(IEMVData.SUPPORT_MAG_CARD, true)
            putInt(IEMVData.TIME_OUT, SEARCH_TIMEOUT_S)
        }
        // A busy service never calls back, so the wait has its own deadline slightly after the reader's
        val result = withTimeoutOrNull((SEARCH_TIMEOUT_S + WATCHDOG_GRACE_S) * 1000L) {
            suspendCancellableCoroutine { cont ->
                fun finish(search: Search) {
                    if (cont.isActive) cont.resume(search)
                }
                // Callbacks arrive on a Binder thread; the listener must stay referenced while the wait is active
                val listener = object : ISearchCardListener.Stub() {
                    override fun onCardInsert() = finish(Search.Done(CardEvent.Inserted))
                    override fun onCardTapped() = finish(Search.Done(CardEvent.Tapped))
                    override fun onCardSwiped(tracks: Bundle) =
                        finish(Search.Done(CardEvent.Swiped(last4FromTrack2(tracks.getByteArray(IEMVData.TRACK2)))))

                    override fun onError(errCode: Int) = finish(
                        when (errCode) {
                            ErrCode.ERR_OP_TIMEOUT, ErrCode.ERR_SERVER_BUSY, ErrCode.ERR_EMV_BUSY,
                            ErrCode.ERR_SERVICE_NOT_ACCEPTED, ErrCode.ERR_CANCELED_BY_USER -> Search.Retry
                            ErrCode.ERR_SYSTEM_MODULE_DISABLED -> Search.Done(CardEvent.Unavailable)
                            else -> Search.Done(CardEvent.ReadFailed)
                        }
                    )
                }
                // May run on any thread: the cancel is queued on the reader thread behind earlier calls
                cont.invokeOnCancellation { readerScope.launch { runCatching { device?.cancel() } } }
                try {
                    service.searchCard(bundle, listener)
                } catch (_: RemoteException) {
                    disconnect()
                    finish(Search.Retry)
                }
            }
        }
        return result ?: Search.Retry
    }

    // Track 2 is "PAN=YYMM..." (sometimes with 'D' as separator). The raw track is never stored or logged.
    private fun last4FromTrack2(track: ByteArray?): String? {
        val pan = track?.toString(Charsets.UTF_8)?.trim()
            ?.trimStart(';')?.substringBefore('=')?.substringBefore('D')
            ?: return null
        return if (pan.length in 12..19 && pan.all(Char::isDigit)) pan.takeLast(4) else null
    }

    private companion object {
        const val SERVICE_ACTION = "com.pos.aidl.remote.service"
        const val SERVICE_PACKAGE = "com.ftpos.apiservice"
        const val BIND_TIMEOUT_MS = 3_000L
        const val SEARCH_TIMEOUT_S = 30
        const val WATCHDOG_GRACE_S = 3
        const val RETRY_DELAY_MS = 1_000L
    }
}
