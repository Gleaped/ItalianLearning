package com.gleaped.italianlearning.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gleaped.italianlearning.data.db.ReminderDao
import com.gleaped.italianlearning.data.db.ReminderEntity
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.domain.reminders.ReminderPlanner
import com.gleaped.italianlearning.domain.reminders.ReminderSpec
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime

fun ReminderEntity.toSpec(): ReminderSpec = ReminderSpec(
    id = id,
    hour = hour,
    minute = minute,
    days = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
    enabled = enabled,
)

fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

/**
 * Registers a single inexact AlarmManager alarm for the next reminder.
 *
 * Why AlarmManager rather than WorkManager: reminders are tied to wall-clock times the
 * user picked ("19:00 on weekdays"). WorkManager has no notion of time-of-day, periodic
 * work drifts and is batched freely, so a "7 pm" reminder could arrive at 9 pm.
 * `setAndAllowWhileIdle` (inexact, Doze-tolerant) fires close to the requested time
 * without needing the SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permissions, which are
 * unnecessary for a friendly nudge. Alarms do not survive reboots or clock changes, so
 * [BootReceiver] calls [reschedule] for BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED and
 * MY_PACKAGE_REPLACED; the app also reschedules on every launch.
 */
class ReminderScheduler(
    private val context: Context,
    private val reminderDao: ReminderDao,
    private val settings: SettingsRepository,
) {
    suspend fun reschedule(now: ZonedDateTime = ZonedDateTime.now()) {
        val s = settings.current()
        val next = ReminderPlanner.nextFireTime(
            reminders = reminderDao.all().map { it.toSpec() },
            now = now,
            lastOpened = s.lastOpenedMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            lastFired = s.lastReminderFiredMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            paused = s.remindersPaused,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarmManager.cancel(pending)
        if (next != null) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.gleaped.italianlearning.action.REMINDER"
        private const val REQUEST_CODE = 4201

        fun alarmIntent(context: Context): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
