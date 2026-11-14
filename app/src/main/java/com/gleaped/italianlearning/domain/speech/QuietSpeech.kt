package com.gleaped.italianlearning.domain.speech

/**
 * "Can't talk now": for a while every speaking step is typed instead, without changing the
 * speaking-practice setting. Only the end time is stored.
 */
object QuietSpeech {
    const val DURATION_MILLIS = 60 * 60 * 1000L

    fun until(nowMillis: Long): Long = nowMillis + DURATION_MILLIS

    /** A stored time further ahead than one pause can be (the clock was changed) does not count. */
    fun active(untilMillis: Long, nowMillis: Long): Boolean =
        untilMillis > nowMillis && untilMillis - nowMillis <= DURATION_MILLIS
}
