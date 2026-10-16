package dev.ygtlab.carplay.service

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow

/** Caller name taken from the dialer's ongoing-call notification (the phone-state API does not expose it). */
object CallBridge {
    val callerName = MutableStateFlow<String?>(null)
}

data class IncomingMessage(
    val id: Long,
    val app: String,
    val sender: String,
    val text: String,
    val postedAtMs: Long,
)

/** Latest chat message seen in the notification shade. Kept in memory only, never persisted. */
object MessageBridge {
    val latest = MutableStateFlow<IncomingMessage?>(null)
    private val counter = AtomicLong()
    fun nextId(): Long = counter.incrementAndGet()
}

class CarNotificationListener : NotificationListenerService() {
    private var lastKey: String? = null
    private var lastText: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL) {
            CallBridge.callerName.value = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            return
        }
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isMessage = n.category == Notification.CATEGORY_MESSAGE ||
            n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name
        if (!isMessage) return

        val (sender, text) = parse(n.extras) ?: return
        // Chat apps re-post the same notification when other things change; ignore repeats.
        if (sbn.key == lastKey && text == lastText) return
        lastKey = sbn.key
        lastText = text
        MessageBridge.latest.value = IncomingMessage(
            id = MessageBridge.nextId(),
            app = appLabel(sbn.packageName),
            sender = sender,
            text = text,
            postedAtMs = sbn.postTime,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_CALL) CallBridge.callerName.value = null
    }

    @Suppress("DEPRECATION")
    private fun parse(extras: Bundle): Pair<String, String>? {
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!bundles.isNullOrEmpty()) {
            val last = Notification.MessagingStyle.Message
                .getMessagesFromBundleArray(bundles.filterIsInstance<Parcelable>().toTypedArray())
                .lastOrNull()
            val text = last?.text?.toString()
            if (last != null && !text.isNullOrBlank()) {
                val sender = last.senderPerson?.name?.toString()
                    ?: last.sender?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: ""
                return sender to text
            }
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
        return if (text.isNullOrBlank()) null else title to text
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
