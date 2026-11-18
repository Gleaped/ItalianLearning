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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gleaped.italianlearning.domain.oral.ExamTask
import com.gleaped.italianlearning.domain.oral.Fluency
import com.gleaped.italianlearning.domain.oral.FluencyMeter
import com.gleaped.italianlearning.domain.oral.OralCriterion
import com.gleaped.italianlearning.domain.oral.OralFeedback
import com.gleaped.italianlearning.domain.oral.OralPaper
import com.gleaped.italianlearning.domain.oral.SavedOral
import com.gleaped.italianlearning.domain.progress.ExamClock
import com.gleaped.italianlearning.speech.AnswerRecorder
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.SpeakerButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Phase { PICK, PREPARE, EXAM, REVIEW, DONE }

/** One recorded answer of the rehearsal: which task, the question asked, and what the microphone heard. */
private class Take(val task: Int, val question: String?, val key: String, var fluency: Fluency? = null)

/**
 * A full rehearsal of the CELI 3 oral: preparation time with the materials, then an examiner who
 * speaks, with a presentation, a photo, a text and a role-play, each followed by a question.
 * Everything is recorded on the phone only, played back at the end, and deleted on leaving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OralExamScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var papers by remember { mutableStateOf<List<OralPaper>?>(null) }
    var paper by remember { mutableStateOf<OralPaper?>(null) }
    var phase by remember { mutableStateOf(Phase.PICK) }
    var prepMinutes by remember { mutableIntStateOf(15) }
    var micAllowed by remember { mutableStateOf(false) }

    val recorders = remember { HashMap<String, AnswerRecorder>() }
    val takes = remember { mutableListOf<Take>() }
    val notes = remember { mutableStateMapOf<Int, String>() }
    val scores = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) {
        container.oral.load()
        papers = container.oralPapers.papers()
    }
    DisposableEffect(Unit) {
        onDispose {
            container.tts.stop()
            recorders.values.forEach { it.release() }
            recorders.clear()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAllowed = granted
        phase = Phase.PREPARE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Esame orale") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = papers
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (list == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val p = paper
                when (phase) {
                    Phase.PICK -> {
                        Text("A full rehearsal of the CELI 3 oral", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "You get your materials and time to prepare, then an examiner talks with you: a short presentation, " +
                                "a photo, a text and a role-play, each with a follow-up question. It takes about 15 minutes. " +
                                "You are recorded on this phone only; you listen back, see how long you spoke and where you paused, " +
                                "and score yourself like an examiner. Nothing is sent anywhere.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (list.isEmpty()) Text("No rehearsal papers were found.", style = MaterialTheme.typography.bodyLarge)
                        Text("Preparation time", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { m ->
                                FilterChip(selected = prepMinutes == m, onClick = { prepMinutes = m }, label = { Text("$m min") })
                            }
                        }
                        Text(
                            "The real exam gives about 15 minutes. Start with 5 if you are new to this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        list.forEach { candidate ->
                            Button(
                                onClick = {
                                    paper = candidate
                                    takes.clear()
                                    notes.clear()
                                    scores.clear()
                                    recorders.values.forEach { it.release() }
                                    recorders.clear()
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        micAllowed = true
                                        phase = Phase.PREPARE
                                    } else {
                                        permission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(candidate.title) }
                        }
                    }

                    Phase.PREPARE -> if (p != null) {
                        val prepared = p.tasks.withIndex().filter { it.value.kind != "presentation" }
                        var left by remember { mutableIntStateOf(prepMinutes * 60) }
                        LaunchedEffect(Unit) {
                            while (left > 0) {
                                delay(1000)
                                left--
                            }
                        }
                        Text(
                            if (left > 0) "Preparation ${ExamClock.format(left)}" else "Time is up",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "These are your three materials. Think about what you will say and write short notes below; you may look at them in the exam, as on paper.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!micAllowed) {
                            Text(
                                "The microphone is not allowed, so nothing will be recorded. You can allow it in the phone's app settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        prepared.forEach { (index, task) ->
                            MaterialCard(task) {
                                OutlinedTextField(
                                    value = notes[index].orEmpty(),
                                    onValueChange = { notes[index] = it },
                                    label = { Text("Your notes") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                )
                            }
                        }
                        Button(onClick = { phase = Phase.EXAM }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (left > 0) "I'm ready: start the exam" else "Start the exam")
                        }
                    }

                    Phase.EXAM -> if (p != null) {
                        ExamRun(
                            paper = p,
                            notes = notes,
                            micAllowed = micAllowed,
                            recorderFor = { key -> recorders.getOrPut(key) { AnswerRecorder(context, "exam-$key.m4a") } },
                            onTake = { takes += it },
                            onFinished = { phase = Phase.REVIEW },
                        )
                    }

                    Phase.REVIEW -> if (p != null) {
                        Review(
                            paper = p,
                            takes = takes,
                            recorderFor = { key -> recorders[key] },
                            scores = scores,
                            onSave = {
                                val given = scores.toMap()
                                if (given.isNotEmpty()) {
                                    val now = System.currentTimeMillis()
                                    scope.launch { container.oral.save(SavedOral("$now-${p.id}", "Esame orale: ${p.title}", given, now)) }
                                }
                                phase = Phase.DONE
                            },
                        )
                    }

                    Phase.DONE -> {
                        val estimate = OralFeedback.estimate(scores)
                        Text("Esame finito", style = MaterialTheme.typography.titleLarge)
                        if (estimate == null) {
                            Text("You did not score yourself, so nothing was saved.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "Your own scores suggest about $estimate out of ${OralFeedback.MAX_MARK} for the oral " +
                                    "(the pass mark is ${OralFeedback.PASS_MARK}). It is a guess from your own marking, not an official result.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "The scores are kept under Progress. Aim your next rehearsal at the criterion you scored lowest.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                recorders.values.forEach { it.release() }
                                recorders.clear()
                                phase = Phase.PICK
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Another rehearsal") }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialCard(task: ExamTask, extra: @Composable () -> Unit = {}) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (task.material.isNotBlank()) LookupText(task.material, style = MaterialTheme.typography.bodyLarge)
            if (task.cues.isNotEmpty()) {
                Text(
                    "Cover: " + task.cues.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra()
        }
    }
}

private enum class Stage { LISTEN, SPEAK, FOLLOW_LISTEN, FOLLOW_SPEAK }

/** The examination itself, task by task. Calls [onTake] after each recorded answer and [onFinished] at the end. */
@Composable
private fun ExamRun(
    paper: OralPaper,
    notes: Map<Int, String>,
    micAllowed: Boolean,
    recorderFor: (String) -> AnswerRecorder,
    onTake: (Take) -> Unit,
    onFinished: () -> Unit,
) {
    val container = LocalAppContainer.current
    val tts = container.tts
    var index by remember { mutableIntStateOf(-1) }
    var stage by remember { mutableStateOf(Stage.LISTEN) }
    var seconds by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val random = remember { Random.Default }
    var greeting by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf("") }
    var meter by remember { mutableStateOf(FluencyMeter()) }
    var meterKey by remember { mutableStateOf("") }
    var frames by remember { mutableIntStateOf(0) }
    var spoken by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        greeting = paper.greeting.randomOrNull(random).orEmpty()
        if (greeting.isNotBlank()) tts.speakSequence(listOf(greeting), second = true)
        delay(2500)
        index = 0
    }
    val task = paper.tasks.getOrNull(index)
    LaunchedEffect(index) {
        val t = paper.tasks.getOrNull(index) ?: return@LaunchedEffect
        stage = Stage.LISTEN
        spoken = t.examiner.random(random)
        tts.speakSequence(listOf(spoken), second = true)
    }
    // While recording: a second counter and the microphone level, for the pauses.
    LaunchedEffect(recording, meterKey) {
        while (recording) {
            delay(FluencyMeter.FRAME_MILLIS.toLong())
            meter.add(recorderFor(meterKey).level())
            frames++
            seconds = frames * FluencyMeter.FRAME_MILLIS / 1000
        }
    }

    fun beginTake(key: String) {
        tts.stop()
        problem = null
        seconds = 0
        frames = 0
        meter = FluencyMeter()
        if (!micAllowed) return
        val r = recorderFor(key)
        if (r.start()) {
            meterKey = key
            recording = true
        } else {
            problem = "The microphone could not be opened. You can continue without recording."
        }
    }

    fun endTake(followUp: String?) {
        val key = "$index-${if (followUp == null) 0 else 1}"
        recording = false
        val ok = if (micAllowed) recorderFor(key).stop() else false
        val take = Take(index, followUp, key, if (ok) meter.result() else null)
        onTake(take)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (task == null) {
            if (index < 0) {
                Text("L'esaminatrice sta per cominciare…", style = MaterialTheme.typography.titleMedium)
                if (greeting.isNotBlank()) ExaminerLine(greeting)
            } else {
                // After the last task.
                LaunchedEffect(Unit) {
                    closing = paper.closing.randomOrNull(random).orEmpty()
                    if (closing.isNotBlank()) tts.speakSequence(listOf(closing), second = true)
                }
                if (closing.isNotBlank()) ExaminerLine(closing)
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Listen back and score") }
            }
            return@Column
        }

        Text("Part ${index + 1} of ${paper.tasks.size}: ${task.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ExaminerLine(if (stage == Stage.FOLLOW_LISTEN || stage == Stage.FOLLOW_SPEAK) question else spoken)
        MaterialCard(task) {
            notes[index]?.takeIf { it.isNotBlank() }?.let {
                Text("Your notes: $it", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
        }
        if (recording) {
            Text("Recording ${ExamClock.format(seconds)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

        when (stage) {
            Stage.LISTEN -> Button(onClick = {
                stage = Stage.SPEAK
                beginTake("$index-0")
            }, modifier = Modifier.fillMaxWidth()) { Text("Start my answer") }

            Stage.SPEAK -> Button(onClick = {
                val follow = task.followUps.randomOrNull(random)
                endTake(null)
                if (follow == null) {
                    index++
                } else {
                    question = follow
                    stage = Stage.FOLLOW_LISTEN
                    tts.speakSequence(listOf(follow), second = true)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("I've finished") }

            Stage.FOLLOW_LISTEN -> Button(onClick = {
                stage = Stage.FOLLOW_SPEAK
                beginTake("$index-1")
            }, modifier = Modifier.fillMaxWidth()) { Text("Answer the question") }

            Stage.FOLLOW_SPEAK -> Button(onClick = {
                endTake(question)
                index++
            }, modifier = Modifier.fillMaxWidth()) { Text("I've finished") }
        }
        if (stage == Stage.LISTEN || stage == Stage.FOLLOW_LISTEN) {
            TextButton(onClick = {
                tts.speakSequence(listOf(if (stage == Stage.LISTEN) spoken else question), slow = true, second = true)
            }) { Text("Ask me again, slowly") }
        }
    }
}

@Composable
private fun ExaminerLine(text: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("L'esaminatrice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            SpeakerButton(text, second = true)
        }
    }
}

@Composable
private fun Review(
    paper: OralPaper,
    takes: List<Take>,
    recorderFor: (String) -> AnswerRecorder?,
    scores: MutableMap<String, Int>,
    onSave: () -> Unit,
) {
    Text("Listen back, then score yourself", style = MaterialTheme.typography.titleLarge)
    paper.tasks.forEachIndexed { index, task ->
        val mine = takes.filter { it.task == index }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                mine.forEach { take ->
                    Text(take.question ?: "Your answer", style = MaterialTheme.typography.labelLarge)
                    PlayTake(recorderFor(take.key))
                    take.fluency?.let { f ->
                        OralFeedback.comments(f, task).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (mine.isEmpty()) Text("No answer was recorded for this part.", style = MaterialTheme.typography.bodySmall)
                var showModel by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showModel = !showModel }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showModel) "Hide a model answer" else "Show a model answer")
                }
                if (showModel) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LookupText(task.modelAnswer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        SpeakerButton(task.modelAnswer)
                    }
                }
            }
        }
    }
    HorizontalDivider()
    Text("Score yourself as an examiner would", style = MaterialTheme.typography.titleMedium)
    OralCriterion.entries.forEach { criterion ->
        val value = scores[criterion.key] ?: 0
        Column {
            Text("${criterion.italian}: " + if (value == 0) "not scored" else "$value/${OralCriterion.MAX_SCORE}", style = MaterialTheme.typography.bodyLarge)
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
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
}

@Composable
private fun PlayTake(recorder: AnswerRecorder?) {
    var playing by remember { mutableStateOf(false) }
    if (recorder == null || !recorder.hasRecording) {
        Text("No recording.", style = MaterialTheme.typography.bodySmall)
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
