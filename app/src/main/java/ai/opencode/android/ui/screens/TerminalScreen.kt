package ai.opencode.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.opencode.android.util.TermuxCommandHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit
) {
    val terminalColor = Color(0xFF0a0a0a)
    val promptColor = Color(0xFFfab283)
    val outputColor = Color(0xFFeeeeee)
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val isTermuxInstalled = remember { TermuxCommandHelper.isTermuxInstalled(context) }

    val termuxLines = remember {
        mutableStateListOf(
            if (isTermuxInstalled) "Termux detected. Tap a command below to run it in Termux."
            else "Termux is NOT installed.",
            ""
        )
    }

    LaunchedEffect(termuxLines.size) {
        if (termuxLines.isNotEmpty()) {
            listState.animateScrollToItem(termuxLines.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Terminal") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Quick command buttons
        if (isTermuxInstalled) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Quick commands (opens Termux):",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickCommandChip("Start server") {
                            TermuxCommandHelper.startOpencodeServe(context)
                            termuxLines.add("> Starting opencode serve --port 4096 in background...")
                            termuxLines.add("  Termux will handle this. Check Termux for output.")
                            termuxLines.add("")
                        }
                        QuickCommandChip("Update server") {
                            TermuxCommandHelper.updateOpencode(context)
                            termuxLines.add("> Running: npm i -g opencode@latest --force")
                            termuxLines.add("  Opening Termux to show progress...")
                            termuxLines.add("")
                        }
                        QuickCommandChip("Open Termux") {
                            TermuxCommandHelper.openTermux(context)
                        }
                    }
                }
            }
        }

        // Output area
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(terminalColor)
                .padding(12.dp),
            state = listState
        ) {
            items(termuxLines) { line ->
                Text(
                    text = line.ifEmpty { " " },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = outputColor,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // Input bar — sends commands via Termux
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    placeholder = {
                        Text(
                            if (isTermuxInstalled) "Command to run in Termux..."
                            else "Install Termux to use terminal",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    },
                    singleLine = true,
                    enabled = isTermuxInstalled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotEmpty() && isTermuxInstalled) {
                            val cmd = input
                            input = ""
                            termuxLines.add("$ $cmd")
                            TermuxCommandHelper.sendCommand(
                                context = context,
                                command = "/data/data/com.termux/files/usr/bin/bash",
                                args = arrayOf("-l", "-c", cmd),
                                background = false,
                                openTerminal = true
                            )
                            termuxLines.add("  Sent to Termux (check Termux app for output)")
                            termuxLines.add("")
                        }
                        focusManager.clearFocus()
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (input.isNotEmpty() && isTermuxInstalled) {
                            val cmd = input
                            input = ""
                            termuxLines.add("$ $cmd")
                            TermuxCommandHelper.sendCommand(
                                context = context,
                                command = "/data/data/com.termux/files/usr/bin/bash",
                                args = arrayOf("-l", "-c", cmd),
                                background = false,
                                openTerminal = true
                            )
                            termuxLines.add("  Sent to Termux (check Termux app for output)")
                            termuxLines.add("")
                        }
                    },
                    enabled = isTermuxInstalled && input.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isTermuxInstalled && input.isNotEmpty())
                            promptColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCommandChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
