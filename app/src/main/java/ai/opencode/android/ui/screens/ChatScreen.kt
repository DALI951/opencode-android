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
import androidx.compose.ui.platform.LocalContext
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check

val MonoFontFamily = FontFamily.Monospace

@Composable
fun CopyButton(text: String, context: Context) {
    var copied by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("opencode", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            copied = true
        },
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = "Copy",
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

val AVAILABLE_THEMES = listOf(
    "default" to "Default Dark",
    "dark" to "Dark",
    "light" to "Light",
    "catppuccin" to "Catppuccin Mocha",
    "dracula" to "Dracula",
    "nord" to "Nord",
    "tokyo-night" to "Tokyo Night",
    "gruvbox" to "Gruvbox",
    "rose-pine" to "Rose Pine",
    "kanagawa" to "Kanagawa",
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
    onSetAgent: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onDismissPicker: () -> Unit,
    onBrowseSessions: () -> Unit = {},
    onConnect: () -> Unit = {}
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
                WelcomeScreen(
                    onNewSession = onNewSession,
                    onBrowseSessions = onBrowseSessions,
                    serverVersion = uiState.serverVersion,
                    onConnect = onConnect,
                    error = uiState.connectionError,
                    isConnecting = uiState.isConnecting
                )
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
                                streamingReasoning = uiState.streamingReasoning,
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
                            onSetAgent = onSetAgent,
                            agents = uiState.availableAgents
                        )
                    }
                }
            }
        }
    }

    if (uiState.showModelPicker) {
        AlertDialog(
            onDismissRequest = onDismissPicker,
            title = {
                Text(
                    "Select Model",
                    style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    uiState.availableModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model.providerID, model.modelID) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.modelID,
                                    style = TextStyle(
                                        fontFamily = MonoFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = model.providerID,
                                    style = TextStyle(
                                        fontFamily = MonoFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                    if (uiState.availableModels.isEmpty()) {
                        Text(
                            "No models found in config",
                            style = TextStyle(fontFamily = MonoFontFamily, fontSize = 13.sp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissPicker) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showThemePicker) {
        AlertDialog(
            onDismissRequest = onDismissPicker,
            title = {
                Text(
                    "Select Theme",
                    style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AVAILABLE_THEMES.forEach { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTheme(id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = TextStyle(
                                    fontFamily = MonoFontFamily,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissPicker) {
                    Text("Cancel")
                }
            }
        )
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
    val context = LocalContext.current
    val allText = parts.filterIsInstance<TextPartData>().joinToString("\n") { it.text.trim() }

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
                        Box(modifier = Modifier.fillMaxWidth()) {
                            MarkdownText(
                                text = part.text.trim(),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                            )
                            if (allText.length > 10) {
                                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                    CopyButton(text = allText, context = context)
                                }
                            }
                        }
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
fun StreamingBlock(streamingText: String, streamingReasoning: String, toolCalls: List<ToolCallPartData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(start = 4.dp)
    ) {
        if (streamingReasoning.isNotBlank()) {
            ReasoningItem(
                part = ReasoningPartData(
                    id = "streaming-reasoning",
                    sessionID = "",
                    messageID = "",
                    text = streamingReasoning
                )
            )
        }
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
fun WelcomeScreen(
    onNewSession: () -> Unit,
    onBrowseSessions: () -> Unit = {},
    serverVersion: String? = null,
    onConnect: () -> Unit = {},
    error: String? = null,
    isConnecting: Boolean = false
) {
    val isConnected = serverVersion != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "opencode",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "AI Coding Agent",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        if (isConnected) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connected \u2022 v$serverVersion",
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Get Started",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You need an OpenCode server running in Termux/Ubuntu on this device.",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Setup Steps",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SetupStep("1", "Install Termux", "From F-Droid (not Play Store)")
                    SetupStep("2", "Install Ubuntu/proot", "pkg install proot-distro && proot-distro install ubuntu")
                    SetupStep("3", "Install OpenCode", "npm i -g opencode@latest")
                    SetupStep("4", "Set password", "export OPENCODE_SERVER_PASSWORD=yourpassword")
                    SetupStep("5", "Start the server", "opencode serve --hostname 0.0.0.0 --port 4096")
                    SetupStep("6", "Tap Connect below", "App will connect to 127.0.0.1:4096")
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text(
                        "Connect to Server",
                        style = TextStyle(fontFamily = MonoFontFamily, fontSize = 14.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Default Connection",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Host: 127.0.0.1:4096",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                    Text(
                        text = "User: opencode",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                    Text(
                        text = "Change in Settings if needed",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        if (isConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Reference",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CommandHint("/model", "Switch AI model")
                    CommandHint("/theme", "Change theme")
                    CommandHint("/compact", "Compact session context")
                    CommandHint("/abort", "Stop current generation")
                    CommandHint("/cost", "Show session cost")
                    CommandHint("/diff", "Show file changes")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type / in the input to see all commands",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tips",
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TipItem("Use the agent picker (build, plan, deepsearch, etc.) to switch agents")
                    TipItem("File diffs appear when the agent modifies your code")
                    TipItem("Grant or deny tool permissions from the dialog that appears")
                    TipItem("Reasoning blocks expand to show thinking steps")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onBrowseSessions,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Browse Sessions",
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SetupStep(number: String, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = detail,
                style = TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Composable
fun CommandHint(cmd: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = cmd,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = description,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun TipItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u2022 ",
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
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
    onSetAgent: (String) -> Unit = {},
    agents: List<String> = listOf("build", "plan")
) {
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
