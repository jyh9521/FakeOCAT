package com.example.fakeocat.ui.viewmodel

import com.example.fakeocat.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 管理应用语言、母语、目标语言等语言配置的读取与持久化。
 * 从 ChatViewModel 中拆分，降低 ViewModel 对 PreferencesManager 的直接耦合。
 */
class LanguageConfigManager(
    private val prefs: PreferencesManager,
    scope: CoroutineScope
) {
    /** 界面语言（"system" 表示跟随系统） */
    val appLanguage: StateFlow<String> = prefs.appLanguageFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "system")

    /** 用户母语（默认为中文） */
    val nativeLanguage: StateFlow<String> = prefs.nativeLanguageFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "zh")

    /** 目标学习语言（默认为日语） */
    val targetLanguage: StateFlow<String> = prefs.targetLanguageFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "ja")

    /** 更新界面语言并持久化 */
    suspend fun updateAppLanguage(lang: String) {
        prefs.setAppLanguage(lang)
    }
}
