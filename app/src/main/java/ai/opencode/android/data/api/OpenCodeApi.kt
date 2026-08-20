package ai.opencode.android.data.api

import android.util.Log
import ai.opencode.android.data.model.*
import ai.opencode.android.data.sse.SseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenCodeApi(
    private val client: OkHttpClient,
    private val sseClient: SseClient,
    private val json: Json
) {
    private var baseUrl: String = "http://127.0.0.1:4096"
    private var authHeader: String? = null

    fun configure(url: String, username: String? = null, password: String? = null) {
        baseUrl = url.trimEnd('/')
        authHeader = if (username != null && password != null) {
            val credentials = "$username:$password"
            "Basic " + android.util.Base64.encodeToString(
                credentials.toByteArray(),
                android.util.Base64.NO_WRAP
            )
        } else null
    }

    private fun buildRequest(method: String, path: String, body: String? = null): Request {
        val url = "$baseUrl$path"
        val builder = Request.Builder().url(url)
        authHeader?.let { builder.addHeader("Authorization", it) }

        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.post((body ?: "{}").toRequestBody(mediaType))
            }
            "DELETE" -> builder.delete()
            "PATCH" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.patch((body ?: "{}").toRequestBody(mediaType))
            }
        }
        return builder.build()
    }

    private suspend fun rawRequest(
        method: String,
        path: String,
        body: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(method, path, body)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    ApiException(response.code, responseBody)
                )
            }

            Result.success(responseBody)
        } catch (e: Exception) {
            Log.e("OpenCodeApi", "Request failed: $method $path", e)
            Result.failure(e)
        }
    }

    // Health
    suspend fun health(): Result<HealthResponse> {
        return rawRequest("GET", "/global/health").map { body ->
            json.decodeFromString<HealthResponse>(body)
        }
    }

    // Projects
    suspend fun listProjects(): Result<List<Project>> {
        return rawRequest("GET", "/project").map { body ->
            json.decodeFromString<List<Project>>(body)
        }
    }

    // Sessions
    suspend fun listSessions(): Result<List<Session>> {
        return rawRequest("GET", "/session").map { body ->
            json.decodeFromString<List<Session>>(body)
        }
    }

    suspend fun createSession(title: String? = null): Result<Session> {
        val body = buildJsonObject {
            title?.let { put("title", it) }
        }.toString()
        return rawRequest("POST", "/session", body).map { responseBody ->
            json.decodeFromString<Session>(responseBody)
        }
    }

    suspend fun deleteSession(id: String): Result<Boolean> {
        return rawRequest("DELETE", "/session/$id").map { true }
    }

    suspend fun getSession(id: String): Result<Session> {
        return rawRequest("GET", "/session/$id").map { body ->
            json.decodeFromString<Session>(body)
        }
    }

    suspend fun abortSession(id: String): Result<Boolean> {
        return rawRequest("POST", "/session/$id/abort").map { true }
    }

    suspend fun getSessionDiff(id: String): Result<List<FileDiff>> {
        return rawRequest("GET", "/session/$id/diff").map { body ->
            json.decodeFromString<List<FileDiff>>(body)
        }
    }

    // Messages
    suspend fun listMessages(sessionId: String): Result<List<Pair<Message, List<Part>>>> {
        return rawRequest("GET", "/session/$sessionId/message").map { body ->
            val array = json.parseToJsonElement(body).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                val infoElement = obj["info"] ?: throw IllegalArgumentException("Missing info")
                val partsElement = obj["parts"] ?: JsonArray(emptyList())

                val message = parseMessage(json, infoElement)
                val parts = partsElement.jsonArray.map { parsePart(json, it) }

                Pair(message, parts)
            }
        }
    }

    suspend fun sendMessageAsync(sessionId: String, text: String): Result<Unit> {
        val body = buildJsonObject {
            put("parts", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }.toString()
        return rawRequest("POST", "/session/$sessionId/prompt_async", body).map { }
    }

    // Files
    suspend fun readFile(path: String): Result<FileContent> {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        return rawRequest("GET", "/file/content?path=$encodedPath").map { body ->
            json.decodeFromString<FileContent>(body)
        }
    }

    suspend fun listFiles(path: String? = null): Result<List<FileNode>> {
        val query = if (path != null) "?path=${java.net.URLEncoder.encode(path, "UTF-8")}" else ""
        return rawRequest("GET", "/file$query").map { body ->
            json.decodeFromString<List<FileNode>>(body)
        }
    }

    suspend fun searchFiles(query: String): Result<List<String>> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return rawRequest("GET", "/find/file?query=$encoded").map { body ->
            json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.content }
        }
    }

    suspend fun getConfig(): Result<JsonObject> {
        return rawRequest("GET", "/config").map { body ->
            json.parseToJsonElement(body).jsonObject
        }
    }

    // SSE Events
    fun subscribeEvents(): Flow<EventEnvelope> {
        val url = "$baseUrl/event"
        val headers = mutableMapOf<String, String>()
        authHeader?.let { headers["Authorization"] = it }

        return sseClient.connect(url, headers).map { sseEvent ->
            try {
                parseEvent(json, json.parseToJsonElement(sseEvent.data))
            } catch (e: Exception) {
                Log.w("OpenCodeApi", "Failed to parse event: ${sseEvent.data}", e)
                EventEnvelope("unknown", JsonNull)
            }
        }
    }

    fun subscribeGlobalEvents(): Flow<EventEnvelope> {
        val url = "$baseUrl/global/event"
        val headers = mutableMapOf<String, String>()
        authHeader?.let { headers["Authorization"] = it }

        return sseClient.connect(url, headers).map { sseEvent ->
            try {
                val element = json.parseToJsonElement(sseEvent.data)
                // GlobalEvent wraps payload in a "payload" field
                val payload = element.jsonObject["payload"] ?: element
                parseEvent(json, payload)
            } catch (e: Exception) {
                Log.w("OpenCodeApi", "Failed to parse global event", e)
                EventEnvelope("unknown", JsonNull)
            }
        }
    }

    private fun parseEvent(json: Json, element: JsonElement): EventEnvelope {
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: "unknown"
        val properties = obj["properties"] ?: JsonNull
        return EventEnvelope(type, properties)
    }

    // Permission response
    suspend fun respondPermission(sessionId: String, permissionId: String, response: String): Result<Boolean> {
        val body = buildJsonObject {
            put("response", response)
        }.toString()
        return rawRequest("POST", "/session/$sessionId/permissions/$permissionId", body).map { true }
    }

    class ApiException(val code: Int, val responseBody: String) :
        Exception("API error $code: $responseBody")
}

data class EventEnvelope(
    val type: String,
    val properties: JsonElement
)
