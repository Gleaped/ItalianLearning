package com.gleaped.italianlearning.ui.practice

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.practice.TenseSet
import com.gleaped.italianlearning.domain.practice.VerbDrill
import com.gleaped.italianlearning.domain.practice.VerbQuestion
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.SpeakerButton
import com.gleaped.italianlearning.ui.lesson.AnswerField
import kotlinx.coroutines.launch

private enum class Phase { SETUP, LOADING, ASKING, SUMMARY }

/**
 * "Coniuga": an endless verb drill built from the dictionary's forms tables. Ten forms a round
 * from the commonest verbs; the ones missed are asked once more at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerbDrillScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.SETUP) }
    var set by remember { mutableStateOf(TenseSet.EVERYDAY) }
    var verbs by remember { mutableStateOf<List<com.gleaped.italianlearning.domain.dictionary.LemmaRow>>(emptyList()) }
    var questions by remember { mutableStateOf<List<VerbQuestion>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var missed by remember { mutableStateOf<List<VerbQuestion>>(emptyList()) }
    var retried by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var noDictionary by remember { mutableStateOf(false) }

    fun startRound() {
        phase = Phase.LOADING
        scope.launch {
            if (verbs.isEmpty()) verbs = container.dictionary.verbs(VerbDrill.MAX_RANK, VerbDrill.POOL)
            val round = VerbDrill.round(verbs, set)
            if (round.isEmpty()) {
                noDictionary = true
                phase = Phase.SETUP
            } else {
                noDictionary = false
                questions = round
                index = 0
                typed = ""
                verdict = null
                missed = emptyList()
                retried = false
                correct = 0
                answered = 0
                phase = Phase.ASKING
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.SUMMARY && answered > 0) container.learning.recordExtraPractice(answered)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coniuga") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (phase) {
                Phase.SETUP -> {
                    Text("Ten forms, any verb", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "You get an infinitive, a tense and a person, and type the form. The verbs are the commonest in Italian, " +
                            "so the drill never runs out. A missing accent counts as almost.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (noDictionary) {
                        Text(
                            "The offline dictionary is not available, so there are no verbs to practise.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TenseSet.entries.forEach { option ->
                        Card(onClick = { set = option }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.titleMedium)
                                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (set == option) Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Button(onClick = { startRound() }, modifier = Modifier.fillMaxWidth()) { Text("Start") }
                }

                Phase.LOADING -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                Phase.ASKING -> {
                    val q = questions[index.coerceIn(0, questions.size - 1)]
                    LinearProgressIndicator(progress = { index.toFloat() / questions.size }, modifier = Modifier.fillMaxWidth())
                    Text(if (retried && index >= questions.size - missed.size) "Riproviamo" else "${index + 1} of ${questions.size}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(q.infinitive, style = MaterialTheme.typography.headlineMedium)
                    Text(q.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${q.tense}  ·  ${q.person}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    val check = {
                        if (verdict == null && typed.isNotBlank()) {
                            val v = VerbDrill.grade(typed, q)
                            verdict = v
                            answered++
                            if (v == Verdict.CORRECT) correct++
                            if (v == Verdict.WRONG && !retried) missed = missed + q
                        }
                    }
                    AnswerField(
                        value = typed,
                        onValueChange = { typed = it },
                        enabled = verdict == null,
                        onDone = check,
                        label = "${q.person} …",
                    )
                    val v = verdict
                    if (v == null) {
                        Button(onClick = check, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Check") }
                    } else {
                        Text(
                            when (v) {
                                Verdict.CORRECT -> "Giusto!"
                                Verdict.ALMOST -> "Quasi: check the accent."
                                Verdict.WRONG -> "Not quite."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (v == Verdict.WRONG) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${q.person} ${q.form}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            SpeakerButton(q.form.substringBefore('/').trim())
                        }
                        Button(
                            onClick = {
                                typed = ""
                                verdict = null
                                if (index + 1 < questions.size) {
                                    index++
                                } else if (!retried && missed.isNotEmpty()) {
                                    // The ones missed come round once more.
                                    retried = true
                                    questions = questions + missed
                                    index++
                                } else {
                                    phase = Phase.SUMMARY
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Continue") }
                    }
                }

                Phase.SUMMARY -> {
                    Text("Round finished", style = MaterialTheme.typography.titleLarge)
                    Text("$correct of $answered right first time.", style = MaterialTheme.typography.bodyLarge)
                    if (missed.isNotEmpty()) {
                        Text("Worth another look:", style = MaterialTheme.typography.titleSmall)
                        missed.forEach { Text("${it.infinitive}: ${it.tense}, ${it.person} ${it.form}", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Button(onClick = { startRound() }, modifier = Modifier.fillMaxWidth()) { Text("Another round") }
                    OutlinedButton(onClick = { phase = Phase.SETUP }, modifier = Modifier.fillMaxWidth()) { Text("Change the tenses") }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}
