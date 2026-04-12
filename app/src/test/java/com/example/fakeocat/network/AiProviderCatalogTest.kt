package com.example.fakeocat.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {

    @Test
    fun providers_match_required_order_and_models() {
        val expected = listOf(
            "openai" to "gpt-5.4-mini",
            "anthropic" to "claude-haiku-4-5",
            "gemini" to "gemini-3.1-flash-lite-preview",
            "grok" to "grok-4-1-fast",
            "mimo" to "mimo-v2-flash",
            "deepseek" to "deepseek-v3.2",
            "doubao" to "doubao-lite-128k",
            "qwen" to "qwen-turbo-latest",
            "hunyuan" to "hunyuan-lite",
            "ernie" to "ernie-speed-128k",
            "zhipu" to "glm-4.7-flash",
            "kimi" to "moonshot-v1-8k",
            "minimax" to "minimax-m2.5-highspeed"
        )

        val actual = AiProviderCatalog.providers.map { it.id to it.model }
        assertEquals(expected, actual)
    }

    @Test
    fun openai_compatible_providers_have_urls() {
        val byId = AiProviderCatalog.providers.associateBy { it.id }
        assertNotNull(byId["openai"]?.chatCompletionsUrl)
        assertNotNull(byId["deepseek"]?.chatCompletionsUrl)
        assertNotNull(byId["grok"]?.chatCompletionsUrl)
        assertNotNull(byId["doubao"]?.chatCompletionsUrl)
        assertNotNull(byId["qwen"]?.chatCompletionsUrl)
        assertNotNull(byId["zhipu"]?.chatCompletionsUrl)
        assertNotNull(byId["kimi"]?.chatCompletionsUrl)
        assertNotNull(byId["minimax"]?.chatCompletionsUrl)
        assertNotNull(byId["mimo"]?.chatCompletionsUrl)
        assertNotNull(byId["hunyuan"]?.chatCompletionsUrl)
        assertNotNull(byId["ernie"]?.chatCompletionsUrl)

        assertEquals(null, byId["anthropic"]?.chatCompletionsUrl)
        assertEquals(null, byId["gemini"]?.chatCompletionsUrl)
    }

    @Test
    fun ids_are_unique() {
        val ids = AiProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size == 13)
    }
}

