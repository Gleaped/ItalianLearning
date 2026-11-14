package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.R
import com.gleaped.italianlearning.content.ChooseMood
import com.gleaped.italianlearning.content.Conjugation
import com.gleaped.italianlearning.content.FindTrigger
import com.gleaped.italianlearning.content.MinimalPair
import com.gleaped.italianlearning.content.SortBuckets
import com.gleaped.italianlearning.content.Transform
import com.gleaped.italianlearning.content.ErrorCorrection
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.Flashcard
import com.gleaped.italianlearning.content.FreeWrite
import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.GrammarNote
import com.gleaped.italianlearning.content.Invalid
import com.gleaped.italianlearning.content.ListenChoose
import com.gleaped.italianlearning.content.ListenTranscribe
import com.gleaped.italianlearning.content.MatchPairs
import com.gleaped.italianlearning.content.MultipleChoice
import com.gleaped.italianlearning.content.Reading
import com.gleaped.italianlearning.content.Roleplay
import com.gleaped.italianlearning.content.SentenceBuild
import com.gleaped.italianlearning.content.SpeakRepeat
import com.gleaped.italianlearning.content.SpeakRespond
import com.gleaped.italianlearning.content.Translate
import com.gleaped.italianlearning.content.Unsupported
import com.gleaped.italianlearning.domain.grading.AnswerNormalizer
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.speech.SpeakableText
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.speech.SpeechCapture
import com.gleaped.italianlearning.speech.TtsStatus
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.RatingChoices
import com.gleaped.italianlearning.ui.components.RichText
import com.gleaped.italianlearning.ui.components.SpeakerButton
import kotlin.math.roundToInt

/** A link from a grammar note to its page in the grammar reference. */
class RuleLink(val title: String, val open: () -> Unit)

/** Callbacks from an exercise to the lesson player. */
class ExerciseActions(
    val answer: (Feedback) -> Unit,
    val continueWith: (Verdict?) -> Unit,
    val flashcardSeen: (Flashcard) -> Unit,
    /** The reference topic for a grammar note, when one lists this unit. */
    val ruleFor: (GrammarNote) -> RuleLink? = { null },
)

@Composable
fun ExerciseView(
    exercise: Exercise,
    answered: Boolean,
    autoPlay: Boolean,
    speechEnabled: Boolean,
    actions: ExerciseActions,
) {
    when (exercise) {
        is GrammarNote -> GrammarNoteView(exercise, actions.ruleFor(exercise)) { actions.continueWith(null) }
        is Flashcard -> FlashcardView(exercise, actions)
        is MultipleChoice -> ChoiceView(exercise.prompt, null, exercise.options, exercise.answer, answered, actions)
        is GapFill -> GapFillView(exercise, answered, actions)
        is Conjugation -> ConjugationView(exercise, answered, actions)
        is SentenceBuild -> SentenceBuildView(exercise, answered, actions)
        is Translate -> TranslateView(exercise, answered, actions)
        is MatchPairs -> MatchPairsView(exercise, answered, actions)
        is ErrorCorrection -> ErrorCorrectionView(exercise, answered, actions)
        is ListenTranscribe -> ListenTranscribeView(exercise, answered, autoPlay, actions)
        is ListenChoose -> ChoiceView(exercise.question, exercise, exercise.options, exercise.answer, answered, actions, autoPlay)
        is Reading -> ReadingView(exercise, answered, actions)
        is SpeakRepeat -> SpeakRepeatView(exercise, answered, speechEnabled, actions)
        is SpeakRespond -> SpeakRespondView(exercise, answered, speechEnabled, actions)
        is Roleplay -> RoleplayView(exercise, answered, autoPlay, speechEnabled, actions)
        is FreeWrite -> FreeWriteView(exercise, answered, actions)
        is ChooseMood -> ChooseMoodView(exercise, answered, actions)
        is FindTrigger -> FindTriggerView(exercise, answered, actions)
        is Transform -> TransformView(exercise, answered, actions)
        is SortBuckets -> SortBucketsView(exercise, answered, actions)
        is MinimalPair -> MinimalPairView(exercise, answered, actions)
        is Unsupported -> UnavailableView("This exercise type (\"${exercise.type}\") needs a newer version of the app.") { actions.continueWith(null) }
        is Invalid -> UnavailableView("This exercise could not be loaded.") { actions.continueWith(null) }
    }
}

