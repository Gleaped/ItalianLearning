package com.gleaped.italianlearning.ui.oral

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gleaped.italianlearning.domain.oral.OralCriterion
import com.gleaped.italianlearning.domain.oral.OralLog
import com.gleaped.italianlearning.domain.oral.OralPrompt
import com.gleaped.italianlearning.domain.oral.SavedOral
import com.gleaped.italianlearning.domain.progress.ExamClock
import com.gleaped.italianlearning.speech.AnswerRecorder
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.SpeakerButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Step { INTRO, PREPARE, SPEAK, REVIEW, DONE }

/**
 * "Prova orale": a practice round like the exam's speaking part. Three tasks, each with time to
 * prepare, then you speak and record yourself, listen back, compare with a model answer and score
 * yourself on the four exam criteria. The recording is never kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OralScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val recorder = remember { AnswerRecorder(context) }
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            container.tts.stop()
        }
    }

    var prompts by remember { mutableStateOf<List<OralPrompt>?>(null) }
    LaunchedEffect(Unit) {
        container.oral.load()
        prompts = OralLog.pick(OralLog.candidates(container.content.load().units))
    }

    var step by remember { mutableStateOf(Step.INTRO) }
    var index by remember { mutableIntStateOf(0) }
    var prepMinutes by remember { mutableIntStateOf(OralLog.PREP_MINUTES[1]) }
    val scores = remember { mutableStateMapOf<String, Int>() }
    val roundScores = remember { mutableStateMapOf<Int, Map<String, Int>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prova orale") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = prompts
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (list.isEmpty()) {
                Text("No speaking tasks were found in the exam track.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            val current = list.getOrNull(index)
            when (step) {
                Step.INTRO -> {
                    Text("A round of ${list.size} speaking tasks", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "For each task you get time to prepare and make notes on paper, then you speak for a minute or two while the phone records you. " +
                            "You listen back, read a model answer and score yourself on the four criteria the examiners use. " +
                            "The recording stays on the phone and is deleted when you leave.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Preparation time per task", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OralLog.PREP_MINUTES.forEach { m ->
                            FilterChip(selected = prepMinutes == m, onClick = { prepMinutes = m }, label = { Text("$m min") })
                        }
                    }
                    Text(
                        "The real oral gives about 15 minutes of preparation for all of its parts together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { step = Step.PREPARE }, modifier = Modifier.fillMaxWidth()) { Text("Start") }
                }

                Step.PREPARE -> if (current != null) {
                    TaskHeader(index, list.size, current)
                    var left by remember(index) { mutableIntStateOf(prepMinutes * 60) }
                    LaunchedEffect(index) {
                        while (left > 0) {
                            delay(1000)
                            left--
                        }
                    }
                    Text(
                        if (left > 0) "Prepare: ${ExamClock.format(left)}" else "Time to speak",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Think about what you will say: how you start, two or three points, how you finish. Notes on paper are fine.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { step = Step.SPEAK }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (left > 0) "I'm ready" else "Speak now")
                    }
                }

                Step.SPEAK -> if (current != null) {
                    TaskHeader(index, list.size, current)
                    RecordPanel(recorder = recorder, onRecorded = { step = Step.REVIEW })
                }

                Step.REVIEW -> if (current != null) {
                    TaskHeader(index, list.size, current)
                    PlaybackPanel(recorder)
                    var showModel by remember(index) { mutableStateOf(false) }
                    OutlinedButton(onClick = { showModel = !showModel }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showModel) "Hide the model answer" else "Show a model answer")
                    }
                    if (showModel) {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                LookupText(current.modelAnswer, style = MaterialTheme.typography.bodyLarge)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpeakerButton(current.modelAnswer) }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Score yourself, as an examiner would", style = MaterialTheme.typography.titleMedium)
                    OralCriterion.entries.forEach { criterion ->
                        val value = scores[criterion.key] ?: 0
                        Column {
                            Text(
                                "${criterion.italian}: " + if (value == 0) "not scored" else "$value/${OralCriterion.MAX_SCORE}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(criterion.question, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { v ->
                                    val n = v.roundToInt()
                                    if (n == 0) scores.remove(criterion.key) else scores[criterion.key] = n
                                },
                                valueRange = 0f..OralCriterion.MAX_SCORE.toFloat(),
                                steps = OralCriterion.MAX_SCORE - 1,
                            )
                        }
                    }
                    val last = index == list.size - 1
                    Button(
                        onClick = {
                            val given = scores.toMap()
                            roundScores[index] = given
                            if (given.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                // Outlives this screen, so the scores are kept even if it is closed at once.
                                container.appScope.launch {
                                    container.oral.save(SavedOral("$now-${current.exerciseId.orEmpty()}", current.prompt, given, now))
                                }
                            }
                            scores.clear()
                            recorder.stopPlayback()
                            if (last) {
                                step = Step.DONE
                            } else {
                                index++
                                step = Step.PREPARE
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (last) "Finish" else "Next task") }
                }

                Step.DONE -> {
                    Text("Round finished", style = MaterialTheme.typography.titleLarge)
                    val all = roundScores.values.toList()
                    if (all.isEmpty()) {
                        Text("You did not score yourself this time, so nothing was saved.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        OralCriterion.entries.forEach { c ->
                            val values = all.mapNotNull { it[c.key] }
                            if (values.isNotEmpty()) {
                                Text("${c.italian}: ${"%.1f".format(values.average())} of ${OralCriterion.MAX_SCORE}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text(
                            "Your own scores are kept under Progress. Notice which criterion is lowest and aim your next round at it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                prompts = OralLog.pick(OralLog.candidates(container.content.load().units))
                                index = 0
                                roundScores.clear()
                                step = Step.INTRO
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Another round") }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun TaskHeader(index: Int, total: Int, task: OralPrompt) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Task ${index + 1} of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        task.instruction?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        }
        LookupText(task.prompt, style = MaterialTheme.typography.titleLarge)
    }
}

/** Record button: asks for the microphone the first time, then records until stopped. */
@Composable
private fun RecordPanel(recorder: AnswerRecorder, onRecorded: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }

    val begin: () -> Unit = {
        container.tts.stop()
        if (recorder.start()) {
            problem = null
            seconds = 0
            recording = true
        } else {
            problem = "The microphone could not be opened. Another app may be using it."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else problem = "The microphone is needed to record your answer. You can allow it in the phone's app settings."
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            seconds++
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            if (recording) "Recording ${ExamClock.format(seconds)}" else "Speak for one or two minutes, in full sentences.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (!recording) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        begin()
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start recording") }
        } else {
            Button(
                onClick = {
                    recording = false
                    if (recorder.stop()) onRecorded() else problem = "Nothing was recorded. Try again and speak a little longer."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop") }
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        if (!recording) {
            TextButton(onClick = onRecorded) { Text("Skip the recording") }
        }
    }
}

/** Listen back to the recording. */
@Composable
private fun PlaybackPanel(recorder: AnswerRecorder) {
    var playing by remember { mutableStateOf(false) }
    if (!recorder.hasRecording) {
        Text("No recording this time. Compare your answer from memory with the model answer.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    OutlinedButton(
        onClick = {
            if (playing) {
                recorder.stopPlayback()
                playing = false
            } else {
                playing = recorder.play { playing = false }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (playing) "Stop playback" else "Listen to your answer") }
}
