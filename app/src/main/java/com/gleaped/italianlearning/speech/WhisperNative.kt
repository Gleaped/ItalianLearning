package com.gleaped.italianlearning.speech

/** JNI bridge to whisper.cpp (see app/src/main/cpp). Loaded lazily so a missing library only disables Whisper. */
internal object WhisperNative {
    val available: Boolean = try {
        System.loadLibrary("italiano_whisper")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic external fun init(path: String): Long
    @JvmStatic external fun free(handle: Long)
    @JvmStatic external fun transcribe(handle: Long, samples: FloatArray, language: String, threads: Int): String
}
