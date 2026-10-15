package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.TripStore
import dev.ygtlab.carplay.data.TripRecorder
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ygtlab.carplay.data.BatteryRepository
import dev.ygtlab.carplay.data.CallPhase
import dev.ygtlab.carplay.data.CallRepository
import dev.ygtlab.carplay.data.LocationRepository
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.MediaRepository
import dev.ygtlab.carplay.data.NavigationController
import dev.ygtlab.carplay.data.ThemeController
import dev.ygtlab.carplay.data.Voice
import dev.ygtlab.carplay.service.CallBridge
import dev.ygtlab.carplay.service.IncomingMessage
import dev.ygtlab.carplay.service.MessageBridge
import dev.ygtlab.carplay.util.Prefs
import dev.ygtlab.carplay.util.simplifyMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GpsUi(val speedKmh: Int = 0, val hasFix: Boolean = false)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val location = LocationRepository(app)
    val battery = BatteryRepository(app)
    val media = MediaRepository(app)
    val call = CallRepository(app)
    val theme = ThemeController(app)

    val messages: StateFlow<IncomingMessage?> = MessageBridge.latest
    val readMessagesAloud = MutableStateFlow(Prefs.readMessagesAloud(app))
    private val messageVoice = Voice(app)

    val callerName: StateFlow<String?> = CallBridge.callerName

    val locationState: StateFlow<LocationState> = location.state
    val nav = NavigationController(app, viewModelScope, location.state)

    val recordTrips = MutableStateFlow(Prefs.recordTrips(app))
    val trips = TripRecorder(TripStore(app), viewModelScope, location.state, recordTrips)

    fun setRecordTrips(enabled: Boolean) {
        Prefs.setRecordTrips(getApplication(), enabled)
        recordTrips.value = enabled
    }

    /** Bumped on every onResume so permission-dependent UI re-checks its state. */
    val resumeTick = MutableStateFlow(0)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    val gps: StateFlow<GpsUi> = combine(location.state, ticker) { loc, now ->
        val fresh = now - loc.fixAtMs < FIX_TIMEOUT_MS
        GpsUi(if (fresh) loc.speedKmh else 0, fresh)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GpsUi())

    init {
        viewModelScope.launch {
            MessageBridge.latest.filterNotNull().collect { message ->
                val fresh = System.currentTimeMillis() - message.postedAtMs < FRESH_MESSAGE_MS
                if (fresh && readMessagesAloud.value && call.state.value.phase == CallPhase.IDLE) speakMessage(message)
            }
        }
    }

    fun onResume() {
        location.start()
        battery.start()
        media.start()
        theme.start()
        call.start()
        resumeTick.value++
    }

    fun setReadMessagesAloud(enabled: Boolean) {
        Prefs.setReadMessagesAloud(getApplication(), enabled)
        readMessagesAloud.value = enabled
    }

    fun speakMessage(message: IncomingMessage) {
        val ctx = getApplication<Application>()
        val body = simplifyMessage(message.text, ctx.getString(R.string.message_link))
        val sender = message.sender.trim()
        messageVoice.speak(if (sender.isEmpty()) body else ctx.getString(R.string.message_speak_fmt, sender, body))
    }

    fun onPause() {
        theme.stop()
    }

    override fun onCleared() {
        messageVoice.shutdown()
        theme.stop()
        nav.shutdown()
        location.stop()
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 5000L
        const val FRESH_MESSAGE_MS = 30_000L
    }
}
