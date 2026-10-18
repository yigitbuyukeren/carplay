package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.LeanSensor
import android.Manifest
import dev.ygtlab.carplay.data.AndroidSmsSender
import dev.ygtlab.carplay.data.AndroidSosAlarm
import dev.ygtlab.carplay.data.CrashSensor
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.MaintenanceMath
import dev.ygtlab.carplay.data.Odometer
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.ServiceDue
import dev.ygtlab.carplay.data.ServiceItem
import dev.ygtlab.carplay.data.RouteOptions
import dev.ygtlab.carplay.data.SosContact
import dev.ygtlab.carplay.data.SosController
import dev.ygtlab.carplay.data.SosMath
import dev.ygtlab.carplay.data.SosReason
import dev.ygtlab.carplay.util.hasPermission
import dev.ygtlab.carplay.data.ThermalGuard
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
    val nav = NavigationController(
        app::getString, viewModelScope, location.state, Voice(app),
        initialOptions = RouteOptions(Prefs.avoidHighways(app), Prefs.avoidTolls(app)),
        onOptionsChanged = { Prefs.setRouteOptions(app, it.avoidHighways, it.avoidTolls) },
    )

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

    val odometer = Odometer(viewModelScope, location.state, Prefs.odometerM(app)) { Prefs.setOdometerM(app, it) }
    val service = MutableStateFlow(MaintenanceMath.fromJson(Prefs.service(app)))
    val serviceDue: StateFlow<ServiceDue?> =
        combine(service, odometer.meters) { list, meters -> MaintenanceMath.mostUrgent(list, meters) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun serviceDone(item: ServiceItem) = updateService(MaintenanceMath.markDone(service.value, item, odometer.meters.value))

    fun changeServiceInterval(item: ServiceItem, steps: Int) = updateService(MaintenanceMath.changeInterval(service.value, item, steps))

    private fun updateService(list: List<dev.ygtlab.carplay.data.ServiceState>) {
        service.value = list
        Prefs.setService(getApplication(), MaintenanceMath.toJson(list))
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
    val trips = TripRecorder(TripStore(app), viewModelScope, location.state, recordTrips) { stop ->
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(stop.t))
        savePlace(PlaceKind.PARKED, Place(app.getString(R.string.saved_parked), time, GeoPoint(stop.lat, stop.lon)))
    }

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

    val thermalEnabled = MutableStateFlow(Prefs.thermalGuard(app))
    val thermal = ThermalGuard(
        viewModelScope, battery.state,
        gps.map { it.speedKmh }.stateIn(viewModelScope, SharingStarted.Eagerly, 0),
        thermalEnabled,
    ) {
        if (call.state.value.phase == CallPhase.IDLE) announcer.speak(app.getString(R.string.thermal_voice))
    }

    fun setThermalEnabled(enabled: Boolean) {
        Prefs.setThermalGuard(getApplication(), enabled)
        thermalEnabled.value = enabled
    }

    val sosContacts = MutableStateFlow(SosMath.fromJson(Prefs.sosContacts(app)))
    val sosCrashEnabled = MutableStateFlow(Prefs.sosCrash(app))
    val sosButtonEnabled = MutableStateFlow(Prefs.sosButton(app))
    val sos = SosController(
        scope = viewModelScope,
        location = location.state,
        contacts = sosContacts,
        sms = AndroidSmsSender(app),
        alarm = AndroidSosAlarm(app),
        smsAllowed = { app.hasPermission(Manifest.permission.SEND_SMS) },
        speak = { reason, seconds ->
            announcer.speak(
                when (reason) {
                    SosReason.AUTO -> app.getString(R.string.sos_voice_auto, seconds)
                    SosReason.MANUAL -> app.getString(R.string.sos_voice_manual, seconds)
                    SosReason.TEST -> app.getString(R.string.sos_voice_test)
                }
            )
        },
        message = { reason, link, stale ->
            val head = app.getString(if (reason == SosReason.AUTO) R.string.sos_sms_auto else R.string.sos_sms_manual)
            val where = when {
                link == null -> app.getString(R.string.sos_loc_none)
                stale -> app.getString(R.string.sos_loc_stale, link)
                else -> app.getString(R.string.sos_loc, link)
            }
            "$head $where"
        },
    )
    private val crashSensor = CrashSensor(app, speedKmh = { gps.value.speedKmh }) { sos.trigger(SosReason.AUTO) }

    fun setSosCrashEnabled(enabled: Boolean) {
        Prefs.setSosCrash(getApplication(), enabled)
        sosCrashEnabled.value = enabled
        if (enabled) crashSensor.start() else crashSensor.stop()
    }

    fun setSosButtonEnabled(enabled: Boolean) {
        Prefs.setSosButton(getApplication(), enabled)
        sosButtonEnabled.value = enabled
    }

    fun addSosContact(contact: SosContact) = updateSosContacts(SosMath.addContact(sosContacts.value, contact))

    fun removeSosContact(contact: SosContact) = updateSosContacts(sosContacts.value - contact)

    private fun updateSosContacts(list: List<SosContact>) {
        sosContacts.value = list
        Prefs.setSosContacts(getApplication(), SosMath.toJson(list))
        // Without a contact there is nobody to alert, so the triggers switch themselves off.
        if (list.isEmpty()) {
            setSosCrashEnabled(false)
            setSosButtonEnabled(false)
        }
    }

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
        if (sosCrashEnabled.value) crashSensor.start()
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
        odometer.flush()
        crashSensor.stop()
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
