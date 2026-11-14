package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gleaped.italianlearning.content.reference.GrammarReference
import com.gleaped.italianlearning.content.reference.ReferenceParser
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.SpeakerButton
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.RichText
import com.gleaped.italianlearning.ui.components.highlightColor
import com.gleaped.italianlearning.ui.components.inlineMarkup
import com.gleaped.italianlearning.content.GrammarNote
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.platform.LocalConfiguration
import com.gleaped.italianlearning.ui.lookup.WordLookupHost
import androidx.compose.runtime.produceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    unitId: String,
    lessonId: String,
    onClose: () -> Unit,
    onOpenLesson: (unitId: String, lessonId: String) -> Unit,
    onReview: () -> Unit,
    onOpenRule: (topicId: String) -> Unit = {},
    onOpenWord: (lemmaId: Long) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: LessonViewModel = viewModel(factory = LessonViewModel.factory(container, unitId, lessonId))
    val reference by produceState<GrammarReference?>(null, container) { value = container.reference.load() }
    val ruleFor: (com.gleaped.italianlearning.content.GrammarNote) -> RuleLink? = remember(reference, unitId) {
        { note: com.gleaped.italianlearning.content.GrammarNote ->
            reference?.let { ReferenceParser.topicForNote(it, unitId, note.title, note.body) }
                ?.let { topic -> RuleLink(topic.title) { onOpenRule(topic.id) } }
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    val running = state is LessonUiState.Running
    BackHandler(enabled = running) { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this lesson?") },
            text = {
                Text(
                    if (vm.isPractice) {
                        "This round won't be saved. The items stay in Punti deboli for next time."
                    } else {
                        "Your place is saved. You can pick up where you left off, or start again."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    container.tts.stop()
                    onClose()
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }

    val primaryAction = remember { PrimaryActionState() }

    // Long-press an Italian word anywhere in the lesson to look it up.
    WordLookupHost(onOpenEntry = onOpenWord) {
      PrimaryActionHost(primaryAction) {
        Scaffold(
            // Edge-to-edge: the window is not resized for the keyboard, so pad for it here. The
            // exercise column and the feedback bar then always end at the top of the keyboard.
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (running) confirmExit = true else onClose() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close lesson")
                        }
                    },
                    title = {
                        when (val s = state) {
                            is LessonUiState.Running -> LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                            )
                            is LessonUiState.Finished -> Text(s.lesson.title)
                            is LessonUiState.Intro -> Text(s.unit.title, style = MaterialTheme.typography.titleMedium)
                            else -> Text("")
                        }
                    },
                )
            },
            bottomBar = {
                val s = state
                when {
                    s is LessonUiState.Running && s.feedback != null ->
                        FeedbackPanel(s.feedback, onContinue = vm::next, onOverride = vm::overrideCorrect)
                    s is LessonUiState.Running -> PrimaryActionBar(primaryAction)
                    s is LessonUiState.Intro -> StartBar(
                        resumeStep = s.resumeStep,
                        onStart = { vm.start() },
                        onResume = { vm.start(resume = true) },
                    )
                    else -> Unit
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    LessonUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is LessonUiState.Error -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(s.message, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onClose) { Text("Back") }
                    }
                    is LessonUiState.Intro -> IntroView(s)
                    is LessonUiState.Running -> RunningLesson(s, vm, ruleFor)
                    is LessonUiState.Finished -> SummaryView(
                        s,
                        onClose = onClose,
                        onNext = { u, l -> onOpenLesson(u, l) },
                        onReview = onReview,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun RunningLesson(s: LessonUiState.Running, vm: LessonViewModel, ruleFor: (com.gleaped.italianlearning.content.GrammarNote) -> RuleLink?) {
    val actions = remember(vm, ruleFor) {
        ExerciseActions(
            answer = vm::answer,
            continueWith = vm::recordAndContinue,
            flashcardSeen = vm::onFlashcardSeen,
            ruleFor = ruleFor,
        )
    }
    key(s.step) {
        val scroll = rememberScrollState()
        LaunchedEffect(s.feedback != null) {
            if (s.feedback != null) scroll.animateScrollTo(scroll.maxValue)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = ReadableWidth),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val earlierRules = remember(s.lesson, s.index, s.inRetry) {
                (if (s.inRetry) s.lesson.exercises else s.lesson.exercises.take(s.index)).filterIsInstance<GrammarNote>()
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (s.inRetry) {
                        "Riproviamo  ·  ${s.retryLeft} to get right"
                    } else {
                        listOfNotNull(stageLabel(s.exercise.stage), "${s.index + 1} of ${s.lesson.exercises.size}").joinToString("  ·  ")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (earlierRules.isNotEmpty() && s.exercise !is GrammarNote) {
                    var showRules by remember { mutableStateOf(false) }
                    TextButton(onClick = { showRules = true }) { Text("Rivedi la regola") }
                    if (showRules) RuleRecallSheet(earlierRules, onDismiss = { showRules = false })
                }
            }
            s.exercise.instruction?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            ExerciseView(
                exercise = s.exercise,
                answered = s.feedback != null,
                autoPlay = s.autoPlay,
                speechEnabled = s.speechEnabled,
                actions = actions,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Verdict, answer and why; at most about 40% of the screen (it scrolls inside beyond that). */
@Composable
private fun FeedbackPanel(feedback: Feedback, onContinue: () -> Unit, onOverride: () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    Surface(color = verdictColor(feedback.verdict), tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) { FeedbackBody(feedback) }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
            if (feedback.canOverride) {
                TextButton(
                    onClick = onOverride,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("My answer was right") }
            }
        }
    }
}

/** Widest text column in lessons: about 60 characters per line on tablets and in landscape. */
val ReadableWidth = 640.dp

fun stageLabel(stage: String?): String? = when (stage?.lowercase()) {
    "warmup" -> "Riscaldamento"
    "learn" -> "La regola"
    "practise", "practice" -> "Pratica"
    "use" -> "Usalo"
    else -> null
}

@Composable
private fun StartBar(resumeStep: Int?, onStart: () -> Unit, onResume: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (resumeStep != null) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Continua dal passo $resumeStep", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start again") }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Cominciamo", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Intro screen: lesson title, the goal in one line, how long it takes. */
@Composable
private fun IntroView(s: LessonUiState.Intro) {
    val lesson = s.lesson
    val steps = lesson.exercises.size
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .widthIn(max = ReadableWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
        val goal = lesson.goal.ifBlank { s.unit.objective }
        if (goal.isNotBlank()) {
            Text(goal, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "About ${lesson.minutes} minutes · $steps steps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val stages = lesson.exercises.mapNotNull { stageLabel(it.stage) }.distinct()
        if (stages.size > 1) {
            Text(stages.joinToString("  →  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The lesson's rule cards so far, in a sheet over the current exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleRecallSheet(notes: List<GrammarNote>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            notes.forEach { note ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleLarge)
                    RichText(note.body)
                    note.examples.forEach { ex ->
                        ItalianText(ex.it, style = MaterialTheme.typography.bodyLarge)
                        if (ex.en.isNotBlank()) Text(ex.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Verdict, note, expected answer and explanation; shared by lessons and challenges. */
@Composable
fun FeedbackBody(feedback: Feedback) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(verdictTitle(feedback.verdict), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        feedback.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val showExpected = feedback.expected != null &&
            (feedback.verdict != com.gleaped.italianlearning.domain.grading.Verdict.CORRECT || feedback.expectedLabel != "Answer")
        if (showExpected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(feedback.expectedLabel, style = MaterialTheme.typography.labelMedium)
                    LookupText(feedback.expected.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                SpeakerButton(feedback.expected.orEmpty())
            }
        }
        feedback.explanation?.takeIf { it.isNotBlank() }?.let {
            LookupText(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryView(
    s: LessonUiState.Finished,
    onClose: () -> Unit,
    onNext: (String, String) -> Unit,
    onReview: () -> Unit,
) {
    val sum = s.summary
    val total = sum.correct + sum.almost + sum.wrong
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (s.practice) "Fatto!" else "Lezione completata!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(s.lesson.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (s.lesson.takeaway.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Da ricordare", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LookupText(inlineMarkup(s.lesson.takeaway, highlightColor()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (total > 0) {
            Text("${sum.correct} correct · ${sum.almost} almost · ${sum.wrong} to revisit", style = MaterialTheme.typography.bodyLarge)
            val pct = (sum.correct + sum.almost * 0.5) / total
            Text(
                when {
                    pct >= 0.9 -> "Bravissimo! That was excellent."
                    pct >= 0.7 -> "Molto bene! Solid work."
                    pct >= 0.5 -> "Bene! A little review will make it stick."
                    s.practice -> "Good effort. These will come back tomorrow for another go."
                    else -> "Good effort. The items you missed will come back in Punti deboli on the Today screen."
                },
                textAlign = TextAlign.Center,
            )
        }
        if (sum.skipped > 0) {
            Text("${sum.skipped} skipped", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            when {
                s.practice -> "Items you got right come back after a longer gap; three in a row and they are done."
                sum.wrong > 0 -> "New words from this unit are now in your review deck. The items you missed come back tomorrow in Punti deboli."
                else -> "New words from this unit are now in your review deck."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        if (sum.nextUnitId != null && sum.nextLessonId != null) {
            Button(onClick = { onNext(sum.nextUnitId, sum.nextLessonId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Next: ${sum.nextLessonTitle ?: "lesson"}")
            }
        }
        if (!s.practice) {
            OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review cards") }
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
