package ai.opencode.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.opencode.android.ui.chat.ChatViewModel
import ai.opencode.android.ui.screens.ChatScreen
import ai.opencode.android.ui.screens.ConnectionScreen
import ai.opencode.android.ui.screens.FileBrowserScreen
import ai.opencode.android.ui.screens.SessionListScreen
import ai.opencode.android.ui.screens.SettingsScreen
import ai.opencode.android.ui.screens.DiffViewerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenCodeMain(
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OpenCode")
                        if (uiState.isConnected) {
                            Text(
                                text = "v${uiState.serverVersion ?: "?"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { currentScreen = Screen.Sessions }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Sessions")
                    }
                },
                actions = {
                    if (uiState.isGenerating) {
                        IconButton(onClick = { viewModel.abortSession() }) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { currentScreen = Screen.Files }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Files")
                    }
                    if (uiState.currentSessionId != null) {
                        IconButton(onClick = { currentScreen = Screen.Diff }) {
                            Icon(Icons.Outlined.TrackChanges, contentDescription = "Changes")
                        }
                    }
                    IconButton(onClick = { currentScreen = Screen.Settings }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                uiState.isConnecting -> {
                    ConnectionScreen(
                        isConnecting = true,
                        onRetry = { viewModel.connectToServer() }
                    )
                }
                !uiState.isConnected && uiState.connectionError != null -> {
                    ConnectionScreen(
                        isConnecting = false,
                        error = uiState.connectionError,
                        onRetry = { viewModel.connectToServer() }
                    )
                }
                else -> {
                    when (currentScreen) {
                        is Screen.Chat -> {
                            ChatScreen(
                                uiState = uiState,
                                onSendMessage = { viewModel.sendMessage(it) },
                                onNewSession = { viewModel.createNewSession() },
                                onSelectSession = { id ->
                                    viewModel.selectSession(id)
                                    currentScreen = Screen.Chat
                                },
                                onUpdateInput = { viewModel.updateInput(it) },
                                onAbort = { viewModel.abortSession() },
                                onPermissionResponse = { allow -> viewModel.respondPermission(allow) }
                            )
                        }
                        is Screen.Sessions -> {
                            SessionListScreen(
                                sessions = uiState.sessions,
                                currentSessionId = uiState.currentSessionId,
                                onSelectSession = { id ->
                                    viewModel.selectSession(id)
                                    currentScreen = Screen.Chat
                                },
                                onDeleteSession = { viewModel.deleteSession(it) },
                                onNewSession = {
                                    viewModel.createNewSession()
                                    currentScreen = Screen.Chat
                                },
                                onBack = { currentScreen = Screen.Chat }
                            )
                        }
                        is Screen.Files -> {
                            FileBrowserScreen(
                                onBack = { currentScreen = Screen.Chat }
                            )
                        }
                        is Screen.Diff -> {
                            DiffViewerScreen(
                                diffs = uiState.currentDiffs,
                                onBack = { currentScreen = Screen.Chat }
                            )
                        }
                        is Screen.Settings -> {
                            SettingsScreen(
                                onBack = { currentScreen = Screen.Chat },
                                onReconnect = {
                                    viewModel.connectToServer()
                                    currentScreen = Screen.Chat
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Permission dialog
    uiState.pendingPermission?.let { permission ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permission Required") },
            text = {
                Text("OpenCode needs permission: ${permission.title}")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondPermission(true) }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondPermission(false) }) {
                    Text("Deny")
                }
            }
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(error)
        }
    }
}

sealed class Screen {
    data object Chat : Screen()
    data object Sessions : Screen()
    data object Files : Screen()
    data object Diff : Screen()
    data object Settings : Screen()
}
