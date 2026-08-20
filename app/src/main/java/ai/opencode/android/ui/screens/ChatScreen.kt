package ai.opencode.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.opencode.android.data.model.*
import ai.opencode.android.ui.chat.ChatUiState
import ai.opencode.android.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onUpdateInput: (String) -> Unit,
    onAbort: () -> Unit,
    onPermissionResponse: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size, uiState.parts.size, uiState.streamingText, uiState.isGenerating) {
        if (uiState.isGenerating) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        } else if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.currentSessionId == null) {
            WelcomeScreen(onNewSession = onNewSession)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { (it as? UserMessage)?.id ?: (it as? AssistantMessage)?.id ?: "" }
                ) { message ->
                    val messageParts = when (message) {
                        is UserMessage -> emptyList()
                        is AssistantMessage -> uiState.parts.filter { it.messageID == message.id }
                        else -> emptyList()
                    }
                    ChatMessageItem(
                        message = message,
                        parts = messageParts,
                        userInputText = if (message is UserMessage) uiState.userInputTexts[message.id] else null
                    )
                }
                if (uiState.isGenerating) {
                    item(key = "streaming") {
                        StreamingContent(
                            streamingText = uiState.streamingText,
                            toolCalls = uiState.currentToolCalls
                        )
                    }
                }
            }
        }

        if (uiState.currentSessionId != null) {
            ChatInput(
                input = uiState.currentInput,
                onInputChange = onUpdateInput,
                onSend = { text ->
                    onSendMessage(text)
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
                    }
                },
                isGenerating = uiState.isGenerating,
                onAbort = onAbort
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: Any, parts: List<Part>, userInputText: String? = null) {
    when (message) {
        is UserMessage -> UserMessageBubble(message = message, displayText = userInputText)
        is AssistantMessage -> AssistantMessageBubble(message = message, parts = parts)
    }
}

@Composable
fun UserMessageBubble(message: UserMessage, displayText: String? = null) {
    val text = displayText ?: message.summary?.title
    if (text.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun AssistantMessageBubble(message: AssistantMessage, parts: List<Part>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "OC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 340.dp)) {
            if (parts.isNotEmpty() || message.error != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    color = if (message.error != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        parts.forEach { part ->
                            when (part) {
                                is TextPartData -> if (part.text.isNotBlank()) {
                                    MarkdownText(text = part.text)
                                }
                                is ToolCallPartData -> ToolCallItem(part = part)
                                is ReasoningPartData -> ReasoningItem(part = part)
                                is StepFinishPartInfo -> StepFinishItem(part = part)
                                else -> { /* ignore */ }
                            }
                        }
                        if (message.error != null) {
                            Text(
                                "Error occurred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (message.tokens.input > 0 || message.tokens.output > 0) {
                Text(
                    text = "${message.tokens.input} in / ${message.tokens.output} out",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StreamingContent(streamingText: String, toolCalls: List<ToolCallPartData>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "OC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 340.dp)) {
            toolCalls.forEach { tool ->
                ToolCallItem(part = tool)
            }
            if (streamingText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MarkdownText(text = streamingText, modifier = Modifier.padding(12.dp))
                }
            } else if (toolCalls.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Thinking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "OC",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "OpenCode",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "AI Coding Agent",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Session")
        }
    }
}

@Composable
fun ChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isGenerating: Boolean,
    onAbort: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 150.dp),
                placeholder = {
                    Text(
                        "Ask OpenCode...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFEEEEEE),
                    unfocusedTextColor = Color(0xFFEEEEEE),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
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
