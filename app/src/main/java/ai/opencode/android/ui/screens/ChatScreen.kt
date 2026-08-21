package ai.opencode.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.opencode.android.data.model.*
import ai.opencode.android.ui.chat.ChatUiState
import ai.opencode.android.ui.components.*
import kotlinx.coroutines.launch

val MonoFontFamily = FontFamily.Monospace

data class SlashCommand(
    val name: String,
    val description: String
)

val SLASH_COMMANDS = listOf(
    SlashCommand("/init", "Initialize a new project"),
    SlashCommand("/compact", "Compact the session context"),
    SlashCommand("/undo", "Undo the last change"),
    SlashCommand("/redo", "Redo the last undone change"),
    SlashCommand("/clear", "Clear the current session"),
    SlashCommand("/diff", "Show current changes"),
    SlashCommand("/log", "Show session log"),
    SlashCommand("/abort", "Abort the current operation"),
    SlashCommand("/cost", "Show session cost"),
    SlashCommand("/model", "Switch the AI model"),
    SlashCommand("/theme", "Change the theme"),
)

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onUpdateInput: (String) -> Unit,
    onAbort: () -> Unit,
    onPermissionResponse: (Boolean) -> Unit,
    onSetAgent: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val showSlashMenu = uiState.currentInput.startsWith("/") && uiState.currentInput.length <= 20
    val filteredCommands = if (showSlashMenu) {
        SLASH_COMMANDS.filter { it.name.startsWith(uiState.currentInput, ignoreCase = true) }
    } else emptyList()

    LaunchedEffect(uiState.messages.size, uiState.parts.size, uiState.streamingText, uiState.isGenerating) {
        kotlinx.coroutines.delay(50)
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (uiState.currentSessionId == null) {
                WelcomeScreen(onNewSession = onNewSession)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val grouped = groupMessages(uiState.messages)
                    items(
                        items = grouped,
                        key = { it.key }
                    ) { group ->
                        when (group) {
                            is MessageGroup.User -> {
                                var displayText = uiState.userInputTexts[group.message.id]
                                if (displayText == null) displayText = group.message.summary?.title
                                if (displayText == null) {
                                    displayText = uiState.parts
                                        .filter { it.messageID == group.message.id && it is TextPartData }
                                        .joinToString("\n") { (it as TextPartData).text }
                                }
                                if (!displayText.isNullOrBlank()) {
                                    UserMessageRow(text = displayText)
                                }
                            }
                            is MessageGroup.Assistant -> {
                                val allParts = group.messages.flatMap { msg ->
                                    uiState.parts.filter { it.messageID == msg.id }
                                }
                                AssistantMessageBlock(
                                    parts = allParts,
                                    tokenUsage = group.messages.lastOrNull()?.let {
                                        if (it.tokens.input > 0) it.tokens else null
                                    }
                                )
                            }
                        }
                    }
                    if (uiState.isGenerating) {
                        item(key = "streaming") {
                            StreamingBlock(
                                streamingText = uiState.streamingText,
                                toolCalls = uiState.currentToolCalls
                            )
                        }
                    }
                }
            }

            if (uiState.currentSessionId != null) {
                Box {
                    Column {
                        if (showSlashMenu && filteredCommands.isNotEmpty()) {
                            SlashCommandMenu(
                                commands = filteredCommands,
                                onSelect = { cmd ->
                                    onUpdateInput(cmd.name + " ")
                                }
                            )
                        }
                        ChatInput(
                            input = uiState.currentInput,
                            onInputChange = onUpdateInput,
                            onSend = { text ->
                                onSendMessage(text)
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
                                }
                            },
                            isGenerating = uiState.isGenerating,
                            onAbort = onAbort,
                            currentAgent = uiState.currentAgent,
                            onSetAgent = onSetAgent
                        )
                    }
                }
            }
        }
    }
}

sealed class MessageGroup(val key: String) {
    class User(val message: UserMessage) : MessageGroup("user-${message.id}")
    class Assistant(val messages: List<AssistantMessage>) : MessageGroup(
        "assistant-${messages.firstOrNull()?.id ?: System.currentTimeMillis()}"
    )
}

fun groupMessages(messages: List<Any>): List<MessageGroup> {
    val groups = mutableListOf<MessageGroup>()
    var i = 0
    while (i < messages.size) {
        val msg = messages[i]
        if (msg is UserMessage) {
            groups.add(MessageGroup.User(msg))
            i++
        } else if (msg is AssistantMessage) {
            val assistantMsgs = mutableListOf<AssistantMessage>()
            while (i < messages.size && messages[i] is AssistantMessage) {
                assistantMsgs.add(messages[i] as AssistantMessage)
                i++
            }
            groups.add(MessageGroup.Assistant(assistantMsgs))
        } else {
            i++
        }
    }
    return groups
}

@Composable
fun UserMessageRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "> ",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AssistantMessageBlock(parts: List<Part>, tokenUsage: TokenUsage?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(start = 4.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is TextPartData -> {
                    if (part.text.isNotBlank()) {
                        Text(
                            text = part.text.trim(),
                            style = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                }
                is ReasoningPartData -> {
                    ReasoningItem(part = part)
                }
                is ToolCallPartData -> {
                    ToolCallItem(part = part)
                }
                is StepFinishPartInfo -> {
                    StepFinishItem(part = part)
                }
                else -> { /* ignore */ }
            }
        }
        if (tokenUsage != null && (tokenUsage.input > 0 || tokenUsage.output > 0)) {
            Text(
                text = "[${tokenUsage.input} in / ${tokenUsage.output} out]",
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun StreamingBlock(streamingText: String, toolCalls: List<ToolCallPartData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(start = 4.dp)
    ) {
        toolCalls.forEach { tool ->
            ToolCallItem(part = tool)
        }
        if (streamingText.isNotBlank()) {
            Text(
                text = streamingText.trim(),
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        } else if (toolCalls.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Thinking...",
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun SlashCommandMenu(commands: List<SlashCommand>, onSelect: (SlashCommand) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(max = 240.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            commands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cmd) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cmd.name,
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = cmd.description,
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onNewSession: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "opencode",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI Coding Agent",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "New Session",
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isGenerating: Boolean,
    onAbort: () -> Unit,
    currentAgent: String = "build",
    onSetAgent: (String) -> Unit = {}
) {
    val agents = listOf("build", "plan", "deepsearch", "inspector", "interrogator", "therapist")
    var agentMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { agentMenuExpanded = true }) {
                        Text(
                            text = currentAgent,
                            style = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "\u25BC",
                            style = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    DropdownMenu(
                        expanded = agentMenuExpanded,
                        onDismissRequest = { agentMenuExpanded = false }
                    ) {
                        agents.forEach { agent ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = agent,
                                        style = TextStyle(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = if (agent == currentAgent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (agent == currentAgent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                },
                                onClick = {
                                    onSetAgent(agent)
                                    agentMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "> ",
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    placeholder = {
                        Text(
                            "Type a message or / for commands...",
                            style = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    },
                    maxLines = 5,
                    textStyle = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) onSend(input)
                    }),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isGenerating) {
                    FilledIconButton(
                        onClick = onAbort,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = { if (input.isNotBlank()) onSend(input) },
                        enabled = input.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
