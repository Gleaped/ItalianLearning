package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.srs.Fsrs
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.domain.srs.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val today = 20_000L

    @Test
    fun firstReviewsFollowDefaultStability() {
        val good = Fsrs.review(SrsState(dueEpochDay = today), Rating.GOOD, today)
        assertEquals(4, good.intervalDays)
        assertEquals(1, good.repetitions)
        assertTrue(good.easiness < 0)
        val easy = Fsrs.review(SrsState(dueEpochDay = today), Rating.EASY, today)
        assertEquals(14, easy.intervalDays)
        val again = Fsrs.review(SrsState(dueEpochDay = today), Rating.AGAIN, today)
        assertEquals(1, again.intervalDays)
        assertEquals(1, again.lapses)
    }

    @Test
    fun intervalsGrowOnTimeGoodReviews() {
        var s = Fsrs.review(SrsState(dueEpochDay = today), Rating.GOOD, today)
        var day = today
        var previous = s.intervalDays
        repeat(3) {
            day = s.dueEpochDay
            s = Fsrs.review(s, Rating.GOOD, day)
            assertTrue("${s.intervalDays} > $previous", s.intervalDays > previous)
            previous = s.intervalDays
        }
    }

    @Test
    fun easyBeatsGoodBeatsHard() {
        val base = Fsrs.review(SrsState(dueEpochDay = today), Rating.GOOD, today)
        val due = base.dueEpochDay
        val hard = Fsrs.review(base, Rating.HARD, due).intervalDays
        val good = Fsrs.review(base, Rating.GOOD, due).intervalDays
        val easy = Fsrs.review(base, Rating.EASY, due).intervalDays
        assertTrue("$hard < $good", hard < good)
        assertTrue("$good < $easy", good < easy)
    }

    @Test
    fun forgettingResetsToTomorrowAndKeepsHistory() {
        var s = Fsrs.review(SrsState(dueEpochDay = today), Rating.GOOD, today)
        s = Fsrs.review(s, Rating.GOOD, s.dueEpochDay)
        val lapsed = Fsrs.review(s, Rating.AGAIN, s.dueEpochDay)
        assertEquals(0, lapsed.repetitions)
        assertEquals(1, lapsed.intervalDays)
        assertEquals(1, lapsed.lapses)
    }

    @Test
    fun lapsesRaiseDifficulty() {
        var s = Fsrs.review(SrsState(dueEpochDay = today), Rating.GOOD, today)
        val before = Fsrs.difficultyOf(s)
        repeat(3) { s = Fsrs.review(s, Rating.AGAIN, s.dueEpochDay) }
        assertTrue(Fsrs.difficultyOf(s) > before)
    }

    @Test
    fun legacyCardsCarryOver() {
        val legacy = SrsState(easiness = 2.5, repetitions = 4, intervalDays = 20, dueEpochDay = today)
        val next = Fsrs.review(legacy, Rating.GOOD, today)
        assertTrue(next.intervalDays > 20)
        assertTrue(next.easiness < 0)
    }

    @Test
    fun intervalsAreCapped() {
        val s = Fsrs.review(SrsState(easiness = -3.0, repetitions = 9, intervalDays = 300, dueEpochDay = today), Rating.EASY, today)
        assertEquals(365, s.intervalDays)
    }

    @Test
    fun retrievabilityIsNinetyPercentAtStability() {
        assertEquals(0.9, Fsrs.retrievability(10.0, 10.0), 1e-9)
    }
}
