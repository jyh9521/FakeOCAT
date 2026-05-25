package com.example.fakeocat.ui.viewmodel

/**
 * 纯工具类：负责所有 Prompt 构建逻辑。
 * 不依赖注入，不持有状态，所有方法均为纯函数。
 */
class PromptBuilder {

    companion object {
        /** 应用支持的语言列表 */
        val supportedLanguages = listOf(
            "zh", "en", "ja", "ko", "es", "fr", "de", "pt", "ru", "it",
            "nl", "sv", "pl", "cs", "el", "he", "ar", "th", "vi", "id", "hi", "tr"
        )

        /** 返回语言的本地化显示名称（用于 UI） */
        fun langDisplayName(code: String): String = when (code) {
            "zh" -> "中文"
            "en" -> "English"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "pt" -> "Português"
            "ru" -> "Русский"
            "it" -> "Italiano"
            "ar" -> "العربية"
            "th" -> "ไทย"
            "vi" -> "Tiếng Việt"
            "id" -> "Bahasa Indonesia"
            "hi" -> "हिन्दी"
            "tr" -> "Türkçe"
            "nl" -> "Nederlands"
            "sv" -> "Svenska"
            "pl" -> "Polski"
            "cs" -> "Čeština"
            "el" -> "Ελληνικά"
            "he" -> "עברית"
            else -> code
        }

        /** 返回语言的中文全名（用于 Prompt 中的语言名称） */
        fun langFullNameCn(code: String): String = when (code) {
            "zh" -> "中文"
            "en" -> "英语"
            "ja" -> "日语"
            "ko" -> "韩语"
            "es" -> "西班牙语"
            "fr" -> "法语"
            "de" -> "德语"
            "pt" -> "葡萄牙语"
            "ru" -> "俄语"
            "it" -> "意大利语"
            "ar" -> "阿拉伯语"
            "th" -> "泰语"
            "vi" -> "越南语"
            "id" -> "印尼语"
            "hi" -> "印地语"
            "tr" -> "土耳其语"
            "nl" -> "荷兰语"
            "sv" -> "瑞典语"
            "pl" -> "波兰语"
            "cs" -> "捷克语"
            "el" -> "希腊语"
            "he" -> "希伯来语"
            else -> code
        }

        // ══════════════════════════════════════════════
        // 预编译的 Prompt 模板（String.format 占位符）
        // %1$s = nativeName, %2$s = targetName, %3$s = target code (e.g. "ja"),
        // %4$s = formattingInstruction（需在运行时填充）
        // ══════════════════════════════════════════════

        private const val FORMATTING_INSTRUCTION_TEMPLATE =
            "当你提到%2\$s或其他外语中的特定单词或短语时，请务必使用以下格式以便系统生成发音按钮：\n" +
            "[单词或短语](发音/假名|国际音标|语言代码)\n" +
            "如果语言是%2\$s，语言代码请填 \"%3\$s\"。\n" +
            "例如：[雹](ひょう|çjaʊ|%3\$s) 或 [Bonjour](bonjour|bɔ̃ʒuʁ|fr)."

        private const val SYSTEM_PROMPT_HOW_TO_SAY =
            "你是一个专业的语言学习助手，帮助母语为%1\$s的用户学习%2\$s。\n" +
            "%4\$s\n" +
            "用户会给你一个词或句子，请你翻译成%2\$s。请严格按照以下格式回复：\n\n" +
            "1. 先给出口语（非正式）和敬语（正式）两种翻译，每种翻译中的关键词请使用 [单词](读音|IPA) 格式。\n" +
            "2. 然后逐条详细解析每个翻译中的生词、语法点和使用场景。\n" +
            "3. 最后给出2-3个其他相关的自然表达。\n\n" +
            "请确保所有%2\$s关键内容都标注读音。所有解释说明请使用%1\$s。"

        private const val SYSTEM_PROMPT_WHAT_MEANS =
            "你是一个语言学习助手，帮助母语为%1\$s的用户理解外语内容。\n" +
            "%4\$s\n" +
            "请对用户提供的内容进行详细解析：\n\n" +
            "1. 读音和国际音标（使用 [单词](读音|IPA) 格式）\n" +
            "2. 词性和基本释义\n" +
            "3. 详细解释和文化背景\n" +
            "4. 2-3个例句（附带%1\$s翻译，例句中的关键词也请使用 [单词](读音|IPA) 格式）\n" +
            "5. 近义词和反义词\n\n" +
            "所有解释说明请使用%1\$s。"

        private const val SYSTEM_PROMPT_FREE_CHAT =
            "你是一个友好的对话伙伴，帮助母语为%1\$s的用户练习%2\$s。" +
            "用自然的方式与用户聊天，适当使用%2\$s并附上%1\$s解释。" +
            "提到关键词时请使用 [单词](读音|IPA) 格式。"
    }

    /**
     * 使用预编译的模板构建 system prompt。
     * 相比字符串模板拼接，String.format 减少了运行时字符串分配次数。
     */
    fun buildSystemPrompt(mode: String, native: String, target: String): String {
        val nativeName = langFullNameCn(native)
        val targetName = langFullNameCn(target)

        val formattingInstruction = java.lang.String.format(
            FORMATTING_INSTRUCTION_TEMPLATE, nativeName, targetName, target, ""
        )

        val template = when (mode) {
            "HowToSay" -> SYSTEM_PROMPT_HOW_TO_SAY
            "WhatMeans" -> SYSTEM_PROMPT_WHAT_MEANS
            else -> SYSTEM_PROMPT_FREE_CHAT
        }

        return java.lang.String.format(template, nativeName, targetName, target, formattingInstruction)
    }

    /**
     * 根据模式构建用户消息 Prompt。
     */
    fun buildUserPrompt(mode: String, userText: String, target: String): String {
        val targetName = langFullNameCn(target)
        return when (mode) {
            "HowToSay" -> "\"$userText\" 用${targetName}怎么说？请提供口语和敬语（或正式/非正式）两种表达，然后逐一解析每个表达的含义和用法，最后给出其他相关句型。"
            "WhatMeans" -> "请详细解释「$userText」是什么意思，包括读音、词性、释义、使用场景和例句。"
            else -> userText
        }
    }
}
