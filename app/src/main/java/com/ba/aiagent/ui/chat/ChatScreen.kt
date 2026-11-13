package com.ba.aiagent.ui.chat

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.data.api.AiApiClient
import com.ba.aiagent.data.api.ChatMsg
import com.ba.aiagent.data.api.Providers
import com.ba.aiagent.data.local.MessageEntity
import com.ba.aiagent.domain.agent.AgentEngine
import com.ba.aiagent.domain.agent.AgentStep
import com.ba.aiagent.domain.agent.AgentTools
import com.ba.aiagent.domain.agent.ShellRunner
import com.ba.aiagent.domain.model.ChatMessage
import com.ba.aiagent.ui.components.EmptyState
import com.ba.aiagent.ui.components.MessageBubble
import com.ba.aiagent.ui.components.ToolCallCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(private val app: BaAiAgentApp, private val sessionId: Long) : ViewModel() {
    private val api = AiApiClient()
    private val tools = AgentTools(app.workspaceStore, ShellRunner(app.terminal))
    private val engine = AgentEngine(api, tools)

    val messages = app.database.chatDao().messages(sessionId)
    val session = MutableStateFlow<com.ba.aiagent.data.local.SessionEntity?>(null)
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming
    private val _streamText = MutableStateFlow("")
    val streamText: StateFlow<String> = _streamText
    private val _steps = MutableStateFlow<List<AgentStep>>(emptyList())
    val steps: StateFlow<List<AgentStep>> = _steps
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _skillLoaded = MutableStateFlow<String?>(null)
    val skillLoaded: StateFlow<String?> = _skillLoaded
    private val _approval = MutableStateFlow<Triple<String, String, CompletableDeferred<Boolean>>?>(null)
    val approval: StateFlow<Triple<String, String, CompletableDeferred<Boolean>>?> = _approval

    private var job: Job? = null

    init {
        viewModelScope.launch {
            session.value = app.database.chatDao().session(sessionId)
        }
    }

    fun stop() { job?.cancel(); _streaming.value = false }

    fun clearError() { _error.value = null }

    fun answerApproval(approved: Boolean) {
        _approval.value?.third?.complete(approved)
        _approval.value = null
    }

    fun send(text: String, attachContent: String? = null) {
        if (text.isBlank() || _streaming.value) return
        val sess = session.value ?: return
        val matched = app.skillStore.matchFor(text)
        _skillLoaded.value = matched.firstOrNull()?.name
        job = viewModelScope.launch {
            _streaming.value = true
            _streamText.value = ""
            _steps.value = emptyList()
            _error.value = null
            val dao = app.database.chatDao()
            val fullText = if (attachContent != null) "$text\n\n[Attached file]\n$attachContent" else text
            dao.insertMessage(MessageEntity(sessionId = sessionId, role = "user", content = fullText, timestamp = System.currentTimeMillis()))
            if (sess.title == "New chat") {
                dao.renameSession(sessionId, text.take(40))
            }
            dao.updateSession(sessionId, sess.title, System.currentTimeMillis(), text.take(80))
            try {
                val providerId = sess.providerId
                val provider = Providers.byId(providerId)
                val key = app.keyStore.getKey(providerId)
                if (key.isNullOrBlank()) {
                    _error.value = "No API key saved for ${provider.name}. Add it in Settings → Provider Hub."
                    _streaming.value = false
                    return@launch
                }
                var model = sess.modelId
                if (model.isBlank()) {
                    model = app.settingsStore.activeModel.first().ifBlank { "anthropic/claude-sonnet-4.5" }
                }
                val temp = app.settingsStore.temperature.first()
                val maxTok = app.settingsStore.maxTokens.first()
                val history = dao.messages(sessionId).first()
                    .map { ChatMessage(id = it.id, sessionId = it.sessionId, role = it.role, content = it.content) }
                val sysPrompt = if (sess.mode == "chat") app.settingsStore.systemPrompt.first() else app.settingsStore.agentPrompt.first()
                val skillText = matched.joinToString("\n") { "Skill ${it.name}: ${it.instructions}" }
                val finalSystem = if (skillText.isNotBlank()) "$sysPrompt\n\nLoaded skills:\n$skillText" else sysPrompt

                if (sess.mode == "chat") {
                    val sb = StringBuilder()
                    api.chatStream(provider, key, model, history.map { ChatMsg(it.role, it.content) }, temp, maxTok, finalSystem)
                        .collect { token -> sb.append(token); _streamText.value = sb.toString() }
                    dao.insertMessage(MessageEntity(sessionId = sessionId, role = "assistant", content = sb.toString(), timestamp = System.currentTimeMillis()))
                } else {
                    val sb = StringBuilder()
                    var currentAssistant = StringBuilder()
                    engine.runAgentTurn(
                        providerId = providerId, apiKey = key, model = model,
                        history = history, userText = fullText,
                        temperature = temp, maxTokens = maxTok, systemPrompt = finalSystem,
                        approve = { tool, summary ->
                            val d = CompletableDeferred<Boolean>()
                            _approval.value = Triple(tool, summary, d)
                            d.await()
                        },
                        onStep = { step ->
                            _steps.value = _steps.value.filterNot { it.tool == step.tool && it.status == "Running" } + step
                            viewModelScope.launch {
                                dao.insertMessage(
                                    MessageEntity(sessionId = sessionId, role = "tool", content = step.result, toolName = step.tool, toolStatus = step.status, timestamp = System.currentTimeMillis())
                                )
                            }
                        },
                        onToken = { token ->
                            currentAssistant.append(token)
                            _streamText.value = currentAssistant.toString()
                        }
                    )
                    val finalText = currentAssistant.toString()
                    if (finalText.isNotBlank()) {
                        dao.insertMessage(MessageEntity(sessionId = sessionId, role = "assistant", content = finalText, timestamp = System.currentTimeMillis()))
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.message ?: "Something went wrong."
                val partial = _streamText.value
                if (partial.isNotBlank()) {
                    dao.insertMessage(MessageEntity(sessionId = sessionId, role = "assistant", content = partial, timestamp = System.currentTimeMillis()))
                }
            } finally {
                _streaming.value = false
                _streamText.value = ""
            }
        }
    }

    fun exportChat(context: android.content.Context) {
        viewModelScope.launch {
            val msgs = app.database.chatDao().messages(sessionId).first()
            val md = buildString {
                append("# ${session.value?.title ?: "Chat"}\n\n")
                msgs.forEach { append("**${it.role}:** ${it.content}\n\n") }
            }
            val file = java.io.File(app.cacheDir, "chat-export.md").apply { writeText(md) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export chat"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(app: BaAiAgentApp, sessionId: Long, onBack: () -> Unit, onOpenDesign: () -> Unit) {
    val vm = remember(sessionId) { ChatViewModel(app, sessionId) }
    val messages by vm.messages.collectAsState(initial = emptyList())
    val sess by vm.session.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val streamText by vm.streamText.collectAsState()
    val steps by vm.steps.collectAsState()
    val error by vm.error.collectAsState()
    val skill by vm.skillLoaded.collectAsState()
    val approval by vm.approval.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showSlash by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sess?.title ?: "Chat") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export chat (.md)") }, onClick = { menu = false; vm.exportChat(context) })
                        DropdownMenuItem(text = { Text("Design") }, onClick = { menu = false; onOpenDesign() })
                        DropdownMenuItem(text = { Text("Clear chat") }, onClick = {
                            menu = false
                            scope.launch { app.database.chatDao().deleteMessagesFor(sessionId) }
                        })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text("${sess?.providerId ?: ""} • ${(sess?.modelId ?: "").ifBlank { "default model" }}") })
                Spacer(Modifier.width(8.dp))
                listOf("chat", "agent", "plan").forEach { mode ->
                    TextButton(onClick = {
                        scope.launch {
                            val s = sess ?: return@launch
                            app.database.chatDao().updateMode(s.id, mode)
                            vm.session.value = s.copy(mode = mode)
                        }
                    }) {
                        Text(mode.replaceFirstChar { it.uppercase() }, color = if (sess?.mode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (skill != null) Text("Skill loaded: $skill", color = MaterialTheme.colorScheme.primary)
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (messages.isEmpty() && !streaming) {
                    item { EmptyState("Ask the agent", "Try: /fix, /review, /plan — or ask it to build a file.") }
                }
                items(messages, key = { it.id }) { m ->
                    when (m.role) {
                        "tool" -> ToolCallCard(m.toolName ?: "tool", "", m.toolStatus ?: "Done", m.content)
                        else -> MessageBubble(m.content, isUser = m.role == "user")
                    }
                }
                items(steps.filter { it.status == "Running" }) { s ->
                    ToolCallCard(s.tool, s.args, s.status, s.result)
                }
                if (streaming && streamText.isNotBlank()) {
                    item { MessageBubble(streamText, isUser = false, isStreaming = true) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = { showSlash = !showSlash }) { Text("/") }
                IconButton(onClick = {
                    val f = app.workspaceStore.listFiles().firstOrNull { it.isFile }
                    if (f != null) vm.send(input.ifBlank { "Use the attached file." }, f.readText().take(4000))
                }) { Icon(Icons.Default.AttachFile, contentDescription = "Attach first workspace file") }
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text("Message the agent…") },
                    modifier = Modifier.weight(1f), maxLines = 4
                )
                if (streaming) {
                    IconButton(onClick = { vm.stop() }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
                } else {
                    IconButton(onClick = { vm.send(input); input = "" }) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
            if (showSlash) {
                Row {
                    listOf("/plan", "/fix", "/review", "/refactor", "/explain", "/test", "/commit", "/new", "/docs", "/ui").forEach { cmd ->
                        TextButton(onClick = { input = "$cmd "; showSlash = false }) { Text(cmd) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    approval?.let { (tool, summary, _) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Approve tool call?") },
            text = { Text("$tool\n$summary\n\nDangerous tools need your approval every time.") },
            confirmButton = { TextButton(onClick = { vm.answerApproval(true) }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { vm.answerApproval(false) }) { Text("Deny") } }
        )
    }
}
