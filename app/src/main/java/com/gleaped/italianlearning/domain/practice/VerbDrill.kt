package com.gleaped.italianlearning.domain.practice

import com.gleaped.italianlearning.content.reference.ChartTable
import com.gleaped.italianlearning.domain.dictionary.FormsParser
import com.gleaped.italianlearning.domain.dictionary.LemmaRow
import com.gleaped.italianlearning.domain.grading.Grader
import com.gleaped.italianlearning.domain.grading.Verdict
import kotlin.random.Random

/** Which tenses a round asks about. */
enum class TenseSet(val label: String, val description: String, val titles: List<String>) {
    EVERYDAY(
        "Tempi di base", "Presente, passato prossimo, imperfetto, futuro",
        listOf("Indicativo presente", "Indicativo passato prossimo", "Indicativo imperfetto", "Indicativo futuro semplice"),
    ),
    CONDITIONAL(
        "Condizionale e imperativo", "Condizionale presente e passato, imperativo",
        listOf("Condizionale presente", "Condizionale passato", "Imperativo"),
    ),
    SUBJUNCTIVE(
        "Congiuntivo", "Presente, passato, imperfetto, trapassato",
        listOf("Congiuntivo presente", "Congiuntivo passato", "Congiuntivo imperfetto", "Congiuntivo trapassato"),
    ),
    PAST(
        "Passati", "Passato remoto, trapassato prossimo, futuro anteriore",
        listOf("Indicativo passato remoto", "Indicativo trapassato prossimo", "Indicativo futuro anteriore"),
    ),
    ALL(
        "Tutti i tempi", "Everything above",
        listOf(
            "Indicativo presente", "Indicativo passato prossimo", "Indicativo imperfetto", "Indicativo futuro semplice",
            "Condizionale presente", "Condizionale passato", "Imperativo",
            "Congiuntivo presente", "Congiuntivo passato", "Congiuntivo imperfetto", "Congiuntivo trapassato",
            "Indicativo passato remoto", "Indicativo trapassato prossimo", "Indicativo futuro anteriore",
        ),
    ),
}

/** One form to produce: "andare, congiuntivo imperfetto, loro". */
data class VerbQuestion(
    val lemmaId: Long,
    val infinitive: String,
    val meaning: String,
    val tense: String,
    val person: String,
    /** The form as the dictionary writes it ("sono andato/a"). */
    val form: String,
    /** Everything accepted: "sono andato/a" gives "sono andato" and "sono andata". */
    val accepted: List<String>,
)

/**
 * An endless supply of conjugation questions from the dictionary's forms tables: any form of the
 * commonest verbs in the tenses chosen. Pure Kotlin.
 */
object VerbDrill {
    const val ROUND_SIZE = 10

    /** The verbs the drill draws from: this many of the most frequent. */
    const val POOL = 300

    /** Only the verbs up to this frequency rank are fetched. */
    const val MAX_RANK = 4000

    private val SKIP_PERSONS = setOf("negativo (tu)", "negativo")

    /** "sono andato/a" gives ["sono andato/a", "sono andato", "sono andata"]; "ha, abbia" style lists split on commas. */
    fun accepted(form: String): List<String> {
        val out = LinkedHashSet<String>()
        for (part in form.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
            out += part
            // "andato/a" and "andati/e": the ending letter replaces the last one(s) of the participle.
            Regex("^(.*?)(\\p{L}+?)/(\\p{L})$").matchEntire(part)?.let { m ->
                val head = m.groupValues[1]
                val word = m.groupValues[2]
                val alt = m.groupValues[3]
                out += head + word
                out += head + word.dropLast(1) + alt
            }
        }
        return out.toList()
    }

    /** True when the table is one of [titles]; "(con essere)" style suffixes are accepted. */
    private fun matches(table: ChartTable, titles: Collection<String>): Boolean =
        titles.any { table.title == it || table.title.startsWith("$it (") }

    /** All questions for one verb in the tenses [titles]. */
    fun questionsFor(verb: LemmaRow, titles: Collection<String>): List<VerbQuestion> =
        FormsParser.parse(verb.formsJson).filter { matches(it, titles) }.flatMap { table ->
            table.rows.mapNotNull { row ->
                val person = row.getOrNull(0)?.trim().orEmpty()
                val form = row.getOrNull(1)?.trim().orEmpty()
                if (person.isEmpty() || form.isEmpty() || person.lowercase() in SKIP_PERSONS) return@mapNotNull null
                VerbQuestion(verb.id, verb.lemma, shortMeaning(verb.firstGloss), table.title.substringBefore(" (").trim(), person, form, accepted(form))
            }
        }

    private val BRACKETED = Regex("\\s*\\([^)]*\\)")

    private fun shortMeaning(gloss: String): String = BRACKETED.replace(gloss, "").trim().ifEmpty { gloss }

    /** A round of [size] questions: different verbs, mixed tenses. */
    fun round(verbs: List<LemmaRow>, set: TenseSet, size: Int = ROUND_SIZE, random: Random = Random.Default): List<VerbQuestion> {
        val pool = verbs.take(POOL).shuffled(random)
        val out = ArrayList<VerbQuestion>()
        for (verb in pool) {
            if (out.size >= size) break
            val options = questionsFor(verb, set.titles)
            if (options.isNotEmpty()) out += options.random(random)
        }
        return out
    }

    /** Grades [typed] against the question; a missing accent is "almost", a leading subject pronoun is ignored. */
    fun grade(typed: String, q: VerbQuestion): Verdict =
        q.accepted.map { Grader.gradeConjugation(typed, it).verdict }.minByOrNull { it.ordinal } ?: Verdict.WRONG
}
