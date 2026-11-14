package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.flags.ExerciseFlag
import com.gleaped.italianlearning.domain.flags.FlagLog
import com.gleaped.italianlearning.domain.progress.Accuracy
import com.gleaped.italianlearning.domain.progress.CourseProgress
import com.gleaped.italianlearning.domain.progress.ExamPlan
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import com.gleaped.italianlearning.domain.progress.Skill
import com.gleaped.italianlearning.domain.progress.SkillScore
import com.gleaped.italianlearning.domain.progress.Standing
import com.gleaped.italianlearning.domain.writing.SavedWriting
import com.gleaped.italianlearning.domain.writing.WritingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseATest {
    private fun score(skill: Skill, percent: Int?): SkillScore {
        val acc = if (percent == null) Accuracy(0, 0.0) else Accuracy(100, percent.toDouble())
        return SkillScore(skill, acc, acc)
    }

    @Test
    fun readinessNeedsEveryWrittenPart() {
        val partial = ExamReadiness.estimate(
            listOf(score(Skill.READING, 80), score(Skill.WRITING, 70), score(Skill.GRAMMAR, null), score(Skill.LISTENING, 60)),
        )
        assertNull(partial.written)
        val full = ExamReadiness.estimate(
            listOf(
                score(Skill.READING, 80), score(Skill.WRITING, 70), score(Skill.GRAMMAR, 90),
                score(Skill.LISTENING, 60), score(Skill.SPEAKING, 50),
            ),
        )
        // 32 + 28 + 18 + 24
        assertEquals(102, full.written)
        assertEquals(30, full.oral)
        assertEquals(132, full.overall)
    }

    @Test
    fun readinessUsesNoPointsBelowTheAnswerMinimum() {
        val r = ExamReadiness.estimate(listOf(SkillScore(Skill.READING, Accuracy(3, 3.0), Accuracy(3, 3.0))))
        assertTrue(r.parts.all { it.points == null })
        assertNull(r.oral)
    }

    @Test
    fun standingAgainstPassMarks() {
        // Written pass mark 84 of 140, margin 10 points.
        assertEquals(Standing.ABOVE, ExamReadiness.standing(100, ExamReadiness.WRITTEN_PASS, ExamReadiness.WRITTEN_MAX))
        assertEquals(Standing.NEAR, ExamReadiness.standing(80, ExamReadiness.WRITTEN_PASS, ExamReadiness.WRITTEN_MAX))
        assertEquals(Standing.BELOW, ExamReadiness.standing(60, ExamReadiness.WRITTEN_PASS, ExamReadiness.WRITTEN_MAX))
    }

    @Test
    fun paceTowardsTheExam() {
        assertEquals(10, ExamPlan.daysLeft(110, 100))
        assertEquals(0, ExamPlan.lessonsPerWeek(0, 30))
        assertNull(ExamPlan.lessonsPerWeek(40, 0))
        assertNull(ExamPlan.lessonsPerWeek(40, -3))
        // 40 lessons in 10 weeks: 4 a week.
        assertEquals(4, ExamPlan.lessonsPerWeek(40, 70))
        // Rounded up so the pace is enough.
        assertEquals(2, ExamPlan.lessonsPerWeek(15, 70))
        val levels = listOf(
            CourseProgress.LevelProgress("A2", "A2", 3, 46, 10, 10),
            CourseProgress.LevelProgress("B1", "B1", 20, 68, 15, 15),
            CourseProgress.LevelProgress("B2", "B2", 0, 51, 11, 11),
            CourseProgress.LevelProgress("C1", "C1", 0, 47, 10, 10),
        )
        assertEquals(48 + 51, ExamPlan.remainingLessons(levels))
    }

    @Test
    fun flagsReplaceTheSameExercise() {
        fun flag(note: String, at: Long) =
            ExerciseFlag(FlagLog.key("u", "u-l1", 2, "u-l1-e03"), "u", "u-l1", 2, "u-l1-e03", "gap_fill", note, at)
        val first = FlagLog.add(emptyList(), flag("too strict", 1))
        val second = FlagLog.add(first, flag("answer: sia", 2))
        assertEquals(1, second.size)
        assertEquals("answer: sia", second.first().note)
        assertEquals("u:u-l1:2", FlagLog.key("u", "u-l1", 2, null))
        val round = FlagLog.decode(FlagLog.encode(second))
        assertEquals(second, round)
        assertTrue(FlagLog.decode("not json").isEmpty())
        assertTrue(FlagLog.shareText(second).contains("u-l1-e03 (gap_fill): answer: sia"))
    }

    @Test
    fun feedbackRequestNamesTheExamAndCriteria() {
        val entry = SavedWriting("1", null, "Scrivi una mail al padrone di casa.", "Gentile signor Rossi, ...", "", 0L, mapOf("lessico" to 4))
        val text = WritingLog.feedbackRequest(entry)
        assertTrue(text.contains("CELI 3"))
        assertTrue(text.contains("coerenza"))
        assertTrue(text.contains("Scrivi una mail al padrone di casa."))
        assertTrue(text.contains("Gentile signor Rossi"))
        assertNotNull(Regex("Lessico 4/5").find(text))
    }
}
