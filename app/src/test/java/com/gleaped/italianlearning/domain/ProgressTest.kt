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
}
