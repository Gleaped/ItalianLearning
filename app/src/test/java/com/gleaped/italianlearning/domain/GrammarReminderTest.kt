package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.GrammarTip
import com.gleaped.italianlearning.content.ItEn
import com.gleaped.italianlearning.content.Level
import com.gleaped.italianlearning.content.UnitRef
import com.gleaped.italianlearning.domain.reminders.ReminderComposer
import com.gleaped.italianlearning.domain.reminders.ReminderKind
import com.gleaped.italianlearning.domain.reminders.ReminderKind.GRAMMAR
import com.gleaped.italianlearning.domain.reminders.ReminderKind.NEXT_LESSON
import com.gleaped.italianlearning.domain.reminders.ReminderKind.REVIEW
import com.gleaped.italianlearning.domain.reminders.ReminderRotation
import com.gleaped.italianlearning.domain.reminders.ReminderTarget
import com.gleaped.italianlearning.domain.reminders.TipSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarReminderTest {

    private val course = Course(
        levels = listOf(
            Level("A2R", "Ripasso", remedial = true),
            Level("B1", "B1"),
            Level("B2", "B2"),
            Level("CELI3", "Esame CELI 3"),
        ),
        units = listOf(
            UnitRef("b2-01", "B2", 1, "B2 one"),
            UnitRef("a2r-01", "A2R", 1, "Rip 1"),
            UnitRef("a2r-02", "A2R", 2, "Rip 2"),
            UnitRef("b1-01", "B1", 1, "B1 one"),
            UnitRef("b1-02", "B1", 2, "B1 two"),
            UnitRef("b1-03", "B1", 3, "B1 three", available = false),
            UnitRef("celi3-01", "CELI3", 1, "Exam"),
        ),
    )

    private fun tip(id: String, unit: String = id.substringBeforeLast("-t"), challenge: Boolean = true) = GrammarTip(
        id = id,
        unitId = unit,
        rule = "Rule $id",
        tip = "Tip $id",
        example = ItEn("Esempio.", "Example."),
        challenge = if (challenge) GapFill(sentence = "___", answers = listOf("x")) else null,
    )

    // ------------------------------------------------------------ eligible units

    @Test
    fun startedUnitsInCourseOrderPlusOpenedRemedial() {
        val all = setOf("a2r-01", "a2r-02", "b1-01", "b1-02", "b2-01", "celi3-01")
        val ids = TipSelector.eligibleUnitIds(
            course,
            startedUnitIds = setOf("b2-01", "b1-01"),
            openedUnitIds = setOf("a2r-02", "b1-02"), // b1-02 opened but not started: not eligible
            unitsWithTips = all,
        )
        assertEquals(listOf("a2r-02", "b1-01", "b2-01"), ids)
    }

    @Test
    fun nothingStartedFallsBackToFirstMainPathUnitWithTips() {
        val ids = TipSelector.eligibleUnitIds(course, emptySet(), emptySet(), unitsWithTips = setOf("b1-02", "a2r-01", "b2-01"))
        assertEquals(listOf("b1-02"), ids)
    }

    @Test
    fun startedUnitsWithoutTipsAlsoFallBack() {
        val ids = TipSelector.eligibleUnitIds(course, setOf("b2-01"), emptySet(), unitsWithTips = setOf("b1-01"))
        assertEquals(listOf("b2-01", "b1-01"), ids)
    }

    @Test
    fun unavailableUnitsAreIgnored() {
        val ids = TipSelector.eligibleUnitIds(course, setOf("b1-03"), emptySet(), unitsWithTips = setOf("b1-03", "b1-01"))
        assertEquals(listOf("b1-01"), ids)
    }

    @Test
    fun noTipsAnywhereGivesNoFallback() {
        assertEquals(emptyList<String>(), TipSelector.eligibleUnitIds(course, emptySet(), emptySet(), emptySet()))
    }

    // ------------------------------------------------------------ rotation of tips

    @Test
    fun neverShownTipsComeFirstInOrder() {
        val tips = listOf(tip("b1-01-t1"), tip("b1-01-t2"), tip("b1-02-t1"))
        assertEquals("b1-01-t1", TipSelector.pick(tips, emptyList())?.id)
        assertEquals("b1-01-t2", TipSelector.pick(tips, listOf("b1-01-t1"))?.id)
        assertEquals("b1-02-t1", TipSelector.pick(tips, listOf("b1-01-t2", "b1-01-t1"))?.id)
    }

    @Test
    fun noRepeatUntilAllShownThenLeastRecent() {
        val tips = listOf(tip("a-t1"), tip("a-t2"), tip("a-t3"))
        var history = emptyList<String>()
        val shown = mutableListOf<String>()
        repeat(9) {
            val picked = TipSelector.pick(tips, history)!!
            shown += picked.id
            history = TipSelector.recordShown(history, picked.id)
        }
        // Every window of 3 consecutive picks contains all three tips.
        for (i in 0..shown.size - 3) assertEquals(shown.subList(i, i + 3).toString(), 3, shown.subList(i, i + 3).toSet().size)
        assertEquals(listOf("a-t1", "a-t2", "a-t3", "a-t1", "a-t2", "a-t3", "a-t1", "a-t2", "a-t3"), shown)
    }

    @Test
    fun newTipJoiningMidCyclePreferredOverRepeats() {
        val tips = listOf(tip("a-t1"), tip("a-t2"), tip("b-t1"))
        // a-t1 and a-t2 already shown; a newly started unit's tip goes next.
        assertEquals("b-t1", TipSelector.pick(tips, listOf("a-t1", "a-t2"))?.id)
    }

    @Test
    fun historyIgnoresTipsNoLongerEligibleAndHandlesDuplicates() {
        val tips = listOf(tip("a-t1"), tip("a-t2"))
        // a-t1 shown twice, last time most recently; stale ids in history are harmless.
        val history = listOf("a-t1", "gone-t9", "a-t2", "a-t1")
        assertEquals("a-t2", TipSelector.pick(tips, history)?.id)
    }

    @Test
    fun emptyCandidatesGiveNull() {
        assertNull(TipSelector.pick(emptyList(), listOf("x")))
    }

    @Test
    fun recordShownMovesToEndAndTrims() {
        assertEquals(listOf("b", "c", "a"), TipSelector.recordShown(listOf("a", "b", "c"), "a"))
        assertEquals(listOf("c", "d"), TipSelector.recordShown(listOf("a", "b", "c"), "d", limit = 2))
    }

    // ------------------------------------------------------------ rotation of kinds

    private val allKinds = ReminderKind.entries.toSet()

    @Test
    fun grammarIsTwoInThree() {
        val firsts = (0L until 60L).map { ReminderRotation.order(it, allKinds).first() }
        assertEquals(40, firsts.count { it == GRAMMAR })
        assertEquals(10, firsts.count { it == REVIEW })
        assertEquals(10, firsts.count { it == NEXT_LESSON })
        assertEquals(listOf(GRAMMAR, GRAMMAR, REVIEW, GRAMMAR, GRAMMAR, NEXT_LESSON), firsts.take(6))
    }

    @Test
    fun orderListsEveryAllowedKindOnceForFallback() {
        assertEquals(listOf(REVIEW, GRAMMAR, NEXT_LESSON), ReminderRotation.order(2, allKinds))
        assertEquals(listOf(NEXT_LESSON, GRAMMAR, REVIEW), ReminderRotation.order(5, allKinds))
        for (seq in 0L..12L) assertEquals(3, ReminderRotation.order(seq, allKinds).size)
    }

    @Test
    fun disabledKindsAreSkipped() {
        assertEquals(listOf(REVIEW, NEXT_LESSON), ReminderRotation.order(0, setOf(REVIEW, NEXT_LESSON)))
        assertEquals(listOf(NEXT_LESSON, REVIEW), ReminderRotation.order(4, setOf(REVIEW, NEXT_LESSON)))
        assertEquals(listOf(GRAMMAR), ReminderRotation.order(2, setOf(GRAMMAR)))
        assertEquals(emptyList<ReminderKind>(), ReminderRotation.order(3, emptySet()))
    }

    @Test
    fun negativeOrHugeSequenceIsSafe() {
        (0 until 6).forEach { assertEquals(3, ReminderRotation.order(Long.MAX_VALUE - it, allKinds).size) }
        assertEquals(3, ReminderRotation.order(-1, allKinds).size)
    }

    // ------------------------------------------------------------ composed copy

    @Test
    fun grammarNotificationUsesRuleTipExampleAndChallengeAction() {
        val c = ReminderComposer.grammar(tip("b1-01-t1"))
        assertEquals(GRAMMAR, c.kind)
        assertEquals("Rule b1-01-t1", c.title)
        assertEquals("Tip b1-01-t1", c.body)
        assertTrue(c.bigText.startsWith("Tip b1-01-t1"))
        assertTrue(c.bigText.contains("Esempio."))
        assertTrue(c.bigText.contains("Example."))
        assertEquals(ReminderComposer.CHALLENGE_ACTION, c.actionLabel)
        assertEquals(ReminderTarget.Challenge("b1-01", "b1-01-t1"), c.target)
        assertEquals("b1-01-t1", c.tipId)
    }

    @Test
    fun tipWithoutChallengeHasNoAction() {
        val c = ReminderComposer.grammar(tip("b1-01-t2", challenge = false))
        assertNull(c.actionLabel)
        assertEquals(ReminderTarget.Today, c.target)
        assertFalse(c.bigText.contains("sfida"))
    }

    @Test
    fun reviewAndLessonCopy() {
        val one = ReminderComposer.review(1, seed = 0)
        assertEquals(ReminderTarget.Review, one.target)
        assertTrue(one.body.startsWith("One card"))
        assertTrue(ReminderComposer.review(12, seed = 1).body.startsWith("12 cards"))
        val lesson = ReminderComposer.nextLesson("Al ristorante", "B1 · 3", 12, seed = 3, inactive = false, practicedToday = false)
        assertEquals(NEXT_LESSON, lesson.kind)
        assertTrue(lesson.body.contains("Al ristorante"))
        val quiet = ReminderComposer.nextLesson("X", "Y", 5, seed = 0, inactive = true, practicedToday = false)
        assertTrue(quiet.body.contains("No rush"))
    }

    @Test
    fun composedCopyStaysGentle() {
        val banned = listOf("streak", "lose", "lost", "miss", "fail", "disappoint", "shame", "guilt", "don't forget", "hurry")
        val texts = ReminderComposer.allFixedTitles +
            (0L..5L).flatMap { seed ->
                listOf(
                    ReminderComposer.review(3, seed),
                    ReminderComposer.nextLesson("L", "U", 10, seed, inactive = seed % 2 == 0L, practicedToday = seed % 3 == 0L),
                    ReminderComposer.generic(seed, inactive = false, practicedToday = false),
                    ReminderComposer.grammar(tip("a-t1")),
                ).flatMap { listOf(it.title, it.body, it.bigText) }
            }
        for (text in texts) {
            val lower = text.lowercase()
            banned.forEach { assertFalse("\"$text\" contains \"$it\"", lower.contains(it)) }
        }
    }
}
