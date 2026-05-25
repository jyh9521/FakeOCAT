package com.example.fakeocat.network

import com.example.fakeocat.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 带 DNS 缓存的自定义 Dns 实现。
 * 在应用启动时预解析所有 AI provider 的 host，消除首次请求的 DNS 查询延迟。
 */
internal class CachedDns : Dns {
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        return cache.getOrPut(hostname) {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    /** 预解析 host，结果将被缓存供后续 lookup 使用。 */
    fun prelookup(vararg hostnames: String) {
        hostnames.forEach { hostname ->
            try {
                // 同步解析后放入缓存
                cache[hostname] = Dns.SYSTEM.lookup(hostname)
            } catch (_: Exception) {
                // 预热失败不影响主流程
            }
        }
    }

    companion object {
        /** 所有 AI provider 的 host 列表 */
        val ALL_PROVIDER_HOSTS = arrayOf(
            "api.openai.com",
            "api.anthropic.com",
            "generativelanguage.googleapis.com",
            "api.deepseek.com",
            "api.x.ai",
            "dashscope.aliyuncs.com",
            "open.bigmodel.cn",
            "api.moonshot.cn",
            "api.minimax.chat",
            "api.mimogpt.com",
            "api.hunyuan.cloud.tencent.com",
            "qianfan.baidubce.com"
        )
    }
}

/**
 * 低延迟的 LLM 流式请求客户端。
 *
 * 核心优化：
 * - 共享连接池（20 空闲连接、10 分钟 keep-alive）减少 TCP/TLS 握手
 * - DNS 预解析 + 缓存消除 DNS 查询延迟
 * - 原始 OkHttp source 逐行读取替代 okhttp-sse 减少回调开销
 * - 首 token 超时检测（5s），快速失败避免长时间等待
 */
class LlmClient() {

    companion object {
        private const val FIRST_TOKEN_TIMEOUT_MS = 5_000L
        /** 证书固定引脚（SHA256 哈希）。仅在 Release 构建时启用。 */
        private val certificatePinner = CertificatePinner.Builder()
            .add("generativelanguage.googleapis.com",
                "sha256/vqg5bUG+qXcqS0J4VsQyBG/rH/5mQLKLYCpFr4bebvk=")
            .add("api.openai.com",
                "sha256/rwQEJp/dzuKRR34exkV/Eg+BvIqclbrD/QqVK44O1n0=")
            .add("api.anthropic.com",
                "sha256/PLNqWhvts4aLeuvBGZ2pDdKMfEF+w24PNmK0lnIH0Jc=")
            .add("api.deepseek.com",
                "sha256/v6jE0yqApnVtkKqJ7dSnRru0HkMRhcv5JhX1Pz4/z9I=")
            .add("api.mistral.ai",
                "sha256/9RArj3lJHCZ5gMr0qDVOjZs2UJ+emZfRvhQ7v+xY/KM=")
            .add("api.x.ai",
                "sha256/3HeDDOKCxGnZy3pDdKm1lhOIG/kSaLFUjYRTCpM8lMA=")
            .build()
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val READ_TIMEOUT_SEC = 30L
        private const val WRITE_TIMEOUT_SEC = 30L
    }

    /** 共享 DNS 缓存实例，同时供外部 prelookup 调用 */
    internal val dnsCache = CachedDns()

    /** 共享连接池：20 个空闲连接，10 分钟 keep-alive */
    private val sharedConnectionPool = ConnectionPool(
        maxIdleConnections = 20,
        keepAliveDuration = 10,
        TimeUnit.MINUTES
    )

    /** 非流式 HTTP 客户端（用于 model list 等短请求） */
    private val httpClient = OkHttpClient.Builder()
        .dns(dnsCache)
        .connectionPool(sharedConnectionPool)
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            // 证书固定：仅在 Release 构建时启用，防止 MITM 攻击
            if (!BuildConfig.DEBUG) {
                certificatePinner(certificatePinner)
            }
        }
        .build()

    /** 流式 HTTP 客户端（长连接，readTimeout = 0） */
    private val streamClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .connectionPool(sharedConnectionPool)
        .build()

    init {
        // 后台异步预解析所有 provider 的 DNS，避免阻塞主线程
        Thread {
            dnsCache.prelookup(*CachedDns.ALL_PROVIDER_HOSTS)
        }.apply {
            isDaemon = true
            name = "dns-prelookup"
        }.start()
    }

