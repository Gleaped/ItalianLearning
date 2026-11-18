package com.gleaped.italianlearning.domain.conversation

import kotlin.random.Random

/**
 * Runs one [Scenario]: says the partner's lines, works out which answer the learner gave,
 * remembers slots, repairs like a real person and moves on when the learner is stuck.
 * No Android and no network, so every rule here is unit-tested. One instance is one scene.
 */
class ConversationEngine(
    val scenario: Scenario,
    private val random: Random = Random.Default,
) {
    private val states = scenario.states.associateBy { it.id }

    var current: ConvState = states.getValue(scenario.start)
        private set

    /** What the learner has said that a slot remembers, e.g. dish to "il tiramisù". */
    val slots: MutableMap<String, String> = linkedMapOf()

    private var understood = 0
    private var missed = 0
    private var triesHere = 0
    private var goalReached = current.goal
    private var lastSaid = ""
    private val lastChoice = HashMap<String, Int>()
    private val found = LinkedHashMap<String, Correction>()

    val ended: Boolean get() = current.end

    /** The partner's opening line. */
    fun open(): Response {
        val line = say(current)
        return Response(listOf(line), Response.Kind.MATCHED, ended = current.end)
    }

    /**
     * The learner answered with [alternatives], the recogniser's guesses (best first) or the typed text.
     * [skipped] means the learner gave up on this turn, which counts as stuck at once.
     */
    fun respond(alternatives: List<String>, skipped: Boolean = false): Response {
        val heardAll = alternatives.map { IntentMatch.tokens(it) }.filter { it.isNotEmpty() }
        val corrections = alternatives.firstOrNull()?.let { LearnerErrors.find(it) }.orEmpty()
            .also { list -> list.forEach { found.putIfAbsent(it.id, it) } }

        // A polite request to say it again beats any answer that merely contains "per favore".
        if (!skipped && heardAll.isNotEmpty() && asksToRepeat(heardAll)) {
            return Response(listOf(lastSaid), Response.Kind.REPEAT, corrections, slow = true)
        }

        val hit = if (skipped) null else bestBranch(heardAll)
        if (hit != null) {
            val (branch, heard) = hit
            understood++
            branch.capture?.let { name -> capture(name, heard)?.let { slots[name] = it } }
            val lines = ArrayList<String>()
            pick(branch.reply, "r:${current.id}:${branch.next}")?.let { lines += fill(it) }
            val next = states.getValue(branch.next)
            current = next
            triesHere = 0
            if (next.goal) goalReached = true
            lines += say(next)
            return Response(lines, Response.Kind.MATCHED, corrections, ended = next.end)
        }

        missed++
        triesHere = if (skipped) maxOf(triesHere + 1, MAX_TRIES) else triesHere + 1
        val otherwise = current.otherwise?.let { states[it] }
        if (triesHere >= MAX_TRIES && otherwise != null) {
            current = otherwise
            triesHere = 0
            if (otherwise.goal) goalReached = true
            return Response(
                listOf(say(otherwise)), Response.Kind.MOVED_ON, corrections,
                hint = null, ended = otherwise.end,
            )
        }
        val repair = pick(current.reprompt, "p:${current.id}")?.let { fill(it) } ?: DEFAULT_REPROMPT
        lastSaid = repair
        val stuck = triesHere >= MAX_TRIES
        return Response(
            listOf(repair), Response.Kind.REPAIR, corrections,
            hint = if (stuck) current.hint else null,
            model = if (stuck) current.model?.let { fill(it) } else null,
            slow = triesHere >= 1,
        )
    }

    /** The help for the current moment, for a learner who asks for it. */
    fun help(): Pair<String?, String?> = current.hint to current.model?.let { fill(it) }

    fun debrief(): Debrief = Debrief(
        goalReached = goalReached,
        understood = understood,
        missed = missed,
        corrections = found.values.toList(),
        phrases = scenario.phrases,
    )

    // ------------------------------------------------------------------ internals

    /** The first branch, in the order the author wrote them, that any of the guesses carries. */
    private fun bestBranch(heardAll: List<List<String>>): Pair<Branch, List<String>>? {
        for (branch in current.expect) {
            for (heard in heardAll.take(MAX_GUESSES)) {
                if (IntentMatch.best(heard, branch.match) > 0) return branch to heard
            }
        }
        return null
    }

    private fun capture(name: String, heard: List<String>): String? {
        val options = scenario.slots[name].orEmpty()
        return options
            .map { it to IntentMatch.score(heard, it) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun asksToRepeat(heardAll: List<List<String>>): Boolean =
        heardAll.any { heard -> REPEAT_REQUESTS.any { IntentMatch.score(heard, it) > 0 } }

    /** A line of the partner for [state], with slots filled; remembered for a request to repeat. */
    private fun say(state: ConvState): String {
        val line = fill(pick(state.say, "s:${state.id}").orEmpty())
        lastSaid = line
        return line
    }

    /** A variant that is not the one used last time for [key], when there is a choice. */
    private fun pick(options: List<String>, key: String): String? {
        if (options.isEmpty()) return null
        if (options.size == 1) return options[0]
        val last = lastChoice[key]
        val candidates = options.indices.filter { it != last }
        val index = candidates[random.nextInt(candidates.size)]
        lastChoice[key] = index
        return options[index]
    }

    /** Fills `{slot}` and `{slot|default}` from what the learner has said so far. */
    fun fill(text: String): String = SLOT.replace(text) { m ->
        slots[m.groupValues[1]] ?: m.groupValues[2]
    }.replace(Regex("\\s+"), " ").replace(Regex("\\s+([,.;:!?])"), "$1").trim()
        .replaceFirstChar { it.uppercase() }

    companion object {
        const val MAX_TRIES = 2
        private const val MAX_GUESSES = 3
        const val DEFAULT_REPROMPT = "Come, scusi?"
        private val SLOT = Regex("\\{(\\w+)(?:\\|([^}]*))?\\}")
        private val REPEAT_REQUESTS = listOf(
            "può ripetere", "puoi ripetere", "ripeti", "ripetere", "non ho capito", "più piano",
            "più lentamente", "lentamente", "un'altra volta", "ancora una volta",
        )
    }
}
