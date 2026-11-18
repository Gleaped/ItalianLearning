package com.gleaped.italianlearning.data.oral

import android.content.Context
import com.gleaped.italianlearning.domain.oral.OralPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The rehearsal orals shipped in assets/oral. A file that cannot be read is skipped. */
class OralPaperRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cached: List<OralPaper>? = null

    suspend fun papers(): List<OralPaper> = cached ?: withContext(Dispatchers.IO) {
        appContext.assets.list("oral").orEmpty().filter { it.endsWith(".json") }.sorted().mapNotNull { name ->
            runCatching {
                val text = appContext.assets.open("oral/$name").bufferedReader().use { it.readText() }
                json.decodeFromString(OralPaper.serializer(), text)
            }.getOrNull()?.takeIf { it.tasks.isNotEmpty() }
        }
    }.also { cached = it }
}
