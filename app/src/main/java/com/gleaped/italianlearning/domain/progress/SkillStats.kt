package com.gleaped.italianlearning.domain.progress

import com.gleaped.italianlearning.content.ExerciseTypes
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.AttemptRecord
import kotlin.math.roundToInt

/** The skills the CELI exam tests, as far as exercise types can tell them apart. */
enum class Skill(val italian: String, val english: String) {
    GRAMMAR("Grammatica e lessico", "Grammar and vocabulary"),
    READING("Lettura", "Reading"),
    LISTENING("Ascolto", "Listening"),
    WRITING("Scrittura", "Writing"),
    SPEAKING("Parlato", "Speaking"),
    ;

    companion object {
        /** The skill an exercise type practises; null for unscored types. */
        fun of(type: String): Skill? = when (type) {
            ExerciseTypes.READING -> READING
            ExerciseTypes.LISTEN_TRANSCRIBE, ExerciseTypes.LISTEN_CHOOSE -> LISTENING
            ExerciseTypes.FREE_WRITE -> WRITING
            ExerciseTypes.SPEAK_REPEAT, ExerciseTypes.SPEAK_RESPOND, ExerciseTypes.ROLEPLAY -> SPEAKING
            ExerciseTypes.GRAMMAR_NOTE, ExerciseTypes.FLASHCARD -> null
            else -> GRAMMAR
        }
    }
}

/** Accuracy over [answers] answers; "almost" counts half. */
data class Accuracy(val answers: Int, val points: Double) {
    /** Null until there are enough answers for the figure to mean something. */
    val percent: Int? get() = if (answers < SkillStats.MIN_ANSWERS) null else (100 * points / answers).roundToInt()
}

data class SkillScore(val skill: Skill, val overall: Accuracy, val recent: Accuracy)

/** A unit whose grammar answers are the least accurate. */
data class WeakTopic(val unitId: String, val answers: Int, val percent: Int)

/** Progress by skill, worked out from the stored exercise answers. Pure Kotlin. */
object SkillStats {
    /** Fewer answers than this give no percentage. */
    const val MIN_ANSWERS = 5

    /** "Recent" means this many days up to today. */
    const val RECENT_DAYS = 14

    /** A topic at or above this accuracy is not listed as weak. */
    const val WEAK_BELOW_PERCENT = 85

    private val LESSON_SUFFIX = Regex("-l\\d+$")

    private fun points(v: Verdict) = when (v) {
        Verdict.CORRECT -> 1.0
        Verdict.ALMOST -> 0.5
        Verdict.WRONG -> 0.0
    }

    private fun accuracy(attempts: List<AttemptRecord>) = Accuracy(attempts.size, attempts.sumOf { points(it.verdict) })

    /** One score per skill, in the order of [Skill]. */
    fun bySkill(attempts: List<AttemptRecord>, todayEpochDay: Long): List<SkillScore> {
        val grouped = attempts.groupBy { Skill.of(it.type) }
        val since = todayEpochDay - RECENT_DAYS + 1
        return Skill.entries.map { skill ->
            val all = grouped[skill].orEmpty()
            SkillScore(skill, accuracy(all), accuracy(all.filter { it.epochDay >= since }))
        }
    }

    /** "b1-01-l3" belongs to unit "b1-01"; null when the id has another shape. */
    fun unitIdOf(lessonId: String): String? =
        LESSON_SUFFIX.replace(lessonId, "").takeIf { it != lessonId && it.isNotEmpty() }

    /**
     * The units with the least accurate grammar answers, weakest first. Only the latest
     * [window] answers of each unit count, so a topic drops off the list once it improves.
     */
    fun weakestTopics(attempts: List<AttemptRecord>, limit: Int = 3, window: Int = 30): List<WeakTopic> =
        attempts.filter { Skill.of(it.type) == Skill.GRAMMAR }
            .groupBy { unitIdOf(it.lessonId) }
            .mapNotNull { (unitId, list) ->
                if (unitId == null) return@mapNotNull null
                val percent = accuracy(list.takeLast(window)).percent ?: return@mapNotNull null
                WeakTopic(unitId, minOf(list.size, window), percent)
            }
            .filter { it.percent < WEAK_BELOW_PERCENT }
            .sortedWith(compareBy({ it.percent }, { it.unitId }))
            .take(limit)

    /** Units with a wrong grammar answer in the last [days] days: where reminders should aim. */
    fun recentlyMissedUnits(attempts: List<AttemptRecord>, todayEpochDay: Long, days: Int = RECENT_DAYS): Set<String> =
        attempts.asSequence()
            .filter { it.verdict == Verdict.WRONG && it.epochDay > todayEpochDay - days && Skill.of(it.type) == Skill.GRAMMAR }
            .mapNotNull { unitIdOf(it.lessonId) }
            .toSet()
}
