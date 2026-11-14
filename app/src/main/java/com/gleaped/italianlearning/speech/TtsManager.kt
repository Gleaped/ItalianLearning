package com.gleaped.italianlearning.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.gleaped.italianlearning.domain.speech.SpeakableText
import com.gleaped.italianlearning.domain.speech.VoiceChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsStatus { INITIALISING, READY, NO_ITALIAN_VOICE, UNAVAILABLE }

/** An installed Italian voice the learner can choose in Settings. */
data class VoiceOption(val name: String, val label: String)

/**
 * Wraps Android TextToSpeech with an Italian (it-IT) voice. Created lazily and kept for
 * the app's lifetime; speech is offline when the device has the Italian voice data.
 *
 * The main voice is the learner's choice, or else the best-quality Italian voice installed.
 * Dialogues use a second voice for the other speaker; with only one voice installed, the
 * same voice is pitched lower instead so the speakers can still be told apart.
 */
class TtsManager(context: Context) {
    private val _status = MutableStateFlow(TtsStatus.INITIALISING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())

    /** Installed Italian voices that work offline, best first. */
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private var baseRate = 0.9f
    private var pending: Pair<String, Boolean>? = null
    private val italian: Locale = Locale.forLanguageTag("it-IT")
    private var installed: List<Voice> = emptyList()
    private var preferredName: String = ""
    private var mainVoice: Voice? = null
    private var secondVoice: Voice? = null

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
        refreshVoices()
        pending?.let { (text, slow) -> speak(text, slow) }
        pending = null
    }

    /** Re-reads the installed voices; called when Settings opens, as voices can be added at any time. */
    fun refreshVoices() {
        if (_status.value != TtsStatus.READY) return
        // Some engines throw or return null here; the engine's default voice is then used.
        val all = runCatching { tts.voices }.getOrNull().orEmpty().filterNotNull()
        val usable = all.filter { v ->
            v.locale?.language == "it" &&
                !v.isNetworkConnectionRequired &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        val order = VoiceChoice.rank(usable.map { VoiceChoice.Candidate(it.name, it.quality, it.locale?.country == "IT") })
        installed = order.mapNotNull { name -> usable.firstOrNull { it.name == name } }
        _voices.value = installed.mapIndexed { index, v ->
            VoiceOption(v.name, "Voce ${index + 1}" + if (v.quality >= Voice.QUALITY_HIGH) " (high quality)" else "")
        }
        choose()
    }

    /** The voice chosen in Settings by name; blank means the best one installed. */
    fun setPreferredVoice(name: String) {
        preferredName = name
        choose()
    }

    private fun choose() {
        val names = installed.map { it.name }
        mainVoice = VoiceChoice.main(names, preferredName)?.let { n -> installed.firstOrNull { it.name == n } }
        secondVoice = VoiceChoice.second(names, mainVoice?.name)?.let { n -> installed.firstOrNull { it.name == n } }
    }

    fun setRate(rate: Float) {
        baseRate = rate.coerceIn(0.5f, 1.5f)
    }

    /**
     * Speaks [text]. With [second], the other speaker's voice is used (role-play partner).
     * With [dialogue], lines separated by a dash are read by alternating voices.
     */
    fun speak(text: String, slow: Boolean = false, second: Boolean = false, dialogue: Boolean = false) {
        when (_status.value) {
            TtsStatus.INITIALISING -> pending = text to slow
            TtsStatus.READY -> {
                tts.setSpeechRate(if (slow) baseRate * 0.7f else baseRate)
                val turns = if (dialogue) SpeakableText.dialogueTurns(text) else listOf(text)
                turns.forEachIndexed { index, turn ->
                    useVoice(second = second != (index % 2 == 1))
                    val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val r = tts.speak(turn, mode, null, "it-${text.hashCode()}-$index")
                    if (r == TextToSpeech.ERROR) Log.w("TtsManager", "speak failed")
                }
            }
            TtsStatus.NO_ITALIAN_VOICE, TtsStatus.UNAVAILABLE -> Unit
        }
    }

    private fun useVoice(second: Boolean) {
        val other = secondVoice
        val voice = if (second && other != null) other else mainVoice
        // A failure here leaves the engine's current voice in place, which is still Italian.
        if (voice != null) runCatching { tts.setVoice(voice) }
        tts.setPitch(if (second && other == null) SECOND_SPEAKER_PITCH else 1.0f)
    }

    fun stop() {
        if (_status.value == TtsStatus.READY) tts.stop()
    }

    private companion object {
        const val SECOND_SPEAKER_PITCH = 0.8f
    }
}
