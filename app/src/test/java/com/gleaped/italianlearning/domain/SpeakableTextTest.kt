package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.speech.SpeakableText
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakableTextTest {

    @Test
    fun `gender notes are not spoken`() {
        assertEquals("l'umido", SpeakableText.forCard("l'umido (m.)"))
        assertEquals("l'allerta meteo", SpeakableText.forCard("l'allerta meteo (f.)"))
    }

    @Test
    fun `notes in the middle are removed without doubling spaces`() {
        assertEquals("fare una domanda", SpeakableText.forCard("fare (porre) una domanda"))
        assertEquals("buttare", SpeakableText.forCard("buttare (via)"))
    }

    @Test
    fun `plain text is unchanged`() {
        assertEquals("Come si fa a…?", SpeakableText.forCard("Come si fa a…?"))
        assertEquals("la raccolta differenziata", SpeakableText.forCard("la raccolta differenziata"))
    }

    @Test
    fun `no space is left before punctuation`() {
        assertEquals("tenerci, volerci", SpeakableText.forCard("tenerci (a), volerci"))
    }

    @Test
    fun `text that is only a note is kept`() {
        assertEquals("(m.)", SpeakableText.forCard("(m.)"))
    }
}
