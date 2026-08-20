package ai.opencode.android.di

import ai.opencode.android.data.api.ConnectionManager

object AppContainer {
    lateinit var connectionManager: ConnectionManager
        private set

    fun initialize(connectionManager: ConnectionManager) {
        this.connectionManager = connectionManager
    }
}
