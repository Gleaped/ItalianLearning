package com.gleaped.italianlearning.domain.reminders

import com.gleaped.italianlearning.content.Course
import com.gleaped.italianlearning.content.GrammarTip

/**
 * Chooses which grammar tip a reminder shows.
 *
 * Eligible units: those the learner has started (at least one lesson completed) plus
 * remedial units they have opened. When none of those has tips, the first available
 * main-path unit with tips is used, so a brand-new learner still gets a tip from where
 * they will start.
 *
 * Rotation: tips never shown come first (in course order); once every eligible tip has
 * been shown, the one shown longest ago comes next. So no tip repeats until all the
 * others have had a turn.
 */
object TipSelector {

    /** How many shown tip ids to remember; comfortably more than a whole course has. */
    const val HISTORY_LIMIT = 400

    /**
     * Eligible unit ids in course order (level order, then unit order).
     *
     * @param unitsWithTips ids of units whose file is loaded and has at least one tip
     *   (only needed for the fallback; started units are always listed).
     */
    fun eligibleUnitIds(
        course: Course,
        startedUnitIds: Set<String>,
        openedUnitIds: Set<String>,
        unitsWithTips: Set<String>,
    ): List<String> {
        val levelIndex = course.levels.mapIndexed { i, l -> l.id to i }.toMap()
        val remedial = course.levels.filter { it.alwaysOpen }.map { it.id }.toSet()
        val ordered = course.units
            .filter { it.available }
            .sortedWith(compareBy({ levelIndex[it.level] ?: Int.MAX_VALUE }, { it.order }))
        val chosen = ordered.filter { ref ->
            ref.id in startedUnitIds || (ref.level in remedial && ref.id in openedUnitIds)
        }.map { it.id }
        if (chosen.any { it in unitsWithTips }) return chosen
        val examOrPath = ordered.filter { it.level !in remedial }
        val fallback = (examOrPath + ordered).firstOrNull { it.id in unitsWithTips } ?: return chosen
        return chosen + fallback.id
    }

    /**
     * Picks the next tip from [candidates] (already in preferred order).
     * [history] lists shown tip ids, oldest first, most recent last; an id may appear
     * more than once (only its last showing counts).
     */
    fun pick(candidates: List<GrammarTip>, history: List<String>): GrammarTip? {
        if (candidates.isEmpty()) return null
        val lastShown = HashMap<String, Int>()
        history.forEachIndexed { i, id -> lastShown[id] = i }
        candidates.firstOrNull { it.id !in lastShown }?.let { return it }
        return candidates.minByOrNull { lastShown.getValue(it.id) }
    }

    /** History after showing [tipId]: moved to the end, trimmed to [HISTORY_LIMIT]. */
    fun recordShown(history: List<String>, tipId: String, limit: Int = HISTORY_LIMIT): List<String> =
        (history.filter { it != tipId } + tipId).takeLast(limit)
}
