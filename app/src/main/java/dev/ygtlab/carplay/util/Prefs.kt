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
    private const val KEY_AVOID_HIGHWAYS = "route_avoid_highways"
    private const val KEY_AVOID_TOLLS = "route_avoid_tolls"
    private const val KEY_SOS_CONTACTS = "sos_contacts"
    private const val KEY_SOS_CRASH = "sos_crash"
    private const val KEY_SOS_BUTTON = "sos_button"
    private const val KEY_THERMAL = "thermal_guard"
    private const val KEY_WEATHER = "weather_enabled"
    private const val KEY_RAIN_VOICE = "rain_voice"

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

    fun savedPlaces(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PLACES, null)

    fun setSavedPlaces(context: Context, json: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_PLACES, json).apply()
    }

    fun weatherEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WEATHER, true)

    fun setWeatherEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WEATHER, enabled).apply()
    }

    fun rainVoice(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_RAIN_VOICE, true)

    fun setRainVoice(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_RAIN_VOICE, enabled).apply()
    }

    fun dialerPrompted(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("dialer_prompted", false)

    fun setDialerPrompted(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("dialer_prompted", true).apply()
    }

    fun thermalGuard(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_THERMAL, true)

    fun setThermalGuard(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_THERMAL, enabled).apply()
    }

    fun sosContacts(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_SOS_CONTACTS, null)

    fun setSosContacts(context: Context, json: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_SOS_CONTACTS, json).apply()
    }

    fun sosCrash(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SOS_CRASH, false)

    fun setSosCrash(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SOS_CRASH, enabled).apply()
    }

    fun sosButton(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SOS_BUTTON, false)

    fun setSosButton(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SOS_BUTTON, enabled).apply()
    }

    fun avoidHighways(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AVOID_HIGHWAYS, false)

    fun avoidTolls(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AVOID_TOLLS, false)

    fun setRouteOptions(context: Context, highways: Boolean, tolls: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AVOID_HIGHWAYS, highways).putBoolean(KEY_AVOID_TOLLS, tolls).apply()
    }

}
