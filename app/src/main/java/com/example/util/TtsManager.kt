package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs: SharedPreferences = context.getSharedPreferences("tts_preferences", Context.MODE_PRIVATE)

    private var currentSpeechRate: Float = prefs.getFloat("tts_rate", 0.9f)

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsManager", "Korean TTS language not supported or missing data")
                AppLogManager.log("TtsManager", "Ngôn ngữ TTS tiếng Hàn chưa được cài đặt trên thiết bị", "WARN")
            } else {
                isInitialized = true
                AppLogManager.log("TtsManager", "Khởi tạo TextToSpeech thành công (Tốc độ mặc định: ${currentSpeechRate}x)", "INFO")
            }
        } else {
            Log.e("TtsManager", "TTS initialization failed")
            AppLogManager.log("TtsManager", "Khởi tạo TextToSpeech thất bại (status=$status)", "ERROR")
        }
    }

    fun getSpeechRate(): Float = currentSpeechRate

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat("tts_rate", currentSpeechRate).apply()
        AppLogManager.log("TtsManager", "Cập nhật tốc độ đọc TTS: ${currentSpeechRate}x", "INFO")
    }

    fun speakKorean(text: String, rate: Float? = null) {
        if (text.isBlank()) return
        val targetRate = rate ?: currentSpeechRate
        AppLogManager.log("TTS", "Phát âm tiếng Hàn (${targetRate}x): \"${text.take(40)}\"", "INFO")
        tts?.apply {
            language = Locale.KOREAN
            setSpeechRate(targetRate)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "KR_${System.currentTimeMillis()}")
        }
    }

    fun speakVietnamese(text: String, rate: Float? = null) {
        if (text.isBlank()) return
        val targetRate = rate ?: currentSpeechRate
        AppLogManager.log("TTS", "Phát âm tiếng Việt (${targetRate}x): \"${text.take(40)}\"", "INFO")
        tts?.apply {
            language = Locale("vi", "VN")
            setSpeechRate(targetRate)
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
