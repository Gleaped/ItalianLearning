package com.gleaped.italianlearning.domain.flags

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An exercise the learner marked as wrong, unclear or too strict, with an optional note. */
@Serializable
data class ExerciseFlag(
    val id: String,
    val unitId: String,
    val lessonId: String,
    val exerciseIndex: Int,
    val exerciseId: String? = null,
    val type: String = "",
    val note: String = "",
    val flaggedAtMillis: Long,
)

/** The list of flags and its storage format. Pure Kotlin. */
object FlagLog {
    const val LIMIT = 500
    const val MAX_NOTE = 400

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ExerciseFlag.serializer())

    fun encode(flags: List<ExerciseFlag>): String = json.encodeToString(serializer, flags)

    fun decode(raw: String?): List<ExerciseFlag> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The key of one exercise: flagging it again replaces the earlier note. */
    fun key(unitId: String, lessonId: String, exerciseIndex: Int, exerciseId: String?): String =
        exerciseId?.takeIf { it.isNotBlank() } ?: "$unitId:$lessonId:$exerciseIndex"

    /** [flags] (newest first) with [flag] at the front, replacing one for the same exercise. */
    fun add(flags: List<ExerciseFlag>, flag: ExerciseFlag, limit: Int = LIMIT): List<ExerciseFlag> =
        (listOf(flag.copy(note = flag.note.trim().take(MAX_NOTE))) + flags.filter { it.id != flag.id }).take(limit)

    /** The list as plain text to send to whoever fixes the content. */
    fun shareText(flags: List<ExerciseFlag>): String = buildString {
        append("Exercises flagged in Italiano (").append(flags.size).append(")\n")
        flags.forEach { f ->
            append("\n- ").append(f.exerciseId ?: "${f.lessonId} #${f.exerciseIndex + 1}")
            if (f.type.isNotBlank()) append(" (").append(f.type).append(")")
            if (f.note.isNotBlank()) append(": ").append(f.note.replace('\n', ' '))
        }
    }
}
