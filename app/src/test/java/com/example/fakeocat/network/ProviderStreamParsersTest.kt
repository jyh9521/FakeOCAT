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
    fun openai_parser_handles_done_token() {
        val chunk = ProviderStreamParsers.openAiCompatible.parse(null, "[DONE]")
        assertTrue(chunk is ParsedSseChunk.Done)
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
    fun provider_specific_fallbacks_cover_non_standard_fields() {
        val ernie = ProviderStreamParsers.ernie.parse(null, """{"result":"ernie text"}""")
        assertEquals(ParsedSseChunk.Text("ernie text"), ernie)

        val minimax = ProviderStreamParsers.minimax.parse(null, """{"reply":"minimax text"}""")
        assertEquals(ParsedSseChunk.Text("minimax text"), minimax)

        val hunyuan = ProviderStreamParsers.hunyuan.parse(null, """{"reply":"hunyuan text"}""")
        assertEquals(ParsedSseChunk.Text("hunyuan text"), hunyuan)
    }

    @Test
    fun for_provider_routes_to_expected_behavior() {
        val chunk = ProviderStreamParsers.forProvider("ernie").parse(null, """{"result":"ok"}""")
        assertEquals(ParsedSseChunk.Text("ok"), chunk)

        val fallback = ProviderStreamParsers.forProvider("unknown").parse(null, """{"choices":[{"delta":{"content":"ok2"}}]}""")
        assertEquals(ParsedSseChunk.Text("ok2"), fallback)
    }
}

