package com.ba.aiagent.data.api

import com.ba.aiagent.domain.model.ProviderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ApiError(message: String) : Exception(message) {
    class InvalidKey : ApiError("Invalid API key (401). Check the key in Settings.")
    class NoCredits : ApiError("Insufficient credits (402). Top up the provider account.")
    class RateLimited : ApiError("Rate limited (429). Wait a little and try again.")
    class Http(val code: Int) : ApiError("Provider error (HTTP $code).")
    class Network : ApiError("No internet connection.")
    class Timeout : ApiError("Request timed out (60s). Try again.")
    class Other(msg: String) : ApiError(msg)
}

fun httpError(code: Int): ApiError = when (code) {
    401, 403 -> ApiError.InvalidKey()
    402 -> ApiError.NoCredits()
    429 -> ApiError.RateLimited()
    else -> ApiError.Http(code)
}

data class ChatMsg(val role: String, val content: String)

/**
 * One client for every provider. OpenAI-compatible providers use
 * POST /chat/completions with SSE streaming. Anthropic uses its Messages
 * API (non-streaming simplified: one request per turn, still real).
 * Gemini uses generateContent.
 */
class AiApiClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun chatStream(
        provider: ProviderDef,
        apiKey: String,
        model: String,
        messages: List<ChatMsg>,
        temperature: Double,
        maxTokens: Int,
        systemPrompt: String
    ): Flow<String> = flow {
        when (provider.kind) {
            ProviderKind.ANTHROPIC -> {
                val text = anthropicOnce(provider, apiKey, model, messages, temperature, maxTokens, systemPrompt)
                emit(text)
            }
            ProviderKind.GEMINI -> {
                val text = geminiOnce(provider, apiKey, model, messages, temperature, maxTokens, systemPrompt)
                emit(text)
            }
            ProviderKind.OPENAI_COMPATIBLE -> {
                val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
                val msgs = buildList {
                    if (systemPrompt.isNotBlank()) add(ChatMsg("system", systemPrompt))
                    addAll(messages)
                }
                val bodyJson = buildString {
                    append("{\"model\":")
                    append(json.encodeToString(String.serializer(), model))
                    append(",\"messages\":[")
                    append(msgs.joinToString(",") { m ->
                        "{\"role\":" + json.encodeToString(String.serializer(), m.role) +
                            ",\"content\":" + json.encodeToString(String.serializer(), m.content) + "}"
                    })
                    append("],\"temperature\":").append(temperature)
                    append(",\"max_tokens\":").append(maxTokens)
                    append(",\"stream\":true}")
                }
                val req = Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (provider.id == "openrouter") {
                            addHeader("HTTP-Referer", "https://ba-ai-agent.app")
                            addHeader("X-Title", "BA AI AGENT")
                        }
                    }
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                try {
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw httpError(resp.code)
                        val source = resp.body?.source() ?: return@flow
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            if (data.isEmpty()) continue
                            try {
                                val obj = json.parseToJsonElement(data).jsonObject
                                val delta = obj["choices"]?.jsonArray?.firstOrNull()
                                    ?.jsonObject?.get("delta")?.jsonObject
                                    ?.get("content")?.jsonPrimitive?.contentOrNull
                                    ?: obj["choices"]?.jsonArray?.firstOrNull()
                                        ?.jsonObject?.get("message")?.jsonObject
                                        ?.get("content")?.jsonPrimitive?.contentOrNull
                                if (delta != null) emit(delta)
                            } catch (_: Exception) { /* skip malformed chunk */ }
                        }
                    }
                } catch (e: ApiError) { throw e }
                catch (e: java.net.SocketTimeoutException) { throw ApiError.Timeout() }
                catch (e: java.io.IOException) { throw ApiError.Network() }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun anthropicOnce(
        provider: ProviderDef, apiKey: String, model: String,
        messages: List<ChatMsg>, temperature: Double, maxTokens: Int, systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val bodyJson = buildString {
            append("{\"model\":")
            append(json.encodeToString(String.serializer(), model))
            append(",\"max_tokens\":").append(maxTokens)
            append(",\"temperature\":").append(temperature)
            if (systemPrompt.isNotBlank()) {
                append(",\"system\":")
                append(json.encodeToString(String.serializer(), systemPrompt))
            }
            append(",\"messages\":[")
            append(messages.filter { it.role != "system" }.joinToString(",") { m ->
                "{\"role\":" + json.encodeToString(String.serializer(), m.role) +
                    ",\"content\":" + json.encodeToString(String.serializer(), m.content) + "}"
            })
            append("]}")
        }
        val req = Request.Builder().url(provider.baseUrl.trimEnd('/') + "/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code)
                val text = resp.body?.string() ?: ""
                val obj = json.parseToJsonElement(text).jsonObject
                return@withContext obj["content"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            }
        } catch (e: ApiError) { throw e }
        catch (e: java.net.SocketTimeoutException) { throw ApiError.Timeout() }
        catch (e: java.io.IOException) { throw ApiError.Network() }
    }

    private suspend fun geminiOnce(
        provider: ProviderDef, apiKey: String, model: String,
        messages: List<ChatMsg>, temperature: Double, maxTokens: Int, systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val contents = messages.filter { it.role != "system" }.joinToString(",") { m ->
            val role = if (m.role == "assistant") "model" else "user"
            "{\"role\":\"$role\",\"parts\":[{\"text\":" +
                json.encodeToString(String.serializer(), m.content) + "}]}"
        }
        val bodyJson = "{\"contents\":[$contents],\"generationConfig\":{\"temperature\":$temperature,\"maxOutputTokens\":$maxTokens}}" +
            if (systemPrompt.isNotBlank()) "" else ""
        val url = provider.baseUrl.trimEnd('/') + "/models/$model:generateContent?key=$apiKey"
        val req = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code)
                val text = resp.body?.string() ?: ""
                val obj = json.parseToJsonElement(text).jsonObject
                return@withContext obj["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")?.jsonObject?.get("parts")
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            }
        } catch (e: ApiError) { throw e }
        catch (e: java.net.SocketTimeoutException) { throw ApiError.Timeout() }
        catch (e: java.io.IOException) { throw ApiError.Network() }
    }

    suspend fun fetchModels(provider: ProviderDef, apiKey: String): List<ProviderModel> =
        withContext(Dispatchers.IO) {
            val url = provider.modelsUrl ?: return@withContext fallback(provider)
            val reqBuilder = Request.Builder().url(url)
                .addHeader("Content-Type", "application/json")
            if (provider.kind == ProviderKind.OPENAI_COMPATIBLE) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                if (provider.id == "openrouter") {
                    reqBuilder.addHeader("HTTP-Referer", "https://ba-ai-agent.app")
                    reqBuilder.addHeader("X-Title", "BA AI AGENT")
                }
            } else if (provider.kind == ProviderKind.GEMINI) {
                // Gemini models list needs the key as query param
                val req2 = Request.Builder().url("$url?key=$apiKey").build()
                return@withContext try {
                    http.newCall(req2).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext fallback(provider)
                        parseModels(provider, resp.body?.string() ?: "")
                    }
                } catch (_: Exception) { fallback(provider) }
            }
            try {
                http.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext fallback(provider)
                    parseModels(provider, resp.body?.string() ?: "")
                }
            } catch (_: Exception) { fallback(provider) }
        }

    private fun parseModels(provider: ProviderDef, body: String): List<ProviderModel> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val arr: JsonArray? = root["data"]?.jsonArray ?: root["models"]?.jsonArray
            val list = arr?.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull
                    ?: o["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@mapNotNull null
                val pricing = o["pricing"]?.jsonObject
                val promptPrice = pricing?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val completionPrice = pricing?.get("completion")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val ctx = o["context_length"]?.jsonPrimitive?.intOrNull
                    ?: o["input_token_limit"]?.jsonPrimitive?.intOrNull ?: 0
                ProviderModel(
                    id = id,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                    providerId = provider.id,
                    contextLength = ctx,
                    inputPricePerMillion = (promptPrice ?: 0.0) * 1_000_000,
                    outputPricePerMillion = (completionPrice ?: 0.0) * 1_000_000,
                    isFree = promptPrice == 0.0
                )
            } ?: emptyList()
            if (list.isEmpty()) fallback(provider) else list.sortedBy { it.name.lowercase() }
        } catch (_: Exception) { fallback(provider) }
    }

    private fun fallback(provider: ProviderDef): List<ProviderModel> =
        (Providers.fallbackModels[provider.id] ?: emptyList()).map {
            ProviderModel(id = it, name = it, providerId = provider.id)
        }

    /** Validate a key: OpenRouter uses /auth/key, others use a tiny models call. Returns null on success, error message on failure. */
    suspend fun testKey(provider: ProviderDef, apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            if (provider.id == "openrouter") {
                val req = Request.Builder().url("https://openrouter.ai/api/v1/auth/key")
                    .addHeader("Authorization", "Bearer $apiKey").build()
                http.newCall(req).execute().use { resp ->
                    return@withContext if (resp.isSuccessful) null else httpError(resp.code).message
                }
            }
            val models = fetchModels(provider, apiKey)
            return@withContext if (models.isNotEmpty()) null else "Could not reach the provider. Check the key."
        } catch (e: ApiError) { e.message }
        catch (_: Exception) { "Could not reach the provider. Check the key and internet." }
    }
}
