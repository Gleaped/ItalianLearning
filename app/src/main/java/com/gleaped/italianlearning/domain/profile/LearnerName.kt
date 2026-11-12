package com.gleaped.italianlearning.domain.profile

/**
 * The learner's name in content: unit files write `{name}` wherever a dialogue addresses or
 * introduces the learner, and the loader swaps in the chosen name before parsing.
 */
object LearnerName {
    const val PLACEHOLDER = "{name}"
    const val MAX_LENGTH = 30

    /** Trims, collapses spaces, drops characters that could break JSON or markup, caps the length. */
    fun clean(raw: String): String = raw
        .replace(Regex("[\"\\\\{}<>*=|\\p{Cntrl}]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_LENGTH)
        .trim()

    /** Replaces every placeholder in raw unit JSON. The name is cleaned, so it is JSON-safe. */
    fun substitute(rawJson: String, name: String): String {
        if (!rawJson.contains(PLACEHOLDER)) return rawJson
        return rawJson.replace(PLACEHOLDER, clean(name))
    }
}
