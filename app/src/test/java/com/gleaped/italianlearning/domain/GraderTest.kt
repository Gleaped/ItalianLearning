package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.grading.AnswerNormalizer
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {

    @Test
    fun normalisesCaseWhitespaceFinalPunctuationAndApostrophes() {
        assertEquals("l'acqua è fredda", AnswerNormalizer.normalize("  L’acqua   è fredda!  "))
        assertEquals("l'acqua", AnswerNormalizer.normalize("l' acqua"))
        assertEquals("sì grazie", AnswerNormalizer.normalize("Sì, grazie."))
        assertEquals("perché", AnswerNormalizer.normalize("Perché?!"))
        assertEquals("perche", AnswerNormalizer.stripAccents("perché"))
    }

    @Test
    fun exactAnswerIsCorrect() {
        val r = Grader.gradeTyped("il conto, per favore", listOf("Il conto, per favore."))
        assertEquals(Verdict.CORRECT, r.verdict)
    }

    @Test
    fun internalCommasAreIgnored() {
        assertEquals(Verdict.CORRECT, Grader.gradeTyped("Sì grazie", listOf("Sì, grazie")).verdict)
    }

    @Test
    fun anyAcceptedAnswerCounts() {
        val r = Grader.gradeTyped("può portare", listOf("porta", "può portare"))
        assertEquals(Verdict.CORRECT, r.verdict)
        assertEquals("può portare", r.expected)
    }

    @Test
    fun curlyApostropheMatchesStraight() {
        assertEquals(Verdict.CORRECT, Grader.gradeTyped("Un’aranciata", listOf("un'aranciata")).verdict)
    }

    @Test
    fun missingAccentIsAlmost() {
        val r = Grader.gradeTyped("perche", listOf("perché"))
        assertEquals(Verdict.ALMOST, r.verdict)
        assertEquals("perché", r.expected)
        assertEquals(Verdict.ALMOST, Grader.gradeTyped("citta", listOf("città")).verdict)
        // e' typed for è
        assertEquals(Verdict.ALMOST, Grader.gradeTyped("e' tornata", listOf("è tornata")).verdict)
    }

    @Test
    fun wrongAccentIsAlmostAndWrongWordIsWrong() {
        assertEquals(Verdict.ALMOST, Grader.gradeTyped("perchè", listOf("perché")).verdict)
        assertEquals(Verdict.WRONG, Grader.gradeTyped("perciò", listOf("perché")).verdict)
        assertEquals(Verdict.WRONG, Grader.gradeTyped("", listOf("perché")).verdict)
    }

    @Test
    fun conjugationStripsSubjectPronoun() {
        assertEquals(Verdict.CORRECT, Grader.gradeConjugation("io ero", "ero").verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeConjugation("Lei è rimasta", "è rimasta").verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeConjugation("noi siamo rimasti", "siamo rimasti").verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeConjugation("sono rimasto", "sono rimasto").verdict)
        assertEquals(Verdict.ALMOST, Grader.gradeConjugation("lui e rimasto", "è rimasto").verdict)
        assertEquals(Verdict.WRONG, Grader.gradeConjugation("io sono", "ero").verdict)
    }

    @Test
    fun sentenceBuildIgnoresPunctuation() {
        val tiles = listOf("Mentre", "aspettavo", "l'autobus", "ho", "incontrato", "una", "vicina")
        assertEquals(Verdict.CORRECT, Grader.gradeSentenceBuild(tiles, tiles).verdict)
        assertEquals(Verdict.CORRECT, Grader.gradeSentenceBuild(tiles, tiles.dropLast(1) + "vicina.").verdict)
        assertEquals(Verdict.WRONG, Grader.gradeSentenceBuild(tiles.reversed(), tiles).verdict)
    }

    @Test
    fun choiceAndCombine() {
        assertEquals(Verdict.CORRECT, Grader.gradeChoice(1, 1, listOf("a", "b")).verdict)
        assertEquals(Verdict.WRONG, Grader.gradeChoice(0, 1, listOf("a", "b")).verdict)
        assertEquals(Verdict.CORRECT, Grader.combine(listOf(Verdict.CORRECT, Verdict.CORRECT)))
        assertEquals(Verdict.ALMOST, Grader.combine(listOf(Verdict.CORRECT, Verdict.ALMOST)))
        assertEquals(Verdict.ALMOST, Grader.combine(listOf(Verdict.CORRECT, Verdict.WRONG)))
        assertEquals(Verdict.WRONG, Grader.combine(listOf(Verdict.CORRECT, Verdict.WRONG, Verdict.WRONG)))
    }

    @Test
    fun matchPairsByMistakes() {
        assertEquals(Verdict.CORRECT, Grader.gradeMatchPairs(0))
        assertEquals(Verdict.ALMOST, Grader.gradeMatchPairs(2))
        assertEquals(Verdict.WRONG, Grader.gradeMatchPairs(3))
    }

    @Test
    fun speakRepeatWordOverlap() {
        assertEquals(1.0, Grader.wordOverlap("vorrei un cornetto", "Vorrei un cornetto."), 1e-9)
        assertEquals(2.0 / 3, Grader.wordOverlap("vorrei cornetto", "Vorrei un cornetto."), 1e-9)
        val (verdict, score) = Grader.gradeSpeakRepeat(listOf("forse un panino", "vorrei un cornetto"), "Vorrei un cornetto.")
        assertEquals(Verdict.CORRECT, verdict)
        assertEquals(1.0, score, 1e-9)
        assertEquals(Verdict.WRONG, Grader.gradeSpeakRepeat(emptyList(), "ciao").first)
    }

    @Test
    fun keywordsIncludingApostrophesAndPhrases() {
        assertTrue(Grader.containsKeyword("da bambino c'era un giardino", "c'era"))
        assertTrue(Grader.containsKeyword("c era un giardino", "c'era"))
        assertTrue(Grader.containsKeyword("poi è successo un imprevisto", "è successo"))
        assertTrue(Grader.containsKeyword("poi e successo", "è successo"))
        assertTrue(Grader.containsKeyword("ho prenotato un tavolo", "prenotare"))
        assertFalse(Grader.containsKeyword("ero a casa", "era"))
        val (verdict, used) = Grader.gradeKeywords(listOf("Vorrei un'aranciata per favore"), listOf("vorrei", "per favore"))
        assertEquals(Verdict.CORRECT, verdict)
        assertEquals(2, used.size)
        assertEquals(Verdict.ALMOST, Grader.gradeKeywords(listOf("un'aranciata per favore"), listOf("vorrei", "per favore", "grazie")).first)
        assertEquals(Verdict.WRONG, Grader.gradeKeywords(listOf("ciao"), listOf("vorrei")).first)
    }

    @Test
    fun freeWriteSelfCheck() {
        assertEquals(Verdict.ALMOST, Grader.gradeFreeWrite(10, 30, 3, 3))
        assertEquals(Verdict.CORRECT, Grader.gradeFreeWrite(40, 30, 3, 3))
        assertEquals(Verdict.WRONG, Grader.gradeFreeWrite(40, 30, 0, 3))
        assertEquals(4, AnswerNormalizer.wordCount("Il mio bar preferito — "))
    }
}
