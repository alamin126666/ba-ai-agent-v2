package com.ba.aiagent.ui.skills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.data.local.SkillParser
import com.ba.aiagent.domain.model.Skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(app: BaAiAgentApp) {
    var refresh by remember { mutableStateOf(0) }
    val skills = remember(refresh) { app.skillStore.all() }
    var selected by remember { mutableStateOf<Skill?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(skills.associate { it.name to true }) }

    Scaffold(topBar = { TopAppBar(title = { Text("Skills", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Claude Code style skills. Type /plan, /fix, /review in chat — or let the agent load one automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = githubUrl, onValueChange = { githubUrl = it }, label = { Text("Install from GitHub — paste SKILL.md raw URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                Button(onClick = {
                    // The raw URL must be fetched by the agent in chat in this version;
                    // here we validate a pasted SKILL.md body instead — see note below.
                    notice = "Paste the SKILL.md text into Create custom skill below, or ask the agent in chat: \"install this skill <url>\" — it fetches the file with its tools."
                }) { Text("Install") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { showCreate = true }) { Text("Create custom skill") }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(skills, key = { it.name }) { s ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("/${s.name}  •  ${s.source}", fontWeight = FontWeight.Bold)
                                Text(s.description)
                            }
                            Switch(
                                checked = enabled[s.name] ?: true,
                                onCheckedChange = { enabled = enabled + (s.name to it) }
                            )
                            TextButton(onClick = { selected = s }) { Text("Read") }
                        }
                    }
                }
            }
        }
    }
    selected?.let { s ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("/${s.name}") },
            text = { Text(s.instructions) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            dismissButton = {
                if (s.source == "custom") TextButton(onClick = {
                    app.skillStore.deleteCustom(s.name); selected = null; refresh++
                }) { Text("Delete") }
            }
        )
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create custom skill") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. seo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description — when to use it") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("Instructions (Markdown)") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && instructions.isNotBlank()) {
                        app.skillStore.saveCustom(Skill(name, desc, instructions, "custom"))
                        showCreate = false; refresh++
                        name = ""; desc = ""; instructions = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}
