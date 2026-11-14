package com.gleaped.italianlearning.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.ItalianoApp
import com.gleaped.italianlearning.domain.reminders.ReminderComposer
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
        val container = (context.applicationContext as ItalianoApp).container
        if (intent.action == ReminderScheduler.ACTION_SNOOZED) {
            val unitId = intent.getStringExtra(ReminderActionReceiver.EXTRA_UNIT_ID)
            val tipId = intent.getStringExtra(ReminderActionReceiver.EXTRA_TIP_ID)
            runAsync { showSnoozed(context, container, unitId, tipId) }
            return
        }
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        runAsync {
            val now = ZonedDateTime.now()
            val s = container.settings.current()
            val lastOpened = s.lastOpenedMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val lastFired = s.lastReminderFiredMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val practicedToday = container.learning.practicedToday()
            if (ReminderPlanner.shouldNotify(now, lastOpened, lastFired, s.remindersPaused, s.quietAfterPractice, practicedToday)) {
                val inactive = ReminderPlanner.isInactiveAt(now.toInstant(), lastOpened)
                val reminder = container.reminderContent.build(
                    seed = now.toLocalDate().toEpochDay() * 31 + now.hour,
                    inactive = inactive,
                    practicedToday = practicedToday,
                )
                Notifications.showReminder(context, reminder)
                container.settings.recordReminder(now.toInstant().toEpochMilli(), reminder.tipId)
            }
            container.reminderScheduler.reschedule(now)
        }
    }
}

/** "Later" was tapped an hour ago: the same grammar tip comes back, or a fresh reminder of another kind. */
private suspend fun showSnoozed(context: Context, container: AppContainer, unitId: String?, tipId: String?) {
    val now = ZonedDateTime.now()
    val s = container.settings.current()
    if (s.remindersPaused) return
    val seed = now.toLocalDate().toEpochDay() * 31 + now.hour
    val practicedToday = container.learning.practicedToday()
    if (s.quietAfterPractice && practicedToday) return
    val reminder = (if (unitId != null && tipId != null) container.reminderContent.forTip(unitId, tipId, seed) else null)
        ?: container.reminderContent.build(seed = seed, inactive = false, practicedToday = practicedToday)
    Notifications.showReminder(context, reminder)
}

/** Buttons on a reminder: an answer to its question, or "Più tardi". */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ItalianoApp).container
        val unitId = intent.getStringExtra(EXTRA_UNIT_ID)
        val tipId = intent.getStringExtra(EXTRA_TIP_ID)
        when (intent.action) {
            ACTION_LATER -> {
                Notifications.cancelReminder(context)
                container.reminderScheduler.snooze(unitId, tipId)
            }
            ACTION_ANSWER -> {
                val chosen = intent.getStringExtra(EXTRA_CHOSEN)
                if (unitId == null || tipId == null || chosen == null) return
                runAsync {
                    val tip = container.reminderContent.tip(unitId, tipId)
                    if (tip == null) {
                        Notifications.cancelReminder(context)
                    } else {
                        // The same notification turns into the verdict and the rule.
                        Notifications.showReminder(context, ReminderComposer.quizResult(tip, chosen), later = false)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.gleaped.italianlearning.action.REMINDER_ANSWER"
        const val ACTION_LATER = "com.gleaped.italianlearning.action.REMINDER_LATER"
        const val EXTRA_UNIT_ID = "unit_id"
        const val EXTRA_TIP_ID = "tip_id"
        const val EXTRA_CHOSEN = "chosen"

        fun answerIntent(context: Context, unitId: String, tipId: String, chosen: String): Intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_ANSWER)
                .putExtra(EXTRA_UNIT_ID, unitId)
                .putExtra(EXTRA_TIP_ID, tipId)
                .putExtra(EXTRA_CHOSEN, chosen)

        fun laterIntent(context: Context, unitId: String?, tipId: String?): Intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_LATER)
                .putExtra(EXTRA_UNIT_ID, unitId)
                .putExtra(EXTRA_TIP_ID, tipId)
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
