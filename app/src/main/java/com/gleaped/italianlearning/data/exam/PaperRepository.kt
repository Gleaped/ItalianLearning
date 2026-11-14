package com.gleaped.italianlearning.data.exam

import android.content.Context
import com.gleaped.italianlearning.domain.exam.PaperLog
import com.gleaped.italianlearning.domain.exam.SavedPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Practice papers the learner scored, newest first, in one JSON file in the app's private storage. */
class PaperRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "papers.json")
    private val mutex = Mutex()
    private var loaded = false
    private val _papers = MutableStateFlow<List<SavedPaper>>(emptyList())

    /** Empty until [load] has run. */
    val papers: StateFlow<List<SavedPaper>> = _papers.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _papers.value = withContext(Dispatchers.IO) {
            PaperLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull())
        }
        loaded = true
    }

    suspend fun save(paper: SavedPaper) = mutex.withLock {
        ensureLoaded()
        persist(PaperLog.add(_papers.value, paper))
    }

    suspend fun delete(id: String) = mutex.withLock {
        ensureLoaded()
        persist(_papers.value.filter { it.id != id })
    }

    private suspend fun persist(next: List<SavedPaper>) {
        _papers.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(PaperLog.encode(next))
                if (!tmp.renameTo(file)) {
                    file.writeText(PaperLog.encode(next))
                    tmp.delete()
                }
            }
        }
    }
}
