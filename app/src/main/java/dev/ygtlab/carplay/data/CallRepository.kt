package dev.ygtlab.carplay.data

import android.Manifest
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import dev.ygtlab.carplay.util.hasPermission
import dev.ygtlab.carplay.service.InCallHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CallPhase { IDLE, RINGING, DIALING, ACTIVE }

/** [managed] is true when MotoDash is the default phone app and so can mute and switch to the speaker itself. */
data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val startedAtMs: Long = 0L,
    val muted: Boolean = false,
    /** Current [android.telecom.CallAudioState] route and the mask of routes the phone offers right now. */
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val routes: Int = CallAudioState.ROUTE_EARPIECE,
    val managed: Boolean = false,
)

class CallRepository(private val context: Context, private val scope: CoroutineScope) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    private val phoneState = MutableStateFlow(CallState())
    private val dialingUntil = MutableStateFlow(0L)

    /** The in-call service knows more (dialing, mute), so it wins whenever MotoDash is the default phone app. */
    val state: StateFlow<CallState> = combine(phoneState, InCallHub.state, dialingUntil) { phone, managed, until ->
        when {
            managed != null -> managed
            // Without the in-call service the phone only reports "off hook"; show "calling" for the first seconds.
            until > 0 && phone.phase != CallPhase.RINGING -> CallState(CallPhase.DIALING, phone.startedAtMs)
            else -> phone
        }
    }.stateIn(scope, SharingStarted.Eagerly, CallState())

    /** Called the moment a call is placed from MotoDash so its call screen is up before the system reports anything. */
    fun markDialing() {
        dialingUntil.value = System.currentTimeMillis()
        scope.launch {
            delay(DIALING_GRACE_MS)
            dialingUntil.value = 0
        }
    }

    private var started = false

    private fun handle(raw: Int) {
        // A call that ends (or fails) right after being placed must not leave the screen on "calling".
        if (raw == TelephonyManager.CALL_STATE_IDLE && phoneState.value.phase == CallPhase.ACTIVE) dialingUntil.value = 0
        phoneState.value = when (raw) {
            TelephonyManager.CALL_STATE_RINGING -> CallState(CallPhase.RINGING)
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val prev = phoneState.value
                CallState(CallPhase.ACTIVE, if (prev.phase == CallPhase.ACTIVE) prev.startedAtMs else System.currentTimeMillis())
            }
            else -> CallState()
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (started || !context.hasPermission(Manifest.permission.READ_PHONE_STATE)) return
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephony.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handle(state)
                },
            )
        } else {
            telephony.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = handle(state)
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun answer() {
        InCallHub.call?.let { it.answer(VideoProfile.STATE_AUDIO_ONLY); return }
        if (!context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            telecom.acceptRingingCall()
        } catch (_: SecurityException) {
        }
    }

    @Suppress("DEPRECATION")
    fun hangUp() {
        InCallHub.call?.let {
            if (it.state == Call.STATE_RINGING) it.reject(false, null) else it.disconnect()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            telecom.endCall()
        } catch (_: SecurityException) {
        }
    }

    fun toggleMute() {
        InCallHub.service?.setMuted(!state.value.muted)
    }

    /** Sends the call sound to one of the routes the phone offers: earpiece, speaker, headset or Bluetooth. */
    fun setRoute(route: Int) {
        InCallHub.service?.setAudioRoute(route)
    }

    private companion object {
        const val DIALING_GRACE_MS = 8000L
    }
}
