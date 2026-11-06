package com.gleaped.italianlearning.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val unitId: String,
    val completedAtMillis: Long,
    val correct: Int,
    val almost: Int,
    val wrong: Int,
    val attempts: Int,
)

@Entity(
    tableName = "exercise_results",
    indices = [Index("lessonId")],
)
data class ExerciseResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: String,
    val exerciseIndex: Int,
    val exerciseId: String?,
    val type: String,
    /** Verdict name: CORRECT, ALMOST or WRONG. */
    val verdict: String,
    val answeredAtMillis: Long,
)

/** One spaced-repetition card. [id] is stable: "v:<unitId>:<it>" or "f:<lessonId>:<front>". */
@Entity(tableName = "srs_cards", indices = [Index("dueEpochDay")])
data class SrsCardEntity(
    @PrimaryKey val id: String,
    val front: String,
    val back: String,
    val example: String?,
    val sourceUnitId: String,
    val easiness: Double,
    val repetitions: Int,
    val intervalDays: Int,
    val dueEpochDay: Long,
    val lapses: Int,
    /** Null until first reviewed; used for the daily new-card limit. */
    val firstReviewedEpochDay: Long?,
    val lastReviewedEpochDay: Long?,
)

/** A day on which the learner did something (lesson or review). Drives the streak. */
@Entity(tableName = "activity_days")
data class ActivityDayEntity(
    @PrimaryKey val epochDay: Long,
    val lessonsCompleted: Int,
    val cardsReviewed: Int,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bit mask, bit (DayOfWeek.value - 1) set when the reminder fires on that day. */
    val daysMask: Int,
    val enabled: Boolean,
)
