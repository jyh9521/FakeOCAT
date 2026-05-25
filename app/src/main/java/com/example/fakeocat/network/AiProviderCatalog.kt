package com.example.fakeocat.network

enum class AiAuthScheme {
    BearerToken,
    AnthropicApiKeyHeader,
    GeminiApiKeyQuery
}

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val model: String,
    val authScheme: AiAuthScheme,
    val chatCompletionsUrl: String?
)

object AiProviderCatalog {
    val providers: List<AiProviderInfo> = listOf(
        AiProviderInfo(
            id = "openai",
            displayName = "OpenAI",
            model = "gpt-5.4-mini",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.openai.com/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "anthropic",
            displayName = "Anthropic",
            model = "claude-haiku-4-5",
            authScheme = AiAuthScheme.AnthropicApiKeyHeader,
            chatCompletionsUrl = null
        ),
        AiProviderInfo(
            id = "gemini",
            displayName = "Gemini",
            model = "gemini-2.5-flash",
            authScheme = AiAuthScheme.GeminiApiKeyQuery,
            chatCompletionsUrl = null
        ),
        AiProviderInfo(
            id = "grok",
            displayName = "Grok（xAI）",
            model = "grok-3-mini",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.x.ai/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "mimo",
            displayName = "小米 mimo",
            model = "mimo-v2-flash",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.mimogpt.com/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "deepseek",
            displayName = "DeepSeek",
            model = "deepseek-chat",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.deepseek.com/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "qwen",
            displayName = "阿里千问",
            model = "qwen-mt-lite",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "hunyuan",
            displayName = "腾讯混元",
            model = "hunyuan-lite",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.hunyuan.cloud.tencent.com/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "ernie",
            displayName = "百度文心一言",
            model = "ernie-speed-128k",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://qianfan.baidubce.com/v2/chat/completions"
        ),
        AiProviderInfo(
            id = "zhipu",
            displayName = "智谱AI",
            model = "glm-4.7-flash",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        ),
        AiProviderInfo(
            id = "kimi",
            displayName = "Kimi（Moonshot）",
            model = "moonshot-v1-8k",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.moonshot.cn/v1/chat/completions"
        ),
        AiProviderInfo(
            id = "minimax",
            displayName = "MiniMax",
            model = "minimax-m2.5-highspeed",
            authScheme = AiAuthScheme.BearerToken,
            chatCompletionsUrl = "https://api.minimax.chat/v1/chat/completions"
        )
    )

    fun getProvider(id: String): AiProviderInfo? = providers.firstOrNull { it.id == id }
}

