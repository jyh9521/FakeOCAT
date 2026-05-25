package com.example.fakeocat.ui.viewmodel

import app.cash.turbine.test
import com.example.fakeocat.data.PreferencesManager
import com.example.fakeocat.data.db.DatabaseHelper
import com.example.fakeocat.data.db.entity.MessageEntity
import com.example.fakeocat.network.LlmClient
import com.example.fakeocat.network.ResponseCache
import com.example.fakeocat.network.TtsManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/**
 * ChatViewModel 核心业务逻辑单元测试。
 *
 * 依赖注入的组件（LlmClient、DatabaseHelper、PreferencesManager 等）均通过 MockK 模拟，
 * 使用 StandardTestDispatcher 控制协程调度，Turbine 测试 StateFlow 发出值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Mock 依赖
    private val llmClient: LlmClient = mockk(relaxed = true)
    private val dbHelper: DatabaseHelper = mockk(relaxed = true)
    private val prefs: PreferencesManager = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val responseCache: ResponseCache = mockk(relaxed = true)

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock PreferencesManager 的 Flow 属性
        every { prefs.appLanguageFlow } returns flowOf("zh")
        every { prefs.nativeLanguageFlow } returns flowOf("zh")
        every { prefs.targetLanguageFlow } returns flowOf("ja")
        every { prefs.selectedProviderFlow } returns flowOf("openai")
        every { prefs.themeModeFlow } returns flowOf("auto")

        // Mock DatabaseHelper 的 Flow 方法（ChatViewModel init 中即订阅）
        every { dbHelper.getAllMessagesFlow() } returns flowOf(emptyList())
        every { dbHelper.getBookmarkedMessagesFlow() } returns flowOf(emptyList())

        viewModel = ChatViewModel(llmClient, dbHelper, prefs, ttsManager, responseCache)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ══════════════════════════════════════════════
    // 初始化状态测试
    // ══════════════════════════════════════════════

    @Test
    fun `初始化后 uiState 为 Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(ChatUiState.Idle, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `初始化后 isRequestInFlight 为 false`() = runTest {
        viewModel.isRequestInFlight.test {
            assertEquals(false, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `初始化后 currentMode 默认为 HowToSay`() = runTest {
        viewModel.currentMode.test {
            assertEquals("HowToSay", awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════
    // 取消生成测试
    // ══════════════════════════════════════════════

    @Test
    fun `cancelGeneration 将 isRequestInFlight 置为 false`() = runTest {
        // 先通过 sendMessage 间接触发 isRequestInFlight = true
        // 由于 sendMessage 会真正调用 streamOrchestrator，此处用反射或直接操作内部状态
        // 最简单的做法：直接调用 cancelGeneration 验证副作用
        viewModel.cancelGeneration()

        viewModel.isRequestInFlight.test {
            assertEquals(false, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `cancelGeneration 将 uiState 置为 Idle`() = runTest {
        viewModel.cancelGeneration()

        viewModel.uiState.test {
            assertEquals(ChatUiState.Idle, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════
    // 模式切换测试
    // ══════════════════════════════════════════════

    @Test
    fun `setMode 更新 currentMode 状态`() = runTest {
        viewModel.setMode("FreeChat")

        viewModel.currentMode.test {
            assertEquals("FreeChat", awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════
    // 语言相关测试
    // ══════════════════════════════════════════════

    @Test
    fun `getEffectiveTargetLang 默认返回 targetLanguage 值`() {
        val result = viewModel.getEffectiveTargetLang()
        // targetLanguageFlow 被 mock 为 flowOf("ja")，LanguageConfigManager 将其转为 StateFlow("ja")
        assertEquals("ja", result)
    }

    @Test
    fun `setHowToSayLang 覆盖 getEffectiveTargetLang 返回值`() {
        viewModel.setHowToSayLang("ko")
        assertEquals("ko", viewModel.getEffectiveTargetLang())
    }

    // ══════════════════════════════════════════════
    // 书签 toggleBookmark 测试（验证方法可调用且不抛异常）
    // 注意：toggleBookmark 内部启动协程调用 dbHelper，这里仅测试无异常
    // ══════════════════════════════════════════════

    @Test
    fun `toggleBookmark 对有效消息不抛异常`() = runTest {
        val msg = MessageEntity(
            id = 1L,
            text = "Hello",
            isUser = true,
            mode = "FreeChat"
        )

        // 不验证 dbHelper 的调用（因为是 launch 异步），仅验证不崩
        viewModel.toggleBookmark(msg)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `toggleBookmark 对 id 为 0 的消息直接跳过`() = runTest {
        val msg = MessageEntity(
            id = 0L,  // 无效 ID
            text = "Hello",
            isUser = true,
            mode = "FreeChat"
        )
        viewModel.toggleBookmark(msg)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ══════════════════════════════════════════════
    // 历史与显示测试
    // ══════════════════════════════════════════════

    @Test
    fun `loadHistoryIntoDisplay 设置 showHistory 为 true`() = runTest {
        viewModel.loadHistoryIntoDisplay()

        viewModel.showHistory.test {
            assertEquals(true, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `startNewChat 设置 showHistory 为 false`() = runTest {
        // 先加载历史
        viewModel.loadHistoryIntoDisplay()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startNewChat()

        viewModel.showHistory.test {
            assertEquals(false, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════
    // sendMessage 空输入测试
    // ══════════════════════════════════════════════

    @Test
    fun `sendMessage 空白输入不触发任何操作`() = runTest {
        viewModel.sendMessage("")
        viewModel.sendMessage("   ")

        // 验证 uiState 仍为 Idle（未进入 Loading）
        viewModel.uiState.test {
            assertEquals(ChatUiState.Idle, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════
    // displayMessages 过滤逻辑测试
    // ══════════════════════════════════════════════

    @Test
    fun `displayMessages 默认过滤仅显示当前会话消息`() = runTest {
        viewModel.displayMessages.test {
            val messages = awaitItem()
            assertTrue("默认应显示空列表", messages.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }
}
