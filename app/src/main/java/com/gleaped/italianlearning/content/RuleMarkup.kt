package com.gleaped.italianlearning.content

import com.gleaped.italianlearning.content.reference.ChartTable

/**
 * Rule-card markup (docs/lesson-format.md, "Rule-card markup"): blocks of paragraphs, `- ` bullets,
 * `| pipe | tables |` and `> ` callouts. Inline `**bold**`, `*italic*` and `==highlight==` are
 * left in the text for the UI to style. Pure Kotlin so it can be unit-tested.
 */
sealed interface RuleBlock {
    data class Paragraph(val lines: List<String>) : RuleBlock
    data class Bullets(val items: List<String>) : RuleBlock
    data class Table(val table: ChartTable) : RuleBlock
    data class Callout(val text: String) : RuleBlock
}

object RuleMarkup {
    private val SEPARATOR = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

    fun parse(body: String): List<RuleBlock> {
        val blocks = mutableListOf<RuleBlock>()
        val chunks = body.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim('\n') }.filter { it.isNotBlank() }
        for (chunk in chunks) {
            val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                when {
                    line.startsWith("|") -> {
                        val rows = mutableListOf<String>()
                        while (i < lines.size && lines[i].startsWith("|")) rows += lines[i++]
                        blocks += RuleBlock.Table(parseTable(rows))
                    }
                    line.startsWith(">") -> {
                        val text = mutableListOf<String>()
                        while (i < lines.size && lines[i].startsWith(">")) text += lines[i++].removePrefix(">").trim()
                        blocks += RuleBlock.Callout(text.joinToString(" "))
                    }
                    line.startsWith("- ") || line.startsWith("• ") -> {
                        val items = mutableListOf<String>()
                        while (i < lines.size && (lines[i].startsWith("- ") || lines[i].startsWith("• "))) items += lines[i++].drop(2).trim()
                        blocks += RuleBlock.Bullets(items)
                    }
                    else -> {
                        val text = mutableListOf<String>()
                        while (i < lines.size && !isBlockStart(lines[i])) text += lines[i++]
                        blocks += RuleBlock.Paragraph(text)
                    }
                }
            }
        }
        return blocks
    }

    private fun isBlockStart(line: String) =
        line.startsWith("|") || line.startsWith(">") || line.startsWith("- ") || line.startsWith("• ")

    private fun cellsOf(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    fun parseTable(rows: List<String>): ChartTable {
        val content = rows.filterNot { SEPARATOR.matches(it.trim()) }
        if (content.isEmpty()) return ChartTable()
        val header = cellsOf(content.first())
        return ChartTable(columns = header, rows = content.drop(1).map(::cellsOf))
    }

    /** Text without markup, for word counts and notifications. */
    fun plain(text: String): String = text.replace("==", "").replace("**", "").replace("*", "")
}
