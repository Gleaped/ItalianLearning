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
        val goal: String = "",
        val takeaway: String = "",
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
        val grammarTips: List<JsonObject> = emptyList(),
        val format: Int = 1,
        val module: String? = null,
        val track: String? = null,
    )

    @Serializable
    private data class GrammarTipDto(
        val id: String = "",
        val rule: String = "",
        val tip: String = "",
        val example: ItEn? = null,
        val challenge: JsonObject? = null,
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
                    goal = l.goal.trim(),
                    takeaway = l.takeaway.trim(),
                )
            },
            vocabulary = dto.vocabulary,
            grammarTips = dto.grammarTips.mapIndexedNotNull { i, obj -> parseGrammarTip(dto.id, i, obj) },
            format = dto.format,
            module = dto.module,
            track = dto.track,
        )
    }

    /**
     * One tip, decoded on its own so a broken tip is dropped without breaking the unit.
     * A tip without rule or text is dropped; a broken challenge leaves the tip without one.
     */
    fun parseGrammarTip(unitId: String, index: Int, obj: JsonObject): GrammarTip? {
        val dto = try {
            json.decodeFromJsonElement(GrammarTipDto.serializer(), obj)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (dto.rule.isBlank() || dto.tip.isBlank()) return null
        val challenge = dto.challenge?.let(::parseExercise)?.takeUnless { it is Invalid || it is Unsupported }
        return GrammarTip(
            id = dto.id.ifBlank { "$unitId-t${index + 1}" },
            unitId = unitId,
            rule = dto.rule.trim(),
            tip = dto.tip.trim(),
            example = dto.example?.takeIf { it.it.isNotBlank() },
            challenge = challenge,
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
