package com.gleaped.italianlearning.domain.exam

import com.gleaped.italianlearning.domain.progress.ExamPart
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A practice paper done under real conditions, with the points the learner gave each part. Keys are [ExamPart] names. */
@Serializable
data class SavedPaper(
    val id: String,
    val label: String,
    val epochDay: Long,
    val scores: Map<String, Int> = emptyMap(),
)

/** The totals of one paper on the official scale; a total is null while a part is missing. */
data class PaperTotals(val written: Int?, val oral: Int?, val overall: Int?) {
    val writtenPassed: Boolean? get() = written?.let { it >= ExamReadiness.WRITTEN_PASS }
    val oralPassed: Boolean? get() = oral?.let { it >= ExamReadiness.ORAL_PASS }

    /** Passing needs the written mark, the oral mark and the overall mark. */
    val passed: Boolean? get() = if (overall == null) null else overall >= ExamReadiness.OVERALL_PASS && writtenPassed == true && oralPassed == true
}

/** The list of practice papers, its storage format and the scoring. Pure Kotlin. */
object PaperLog {
    const val LIMIT = 50

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedPaper.serializer())

    fun encode(papers: List<SavedPaper>): String = json.encodeToString(serializer, papers)

    fun decode(raw: String?): List<SavedPaper> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Newest first, one entry per id, at most [LIMIT]. */
    fun add(papers: List<SavedPaper>, paper: SavedPaper): List<SavedPaper> =
        (listOf(paper.copy(scores = cleanScores(paper.scores))) + papers.filter { it.id != paper.id }).take(LIMIT)

    /** Keeps only known parts with a score from 0 to the part's maximum. */
    fun cleanScores(scores: Map<String, Int>): Map<String, Int> =
        scores.filter { (key, value) ->
            val part = ExamPart.entries.firstOrNull { it.name == key }
            part != null && value in 0..part.max
        }

    /** A whole number from 0 to the part's maximum, or null for blank or invalid text. */
    fun parseScore(text: String, part: ExamPart): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 0..part.max }

    fun totals(paper: SavedPaper): PaperTotals {
        val scores = cleanScores(paper.scores)
        val written = ExamPart.entries.filter { it.written }
        val writtenTotal = if (written.all { scores.containsKey(it.name) }) written.sumOf { scores.getValue(it.name) } else null
        val oral = scores[ExamPart.ORAL.name]
        val overall = if (writtenTotal != null && oral != null) writtenTotal + oral else null
        return PaperTotals(writtenTotal, oral, overall)
    }

    /** The official grade letter for an overall mark out of 200. */
    fun grade(overall: Int): String = when {
        overall >= 173 -> "A (ottimo)"
        overall >= 144 -> "B (buono)"
        overall >= 117 -> "C (sufficiente)"
        overall >= 69 -> "D (insufficiente)"
        else -> "E (insufficiente)"
    }
}
