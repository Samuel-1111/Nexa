package com.nexa.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexa.core.database.NexaDatabase

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        Thread {
            try {
                val db = NexaDatabase.getInstance(context)
                val alarms = context.getSystemService(android.app.AlarmManager::class.java)
                val now = System.currentTimeMillis()
                kotlinx.coroutines.runBlocking {
                    db.reminderDao().getFuture(now).forEach { row ->
                        val alarmIntent = Intent(context, ReminderAlarmReceiver::class.java)
                            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, row.id)
                            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_TITLE, row.title)
                        val pi = android.app.PendingIntent.getBroadcast(
                            context,
                            row.id.hashCode(),
                            alarmIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {\n                            alarms.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, row.triggerAtEpochMs, pi)\n                        } else {\n                            alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, row.triggerAtEpochMs, pi)\n                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}