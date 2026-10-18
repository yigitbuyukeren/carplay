package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.LeanSensor
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.WeatherAlert
import dev.ygtlab.carplay.data.WeatherController
import dev.ygtlab.carplay.data.PlaceKind
import dev.ygtlab.carplay.data.SavedPlace
import dev.ygtlab.carplay.data.SavedPlaces
import dev.ygtlab.carplay.data.EngineAlert
import dev.ygtlab.carplay.data.ObdRepository
import dev.ygtlab.carplay.data.BluetoothScanner
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.ygtlab.carplay.data.SpeedLimitController
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
import kotlinx.coroutines.flow.Flow
import dev.ygtlab.carplay.data.NavUi
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
    private val announcer = Voice(app)

    val callerName: StateFlow<String?> = CallBridge.callerName

    val locationState: StateFlow<LocationState> = location.state
    val nav = NavigationController(app::getString, viewModelScope, location.state, Voice(app))

    val places = MutableStateFlow(SavedPlaces.fromJson(Prefs.savedPlaces(app)))

    fun savePlace(kind: PlaceKind, place: Place) = updatePlaces(SavedPlaces.put(places.value, SavedPlace(kind, place)))

    fun removePlace(item: SavedPlace) = updatePlaces(SavedPlaces.remove(places.value, item))

    private fun updatePlaces(list: List<SavedPlace>) {
        places.value = list
        Prefs.setSavedPlaces(getApplication(), SavedPlaces.toJson(list))
    }

    val weatherEnabled = MutableStateFlow(Prefs.weatherEnabled(app))
    val rainVoice = MutableStateFlow(Prefs.rainVoice(app))
    val weather = WeatherController(viewModelScope, location.state, weatherEnabled) { alert, snapshot ->
        if (!rainVoice.value || call.state.value.phase != CallPhase.IDLE) return@WeatherController
        announcer.speak(
            when (alert) {
                WeatherAlert.RAIN -> snapshot.rainInMin?.takeIf { it > 0 }
                    ?.let { app.getString(R.string.rain_voice_fmt, it) } ?: app.getString(R.string.rain_voice_now)
                WeatherAlert.ICE -> app.getString(R.string.ice_voice_fmt, snapshot.tempC)
            }
        )
    }

    fun setWeatherEnabled(enabled: Boolean) {
        Prefs.setWeatherEnabled(getApplication(), enabled)
        weatherEnabled.value = enabled
    }

    fun setRainVoice(enabled: Boolean) {
        Prefs.setRainVoice(getApplication(), enabled)
        rainVoice.value = enabled
    }

    val leanEnabled = MutableStateFlow(Prefs.leanEnabled(app))
    val lean = LeanSensor(app)

    fun setLeanEnabled(enabled: Boolean) {
        Prefs.setLeanEnabled(getApplication(), enabled)
        leanEnabled.value = enabled
        if (enabled) lean.start() else lean.stop()
    }

    val speedLimitEnabled = MutableStateFlow(Prefs.speedLimit(app))
    private val guiding: Flow<Boolean> = nav.ui.map { ui: NavUi -> ui.navigating }.distinctUntilChanged()
    private val limitActive: StateFlow<Boolean> =
        combine(speedLimitEnabled, guiding) { on: Boolean, navigating: Boolean -> on && navigating }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val speedLimit = SpeedLimitController(viewModelScope, location.state, limitActive) { limit ->
        if (call.state.value.phase == CallPhase.IDLE) {
            announcer.speak(app.getString(R.string.speed_limit_warning, limit))
        }
    }

    fun setSpeedLimitEnabled(enabled: Boolean) {
        Prefs.setSpeedLimit(getApplication(), enabled)
        speedLimitEnabled.value = enabled
    }

    val recordTrips = MutableStateFlow(Prefs.recordTrips(app))
    val trips = TripRecorder(TripStore(app), viewModelScope, location.state, recordTrips)

    fun setRecordTrips(enabled: Boolean) {
        Prefs.setRecordTrips(getApplication(), enabled)
        recordTrips.value = enabled
    }

    val obdEnabled = MutableStateFlow(Prefs.obdEnabled(app))
    val obdDevice = MutableStateFlow(Prefs.obdDevice(app))
    val obd: ObdRepository = ObdRepository(app, viewModelScope) { alert ->
        if (call.state.value.phase != CallPhase.IDLE) return@ObdRepository
        announcer.speak(
            when (alert) {
                EngineAlert.OVERHEAT -> app.getString(R.string.obd_alert_overheat, obd.state.value.data.coolantC ?: 0)
                EngineAlert.LOW_VOLTAGE -> app.getString(R.string.obd_alert_low_voltage)
                EngineAlert.HIGH_VOLTAGE -> app.getString(R.string.obd_alert_high_voltage)
                EngineAlert.MALFUNCTION -> app.getString(R.string.obd_alert_mil)
            }
        )
    }

    fun setObdEnabled(enabled: Boolean) {
        Prefs.setObdEnabled(getApplication(), enabled)
        obdEnabled.value = enabled
        if (enabled) obd.start() else obd.stop()
    }

    fun setObdDevice(address: String) {
        Prefs.setObdDevice(getApplication(), address)
        obdDevice.value = address
        obd.restart()
    }

    /** Pairing a new adapter from the settings card selects it and connects right away. */
    val bluetooth = BluetoothScanner(app, ::setObdDevice)

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
        weather.start()
        viewModelScope.launch { gps.collect { lean.moving = it.speedKmh >= LEAN_MIN_KMH } }
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
        if (leanEnabled.value) lean.start()
        call.start()
        obd.start()
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
        announcer.speak(if (sender.isEmpty()) body else ctx.getString(R.string.message_speak_fmt, sender, body))
    }

    fun onPause() {
        theme.stop()
        lean.stop()
    }

    override fun onCleared() {
        obd.stop()
        bluetooth.release()
        announcer.shutdown()
        lean.stop()
        theme.stop()
        nav.shutdown()
        location.stop()
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 5000L
        const val FRESH_MESSAGE_MS = 30_000L
        const val LEAN_MIN_KMH = 15
    }
}
