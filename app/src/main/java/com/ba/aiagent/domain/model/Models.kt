package com.ba.aiagent.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long = 0,
    val role: String = "user", // user | assistant | tool
    val content: String = "",
    val toolName: String? = null,
    val toolStatus: String? = null, // Running | Done | Failed
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatSession(
    val id: Long = 0,
    val title: String = "New chat",
    val providerId: String = "openrouter",
    val modelId: String = "",
    val mode: String = "chat", // chat | agent | plan
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPreview: String = ""
)

@Serializable
data class ProviderModel(
    val id: String,
    val name: String,
    val providerId: String,
    val contextLength: Int = 0,
    val inputPricePerMillion: Double = 0.0,
    val outputPricePerMillion: Double = 0.0,
    val isFree: Boolean = false,
    val isFavourite: Boolean = false
) {
    val contextLabel: String
        get() = when {
            contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M"
            contextLength >= 1_000 -> "${contextLength / 1_000}K"
            contextLength > 0 -> "$contextLength"
            else -> "—"
        }
}

@Serializable
data class Skill(
    val name: String,
    val description: String,
    val instructions: String,
    val source: String = "built-in", // built-in | custom | github
    val enabled: Boolean = true
)

@Serializable
data class DesignTheme(
    val id: String,
    val name: String,
    val background: String,
    val surface: String,
    val accent: String,
    val text: String,
    val radiusDp: Int,
    val fontFamily: String = "System"
)
