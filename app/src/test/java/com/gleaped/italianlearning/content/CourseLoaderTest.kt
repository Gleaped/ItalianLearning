package com.gleaped.italianlearning.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CourseLoaderTest {
    private val course = """
        {"levels":[{"id":"B1","title":"B1"}],
         "units":[{"id":"u1","level":"B1","order":1,"title":"U1"},
                  {"id":"u2","level":"B1","order":2,"title":"U2"},
                  {"id":"u3","level":"B1","order":3,"title":"U3","available":false},
                  {"id":"broken","level":"B1","order":4,"title":"B"}]}
    """.trimIndent()

    private val files = mapOf(
        "course/course.json" to course,
        "course/units/u1.json" to """{"id":"u1","level":"B1","lessons":[{"id":"l1","exercises":[]}]}""",
        "course/units/u2.json" to """{"id":"u2","level":"B1","lessons":[]}""",
        "course/units/u3.json" to """{"id":"u3","level":"B1"}""",
        "course/units/broken.json" to """{"id": """,
    )
    private val reads = ConcurrentHashMap<String, AtomicInteger>()
    private val loader = CourseLoader(
        readText = { path ->
            reads.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            files[path] ?: throw FileNotFoundException(path)
        },
        dispatcher = Dispatchers.Default,
    )

    @Test
    fun unitIsLoadedLazilyAndOnce() = runBlocking {
        assertEquals("u1", loader.unit("u1")?.id)
        assertEquals("u1", loader.unit("u1")?.id)
        assertEquals(1, reads["course/units/u1.json"]?.get())
        assertNull(reads["course/units/u2.json"])
        assertNull(loader.unit("u3")) // not available
        assertNull(loader.unit("nope"))
        assertNull(loader.unit("broken"))
    }

    @Test
    fun allLoadsAvailableUnitsAndReportsErrors() = runBlocking {
        val all = loader.all()
        assertEquals(setOf("u1", "u2"), all.units.keys)
        assertEquals(1, all.loadErrors.size)
        assertTrue(all.loadErrors.single().contains("broken"))
        loader.all()
        assertEquals(1, reads["course/course.json"]?.get())
        assertEquals(1, reads["course/units/u2.json"]?.get())
    }

    @Test
    fun missingCourseGivesEmptyContent() = runBlocking {
        val empty = CourseLoader(readText = { throw FileNotFoundException(it) })
        val all = empty.all()
        assertTrue(all.units.isEmpty())
        assertEquals(listOf("course.json is missing"), all.loadErrors)
    }
}
