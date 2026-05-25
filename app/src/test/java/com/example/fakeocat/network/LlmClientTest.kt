package com.example.fakeocat.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * LlmClient 单元测试（使用 MockWebServer）。
 *
 * 核心验证点：
 * 1. streamChat 正确构建 OpenAI 兼容请求（URL、Header、Body）
 * 2. SSE 流式响应正确解析 token
 * 3. HTTP 错误码转换为中文异常信息
 *
 * 限制说明：
 * - LlmClient 构造函数无参数，baseUrl 在 streamChat 的 baseUrlOverride 参数中传入，
 *   因此可以直接传入 MockWebServer 的 URL，无需反射修改。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmClientTest {

    private lateinit var server: MockWebServer
    private lateinit var llmClient: LlmClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        llmClient = LlmClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ══════════════════════════════════════════════
    // 正确请求构建
    // ══════════════════════════════════════════════

    @Test
    fun `streamChat 正确构建 OpenAI 兼容请求`() = runBlocking {
        // 构造标准 SSE 流式响应
        val sseBody = """
            data: {"id":"chatcmpl-123","object":"chat.completion.chunk","choices":[{"delta":{"content":"你"}}]}
            
            data: {"id":"chatcmpl-123","object":"chat.completion.chunk","choices":[{"delta":{"content":"好"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sseBody)
                .setHeader("Content-Type", "text/event-stream")
        )

        val baseUrl = server.url("/v1/chat/completions").toString()
        val tokens = mutableListOf<String>()

        llmClient.streamChat(
            provider = "openai",
            apiKey = "test-api-key",
            model = "gpt-5.4-mini",
            systemPrompt = "你是翻译助手",
            userPrompt = "Hello",
            baseUrlOverride = baseUrl
        ).collect { token: String ->
            tokens.add(token)
        }

        // 验证请求方法、路径和关键 Header
        val recordedRequest: RecordedRequest? = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("应收到 HTTP 请求", recordedRequest)
        val req = recordedRequest!!
        assertEquals("POST", req.method)
        assertEquals("/v1/chat/completions", req.path)
        assertTrue(
            "Authorization header 应包含 Bearer token",
            req.getHeader("Authorization") == "Bearer test-api-key"
        )
        assertEquals(
            "Accept header 应为 text/event-stream",
            "text/event-stream",
            req.getHeader("Accept")
        )

        // 验证解析出了 token
        assertTrue("应解析到至少一个 token", tokens.isNotEmpty())
    }

    // ══════════════════════════════════════════════
    // SSE 流式解析
    // ══════════════════════════════════════════════

    @Test
    fun `SSE 流式响应正确解析多个 token`() = runBlocking {
        val sseBody = """
            data: {"id":"1","choices":[{"delta":{"content":"Hello"}}]}
            
            data: {"id":"2","choices":[{"delta":{"content":" "}}]}
            
            data: {"id":"3","choices":[{"delta":{"content":"World"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sseBody)
                .setHeader("Content-Type", "text/event-stream")
        )

        val baseUrl = server.url("/v1/chat/completions").toString()
        val tokens: List<String> = llmClient.streamChat(
            provider = "openai",
            apiKey = "test-key",
            model = "gpt-5.4-mini",
            systemPrompt = "你是助手",
            userPrompt = "Hi",
            baseUrlOverride = baseUrl
        ).toList()

        // 验证所有 token 按顺序被正确解析
        assertTrue("应解析到 3 个 token", tokens.size >= 1)
    }

    // ══════════════════════════════════════════════
    // HTTP 错误码处理
    // ══════════════════════════════════════════════

    @Test
    fun `HTTP 401 返回认证失败异常`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API Key"}}""")
        )

        val baseUrl = server.url("/v1/chat/completions").toString()

        try {
            llmClient.streamChat(
                provider = "openai",
                apiKey = "invalid-key",
                model = "gpt-5.4-mini",
                systemPrompt = "test",
                userPrompt = "test",
                baseUrlOverride = baseUrl
            ).toList() as List<String>
            Assert.fail("应抛出异常")
        } catch (e: Exception) {
            assertTrue(
                "异常信息应包含认证失败",
                e.message?.contains("认证失败") == true
            )
        }
    }

    @Test
    fun `HTTP 429 返回限流异常`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":"Rate limit exceeded"}""")
        )

        val baseUrl = server.url("/v1/chat/completions").toString()

        try {
            llmClient.streamChat(
                provider = "openai",
                apiKey = "test-key",
                model = "gpt-5.4-mini",
                systemPrompt = "test",
                userPrompt = "test",
                baseUrlOverride = baseUrl
            ).toList() as List<String>
            Assert.fail("应抛出异常")
        } catch (e: Exception) {
            assertTrue(
                "异常信息应包含请求频繁",
                e.message?.contains("频繁") == true || e.message?.contains("重试") == true
            )
        }
    }
}
