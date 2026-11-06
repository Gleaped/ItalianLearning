package com.gleaped.italianlearning.data

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Flashcard
import com.gleaped.italianlearning.data.db.ActivityDayEntity
import com.gleaped.italianlearning.data.db.AppDatabase
import com.gleaped.italianlearning.data.db.ExerciseResultEntity
import com.gleaped.italianlearning.data.db.LessonProgressEntity
import com.gleaped.italianlearning.data.db.SrsCardEntity
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.domain.srs.Sm2
import com.gleaped.italianlearning.domain.srs.SrsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Result of one exercise within a lesson run. */
data class ExerciseOutcome(
    val index: Int,
    val exerciseId: String?,
    val type: String,
    val verdict: Verdict?,
)

class LearningRepository(private val db: AppDatabase) {
    private val progress = db.progressDao()
    private val srs = db.srsDao()

    private fun today(): Long = LocalDate.now().toEpochDay()

    val completedLessonIds: Flow<Set<String>> =
        progress.observeLessons().map { list -> list.map { it.lessonId }.toSet() }

    val activeDays: Flow<List<Long>> =
        progress.observeActivity().map { list -> list.filter { it.lessonsCompleted > 0 || it.cardsReviewed > 0 }.map { it.epochDay } }

    fun dueReviewCount(): Flow<Int> = srs.observeDueReviewCount(today())
    fun newCardCount(): Flow<Int> = srs.observeNewCount()
    fun totalCardCount(): Flow<Int> = srs.observeTotal()

    suspend fun completeLesson(unit: CourseUnit, lessonId: String, outcomes: List<ExerciseOutcome>) {
        val now = System.currentTimeMillis()
        val scored = outcomes.mapNotNull { it.verdict }
        val previous = progress.lesson(lessonId)
        progress.upsertLesson(
            LessonProgressEntity(
                lessonId = lessonId,
                unitId = unit.id,
                completedAtMillis = now,
                correct = scored.count { it == Verdict.CORRECT },
                almost = scored.count { it == Verdict.ALMOST },
                wrong = scored.count { it == Verdict.WRONG },
                attempts = (previous?.attempts ?: 0) + 1,
            ),
        )
        progress.insertResults(
            outcomes.filter { it.verdict != null }.map {
                ExerciseResultEntity(
                    lessonId = lessonId,
                    exerciseIndex = it.index,
                    exerciseId = it.exerciseId,
                    type = it.type,
                    verdict = it.verdict!!.name,
                    answeredAtMillis = now,
                )
            },
        )
        recordActivity(lessons = 1, cards = 0)
        // Unit vocabulary feeds the review deck once the learner has met the unit.
        addVocabulary(unit)
    }

    suspend fun addVocabulary(unit: CourseUnit) {
        val cards = unit.vocabulary.map { v ->
            newCard(id = "v:${unit.id}:${v.it}", front = v.it, back = v.en, example = v.example, unitId = unit.id)
        }
        if (cards.isNotEmpty()) srs.insertIfAbsent(cards)
    }

    suspend fun addFlashcard(unitId: String, lessonId: String, card: Flashcard) {
        srs.insertIfAbsent(
            listOf(newCard("f:$lessonId:${card.front}", card.front, card.back, card.example, unitId)),
        )
    }

    private fun newCard(id: String, front: String, back: String, example: String?, unitId: String) = SrsCardEntity(
        id = id,
        front = front,
        back = back,
        example = example,
        sourceUnitId = unitId,
        easiness = Sm2.INITIAL_EASINESS,
        repetitions = 0,
        intervalDays = 0,
        dueEpochDay = today(),
        lapses = 0,
        firstReviewedEpochDay = null,
        lastReviewedEpochDay = null,
    )

    /** Due reviews plus today's allowance of new cards. */
    suspend fun reviewQueue(dailyNewCards: Int): List<SrsCardEntity> {
        val day = today()
        val due = srs.dueReviews(day)
        val allowance = (dailyNewCards - srs.newIntroducedOn(day)).coerceAtLeast(0)
        val fresh = if (allowance > 0) srs.newCards(allowance) else emptyList()
        return due + fresh
    }

    suspend fun review(card: SrsCardEntity, rating: Rating): SrsCardEntity {
        val day = today()
        val next = Sm2.review(card.toState(), rating, day)
        val updated = card.copy(
            easiness = next.easiness,
            repetitions = next.repetitions,
            intervalDays = next.intervalDays,
            dueEpochDay = next.dueEpochDay,
            lapses = next.lapses,
            firstReviewedEpochDay = card.firstReviewedEpochDay ?: day,
            lastReviewedEpochDay = day,
        )
        srs.update(updated)
        recordActivity(lessons = 0, cards = 1)
        return updated
    }

    private suspend fun recordActivity(lessons: Int, cards: Int) {
        val day = today()
        val existing = progress.activity(day)
        progress.upsertActivity(
            ActivityDayEntity(
                epochDay = day,
                lessonsCompleted = (existing?.lessonsCompleted ?: 0) + lessons,
                cardsReviewed = (existing?.cardsReviewed ?: 0) + cards,
            ),
        )
    }

    suspend fun practicedToday(): Boolean {
        val a = progress.activity(today()) ?: return false
        return a.lessonsCompleted > 0 || a.cardsReviewed > 0
    }

    suspend fun resetProgress() {
        progress.clearLessons()
        progress.clearResults()
        progress.clearActivity()
        srs.clear()
    }
}

fun SrsCardEntity.toState() = SrsState(
    easiness = easiness,
    repetitions = repetitions,
    intervalDays = intervalDays,
    dueEpochDay = dueEpochDay,
    lapses = lapses,
)
