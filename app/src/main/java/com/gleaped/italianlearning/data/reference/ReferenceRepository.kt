package com.gleaped.italianlearning.data.reference

import android.content.Context
import android.util.Log
import com.gleaped.italianlearning.content.reference.GrammarReference
import com.gleaped.italianlearning.content.reference.ReferenceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The grammar reference in assets/grammar/reference.json, parsed once off the main thread.
 * A missing or broken file yields null; the Grammatica screen then says so.
 */
class ReferenceRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val mutex = Mutex()
    @Volatile private var loaded = false
    @Volatile private var cached: GrammarReference? = null

    suspend fun load(): GrammarReference? {
        if (loaded) return cached
        return mutex.withLock {
            if (!loaded) {
                cached = withContext(Dispatchers.Default) { read() }
                loaded = true
            }
            cached
        }
    }

    private fun read(): GrammarReference? = try {
        val text = assets.open(PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        ReferenceParser.parse(text)
    } catch (e: IOException) {
        Log.i(TAG, "No grammar reference installed", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "reference.json is invalid", e)
        null
    }

    private companion object {
        const val TAG = "ReferenceRepository"
        const val PATH = "grammar/reference.json"
    }
}
