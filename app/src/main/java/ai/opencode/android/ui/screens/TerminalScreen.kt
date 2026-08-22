package ai.opencode.android.ui.screens

import android.util.Log
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit
) {
    val terminalColor = Color(0xFF0a0a0a)
    val promptColor = Color(0xFFfab283)
    val outputColor = Color(0xFFeeeeee)

    var lines by remember { mutableStateOf(listOf("OpenCode Terminal", "")) }
    var input by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var process by remember { mutableStateOf<Process?>(null) }
    var stdin by remember { mutableStateOf<OutputStream?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    DisposableEffect(Unit) {
        val job = scope.launch(Dispatchers.IO) {
            var p: Process? = null
            var reader: BufferedReader? = null

            try {
                // Try bash first, then sh
                val shells = listOf(
                    arrayOf("bash"),
                    arrayOf("sh"),
                    arrayOf("/system/bin/sh")
                )

                for (shell in shells) {
                    try {
                        val pb = ProcessBuilder(*shell)
                        pb.redirectErrorStream(true)
                        pb.environment()["TERM"] = "xterm-256color"
                        p = pb.start()
                        reader = BufferedReader(InputStreamReader(p.inputStream))
                        break
                    } catch (e: Exception) {
                        Log.w("Terminal", "Shell ${shell[0]} not available: ${e.message}")
                        p?.destroyForcibly()
                        p = null
                    }
                }

                if (p == null || reader == null) {
                    withContext(Dispatchers.Main) {
                        lines = lines + "No shell found on this device."
                        lines = lines + "Install Termux for a full Linux terminal."
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    process = p
                    stdin = p.outputStream
                    isRunning = true
                    lines = lines + "Shell ready. Type commands below."
                    lines = lines + ""
                }

                var line: String? = reader.readLine()
                while (line != null) {
                    val captured = line
                    withContext(Dispatchers.Main) {
                        lines = lines + captured
                    }
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                Log.e("Terminal", "Shell error", e)
                withContext(Dispatchers.Main) {
                    lines = lines + "Shell error: ${e.message}"
                }
            } finally {
                p?.destroyForcibly()
            }
        }

        onDispose {
            process?.destroyForcibly()
            job.cancel()
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(terminalColor)
                .padding(12.dp),
            state = listState
        ) {
            items(lines) { line ->
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
                            "Enter command...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotEmpty() && isRunning) {
                            val cmd = input
                            input = ""
                            lines = lines + "$ $cmd"
                            scope.launch(Dispatchers.IO) {
                                try {
                                    stdin?.let { os ->
                                        os.write((cmd + "\n").toByteArray())
                                        os.flush()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        lines = lines + "Error: ${e.message}"
                                    }
                                }
                            }
                        }
                        focusManager.clearFocus()
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (input.isNotEmpty() && isRunning) {
                            val cmd = input
                            input = ""
                            lines = lines + "$ $cmd"
                            scope.launch(Dispatchers.IO) {
                                try {
                                    stdin?.let { os ->
                                        os.write((cmd + "\n").toByteArray())
                                        os.flush()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        lines = lines + "Error: ${e.message}"
                                    }
                                }
                            }
                        }
                    },
                    enabled = isRunning && input.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isRunning && input.isNotEmpty())
                            promptColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
