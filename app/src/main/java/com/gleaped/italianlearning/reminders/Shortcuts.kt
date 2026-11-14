package com.gleaped.italianlearning.reminders

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.gleaped.italianlearning.domain.reminders.ReminderTarget

/** Launcher shortcuts (long-press the app icon): review, today's lesson and the dictionary. */
object Shortcuts {
    fun publish(context: Context) {
        runCatching {
            val shortcuts = listOf(
                shortcut(context, "review", "Ripasso", "Review cards", ReminderTarget.Review),
                shortcut(context, "today", "Prossima lezione", "Next lesson", ReminderTarget.Today),
                shortcut(context, "lookup", "Dizionario", "Dictionary", ReminderTarget.Lookup),
            )
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    private fun shortcut(context: Context, id: String, shortLabel: String, longLabel: String, target: ReminderTarget) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            // A shortcut intent needs an action; the extras carry the destination.
            .setIntent(DeepLinks.intentFor(context, target).setAction(Intent.ACTION_VIEW))
            .build()
}
