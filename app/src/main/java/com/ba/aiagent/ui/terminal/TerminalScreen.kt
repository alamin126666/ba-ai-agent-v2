package com.ba.aiagent.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ba.aiagent.BaAiAgentApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(app: BaAiAgentApp) {
    val terminal = app.terminal
    val output by terminal.output.collectAsState()
    val cwd by terminal.cwd.collectAsState()
    val history by terminal.history.collectAsState()
    val linuxState by app.linux.state.collectAsState()
    var command by remember { mutableStateOf("") }
    var histIndex by remember { mutableStateOf(-1) }
    var savedDraft by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Follow the tail like a real terminal.
    LaunchedEffect(output) {
        scroll.scrollTo(scroll.maxValue)
    }

    val root = app.workspaceStore.root.absolutePath
    val shownCwd = if (terminal.inLinux) {
        if (cwd == "/workspace") "~" else if (cwd.startsWith("/workspace/")) "~" + cwd.removePrefix("/workspace") else cwd
    } else if (cwd == root) "~" else if (cwd.startsWith("$root/")) "~" + cwd.removePrefix(root) else cwd

    fun historyUp() {
        if (history.isEmpty()) return
        if (histIndex == -1) {
            savedDraft = command
            histIndex = history.lastIndex
        } else if (histIndex > 0) {
            histIndex--
        }
        command = history[histIndex]
    }

    fun historyDown() {
        if (histIndex == -1) return
        if (histIndex < history.lastIndex) {
            histIndex++
            command = history[histIndex]
        } else {
            histIndex = -1
            command = savedDraft
        }
    }

    fun send() {
        if (command.isBlank()) return
        terminal.send(command)
        command = ""
        histIndex = -1
    }

    Scaffold(topBar = { TopAppBar(title = { Text("BA Terminal") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Our own Linux, built into this app — no other app, no VPS, no root. " +
                    "The agent and this tab share one shell, so cd sticks. Dangerous commands are blocked.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(linuxState, color = Color(0xFF4ADE80), fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(
                "ba@terminal:$shownCwd $",
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4ADE80)
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0A0E13), MaterialTheme.shapes.medium)
                    .padding(12.dp).verticalScroll(scroll)
            ) {
                Text(output, fontFamily = FontFamily.Monospace, color = Color(0xFF4ADE80))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { historyUp() }) { Text("↑", fontFamily = FontFamily.Monospace) }
                TextButton(onClick = { historyDown() }) { Text("↓", fontFamily = FontFamily.Monospace) }
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text("ls, cat, pwd…") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { send() }, modifier = Modifier.padding(start = 8.dp)) { Text("Run") }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { terminal.clear() }) { Text("Clear") }
                OutlinedButton(onClick = { terminal.restart() }, modifier = Modifier.padding(start = 8.dp)) { Text("Restart shell") }
            }
        }
    }
}
