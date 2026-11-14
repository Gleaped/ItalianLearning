package com.gleaped.italianlearning.data.oral

import android.content.Context
import com.gleaped.italianlearning.domain.oral.OralLog
import com.gleaped.italianlearning.domain.oral.SavedOral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Scored oral practice answers, newest first, in one JSON file in the app's private storage. */
class OralRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "oral.json")
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<SavedOral>>(emptyList())

    /** Empty until [load] has run. */
    val entries: StateFlow<List<SavedOral>> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = withContext(Dispatchers.IO) {
            OralLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull())
        }
        loaded = true
    }

    suspend fun save(entry: SavedOral) = mutex.withLock {
        ensureLoaded()
        val next = OralLog.add(_entries.value, entry)
        _entries.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(OralLog.encode(next))
                if (!tmp.renameTo(file)) {
                    file.writeText(OralLog.encode(next))
                    tmp.delete()
                }
            }
        }
    }
}
