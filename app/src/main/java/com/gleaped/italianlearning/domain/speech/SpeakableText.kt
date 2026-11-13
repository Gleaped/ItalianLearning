package com.gleaped.italianlearning.domain.speech

/** Prepares text for the Italian voice. Pure Kotlin, no Android dependencies. */
object SpeakableText {

    private val BRACKETED = Regex("\\s*\\([^)]*\\)")
    private val WHITESPACE = Regex("\\s+")
    private val SPACE_BEFORE_PUNCT = Regex("\\s+([,.;:!?])")

    /**
     * A vocabulary card as it should be spoken: bracketed notes are for the eye only, so
     * "l'umido (m.)" is read as "l'umido" and "fare (porre) una domanda" as "fare una domanda".
     * Falls back to the original text when nothing else is left.
     */
    fun forCard(text: String): String {
        val stripped = BRACKETED.replace(text, " ")
        val tidy = SPACE_BEFORE_PUNCT.replace(WHITESPACE.replace(stripped, " ").trim(), "$1")
        return tidy.ifBlank { text }
    }
}
