package com.gleaped.italianlearning.domain.dictionary

/**
 * Read access to dictionary.db. The Android implementation runs [DictionarySql] on a
 * read-only SQLiteDatabase; tests use an in-memory fake.
 */
interface DictionaryStore {
    /** Rows of `form` whose form_norm equals [norm], joined to their lemma. */
    fun formsByNorm(norm: String, limit: Int): List<Pair<FormMatch, LemmaRow>>

    /** Lemmas with `lemma >= lower AND lemma < upper`, by frequency. */
    fun lemmasInRange(lower: String, upper: String, limit: Int): List<LemmaRow>

    fun lemma(id: Long): LemmaRow?

    fun meta(): Map<String, String>

    /** Content words ranked after [afterRank] by frequency, nearest first. */
    fun contentWordsAfter(afterRank: Int, limit: Int): List<LemmaRow> = emptyList()

    /** The most frequent verbs, with their forms tables, up to rank [maxRank]. */
    fun verbsUpTo(maxRank: Int, limit: Int): List<LemmaRow> = emptyList()
}

/** The SQL behind [DictionaryStore], kept here (pure Kotlin) so it is reviewed and tested with the planner. */
object DictionarySql {
    private const val LEMMA_COLS =
        "l.id, l.lemma, l.pos, l.gender, l.gloss_en, l.gloss_it, l.example_it, l.example_en, l.freq_rank, l.notes, l.forms_json"

    /** Uses form_norm_idx. Args: form_norm, limit. Columns: form, tags, then [LEMMA_COLS]. */
    const val FORMS_BY_NORM =
        "SELECT f.form, f.tags, $LEMMA_COLS FROM form f JOIN lemma l ON l.id = f.lemma_id " +
            "WHERE f.form_norm = ? ORDER BY l.freq_rank IS NULL, l.freq_rank, l.id LIMIT ?"

    /** Range scan on lemma_lemma (a prefix search that can use the index). Args: lower, upper, limit. */
    const val LEMMAS_IN_RANGE =
        "SELECT $LEMMA_COLS FROM lemma l WHERE l.lemma >= ? AND l.lemma < ? " +
            "ORDER BY l.freq_rank IS NULL, l.freq_rank, length(l.lemma), l.lemma LIMIT ?"

    /** Args: after rank, limit. */
    const val CONTENT_WORDS_AFTER =
        "SELECT $LEMMA_COLS FROM lemma l WHERE l.freq_rank > ? AND l.pos IN ('noun','verb','adj','adv') " +
            "ORDER BY l.freq_rank LIMIT ?"

    /** Args: max rank, limit. */
    const val VERBS_UP_TO =
        "SELECT $LEMMA_COLS FROM lemma l WHERE l.pos = 'verb' AND l.freq_rank IS NOT NULL AND l.freq_rank <= ? " +
            "ORDER BY l.freq_rank LIMIT ?"

    const val LEMMA_BY_ID = "SELECT $LEMMA_COLS FROM lemma l WHERE l.id = ?"

    const val META = "SELECT key, value FROM meta"

    /** Column index of the first lemma column in [FORMS_BY_NORM]. */
    const val FORMS_LEMMA_OFFSET = 2

    /** Upper bound for a prefix range: every string starting with [prefix] sorts below it. */
    fun prefixUpperBound(prefix: String): String = prefix + '￿'
}

/** What a lookup will run, worked out from the raw query; see [DictionaryLookup.plan]. */
data class LookupPlan(
    val norm: String,
    /** Distinct lowercase prefixes for lemma.lemma (the accented spelling and the normalised one). */
    val prefixes: List<String>,
) {
    val isEmpty: Boolean get() = norm.isEmpty()
}

/**
 * Lookup per the spec: exact matches on form.form_norm first, then prefix matches on
 * lemma.lemma; results grouped by lemma and ordered by freq_rank.
 */
class DictionaryLookup(private val store: DictionaryStore) {

    fun contentWordsAfter(afterRank: Int, limit: Int): List<LemmaRow> = store.contentWordsAfter(afterRank, limit)

    fun verbsUpTo(maxRank: Int, limit: Int): List<LemmaRow> = store.verbsUpTo(maxRank, limit)

    /**
     * Whether [word] is a spelling the dictionary knows: [Spelling.KNOWN] when one of its forms
     * matches exactly, [Spelling.ACCENT] when only the accents differ (with the form that matches),
     * [Spelling.UNKNOWN] when no form matches even without accents.
     */
    fun spelling(word: String): SpellingResult {
        val norm = WordNormalizer.normalize(word)
        if (norm.isEmpty()) return SpellingResult(Spelling.KNOWN)
        val forms = store.formsByNorm(norm, 12).map { it.first.form.let { f -> WordNormalizer.lowerKeepAccents(f) } }
        if (forms.isEmpty()) return SpellingResult(Spelling.UNKNOWN)
        val typed = WordNormalizer.lowerKeepAccents(word)
        return if (typed in forms) SpellingResult(Spelling.KNOWN) else SpellingResult(Spelling.ACCENT, forms.first())
    }

