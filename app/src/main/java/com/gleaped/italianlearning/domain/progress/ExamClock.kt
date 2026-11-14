package com.gleaped.italianlearning.domain.progress

/**
 * The clock of a timed exam paper. [baseSeconds] were used in earlier sittings of the same
 * paper; [startedAtMillis] is when this sitting began, on the clock later passed as "now".
 */
data class ExamClock(val limitSeconds: Int, val baseSeconds: Int, val startedAtMillis: Long) {

    fun elapsedSeconds(nowMillis: Long): Int {
        val sitting = ((nowMillis - startedAtMillis).coerceAtLeast(0L) / 1000L).coerceAtMost(MAX_SECONDS.toLong())
        return (baseSeconds.coerceAtLeast(0) + sitting.toInt()).coerceAtMost(MAX_SECONDS)
    }

    /** Negative once the time is up. */
    fun remainingSeconds(nowMillis: Long): Int = limitSeconds - elapsedSeconds(nowMillis)

    companion object {
        const val MAX_SECONDS = 24 * 60 * 60

        /** A practice target only: the real exam is marked by the examining board. */
        const val TARGET_PERCENT = 60

        /** "12:05", or "1:02:05" from an hour up. Negative values count as zero. */
        fun format(seconds: Int): String {
            val s = seconds.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            fun two(n: Int) = n.toString().padStart(2, '0')
            return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
        }

        /** Score as a whole percentage, an "almost" counting half; null when nothing was scored. */
        fun percent(correct: Int, almost: Int, wrong: Int): Int? {
            val total = correct + almost + wrong
            if (total <= 0) return null
            return Math.round((correct + almost * 0.5) * 100 / total).toInt()
        }
    }
}
