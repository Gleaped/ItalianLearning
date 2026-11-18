package com.gleaped.italianlearning.domain.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One finished scene, kept for the learner's own progress. No audio and no text of what was said. */
@Serializable
data class SavedConversation(
    val scenarioId: String,
    val goalReached: Boolean,
    val understood: Int,
    val missed: Int,
    val corrections: Int,
    val savedAtMillis: Long,
)

/** The list of finished scenes and its storage format. Pure Kotlin. */
object ConversationLog {
    const val LIMIT = 300

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedConversation.serializer())

    fun encode(entries: List<SavedConversation>): String = json.encodeToString(serializer, entries)

    fun decode(raw: String?): List<SavedConversation> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entries: List<SavedConversation>, entry: SavedConversation): List<SavedConversation> =
        (listOf(entry) + entries).take(LIMIT)

    /** How many times each scenario was finished with its goal reached. */
    fun completions(entries: List<SavedConversation>): Map<String, Int> =
        entries.filter { it.goalReached }.groupingBy { it.scenarioId }.eachCount()
}

/** Checks a scenario for mistakes in its own structure. Used by the tests and the validator. */
object ScenarioCheck {
    fun problems(s: Scenario): List<String> {
        val out = ArrayList<String>()
        val ids = s.states.map { it.id }
        if (ids.size != ids.toSet().size) out += "duplicate state ids"
        if (s.start !in ids) out += "start ${s.start} is not a state"
        for (st in s.states) {
            if (st.say.isEmpty() || st.say.any { it.isBlank() }) out += "${st.id}: needs a line to say"
            if (!st.end && st.expect.isEmpty()) out += "${st.id}: not an end but expects nothing"
            if (st.end && st.expect.isNotEmpty()) out += "${st.id}: an end state must not expect answers"
            if (!st.end && st.otherwise == null) out += "${st.id}: needs otherwise so a stuck learner can move on"
            st.otherwise?.let { if (it !in ids) out += "${st.id}: otherwise $it is not a state" }
            for (b in st.expect) {
                if (b.next !in ids) out += "${st.id}: next ${b.next} is not a state"
                if (b.match.isEmpty()) out += "${st.id}: a branch has no words to match"
                b.capture?.let { if (it !in s.slots) out += "${st.id}: capture $it is not a slot" }
            }
        }
        val reachable = HashSet<String>()
        fun walk(id: String) {
            if (!reachable.add(id)) return
            val st = s.states.firstOrNull { it.id == id } ?: return
            st.expect.forEach { walk(it.next) }
            st.otherwise?.let { walk(it) }
        }
        if (s.start in ids) walk(s.start)
        (ids - reachable).forEach { out += "$it is never reached" }
        if (reachable.none { id -> s.states.first { it.id == id }.end }) out += "no end state is reachable"
        if (s.states.none { it.goal }) out += "no state marks the goal"
        return out
    }
}
