package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.GrammarNote
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.content.MultipleChoice
import com.gleaped.italianlearning.domain.backup.Backup
import com.gleaped.italianlearning.domain.backup.BackupCard
import com.gleaped.italianlearning.domain.backup.BackupCodec
import com.gleaped.italianlearning.domain.backup.BackupDay
import com.gleaped.italianlearning.domain.backup.BackupLesson
import com.gleaped.italianlearning.domain.backup.BackupResult
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.DailyMix
import com.gleaped.italianlearning.domain.progress.ExamClock
import com.gleaped.italianlearning.domain.progress.LessonCheckpoint
import com.gleaped.italianlearning.domain.progress.LessonCheckpointCodec
import com.gleaped.italianlearning.domain.speech.QuietSpeech
import com.gleaped.italianlearning.domain.srs.CardKind
import com.gleaped.italianlearning.domain.srs.Cards
import com.gleaped.italianlearning.domain.writing.SavedWriting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class LastBatchTest {

    // ---- can't talk now

    @Test
    fun `speaking is paused for an hour and then comes back`() {
        val now = 1_000_000L
        val until = QuietSpeech.until(now)
        assertTrue(QuietSpeech.active(until, now))
        assertTrue(QuietSpeech.active(until, until - 1))
        assertFalse(QuietSpeech.active(until, until))
        assertFalse(QuietSpeech.active(0L, now))
    }

    @Test
    fun `a pause far in the future after a clock change does not count`() {
        val now = 1_000_000L
        assertFalse(QuietSpeech.active(now + QuietSpeech.DURATION_MILLIS + 1, now))
    }

    // ---- dictionary words in the deck

    @Test
    fun `a dictionary card is a recognise card with the first senses on its back`() {
        assertEquals("d:42", Cards.dictionaryId(42))
        assertEquals(CardKind.RECOGNISE, Cards.kindOf(Cards.dictionaryId(42)))
        assertEquals(CardKind.PRODUCE, Cards.kindOf(Cards.produceId(Cards.dictionaryId(42))))
        assertEquals("go; walk; leave", Cards.dictionaryBack(listOf("go", " walk ", "go", "", "leave", "work")))
        assertEquals("", Cards.dictionaryBack(emptyList()))
    }

    // ---- backup

    private val backup = Backup(
        exportedAtMillis = 123L,
        lessons = listOf(BackupLesson("b1-01-l1", "b1-01", 10L, 5, 1, 2, 1)),
        results = listOf(
            BackupResult("b1-01-l1", 0, "b1-01-l1-e00", "gap_fill", "WRONG", 10L),
            BackupResult("b1-01-l1", 1, null, "translate", "CORRECT", 11L),
        ),
        cards = listOf(
            BackupCard("v:b1-01:il treno", "il treno", "the train", "Prendo il treno.", "b1-01", 2.5, 2, 6, 20_000L, 0, 19_990L, 19_994L),
            BackupCard("d:42", "andare", "go; walk", null, Cards.DICTIONARY_UNIT_ID, 2.5, 0, 0, 20_000L, 0),
        ),
        days = listOf(BackupDay(19_994L, 1, 12)),
        writing = listOf(SavedWriting(id = "w1", prompt = "Scrivi una mail", text = "Gentile signora…", savedAtMillis = 5L, scores = mapOf("lessico" to 4))),
    )

    @Test
    fun `a backup survives being written and read`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        assertEquals("1 lesson, 2 cards, 1 text", BackupCodec.summary(backup))
        assertEquals("italiano-backup-2026-11-14.json", BackupCodec.fileName(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun `other files and newer formats are refused`() {
        assertNull(BackupCodec.decode(null))
        assertNull(BackupCodec.decode("   "))
        assertNull(BackupCodec.decode("not json"))
        assertNull(BackupCodec.decode("""{"some":"other file"}""".replace("some", "app")))
        assertNull(BackupCodec.decode("""[1, 2, 3]"""))
        assertNull(BackupCodec.decode(BackupCodec.encode(backup.copy(version = BackupCodec.VERSION + 1))))
        assertNull(BackupCodec.decode(BackupCodec.encode(backup.copy(app = "altro"))))
    }

    @Test
    fun `fields added by a later version are ignored`() {
        val withExtra = BackupCodec.encode(backup).replaceFirst("{", """{"futureField":true,""")
        assertEquals(backup, BackupCodec.decode(withExtra))
    }

    // ---- daily mix

    private fun gap(id: String): Exercise = GapFill(id = id, sentence = "Io ___ a casa.", answers = listOf("vado"))
    private fun choice(id: String): Exercise = MultipleChoice(id = id, prompt = "?", options = listOf("a", "b"), answer = 0)

    private fun unit(id: String, exercisesPerLesson: List<List<Exercise>>) = CourseUnit(
        id = id,
        level = "B1",
        title = id,
        objective = "",
        grammarFocus = "",
        remedialUnitIds = emptyList(),
        lessons = exercisesPerLesson.mapIndexed { i, list -> Lesson("$id-l${i + 1}", "L", 5, list) },
        vocabulary = emptyList(),
    )

    private val units = listOf(
        unit("u1", listOf(listOf(GrammarNote(id = "n"), gap("a1"), gap("a2"), choice("a3")), listOf(gap("a4")))),
        unit("u2", listOf(listOf(gap("b1"), choice("b2"), choice("b3"), gap("b4")))),
        unit("u3", listOf(listOf(gap("c1"), gap("c2"), choice("c3")))),
    )

    @Test
    fun `the mix only draws on finished lessons and leaves out rule cards`() {
        val candidates = DailyMix.candidates(units, setOf("u1-l1", "u2-l1"))
        assertEquals(setOf("u1-l1", "u2-l1"), candidates.map { it.lessonId }.toSet())
        assertEquals(7, candidates.size)
        assertTrue(candidates.none { it.exercise is GrammarNote })
        // The index is the exercise's place in its lesson, rule card included.
        assertEquals(1, candidates.first { it.exercise.id == "a1" }.exerciseIndex)
    }

    @Test
    fun `the mix takes turns between units and never repeats an exercise`() {
        val candidates = DailyMix.candidates(units, setOf("u1-l1", "u1-l2", "u2-l1", "u3-l1"))
        repeat(20) { seed ->
            val picked = DailyMix.pick(candidates, emptySet(), emptySet(), Random(seed), size = 6)
            assertEquals(6, picked.size)
            assertEquals(6, picked.map { it.key }.toSet().size)
            assertEquals(mapOf("u1" to 2, "u2" to 2, "u3" to 2), picked.groupingBy { it.unitId }.eachCount())
        }
    }

    @Test
    fun `a weak unit gets more of the mix`() {
        val candidates = DailyMix.candidates(units, setOf("u1-l1", "u1-l2", "u2-l1", "u3-l1"))
        repeat(20) { seed ->
            val picked = DailyMix.pick(candidates, setOf("u2"), emptySet(), Random(seed), size = 4)
            assertEquals(2, picked.count { it.unitId == "u2" })
        }
    }

    @Test
    fun `exercises answered lately wait while there are others`() {
        val candidates = DailyMix.candidates(units, setOf("u2-l1"))
        val recent = setOf("u2-l1" to 0, "u2-l1" to 1)
        repeat(20) { seed ->
            val picked = DailyMix.pick(candidates, emptySet(), recent, Random(seed), size = 2)
            assertEquals(setOf("b3", "b4"), picked.map { it.exercise.id }.toSet())
        }
    }

    @Test
    fun `a small pool gives a short mix and an empty one gives nothing`() {
        val candidates = DailyMix.candidates(units, setOf("u3-l1"))
        assertEquals(3, DailyMix.pick(candidates, emptySet(), emptySet(), Random(1)).size)
        assertTrue(DailyMix.pick(emptyList(), emptySet(), emptySet(), Random(1)).isEmpty())
    }

    // ---- timed papers

    @Test
    fun `the exam clock adds this sitting to the time used before`() {
        val clock = ExamClock(limitSeconds = 45 * 60, baseSeconds = 600, startedAtMillis = 10_000L)
        assertEquals(600, clock.elapsedSeconds(10_000L))
        assertEquals(690, clock.elapsedSeconds(100_999L))
        assertEquals(45 * 60 - 690, clock.remainingSeconds(100_999L))
        assertTrue(clock.remainingSeconds(10_000L + 3_000_000L) < 0)
        // A clock that went backwards never gives time back.
        assertEquals(600, clock.elapsedSeconds(5_000L))
    }

    @Test
    fun `times are shown as minutes and seconds, with hours when needed`() {
        assertEquals("0:00", ExamClock.format(-5))
        assertEquals("0:09", ExamClock.format(9))
        assertEquals("44:59", ExamClock.format(44 * 60 + 59))
        assertEquals("2:15:00", ExamClock.format(135 * 60))
        assertEquals("1:02:05", ExamClock.format(3725))
    }

    @Test
    fun `the exam score counts an almost as half`() {
        assertNull(ExamClock.percent(0, 0, 0))
        assertEquals(100, ExamClock.percent(4, 0, 0))
        assertEquals(63, ExamClock.percent(2, 1, 1))
        assertEquals(0, ExamClock.percent(0, 0, 3))
    }

    @Test
    fun `a saved place keeps the time used, and older saves still load`() {
        val c = LessonCheckpoint("celi-06", "celi-06-l2", 12, mapOf(0 to Verdict.CORRECT, 1 to Verdict.WRONG), elapsedSeconds = 754)
        assertEquals(c, LessonCheckpointCodec.decode(LessonCheckpointCodec.encode(c)))
        val old = LessonCheckpointCodec.decode("b1-05\nb1-05-l1\n2\n0:C,1:W")
        assertEquals(LessonCheckpoint("b1-05", "b1-05-l1", 2, mapOf(0 to Verdict.CORRECT, 1 to Verdict.WRONG)), old)
        assertNull(LessonCheckpointCodec.decode("b1-05\nb1-05-l1\n2\n0:C\nx"))
        assertNull(LessonCheckpointCodec.decode("b1-05\nb1-05-l1\n2\n0:C\n-4"))
    }
}
