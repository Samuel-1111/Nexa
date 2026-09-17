package com.nexa.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "NEXA reminders", NotificationManager.IMPORTANCE_HIGH))
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE).orEmpty().ifBlank { "NEXA reminder" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("It is time for your reminder.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty().hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "nexa_reminders"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
    }
}
