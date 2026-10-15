package dev.ygtlab.carplay.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import dev.ygtlab.carplay.util.SpeedUtil
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocationState(val speedKmh: Int = 0, val fixAtMs: Long = 0L)

class LocationRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state

    private var active = false

    private val listener = LocationListener { location ->
        val kmh = if (location.hasSpeed()) SpeedUtil.msToKmh(location.speed) else 0
        _state.value = LocationState(kmh, System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (active || !context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            active = true
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    fun stop() {
        if (!active) return
        manager.removeUpdates(listener)
        active = false
    }
}
