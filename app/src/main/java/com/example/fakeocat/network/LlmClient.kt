package com.example.fakeocat.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LlmClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val streamClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val eventSourceFactory = EventSources.createFactory(streamClient)

    fun streamChat(
        provider: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        baseUrlOverride: String? = null
    ): Flow<String> {
        return when (provider) {
            "gemini" -> streamGemini(apiKey, model, systemPrompt, userPrompt)
            "anthropic" -> streamAnthropic(apiKey, model, systemPrompt, userPrompt)
            else -> streamOpenAICompatible(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride ?: getBaseUrl(provider))
        }
    }

    private fun getBaseUrl(provider: String): String = when (provider) {
        "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
        "grok" -> "https://api.x.ai/v1/chat/completions"
        "doubao" -> "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        "zhipu" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        "kimi" -> "https://api.moonshot.cn/v1/chat/completions"
        "minimax" -> "https://api.minimax.chat/v1/chat/completions"
        "mimo" -> "https://api.mimogpt.com/v1/chat/completions"
        "hunyuan" -> "https://api.hunyuan.cloud.tencent.com/v1/chat/completions"
        "ernie" -> "https://qianfan.baidubce.com/v2/chat/completions"
        else -> "https://api.openai.com/v1/chat/completions"
    }

    // ═══════════════════════════════════════════════════
    // 兼容 OpenAI 风格的接口（OpenAI、DeepSeek、Grok 等）
    // ═══════════════════════════════════════════════════
    private fun streamOpenAICompatible(
        provider: String,
        apiKey: String, model: String, systemPrompt: String, userPrompt: String, baseUrl: String
    ): Flow<String> = callbackFlow {
        val parser = ProviderStreamParsers.forProvider(provider)
        val receivedAny = AtomicBoolean(false)
        val timeoutJob = launch {
            delay(30_000)
            if (!receivedAny.get()) close(Exception("Request timeout"))
        }

        val json = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
            })
        }

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val eventSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                when (val chunk = parser.parse(type, data)) {
                    is ParsedSseChunk.Text -> {
                        receivedAny.set(true)
                        timeoutJob.cancel()
                        trySend(chunk.value)
                    }
                    ParsedSseChunk.Done -> close()
                    ParsedSseChunk.Ignore -> Unit
                }
            }
            override fun onFailure(es: EventSource, t: Throwable?, r: Response?) {
                timeoutJob.cancel()
                close(Exception(r?.body?.string() ?: t?.message ?: "Unknown Error"))
            }
            override fun onClosed(es: EventSource) { close() }
        })
        awaitClose {
            timeoutJob.cancel()
            eventSource.cancel()
        }
    }

    // ═══════════════════════════════════════════════════
    // 谷歌 Gemini（接口形态与 OpenAI 兼容层不同）
    // ═══════════════════════════════════════════════════
    private fun streamGemini(
        apiKey: String, model: String, systemPrompt: String, userPrompt: String
    ): Flow<String> = callbackFlow {
        val receivedAny = AtomicBoolean(false)
        val timeoutJob = launch {
            delay(30_000)
            if (!receivedAny.get()) close(Exception("Request timeout"))
        }

        val resolvedModel = resolveGeminiModel(model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:streamGenerateContent?alt=sse&key=$apiKey"

        val json = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val eventSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                when (val chunk = ProviderStreamParsers.gemini.parse(type, data)) {
                    is ParsedSseChunk.Text -> {
                        receivedAny.set(true)
                        timeoutJob.cancel()
                        trySend(chunk.value)
                    }
                    ParsedSseChunk.Done -> close()
                    ParsedSseChunk.Ignore -> Unit
                }
            }
            override fun onFailure(es: EventSource, t: Throwable?, r: Response?) {
                timeoutJob.cancel()
                val code = r?.code
                val detail = r?.body?.string() ?: t?.message ?: "Unknown Error"
                val message = when (code) {
                    429 -> "Gemini($resolvedModel) 请求失败：HTTP 429 配额不足或速率受限。$detail"
                    404 -> "Gemini($resolvedModel) 请求失败：HTTP 404 模型未找到或当前账号不可用。$detail"
                    else -> "Gemini($resolvedModel) 请求失败：${if (code != null) "HTTP $code " else ""}$detail"
                }
                close(Exception(message))
            }
            override fun onClosed(es: EventSource) { close() }
        })
        awaitClose {
            timeoutJob.cancel()
            eventSource.cancel()
        }
    }

    private fun normalizeGeminiModelName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("models/")) trimmed.removePrefix("models/") else trimmed
    }

    private fun resolveGeminiModel(requestedModel: String): String {
        return normalizeGeminiModelName(requestedModel)
    }

    private fun fetchGeminiModels(apiKey: String): List<String> {
        val endpoints = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey",
            "https://generativelanguage.googleapis.com/v1/models?key=$apiKey"
        )

        endpoints.forEach { url ->
            val models = fetchGeminiModelsFrom(url)
            if (models.isNotEmpty()) return models
        }
        return emptyList()
    }

    private fun fetchGeminiModelsFrom(url: String): List<String> {
        return try {
            val listRequest = Request.Builder().url(url).get().build()
            httpClient.newCall(listRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val models = JSONObject(body).optJSONArray("models") ?: return emptyList()
                buildSet {
                    for (i in 0 until models.length()) {
                        val modelObj = models.optJSONObject(i) ?: continue
                        val methods = modelObj.optJSONArray("supportedGenerationMethods")
                        val supportsGeneration = methods?.let { arr ->
                            (0 until arr.length()).any { index ->
                                val method = arr.optString(index)
                                method == "streamGenerateContent" || method == "generateContent"
                            }
                        } == true
                        if (!supportsGeneration) continue
                        val name = modelObj.optString("name")
                        if (name.isNotBlank()) add(normalizeGeminiModelName(name))
                    }
                }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════
    // 克劳德接口（Anthropic，请求头与请求体格式不同）
    // ═══════════════════════════════════════════════════
    private fun streamAnthropic(
        apiKey: String, model: String, systemPrompt: String, userPrompt: String
    ): Flow<String> = callbackFlow {
        val receivedAny = AtomicBoolean(false)
        val timeoutJob = launch {
            delay(30_000)
            if (!receivedAny.get()) {
                close(Exception("Request timeout"))
            }
        }

        val json = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
            })
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Accept", "text/event-stream")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val eventSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                when (val chunk = ProviderStreamParsers.anthropic.parse(type, data)) {
                    is ParsedSseChunk.Text -> {
                        receivedAny.set(true)
                        timeoutJob.cancel()
                        trySend(chunk.value)
                    }
                    ParsedSseChunk.Done -> close()
                    ParsedSseChunk.Ignore -> Unit
                }
            }
            override fun onFailure(es: EventSource, t: Throwable?, r: Response?) {
                timeoutJob.cancel()
                close(Exception(r?.body?.string() ?: t?.message ?: "Anthropic API Error"))
            }
            override fun onClosed(es: EventSource) { close() }
        })
        awaitClose {
            timeoutJob.cancel()
            eventSource.cancel()
        }
    }
}
