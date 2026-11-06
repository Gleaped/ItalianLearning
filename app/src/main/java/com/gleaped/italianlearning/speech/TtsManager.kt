package com.gleaped.italianlearning.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsStatus { INITIALISING, READY, NO_ITALIAN_VOICE, UNAVAILABLE }

/**
 * Wraps Android TextToSpeech with an Italian (it-IT) voice. Created lazily and kept for
 * the app's lifetime; speech is offline when the device has the Italian voice data.
 */
class TtsManager(context: Context) {
    private val _status = MutableStateFlow(TtsStatus.INITIALISING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private var baseRate = 0.9f
    private var pending: Pair<String, Boolean>? = null
    private val italian: Locale = Locale.forLanguageTag("it-IT")

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { result ->
        onInit(result)
    }

    private fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            _status.value = TtsStatus.UNAVAILABLE
            return
        }
        val lang = tts.setLanguage(italian)
        _status.value = if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            TtsStatus.NO_ITALIAN_VOICE
        } else {
            TtsStatus.READY
        }
        pending?.let { (text, slow) -> speak(text, slow) }
        pending = null
    }

    fun setRate(rate: Float) {
        baseRate = rate.coerceIn(0.5f, 1.5f)
    }

    fun speak(text: String, slow: Boolean = false) {
        when (_status.value) {
            TtsStatus.INITIALISING -> pending = text to slow
            TtsStatus.READY -> {
                tts.setSpeechRate(if (slow) baseRate * 0.7f else baseRate)
                val r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "it-${text.hashCode()}")
                if (r == TextToSpeech.ERROR) Log.w("TtsManager", "speak failed")
            }
            TtsStatus.NO_ITALIAN_VOICE, TtsStatus.UNAVAILABLE -> Unit
        }
    }

    fun stop() {
        if (_status.value == TtsStatus.READY) tts.stop()
    }
}
