package ai.opencode.android

import android.app.Application
import ai.opencode.android.data.api.ConnectionManager
import ai.opencode.android.di.AppContainer

class OpenCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val connectionManager = ConnectionManager(this)
        connectionManager.configure()
        AppContainer.initialize(connectionManager)
    }
}
