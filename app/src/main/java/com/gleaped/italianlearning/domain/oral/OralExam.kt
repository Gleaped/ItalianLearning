package com.gleaped.italianlearning.domain.oral

import kotlinx.serialization.Serializable

/** One part of a rehearsal oral: the presentation, the photo, the text or the role-play. */
@Serializable
data class ExamTask(
    /** presentation, photo, text or roleplay. */
    val kind: String,
    val title: String,
    /** What the examiner says to set the task; one variant is chosen. */
    val examiner: List<String>,
    /** The material the candidate receives: a situation, a described photo or a short text. */
    val material: String = "",
    /** Points to cover, shown while preparing. */
    val cues: List<String> = emptyList(),
    /** Questions the examiner may ask after the first answer. */
    val followUps: List<String> = emptyList(),
    val modelAnswer: String = "",
    /** The length of a good answer, in seconds. */
    val minSeconds: Int = 60,
    val maxSeconds: Int = 150,
    /** An optional picture shipped in assets, for a real photo task. */
    val image: String? = null,
)

/** A full rehearsal oral in the shape of the CELI 3 oral (see docs/celi3-exam.md). */
@Serializable
data class OralPaper(
    val id: String,
    val title: String,
    val prepareMinutes: Int = 15,
    val greeting: List<String> = emptyList(),
    val closing: List<String> = emptyList(),
    val tasks: List<ExamTask>,
)

/** What the microphone level says about one spoken answer. */
data class Fluency(
    val seconds: Int,
    val speakingPercent: Int,
    val longestPauseSeconds: Double,
    val longPauses: Int,
)

/**
 * Reads the microphone level while the learner speaks and finds the pauses. It cannot tell
 * what was said, only when the learner was silent, which is what "little hesitation" is about.
 * Pure Kotlin.
 */
class FluencyMeter(private val frameMillis: Int = FRAME_MILLIS) {
    private val levels = ArrayList<Int>()

    fun add(level: Int) {
        levels += level.coerceAtLeast(0)
    }

    fun result(): Fluency {
        if (levels.isEmpty()) return Fluency(0, 0, 0.0, 0)
        val sorted = levels.sorted()
        val loud = sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.size - 1)]
        // Speech is whatever is clearly above the room's noise, relative to how loudly this learner speaks.
        val threshold = maxOf(MIN_LEVEL, (loud * 0.15).toInt())
        val voiced = levels.map { it >= threshold }
        val first = voiced.indexOfFirst { it }
        val last = voiced.indexOfLast { it }
        val seconds = levels.size * frameMillis / 1000
        if (first < 0) return Fluency(seconds, 0, 0.0, 0)
        // Silence before the first word and after the last one is not hesitation.
        val core = voiced.subList(first, last + 1)
        var longest = 0
        var run = 0
        var longPauses = 0
        for (v in core + true) {
            if (v) {
                if (run * frameMillis >= LONG_PAUSE_MILLIS) longPauses++
                longest = maxOf(longest, run)
                run = 0
            } else {
                run++
            }
        }
        return Fluency(
            seconds = seconds,
            speakingPercent = core.count { it } * 100 / core.size,
            longestPauseSeconds = longest * frameMillis / 1000.0,
            longPauses = longPauses,
        )
    }

    companion object {
        const val FRAME_MILLIS = 200
        private const val MIN_LEVEL = 900
        private const val LONG_PAUSE_MILLIS = 1500
    }
}

/** Plain-language comments on one answer, and a rough oral score. Pure Kotlin. */
object OralFeedback {
    private const val PASS_ORAL = 33
    private const val ORAL_MAX = 60

    fun comments(f: Fluency, task: ExamTask): List<String> {
        val out = ArrayList<String>()
        if (f.seconds == 0) return listOf("No answer was recorded.")
        val len = "${f.seconds / 60}:${"%02d".format(f.seconds % 60)}"
        out += when {
            f.seconds < task.minSeconds -> "You spoke for $len. Aim for at least ${task.minSeconds / 60}:${"%02d".format(task.minSeconds % 60)}: add a reason or an example."
            f.seconds > task.maxSeconds -> "You spoke for $len, a little long. In the exam you can be stopped; keep to the main points."
            else -> "You spoke for $len, a good length."
        }
        out += when {
            f.longPauses == 0 -> "No long pauses: you kept going."
            f.longPauses == 1 -> "One long pause (${"%.1f".format(f.longestPauseSeconds)} s). Fillers like “allora” or “dunque” keep the turn."
            else -> "${f.longPauses} long pauses, the longest ${"%.1f".format(f.longestPauseSeconds)} s. Prepare linking phrases such as “inoltre”, “per esempio”, “secondo me”."
        }
        return out
    }

    /** The oral score out of 60 that the average of the four self-scores suggests, or null without scores. */
    fun estimate(scores: Map<String, Int>): Int? {
        val given = OralCriterion.entries.mapNotNull { scores[it.key] }
        if (given.isEmpty()) return null
        return Math.round(given.average() * ORAL_MAX / OralCriterion.MAX_SCORE).toInt()
    }

    fun passes(estimate: Int): Boolean = estimate >= PASS_ORAL
    const val PASS_MARK = PASS_ORAL
    const val MAX_MARK = ORAL_MAX
}
