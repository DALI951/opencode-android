package ai.opencode.android.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.opencode.android.data.api.ConnectionManager
import ai.opencode.android.data.api.EventEnvelope
import ai.opencode.android.data.model.*
import ai.opencode.android.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

class ChatViewModel(
    private val connectionManager: ConnectionManager = AppContainer.connectionManager
) : ViewModel() {

    private val api get() = connectionManager.api
    private val json get() = connectionManager.json

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var eventSubscription: Job? = null
    private var currentSessionId: String? = null
    private val streamingText = ConcurrentHashMap<String, StringBuilder>()

    init {
        connectToServer()
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
                Log.e("ChatViewModel", "Event subscription error", e)
                _uiState.update { it.copy(isConnected = false) }
            }
        }
    }

    private fun handleEvent(event: EventEnvelope) {
        when (event.type) {
            "message.updated" -> {
                val info = event.properties.jsonObject["info"] ?: return
                val message = parseMessage(json, info)
                if (message.sessionID == currentSessionId) {
                    _uiState.update { state ->
                        val messages = state.messages.toMutableList()
                        val idx = messages.indexOfFirst { it.id == message.id }
                        if (idx >= 0) messages[idx] = message else messages.add(message)
                        state.copy(messages = messages)
                    }
                }
            }

            "message.part.updated" -> {
                val props = event.properties.jsonObject
                val partElement = props["part"] ?: return
                val delta = props["delta"]?.jsonPrimitive?.contentOrNull
                val part = parsePart(json, partElement)
                if (part.sessionID == currentSessionId) {
                    handlePartUpdate(part, delta)
                }
            }

            "session.status" -> {
                val props = event.properties.jsonObject
                val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
                val statusElement = props["status"] ?: return
                if (sessionId == currentSessionId) {
                    _uiState.update {
                        it.copy(sessionStatus = parseSessionStatus(json, statusElement))
                    }
                }
            }

            "session.idle" -> {
                val props = event.properties.jsonObject
                val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
                if (sessionId == currentSessionId) {
                    _uiState.update {
                        it.copy(sessionStatus = SessionStatus.Idle, isGenerating = false)
                    }
                }
            }

            "session.created", "session.updated" -> loadSessions()

            "session.deleted" -> {
                val info = event.properties.jsonObject["info"]?.let {
                    json.decodeFromJsonElement<Session>(it)
                }
                loadSessions()
                if (info?.id == currentSessionId) {
                    currentSessionId = null
                    _uiState.update { it.copy(messages = emptyList(), parts = emptyList(), currentSessionId = null) }
                }
            }

            "session.diff" -> {
                val props = event.properties.jsonObject
                val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
                val diffArray = props["diff"]?.jsonArray ?: return
                if (sessionId == currentSessionId) {
                    val diffs = diffArray.map { json.decodeFromJsonElement<FileDiff>(it) }
                    _uiState.update { it.copy(currentDiffs = diffs) }
                }
            }

            "session.error" -> {
                val props = event.properties.jsonObject
                val sessionId = props["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId == currentSessionId || sessionId == null) {
                    _uiState.update {
                        it.copy(isGenerating = false, error = "Session error occurred")
                    }
                }
            }

            "permission.updated" -> {
                val permission = json.decodeFromJsonElement<PermissionInfo>(event.properties)
                _uiState.update { it.copy(pendingPermission = permission) }
            }

            "permission.replied" -> {
                _uiState.update { it.copy(pendingPermission = null) }
            }

            "todo.updated" -> {
                val props = event.properties.jsonObject
                val sessionId = props["sessionID"]?.jsonPrimitive?.content ?: return
                val todosArray = props["todos"]?.jsonArray ?: return
                if (sessionId == currentSessionId) {
                    val todos = todosArray.map { json.decodeFromJsonElement<Todo>(it) }
                    _uiState.update { it.copy(todos = todos) }
                }
            }
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
                    state.copy(parts = existingParts, isGenerating = true)
                }
            }

            is ToolCallPartData, is ReasoningPartData, is StepFinishPartInfo, is StepStartPartInfo -> {
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
                isGenerating = false
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

        _uiState.update { state ->
            state.copy(
                messages = state.messages + UserMessage(
                    id = "temp-${System.currentTimeMillis()}",
                    sessionID = sessionId,
                    time = MessageTime(created = System.currentTimeMillis()),
                    agent = "build",
                    model = MessageModel(providerID = "", modelID = "")
                ).let { object : Message() { override val id = it.id; override val sessionID = it.sessionID } },
                isGenerating = true,
                currentInput = ""
            )
        }

        viewModelScope.launch {
            api.sendMessageAsync(sessionId, text).fold(
                onSuccess = { },
                onFailure = { e ->
                    _uiState.update { it.copy(isGenerating = false, error = "Failed to send: ${e.message}") }
                }
            )
        }
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
                        _uiState.update { it.copy(messages = emptyList(), parts = emptyList(), currentSessionId = null) }
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

    override fun onCleared() {
        super.onCleared()
        eventSubscription?.cancel()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel() as T
    }
}

data class ChatUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val serverVersion: String? = null,
    val sessions: List<Session> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<Message> = emptyList(),
    val parts: List<Part> = emptyList(),
    val currentDiffs: List<FileDiff> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val isGenerating: Boolean = false,
    val isCreatingSession: Boolean = false,
    val currentInput: String = "",
    val pendingPermission: PermissionInfo? = null,
    val error: String? = null
)
