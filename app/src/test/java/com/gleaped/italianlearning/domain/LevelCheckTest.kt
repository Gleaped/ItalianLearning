package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.GrammarNote
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.domain.practice.LevelCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LevelCheckTest {
    private fun gap(id: String): Exercise = GapFill(id = id, sentence = "Io ___ a casa.", answers = listOf("vado"))

    private fun unit(id: String, count: Int) = CourseUnit(
        id = id,
        level = "B1",
        title = id,
        objective = "",
        grammarFocus = "",
        remedialUnitIds = emptyList(),
        lessons = listOf(
            Lesson("$id-l1", "L", 5, listOf<Exercise>(GrammarNote(id = "$id-n")) + (1..count).map { gap("$id-g$it") }),
        ),
        vocabulary = emptyList(),
    )

    @Test
    fun lessonIdsRoundTripToTheirModule() {
        assertEquals("check-b1-m1", LevelCheck.lessonId("b1-m1"))
        assertEquals("b1-m1", LevelCheck.moduleOf("check-b1-m1"))
        assertNull(LevelCheck.moduleOf("weak"))
        assertNull(LevelCheck.moduleOf("mix"))
        assertNull(LevelCheck.moduleOf("check-"))
    }

    @Test
    fun theCheckDrawsOnEveryUnitWithoutRepeats() {
        val units = listOf(unit("u1", 10), unit("u2", 10), unit("u3", 10))
        val picked = LevelCheck.pick(units, Random(1))
        assertEquals(LevelCheck.SIZE, picked.size)
        assertEquals(setOf("u1", "u2", "u3"), picked.map { it.unitId }.toSet())
        assertEquals(picked.size, picked.map { it.exercise.id }.toSet().size)
        assertTrue(picked.none { it.exercise is GrammarNote })
    }

    @Test
    fun aSmallModuleGivesAShortCheckAndAnEmptyOneGivesNothing() {
        assertEquals(4, LevelCheck.pick(listOf(unit("u1", 4)), Random(1)).size)
        assertTrue(LevelCheck.pick(emptyList(), Random(1)).isEmpty())
    }

    @Test
    fun passingNeedsEightyFivePercentAndEnoughQuestions() {
        assertTrue(LevelCheck.passed(17, 0, 20))
        assertTrue(LevelCheck.passed(16, 2, 20))
        assertFalse(LevelCheck.passed(16, 1, 20))
        assertFalse(LevelCheck.passed(5, 0, 5))
        assertFalse(LevelCheck.passed(0, 0, 0))
    }
}
