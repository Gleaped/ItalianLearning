package com.gleaped.italianlearning.content.reference

import com.gleaped.italianlearning.content.ContentParser
import kotlinx.serialization.Serializable

/**
 * The grammar reference shipped in assets/grammar/reference.json.
 * Format: docs/reference-and-dictionary-format.md.
 */
@Serializable
data class GrammarReference(
    val version: Int = 1,
    val sections: List<ReferenceSection> = emptyList(),
) {
    val topics: List<ReferenceTopic> get() = sections.flatMap { it.topics }

    fun topic(id: String): ReferenceTopic? = sections.firstNotNullOfOrNull { s -> s.topics.firstOrNull { it.id == id } }

    fun sectionOf(topicId: String): ReferenceSection? = sections.firstOrNull { s -> s.topics.any { it.id == topicId } }
}

@Serializable
data class ReferenceSection(
    val id: String,
    val title: String = "",
    val topics: List<ReferenceTopic> = emptyList(),
)

@Serializable
data class ReferenceTopic(
    val id: String,
    val title: String = "",
    val level: String = "",
    val summary: String = "",
    val body: String = "",
    val tables: List<ChartTable> = emptyList(),
    val examples: List<ReferenceExample> = emptyList(),
    val pitfalls: List<String> = emptyList(),
    val relatedUnitIds: List<String> = emptyList(),
)

@Serializable
data class ReferenceExample(val it: String = "", val en: String = "")

/**
 * A small chart: used both by the grammar reference and by the dictionary's forms_json.
 * Rows may be shorter than [columns]; [cells] pads them.
 */
@Serializable
data class ChartTable(
    val title: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val note: String? = null,
) {
    /** Number of columns to draw: the header or the widest row, whichever is wider. */
    val width: Int get() = maxOf(columns.size, rows.maxOfOrNull { it.size } ?: 0)

    /** True when the header row has at least one non-blank label worth drawing. */
    val hasHeader: Boolean get() = columns.any { it.isNotBlank() }

    /** Row [index] padded with blanks to [width]. */
    fun cells(index: Int): List<String> {
        val row = rows[index]
        return if (row.size >= width) row else row + List(width - row.size) { "" }
    }

    companion object {
        /** Spec limit so a chart fits a phone screen. */
        const val MAX_COLUMNS = 5
    }
}

object ReferenceParser {
    val LEVELS = setOf("A2", "B1", "B2", "C1")

    fun parse(text: String): GrammarReference = ContentParser.json.decodeFromString(GrammarReference.serializer(), text)

    /** Topics whose title, summary or level match every word of [query] (accent- and case-insensitive). */
    fun filter(reference: GrammarReference, query: String): List<ReferenceSection> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return reference.sections.filter { it.topics.isNotEmpty() }
        return reference.sections.mapNotNull { section ->
            val hits = section.topics.filter { t ->
                val hay = fold("${t.title} ${t.summary} ${t.level} ${section.title}")
                words.all { it in hay }
            }
            if (hits.isEmpty()) null else section.copy(topics = hits)
        }
    }

    /**
     * The reference topic a lesson's grammar note should link to: among topics listing
     * [unitId] in relatedUnitIds, the one whose title shares most words with the note's
     * title (then body); the first such topic when nothing overlaps. Null when none lists the unit.
     */
    fun topicForNote(reference: GrammarReference, unitId: String, noteTitle: String, noteBody: String = ""): ReferenceTopic? {
        val candidates = reference.topics.filter { unitId in it.relatedUnitIds }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()
        val titleWords = significantWords(noteTitle)
        val bodyWords = significantWords(noteBody)
        val best = candidates.withIndex().maxWithOrNull(
            compareBy<IndexedValue<ReferenceTopic>>(
                { overlap(titleWords, it.value) },
                { overlap(bodyWords, it.value) },
            ).thenByDescending { it.index },
        )
        return best?.value ?: candidates.first()
    }

    private fun overlap(words: Set<String>, topic: ReferenceTopic): Int {
        val topicWords = significantWords(topic.title)
        return words.count { it in topicWords }
    }

    private fun significantWords(text: String): Set<String> =
        fold(text).split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()

    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
