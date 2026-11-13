package com.ba.aiagent.ui.files

import android.content.Intent
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.ui.components.EmptyState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(app: BaAiAgentApp, onOpenFile: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val files = remember(query, refresh) {
        app.workspaceStore.listFiles().filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Files — workspace") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Workspace: ${app.workspaceStore.root.absolutePath}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search files") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                EmptyState("No files yet", "Ask the agent to create a file, or use /new.")
            } else {
                LazyColumn {
                    items(files, key = { it.absolutePath }) { f ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { onOpenFile(f.name) }
                        ) {
                            Row(Modifier.padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name)
                                    Text(
                                        "${f.length()} bytes • " + SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(f.lastModified())),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share ${f.name}"))
                                }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                                IconButton(onClick = { toDelete = f }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${f.name}?") },
            confirmButton = {
                TextButton(onClick = { f.delete(); toDelete = null; refresh++ }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(app: BaAiAgentApp, path: String, onBack: () -> Unit) {
    val content = remember(path) { app.workspaceStore.read(path) ?: "File not found: $path" }
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(path) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) { Text("Copy") }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(content, fontFamily = FontFamily.Monospace)
        }
    }
}