    // ═══════════════════════════════════════════════════
    // 公共入口
    // ═══════════════════════════════════════════════════

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
            else -> streamOpenAICompatible(
                provider, apiKey, model, systemPrompt, userPrompt,
                baseUrlOverride ?: getBaseUrl(provider)
            )
        }
    }

    // ═══════════════════════════════════════════════════
    // 基础 URL 解析
    // ═══════════════════════════════════════════════════

    private fun getBaseUrl(provider: String): String = when (provider) {
        "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
        "grok" -> "https://api.x.ai/v1/chat/completions"
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
    // ── 使用原始 OkHttp source 逐行读取，替代 okhttp-sse
    // ═══════════════════════════════════════════════════

    private fun streamOpenAICompatible(
        provider: String,
        apiKey: String, model: String, systemPrompt: String, userPrompt: String, baseUrl: String
    ): Flow<String> = channelFlow {
        val parser = ProviderStreamParsers.forProvider(provider)

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
            .addHeader("Cache-Control", "no-cache")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        launch(Dispatchers.IO) {
        var response: Response? = null
        try {
            response = streamClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "认证失败：API Key 无效或已过期"
                    429 -> "请求过于频繁，请稍后重试"
                    500 -> "服务器内部错误，请稍后重试"
                    503 -> "服务暂时不可用，请稍后重试"
                    else -> "HTTP ${response.code}: ${response.message}"
                }
                close(Exception(errorMsg))
                return@launch
            }

                val body = response.body ?: run { close(); return@launch }
                val source = body.source()
                // 设置首 Token 超时：若在 FIRST_TOKEN_TIMEOUT_MS 内未收到有效 token，则抛超时异常
                source.timeout().timeout(FIRST_TOKEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                var hasReceivedFirstToken = false

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.trim().isEmpty()) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    when (val chunk = parser.parse(null, data)) {
                        is ParsedSseChunk.Text -> {
                            if (!hasReceivedFirstToken) {
                                hasReceivedFirstToken = true
                                // 首 Token 已到达，清除超时限制
                                source.timeout().timeout(0, TimeUnit.MILLISECONDS)
                            }
                            send(chunk.value)
                        }
                        ParsedSseChunk.Done -> {
                            close()
                            return@launch
                        }
                        ParsedSseChunk.Ignore -> Unit
                    }
                }
                close() // EOF
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
            } finally {
                response?.close()
            }
        }
    }.also {
        // 附加首 token 超时检查（通过 channelFlow 自身超时由 collect 端负责）
    }

    // ═══════════════════════════════════════════════════
    // 谷歌 Gemini（接口形态与 OpenAI 兼容层不同）
    // ═══════════════════════════════════════════════════

    private fun streamGemini(
        apiKey: String, model: String, systemPrompt: String, userPrompt: String
    ): Flow<String> = channelFlow {
        val resolvedModel = resolveGeminiModel(model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:streamGenerateContent?alt=sse"

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
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        launch(Dispatchers.IO) {
            var response: Response? = null
            try {
                response = streamClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    val detail = response.body?.string() ?: "Unknown"
                    val message = when (code) {
                        429 -> "Gemini($resolvedModel) 请求失败：HTTP 429 配额不足或速率受限。$detail"
                        404 -> "Gemini($resolvedModel) 请求失败：HTTP 404 模型未找到或当前账号不可用。$detail"
                        else -> "Gemini($resolvedModel) 请求失败：HTTP $code $detail"
                    }
                    close(Exception(message))
                    return@launch
                }

                val body = response.body ?: run { close(); return@launch }
                val source = body.source()
                // 设置首 Token 超时
                source.timeout().timeout(FIRST_TOKEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                var hasReceivedFirstToken = false

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.trim().isEmpty()) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    when (val chunk = ProviderStreamParsers.gemini.parse(null, data)) {
                        is ParsedSseChunk.Text -> {
                            if (!hasReceivedFirstToken) {
                                hasReceivedFirstToken = true
                                // 首 Token 已到达，清除超时限制
                                source.timeout().timeout(0, TimeUnit.MILLISECONDS)
                            }
                            send(chunk.value)
                        }
                        ParsedSseChunk.Done -> {
                            close()
                            return@launch
                        }
                        ParsedSseChunk.Ignore -> Unit
                    }
                }
                close() // EOF
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
            } finally {
                response?.close()
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // 克劳德接口（Anthropic，请求头与请求体格式不同）
    // ═══════════════════════════════════════════════════

    private fun streamAnthropic(
        apiKey: String, model: String, systemPrompt: String, userPrompt: String
    ): Flow<String> = channelFlow {
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
            .addHeader("Cache-Control", "no-cache")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        launch(Dispatchers.IO) {
            var response: Response? = null
            try {
                response = streamClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    close(Exception("Anthropic API Error: HTTP ${response.code}: ${response.body?.string()}"))
                    return@launch
                }

                val body = response.body ?: run { close(); return@launch }
                val source = body.source()

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.trim().isEmpty()) continue
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    when (val chunk = ProviderStreamParsers.anthropic.parse(null, data)) {
                        is ParsedSseChunk.Text -> {
                            send(chunk.value)
                        }
                        ParsedSseChunk.Done -> {
                            close()
                            return@launch
                        }
                        ParsedSseChunk.Ignore -> Unit
                    }
                }
                close() // EOF
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
            } finally {
                response?.close()
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // Gemini 模型名称辅助方法
    // ═══════════════════════════════════════════════════

    private fun normalizeGeminiModelName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("models/")) trimmed.removePrefix("models/") else trimmed
    }

    private fun resolveGeminiModel(requestedModel: String): String {
        return normalizeGeminiModelName(requestedModel)
    }
}
