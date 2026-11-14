package com.gleaped.italianlearning.domain.srs

import com.gleaped.italianlearning.domain.dictionary.LemmaRow

/**
 * "Parole di base": the most frequent nouns, verbs, adjectives and adverbs of Italian, fed into the
 * review deck a few a day in order of frequency, skipping words the course already teaches.
 * Function words (articles, prepositions, pronouns) are left to the lessons. Pure Kotlin.
 */
object CoreDeck {
    /** Source "unit" of core-word cards. */
    const val UNIT_ID = "~core"

    /** Words more frequent than this rank are taken as known at B1 and not offered. */
    const val START_RANK = 250

    /** Rank after which no more words are offered. */
    const val END_RANK = 6000

    const val DEFAULT_PER_DAY = 5
    const val MAX_PER_DAY = 15

    /** No more words are added while this many new cards are already waiting. */
    const val MAX_WAITING = 60

    /** Parts of speech worth learning one by one. */
    val POS = setOf("noun", "verb", "adj", "adv")

    private const val PREFIX = "c:"
    private val BRACKET_LEAD = Regex("^(?:\\([^)]*\\)\\s*)+")
    private val FORM_OF = Regex("^(?:inflection|plural|feminine|masculine|alternative|obsolete|archaic|misspelling|superlative|diminutive|augmentative|past participle|present participle|gerund)\\b.*\\bof\\b", RegexOption.IGNORE_CASE)

    fun cardId(lemmaId: Long): String = PREFIX + lemmaId

    fun isCoreCard(cardId: String): Boolean = cardId.startsWith(PREFIX)

    /**
     * The meaning side of a card: the first two senses with their leading notes removed
     * ("(transitive) to support" becomes "to support"); null when no clear sense is left.
     */
    fun back(row: LemmaRow): String? {
        val senses = row.glosses
            .map { BRACKET_LEAD.replace(it, "").trim() }
            .filter { it.length >= 2 && !FORM_OF.containsMatchIn(it) }
            .distinct()
            .take(2)
        return senses.joinToString("; ").ifBlank { null }
    }

    /** The Italian side: the headword, with the gender of nouns as the other cards write it ("azienda (f.)"). */
    fun front(row: LemmaRow): String {
        val gender = when (row.gender) {
            "m" -> " (m.)"
            "f" -> " (f.)"
            "mf" -> " (m./f.)"
            else -> ""
        }
        return if (row.pos == "noun") row.lemma + gender else row.lemma
    }

    /** True when [row] is a word to offer: a content word with a clear meaning that the course does not already teach. */
    fun usable(row: LemmaRow, known: Set<String>): Boolean {
        if (row.pos !in POS || row.lemma.length < 3) return false
        if (row.lemma.lowercase() in known) return false
        if (row.lemma.any { it.isDigit() || it == ' ' }) return false
        return back(row) != null
    }

    /** Up to [limit] usable rows from [rows] (already in frequency order). */
    fun select(rows: List<LemmaRow>, known: Set<String>, limit: Int): List<LemmaRow> =
        rows.filter { usable(it, known) }.take(limit)

    private val ARTICLE = Regex("^(?:il|lo|la|i|gli|le|un|uno|una)\\s+|^(?:l|un)['’]\\s*", RegexOption.IGNORE_CASE)
    private val BRACKETED = Regex("\\s*\\([^)]*\\)")

    /** The headwords of course vocabulary, as bare lowercase words (no article, no notes), for [usable]. */
    fun knownWords(courseFronts: Collection<String>): Set<String> = courseFronts.flatMap { front ->
        BRACKETED.replace(front, " ").split('/').map { ARTICLE.replace(it.trim(), "").trim().lowercase() }
    }.filter { it.isNotEmpty() }.toSet()
}
