package com.gleaped.italianlearning.domain.writing

import com.gleaped.italianlearning.domain.dictionary.Spelling
import com.gleaped.italianlearning.domain.dictionary.SpellingResult

/** What kind of thing a note is about. */
enum class NoteKind { SPELLING, PATTERN, REGISTER, STYLE }

/** One thing in a text that is worth a second look. [quote] is the part of the text it is about. */
data class WritingNote(val kind: NoteKind, val message: String, val quote: String? = null)

/** The result of checking a text. */
data class WritingReport(
    val words: Int,
    val sentences: Int,
    /** Linking words found, each once, in the order they appear. */
    val connectors: List<String>,
    val notes: List<WritingNote>,
    /** False when the dictionary was not available, so spelling was not checked. */
    val spellingChecked: Boolean,
)

/**
 * An offline "things to look at" check for a written text. It cannot judge whether grammar is
 * right; it finds what a rule can find: words the dictionary does not know, missing accents,
 * a short list of frequent learner errors, mixed formal and informal address, repeated words,
 * very long or very choppy sentences, and few linking words. Pure Kotlin.
 */
object WritingChecker {
    /** At most this many spelling notes, so a text full of names does not bury the rest. */
    const val MAX_SPELLING_NOTES = 8

    /** A repeated word is reported at this many uses or more. */
    const val REPEAT_AT = 4

    private val wordRegex = Regex("\\p{L}+(?:['’]\\p{L}+)*")
    /** A whole-word pattern; `\\b` is not used because it treats accented letters differently on different runtimes. */
    private fun w(inner: String) = "(?<!\\p{L})(?:$inner)(?!\\p{L})"

    private val tokenRegex = Regex("\\p{L}+")
    private val sentenceSplit = Regex("(?<=[.!?…])\\s+")

    private val CONNECTORS = listOf(
        "inoltre", "tuttavia", "perciò", "quindi", "dunque", "infatti", "invece", "anzi", "però", "ma",
        "nonostante", "sebbene", "benché", "anche se", "mentre", "perché", "poiché", "siccome", "affinché",
        "in conclusione", "in primo luogo", "da un lato", "dall'altro", "d'altra parte", "per esempio",
        "ad esempio", "innanzitutto", "infine", "prima di tutto", "per questo", "di conseguenza", "pertanto",
        "oppure", "cioè", "ovvero", "comunque", "purtroppo", "fortunatamente",
    )

    // Words that are repeated naturally and are not worth a note.
    private val IGNORE_REPEATS = setOf(
        "essere", "avere", "sono", "della", "delle", "degli", "dello", "nella", "nelle", "negli", "nello", "sulla", "sulle",
        "anche", "molto", "come", "dove", "quando", "questo", "questa", "questi", "queste", "quello", "quella", "perché",
        "ancora", "anche", "sempre", "ogni", "cosa", "cose", "alla", "alle", "agli", "allo", "dalla", "dalle", "degli",
        "stato", "stata", "fatto", "fare", "hanno", "siamo", "siete", "della", "tutto", "tutti", "tutte", "senza", "dopo",
        "prima", "ho", "ha", "ci", "che", "con", "per", "non", "una", "uno", "gli", "nel", "del", "dei", "più", "mio", "mia",
    )

    private val FORMAL_MARKERS = Regex(
        w("(gentile|egregio|spettabile|gentilissim[oa]|la ringrazio|le scrivo|le chiedo|la prego|distinti saluti|cordiali saluti|in attesa di un suo)"),
        RegexOption.IGNORE_CASE,
    )
    private val INFORMAL_MARKERS = Regex(
        w("(ciao|caro|cara|ti scrivo|ti ringrazio|ti chiedo|un abbraccio|baci|a presto|tvb|ci sentiamo)"),
        RegexOption.IGNORE_CASE,
    )

    private class Rule(val pattern: Regex, val message: String)

    private val RULES = listOf(
        Rule(Regex(w("qual['’]è"), RegexOption.IGNORE_CASE), "“qual è” is written without an apostrophe."),
        Rule(Regex(w("(?:a me mi|a te ti|a lui gli|a lei le|a noi ci|a voi vi)"), RegexOption.IGNORE_CASE), "“a me mi” and its kind double the pronoun. Fine in speech, but in a formal text use one of them."),
        Rule(Regex(w("un['’](?:amico|altro|anno|aereo|albergo|appuntamento|ufficio|uomo|albero|esempio|errore|orario|incontro|impiegato|ospedale)"), RegexOption.IGNORE_CASE), "A masculine noun takes “un”, without an apostrophe (un amico, un altro)."),
        Rule(Regex(w("(?:più meglio|più peggio|più migliore|più peggiore)"), RegexOption.IGNORE_CASE), "“meglio”, “peggio”, “migliore” and “peggiore” are already comparatives: no “più”."),
        Rule(Regex(w("piuttosto che"), RegexOption.IGNORE_CASE), "“piuttosto che” means “rather than”. For “or” use “oppure”, “o”."),
        Rule(Regex(w("pò"), RegexOption.IGNORE_CASE), "“un po’” is written with an apostrophe, not an accent."),
        Rule(Regex(w("(?:penso|credo|ritengo|immagino|dubito|temo|spero|sembra|pare)\\s+che\\s+(?:è|sono|ha|hanno|fa|fanno|va|vanno|può|possono|deve|devono|vuole|vogliono|sa|sanno|viene|vengono|sta|stanno)"), RegexOption.IGNORE_CASE), "After verbs of opinion, doubt, fear or hope, “che” is usually followed by the congiuntivo (sia, abbiano, faccia…)."),
        Rule(Regex("(?:^|[.,;!?]\\s*)se\\s+(?:sarei|avrei|potrei|vorrei|farei|direi|andrei|verrei)\\b", RegexOption.IGNORE_CASE), "After “se” the condizionale is not used. Try the imperfetto or trapassato congiuntivo (se fossi, se avessi avuto)."),
    )

