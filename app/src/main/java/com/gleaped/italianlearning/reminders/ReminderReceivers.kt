package com.gleaped.italianlearning.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gleaped.italianlearning.ItalianoApp
import com.gleaped.italianlearning.domain.reminders.ReminderMessages
import com.gleaped.italianlearning.domain.reminders.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/** Runs [block] off the main thread while keeping the receiver alive via goAsync(). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Reminders", "Reminder work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** Fired by the alarm: maybe show a notification, then schedule the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val container = (context.applicationContext as ItalianoApp).container
        runAsync {
            val now = ZonedDateTime.now()
            val s = container.settings.current()
            val lastOpened = s.lastOpenedMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val lastFired = s.lastReminderFiredMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            if (ReminderPlanner.shouldNotify(now, lastOpened, lastFired, s.remindersPaused)) {
                val inactive = ReminderPlanner.isInactiveAt(now.toInstant(), lastOpened)
                val message = ReminderMessages.pick(
                    seed = now.toLocalDate().toEpochDay() * 31 + now.hour,
                    inactive = inactive,
                    practicedToday = container.learning.practicedToday(),
                )
                Notifications.showReminder(context, message)
                container.settings.markReminderFired(now.toInstant().toEpochMilli())
            }
            container.reminderScheduler.reschedule(now)
        }
    }
}

/** Alarms are dropped on reboot and may be stale after clock / time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val container = (context.applicationContext as ItalianoApp).container
                runAsync { container.reminderScheduler.reschedule() }
            }
        }
    }
}
