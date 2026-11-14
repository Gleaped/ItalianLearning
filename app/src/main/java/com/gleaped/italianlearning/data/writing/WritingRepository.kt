package com.gleaped.italianlearning.data.writing

import android.content.Context
import com.gleaped.italianlearning.domain.writing.SavedWriting
import com.gleaped.italianlearning.domain.writing.WritingLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The learner's saved free-writing texts, newest first. Kept in one JSON file in the app's
 * private storage (the database has no table for them, and adding one would need a migration).
 */
class WritingRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "writing.json")
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<SavedWriting>>(emptyList())

    /** Empty until [load] has run. */
    val entries: StateFlow<List<SavedWriting>> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = withContext(Dispatchers.IO) {
            WritingLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull())
        }
        loaded = true
    }

    suspend fun save(entry: SavedWriting) = update { WritingLog.add(it, entry.copy(scores = WritingLog.cleanScores(entry.scores))) }

    suspend fun delete(id: String) = update { list -> list.filter { it.id != id } }

    /** Used when a backup is restored. */
    suspend fun replaceAll(entries: List<SavedWriting>) =
        update { entries.filter { it.text.isNotBlank() }.distinctBy { it.id }.take(WritingLog.LIMIT) }

    private suspend fun update(change: (List<SavedWriting>) -> List<SavedWriting>) = mutex.withLock {
        ensureLoaded()
        val next = change(_entries.value)
        if (next == _entries.value) return@withLock
        _entries.value = next
        withContext(Dispatchers.IO) {
            // Written to a temporary file first, so a crash cannot leave half a file behind.
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(WritingLog.encode(next))
                if (!tmp.renameTo(file)) {
                    file.writeText(WritingLog.encode(next))
                    tmp.delete()
                }
            }
        }
    }
}
