package com.example.fakeocat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MarkdownMessageRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_basic_markdown_without_raw_markers() {
        rule.setContent {
            TestTheme {
                MarkdownMessage(
                    text = "# Title\n\n**Bold** and *Italic* and `Code`\n\n[Link](https://example.com)",
                    isUser = false,
                    onSpeak = { _, _ -> }
                )
            }
        }

        rule.onNodeWithText("Title").fetchSemanticsNode()
        rule.onNodeWithText("Bold").fetchSemanticsNode()
        rule.onNodeWithText("Italic").fetchSemanticsNode()
        rule.onNodeWithText("Code").fetchSemanticsNode()
        rule.onNodeWithText("Link").fetchSemanticsNode()

        assertEquals(0, rule.onAllNodesWithText("**Bold**").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("*Italic*").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("`Code`").fetchSemanticsNodes().size)
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

