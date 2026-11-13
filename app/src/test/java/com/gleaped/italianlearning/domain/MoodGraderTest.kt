package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.srs.Sm2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodGraderTest {
    @Test
    fun chooseMoodNeedsTheRightMoodFirst() {
        assertEquals(Verdict.WRONG, Grader.gradeChooseMood(0, 1, "abbia", listOf("abbia")).verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeChooseMood(1, 1, "abbia", listOf("abbia")).verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeChooseMood(1, 1, "lui abbia", listOf("abbia")).verdict)
        assertEquals(Verdict.WRONG, Grader.gradeChooseMood(1, 1, "ha", listOf("abbia")).verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeChooseMood(1, 1, null, emptyList()).verdict)
        assertEquals(Verdict.ALMOST, Grader.gradeChooseMood(1, 1, "perche", listOf("perché")).verdict)
    }

    @Test
    fun findTriggerMatchesWholeWordsIgnoringPunctuation() {
        val s = "Esco, prima che piova."
        assertEquals(1..2, Grader.triggerSpan(s, "prima che"))
        assertEquals(Verdict.CORRECT, Grader.gradeFindTrigger(2, s, "prima che").verdict)
        assertEquals(Verdict.WRONG, Grader.gradeFindTrigger(3, s, "prima che").verdict)
        assertEquals(0..0, Grader.triggerSpan("Benché sia tardi, resto.", "benché"))
        assertEquals(0..1, Grader.triggerSpan("L'unico che sappia nuotare.", "l'unico che"))
        assertNull(Grader.triggerSpan("Penso che sia vero.", "credo"))
    }

    @Test
    fun sortBucketsAllowsOneSlipOnLongSets() {
        assertEquals(Verdict.CORRECT, Grader.gradeSortBuckets(listOf(0, 1, 1), listOf(0, 1, 1)).first)
        assertEquals(Verdict.WRONG, Grader.gradeSortBuckets(listOf(0, 0, 1), listOf(0, 1, 1)).first)
        assertEquals(Verdict.ALMOST, Grader.gradeSortBuckets(listOf(0, 1, 1, 1), listOf(0, 1, 1, 0)).first)
        assertEquals(listOf(true, false), Grader.gradeSortBuckets(listOf(0, 0), listOf(0, 1)).second)
    }

    @Test
    fun intervalsInPlainWords() {
        assertEquals("later today", Sm2.describeInterval(0))
        assertEquals("tomorrow", Sm2.describeInterval(1))
        assertEquals("in 6 days", Sm2.describeInterval(6))
        assertEquals("in 3 weeks", Sm2.describeInterval(21))
        assertEquals("in 4 months", Sm2.describeInterval(120))
    }
}
