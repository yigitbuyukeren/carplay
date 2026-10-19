package dev.ygtlab.carplay.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import dev.ygtlab.carplay.MainActivity
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.CallPhase
import dev.ygtlab.carplay.data.CallState
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.flow.MutableStateFlow

/** What the dashboard knows about calls while MotoDash is the default phone app; null state means "not managing". */
object InCallHub {
    val state = MutableStateFlow<CallState?>(null)
    @Volatile var service: MotoInCallService? = null
    @Volatile var call: Call? = null

    fun phaseOf(callState: Int): CallPhase = when (callState) {
        Call.STATE_RINGING -> CallPhase.RINGING
        Call.STATE_NEW, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL, Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.DIALING
        else -> CallPhase.ACTIVE
    }
}

/**
 * Lets MotoDash carry the whole call itself instead of handing over to the phone's own call screen.
 * The system only binds this once the user has made MotoDash the default phone app.
 */
class MotoInCallService : InCallService() {
    @Volatile private var latestAudio: CallAudioState? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    override fun onCreate() {
        super.onCreate()
        InCallHub.service = this
    }

    override fun onDestroy() {
        InCallHub.service = null
        InCallHub.call = null
        InCallHub.state.value = null
        CallBridge.callerName.value = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        publish()
        val incoming = call.state == Call.STATE_RINGING
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (incoming) {
            // A full-screen notification is how the system lets a dialer come up over whatever is on screen.
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.call_incoming), NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(getString(R.string.call_incoming))
                .setContentText(CallBridge.callerName.value ?: getString(R.string.phone_unknown))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setFullScreenIntent(pi, true)
                .build())
        }
        try { startActivity(open) } catch (_: RuntimeException) { }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        publish()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        latestAudio = audioState
        publish()
    }


    /** The call the rider cares about: one that is ringing first, otherwise the first live one. */
    private fun publish() {
        val live = calls.filter { it.state != Call.STATE_DISCONNECTED }
        val main = live.firstOrNull { it.state == Call.STATE_RINGING } ?: live.firstOrNull()
        InCallHub.call = main
        if (main == null) {
            InCallHub.state.value = CallState(CallPhase.IDLE, managed = true)
            CallBridge.callerName.value = null
            return
        }
        val d = main.details
        val number = d.handle?.schemeSpecificPart
        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.contactDisplayName else lookupName(number)
        // Last resort before the bare number: the name the carrier sends with the call, when it sends one.
        CallBridge.callerName.value = listOf(saved, d.callerDisplayName, number).firstOrNull { !it.isNullOrBlank() }
        @Suppress("DEPRECATION") val audio = latestAudio ?: callAudioState
        InCallHub.state.value = CallState(
            phase = InCallHub.phaseOf(main.state),
            startedAtMs = d.connectTimeMillis,
            muted = audio?.isMuted == true,
            route = audio?.route ?: CallAudioState.ROUTE_EARPIECE,
            routes = audio?.supportedRouteMask ?: CallAudioState.ROUTE_EARPIECE,
            managed = true,
        )
    }

    private fun lookupName(number: String?): String? {
        if (number.isNullOrBlank() || !hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return try {
            contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: RuntimeException) {
            null
        }
    }

    private companion object {
        const val CHANNEL = "calls"
        const val NOTIFICATION_ID = 42
    }
}
