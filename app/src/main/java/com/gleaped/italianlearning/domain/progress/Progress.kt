package com.gleaped.italianlearning.domain.progress

import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.CourseUnit
import com.gleaped.italianlearning.content.Lesson
import com.gleaped.italianlearning.content.UnitRef

/** Streak = consecutive days with activity, ending today (or yesterday, if today is still open). */
object Streak {
    fun current(activeEpochDays: Collection<Long>, todayEpochDay: Long): Int {
        if (activeEpochDays.isEmpty()) return 0
        val days = activeEpochDays.toHashSet()
        var day = if (todayEpochDay in days) todayEpochDay else todayEpochDay - 1
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }
}

enum class UnitStatus { UNLOCKED, LOCKED, COMING_SOON }

/**
 * Course navigation rules:
 *  - remedial and foundation (A1, A2) units are always open when available
 *  - exam-preparation units (CELI3) are always open when available; they are not part
 *    of the main path
 *  - units whose level is not declared in course.json are treated like exam units
 *    (open, off the main path) rather than being hidden or blocking the path
 *  - main-path units (B1 → B2 → C1, by order) open one after another: a unit is
 *    open when every lesson of the previous available unit is complete
 *  - `available: false` units are "coming soon"
 */
class CourseProgress(
    private val course: Course,
    private val units: Map<String, CourseUnit>,
    private val completedLessons: Set<String>,
    private val unlockAll: Boolean = false,
) {
    private val remedialLevels: Set<String> = course.levels.filter { it.alwaysOpen }.map { it.id }.toSet()
    private val pathLevels: Set<String> = course.levels.filter { !it.alwaysOpen && !it.isExam }.map { it.id }.toSet()
    private val levelOrder: Map<String, Int> = course.levels.mapIndexed { i, l -> l.id to i }.toMap()

    /** Main path in study order (B1 → B2 → C1: by level order then unit order). */
    val mainPath: List<UnitRef> = course.units
        .filter { it.level in pathLevels }
        .sortedWith(compareBy({ levelOrder[it.level] ?: Int.MAX_VALUE }, { it.order }))

    fun isRemedial(ref: UnitRef): Boolean = ref.level in remedialLevels

    /** Open-access units outside the main path: exam prep and units of undeclared levels. */
    fun isOffPath(ref: UnitRef): Boolean = ref.level !in remedialLevels && ref.level !in pathLevels

    fun isUnitComplete(unitId: String): Boolean {
        val unit = units[unitId] ?: return false
        return unit.lessons.isNotEmpty() && unit.lessons.all { it.id in completedLessons }
    }

    fun status(ref: UnitRef): UnitStatus {
        if (!ref.available || units[ref.id] == null) return UnitStatus.COMING_SOON
        if (unlockAll || isRemedial(ref) || isOffPath(ref)) return UnitStatus.UNLOCKED
        val index = mainPath.indexOfFirst { it.id == ref.id }
        if (index <= 0) return UnitStatus.UNLOCKED
        val previous = mainPath.subList(0, index).lastOrNull { it.available && units[it.id] != null }
            ?: return UnitStatus.UNLOCKED
        return if (isUnitComplete(previous.id)) UnitStatus.UNLOCKED else UnitStatus.LOCKED
    }

    /** First incomplete lesson on the main path that is unlocked. */
    fun nextLesson(): kotlin.Pair<CourseUnit, Lesson>? {
        for (ref in mainPath) {
            if (status(ref) != UnitStatus.UNLOCKED) continue
            val unit = units[ref.id] ?: continue
            val lesson = unit.lessons.firstOrNull { it.id !in completedLessons } ?: continue
            return unit to lesson
        }
        return null
    }

    data class LevelProgress(val levelId: String, val title: String, val completed: Int, val total: Int, val plannedUnits: Int, val availableUnits: Int) {
        val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
    }

    fun levelProgress(): List<LevelProgress> = course.levels.map { level ->
        val refs = course.units.filter { it.level == level.id }
        val lessons = refs.mapNotNull { units[it.id] }.flatMap { it.lessons }
        LevelProgress(
            levelId = level.id,
            title = level.title,
            completed = lessons.count { it.id in completedLessons },
            total = lessons.size,
            plannedUnits = refs.size,
            availableUnits = refs.count { it.available && units[it.id] != null },
        )
    }
}
