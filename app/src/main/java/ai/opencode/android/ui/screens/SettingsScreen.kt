package ai.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ai.opencode.android.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReconnect: () -> Unit
) {
    val connectionManager = AppContainer.connectionManager

    var serverUrl by remember { mutableStateOf(connectionManager.serverUrl) }
    var username by remember { mutableStateOf(connectionManager.username) }
    var password by remember { mutableStateOf(connectionManager.password) }
    var showPassword by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("http://127.0.0.1:4096") }
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("opencode") }
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                      else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                }
            )

            HorizontalDivider()

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        connectionManager.configure()
                    },
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test")
                    }
                }

                Button(
                    onClick = {
                        isSaving = true
                        connectionManager.serverUrl = serverUrl
                        connectionManager.username = username
                        connectionManager.password = password
                        connectionManager.configure()
                        isSaving = false
                        onReconnect()
                    },
                    enabled = !isSaving
                ) {
                    Text("Save & Reconnect")
                }
            }

            testResult?.let { result ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (result.startsWith("OK"))
                        MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            HorizontalDivider()

            // Info
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "OpenCode Android connects to your OpenCode server running in Termux/Ubuntu via HTTP API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Make sure 'opencode serve' is running in your Termux/Ubuntu environment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
