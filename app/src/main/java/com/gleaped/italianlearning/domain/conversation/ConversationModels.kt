package com.gleaped.italianlearning.domain.conversation

import com.gleaped.italianlearning.content.ItEn
import kotlinx.serialization.Serializable

/**
 * A role-play that reacts to what the learner says. Written as JSON in assets/conversations,
 * one scenario per file (see docs/conversation-format.md). The level is any CEFR level from A1
 * to C1, so the same engine serves a first-day learner and an exam candidate.
 */
@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val level: String = "",
    /** One English sentence that sets the scene. */
    val setting: String = "",
    /** What the learner is trying to achieve, in English. */
    val goal: String = "",
    /** The partner's name or role in Italian, shown above their lines. */
    val partner: String = "",
    val minutes: Int = 5,
    /** Words the learner may say that the partner remembers and repeats later: slot name to options. */
    val slots: Map<String, List<String>> = emptyMap(),
    /** The id of the state the conversation opens in. */
    val start: String,
    val states: List<ConvState>,
    /** Useful phrases shown at the end. */
    val phrases: List<ItEn> = emptyList(),
)

/** One moment of the conversation: what the partner says, and what the learner may answer. */
@Serializable
data class ConvState(
    val id: String,
    /** Variants of the partner's line; one is chosen each time. `{slot|default}` is filled from what the learner said. */
    val say: List<String>,
    val expect: List<Branch> = emptyList(),
    /** What the partner says when the answer was not understood. */
    val reprompt: List<String> = emptyList(),
    /** English help shown when the learner is stuck. */
    val hint: String? = null,
    /** A model answer in Italian, shown with the hint. */
    val model: String? = null,
    /** Where the conversation goes when the learner cannot answer after two tries. */
    val otherwise: String? = null,
    /** True for the last moment: the partner says goodbye and the conversation ends. */
    val end: Boolean = false,
    /** True for the state that means the learner reached the goal of the scene. */
    val goal: Boolean = false,
)

/** One kind of answer the learner may give in a state. */
@Serializable
data class Branch(
    /** Words or short phrases that signal this answer; a phrase counts when all its words were heard, in any order. */
    val match: List<String>,
    val next: String,
    /** The partner's quick reaction before the next state's line. */
    val reply: List<String> = emptyList(),
    /** A slot of the scenario to fill from what the learner said. */
    val capture: String? = null,
)

/** A mistake spotted in what the learner said, with the usual way to say it. */
data class Correction(val id: String, val said: String, val better: String, val note: String)

/** What the engine gives back after a learner turn. */
data class Response(
    /** The partner's lines, in order. */
    val lines: List<String>,
    val kind: Kind,
    /** A gentle correction of something said, if any. */
    val corrections: List<Correction> = emptyList(),
    /** English help and a model answer, when the learner is stuck. */
    val hint: String? = null,
    val model: String? = null,
    val ended: Boolean = false,
    /** True when the partner should speak slowly, as after a request to repeat. */
    val slow: Boolean = false,
) {
    enum class Kind { MATCHED, REPEAT, REPAIR, MOVED_ON }
}

/** The result of one scene, for the debrief and the progress log. */
data class Debrief(
    val goalReached: Boolean,
    val understood: Int,
    val missed: Int,
    val corrections: List<Correction>,
    val phrases: List<ItEn>,
)
