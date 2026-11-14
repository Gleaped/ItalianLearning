package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.ExerciseTypes
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.AttemptRecord
import com.gleaped.italianlearning.domain.practice.WeakSpots
import com.gleaped.italianlearning.domain.progress.LessonCheckpoint
import com.gleaped.italianlearning.domain.progress.LessonCheckpointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTest {

    private fun attempt(verdict: Verdict, day: Long, index: Int = 0, type: String = ExerciseTypes.GAP_FILL) =
        AttemptRecord("b1-01-l1", index, "b1-01-l1-e0$index", type, verdict, day)

    // ---- weak spots

    @Test
    fun `a wrong answer comes back the next day`() {
        val attempts = listOf(attempt(Verdict.WRONG, day = 100))
        assertTrue(WeakSpots.due(attempts, todayEpochDay = 100).isEmpty())
        assertEquals(1, WeakSpots.due(attempts, todayEpochDay = 101).size)
    }

    @Test
    fun `correct answers space it out to 3 then 7 days and then close it`() {
        val first = listOf(attempt(Verdict.WRONG, 100), attempt(Verdict.CORRECT, 101))
        assertEquals(104L, WeakSpots.open(first).single().dueEpochDay)
        val second = first + attempt(Verdict.CORRECT, 104)
        assertEquals(111L, WeakSpots.open(second).single().dueEpochDay)
        val third = second + attempt(Verdict.CORRECT, 111)
        assertTrue(WeakSpots.open(third).isEmpty())
    }

    @Test
    fun `a new wrong answer starts again at one day`() {
        val attempts = listOf(
            attempt(Verdict.WRONG, 100),
            attempt(Verdict.CORRECT, 101),
            attempt(Verdict.WRONG, 104),
        )
        assertEquals(105L, WeakSpots.open(attempts).single().dueEpochDay)
    }

    @Test
    fun `almost keeps the step and waits the same gap again`() {
        val attempts = listOf(
            attempt(Verdict.WRONG, 100),
            attempt(Verdict.CORRECT, 101),
            attempt(Verdict.ALMOST, 104),
        )
        assertEquals(107L, WeakSpots.open(attempts).single().dueEpochDay)
    }

    @Test
    fun `never wrong means never weak, and long exercise types are left out`() {
        assertTrue(WeakSpots.open(listOf(attempt(Verdict.CORRECT, 100), attempt(Verdict.ALMOST, 101))).isEmpty())
        assertTrue(WeakSpots.open(listOf(attempt(Verdict.WRONG, 100, type = ExerciseTypes.FREE_WRITE))).isEmpty())
    }

    @Test
    fun `due spots are listed longest overdue first`() {
        val attempts = listOf(
            attempt(Verdict.WRONG, 105, index = 1),
            attempt(Verdict.WRONG, 100, index = 2),
        )
        assertEquals(listOf(2, 1), WeakSpots.due(attempts, 110).map { it.exerciseIndex })
    }

    // ---- typing slips

    @Test
    fun `one slip in a long word of a sentence is almost`() {
        val r = Grader.gradeTyped("Qui non si può parchegiare", listOf("Qui non si può parcheggiare."), allowSlip = true)
        assertEquals(Verdict.ALMOST, r.verdict)
        assertEquals(Grader.spellingNote("parcheggiare"), r.note)
    }

    @Test
    fun `swapped neighbours count as one slip`() {
        val r = Grader.gradeTyped("Domani andiamo al ristornate", listOf("Domani andiamo al ristorante"), allowSlip = true)
        assertEquals(Verdict.WRONG, r.verdict) // the swap is inside the protected ending
        val ok = Grader.gradeTyped("Domani andiamo al rsitorante", listOf("Domani andiamo al ristorante"), allowSlip = true)
        assertEquals(Verdict.ALMOST, ok.verdict)
    }

    @Test
    fun `a wrong ending is never excused`() {
        val answers = listOf("Penso che loro parlino troppo")
        assertEquals(Verdict.WRONG, Grader.gradeTyped("Penso che loro parlano troppo", answers, allowSlip = true).verdict)
    }

    @Test
    fun `short words, short answers and two slips stay wrong`() {
        assertEquals(Verdict.WRONG, Grader.gradeTyped("Io sano qui oggi", listOf("Io sono qui oggi"), allowSlip = true).verdict)
        assertEquals(Verdict.WRONG, Grader.gradeTyped("parchegiare", listOf("parcheggiare"), allowSlip = true).verdict)
        assertEquals(
            Verdict.WRONG,
            Grader.gradeTyped("Qui non si può parchegiare la machina", listOf("Qui non si può parcheggiare la macchina"), allowSlip = true).verdict,
        )
    }

    @Test
    fun `slips are not tolerated unless asked for`() {
        assertEquals(Verdict.WRONG, Grader.gradeTyped("Qui non si può parchegiare", listOf("Qui non si può parcheggiare")).verdict)
    }

    // ---- lesson checkpoint

    @Test
    fun `a checkpoint survives encoding`() {
        val c = LessonCheckpoint("b1-05", "b1-05-l1", 4, mapOf(0 to Verdict.CORRECT, 1 to null, 2 to Verdict.WRONG, 3 to Verdict.ALMOST))
        assertEquals(c, LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(c)))
        val empty = LessonCheckpoint("b1-05", "b1-05-l1", 1, emptyMap())
        assertEquals(empty, LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(empty)))
    }

    @Test
    fun `damaged checkpoint text is ignored`() {
        assertNull(LessonCheckpointCodec.decode(null))
        assertNull(LessonCheckpointCodec.decode(""))
        assertNull(LessonCheckpointCodec.decode("b1-05\nb1-05-l1\nx\n"))
        assertNull(LessonCheckpointCodec.decode("b1-05\nb1-05-l1\n2\n0:Z"))
    }
}
