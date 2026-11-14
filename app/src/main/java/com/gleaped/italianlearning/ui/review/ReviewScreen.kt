package com.gleaped.italianlearning.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import com.gleaped.italianlearning.data.db.SrsCardEntity
import com.gleaped.italianlearning.data.toState
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.srs.CardKind
import com.gleaped.italianlearning.domain.srs.Cards
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.domain.srs.Sm2
import com.gleaped.italianlearning.domain.speech.SpeakableText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.SpeakerButton
import com.gleaped.italianlearning.ui.components.RatingChoices
import com.gleaped.italianlearning.ui.challenge.TipCard
import com.gleaped.italianlearning.ui.lesson.AnswerField
import com.gleaped.italianlearning.ui.lesson.CheckButton
import com.gleaped.italianlearning.ui.lesson.ExerciseActions
import com.gleaped.italianlearning.ui.lesson.ExerciseView
import com.gleaped.italianlearning.ui.lesson.Feedback
import com.gleaped.italianlearning.ui.lesson.FeedbackBody
import com.gleaped.italianlearning.ui.lesson.verdictColor
import androidx.compose.material3.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReviewUiState(
    val loading: Boolean = true,
    val queue: List<SrsCardEntity> = emptyList(),
    val revealed: Boolean = false,
    val reviewed: Int = 0,
    val initialSize: Int = 0,
    /** True at the end of a set of [ReviewViewModel.SET_SIZE] cards, until the learner chooses to go on. */
    val setBreak: Boolean = false,
    /** New cards still in the deck that today's allowance does not cover. */
    val newWaiting: Int = 0,
    /** The last rating can be taken back. */
    val canUndo: Boolean = false,
    /** The grammar tips behind the grammar cards in [queue], by card id. */
    val tips: Map<String, GrammarTip> = emptyMap(),
    /** A grammar card's challenge has been answered (or skipped); [grammarFeedback] is null when skipped. */
    val grammarDone: Boolean = false,
    val grammarFeedback: Feedback? = null,
    val autoPlay: Boolean = true,
    val speechEnabled: Boolean = true,
) {
    val current: SrsCardEntity? get() = queue.firstOrNull()

    /** The challenge to answer when the current card is a grammar card whose tip still exists. */
    val currentTip: GrammarTip? get() = current?.let { tips[it.id] }?.takeIf { it.challenge != null }

    /** How a grammar card is rated from the challenge result: no self-rating needed. */
    val grammarRating: Rating
        get() = when (grammarFeedback?.verdict) {
            Verdict.CORRECT -> Rating.GOOD
            Verdict.ALMOST -> Rating.HARD
            else -> Rating.AGAIN
        }
}

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** The state and the card as they were just before the last rating, for [undo]. */
    private var beforeLastRating: Pair<ReviewUiState, SrsCardEntity>? = null

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val settings = container.settings.current()
            // Grammar cards for lessons finished before the deck held grammar.
            runCatching { container.learning.syncGrammarCards { container.content.unit(it) } }
            val queue = container.learning.reviewQueue(settings.dailyNewCards)
            val tips = HashMap<String, GrammarTip>()
            for (card in queue) {
                if (Cards.kindOf(card.id) != CardKind.GRAMMAR) continue
                val tipId = Cards.tipIdOf(card.id, card.sourceUnitId) ?: continue
                val tip = runCatching { container.content.unit(card.sourceUnitId) }.getOrNull()
                    ?.grammarTips?.firstOrNull { it.id == tipId } ?: continue
                tips[card.id] = tip
            }
            val waiting = (container.learning.waitingNewCardCount() - queue.count { it.firstReviewedEpochDay == null })
                .coerceAtLeast(0)
            beforeLastRating = null
            _state.value = ReviewUiState(
                loading = false,
                queue = queue,
                initialSize = queue.size,
                newWaiting = waiting,
                tips = tips,
                autoPlay = settings.autoPlayAudio,
                speechEnabled = settings.speechPracticeEnabled,
            )
        }
    }

    fun reveal() {
        _state.value = _state.value.copy(revealed = true)
        // Grammar cards without a challenge show a rule in English, so there is nothing to say.
        _state.value.current?.takeIf { Cards.kindOf(it.id) != CardKind.GRAMMAR }
            ?.let { container.tts.speak(SpeakableText.forCard(it.front)) }
    }

    /** The challenge of the current grammar card was answered. */
    fun answerGrammar(feedback: Feedback) {
        val s = _state.value
        val tip = s.currentTip ?: return
        if (s.grammarDone) return
        val explanation = feedback.explanation ?: tip.challenge?.explanation
        _state.value = s.copy(grammarDone = true, grammarFeedback = feedback.copy(explanation = explanation, canOverride = false))
    }

    /** The challenge ended without an answer (skipped): the card comes back in this session. */
    fun skipGrammar() {
        val s = _state.value
        if (s.currentTip == null || s.grammarDone) return
        _state.value = s.copy(grammarDone = true)
    }

    /** Moves on from an answered grammar card, rating it from the result. */
    fun continueGrammar() {
        val s = _state.value
        if (s.currentTip == null || !s.grammarDone) return
        submit(s, s.grammarRating)
    }

    /**
     * Called when the screen is shown again. The tab keeps this view model alive, so without
     * this a queue loaded earlier (often empty) would hide cards added by a lesson since.
     * A session in progress is left alone.
     */
    fun refreshIfIdle() {
        val s = _state.value
        if (s.loading) return
        if (s.current == null || (s.reviewed == 0 && !s.revealed && !s.grammarDone)) reload()
    }

    /** Starts the next set after a break. */
    fun continueAfterBreak() {
        _state.value = _state.value.copy(setBreak = false)
    }

    /** Takes back the last rating and shows that card again, answer side up. */
    fun undo() {
        val (previous, original) = beforeLastRating ?: return
        beforeLastRating = null
        viewModelScope.launch {
            container.learning.undoReview(original)
            _state.value = previous.copy(setBreak = false, canUndo = false)
        }
    }

    fun rate(rating: Rating) {
        val s = _state.value
        if (!s.revealed) return
        submit(s, rating)
    }

    private fun submit(s: ReviewUiState, rating: Rating) {
        val card = s.current ?: return
        // Hide the rating buttons immediately so a double tap cannot rate twice.
        _state.value = s.copy(revealed = false, grammarDone = false, grammarFeedback = null)
        viewModelScope.launch {
            val updated = container.learning.review(card, rating)
            val rest = s.queue.drop(1)
            // Cards marked "Again" come back at the end of this session.
            val queue = if (rating == Rating.AGAIN) rest + updated else rest
            val reviewed = s.reviewed + 1
            beforeLastRating = s to card
            _state.value = s.copy(
                queue = queue,
                revealed = false,
                grammarDone = false,
                grammarFeedback = null,
                reviewed = reviewed,
                // A pause after every full set, so a long queue comes in manageable pieces.
                setBreak = queue.isNotEmpty() && reviewed % SET_SIZE == 0,
                canUndo = true,
            )
        }
    }

    companion object {
        /** Cards per set; after each set the learner chooses to continue or stop. */
        const val SET_SIZE = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReviewViewModel(container) }
        }
    }
}

