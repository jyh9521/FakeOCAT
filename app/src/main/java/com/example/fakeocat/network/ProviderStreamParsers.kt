package com.example.fakeocat.network

import org.json.JSONObject

internal sealed class ParsedSseChunk {
    data class Text(val value: String) : ParsedSseChunk()
    object Done : ParsedSseChunk()
    object Ignore : ParsedSseChunk()
}

internal fun interface ProviderStreamParser {
    fun parse(type: String?, data: String): ParsedSseChunk
}

internal object ProviderStreamParsers {

    private val doneEventTypes = setOf(
        "done",
        "message_stop",
        "completion_stop",
        "response.completed"
    )

    val openAiCompatible = ProviderStreamParser { type, data ->
        if (isDoneToken(type, data)) return@ProviderStreamParser ParsedSseChunk.Done
        val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore

        val text = firstNonBlank(
            obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content"),
            obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("reasoning_content"),
            obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("reasoning"),
            obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optString("text"),
            extractMessageContent(obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")),
            obj.optNullableString("output_text"),
            obj.optNullableString("text")
        )

        if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
    }

    val anthropic = ProviderStreamParser { type, data ->
        if (isDoneToken(type, data)) return@ProviderStreamParser ParsedSseChunk.Done
        val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore
        if (doneEventTypes.contains(obj.optString("type"))) {
            return@ProviderStreamParser ParsedSseChunk.Done
        }

        val text = obj.optJSONObject("delta")?.optString("text")
        if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
    }

    val gemini = ProviderStreamParser { type, data ->
        if (isDoneToken(type, data)) return@ProviderStreamParser ParsedSseChunk.Done
        val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore

        val text = obj.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")

        if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
    }

    val hunyuan = ProviderStreamParser { type, data ->
        when (val base = openAiCompatible.parse(type, data)) {
            is ParsedSseChunk.Text -> base
            ParsedSseChunk.Done -> ParsedSseChunk.Done
            ParsedSseChunk.Ignore -> {
                val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore
                val text = firstNonBlank(
                    obj.optNullableString("reply"),
                    obj.optNullableString("result")
                )
                if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
            }
        }
    }

    val ernie = ProviderStreamParser { type, data ->
        when (val base = openAiCompatible.parse(type, data)) {
            is ParsedSseChunk.Text -> base
            ParsedSseChunk.Done -> ParsedSseChunk.Done
            ParsedSseChunk.Ignore -> {
                val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore
                val text = obj.optNullableString("result")
                if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
            }
        }
    }

    val minimax = ProviderStreamParser { type, data ->
        when (val base = openAiCompatible.parse(type, data)) {
            is ParsedSseChunk.Text -> base
            ParsedSseChunk.Done -> ParsedSseChunk.Done
            ParsedSseChunk.Ignore -> {
                val obj = parseJson(data) ?: return@ProviderStreamParser ParsedSseChunk.Ignore
                val text = firstNonBlank(
                    obj.optNullableString("reply"),
                    obj.optNullableString("output_text")
                )
                if (text.isNullOrBlank()) ParsedSseChunk.Ignore else ParsedSseChunk.Text(text)
            }
        }
    }

    fun forProvider(provider: String): ProviderStreamParser = when (provider) {
        "anthropic" -> anthropic
        "gemini" -> gemini
        "hunyuan" -> hunyuan
        "ernie" -> ernie
        "minimax" -> minimax
        else -> openAiCompatible
    }

    private fun parseJson(data: String): JSONObject? {
        val trimmed = data.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMessageContent(message: JSONObject?): String? {
        if (message == null) return null
        val direct = message.optNullableString("content")
        if (!direct.isNullOrBlank()) return direct

        val contentArray = message.optJSONArray("content") ?: return null
        for (i in 0 until contentArray.length()) {
            val part = contentArray.opt(i)
            when (part) {
                is JSONObject -> {
                    val text = part.optNullableString("text")
                    if (!text.isNullOrBlank()) return text
                }
                is String -> if (part.isNotBlank()) return part
            }
        }
        return null
    }

    private fun isDoneToken(type: String?, data: String): Boolean {
        val trimmedType = type?.trim().orEmpty()
        if (doneEventTypes.contains(trimmedType)) return true

        val trimmedData = data.trim()
        if (trimmedData == "[DONE]") return true

        val obj = parseJson(trimmedData) ?: return false
        return doneEventTypes.contains(obj.optString("type"))
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}

