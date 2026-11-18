package com.gleaped.italianlearning.domain.conversation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ConversationEngineTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun scenarios(): List<Pair<String, Scenario>> {
        val dir = listOf(File("src/main/assets/conversations"), File("app/src/main/assets/conversations")).firstOrNull { it.isDirectory }
        assertNotNull("conversations folder", dir)
        return dir!!.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
            .map { it.name to json.decodeFromString(Scenario.serializer(), it.readText()) }
    }

    @Test
    fun everyScenarioIsStructurallySound() {
        val all = scenarios()
        assertTrue(all.size >= 10)
        for ((name, s) in all) assertEquals("$name: ${ScenarioCheck.problems(s)}", emptyList<String>(), ScenarioCheck.problems(s))
        assertEquals("ids are unique", all.size, all.map { it.second.id }.toSet().size)
    }

    @Test
    fun theModelAnswerOfEveryMomentIsUnderstoodAndReachesTheGoal() {
        for ((name, s) in scenarios()) {
            val e = ConversationEngine(s, Random(1))
            e.open()
            var guard = 0
            while (!e.ended && guard++ < 30) {
                val model = e.current.model
                assertNotNull("$name/${e.current.id} needs a model answer", model)
                val r = e.respond(listOf(model!!))
                assertEquals("$name/${e.current.id}: model not understood", Response.Kind.MATCHED, r.kind)
            }
            assertTrue("$name ends", e.ended)
            assertTrue("$name goal", e.debrief().goalReached)
        }
    }

    @Test
    fun aLearnerWhoSaysNothingUsefulIsMovedOnAndTheSceneStillEnds() {
        for ((name, s) in scenarios()) {
            val e = ConversationEngine(s, Random(2))
            e.open()
            var turns = 0
            while (!e.ended && turns++ < 60) e.respond(listOf("blorp zzz"))
            assertTrue("$name ends", e.ended)
            assertTrue(e.debrief().missed > 0)
        }
    }

    private fun bar() = scenarios().first { it.second.id == "a1-bar" }.second

    @Test
    fun theFirstMatchingBranchWinsAndSlotsAreRemembered() {
        val e = ConversationEngine(bar(), Random(3))
        e.open()
        val r = e.respond(listOf("Vorrei un cappuccino per favore"))
        assertEquals(Response.Kind.MATCHED, r.kind)
        assertEquals("un cappuccino", e.slots["drink"])
        assertEquals("food", e.current.id)
        e.respond(listOf("no grazie"))
        val pay = e.respond(listOf("ecco"))
        assertTrue(pay.lines.joinToString(" "), pay.lines.any { it.contains("cappuccino") || it.contains("resto") })
    }

    @Test
    fun aRequestToRepeatRepeatsSlowlyWithoutCountingAsAMiss() {
        val e = ConversationEngine(bar(), Random(4))
        val first = e.open().lines.single()
        val r = e.respond(listOf("Può ripetere, per favore?"))
        assertEquals(Response.Kind.REPEAT, r.kind)
        assertEquals(first, r.lines.single())
        assertTrue(r.slow)
        assertEquals(0, e.debrief().missed)
    }

    @Test
    fun aMisunderstoodAnswerIsRepairedThenHelpedThenMovedOn() {
        val e = ConversationEngine(bar(), Random(5))
        e.open()
        val first = e.respond(listOf("blorp"))
        assertEquals(Response.Kind.REPAIR, first.kind)
        assertEquals(null, first.hint)
        val second = e.respond(listOf("blorp"))
        assertEquals(Response.Kind.MOVED_ON, second.kind)
        assertEquals("what", e.current.id)
    }

    @Test
    fun skippingIsStuckAtOnce() {
        val e = ConversationEngine(bar(), Random(6))
        e.open()
        val r = e.respond(emptyList(), skipped = true)
        assertEquals(Response.Kind.MOVED_ON, r.kind)
    }

    @Test
    fun theRecognisersLaterGuessesAreUsed() {
        val e = ConversationEngine(bar(), Random(7))
        e.open()
        val r = e.respond(listOf("bla bla", "un caffè"))
        assertEquals(Response.Kind.MATCHED, r.kind)
        assertEquals("un caffè", e.slots["drink"])
    }

    @Test
    fun correctionsAreCollectedOncePerKind() {
        val e = ConversationEngine(bar(), Random(8))
        e.open()
        e.respond(listOf("io sono 25 anni e vorrei un caffè"))
        val d = e.debrief()
        assertEquals(listOf("age-essere"), d.corrections.map { it.id })
        assertFalse(d.corrections.isEmpty())
    }

    @Test
    fun slotsAreFilledWithADefaultWhenUnknown() {
        val e = ConversationEngine(bar(), Random(9))
        assertEquals("Un caffè, grazie", e.fill("{drink|un caffè}, grazie"))
        assertEquals("Ciao", e.fill("ciao {drink|}"))
    }
}
