package ai.opencode.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.opencode.android.data.model.*

@Composable
fun ToolCallItem(part: ToolCallPartData) {
    val state = part.state
    val statusColor = when (state.status) {
        "running" -> MaterialTheme.colorScheme.tertiary
        "completed" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (state.status) {
        "running" -> Icons.Filled.PlayArrow
        "completed" -> Icons.Filled.CheckCircle
        "error" -> Icons.Filled.Error
        else -> Icons.Filled.HourglassEmpty
    }
    val title = when (state.status) {
        "running" -> state.title ?: part.tool
        "completed" -> (state.title ?: "").ifBlank { part.tool }
        "error" -> "Error: ${(state.error ?: "").take(80)}"
        else -> part.tool
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = statusColor.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = part.tool,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ReasoningItem(part: ReasoningPartData) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (expanded) "Hide reasoning" else "Show reasoning",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (expanded) {
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun StepFinishItem(part: StepFinishPartInfo) {
    if (part.cost > 0 || part.tokens.input > 0) {
        Text(
            text = "Step finished - ${part.tokens.input + part.tokens.output} tokens, $${String.format("%.4f", part.cost)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val segments = remember(text) { parseMarkdownSegments(text) }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = segment.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                is MarkdownSegment.Text -> {
                    Text(text = segment.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

sealed class MarkdownSegment {
    data class Text(val text: String) : MarkdownSegment()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownSegment()
}

fun parseMarkdownSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = text.split("\n")
    var i = 0
    val currentText = StringBuilder()

    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            if (currentText.isNotBlank()) {
                segments.add(MarkdownSegment.Text(currentText.toString().trim()))
                currentText.clear()
            }
            val language = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            segments.add(MarkdownSegment.CodeBlock(codeLines.joinToString("\n"), language))
        } else {
            currentText.appendLine(line)
        }
        i++
    }

    if (currentText.isNotBlank()) {
        segments.add(MarkdownSegment.Text(currentText.toString().trim()))
    }
    if (segments.isEmpty() && text.isNotBlank()) {
        segments.add(MarkdownSegment.Text(text))
    }
    return segments
}
