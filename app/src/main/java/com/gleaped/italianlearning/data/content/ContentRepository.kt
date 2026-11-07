package com.gleaped.italianlearning.data.content

import android.content.Context
import android.util.Log
import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.CourseContent
import com.gleaped.italianlearning.content.CourseLoader
import com.gleaped.italianlearning.content.CourseUnit

/**
 * Course content shipped in assets/course. course.json is read eagerly on first use;
 * unit files are parsed lazily (one at a time for a lesson or challenge, all in parallel
 * for the course map), always off the main thread. See [CourseLoader].
 */
class ContentRepository(context: Context) {
    private val assets = context.applicationContext.assets

    private val loader = CourseLoader(
        readText = { path -> assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    suspend fun course(): Course = loader.course()

    /** One unit, parsed on first use. */
    suspend fun unit(id: String): CourseUnit? = loader.unit(id)

    /** The whole course with every available unit. */
    suspend fun load(): CourseContent = loader.all()

    private companion object {
        const val TAG = "ContentRepository"
    }
}
