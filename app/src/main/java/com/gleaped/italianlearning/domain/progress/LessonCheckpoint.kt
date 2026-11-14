package com.gleaped.italianlearning.domain.progress

import com.gleaped.italianlearning.domain.grading.Verdict

/**
 * Where the learner stopped in a lesson, so it can be resumed later (also after the app
 * was closed in the background). [index] is the next exercise to show; it equals the number
 * of exercises when only the re-ask round is left. [verdicts] holds the result of each
 * exercise done so far (null = not scored or skipped).
 */
data class LessonCheckpoint(
    val unitId: String,
    val lessonId: String,
    val index: Int,
    val verdicts: Map<Int, Verdict?>,
)

/** Text form of a [LessonCheckpoint] for the settings store. */
object LessonCheckpointCodec {
    private const val FIELD = '\n'

    fun encode(c: LessonCheckpoint): String {
        val verdicts = c.verdicts.entries.sortedBy { it.key }.joinToString(",") { (i, v) ->
            "$i:" + when (v) {
                Verdict.CORRECT -> "C"
                Verdict.ALMOST -> "A"
                Verdict.WRONG -> "W"
                null -> "-"
            }
        }
        return listOf(c.unitId, c.lessonId, c.index.toString(), verdicts).joinToString(FIELD.toString())
    }

    /** Null for missing or damaged text. */
    fun decode(raw: String?): LessonCheckpoint? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(FIELD)
        if (parts.size != 4) return null
        val index = parts[2].toIntOrNull() ?: return null
        if (parts[0].isBlank() || parts[1].isBlank() || index < 0) return null
        val verdicts = LinkedHashMap<Int, Verdict?>()
        for (item in parts[3].split(',')) {
            if (item.isBlank()) continue
            val i = item.substringBefore(':').toIntOrNull() ?: return null
            verdicts[i] = when (item.substringAfter(':', "")) {
                "C" -> Verdict.CORRECT
                "A" -> Verdict.ALMOST
                "W" -> Verdict.WRONG
                "-" -> null
                else -> return null
            }
        }
        return LessonCheckpoint(parts[0], parts[1], index, verdicts)
    }
}
