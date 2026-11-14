package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.srs.CardKind
import com.gleaped.italianlearning.domain.srs.Cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsTest {

    @Test
    fun `kind is read from the card id`() {
        assertEquals(CardKind.RECOGNISE, Cards.kindOf("v:b1-u1:la casa"))
        assertEquals(CardKind.RECOGNISE, Cards.kindOf("f:b1-u1-l1:grazie"))
        assertEquals(CardKind.PRODUCE, Cards.kindOf(Cards.produceId("v:b1-u1:la casa")))
        assertEquals(CardKind.GRAMMAR, Cards.kindOf(Cards.grammarId("b1-u1", "tip-2")))
    }

    @Test
    fun `tip id is recovered from a grammar card`() {
        assertEquals("tip-2", Cards.tipIdOf(Cards.grammarId("b1-u1", "tip-2"), "b1-u1"))
        assertNull(Cards.tipIdOf("v:b1-u1:la casa", "b1-u1"))
        assertNull(Cards.tipIdOf(Cards.grammarId("b1-u1", "tip-2"), "b2-u9"))
    }

    @Test
    fun `recall accepts the word with or without its article`() {
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("l'umido", "l'umido (m.)"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("umido", "l'umido (m.)"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("raccolta differenziata", "la raccolta differenziata"))
        assertEquals(Verdict.WRONG, Cards.gradeRecall("la raccolta", "la raccolta differenziata"))
    }

    @Test
    fun `recall accepts each alternative`() {
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("il collega", "il/la collega"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("la collega", "il/la collega"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("collega", "il/la collega"))
        assertEquals(Verdict.WRONG, Cards.gradeRecall("il", "il/la collega"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("magari", "magari / forse"))
        assertEquals(Verdict.CORRECT, Cards.gradeRecall("forse", "magari / forse"))
    }

    @Test
    fun `missing accent is almost`() {
        assertEquals(Verdict.ALMOST, Cards.gradeRecall("la citta", "la città"))
    }

    @Test
    fun `cloze leaves the word out of its example`() {
        assertEquals(
            "Faccio ${Cards.GAP} ogni giorno.",
            Cards.cloze("Faccio la raccolta differenziata ogni giorno.", "la raccolta differenziata"),
        )
        assertEquals("Butta ${Cards.GAP} qui.", Cards.cloze("Butta l'umido qui.", "l'umido (m.)"))
    }

    @Test
    fun `cloze needs the word as written`() {
        assertNull(Cards.cloze("Ho buttato tutto.", "buttare"))
        assertNull(Cards.cloze(null, "buttare"))
        // Not inside a longer word.
        assertNull(Cards.cloze("È un casale antico.", "la casa"))
    }

    @Test
    fun `spread mixes extras through the main list`() {
        assertEquals(listOf(1, 2, 3, 9, 4, 5, 6, 8, 7), Cards.spread(listOf(1, 2, 3, 4, 5, 6, 7), listOf(9, 8)))
        assertEquals(listOf(1, 9, 8), Cards.spread(listOf(1), listOf(9, 8)))
        assertTrue(Cards.spread(emptyList<Int>(), emptyList()).isEmpty())
    }
}
