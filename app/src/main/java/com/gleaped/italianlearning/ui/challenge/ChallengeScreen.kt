package com.gleaped.italianlearning.ui.challenge

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.content.GrammarTip
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.progress.CourseProgress
import com.gleaped.italianlearning.domain.progress.UnitStatus
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.lesson.ExerciseActions
import com.gleaped.italianlearning.ui.lesson.ExerciseView
import com.gleaped.italianlearning.ui.lesson.Feedback
import com.gleaped.italianlearning.ui.lesson.FeedbackBody
import com.gleaped.italianlearning.ui.lesson.verdictColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ChallengeUiState {
    data object Loading : ChallengeUiState
    data object Missing : ChallengeUiState
    data class Ready(
        val unitTitle: String,
        val tip: GrammarTip,
        /** Set once the learner answered. */
        val feedback: Feedback?,
        /** Answered, skipped, or the tip has no challenge: show the rule and the exit buttons. */
        val done: Boolean,
        val autoPlay: Boolean,
        val speechEnabled: Boolean,
    ) : ChallengeUiState
}

/** One grammar tip's challenge, opened from a reminder or the unit page. */
class ChallengeViewModel(
    private val container: AppContainer,
    private val unitId: String,
    private val tipId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ChallengeUiState>(ChallengeUiState.Loading)
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Only this unit's file is parsed.
            val unit = container.content.unit(unitId)
            val tip = unit?.grammarTips?.firstOrNull { it.id == tipId }
            if (unit == null || tip == null) {
                _state.value = ChallengeUiState.Missing
                return@launch
            }
            val settings = container.settings.current()
            _state.value = ChallengeUiState.Ready(
                unitTitle = unit.title,
                tip = tip,
                feedback = null,
                done = tip.challenge == null,
                autoPlay = settings.autoPlayAudio,
                speechEnabled = settings.speechPracticeEnabled,
            )
            // Seen here counts as shown, so the next reminder picks another tip.
            container.settings.markTipShown(tip.id)
        }
    }

    fun answer(feedback: Feedback) {
        val s = _state.value as? ChallengeUiState.Ready ?: return
        if (s.feedback != null) return
        val explanation = feedback.explanation ?: s.tip.challenge?.explanation
        _state.value = s.copy(feedback = feedback.copy(explanation = explanation), done = true)
    }

    /** Exercises that finish without feedback (e.g. skip). */
    fun finishWithout(@Suppress("UNUSED_PARAMETER") verdict: Verdict?) {
        val s = _state.value as? ChallengeUiState.Ready ?: return
        _state.value = s.copy(done = true)
    }

    /**
     * Where "Continua la lezione" leads: the first unfinished lesson of this unit when it is
     * open, otherwise the next lesson on the path, otherwise the unit page (lessonId null).
     */
    fun continueLesson(onResult: (unitId: String, lessonId: String?) -> Unit) {
        viewModelScope.launch {
            val all = container.content.load()
            val completed = container.learning.completedLessonIds.first()
            val unlockAll = container.settings.current().unlockAllUnits
            val progress = CourseProgress(all.course, all.units, completed, unlockAll)
            val ref = all.course.units.firstOrNull { it.id == unitId }
            val unit = all.units[unitId]
            if (ref != null && unit != null && progress.status(ref) == UnitStatus.UNLOCKED) {
                val lesson = unit.lessons.firstOrNull { it.id !in completed }
                if (lesson != null) {
                    onResult(unit.id, lesson.id)
                    return@launch
                }
            }
            val next = progress.nextLesson()
            if (next != null) onResult(next.first.id, next.second.id) else onResult(unitId, null)
        }
    }

    companion object {
        fun factory(container: AppContainer, unitId: String, tipId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChallengeViewModel(container, unitId, tipId) }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    unitId: String,
    tipId: String,
    onClose: () -> Unit,
    onOpenLesson: (unitId: String, lessonId: String) -> Unit,
    onOpenUnit: (unitId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: ChallengeViewModel = viewModel(
        key = "challenge/$unitId/$tipId",
        factory = ChallengeViewModel.factory(container, unitId, tipId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val onContinue = {
        vm.continueLesson { u, l -> if (l != null) onOpenLesson(u, l) else onOpenUnit(u) }
    }

    Scaffold(
        // Pads for the keyboard (edge-to-edge), so typed answers and buttons stay visible.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Sfida") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                },
            )
        },
        bottomBar = {
            val s = state
            if (s is ChallengeUiState.Ready && s.done) {
                ChallengeActions(onContinue = onContinue, onClose = onClose)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ChallengeUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ChallengeUiState.Missing -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("This challenge is no longer available.", textAlign = TextAlign.Center)
                    Button(onClick = onClose) { Text("Chiudi") }
                }
                is ChallengeUiState.Ready -> ChallengeContent(s, vm)
            }
        }
    }
}

@Composable
private fun ChallengeContent(s: ChallengeUiState.Ready, vm: ChallengeViewModel) {
    val actions = remember(vm) {
        ExerciseActions(
            answer = vm::answer,
            continueWith = vm::finishWithout,
            flashcardSeen = {},
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(s.done) {
        if (s.done && s.tip.challenge != null) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.unitTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(s.tip.rule, style = MaterialTheme.typography.headlineSmall)
        val challenge = s.tip.challenge
        if (challenge != null) {
            challenge.instruction?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            key(s.tip.id) {
                ExerciseView(
                    exercise = challenge,
                    answered = s.done,
                    autoPlay = s.autoPlay,
                    speechEnabled = s.speechEnabled,
                    actions = actions,
                )
            }
        }
        s.feedback?.let { feedback ->
            Surface(color = verdictColor(feedback.verdict), shape = MaterialTheme.shapes.medium) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) { FeedbackBody(feedback) }
            }
        }
        if (s.done) TipCard(s.tip)
        Spacer(Modifier.height(24.dp))
    }
}

/** The rule itself: shown after answering (or straight away when the tip has no challenge). */
@Composable
fun TipCard(tip: GrammarTip, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("La regola", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(tip.tip, style = MaterialTheme.typography.bodyLarge)
            tip.example?.let { ex ->
                ItalianText(ex.it, style = MaterialTheme.typography.bodyLarge)
                if (ex.en.isNotBlank()) {
                    Text(ex.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChallengeActions(onContinue: () -> Unit, onClose: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Chiudi") }
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("Continua la lezione") }
        }
    }
}
