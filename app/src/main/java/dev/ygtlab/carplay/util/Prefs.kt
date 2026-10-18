package dev.ygtlab.carplay.util

import android.content.Context
import dev.ygtlab.carplay.data.ThemeMode

object Prefs {
    private const val FILE = "motodash"
    private const val KEY_OVERLAY = "overlay_enabled"
    private const val KEY_READ_MESSAGES = "read_messages_aloud"
    private const val KEY_RECORD_TRIPS = "record_trips"
    private const val KEY_SPEED_LIMIT = "speed_limit"
    private const val KEY_LEAN = "lean_enabled"
    private const val KEY_LEAN_STATE = "lean_state"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_PLACES = "saved_places"
    private const val KEY_OBD = "obd_enabled"
    private const val KEY_OBD_DEVICE = "obd_device"
    private const val KEY_OBD_GEARS = "obd_gears"

    fun overlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_OVERLAY, false)

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_OVERLAY, enabled).apply()
    }

    fun readMessagesAloud(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_READ_MESSAGES, false)

    fun setReadMessagesAloud(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_READ_MESSAGES, enabled).apply()
    }

    fun recordTrips(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_RECORD_TRIPS, true)

    fun setRecordTrips(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_RECORD_TRIPS, enabled).apply()
    }

    fun speedLimit(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SPEED_LIMIT, true)

    fun setSpeedLimit(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPEED_LIMIT, enabled).apply()
    }

    fun leanEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LEAN, false)

    fun setLeanEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LEAN, enabled).apply()
    }

    fun leanState(context: Context): FloatArray? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LEAN_STATE, null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()?.takeIf { it.size == 8 }

    fun setLeanState(context: Context, state: FloatArray?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LEAN_STATE, state?.joinToString(","))
            .apply()
    }

    fun themeMode(context: Context): ThemeMode =
        runCatching {
            ThemeMode.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null).orEmpty())
        }.getOrDefault(ThemeMode.AUTO)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun obdEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_OBD, false)

    fun setObdEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_OBD, enabled).apply()
    }

    /** Bluetooth address of the paired adapter, or "fake" for the simulated one. */
    fun obdDevice(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_OBD_DEVICE, null)

    fun setObdDevice(context: Context, address: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_OBD_DEVICE, address).apply()
    }

    fun obdGearRatios(context: Context): List<Float> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_OBD_GEARS, null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }.orEmpty()

    fun setObdGearRatios(context: Context, ratios: List<Float>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_OBD_GEARS, ratios.joinToString(","))
            .apply()
    }

    fun savedPlaces(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PLACES, null)

    fun setSavedPlaces(context: Context, json: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_PLACES, json).apply()
    }
}
