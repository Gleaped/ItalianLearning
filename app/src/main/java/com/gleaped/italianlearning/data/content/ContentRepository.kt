package com.gleaped.italianlearning.data.content

import android.content.Context
import android.util.Log
import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.CourseContent
import com.gleaped.italianlearning.content.CourseLoader
import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.domain.profile.LearnerName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Course content shipped in assets/course. course.json is read eagerly on first use;
 * unit files are parsed lazily (one at a time for a lesson or challenge, all in parallel
 * for the course map), always off the main thread. See [CourseLoader].
 *
 * Unit files write `{name}` for the learner; the chosen name is swapped in before parsing.
 * When the name changes, the parsed cache is dropped and units are parsed again on demand.
 */
class ContentRepository(context: Context, private val settings: SettingsRepository) {
    private val assets = context.applicationContext.assets
    private val mutex = Mutex()
    @Volatile private var loaderName: String? = null
    @Volatile private var loader: CourseLoader? = null

    private fun newLoader(name: String) = CourseLoader(
        readText = { path ->
            val raw = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            LearnerName.substitute(raw, name)
        },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    private suspend fun current(): CourseLoader {
        val name = settings.current().displayName
        loader?.let { if (loaderName == name) return it }
        return mutex.withLock {
            val existing = loader
            if (existing != null && loaderName == name) {
                existing
            } else {
                newLoader(name).also {
                    loader = it
                    loaderName = name
                }
            }
        }
    }

    suspend fun course(): Course = current().course()

    /** One unit, parsed on first use. */
    suspend fun unit(id: String): CourseUnit? = current().unit(id)

    /** The whole course with every available unit. */
    suspend fun load(): CourseContent = current().all()

    private companion object {
        const val TAG = "ContentRepository"
    }
}
