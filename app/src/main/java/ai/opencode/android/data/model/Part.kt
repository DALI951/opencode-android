package ai.opencode.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TextPart(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "text",
    val text: String,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    val time: PartTime? = null
)

@Serializable
data class ReasoningPart(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "reasoning",
    val text: String,
    val time: PartTime
)

@Serializable
data class ToolPart(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "tool",
    val callID: String,
    val tool: String,
    val state: JsonElement,
    val metadata: JsonElement? = null
) {
    fun getState(json: Json): ToolStateInfo {
        return json.decodeFromJsonElement<ToolStateInfo>(state)
    }
}

@Serializable
data class ToolStateInfo(
    val status: String,
    val input: Map<String, JsonElement> = emptyMap(),
    val raw: String? = null,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    val time: ToolTimeInfo? = null,
    val metadata: JsonElement? = null
)

@Serializable
data class ToolTimeInfo(
    val start: Long,
    val end: Long? = null
)

@Serializable
data class FilePartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "file",
    val mime: String,
    val filename: String? = null,
    val url: String
)

@Serializable
data class StepStartPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "step-start",
    val snapshot: String? = null
)

@Serializable
data class StepFinishPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "step-finish",
    val reason: String,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage()
)

@Serializable
data class SnapshotPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "snapshot",
    val snapshot: String
)

@Serializable
data class PatchPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "patch",
    val hash: String,
    val files: List<String>
)

@Serializable
data class AgentPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "agent",
    val name: String
)

@Serializable
data class RetryPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "retry",
    val attempt: Int,
    val time: MessageTime
)

@Serializable
data class CompactionPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "compaction",
    val auto: Boolean
)

@Serializable
data class SubtaskPartData(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String = "subtask",
    val prompt: String,
    val description: String,
    val agent: String
)

@Serializable
data class PartTime(
    val start: Long,
    val end: Long? = null
)

sealed class Part {
    abstract val id: String
    abstract val sessionID: String
    abstract val messageID: String
}

data class TextPartData(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val text: String
) : Part()

data class ReasoningPartData(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val text: String,
    val time: PartTime? = null
) : Part()

data class ToolCallPartData(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val callID: String,
    val tool: String,
    val state: ToolStateInfo
) : Part()

data class StepStartPartInfo(
    override val id: String,
    override val sessionID: String,
    override val messageID: String
) : Part()

data class StepFinishPartInfo(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val reason: String,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage()
) : Part()

data class FilePartInfo(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val mime: String,
    val url: String
) : Part()

data class UnknownPartData(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val type: String
) : Part()

fun parsePart(json: Json, element: JsonElement): Part {
    val obj = element.jsonObject
    val id = obj["id"]?.jsonPrimitive?.content ?: ""
    val sessionId = obj["sessionID"]?.jsonPrimitive?.content ?: ""
    val messageId = obj["messageID"]?.jsonPrimitive?.content ?: ""
    val type = obj["type"]?.jsonPrimitive?.content ?: ""

    return when (type) {
        "text" -> {
            val text = obj["text"]?.jsonPrimitive?.content ?: ""
            TextPartData(id, sessionId, messageId, text)
        }
        "reasoning" -> {
            val text = obj["text"]?.jsonPrimitive?.content ?: ""
            val time = obj["time"]?.let { json.decodeFromJsonElement<PartTime>(it) }
            ReasoningPartData(id, sessionId, messageId, text, time)
        }
        "tool" -> {
            val callID = obj["callID"]?.jsonPrimitive?.content ?: ""
            val tool = obj["tool"]?.jsonPrimitive?.content ?: ""
            val state = obj["state"]?.let { json.decodeFromJsonElement<ToolStateInfo>(it) } ?: ToolStateInfo("pending")
            ToolCallPartData(id, sessionId, messageId, callID, tool, state)
        }
        "step-start" -> StepStartPartInfo(id, sessionId, messageId)
        "step-finish" -> {
            val reason = obj["reason"]?.jsonPrimitive?.content ?: ""
            val cost = obj["cost"]?.jsonPrimitive?.double ?: 0.0
            val tokens = obj["tokens"]?.let { json.decodeFromJsonElement<TokenUsage>(it) } ?: TokenUsage()
            StepFinishPartInfo(id, sessionId, messageId, reason, cost, tokens)
        }
        "file" -> {
            val mime = obj["mime"]?.jsonPrimitive?.content ?: ""
            val url = obj["url"]?.jsonPrimitive?.content ?: ""
            FilePartInfo(id, sessionId, messageId, mime, url)
        }
        else -> UnknownPartData(id, sessionId, messageId, type)
    }
}
