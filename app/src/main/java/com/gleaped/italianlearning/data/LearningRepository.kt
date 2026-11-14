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
import com.gleaped.italianlearning.domain.practice.AttemptRecord
import com.gleaped.italianlearning.domain.practice.WeakSpot
import com.gleaped.italianlearning.domain.practice.WeakSpots
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    /** New cards first reviewed today; they use up the daily new-card allowance. */
    fun newIntroducedTodayCount(): Flow<Int> = srs.observeNewIntroducedOn(today())

    /** New cards still waiting in the deck, whether or not today's allowance has room for them. */
    suspend fun waitingNewCardCount(): Int = srs.newCount()
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

    /** Every stored exercise answer, oldest first; the source for [WeakSpots]. */
    val attempts: Flow<List<AttemptRecord>> = progress.observeResults().map { it.toAttempts() }

    suspend fun attemptsNow(): List<AttemptRecord> = progress.results().toAttempts()

    private fun List<ExerciseResultEntity>.toAttempts(): List<AttemptRecord> {
        val zone = ZoneId.systemDefault()
        return mapNotNull { r ->
            val verdict = Verdict.entries.firstOrNull { it.name == r.verdict } ?: return@mapNotNull null
            AttemptRecord(
                lessonId = r.lessonId,
                exerciseIndex = r.exerciseIndex,
                exerciseId = r.exerciseId,
                type = r.type,
                verdict = verdict,
                epochDay = Instant.ofEpochMilli(r.answeredAtMillis).atZone(zone).toLocalDate().toEpochDay(),
            )
        }
    }

    /**
     * Stores the answers of a practice round against the lessons the exercises came from,
     * which moves each weak spot along (see [WeakSpots]). Counts as activity for the day.
     */
    suspend fun recordPractice(answers: List<Pair<WeakSpot, Verdict>>) {
        if (answers.isEmpty()) return
        val now = System.currentTimeMillis()
        progress.insertResults(
            answers.map { (spot, verdict) ->
                ExerciseResultEntity(
                    lessonId = spot.lessonId,
                    exerciseIndex = spot.exerciseIndex,
                    exerciseId = spot.exerciseId,
                    type = spot.type,
                    verdict = verdict.name,
                    answeredAtMillis = now,
                )
            },
        )
        recordActivity(lessons = 0, cards = answers.size)
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

    /**
     * Takes back a rating: puts [original] (the card as it was before [review]) back and
     * removes the review from today's activity count.
     */
    suspend fun undoReview(original: SrsCardEntity) {
        srs.update(original)
        recordActivity(lessons = 0, cards = -1)
    }

    private suspend fun recordActivity(lessons: Int, cards: Int) {
        val day = today()
        val existing = progress.activity(day)
        progress.upsertActivity(
            ActivityDayEntity(
                epochDay = day,
                lessonsCompleted = (existing?.lessonsCompleted ?: 0) + lessons,
                cardsReviewed = ((existing?.cardsReviewed ?: 0) + cards).coerceAtLeast(0),
            ),
        )
    }

    /** Units with at least one completed lesson. */
    suspend fun startedUnitIds(): Set<String> = progress.startedUnitIds().toSet()

    /** Cards a review session would offer right now: due reviews plus today's new-card allowance. */
    suspend fun reviewReadyCount(dailyNewCards: Int): Int {
        val day = today()
        val allowance = (dailyNewCards - srs.newIntroducedOn(day)).coerceAtLeast(0)
        return srs.dueReviewCount(day) + minOf(srs.newCount(), allowance)
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
