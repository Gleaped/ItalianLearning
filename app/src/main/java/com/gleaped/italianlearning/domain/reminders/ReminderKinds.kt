package com.gleaped.italianlearning.domain.reminders

import com.gleaped.italianlearning.content.GrammarTip

/** What a practice reminder is about. */
enum class ReminderKind { GRAMMAR, REVIEW, NEXT_LESSON }

/**
 * Rotation of reminder kinds. Grammar tips with a challenge are preferred (two in
 * three), the third slot alternates between vocabulary review and the next lesson:
 * G G R G G N, then again.
 *
 * [order] returns every allowed kind, starting with the one due at [sequence] and then
 * in rotation order, so the caller can fall back when a kind has nothing to show
 * (no tips yet, no cards due, course finished).
 */
object ReminderRotation {
    val pattern: List<ReminderKind> = listOf(
        ReminderKind.GRAMMAR,
        ReminderKind.GRAMMAR,
        ReminderKind.REVIEW,
        ReminderKind.GRAMMAR,
        ReminderKind.GRAMMAR,
        ReminderKind.NEXT_LESSON,
    )

    fun order(sequence: Long, allowed: Set<ReminderKind>): List<ReminderKind> {
        val start = Math.floorMod(sequence, pattern.size.toLong()).toInt()
        val result = LinkedHashSet<ReminderKind>()
        for (i in pattern.indices) {
            val kind = pattern[(start + i) % pattern.size]
            if (kind in allowed) result += kind
        }
        return result.toList()
    }
}

/** Where tapping a reminder leads. */
sealed interface ReminderTarget {
    data object Today : ReminderTarget
    data object Review : ReminderTarget
    data class Challenge(val unitId: String, val tipId: String) : ReminderTarget
}

/**
 * A composed reminder. [bigText] is the expanded body; [actionLabel] adds a button
 * that leads to [target] as well.
 */
data class ReminderContent(
    val kind: ReminderKind?,
    val title: String,
    val body: String,
    val bigText: String = body,
    val target: ReminderTarget = ReminderTarget.Today,
    val actionLabel: String? = null,
    val tipId: String? = null,
)

/** Builds gentle reminder copy for each kind. No guilt, no streak talk. */
object ReminderComposer {
    const val CHALLENGE_ACTION = "Prova la sfida"

    fun grammar(tip: GrammarTip): ReminderContent {
        val big = buildString {
            append(tip.tip)
            tip.example?.let { ex ->
                append("\n\n")
                append(ex.it)
                if (ex.en.isNotBlank()) append("\n").append(ex.en)
            }
            if (tip.challenge != null) append("\n\nPronto per una piccola sfida?")
        }
        return ReminderContent(
            kind = ReminderKind.GRAMMAR,
            title = tip.rule,
            body = tip.tip,
            bigText = big,
            target = if (tip.challenge != null) ReminderTarget.Challenge(tip.unitId, tip.id) else ReminderTarget.Today,
            actionLabel = if (tip.challenge != null) CHALLENGE_ACTION else null,
            tipId = tip.id,
        )
    }

    private val reviewTitles = listOf(
        "Qualche parola da ripassare?",
        "Due minuti di ripasso?",
        "Le tue parole ti aspettano",
    )

    fun review(cardCount: Int, seed: Long): ReminderContent {
        val title = reviewTitles[Math.floorMod(seed, reviewTitles.size.toLong()).toInt()]
        val body = if (cardCount == 1) {
            "One card is ready for a quick review, whenever you like."
        } else {
            "$cardCount cards are ready for a quick review, whenever you like."
        }
        return ReminderContent(ReminderKind.REVIEW, title, body, target = ReminderTarget.Review)
    }

    private val lessonTitles = listOf(
        "Pronto per la prossima lezione?",
        "Piano piano si va lontano",
        "Un passo avanti?",
        "Un caffè e una lezione? ☕",
    )
    private val lessonTitlesWelcomeBack = listOf(
        "Bentornato quando vuoi",
        "Nessuna fretta",
        "Un passo alla volta",
    )
    private val lessonTitlesPracticed = listOf(
        "Bravo, oggi hai già studiato!",
        "Ancora un po'?",
    )

    /** Every fixed title, for tone tests. */
    val allFixedTitles: List<String> get() = reviewTitles + lessonTitles + lessonTitlesWelcomeBack + lessonTitlesPracticed

    fun nextLesson(
        lessonTitle: String,
        unitTitle: String,
        minutes: Int,
        seed: Long,
        inactive: Boolean,
        practicedToday: Boolean,
    ): ReminderContent {
        val titles = when {
            inactive -> lessonTitlesWelcomeBack
            practicedToday -> lessonTitlesPracticed
            else -> lessonTitles
        }
        val title = titles[Math.floorMod(seed, titles.size.toLong()).toInt()]
        val body = "Next up: $lessonTitle ($unitTitle), about $minutes min. " +
            if (inactive) "No rush, it's there when you are." else "Whenever suits you."
        return ReminderContent(ReminderKind.NEXT_LESSON, title, body, target = ReminderTarget.Today)
    }

    /** v1 copy, used when no kind has anything to show or every kind is switched off. */
    fun generic(seed: Long, inactive: Boolean, practicedToday: Boolean): ReminderContent {
        val m = ReminderMessages.pick(seed, inactive, practicedToday)
        return ReminderContent(null, m.title, m.body, target = ReminderTarget.Today)
    }
}
