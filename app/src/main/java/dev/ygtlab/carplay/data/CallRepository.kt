package dev.ygtlab.carplay.data

import android.Manifest
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class CallPhase { IDLE, RINGING, ACTIVE }

data class CallState(val phase: CallPhase = CallPhase.IDLE, val startedAtMs: Long = 0L)

class CallRepository(private val context: Context) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    private var started = false

    private fun handle(raw: Int) {
        _state.value = when (raw) {
            TelephonyManager.CALL_STATE_RINGING -> CallState(CallPhase.RINGING)
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val prev = _state.value
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
        if (!context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            telecom.acceptRingingCall()
        } catch (_: SecurityException) {
        }
    }

    @Suppress("DEPRECATION")
    fun hangUp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            telecom.endCall()
        } catch (_: SecurityException) {
        }
    }
}
