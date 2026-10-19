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

data class LocationState(
    val speedKmh: Int = 0,
    val fixAtMs: Long = 0L,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** Heading in degrees, or null while standing still / unknown. */
    val bearing: Float? = null,
    /** Height above sea level in metres and the horizontal accuracy radius, when the fix has them. */
    val altitudeM: Int? = null,
    val accuracyM: Int? = null,
)

class LocationRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state

    private var active = false

    private val listener = LocationListener { location ->
        val kmh = if (location.hasSpeed()) SpeedUtil.msToKmh(location.speed) else 0
        _state.value = LocationState(
            speedKmh = kmh,
            fixAtMs = System.currentTimeMillis(),
            lat = location.latitude,
            lon = location.longitude,
            bearing = if (location.hasBearing() && kmh > 0) location.bearing else _state.value.bearing,
            altitudeM = if (location.hasAltitude()) Math.round(location.altitude).toInt() else null,
            accuracyM = if (location.hasAccuracy()) Math.round(location.accuracy) else null,
        )
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
