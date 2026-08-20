package ai.opencode.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class UserMessage(
    val id: String,
    val sessionID: String,
    val role: String = "user",
    val time: MessageTime,
    val agent: String,
    val model: MessageModel,
    val summary: SummaryInfo? = null
)

@Serializable
data class AssistantMessage(
    val id: String,
    val sessionID: String,
    val role: String = "assistant",
    val time: MessageTime,
    val parentID: String,
    val modelID: String,
    val providerID: String,
    val mode: String,
    val path: MessagePath,
    val cost: Double = 0.0,
    val tokens: TokenUsage,
    val finish: String? = null,
    val error: JsonElement? = null,
    val summary: Boolean? = null
)

open abstract class Message {
    abstract val id: String
    abstract val sessionID: String
}

fun parseMessage(json: Json, element: JsonElement): Any {
    return when (element.jsonObject["role"]?.jsonPrimitive?.content) {
        "user" -> json.decodeFromJsonElement<UserMessage>(element)
        else -> json.decodeFromJsonElement<AssistantMessage>(element)
    }
}

@Serializable
data class MessageTime(
    val created: Long,
    val completed: Long? = null
)

@Serializable
data class MessageModel(
    val providerID: String,
    val modelID: String
)

@Serializable
data class MessagePath(
    val cwd: String,
    val root: String
)

@Serializable
data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: CacheUsage = CacheUsage()
)

@Serializable
data class CacheUsage(
    val read: Long = 0,
    val write: Long = 0
)
