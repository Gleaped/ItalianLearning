package com.gleaped.italianlearning.content

import com.gleaped.italianlearning.domain.profile.LearnerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMarkupTest {
    @Test
    fun parsesParagraphTableCalloutAndBullets() {
        val body = """
            The **congiuntivo** follows ==che==.

            | persona | parlare |
            |---|---|
            | io | parl==i== |
            | noi | parl==iamo== |

            > Same subject? Use di + infinito.

            - penso che
            - spero che
        """.trimIndent()
        val blocks = RuleMarkup.parse(body)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is RuleBlock.Paragraph)
        val table = (blocks[1] as RuleBlock.Table).table
        assertEquals(listOf("persona", "parlare"), table.columns)
        assertEquals(listOf(listOf("io", "parl==i=="), listOf("noi", "parl==iamo==")), table.rows)
        assertEquals("Same subject? Use di + infinito.", (blocks[2] as RuleBlock.Callout).text)
        assertEquals(listOf("penso che", "spero che"), (blocks[3] as RuleBlock.Bullets).items)
    }

    @Test
    fun tableWithoutBlankLineStillSplitsFromParagraph() {
        val blocks = RuleMarkup.parse("Forms:\n| a | b |\n| 1 | 2 |")
        assertEquals(2, blocks.size)
        assertEquals(listOf("Forms:"), (blocks[0] as RuleBlock.Paragraph).lines)
        assertEquals(1, (blocks[1] as RuleBlock.Table).table.rows.size)
    }

    @Test
    fun oldStyleBodiesStillWork() {
        val blocks = RuleMarkup.parse("First line\nsecond line\n\n- one\n- two")
        assertEquals(listOf("First line", "second line"), (blocks[0] as RuleBlock.Paragraph).lines)
        assertEquals(2, (blocks[1] as RuleBlock.Bullets).items.size)
    }

    @Test
    fun learnerNameIsCleanedAndSubstituted() {
        assertEquals("Anna Maria", LearnerName.clean("  Anna   Maria \n"))
        assertEquals("Bob", LearnerName.clean("\"Bob\\{}\""))
        assertEquals(LearnerName.MAX_LENGTH, LearnerName.clean("x".repeat(80)).length)
        assertEquals("""{"text":"Ciao, Sam!"}""", LearnerName.substitute("""{"text":"Ciao, {name}!"}""", "Sam"))
        val parsed = ContentParser.parseUnit(LearnerName.substitute("""{"id":"u","title":"Ciao {name}"}""", "Q\"uote"))
        assertEquals("Ciao Quote", parsed.title)
    }
}
