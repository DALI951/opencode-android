package ai.opencode.android.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.opencode.android.data.api.ConnectionManager
import ai.opencode.android.data.api.EventEnvelope
import ai.opencode.android.data.api.ModelInfo
import ai.opencode.android.data.api.UpdateChecker
import ai.opencode.android.data.api.UpdateInfo
import ai.opencode.android.data.model.*
import ai.opencode.android.di.AppContainer
import ai.opencode.android.util.TermuxCommandHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

class ChatViewModel(
    application: Application,
    private val connectionManager: ConnectionManager = AppContainer.connectionManager
) : AndroidViewModel(application) {

    private val api get() = connectionManager.api
    private val json get() = connectionManager.json
    private val updateChecker by lazy { UpdateChecker(getApplication()) }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var eventSubscription: Job? = null
    private var currentSessionId: String? = null
    private val streamingText = ConcurrentHashMap<String, StringBuilder>()

    init {
        checkForUpdate()
        if (connectionManager.password.isNotBlank()) {
            connectToServer()
        }
    }

    fun connectToServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            try {
                connectionManager.configure()
                val health = api.health()
                health.fold(
                    onSuccess = { h ->
                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                                serverVersion = h.version
                            )
                        }
                        subscribeToEvents()
                        loadSessions()
                        loadAgents()
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                connectionError = e.message ?: "Connection failed"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        connectionError = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun subscribeToEvents() {
        eventSubscription?.cancel()
        eventSubscription = viewModelScope.launch {
            try {
                api.subscribeEvents().collect { event ->
                    handleEvent(event)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Event subscription error, reconnecting in 3s", e)
                _uiState.update { it.copy(isConnected = false) }
                kotlinx.coroutines.delay(3000)
                subscribeToEvents()
            }
        }
    }

    private fun handleEvent(event: EventEnvelope) {
        try {
            when (event.type) {
                "message.updated" -> handle_message_updated(event)
                "message.part.updated" -> handle_message_part_updated(event)
                "session.status" -> handle_session_status(event)
                "session.idle" -> handle_session_idle(event)
                "session.created", "session.updated" -> loadSessions()
                "session.deleted" -> handle_session_deleted(event)
                "session.diff" -> handle_session_diff(event)
                "session.error" -> handle_session_error(event)
                "permission.updated" -> handle_permission_updated(event)
                "permission.replied" -> _uiState.update { it.copy(pendingPermission = null) }
                "todo.updated" -> handle_todo_updated(event)
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error handling event: ${event.type}", e)
        }
    }

    private fun handle_message_updated(event: EventEnvelope) {
        val info = event.properties.jsonObject["info"] ?: return
        val message = parseMessage(json, info)
        if (message is AssistantMessage && message.sessionID == currentSessionId) {
            _uiState.update { state ->
                val messages = state.messages.toMutableList()
                val idx = messages.indexOfFirst { it is AssistantMessage && it.id == message.id }
                if (idx >= 0) messages[idx] = message else messages.add(message)
                state.copy(messages = messages)
            }
        } else if (message is UserMessage && message.sessionID == currentSessionId) {
            _uiState.update { state ->
                val messages = state.messages.toMutableList()
                val existingIdx = messages.indexOfFirst { it is UserMessage && it.id == message.id }
                if (existingIdx >= 0) {
                    messages[existingIdx] = message
                    state.copy(messages = messages)
                } else {
                    val tempIdx = messages.indexOfFirst { it is UserMessage && it.id.startsWith("temp-") }
                    if (tempIdx >= 0) {
                        val tempMsg = messages[tempIdx] as UserMessage
                        val displayText = state.userInputTexts[tempMsg.id]
                        messages[tempIdx] = message
                        val newTexts = if (displayText != null) {
                            state.userInputTexts + (message.id to displayText)
                        } else state.userInputTexts
                        state.copy(messages = messages, userInputTexts = newTexts)
                    } else {
                        state.copy(messages = messages)
                    }
                }
            }
        }
    }

    private fun handle_message_part_updated(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val partElement = props["part"] ?: return
        val delta = props["delta"]?.jsonPrimitive?.contentOrNull
        val part = parsePart(json, partElement)
        if (part.sessionID == currentSessionId) {
            handlePartUpdate(part, delta)
        }
    }

    private fun handle_session_status(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
        val statusElement = props["status"] ?: return
        if (sessionId == currentSessionId) {
            val status = parseSessionStatus(json, statusElement)
            _uiState.update {
                it.copy(
                    sessionStatus = status,
                    isGenerating = status is SessionStatus.Busy
                )
            }
        }
    }

    private fun handle_session_idle(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
        if (sessionId == currentSessionId) {
            _uiState.update {
                it.copy(
                    sessionStatus = SessionStatus.Idle,
                    isGenerating = false,
                    streamingText = "",
                    streamingReasoning = "",
                    currentToolCalls = emptyList()
                )
            }
        }
    }

    private fun handle_session_deleted(event: EventEnvelope) {
        val info = event.properties.jsonObject["info"]?.let {
            json.decodeFromJsonElement<Session>(it)
        }
        loadSessions()
        if (info?.id == currentSessionId) {
            currentSessionId = null
            _uiState.update { it.copy(messages = emptyList(), parts = emptyList(), currentSessionId = null, userInputTexts = emptyMap()) }
        }
    }

    private fun handle_session_diff(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
        val diffArray = props["diff"]?.jsonArray ?: return
        if (sessionId == currentSessionId) {
            val diffs = diffArray.map { json.decodeFromJsonElement<FileDiff>(it) }
            _uiState.update { it.copy(currentDiffs = diffs) }
        }
    }

    private fun handle_session_error(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val sessionId = props["sessionID"]?.jsonPrimitive?.contentOrNull
        if (sessionId == currentSessionId || sessionId == null) {
            _uiState.update {
                it.copy(isGenerating = false, error = "Session error occurred")
            }
        }
    }

    private fun handle_permission_updated(event: EventEnvelope) {
        val permission = json.decodeFromJsonElement<PermissionInfo>(event.properties)
        _uiState.update { it.copy(pendingPermission = permission) }
    }

    private fun handle_todo_updated(event: EventEnvelope) {
        val props = event.properties.jsonObject
        val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
        val todosArray = props["todos"]?.jsonArray ?: return
        if (sessionId == currentSessionId) {
            val todos = todosArray.map { json.decodeFromJsonElement<Todo>(it) }
            _uiState.update { it.copy(todos = todos) }
        }
    }

    private fun handlePartUpdate(part: Part, delta: String?) {
        when (part) {
            is TextPartData -> {
                val msgId = part.messageID
                val sb = streamingText.getOrPut(msgId) { StringBuilder() }
                if (delta != null) {
                    sb.append(delta)
                } else {
                    sb.clear()
                    sb.append(part.text)
                }

                _uiState.update { state ->
                    val existingParts = state.parts.toMutableList()
                    val idx = existingParts.indexOfFirst { it.id == part.id }
                    val updatedPart = part.copy(text = sb.toString())
                    if (idx >= 0) existingParts[idx] = updatedPart else existingParts.add(updatedPart)
                    state.copy(parts = existingParts, isGenerating = true, streamingText = sb.toString())
                }
            }

            is ToolCallPartData -> {
                _uiState.update { state ->
                    val existingParts = state.parts.toMutableList()
                    val idx = existingParts.indexOfFirst { it.id == part.id }
                    if (idx >= 0) existingParts[idx] = part else existingParts.add(part)
                    val toolParts = existingParts.filterIsInstance<ToolCallPartData>()
                    state.copy(parts = existingParts, currentToolCalls = toolParts, isGenerating = true)
                }
            }

            is ReasoningPartData -> {
                val msgId = part.messageID
                val reasoningKey = "reasoning-$msgId"
                val sb = streamingText.getOrPut(reasoningKey) { StringBuilder() }
                if (delta != null) {
                    sb.append(delta)
                } else {
                    sb.clear()
                    sb.append(part.text)
                }
                _uiState.update { state ->
                    val existingParts = state.parts.toMutableList()
                    val idx = existingParts.indexOfFirst { it.id == part.id }
                    val updatedPart = part.copy(text = sb.toString())
                    if (idx >= 0) existingParts[idx] = updatedPart else existingParts.add(updatedPart)
                    state.copy(parts = existingParts, streamingReasoning = sb.toString())
                }
            }

            is StepFinishPartInfo, is StepStartPartInfo -> {
                _uiState.update { state ->
                    val existingParts = state.parts.toMutableList()
                    val idx = existingParts.indexOfFirst { it.id == part.id }
                    if (idx >= 0) existingParts[idx] = part else existingParts.add(part)
                    state.copy(parts = existingParts)
                }
            }

            else -> {
                _uiState.update { state ->
                    val existingParts = state.parts.toMutableList()
                    val idx = existingParts.indexOfFirst { it.id == part.id }
                    if (idx >= 0) existingParts[idx] = part else existingParts.add(part)
                    state.copy(parts = existingParts)
                }
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            api.listSessions().fold(
                onSuccess = { sessions ->
                    _uiState.update {
                        it.copy(sessions = sessions.sortedByDescending { s -> s.time.updated })
                    }
                },
                onFailure = { e -> Log.e("ChatViewModel", "Failed to load sessions", e) }
            )
        }
    }

    fun loadAgents() {
        viewModelScope.launch {
            api.listAgents().fold(
                onSuccess = { agents ->
                    val agentIds = agents.map { it.id }
                    val builtIn = listOf("build", "plan")
                    val merged = (builtIn + agentIds).distinct()
                    if (merged.isNotEmpty()) {
                        _uiState.update { it.copy(availableAgents = merged) }
                    }
                },
                onFailure = { e -> Log.e("ChatViewModel", "Failed to load agents, using defaults", e) }
            )
        }
    }

    fun selectSession(sessionId: String) {
        currentSessionId = sessionId
        streamingText.clear()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                parts = emptyList(),
                currentSessionId = sessionId,
                currentDiffs = emptyList(),
                todos = emptyList(),
                pendingPermission = null,
                isGenerating = false,
                streamingText = "",
                currentToolCalls = emptyList(),
                userInputTexts = emptyMap()
            )
        }

        viewModelScope.launch {
            api.listMessages(sessionId).fold(
                onSuccess = { messagesWithParts ->
                    val messages = messagesWithParts.map { it.first }
                    val allParts = messagesWithParts.flatMap { it.second }
                    _uiState.update { it.copy(messages = messages, parts = allParts) }
                },
                onFailure = { e -> Log.e("ChatViewModel", "Failed to load messages", e) }
            )
        }

        viewModelScope.launch {
            api.getSessionDiff(sessionId).fold(
                onSuccess = { diffs -> _uiState.update { it.copy(currentDiffs = diffs) } },
                onFailure = { /* ignore */ }
            )
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true) }
            api.createSession().fold(
                onSuccess = { session ->
                    _uiState.update { it.copy(isCreatingSession = false) }
                    selectSession(session.id)
                    loadSessions()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isCreatingSession = false, error = "Failed to create session: ${e.message}")
                    }
                }
            )
        }
    }

    fun sendMessage(text: String) {
        val sessionId = currentSessionId ?: return
        if (text.isBlank()) return

        if (text.startsWith("/")) {
            handleSlashCommand(text.trim())
            _uiState.update { it.copy(currentInput = "") }
            return
        }

        val agent = _uiState.value.currentAgent
        val msgId = "temp-${System.currentTimeMillis()}"
        val userMessage = UserMessage(
            id = msgId,
            sessionID = sessionId,
            time = MessageTime(created = System.currentTimeMillis()),
            agent = agent,
            model = MessageModel(providerID = "", modelID = "")
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isGenerating = true,
                currentInput = "",
                streamingText = "",
                streamingReasoning = "",
                currentToolCalls = emptyList(),
                userInputTexts = state.userInputTexts + (msgId to text)
            )
        }

        viewModelScope.launch {
            api.sendMessageAsync(sessionId, text, agent).fold(
                onSuccess = { },
                onFailure = { e ->
                    _uiState.update { it.copy(isGenerating = false, error = "Failed to send: ${e.message}") }
                }
            )
        }
    }

    private fun handleSlashCommand(text: String) {
        val cmd = text.split(" ").first().lowercase()
        when (cmd) {
            "/model" -> {
                viewModelScope.launch {
                    api.listModels().fold(
                        onSuccess = { models ->
                            _uiState.update { it.copy(availableModels = models, showModelPicker = true) }
                        },
                        onFailure = { e ->
                            _uiState.update { it.copy(error = "Failed to load models: ${e.message}") }
                        }
                    )
                }
            }
            "/theme" -> {
                _uiState.update { it.copy(showThemePicker = true) }
            }
            "/abort" -> {
                abortSession()
            }
            "/compact", "/undo", "/redo", "/clear", "/diff", "/log", "/cost", "/init" -> {
                sendSlashCommandToServer(text)
            }
            else -> {
                sendSlashCommandToServer(text)
            }
        }
    }

    private fun sendSlashCommandToServer(text: String) {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            api.sendMessageAsync(sessionId, text, "build").fold(
                onSuccess = { },
                onFailure = { e -> _uiState.update { it.copy(error = "Command failed: ${e.message}") } }
            )
        }
    }

    fun selectModel(providerID: String, modelID: String) {
        _uiState.update { it.copy(showModelPicker = false, availableModels = emptyList()) }
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            api.setSessionModel(sessionId, providerID, modelID).fold(
                onSuccess = { },
                onFailure = { e -> _uiState.update { it.copy(error = "Failed to set model: ${e.message}") } }
            )
        }
    }

    fun selectTheme(theme: String) {
        _uiState.update { it.copy(showThemePicker = false) }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(showModelPicker = false, showThemePicker = false, availableModels = emptyList()) }
    }

    fun abortSession() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            api.abortSession(sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isGenerating = false) } },
                onFailure = { e -> _uiState.update { it.copy(error = "Failed to abort: ${e.message}") } }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            api.deleteSession(sessionId).fold(
                onSuccess = {
                    if (currentSessionId == sessionId) {
                        currentSessionId = null
                        _uiState.update { it.copy(messages = emptyList(), parts = emptyList(), currentSessionId = null, userInputTexts = emptyMap()) }
                    }
                    loadSessions()
                },
                onFailure = { e -> _uiState.update { it.copy(error = "Failed to delete: ${e.message}") } }
            )
        }
    }

    fun respondPermission(allow: Boolean) {
        val permission = _uiState.value.pendingPermission ?: return
        viewModelScope.launch {
            api.respondPermission(permission.sessionID, permission.id, if (allow) "allow" else "deny")
            _uiState.update { it.copy(pendingPermission = null) }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun updateInput(text: String) { _uiState.update { it.copy(currentInput = text) } }
    fun setAgent(agent: String) { _uiState.update { it.copy(currentAgent = agent) } }

    private fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val currentVersion = app.packageManager
                    .getPackageInfo(app.packageName, 0).versionName ?: "1.0.0"
                val update = updateChecker.checkForUpdate(currentVersion)
                if (update != null) {
                    _uiState.update { it.copy(pendingUpdate = update) }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Update check failed", e)
            }
        }
    }

    fun downloadUpdate() {
        val update = _uiState.value.pendingUpdate ?: return
        _uiState.update { it.copy(isDownloadingUpdate = true) }
        viewModelScope.launch {
            updateChecker.downloadAndInstall(update).fold(
                onSuccess = { _uiState.update { it.copy(isDownloadingUpdate = false, pendingUpdate = null) } },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            error = "Update download failed: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(pendingUpdate = null) }
    }

    fun checkForUpdateManually() {
        viewModelScope.launch {
            _uiState.update { it.copy(appUpdateStatus = "Checking for app updates...") }
            try {
                val app = getApplication<Application>()
                val currentVersion = app.packageManager
                    .getPackageInfo(app.packageName, 0).versionName ?: "1.0.0"
                val update = updateChecker.checkForUpdate(currentVersion)
                if (update != null) {
                    _uiState.update { it.copy(pendingUpdate = update, appUpdateStatus = null) }
                } else {
                    _uiState.update { it.copy(appUpdateStatus = "App is up to date (v$currentVersion)") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(appUpdateStatus = "Update check failed: ${e.message}") }
            }
        }
    }

    fun clearAppUpdateStatus() {
        _uiState.update { it.copy(appUpdateStatus = null) }
    }

    fun updateOpenCodeServer() {
        viewModelScope.launch {
            val app = getApplication<Application>()

            if (!TermuxCommandHelper.isTermuxInstalled(app)) {
                _uiState.update { it.copy(serverUpdateStatus = "Termux is not installed. Install it from F-Droid or GitHub.") }
                return@launch
            }

            _uiState.update { it.copy(serverUpdateStatus = "Sending update command to Termux...") }

            val success = TermuxCommandHelper.updateOpencode(app)
            if (success) {
                _uiState.update { it.copy(
                    serverUpdateStatus = "Update command sent to Termux!\n" +
                        "Check Termux for progress.\n" +
                        "After npm install finishes, restart the server in Termux, then tap Reconnect."
                ) }
            } else {
                _uiState.update { it.copy(
                    serverUpdateStatus = "Failed to send command to Termux.\n" +
                        "Make sure Termux is installed and has RUN_COMMAND permission."
                ) }
            }
        }
    }

    fun reconnectAfterServerUpdate() {
        _uiState.update { it.copy(serverUpdateStatus = "Reconnecting...") }
        connectToServer()
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (_uiState.value.isConnected) {
                _uiState.update { it.copy(serverUpdateStatus = "Connected! Server updated successfully.") }
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(serverUpdateStatus = null) }
            } else {
                _uiState.update { it.copy(serverUpdateStatus = "Could not reconnect. Make sure server is running.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventSubscription?.cancel()
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(application) as T
            }
        }
    }
}

data class ChatUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val serverVersion: String? = null,
    val sessions: List<Session> = emptyList(),
    val currentSessionId: String? = null,
    val currentAgent: String = "build",
    val availableAgents: List<String> = listOf("build", "plan"),
    val availableModels: List<ModelInfo> = emptyList(),
    val showModelPicker: Boolean = false,
    val showThemePicker: Boolean = false,
    val messages: List<Any> = emptyList(),
    val parts: List<Part> = emptyList(),
    val currentDiffs: List<FileDiff> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val isGenerating: Boolean = false,
    val isCreatingSession: Boolean = false,
    val currentInput: String = "",
    val pendingPermission: PermissionInfo? = null,
    val error: String? = null,
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val currentToolCalls: List<ToolCallPartData> = emptyList(),
    val userInputTexts: Map<String, String> = emptyMap(),
    val pendingUpdate: UpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val serverUpdateStatus: String? = null,
    val appUpdateStatus: String? = null
)
