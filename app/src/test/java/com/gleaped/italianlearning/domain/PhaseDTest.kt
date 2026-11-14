package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.exam.PaperLog
import com.gleaped.italianlearning.domain.exam.SavedPaper
import com.gleaped.italianlearning.domain.progress.ExamPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseDTest {
    private fun paper(id: String, vararg scores: Pair<ExamPart, Int>) =
        SavedPaper(id, "p$id", 20000, scores.associate { it.first.name to it.second })

    @Test
    fun parseScoreAcceptsOnlyWholeNumbersWithinTheMaximum() {
        assertEquals(30, PaperLog.parseScore(" 30 ", ExamPart.READING))
        assertEquals(0, PaperLog.parseScore("0", ExamPart.LANGUAGE))
        assertNull(PaperLog.parseScore("41", ExamPart.READING))
        assertNull(PaperLog.parseScore("", ExamPart.READING))
        assertNull(PaperLog.parseScore("abc", ExamPart.READING))
    }

    @Test
    fun totalsNeedEveryPartOfTheirGroup() {
        val partial = paper("1", ExamPart.READING to 30, ExamPart.WRITING to 30, ExamPart.LANGUAGE to 12)
        val t = PaperLog.totals(partial)
        assertNull(t.written)
        assertNull(t.overall)
        assertNull(t.passed)
    }

    @Test
    fun aPaperAtThePassMarksPasses() {
        val full = paper(
            "1",
            ExamPart.READING to 26, ExamPart.WRITING to 26, ExamPart.LANGUAGE to 12, ExamPart.LISTENING to 26,
            ExamPart.ORAL to 33,
        )
        val t = PaperLog.totals(full)
        assertEquals(90, t.written)
        assertEquals(33, t.oral)
        assertEquals(123, t.overall)
        assertEquals(true, t.passed)
    }

    @Test
    fun overallPassStillNeedsTheOralMark() {
        val strongWrittenWeakOral = paper(
            "1",
            ExamPart.READING to 40, ExamPart.WRITING to 40, ExamPart.LANGUAGE to 20, ExamPart.LISTENING to 40,
            ExamPart.ORAL to 20,
        )
        val t = PaperLog.totals(strongWrittenWeakOral)
        assertEquals(160, t.overall)
        assertEquals(false, t.passed)
    }

    @Test
    fun addKeepsNewestFirstReplacesSameIdAndDropsBadScores() {
        val a = paper("a", ExamPart.READING to 20)
        val b = paper("b", ExamPart.READING to 99, ExamPart.ORAL to 40)
        var list = PaperLog.add(emptyList(), a)
        list = PaperLog.add(list, b)
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertFalse(list[0].scores.containsKey(ExamPart.READING.name))
        assertEquals(40, list[0].scores[ExamPart.ORAL.name])
        list = PaperLog.add(list, a.copy(label = "again"))
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals("again", list[0].label)
    }

    @Test
    fun storageRoundTripsAndSurvivesGarbage() {
        val list = listOf(paper("a", ExamPart.READING to 20, ExamPart.ORAL to 30))
        assertEquals(list, PaperLog.decode(PaperLog.encode(list)))
        assertTrue(PaperLog.decode("not json").isEmpty())
        assertTrue(PaperLog.decode(null).isEmpty())
    }

    @Test
    fun gradeBandsFollowTheOfficialScale() {
        assertEquals("A (ottimo)", PaperLog.grade(173))
        assertEquals("B (buono)", PaperLog.grade(144))
        assertEquals("C (sufficiente)", PaperLog.grade(117))
        assertEquals("D (insufficiente)", PaperLog.grade(116))
        assertEquals("E (insufficiente)", PaperLog.grade(68))
    }
}
