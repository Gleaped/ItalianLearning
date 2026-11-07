package com.gleaped.italianlearning.content

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** The course plus every available unit that loaded. */
data class CourseContent(
    val course: Course,
    val units: Map<String, CourseUnit>,
    /** Units listed as available whose file was missing or unreadable. */
    val loadErrors: List<String>,
)

/**
 * Loads course content from a text source (assets on Android, files in tests).
 *
 * - course.json is read once and cached.
 * - A unit file is read and parsed only when first asked for ([unit]), then cached, so
 *   opening a lesson or a notification challenge parses one file, not the whole course.
 * - [all] loads every available unit in parallel on [dispatcher] (the course map and
 *   Today need them all to work out what is unlocked).
 *
 * All parsing runs on [dispatcher], never on the caller's (main) thread.
 */
class CourseLoader(
    private val readText: (path: String) -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onError: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {
    private class CourseState(val course: Course, val error: String?)

    private sealed interface UnitResult {
        class Ok(val unit: CourseUnit) : UnitResult
        class Failed(val error: String) : UnitResult
    }

    private val courseMutex = Mutex()
    @Volatile private var courseState: CourseState? = null

    private val unitMutexes = ConcurrentHashMap<String, Mutex>()
    private val unitResults = ConcurrentHashMap<String, UnitResult>()

    private val allMutex = Mutex()
    @Volatile private var allCache: CourseContent? = null

    suspend fun course(): Course = loadCourse().course

    /** A unit listed as available in course.json, or null when unknown, unavailable or broken. */
    suspend fun unit(id: String): CourseUnit? {
        val course = course()
        if (course.units.none { it.id == id && it.available }) return null
        return (loadUnit(id) as? UnitResult.Ok)?.unit
    }

    /** Course plus every available unit, parsed in parallel. Cached after the first call. */
    suspend fun all(): CourseContent {
        allCache?.let { return it }
        return allMutex.withLock {
            allCache ?: buildAll().also { allCache = it }
        }
    }

    private suspend fun buildAll(): CourseContent {
        val state = loadCourse()
        if (state.error != null) return CourseContent(state.course, emptyMap(), listOf(state.error))
        val refs = state.course.units.filter { it.available }.distinctBy { it.id }
        val results = coroutineScope {
            refs.map { ref -> async { ref.id to loadUnit(ref.id) } }.awaitAll()
        }
        val units = LinkedHashMap<String, CourseUnit>()
        val errors = mutableListOf<String>()
        for ((id, result) in results) {
            when (result) {
                is UnitResult.Ok -> units[id] = result.unit
                is UnitResult.Failed -> errors += result.error
            }
        }
        return CourseContent(state.course, units, errors)
    }

    private suspend fun loadCourse(): CourseState {
        courseState?.let { return it }
        return courseMutex.withLock {
            courseState ?: withContext(dispatcher) { readCourse() }.also { courseState = it }
        }
    }

    private fun readCourse(): CourseState = try {
        CourseState(ContentParser.parseCourse(readText(COURSE_PATH)), null)
    } catch (e: IOException) {
        onError("course.json missing", e)
        CourseState(Course(), "course.json is missing")
    } catch (e: IllegalArgumentException) {
        // SerializationException extends IllegalArgumentException.
        onError("course.json invalid", e)
        CourseState(Course(), "course.json is invalid: ${e.message}")
    }

    private suspend fun loadUnit(id: String): UnitResult {
        unitResults[id]?.let { return it }
        val mutex = unitMutexes.computeIfAbsent(id) { Mutex() }
        return mutex.withLock {
            unitResults[id] ?: withContext(dispatcher) { readUnit(id) }.also { unitResults[id] = it }
        }
    }

    private fun readUnit(id: String): UnitResult = try {
        UnitResult.Ok(ContentParser.parseUnit(readText(unitPath(id))))
    } catch (e: IOException) {
        onError("unit $id missing", e)
        UnitResult.Failed("units/$id.json is missing")
    } catch (e: IllegalArgumentException) {
        onError("unit $id invalid", e)
        UnitResult.Failed("units/$id.json is invalid: ${e.message}")
    }

    companion object {
        const val COURSE_PATH = "course/course.json"
        fun unitPath(id: String) = "course/units/$id.json"
    }
}
