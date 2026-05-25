package com.example.fakeocat.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * PromptBuilder 单元测试。
 *
 * PromptBuilder 是纯函数工具类，不依赖任何外部模块，测试最直接。
 * 覆盖 system prompt 构建、用户 prompt 构建、语言名称映射和语言列表。
 */
class PromptBuilderTest {

    // ══════════════════════════════════════════════
    // buildSystemPrompt 测试
    // ══════════════════════════════════════════════

    @Test
    fun `buildSystemPrompt 返回非空字符串且包含目标语言名称`() {
        val prompt = PromptBuilder().buildSystemPrompt(
            mode = "HowToSay",
            native = "zh",
            target = "ja"
        )
        assertTrue("System prompt 不应为空", prompt.isNotEmpty())
        // 验证包含目标语言的中文全名
        assertTrue("System prompt 应包含日语", prompt.contains("日语"))
    }

    @Test
    fun `buildSystemPrompt WhatMeans 模式返回非空字符串`() {
        val prompt = PromptBuilder().buildSystemPrompt(
            mode = "WhatMeans",
            native = "en",
            target = "zh"
        )
        assertTrue("WhatMeans system prompt 不应为空", prompt.isNotEmpty())
        assertTrue("WhatMeans system prompt 应包含英语", prompt.contains("英语"))
    }

    @Test
    fun `buildSystemPrompt FreeChat 模式返回非空字符串`() {
        val prompt = PromptBuilder().buildSystemPrompt(
            mode = "FreeChat",
            native = "zh",
            target = "en"
        )
        assertTrue("FreeChat system prompt 不应为空", prompt.isNotEmpty())
        assertTrue("FreeChat system prompt 应包含英语", prompt.contains("英语"))
    }

    // ══════════════════════════════════════════════
    // langDisplayName 测试
    // ══════════════════════════════════════════════

    @Test
    fun `langDisplayName zh 返回中文`() {
        // 注意：实际实现返回 "中文" 而非 "简体中文"
        assertEquals("中文", PromptBuilder.langDisplayName("zh"))
    }

    @Test
    fun `langDisplayName unknown 返回原始代码`() {
        assertEquals("xyz", PromptBuilder.langDisplayName("xyz"))
    }

    @Test
    fun `langDisplayName 已知语言返回本地化名称`() {
        assertEquals("日本語", PromptBuilder.langDisplayName("ja"))
        assertEquals("English", PromptBuilder.langDisplayName("en"))
        assertEquals("한국어", PromptBuilder.langDisplayName("ko"))
    }

    // ══════════════════════════════════════════════
    // langFullNameCn 测试
    // ══════════════════════════════════════════════

    @Test
    fun `langFullNameCn 已知语言返回中文全名`() {
        assertEquals("中文", PromptBuilder.langFullNameCn("zh"))
        assertEquals("日语", PromptBuilder.langFullNameCn("ja"))
        assertEquals("英语", PromptBuilder.langFullNameCn("en"))
        assertEquals("韩语", PromptBuilder.langFullNameCn("ko"))
    }

    @Test
    fun `langFullNameCn unknown 返回原始代码`() {
        assertEquals("abc", PromptBuilder.langFullNameCn("abc"))
    }

    // ══════════════════════════════════════════════
    // supportedLanguages 测试
    // ══════════════════════════════════════════════

    @Test
    fun `supportedLanguages 列表非空且包含常见语言`() {
        val languages = PromptBuilder.supportedLanguages
        assertTrue("支持的语言列表不应为空", languages.isNotEmpty())
        assertTrue("应包含中文 zh", languages.contains("zh"))
        assertTrue("应包含英文 en", languages.contains("en"))
        assertTrue("应包含日文 ja", languages.contains("ja"))
    }

    // ══════════════════════════════════════════════
    // buildUserPrompt 测试
    // ══════════════════════════════════════════════

    @Test
    fun `buildUserPrompt HowToSay 模式包装用户输入`() {
        val result = PromptBuilder().buildUserPrompt(
            mode = "HowToSay",
            userText = "Hello",
            target = "ja"
        )
        assertTrue("HowToSay prompt 应包含用户原文", result.contains("Hello"))
        assertTrue("HowToSay prompt 应包含日语", result.contains("日语"))
    }

    @Test
    fun `buildUserPrompt WhatMeans 模式包装用户输入`() {
        val result = PromptBuilder().buildUserPrompt(
            mode = "WhatMeans",
            userText = "こんにちは",
            target = "ja"
        )
        assertTrue("WhatMeans prompt 应包含用户原文", result.contains("こんにちは"))
        assertTrue("WhatMeans prompt 应包含详细解释关键词", result.contains("详细解释"))
    }

    @Test
    fun `buildUserPrompt FreeChat 模式直接返回用户原文`() {
        val userText = "今天天气真好"
        val result = PromptBuilder().buildUserPrompt(
            mode = "FreeChat",
            userText = userText,
            target = "en"
        )
        assertEquals("FreeChat 模式应直接返回用户原文", userText, result)
    }
}
