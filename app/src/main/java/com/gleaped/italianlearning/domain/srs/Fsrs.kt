package com.gleaped.italianlearning.domain.srs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-4.5 scheduling (Free Spaced Repetition Scheduler) with the published default weights,
 * working on the same [SrsState] and [Rating] as [Sm2] so it needs no database change.
 *
 * Memory state is kept in the existing columns:
 *  - stability  = [SrsState.intervalDays]  (at 90 % target retention the interval equals stability)
 *  - difficulty = stored in [SrsState.easiness] as a NEGATIVE number (-1 .. -10)
 *  - last review day = dueEpochDay - intervalDays
 * A positive easiness is a card last scheduled by SM-2; its difficulty is converted on the next review,
 * so existing decks carry over without a migration.
 */
object Fsrs {
    private val w = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474,
        0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    const val MAX_INTERVAL_DAYS = 365

    /** Probability of recalling a card of the given stability after [elapsedDays]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    /** Days until recall probability falls to 90 % (equal to stability by construction). */
    private fun intervalFor(stability: Double): Int = stability.roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)

    private fun initialDifficulty(rating: Rating): Double = (w[4] - (rating.grade - 3) * w[5]).coerceIn(1.0, 10.0)

    /** Difficulty of a card as FSRS sees it, converting an SM-2 ease factor (1.3 .. 2.5+) if needed. */
    fun difficultyOf(state: SrsState): Double =
        if (state.easiness < 0) -state.easiness
        else (1.0 + (2.8 - state.easiness.coerceIn(1.3, 2.8)) / 1.5 * 8.0).coerceIn(1.0, 10.0)

    fun review(state: SrsState, rating: Rating, todayEpochDay: Long): SrsState {
        val isNew = state.repetitions == 0 && state.intervalDays == 0 && state.lapses == 0
        if (isNew) {
            val s0 = w[rating.grade - 1].coerceAtLeast(0.1)
            val d0 = initialDifficulty(rating)
            return if (rating == Rating.AGAIN) {
                state.copy(easiness = -d0, repetitions = 0, intervalDays = 1, dueEpochDay = todayEpochDay + 1, lapses = 1)
            } else {
                val days = intervalFor(s0)
                state.copy(
                    easiness = -d0, repetitions = 1, intervalDays = days, dueEpochDay = todayEpochDay + days,
                )
            }
        }
        val s = max(0.1, state.intervalDays.toDouble())
        val d = difficultyOf(state)
        val last = state.dueEpochDay - state.intervalDays
        val elapsed = max(0L, todayEpochDay - last).toDouble()
        val r = retrievability(elapsed, s)
        // Difficulty drifts toward the mean-reverted target; Good leaves it roughly where it was.
        val d1 = (w[7] * initialDifficulty(Rating.GOOD) + (1 - w[7]) * (d - w[6] * (rating.grade - 3))).coerceIn(1.0, 10.0)
        if (rating == Rating.AGAIN) {
            // A lapse always comes back tomorrow; the review screen re-queues it within the session too.
            return state.copy(
                easiness = -d1, repetitions = 0, intervalDays = 1, dueEpochDay = todayEpochDay + 1,
                lapses = state.lapses + 1,
            )
        }
        val hard = if (rating == Rating.HARD) w[15] else 1.0
        val easy = if (rating == Rating.EASY) w[16] else 1.0
        val s1 = s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hard * easy)
        // A pass never shrinks the interval, and Hard < Good < Easy always differ by a day at least.
        var days = intervalFor(max(s1, s + 1))
        if (rating == Rating.HARD) days = max(1, min(days, max(state.intervalDays + 1, (state.intervalDays * 1.2).roundToInt())))
        if (rating == Rating.EASY) days = min(MAX_INTERVAL_DAYS, max(days, state.intervalDays + 2))
        return state.copy(
            easiness = -d1, repetitions = state.repetitions + 1, intervalDays = days,
            dueEpochDay = todayEpochDay + days,
        )
    }
}

/** FSRS grade 1..4 for a rating (Again, Hard, Good, Easy). */
val Rating.grade: Int
    get() = when (this) {
        Rating.AGAIN -> 1
        Rating.HARD -> 2
        Rating.GOOD -> 3
        Rating.EASY -> 4
    }