    /** Word-final apostrophes that are right (po', mo', be') and need no note. */
    private val FINAL_APOSTROPHE_OK = setOf("po", "mo", "be", "di", "da", "fa", "va", "sta")

    private val ACCENT_APOSTROPHE = Regex("(\\p{L}+)['’](?=\\s|[.,;:!?)]|$)")

    /**
     * Checks [text]. [spelling] looks a word up (null when the dictionary is not available);
     * it is only called for words that need it.
     */
    fun check(text: String, spelling: ((String) -> SpellingResult)?): WritingReport {
        val trimmed = text.trim()
        val tokens = wordTokens(trimmed)
        val words = tokens.size
        val sentences = sentences(trimmed)
        val notes = ArrayList<WritingNote>()

        // Frequent errors.
        for (rule in RULES) {
            rule.pattern.find(trimmed)?.let { notes += WritingNote(NoteKind.PATTERN, rule.message, it.value) }
        }
        ACCENT_APOSTROPHE.findAll(trimmed)
            .filter { it.groupValues[1].lowercase() !in FINAL_APOSTROPHE_OK }
            .take(3)
            .forEach {
                notes += WritingNote(
                    NoteKind.PATTERN,
                    "A stressed final vowel is marked with an accent, not an apostrophe (perché, più, così, è).",
                    it.value,
                )
            }

        // Spelling.
        var checked = false
        if (spelling != null) {
            checked = true
            val seen = HashSet<String>()
            var count = 0
            for (match in wordRegex.findAll(trimmed)) {
                if (count >= MAX_SPELLING_NOTES) break
                // Before an apostrophe the part is an elided article or preposition ("dell'", "un'"); only the last part is checked.
                val word = match.value.split('\'', '’').last()
                if (word.length < 2 || !seen.add(word.lowercase())) continue
                // A word followed by a lone apostrophe ("perche'") is reported by the pattern check instead.
                val after = match.range.last + 1
                if (after < trimmed.length && trimmed[after] in "'’" && (after + 1 >= trimmed.length || !trimmed[after + 1].isLetter())) continue
                // A capital letter in the middle of a sentence is most likely a name.
                if (word[0].isUpperCase() && !startsSentence(trimmed, match.range.first)) continue
                val result = spelling(word)
                when (result.kind) {
                    Spelling.KNOWN -> Unit
                    Spelling.ACCENT -> {
                        notes += WritingNote(NoteKind.SPELLING, "Check the accent: “${result.suggestion}”?", word)
                        count++
                    }
                    Spelling.UNKNOWN -> {
                        notes += WritingNote(NoteKind.SPELLING, "Not in the dictionary. A typo, a name or a word to check?", word)
                        count++
                    }
                }
            }
        }

        // Register.
        val formal = FORMAL_MARKERS.findAll(trimmed).map { it.value.lowercase() }.toList()
        val informal = INFORMAL_MARKERS.findAll(trimmed).map { it.value.lowercase() }.toList()
        if (formal.isNotEmpty() && informal.isNotEmpty()) {
            notes += WritingNote(
                NoteKind.REGISTER,
                "Formal (${formal.first()}) and informal (${informal.first()}) address are mixed. Choose Lei or tu and keep to it.",
            )
        }

        // Style.
        val connectors = connectorsIn(trimmed)
        if (words >= 60 && connectors.size < 2) {
            notes += WritingNote(NoteKind.STYLE, "Few linking words (inoltre, tuttavia, quindi, perché…). Linking ideas is marked under coerenza e coesione.")
        }
        val average = if (sentences.isEmpty()) 0.0 else words.toDouble() / sentences.size
        if (sentences.size >= 3 && average > 28) {
            notes += WritingNote(NoteKind.STYLE, "Sentences average ${average.toInt()} words. Some may read better split in two.")
        }
        if (sentences.size >= 5 && average < 7) {
            notes += WritingNote(NoteKind.STYLE, "Sentences are very short (about ${average.toInt()} words). Joining some with a linking word shows more range.")
        }
        repeatedWords(tokens).forEach { (word, n) ->
            notes += WritingNote(NoteKind.STYLE, "“$word” appears $n times. A synonym or a pronoun could vary it.", word)
        }
        return WritingReport(words, sentences.size, connectors, notes, checked)
    }

    fun wordTokens(text: String): List<String> = tokenRegex.findAll(text).map { it.value }.toList()

    fun sentences(text: String): List<String> =
        text.split(sentenceSplit).map { it.trim() }.filter { tokenRegex.containsMatchIn(it) }

    private fun startsSentence(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        return i < 0 || text[i] in ".!?…:\"«"
    }

    /** The linking words used, each once, in order of first use. */
    fun connectorsIn(text: String): List<String> {
        val lower = text.lowercase().replace('’', '\'')
        return CONNECTORS.filter { connector ->
            Regex("(?<![\\p{L}])" + Regex.escape(connector) + "(?![\\p{L}])").containsMatchIn(lower)
        }.sortedBy { lower.indexOf(it) }
    }

    private fun repeatedWords(tokens: List<String>): List<Pair<String, Int>> =
        tokens.map { it.lowercase() }
            .filter { it.length >= 5 && it !in IGNORE_REPEATS }
            .groupingBy { it }.eachCount()
            .filter { it.value >= REPEAT_AT }
            .toList()
            .sortedByDescending { it.second }
            .take(2)
}
