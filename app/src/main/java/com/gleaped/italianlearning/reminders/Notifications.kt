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

object Notifications {
    const val CHANNEL_REMINDERS = "practice_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CONTENT = 0
    private const val REQUEST_ACTION = 1

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
    fun showReminder(context: Context, content: ReminderContent) {
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
        content.actionLabel?.let { label ->
            val actionIntent = PendingIntent.getActivity(
                context,
                REQUEST_ACTION,
                DeepLinks.intentFor(context, content.target),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, label, actionIntent).build())
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
