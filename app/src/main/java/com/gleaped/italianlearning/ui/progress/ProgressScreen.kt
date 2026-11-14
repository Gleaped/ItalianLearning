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
import com.gleaped.italianlearning.domain.progress.SkillScore
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
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressUiState?> = container.learning.attempts.map { attempts ->
        val weak = SkillStats.weakestTopics(attempts).map { topic ->
            // Only the few units listed are parsed.
            val unit = runCatching { container.content.unit(topic.unitId) }.getOrNull()
            WeakTopicRow(topic, unit?.title ?: topic.unitId, unit?.grammarFocus.orEmpty())
        }
        ProgressUiState(
            skills = SkillStats.bySkill(attempts, LocalDate.now().toEpochDay()),
            weak = weak,
            totalAnswers = attempts.size,
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
fun ProgressScreen(onBack: () -> Unit, onOpenUnit: (String) -> Unit, onOpenWriting: () -> Unit) {
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
