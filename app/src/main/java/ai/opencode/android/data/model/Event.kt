package ai.opencode.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String
)

@Serializable
data class Session(
    val id: String,
    val projectID: String,
    val directory: String,
    val parentID: String? = null,
    val title: String,
    val version: String,
    val time: TimeInfo,
    val summary: SummaryInfo? = null,
    val share: ShareInfo? = null,
    val revert: RevertInfo? = null
)

@Serializable
data class TimeInfo(
    val created: Long,
    val updated: Long,
    val compacting: Long? = null
)

@Serializable
data class SummaryInfo(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
    val diffs: List<FileDiff>? = null
)

@Serializable
data class ShareInfo(val url: String)

@Serializable
data class RevertInfo(
    val messageID: String,
    val partID: String? = null,
    val snapshot: String? = null,
    val diff: String? = null
)

@Serializable
data class FileDiff(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

@Serializable
data class Project(
    val id: String,
    val worktree: String,
    val vcs: String? = null,
    val time: TimeInfo
)

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val absolute: String,
    val type: String,
    val ignored: Boolean
)

@Serializable
data class FileContent(
    val type: String,
    val content: String
)

@Serializable
data class Todo(
    val content: String,
    val status: String,
    val priority: String,
    val id: String
)

@Serializable
data class PermissionInfo(
    val id: String,
    val type: String,
    val sessionID: String,
    val messageID: String,
    val title: String,
    val metadata: Map<String, JsonElement> = emptyMap()
)

sealed class SessionStatus {
    data object Idle : SessionStatus()
    data object Busy : SessionStatus()
    data class Retry(val attempt: Int = 0, val message: String = "") : SessionStatus()
}

fun parseSessionStatus(json: Json, element: JsonElement): SessionStatus {
    val obj = element.jsonObject
    return when (obj["type"]?.jsonPrimitive?.content) {
        "idle" -> SessionStatus.Idle
        "busy" -> SessionStatus.Busy
        "retry" -> SessionStatus.Retry(
            attempt = obj["attempt"]?.jsonPrimitive?.int ?: 0,
            message = obj["message"]?.jsonPrimitive?.content ?: ""
        )
        else -> SessionStatus.Idle
    }
}
