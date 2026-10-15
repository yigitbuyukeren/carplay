package dev.ygtlab.carplay.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/** Caller name taken from the dialer's ongoing-call notification (the phone-state API does not expose it). */
object CallBridge {
    val callerName = MutableStateFlow<String?>(null)
}

class CarNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.category != Notification.CATEGORY_CALL) return
        CallBridge.callerName.value = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_CALL) CallBridge.callerName.value = null
    }
}
