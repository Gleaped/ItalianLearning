package com.gleaped.italianlearning.domain.practice

import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.ExerciseTypes
import kotlin.random.Random

/** An exercise from a finished lesson that the daily mix may ask again. */
data class MixCandidate(
    val unitId: String,
    val lessonId: String,
    val exerciseIndex: Int,
    val exercise: Exercise,
) {
    val type: String get() = ExerciseTypes.of(exercise)
    val key: Pair<String, Int> get() = lessonId to exerciseIndex
}

/**
 * "Mix del giorno": a short round of exercises from lessons already finished, with the topics
 * shuffled on purpose. Switching between rules (interleaving) is harder than practising one
 * rule at a time, and that is why it sticks better.
 */
object DailyMix {
    /** Exercises in one round. */
    const val SIZE = 8

    /** Finished lessons needed before the mix is offered. */
    const val MIN_LESSONS = 3

    /** Exercises answered within this many days are kept for later when there are others. */
    const val RECENT_DAYS = 3

    /** Every self-contained exercise of the finished lessons. */
    fun candidates(units: Collection<CourseUnit>, completedLessonIds: Set<String>): List<MixCandidate> =
        units.flatMap { unit ->
            unit.lessons.filter { it.id in completedLessonIds }.flatMap { lesson ->
                lesson.exercises.mapIndexedNotNull { index, exercise ->
                    if (WeakSpots.isRetryable(exercise)) MixCandidate(unit.id, lesson.id, index, exercise) else null
                }
            }
        }

    /**
     * Picks up to [size] exercises: the units take turns (units in [weakUnitIds] get two turns
     * per round), exercises not in [recent] come first, and within a unit a type that
     * already fills about a third of the round is passed over while the unit has others.
     */
    fun pick(
        candidates: List<MixCandidate>,
        weakUnitIds: Set<String>,
        recent: Set<Pair<String, Int>>,
        random: Random,
        size: Int = SIZE,
    ): List<MixCandidate> {
        if (candidates.isEmpty() || size <= 0) return emptyList()
        val queues = candidates.groupBy { it.unitId }.mapValues { (_, list) ->
            val (seen, fresh) = list.shuffled(random).partition { it.key in recent }
            ArrayDeque(fresh + seen)
        }
        val turns = queues.keys.sorted().flatMap { if (it in weakUnitIds) listOf(it, it) else listOf(it) }.shuffled(random)
        val perType = HashMap<String, Int>()
        val maxPerType = (size + 2) / 3
        val picked = ArrayList<MixCandidate>()
        while (picked.size < size) {
            var added = false
            for (unitId in turns) {
                if (picked.size >= size) break
                val queue = queues.getValue(unitId)
                if (queue.isEmpty()) continue
                // The unit's turn is never lost: with only well-used types left, its first exercise goes in.
                val at = queue.indexOfFirst { (perType[it.type] ?: 0) < maxPerType }.coerceAtLeast(0)
                val candidate = queue.removeAt(at)
                perType[candidate.type] = (perType[candidate.type] ?: 0) + 1
                picked += candidate
                added = true
            }
            if (!added) break
        }
        return picked.shuffled(random)
    }
}
