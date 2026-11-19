package com.gleaped.italianlearning.domain.srs

/** What a review session offers today: due cards (after the daily cap), new cards, and whether a backlog is being eased. */
data class ReviewCounts(val due: Int, val fresh: Int, val eased: Boolean = false)

/**
 * A daily limit on old cards, so a gap of a few days (or a big new deck) never turns into
 * a pile of hundreds. The rest simply wait, oldest first, and come back on the next days.
 */
object ReviewLoad {
    const val DAILY_CAP = 60

    /** How many more old cards today's session may show, given those already reviewed today. */
    fun allowance(reviewedToday: Int): Int = (DAILY_CAP - reviewedToday).coerceAtLeast(0)

    fun shown(due: Int, reviewedToday: Int): Int = minOf(due, allowance(reviewedToday))

    /** True when more cards are waiting than today's allowance lets through. */
    fun eased(due: Int, reviewedToday: Int): Boolean = due > allowance(reviewedToday)
}
