package com.gleaped.italianlearning.domain.practice

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.ExerciseTypes
import com.gleaped.italianlearning.domain.grading.Verdict

/** One stored answer to a lesson exercise. */
data class AttemptRecord(
    val lessonId: String,
    val exerciseIndex: Int,
    val exerciseId: String?,
    val type: String,
    val verdict: Verdict,
    val epochDay: Long,
)

/** An exercise the learner got wrong and has not yet got right often enough. */
data class WeakSpot(
    val lessonId: String,
    val exerciseIndex: Int,
    val exerciseId: String?,
    val type: String,
    val dueEpochDay: Long,
)

/**
 * "Punti deboli": exercises answered wrongly come back after 1, 3 and 7 days.
 *
 * Nothing extra is stored. The state of each exercise is worked out from its answer history:
 * a wrong answer opens (or re-opens) a weak spot; each later correct answer moves it one step
 * along [INTERVALS]; after [INTERVALS].size correct answers in a row it is closed. An "almost"
 * keeps the step and asks again after the same gap.
 */
object WeakSpots {
    /** Days to wait before asking again, by number of correct answers since the last wrong one. */
    val INTERVALS: List<Int> = listOf(1, 3, 7)

    /** Exercises asked in one practice round at most. */
    const val SESSION_SIZE = 10

    /** Quick, self-contained exercise types that can be re-asked out of their lesson. */
    val TYPES: Set<String> = setOf(
        ExerciseTypes.MULTIPLE_CHOICE,
        ExerciseTypes.GAP_FILL,
        ExerciseTypes.CONJUGATION,
        ExerciseTypes.SENTENCE_BUILD,
        ExerciseTypes.TRANSLATE,
        ExerciseTypes.ERROR_CORRECTION,
        ExerciseTypes.LISTEN_TRANSCRIBE,
        ExerciseTypes.LISTEN_CHOOSE,
        ExerciseTypes.CHOOSE_MOOD,
        ExerciseTypes.FIND_TRIGGER,
        ExerciseTypes.TRANSFORM,
        ExerciseTypes.SORT_BUCKETS,
        ExerciseTypes.MINIMAL_PAIR,
    )

    fun isRetryable(exercise: Exercise): Boolean = ExerciseTypes.of(exercise) in TYPES

    private class Open(var streak: Int, var lastDay: Long, var exerciseId: String?, var type: String)

    /** Every open weak spot. [attempts] must be in the order they were answered. */
    fun open(attempts: List<AttemptRecord>): List<WeakSpot> {
        val state = LinkedHashMap<Pair<String, Int>, Open>()
        for (a in attempts) {
            if (a.type !in TYPES) continue
            val key = a.lessonId to a.exerciseIndex
            val current = state[key]
            when (a.verdict) {
                Verdict.WRONG -> state[key] = Open(0, a.epochDay, a.exerciseId, a.type)
                Verdict.ALMOST -> if (current != null) current.lastDay = a.epochDay
                Verdict.CORRECT -> if (current != null) {
                    current.streak++
                    current.lastDay = a.epochDay
                    if (current.streak >= INTERVALS.size) state.remove(key)
                }
            }
        }
        return state.map { (key, o) ->
            WeakSpot(key.first, key.second, o.exerciseId, o.type, o.lastDay + INTERVALS[o.streak])
        }
    }

    /** Weak spots due on or before [todayEpochDay], longest overdue first. */
    fun due(attempts: List<AttemptRecord>, todayEpochDay: Long): List<WeakSpot> =
        open(attempts).filter { it.dueEpochDay <= todayEpochDay }.sortedBy { it.dueEpochDay }

    /**
     * Pairs each weak spot with its exercise in the current course. Spots whose exercise has
     * moved or changed since it was answered (after a content update) are dropped.
     */
    fun resolve(spots: List<WeakSpot>, units: Map<String, CourseUnit>): List<Pair<WeakSpot, Exercise>> {
        val lessons = units.values.flatMap { it.lessons }.associateBy { it.id }
        return spots.mapNotNull { spot ->
            val exercise = lessons[spot.lessonId]?.exercises?.getOrNull(spot.exerciseIndex) ?: return@mapNotNull null
            if (ExerciseTypes.of(exercise) != spot.type) return@mapNotNull null
            if (spot.exerciseId != null && exercise.id != null && spot.exerciseId != exercise.id) return@mapNotNull null
            spot to exercise
        }
    }
}

/** Ids of the practice session that re-asks weak spots; it runs in the lesson player. */
object Practice {
    const val UNIT_ID = "~practice"
    const val WEAK_LESSON_ID = "weak"
    const val TITLE = "Punti deboli"
}
