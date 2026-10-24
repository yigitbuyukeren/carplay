package com.ridedeck.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ridedeck.app.service.ReminderReceiver

/** In-app reminders: one alarm that raises a notification, no calendar. Inexact on purpose (no exact-alarm permission needed). */
object Reminders {
    fun schedule(context: Context, minutes: Int, what: String?): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_WHAT, what)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pending)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
