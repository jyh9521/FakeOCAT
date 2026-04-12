package com.example.fakeocat.network

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = (status == TextToSpeech.SUCCESS)
        }
    }

    fun speak(text: String, langCode: String) {
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
            "he" -> Locale("iw") // Android uses "iw" for Hebrew
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
