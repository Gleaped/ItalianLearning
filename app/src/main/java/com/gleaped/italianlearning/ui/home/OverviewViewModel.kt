package com.gleaped.italianlearning.ui.home

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
)

class OverviewViewModel(container: AppContainer) : ViewModel() {

    private val contentFlow = flow { emit(container.content.load()) }

    private val reviewCounts = combine(
        container.learning.dueReviewCount(),
        container.learning.newCardCount(),
        container.learning.newIntroducedTodayCount(),
        container.settings.settings,
    ) { due, fresh, introducedToday, settings ->
        // Same rule as the review queue: new cards already met today use up the daily allowance.
        val allowance = (settings.dailyNewCards - introducedToday).coerceAtLeast(0)
        due to minOf(fresh, allowance)
    }

    /** Review counts, the answer history and the saved lesson position, bundled to stay within combine's arity. */
    private class Extras(val counts: Pair<Int, Int>, val attempts: List<AttemptRecord>, val checkpoint: LessonCheckpoint?)

    private val extras = combine(
        reviewCounts,
        container.learning.attempts,
        container.settings.lessonCheckpoint,
    ) { counts, attempts, checkpoint -> Extras(counts, attempts, checkpoint) }

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
        Overview(
            content = content,
            progress = CourseProgress(content.course, content.units, completed, settings.unlockAllUnits),
            completedLessons = completed,
            streak = Streak.current(days, today),
            practicedToday = today in days,
            dueReviews = counts.first,
            newCards = counts.second,
            weakDue = weakDue,
            checkpoint = checkpoint,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OverviewViewModel(container) }
        }
    }
}
