package com.ba.aiagent.ui.terminal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.domain.agent.ShellRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(private val app: BaAiAgentApp) : ViewModel() {
    private val shell = ShellRunner(app.workspaceStore)
    private val _output = MutableStateFlow("\$ BA AI AGENT terminal — local shell, no VPS.\n\$ Working dir: ${app.workspaceStore.root.absolutePath}\n")
    val output: StateFlow<String> = _output
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun run(command: String, useTermux: Boolean) {
        if (command.isBlank() || _running.value) return
        viewModelScope.launch {
            _running.value = true
            _output.value += "\n\$ $command\n"
            if (useTermux) {
                val sent = shell.sendToTermux(app.applicationContext, command)
                _output.value += if (sent) "(sent to Termux — see the Termux app for output)\n"
                else "(Termux not found — install Termux + Termux:API, or turn the Bridge off)\n"
            } else {
                val r = shell.run(command)
                _output.value += r.output + "\n"
                if (!r.ok) _output.value += "(exit code != 0)\n"
            }
            _running.value = false
        }
    }

    fun clear() {
        _output.value = "\$ Terminal cleared.\n"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(app: BaAiAgentApp) {
    val vm = remember { TerminalViewModel(app) }
    val output by vm.output.collectAsState()
    val running by vm.running.collectAsState()
    val bridge by app.settingsStore.termuxBridge.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Scaffold(topBar = { TopAppBar(title = { Text("Terminal") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Termux Bridge", modifier = Modifier.weight(1f))
                Switch(checked = bridge, onCheckedChange = { scope.launch { app.settingsStore.setTermuxBridge(it) } })
            }
            Text(
                "Local shell on this phone — no VPS, no root. Dangerous commands (rm -rf, su, format) are blocked. " +
                    "With the Bridge ON, commands go to the installed Termux app (pkg, python, node, git).",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")))
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Install Termux (F-Droid)") }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0A0E13), MaterialTheme.shapes.medium)
                    .padding(12.dp).verticalScroll(scroll)
            ) {
                Text(output, fontFamily = FontFamily.Monospace, color = Color(0xFF4ADE80))
                if (running) Text("…running", fontFamily = FontFamily.Monospace, color = Color(0xFF4ADE80))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text("ls, cat, pwd…") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.run(command, bridge); command = "" }, modifier = Modifier.padding(start = 8.dp)) { Text("Run") }
                OutlinedButton(onClick = { vm.clear() }, modifier = Modifier.padding(start = 8.dp)) { Text("Clear") }
            }
        }
    }
}
