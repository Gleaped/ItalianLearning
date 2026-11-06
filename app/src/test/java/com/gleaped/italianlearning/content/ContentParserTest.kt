package com.gleaped.italianlearning.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentParserTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    private val unit by lazy { ContentParser.parseUnit(resource("all_types_unit.json")) }
    private val exercises by lazy { unit.lessons.single().exercises }

    @Test
    fun courseParsesAndIgnoresUnknownFields() {
        val course = ContentParser.parseCourse(resource("course.json"))
        assertEquals(4, course.levels.size)
        assertTrue(course.levels.first { it.id == "A2R" }.remedial)
        assertFalse(course.levels.first { it.id == "B1" }.remedial)
        assertEquals(4, course.units.size)
        assertFalse(course.units.first { it.id == "b1-03" }.available)
    }

    @Test
    fun unitMetadata() {
        assertEquals("b1-01", unit.id)
        assertEquals(listOf("a2r-01"), unit.remedialUnitIds)
        assertEquals(2, unit.vocabulary.size)
        assertEquals(null, unit.vocabulary[1].example)
        assertEquals(12, unit.lessons.single().minutes)
    }

    @Test
    fun fixtureCoversEveryDocumentedType() {
        val known = exercises.filter { it !is Unsupported && it !is Invalid }
        val typeNames = known.map { it::class.simpleName }.toSet()
        assertEquals(
            setOf(
                "GrammarNote", "Flashcard", "MultipleChoice", "GapFill", "Conjugation", "SentenceBuild",
                "Translate", "MatchPairs", "ErrorCorrection", "ListenTranscribe", "ListenChoose", "Reading",
                "SpeakRepeat", "SpeakRespond", "Roleplay", "FreeWrite",
            ),
            typeNames,
        )
        assertEquals(ExerciseTypes.ALL.size, typeNames.size)
    }

    @Test
    fun eachTypeDecodesItsFields() {
        val note = exercises[0] as GrammarNote
        assertEquals("e1", note.id)
        assertEquals("Vorrei un caffè.", note.examples.single().it)
        assertFalse(note.scored)

        val card = exercises[1] as Flashcard
        assertEquals("receipt", card.back)
        assertEquals("Mi può fare la ricevuta?", card.example)

        val mc = exercises[2] as MultipleChoice
        assertEquals(1, mc.answer)
        assertEquals(3, mc.options.size)
        assertEquals("Scegli", mc.instruction)
        assertEquals("Vorrei is polite.", mc.explanation)

        val gap = exercises[3] as GapFill
        assertEquals(listOf("porta", "può portare"), gap.answers)
        assertEquals("portare", gap.hint)

        val conj = exercises[4] as Conjugation
        assertEquals("volere", conj.verb)
        assertEquals("tu (f.)", conj.items[1].person)

        val build = exercises[5] as SentenceBuild
        assertEquals(4, build.tiles.size)
        assertEquals(listOf("voglio"), build.distractors)

        val toIt = exercises[6] as Translate
        assertFalse(toIt.fromItalian)
        val toEn = exercises[7] as Translate
        assertTrue(toEn.fromItalian)

        val match = exercises[8] as MatchPairs
        assertEquals("the receipt", match.pairs[1].right)

        val err = exercises[9] as ErrorCorrection
        assertEquals("Io vorrebbe un caffè.", err.sentence)

        val lt = exercises[10] as ListenTranscribe
        assertTrue(lt.slow)

        val lc = exercises[11] as ListenChoose
        assertEquals("Quanto costa?", lc.question)
        assertEquals(0, lc.answer)

        val reading = exercises[12] as Reading
        assertEquals(1, reading.questions.single().answer)

        val sr = exercises[13] as SpeakRepeat
        assertEquals("I'd like a croissant.", sr.translation)

        val resp = exercises[14] as SpeakRespond
        assertEquals(listOf("vorrei", "per favore"), resp.keywords)

        val rp = exercises[15] as Roleplay
        assertTrue(rp.turns[0].isNpc)
        assertFalse(rp.turns[1].isNpc)
        assertEquals(listOf("caffè"), rp.turns[1].keywords)
        assertEquals("Order a coffee", rp.turns[1].hint)

        val fw = exercises[16] as FreeWrite
        assertEquals(30, fw.minWords)
        assertEquals(1, fw.checklist.size)
    }

    @Test
    fun unknownTypeBecomesUnsupported() {
        val ex = exercises[17]
        assertTrue(ex is Unsupported)
        assertEquals("hologram_quiz", (ex as Unsupported).type)
    }

    @Test
    fun brokenExerciseBecomesInvalidWithoutBreakingUnit() {
        val ex = exercises[18]
        assertTrue("was $ex", ex is Invalid)
        assertEquals("multiple_choice", (ex as Invalid).type)
    }

    /** Parses the real shipped content, when present, so broken JSON fails CI. */
    @Test
    fun shippedContentParses() {
        val dir = listOf(File("src/main/assets/course"), File("app/src/main/assets/course")).firstOrNull { it.isDirectory }
            ?: return
        val course = ContentParser.parseCourse(File(dir, "course.json").readText())
        assertTrue(course.levels.isNotEmpty())
        for (ref in course.units.filter { it.available }) {
            val file = File(dir, "units/${ref.id}.json")
            assertTrue("missing unit file for available unit ${ref.id}", file.isFile)
            val unit = ContentParser.parseUnit(file.readText())
            assertEquals(ref.id, unit.id)
            for (lesson in unit.lessons) {
                for (ex in lesson.exercises) {
                    assertFalse("${lesson.id}: $ex", ex is Invalid || ex is Unsupported)
                    if (ex is MultipleChoice) assertTrue("${lesson.id} answer index", ex.answer in ex.options.indices)
                    if (ex is ListenChoose) assertTrue("${lesson.id} answer index", ex.answer in ex.options.indices)
                    if (ex is Reading) ex.questions.forEach { assertTrue("${lesson.id} reading answer", it.answer in it.options.indices) }
                }
            }
            unit.remedialUnitIds.forEach { id -> assertTrue("unknown remedial unit $id", course.units.any { it.id == id }) }
        }
    }
}
