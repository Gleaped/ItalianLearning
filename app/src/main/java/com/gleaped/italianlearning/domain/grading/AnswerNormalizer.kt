package com.gleaped.italianlearning.domain.grading

import java.text.Normalizer
import java.util.Locale

/**
 * Text normalisation used for comparing typed and spoken answers.
 *
 * Rules (docs/lesson-format.md plus content conventions):
 *  - case-insensitive, surrounding whitespace trimmed, inner whitespace collapsed
 *  - curly / typographic apostrophes and accents-used-as-apostrophes become `'`
 *  - a space after an apostrophe is removed ("l' acqua" == "l'acqua")
 *  - final punctuation is ignored, and so are internal commas
 *  - surrounding quotes are ignored
 *  - accents are significant, but [stripAccents] lets callers detect "almost"
 */
object AnswerNormalizer {

    private val APOSTROPHES = Regex("[‘’ʼ`´′]")
    private val WHITESPACE = Regex("\\s+")
    private val SPACE_AFTER_APOSTROPHE = Regex("'\\s+")
    private val TRAILING_PUNCT = Regex("[\\s.!?…;:,\"»«“”]+$")
    private val LEADING_PUNCT = Regex("^[\\s\"«»“”¿¡]+")
    private val COMMAS = Regex("\\s*,\\s*")
    private val DASHES = Regex("[–—]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        s = s.lowercase(Locale.ITALIAN)
        s = APOSTROPHES.replace(s, "'")
        s = DASHES.replace(s, "-")
        s = WHITESPACE.replace(s, " ").trim()
        s = LEADING_PUNCT.replace(s, "")
        s = TRAILING_PUNCT.replace(s, "")
        s = COMMAS.replace(s, " ")
        s = SPACE_AFTER_APOSTROPHE.replace(s, "'")
        s = WHITESPACE.replace(s, " ").trim()
        return s
    }

    /** Removes diacritics: "perché" -> "perche". Input should already be normalised. */
    fun stripAccents(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return Normalizer.normalize(COMBINING_MARKS.replace(decomposed, ""), Normalizer.Form.NFC)
    }

    /** Normalised, punctuation-free form: everything that is not a letter or digit becomes a space. */
    fun withoutPunctuation(input: String): String =
        WHITESPACE.replace(NON_WORD.replace(normalize(input), " "), " ").trim()

    /** Accent-insensitive word tokens, for speech scoring. Apostrophes split words ("c'era" -> c, era). */
    fun tokens(input: String): List<String> {
        val cleaned = stripAccents(withoutPunctuation(input))
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(' ').filter { it.isNotEmpty() }
    }

    fun wordCount(input: String): Int =
        input.trim().split(WHITESPACE).count { token -> token.any { it.isLetterOrDigit() } }
}
