package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.speech.AudioKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioKeyTest {
    @Test
    fun normaliseCollapsesAndTrimsAllWhitespace() {
        assertEquals("Ciao a tutti", AudioKey.normalise("  Ciao \n\t a tutti  "))
        assertEquals("", AudioKey.normalise("   "))
    }

    @Test
    fun keyIgnoresLayout() {
        assertEquals(AudioKey.key("Come va?", "a"), AudioKey.key(" Come  va? ", "a"))
    }

    /**
     * Android's regex engine rejects the "(?U)" inline flag that the JVM accepts, which crashed every
     * playback on a phone while these JVM tests passed. Keep it out of the app code.
     */
    @Test
    fun appCodeAvoidsRegexFlagsAndroidRejects() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val bad = root.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readText().contains("(?U)") }
            .map { it.name }.toList()
        assertFalse("Android rejects (?U): $bad", bad.isNotEmpty())
    }
}
