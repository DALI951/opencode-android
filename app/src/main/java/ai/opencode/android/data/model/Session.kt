package ai.opencode.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