@Composable
private fun UnavailableView(message: String, onSkip: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        CheckButton(enabled = true, onClick = onSkip, label = "Skip")
    }
}

// ---------------------------------------------------------------- grammar_note

@Composable
private fun GrammarNoteView(e: GrammarNote, rule: RuleLink?, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (e.title.isNotBlank()) Text(e.title, style = MaterialTheme.typography.headlineSmall)
        RichText(e.body)
        if (e.examples.isNotEmpty()) {
            SectionLabel("Esempi")
            e.examples.forEach { ex ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                        ItalianText(ex.it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        if (ex.en.isNotBlank()) {
                            Text(ex.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (rule != null) {
            TextButton(onClick = rule.open, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Vedi la regola: ${rule.title}", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        CheckButton(enabled = true, onClick = onContinue, label = "Continue")
    }
}

// ---------------------------------------------------------------- flashcard

@Composable
private fun FlashcardView(e: Flashcard, actions: ExerciseActions) {
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(e) { actions.flashcardSeen(e) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = { flipped = !flipped },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LookupText(e.front, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    SpeakerButton(SpeakableText.forCard(e.front))
                }
                if (flipped) {
                    HorizontalDivider()
                    Text(e.back, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    e.example?.let { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LookupText(ex, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                            SpeakerButton(ex)
                        }
                    }
                } else {
                    Text("Tap to flip", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (flipped) {
            RatingChoices(onRate = { actions.continueWith(ratingVerdict(it)) })
            Text(
                "This card is now in your review deck.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CheckButton(enabled = true, onClick = { flipped = true }, label = "Show answer")
        }
    }
}

private fun ratingVerdict(r: Rating): Verdict = when (r) {
    Rating.AGAIN -> Verdict.WRONG
    Rating.HARD -> Verdict.ALMOST
    Rating.GOOD, Rating.EASY -> Verdict.CORRECT
}

// ---------------------------------------------------------------- multiple_choice / listen_choose

@Composable
private fun ChoiceView(
    prompt: String,
    listen: ListenChoose?,
    options: List<String>,
    answer: Int,
    answered: Boolean,
    actions: ExerciseActions,
    autoPlay: Boolean = false,
) {
    var selected by remember { mutableIntStateOf(-1) }
    // Shown in a fresh random order each time, so the position of the right answer says nothing.
    val order = remember(options) { shuffledOrder(options.size) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listen != null) {
            ListenControls(listen.audioText, autoPlay = autoPlay, revealAfterAnswer = answered)
        }
        if (prompt.isNotBlank()) {
            LookupText(prompt, style = MaterialTheme.typography.titleLarge)
        }
        order.forEach { i ->
            OptionButton(
                text = options[i],
                selected = selected == i,
                answered = answered,
                isCorrect = i == answer,
                onClick = {
                    selected = i
                    val r = Grader.gradeChoice(i, answer, options)
                    actions.answer(Feedback(r.verdict, expected = r.expected))
                },
            )
        }
    }
}

/** Display order for [size] answer options: indexes into the original list, shuffled. */
internal fun shuffledOrder(size: Int): List<Int> = (0 until size).shuffled()

// ---------------------------------------------------------------- listening helpers

@Composable
private fun ListenControls(audioText: String, autoPlay: Boolean, revealAfterAnswer: Boolean, slowByDefault: Boolean = false) {
    val tts = LocalAppContainer.current.tts
    val status by tts.status.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(audioText, status) {
        if (autoPlay && status == TtsStatus.READY) tts.speak(audioText, slowByDefault)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { tts.speak(audioText, slowByDefault) }) {
                Icon(painterResource(R.drawable.ic_volume), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play")
            }
            OutlinedButton(onClick = { tts.speak(audioText, slow = true) }) {
                Icon(painterResource(R.drawable.ic_slow), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Slowly")
            }
        }
        when (status) {
            TtsStatus.NO_ITALIAN_VOICE, TtsStatus.UNAVAILABLE -> {
                Text(
                    "Italian text-to-speech isn't available. Install the Italian voice in Android settings " +
                        "(Text-to-speech output), or read the text instead.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!revealed) TextButton(onClick = { revealed = true }) { Text("Show text") }
            }
            else -> Unit
        }
        if (revealed || revealAfterAnswer) {
            LookupText(audioText, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- gap_fill

@Composable
private fun GapFillView(e: GapFill, answered: Boolean, actions: ExerciseActions) {
    var input by remember { mutableStateOf("") }
    val check = {
        val r = Grader.gradeTyped(input, e.answers)
        actions.answer(Feedback(r.verdict, expected = e.sentence.replace("___", r.expected), note = r.note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookupText(e.sentence.replace("___", "_____"), style = MaterialTheme.typography.titleLarge)
        e.hint?.let { Hint(it) }
        AnswerField(input, { input = it }, enabled = !answered, onDone = check)
        CheckButton(enabled = !answered && input.isNotBlank(), onClick = check)
    }
}

// ---------------------------------------------------------------- conjugation

@Composable
private fun ConjugationView(e: Conjugation, answered: Boolean, actions: ExerciseActions) {
    val inputs = remember { mutableStateListOf(*Array(e.items.size) { "" }) }
    val results = remember { mutableStateListOf<Verdict?>(*arrayOfNulls<Verdict>(e.items.size)) }
    val check = {
        val verdicts = e.items.mapIndexed { i, item -> Grader.gradeConjugation(inputs[i], item.answer).verdict }
        verdicts.forEachIndexed { i, v -> results[i] = v }
        val overall = Grader.combine(verdicts)
        val missed = e.items.filterIndexed { i, _ -> verdicts[i] != Verdict.CORRECT }
            .joinToString("\n") { "${it.person}: ${it.answer}" }
        actions.answer(
            Feedback(
                overall,
                expected = missed.ifBlank { null },
                note = if (verdicts.any { it == Verdict.ALMOST }) Grader.ACCENT_NOTE else null,
                expectedLabel = "Correct forms",
            ),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LookupText(e.verb, style = MaterialTheme.typography.headlineSmall)
        if (e.tense.isNotBlank()) Text(e.tense, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        e.items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.person, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge)
                val last = i == e.items.lastIndex
                AnswerField(
                    value = inputs[i],
                    onValueChange = { inputs[i] = it },
                    enabled = !answered,
                    onDone = { if (last && inputs.any { it.isNotBlank() }) check() },
                    label = "",
                    modifier = Modifier.weight(1f),
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                )
                val v = results[i]
                Text(
                    text = when (v) {
                        Verdict.CORRECT -> "✓"
                        Verdict.ALMOST -> "≈"
                        Verdict.WRONG -> "✗"
                        null -> ""
                    },
                    modifier = Modifier.width(28.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        CheckButton(enabled = !answered && inputs.any { it.isNotBlank() }, onClick = check)
    }
}

// ---------------------------------------------------------------- sentence_build

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceBuildView(e: SentenceBuild, answered: Boolean, actions: ExerciseActions) {
    val allTiles = remember(e) { e.tiles + e.distractors }
    val order = remember(e) { allTiles.indices.shuffled() }
    val chosen = remember(e) { mutableStateListOf<Int>() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (e.translation.isNotBlank()) Text(e.translation, style = MaterialTheme.typography.titleLarge)
        OutlinedCard(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            FlowRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chosen.toList().forEach { idx ->
                    Button(onClick = { chosen.remove(idx) }, enabled = !answered) { Text(allTiles[idx]) }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            order.filter { it !in chosen }.forEach { idx ->
                OutlinedButton(onClick = { chosen.add(idx) }, enabled = !answered) { Text(allTiles[idx]) }
            }
        }
        CheckButton(enabled = !answered && chosen.isNotEmpty(), onClick = {
            val r = Grader.gradeSentenceBuild(chosen.map { allTiles[it] }, e.tiles)
            actions.answer(Feedback(r.verdict, expected = r.expected))
        })
    }
}

// ---------------------------------------------------------------- translate

@Composable
private fun TranslateView(e: Translate, answered: Boolean, actions: ExerciseActions) {
    var input by remember { mutableStateOf("") }
    val check = {
        val r = Grader.gradeTyped(input, e.answers, allowSlip = true)
        actions.answer(Feedback(r.verdict, expected = r.expected, note = r.note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (e.fromItalian) "Translate into English" else "Translate into Italian",
            style = MaterialTheme.typography.labelLarge,
        )
        if (e.fromItalian) {
            ItalianText(e.source, style = MaterialTheme.typography.titleLarge)
        } else {
            Text(e.source, style = MaterialTheme.typography.titleLarge)
        }
        AnswerField(input, { input = it }, enabled = !answered, onDone = check, singleLine = false, minLines = 2, accentKeys = !e.fromItalian)
        CheckButton(enabled = !answered && input.isNotBlank(), onClick = check)
    }
}

// ---------------------------------------------------------------- match_pairs

@Composable
private fun MatchPairsView(e: MatchPairs, answered: Boolean, actions: ExerciseActions) {
    val rightOrder = remember(e) { e.pairs.indices.shuffled() }
    val matched = remember(e) { mutableStateListOf<Int>() }
    var selectedLeft by remember(e) { mutableIntStateOf(-1) }
    var wrongFlash by remember(e) { mutableIntStateOf(-1) }
    var mistakes by remember(e) { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tap an item on the left, then its match on the right.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.pairs.forEachIndexed { i, pair ->
                    OptionButton(
                        text = pair.left,
                        selected = selectedLeft == i,
                        answered = i in matched,
                        isCorrect = i in matched,
                        onClick = {
                            selectedLeft = i
                            wrongFlash = -1
                        },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rightOrder.forEach { j ->
                    OptionButton(
                        text = e.pairs[j].right,
                        selected = wrongFlash == j,
                        answered = j in matched || wrongFlash == j,
                        isCorrect = j in matched,
                        onClick = {
                            if (answered || selectedLeft < 0) return@OptionButton
                            if (selectedLeft == j) {
                                matched.add(j)
                                selectedLeft = -1
                                wrongFlash = -1
                                if (matched.size == e.pairs.size) {
                                    actions.answer(Feedback(Grader.gradeMatchPairs(mistakes), note = if (mistakes > 0) "$mistakes wrong attempt(s)" else null))
                                }
                            } else {
                                mistakes++
                                wrongFlash = j
                            }
                        },
                    )
                }
            }
        }
        if (wrongFlash >= 0) {
            TextButton(onClick = { wrongFlash = -1 }) { Text("Not a match — try again") }
        }
    }
}

// ---------------------------------------------------------------- error_correction

@Composable
private fun ErrorCorrectionView(e: ErrorCorrection, answered: Boolean, actions: ExerciseActions) {
    var input by remember { mutableStateOf(e.sentence) }
    val check = {
        val r = Grader.gradeTyped(input, e.answers)
        actions.answer(Feedback(r.verdict, expected = r.expected, note = r.note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Find and fix the mistake:", style = MaterialTheme.typography.labelLarge)
        LookupText(e.sentence, style = MaterialTheme.typography.titleLarge)
        AnswerField(input, { input = it }, enabled = !answered, onDone = check, label = "Corrected sentence", singleLine = false, minLines = 2)
        CheckButton(enabled = !answered && input.isNotBlank(), onClick = check)
    }
}

// ---------------------------------------------------------------- listen_transcribe

@Composable
private fun ListenTranscribeView(e: ListenTranscribe, answered: Boolean, autoPlay: Boolean, actions: ExerciseActions) {
    var input by remember { mutableStateOf("") }
    val check = {
        val r = Grader.gradeTyped(input, e.answers, allowSlip = true)
        actions.answer(Feedback(r.verdict, expected = r.expected, note = r.note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListenControls(e.audioText, autoPlay = autoPlay, revealAfterAnswer = false, slowByDefault = e.slow)
        AnswerField(input, { input = it }, enabled = !answered, onDone = check, label = "What did you hear?", singleLine = false, minLines = 2)
        CheckButton(enabled = !answered && input.isNotBlank(), onClick = check)
    }
}

// ---------------------------------------------------------------- reading

@Composable
private fun ReadingView(e: Reading, answered: Boolean, actions: ExerciseActions) {
    val picks = remember(e) { mutableStateListOf(*Array(e.questions.size) { -1 }) }
    val orders = remember(e) { e.questions.map { shuffledOrder(it.options.size) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (e.title.isNotBlank()) Text(e.title, style = MaterialTheme.typography.headlineSmall)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                RichText(e.passage)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpeakerButton(e.passage) }
            }
        }
        e.questions.forEachIndexed { qi, q ->
            LookupText(q.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            orders[qi].forEach { oi ->
                OptionButton(
                    text = q.options[oi],
                    selected = picks[qi] == oi,
                    answered = answered,
                    isCorrect = oi == q.answer,
                    onClick = { picks[qi] = oi },
                )
            }
        }
        if (e.questions.isEmpty()) {
            CheckButton(enabled = true, onClick = { actions.continueWith(null) }, label = "Continue")
        } else {
            CheckButton(enabled = !answered && picks.none { it < 0 }, onClick = {
                val verdicts = e.questions.mapIndexed { i, q -> if (picks[i] == q.answer) Verdict.CORRECT else Verdict.WRONG }
                val right = verdicts.count { it == Verdict.CORRECT }
                actions.answer(Feedback(Grader.combine(verdicts), note = "$right of ${verdicts.size} correct"))
            })
        }
    }
}

// ---------------------------------------------------------------- speaking

private fun heardNote(heard: String, percent: Int? = null): String =
    "We heard: “$heard”" + (percent?.let { " ($it% of the words)" } ?: "")

/** Shared mic / type-instead / skip block. Calls [onResponses] with the recogniser's alternatives or typed text. */
@Composable
private fun SpeakOrType(
    answered: Boolean,
    speechEnabled: Boolean,
    onResponses: (List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    var typing by remember { mutableStateOf(!speechEnabled) }
    var unavailableReason by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!typing) {
            SpeechCapture(
                enabled = !answered,
                onResult = onResponses,
                onUnavailable = { reason ->
                    unavailableReason = when (reason) {
                        com.gleaped.italianlearning.speech.SpeechUnavailable.PERMISSION_DENIED ->
                            "Microphone permission was not granted, so let's type instead. You can allow it later in Android settings."
                        com.gleaped.italianlearning.speech.SpeechUnavailable.NO_RECOGNIZER ->
                            "No speech recogniser is available on this device, so let's type instead."
                        com.gleaped.italianlearning.speech.SpeechUnavailable.LANGUAGE_UNAVAILABLE ->
                            "Italian speech recognition isn't installed, so let's type instead."
                    }
                    typing = true
                },
            )
        } else {
            unavailableReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            AnswerField(
                value = input,
                onValueChange = { input = it },
                enabled = !answered,
                onDone = { onResponses(listOf(input)) },
                label = "Type your answer in Italian",
                singleLine = false,
                minLines = 2,
            )
            CheckButton(enabled = !answered && input.isNotBlank(), onClick = { onResponses(listOf(input)) })
        }
        if (!answered) {
            SpeechFallbackRow(showTypeInstead = !typing, onTypeInstead = { typing = true }, onSkip = onSkip, enabled = true)
        }
    }
}

@Composable
private fun SpeakRepeatView(e: SpeakRepeat, answered: Boolean, speechEnabled: Boolean, actions: ExerciseActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Say it aloud:", style = MaterialTheme.typography.labelLarge)
        ItalianText(e.text, style = MaterialTheme.typography.headlineSmall)
        e.translation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        SpeakOrType(
            answered = answered,
            speechEnabled = speechEnabled,
            onResponses = { alternatives ->
                val (verdict, score) = Grader.gradeSpeakRepeat(alternatives, e.text)
                val best = alternatives.maxByOrNull { Grader.wordOverlap(it, e.text) }.orEmpty()
                actions.answer(Feedback(verdict, expected = e.text, note = heardNote(best, (score * 100).roundToInt())))
            },
            onSkip = { actions.continueWith(null) },
        )
    }
}

@Composable
private fun SpeakRespondView(e: SpeakRespond, answered: Boolean, speechEnabled: Boolean, actions: ExerciseActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ItalianText(e.prompt, style = MaterialTheme.typography.titleLarge)
        if (e.keywords.isNotEmpty()) {
            Text(
                "Try to use: " + e.keywords.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SpeakOrType(
            answered = answered,
            speechEnabled = speechEnabled,
            onResponses = { alternatives ->
                val (verdict, used) = Grader.gradeKeywords(alternatives, e.keywords)
                val heard = alternatives.firstOrNull().orEmpty()
                val note = heardNote(heard) +
                    if (e.keywords.isNotEmpty()) "\nKeywords used: ${used.size} of ${e.keywords.size}" else ""
                actions.answer(Feedback(verdict, expected = e.modelAnswer.ifBlank { null }, note = note, expectedLabel = "Model answer"))
            },
            onSkip = { actions.continueWith(null) },
        )
    }
}

// ---------------------------------------------------------------- roleplay

private data class RoleplayLine(val npc: Boolean, val text: String, val sub: String? = null)

@Composable
private fun RoleplayView(e: Roleplay, answered: Boolean, autoPlay: Boolean, speechEnabled: Boolean, actions: ExerciseActions) {
    val tts = LocalAppContainer.current.tts
    var turn by remember(e) { mutableIntStateOf(0) }
    val transcript = remember(e) { mutableStateListOf<RoleplayLine>() }
    val verdicts = remember(e) { mutableStateListOf<Verdict>() }
    var awaitingNext by remember(e) { mutableStateOf(false) }

    val current = e.turns.getOrNull(turn)
    LaunchedEffect(turn) {
        val t = e.turns.getOrNull(turn) ?: return@LaunchedEffect
        if (t.isNpc) {
            transcript.add(RoleplayLine(true, t.text))
            if (autoPlay) tts.speak(t.text)
            awaitingNext = true
        }
    }

    fun advance() {
        awaitingNext = false
        if (turn + 1 >= e.turns.size) {
            turn = e.turns.size
            val overall = Grader.combine(verdicts.toList())
            actions.answer(Feedback(overall, note = "Conversation finished."))
        } else {
            turn += 1
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (e.scenario.isNotBlank()) {
            Text(e.scenario, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
        transcript.forEach { line -> ChatBubble(line) }
        if (!answered && current != null) {
            if (current.isNpc) {
                if (awaitingNext) {
                    CheckButton(enabled = true, onClick = { advance() }, label = "Continue")
                }
            } else if (!awaitingNext) {
                Text("Your turn", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                current.hint?.let { Hint(it) }
                SpeakOrType(
                    answered = false,
                    speechEnabled = speechEnabled,
                    onResponses = { alternatives ->
                        val verdict = if (current.keywords.isNotEmpty()) {
                            Grader.gradeKeywords(alternatives, current.keywords).first
                        } else {
                            Grader.gradeSpeakRepeat(alternatives, current.text).first
                        }
                        verdicts.add(verdict)
                        transcript.add(RoleplayLine(false, alternatives.firstOrNull().orEmpty(), "Model: ${current.text}"))
                        awaitingNext = true
                    },
                    onSkip = {
                        transcript.add(RoleplayLine(false, "…", "Model: ${current.text}"))
                        awaitingNext = true
                    },
                )
            } else {
                CheckButton(enabled = true, onClick = { advance() }, label = "Continue")
            }
        }
    }
}

@Composable
private fun ChatBubble(line: RoleplayLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.npc) Arrangement.Start else Arrangement.End,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (line.npc) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LookupText(line.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (line.npc) SpeakerButton(line.text)
                }
                line.sub?.let { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        SpeakerButton(sub.removePrefix("Model: "))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- free_write

@Composable
private fun FreeWriteView(e: FreeWrite, answered: Boolean, actions: ExerciseActions) {
    var text by remember(e) { mutableStateOf("") }
    var selfCheck by remember(e) { mutableStateOf(false) }
    val checked = remember(e) { mutableStateListOf(*Array(e.checklist.size) { false }) }
    val words = AnswerNormalizer.wordCount(text)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(e.prompt, style = MaterialTheme.typography.titleLarge)
        AnswerField(
            value = text,
            onValueChange = { text = it },
            enabled = !selfCheck && !answered,
            onDone = {},
            label = "Scrivi qui",
            singleLine = false,
            minLines = 6,
        )
        Text(
            if (e.minWords > 0) "$words / ${e.minWords} words" else "$words words",
            style = MaterialTheme.typography.bodySmall,
            color = if (words >= e.minWords) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!selfCheck) {
            CheckButton(enabled = text.isNotBlank(), onClick = { selfCheck = true }, label = "I'm done — check my writing")
        } else {
            if (e.modelAnswer.isNotBlank()) {
                SectionLabel("Model answer")
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        LookupText(e.modelAnswer, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpeakerButton(e.modelAnswer) }
                    }
                }
            }
            if (e.checklist.isNotEmpty()) {
                SectionLabel("Self-check: tick what your text does")
                e.checklist.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it }, enabled = !answered)
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            CheckButton(enabled = !answered, label = "Finish", onClick = {
                val verdict = Grader.gradeFreeWrite(words, e.minWords, checked.count { it }, e.checklist.size)
                val note = if (words < e.minWords) "Aim for at least ${e.minWords} words next time." else null
                actions.answer(Feedback(verdict, note = note))
            })
        }
    }
}
