package com.ba.aiagent.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys live ONLY here. Never in plain prefs, files or logs. */
class SecureKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ba_ai_agent_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveKey(providerId: String, key: String) {
        prefs.edit().putString("key_$providerId", key).apply()
    }

    fun getKey(providerId: String): String? = prefs.getString("key_$providerId", null)

    fun hasKey(providerId: String): Boolean = !getKey(providerId).isNullOrBlank()

    fun removeKey(providerId: String) {
        prefs.edit().remove("key_$providerId").apply()
    }

    /** Last 4 chars only — never log or show the full key. */
    fun maskedKey(providerId: String): String? {
        val key = getKey(providerId) ?: return null
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "…" + key.takeLast(4)
    }
}
