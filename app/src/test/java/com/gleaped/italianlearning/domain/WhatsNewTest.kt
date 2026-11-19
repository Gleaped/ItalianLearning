package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.profile.WhatsNew
import com.gleaped.italianlearning.domain.profile.WhatsNewNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewTest {
    private val notes = listOf(WhatsNewNote(1, "a"), WhatsNewNote(2, "b"))

    @Test fun showsTheNewestUnseenNote() {
        assertEquals(2, WhatsNew.pending(0, notes)?.id)
        assertEquals(2, WhatsNew.pending(1, notes)?.id)
    }

    @Test fun nothingWhenAllSeen() {
        assertNull(WhatsNew.pending(2, notes))
        assertNull(WhatsNew.pending(5, notes))
    }
}
