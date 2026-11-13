package com.ba.aiagent.ui.design

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ba.aiagent.BaAiAgentApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(app: BaAiAgentApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val activeDesign by app.settingsStore.activeDesign.collectAsState(initial = "linear")
    var prompt by remember { mutableStateOf("") }
    var previewHtml by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Design", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("DESIGN.md themes — pick a style, the agent builds UI in that style.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(app.designStore.themes(), key = { it.id }) { t ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeDesign == t.id) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { scope.launch { app.settingsStore.setActiveDesign(t.id) } }
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Bold)
                                Text("bg ${t.background} • accent ${t.accent} • radius ${t.radiusDp}dp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (activeDesign == t.id) Text("ACTIVE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Button(onClick = { showImport = true }) { Text("Import DESIGN.md") }
            Spacer(Modifier.height(16.dp))
            Text("Prototype mode", fontWeight = FontWeight.Bold)
            Text("Describe a page — the agent writes a single self-contained HTML file (inline CSS, works offline) and previews it here. Ask in chat: \"/ui make a login page\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prototype prompt, e.g. login page") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val theme = app.designStore.byId(activeDesign)
                previewHtml = samplePrototype(prompt.ifBlank { "Login page" }, theme.name, theme.background, theme.accent)
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Preview sample prototype") }
            previewHtml?.let { html ->
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false } },
                    update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                )
                Button(onClick = {
                    app.workspaceStore.write("prototype.html", html)
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Save as prototype.html in workspace") }
            }
        }
    }
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import DESIGN.md") },
            text = {
                Column {
                    Text("Paste a DESIGN.md file. It is saved as a custom theme note in the workspace (design-import.md).")
                    OutlinedTextField(value = importText, onValueChange = { importText = it }, label = { Text("DESIGN.md text") }, modifier = Modifier.fillMaxWidth(), minLines = 6)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.isNotBlank()) app.workspaceStore.write("design-import.md", importText)
                    showImport = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
}

fun samplePrototype(title: String, themeName: String, bg: String, accent: String): String = """
<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;font-family:system-ui;background:$bg;color:#fff;display:flex;min-height:100vh;align-items:center;justify-content:center}
.card{background:#ffffff14;border:1px solid #ffffff22;border-radius:16px;padding:28px;width:min(340px,90vw)}
h1{margin:0 0 4px;font-size:22px}p{opacity:.7;margin:0 0 20px}
input{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #ffffff33;background:#00000033;color:#fff;margin-bottom:12px}
button{width:100%;padding:12px;border:0;border-radius:10px;background:$accent;color:#fff;font-weight:700}
.small{text-align:center;opacity:.6;font-size:12px;margin-top:14px}</style></head>
<body><div class="card"><h1>$title</h1><p>Prototype — $themeName theme (BA AI AGENT)</p>
<input placeholder="Email"><input type="password" placeholder="Password">
<button>Continue</button><div class="small">Self-contained HTML — no CDN, works offline</div></div></body></html>
""".trimIndent()
