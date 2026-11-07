package com.gleaped.italianlearning.domain.dictionary

import com.gleaped.italianlearning.content.ContentParser
import com.gleaped.italianlearning.content.reference.ChartTable
import kotlinx.serialization.Serializable

/** One row of the `lemma` table. */
data class LemmaRow(
    val id: Long,
    val lemma: String,
    val pos: String,
    val gender: String?,
    val glossEn: String,
    val glossIt: String?,
    val exampleIt: String?,
    val exampleEn: String?,
    val freqRank: Int?,
    val notes: String?,
    val formsJson: String,
) {
    /** Senses, most common first ("go ; walk" → ["go", "walk"]). */
    val glosses: List<String> get() = glossEn.split(" ; ", ";").map { it.trim() }.filter { it.isNotEmpty() }

    val firstGloss: String get() = glosses.firstOrNull().orEmpty()

    /** "verbo", "nome m.", "aggettivo"... */
    val posLabel: String
        get() {
            val p = POS_LABELS[pos] ?: pos
            val g = when (gender) {
                "m" -> "m."
                "f" -> "f."
                "mf" -> "m./f."
                else -> null
            }
            return if (g != null) "$p $g" else p
        }

    companion object {
        val POS_LABELS = mapOf(
            "verb" to "verbo", "noun" to "nome", "adj" to "aggettivo", "adv" to "avverbio",
            "pron" to "pronome", "prep" to "preposizione", "conj" to "congiunzione",
            "det" to "determinante", "interj" to "interiezione", "other" to "altro",
        )
    }
}

/** A `form` row matched by an exact lookup, joined to its lemma. */
data class FormMatch(val form: String, val tags: String, val lemmaId: Long)

/** One lemma in the result list, with the inflected forms that matched (empty for prefix hits). */
data class LookupGroup(val lemma: LemmaRow, val matches: List<FormMatch>) {
    /** True when the query matched an inflected form rather than the lemma itself. */
    val matchedInflection: Boolean
        get() = matches.isNotEmpty() && matches.none { WordNormalizer.normalize(it.form) == WordNormalizer.normalize(lemma.lemma) }

    /**
     * "andò → andare · indicativo passato remoto, lui/lei"; null when the lemma itself matched.
     * Several readings of one form are joined with " / " (at most three).
     */
    val matchLine: String?
        get() {
            if (!matchedInflection) return null
            val form = matches.first().form
            val tags = matches.map { it.tags }.filter { it.isNotBlank() }.distinct()
            val shown = tags.take(3).joinToString(" / ") + if (tags.size > 3) " …" else ""
            return if (shown.isBlank()) "$form → ${lemma.lemma}" else "$form → ${lemma.lemma} · $shown"
        }
}

@Serializable
private data class FormsDto(val tables: List<ChartTable> = emptyList())

/** A group of forms tables under one heading (verbs: one per mood). */
data class FormsGroup(val title: String?, val tables: List<ChartTable>)

object FormsParser {
    /** Parses forms_json; a broken value yields no tables rather than an error. */
    fun parse(formsJson: String?): List<ChartTable> {
        if (formsJson.isNullOrBlank()) return emptyList()
        return try {
            ContentParser.json.decodeFromString(FormsDto.serializer(), formsJson).tables.filter { it.rows.isNotEmpty() }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private val MOODS = listOf("Indicativo", "Congiuntivo", "Condizionale", "Imperativo")
    private val INDEFINITE = listOf("infinito", "gerundio", "participio")
    private val INDICATIVE_TENSES = listOf(
        "presente", "imperfetto", "passato remoto", "futuro", "passato prossimo",
        "trapassato prossimo", "trapassato remoto",
    )

    /** The mood a verb table belongs to, from its title. */
    fun moodOf(title: String): String {
        val t = title.trim().lowercase()
        MOODS.firstOrNull { t.startsWith(it.lowercase()) }?.let { return it }
        if (INDEFINITE.any { t.startsWith(it) }) return "Forme indefinite"
        if (INDICATIVE_TENSES.any { t.startsWith(it) }) return "Indicativo"
        return "Altre forme"
    }

    /**
     * Verbs: tables grouped by mood in the usual order (indicativo, congiuntivo, condizionale,
     * imperativo, forme indefinite, others), keeping the source order inside each group.
     * Other parts of speech: one untitled group with every table.
     */
    fun group(pos: String, tables: List<ChartTable>): List<FormsGroup> {
        if (tables.isEmpty()) return emptyList()
        if (pos != "verb") return listOf(FormsGroup(null, tables))
        val order = MOODS + listOf("Forme indefinite", "Altre forme")
        return tables.groupBy { moodOf(it.title) }
            .toList()
            .sortedBy { order.indexOf(it.first) }
            .map { (mood, list) -> FormsGroup(mood, list) }
    }
}
