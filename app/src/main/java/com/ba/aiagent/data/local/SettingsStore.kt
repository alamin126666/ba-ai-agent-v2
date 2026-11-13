package com.ba.aiagent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ba_ai_agent_settings")

class SettingsStore(private val context: Context) {
    companion object {
        private val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        private val ACTIVE_MODEL = stringPreferencesKey("active_model")
        private val TEMPERATURE = doublePreferencesKey("temperature")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val STREAMING = booleanPreferencesKey("streaming")
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val AGENT_PROMPT = stringPreferencesKey("agent_prompt")
        private val THEME_MODE = stringPreferencesKey("theme_mode") // dark | light | system
        private val ACCENT = stringPreferencesKey("accent") // orange | violet
        private val FAVOURITES = stringSetPreferencesKey("favourites")
        private val ACTIVE_DESIGN = stringPreferencesKey("active_design")

        const val DEFAULT_SYSTEM_PROMPT =
            "You are BA AI AGENT, a helpful AI assistant. Answer clearly in Markdown. Give complete, runnable code when asked."
        const val DEFAULT_AGENT_PROMPT =
            "You are BA AI AGENT, a mobile AI coding agent like Claude Code. Plan before you act. Use tools when the user asks you to create, edit, or read files. When you need a tool, reply with ONLY one JSON tool call in a ```json block. Otherwise reply with the final answer in Markdown. Keep code complete and runnable. Never invent file contents you did not read."
    }

    val activeProvider: Flow<String> = context.dataStore.data.map { it[ACTIVE_PROVIDER] ?: "openrouter" }
    val activeModel: Flow<String> = context.dataStore.data.map { it[ACTIVE_MODEL] ?: "" }
    val temperature: Flow<Double> = context.dataStore.data.map { it[TEMPERATURE] ?: 0.7 }
    val maxTokens: Flow<Int> = context.dataStore.data.map { it[MAX_TOKENS] ?: 4096 }
    val streaming: Flow<Boolean> = context.dataStore.data.map { it[STREAMING] ?: true }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT }
    val agentPrompt: Flow<String> = context.dataStore.data.map { it[AGENT_PROMPT] ?: DEFAULT_AGENT_PROMPT }
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "dark" }
    val accent: Flow<String> = context.dataStore.data.map { it[ACCENT] ?: "orange" }
    val favourites: Flow<Set<String>> = context.dataStore.data.map { it[FAVOURITES] ?: emptySet() }
    val activeDesign: Flow<String> = context.dataStore.data.map { it[ACTIVE_DESIGN] ?: "linear" }

    suspend fun setActiveProvider(v: String) = context.dataStore.edit { it[ACTIVE_PROVIDER] = v }
    suspend fun setActiveModel(v: String) = context.dataStore.edit { it[ACTIVE_MODEL] = v }
    suspend fun setTemperature(v: Double) = context.dataStore.edit { it[TEMPERATURE] = v }
    suspend fun setMaxTokens(v: Int) = context.dataStore.edit { it[MAX_TOKENS] = v }
    suspend fun setStreaming(v: Boolean) = context.dataStore.edit { it[STREAMING] = v }
    suspend fun setSystemPrompt(v: String) = context.dataStore.edit { it[SYSTEM_PROMPT] = v }
    suspend fun setAgentPrompt(v: String) = context.dataStore.edit { it[AGENT_PROMPT] = v }
    suspend fun setThemeMode(v: String) = context.dataStore.edit { it[THEME_MODE] = v }
    suspend fun setAccent(v: String) = context.dataStore.edit { it[ACCENT] = v }
    suspend fun setActiveDesign(v: String) = context.dataStore.edit { it[ACTIVE_DESIGN] = v }

    suspend fun toggleFavourite(modelId: String) = context.dataStore.edit { prefs ->
        val cur = prefs[FAVOURITES] ?: emptySet()
        prefs[FAVOURITES] = if (cur.contains(modelId)) cur - modelId else cur + modelId
    }

    suspend fun clearFavourites() = context.dataStore.edit { it.remove(FAVOURITES) }
}
