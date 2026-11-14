package com.gleaped.italianlearning.reminders

import android.content.Context
import android.content.Intent
import com.gleaped.italianlearning.MainActivity
import com.gleaped.italianlearning.domain.reminders.ReminderTarget

/**
 * Intents from notifications into [MainActivity]. A target arrives as extras on the
 * launch intent (cold start) or via onNewIntent (app already running).
 */
object DeepLinks {
    private const val EXTRA_OPEN = "open"
    private const val EXTRA_UNIT_ID = "unit_id"
    private const val EXTRA_TIP_ID = "tip_id"

    /** v1 notifications still sitting in the shade carry this flag. */
    private const val EXTRA_OPEN_TODAY_V1 = "open_today"

    private const val OPEN_TODAY = "today"
    private const val OPEN_REVIEW = "review"
    private const val OPEN_LOOKUP = "lookup"
    private const val OPEN_CHALLENGE = "challenge"

    fun intentFor(context: Context, target: ReminderTarget): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (target) {
                ReminderTarget.Today -> putExtra(EXTRA_OPEN, OPEN_TODAY)
                ReminderTarget.Review -> putExtra(EXTRA_OPEN, OPEN_REVIEW)
                ReminderTarget.Lookup -> putExtra(EXTRA_OPEN, OPEN_LOOKUP)
                is ReminderTarget.Challenge -> {
                    putExtra(EXTRA_OPEN, OPEN_CHALLENGE)
                    putExtra(EXTRA_UNIT_ID, target.unitId)
                    putExtra(EXTRA_TIP_ID, target.tipId)
                }
            }
        }

    /** The target carried by [intent], or null for a normal launch. */
    fun parse(intent: Intent?): ReminderTarget? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_OPEN_TODAY_V1, false)) return ReminderTarget.Today
        return when (intent.getStringExtra(EXTRA_OPEN)) {
            OPEN_TODAY -> ReminderTarget.Today
            OPEN_REVIEW -> ReminderTarget.Review
            OPEN_LOOKUP -> ReminderTarget.Lookup
            OPEN_CHALLENGE -> {
                val unitId = intent.getStringExtra(EXTRA_UNIT_ID)
                val tipId = intent.getStringExtra(EXTRA_TIP_ID)
                if (unitId.isNullOrBlank() || tipId.isNullOrBlank()) ReminderTarget.Today else ReminderTarget.Challenge(unitId, tipId)
            }
            else -> null
        }
    }
}
