package com.gleaped.italianlearning.domain.writing

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A free-writing text the learner finished, kept so it can be reread, compared and shared. */
@Serializable
data class SavedWriting(
    val id: String,
    val exerciseId: String? = null,
    val prompt: String,
    val text: String,
    val modelAnswer: String = "",
    val savedAtMillis: Long,
    /** Self-assessment, 1 to 5, by [CeliCriterion.key]; criteria left unscored are absent. */
    val scores: Map<String, Int> = emptyMap(),
)

/** The areas a CELI written production is marked on, for self-assessment. */
enum class CeliCriterion(val key: String, val italian: String, val question: String) {
    VOCABULARY("lessico", "Lessico", "Varied, precise words for the topic?"),
    GRAMMAR("grammatica", "Grammatica", "Forms, agreement, tenses and sentence structure?"),
    APPROPRIATENESS("adeguatezza", "Adeguatezza", "Register and format right for the task and the reader?"),
    COHERENCE("coerenza", "Coerenza", "Clear order, linked ideas, every point of the task covered?"),
    ;

    companion object {
        const val MAX_SCORE = 5
    }
}

/** The list of saved texts and its storage format. Pure Kotlin. */
object WritingLog {
    /** Oldest texts are dropped beyond this many. */
    const val LIMIT = 300

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedWriting.serializer())

    fun encode(entries: List<SavedWriting>): String = json.encodeToString(serializer, entries)

    /** Unreadable data gives an empty list rather than an error. */
    fun decode(raw: String?): List<SavedWriting> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** [entries] (newest first) with [entry] added at the front; blank texts are not kept. */
    fun add(entries: List<SavedWriting>, entry: SavedWriting, limit: Int = LIMIT): List<SavedWriting> {
        if (entry.text.isBlank()) return entries
        return (listOf(entry) + entries.filter { it.id != entry.id }).take(limit)
    }

    /** Only scores in range are kept. */
    fun cleanScores(scores: Map<String, Int>): Map<String, Int> =
        scores.filter { (key, value) -> value in 1..CeliCriterion.MAX_SCORE && CeliCriterion.entries.any { it.key == key } }

    /** The text as shared with a tutor: prompt, text and self-assessment. */
    fun shareText(entry: SavedWriting): String = buildString {
        append("Traccia: ").append(entry.prompt.trim()).append("\n\n")
        append(entry.text.trim())
        val scored = CeliCriterion.entries.mapNotNull { c -> entry.scores[c.key]?.let { "${c.italian} $it/${CeliCriterion.MAX_SCORE}" } }
        if (scored.isNotEmpty()) append("\n\nAutovalutazione: ").append(scored.joinToString(", "))
    }
}
