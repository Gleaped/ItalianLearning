package com.gleaped.italianlearning.data.content

import android.content.Context
import android.util.Log
import com.gleaped.italianlearning.content.ContentParser
import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.CourseUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Everything shipped in assets/course, loaded once and kept in memory (it is small). */
data class CourseContent(
    val course: Course,
    val units: Map<String, CourseUnit>,
    /** Units listed as available whose file was missing or unreadable. */
    val loadErrors: List<String>,
)

class ContentRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cached: CourseContent? = null

    suspend fun load(): CourseContent = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { readAll() }.also { cached = it }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun readAll(): CourseContent {
        val course = try {
            ContentParser.parseCourse(readAsset("course/course.json"))
        } catch (e: IOException) {
            Log.e(TAG, "course.json missing", e)
            return CourseContent(Course(), emptyMap(), listOf("course.json is missing"))
        } catch (e: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException.
            Log.e(TAG, "course.json invalid", e)
            return CourseContent(Course(), emptyMap(), listOf("course.json is invalid: ${e.message}"))
        }
        val units = mutableMapOf<String, CourseUnit>()
        val errors = mutableListOf<String>()
        for (ref in course.units.filter { it.available }) {
            try {
                units[ref.id] = ContentParser.parseUnit(readAsset("course/units/${ref.id}.json"))
            } catch (e: IOException) {
                Log.w(TAG, "unit ${ref.id} missing", e)
                errors += "units/${ref.id}.json is missing"
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "unit ${ref.id} invalid", e)
                errors += "units/${ref.id}.json is invalid: ${e.message}"
            }
        }
        return CourseContent(course, units, errors)
    }

    private companion object {
        const val TAG = "ContentRepository"
    }
}
