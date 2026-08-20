package ai.opencode.android.data.sse

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class SseClient(private val client: OkHttpClient) {

    fun connect(url: String, headers: Map<String, String> = emptyMap()): Flow<SseEvent> = callbackFlow {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data.isNotBlank()) {
                    trySend(SseEvent(eventType = type ?: "message", data = data))
                }
            }

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d("SseClient", "SSE connected to $url")
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                Log.e("SseClient", "SSE failure: ${t?.message}, code=${response?.code}")
                close(t ?: IOException("SSE connection failed: ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    data class SseEvent(
        val eventType: String,
        val data: String
    )
}
