package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.dictionary.LemmaRow
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.TenseSet
import com.gleaped.italianlearning.domain.practice.VerbDrill
import com.gleaped.italianlearning.domain.srs.CoreDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhaseCTest {
    private fun row(id: Long, lemma: String, pos: String, gloss: String, gender: String? = null, forms: String = """{"tables":[]}""") =
        LemmaRow(id, lemma, pos, gender, gloss, null, null, null, 300 + id.toInt(), null, forms)

    @Test
    fun coreCardBackDropsLeadingNotesAndKeepsTwoSenses() {
        assertEquals("to support", CoreDeck.back(row(1, "supportare", "verb", "(transitive) to support")))
        assertEquals("space; room", CoreDeck.back(row(2, "spazio", "noun", "(mathematics, geometry) space ; room ; interval ; distance")))
        assertNull(CoreDeck.back(row(3, "x", "noun", "plural of casa")))
        assertNull(CoreDeck.back(row(4, "y", "noun", "(rare)")))
    }

    @Test
    fun coreCardFrontShowsNounGender() {
        assertEquals("azienda (f.)", CoreDeck.front(row(1, "azienda", "noun", "company", gender = "f")))
        assertEquals("supportare", CoreDeck.front(row(2, "supportare", "verb", "to support")))
    }

    @Test
    fun coreWordsSkipFunctionWordsAndWhatTheCourseTeaches() {
        val known = CoreDeck.knownWords(listOf("l'azienda (f.)", "il/la collega", "la ricevuta", "fare una domanda"))
        assertTrue("azienda" in known)
        assertTrue("collega" in known)
        assertTrue("ricevuta" in known)
        val rows = listOf(
            row(1, "azienda", "noun", "company", "f"),
            row(2, "di", "prep", "of"),
            row(3, "utile", "adj", "useful"),
            row(4, "ok", "adv", "okay"),
            row(5, "baciare", "verb", "(transitive) to kiss"),
            row(6, "tranquillo", "adj", "calm"),
        )
        assertEquals(listOf("utile", "baciare"), CoreDeck.select(rows, known, 2).map { it.lemma })
        assertEquals("c:7", CoreDeck.cardId(7))
        assertTrue(CoreDeck.isCoreCard("c:7"))
        assertFalse(CoreDeck.isCoreCard("v:u:x"))
    }

    private val andare = row(
        10, "andare", "verb", "to go ; to walk",
        forms = """{"tables":[
          {"title":"Indicativo presente","columns":["",""],"rows":[["io","vado"],["tu","vai"]]},
          {"title":"Indicativo passato prossimo","columns":["",""],"rows":[["io","sono andato/a"],["noi","siamo andati/e"]]},
          {"title":"Imperativo","columns":["",""],"rows":[["tu","va'"],["negativo (tu)","non andare"]]},
          {"title":"Participio","columns":["",""],"rows":[["passato","andato, andata, andati, andate"]]}
        ]}""",
    )

    @Test
    fun acceptedFormsExpandSlashesAndLists() {
        assertEquals(listOf("sono andato/a", "sono andato", "sono andata"), VerbDrill.accepted("sono andato/a"))
        assertEquals(listOf("siamo andati/e", "siamo andati", "siamo andate"), VerbDrill.accepted("siamo andati/e"))
        assertEquals(listOf("vado"), VerbDrill.accepted("vado"))
        assertEquals(listOf("parlato", "parlata"), VerbDrill.accepted("parlato, parlata"))
    }

    @Test
    fun questionsComeFromTheChosenTenses() {
        val everyday = VerbDrill.questionsFor(andare, TenseSet.EVERYDAY.titles)
        assertEquals(4, everyday.size)
        assertEquals("andare", everyday.first().infinitive)
        assertEquals("to go", everyday.first().meaning)
        val imperative = VerbDrill.questionsFor(andare, TenseSet.CONDITIONAL.titles)
        // "negativo (tu)" is not asked.
        assertEquals(listOf("tu"), imperative.map { it.person })
        assertTrue(VerbDrill.questionsFor(andare, TenseSet.SUBJUNCTIVE.titles).isEmpty())
    }

    @Test
    fun answersAreGradedLeniently() {
        val q = VerbDrill.questionsFor(andare, TenseSet.EVERYDAY.titles).first { it.form.startsWith("sono") }
        assertEquals(Verdict.CORRECT, VerbDrill.grade("sono andato", q))
        assertEquals(Verdict.CORRECT, VerbDrill.grade("sono andata", q))
        assertEquals(Verdict.CORRECT, VerbDrill.grade("io sono andato", q))
        assertEquals(Verdict.WRONG, VerbDrill.grade("ho andato", q))
        val imp = VerbDrill.questionsFor(andare, TenseSet.CONDITIONAL.titles).first()
        assertEquals(Verdict.CORRECT, VerbDrill.grade("va'", imp))
    }

    @Test
    fun aRoundUsesDifferentVerbs() {
        val other = row(11, "venire", "verb", "to come", forms = """{"tables":[{"title":"Indicativo presente","columns":["",""],"rows":[["io","vengo"]]}]}""")
        val round = VerbDrill.round(listOf(andare, other), TenseSet.EVERYDAY, size = 5, random = Random(3))
        assertEquals(2, round.size)
        assertEquals(setOf("andare", "venire"), round.map { it.infinitive }.toSet())
    }
}
