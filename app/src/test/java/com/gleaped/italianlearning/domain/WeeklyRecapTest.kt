package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.progress.Accuracy
import com.gleaped.italianlearning.domain.progress.DayCount
import com.gleaped.italianlearning.domain.progress.Skill
import com.gleaped.italianlearning.domain.progress.SkillScore
import com.gleaped.italianlearning.domain.progress.WeeklyRecap
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyRecapTest {
    // 2026-11-19 is a Thursday.
    private val thursday = LocalDate.of(2026, 11, 19).toEpochDay()
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    @Test fun mondayOfFindsTheStartOfTheWeek() {
        assertEquals(monday, WeeklyRecap.mondayOf(thursday))
        assertEquals(monday, WeeklyRecap.mondayOf(monday))
    }

    @Test fun noRecapWithoutPracticeLastWeek() {
        assertNull(WeeklyRecap.summarize(listOf(DayCount(monday + 1, 2, 10)), emptyList(), thursday))
    }

    @Test fun countsLastWeekOnly() {
        val days = listOf(
            DayCount(monday - 8, 1, 0), // the week before last
            DayCount(monday - 5, 2, 10),
            DayCount(monday - 1, 1, 5),
            DayCount(monday + 1, 9, 9), // this week
        )
        val s = WeeklyRecap.summarize(days, emptyList(), thursday)!!
        assertEquals(2, s.daysPracticed)
        assertEquals(3, s.lessons)
        assertEquals(15, s.cards)
        assertNull(s.focus)
    }

    @Test fun weakestSkillBecomesTheFocus() {
        val days = listOf(DayCount(monday - 2, 1, 0))
        val good = Accuracy(20, 19.0)
        val weak = Accuracy(20, 10.0)
        val scores = listOf(
            SkillScore(Skill.GRAMMAR, good, good),
            SkillScore(Skill.LISTENING, weak, weak),
        )
        assertEquals(Skill.LISTENING, WeeklyRecap.summarize(days, scores, thursday)!!.focus)
    }
}
