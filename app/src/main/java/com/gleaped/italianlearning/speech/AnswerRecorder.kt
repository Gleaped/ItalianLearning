package com.gleaped.italianlearning.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records one spoken answer to a temporary file and plays it back. The recording is overwritten
 * by the next one and removed with [release]; nothing is kept or sent anywhere.
 */
class AnswerRecorder(private val context: Context) {
    private val file = File(context.cacheDir, "oral-answer.m4a")
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    /** True once a recording has been made and can be played. */
    var hasRecording: Boolean = false
        private set

    /** Starts recording; false when the microphone could not be opened. */
    fun start(): Boolean {
        stopPlayback()
        hasRecording = false
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            releaseRecorder()
            false
        }
    }

    /** Stops recording; true when there is something to play. */
    fun stop(): Boolean {
        val r = recorder ?: return hasRecording
        hasRecording = try {
            r.stop()
            file.exists() && file.length() > 0
        } catch (e: RuntimeException) {
            // Stopped straight after starting: no audio was captured.
            false
        }
        releaseRecorder()
        return hasRecording
    }

    /** Plays the recording; [onDone] runs when it ends. False when there is nothing to play. */
    fun play(onDone: () -> Unit): Boolean {
        if (!hasRecording) return false
        stopPlayback()
        return try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                stopPlayback()
                onDone()
            }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            stopPlayback()
            false
        }
    }

    fun stopPlayback() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private fun releaseRecorder() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.release() }
    }

    /** Stops everything and deletes the recording. */
    fun release() {
        stopPlayback()
        releaseRecorder()
        hasRecording = false
        runCatching { file.delete() }
    }
}
