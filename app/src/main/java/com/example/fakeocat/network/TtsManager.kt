package com.example.fakeocat.network

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(
    private val appContext: Context
) {
    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady = false

    /**
     * 懒初始化 TTS 引擎，避免在构造函数中阻塞主线程。
     * 首次 speak() 调用时才会创建 TextToSpeech 实例。
     */
    private fun ensureInitialized() {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            tts = TextToSpeech(appContext.applicationContext) { status ->
                isReady = (status == TextToSpeech.SUCCESS)
            }
        }
    }

    fun speak(text: String, langCode: String) {
        ensureInitialized()
        if (!isReady) return
        val locale = when (langCode) {
            "zh" -> Locale.CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "es" -> Locale("es")
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "pt" -> Locale("pt")
            "ru" -> Locale("ru")
            "it" -> Locale.ITALIAN
            "ar" -> Locale("ar")
            "th" -> Locale("th")
            "vi" -> Locale("vi")
            "id" -> Locale("id")
            "hi" -> Locale("hi")
            "tr" -> Locale("tr")
            "nl" -> Locale("nl")
            "sv" -> Locale("sv")
            "pl" -> Locale("pl")
            "cs" -> Locale("cs")
            "el" -> Locale("el")
            "he" -> Locale("iw") // Android 使用 "iw" 表示希伯来语
            else -> Locale.getDefault()
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fakeocat_tts")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
