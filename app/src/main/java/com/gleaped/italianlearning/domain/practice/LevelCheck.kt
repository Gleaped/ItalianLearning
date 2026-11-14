package com.gleaped.italianlearning.domain.practice

import com.gleaped.italianlearning.content.CourseUnit
import kotlin.random.Random

/**
 * "Test out of this module": a round of exercises drawn from every unit of one module. Scoring
 * 85% or more marks the module's lessons as known. The result is advice for the learner, never
 * a lock: the lessons stay open and can still be done. Pure Kotlin.
 */
object LevelCheck {
    /** The lesson id of a check is this prefix plus the module id. */
    const val PREFIX = "check-"
    const val TITLE = "Test del modulo"

    /** Exercises in one check. */
    const val SIZE = 20

    /** Fewer exercises than this is too thin a sample to judge anything. */
    const val MIN_SIZE = 6

    /** Share of the score (a correct answer counts 1, an "almost" half) needed to pass. */
    const val PASS = 0.85

    fun lessonId(moduleId: String): String = PREFIX + moduleId

    /** The module a check lesson id belongs to, or null for any other lesson id. */
    fun moduleOf(lessonId: String): String? =
        if (lessonId.startsWith(PREFIX)) lessonId.removePrefix(PREFIX).takeIf { it.isNotBlank() } else null

    /** Self-contained exercises from every lesson of [units], the units taking turns. */
    fun pick(units: List<CourseUnit>, random: Random, size: Int = SIZE): List<MixCandidate> =
        DailyMix.pick(
            candidates = DailyMix.candidates(units, units.flatMap { u -> u.lessons.map { it.id } }.toSet()),
            weakUnitIds = emptySet(),
            recent = emptySet(),
            random = random,
            size = size,
        )

    fun score(correct: Int, almost: Int, total: Int): Double =
        if (total <= 0) 0.0 else (correct + almost * 0.5) / total

    fun passed(correct: Int, almost: Int, total: Int): Boolean = total >= MIN_SIZE && score(correct, almost, total) >= PASS
}
