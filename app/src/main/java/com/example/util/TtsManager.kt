package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsManager", "Korean TTS language not supported or missing data")
            } else {
                isInitialized = true
            }
        } else {
            Log.e("TtsManager", "TTS initialization failed")
        }
    }

    fun speakKorean(text: String, rate: Float = 0.9f) {
        if (text.isBlank()) return
        tts?.apply {
            language = Locale.KOREAN
            setSpeechRate(rate)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "KR_${System.currentTimeMillis()}")
        }
    }

    fun speakVietnamese(text: String, rate: Float = 1.0f) {
        if (text.isBlank()) return
        tts?.apply {
            language = Locale("vi", "VN")
            setSpeechRate(rate)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "VI_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
