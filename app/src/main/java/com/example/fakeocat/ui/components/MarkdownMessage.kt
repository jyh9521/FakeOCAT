package com.example.fakeocat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.max

private const val URL_ANNOTATION_TAG = "url"

@Composable
fun MarkdownMessage(
    text: String,
    isUser: Boolean,
    onSpeak: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val pronRegex = Regex("(\\*\\*)?\\s*\\[(.*?)\\]\\((.*?)\\|(.*?)(?:\\|(.*?))?\\)\\s*(\\*\\*)?")
    val lines = text.lines()

    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var inCodeBlock = false
        val codeLines = ArrayList<String>()

        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    inCodeBlock = false
                    if (codeLines.isNotEmpty()) {
                        CodeBlock(
                            code = codeLines.joinToString("\n"),
                            isUser = isUser
                        )
                        codeLines.clear()
                    }
                } else {
                    inCodeBlock = true
                    codeLines.clear()
                }
                return@forEach
            }

            if (inCodeBlock) {
                codeLines.add(rawLine)
                return@forEach
            }

            if (rawLine.isBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                return@forEach
            }

            when {
                trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    )
                }

                trimmed.startsWith("###### ") -> MarkdownLine(trimmed.removePrefix("###### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 6)
                trimmed.startsWith("##### ") -> MarkdownLine(trimmed.removePrefix("##### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 5)
                trimmed.startsWith("#### ") -> MarkdownLine(trimmed.removePrefix("#### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 4)
                trimmed.startsWith("### ") -> MarkdownLine(trimmed.removePrefix("### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 3)
                trimmed.startsWith("## ") -> MarkdownLine(trimmed.removePrefix("## "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 2)
                trimmed.startsWith("# ") -> MarkdownLine(trimmed.removePrefix("# "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 1)

                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") -> {
                    val content = trimmed.drop(2)
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        MarkdownLine(content, isUser, onSpeak, pronRegex)
                    }
                }

                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val match = Regex("^(\\d+\\.)\\s(.*)").find(trimmed)
                    val prefix = match?.groups?.get(1)?.value ?: ""
                    val content = match?.groups?.get(2)?.value ?: ""
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("$prefix ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        MarkdownLine(content, isUser, onSpeak, pronRegex)
                    }
                }

                trimmed.startsWith("> ") -> {
                    val content = trimmed.removePrefix("> ")
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                    ) {
                        MarkdownLine(content, isUser, onSpeak, pronRegex)
                    }
                }

                else -> MarkdownLine(rawLine, isUser, onSpeak, pronRegex)
            }
        }

        if (inCodeBlock && codeLines.isNotEmpty()) {
            CodeBlock(
                code = codeLines.joinToString("\n"),
                isUser = isUser
            )
            codeLines.clear()
        }
    }
}

@Composable
private fun CodeBlock(
    code: String,
    isUser: Boolean
) {
    val scrollState = rememberScrollState()
    val container = if (isUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(container)
            .horizontalScroll(scrollState)
            .padding(10.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MarkdownLine(
    text: String,
    isUser: Boolean,
    onSpeak: (String, String?) -> Unit,
    pronRegex: Regex,
    isHeader: Boolean = false,
    headerLevel: Int = 0
) {
    val boldRanges = Regex("\\*\\*.*?\\*\\*").findAll(text).map { it.range }.toList()
    val italicRanges = Regex("(?<!\\*)\\*.*?\\*(?!\\*)").findAll(text).map { it.range }.toList()
    val codeRanges = Regex("`.*?`").findAll(text).map { it.range }.toList()

    fun isPosStyled(pos: Int): Triple<Boolean, Boolean, Boolean> {
        val isBold = boldRanges.any { pos in it }
        val isItalic = italicRanges.any { pos in it }
        val isCode = codeRanges.any { pos in it }
        return Triple(isBold, isItalic, isCode)
    }

    val baseFontSize = if (isHeader) max(22 - headerLevel * 2, 14).sp else 15.sp
    val baseLineHeight = if (isHeader) max(26 - headerLevel * 2, 18).sp else 22.sp
    val baseColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        var lastIndex = 0
        pronRegex.findAll(text).forEach { match ->
            val plainText = text.substring(lastIndex, match.range.first)
            if (plainText.isNotEmpty()) {
                val (isBold, isItalic, isCode) = isPosStyled((match.range.first - 1).coerceAtLeast(0))
                InlineMarkdownText(
                    text = plainText,
                    isUser = isUser,
                    isHeader = isHeader,
                    headerLevel = headerLevel,
                    baseColor = baseColor,
                    baseFontSize = baseFontSize,
                    baseLineHeight = baseLineHeight,
                    isParentBold = isBold,
                    isParentItalic = isItalic,
                    isParentCode = isCode
                )
            }

            val (isBold, isItalic, _) = isPosStyled(match.range.first)
            val term = match.groups[2]?.value ?: ""
            val ipa = match.groups[4]?.value ?: ""
            val lang = match.groups[5]?.value ?: ""

            AssistChip(
                onClick = { onSpeak(term, lang.ifEmpty { null }) },
                label = {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = term,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isBold || isHeader) FontWeight.ExtraBold else FontWeight.Bold,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                        )
                        if (ipa.isNotEmpty()) {
                            Text(
                                text = "[$ipa]",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak",
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    labelColor = baseColor
                ),
                border = null,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            lastIndex = match.range.last + 1
        }

        val remainingText = text.substring(lastIndex)
        if (remainingText.isNotEmpty()) {
            val (isBold, isItalic, isCode) = isPosStyled(lastIndex.coerceAtLeast(0))
            InlineMarkdownText(
                text = remainingText,
                isUser = isUser,
                isHeader = isHeader,
                headerLevel = headerLevel,
                baseColor = baseColor,
                baseFontSize = baseFontSize,
                baseLineHeight = baseLineHeight,
                isParentBold = isBold,
                isParentItalic = isItalic,
                isParentCode = isCode
            )
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    isUser: Boolean,
    isHeader: Boolean,
    headerLevel: Int,
    baseColor: Color,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    baseLineHeight: androidx.compose.ui.unit.TextUnit,
    isParentBold: Boolean,
    isParentItalic: Boolean,
    isParentCode: Boolean
) {
    val annotated = parseInlineMarkdown(
        text = text,
        isHeader = isHeader,
        headerLevel = headerLevel,
        isParentBold = isParentBold,
        isParentItalic = isParentItalic,
        isParentCode = isParentCode
    )
    if (annotated.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    val textStyle = TextStyle(
        fontSize = baseFontSize,
        lineHeight = baseLineHeight,
        fontWeight = if (isHeader || isParentBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (isParentItalic) FontStyle.Italic else FontStyle.Normal,
        color = baseColor
    )

    val hasUrl = annotated.getStringAnnotations(URL_ANNOTATION_TAG, 0, annotated.length).isNotEmpty()
    if (hasUrl) {
        ClickableText(
            text = annotated,
            style = textStyle,
            onClick = { offset ->
                val ann = annotated.getStringAnnotations(URL_ANNOTATION_TAG, offset, offset).firstOrNull() ?: return@ClickableText
                val url = ann.item
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    uriHandler.openUri(url)
                }
            }
        )
    } else {
        Text(text = annotated, style = textStyle)
    }
}

private fun parseInlineMarkdown(
    text: String,
    isHeader: Boolean,
    headerLevel: Int,
    isParentBold: Boolean,
    isParentItalic: Boolean,
    isParentCode: Boolean
): AnnotatedString {
    val cleanedText = text
        .replace(Regex("^\\s*\\*\\*|\\*\\*\\s*$"), "")
        .replace(Regex("^\\s*(?<!\\*)\\*|(?<!\\*)\\*\\s*$"), "")
        .replace(Regex("^\\s*`|`\\s*$"), "")
        .replace(Regex("(?<![a-zA-Z0-9])\\*\\*(?![a-zA-Z0-9])"), "")
        .replace(Regex("(?<![a-zA-Z0-9])\\*(?![a-zA-Z0-9])"), "")

    if (cleanedText.isBlank() && (text.contains("**") || text.contains("*") || text.contains("`"))) return AnnotatedString("")

    val inlineRegex = Regex(
        "(`[^`]+`)|(\\[([^\\]]+)]\\(([^)]+)\\))|(\\*\\*([^*]+)\\*\\*)|((?<!\\*)\\*([^*]+)\\*(?!\\*))"
    )

    return buildAnnotatedString {
        var currentIndex = 0
        val baseStyle = SpanStyle(
            fontWeight = if (isParentBold || isHeader) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isParentItalic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (isParentCode) FontFamily.Monospace else null,
            background = if (isParentCode) Color.LightGray.copy(alpha = 0.3f) else Color.Unspecified
        )

        pushStyle(baseStyle)
        inlineRegex.findAll(cleanedText).forEach { match ->
            append(cleanedText.substring(currentIndex, match.range.first))

            val matchText = match.value
            when {
                match.groups[1] != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.LightGray.copy(alpha = 0.3f),
                            color = Color.Red.copy(alpha = 0.8f)
                        )
                    ) {
                        append(matchText.removeSurrounding("`").trim())
                    }
                }

                match.groups[2] != null -> {
                    val label = match.groups[3]?.value.orEmpty()
                    val url = match.groups[4]?.value.orEmpty()
                    val start = length
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF1565C0),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    val end = length
                    if (url.isNotBlank()) {
                        addStringAnnotation(URL_ANNOTATION_TAG, url.trim(), start, end)
                    }
                }

                match.groups[5] != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groups[6]?.value.orEmpty().trim())
                    }
                }

                match.groups[7] != null -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groups[8]?.value.orEmpty().trim())
                    }
                }
            }

            currentIndex = match.range.last + 1
        }
        append(cleanedText.substring(currentIndex))
        pop()
    }
}

fun markdownToPlainText(text: String): String {
    var t = text
    t = t.replace(Regex("```[\\s\\S]*?```"), { m ->
        m.value.removePrefix("```").removeSuffix("```")
    })
    t = t.replace(Regex("\\[(.*?)]\\((.*?)(?:\\|(.*?))?(?:\\|(.*?))?\\)"), "$1")
    t = t.replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
    t = t.replace("**", "")
    t = t.replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
    t = t.replace("`", "")
    t = t.replace(Regex("(?m)^#{1,6}\\s+"), "")
    t = t.replace(Regex("(?m)^>\\s+"), "")
    t = t.replace(Regex("(?m)^\\s*([-+*]|\\d+\\.)\\s+"), "")
    return t
}
