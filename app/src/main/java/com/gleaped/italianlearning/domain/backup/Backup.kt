package com.gleaped.italianlearning.domain.backup

import com.gleaped.italianlearning.domain.writing.SavedWriting
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class BackupLesson(
    val lessonId: String,
    val unitId: String,
    val completedAtMillis: Long,
    val correct: Int,
    val almost: Int,
    val wrong: Int,
    val attempts: Int,
)

@Serializable
data class BackupResult(
    val lessonId: String,
    val exerciseIndex: Int,
    val exerciseId: String? = null,
    val type: String,
    val verdict: String,
    val answeredAtMillis: Long,
)

@Serializable
data class BackupCard(
    val id: String,
    val front: String,
    val back: String,
    val example: String? = null,
    val sourceUnitId: String,
    val easiness: Double,
    val repetitions: Int,
    val intervalDays: Int,
    val dueEpochDay: Long,
    val lapses: Int,
    val firstReviewedEpochDay: Long? = null,
    val lastReviewedEpochDay: Long? = null,
)

@Serializable
data class BackupDay(val epochDay: Long, val lessonsCompleted: Int, val cardsReviewed: Int)

/**
 * Everything the learner has built up: finished lessons, every answer, the review deck with its
 * schedule, the days practised and the saved writing. Settings and reminders are not included.
 */
@Serializable
data class Backup(
    val app: String = BackupCodec.APP,
    val version: Int = BackupCodec.VERSION,
    val exportedAtMillis: Long = 0L,
    val lessons: List<BackupLesson> = emptyList(),
    val results: List<BackupResult> = emptyList(),
    val cards: List<BackupCard> = emptyList(),
    val days: List<BackupDay> = emptyList(),
    val writing: List<SavedWriting> = emptyList(),
)

/** The backup file format. Pure Kotlin. */
object BackupCodec {
    const val APP = "italiano"
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Null when [raw] is not a backup of this app, is damaged, or comes from a newer format. */
    fun decode(raw: String?): Backup? {
        if (raw.isNullOrBlank()) return null
        val backup = try {
            json.decodeFromString(Backup.serializer(), raw)
        } catch (e: Exception) {
            return null
        }
        return backup.takeIf { it.app == APP && it.version in 1..VERSION }
    }

    /** "12 lessons, 340 cards, 3 texts". */
    fun summary(backup: Backup): String = listOf(
        count(backup.lessons.size, "lesson"),
        count(backup.cards.size, "card"),
        count(backup.writing.size, "text"),
    ).joinToString(", ")

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    fun fileName(day: LocalDate): String = "italiano-backup-$day.json"
}
