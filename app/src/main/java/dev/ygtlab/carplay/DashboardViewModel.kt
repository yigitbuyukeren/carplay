package dev.ygtlab.carplay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ygtlab.carplay.data.BatteryRepository
import dev.ygtlab.carplay.data.CallRepository
import dev.ygtlab.carplay.data.LocationRepository
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.NavigationController
import dev.ygtlab.carplay.data.MediaRepository
import dev.ygtlab.carplay.data.ThemeController
import dev.ygtlab.carplay.service.CallBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class GpsUi(val speedKmh: Int = 0, val hasFix: Boolean = false)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val location = LocationRepository(app)
    val battery = BatteryRepository(app)
    val media = MediaRepository(app)
    val call = CallRepository(app)
    val theme = ThemeController(app)

    val callerName: StateFlow<String?> = CallBridge.callerName

    val locationState: StateFlow<LocationState> = location.state
    val nav = NavigationController(app, viewModelScope, location.state)

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

    fun onResume() {
        location.start()
        battery.start()
        media.start()
        theme.start()
        call.start()
        resumeTick.value++
    }

    fun onPause() {
        theme.stop()
    }

    override fun onCleared() {
        theme.stop()
        nav.shutdown()
        location.stop()
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 5000L
    }
}
