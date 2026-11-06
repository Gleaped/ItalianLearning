package com.gleaped.italianlearning.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Exercise
import com.gleaped.italianlearning.content.ExerciseTypes
import com.gleaped.italianlearning.content.Flashcard
import com.gleaped.italianlearning.content.FreeWrite
import com.gleaped.italianlearning.content.GrammarNote
import com.gleaped.italianlearning.content.Invalid
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.content.Unsupported
import com.gleaped.italianlearning.data.ExerciseOutcome
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.progress.CourseProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Feedback shown after an answer. */
data class Feedback(
    val verdict: Verdict,
    val expected: String? = null,
    val note: String? = null,
    val explanation: String? = null,
    /** Label for [expected]: "Answer", "Model answer"… */
    val expectedLabel: String = "Answer",
)

data class LessonSummary(
    val correct: Int,
    val almost: Int,
    val wrong: Int,
    val skipped: Int,
    val nextUnitId: String?,
    val nextLessonId: String?,
    val nextLessonTitle: String?,
)

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data class Error(val message: String) : LessonUiState
    data class Running(
        val unit: CourseUnit,
        val lesson: Lesson,
        val index: Int,
        val feedback: Feedback?,
        val autoPlay: Boolean,
        val speechEnabled: Boolean,
    ) : LessonUiState {
        val exercise: Exercise get() = lesson.exercises[index]
        val progress: Float get() = if (lesson.exercises.isEmpty()) 1f else index.toFloat() / lesson.exercises.size
    }
    data class Finished(val unit: CourseUnit, val lesson: Lesson, val summary: LessonSummary) : LessonUiState
}

private fun typeOf(e: Exercise): String = when (e) {
    is GrammarNote -> ExerciseTypes.GRAMMAR_NOTE
    is Flashcard -> ExerciseTypes.FLASHCARD
    is com.gleaped.italianlearning.content.MultipleChoice -> ExerciseTypes.MULTIPLE_CHOICE
    is com.gleaped.italianlearning.content.GapFill -> ExerciseTypes.GAP_FILL
    is com.gleaped.italianlearning.content.Conjugation -> ExerciseTypes.CONJUGATION
    is com.gleaped.italianlearning.content.SentenceBuild -> ExerciseTypes.SENTENCE_BUILD
    is com.gleaped.italianlearning.content.Translate -> ExerciseTypes.TRANSLATE
    is com.gleaped.italianlearning.content.MatchPairs -> ExerciseTypes.MATCH_PAIRS
    is com.gleaped.italianlearning.content.ErrorCorrection -> ExerciseTypes.ERROR_CORRECTION
    is com.gleaped.italianlearning.content.ListenTranscribe -> ExerciseTypes.LISTEN_TRANSCRIBE
    is com.gleaped.italianlearning.content.ListenChoose -> ExerciseTypes.LISTEN_CHOOSE
    is com.gleaped.italianlearning.content.Reading -> ExerciseTypes.READING
    is com.gleaped.italianlearning.content.SpeakRepeat -> ExerciseTypes.SPEAK_REPEAT
    is com.gleaped.italianlearning.content.SpeakRespond -> ExerciseTypes.SPEAK_RESPOND
    is com.gleaped.italianlearning.content.Roleplay -> ExerciseTypes.ROLEPLAY
    is FreeWrite -> ExerciseTypes.FREE_WRITE
    is Unsupported -> e.type
    is Invalid -> e.type
}

class LessonViewModel(
    private val container: AppContainer,
    private val unitId: String,
    private val lessonId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private val outcomes = mutableListOf<ExerciseOutcome>()

    init {
        viewModelScope.launch {
            val content = container.content.load()
            val unit = content.units[unitId]
            val lesson = unit?.lessons?.firstOrNull { it.id == lessonId }
            if (unit == null || lesson == null) {
                _state.value = LessonUiState.Error("This lesson could not be found.")
                return@launch
            }
            if (lesson.exercises.isEmpty()) {
                _state.value = LessonUiState.Error("This lesson has no exercises yet.")
                return@launch
            }
            val settings = container.settings.current()
            _state.value = LessonUiState.Running(
                unit = unit,
                lesson = lesson,
                index = 0,
                feedback = null,
                autoPlay = settings.autoPlayAudio,
                speechEnabled = settings.speechPracticeEnabled,
            )
        }
    }

    private fun record(running: LessonUiState.Running, verdict: Verdict?) {
        val ex = running.exercise
        outcomes.removeAll { it.index == running.index }
        outcomes += ExerciseOutcome(
            index = running.index,
            exerciseId = ex.id,
            type = typeOf(ex),
            verdict = if (ex.scored) verdict else null,
        )
    }

    /** Learner answered: record and show feedback. */
    fun answer(feedback: Feedback) {
        val running = _state.value as? LessonUiState.Running ?: return
        if (running.feedback != null) return
        record(running, feedback.verdict)
        val withExplanation = feedback.copy(explanation = feedback.explanation ?: running.exercise.explanation)
        _state.value = running.copy(feedback = withExplanation)
    }

    /** Exercises without a feedback step (grammar note, flashcard rating, skip). */
    fun recordAndContinue(verdict: Verdict?) {
        val running = _state.value as? LessonUiState.Running ?: return
        record(running, verdict)
        advance(running)
    }

    fun onFlashcardSeen(card: Flashcard) {
        viewModelScope.launch { container.learning.addFlashcard(unitId, lessonId, card) }
    }

    fun next() {
        val running = _state.value as? LessonUiState.Running ?: return
        advance(running)
    }

    private fun advance(running: LessonUiState.Running) {
        val nextIndex = running.index + 1
        if (nextIndex < running.lesson.exercises.size) {
            _state.update { running.copy(index = nextIndex, feedback = null) }
            return
        }
        finish(running)
    }

    private fun finish(running: LessonUiState.Running) {
        viewModelScope.launch {
            container.learning.completeLesson(running.unit, running.lesson.id, outcomes.toList())
            val content = container.content.load()
            val completed = container.learning.completedLessonIds.first() + running.lesson.id
            val unlockAll = container.settings.current().unlockAllUnits
            // Prefer the next lesson in this unit (works for remedial units too), then the main path.
            val inUnit = running.unit.lessons.firstOrNull { it.id !in completed }
            val next = if (inUnit != null) {
                running.unit to inUnit
            } else {
                CourseProgress(content.course, content.units, completed, unlockAll).nextLesson()
            }
            val scored = outcomes.filter { o -> running.lesson.exercises.getOrNull(o.index)?.scored == true }
            _state.value = LessonUiState.Finished(
                unit = running.unit,
                lesson = running.lesson,
                summary = LessonSummary(
                    correct = scored.count { it.verdict == Verdict.CORRECT },
                    almost = scored.count { it.verdict == Verdict.ALMOST },
                    wrong = scored.count { it.verdict == Verdict.WRONG },
                    skipped = scored.count { it.verdict == null },
                    nextUnitId = next?.first?.id,
                    nextLessonId = next?.second?.id,
                    nextLessonTitle = next?.second?.title,
                ),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer, unitId: String, lessonId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LessonViewModel(container, unitId, lessonId) }
            }
    }
}
