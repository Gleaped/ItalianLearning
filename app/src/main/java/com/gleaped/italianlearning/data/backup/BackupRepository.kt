package com.gleaped.italianlearning.data.backup

import androidx.room.withTransaction
import com.gleaped.italianlearning.data.db.ActivityDayEntity
import com.gleaped.italianlearning.data.db.AppDatabase
import com.gleaped.italianlearning.data.db.ExerciseResultEntity
import com.gleaped.italianlearning.data.db.LessonProgressEntity
import com.gleaped.italianlearning.data.db.SrsCardEntity
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.data.writing.WritingRepository
import com.gleaped.italianlearning.domain.backup.Backup
import com.gleaped.italianlearning.domain.backup.BackupCard
import com.gleaped.italianlearning.domain.backup.BackupDay
import com.gleaped.italianlearning.domain.backup.BackupLesson
import com.gleaped.italianlearning.domain.backup.BackupResult

/** Copies the learner's progress into a [Backup] and puts one back. */
class BackupRepository(
    private val db: AppDatabase,
    private val writing: WritingRepository,
    private val settings: SettingsRepository,
) {
    suspend fun export(nowMillis: Long): Backup {
        writing.load()
        val progress = db.progressDao()
        return Backup(
            exportedAtMillis = nowMillis,
            lessons = progress.allLessons().map {
                BackupLesson(it.lessonId, it.unitId, it.completedAtMillis, it.correct, it.almost, it.wrong, it.attempts)
            },
            results = progress.results().map {
                BackupResult(it.lessonId, it.exerciseIndex, it.exerciseId, it.type, it.verdict, it.answeredAtMillis)
            },
            cards = db.srsDao().all().map {
                BackupCard(
                    it.id, it.front, it.back, it.example, it.sourceUnitId, it.easiness, it.repetitions,
                    it.intervalDays, it.dueEpochDay, it.lapses, it.firstReviewedEpochDay, it.lastReviewedEpochDay,
                )
            },
            days = progress.allActivity().map { BackupDay(it.epochDay, it.lessonsCompleted, it.cardsReviewed) },
            writing = writing.entries.value,
        )
    }

    /** Replaces the progress on this phone with [backup]. Either all of it is restored or none. */
    suspend fun restore(backup: Backup) {
        val progress = db.progressDao()
        val srs = db.srsDao()
        db.withTransaction {
            progress.clearLessons()
            progress.clearResults()
            progress.clearActivity()
            srs.clear()
            progress.insertLessons(
                backup.lessons.map {
                    LessonProgressEntity(it.lessonId, it.unitId, it.completedAtMillis, it.correct, it.almost, it.wrong, it.attempts)
                },
            )
            // In the order they were answered: weak spots are worked out from that order.
            progress.insertResults(
                backup.results.map {
                    ExerciseResultEntity(
                        lessonId = it.lessonId,
                        exerciseIndex = it.exerciseIndex,
                        exerciseId = it.exerciseId,
                        type = it.type,
                        verdict = it.verdict,
                        answeredAtMillis = it.answeredAtMillis,
                    )
                },
            )
            progress.insertActivity(backup.days.map { ActivityDayEntity(it.epochDay, it.lessonsCompleted, it.cardsReviewed) })
            srs.insertAll(
                backup.cards.map {
                    SrsCardEntity(
                        id = it.id,
                        front = it.front,
                        back = it.back,
                        example = it.example,
                        sourceUnitId = it.sourceUnitId,
                        easiness = it.easiness,
                        repetitions = it.repetitions,
                        intervalDays = it.intervalDays,
                        dueEpochDay = it.dueEpochDay,
                        lapses = it.lapses,
                        firstReviewedEpochDay = it.firstReviewedEpochDay,
                        lastReviewedEpochDay = it.lastReviewedEpochDay,
                    )
                },
            )
        }
        writing.replaceAll(backup.writing)
        settings.clearLessonCheckpoint()
    }
}
