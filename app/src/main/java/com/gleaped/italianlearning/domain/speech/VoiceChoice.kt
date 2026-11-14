package com.gleaped.italianlearning.domain.speech

/** Which installed Italian voice to use. Pure Kotlin, so the choice can be unit-tested. */
object VoiceChoice {

    /** [quality] is Android's Voice.quality (higher is better); [italy] is true for it-IT. */
    data class Candidate(val name: String, val quality: Int, val italy: Boolean)

    /** Voice names, best first: quality, then Italy before other Italian locales, then name. */
    fun rank(candidates: List<Candidate>): List<String> =
        candidates.sortedWith(
            compareByDescending<Candidate> { it.quality }.thenByDescending { it.italy }.thenBy { it.name },
        ).map { it.name }

    /** The learner's [preferred] voice when it is still installed, otherwise the best one. */
    fun main(ranked: List<String>, preferred: String): String? =
        ranked.firstOrNull { it == preferred } ?: ranked.firstOrNull()

    /** The best voice other than [main], for the second speaker of a dialogue; null when there is none. */
    fun second(ranked: List<String>, main: String?): String? = ranked.firstOrNull { it != main }
}
