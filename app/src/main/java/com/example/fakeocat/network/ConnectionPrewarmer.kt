package com.example.fakeocat.network

import android.util.Log
import com.example.fakeocat.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 连接预热器。
 *
 * 在用户选择/切换 provider 时，提前发起一个轻量级 OPTIONS 或 GET 请求，
 * 建立 TCP+TLS 连接并缓存 DNS 解析结果，使后续首次流式请求的 TTFT 大幅降低。
 *
 * 每个 provider 只预热一次，避免重复浪费。
 */
object ConnectionPrewarmer {

    private const val TAG = "ConnectionPrewarmer"

    private val warmedProviders = ConcurrentHashMap<String, AtomicBoolean>()

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

    // 用于预热的轻量级 HTTP 客户端（短超时，快速失败）
    private val warmupClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .apply {
            // 证书固定：仅在 Release 构建时启用，防止 MITM 攻击
            if (!BuildConfig.DEBUG) {
                certificatePinner(certificatePinner)
            }
        }
        .build()

    /** 所有 provider 的轻量级探测端点映射 */
    private val probeEndpoints: Map<String, (String) -> Request> = mapOf(
        "openai" to { apiKey ->
            Request.Builder()
                .url("https://api.openai.com/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .method("GET", null)
                .build()
        },
        "anthropic" to { apiKey ->
            Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .method("OPTIONS", null)
                .build()
        },
        "gemini" to { apiKey ->
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models")
                .addHeader("x-goog-api-key", apiKey)
                .method("GET", null)
                .build()
        },
        "deepseek" to { apiKey ->
            Request.Builder()
                .url("https://api.deepseek.com/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .method("GET", null)
                .build()
        },
        "grok" to { apiKey ->
            Request.Builder()
                .url("https://api.x.ai/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .method("GET", null)
                .build()
        },
        "qwen" to { apiKey ->
            Request.Builder()
                .url("https://dashscope.aliyuncs.com/compatible-mode/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .method("GET", null)
                .build()
        }
    )

    /**
     * 异步预热指定 provider 的连接。
     * 每个 provider 只执行一次实际预热，后续调用立即返回。
     */
    fun warmUp(provider: String, apiKey: String) {
        val flag = warmedProviders.getOrPut(provider) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) {
            Log.d(TAG, "Provider $provider already warmed up, skipping")
            return
        }

        val requestBuilder = probeEndpoints[provider] ?: run {
            Log.d(TAG, "No probe endpoint for $provider, skipping warmup")
            return
        }

        Thread {
            try {
                Log.d(TAG, "Warming up connection for $provider...")
                val request = requestBuilder.invoke(apiKey)
                warmupClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Warmup for $provider completed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Warmup for $provider failed (non-critical): ${e.message}")
                // 预热失败后重置标记以便下次重试
                warmedProviders[provider] = AtomicBoolean(false)
            }
        }.apply {
            isDaemon = true
            name = "prewarmer-$provider"
        }.start()
    }

    /** 重置所有预热状态（例如 API Key 变更后）。 */
    fun reset() {
        warmedProviders.clear()
    }

    /** 重置指定 provider 的预热状态。 */
    fun resetForProvider(provider: String) {
        warmedProviders.remove(provider)
    }
}
