package com.gleaped.italianlearning.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gleaped.italianlearning.R
import com.gleaped.italianlearning.domain.reminders.ReminderContent
import com.gleaped.italianlearning.domain.reminders.ReminderTarget

object Notifications {
    const val CHANNEL_REMINDERS = "practice_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CONTENT = 0
    private const val REQUEST_ACTION = 1
    private const val REQUEST_LATER = 2
    private const val REQUEST_OPTION_BASE = 10

    /** Android shows at most this many buttons on a notification. */
    private const val MAX_ACTIONS = 3
    const val LATER_LABEL = "Più tardi"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Permission is checked at the top of the function (lint cannot always see through the SDK check).
    @SuppressLint("MissingPermission")
    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    /**
     * Posts [content], replacing any reminder still showing. A quiz gets one button per option;
     * [later] adds "Più tardi" when there is room for it.
     */
    fun showReminder(context: Context, content: ReminderContent, later: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            DeepLinks.intentFor(context, content.target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        var actions = 0
        content.quiz?.let { quiz ->
            quiz.options.take(MAX_ACTIONS).forEachIndexed { index, option ->
                val answer = PendingIntent.getBroadcast(
                    context,
                    REQUEST_OPTION_BASE + index,
                    ReminderActionReceiver.answerIntent(context, quiz.unitId, quiz.tipId, option),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, option, answer).build())
                actions++
            }
        }
        if (content.quiz == null) content.actionLabel?.let { label ->
            val actionIntent = PendingIntent.getActivity(
                context,
                REQUEST_ACTION,
                DeepLinks.intentFor(context, content.target),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, label, actionIntent).build())
            actions++
        }
        if (later && actions < MAX_ACTIONS) {
            val target = content.target as? ReminderTarget.Challenge
            val laterIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_LATER,
                ReminderActionReceiver.laterIntent(context, target?.unitId, target?.tipId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, LATER_LABEL, laterIntent).build())
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
