package com.example.fakeocat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPlainTextTest {

    @Test
    fun strips_markdown_markers() {
        val input = """
            # H1
            - **Bold** *Italic* `Code`
            [Google](https://google.com)
            [Bonjour](bonjour|bɔ̃ʒuʁ|fr)
        """.trimIndent()

        val out = markdownToPlainText(input)
        assertEquals(
            """
            H1
            Bold Italic Code
            Google
            Bonjour
            """.trimIndent(),
            out.trim()
        )
    }
}

