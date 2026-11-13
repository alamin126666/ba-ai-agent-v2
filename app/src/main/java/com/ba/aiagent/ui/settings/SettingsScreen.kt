package com.ba.aiagent.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.data.api.AiApiClient
import com.ba.aiagent.data.api.Providers
import com.ba.aiagent.ui.components.ApiKeyField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: BaAiAgentApp, onOpenModels: () -> Unit, onOpenDesign: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeProvider by app.settingsStore.activeProvider.collectAsState(initial = "openrouter")
    val activeModel by app.settingsStore.activeModel.collectAsState(initial = "")
    val temp by app.settingsStore.temperature.collectAsState(initial = 0.7)
    val maxTok by app.settingsStore.maxTokens.collectAsState(initial = 4096)
    val streaming by app.settingsStore.streaming.collectAsState(initial = true)
    val sysPrompt by app.settingsStore.systemPrompt.collectAsState(initial = "")
    val agentPrompt by app.settingsStore.agentPrompt.collectAsState(initial = "")
    val themeMode by app.settingsStore.themeMode.collectAsState(initial = "dark")
    val accent by app.settingsStore.accent.collectAsState(initial = "orange")
    val termux by app.settingsStore.termuxBridge.collectAsState(initial = true)
    var keyInputs by remember { mutableStateOf(mapOf<String, String>()) }
    var keyStatus by remember { mutableStateOf(mapOf<String, String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val api = remember { AiApiClient() }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Provider Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp())
            Text("Every provider needs its own key. Keys stay only on this phone (encrypted). Never share them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Providers.all.forEach { p ->
                val saved = app.keyStore.hasKey(p.id)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (activeProvider == p.id) Text("ACTIVE", color = MaterialTheme.colorScheme.primary)
                        }
                        if (p.note != null) Text(p.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (saved) "Saved: ${app.keyStore.maskedKey(p.id)}" else "Not set", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ApiKeyField(
                            value = keyInputs[p.id] ?: "",
                            onChange = { keyInputs = keyInputs + (p.id to it) },
                            label = "API key for ${p.name}"
                        )
                        Row {
                            Button(onClick = {
                                val key = keyInputs[p.id].orEmpty()
                                if (key.isBlank()) return@Button
                                scope.launch {
                                    val err = api.testKey(p, key)
                                    if (err == null) {
                                        app.keyStore.saveKey(p.id, key)
                                        app.settingsStore.setActiveProvider(p.id)
                                        keyStatus = keyStatus + (p.id to "Saved ✓")
                                    } else {
                                        keyStatus = keyStatus + (p.id to err)
                                    }
                                }
                            }) { Text("Save Key") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val key = app.keyStore.getKey(p.id) ?: return@launch
                                    val err = api.testKey(p, key)
                                    keyStatus = keyStatus + (p.id to (err ?: "Connection OK ✓"))
                                }
                            }) { Text("Test") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyHelpUrl)))
                            }) { Text("Get key") }
                        }
                        keyStatus[p.id]?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Model", fontWeight = FontWeight.Bold, fontSize = 18.sp())
            Text("Provider: $activeProvider • Model: ${activeModel.ifBlank { "not picked yet" }}")
            Button(onClick = onOpenModels, modifier = Modifier.padding(top = 8.dp)) { Text("Select Model") }
            OutlinedButton(onClick = onOpenDesign, modifier = Modifier.padding(top = 8.dp)) { Text("Design themes") }

            Spacer(Modifier.height(16.dp))
            Text("Agent / Chat", fontWeight = FontWeight.Bold, fontSize = 18.sp())
            Text("Temperature: ${"%.1f".format(temp)}")
            Slider(value = temp.toFloat(), onValueChange = { scope.launch { app.settingsStore.setTemperature(it.toDouble()) } }, valueRange = 0f..1f)
            Text("Max tokens: $maxTok")
            Row {
                listOf(1024, 2048, 4096, 8192).forEach { v ->
                    TextButton(onClick = { scope.launch { app.settingsStore.setMaxTokens(v) } }) {
                        Text("$v", color = if (maxTok == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Streaming", modifier = Modifier.weight(1f))
                Switch(checked = streaming, onCheckedChange = { scope.launch { app.settingsStore.setStreaming(it) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Termux Bridge", modifier = Modifier.weight(1f))
                Switch(checked = termux, onCheckedChange = { scope.launch { app.settingsStore.setTermuxBridge(it) } })
            }
            OutlinedTextField(value = sysPrompt, onValueChange = { scope.launch { app.settingsStore.setSystemPrompt(it) } }, label = { Text("Chat system prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = agentPrompt, onValueChange = { scope.launch { app.settingsStore.setAgentPrompt(it) } }, label = { Text("Agent system prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextButton(onClick = {
                scope.launch {
                    app.settingsStore.setSystemPrompt(com.ba.aiagent.data.local.SettingsStore.DEFAULT_SYSTEM_PROMPT)
                    app.settingsStore.setAgentPrompt(com.ba.aiagent.data.local.SettingsStore.DEFAULT_AGENT_PROMPT)
                }
            }) { Text("Reset prompts to default") }

            Spacer(Modifier.height(16.dp))
            Text("Theme", fontWeight = FontWeight.Bold, fontSize = 18.sp())
            Row {
                listOf("dark", "light", "system").forEach { m ->
                    TextButton(onClick = { scope.launch { app.settingsStore.setThemeMode(m) } }) {
                        Text(m.replaceFirstChar { it.uppercase() }, color = if (themeMode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row {
                listOf("orange" to "Claude orange", "violet" to "BA violet").forEach { (id, label) ->
                    TextButton(onClick = { scope.launch { app.settingsStore.setAccent(id) } }) {
                        Text(label, color = if (accent == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Muse Bridge", fontWeight = FontWeight.Bold, fontSize = 18.sp())
            Text("The personal Muse agent has no token — share a chat to the Muse app from the chat menu, paste its reply back, and the agent applies it as a patch. The Meta Muse provider above (dev.meta.ai key) runs Muse Spark / Wonder models by API.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all chats") }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all chats?") },
            text = { Text("All chat sessions and messages will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.database.chatDao().clearMessages(); app.database.chatDao().clearSessions() }
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
