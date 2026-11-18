package com.gleaped.italianlearning.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.gleaped.italianlearning.domain.speech.AudioKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** What the learner sees of the natural-voice pack in Settings. */
data class VoicePackState(
    val installed: Boolean = false,
    val files: Int = 0,
    val megabytes: Int = 0,
    val downloading: Boolean = false,
    /** 0 to 100 while downloading. */
    val percent: Int = 0,
    val error: String? = null,
)

/**
 * Natural-voice audio rendered ahead of time (tools/audio) and downloaded once. It lives in the
 * app's private storage, works offline afterwards and is optional: any line it lacks is read by
 * the phone's own voice instead.
 */
class VoicePack(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "voicepack")
    private val cache = context.applicationContext.cacheDir

    private val _state = MutableStateFlow(VoicePackState())
    val state: StateFlow<VoicePackState> = _state.asStateFlow()

    @Volatile
    private var known: Set<String> = emptySet()

    init {
        refresh()
    }

    /** The audio file of [text] in [voice], or null when the pack has none. */
    fun fileFor(text: String, voice: String): File? {
        if (known.isEmpty()) return null
        val name = AudioKey.key(text, voice) + ".m4a"
        return if (name in known) File(dir, name) else null
    }

    fun refresh() {
        val files = dir.listFiles { f -> AudioKey.isPackFile(f.name) }.orEmpty()
        known = files.map { it.name }.toSet()
        _state.value = _state.value.copy(
            installed = files.isNotEmpty(),
            files = files.size,
            megabytes = (files.sumOf { it.length() } / (1024 * 1024)).toInt(),
        )
    }

    /** Downloads and unpacks the pack from [url]. Returns true on success; the reason is in [state] otherwise. */
    suspend fun download(url: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(downloading = true, percent = 0, error = null)
        val zip = File(cache, "voicepack.zip")
        val fresh = File(dir.parentFile, "voicepack.new")
        try {
            fetch(url, zip)
            fresh.deleteRecursively()
            fresh.mkdirs()
            var count = 0
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    // Only the audio files are taken, under their own names: nothing else can be written.
                    if (!entry.isDirectory && AudioKey.isPackFile(entry.name)) {
                        File(fresh, entry.name).outputStream().use { zin.copyTo(it) }
                        count++
                    }
                }
            }
            if (count == 0) error("The download held no audio.")
            dir.deleteRecursively()
            if (!fresh.renameTo(dir)) error("The voices could not be saved.")
            refresh()
            _state.value = _state.value.copy(downloading = false, percent = 100)
            true
        } catch (e: Exception) {
            fresh.deleteRecursively()
            _state.value = _state.value.copy(
                downloading = false,
                error = "Could not download the voices (${e.message ?: "no connection"}). Try again on Wi-Fi.",
            )
            false
        } finally {
            zip.delete()
        }
    }

    private fun fetch(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode != 200) error("server answered ${connection.responseCode}")
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = _state.value.copy(percent = (done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun delete() {
        dir.deleteRecursively()
        refresh()
    }

    companion object {
        /** The pack is published as an asset of a pre-release, so Obtainium keeps following app releases only. */
        const val URL = "https://github.com/Gleaped/ItalianLearning/releases/download/voicepack/voicepack.zip"
    }
}

/** Plays pre-rendered lines one after another. */
class VoicePlayer {
    private var player: MediaPlayer? = null
    private var generation = 0

    /** Plays [files] in order at [speed] (1.0 is normal); [onDone] runs after the last, not after a [stop]. */
    fun play(files: List<File>, speed: Float = 1.0f, onDone: () -> Unit = {}) {
        stop()
        val mine = generation
        fun next(index: Int) {
            if (mine != generation) return
            if (index >= files.size) {
                onDone()
                return
            }
            val p = MediaPlayer()
            try {
                p.setDataSource(files[index].absolutePath)
                p.setOnCompletionListener {
                    release(p)
                    next(index + 1)
                }
                p.setOnErrorListener { _, _, _ ->
                    release(p)
                    next(index + 1)
                    true
                }
                p.prepare()
                if (speed != 1.0f) runCatching { p.playbackParams = PlaybackParams().setSpeed(speed) }
                p.start()
                player = p
            } catch (e: Exception) {
                release(p)
                next(index + 1)
            }
        }
        next(0)
    }

    fun stop() {
        generation++
        player?.let { release(it) }
        player = null
    }

    private fun release(p: MediaPlayer) {
        runCatching { p.stop() }
        runCatching { p.release() }
        if (player === p) player = null
    }
}
