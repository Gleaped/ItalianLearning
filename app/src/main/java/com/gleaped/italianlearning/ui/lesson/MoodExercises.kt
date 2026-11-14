package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.content.ChooseMood
import com.gleaped.italianlearning.content.FindTrigger
import com.gleaped.italianlearning.content.MinimalPair
import com.gleaped.italianlearning.content.SortBuckets
import com.gleaped.italianlearning.content.Transform
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.SpeakerButton

// ---------------------------------------------------------------- choose_mood

/** Step 1: tap the mood. Step 2 (when the exercise lists forms): type the verb. */
@Composable
fun ChooseMoodView(e: ChooseMood, answered: Boolean, actions: ExerciseActions) {
    var mood by remember(e) { mutableIntStateOf(-1) }
    var form by remember(e) { mutableStateOf("") }
    val needsForm = e.forms.isNotEmpty()
    val check = {
        val r = Grader.gradeChooseMood(mood, e.answer, if (needsForm) form else null, e.forms)
        val rightMood = e.options.getOrElse(e.answer) { "" }
        val expected = if (needsForm) e.sentence.replace("___", e.forms.first()) else rightMood
        val note = buildList {
            if (mood != e.answer) add("The mood here is the $rightMood.")
            e.trigger?.takeIf { it.isNotBlank() }?.let { add("Trigger: $it") }
            r.note?.let { add(it) }
        }.joinToString("\n").ifBlank { null }
        actions.answer(Feedback(r.verdict, expected = expected, note = note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LookupText(e.sentence.replace("___", "_____"), style = MaterialTheme.typography.titleLarge)
        if (e.verb.isNotBlank()) Hint(e.verb)
        Text("Which mood?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            e.options.forEachIndexed { i, option ->
                Column(Modifier.weight(1f)) {
                    OptionButton(
                        text = option,
                        selected = mood == i,
                        answered = answered,
                        isCorrect = i == e.answer,
                        onClick = { mood = i },
                    )
                }
            }
        }
        if (needsForm && mood >= 0) {
            AnswerField(form, { form = it }, enabled = !answered, onDone = check, label = "The verb form")
        }
        CheckButton(enabled = !answered && mood >= 0 && (!needsForm || form.isNotBlank()), onClick = check)
    }
}

// ---------------------------------------------------------------- find_trigger

/** The sentence as tappable words; tap the one that triggers the mood. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindTriggerView(e: FindTrigger, answered: Boolean, actions: ExerciseActions) {
    val tokens = remember(e) { Grader.triggerTokens(e.sentence) }
    val span = remember(e) { Grader.triggerSpan(e.sentence, e.trigger) }
    var picked by remember(e) { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Tap the word that triggers the mood.", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tokens.forEachIndexed { i, token ->
                val inTrigger = span != null && i in span
                val container = when {
                    answered && inTrigger -> verdictColor(Verdict.CORRECT)
                    answered && picked == i -> verdictColor(Verdict.WRONG)
                    picked == i -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
                OutlinedButton(
                    onClick = { picked = i },
                    enabled = !answered,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = container,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = container,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, if (picked == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    // A mark as well as the colour, so the result does not depend on telling green from red.
                    val mark = when {
                        answered && inTrigger -> " ✓"
                        answered && picked == i -> " ✗"
                        else -> ""
                    }
                    Text(token + mark, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                }
            }
        }
        CheckButton(enabled = !answered && picked >= 0, onClick = {
            val r = Grader.gradeFindTrigger(picked, e.sentence, e.trigger)
            actions.answer(Feedback(r.verdict, expected = e.trigger, expectedLabel = "Trigger"))
        })
    }
}

// ---------------------------------------------------------------- transform

@Composable
fun TransformView(e: Transform, answered: Boolean, actions: ExerciseActions) {
    var input by remember(e) { mutableStateOf("") }
    val check = {
        val r = Grader.gradeTyped(input, e.answers, allowSlip = true)
        actions.answer(Feedback(r.verdict, expected = r.expected, note = r.note))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
                ItalianText(e.source, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text("Change it: ${e.cue}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        e.hint?.let { Hint(it) }
        AnswerField(input, { input = it }, enabled = !answered, onDone = check, label = "New sentence", singleLine = false, minLines = 2)
        CheckButton(enabled = !answered && input.isNotBlank(), onClick = check)
    }
}

// ---------------------------------------------------------------- sort_buckets

/** Each item gets a row of bucket chips; tap one per item. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortBucketsView(e: SortBuckets, answered: Boolean, actions: ExerciseActions) {
    val picks = remember(e) { mutableStateListOf(*Array(e.items.size) { -1 }) }
    var results by remember(e) { mutableStateOf<List<Boolean>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Put each one in the right group.", style = MaterialTheme.typography.labelLarge)
        e.items.forEachIndexed { i, item ->
            val ok = results?.getOrNull(i)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LookupText(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (ok != null) Text(if (ok) "✓" else "✗", style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.buckets.forEachIndexed { b, bucket ->
                            val isAnswer = answered && b == item.bucket
                            FilterChip(
                                selected = picks[i] == b || isAnswer,
                                onClick = { if (!answered) picks[i] = b },
                                label = { Text(bucket) },
                                colors = if (isAnswer) {
                                    FilterChipDefaults.filterChipColors(selectedContainerColor = verdictColor(Verdict.CORRECT))
                                } else if (answered && picks[i] == b) {
                                    FilterChipDefaults.filterChipColors(selectedContainerColor = verdictColor(Verdict.WRONG))
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                },
                                modifier = Modifier.heightIn(min = 40.dp),
                            )
                        }
                    }
                }
            }
        }
        CheckButton(enabled = !answered && picks.none { it < 0 }, onClick = {
            val (verdict, correct) = Grader.gradeSortBuckets(picks.toList(), e.items.map { it.bucket })
            results = correct
            actions.answer(Feedback(verdict, note = "${correct.count { it }} of ${correct.size} in the right group"))
        })
    }
}

// ---------------------------------------------------------------- minimal_pair

/** Two or three near-identical sentences; pick the one that answers the question. */
@Composable
fun MinimalPairView(e: MinimalPair, answered: Boolean, actions: ExerciseActions) {
    var picked by remember(e) { mutableIntStateOf(-1) }
    val order = remember(e) { shuffledOrder(e.sentences.size) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookupText(e.question, style = MaterialTheme.typography.titleLarge)
        order.forEach { i ->
            val sentence = e.sentences[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    OptionButton(
                        text = sentence,
                        selected = picked == i,
                        answered = answered,
                        isCorrect = i == e.answer,
                        onClick = {
                            picked = i
                            val r = Grader.gradeChoice(i, e.answer, e.sentences)
                            actions.answer(Feedback(r.verdict, expected = r.expected))
                        },
                    )
                    if (answered) {
                        e.translations.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                    }
                }
                SpeakerButton(sentence)
            }
        }
    }
}
