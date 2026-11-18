package com.gleaped.italianlearning.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.gleaped.italianlearning.domain.speech.Endpointer
import com.gleaped.italianlearning.domain.speech.WhisperText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The Whisper models the learner can choose between; both are 5-bit quantised multilingual models. */
enum class WhisperModel(val id: String, val label: String, val fileName: String, val approxMb: Int) {
    BASE("base", "Base", "ggml-base-q5_1.bin", 60),
    SMALL("small", "Small", "ggml-small-q5_1.bin", 190);

    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        fun of(id: String): WhisperModel = entries.firstOrNull { it.id == id } ?: BASE
    }
}

data class WhisperState(
    /** False when the native library did not load (for example a 32-bit phone). */
    val supported: Boolean = true,
    val installed: Set<WhisperModel> = emptySet(),
    val downloading: WhisperModel? = null,
    val percent: Int = 0,
    val error: String? = null,
)

/** Audio captured for Whisper: 16 kHz mono samples in -1..1. */
class Recording(val samples: FloatArray, val heardSpeech: Boolean)

/**
 * Optional on-device recogniser. Nothing here runs unless the learner switched Whisper on in Settings and
 * downloaded a model; the phone's own recogniser is used otherwise. Audio never leaves the phone.
 */
class WhisperEngine(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "whisper")

    private val _state = MutableStateFlow(WhisperState(supported = WhisperNative.available))
    val state: StateFlow<WhisperState> = _state.asStateFlow()

    private val lock = Mutex()
    private var handle = 0L
    private var loaded: WhisperModel? = null

    init {
        refresh()
    }

    fun file(model: WhisperModel) = File(dir, model.fileName)

    fun isReady(model: WhisperModel): Boolean = WhisperNative.available && model in _state.value.installed

    fun refresh() {
        _state.value = _state.value.copy(installed = WhisperModel.entries.filter { validModel(file(it)) }.toSet())
    }

    /** A whole ggml file starts with the magic "lmgg" and is megabytes long; a failed download is neither. */
    private fun validModel(f: File): Boolean {
        if (!f.isFile || f.length() < 10L * 1024 * 1024) return false
        return runCatching {
            f.inputStream().use { input ->
                val head = ByteArray(4)
                input.read(head) == 4 && String(head, Charsets.ISO_8859_1) == "lmgg"
            }
        }.getOrDefault(false)
    }

    suspend fun download(model: WhisperModel): Boolean = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(downloading = model, percent = 0, error = null)
        dir.mkdirs()
        val partial = File(dir, model.fileName + ".part")
        try {
            val connection = URL(model.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            try {
                if (connection.responseCode != 200) error("server answered ${connection.responseCode}")
                val total = connection.contentLengthLong
                var done = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { out ->
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
            if (!validModel(partial)) error("the file was not a Whisper model")
            lock.withLock { releaseLocked() }
            file(model).delete()
            if (!partial.renameTo(file(model))) error("the model could not be saved")
            refresh()
            _state.value = _state.value.copy(downloading = null, percent = 100)
            true
        } catch (e: Exception) {
            partial.delete()
            _state.value = _state.value.copy(
                downloading = null,
                error = "Could not download the model (${e.message ?: "no connection"}). Try again on Wi-Fi.",
            )
            false
        }
    }

    suspend fun delete(model: WhisperModel) {
        lock.withLock { if (loaded == model) releaseLocked() }
        file(model).delete()
        refresh()
    }

    /** Transcribes Italian speech. Returns null if the model could not be loaded. */
    suspend fun transcribe(model: WhisperModel, samples: FloatArray): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (!WhisperNative.available) return@withLock null
            if (loaded != model) {
                releaseLocked()
                val h = WhisperNative.init(file(model).absolutePath)
                if (h == 0L) return@withLock null
                handle = h
                loaded = model
            }
            val threads = min(4, max(1, Runtime.getRuntime().availableProcessors() - 2))
            WhisperText.clean(WhisperNative.transcribe(handle, samples, "it", threads))
        }
    }

    /** Frees the model's memory (about the size of the file). Called when the app is done with speech. */
    suspend fun release() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        if (handle != 0L) WhisperNative.free(handle)
        handle = 0L
        loaded = null
    }
}

/** Records the microphone at 16 kHz until the learner stops, goes quiet, or time runs out. */
object WhisperRecorder {
    private const val RATE = 16_000
    private const val FRAME = RATE / 10

    /** Returns null if the microphone could not be opened. [stopRequested] is polled every 100 ms. */
    @SuppressLint("MissingPermission") // the caller checks RECORD_AUDIO first
    suspend fun record(onLevel: (Float) -> Unit, stopRequested: () -> Boolean): Recording? = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return@withContext null
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, FRAME * 2 * 4),
            )
        } catch (_: Exception) {
            return@withContext null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }
        val endpointer = Endpointer()
        val all = FloatArray(RATE * 31)
        var count = 0
        val buffer = ShortArray(FRAME)
        try {
            recorder.startRecording()
            while (coroutineContext.isActive) {
                var got = 0
                while (got < FRAME) {
                    val n = recorder.read(buffer, got, FRAME - got)
                    if (n <= 0) return@withContext if (count > 0) finish(all, count, endpointer) else null
                    got += n
                }
                var sum = 0.0
                for (i in 0 until FRAME) {
                    val v = buffer[i] / 32768f
                    all[count + i] = v
                    sum += v * v
                }
                count += FRAME
                val rms = sqrt(sum / FRAME)
                onLevel(min(1f, (rms * 12).toFloat()))
                val decision = endpointer.onFrame(rms)
                if (stopRequested() || decision != Endpointer.Decision.CONTINUE) break
            }
            finish(all, count, endpointer)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun finish(all: FloatArray, count: Int, endpointer: Endpointer): Recording {
        // Keep a third of a second of the trailing quiet so the last word is not clipped.
        val trim = max(0, endpointer.trailingQuietFrames - 3) * FRAME
        return Recording(all.copyOf(max(0, count - trim)), endpointer.heardSpeech)
    }
}
