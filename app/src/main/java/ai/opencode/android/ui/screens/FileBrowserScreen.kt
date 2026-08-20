package ai.opencode.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.opencode.android.data.model.FileContent
import ai.opencode.android.data.model.FileNode
import ai.opencode.android.di.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onBack: () -> Unit
) {
    val api = AppContainer.connectionManager.api
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var fileContent by remember { mutableStateOf<FileContent?>(null) }
    var viewingFile by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(currentPath) {
        isLoading = true
        api.listFiles(currentPath).fold(
            onSuccess = { files = it; isLoading = false },
            onFailure = { isLoading = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (viewingFile != null) {
                    Text(
                        text = viewingFile?.substringAfterLast('/') ?: "File",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("Files")
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (viewingFile != null) {
                        viewingFile = null
                        fileContent = null
                    } else if (currentPath != null) {
                        currentPath = currentPath?.substringBeforeLast('/')
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Search bar
        if (viewingFile == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.length >= 3) {
                        scope.launch {
                            api.searchFiles(query).fold(
                                onSuccess = { searchResults = it },
                                onFailure = { searchResults = emptyList() }
                            )
                        }
                    } else {
                        searchResults = emptyList()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            viewingFile != null && fileContent != null -> {
                // File viewer
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = fileContent!!.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            searchResults.isNotEmpty() && searchQuery.length >= 3 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(searchResults) { path ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = path.substringAfterLast('/'),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                viewingFile = path
                                scope.launch {
                                    api.readFile(path).fold(
                                        onSuccess = { fileContent = it },
                                        onFailure = { }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(files.sortedBy { it.type == "file" }) { node ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = node.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (node.type == "directory") Icons.Outlined.Folder
                                    else Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (node.type == "directory")
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                if (node.type == "directory") {
                                    currentPath = node.path
                                    searchQuery = ""
                                    searchResults = emptyList()
                                } else {
                                    viewingFile = node.path
                                    scope.launch {
                                        api.readFile(node.path).fold(
                                            onSuccess = { fileContent = it },
                                            onFailure = { }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
