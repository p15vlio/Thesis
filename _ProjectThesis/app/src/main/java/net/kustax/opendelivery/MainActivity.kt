package net.kustax.opendelivery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.navigation.AppNavGraph
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.GuestDeskTheme

class MainActivity : ComponentActivity() {

    private lateinit var kioskManager: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        kioskManager = KioskManager(this)

        setContent {
            GuestDeskTheme {
                // The stored registration decides the first screen; until it is read only the background shows
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val isRegistered = AppContainer.devicePreferences.registrationState.first().isRegistered
                    startDestination = if (isRegistered) Screen.Idle.route else Screen.Registration.route
                }
                val start = startDestination
                if (start == null) {
                    Box(Modifier.fillMaxSize().background(Background))
                } else {
                    AppNavGraph(
                        navController = rememberNavController(),
                        startDestination = start
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (AppContainer.devicePreferences.registrationState.first().isRegistered) kioskManager.startKiosk()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // The QR scanner library still reports its result through this deprecated callback
    @Deprecated("Use Activity Result API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result?.contents != null) {
            AppContainer.pendingQrResult.value = result.contents
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
