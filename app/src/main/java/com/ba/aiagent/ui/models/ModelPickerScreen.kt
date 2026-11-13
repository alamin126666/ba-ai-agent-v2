package com.ba.aiagent.ui.models

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.LaunchedEffect
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
import com.ba.aiagent.data.api.AiApiClient
import com.ba.aiagent.data.api.Providers
import com.ba.aiagent.domain.model.ProviderModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(app: BaAiAgentApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val activeProvider by app.settingsStore.activeProvider.collectAsState(initial = "openrouter")
    val activeModel by app.settingsStore.activeModel.collectAsState(initial = "")
    val favourites by app.settingsStore.favourites.collectAsState(initial = emptySet())
    val api = remember { AiApiClient() }
    var models by remember { mutableStateOf<List<ProviderModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    var favOnly by remember { mutableStateOf(false) }

    fun load() {
        val provider = Providers.byId(activeProvider)
        val key = app.keyStore.getKey(activeProvider)
        if (key.isNullOrBlank()) {
            error = "Add your ${provider.name} API key first (Settings → Provider Hub)."
            models = emptyList()
            return
        }
        loading = true
        error = null
        scope.launch {
            models = api.fetchModels(provider, key)
            loading = false
        }
    }

    LaunchedEffect(activeProvider) { load() }

    val filtered = models.filter {
        (!freeOnly || it.isFree) && (!favOnly || favourites.contains(it.id)) &&
            (query.isBlank() || it.id.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true))
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Select Model — ${Providers.byId(activeProvider).name}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search models") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = { freeOnly = !freeOnly }) { Text(if (freeOnly) "Free only ✓" else "Free only") }
                TextButton(onClick = { favOnly = !favOnly }) { Text(if (favOnly) "Favourites ✓" else "Favourites") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { load() }) { Text("Refresh") }
            }
            if (loading) Text("Loading live model list…")
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Button(onClick = { load() }) { Text("Retry") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(filtered, key = { it.id }) { m ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeModel == m.id) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    app.settingsStore.setActiveModel(m.id)
                                    onBack()
                                }
                            }
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(m.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (m.isFree) Text("FREE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Text(m.id, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Context ${m.contextLabel}  •  In \$${m.inputPricePerMillion} / Out \$${m.outputPricePerMillion} per 1M", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { scope.launch { app.settingsStore.toggleFavourite(m.id) } }) {
                                Icon(
                                    if (favourites.contains(m.id)) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favourite"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
