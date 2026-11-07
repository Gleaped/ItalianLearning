package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.ContentParser
import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.domain.progress.CourseProgress
import com.gleaped.italianlearning.domain.progress.Streak
import com.gleaped.italianlearning.domain.progress.UnitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    private val course = ContentParser.parseCourse(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/course.json")).readText(),
    )

    private fun unit(id: String, level: String, vararg lessons: String) = CourseUnit(
        id, level, id, "", "", emptyList(),
        lessons.map { Lesson(it, it, 5, emptyList()) }, emptyList(),
    )

    private val units = listOf(
        unit("a2r-01", "A2R", "a-l1"),
        unit("b1-01", "B1", "b1-l1", "b1-l2"),
        unit("b1-02", "B1", "b2-l1"),
    ).associateBy { it.id }

    @Test
    fun streakCounting() {
        assertEquals(0, Streak.current(emptyList(), 100))
        assertEquals(3, Streak.current(listOf(98, 99, 100), 100))
        assertEquals(2, Streak.current(listOf(98, 99), 100)) // today not done yet
        assertEquals(0, Streak.current(listOf(97, 98), 100))
        assertEquals(1, Streak.current(listOf(95, 100), 100))
    }

    @Test
    fun unlockRules() {
        val p = CourseProgress(course, units, completedLessons = setOf("b1-l1"))
        val refs = course.units.associateBy { it.id }
        assertEquals(UnitStatus.UNLOCKED, p.status(refs.getValue("a2r-01")))
        assertEquals(UnitStatus.UNLOCKED, p.status(refs.getValue("b1-01")))
        assertEquals(UnitStatus.LOCKED, p.status(refs.getValue("b1-02")))
        assertEquals(UnitStatus.COMING_SOON, p.status(refs.getValue("b1-03")))

        val done = CourseProgress(course, units, setOf("b1-l1", "b1-l2"))
        assertEquals(UnitStatus.UNLOCKED, done.status(refs.getValue("b1-02")))
        assertEquals(UnitStatus.UNLOCKED, CourseProgress(course, units, emptySet(), unlockAll = true).status(refs.getValue("b1-02")))
    }

    @Test
    fun nextLessonSkipsRemedialAndCompleted() {
        assertEquals("b1-l1", CourseProgress(course, units, emptySet()).nextLesson()?.second?.id)
        assertEquals("b1-l2", CourseProgress(course, units, setOf("b1-l1")).nextLesson()?.second?.id)
        assertEquals("b2-l1", CourseProgress(course, units, setOf("b1-l1", "b1-l2")).nextLesson()?.second?.id)
        assertNull(CourseProgress(course, units, setOf("b1-l1", "b1-l2", "b2-l1")).nextLesson())
    }

    @Test
    fun levelProgress() {
        val lp = CourseProgress(course, units, setOf("b1-l1")).levelProgress().first { it.levelId == "B1" }
        assertEquals(1, lp.completed)
        assertEquals(3, lp.total)
        assertEquals(3, lp.plannedUnits)
        assertEquals(2, lp.availableUnits)
    }

    @Test
    fun examAndUnknownLevelsAreOpenAndOffThePath() {
        val c = com.gleaped.italianlearning.content.Course(
            levels = listOf(
                com.gleaped.italianlearning.content.Level("A2R", "Ripasso", remedial = true),
                com.gleaped.italianlearning.content.Level("B1", "B1"),
                com.gleaped.italianlearning.content.Level("C1", "C1"),
                com.gleaped.italianlearning.content.Level("CELI3", "Esame CELI 3"),
            ),
            units = listOf(
                com.gleaped.italianlearning.content.UnitRef("b1-01", "B1", 1, "b1"),
                com.gleaped.italianlearning.content.UnitRef("c1-01", "C1", 1, "c1"),
                com.gleaped.italianlearning.content.UnitRef("celi3-01", "CELI3", 1, "exam"),
                com.gleaped.italianlearning.content.UnitRef("x-01", "ZZ", 1, "unknown level"),
            ),
        )
        val us = listOf(
            unit("b1-01", "B1", "b1-l1"),
            unit("c1-01", "C1", "c1-l1"),
            unit("celi3-01", "CELI3", "e-l1"),
            unit("x-01", "ZZ", "x-l1"),
        ).associateBy { it.id }
        val p = CourseProgress(c, us, emptySet())
        val refs = c.units.associateBy { it.id }
        assertEquals(listOf("b1-01", "c1-01"), p.mainPath.map { it.id })
        assertEquals(UnitStatus.LOCKED, p.status(refs.getValue("c1-01")))
        assertEquals(UnitStatus.UNLOCKED, p.status(refs.getValue("celi3-01")))
        assertEquals(UnitStatus.UNLOCKED, p.status(refs.getValue("x-01")))
        // Finishing the path does not lead into the exam track.
        assertNull(CourseProgress(c, us, setOf("b1-l1", "c1-l1")).nextLesson())
        org.junit.Assert.assertTrue(c.levels.last().isExam)
        org.junit.Assert.assertFalse(c.levels[1].isExam)
    }
}
