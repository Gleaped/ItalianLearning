package com.gleaped.italianlearning.reminders

import com.gleaped.italianlearning.domain.progress.SkillStats
import java.time.LocalDate
import com.gleaped.italianlearning.content.GrammarTip
import com.gleaped.italianlearning.data.LearningRepository
import com.gleaped.italianlearning.data.content.ContentRepository
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.domain.progress.CourseProgress
import com.gleaped.italianlearning.domain.reminders.ReminderComposer
import com.gleaped.italianlearning.domain.reminders.ReminderContent
import com.gleaped.italianlearning.domain.reminders.ReminderKind
import com.gleaped.italianlearning.domain.reminders.ReminderRotation
import com.gleaped.italianlearning.domain.reminders.TipSelector
import kotlinx.coroutines.flow.first

/**
 * Decides what the next reminder says. Tries kinds in [ReminderRotation] order and
 * uses the first that has something to show; falls back to the v1 generic copy.
 *
 * Only the units needed are parsed: grammar tips read the started / opened units (plus
 * the first unit as a fallback); the next-lesson kind needs the whole course and is
 * only built when it is that kind's turn.
 */
class ReminderContentBuilder(
    private val content: ContentRepository,
    private val learning: LearningRepository,
    private val settings: SettingsRepository,
) {
    suspend fun build(seed: Long, inactive: Boolean, practicedToday: Boolean): ReminderContent {
        val s = settings.current()
        val state = settings.currentReminderState()
        val ready = learning.reviewReadyCount(s.dailyNewCards)
        for (kind in ReminderRotation.order(state.sequence, s.allowedReminderKinds, ready)) {
            val built = when (kind) {
                ReminderKind.GRAMMAR -> {
                    // Every second grammar reminder aims at units with recent mistakes.
                    val missed = if (Math.floorMod(state.sequence, 2L) == 0L) {
                        SkillStats.recentlyMissedUnits(learning.attemptsNow(), LocalDate.now().toEpochDay())
                    } else {
                        emptySet()
                    }
                    nextTip(state.tipHistory, state.openedUnitIds, missed)?.let { ReminderComposer.grammar(it, seed) }
                }
                ReminderKind.REVIEW -> ready
                    .takeIf { it > 0 }
                    ?.let { ReminderComposer.review(it, seed) }
                ReminderKind.NEXT_LESSON -> nextLesson(s.unlockAllUnits, seed, inactive, practicedToday)
            }
            if (built != null) return built
        }
        return ReminderComposer.generic(seed, inactive, practicedToday)
    }

    /** The reminder for one known tip: used when "Later" brings a grammar reminder back. */
    suspend fun forTip(unitId: String, tipId: String, seed: Long): ReminderContent? =
        tip(unitId, tipId)?.let { ReminderComposer.grammar(it, seed) }

    suspend fun tip(unitId: String, tipId: String): GrammarTip? =
        content.unit(unitId)?.grammarTips?.firstOrNull { it.id == tipId }

    suspend fun nextTip(history: List<String>, openedUnitIds: Set<String>, priorityUnitIds: Set<String> = emptySet()): GrammarTip? {
        val course = content.course()
        val started = learning.startedUnitIds()
        // Units that could contribute: started, opened remedial, and the fallback candidates.
        var withTips = loadTips(
            TipSelector.eligibleUnitIds(course, started, openedUnitIds, unitsWithTips = emptySet()),
        )
        var eligible = TipSelector.eligibleUnitIds(course, started, openedUnitIds, withTips.keys)
        if (eligible.none { it in withTips }) {
            // Nobody started yet (or started units have no tips): look for the first unit with tips,
            // parsing units one by one in course order until one has some.
            val levelIndex = course.levels.mapIndexed { i, l -> l.id to i }.toMap()
            val remedial = course.levels.filter { it.alwaysOpen }.map { it.id }.toSet()
            val ordered = course.units.filter { it.available }
                .sortedWith(compareBy({ if (it.level in remedial) 1 else 0 }, { levelIndex[it.level] ?: Int.MAX_VALUE }, { it.order }))
            for (ref in ordered) {
                val unit = content.unit(ref.id) ?: continue
                if (unit.grammarTips.isNotEmpty()) {
                    withTips = withTips + (unit.id to unit.grammarTips)
                    break
                }
            }
            eligible = TipSelector.eligibleUnitIds(course, started, openedUnitIds, withTips.keys)
        }
        val candidates = eligible.flatMap { withTips[it].orEmpty() }
        return TipSelector.pick(candidates, history, priorityUnitIds)
    }

    private suspend fun loadTips(unitIds: List<String>): Map<String, List<GrammarTip>> {
        val result = LinkedHashMap<String, List<GrammarTip>>()
        for (id in unitIds) {
            val tips = content.unit(id)?.grammarTips.orEmpty()
            if (tips.isNotEmpty()) result[id] = tips
        }
        return result
    }

    private suspend fun nextLesson(unlockAll: Boolean, seed: Long, inactive: Boolean, practicedToday: Boolean): ReminderContent? {
        val all = content.load()
        val completed = learning.completedLessonIds.first()
        val (unit, lesson) = CourseProgress(all.course, all.units, completed, unlockAll).nextLesson() ?: return null
        return ReminderComposer.nextLesson(lesson.title, unit.title, lesson.minutes, seed, inactive, practicedToday)
    }
}
