package com.example.fakeocat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fakeocat.data.PreferencesManager
import com.example.fakeocat.data.db.DatabaseHelper
import com.example.fakeocat.data.db.entity.BookmarkEntity
import com.example.fakeocat.data.db.entity.MessageEntity
import com.example.fakeocat.network.LlmClient
import com.example.fakeocat.network.ResponseCache
import com.example.fakeocat.network.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel 的 UI 状态密封类 */
sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Generating(val partialMessage: String) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

/**
 * ChatViewModel —— 精简后仅保留 UI 状态管理和流程编排。
 *
 * Prompt 构建 → [PromptBuilder]
 * 流式请求编排 → [StreamOrchestrator]
 * 语言配置管理 → [LanguageConfigManager]
 * 会话/书签 CRUD → 直接委托 [DatabaseHelper]
 *
 * 生产环境通过 [Factory] 创建，测试可直接传入 mock 依赖。
 */
class ChatViewModel internal constructor(
    val prefs: PreferencesManager,
    private val dbHelper: DatabaseHelper,
    private val llmClient: LlmClient,
    private val ttsManager: TtsManager,
    private val responseCache: ResponseCache
) : ViewModel() {
    private val TAG = "ChatViewModel"

    // ══════════════════════════════════════════════
    // 辅助类（纯工具类在 init 中直接创建）
    // ══════════════════════════════════════════════
    private val promptBuilder = PromptBuilder()
    private val languageConfigManager = LanguageConfigManager(prefs, viewModelScope)
    private val streamOrchestrator = StreamOrchestrator(
        llmClient, dbHelper, prefs, responseCache
    )

    // ══════════════════════════════════════════════
    // 语言与主题配置（通过 LanguageConfigManager 中转）
    // ══════════════════════════════════════════════
    val appLanguage: StateFlow<String> = languageConfigManager.appLanguage
    val nativeLanguage: StateFlow<String> = languageConfigManager.nativeLanguage
    val targetLanguage: StateFlow<String> = languageConfigManager.targetLanguage

    val themeMode = prefs.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val selectedProvider = prefs.selectedProviderFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "openai")

    // ══════════════════════════════════════════════
    // 会话与书签（DB 操作调度到 IO 线程，避免首次创建数据库时阻塞主线程）
    // ══════════════════════════════════════════════
    val chatHistory: StateFlow<List<MessageEntity>> = dbHelper.getAllMessagesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory

    val displayMessages: StateFlow<List<MessageEntity>> = kotlinx.coroutines.flow.combine(
        chatHistory, _currentSessionMessageIds, _showHistory
    ) { history, sessionIds, showFull ->
        if (showFull) history else history.filter { it.id in sessionIds }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedMessages: StateFlow<List<BookmarkEntity>> = dbHelper.getBookmarkedMessagesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadHistoryIntoDisplay() { _showHistory.value = true }
    fun startNewChat() { _showHistory.value = false; _currentSessionMessageIds.value = emptySet() }

    // ══════════════════════════════════════════════
    // UI 状态
    // ══════════════════════════════════════════════
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _isRequestInFlight = MutableStateFlow(false)
    val isRequestInFlight: StateFlow<Boolean> = _isRequestInFlight

    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvents: kotlinx.coroutines.flow.Flow<String> = _uiEvents

    private var generationJob: Job? = null

    // ══════════════════════════════════════════════
    // 模式与语言覆盖
    // ══════════════════════════════════════════════
    private val _currentMode = MutableStateFlow("HowToSay")
    val currentMode: StateFlow<String> = _currentMode

    private val _howToSayLang = MutableStateFlow<String?>(null)
    val howToSayLang: StateFlow<String?> = _howToSayLang

    fun setMode(mode: String) { _currentMode.value = mode }
    fun setHowToSayLang(lang: String) { _howToSayLang.value = lang }
    fun getEffectiveTargetLang(): String = _howToSayLang.value ?: targetLanguage.value

    // ══════════════════════════════════════════════
    // sendMessage —— 流程编排入口
    // ══════════════════════════════════════════════
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        generationJob?.cancel()
        _isRequestInFlight.value = true
        generationJob = viewModelScope.launch {
            val mode = _currentMode.value
            val native = nativeLanguage.value
            val target = if (mode == "HowToSay") getEffectiveTargetLang() else targetLanguage.value

            // 持久化用户消息
            val userMsg = MessageEntity(text = userText, isUser = true, mode = mode)
            val userId = dbHelper.insertMessage(userMsg)
            _currentSessionMessageIds.value += userId

            _uiState.value = ChatUiState.Loading

            // 构建 Prompt
            val systemPrompt = promptBuilder.buildSystemPrompt(mode, native, target)
            val userPrompt = promptBuilder.buildUserPrompt(mode, userText, target)

            // 委托 StreamOrchestrator 执行流式请求
            streamOrchestrator.executeStreamRequest(
                mode = mode,
                userId = userId,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                userText = userText,
                onToken = { fullText ->
                    _uiState.value = ChatUiState.Generating(fullText)
                },
                onFallback = { msg ->
                    _uiEvents.tryEmit(msg)
                },
                onComplete = { assistantMsg ->
                    _currentSessionMessageIds.value += assistantMsg.id
                    _uiState.value = ChatUiState.Idle
                    _isRequestInFlight.value = false
                    generationJob = null
                },
                onCancelled = {
                    _uiEvents.tryEmit("已停止")
                    _uiState.value = ChatUiState.Idle
                    _isRequestInFlight.value = false
                },
                onError = { e ->
                    _uiState.value = ChatUiState.Error(e.message ?: "Unexpected error")
                    _isRequestInFlight.value = false
                    generationJob = null
                }
            )
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isRequestInFlight.value = false
        _uiState.value = ChatUiState.Idle
    }

    // ══════════════════════════════════════════════
    // 书签 & 消息管理
    // ══════════════════════════════════════════════
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

    // ══════════════════════════════════════════════
    // TTS
    // ══════════════════════════════════════════════
    fun speak(text: String, lang: String? = null) {
        ttsManager.speak(text, lang ?: getEffectiveTargetLang())
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        ttsManager.shutdown()
    }

    /**
     * 生产环境使用的 ViewModelProvider.Factory。
     * 从 [Application] 创建所有依赖并注入 [ChatViewModel]。
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val prefs = PreferencesManager(application)
            val dbHelper = DatabaseHelper(application)
            val llmClient = LlmClient()
            val ttsManager = TtsManager(application)
            val responseCache = ResponseCache(application)
            return ChatViewModel(prefs, dbHelper, llmClient, ttsManager, responseCache) as T
        }
    }
}
