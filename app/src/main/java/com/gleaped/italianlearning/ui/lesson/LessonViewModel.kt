package com.gleaped.italianlearning.ui.lesson

import android.os.SystemClock
import com.gleaped.italianlearning.domain.practice.DailyMix
import com.gleaped.italianlearning.domain.progress.ExamClock
import com.gleaped.italianlearning.domain.progress.SkillStats
import kotlin.random.Random
import com.gleaped.italianlearning.domain.speech.SpokenWord
import com.gleaped.italianlearning.content.SentenceBuild
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
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.content.Conjugation
import com.gleaped.italianlearning.content.ErrorCorrection
import com.gleaped.italianlearning.content.GapFill
import com.gleaped.italianlearning.content.ListenTranscribe
import com.gleaped.italianlearning.content.SpeakRepeat
import com.gleaped.italianlearning.content.SpeakRespond
import com.gleaped.italianlearning.content.Transform
import com.gleaped.italianlearning.content.Translate
import com.gleaped.italianlearning.domain.practice.LevelCheck
import com.gleaped.italianlearning.domain.practice.Practice
import com.gleaped.italianlearning.domain.practice.WeakSpot
import com.gleaped.italianlearning.domain.practice.WeakSpots
import com.gleaped.italianlearning.domain.progress.LessonCheckpoint
import java.time.LocalDate
import com.gleaped.italianlearning.data.ExerciseOutcome
import com.gleaped.italianlearning.domain.grading.Verdict
import com.gleaped.italianlearning.domain.progress.CourseProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Feedback shown after an answer. */
data class Feedback(
    val verdict: Verdict,
    val expected: String? = null,
    val note: String? = null,
    val explanation: String? = null,
    /** Label for [expected]: "Answer", "Model answer"… */
    val expectedLabel: String = "Answer",
    /** Offers "My answer was right"; set by the lesson player for typed and spoken answers. */
    val canOverride: Boolean = false,
    /** A spoken answer word by word: [expected] is then shown with the missed words marked. */
    val spoken: List<SpokenWord>? = null,
    /** Italian the learner can repeat aloud from the feedback panel; set by the lesson player. */
    val sayIt: String? = null,
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
    /**
     * Before the first exercise: what the lesson is about and how long it takes.
     * [resumeStep] is the 1-based step a saved run would continue from, or null.
     */
    data class Intro(val unit: CourseUnit, val lesson: Lesson, val resumeStep: Int? = null) : LessonUiState
    data class Error(val message: String) : LessonUiState
    data class Running(
        val unit: CourseUnit,
        val lesson: Lesson,
        val index: Int,
        val feedback: Feedback?,
        val autoPlay: Boolean,
        val speechEnabled: Boolean,
        /** Counts every exercise shown, so a re-asked exercise starts from a clean state. */
        val step: Int = 0,
        /** Above zero in the re-ask round at the end: missed items still to get right, this one included. */
        val retryLeft: Int = 0,
        /** Timed exam papers only; read against SystemClock.elapsedRealtime(). */
        val clock: ExamClock? = null,
    ) : LessonUiState {
        val exercise: Exercise get() = lesson.exercises[index]
        val inRetry: Boolean get() = retryLeft > 0
        val progress: Float get() = when {
            inRetry || lesson.exercises.isEmpty() -> 1f
            else -> index.toFloat() / lesson.exercises.size
        }
    }
    data class Finished(
        val unit: CourseUnit,
        val lesson: Lesson,
        val summary: LessonSummary,
        /** A practice round rather than a course lesson. */
        val practice: Boolean = false,
        /** Timed exam papers only: seconds used and the paper's limit. */
        val examSeconds: Int? = null,
        val examLimitSeconds: Int? = null,
        /** A module check only: whether it was passed, and so the module's lessons marked as known. */
        val checkPassed: Boolean? = null,
    ) : LessonUiState
}

