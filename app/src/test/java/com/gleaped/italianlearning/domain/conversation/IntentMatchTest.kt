package com.gleaped.italianlearning.domain.conversation

import com.gleaped.italianlearning.domain.speech.AudioKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentMatchTest {
    private fun heard(s: String) = IntentMatch.tokens(s)

    @Test
    fun wordsMatchWhenEqualOneLetterOffOrSharingAStem() {
        assertTrue(IntentMatch.wordMatches("prenotato", "prenotato"))
        assertTrue(IntentMatch.wordMatches("prenotatto", "prenotato"))
        assertTrue(IntentMatch.wordMatches("prenotazione", "prenotato"))
        assertFalse(IntentMatch.wordMatches("si", "no"))
        assertFalse(IntentMatch.wordMatches("casa", "cosa"))
    }

    @Test
    fun aPhraseNeedsAllItsWordsInAnyOrder() {
        assertEquals(2, IntentMatch.score(heard("per favore, un caffè"), "per favore"))
        assertEquals(0, IntentMatch.score(heard("per un caffè"), "per favore"))
        assertEquals(1, IntentMatch.score(heard("Sì, grazie"), "sì"))
    }

    @Test
    fun accentsAndApostrophesDoNotMatter() {
        assertEquals(2, IntentMatch.score(heard("dov'è la stazione"), "dov'è"))
        assertEquals(1, IntentMatch.score(heard("un te caldo"), "tè"))
    }

    @Test
    fun audioKeysMatchTheRenderScript() {
        // Computed with the render script's recipe: sha256("a|Buonasera, come va?")[:16].
        assertEquals("755565aedab8d4ab", AudioKey.key("Buonasera, come va?", AudioKey.VOICE_MAIN))
        assertEquals(AudioKey.key("Buonasera, come va?", "a"), AudioKey.key("  Buonasera,   come va? ", "a"))
        assertTrue(AudioKey.key("Ciao", "a") != AudioKey.key("Ciao", "b"))
        assertTrue(AudioKey.isPackFile("755565aedab8d4ab.m4a"))
        assertFalse(AudioKey.isPackFile("../755565aedab8d4ab.m4a"))
        assertFalse(AudioKey.isPackFile("manifest.json"))
    }

    @Test
    fun dialogueTurnsAlternateVoices() {
        assertEquals("a", AudioKey.voiceForTurn(0, second = false))
        assertEquals("b", AudioKey.voiceForTurn(1, second = false))
        assertEquals("b", AudioKey.voiceForTurn(0, second = true))
        assertEquals("a", AudioKey.voiceForTurn(1, second = true))
    }

    @Test
    fun commonSlipsAreSpottedWithTheBetterForm() {
        val found = LearnerErrors.find("Io ho andato a Roma. Credo che è giusto. Se avrei tempo, verrei.").associateBy { it.id }
        assertEquals("sono andato", found.getValue("essere-verbs").better)
        assertEquals("credo che sia", found.getValue("congiuntivo-opinione").better)
        assertEquals("se avessi", found.getValue("se-condizionale").better)
        assertTrue(LearnerErrors.find("Sono andato a Roma e credo che sia giusto.").isEmpty())
    }
}
