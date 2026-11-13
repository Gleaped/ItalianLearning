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
import com.gleaped.italianlearning.data.db.SrsCardEntity
import com.gleaped.italianlearning.data.toState
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.domain.srs.Sm2
import com.gleaped.italianlearning.domain.speech.SpeakableText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.SpeakerButton
import com.gleaped.italianlearning.ui.components.RatingChoices
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
) {
    val current: SrsCardEntity? get() = queue.firstOrNull()
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
            val limit = container.settings.current().dailyNewCards
            val queue = container.learning.reviewQueue(limit)
            val waiting = (container.learning.waitingNewCardCount() - queue.count { it.firstReviewedEpochDay == null })
                .coerceAtLeast(0)
            beforeLastRating = null
            _state.value = ReviewUiState(loading = false, queue = queue, initialSize = queue.size, newWaiting = waiting)
        }
    }

    fun reveal() {
        _state.value = _state.value.copy(revealed = true)
        _state.value.current?.let { container.tts.speak(SpeakableText.forCard(it.front)) }
    }

    /**
     * Called when the screen is shown again. The tab keeps this view model alive, so without
     * this a queue loaded earlier (often empty) would hide cards added by a lesson since.
     * A session in progress is left alone.
     */
    fun refreshIfIdle() {
        val s = _state.value
        if (s.loading) return
        if (s.current == null || (s.reviewed == 0 && !s.revealed)) reload()
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
            _state.value = previous.copy(revealed = true, setBreak = false, canUndo = false)
        }
    }

    fun rate(rating: Rating) {
        val s = _state.value
        val card = s.current ?: return
        if (!s.revealed) return
        // Hide the rating buttons immediately so a double tap cannot rate twice.
        _state.value = s.copy(revealed = false)
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
        Text("${state.queue.size} left", style = MaterialTheme.typography.labelMedium)
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
                    SpeakerButton(SpeakableText.forCard(card.front))
                }
                if (state.revealed) {
                    HorizontalDivider()
                    Text(card.back, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    card.example?.let { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                            SpeakerButton(ex)
                        }
                    }
                } else {
                    Text("Tap to reveal", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.revealed) {
            val today = LocalDate.now().toEpochDay()
            val cardState = card.toState()
            RatingChoices(
                onRate = vm::rate,
                whenBack = { r ->
                    if (r == Rating.AGAIN) "in this session" else Sm2.describeInterval(Sm2.review(cardState, r, today).intervalDays)
                },
            )
        } else {
            Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
            if (state.canUndo) UndoButton(vm::undo)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UndoButton(onUndo: () -> Unit) {
    TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text("Undo last rating") }
}
