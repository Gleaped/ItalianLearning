package com.gleaped.italianlearning.domain.grading

/** Result of checking one answer. */
enum class Verdict { CORRECT, ALMOST, WRONG }

data class GradeResult(
    val verdict: Verdict,
    /** The accepted answer closest to what the learner wrote (shown as feedback). */
    val expected: String,
    val note: String? = null,
)

/**
 * Pure grading logic for every exercise type. No Android dependencies.
 */
object Grader {

    private val SUBJECT_PRONOUNS = setOf("io", "tu", "lui", "lei", "noi", "voi", "loro", "egli", "ella", "esso", "essa", "essi", "esse")

    const val ACCENT_NOTE = "Almost! Check the accents."

    /** Compares a typed answer with a list of accepted answers. */
    fun gradeTyped(input: String, accepted: List<String>): GradeResult {
        val answers = accepted.filter { it.isNotBlank() }
        if (answers.isEmpty()) return GradeResult(Verdict.CORRECT, "")
        val user = AnswerNormalizer.normalize(input)
        if (user.isEmpty()) return GradeResult(Verdict.WRONG, answers.first())

        // 1. Exact after normalisation.
        answers.firstOrNull { AnswerNormalizer.normalize(it) == user }
            ?.let { return GradeResult(Verdict.CORRECT, it) }
        // 2. Punctuation-only differences are fine too.
        val userBare = AnswerNormalizer.withoutPunctuation(input)
        answers.firstOrNull { AnswerNormalizer.withoutPunctuation(it) == userBare }
            ?.let { return GradeResult(Verdict.CORRECT, it) }
        // 3. Only accents differ -> almost.
        val userNoAccents = AnswerNormalizer.stripAccents(userBare)
        answers.firstOrNull { AnswerNormalizer.stripAccents(AnswerNormalizer.withoutPunctuation(it)) == userNoAccents }
            ?.let { return GradeResult(Verdict.ALMOST, it, ACCENT_NOTE) }
        return GradeResult(Verdict.WRONG, answers.first())
    }

    /** Conjugation items: the learner may type the subject pronoun ("io ero"); it is stripped. */
    fun gradeConjugation(input: String, answer: String): GradeResult {
        val raw = gradeTyped(input, listOf(answer))
        if (raw.verdict == Verdict.CORRECT) return raw
        val stripped = gradeTyped(stripLeadingPronoun(input), listOf(answer))
        return if (stripped.verdict.ordinal < raw.verdict.ordinal) stripped else raw
    }

    fun stripLeadingPronoun(input: String): String {
        val trimmed = input.trim()
        val firstSpace = trimmed.indexOf(' ')
        if (firstSpace <= 0) return trimmed
        val first = trimmed.substring(0, firstSpace).trimEnd(',').lowercase()
        return if (first in SUBJECT_PRONOUNS) trimmed.substring(firstSpace + 1).trim() else trimmed
    }

    /** Sentence build: tiles are joined with spaces and compared ignoring punctuation. */
    fun gradeSentenceBuild(selectedTiles: List<String>, correctTiles: List<String>): GradeResult {
        val expected = correctTiles.joinToString(" ")
        val user = selectedTiles.joinToString(" ")
        val ok = AnswerNormalizer.withoutPunctuation(user) == AnswerNormalizer.withoutPunctuation(expected)
        return GradeResult(if (ok) Verdict.CORRECT else Verdict.WRONG, expected)
    }

    fun gradeChoice(selected: Int, answer: Int, options: List<String>): GradeResult =
        GradeResult(
            if (selected == answer) Verdict.CORRECT else Verdict.WRONG,
            options.getOrElse(answer) { "" },
        )

