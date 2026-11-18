package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.oral.ExamTask
import com.gleaped.italianlearning.domain.oral.FluencyMeter
import com.gleaped.italianlearning.domain.oral.OralFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluencyMeterTest {
    private fun meter(vararg runs: Pair<Int, Int>): FluencyMeter {
        val m = FluencyMeter(frameMillis = 200)
        for ((level, frames) in runs) repeat(frames) { m.add(level) }
        return m
    }

    @Test
    fun silenceBeforeAndAfterSpeechIsNotAPause() {
        val f = meter(100 to 10, 8000 to 25, 100 to 10).result()
        assertEquals(0, f.longPauses)
        assertEquals(100, f.speakingPercent)
        assertEquals(9, f.seconds)
    }

    @Test
    fun aPauseInTheMiddleIsFound() {
        val f = meter(8000 to 15, 100 to 10, 8000 to 15).result()
        assertEquals(1, f.longPauses)
        assertEquals(2.0, f.longestPauseSeconds, 0.001)
        assertTrue(f.speakingPercent in 60..80)
    }

    @Test
    fun aShortBreathIsNotLong() {
        val f = meter(8000 to 15, 100 to 4, 8000 to 15).result()
        assertEquals(0, f.longPauses)
    }

    @Test
    fun noSpeechAtAll() {
        val f = meter(50 to 30).result()
        assertEquals(0, f.speakingPercent)
        assertEquals(0, FluencyMeter().result().seconds)
    }

    @Test
    fun commentsAndTheRoughScore() {
        val task = ExamTask("photo", "Foto", listOf("x"), minSeconds = 60, maxSeconds = 120)
        val short = meter(8000 to 100).result() // 20 s
        assertTrue(OralFeedback.comments(short, task).first().contains("Aim for at least"))
        val good = meter(8000 to 400).result() // 80 s
        assertTrue(OralFeedback.comments(good, task).first().contains("good length"))
        assertNull(OralFeedback.estimate(emptyMap()))
        assertEquals(60, OralFeedback.estimate(mapOf("lessico" to 5, "grammatica" to 5, "sociolinguistica" to 5, "fonetica" to 5)))
        assertTrue(OralFeedback.passes(OralFeedback.estimate(mapOf("lessico" to 3, "grammatica" to 3))!!))
        assertEquals(false, OralFeedback.passes(OralFeedback.estimate(mapOf("lessico" to 2))!!))
    }
}
