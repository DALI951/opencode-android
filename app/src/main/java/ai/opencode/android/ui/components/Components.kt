package ai.opencode.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import ai.opencode.android.data.model.*
import ai.opencode.android.ui.screens.MonoFontFamily

@Composable
fun ToolCallItem(part: ToolCallPartData) {
    val state = part.state
    val statusSymbol = when (state.status) {
        "running" -> "\u25B6"
        "completed" -> "\u2713"
        "error" -> "\u2717"
        else -> "\u25CB"
    }
    val statusColor = when (state.status) {
        "running" -> MaterialTheme.colorScheme.tertiary
        "completed" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (state.status) {
        "running" -> state.title ?: part.tool
        "completed" -> (state.title ?: "").ifBlank { part.tool }
        "error" -> "Error: ${(state.error ?: "").take(80)}"
        else -> part.tool
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusSymbol,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                color = statusColor
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = part.tool,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        )
        if (title != part.tool) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ReasoningItem(part: ReasoningPartData) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "\u25BC" else "\u25B6",
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "reasoning",
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
        if (expanded && part.text.isNotBlank()) {
            Text(
                text = part.text,
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun StepFinishItem(part: StepFinishPartInfo) {
    if (part.cost > 0 || part.tokens.input > 0) {
        Text(
            text = "[step: ${part.tokens.input + part.tokens.output} tokens, $${String.format("%.4f", part.cost)}]",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.padding(vertical = 1.dp)
        )
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    val context = LocalContext.current

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    var copied by remember { mutableStateOf(false) }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (segment.language.isNotBlank()) {
                                    Text(
                                        text = segment.language,
                                        style = TextStyle(
                                            fontFamily = MonoFontFamily,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("code", segment.code)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        copied = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (copied) {
                                    LaunchedEffect(Unit) {
                                        kotlinx.coroutines.delay(2000)
                                        copied = false
                                    }
                                }
                            }
                            Text(
                                text = segment.code,
                                style = TextStyle(
                                    fontFamily = MonoFontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                is MarkdownSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
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
