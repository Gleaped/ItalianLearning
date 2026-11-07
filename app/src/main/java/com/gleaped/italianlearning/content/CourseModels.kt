package com.gleaped.italianlearning.content

import kotlinx.serialization.Serializable

/**
 * Content models mirroring docs/lesson-format.md. Pure Kotlin (no Android
 * dependencies) so they can be unit-tested on the JVM.
 */
@Serializable
data class Course(
    val version: Int = 1,
    val levels: List<Level> = emptyList(),
    val units: List<UnitRef> = emptyList(),
)

@Serializable
data class Level(
    val id: String,
    val title: String,
    val description: String = "",
    val remedial: Boolean = false,
    /** Exam-preparation track (e.g. CELI3): always open, outside the B1 → C1 unlock chain. */
    val exam: Boolean = false,
) {
    /** `exam: true`, or a CELI level id (course.json marks CELI3 by id only). */
    val isExam: Boolean get() = exam || id.uppercase().startsWith("CELI")
}

@Serializable
data class UnitRef(
    val id: String,
    val level: String,
    val order: Int = 0,
    val title: String,
    val summary: String = "",
    val available: Boolean = true,
)

@Serializable
data class VocabItem(
    val it: String,
    val en: String,
    val example: String? = null,
)

/** A unit file after parsing; exercises are decoded one by one (see [ContentParser]). */
data class CourseUnit(
    val id: String,
    val level: String,
    val title: String,
    val objective: String,
    val grammarFocus: String,
    val remedialUnitIds: List<String>,
    val lessons: List<Lesson>,
    val vocabulary: List<VocabItem>,
    val grammarTips: List<GrammarTip> = emptyList(),
)

/**
 * A short grammar reminder used by notifications and the unit page. [challenge] is
 * null when the tip has none or it could not be decoded.
 */
data class GrammarTip(
    val id: String,
    val unitId: String,
    val rule: String,
    val tip: String,
    val example: ItEn?,
    val challenge: Exercise?,
) {
    companion object {
        const val MAX_RULE_LENGTH = 40
        const val MAX_TIP_LENGTH = 140
        const val MAX_CHALLENGE_ITEMS = 3

        /** Exercise types a tip challenge may use (one-card, quick to answer). */
        val QUICK_TYPES: Set<String> = setOf(
            ExerciseTypes.MULTIPLE_CHOICE,
            ExerciseTypes.GAP_FILL,
            ExerciseTypes.TRANSLATE,
            ExerciseTypes.ERROR_CORRECTION,
            ExerciseTypes.CONJUGATION,
        )
    }
}

data class Lesson(
    val id: String,
    val title: String,
    val minutes: Int,
    val exercises: List<Exercise>,
)