class LessonViewModel(
    private val container: AppContainer,
    private val unitId: String,
    private val lessonId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    /** True for the "Punti deboli" round, which re-asks exercises from several lessons. */
    val isPractice: Boolean = unitId == Practice.UNIT_ID

    /** True for the "Mix del giorno" round: exercises from finished lessons, topics shuffled. */
    val isMix: Boolean = isPractice && lessonId == Practice.MIX_LESSON_ID

    /** True for "Test del modulo": exercises from a whole module, to test out of it. */
    private val checkModuleId: String? = if (isPractice) LevelCheck.moduleOf(lessonId) else null
    private val isCheck: Boolean = checkModuleId != null
    private var checkUnits: List<CourseUnit> = emptyList()
    private var clock: ExamClock? = null

    /** First-attempt results, one per exercise; the re-ask round does not change them. */
    private val outcomes = mutableListOf<ExerciseOutcome>()
    private var autoPlay = true
    private var speechEnabled = true
    private var steps = 0

    /** Practice only: where each exercise of the round came from. */
    private var origins: List<WeakSpot> = emptyList()
    private var checkpoint: LessonCheckpoint? = null

    /** Exercise indexes still to get right in the re-ask round, and how often each was re-asked. */
    private val retryQueue = ArrayDeque<Int>()
    private val retryCount = HashMap<Int, Int>()

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            autoPlay = settings.autoPlayAudio
            speechEnabled = settings.speechNow()
            when {
                isMix -> loadMix()
                isCheck -> loadCheck()
                isPractice -> loadPractice()
                else -> loadLesson()
            }
        }
    }

    private suspend fun loadLesson() {
        // Only this unit's file is parsed (off the main thread); the rest stays lazy.
        val unit = container.content.unit(unitId)
        val lesson = unit?.lessons?.firstOrNull { it.id == lessonId }
        if (unit == null || lesson == null) {
            _state.value = LessonUiState.Error("This lesson could not be found.")
            return
        }
        if (lesson.exercises.isEmpty()) {
            _state.value = LessonUiState.Error("This lesson has no exercises yet.")
            return
        }
        val saved = container.settings.currentLessonCheckpoint()
            ?.takeIf { it.lessonId == lessonId && it.unitId == unitId && it.index in 1..lesson.exercises.size }
        checkpoint = saved
        _state.value = LessonUiState.Intro(
            unit = unit,
            lesson = lesson,
            resumeStep = saved?.let { minOf(it.index + 1, lesson.exercises.size) },
        )
    }

    private suspend fun loadPractice() {
        val today = LocalDate.now().toEpochDay()
        val due = WeakSpots.due(container.learning.attemptsNow(), today)
        val picked = WeakSpots.resolve(due, container.content.load().units).take(WeakSpots.SESSION_SIZE)
        if (picked.isEmpty()) {
            _state.value = LessonUiState.Error("Nothing to revisit right now. Missed items come back here after a day.")
            return
        }
        origins = picked.map { it.first }
        showRound(
            Lesson(
                id = Practice.WEAK_LESSON_ID,
                title = Practice.TITLE,
                minutes = (picked.size + 1) / 2,
                exercises = picked.map { it.second },
                goal = "Another go at items you missed earlier. Each one comes back after 1, 3 and 7 days until it sticks.",
            ),
        )
    }

    private suspend fun loadCheck() {
        val moduleId = checkModuleId ?: return
        val content = container.content.load()
        checkUnits = content.course.units
            .filter { it.module == moduleId && it.available }
            .sortedBy { it.order }
            .mapNotNull { content.units[it.id] }
        val picked = LevelCheck.pick(checkUnits, Random(System.nanoTime()))
        if (picked.size < LevelCheck.MIN_SIZE) {
            _state.value = LessonUiState.Error("This module has too few exercises to test out of.")
            return
        }
        val today = LocalDate.now().toEpochDay()
        origins = picked.map { WeakSpot(it.lessonId, it.exerciseIndex, it.exercise.id, it.type, today) }
        showRound(
            Lesson(
                id = lessonId,
                title = LevelCheck.TITLE,
                minutes = (picked.size + 1) / 2,
                exercises = picked.map { it.exercise },
                goal = "${picked.size} questions from across the module. Score 85% or more and its lessons are marked as known; " +
                    "you can still open any of them. Misses come back in Punti deboli.",
            ),
        )
    }

    private suspend fun loadMix() {
        val today = LocalDate.now().toEpochDay()
        val content = container.content.load()
        val attempts = container.learning.attemptsNow()
        val picked = DailyMix.pick(
            candidates = DailyMix.candidates(content.units.values, container.learning.completedLessonIds.first()),
            weakUnitIds = SkillStats.recentlyMissedUnits(attempts, today),
            recent = attempts.filter { it.epochDay > today - DailyMix.RECENT_DAYS }.map { it.lessonId to it.exerciseIndex }.toSet(),
            random = Random(System.nanoTime()),
        )
        if (picked.isEmpty()) {
            _state.value = LessonUiState.Error("The mix draws on lessons you have finished. Finish a lesson or two first.")
            return
        }
        // Stored like a "Punti deboli" round: a miss here opens a weak spot for that exercise.
        origins = picked.map { WeakSpot(it.lessonId, it.exerciseIndex, it.exercise.id, it.type, today) }
        showRound(
            Lesson(
                id = Practice.MIX_LESSON_ID,
                title = Practice.MIX_TITLE,
                minutes = (picked.size + 1) / 2,
                exercises = picked.map { it.exercise },
                goal = "A short mix from lessons you have finished, with the topics shuffled on purpose. " +
                    "Switching between rules is harder than practising one at a time, and that is why it sticks.",
            ),
        )
    }

    /** Shows the intro of a practice round, which is a lesson made up on the spot. */
    private fun showRound(lesson: Lesson) {
        val unit = CourseUnit(
            id = Practice.UNIT_ID,
            level = "",
            title = lesson.title,
            objective = "",
            grammarFocus = "",
            remedialUnitIds = emptyList(),
            lessons = listOf(lesson),
            vocabulary = emptyList(),
        )
        _state.value = LessonUiState.Intro(unit, lesson)
    }

    /** Leaves the intro screen: continues a saved run when [resume] is set, otherwise starts at step 1. */
    fun start(resume: Boolean = false) {
        val intro = _state.value as? LessonUiState.Intro ?: return
        val saved = checkpoint.takeIf { resume }
        outcomes.clear()
        retryQueue.clear()
        retryCount.clear()
        clock = intro.lesson.takeIf { it.timed }?.let {
            ExamClock(it.minutes * 60, saved?.elapsedSeconds ?: 0, SystemClock.elapsedRealtime())
        }
        val first = LessonUiState.Running(
            unit = intro.unit,
            lesson = intro.lesson,
            index = 0,
            feedback = null,
            autoPlay = autoPlay,
            speechEnabled = speechEnabled,
            clock = clock,
        )
        if (saved == null) {
            if (checkpoint != null) {
                checkpoint = null
                viewModelScope.launch { container.settings.clearLessonCheckpoint() }
            }
            _state.value = first
            return
        }
        saved.verdicts.forEach { (index, verdict) ->
            val ex = intro.lesson.exercises.getOrNull(index) ?: return@forEach
            outcomes += ExerciseOutcome(index, ex.id, ExerciseTypes.of(ex), if (ex.scored) verdict else null)
        }
        if (saved.index < intro.lesson.exercises.size) {
            _state.value = first.copy(index = saved.index, step = ++steps)
        } else {
            startRetryOrFinish(first.copy(index = intro.lesson.exercises.lastIndex))
        }
    }

    private fun record(running: LessonUiState.Running, verdict: Verdict?) {
        if (running.inRetry) return
        val ex = running.exercise
        outcomes.removeAll { it.index == running.index }
        outcomes += ExerciseOutcome(
            index = running.index,
            exerciseId = ex.id,
            type = ExerciseTypes.of(ex),
            verdict = if (ex.scored) verdict else null,
        )
    }

    /** Learner answered: record and show feedback. */
    fun answer(feedback: Feedback) {
        val running = _state.value as? LessonUiState.Running ?: return
        if (running.feedback != null) return
        record(running, feedback.verdict)
        _state.value = running.copy(
            feedback = feedback.copy(
                explanation = feedback.explanation ?: running.exercise.explanation,
                canOverride = feedback.verdict != Verdict.CORRECT && canOverride(running.exercise),
                sayIt = sayable(running.exercise, feedback).takeIf { running.speechEnabled },
            ),
        )
    }

    /**
     * "My answer was right": the stored answers cannot list every correct wording, and the
     * speech recogniser mishears. Counts the current answer as correct.
     */
    fun overrideCorrect() {
        val running = _state.value as? LessonUiState.Running ?: return
        val feedback = running.feedback?.takeIf { it.canOverride } ?: return
        record(running, Verdict.CORRECT)
        _state.value = running.copy(
            feedback = feedback.copy(verdict = Verdict.CORRECT, note = "Counted as correct.", canOverride = false),
        )
    }

    /** Exercises without a feedback step (grammar note, flashcard rating, skip). */
    fun recordAndContinue(verdict: Verdict?) {
        val running = _state.value as? LessonUiState.Running ?: return
        record(running, verdict)
        advance(running)
    }

    fun onFlashcardSeen(card: Flashcard) {
        if (isPractice) return
        viewModelScope.launch { container.learning.addFlashcard(unitId, lessonId, card) }
    }

    fun next() {
        val running = _state.value as? LessonUiState.Running ?: return
        advance(running)
    }

    private fun advance(running: LessonUiState.Running) {
        if (running.inRetry) {
            // Still wrong: once more at the end of the round, up to MAX_RETRIES times per item.
            val again = running.feedback?.verdict == Verdict.WRONG &&
                (retryCount[running.index] ?: 0) < MAX_RETRIES
            if (again) {
                retryCount[running.index] = (retryCount[running.index] ?: 0) + 1
                retryQueue.addLast(running.index)
            }
            showNextRetry(running)
            return
        }
        val nextIndex = running.index + 1
        saveCheckpoint(running, nextIndex)
        if (nextIndex < running.lesson.exercises.size) {
            _state.value = running.copy(index = nextIndex, feedback = null, step = ++steps)
            return
        }
        startRetryOrFinish(running)
    }

    /** After the last exercise: re-ask what was missed, so each item is got right once before leaving. */
    private fun startRetryOrFinish(running: LessonUiState.Running) {
        retryQueue.clear()
        retryCount.clear()
        outcomes.sortedBy { it.index }
            .filter { it.verdict == Verdict.WRONG }
            .filter { o -> running.lesson.exercises.getOrNull(o.index)?.let(WeakSpots::isRetryable) == true }
            .forEach { retryQueue.addLast(it.index) }
        showNextRetry(running)
    }

    private fun showNextRetry(running: LessonUiState.Running) {
        val index = retryQueue.removeFirstOrNull()
        if (index == null) {
            finish(running)
            return
        }
        _state.value = running.copy(index = index, feedback = null, step = ++steps, retryLeft = retryQueue.size + 1)
    }

    private fun saveCheckpoint(running: LessonUiState.Running, nextIndex: Int) {
        if (isPractice) return
        val saved = LessonCheckpoint(
            unitId = unitId,
            lessonId = lessonId,
            index = nextIndex,
            verdicts = outcomes.associate { it.index to it.verdict },
            elapsedSeconds = clock?.elapsedSeconds(SystemClock.elapsedRealtime()) ?: 0,
        )
        checkpoint = saved
        viewModelScope.launch { container.settings.saveLessonCheckpoint(saved) }
    }

    private fun finish(running: LessonUiState.Running) {
        val examSeconds = clock?.elapsedSeconds(SystemClock.elapsedRealtime())
        viewModelScope.launch {
            val scored = outcomes.filter { o -> running.lesson.exercises.getOrNull(o.index)?.scored == true }
            var next: kotlin.Pair<CourseUnit, Lesson>? = null
            var checkPassed: Boolean? = null
            if (isPractice) {
                container.learning.recordPractice(
                    outcomes.mapNotNull { o ->
                        val spot = origins.getOrNull(o.index) ?: return@mapNotNull null
                        val verdict = o.verdict ?: return@mapNotNull null
                        spot to verdict
                    },
                )
                if (isMix) container.settings.markMixDone(LocalDate.now().toEpochDay())
                if (isCheck) {
                    val ok = LevelCheck.passed(
                        scored.count { it.verdict == Verdict.CORRECT },
                        scored.count { it.verdict == Verdict.ALMOST },
                        scored.size,
                    )
                    checkPassed = ok
                    if (ok) container.learning.markUnitsKnown(checkUnits)
                }
            } else {
                container.learning.completeLesson(running.unit, running.lesson.id, outcomes.toList())
                checkpoint = null
                container.settings.clearLessonCheckpoint()
                val content = container.content.load()
                val completed = container.learning.completedLessonIds.first() + running.lesson.id
                val unlockAll = container.settings.current().unlockAllUnits
                // Prefer the next lesson in this unit (works for remedial units too), then the main path.
                val inUnit = running.unit.lessons.firstOrNull { it.id !in completed }
                next = if (inUnit != null) {
                    running.unit to inUnit
                } else {
                    CourseProgress(content.course, content.units, completed, unlockAll).nextLesson()
                }
            }
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
                practice = isPractice,
                examSeconds = examSeconds,
                examLimitSeconds = clock?.limitSeconds,
                checkPassed = checkPassed,
            )
        }
    }

    companion object {
        /** How often a missed item is re-asked in the end-of-lesson round if it is missed again. */
        const val MAX_RETRIES = 2

        /** The Italian answer to repeat aloud after a written exercise, if it has one. */
        fun sayable(exercise: Exercise, feedback: Feedback): String? = when (exercise) {
            is GapFill, is Transform, is ErrorCorrection, is ListenTranscribe, is Conjugation, is SentenceBuild ->
                feedback.expected
            is Translate -> feedback.expected.takeIf { !exercise.fromItalian }
            else -> null
        }?.takeIf { it.isNotBlank() }

        /** Typed and spoken answers, where a correct answer can be marked wrong. */
        fun canOverride(exercise: Exercise): Boolean = when (exercise) {
            is GapFill, is Translate, is Transform, is ErrorCorrection, is ListenTranscribe,
            is SpeakRepeat, is SpeakRespond, is Conjugation,
            -> true
            else -> false
        }

        fun factory(container: AppContainer, unitId: String, lessonId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LessonViewModel(container, unitId, lessonId) }
            }
    }
}
