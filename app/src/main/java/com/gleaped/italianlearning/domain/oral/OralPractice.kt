package com.gleaped.italianlearning.domain.oral

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.SpeakRespond
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** The four areas the CELI oral is marked on, 1 to 5 each, for the learner's own scoring. */
enum class OralCriterion(val key: String, val italian: String, val question: String) {
    LEXIS("lessico", "Lessico", "Words good and varied for the topic, not repetitive?"),
    GRAMMAR("grammatica", "Grammatica", "A range of structures with few errors?"),
    SOCIAL("sociolinguistica", "Adeguatezza", "Right register, natural and active, little hesitation?"),
    PHONETICS("fonetica", "Pronuncia", "Clear sounds and natural rhythm and intonation?"),
    ;

    companion object {
        const val MAX_SCORE = 5
    }
}

/** One answer the learner recorded and scored. The recording itself is not kept. */
@Serializable
data class SavedOral(
    val id: String,
    val prompt: String,
    val scores: Map<String, Int> = emptyMap(),
    val savedAtMillis: Long,
)

/** A speaking task for the oral practice. */
data class OralPrompt(val unitId: String, val exerciseId: String?, val instruction: String?, val prompt: String, val modelAnswer: String)

/** The list of scored answers, its storage format and the choice of prompts. Pure Kotlin. */
object OralLog {
    const val LIMIT = 300

    /** Preparation times offered, in minutes. The real oral gives about 15 minutes for all its parts. */
    val PREP_MINUTES = listOf(1, 3, 5)

    /** Tasks in one round, as in the exam: presentation, photo or text, conversation. */
    const val ROUND_SIZE = 3

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedOral.serializer())

    fun encode(entries: List<SavedOral>): String = json.encodeToString(serializer, entries)

    fun decode(raw: String?): List<SavedOral> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entries: List<SavedOral>, entry: SavedOral, limit: Int = LIMIT): List<SavedOral> =
        (listOf(entry.copy(scores = cleanScores(entry.scores))) + entries.filter { it.id != entry.id }).take(limit)

    fun cleanScores(scores: Map<String, Int>): Map<String, Int> =
        scores.filter { (key, value) -> value in 1..OralCriterion.MAX_SCORE && OralCriterion.entries.any { it.key == key } }

    /**
     * The speaking tasks of the exam track: the oral lesson of "Parlare per l'esame" and the
     * oral paper of the mock exam. Only tasks with a model answer are used.
     */
    fun candidates(units: Map<String, CourseUnit>): List<OralPrompt> {
        val out = ArrayList<OralPrompt>()
        fun addFrom(unitId: String, keep: (com.gleaped.italianlearning.content.Lesson) -> Boolean) {
            units[unitId]?.lessons?.filter(keep)?.forEach { lesson ->
                lesson.exercises.filterIsInstance<SpeakRespond>().filter { it.modelAnswer.isNotBlank() }.forEach {
                    out += OralPrompt(unitId, it.id, it.instruction, it.prompt, it.modelAnswer)
                }
            }
        }
        addFrom("celi-05") { true }
        addFrom("celi-06") { it.title.startsWith("Prova orale") }
        addFrom("celi-09") { it.title.startsWith("Prova orale") }
        return out
    }

    /** [count] different tasks, chosen at random. */
    fun pick(candidates: List<OralPrompt>, count: Int = ROUND_SIZE, random: Random = Random.Default): List<OralPrompt> =
        candidates.distinctBy { it.prompt }.shuffled(random).take(count)

    /** The average score per criterion over the latest [last] answers that scored it; absent without data. */
    fun averages(entries: List<SavedOral>, last: Int = 10): Map<OralCriterion, Double> {
        val recent = entries.take(last)
        return OralCriterion.entries.mapNotNull { c ->
            val values = recent.mapNotNull { it.scores[c.key] }
            if (values.isEmpty()) null else c to values.average()
        }.toMap()
    }
}
