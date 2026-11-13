package com.ba.aiagent.data.api

/** All providers in the Hub. `kind` decides the request format. */
enum class ProviderKind { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }

data class ProviderDef(
    val id: String,
    val name: String,
    val baseUrl: String,
    val kind: ProviderKind,
    val modelsUrl: String? = null,
    val keyHelpUrl: String,
    val keyPrefix: String = "",
    val note: String? = null
)

object Providers {
    val all: List<ProviderDef> = listOf(
        ProviderDef(
            id = "openrouter", name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://openrouter.ai/api/v1/models",
            keyHelpUrl = "https://openrouter.ai/keys", keyPrefix = "sk-or-",
            note = "Hundreds of models incl. free ones. Default provider."
        ),
        ProviderDef(
            id = "meta", name = "Meta Muse",
            baseUrl = "https://api.meta.ai/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.meta.ai/v1/models",
            keyHelpUrl = "https://dev.meta.ai", keyPrefix = "LLM_",
            note = "Muse Spark 1.3 / Muse Wonder. Paid per token. Key starts with LLM_ — copy it once, it is shown only once. Add a payment method in Billing first."
        ),
        ProviderDef(
            id = "openai", name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.openai.com/v1/models",
            keyHelpUrl = "https://platform.openai.com/api-keys", keyPrefix = "sk-"
        ),
        ProviderDef(
            id = "anthropic", name = "Anthropic Claude",
            baseUrl = "https://api.anthropic.com/v1",
            kind = ProviderKind.ANTHROPIC,
            keyHelpUrl = "https://console.anthropic.com/settings/keys", keyPrefix = "sk-ant-"
        ),
        ProviderDef(
            id = "gemini", name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            kind = ProviderKind.GEMINI,
            keyHelpUrl = "https://aistudio.google.com/app/apikey", keyPrefix = "AIza"
        ),
        ProviderDef(
            id = "groq", name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.groq.com/openai/v1/models",
            keyHelpUrl = "https://console.groq.com/keys", keyPrefix = "gsk_"
        ),
        ProviderDef(
            id = "deepseek", name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.deepseek.com/v1/models",
            keyHelpUrl = "https://platform.deepseek.com/api_keys", keyPrefix = "sk-"
        ),
        ProviderDef(
            id = "mistral", name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.mistral.ai/v1/models",
            keyHelpUrl = "https://console.mistral.ai/api-keys", keyPrefix = ""
        ),
        ProviderDef(
            id = "xai", name = "xAI Grok",
            baseUrl = "https://api.x.ai/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.x.ai/v1/models",
            keyHelpUrl = "https://console.x.ai", keyPrefix = "xai-"
        ),
        ProviderDef(
            id = "together", name = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            modelsUrl = "https://api.together.xyz/v1/models",
            keyHelpUrl = "https://api.together.ai/settings/api-keys", keyPrefix = ""
        )
    )

    fun byId(id: String): ProviderDef = all.firstOrNull { it.id == id } ?: all[0]

    /** Models that are always offered when the live list cannot be fetched. Never the only source. */
    val fallbackModels: Map<String, List<String>> = mapOf(
        "openrouter" to listOf("anthropic/claude-sonnet-4.5", "openai/gpt-5", "google/gemini-2.5-pro", "meta-llama/llama-3.3-70b-instruct:free"),
        "meta" to listOf("muse-spark-1.3", "muse-wonder-1.0", "muse-spark-1.3-contributor"),
        "openai" to listOf("gpt-5", "gpt-5-mini"),
        "anthropic" to listOf("claude-sonnet-4-5", "claude-opus-4-1"),
        "gemini" to listOf("gemini-2.5-pro", "gemini-2.5-flash"),
        "groq" to listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b"),
        "deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
        "mistral" to listOf("mistral-large-latest", "mistral-medium-latest"),
        "xai" to listOf("grok-4", "grok-4-fast"),
        "together" to listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo")
    )
}