@Composable
fun ReviewScreen(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ReviewViewModel = viewModel(factory = ReviewViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refreshIfIdle() }

    if (state.loading) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val card = state.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (card == null) {
            Text(
                when {
                    state.reviewed > 0 -> "Finito! You reviewed ${state.reviewed} card(s). Ci vediamo domani."
                    state.newWaiting > 0 ->
                        "All done for today. ${state.newWaiting} new card(s) are waiting and will start to appear tomorrow."
                    else -> "Nothing to review right now. Finish a lesson to add new words to your deck."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Today") }
            if (state.canUndo) UndoButton(vm::undo)
            return@Column
        }
        if (state.setBreak) {
            Text("${state.reviewed} cards done", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${state.queue.size} left. Carry on, or stop here: the rest will keep until later.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = vm::continueAfterBreak, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Continue")
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Stop for now")
            }
            if (state.canUndo) UndoButton(vm::undo)
            return@Column
        }
        val total = state.reviewed + state.queue.size
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else state.reviewed.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        val kind = Cards.kindOf(card.id)
        val tip = state.currentTip
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when {
                    kind == CardKind.PRODUCE -> "Dillo in italiano"
                    kind == CardKind.GRAMMAR -> "Grammatica"
                    else -> "Che cosa significa?"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("${state.queue.size} left", style = MaterialTheme.typography.labelMedium)
        }
        val today = LocalDate.now().toEpochDay()
        val whenBack: (Rating) -> String = { r ->
            if (r == Rating.AGAIN) "in this session" else Sm2.describeInterval(Sm2.review(card.toState(), r, today).intervalDays)
        }
        // Each showing of a card starts clean, also when "Again" brings it back in this session.
        key(card.id, state.reviewed) {
            when {
                tip != null -> GrammarCard(state, tip, vm, whenBack)
                kind == CardKind.PRODUCE -> ProduceCard(state, card, vm, whenBack)
                else -> RecogniseCard(state, card, vm, whenBack, speak = kind != CardKind.GRAMMAR)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UndoButton(onUndo: () -> Unit) {
    TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text("Undo last rating") }
}


/** Italian shown, meaning recalled: tap to reveal, then rate. Also a grammar card whose challenge is gone. */
@Composable
private fun RecogniseCard(
    state: ReviewUiState,
    card: SrsCardEntity,
    vm: ReviewViewModel,
    whenBack: (Rating) -> String,
    speak: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = { if (!state.revealed) vm.reveal() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.front, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (speak) SpeakerButton(SpeakableText.forCard(card.front))
            }
            if (state.revealed) {
                HorizontalDivider()
                Text(card.back, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                ExampleLine(card.example)
            } else {
                Text("Tap to reveal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (state.revealed) {
        RatingChoices(onRate = vm::rate, whenBack = whenBack)
    } else {
        Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
        if (state.canUndo) UndoButton(vm::undo)
    }
}

/**
 * Meaning shown, Italian produced. Typing is optional: the learner can also say the word and
 * tap "Show answer". A typed answer is checked, but the learner still rates how it went.
 */
@Composable
private fun ProduceCard(
    state: ReviewUiState,
    card: SrsCardEntity,
    vm: ReviewViewModel,
    whenBack: (Rating) -> String,
) {
    var typed by remember { mutableStateOf("") }
    val context = remember(card.id) { Cards.cloze(card.example, card.front) }
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(card.back, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (!state.revealed) {
                // The example with the word left out gives a context to recall it in.
                context?.let { Text(it, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) }
            } else {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    SpeakerButton(SpeakableText.forCard(card.front))
                }
                ExampleLine(card.example)
            }
        }
    }
    if (state.revealed) {
        if (typed.isNotBlank()) {
            val verdict = remember(typed) { Cards.gradeRecall(typed, card.front) }
            Surface(color = verdictColor(verdict), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (verdict) {
                        Verdict.CORRECT -> "✓ You wrote: $typed"
                        Verdict.ALMOST -> "≈ You wrote: $typed (check the accents)"
                        Verdict.WRONG -> "✗ You wrote: $typed"
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        RatingChoices(onRate = vm::rate, whenBack = whenBack)
    } else {
        AnswerField(
            value = typed,
            onValueChange = { typed = it },
            enabled = true,
            onDone = vm::reveal,
            label = "Type it, or just say it aloud",
        )
        CheckButton(enabled = true, onClick = vm::reveal, label = if (typed.isBlank()) "Show answer" else "Check")
        if (state.canUndo) UndoButton(vm::undo)
    }
}

/** A grammar tip's quick challenge; the result rates the card, so there is nothing to self-assess. */
@Composable
private fun GrammarCard(
    state: ReviewUiState,
    tip: GrammarTip,
    vm: ReviewViewModel,
    whenBack: (Rating) -> String,
) {
    val challenge = tip.challenge ?: return
    val actions = remember(vm) {
        ExerciseActions(
            answer = vm::answerGrammar,
            continueWith = { vm.skipGrammar() },
            flashcardSeen = {},
        )
    }
    Text(tip.rule, style = MaterialTheme.typography.titleLarge)
    challenge.instruction?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
    }
    ExerciseView(
        exercise = challenge,
        answered = state.grammarDone,
        autoPlay = state.autoPlay,
        speechEnabled = state.speechEnabled,
        actions = actions,
    )
    if (state.grammarDone) {
        state.grammarFeedback?.let { feedback ->
            Surface(color = verdictColor(feedback.verdict), shape = MaterialTheme.shapes.medium) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) { FeedbackBody(feedback) }
            }
        }
        TipCard(tip)
        Button(onClick = vm::continueGrammar, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Continue · back ${whenBack(state.grammarRating)}")
        }
    } else if (state.canUndo) {
        UndoButton(vm::undo)
    }
}

@Composable
private fun ExampleLine(example: String?) {
    if (example.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(example, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        SpeakerButton(example)
    }
}
