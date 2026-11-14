package com.gleaped.italianlearning.domain.srs

import com.gleaped.italianlearning.domain.grading.AnswerNormalizer
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict

/**
 * What a review card asks. The kind is read from the card id, so no extra column is stored:
 *  - `v:` / `f:` ids are [RECOGNISE] cards: see the Italian, recall the meaning
 *  - `d:<lemmaId>` is a [RECOGNISE] card the learner added from the dictionary
 *  - `r:<id of the recognise card>` is its [PRODUCE] twin: see the meaning, produce the Italian
 *  - `g:<unitId>:<tipId>` is a [GRAMMAR] card: answer the tip's quick challenge
 */
enum class CardKind { RECOGNISE, PRODUCE, GRAMMAR }

object Cards {
    private const val PRODUCE_PREFIX = "r:"
    private const val GRAMMAR_PREFIX = "g:"

    /** A produce card is added once its recognise card has been known this many times in a row. */
    const val PRODUCE_AFTER_REPETITIONS = 2

    fun kindOf(cardId: String): CardKind = when {
        cardId.startsWith(PRODUCE_PREFIX) -> CardKind.PRODUCE
        cardId.startsWith(GRAMMAR_PREFIX) -> CardKind.GRAMMAR
        else -> CardKind.RECOGNISE
    }

    fun produceId(recogniseCardId: String): String = PRODUCE_PREFIX + recogniseCardId

    /** Source "unit" of cards added from the dictionary. */
    const val DICTIONARY_UNIT_ID = "~dizionario"

    fun dictionaryId(lemmaId: Long): String = "d:$lemmaId"

    /** The meaning side of a dictionary card: the first few senses, most common first. */
    fun dictionaryBack(glosses: List<String>, limit: Int = 3): String =
        glosses.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(limit).joinToString("; ")

    fun grammarId(unitId: String, tipId: String): String = "$GRAMMAR_PREFIX$unitId:$tipId"

    /** The tip id of a grammar card whose unit is [unitId], or null. */
    fun tipIdOf(cardId: String, unitId: String): String? =
        cardId.removePrefix("$GRAMMAR_PREFIX$unitId:").takeIf { it != cardId && it.isNotBlank() }

    private val BRACKETED = Regex("\\s*\\([^)]*\\)")
    private val ARTICLE_WORDS = setOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una")
    private val ARTICLE = Regex("^(il|lo|la|i|gli|le|un|uno|una)\\s+|^(l|un)['’]\\s*", RegexOption.IGNORE_CASE)
    const val GAP = "_____"

    /** The Italian of a card without bracketed notes: "l'umido (m.)" -> "l'umido". */
    private fun plain(front: String): String = BRACKETED.replace(front, " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Answers accepted when the learner types the Italian of a card: the whole entry, each
     * alternative of "a / b", and each of those without its article.
     */
    fun acceptedRecall(front: String): List<String> {
        val whole = plain(front)
        val pieces = whole.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        // "il/la collega": the bare articles are not answers, but "il collega" is.
        val noun = pieces.lastOrNull()?.let { ARTICLE.replace(it, "").trim() }.orEmpty()
        val parts = pieces.map { if (it.lowercase() in ARTICLE_WORDS) "$it $noun".trim() else it }
            .filter { it.lowercase() !in ARTICLE_WORDS }
        val base = (listOf(whole) + parts).distinct()
        return (base + base.map { ARTICLE.replace(it, "").trim() }).filter { it.isNotEmpty() }.distinct()
    }

    /** [main] with the [extra] items spread through it, one after every third item, the rest at the end. */
    fun <T> spread(main: List<T>, extra: List<T>): List<T> {
        if (extra.isEmpty()) return main
        val out = ArrayList<T>(main.size + extra.size)
        var next = 0
        main.forEachIndexed { index, item ->
            out += item
            if ((index + 1) % 3 == 0 && next < extra.size) out += extra[next++]
        }
        while (next < extra.size) out += extra[next++]
        return out
    }

    /** Grades a typed recall; accents only off is "almost". */
    fun gradeRecall(typed: String, front: String): Verdict = Grader.gradeTyped(typed, acceptedRecall(front)).verdict

    /**
     * [example] with the card's word replaced by a gap, as context for producing it; null when
     * the word does not occur in the example as written (for instance a conjugated verb).
     */
    fun cloze(example: String?, front: String): String? {
        if (example.isNullOrBlank()) return null
        val candidates = acceptedRecall(front).sortedByDescending { it.length }
        for (candidate in candidates) {
            if (AnswerNormalizer.wordCount(candidate) == 0) continue
            val pattern = Regex("(?<![\\p{L}])" + Regex.escape(candidate) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
            val match = pattern.find(example) ?: continue
            return example.replaceRange(match.range, GAP)
        }
        return null
    }
}