    fun search(query: String, maxGroups: Int = MAX_GROUPS): List<LookupGroup> {
        val plan = plan(query)
        if (plan.isEmpty) return emptyList()
        val exact = store.formsByNorm(plan.norm, EXACT_LIMIT)
        val prefix = plan.prefixes.flatMap { p ->
            store.lemmasInRange(p, DictionarySql.prefixUpperBound(p), PREFIX_LIMIT)
        }
        return rank(plan, exact, prefix).take(maxGroups)
    }

    fun entry(id: Long): LemmaRow? = store.lemma(id)

    companion object {
        const val MAX_GROUPS = 30
        const val EXACT_LIMIT = 60
        const val PREFIX_LIMIT = 40
        /** Prefix matches start at this many letters, so one letter does not list half the dictionary. */
        const val MIN_PREFIX = 2

        fun plan(query: String): LookupPlan {
            val norm = WordNormalizer.normalize(query)
            if (norm.isEmpty()) return LookupPlan("", emptyList())
            val prefixes = if (norm.length < MIN_PREFIX) {
                emptyList()
            } else {
                listOf(WordNormalizer.lowerKeepAccents(query), norm).filter { it.isNotEmpty() }.distinct()
            }
            return LookupPlan(norm, prefixes)
        }

        /**
         * Exact form matches first (the lemma spelled exactly like the query on top, then by
         * freq_rank, unknown frequency last); then prefix hits not already listed, by freq_rank
         * then shorter lemma. Matches of one lemma are merged into one group.
         */
        fun rank(
            plan: LookupPlan,
            exact: List<Pair<FormMatch, LemmaRow>>,
            prefix: List<LemmaRow>,
        ): List<LookupGroup> {
            val exactGroups = exact.groupBy { it.second.id }.map { (_, rows) ->
                LookupGroup(rows.first().second, rows.map { it.first }.distinct())
            }.sortedWith(
                compareBy<LookupGroup>(
                    { if (WordNormalizer.normalize(it.lemma.lemma) == plan.norm) 0 else 1 },
                    { it.lemma.freqRank ?: Int.MAX_VALUE },
                    { it.lemma.lemma.length },
                    { it.lemma.lemma },
                ),
            )
            val seen = exactGroups.map { it.lemma.id }.toMutableSet()
            val prefixGroups = prefix.distinctBy { it.id }
                .filter { seen.add(it.id) }
                .sortedWith(
                    compareBy<LemmaRow>(
                        { if (WordNormalizer.normalize(it.lemma) == plan.norm) 0 else 1 },
                        { it.freqRank ?: Int.MAX_VALUE },
                        { it.lemma.length },
                        { it.lemma },
                    ),
                )
                .map { LookupGroup(it, emptyList()) }
            return exactGroups + prefixGroups
        }
    }
}

/** Most-recent-first list of looked-up lemmas, without duplicates. */
object RecentLookups {
    const val MAX = 12

    fun add(recent: List<String>, lemma: String, max: Int = MAX): List<String> {
        val word = lemma.trim()
        if (word.isEmpty()) return recent
        return (listOf(word) + recent.filterNot { it.equals(word, ignoreCase = true) }).take(max)
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(raw: String?): List<String> = raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Whether the dictionary must be (re)copied from the APK's assets. */
enum class InstallAction {
    /** The installed copy belongs to this APK: open it. */
    USE_INSTALLED,
    /** Nothing installed yet: copy straight into place. */
    COPY,
    /** The APK changed since the copy: copy to a temp file and swap only if meta.version differs. */
    COPY_AND_COMPARE,
}

object DictionaryInstallPlan {
    /**
     * @param installedVersion meta.version of the installed copy, null when there is none or it is unreadable
     * @param installedStamp the APK stamp recorded at the last copy (null when unknown)
     * @param apkStamp the current APK's stamp (e.g. lastUpdateTime + versionCode)
     */
    fun decide(installedVersion: String?, installedStamp: String?, apkStamp: String): InstallAction = when {
        installedVersion == null -> InstallAction.COPY
        installedStamp != apkStamp -> InstallAction.COPY_AND_COMPARE
        else -> InstallAction.USE_INSTALLED
    }

    /** After COPY_AND_COMPARE: replace the installed copy only when the asset's version differs. */
    fun shouldReplace(installedVersion: String?, assetVersion: String?): Boolean =
        assetVersion != null && assetVersion != installedVersion
}

/** How a written word compares with the dictionary's forms. */
enum class Spelling { KNOWN, ACCENT, UNKNOWN }

/** [suggestion] is the matching form when only the accents differ. */
data class SpellingResult(val kind: Spelling, val suggestion: String? = null)
