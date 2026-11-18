package com.gleaped.italianlearning.domain.conversation

import com.gleaped.italianlearning.domain.grading.AnswerNormalizer

/**
 * Common slips of learners that can be spotted in a transcript with a pattern. They are
 * shown as a gentle "si dice" note after the partner's reply, never as a red mark. Pure Kotlin.
 */
object LearnerErrors {

    private class Rule(val id: String, val pattern: Regex, val note: String, val better: (MatchResult) -> String)

    private val SUBJUNCTIVE = mapOf(
        "è" to "sia", "ha" to "abbia", "sono" to "siano", "hanno" to "abbiano", "va" to "vada", "fa" to "faccia",
        "può" to "possa", "vuole" to "voglia", "sa" to "sappia", "viene" to "venga",
    )
    private val IF_CLAUSE = mapOf(
        "avrei" to "avessi", "sarei" to "fossi", "potrei" to "potessi", "vorrei" to "volessi", "farei" to "facessi",
    )

    private val RULES = listOf(
        Rule(
            "un-acqua", Regex("(?<!\\p{L})un (acqua|amica|ora|isola|idea|opinione|erba|estate|università|attrice|arancia)(?!\\p{L})"),
            "Feminine nouns that start with a vowel take un', with an apostrophe.",
        ) { "un'" + it.groupValues[1] },
        Rule(
            "age-essere", Regex("(?<!\\p{L})(?:io )?sono (\\d{1,3}) anni(?!\\p{L})"),
            "Age uses avere in Italian, not essere.",
        ) { "ho " + it.groupValues[1] + " anni" },
        Rule(
            "essere-verbs", Regex("(?<!\\p{L})ho (andato|stato|venuto|uscito|partito|arrivato|tornato|entrato|rimasto|nato|salito|sceso)(?!\\p{L})"),
            "Verbs of movement and state take essere in the passato prossimo.",
        ) { "sono " + it.groupValues[1] },
        Rule(
            "piu-meglio", Regex("(?<!\\p{L})più (meglio|peggio|migliore|peggiore)(?!\\p{L})"),
            "Meglio and peggio are already comparatives, so più is not needed.",
        ) { it.groupValues[1] },
        Rule(
            "piace-plural", Regex("(?<!\\p{L})mi piace (i|le|gli) (\\p{L}+)"),
            "Piacere agrees with the thing you like: one thing, piace; several, piacciono.",
        ) { "mi piacciono " + it.groupValues[1] + " " + it.groupValues[2] },
        Rule(
            "la-problema", Regex("(?<!\\p{L})la (problema|programma|sistema|tema|clima)(?!\\p{L})"),
            "Problema, programma, sistema, tema and clima are masculine: il problema.",
        ) { "il " + it.groupValues[1] },
        Rule(
            "uno-amico", Regex("(?<!\\p{L})uno (amico|albero|inverno|ospite|uomo|esame|ufficio|incontro)(?!\\p{L})"),
            "Before a masculine noun starting with a vowel, uno becomes un.",
        ) { "un " + it.groupValues[1] },
        Rule(
            "un-zaino", Regex("(?<!\\p{L})un (zaino|studente|psicologo|spagnolo|sbaglio|scherzo|zio|stato|sport)(?!\\p{L})"),
            "Before s + consonant, z, gn or ps, masculine nouns take uno.",
        ) { "uno " + it.groupValues[1] },
        Rule(
            "congiuntivo-opinione", Regex("(?<!\\p{L})(penso|credo|spero|immagino|dubito|temo) che (è|ha|sono|hanno|va|fa|può|vuole|sa|viene)(?!\\p{L})"),
            "After verbs of opinion, hope and doubt the exam expects the congiuntivo.",
        ) { it.groupValues[1] + " che " + SUBJUNCTIVE.getValue(it.groupValues[2]) },
        Rule(
            "se-condizionale", Regex("(?<!\\p{L})se (?:io )?(avrei|sarei|potrei|vorrei|farei)(?!\\p{L})"),
            "After se in an unreal condition use the imperfetto congiuntivo, not the conditional.",
        ) { "se " + IF_CLAUSE.getValue(it.groupValues[1]) },
        Rule(
            "sono-fame", Regex("(?<!\\p{L})sono (fame|sete|sonno|caldo|freddo|paura)(?!\\p{L})"),
            "Hunger, thirst, sleepiness, heat, cold and fear use avere: ho fame.",
        ) { "ho " + it.groupValues[1] },
        Rule(
            "piaciuto-ha", Regex("(?<!\\p{L})mi (?:ha|hanno) piaciuto(?!\\p{L})"),
            "Piacere takes essere in the passato prossimo: mi è piaciuto.",
        ) { "mi è piaciuto" },
        Rule(
            "bisogno-a", Regex("(?<!\\p{L})bisogno (a|per|da) "),
            "Avere bisogno is followed by di.",
        ) { "bisogno di " },
    )

    /** The mistakes found in [text], each once, in the order of the rules. */
    fun find(text: String): List<Correction> {
        val said = AnswerNormalizer.normalize(text)
        if (said.isEmpty()) return emptyList()
        return RULES.mapNotNull { rule ->
            rule.pattern.find(said)?.let { m -> Correction(rule.id, m.value.trim(), rule.better(m).trim(), rule.note) }
        }
    }
}
