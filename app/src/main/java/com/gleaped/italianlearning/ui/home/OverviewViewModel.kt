package com.gleaped.italianlearning.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.data.content.CourseContent
import com.gleaped.italianlearning.domain.progress.CourseProgress
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
)

class OverviewViewModel(container: AppContainer) : ViewModel() {

    private val contentFlow = flow { emit(container.content.load()) }

    private val reviewCounts = combine(
        container.learning.dueReviewCount(),
        container.learning.newCardCount(),
        container.settings.settings,
    ) { due, fresh, settings -> due to minOf(fresh, settings.dailyNewCards) }

    val overview: StateFlow<Overview?> = combine(
        contentFlow,
        container.learning.completedLessonIds,
        container.learning.activeDays,
        reviewCounts,
        container.settings.settings,
    ) { content, completed, days, counts, settings ->
        val today = LocalDate.now().toEpochDay()
        Overview(
            content = content,
            progress = CourseProgress(content.course, content.units, completed, settings.unlockAllUnits),
            completedLessons = completed,
            streak = Streak.current(days, today),
            practicedToday = today in days,
            dueReviews = counts.first,
            newCards = counts.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OverviewViewModel(container) }
        }
    }
}
