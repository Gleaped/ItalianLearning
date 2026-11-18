package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.speech.Endpointer
import com.gleaped.italianlearning.domain.speech.Endpointer.Decision
import com.gleaped.italianlearning.domain.speech.WhisperText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperSupportTest {
    @Test
    fun cleanRemovesNonSpeechTagsAndTidiesSpaces() {
        assertEquals("Buongiorno, come va?", WhisperText.clean(" [BLANK_AUDIO] Buongiorno,  come va? (musica)\n"))
        assertEquals("", WhisperText.clean("[BLANK_AUDIO]"))
        assertEquals("Sì, è già tardi.", WhisperText.clean("Sì, è già tardi."))
    }

    private fun run(endpointer: Endpointer, rms: Double, frames: Int): Decision {
        var last = Decision.CONTINUE
        repeat(frames) { if (last == Decision.CONTINUE) last = endpointer.onFrame(rms) }
        return last
    }

    @Test
    fun stopsAfterSpeechThenSilence() {
        val e = Endpointer()
        assertEquals(Decision.CONTINUE, run(e, 0.002, 5)) // quiet room
        assertEquals(Decision.CONTINUE, run(e, 0.1, 15)) // speaking
        assertTrue(e.heardSpeech)
        assertEquals(Decision.CONTINUE, run(e, 0.002, 12))
        assertEquals(Decision.STOP_AFTER_SPEECH, e.onFrame(0.002))
        assertTrue(e.trailingQuietFrames >= 13)
    }

    @Test
    fun givesUpWhenNothingIsSaid() {
        val e = Endpointer()
        assertEquals(Decision.STOP_NO_SPEECH, run(e, 0.003, 100))
        assertFalse(e.heardSpeech)
    }

    @Test
    fun aShortClickIsNotSpeech() {
        val e = Endpointer()
        run(e, 0.003, 3)
        run(e, 0.2, 1)
        assertEquals(Decision.STOP_NO_SPEECH, run(e, 0.003, 100))
        assertFalse(e.heardSpeech)
    }

    @Test
    fun stopsAtTheLimitEvenIfStillSpeaking() {
        val e = Endpointer()
        assertEquals(Decision.STOP_MAX, run(e, 0.1, 400))
    }
}
