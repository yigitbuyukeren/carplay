package com.ridedeck.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ridedeck.app.util.Prefs
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { AUTO, DAY, NIGHT }

/**
 * Decides between the day and night palette. In AUTO the ambient light sensor drives it, smoothed
 * and with hysteresis so tunnels and bridges do not make the dashboard flicker; without a sensor
 * it falls back to the clock.
 */
class ThemeController(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _mode = MutableStateFlow(Prefs.themeMode(context))
    val mode: StateFlow<ThemeMode> = _mode

    private val _isDay = MutableStateFlow(resolve(_mode.value, clockSaysDay()))
    val isDay: StateFlow<Boolean> = _isDay

    private var autoDay = clockSaysDay()
    private var smoothedLux = -1f
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values[0]
            smoothedLux = if (smoothedLux < 0) lux else smoothedLux + SMOOTHING * (lux - smoothedLux)
            if (autoDay && smoothedLux < TO_NIGHT_LUX) autoDay = false
            else if (!autoDay && smoothedLux > TO_DAY_LUX) autoDay = true
            publish()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        autoDay = if (light == null) clockSaysDay() else autoDay
        if (!listening && light != null) {
            sensors.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
            listening = true
        }
        publish()
    }

    fun stop() {
        if (listening) sensors.unregisterListener(listener)
        listening = false
    }

    fun setMode(mode: ThemeMode) {
        Prefs.setThemeMode(context, mode)
        _mode.value = mode
        publish()
    }

    private fun publish() {
        _isDay.value = resolve(_mode.value, autoDay)
    }

    private fun resolve(mode: ThemeMode, auto: Boolean) = when (mode) {
        ThemeMode.DAY -> true
        ThemeMode.NIGHT -> false
        ThemeMode.AUTO -> auto
    }

    private fun clockSaysDay(): Boolean = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18

    private companion object {
        const val SMOOTHING = 0.03f
        const val TO_DAY_LUX = 4000f
        const val TO_NIGHT_LUX = 1500f
    }
}
