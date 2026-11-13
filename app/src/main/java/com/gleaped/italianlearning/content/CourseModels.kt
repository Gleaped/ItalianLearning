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
    /** Groups of units inside a level, shown as headings in the course map. */
    val modules: List<Module> = emptyList(),
) {
    fun modulesOf(levelId: String): List<Module> = modules.filter { it.level == levelId }
}

@Serializable
data class Module(
    val id: String,
    val level: String,
    val title: String,
    val description: String = "",
)

@Serializable
data class Level(
    val id: String,
    val title: String,
    val description: String = "",
    val remedial: Boolean = false,
    /** Foundation level (A1, A2): always open and outside the B1 → C1 unlock chain, like refreshers. */
    val foundation: Boolean = false,
    /** Exam-preparation track (e.g. CELI3): always open, outside the B1 → C1 unlock chain. */
    val exam: Boolean = false,
) {
    /** `exam: true`, or a CELI level id (course.json marks CELI3 by id only). */
    val isExam: Boolean get() = exam || id.uppercase().startsWith("CELI")

    /** Always open, never part of the unlock chain. */
    val alwaysOpen: Boolean get() = remedial || foundation
}

@Serializable
data class UnitRef(
    val id: String,
    val level: String,
    val order: Int = 0,
    val title: String,
    val summary: String = "",
    val available: Boolean = true,
    /** Module id within the level, when course.json groups units. */
    val module: String? = null,
    /** Track id (e.g. "congiuntivo"); track units are also listed in the grammar hub. */
    val track: String? = null,
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
    /** 2 for units written to the short-lesson anatomy. */
    val format: Int = 1,
    val module: String? = null,
    val track: String? = null,
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
            ExerciseTypes.CHOOSE_MOOD,
            ExerciseTypes.TRANSFORM,
            ExerciseTypes.FIND_TRIGGER,
        )
    }
}

data class Lesson(
    val id: String,
    val title: String,
    val minutes: Int,
    val exercises: List<Exercise>,
    /** What the learner will be able to do; shown on the intro screen. */
    val goal: String = "",
    /** The lesson's rule in one line; shown on the wrap-up screen. */
    val takeaway: String = "",
)
