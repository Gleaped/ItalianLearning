package com.gleaped.italianlearning.ui.home

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.gleaped.italianlearning.domain.practice.DailyMix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.content.CourseContent
import com.gleaped.italianlearning.domain.practice.AttemptRecord
import com.gleaped.italianlearning.domain.practice.WeakSpots
import com.gleaped.italianlearning.domain.progress.CourseProgress
import com.gleaped.italianlearning.domain.progress.ExamPlan
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import com.gleaped.italianlearning.domain.progress.Readiness
import com.gleaped.italianlearning.domain.progress.SkillStats
import com.gleaped.italianlearning.domain.progress.LessonCheckpoint
import com.gleaped.italianlearning.domain.progress.Streak
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Everything Home, the course map and unit pages need. */
data class Overview(
    val content: CourseContent,
    val progress: CourseProgress,
    val completedLessons: Set<String>,
    val streak: Int,
    val practicedToday: Boolean,
    val dueReviews: Int,
    val newCards: Int,
    /** Missed exercises ready to be asked again ("Punti deboli"). */
    val weakDue: Int = 0,
    /** The lesson left part-way, when it can still be continued. */
    val checkpoint: LessonCheckpoint? = null,
    /** Lessons finished today. */
    val lessonsToday: Int = 0,
    /** Enough lessons are finished for the daily mix. */
    val mixAvailable: Boolean = false,
    val mixDoneToday: Boolean = false,
    /** Day of the exam as an epoch day; 0 = not set. */
    val examDateEpochDay: Long = 0L,
    /** Rough exam score estimate from the answers so far. */
    val readiness: Readiness = Readiness(emptyList(), null, null),
    /** Lessons still to do on the way to B2. */
    val remainingLessons: Int = 0,
)

class OverviewViewModel(container: AppContainer) : ViewModel() {

    private val contentFlow = flow { emit(container.content.load()) }

    private val reviewCounts = container.learning.reviewCounts(
        container.settings.settings.map { it.dailyNewCards }.distinctUntilChanged(),
    )

    /** Review counts, the answer history and the rest of today's state, bundled to stay within combine's arity. */
    private class Extras(
        val counts: Pair<Int, Int>,
        val attempts: List<AttemptRecord>,
        val checkpoint: LessonCheckpoint?,
        val lessonsToday: Int,
        val lastMixDay: Long,
    )

    private val extras = combine(
        reviewCounts,
        container.learning.attempts,
        container.settings.lessonCheckpoint,
        container.learning.lessonsToday,
        container.settings.lastMixDay,
    ) { counts, attempts, checkpoint, lessonsToday, lastMixDay -> Extras(counts, attempts, checkpoint, lessonsToday, lastMixDay) }

    val overview: StateFlow<Overview?> = combine(
        contentFlow,
        container.learning.completedLessonIds,
        container.learning.activeDays,
        extras,
        container.settings.settings,
    ) { content, completed, days, extra, settings ->
        val today = LocalDate.now().toEpochDay()
        val counts = extra.counts
        val weakDue = WeakSpots.resolve(WeakSpots.due(extra.attempts, today), content.units).size
        val checkpoint = extra.checkpoint?.takeIf { saved ->
            val lesson = content.units[saved.unitId]?.lessons?.firstOrNull { it.id == saved.lessonId }
            lesson != null && saved.index in 1..lesson.exercises.size
        }
        val courseProgress = CourseProgress(content.course, content.units, completed, settings.unlockAllUnits)
        Overview(
            content = content,
            progress = courseProgress,
            completedLessons = completed,
            streak = Streak.current(days, today),
            practicedToday = today in days,
            dueReviews = counts.first,
            newCards = counts.second,
            weakDue = weakDue,
            checkpoint = checkpoint,
            lessonsToday = extra.lessonsToday,
            mixAvailable = completed.size >= DailyMix.MIN_LESSONS,
            mixDoneToday = extra.lastMixDay == today,
            examDateEpochDay = settings.examDateEpochDay,
            readiness = ExamReadiness.estimate(SkillStats.bySkill(extra.attempts, today)),
            remainingLessons = ExamPlan.remainingLessons(courseProgress.levelProgress()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OverviewViewModel(container) }
        }
    }
}
