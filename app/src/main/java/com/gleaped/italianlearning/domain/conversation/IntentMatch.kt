package com.gleaped.italianlearning.domain.conversation

import com.gleaped.italianlearning.domain.grading.AnswerNormalizer

/**
 * Decides whether what the learner said carries an intent. Speech recognisers slip and
 * learners inflect, so words match when they are equal, one letter apart, or share a long
 * stem ("prenotato" and "prenotazione"). Pure Kotlin.
 */
object IntentMatch {

    fun tokens(text: String): List<String> = AnswerNormalizer.tokens(text)

    fun wordMatches(heard: String, wanted: String): Boolean {
        if (heard == wanted) return true
        if (heard.length < 5 || wanted.length < 5) return false
        if (distanceAtMostOne(heard, wanted)) return true
        val shorter = minOf(heard.length, wanted.length)
        val prefix = commonPrefix(heard, wanted)
        return prefix >= 5 && prefix * 10 >= shorter * 6
    }

    /** The number of words in [pattern] when every one of them was heard, otherwise 0. */
    fun score(heard: List<String>, pattern: String): Int {
        val words = tokens(pattern)
        if (words.isEmpty()) return 0
        return if (words.all { w -> heard.any { wordMatches(it, w) } }) words.size else 0
    }

    /** The best [score] of any of [patterns]. */
    fun best(heard: List<String>, patterns: List<String>): Int = patterns.maxOfOrNull { score(heard, it) } ?: 0

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun distanceAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        if (i == a.length && i == b.length) return true
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            a.length > b.length -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
    }
}
