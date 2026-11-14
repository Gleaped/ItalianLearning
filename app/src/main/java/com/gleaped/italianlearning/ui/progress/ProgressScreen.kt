package com.gleaped.italianlearning.ui.progress

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.domain.oral.OralCriterion
import com.gleaped.italianlearning.domain.oral.OralLog
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import com.gleaped.italianlearning.domain.progress.Readiness
import com.gleaped.italianlearning.domain.progress.SkillScore
import com.gleaped.italianlearning.domain.progress.Standing
import com.gleaped.italianlearning.domain.progress.SkillStats
import com.gleaped.italianlearning.domain.progress.WeakTopic
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** A weak topic with the unit's title and grammar focus for display. */
data class WeakTopicRow(val topic: WeakTopic, val title: String, val focus: String)

data class ProgressUiState(
    val skills: List<SkillScore>,
    val weak: List<WeakTopicRow>,
    val totalAnswers: Int,
    val readiness: Readiness,
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressUiState?> = container.learning.attempts.map { attempts ->
        val weak = SkillStats.weakestTopics(attempts).map { topic ->
            // Only the few units listed are parsed.
            val unit = runCatching { container.content.unit(topic.unitId) }.getOrNull()
            WeakTopicRow(topic, unit?.title ?: topic.unitId, unit?.grammarFocus.orEmpty())
        }
        val skills = SkillStats.bySkill(attempts, LocalDate.now().toEpochDay())
        ProgressUiState(
            skills = skills,
            weak = weak,
            totalAnswers = attempts.size,
            readiness = ExamReadiness.estimate(skills),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProgressViewModel(container) }
        }
    }
}

/** Accuracy by skill and the weakest grammar topics, from every answer given in lessons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onBack: () -> Unit, onOpenUnit: (String) -> Unit, onOpenWriting: () -> Unit, onOpenOral: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm: ProgressViewModel = viewModel(factory = ProgressViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("I tuoi progressi") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
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
            Text("By skill", style = MaterialTheme.typography.titleLarge)
            Text(
                if (s.totalAnswers == 0) {
                    "Nothing to show yet. Finish a lesson and your answers will be counted here."
                } else {
                    "How often your answers were right, from ${s.totalAnswers} answers in lessons. " +
                        "An \"almost\" counts half. Recent means the last ${SkillStats.RECENT_DAYS} days."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.skills.forEach { SkillRow(it) }
            Text(
                "Writing and speaking are scored from your own checklist and the speech recogniser, so read them as a rough guide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("CELI 3 estimate by part", style = MaterialTheme.typography.titleLarge)
            ReadinessTable(s.readiness)

            Text("Weakest grammar topics", style = MaterialTheme.typography.titleLarge)
            if (s.weak.isEmpty()) {
                Text(
                    "No weak topics right now. A unit appears here when its recent grammar answers drop below ${SkillStats.WEAK_BELOW_PERCENT}%.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.weak.forEach { row ->
                Card(onClick = { onOpenUnit(row.topic.unitId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${row.topic.percent}%", style = MaterialTheme.typography.titleMedium)
                        }
                        if (row.focus.isNotBlank()) {
                            Text(
                                row.focus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("Last ${row.topic.answers} answers. Tap to open the unit.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Speaking practice", style = MaterialTheme.typography.titleLarge)
            OralSummary()
            OutlinedButton(onClick = onOpenOral, modifier = Modifier.fillMaxWidth()) { Text("Prova orale: record and score yourself") }

            Text("Writing", style = MaterialTheme.typography.titleLarge)
            Text(
                "Every text you finish in a writing exercise is kept, with the model answer and your own scores.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenWriting, modifier = Modifier.fillMaxWidth()) { Text("I miei testi") }
        }
    }
}

@Composable
private fun SkillRow(score: SkillScore) {
    val overall = score.overall.percent
    val recent = score.recent.percent
    val summary = when {
        overall == null -> "Not enough answers yet (${score.overall.answers})"
        recent == null -> "$overall% of ${score.overall.answers} answers"
        else -> "$overall% of ${score.overall.answers} answers · recent $recent%"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(score.skill.italian, style = MaterialTheme.typography.titleMedium)
                Text(score.skill.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(overall?.let { "$it%" } ?: "–", style = MaterialTheme.typography.headlineSmall)
        }
        LinearProgressIndicator(
            progress = { (overall ?: 0) / 100f },
            // The figures are in the text; the bar only repeats them.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The estimated points per exam part with the pass marks, as a short table. */
@Composable
private fun ReadinessTable(r: Readiness) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Your lesson accuracy turned into points on the exam's own scale. Lesson answers are easier than exam tasks, " +
                "and writing and speaking depend on your own scoring, so read this as a guide, not a forecast.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        r.parts.forEach { est ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${est.part.italian}: ${est.part.english}", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    est.points?.let { "$it / ${est.part.max}" } ?: "not enough answers",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        val rows = listOf(
            Triple("Written total", r.written, ExamReadiness.WRITTEN_MAX to ExamReadiness.WRITTEN_PASS),
            Triple("Oral total", r.oral, ExamReadiness.ORAL_MAX to ExamReadiness.ORAL_PASS),
            Triple("Overall", r.overall, 200 to ExamReadiness.OVERALL_PASS),
        )
        rows.forEach { (label, points, scale) ->
            val (max, pass) = scale
            val standing = points?.let { ExamReadiness.standing(it, pass, max) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text("Pass mark $pass of $max", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(points?.let { "$it / $max" } ?: "–", style = MaterialTheme.typography.titleMedium)
                    standing?.let {
                        Text(
                            when (it) {
                                Standing.ABOVE -> "above"
                                Standing.NEAR -> "close"
                                Standing.BELOW -> "below"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Your own scores from the oral practice, averaged over the latest answers. */
@Composable
private fun OralSummary() {
    val container = LocalAppContainer.current
    val entries by container.oral.entries.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { container.oral.load() }
    val averages = OralLog.averages(entries)
    if (averages.isEmpty()) {
        Text(
            "Record yourself answering an exam-style task, listen back and score yourself. Your averages appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Your own scores over the last ${minOf(entries.size, 10)} answers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OralCriterion.entries.forEach { c ->
            averages[c]?.let { Text("${c.italian}: ${"%.1f".format(it)} of ${OralCriterion.MAX_SCORE}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
