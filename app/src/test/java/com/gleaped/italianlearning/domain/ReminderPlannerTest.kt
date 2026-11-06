package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.reminders.ReminderMessages
import com.gleaped.italianlearning.domain.reminders.ReminderPlanner
import com.gleaped.italianlearning.domain.reminders.ReminderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val zone = ZoneId.of("Europe/London")
    private val everyDay = DayOfWeek.entries.toSet()

    // 2026-11-04 is a Wednesday.
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 11): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(2026, month, day, hour, minute), zone)

    private val morning = ReminderSpec(1, 8, 30, everyDay)
    private val evening = ReminderSpec(2, 19, 0, everyDay)

    private fun next(
        reminders: List<ReminderSpec>,
        now: ZonedDateTime,
        lastOpened: ZonedDateTime? = now,
        lastFired: ZonedDateTime? = null,
        paused: Boolean = false,
    ) = ReminderPlanner.nextFireTime(reminders, now, lastOpened?.toInstant(), lastFired?.toInstant(), paused)

    @Test
    fun laterTodayWhenStillAhead() {
        assertEquals(at(4, 8, 30), next(listOf(morning, evening), at(4, 7, 0)))
        assertEquals(at(4, 19, 0), next(listOf(morning, evening), at(4, 9, 0)))
    }

    @Test
    fun rollsToTomorrowAfterLastSlot() {
        assertEquals(at(5, 8, 30), next(listOf(morning, evening), at(4, 20, 0)))
    }

    @Test
    fun exactlyNowIsNotRepeated() {
        assertEquals(at(4, 19, 0), next(listOf(morning, evening), at(4, 8, 30)))
    }

    @Test
    fun respectsDaysOfWeek() {
        val weekendOnly = ReminderSpec(3, 10, 0, setOf(SATURDAY))
        assertEquals(at(7, 10, 0), next(listOf(weekendOnly), at(4, 12, 0)))
        val mwf = ReminderSpec(4, 18, 0, setOf(MONDAY, WEDNESDAY, FRIDAY))
        assertEquals(at(6, 18, 0), next(listOf(mwf), at(4, 18, 30)))
        // Same weekday next week when today's slot has passed.
        val wedOnly = ReminderSpec(5, 7, 0, setOf(WEDNESDAY))
        assertEquals(at(11, 7, 0), next(listOf(wedOnly), at(4, 8, 0)))
    }

    @Test
    fun disabledEmptyOrPausedYieldNothing() {
        assertNull(next(listOf(morning.copy(enabled = false)), at(4, 7)))
        assertNull(next(listOf(morning.copy(days = emptySet())), at(4, 7)))
        assertNull(next(listOf(morning), at(4, 7), paused = true))
        assertNull(next(emptyList(), at(4, 7)))
    }

    @Test
    fun inactiveUserGetsAtMostOnePerDay() {
        val lastOpened = at(1, 12, 0) // 3 days before
        // Morning already fired today -> skip the evening, next is tomorrow morning.
        assertEquals(at(5, 8, 30), next(listOf(morning, evening), at(4, 9, 0), lastOpened, lastFired = at(4, 8, 30)))
        // Nothing fired today yet -> evening still allowed.
        assertEquals(at(4, 19, 0), next(listOf(morning, evening), at(4, 9, 0), lastOpened, lastFired = at(3, 8, 30)))
    }

    @Test
    fun activeUserGetsAllReminders() {
        val lastOpened = at(3, 20, 0) // yesterday evening
        assertEquals(at(4, 19, 0), next(listOf(morning, evening), at(4, 9, 0), lastOpened, lastFired = at(4, 8, 30)))
    }

    @Test
    fun inactivityIsJudgedAtFireTime() {
        // Opened 2026-11-02 at 10:00; morning on the 4th is 46.5h later (active), evening is 57h later (quiet).
        val lastOpened = at(2, 10, 0)
        assertEquals(at(5, 8, 30), next(listOf(morning, evening), at(4, 9, 0), lastOpened, lastFired = at(4, 8, 30)))
    }

    @Test
    fun shouldNotifyRules() {
        val now = at(4, 19, 0)
        assertTrue(ReminderPlanner.shouldNotify(now, at(4, 10).toInstant(), at(4, 8, 30).toInstant(), false))
        assertFalse(ReminderPlanner.shouldNotify(now, at(4, 10).toInstant(), null, true))
        assertFalse(ReminderPlanner.shouldNotify(now, at(1, 10).toInstant(), at(4, 8, 30).toInstant(), false))
        assertTrue(ReminderPlanner.shouldNotify(now, at(1, 10).toInstant(), at(3, 19).toInstant(), false))
    }

    @Test
    fun handlesDstSpringForwardGap() {
        // 2027-03-28 01:00 GMT -> 02:00 BST in London; a 01:30 reminder does not exist that day.
        val r = ReminderSpec(9, 1, 30, everyDay)
        val now = ZonedDateTime.of(LocalDateTime.of(2027, 3, 28, 0, 0), zone)
        val fire = next(listOf(r), now)!!
        assertTrue(fire.isAfter(now))
        assertEquals(28, fire.dayOfMonth)
    }

    @Test
    fun messagesAreGentleAndVaried() {
        val banned = listOf("streak", "lose", "lost", "miss", "fail", "disappoint", "shame", "guilt", "don't forget")
        ReminderMessages.all.forEach { m ->
            val text = (m.title + " " + m.body).lowercase()
            banned.forEach { word -> assertFalse("'$word' in $m", text.contains(word)) }
        }
        val picks = (0L until 10L).map { ReminderMessages.pick(it, inactive = false, practicedToday = false) }.toSet()
        assertTrue(picks.size >= 5)
        assertEquals(ReminderMessages.pick(-3, true, false), ReminderMessages.pick(1, true, false))
    }
}
