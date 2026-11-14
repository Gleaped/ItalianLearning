package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.speech.SpeakableText
import com.gleaped.italianlearning.domain.speech.SpokenWords
import com.gleaped.italianlearning.domain.speech.VoiceChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFeedbackTest {

    private fun missed(heard: String, target: String) =
        SpokenWords.mark(heard, target).filter { !it.heard }.map { it.text }

    @Test
    fun `every word heard`() {
        val words = SpokenWords.mark("vorrei un caffè per favore", "Vorrei un caffè, per favore.")
        assertTrue(SpokenWords.allHeard(words))
        assertEquals(listOf("Vorrei", "un", "caffè,", "per", "favore."), words.map { it.text })
    }

    @Test
    fun `missed words are marked as written`() {
        assertEquals(listOf("prenotare", "due."), missed("vorrei un tavolo per", "Vorrei prenotare un tavolo per due."))
    }

    @Test
    fun `accents and case do not matter`() {
        assertEquals(emptyList<String>(), missed("PERCHE no", "Perché no?"))
    }

    @Test
    fun `a word said once counts once`() {
        assertEquals(listOf("no."), missed("no grazie", "No, grazie, no."))
    }

    @Test
    fun `an elided word needs both parts`() {
        assertEquals(emptyList<String>(), missed("bevo l'acqua", "Bevo l'acqua."))
        assertEquals(listOf("l'acqua."), missed("bevo acqua", "Bevo l'acqua."))
    }

    @Test
    fun `best alternative is the closest one`() {
        assertEquals("vado al mare", SpokenWords.best(listOf("vado a male", "vado al mare"), "Vado al mare."))
        assertEquals("", SpokenWords.best(emptyList(), "Vado al mare."))
    }

    @Test
    fun `dialogue turns split at dashes`() {
        assertEquals(
            listOf("Come va?", "Bene, grazie.", "Meno male!"),
            SpeakableText.dialogueTurns("Come va? – Bene, grazie. — Meno male!"),
        )
        assertEquals(listOf("Lei è di qui?", "No."), SpeakableText.dialogueTurns("— Lei è di qui? — No."))
    }

    @Test
    fun `text without speaker dashes is one turn`() {
        assertEquals(listOf("Guardo un film."), SpeakableText.dialogueTurns("Guardo un film."))
        assertEquals(listOf("Un ex-collega mi ha scritto."), SpeakableText.dialogueTurns("Un ex-collega mi ha scritto."))
    }

    @Test
    fun `voices are ranked by quality then Italy then name`() {
        val ranked = VoiceChoice.rank(
            listOf(
                VoiceChoice.Candidate("it-ch-a", 400, false),
                VoiceChoice.Candidate("it-it-b", 300, true),
                VoiceChoice.Candidate("it-it-c", 400, true),
                VoiceChoice.Candidate("it-it-a", 400, true),
            ),
        )
        assertEquals(listOf("it-it-a", "it-it-c", "it-ch-a", "it-it-b"), ranked)
    }

    @Test
    fun `main voice is the preferred one when installed`() {
        val ranked = listOf("a", "b", "c")
        assertEquals("b", VoiceChoice.main(ranked, "b"))
        assertEquals("a", VoiceChoice.main(ranked, ""))
        assertEquals("a", VoiceChoice.main(ranked, "gone"))
        assertNull(VoiceChoice.main(emptyList(), "b"))
    }

    @Test
    fun `second voice differs from the main one`() {
        assertEquals("b", VoiceChoice.second(listOf("a", "b"), "a"))
        assertEquals("a", VoiceChoice.second(listOf("a", "b"), "b"))
        assertNull(VoiceChoice.second(listOf("a"), "a"))
        assertFalse(VoiceChoice.second(listOf("a", "b", "c"), "a") == "a")
    }
}
