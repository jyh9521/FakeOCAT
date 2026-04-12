package com.example.fakeocat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fakeocat.R
import com.example.fakeocat.data.db.entity.MessageEntity
import com.example.fakeocat.ui.viewmodel.ChatUiState
import com.example.fakeocat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun FormattedMessageText(
    text: String,
    isUser: Boolean,
    onSpeak: (String, String?) -> Unit
) {
    // 自定义发音标记正则：可选地被 ** 或 * 包裹，并允许包含空格
    // 第 1 组：可选前缀样式（**、*）
    // 第 2 组：词条（可点击单词）
    // 第 3 组：读音
    // 第 4 组：IPA
    // 第 5 组：可选语言代码
    // 第 6 组：可选后缀样式（**、*）
    val pronRegex = Regex("(\\*\\*)?\\s*\\[(.*?)\\]\\((.*?)\\|(.*?)(?:\\|(.*?))?\\)\\s*(\\*\\*)?")
    
    // 1. 先把消息按行拆分成块级元素。
    val lines = text.lines()
    
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            if (line.isBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                return@forEach
            }

            // 2. 处理块级元素（标题、列表、代码块）
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("###### ") -> MarkdownLine(trimmed.removePrefix("###### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 6)
                trimmed.startsWith("##### ") -> MarkdownLine(trimmed.removePrefix("##### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 5)
                trimmed.startsWith("#### ") -> MarkdownLine(trimmed.removePrefix("#### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 4)
                trimmed.startsWith("### ") -> MarkdownLine(trimmed.removePrefix("### "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 3)
                trimmed.startsWith("## ") -> MarkdownLine(trimmed.removePrefix("## "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 2)
                trimmed.startsWith("# ") -> MarkdownLine(trimmed.removePrefix("# "), isUser, onSpeak, pronRegex, isHeader = true, headerLevel = 1)
                
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    val content = trimmed.substring(2)
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
                    Box(modifier = Modifier
                        .padding(start = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        MarkdownLine(content, isUser, onSpeak, pronRegex)
                    }
                }
                else -> {
                    MarkdownLine(line, isUser, onSpeak, pronRegex)
                }
            }
        }
    }
}

@Composable
fun MarkdownLine(
    text: String,
    isUser: Boolean,
    onSpeak: (String, String?) -> Unit,
    pronRegex: Regex,
    isHeader: Boolean = false,
    headerLevel: Int = 0
) {
    // 1. 先全局识别这一行的样式范围，用于处理跨按钮样式。
    val boldRanges = Regex("\\*\\*.*?\\*\\*").findAll(text).map { it.range }.toList()
    val italicRanges = Regex("\\*.*?\\*").findAll(text).map { it.range }.toList()
    val codeRanges = Regex("`.*?`").findAll(text).map { it.range }.toList()

    fun isPosStyled(pos: Int): Triple<Boolean, Boolean, Boolean> {
        val isBold = boldRanges.any { pos in it }
        val isItalic = italicRanges.any { pos in it }
        val isCode = codeRanges.any { pos in it }
        return Triple(isBold, isItalic, isCode)
    }

    // 使用 FlowRow 统一布局行内发音按钮与普通 Markdown 文本。
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        var lastIndex = 0
        pronRegex.findAll(text).forEach { match ->
            // 匹配项之前的普通文本
            val plainText = text.substring(lastIndex, match.range.first)
            if (plainText.isNotEmpty()) {
                val (isBold, isItalic, isCode) = isPosStyled(match.range.first - 1)
                val parsed = parseInlineMarkdown(plainText, isUser, isHeader, headerLevel, isBold, isItalic, isCode)
                if (parsed.isNotEmpty()) {
                    Text(
                        text = parsed,
                        fontSize = if (isHeader) (22 - headerLevel * 2).coerceAtLeast(14).sp else 15.sp,
                        lineHeight = if (isHeader) (26 - headerLevel * 2).coerceAtLeast(18).sp else 22.sp,
                        fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 匹配项本身渲染为发音按钮
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
                            fontWeight = if (isBold || isHeader) FontWeight.ExtraBold else FontWeight.Bold,
                            fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
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
                    labelColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
            
            lastIndex = match.range.last + 1
        }
        
        // 处理所有匹配后，可能还剩余尾部文本
        val remainingText = text.substring(lastIndex)
        if (remainingText.isNotEmpty()) {
            val (isBold, isItalic, isCode) = isPosStyled(lastIndex)
            val parsed = parseInlineMarkdown(remainingText, isUser, isHeader, headerLevel, isBold, isItalic, isCode)
            if (parsed.isNotEmpty()) {
                Text(
                    text = parsed,
                    fontSize = if (isHeader) (22 - headerLevel * 2).coerceAtLeast(14).sp else 15.sp,
                    lineHeight = if (isHeader) (26 - headerLevel * 2).coerceAtLeast(18).sp else 22.sp,
                    fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 将 **加粗**、*斜体*、`代码` 等行内 Markdown 解析为 AnnotatedString。 */
fun parseInlineMarkdown(
    text: String,
    isUser: Boolean,
    isHeader: Boolean,
    headerLevel: Int,
    isParentBold: Boolean = false,
    isParentItalic: Boolean = false,
    isParentCode: Boolean = false
): AnnotatedString {
    // 1. 去掉 AI 可能当作项目符号使用的独立/孤立 Markdown 标记。
    // 同时清理位于边界且归属于父样式范围的冗余标记。
    val cleanedText = text
        .replace(Regex("^\\s*\\*\\*|\\*\\*\\s*$"), "") // 移除边界处加粗标记
        .replace(Regex("^\\s*\\*|\\*\\s*$"), "")     // 移除边界处斜体标记
        .replace(Regex("^\\s*`|`\\s*$"), "")         // 移除边界处代码标记
        .replace(Regex("(?<![a-zA-Z0-9])\\*\\*(?![a-zA-Z0-9])"), "") // 移除孤立的 **
        .replace(Regex("(?<![a-zA-Z0-9])\\*(?![a-zA-Z0-9])"), "")   // 移除孤立的 *

    if (cleanedText.isBlank() && (text.contains("**") || text.contains("*"))) return AnnotatedString("")

    return buildAnnotatedString {
        var currentIndex = 0
        
        // 2. 合并加粗、斜体、代码的匹配规则。
        // 使用相对宽松的匹配，以兼容内部包含空格的情况。
        val inlineRegex = Regex("(\\*\\*.*?\\*\\*)|(\\*.*?\\*)|(`.*?`)")
        
        // 基于父级上下文设置初始样式
        val baseStyle = SpanStyle(
            fontWeight = if (isParentBold || isHeader) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isParentItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            fontFamily = if (isParentCode) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            background = if (isParentCode) Color.LightGray.copy(alpha = 0.3f) else Color.Unspecified
        )

        withStyle(style = baseStyle) {
            inlineRegex.findAll(cleanedText).forEach { match ->
                // 先追加匹配前的文本
                append(cleanedText.substring(currentIndex, match.range.first))
                
                val matchText = match.value
                when {
                    matchText.startsWith("**") && matchText.endsWith("**") -> {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(matchText.removeSurrounding("**").trim())
                        }
                    }
                    matchText.startsWith("*") && matchText.endsWith("*") -> {
                        withStyle(style = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(matchText.removeSurrounding("*").trim())
                        }
                    }
                    matchText.startsWith("`") && matchText.endsWith("`") -> {
                        withStyle(style = SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = Color.LightGray.copy(alpha = 0.3f),
                            color = Color.Red.copy(alpha = 0.8f)
                        )) {
                            append(matchText.removeSurrounding("`").trim())
                        }
                    }
                }
                currentIndex = match.range.last + 1
            }
            
            // 追加剩余文本
            append(cleanedText.substring(currentIndex))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val messages by viewModel.displayMessages.collectAsState()
    val showHistoryFlag by viewModel.showHistory.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRequestInFlight by viewModel.isRequestInFlight.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val targetLang by viewModel.targetLanguage.collectAsState()
    val howToSayLang by viewModel.howToSayLang.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val effectiveLang = howToSayLang ?: targetLang

    LaunchedEffect(messages.size, uiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val targetLangName = ChatViewModel.langDisplayName(effectiveLang)
    val howToSayLabel = stringResource(R.string.mode_how_to_say, targetLangName)

    // 语言选择器状态
    var showLangPicker by remember { mutableStateOf(false) }
    val allLanguages = listOf(
        "zh" to stringResource(R.string.lang_chinese),
        "en" to stringResource(R.string.lang_english),
        "ja" to stringResource(R.string.lang_japanese),
        "ko" to stringResource(R.string.lang_korean),
        "es" to stringResource(R.string.lang_spanish),
        "fr" to stringResource(R.string.lang_french),
        "de" to stringResource(R.string.lang_german),
        "pt" to stringResource(R.string.lang_portuguese),
        "ru" to stringResource(R.string.lang_russian),
        "it" to stringResource(R.string.lang_italian),
        "nl" to stringResource(R.string.lang_dutch),
        "sv" to stringResource(R.string.lang_swedish),
        "pl" to stringResource(R.string.lang_polish),
        "cs" to stringResource(R.string.lang_czech),
        "el" to stringResource(R.string.lang_greek),
        "he" to stringResource(R.string.lang_hebrew),
        "ar" to stringResource(R.string.lang_arabic),
        "th" to stringResource(R.string.lang_thai),
        "vi" to stringResource(R.string.lang_vietnamese),
        "id" to stringResource(R.string.lang_indonesian),
        "hi" to stringResource(R.string.lang_hindi),
        "tr" to stringResource(R.string.lang_turkish)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    if (showHistoryFlag || messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_chat))
                        }
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_history))
                    }
                    IconButton(onClick = onNavigateToBookmarks) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.nav_bookmarks))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 底部区域：模式切换 + 输入框
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                // 模式选择 Chip
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ── “用 X 语怎么说”模式（带下拉） ──
                    Column {
                        FilterChip(
                            selected = currentMode == "HowToSay",
                            onClick = {
                                if (currentMode == "HowToSay") {
                                    showLangPicker = true
                                } else {
                                    viewModel.setMode("HowToSay")
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = howToSayLabel,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        DropdownMenu(
                            expanded = showLangPicker,
                            onDismissRequest = { showLangPicker = false }
                        ) {
                            allLanguages.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setHowToSayLang(code)
                                        viewModel.setMode("HowToSay")
                                        showLangPicker = false
                                    }
                                )
                            }
                        }
                    }

                    FilterChip(
                        selected = currentMode == "WhatMeans",
                        onClick = { viewModel.setMode("WhatMeans") },
                        label = {
                            Text(
                                text = stringResource(R.string.mode_what_means),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = currentMode == "FreeChat",
                        onClick = { viewModel.setMode("FreeChat") },
                        label = {
                            Text(
                                text = stringResource(R.string.mode_free_chat),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // 输入栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.hint_input)) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        maxLines = 4,
                        singleLine = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isRequestInFlight) {
                        androidx.compose.material3.Button(
                            onClick = { viewModel.cancelGeneration() },
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.action_stop), fontSize = 13.sp)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText.trim())
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.action_send),
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // 消息列表：占据剩余可用空间
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onToggleBookmark = { 
                        viewModel.toggleBookmark(message)
                        scope.launch {
                            snackbarHostState.showSnackbar(if (message.isBookmarked) "已取消收藏" else "已收藏")
                        }
                    },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                    },
                    onSpeak = { text, lang -> viewModel.speak(text, lang) }
                )
            }

            if (uiState is ChatUiState.Generating) {
                item {
                    val partial = (uiState as ChatUiState.Generating).partialMessage
                    AssistantBubble(text = partial.ifEmpty { "…" }, onSpeak = { text, lang -> viewModel.speak(text, lang) })
                }
            }

            if (uiState is ChatUiState.Loading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Start) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (uiState is ChatUiState.Error) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = (uiState as ChatUiState.Error).message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    onToggleBookmark: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: (String, String?) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = screenWidth * 0.85f)) {
            Box {
                Card(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { showMenu = true }
                            )
                        },
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    SelectionContainer {
                        FormattedMessageText(
                            text = message.text,
                            isUser = message.isUser,
                            onSpeak = onSpeak
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("复制文本") },
                        onClick = {
                            onCopy()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (message.isBookmarked) "取消收藏" else "收藏消息") },
                        onClick = {
                            onToggleBookmark()
                            showMenu = false
                        },
                        leadingIcon = { 
                            Icon(
                                if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            if (!message.isUser) {
                Row(modifier = Modifier.padding(top = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            modifier = Modifier.size(16.dp),
                            tint = if (message.isBookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantBubble(text: String, onSpeak: (String, String?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.8f).dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            FormattedMessageText(text = text, isUser = false, onSpeak = onSpeak)
        }
    }
}
