package com.example.fakeocat.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStreamParsersTest {

    @Test
    fun openai_parser_reads_delta_content() {
        val data = """{"choices":[{"delta":{"content":"hello"}}]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("hello"), chunk)
    }

    @Test
    fun openai_parser_reads_reasoning_content() {
        val data = """{"choices":[{"delta":{"reasoning_content":"思考过程..."}}]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("思考过程..."), chunk)
    }

    @Test
    fun openai_parser_reads_reasoning_field() {
        val data = """{"choices":[{"delta":{"reasoning":"thinking..."}}]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("thinking..."), chunk)
    }

    @Test
    fun openai_parser_prefers_content_over_reasoning() {
        val data = """{"choices":[{"delta":{"content":"main","reasoning_content":"思考"}}]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("main"), chunk)
    }

    @Test
    fun openai_parser_falls_back_to_choice_text() {
        val data = """{"choices":[{"text":"completion text"}]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("completion text"), chunk)
    }

    @Test
    fun openai_parser_reads_output_text() {
        val data = """{"output_text":"fallback text"}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("fallback text"), chunk)
    }

    @Test
    fun openai_parser_reads_top_level_text() {
        val data = """{"text":"top level text"}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertEquals(ParsedSseChunk.Text("top level text"), chunk)
    }

    @Test
    fun openai_parser_ignores_empty_choices() {
        val data = """{"choices":[]}"""
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, data)
        assertTrue(chunk is ParsedSseChunk.Ignore)
    }

    @Test
    fun openai_parser_handles_done_token() {
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, "[DONE]")
        assertTrue(chunk is ParsedSseChunk.Done)
    }

    @Test
    fun openai_parser_handles_done_type() {
        val chunk = ProviderStreamParsers.openAiCompatible.parse("done", """{"type":"done"}""")
        assertTrue(chunk is ParsedSseChunk.Done)
    }

    @Test
    fun openai_parser_ignores_invalid_json() {
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, "not json")
        assertTrue(chunk is ParsedSseChunk.Ignore)
    }

    @Test
    fun openai_parser_ignores_heartbeat() {
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, ": heart beat")
        assertTrue(chunk is ParsedSseChunk.Ignore)
    }

    @Test
    fun anthropic_parser_reads_delta_text_and_done_type() {
        val textChunk = ProviderStreamParsers.anthropic.parse(
            "content_block_delta",
            """{"type":"content_block_delta","delta":{"text":"abc"}}"""
        )
        assertEquals(ParsedSseChunk.Text("abc"), textChunk)

        val doneChunk = ProviderStreamParsers.anthropic.parse("message_stop", "{}")
        assertTrue(doneChunk is ParsedSseChunk.Done)
    }

    @Test
    fun anthropic_parser_handles_completion_stop() {
        val chunk = ProviderStreamParsers.anthropic.parse("completion_stop", """{"type":"completion_stop"}""")
        assertTrue(chunk is ParsedSseChunk.Done)
    }

    @Test
    fun gemini_parser_reads_candidates_text() {
        val data = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "gemini text"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val chunk = ProviderStreamParsers.gemini.parse(null, data)
        assertEquals(ParsedSseChunk.Text("gemini text"), chunk)
    }

    @Test
    fun gemini_parser_handles_done_via_response_completed() {
        val chunk = ProviderStreamParsers.gemini.parse("response.completed", "{}")
        assertTrue(chunk is ParsedSseChunk.Done)
    }

    @Test
    fun gemini_parser_ignores_empty_candidates() {
        val data = """{"candidates":[]}"""
        val chunk = ProviderStreamParsers.gemini.parse(null, data)
        assertTrue(chunk is ParsedSseChunk.Ignore)
    }

    @Test
    fun provider_specific_fallbacks_cover_non_standard_fields() {
        val ernie = ProviderStreamParsers.ernie.parse(null, """{"result":"ernie text"}""")
        assertEquals(ParsedSseChunk.Text("ernie text"), ernie)

        val minimax = ProviderStreamParsers.minimax.parse(null, """{"reply":"minimax text"}""")
        assertEquals(ParsedSseChunk.Text("minimax text"), minimax)

        val hunyuan = ProviderStreamParsers.hunyuan.parse(null, """{"reply":"hunyuan text"}""")
        assertEquals(ParsedSseChunk.Text("hunyuan text"), hunyuan)
    }

    @Test
    fun ernie_fallback_after_openai_format() {
        // ernie 先尝试 openai 格式，失败后尝试自己的格式
        val chunk = ProviderStreamParsers.ernie.parse(null, """{"result":"仅 ernie 字段"}""")
        assertEquals(ParsedSseChunk.Text("仅 ernie 字段"), chunk)
    }

    @Test
    fun for_provider_routes_to_expected_behavior() {
        val chunk = ProviderStreamParsers.forProvider("ernie").parse(null, """{"result":"ok"}""")
        assertEquals(ParsedSseChunk.Text("ok"), chunk)

        val fallback = ProviderStreamParsers.forProvider("unknown").parse(null, """{"choices":[{"delta":{"content":"ok2"}}]}""")
        assertEquals(ParsedSseChunk.Text("ok2"), fallback)
    }

    @Test
    fun for_provider_anthropic_routes_correctly() {
        val chunk = ProviderStreamParsers.forProvider("anthropic")
            .parse("content_block_delta", """{"type":"content_block_delta","delta":{"text":"hi"}}""")
        assertEquals(ParsedSseChunk.Text("hi"), chunk)
    }

    @Test
    fun for_provider_gemini_routes_correctly() {
        val chunk = ProviderStreamParsers.forProvider("gemini")
            .parse(null, """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}""")
        assertEquals(ParsedSseChunk.Text("hi"), chunk)
    }
}

