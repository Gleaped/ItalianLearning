package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.GrammarTip
import com.gleaped.italianlearning.content.ItEn
import com.gleaped.italianlearning.content.MultipleChoice
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.AttemptRecord
import com.gleaped.italianlearning.domain.progress.Skill
import com.gleaped.italianlearning.domain.progress.SkillStats
import com.gleaped.italianlearning.domain.reminders.ReminderComposer
import com.gleaped.italianlearning.domain.reminders.ReminderKind
import com.gleaped.italianlearning.domain.reminders.ReminderKind.GRAMMAR
import com.gleaped.italianlearning.domain.reminders.ReminderKind.NEXT_LESSON
import com.gleaped.italianlearning.domain.reminders.ReminderKind.REVIEW
import com.gleaped.italianlearning.domain.reminders.ReminderPlanner
import com.gleaped.italianlearning.domain.reminders.ReminderRotation
import com.gleaped.italianlearning.domain.reminders.ReminderTarget
import com.gleaped.italianlearning.domain.reminders.TipSelector
import com.gleaped.italianlearning.domain.writing.SavedWriting
import com.gleaped.italianlearning.domain.writing.WritingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class Phase4Test {

    // ------------------------------------------------------------ progress by skill

    private fun attempt(lesson: String, type: String, verdict: Verdict, day: Long = 100, index: Int = 0) =
        AttemptRecord(lesson, index, null, type, verdict, day)

    @Test
    fun `exercise types map to skills`() {
        assertEquals(Skill.READING, Skill.of("reading"))
        assertEquals(Skill.LISTENING, Skill.of("listen_transcribe"))
        assertEquals(Skill.LISTENING, Skill.of("listen_choose"))
        assertEquals(Skill.WRITING, Skill.of("free_write"))
        assertEquals(Skill.SPEAKING, Skill.of("roleplay"))
        assertEquals(Skill.GRAMMAR, Skill.of("gap_fill"))
        assertNull(Skill.of("grammar_note"))
    }

    @Test
    fun `accuracy counts almost as half and needs enough answers`() {
        val attempts = List(6) { attempt("b1-01-l1", "gap_fill", Verdict.CORRECT) } +
            List(2) { attempt("b1-01-l1", "gap_fill", Verdict.ALMOST) } +
            List(2) { attempt("b1-01-l1", "gap_fill", Verdict.WRONG) } +
            List(2) { attempt("b1-01-l1", "reading", Verdict.CORRECT) }
        val scores = SkillStats.bySkill(attempts, todayEpochDay = 100).associateBy { it.skill }
        assertEquals(10, scores.getValue(Skill.GRAMMAR).overall.answers)
        assertEquals(70, scores.getValue(Skill.GRAMMAR).overall.percent)
        assertNull(scores.getValue(Skill.READING).overall.percent)
        assertEquals(0, scores.getValue(Skill.SPEAKING).overall.answers)
        assertEquals(Skill.entries.toList(), SkillStats.bySkill(attempts, 100).map { it.skill })
    }

    @Test
    fun `recent accuracy only counts the last two weeks`() {
        val old = List(5) { attempt("b1-01-l1", "gap_fill", Verdict.WRONG, day = 80) }
        val fresh = List(5) { attempt("b1-01-l1", "gap_fill", Verdict.CORRECT, day = 95) }
        val grammar = SkillStats.bySkill(old + fresh, todayEpochDay = 100).first { it.skill == Skill.GRAMMAR }
        assertEquals(50, grammar.overall.percent)
        assertEquals(100, grammar.recent.percent)
    }

    @Test
    fun `unit id comes from the lesson id`() {
        assertEquals("b1-01", SkillStats.unitIdOf("b1-01-l3"))
        assertEquals("celi-06", SkillStats.unitIdOf("celi-06-l12"))
        assertNull(SkillStats.unitIdOf("weak"))
    }

    @Test
    fun `weakest topics are the least accurate units`() {
        val attempts = List(6) { attempt("b1-01-l1", "gap_fill", Verdict.WRONG) } +
            List(4) { attempt("b1-01-l2", "gap_fill", Verdict.CORRECT) } +
            List(10) { attempt("b1-02-l1", "multiple_choice", Verdict.CORRECT) } +
            List(3) { attempt("b1-03-l1", "gap_fill", Verdict.WRONG) } +
            List(8) { attempt("b1-04-l1", "reading", Verdict.WRONG) } +
            List(5) { attempt("b1-05-l1", "gap_fill", Verdict.ALMOST) }
        val weak = SkillStats.weakestTopics(attempts)
        // b1-02 is fine, b1-03 has too few answers, b1-04 is reading only.
        assertEquals(listOf("b1-01" to 40, "b1-05" to 50), weak.map { it.unitId to it.percent })
    }

    @Test
    fun `a topic recovers once its latest answers are right`() {
        val attempts = List(10) { attempt("b1-01-l1", "gap_fill", Verdict.WRONG) } +
            List(30) { attempt("b1-01-l1", "gap_fill", Verdict.CORRECT) }
        assertTrue(SkillStats.weakestTopics(attempts).isEmpty())
    }

    @Test
    fun `recently missed units look at wrong grammar answers only`() {
        val attempts = listOf(
            attempt("b1-01-l1", "gap_fill", Verdict.WRONG, day = 99),
            attempt("b1-02-l1", "gap_fill", Verdict.WRONG, day = 50),
            attempt("b1-03-l1", "gap_fill", Verdict.ALMOST, day = 99),
            attempt("b1-04-l1", "roleplay", Verdict.WRONG, day = 99),
        )
        assertEquals(setOf("b1-01"), SkillStats.recentlyMissedUnits(attempts, todayEpochDay = 100))
    }

    // ------------------------------------------------------------ saved writing

    private fun text(id: String, body: String = "Cara Anna, ti scrivo da Perugia.") =
        SavedWriting(id = id, exerciseId = "ex", prompt = "Scrivi una mail.", text = body, savedAtMillis = 1L)

    @Test
    fun `saved texts survive encoding`() {
        val entries = listOf(text("a").copy(scores = mapOf("lessico" to 4)), text("b"))
        assertEquals(entries, WritingLog.decode(WritingLog.encode(entries)))
        assertEquals(emptyList<SavedWriting>(), WritingLog.decode("not json"))
        assertEquals(emptyList<SavedWriting>(), WritingLog.decode(null))
    }

    @Test
    fun `newest text goes first and blank texts are not kept`() {
        val list = WritingLog.add(WritingLog.add(emptyList(), text("a")), text("b"))
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(list, WritingLog.add(list, text("c", body = "  ")))
        assertEquals(listOf("c", "b"), WritingLog.add(list, text("c"), limit = 2).map { it.id })
    }

    @Test
    fun `scores outside the scale are dropped`() {
        assertEquals(mapOf("lessico" to 5), WritingLog.cleanScores(mapOf("lessico" to 5, "coerenza" to 0, "altro" to 3, "grammatica" to 9)))
    }

    @Test
    fun `shared text has the prompt the text and the scores`() {
        val shared = WritingLog.shareText(text("a").copy(scores = mapOf("coerenza" to 3)))
        assertTrue(shared.startsWith("Traccia: Scrivi una mail."))
        assertTrue(shared.contains("Cara Anna"))
        assertTrue(shared.endsWith("Autovalutazione: Coerenza 3/5"))
    }

    // ------------------------------------------------------------ reminders

    private fun quizTip(options: List<String> = listOf("si mangiano", "si mangia", "ci si mangia", "mangiano si")) = GrammarTip(
        id = "b1-01-t1",
        unitId = "b1-01",
        rule = "Si passivante",
        tip = "With a plural noun the verb is plural.",
        example = ItEn("Si mangiano ottimi primi.", "You eat great first courses."),
        challenge = MultipleChoice(prompt = "Lì ___ ottimi primi piatti.", options = options, answer = 0, explanation = "Primi piatti is plural."),
    )

    @Test
    fun `quiz offers the right answer among at most three options`() {
        for (seed in 0L..20L) {
            val options = ReminderComposer.quizOptions(quizTip(), seed)!!
            assertEquals(3, options.size)
            assertEquals(3, options.toSet().size)
            assertTrue(options.contains("si mangiano"))
        }
        // The right answer is not always in the same place.
        assertTrue((0L..20L).map { ReminderComposer.quizOptions(quizTip(), it)!!.indexOf("si mangiano") }.toSet().size > 1)
    }

    @Test
    fun `no quiz for long options or other challenge types`() {
        assertNull(ReminderComposer.quizOptions(quizTip(listOf("a", "an option far too long for a button")), 1))
        val gap = quizTip().copy(challenge = GapFill(sentence = "___", answers = listOf("x")))
        assertNull(ReminderComposer.quizOptions(gap, 1))
        assertNull(ReminderComposer.grammar(gap).quiz)
    }

    @Test
    fun `quiz reminder asks the question without giving the rule away`() {
        val c = ReminderComposer.grammar(quizTip(), seed = 3)
        assertNotNull(c.quiz)
        assertEquals("Si passivante", c.title)
        assertEquals("Lì ___ ottimi primi piatti.", c.body)
        assertFalse(c.bigText.contains("plural"))
        assertEquals(ReminderTarget.Challenge("b1-01", "b1-01-t1"), c.target)
    }

    @Test
    fun `quiz result gives the verdict the answer and the rule`() {
        val right = ReminderComposer.quizResult(quizTip(), "si mangiano")
        assertEquals("Giusto! ✓", right.title)
        assertTrue(right.bigText.contains("Lì si mangiano ottimi primi piatti."))
        assertTrue(right.bigText.contains("With a plural noun"))
        val wrong = ReminderComposer.quizResult(quizTip(), "si mangia")
        assertEquals("Non proprio", wrong.title)
        assertTrue(wrong.body.contains("si mangiano"))
        assertTrue(wrong.bigText.contains("You chose “si mangia”"))
        assertNull(wrong.quiz)
    }

    private val allKinds = ReminderKind.entries.toSet()

    @Test
    fun `many cards waiting put review first every other reminder`() {
        assertEquals(listOf(REVIEW, GRAMMAR, NEXT_LESSON), ReminderRotation.order(0, allKinds, reviewReady = 25))
        assertEquals(ReminderRotation.order(1, allKinds), ReminderRotation.order(1, allKinds, reviewReady = 25))
        assertEquals(ReminderRotation.order(0, allKinds), ReminderRotation.order(0, allKinds, reviewReady = 5))
        assertEquals(listOf(GRAMMAR), ReminderRotation.order(0, setOf(GRAMMAR), reviewReady = 99))
    }

    private fun tip(id: String, unit: String) =
        GrammarTip(id, unit, "Rule", "Tip", null, GapFill(sentence = "___", answers = listOf("x")))

    @Test
    fun `tips from units with recent mistakes go first`() {
        val tips = listOf(tip("a1", "a"), tip("a2", "a"), tip("b1", "b"))
        assertEquals("b1", TipSelector.pick(tips, emptyList(), setOf("b"))?.id)
        // Shown a moment ago: the normal order applies.
        assertEquals("a1", TipSelector.pick(tips, listOf("b1"), setOf("b"))?.id)
        // No priority: unchanged behaviour.
        assertEquals("a1", TipSelector.pick(tips, emptyList(), emptySet())?.id)
    }

    @Test
    fun `quiet after practice silences reminders only when asked`() {
        val now = ZonedDateTime.of(2026, 11, 14, 19, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val opened = now.minusHours(2).toInstant()
        assertFalse(ReminderPlanner.shouldNotify(now, opened, null, false, quietAfterPractice = true, practicedToday = true))
        assertTrue(ReminderPlanner.shouldNotify(now, opened, null, false, quietAfterPractice = true, practicedToday = false))
        assertTrue(ReminderPlanner.shouldNotify(now, opened, null, false, quietAfterPractice = false, practicedToday = true))
    }
}
