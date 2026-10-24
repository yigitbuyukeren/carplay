package com.ridedeck.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ridedeck.app.MainActivity
import com.ridedeck.app.R
import com.ridedeck.app.util.hasPermission

/** Fires a reminder set by voice ("yirmi dakika sonra hatırlat"): a notification with the text, nothing else. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        if (android.os.Build.VERSION.SDK_INT >= 33 && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val what = intent.getStringExtra(EXTRA_WHAT)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            intent.getIntExtra(EXTRA_ID, 0),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(what ?: context.getString(R.string.reminder_default_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    companion object {
        const val CHANNEL = "reminders"
        const val EXTRA_WHAT = "what"
        const val EXTRA_ID = "id"
    }
}
