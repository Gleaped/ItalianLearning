package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.domain.srs.Sm2
import com.gleaped.italianlearning.domain.srs.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2Test {
    private val today = 20_000L

    @Test
    fun goodProgressionFollowsSm2Intervals() {
        var s = SrsState(dueEpochDay = today)
        s = Sm2.review(s, Rating.GOOD, today)
        assertEquals(1, s.intervalDays)
        assertEquals(today + 1, s.dueEpochDay)
        s = Sm2.review(s, Rating.GOOD, today + 1)
        assertEquals(6, s.intervalDays)
        s = Sm2.review(s, Rating.GOOD, today + 7)
        // EF stays 2.5 with quality 4: 6 * 2.5 = 15
        assertEquals(2.5, s.easiness, 1e-9)
        assertEquals(15, s.intervalDays)
        assertEquals(today + 7 + 15, s.dueEpochDay)
        assertEquals(3, s.repetitions)
    }

    @Test
    fun againResetsAndLowersEasiness() {
        var s = SrsState(easiness = 2.5, repetitions = 3, intervalDays = 15, dueEpochDay = today)
        s = Sm2.review(s, Rating.AGAIN, today)
        assertEquals(0, s.repetitions)
        assertEquals(1, s.intervalDays)
        assertEquals(today + 1, s.dueEpochDay)
        assertEquals(1, s.lapses)
        assertTrue(s.easiness < 2.5)
    }

    @Test
    fun easinessNeverBelowMinimum() {
        var s = SrsState()
        repeat(20) { s = Sm2.review(s, Rating.AGAIN, today) }
        assertEquals(Sm2.MIN_EASINESS, s.easiness, 1e-9)
    }

    @Test
    fun easyBeatsGoodBeatsHard() {
        val base = SrsState(easiness = 2.5, repetitions = 2, intervalDays = 6, dueEpochDay = today)
        val hard = Sm2.review(base, Rating.HARD, today).intervalDays
        val good = Sm2.review(base, Rating.GOOD, today).intervalDays
        val easy = Sm2.review(base, Rating.EASY, today).intervalDays
        assertTrue("$hard < $good", hard < good)
        assertTrue("$good < $easy", good < easy)
        assertTrue(hard > 6)
    }

    @Test
    fun intervalsAreCapped() {
        val s = Sm2.review(SrsState(easiness = 2.5, repetitions = 9, intervalDays = 300), Rating.EASY, today)
        assertEquals(Sm2.MAX_INTERVAL_DAYS, s.intervalDays)
    }

    @Test
    fun dueAndFormat() {
        assertTrue(Sm2.isDue(SrsState(dueEpochDay = today), today))
        assertFalse(Sm2.isDue(SrsState(dueEpochDay = today + 1), today))
        assertEquals("6d", Sm2.formatInterval(6))
        assertEquals("2mo", Sm2.formatInterval(60))
    }
}
