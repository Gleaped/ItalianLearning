package com.gleaped.italianlearning.domain.progress

import java.time.LocalDate

/** What was done on one day. */
data class DayCount(val epochDay: Long, val lessons: Int, val cards: Int)

/** A short look back at last week, shown once. */
data class WeekSummary(
    /** Monday of the week now running; used to show the recap once per week. */
    val weekStartEpochDay: Long,
    val daysPracticed: Int,
    val lessons: Int,
    val cards: Int,
    /** A skill worth a little extra attention this week; null when nothing stands out. */
    val focus: Skill?,
) {
    val text: String
        get() {
            val days = if (daysPracticed == 1) "1 day" else "$daysPracticed days"
            val parts = buildList {
                if (lessons > 0) add(if (lessons == 1) "1 lesson" else "$lessons lessons")
                if (cards > 0) add("$cards cards")
            }
            val done = if (parts.isEmpty()) "" else " (" + parts.joinToString(" and ") + ")"
            val tail = if (focus != null) " This week, a little extra ${focus.english.lowercase()} would help." else " Keep the same gentle pace."
            return "Last week you practised on $days$done.$tail"
        }
}

object WeeklyRecap {
    /** The Monday of the week that contains [epochDay]. */
    fun mondayOf(epochDay: Long): Long {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value // Monday = 1
        return epochDay - (dow - 1)
    }

    /** Last week's summary, or null when nothing was practised then (no recap is better than a bare zero). */
    fun summarize(days: List<DayCount>, scores: List<SkillScore>, todayEpochDay: Long): WeekSummary? {
        val thisMonday = mondayOf(todayEpochDay)
        val lastMonday = thisMonday - 7
        val week = days.filter { it.epochDay in lastMonday until thisMonday && (it.lessons > 0 || it.cards > 0) }
        if (week.isEmpty()) return null
        val focus = scores
            .mapNotNull { s -> s.recent.percent?.let { s.skill to it } }
            .filter { it.second < SkillStats.WEAK_BELOW_PERCENT }
            .minByOrNull { it.second }
            ?.first
        return WeekSummary(thisMonday, week.size, week.sumOf { it.lessons }, week.sumOf { it.cards }, focus)
    }
}
