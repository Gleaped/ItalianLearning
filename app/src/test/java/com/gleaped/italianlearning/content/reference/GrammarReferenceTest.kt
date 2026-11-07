package com.gleaped.italianlearning.content.reference

import com.gleaped.italianlearning.content.ContentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrammarReferenceTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    private val ref by lazy { ReferenceParser.parse(resource("reference.json")) }

    @Test
    fun parsesSectionsTopicsAndDefaults() {
        assertEquals(3, ref.sections.size)
        val pp = ref.topic("passato-prossimo")!!
        assertEquals("A2", pp.level)
        assertEquals(1, pp.tables.size)
        assertEquals(listOf("a2r-01", "b1-01"), pp.relatedUnitIds)
        assertEquals("Ieri ho lavorato fino a tardi.", pp.examples.single().it)
        assertEquals(1, pp.pitfalls.size)
        val imp = ref.topic("imperfetto")!!
        assertTrue(imp.tables.isEmpty() && imp.examples.isEmpty() && imp.pitfalls.isEmpty())
        assertEquals("verbi-tempi", ref.sectionOf("imperfetto")!!.id)
        assertNull(ref.topic("nope"))
    }

    @Test
    fun tableRowsArePaddedToWidth() {
        val table = ref.topic("passato-prossimo")!!.tables.single()
        assertEquals(4, table.width)
        assertTrue(table.hasHeader)
        assertEquals(listOf("tu", "hai parlato", "", ""), table.cells(1))
        assertEquals("Short rows are padded.", table.note)
        assertTrue(!ChartTable(columns = listOf("", "")).hasHeader)
    }

    @Test
    fun filterMatchesTitleSummaryLevelIgnoringAccents() {
        assertEquals(2, ReferenceParser.filter(ref, "").size) // empty section dropped
        val hits = ReferenceParser.filter(ref, "PASSATO")
        assertEquals(listOf("passato-prossimo"), hits.flatMap { it.topics }.map { it.id })
        assertEquals(listOf("pronomi-diretti"), ReferenceParser.filter(ref, "b1").flatMap { it.topics }.map { it.id })
        assertEquals(listOf("imperfetto"), ReferenceParser.filter(ref, "habits pàst").flatMap { it.topics }.map { it.id })
        assertTrue(ReferenceParser.filter(ref, "congiuntivo").isEmpty())
    }

    @Test
    fun noteLinksToBestMatchingTopic() {
        assertEquals("imperfetto", ReferenceParser.topicForNote(ref, "b1-01", "L'imperfetto: abitudini")?.id)
        assertEquals("passato-prossimo", ReferenceParser.topicForNote(ref, "b1-01", "Il passato prossimo con essere")?.id)
        // No overlap: the first related topic.
        assertEquals("passato-prossimo", ReferenceParser.topicForNote(ref, "b1-01", "Ripasso")?.id)
        assertEquals("passato-prossimo", ReferenceParser.topicForNote(ref, "a2r-01", "anything")?.id)
        assertNull(ReferenceParser.topicForNote(ref, "c1-09", "Passato prossimo"))
    }

    /**
     * Validates the shipped reference against the spec when the file exists: parses, unique
     * topic ids, levels A2–C1, at most 5 columns per table, and relatedUnitIds exist in course.json.
     */
    @Test
    fun shippedReferenceFollowsSpec() {
        val file = listOf(File("src/main/assets/grammar/reference.json"), File("app/src/main/assets/grammar/reference.json"))
            .firstOrNull { it.isFile } ?: return
        val reference = try {
            ReferenceParser.parse(file.readText())
        } catch (e: Exception) {
            throw AssertionError("reference.json does not parse: ${e.message}", e)
        }
        val courseFile = File(file.parentFile.parentFile, "course/course.json")
        val unitIds = if (courseFile.isFile) ContentParser.parseCourse(courseFile.readText()).units.map { it.id }.toSet() else null
        assertTrue("reference.json has no topics", reference.topics.isNotEmpty())
        val ids = mutableSetOf<String>()
        for (section in reference.sections) {
            assertTrue("section ${section.id} has no title", section.title.isNotBlank())
            for (t in section.topics) {
                val where = "topic ${t.id}"
                assertTrue("$where: duplicate id", ids.add(t.id))
                assertTrue("$where: blank title", t.title.isNotBlank())
                assertTrue("$where: level '${t.level}' not one of ${ReferenceParser.LEVELS}", t.level in ReferenceParser.LEVELS)
                assertTrue("$where: blank summary", t.summary.isNotBlank())
                for (table in t.tables) {
                    assertTrue("$where table '${table.title}': more than ${ChartTable.MAX_COLUMNS} columns", table.width <= ChartTable.MAX_COLUMNS)
                    assertTrue("$where table '${table.title}': no rows", table.rows.isNotEmpty())
                }
                t.examples.forEach { assertTrue("$where: example without Italian", it.it.isNotBlank()) }
                if (unitIds != null) {
                    t.relatedUnitIds.forEach { assertTrue("$where: unknown unit $it", it in unitIds) }
                }
            }
        }
    }
}
