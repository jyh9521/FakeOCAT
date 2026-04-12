package com.example.fakeocat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeocat.data.PreferencesManager
import com.example.fakeocat.data.db.DatabaseHelper
import com.example.fakeocat.data.db.entity.BookmarkEntity
import com.example.fakeocat.data.db.entity.MessageEntity
import com.example.fakeocat.network.AiProviderCatalog
import com.example.fakeocat.network.LlmClient
import com.example.fakeocat.network.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Generating(val partialMessage: String) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

data class StreamAttemptResult(
    val succeeded: Boolean,
    val abortFurtherTries: Boolean,
    val errorMessage: String?
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ChatViewModel"
    private val dbHelper = DatabaseHelper.getInstance(application)
    val prefs = PreferencesManager(application)
    private val llmClient = LlmClient()
    private val ttsManager = TtsManager(application)

    val chatHistory: StateFlow<List<MessageEntity>> = dbHelper.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 记录当前“新会话”里发送消息的 ID。
    private val _currentSessionMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    
    // 决定显示完整历史，还是仅显示当前会话。
    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory

    /**
     * 实际用于展示的消息列表。
     * 当 showHistory 为 true 时，显示数据库中的全部消息。
     * 当 showHistory 为 false 时，只显示当前会话中的消息。
     */
    val displayMessages: StateFlow<List<MessageEntity>> = kotlinx.coroutines.flow.combine(
        chatHistory, _currentSessionMessageIds, _showHistory
    ) { history, sessionIds, showFull ->
        if (showFull) history else history.filter { it.id in sessionIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadHistoryIntoDisplay() {
        _showHistory.value = true
    }

    fun startNewChat() {
        _showHistory.value = false
        _currentSessionMessageIds.value = emptySet()
    }

    val bookmarkedMessages: StateFlow<List<BookmarkEntity>> = dbHelper.getBookmarkedMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode = prefs.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val nativeLanguage = prefs.nativeLanguageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh")
    val targetLanguage = prefs.targetLanguageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ja")
    val selectedProvider = prefs.selectedProviderFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "openai")
    val appLanguage = prefs.appLanguageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _isRequestInFlight = MutableStateFlow(false)
    val isRequestInFlight: StateFlow<Boolean> = _isRequestInFlight

    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvents: kotlinx.coroutines.flow.Flow<String> = _uiEvents

    private var generationJob: Job? = null

    // 当前模式："HowToSay"、"WhatMeans"、"FreeChat"
    private val _currentMode = MutableStateFlow("HowToSay")
    val currentMode: StateFlow<String> = _currentMode

    // “怎么说”模式下的目标语言覆盖值（用户可在主界面切换）。
    private val _howToSayLang = MutableStateFlow<String?>(null)
    val howToSayLang: StateFlow<String?> = _howToSayLang

    fun setMode(mode: String) { _currentMode.value = mode }

    fun setHowToSayLang(lang: String) { _howToSayLang.value = lang }

    /** 返回“怎么说”模式下生效的目标语言。 */
    fun getEffectiveTargetLang(): String = _howToSayLang.value ?: targetLanguage.value

    companion object {
        val supportedLanguages = listOf(
            "zh", "en", "ja", "ko", "es", "fr", "de", "pt", "ru", "it", 
            "nl", "sv", "pl", "cs", "el", "he", "ar", "th", "vi", "id", "hi", "tr"
        )

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

        fun getDefaultModel(provider: String): String = when (provider) {
            else -> AiProviderCatalog.getProvider(provider)?.model ?: "gpt-5.4-mini"
        }

        fun getFallbackModel(provider: String): String = when (provider) {
            else -> getDefaultModel(provider)
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        generationJob?.cancel()
        _isRequestInFlight.value = true
        generationJob = viewModelScope.launch {
            val mode = _currentMode.value
            val native = nativeLanguage.value
            val target = if (mode == "HowToSay") getEffectiveTargetLang() else targetLanguage.value
            
            val userMsg = MessageEntity(text = userText, isUser = true, mode = mode)
            val userId = dbHelper.insertMessage(userMsg)
            _currentSessionMessageIds.value += userId

            _uiState.value = ChatUiState.Loading

            var provider = selectedProvider.value
            var apiKey = prefs.apiKeyFlowFor(provider).first()
            
            Log.d(TAG, "Initial check - Provider: $provider, Key Length: ${apiKey.length}")

            if (apiKey.isBlank()) {
                Log.d(TAG, "API Key for $provider is missing. Searching for any available key...")
                val fallback = prefs.findAnyAvailableKey()
                if (fallback != null) {
                    provider = fallback.first
                    apiKey = fallback.second
                    Log.d(TAG, "Fallback successful! Using $provider key instead.")
                } else {
                    Log.e(TAG, "No API Keys found in storage!")
                    _uiState.value = ChatUiState.Error("API Key is missing for $provider! Please configure it in Settings.")
                    _isRequestInFlight.value = false
                    return@launch
                }
            }

            val model = getDefaultModel(provider)
            Log.d(TAG, "Using model: $model for provider: $provider")
            val systemPrompt = buildSystemPrompt(mode, native, target)
            val userPrompt = buildUserPrompt(mode, userText, target)

            var assistantReply = ""
            _uiState.value = ChatUiState.Generating("")

            try {
                val modelsToTry = if (provider == "gemini") listOf(model) else listOf(model, getFallbackModel(provider)).distinct()
                val baseUrlOverride = AiProviderCatalog.getProvider(provider)?.chatCompletionsUrl

                var succeeded = false
                var lastError: String? = null
                for (m in modelsToTry) {
                    if (succeeded) break
                    val result = streamWithRetry(
                        provider = provider,
                        apiKey = apiKey,
                        model = m,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        baseUrlOverride = baseUrlOverride,
                        onChunk = { chunk ->
                            assistantReply += chunk
                            _uiState.value = ChatUiState.Generating(assistantReply)
                        }
                    )
                    succeeded = result.succeeded
                    if (!succeeded) {
                        lastError = result.errorMessage
                        if (result.abortFurtherTries || assistantReply.isNotBlank()) break
                    }
                }

                if (!succeeded) {
                    _uiState.value = ChatUiState.Error(lastError ?: "Network error")
                }
            } catch (_: CancellationException) {
                if (assistantReply.isNotBlank()) {
                    val assistantMsg = MessageEntity(text = assistantReply, isUser = false, mode = mode)
                    val assistantId = dbHelper.insertMessage(assistantMsg)
                    _currentSessionMessageIds.value += assistantId
                }
                _uiEvents.tryEmit("已停止")
                _uiState.value = ChatUiState.Idle
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in sendMessage: ${e.message}", e)
                _uiState.value = ChatUiState.Error(e.message ?: "Unexpected error")
            }

            if (assistantReply.isNotEmpty()) {
                val assistantMsg = MessageEntity(text = assistantReply, isUser = false, mode = mode)
                val assistantId = dbHelper.insertMessage(assistantMsg)
                _currentSessionMessageIds.value += assistantId
                _uiState.value = ChatUiState.Idle
            }

            _isRequestInFlight.value = false
            generationJob = null
        }.also {
            it.invokeOnCompletion { _isRequestInFlight.value = false }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isRequestInFlight.value = false
        _uiState.value = ChatUiState.Idle
    }

    private suspend fun streamWithRetry(
        provider: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        baseUrlOverride: String?,
        onChunk: (String) -> Unit
    ): StreamAttemptResult {
        var lastError: String? = null
        for (attempt in 0 until 3) {
            val receivedAny = AtomicBoolean(false)
            try {
                Log.d(TAG, "Starting streamChat attempt=${attempt + 1} provider=$provider model=$model")
                llmClient.streamChat(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride)
                    .collect { chunk ->
                        receivedAny.set(true)
                        onChunk(chunk)
                    }
                return StreamAttemptResult(succeeded = true, abortFurtherTries = false, errorMessage = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Stream error provider=$provider model=$model attempt=${attempt + 1}: ${e.message}")
                lastError = e.message
                if (provider == "gemini" && (lastError?.contains("HTTP 429") == true || lastError?.contains("HTTP 404") == true)) {
                    return StreamAttemptResult(succeeded = false, abortFurtherTries = true, errorMessage = lastError)
                }
                if (receivedAny.get()) {
                    return StreamAttemptResult(succeeded = false, abortFurtherTries = true, errorMessage = lastError)
                }
                if (attempt == 2) return StreamAttemptResult(succeeded = false, abortFurtherTries = false, errorMessage = lastError)
                delay(500L * (attempt + 1))
            }
        }
        return StreamAttemptResult(succeeded = false, abortFurtherTries = false, errorMessage = lastError)
    }

    private fun buildUserPrompt(mode: String, userText: String, target: String): String {
        val targetName = langFullNameCn(target)
        return when (mode) {
            "HowToSay" -> "\"$userText\" 用${targetName}怎么说？请提供口语和敬语（或正式/非正式）两种表达，然后逐一解析每个表达的含义和用法，最后给出其他相关句型。"
            "WhatMeans" -> "请详细解释「$userText」是什么意思，包括读音、词性、释义、使用场景和例句。"
            else -> userText
        }
    }

    private fun buildSystemPrompt(mode: String, native: String, target: String): String {
        val nativeName = langFullNameCn(native)
        val targetName = langFullNameCn(target)
        
        // 定义可点击发音按钮的通用格式说明。
        val formattingInstruction = """
        当你提到${targetName}或其他外语中的特定单词或短语时，请务必使用以下格式以便系统生成发音按钮：
        [单词或短语](发音/假名|国际音标|语言代码)
        如果语言是${targetName}，语言代码请填 "$target"。
        例如：[雹](ひょう|çjaʊ|$target) 或 [Bonjour](bonjour|bɔ̃ʒuʁ|fr)。
        """

        return when (mode) {
            "HowToSay" -> """你是一个专业的语言学习助手，帮助母语为${nativeName}的用户学习${targetName}。
$formattingInstruction
用户会给你一个词或句子，请你翻译成${targetName}。请严格按照以下格式回复：

1. 先给出口语（非正式）和敬语（正式）两种翻译，每种翻译中的关键词请使用 [单词](读音|IPA) 格式。
2. 然后逐条详细解析每个翻译中的生词、语法点和使用场景。
3. 最后给出2-3个其他相关的自然表达。

请确保所有${targetName}关键内容都标注读音。所有解释说明请使用${nativeName}。"""

            "WhatMeans" -> """你是一个语言学习助手，帮助母语为${nativeName}的用户理解外语内容。
$formattingInstruction
请对用户提供的内容进行详细解析：

1. 读音和国际音标（使用 [单词](读音|IPA) 格式）
2. 词性和基本释义
3. 详细解释和文化背景
4. 2-3个例句（附带${nativeName}翻译，例句中的关键词也请使用 [单词](读音|IPA) 格式）
5. 近义词和反义词

所有解释说明请使用${nativeName}。"""

            else -> "你是一个友好的对话伙伴，帮助母语为${nativeName}的用户练习${targetName}。用自然的方式与用户聊天，适当使用${targetName}并附上${nativeName}解释。提到关键词时请使用 [单词](读音|IPA) 格式。"
        }
    }

    fun toggleBookmark(message: MessageEntity) {
        viewModelScope.launch {
            if (message.id <= 0) return@launch
            val isBookmarked = dbHelper.isMessageBookmarked(message.id)
            if (isBookmarked) {
                dbHelper.removeBookmarkBySourceMessageId(message.id)
            } else {
                dbHelper.addBookmarkFromMessage(message)
            }
        }
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { dbHelper.removeBookmarkById(bookmark.id) }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { dbHelper.deleteMessage(message) }
    }

    fun clearHistory() {
        viewModelScope.launch { 
            dbHelper.clearHistory()
            startNewChat()
        }
    }

    fun speak(text: String, lang: String? = null) {
        ttsManager.speak(text, lang ?: getEffectiveTargetLang())
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        ttsManager.shutdown()
    }
}
