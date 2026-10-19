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
