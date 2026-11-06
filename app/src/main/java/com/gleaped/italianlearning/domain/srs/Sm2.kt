package com.gleaped.italianlearning.domain.srs

import kotlin.math.max
import kotlin.math.roundToInt

/** The four buttons shown when reviewing a card, mapped to SM-2 quality grades. */
enum class Rating(val quality: Int) {
    AGAIN(1),
    HARD(3),
    GOOD(4),
    EASY(5),
}

/** Scheduling state of one card. Days are epoch days (LocalDate.toEpochDay()). */
data class SrsState(
    val easiness: Double = Sm2.INITIAL_EASINESS,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val dueEpochDay: Long = 0,
    val lapses: Int = 0,
)

/**
 * SM-2 spaced repetition (SuperMemo 2), lightly adapted:
 *  - quality < 3 resets the repetition count; the card is due again tomorrow
 *    (the review screen also re-queues it within the same session)
 *  - first successful interval 1 day, second 6 days (classic SM-2), then interval * EF
 *  - HARD grows the interval more slowly (x1.2) than GOOD, EASY gets a 1.3 bonus
 *  - easiness factor never drops below 1.3
 */
object Sm2 {
    const val INITIAL_EASINESS = 2.5
    const val MIN_EASINESS = 1.3
    const val MAX_INTERVAL_DAYS = 365

    fun review(state: SrsState, rating: Rating, todayEpochDay: Long): SrsState {
        val q = rating.quality
        val newEf = max(MIN_EASINESS, state.easiness + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
        if (q < 3) {
            return state.copy(
                easiness = newEf,
                repetitions = 0,
                intervalDays = 1,
                dueEpochDay = todayEpochDay + 1,
                lapses = state.lapses + 1,
            )
        }
        val reps = state.repetitions + 1
        val interval = when {
            reps == 1 -> 1
            reps == 2 -> if (rating == Rating.HARD) 3 else 6
            rating == Rating.HARD -> max(state.intervalDays + 1, (state.intervalDays * 1.2).roundToInt())
            rating == Rating.EASY -> max(state.intervalDays + 1, (state.intervalDays * newEf * 1.3).roundToInt())
            else -> max(state.intervalDays + 1, (state.intervalDays * newEf).roundToInt())
        }.coerceIn(1, MAX_INTERVAL_DAYS)
        val firstInterval = if (reps == 1 && rating == Rating.EASY) 3 else interval
        return state.copy(
            easiness = newEf,
            repetitions = reps,
            intervalDays = firstInterval,
            dueEpochDay = todayEpochDay + firstInterval,
        )
    }

    fun isDue(state: SrsState, todayEpochDay: Long): Boolean = state.dueEpochDay <= todayEpochDay

    /** Human-readable preview for a button ("1d", "6d", "2mo"). */
    fun formatInterval(days: Int): String = when {
        days < 1 -> "<1d"
        days < 30 -> "${days}d"
        days < 365 -> "${(days / 30.0).roundToInt()}mo"
        else -> "${(days / 365.0).roundToInt()}y"
    }
}
