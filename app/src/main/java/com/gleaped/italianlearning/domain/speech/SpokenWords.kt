package com.gleaped.italianlearning.domain.speech

import com.gleaped.italianlearning.domain.grading.AnswerNormalizer

/** A word of the sentence to say, and whether the recogniser heard it. */
data class SpokenWord(val text: String, val heard: Boolean)

/** Word-by-word feedback on a spoken sentence. Pure Kotlin, no Android dependencies. */
object SpokenWords {
    private val WHITESPACE = Regex("\\s+")

    /**
     * The words of [target] as written, each marked heard or missed. Accents, case and
     * punctuation are ignored, and a word said once counts once ("no, no" needs two).
     */
    fun mark(heard: String, target: String): List<SpokenWord> {
        val remaining = AnswerNormalizer.tokens(heard).groupingBy { it }.eachCount().toMutableMap()
        return target.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { word ->
            // "l'acqua" is two spoken tokens; the written word is heard only if both were.
            val parts = AnswerNormalizer.tokens(word)
            val ok = parts.all { (remaining[it] ?: 0) > 0 }
            if (ok) parts.forEach { remaining[it] = remaining.getValue(it) - 1 }
            SpokenWord(word, ok)
        }
    }

    /** Of the recogniser's [alternatives], the one closest to [target]. */
    fun best(alternatives: List<String>, target: String): String =
        alternatives.maxByOrNull { alt -> mark(alt, target).count { it.heard } }.orEmpty()

    fun allHeard(words: List<SpokenWord>): Boolean = words.all { it.heard }
}
