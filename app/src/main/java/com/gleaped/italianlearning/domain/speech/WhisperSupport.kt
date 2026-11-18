package com.gleaped.italianlearning.domain.speech

import kotlin.math.max

/** Tidies the text Whisper returns. */
object WhisperText {
    private val tags = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*""")
    private val spaces = Regex("""[\s\u00a0\u2000-\u200a\u202f\u205f\u3000]+""")

    /** Whisper marks non-speech as "[BLANK_AUDIO]", "(musica)" and the like; those are not words. */
    fun clean(raw: String): String = raw.replace(tags, " ").replace(spaces, " ").trim()
}

/**
 * Decides when to stop listening from the loudness (RMS, 0 to 1) of consecutive audio frames:
 * after speech has begun and then fallen quiet for [silenceMs], when nothing is said for
 * [noSpeechMs], or at [maxMs].
 */
class Endpointer(
    private val frameMs: Int = 100,
    private val silenceMs: Int = 1_300,
    private val noSpeechMs: Int = 8_000,
    private val maxMs: Int = 30_000,
    private val minThreshold: Double = 0.012,
) {
    enum class Decision { CONTINUE, STOP_AFTER_SPEECH, STOP_NO_SPEECH, STOP_MAX }

    private var frames = 0
    private var noiseFloor = 0.0
    private var speechFrames = 0
    private var quietRun = 0

    /** Frames of speech heard so far. */
    val speechMillis: Int get() = speechFrames * frameMs

    /** Quiet frames at the end of what was heard; the caller may trim them. */
    val trailingQuietFrames: Int get() = quietRun

    val heardSpeech: Boolean get() = speechMillis >= MIN_SPEECH_MS

    fun onFrame(rms: Double): Decision {
        frames++
        val threshold = max(minThreshold, noiseFloor * 3.0)
        if (rms > threshold) {
            speechFrames++
            quietRun = 0
        } else {
            quietRun++
            // The room's background level, learned only from frames that were not speech.
            noiseFloor = if (noiseFloor == 0.0) rms else noiseFloor * 0.9 + rms * 0.1
        }
        val elapsed = frames * frameMs
        return when {
            elapsed >= maxMs -> Decision.STOP_MAX
            heardSpeech && quietRun * frameMs >= silenceMs -> Decision.STOP_AFTER_SPEECH
            !heardSpeech && elapsed >= noSpeechMs -> Decision.STOP_NO_SPEECH
            else -> Decision.CONTINUE
        }
    }

    companion object {
        /** Less than this much speech is treated as noise, which Whisper would otherwise "transcribe". */
        const val MIN_SPEECH_MS = 300
    }
}
