package net.kustax.opendelivery

import android.app.Application

class GuestDeskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
