package com.gleaped.italianlearning.speech

import com.gleaped.italianlearning.ui.components.LocalAppContainer
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gleaped.italianlearning.R

/** Why speech could not be used; the caller then offers typing or skipping. */
enum class SpeechUnavailable { PERMISSION_DENIED, NO_RECOGNIZER, LANGUAGE_UNAVAILABLE }

/**
 * Microphone button using Android's SpeechRecognizer with it-IT.
 * RECORD_AUDIO is requested the first time the learner taps the mic.
 */
@Composable
fun SpeechCapture(
    enabled: Boolean,
    onResult: (List<String>) -> Unit,
    onUnavailable: (SpeechUnavailable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tts = LocalAppContainer.current.tts
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            currentOnUnavailable(SpeechUnavailable.NO_RECOGNIZER)
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
            created.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status = "Parla pure… (speak now)"
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    status = "Un attimo…"
                }

                override fun onError(error: Int) {
                    listening = false
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            currentOnUnavailable(SpeechUnavailable.PERMISSION_DENIED)
                        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                            status = "Italian speech recognition isn't installed on this device."
                            currentOnUnavailable(SpeechUnavailable.LANGUAGE_UNAVAILABLE)
                        }
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            status = "Speech recognition isn't available offline. Try typing instead."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            status = "Didn't catch that. Tap the mic and try again."
                        else -> status = "Something went wrong. Try again or type instead."
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (list.isEmpty()) {
                        status = "Didn't catch that. Tap the mic and try again."
                    } else {
                        status = null
                        currentOnResult(list)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer = created
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        // The microphone must not pick up the app's own voice.
        tts.stop()
        listening = true
        status = "Ascolto…"
        r.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else currentOnUnavailable(SpeechUnavailable.PERMISSION_DENIED)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledIconButton(
            onClick = {
                if (listening) {
                    recognizer?.stopListening()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    start()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled,
            modifier = Modifier.size(72.dp),
            colors = if (listening) {
                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
            } else {
                IconButtonDefaults.filledIconButtonColors()
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = if (listening) "Stop listening" else "Speak",
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = status ?: if (listening) "Ascolto…" else "Tap the mic and speak",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// SpeechRecognizer.ERROR_LANGUAGE_* were added in API 31; values inlined so minSdk 26 is fine.
private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
private const val ERROR_LANGUAGE_UNAVAILABLE = 13
