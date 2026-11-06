package com.gleaped.italianlearning.domain.reminders

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One user-configured reminder. [days] empty means the reminder never fires. */
data class ReminderSpec(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
)

/**
 * Pure scheduling rules for practice reminders.
 *
 * Only one alarm is ever registered with the OS: the earliest upcoming reminder.
 * When it fires, the app posts (or suppresses) the notification and asks the
 * planner for the next one.
 *
 * Quiet mode: if the app has not been opened for [inactiveThreshold] (2 days) at
 * the moment a reminder would fire, at most one reminder is delivered per
 * calendar day, until the user opens the app again.
 */
object ReminderPlanner {

    val inactiveThreshold: Duration = Duration.ofDays(2)

    fun isInactiveAt(at: Instant, lastOpened: Instant?): Boolean =
        lastOpened != null && Duration.between(lastOpened, at) >= inactiveThreshold

    /**
     * Next moment a notification should be shown, strictly after [now], or null if none
     * (paused, no enabled reminders, or no selected days).
     */
    fun nextFireTime(
        reminders: List<ReminderSpec>,
        now: ZonedDateTime,
        lastOpened: Instant?,
        lastFired: Instant?,
        paused: Boolean,
    ): ZonedDateTime? {
        if (paused) return null
        val active = reminders.filter { it.enabled && it.days.isNotEmpty() && validTime(it) }
        if (active.isEmpty()) return null
        val zone: ZoneId = now.zone
        val lastFiredDate = lastFired?.atZone(zone)?.toLocalDate()
        val today = now.toLocalDate()

        // 8 days covers "same weekday next week" for a reminder earlier than now today.
        // Extra days handle quiet-mode skips (at most one skipped day, as the
        // last-fired day can only be today).
        for (offset in 0L..9L) {
            val date = today.plusDays(offset)
            val candidates = active
                .filter { date.dayOfWeek in it.days }
                .map { ZonedDateTime.of(date, LocalTime.of(it.hour, it.minute), zone) }
                .filter { it.isAfter(now) }
                .sorted()
            for (t in candidates) {
                val quiet = isInactiveAt(t.toInstant(), lastOpened)
                if (quiet && lastFiredDate != null && lastFiredDate == date) continue
                return t
            }
        }
        return null
    }

    /** Checked again when an alarm actually fires, since state may have changed since scheduling. */
    fun shouldNotify(
        now: ZonedDateTime,
        lastOpened: Instant?,
        lastFired: Instant?,
        paused: Boolean,
    ): Boolean {
        if (paused) return false
        if (!isInactiveAt(now.toInstant(), lastOpened)) return true
        val lastFiredDate = lastFired?.atZone(now.zone)?.toLocalDate() ?: return true
        return lastFiredDate != now.toLocalDate()
    }

    private fun validTime(r: ReminderSpec) = r.hour in 0..23 && r.minute in 0..59
}