    /** Combines several sub-results (conjugation rows, reading questions, roleplay turns). */
    fun combine(verdicts: List<Verdict>): Verdict = when {
        verdicts.isEmpty() -> Verdict.CORRECT
        verdicts.all { it == Verdict.CORRECT } -> Verdict.CORRECT
        verdicts.none { it == Verdict.WRONG } -> Verdict.ALMOST
        verdicts.count { it != Verdict.WRONG } * 2 >= verdicts.size -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    /** Match pairs: judged by the number of wrong attempts made before all pairs matched. */
    fun gradeMatchPairs(mistakes: Int): Verdict = when {
        mistakes <= 0 -> Verdict.CORRECT
        mistakes <= 2 -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    // ---- Speech ----

    /**
     * Word overlap between what the recogniser heard and the target sentence:
     * fraction of target words (as a multiset, accent-insensitive) that were said.
     */
    fun wordOverlap(heard: String, target: String): Double {
        val targetTokens = AnswerNormalizer.tokens(target)
        if (targetTokens.isEmpty()) return 1.0
        val remaining = AnswerNormalizer.tokens(heard).groupingBy { it }.eachCount().toMutableMap()
        var matched = 0
        for (t in targetTokens) {
            val n = remaining[t] ?: 0
            if (n > 0) {
                matched++
                remaining[t] = n - 1
            }
        }
        return matched.toDouble() / targetTokens.size
    }

    fun overlapVerdict(score: Double): Verdict = when {
        score >= 0.8 -> Verdict.CORRECT
        score >= 0.5 -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    /** Best score across the recogniser's alternatives. */
    fun gradeSpeakRepeat(alternatives: List<String>, target: String): Pair<Verdict, Double> {
        val best = alternatives.maxOfOrNull { wordOverlap(it, target) } ?: 0.0
        return overlapVerdict(best) to best
    }

    /**
     * Whether a keyword (possibly several words, possibly with apostrophes like "c'era")
     * occurs in the response. Accent-insensitive. A single long word also matches by
     * stem, so "prenotare" accepts "prenotato".
     */
    fun containsKeyword(response: String, keyword: String): Boolean {
        val words = AnswerNormalizer.tokens(response)
        val kw = AnswerNormalizer.tokens(keyword)
        if (kw.isEmpty()) return true
        if (words.size < kw.size) return false
        for (start in 0..(words.size - kw.size)) {
            var ok = true
            for (i in kw.indices) {
                val w = words[start + i]
                val k = kw[i]
                val isLast = i == kw.lastIndex
                val match = w == k || (isLast && k.length >= 6 && w.startsWith(k.dropLast(2)))
                if (!match) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }

    /** Returns the keywords used and the verdict. */
    fun gradeKeywords(alternatives: List<String>, keywords: List<String>): Pair<Verdict, List<String>> {
        val responses = alternatives.filter { it.isNotBlank() }
        if (responses.isEmpty()) return Verdict.WRONG to emptyList()
        if (keywords.isEmpty()) return Verdict.CORRECT to emptyList()
        val best = responses
            .map { r -> keywords.filter { containsKeyword(r, it) } }
            .maxByOrNull { it.size }
            .orEmpty()
        val ratio = best.size.toDouble() / keywords.size
        val verdict = when {
            ratio >= 0.66 -> Verdict.CORRECT
            best.isNotEmpty() -> Verdict.ALMOST
            else -> Verdict.WRONG
        }
        return verdict to best
    }

    /** Free writing is self-assessed against the checklist. */
    fun gradeFreeWrite(wordCount: Int, minWords: Int, checked: Int, checklistSize: Int): Verdict {
        if (wordCount < minWords) return Verdict.ALMOST
        if (checklistSize == 0) return Verdict.CORRECT
        return when {
            checked >= checklistSize -> Verdict.CORRECT
            checked * 2 >= checklistSize -> Verdict.ALMOST
            else -> Verdict.WRONG
        }
    }

    // ---- Congiuntivo-track types ----

    /**
     * choose_mood: the mood pick decides first; a wrong mood is wrong whatever the form.
     * With [forms] given, the typed form is graded like a conjugation answer.
     */
    fun gradeChooseMood(pickedMood: Int, answerMood: Int, typedForm: String?, forms: List<String>): GradeResult {
        if (pickedMood != answerMood) return GradeResult(Verdict.WRONG, forms.firstOrNull().orEmpty())
        if (forms.isEmpty() || typedForm == null) return GradeResult(Verdict.CORRECT, "")
        val results = forms.map { gradeConjugation(typedForm, it) }
        return results.minByOrNull { it.verdict.ordinal } ?: GradeResult(Verdict.WRONG, forms.first())
    }

    /** Splits a sentence into tappable tokens (whitespace-separated, punctuation kept). */
    fun triggerTokens(sentence: String): List<String> = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun cleanToken(token: String): String =
        token.trim { !it.isLetterOrDigit() && it != '\'' && it != '’' }.lowercase()

    /** Token indexes that make up [trigger] in [sentence] (first whole-word match), or empty. */
    fun triggerSpan(sentence: String, trigger: String): IntRange? {
        val tokens = triggerTokens(sentence).map(::cleanToken)
        val want = triggerTokens(trigger).map(::cleanToken).filter { it.isNotEmpty() }
        if (want.isEmpty() || want.size > tokens.size) return null
        for (i in 0..tokens.size - want.size) {
            if (tokens.subList(i, i + want.size) == want) return i until i + want.size
        }
        return null
    }

    /** find_trigger: tapping any token of the trigger counts. */
    fun gradeFindTrigger(tappedIndex: Int, sentence: String, trigger: String): GradeResult {
        val span = triggerSpan(sentence, trigger)
        val ok = span != null && tappedIndex in span
        return GradeResult(if (ok) Verdict.CORRECT else Verdict.WRONG, trigger)
    }

    /** sort_buckets: per-item verdicts combined; [picks] holds a bucket index (or -1) per item. */
    fun gradeSortBuckets(picks: List<Int>, answers: List<Int>): Pair<Verdict, List<Boolean>> {
        val correct = answers.mapIndexed { i, a -> picks.getOrNull(i) == a }
        val verdict = when (correct.count { !it }) {
            0 -> Verdict.CORRECT
            1 -> if (answers.size >= 4) Verdict.ALMOST else Verdict.WRONG
            else -> Verdict.WRONG
        }
        return verdict to correct
    }
}
