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
)

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
)

data class Lesson(
    val id: String,
    val title: String,
    val minutes: Int,
    val exercises: List<Exercise>,
)
