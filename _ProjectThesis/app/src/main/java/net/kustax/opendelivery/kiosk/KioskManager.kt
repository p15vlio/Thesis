package net.kustax.opendelivery.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context

class KioskManager(private val activity: Activity) {

    fun startKiosk() {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        // Full lock-task mode needs the app to be allow-listed by a device-owner policy. On an ordinary
        // tablet Android falls back to screen pinning, which the user confirms, or refuses the call: either
        // way the app keeps working, only without the kiosk lock.
        try {
            activity.startLockTask()
        } catch (_: SecurityException) {
            // Not allowed on this device: run without lock-task mode
        }
    }

    fun stopKiosk() {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
            // ignore
        }
    }
}
