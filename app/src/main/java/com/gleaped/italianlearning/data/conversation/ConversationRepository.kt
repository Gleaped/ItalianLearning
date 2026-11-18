package com.gleaped.italianlearning.data.conversation

import android.content.Context
import com.gleaped.italianlearning.domain.conversation.ConversationLog
import com.gleaped.italianlearning.domain.conversation.SavedConversation
import com.gleaped.italianlearning.domain.conversation.Scenario
import com.gleaped.italianlearning.domain.conversation.ScenarioCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The conversation scenarios shipped in assets/conversations, and the learner's finished
 * scenes (conversations.json in private storage). A scenario that fails its own checks is
 * left out, so one bad file cannot break the others.
 */
class ConversationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "conversations.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var scenarios: List<Scenario>? = null
    private var loaded = false
    private val _entries = MutableStateFlow<List<SavedConversation>>(emptyList())
    val entries: StateFlow<List<SavedConversation>> = _entries.asStateFlow()

    suspend fun scenarios(): List<Scenario> = mutex.withLock {
        scenarios ?: withContext(Dispatchers.IO) {
            val names = appContext.assets.list(DIR).orEmpty().filter { it.endsWith(".json") }.sorted()
            names.mapNotNull { name ->
                runCatching {
                    val text = appContext.assets.open("$DIR/$name").bufferedReader().use { it.readText() }
                    json.decodeFromString(Scenario.serializer(), text)
                }.getOrNull()?.takeIf { ScenarioCheck.problems(it).isEmpty() }
            }.sortedWith(compareBy({ LEVELS.indexOf(it.level).let { i -> if (i < 0) LEVELS.size else i } }, { it.title }))
        }.also { scenarios = it }
    }

    suspend fun loadLog() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = withContext(Dispatchers.IO) {
            ConversationLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull())
        }
        loaded = true
    }

    suspend fun save(entry: SavedConversation) = mutex.withLock {
        ensureLoaded()
        val next = ConversationLog.add(_entries.value, entry)
        _entries.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(ConversationLog.encode(next))
                if (!tmp.renameTo(file)) {
                    file.writeText(ConversationLog.encode(next))
                    tmp.delete()
                }
            }
        }
    }

    private companion object {
        const val DIR = "conversations"
        val LEVELS = listOf("A1", "A2", "B1", "B2", "C1")
    }
}
