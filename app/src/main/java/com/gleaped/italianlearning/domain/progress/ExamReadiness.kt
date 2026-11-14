package com.gleaped.italianlearning.domain.progress

import kotlin.math.ceil
import kotlin.math.roundToInt

/** The parts of the CELI 3 exam and their converted point maxima (see docs/celi3-exam.md). */
enum class ExamPart(val italian: String, val english: String, val max: Int, val skill: Skill, val written: Boolean) {
    READING("Parte A", "Reading", 40, Skill.READING, true),
    WRITING("Parte B", "Writing", 40, Skill.WRITING, true),
    LANGUAGE("Parte C", "Grammar and vocabulary", 20, Skill.GRAMMAR, true),
    LISTENING("Parte D", "Listening", 40, Skill.LISTENING, true),
    ORAL("Orale", "Speaking", 60, Skill.SPEAKING, false),
}

/** Where an estimate sits against its pass mark. */
enum class Standing { BELOW, NEAR, ABOVE }

/** The estimated points of one exam part; null while there are too few answers. */
data class PartEstimate(val part: ExamPart, val points: Int?)

/** A rough estimate of the exam score on the official scale. */
data class Readiness(val parts: List<PartEstimate>, val written: Int?, val oral: Int?) {
    val overall: Int? get() = if (written != null && oral != null) written + oral else null
}

/**
 * Turns the accuracy per skill into points on the CELI 3 scale, to compare with the pass marks:
 * 84 of 140 written, 33 of 60 oral, 117 of 200 overall. It is a guide only: lesson answers are
 * easier than exam tasks, and writing and speaking scores come from the learner's own checks.
 */
object ExamReadiness {
    const val WRITTEN_MAX = 140
    const val ORAL_MAX = 60
    const val WRITTEN_PASS = 84
    const val ORAL_PASS = 33
    const val OVERALL_PASS = 117

    fun estimate(scores: List<SkillScore>): Readiness {
        val parts = ExamPart.entries.map { part ->
            val score = scores.firstOrNull { it.skill == part.skill }
            // Recent answers say more about now than the whole history.
            val percent = score?.recent?.percent ?: score?.overall?.percent
            PartEstimate(part, percent?.let { (part.max * it / 100.0).roundToInt() })
        }
        val written = parts.filter { it.part.written }
        val writtenPoints = if (written.all { it.points != null }) written.sumOf { it.points ?: 0 } else null
        val oralPoints = parts.firstOrNull { !it.part.written }?.points
        return Readiness(parts, writtenPoints, oralPoints)
    }

    /** Within about 7% of the maximum either side of the pass mark counts as near. */
    fun standing(points: Int, pass: Int, max: Int): Standing {
        val margin = ceil(max * 0.07).toInt()
        return when {
            points >= pass + margin -> Standing.ABOVE
            points >= pass - margin -> Standing.NEAR
            else -> Standing.BELOW
        }
    }
}

/** The countdown and the weekly pace towards an exam date. Pure Kotlin. */
object ExamPlan {
    /** The levels that make up the path to CELI 3 (B2). */
    val PATH_LEVELS = setOf("B1", "B2")

    /** Days from [todayEpochDay] to the exam; negative once it has passed. */
    fun daysLeft(examEpochDay: Long, todayEpochDay: Long): Int = (examEpochDay - todayEpochDay).toInt()

    /** Lessons still to do on the path to B2. */
    fun remainingLessons(levels: List<CourseProgress.LevelProgress>): Int =
        levels.filter { it.levelId in PATH_LEVELS }.sumOf { (it.total - it.completed).coerceAtLeast(0) }

    /** Whole lessons a week that finish [remaining] before the exam; null when there is no time left. */
    fun lessonsPerWeek(remaining: Int, daysLeft: Int): Int? = when {
        remaining <= 0 -> 0
        daysLeft <= 0 -> null
        else -> ceil(remaining * 7.0 / daysLeft).toInt().coerceAtLeast(1)
    }
}
