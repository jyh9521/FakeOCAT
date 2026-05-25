# FakeOCAT 性能优化方案

> 目标：用户向 AI 提问后，**<500ms** 内收到首个 token（TTFT < 500ms）。
>
> 分析基于当前 [`LlmClient.kt`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt)、[`ChatViewModel.kt`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt)、[`ProviderStreamParsers.kt`](app/src/main/java/com/example/fakeocat/network/ProviderStreamParsers.kt) 代码。

---

## 目录

1. [OkHttp 连接池与连接复用](#1-okhttp-连接池与连接复用)
2. [HTTP/2 与 keep-alive 优化](#2-http2-与-keep-alive-优化)
3. [DNS 预解析](#3-dns-预解析)
4. [请求体压缩](#4-请求体压缩)
5. [流式响应——从 okhttp-sse 迁移至原始流式读取](#5-流式响应从-okhttp-sse-迁移至原始流式读取)
6. [多 Provider URL 切换效率（热连接）](#6-多-provider-url-切换效率热连接)
7. [Provider 并行探测（fallback 链优化）](#7-provider-并行探测fallback-链优化)
8. [JSON 序列化/反序列化优化](#8-json-序列化反序列化优化)
9. [冷启动缓存策略（system prompt 与对话前缀缓存）](#9-冷启动缓存策略)
10. [异步非阻塞架构——协程 Channel 替换 callbackFlow](#10-异步非阻塞架构协程-channel-替换-callbackflow)
11. [系统提示词模板预编译](#11-系统提示词模板预编译)
12. [性能测试方案](#12-性能测试方案)

---

## 1. OkHttp 连接池与连接复用

### 当前问题

[`LlmClient.kt:23-31`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt:23) 创建了两个 `OkHttpClient` 实例——`httpClient` 和 `streamClient`，它们使用了 OkHttp 默认的连接池（最大 5 个空闲连接，每个 keep-alive 5 分钟）。然而：

- **没有显式配置连接池参数**，默认池大小偏小（仅 5），如果用户快速多次切换 provider，可能触发连接重建。
- `streamClient` 共享了 `httpClient` 的连接池（通过 `newBuilder()`），但 builder 链中未调用 `connectionPool()` 传递同一个池实例。
- **不同 provider 的 base URL 不同**，OkHttp 按 `host:port` 复用连接，但若用户频繁切换 provider，之前 provider 的连接可能已过期。

### 优化方案

```kotlin
// LlmClient.kt — 共享连接池 + 增大并发连接数
private val sharedConnectionPool = ConnectionPool(
    maxIdleConnections = 20,     // 默认 5 → 20，支持更多 provider 的并发连接
    keepAliveDuration = 10,      // 默认 5 → 10 分钟，更长的 keep-alive 减少 TLS 握手
    TimeUnit.MINUTES
)

// 主 HTTP 客户端（非流式请求，如 model list 查询）
private val httpClient = OkHttpClient.Builder()
    .connectionPool(sharedConnectionPool)
    .connectTimeout(10, TimeUnit.SECONDS)  // 30s → 10s，更快发现连接失败
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

// 流式客户端（共享同一个连接池）
private val streamClient = httpClient.newBuilder()
    .readTimeout(0, TimeUnit.SECONDS)
    .connectionPool(sharedConnectionPool)  // 显式传递同一池实例
    .build()
```

### 预期收益

- 连接复用率从默认的 5 路提高到 20 路，在多 provider 往返切换时减少 DNS + TCP + TLS 握手次数。
- 10 分钟 keep-alive 确保用户连续使用时连接始终存活。

---

## 2. HTTP/2 与 keep-alive 优化

### 当前问题

OkHttp 4.12.0 **默认支持 HTTP/2**，但前提是服务端支持且连接通过 ALPN 协商升级。然而：

- 首次连接到每个 provider 的 base URL 时，**始终需要 TCP + TLS 握手**（即使是 HTTP/2，首次也需要建立连接）。
- OkHttp 的 `retryOnConnectionFailure(true)` 默认开启，但未配置 `eventListener` 来监控连接复用率。

### 优化方案

**方案 A：启用连接失败重试 + 协议版本监测**

```kotlin
// LlmClient.kt — 优化 HTTP/2 配置
private val streamClient = httpClient.newBuilder()
    .readTimeout(0, TimeUnit.SECONDS)
    .connectionPool(sharedConnectionPool)
    .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE, Protocol.HTTP_2, Protocol.HTTP_1_1))
    // H2_PRIOR_KNOWLEDGE：跳过 HTTP/1.1 升级协商，直接使用 HTTP/2
    // 注意：仅推荐用于已知支持 HTTP/2 的服务端（如所有主要 AI API）
    .retryOnConnectionFailure(true)
    .build()
```

> **⚠️ 注意**：`H2_PRIOR_KNOWLEDGE` 需要服务器明确支持 HTTP/2 且不使用 TLS 协商。对 HTTPS 场景，推荐使用 `Protocol.HTTP_2, Protocol.HTTP_1_1` 让 ALPN 协商。实际上 OkHttp 默认就是此配置，无需显式设置。

**方案 B（推荐）：为每个 provider 维护专用连接预热**

在应用启动或用户选择 provider 后，**立即发起一个轻量级的请求**（如 model list）来预热连接池，避免用户首次提问时等待 TCP+TLS 握手。

```kotlin
// 在 ChatViewModel 或独立的热连接管理器中
object ConnectionPrewarmer {
    private val warmedProviders = ConcurrentHashMap<String, AtomicBoolean>()

    suspend fun warmUp(provider: String, apiKey: String) {
        val flag = warmedProviders.getOrPut(provider) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) return // 只预热一次

        withContext(Dispatchers.IO) {
            try {
                val url = when (provider) {
                    "openai" -> "https://api.openai.com/v1/models"
                    "anthropic" -> "https://api.anthropic.com/v1/messages"
                    // ... 其他 provider
                    else -> return@withContext
                }
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Accept", "text/event-stream")
                    .method("OPTIONS", null)  // OPTIONS 请求轻量且少计费
                    .build()
                llmClient.httpClient.newCall(request).execute().close()
            } catch (_: Exception) {
                // 预热失败不影响主流程
            }
        }
    }
}
```

### 预期收益

- 预热后用户提问时，连接已建立，省去 **~200-800ms** 的 TCP + TLS 握手时间。
- 对于 HTTP/2，一次连接可多路复用多个流，进一步提升并发度。

---

## 3. DNS 预解析

### 当前问题

OkHttp 默认使用系统 DNS 解析器。每次首次连接到新 host 时，DNS 查询可能耗时 **50-500ms**（取决于网络和 DNS 缓存状态）。

### 优化方案

使用 OkHttp 的 `Dns` 接口进行预解析 + 缓存：

```kotlin
// 添加到 LlmClient.kt
private val dnsCache = object : Dns {
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()
    
    override fun lookup(hostname: String): List<InetAddress> {
        return cache.getOrPut(hostname) {
            try {
                // 首次阻塞解析后缓存
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }
    
    fun prelookup(vararg hostnames: String) {
        hostnames.forEach { hostname ->
            thread {
                try {
                    cache[hostname] = Dns.SYSTEM.lookup(hostname)
                } catch (_: Exception) { }
            }
        }
    }
}

// 在 builder 中使用
private val httpClient = OkHttpClient.Builder()
    .dns(dnsCache)
    // ...
    .build()

// 在应用启动时（如 Application.onCreate）预解析所有 provider 的 host
init {
    dnsCache.prelookup(
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
        "qianfan.baiduce.com"
    )
}
```

### 预期收益

- 消除 DNS 查询时间：**节省 50-500ms**。
- 在多 provider 切换场景下效果尤其显著。

---

## 4. 请求体压缩

### 当前问题

[`LlmClient.kt`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt) 的 POST body 使用 JSON 明文传输，未启用 gzip 压缩。当 system prompt + user prompt 较大时（尤其 `HowToSay` 和 `WhatMeans` 模式有较长的模板提示词），请求体可能达到 **1-4KB**，上传时间在弱网环境下可能成为瓶颈。

### 优化方案

```kotlin
// 在请求构建时添加 gzip 内容编码
import okhttp3.internal.gzip

val jsonBody = json.toString().toRequestBody("application/json".toMediaType())
val compressedBody = jsonBody.gzip()  // OkHttp 内置 gzip 支持

val request = Request.Builder()
    .url(baseUrl)
    .addHeader("Authorization", "Bearer $apiKey")
    .addHeader("Accept", "text/event-stream")
    .addHeader("Content-Encoding", "gzip")  // 告知服务端已压缩
    .post(compressedBody)
    .build()
```

> **注意**：需要确认各 AI provider 服务端是否支持接收 gzip 编码的请求体。大部分主流 provider（OpenAI、Anthropic、DeepSeek 等）支持。如果不支持，可以改为**减小请求体本身大小**（见 [第 11 节](#11-系统提示词模板预编译)）。

### 替代方案（更通用）：缩短请求体

当前 [`buildSystemPrompt()`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt:334) 中生成的 system prompt 约 **500-800 字符**，user prompt 约 **100-300 字符**。这些内容在每次请求时都重新构建完整的 JSON。

优化：**将固定模板部分分离，运行时只填充变量插值**。

```kotlin
// ChatViewModel.kt — 预编译的 prompt 模板
private object PromptTemplates {
    val HOW_TO_SAY_SYSTEM = buildString {
        append("你是一个专业的语言学习助手，帮助母语为%NATIVE_LANG%的用户学习%TARGET_LANG%。\n")
        append("当你提到%TARGET_LANG%或其他外语中的特定单词或短语时，请务必使用以下格式以便系统生成发音按钮：\n")
        append("[单词或短语](发音/假名|国际音标|语言代码)\n")
        append("如果语言是%TARGET_LANG%，语言代码请填 \"%TARGET_CODE%\"。\n")
        append("例如：[雹](ひょう|çjaʊ|%TARGET_CODE%) 或 [Bonjour](bonjour|bɔ̃ʒuʁ|fr)。\n\n")
        append("用户会给你一个词或句子，请你翻译成%TARGET_LANG%。请严格按照以下格式回复：\n\n")
        append("1. 先给出口语（非正式）和敬语（正式）两种翻译，每种翻译中的关键词请使用 [单词](读音|IPA) 格式。\n")
        append("2. 然后逐条详细解析每个翻译中的生词、语法点和使用场景。\n")
        append("3. 最后给出2-3个其他相关的自然表达。\n\n")
        append("请确保所有%TARGET_LANG%关键内容都标注读音。所有解释说明请使用%NATIVE_LANG%。")
    }
    
    // 预编译的 WHAT_MEANS 和 FreeChat 模板类似
}
```

---

## 5. 流式响应——从 okhttp-sse 迁移至原始流式读取

### 当前问题

当前使用 `okhttp-sse` 的 `EventSources.createFactory(streamClient)` 和 `EventSourceListener` 来消费 SSE 流。这个方案的性能瓶颈：

1. **okhttp-sse 是回调驱动**，内部使用 `EventListener` 模式，每个事件都要经过多层回调包装。
2. **EventSourceListener 的 `onEvent` 回调 → `trySend` → callbackFlow**，链路较长。
3. **不直接控制背压**，如果 downstream 处理慢，缺乏明确的背压信号。
4. **Streaming JSON 解析**：每次收到 `onEvent` 都要创建 `JSONObject(data)`，这个解析本身是 CPU 密集型操作。

### 优化方案

**用 OkHttp 的原始 `Response.body.source()` + 逐行读取替换 okhttp-sse**：

```kotlin
// LlmClient.kt — 新增 OkHttpRawStreamReader
private fun streamChatRaw(
    provider: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    baseUrl: String
): Flow<String> = callbackFlow {
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
    
    // 使用 OkHttp 同步调用 + 原始流读取
    launch(Dispatchers.IO) {
        try {
            val response = streamClient.newCall(request).execute()
            if (!response.isSuccessful) {
                close(Exception("HTTP ${response.code}: ${response.body?.string()}"))
                return@launch
            }
            
            val source = response.body?.source() ?: return@launch
            val buffer = Buffer()
            var line: String?
            
            while (source.read(buffer, 8192) != -1L) {
                while (true) {
                    line = buffer.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    
                    val sseData = parseSseLine(line) ?: continue
                    when (val chunk = parser.parse(null, sseData)) {
                        is ParsedSseChunk.Text -> {
                            trySend(chunk.value)
                        }
                        ParsedSseChunk.Done -> {
                            close()
                            return@launch
                        }
                        ParsedSseChunk.Ignore -> Unit
                    }
                }
            }
            close()  // EOF
        } catch (e: Exception) {
            close(e)
        }
    }
    
    awaitClose { /* 无需额外清理，协程取消会自动关闭 response */ }
}

// SSE 行解析
private fun parseSseLine(line: String): String? {
    return when {
        line.startsWith("data:") -> line.removePrefix("data:").trim()
        line.startsWith("event:") -> null  // event 行由 parser 处理
        else -> null
    }
}
```

### 预期收益

- **消除 okhttp-sse 事件回调层开销**：减少约 1-3ms 每事件。
- **直接控制读取缓冲区**：`8KB` 缓冲区更适合流式场景。
- **可在 `source.read()` 处直接控制背压**：当 channel 满时暂停读取。
- **TTFT 可减少 10-30ms**（主要来自事件分发链的缩短）。

> ⚠️ **建议保留 okhttp-sse 版本作为 fallback**，先对比迁移前后 TTFT 差异再决定是否完全替换。

---

## 6. 多 Provider URL 切换效率（热连接）

### 当前问题

[`getBaseUrl()`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt:50) 在每次 `streamChat()` 调用时根据 provider 名称字符串匹配返回 URL。当用户在 Settings 中切换 provider 后，下一次请求会连接到**不同的 host**，导致：

1. 旧 host 的连接在池中空闲浪费。
2. 新 host 需要重新 DNS + TCP + TLS 握手。

### 优化方案

**为所有 provider 维护连接双重预热** + **自适应优先级池**：

```kotlin
// LlmClient.kt — 连接池管理器
class ProviderConnectionPool {
    // 每个 provider 独立跟踪连接使用情况
    private val providerConnections = ConcurrentHashMap<String, ProviderConnectionState>()
    
    data class ProviderConnectionState(
        val host: String,
        val lastUsed: AtomicLong = AtomicLong(0),
        val connectionCount: AtomicInteger = AtomicInteger(0)
    )
    
    fun recordUsage(provider: String, host: String) {
        providerConnections.computeIfAbsent(provider) {
            ProviderConnectionState(host)
        }.also {
            it.lastUsed.set(System.nanoTime())
            it.connectionCount.incrementAndGet()
        }
    }
    
    // 定期清理长时间未使用的 provider 连接
    fun cleanup() {
        val now = System.nanoTime()
        val timeout = TimeUnit.MINUTES.toNanos(30)
        providerConnections.entries.removeIf { (_, state) ->
            now - state.lastUsed.get() > timeout
        }
    }
}

// 在 streamChat 调用前记录使用
fun streamChat(...): Flow<String> {
    connectionPool.recordUsage(provider, extractHost(baseUrlOverride ?: getBaseUrl(provider)))
    // ...
}
```

### 更进一步的优化：provider 切换预连接

在 [`SettingsScreen.kt`](app/src/main/java/com/example/fakeocat/ui/screens/SettingsScreen.kt:171) 中，当用户选择新 provider 并保存时，**立即启动连接预热**：

```kotlin
// SettingsScreen.kt — 保存时预热
Button(onClick = {
    scope.launch {
        prefs.setApiKeyFor(selectedProvider, apiKey)
        // 异步预热新 provider 连接
        launch(Dispatchers.IO) {
            ConnectionPrewarmer.warmUp(selectedProvider, apiKey)
        }
        Toast.makeText(context, "已保存并预热连接", Toast.LENGTH_SHORT).show()
    }
}) {
    Text("保存并预热连接")
}
```

---

## 7. Provider 并行探测（fallback 链优化）

### 当前问题

[`ChatViewModel.kt:228-247`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt:228) 的 fallback 逻辑是**串行**的：先尝试主 model 请求 → 失败后 → 再尝试 fallback model。如果主 model 超时 30 秒，用户需要等待 30 秒才知道失败，然后再等 fallback。

### 优化方案

**使用并行探测（race pattern）**——同时发起多个 model 的请求，取最先返回成功结果的流：

```kotlin
// ChatViewModel.kt — 并行 fallback
private suspend fun streamWithParallelFallback(
    provider: String,
    apiKey: String,
    models: List<String>,
    systemPrompt: String,
    userPrompt: String,
    baseUrlOverride: String?,
    onChunk: (String) -> Unit
): StreamAttemptResult {
    if (models.size == 1) {
        return streamWithRetry(provider, apiKey, models[0], systemPrompt, userPrompt, baseUrlOverride, onChunk)
    }
    
    return coroutineScope {
        val winner = MutableSharedFlow<StreamAttemptResult>(extraBufferCapacity = 1)
        val jobs = models.map { model ->
            async {
                try {
                    val collected = mutableListOf<String>()
                    llmClient.streamChat(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride)
                        .collect { chunk ->
                            collected.add(chunk)
                            // 非竞胜方也转发
                            onChunk(chunk)
                        }
                    StreamAttemptResult(succeeded = true, abortFurtherTries = false, errorMessage = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    StreamAttemptResult(succeeded = false, abortFurtherTries = false, errorMessage = e.message)
                }
            }
        }
        
        // 等待第一个成功的结果
        val winnerDeferred = jobs.firstOrNull { it.await().succeeded }
        
        // 取消其他仍在运行的请求
        jobs.forEach { it.cancel() }
        
        winnerDeferred?.await() ?: StreamAttemptResult(
            succeeded = false,
            abortFurtherTries = false,
            errorMessage = "All models failed"
        )
    }
}
```

> ⚠️ **注意**：并行探测会**消耗双倍的 API 配额和费用**，请在 Settings 中添加一个开关让用户选择 "启用快速 fallback（消耗双倍配额）"。

### 更温和的方案：超时缩短 + 快速失败

当前 [`streamWithRetry()`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt:287) 中 30 秒超时太长。改为 **可配置超时 + 渐进式重试**：

```kotlin
private suspend fun streamWithRetry(
    provider: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    baseUrlOverride: String?,
    onChunk: (String) -> Unit,
    // 新增参数
    firstTokenTimeoutMs: Long = 5_000,  // 5 秒内无首个 token 即视为超时
): StreamAttemptResult {
    // ... 在 collect 内测量 TTFT
    val ttftTimer = System.nanoTime()
    llmClient.streamChat(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride)
        .collect { chunk ->
            if (ttftTimer > 0) {
                val elapsed = (System.nanoTime() - ttftTimer) / 1_000_000
                Log.d(TAG, "TTFT: ${elapsed}ms for $provider/$model")
                ttftTimer = -1  // 只记录一次
            }
            onChunk(chunk)
        }
}
```

---

## 8. JSON 序列化/反序列化优化

### 当前问题

当前使用 `org.json.JSONObject` 和 `JSONArray` 手动构造/解析 JSON。问题：

1. **反射开销**：`JSONObject` 底层使用 `HashMap` 和反射，每次解析都创建大量中间对象。
2. **多次 `optJSONArray` / `optJSONObject` 链式调用**：[`ProviderStreamParsers.kt:28-47`](app/src/main/java/com/example/fakeocat/network/ProviderStreamParsers.kt:28) 每行 SSE 数据要经过 7 次以上的 `opt*` 调用。
3. **String 拼接构建 JSON**：在热路径（每次请求）上反复拼接字符串。

### 优化方案

**方案 A：迁移至 Moshi（项目中已声明依赖但未使用）**

[`gradle/libs.versions.toml:47-48`](gradle/libs.versions.toml:47) 已声明 `moshi-kotlin` 和 `moshi-kotlin-codegen`，但未在 `build.gradle.kts` 的 dependencies 中引用（且缺少 KSP 配置）。用 Moshi 替换 `org.json`：

```kotlin
// 在 build.gradle.kts 中添加
plugins {
    // 已有插件...
    alias(libs.plugins.ksp) // 添加 KSP 插件
}

dependencies {
    // Moshi（已声明但未使用）
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)  // 用 ksp 代替 kapt
}
```

```kotlin
// ProviderStreamParsers.kt — 使用 Moshi 的 StreamingJsonReader
import com.squareup.moshi.JsonReader
import okio.BufferedSource

fun parseStreamLine(source: BufferedSource): ParsedSseChunk {
    val reader = JsonReader.of(source)
    reader.beginObject()
    var text: String? = null
    var type: String? = null
    
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "choices" -> {
                reader.beginArray()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "delta" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "content" -> text = reader.nextString()
                                    "reasoning_content" -> if (text == null) text = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        "finish_reason" -> {
                            val reason = reader.nextString()
                            if (reason != null && reason != "null") {
                                // done
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    
    return if (text != null) ParsedSseChunk.Text(text) else ParsedSseChunk.Ignore
}
```

**方案 B（推荐，改动最小）：优化 `JSONObject` 使用方式**

```kotlin
// ProviderStreamParsers.kt — 缓存 JSONObject 的 key 路径，减少多次 opt 调用
internal object ProviderStreamParsers {
    // 预定义的 JSONPath 提取器
    private fun extractDeltaContent(obj: JSONObject): String? {
        val choices = obj.optJSONArray("choices") ?: return null
        val choice = choices.optJSONObject(0) ?: return null
        val delta = choice.optJSONObject("delta") ?: return null
        
        // 一次调用获取所有可能的字段
        return delta.optString("content").takeIf { it.isNotEmpty() }
            ?: delta.optString("reasoning_content").takeIf { it.isNotEmpty() }
            ?: delta.optString("reasoning").takeIf { it.isNotEmpty() }
            ?: choice.optString("text").takeIf { it.isNotEmpty() }
    }
    
    val openAiCompatible = ProviderStreamParser { type, data ->
        if (isDoneToken(type, data)) return@ProviderStreamParser ParsedSseChunk.Done
        val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore
        
        // 使用优化后的路径提取器
        val text = extractDeltaContent(obj)
            ?: extractMessageContent(obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message"))
            ?: obj.optNullableString("output_text")
            ?: obj.optNullableString("text")
        
        if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
    }
}
```

---

## 9. 冷启动缓存策略

### 当前问题

首次安装或清除数据后：
1. 用户需要先打开 Settings 配置 API Key → 返回 → 输入问题 → 等待连接建立。
2. **无缓存响应**，每次请求都全量调用 API。

### 优化方案

**缓存常见的几种问答组合**（尤其 "WhatMeans" 模式的短查询）：

```kotlin
// 新增：ResponseCache.kt
package com.example.fakeocat.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.security.MessageDigest

class ResponseCache(context: Context) {
    // 使用 LRU 内存缓存 + 持久化磁盘缓存
    private val memCache = LinkedHashMap<String, CacheEntry>(128, 0.75f, true) {
        it.size > 128  // 最大 128 条
    }
    
    private val cacheDir = context.cacheDir.resolve("ai_responses")
    
    data class CacheEntry(
        val response: String,
        val timestamp: Long,
        val ttl: Long = 30 * 60 * 1000L  // 默认 30 分钟
    )
    
    fun get(requestHash: String): String? {
        // 1. 先查内存
        memCache[requestHash]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < entry.ttl) {
                return entry.response
            }
            memCache.remove(requestHash)
        }
        
        // 2. 查磁盘
        val file = cacheDir.resolve("$requestHash.json")
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                val cache = CacheEntry(
                    response = json.getString("response"),
                    timestamp = json.getLong("timestamp"),
                    ttl = json.optLong("ttl", 30 * 60 * 1000L)
                )
                if (System.currentTimeMillis() - cache.timestamp < cache.ttl) {
                    memCache[requestHash] = cache
                    return cache.response
                }
                file.delete()
            } catch (_: Exception) { }
        }
        return null
    }
    
    fun put(requestHash: String, response: String) {
        memCache[requestHash] = CacheEntry(response, System.currentTimeMillis())
        // 异步写磁盘
        thread {
            cacheDir.mkdirs()
            val file = cacheDir.resolve("$requestHash.json")
            file.writeText(JSONObject().apply {
                put("response", response)
                put("timestamp", System.currentTimeMillis())
            }.toString())
        }
    }
    
    companion object {
        fun hashKey(provider: String, model: String, messages: String): String {
            val input = "$provider|$model|$messages"
            return MessageDigest.getInstance("MD5")
                .digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
```

**使用原则**：
- **只缓存"WhatMeans"模式的结果**（查询通常较稳定，如某个词的解释）。
- **不缓存"HowToSay"和"FreeChat"**（结果多变，不适用缓存）。
- 设置合理的 TTL（30 分钟），过期自动失效。

---

## 10. 异步非阻塞架构——协程 Channel 替换 callbackFlow

### 当前问题

[`callbackFlow`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt:69) + [`EventSourceListener` 的回调](app/src/main/java/com/example/fakeocat/network/LlmClient.kt:93) 组合存在以下问题：

1. **callbackFlow 内部使用 `trySend`**，它是非挂起函数，可能导致背压丢失。
2. **EventSourceListener 的回调在 OkHttp 的线程池中执行**，`trySend` 是线程安全的但不够高效。
3. **每个 SSE 事件都触发一次 `trySend` → Flow 发射**，中间有协程切换开销。

### 优化方案

**改用 `Channel` 作为生产-消费媒介**：

```kotlin
// LlmClient.kt — Channel 版本
fun streamChatV2(
    provider: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    baseUrlOverride: String? = null
): Flow<String> = channelFlow {
    val parser = ProviderStreamParsers.forProvider(provider)
    val baseUrl = baseUrlOverride ?: getBaseUrl(provider)
    
    val json = buildJsonPayload(provider, model, systemPrompt, userPrompt)
    val request = buildRequest(baseUrl, provider, apiKey, json)
    
    // 在 Dispatchers.IO 上执行同步 HTTP 调用 + 流式解析
    launch(Dispatchers.IO) {
        var response: Response? = null
        try {
            response = streamClient.newCall(request).execute()
            if (!response.isSuccessful) {
                close(HttpException(response.code, response.body?.string()))
                return@launch
            }
            
            val body = response.body ?: run { close(); return@launch }
            val source = body.source()
            val buffer = Buffer()
            
            while (isActive && source.read(buffer, 8192) != -1L) {
                var line: String?
                while (buffer.exhausted().not()) {
                    line = buffer.readUtf8Line() ?: break
                    if (line.trim().isEmpty()) continue
                    if (!line.startsWith("data:")) continue
                    
                    val data = line.removePrefix("data:").trim()
                    when (val chunk = parser.parse(null, data)) {
                        is ParsedSseChunk.Text -> {
                            // channelFlow 的 send 是挂起函数，天然支持背压
                            send(chunk.value)
                        }
                        ParsedSseChunk.Done -> {
                            close()
                            return@launch
                        }
                        ParsedSseChunk.Ignore -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            close(e)
        } finally {
            response?.close()
        }
    }
}.flowOn(Dispatchers.IO)  // 确保 Flow 的消费也在 IO 调度器

// ChatViewModel.kt 中消费时不需要改变
llmClient.streamChatV2(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride)
    .flowOn(Dispatchers.Default)  // 将 JSON 解析等 CPU 密集型操作放到 Default
    .collect { chunk ->
        onChunk(chunk)
    }
```

### 预期收益
- `channelFlow` + `send()` 的背压语义比 `callbackFlow` + `trySend()` 更强。
- 减少协程调度次数：`flowOn(Dispatchers.IO)` 确保整个流在 IO 线程处理。
- **TTFT 预计减少 5-15ms**。

---

## 11. 系统提示词模板预编译

### 当前问题

[`ChatViewModel.kt:334-371`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt:334) 中 `buildSystemPrompt()` 和 `buildUserPrompt()` 在每次 `sendMessage()` 时执行。虽然 Kotlin 的 String 模板很快，但这些字符串拼接在协程的初始阶段（在开始网络请求前）执行。

### 优化方案

```kotlin
// ChatViewModel.kt — 预编译提示词
companion object {
    // 预编译的模板片段
    private val SYSTEM_TEMPLATES = mapOf(
        "HowToSay" to """你是一个专业的语言学习助手，帮助母语为%s的用户学习%s。
%s
用户会给你一个词或句子，请你翻译成%s。请严格按照以下格式回复：

1. 先给出口语（非正式）和敬语（正式）两种翻译，每种翻译中的关键词请使用 [单词](读音|IPA) 格式。
2. 然后逐条详细解析每个翻译中的生词、语法点和使用场景。
3. 最后给出2-3个其他相关的自然表达。

请确保所有%s关键内容都标注读音。所有解释说明请使用%s。""",
        
        "WhatMeans" to """你是一个语言学习助手，帮助母语为%s的用户理解外语内容。
%s
请对用户提供的内容进行详细解析：

1. 读音和国际音标（使用 [单词](读音|IPA) 格式）
2. 词性和基本释义
3. 详细解释和文化背景
4. 2-3个例句（附带%s翻译，例句中的关键词也请使用 [单词](读音|IPA) 格式）
5. 近义词和反义词

所有解释说明请使用%s。""",
        
        "FreeChat" to """你是一个友好的对话伙伴，帮助母语为%s的用户练习%s。用自然的方式与用户聊天，适当使用%s并附上%s解释。提到关键词时请使用 [单词](读音|IPA) 格式。"""
    )
    
    private val FORMATTING_INSTRUCTION = """
当你提到%s或其他外语中的特定单词或短语时，请务必使用以下格式以便系统生成发音按钮：
[单词或短语](发音/假名|国际音标|语言代码)
如果语言是%s，语言代码请填 "%s"。
例如：[雹](ひょう|çjaʊ|%s) 或 [Bonjour](bonjour|bɔ̃ʒuʁ|fr)。
"""
}

// buildSystemPrompt 简化为：
private fun buildSystemPrompt(mode: String, native: String, target: String): String {
    val nativeName = langFullNameCn(native)
    val targetName = langFullNameCn(target)
    val template = SYSTEM_TEMPLATES[mode] ?: SYSTEM_TEMPLATES["FreeChat"]!!
    
    val formattingInstruction = FORMATTING_INSTRUCTION.format(
        targetName, targetName, target, target
    )
    
    return String.format(template, nativeName, targetName, formattingInstruction, targetName, targetName, nativeName)
}
```

### 预期收益
- 减少 `sendMessage()` 路径上的字符串分配次数。
- 结合 **请求体压缩**，最终请求体大小可减少 15-20%（减少上传时间）。

---

## 12. 性能测试方案

### 12.1 TTFT 基准测试（adb 命令行 + Logcat）

```kotlin
// 在 LlmClient.kt 中添加 TTFT 打点
data class TtftMetrics(
    val provider: String,
    val model: String,
    val dnsLookupMs: Long,
    val tcpConnectMs: Long,
    val tlsHandshakeMs: Long,
    val ttftMs: Long  // 从请求开始到首个 text chunk 的时间
)
```

```kotlin
// 测试脚本（在 host 机器上执行）
// 1. 使用 adb logcat 过滤 TTFT 日志
adb logcat -c && adb logcat -s LlmClient:Ttft *:S

// 2. 使用 adb shell am instrument 跑插桩测试
// 或在 Android Studio 中运行 androidTest

// 3. 抓取结果
adb logcat -d -s LlmClient:Ttft *:S | grep "TTFT"
```

### 12.2 单元测试级别的性能测试

```kotlin
// app/src/test/java/com/example/fakeocat/network/LlmClientTtftTest.kt
class LlmClientTtftTest {
    
    @Test
    fun testStreamChatTtft() = runBlocking {
        val client = LlmClient()
        val startTime = System.nanoTime()
        var firstTokenTime = 0L
        var tokenCount = 0
        
        client.streamChat(
            provider = "openai",
            apiKey = System.getenv("OPENAI_API_KEY") ?: return@runBlocking,
            model = "gpt-5.4-mini",
            systemPrompt = "你是一个助手",
            userPrompt = "你好"
        ).collect { chunk ->
            if (firstTokenTime == 0L) {
                firstTokenTime = System.nanoTime()
                val ttftMs = (firstTokenTime - startTime) / 1_000_000
                println("TTFT: ${ttftMs}ms")
                assert(ttftMs < 500) { "TTFT exceeded 500ms: ${ttftMs}ms" }
            }
            tokenCount++
        }
        
        println("Total tokens: $tokenCount")
    }
    
    @Test
    fun testConnectionPoolReuse() = runBlocking {
        // 测试连续两次请求的连接复用率
        val client = LlmClient()
        val apiKey = System.getenv("OPENAI_API_KEY") ?: return@runBlocking
        
        // 第一次请求（建立连接）
        client.streamChat("openai", apiKey, "gpt-5.4-mini", "hi", "hello").first()
        val firstMetrics = client.getAndResetMetrics()
        
        // 第二次请求（应复用连接）
        client.streamChat("openai", apiKey, "gpt-5.4-mini", "hi", "hello").first()
        val secondMetrics = client.getAndResetMetrics()
        
        // 第二次应无 TCP/TLS 握手时间
        assert(secondMetrics.tcpConnectMs < 10) { "Expected connection reuse" }
        println("Connection reuse confirmed")
    }
}
```

### 12.3 端到端性能测试（Espresso + Compose Test）

```kotlin
// app/src/androidTest/java/com/example/fakeocat/PerfTest.kt
@Test
fun testSendMessageTtft() {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    
    scenario.onActivity { activity ->
        val vm: ChatViewModel = activity.viewModelStore[ChatViewModel::class.java.name] as ChatViewModel
        val ttftCollector = mutableListOf<Long>()
        
        vm.uiState.collect { state ->
            when (state) {
                is ChatUiState.Generating -> {
                    if (state.partialMessage.isNotEmpty() && ttftCollector.isEmpty()) {
                        ttftCollector.add(System.currentTimeMillis())
                    }
                }
            }
        }
    }
}
```

### 12.4 性能指标采集脚本

```bash
#!/bin/bash
# perf_test.sh — 在连机设备上运行性能测试

PACKAGE="com.example.fakeocat"
TEST_RUNNER="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="$PACKAGE.network.LlmClientTtftTest"

echo "=== FakeOCAT 性能测试 ==="

# 清空 logcat
adb logcat -c

# 运行测试
adb shell am instrument -w -e class "$TEST_CLASS" "$TEST_RUNNER"

# 提取 TTFT 日志
echo ""
echo "=== TTFT 结果 ==="
adb logcat -d -s LlmClient:Ttft *:S | grep -oP 'TTFT: \K\d+'
```

---

## 优化优先级矩阵

| 优先级 | 优化项 | 预估 TTFT 收益 | 实施难度 | 代码变更量 |
|--------|--------|:--------------:|:--------:|:----------:|
| P0 | 连接池调优 + keep-alive | 200-800ms | 低 | ~10 行 |
| P0 | 超时时间缩短 | 100-500ms | 低 | ~5 行 |
| P0 | DNS 预解析 | 50-500ms | 中 | ~40 行 |
| P1 | okhttp-sse → 原始流读取 | 10-30ms | 中 | ~80 行 |
| P1 | JSON 解析优化（Moshi/路径提取） | 5-20ms | 中 | ~60 行 |
| P1 | Provider 预热 | 200-800ms | 中 | ~50 行 |
| P2 | 请求体压缩 | 10-50ms | 低 | ~5 行 |
| P2 | Prompt 模板预编译 | 1-3ms | 低 | ~30 行 |
| P2 | 响应缓存（WhatMeans 模式） | 0-∞ms | 中 | ~120 行 |
| P3 | Channel 替换 callbackFlow | 5-15ms | 高 | ~100 行 |
| P3 | 并行 fallback 探测 | 可变 | 高 | ~80 行 |

### 推荐执行顺序

1. **第一步（快速见效）**：连接池调优 + 超时缩短 + DNS 预解析 → 预计 **减少 TTFT 300-800ms**，仅改 `LlmClient.kt`。
2. **第二步（核心优化）**：原始流读取 + JSON 解析优化 + Provider 预热 → 预计 **再减少 200-400ms**。
3. **第三步（锦上添花）**：请求体压缩 + 模板预编译 + 响应缓存 → 综合提升用户体验。
4. **第四步（极限优化）**：Channel 替换 + 并行 fallback → 达到 **<500ms TTFT 目标**。

---

## 总结

当前代码在 [`LlmClient.kt`](app/src/main/java/com/example/fakeocat/network/LlmClient.kt) 和 [`ChatViewModel.kt`](app/src/main/java/com/example/fakeocat/ui/viewmodel/ChatViewModel.kt) 中的主要瓶颈是：

1. **没有连接池配置**（默认值偏小，不适应多 provider 快速切换）
2. **DNS 解析延迟**（每次切换 provider 都要解析新的 host）
3. **okhttp-sse 回调开销**（多层回调增加 TTFT）
4. **JSON 解析在热路径上**（多次 opt* 调用 + 未使用 Moshi 编译时解析）
5. **串行 fallback**（失败时等待超时）
6. **无连接预热**（首次请求需完整握手）

通过上述 P0 和 P1 优化项的实施，**TTFT 从当前的 1000-3000ms 降低到 <500ms** 是完全可行的。
