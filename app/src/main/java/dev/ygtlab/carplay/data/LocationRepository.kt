package dev.ygtlab.carplay.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
    /** Horizontal accuracy radius in metres, when the fix has it. */
    val accuracyM: Int? = null,
)

class LocationRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state

    private var active = false

    // An object, not a lambda: before Android 11 the listener's other callbacks are abstract, and a lambda
    // would crash with AbstractMethodError the first time the GPS is switched off or on.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmh = if (location.hasSpeed()) SpeedUtil.msToKmh(location.speed) else 0
            _state.value = LocationState(
                speedKmh = kmh,
                fixAtMs = System.currentTimeMillis(),
                lat = location.latitude,
                lon = location.longitude,
                bearing = if (location.hasBearing() && kmh > 0) location.bearing else _state.value.bearing,
                accuracyM = if (location.hasAccuracy()) Math.round(location.accuracy) else null,
            )
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
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
