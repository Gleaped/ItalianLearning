package com.gleaped.italianlearning.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses course.json and unit files. Unknown fields are ignored. Each exercise is
 * decoded on its own so one broken or unknown exercise cannot break a whole unit.
 */
object ContentParser {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Serializable
    private data class LessonDto(
        val id: String,
        val title: String = "",
        val minutes: Int = 10,
        val exercises: List<JsonObject> = emptyList(),
    )

    @Serializable
    private data class UnitDto(
        val id: String,
        val level: String = "",
        val title: String = "",
        val objective: String = "",
        val grammarFocus: String = "",
        val remedialUnitIds: List<String> = emptyList(),
        val lessons: List<LessonDto> = emptyList(),
        val vocabulary: List<VocabItem> = emptyList(),
    )

    fun parseCourse(text: String): Course = json.decodeFromString(Course.serializer(), text)

    fun parseUnit(text: String): CourseUnit {
        val dto = json.decodeFromString(UnitDto.serializer(), text)
        return CourseUnit(
            id = dto.id,
            level = dto.level,
            title = dto.title,
            objective = dto.objective,
            grammarFocus = dto.grammarFocus,
            remedialUnitIds = dto.remedialUnitIds,
            lessons = dto.lessons.map { l ->
                Lesson(
                    id = l.id,
                    title = l.title,
                    minutes = l.minutes,
                    exercises = l.exercises.map(::parseExercise),
                )
            },
            vocabulary = dto.vocabulary,
        )
    }

    fun parseExercise(obj: JsonObject): Exercise {
        val type = (obj["type"] as? JsonPrimitive)?.content.orEmpty()
        return try {
            json.decodeFromJsonElement(ExerciseSerializer, obj)
        } catch (e: SerializationException) {
            Invalid(type = type, error = e.message ?: "Invalid exercise")
        } catch (e: IllegalArgumentException) {
            Invalid(type = type, error = e.message ?: "Invalid exercise")
        }
    }
}
