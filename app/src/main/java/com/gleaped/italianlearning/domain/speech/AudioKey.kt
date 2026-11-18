package com.gleaped.italianlearning.domain.speech

import java.security.MessageDigest

/**
 * Names the pre-rendered audio of one spoken line. The render script in tools/audio uses the
 * same recipe (SHA-256 of "voice|text", first 16 hex digits), so a line the script voiced
 * is found by the app. Pure Kotlin, so the recipe can be unit-tested.
 */
object AudioKey {
    /** The main voice, and the second speaker of a dialogue or the examiner. */
    const val VOICE_MAIN = "a"
    const val VOICE_SECOND = "b"

    /**
     * Whitespace collapsed and trimmed, so layout differences do not change the key. Done by hand
     * rather than with a "(?U)" regex: Android's regex engine rejects that flag, which crashed every
     * playback. Matches Python's str.split() used by the render script.
     */
    fun normalise(text: String): String {
        val out = StringBuilder(text.length)
        var gap = false
        for (c in text) {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085') {
                gap = out.isNotEmpty()
            } else {
                if (gap) out.append(' ')
                gap = false
                out.append(c)
            }
        }
        return out.toString()
    }

    fun key(text: String, voice: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$voice|${normalise(text)}".toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** The voice of turn [index] of a dialogue that starts with [second] or the main voice. */
    fun voiceForTurn(index: Int, second: Boolean): String =
        if (second != (index % 2 == 1)) VOICE_SECOND else VOICE_MAIN

    /** True for a file name the pack may contain: 16 hex digits and .m4a. */
    fun isPackFile(name: String): Boolean = PACK_FILE.matches(name)

    private val PACK_FILE = Regex("[0-9a-f]{16}\\.m4a")
}
