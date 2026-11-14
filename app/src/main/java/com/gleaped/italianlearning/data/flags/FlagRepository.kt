package com.gleaped.italianlearning.data.flags

import android.content.Context
import com.gleaped.italianlearning.domain.flags.ExerciseFlag
import com.gleaped.italianlearning.domain.flags.FlagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Exercises the learner flagged, newest first, in one JSON file in the app's private storage. */
class FlagRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "flags.json")
    private val mutex = Mutex()
    private var loaded = false
    private val _flags = MutableStateFlow<List<ExerciseFlag>>(emptyList())

    /** Empty until [load] has run. */
    val flags: StateFlow<List<ExerciseFlag>> = _flags.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _flags.value = withContext(Dispatchers.IO) {
            FlagLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull())
        }
        loaded = true
    }

    suspend fun add(flag: ExerciseFlag) = update { FlagLog.add(it, flag) }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(change: (List<ExerciseFlag>) -> List<ExerciseFlag>) = mutex.withLock {
        ensureLoaded()
        val next = change(_flags.value)
        if (next == _flags.value) return@withLock
        _flags.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(FlagLog.encode(next))
                if (!tmp.renameTo(file)) {
                    file.writeText(FlagLog.encode(next))
                    tmp.delete()
                }
            }
        }
    }
}
