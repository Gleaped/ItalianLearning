package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.content.SpeakRespond
import com.gleaped.italianlearning.domain.dictionary.DictionaryLookup
import com.gleaped.italianlearning.domain.dictionary.DictionaryStore
import com.gleaped.italianlearning.domain.dictionary.FormMatch
import com.gleaped.italianlearning.domain.dictionary.LemmaRow
import com.gleaped.italianlearning.domain.dictionary.Spelling
import com.gleaped.italianlearning.domain.dictionary.SpellingResult
import com.gleaped.italianlearning.domain.dictionary.WordNormalizer
import com.gleaped.italianlearning.domain.oral.OralCriterion
import com.gleaped.italianlearning.domain.oral.OralLog
import com.gleaped.italianlearning.domain.oral.SavedOral
import com.gleaped.italianlearning.domain.writing.NoteKind
import com.gleaped.italianlearning.domain.writing.SavedWriting
import com.gleaped.italianlearning.domain.writing.WritingChecker
import com.gleaped.italianlearning.domain.writing.WritingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhaseBTest {
    private val known = setOf("ieri", "sono", "andato", "al", "mercato", "perché", "è", "città", "e", "ho", "comprato", "il", "pane", "casa", "la", "mia")

    private val dictionary: (String) -> SpellingResult = { word ->
        val lower = word.lowercase()
        when {
            lower in known -> SpellingResult(Spelling.KNOWN)
            known.any { WordNormalizer.normalize(it) == WordNormalizer.normalize(lower) } ->
                SpellingResult(Spelling.ACCENT, known.first { WordNormalizer.normalize(it) == WordNormalizer.normalize(lower) })
            else -> SpellingResult(Spelling.UNKNOWN)
        }
    }

    private fun notes(text: String) = WritingChecker.check(text, dictionary).notes

    @Test
    fun cleanTextHasNoNotes() {
        assertTrue(notes("Ieri sono andato al mercato e ho comprato il pane.").isEmpty())
    }

    @Test
    fun spellingFindsUnknownWordsAndMissingAccents() {
        val found = notes("Ieri sono andato al mercatto perche ho comprato il pane.")
        assertEquals(listOf("mercatto", "perche"), found.filter { it.kind == NoteKind.SPELLING }.map { it.quote })
        assertTrue(found.first { it.quote == "perche" }.message.contains("perché"))
    }

    @Test
    fun namesAndElisionsAreLeftAlone() {
        // "Rossi" is capitalised mid-sentence; "l'" is an elision, not a word.
        assertTrue(notes("Ieri ho visto Rossi e l'amico.").none { it.quote == "Rossi" || it.quote == "l" })
    }

    @Test
    fun wordsWithAFinalApostropheAreFlaggedOnceByPattern() {
        val found = notes("Ieri sono andato al mercato perche' ho comprato il pane.")
        assertEquals(0, found.count { it.kind == NoteKind.SPELLING })
        assertEquals(1, found.count { it.kind == NoteKind.PATTERN })
        // A correct apostrophe is fine.
        assertTrue(notes("Ho comprato un po' di pane.").none { it.kind == NoteKind.PATTERN })
    }

    @Test
    fun frequentErrorsAreFound() {
        assertTrue(notes("Qual'è la tua casa?").any { it.message.contains("qual è") })
        assertTrue(notes("Ho incontrato un'amico ieri.").any { it.kind == NoteKind.PATTERN })
        assertTrue(notes("Questo è più meglio di quello.").any { it.kind == NoteKind.PATTERN })
        assertTrue(notes("Penso che è una buona idea.").any { it.message.contains("congiuntivo") })
        assertFalse(notes("Penso che sia una buona idea.").any { it.message.contains("congiuntivo") })
        assertTrue(notes("Se potrei, verrei volentieri.").any { it.message.contains("condizionale") })
        // An indirect question may use the condizionale after "se".
        assertFalse(notes("Mi chiedo se potrei venire.").any { it.message.contains("condizionale") })
    }

    @Test
    fun mixedRegisterIsFlagged() {
        val found = notes("Gentile signor Rossi, ciao! La ringrazio.")
        assertTrue(found.any { it.kind == NoteKind.REGISTER })
        assertFalse(notes("Gentile signor Rossi, la ringrazio. Distinti saluti.").any { it.kind == NoteKind.REGISTER })
    }

    @Test
    fun styleNotes() {
        val repeated = "La macchina è grande. La macchina è bella. La macchina è vecchia. La macchina è mia."
        assertTrue(notes(repeated).any { it.kind == NoteKind.STYLE && it.quote == "macchina" })
        val long = (1..70).joinToString(" ") { "parola" } + "."
        assertEquals(70, WritingChecker.wordTokens(long).size)
        assertTrue(WritingChecker.check(long, null).notes.any { it.message.contains("linking") })
        assertFalse(WritingChecker.check("Ciao.", null).spellingChecked)
        assertTrue(WritingChecker.check("Ciao.", dictionary).spellingChecked)
    }

    @Test
    fun connectorsAreFoundOnceInOrder() {
        assertEquals(listOf("tuttavia", "inoltre"), WritingChecker.connectorsIn("Tuttavia non è facile. Inoltre, tuttavia, costa."))
    }

    // ---- dictionary spelling through the lookup

    private class OneForm : DictionaryStore {
        private val row = LemmaRow(1, "perché", "conj", null, "why", null, null, null, 10, null, """{"tables":[]}""")
        override fun formsByNorm(norm: String, limit: Int) =
            if (norm == "perche") listOf(FormMatch("perché", "", 1) to row) else emptyList()
        override fun lemmasInRange(lower: String, upper: String, limit: Int) = emptyList<LemmaRow>()
        override fun lemma(id: Long): LemmaRow? = null
        override fun meta() = emptyMap<String, String>()
    }

    @Test
    fun lookupSpelling() {
        val lookup = DictionaryLookup(OneForm())
        assertEquals(Spelling.KNOWN, lookup.spelling("perché").kind)
        assertEquals(Spelling.KNOWN, lookup.spelling("Perché").kind)
        val accent = lookup.spelling("perche")
        assertEquals(Spelling.ACCENT, accent.kind)
        assertEquals("perché", accent.suggestion)
        assertEquals(Spelling.UNKNOWN, lookup.spelling("xyzzy").kind)
    }

    // ---- second draft

    @Test
    fun secondDraftIsWhatGetsShared() {
        val first = SavedWriting("1", null, "Task", "First draft", "", 0L)
        assertEquals("First draft", first.latest)
        val second = first.copy(revision = "Better draft")
        assertEquals("Better draft", second.latest)
        assertTrue(WritingLog.feedbackRequest(second).contains("second draft"))
        assertTrue(WritingLog.shareText(second).contains("Better draft"))
        assertFalse(WritingLog.shareText(second).contains("First draft"))
    }

    // ---- oral practice

    private fun speak(id: String, model: String = "Model") = SpeakRespond(id = id, prompt = "Prompt $id", modelAnswer = model)

    @Test
    fun oralCandidatesComeFromTheExamTrack() {
        val oral = Lesson("celi-05-l1", "Presentarsi", 10, listOf(speak("a"), speak("b", model = "")))
        val mock = listOf(
            Lesson("celi-06-l1", "Fascicolo 1 – Lettura", 10, listOf(speak("c"))),
            Lesson("celi-06-l4", "Prova orale (15 min)", 15, listOf(speak("d"))),
        )
        val units = mapOf(
            "celi-05" to CourseUnit("celi-05", "CELI3", "t", "", "", emptyList(), listOf(oral), emptyList()),
            "celi-06" to CourseUnit("celi-06", "CELI3", "t", "", "", emptyList(), mock, emptyList()),
        )
        assertEquals(listOf("a", "d"), OralLog.candidates(units).map { it.exerciseId })
        val picked = OralLog.pick(OralLog.candidates(units), 3, Random(1))
        assertEquals(2, picked.size)
    }

    @Test
    fun oralScoresAreCleanedAndAveraged() {
        val a = SavedOral("1", "p", mapOf("lessico" to 4, "grammatica" to 9, "boh" to 3), 1L)
        val list = OralLog.add(emptyList(), a)
        assertEquals(mapOf("lessico" to 4), list.first().scores)
        val more = OralLog.add(list, SavedOral("2", "q", mapOf("lessico" to 2), 2L))
        assertEquals(3.0, OralLog.averages(more)[OralCriterion.LEXIS]!!, 0.001)
        assertFalse(OralLog.averages(more).containsKey(OralCriterion.GRAMMAR))
        assertEquals(more, OralLog.decode(OralLog.encode(more)))
    }
}
