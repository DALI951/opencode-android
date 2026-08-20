package ai.opencode.android.data.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ai.opencode.android.data.sse.SseClient
import java.util.concurrent.TimeUnit

class ConnectionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode_connection", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val sseClient = SseClient(okHttpClient)
    val api = OpenCodeApi(okHttpClient, sseClient, json)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    fun configure() {
        api.configure(serverUrl, username.ifBlank { null }, password.ifBlank { null })
    }

    suspend fun testConnection(): Result<String> {
        configure()
        return api.health().map { it.version }
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:4096"
        const val DEFAULT_USERNAME = "opencode"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
