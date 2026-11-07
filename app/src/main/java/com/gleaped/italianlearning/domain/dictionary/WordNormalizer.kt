package com.gleaped.italianlearning.domain.dictionary

import java.text.Normalizer

/**
 * The dictionary's normalisation, identical to `form.form_norm` in dictionary.db
 * (docs/reference-and-dictionary-format.md): lowercase, accents and apostrophes stripped.
 *
 * 1. lowercase (locale-independent)
 * 2. NFD, then every combining mark removed ("andò" → "ando")
 * 3. apostrophes removed (' ’ ‘ ʼ ` ´): "dell'acqua" → "dellacqua", "po'" → "po"
 * 4. whitespace trimmed and collapsed
 */
object WordNormalizer {
    private val marks = Regex("\\p{Mn}+")
    private val apostrophes = Regex("['’‘ʼ`´]")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(apostrophes, "")
            .replace(spaces, " ")
            .trim()

    /** Lowercased and trimmed but with accents kept, for prefix search on lemma.lemma. */
    fun lowerKeepAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().replace(spaces, " ").trim()

    private fun isWordChar(c: Char): Boolean = c.isLetter() || c.category == CharCategory.NON_SPACING_MARK

    private fun isApostrophe(c: Char): Boolean = c == '\'' || c == '’' || c == 'ʼ' || c == '‘'

    /**
     * The word around character [offset] of [text], for long-press lookup. Letters only;
     * an elided article/preposition keeps its apostrophe ("dell'acqua": pressing "dell"
     * gives "dell'", pressing "acqua" gives "acqua"). Null when [offset] is not on a word.
     */
    fun wordAt(text: String, offset: Int): String? {
        if (text.isEmpty()) return null
        var i = offset.coerceIn(0, text.length - 1)
        if (!isWordChar(text[i])) {
            // A press just after the last letter (layouts report the next offset) still counts.
            if (i > 0 && isWordChar(text[i - 1])) i -= 1 else return null
        }
        var start = i
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = i + 1
        while (end < text.length && isWordChar(text[end])) end++
        val word = text.substring(start, end)
        val elided = end < text.length && isApostrophe(text[end]) &&
            end + 1 < text.length && isWordChar(text[end + 1])
        return if (elided) "$word'" else word
    }
}
