package com.gleaped.italianlearning.domain.reminders

import com.gleaped.italianlearning.content.MultipleChoice
import kotlin.random.Random
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

    /** With this many cards ready, review takes every other reminder, whatever the rotation says. */
    const val REVIEW_PRESSURE = 20

    /**
     * [order], adjusted to need: when [reviewReady] cards are waiting and there are many of
     * them, every second reminder starts with review.
     */
    fun order(sequence: Long, allowed: Set<ReminderKind>, reviewReady: Int): List<ReminderKind> {
        val base = order(sequence, allowed)
        val pressing = reviewReady >= REVIEW_PRESSURE && ReminderKind.REVIEW in allowed && Math.floorMod(sequence, 2L) == 0L
        return if (pressing) listOf(ReminderKind.REVIEW) + base.filter { it != ReminderKind.REVIEW } else base
    }

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
    /** The dictionary search (from a launcher shortcut). */
    data object Lookup : ReminderTarget
    data class Challenge(val unitId: String, val tipId: String) : ReminderTarget
}

/**
 * A question answered on the notification itself: [options] become its buttons.
 * [unitId] and [tipId] let the answer be checked when a button is tapped.
 */
data class ReminderQuiz(val unitId: String, val tipId: String, val options: List<String>)

/**
 * A composed reminder. [bigText] is the expanded body; [actionLabel] adds a button
 * that leads to [target] as well; [quiz] puts answer buttons on the notification instead.
 */
data class ReminderContent(
    val kind: ReminderKind?,
    val title: String,
    val body: String,
    val bigText: String = body,
    val target: ReminderTarget = ReminderTarget.Today,
    val actionLabel: String? = null,
    val tipId: String? = null,
    val quiz: ReminderQuiz? = null,
)

/** Builds gentle reminder copy for each kind. No guilt, no streak talk. */
object ReminderComposer {
    const val CHALLENGE_ACTION = "Prova la sfida"

    /** Buttons on a notification: Android shows three at most, and long labels are cut off. */
    const val MAX_QUIZ_OPTIONS = 3
    const val MAX_QUIZ_OPTION_LENGTH = 22

    /**
     * The options to offer for a tip's multiple-choice challenge, in an order that depends on
     * [seed]: the right answer and up to two wrong ones. Null when the challenge is of another
     * type or an option is too long for a button.
     */
    fun quizOptions(tip: GrammarTip, seed: Long): List<String>? {
        val challenge = tip.challenge as? MultipleChoice ?: return null
        val right = challenge.options.getOrNull(challenge.answer) ?: return null
        if (challenge.options.size < 2 || challenge.options.any { it.length > MAX_QUIZ_OPTION_LENGTH }) return null
        val random = Random(seed)
        val wrong = challenge.options.filterIndexed { i, _ -> i != challenge.answer }.shuffled(random).take(MAX_QUIZ_OPTIONS - 1)
        return (wrong + right).shuffled(random)
    }

    /** Whether [chosen] is the right answer to the tip's multiple-choice challenge. */
    fun isRight(tip: GrammarTip, chosen: String): Boolean {
        val challenge = tip.challenge as? MultipleChoice ?: return false
        return challenge.options.getOrNull(challenge.answer) == chosen
    }

    /** What replaces a quiz notification once an option has been tapped: the verdict and the rule. */
    fun quizResult(tip: GrammarTip, chosen: String): ReminderContent {
        val challenge = tip.challenge as? MultipleChoice
        val right = challenge?.options?.getOrNull(challenge.answer).orEmpty()
        val correct = isRight(tip, chosen)
        val sentence = challenge?.prompt?.replace(Regex("_{2,}"), right).orEmpty()
        val body = if (correct) "$right. ${tip.tip}" else "The answer is “$right”. ${tip.tip}"
        val big = buildString {
            if (sentence.isNotBlank()) append(sentence).append("\n\n")
            if (!correct) append("You chose “").append(chosen).append("”. The answer is “").append(right).append("”.\n")
            append(tip.tip)
            challenge?.explanation?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
        return ReminderContent(
            kind = ReminderKind.GRAMMAR,
            title = if (correct) "Giusto! ✓" else "Non proprio",
            body = body,
            bigText = big,
            target = ReminderTarget.Challenge(tip.unitId, tip.id),
            tipId = tip.id,
        )
    }

    fun grammar(tip: GrammarTip, seed: Long = 0): ReminderContent {
        val options = quizOptions(tip, seed)
        if (options != null) {
            val prompt = (tip.challenge as MultipleChoice).prompt
            // The rule itself would give the answer away, so it comes with the result.
            return ReminderContent(
                kind = ReminderKind.GRAMMAR,
                title = tip.rule,
                body = prompt,
                bigText = prompt,
                target = ReminderTarget.Challenge(tip.unitId, tip.id),
                tipId = tip.id,
                quiz = ReminderQuiz(tip.unitId, tip.id, options),
            )
        }
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
