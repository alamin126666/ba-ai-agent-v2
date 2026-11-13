package com.ba.aiagent.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.data.local.SessionEntity
import com.ba.aiagent.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: BaAiAgentApp, onOpenChat: (Long) -> Unit) {
    val dao = app.database.chatDao()
    val sessions by dao.sessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<SessionEntity?>(null) }
    val filtered = sessions.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.lastPreview.contains(query, ignoreCase = true)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("BA AI AGENT", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    val id = dao.insertSession(
                        SessionEntity(
                            title = "New chat", providerId = "openrouter", modelId = "",
                            mode = "chat", createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(), lastPreview = ""
                        )
                    )
                    onOpenChat(id)
                }
            }) { Icon(Icons.Default.Add, contentDescription = "New chat") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search chats") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                EmptyState("Start a new chat", "Pick a provider key in Settings, then ask the agent to build, fix or explain code.")
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { s ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { onOpenChat(s.id) }
                        ) {
                            Row(Modifier.padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        (s.modelId.ifBlank { s.providerId }) + "  •  " +
                                            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(s.updatedAt)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (s.lastPreview.isNotBlank()) Text(s.lastPreview.take(80), maxLines = 2)
                                }
                                IconButton(onClick = { toDelete = s }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${s.title}\" will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.deleteMessagesFor(s.id); dao.deleteSession(s.id) }
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}
