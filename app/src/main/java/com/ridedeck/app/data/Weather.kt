package com.ridedeck.app.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the dashboard needs from one forecast: now, and whether rain is about to start. */
data class WeatherSnapshot(
    val tempC: Int,
    val code: Int,
    /** Minutes until rain starts within the next hour, 0 when it is already raining, null when dry. */
    val rainInMin: Int?,
    /** Strongest gust right now, km/h. */
    val windKmh: Int = 0,
) {
    val iceRisk: Boolean get() = WeatherMath.iceRisk(tempC)
}

/** A few hours of the hourly forecast boiled down for a spoken answer ("yarın hava nasıl"). */
data class WeatherOutlook(val minC: Int, val maxC: Int, val rainPercent: Int, val code: Int)

enum class WeatherAlert { RAIN, ICE, WIND }

object WeatherMath {
    private const val SLOT_MIN = 15
    private const val HORIZON_MIN = 60

    /** One slot at least this wet counts as rain; light drizzle below it is ignored. */
    private const val SLOT_MM = 0.2
    private const val HORIZON_MM = 0.4
    const val ICE_C = 3

    fun iceRisk(tempC: Int): Boolean = tempC <= ICE_C

    /**
     * Open-Meteo reply for `current=temperature_2m,weather_code` and `minutely_15=precipitation`
     * (local times; `utc_offset_seconds` converts them). Null when the reply lacks the current block.
     */
    fun parse(json: String, nowMs: Long): WeatherSnapshot? {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return null
        if (!current.has("temperature_2m")) return null
        val offset = ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
        val slots = root.optJSONObject("minutely_15")
        var rainIn: Int? = null
        if (slots != null) {
            val times = slots.getJSONArray("time")
            val mm = slots.getJSONArray("precipitation")
            val wet = ArrayList<Pair<Int, Double>>()
            for (i in 0 until minOf(times.length(), mm.length())) {
                val startMs = LocalDateTime.parse(times.getString(i)).toEpochSecond(offset) * 1000
                val minutesAway = ((startMs - nowMs) / 60_000).toInt()
                // Slots that already ended are history, slots past the horizon are too far to warn about.
                if (minutesAway + SLOT_MIN <= 0 || minutesAway > HORIZON_MIN) continue
                wet += minutesAway.coerceAtLeast(0) to mm.optDouble(i, 0.0)
            }
            val heavy = wet.firstOrNull { it.second >= SLOT_MM }
            val total = wet.sumOf { it.second }
            rainIn = when {
                heavy != null -> heavy.first
                total >= HORIZON_MM -> wet.first { it.second > 0.0 }.first
                else -> null
            }
        }
        return WeatherSnapshot(
            tempC = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.optInt("weather_code", 0),
            rainInMin = rainIn,
            windKmh = Math.round(current.optDouble("wind_gusts_10m", 0.0)).toInt(),
        )
    }

    fun outlookUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.2f".format(Locale.US, lat)}&longitude=${"%.2f".format(Locale.US, lon)}" +
            "&hourly=temperature_2m,precipitation_probability,weather_code&forecast_days=2&timezone=auto"

    /**
     * From an hourly reply (`time` as local "2026-10-24T14:00"), the hours [fromHour]..[toHour] of the day [dayOffset] days
     * after the first date in the reply: coldest and warmest temperature, the highest rain chance and the code at that hour.
     */
    fun parseOutlook(json: String, dayOffset: Int, fromHour: Int, toHour: Int): WeatherOutlook? {
        val hourly = JSONObject(json).optJSONObject("hourly") ?: return null
        val times = hourly.optJSONArray("time") ?: return null
        val temps = hourly.optJSONArray("temperature_2m") ?: return null
        val pops = hourly.optJSONArray("precipitation_probability")
        val codes = hourly.optJSONArray("weather_code")
        val dates = (0 until times.length()).map { times.getString(it).substringBefore('T') }.distinct()
        val day = dates.getOrNull(dayOffset) ?: return null
        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        var pop = -1
        var code = 0
        for (i in 0 until times.length()) {
            val t = times.getString(i)
            if (t.substringBefore('T') != day) continue
            val hour = t.substringAfter('T').substringBefore(':').toIntOrNull() ?: continue
            if (hour !in fromHour..toHour || temps.isNull(i)) continue
            val temp = temps.getDouble(i)
            if (temp < min) min = temp
            if (temp > max) max = temp
            val p = pops?.optInt(i, 0) ?: 0
            if (p > pop) { pop = p; code = codes?.optInt(i, 0) ?: 0 }
        }
        if (min == Double.MAX_VALUE) return null
        return WeatherOutlook(Math.round(min).toInt(), Math.round(max).toInt(), pop.coerceAtLeast(0), code)
    }

    /** WMO weather code to an emoji the dashboard can draw anywhere. */
    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun url(lat: Double, lon: Double): String =
        // Two decimals is roughly a kilometre: plenty for a forecast, and less precise than the real fix.
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.2f".format(Locale.US, lat)}&longitude=${"%.2f".format(Locale.US, lon)}" +
            "&current=temperature_2m,weather_code,wind_gusts_10m&minutely_15=precipitation&forecast_minutely_15=8&timezone=auto"
}

class WeatherRepository {
    suspend fun fetch(at: GeoPoint, nowMs: Long = System.currentTimeMillis()): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            safely("weather") {
                WeatherMath.parse(Http.get(WeatherMath.url(at.lat, at.lon), connectMs = 8_000, readMs = 10_000), nowMs)
                    ?: error("no current weather in reply")
            }
        }
}

/** One-off forecast for a later part of the day, asked for by voice. */
suspend fun WeatherRepository.outlook(at: GeoPoint, dayOffset: Int, fromHour: Int, toHour: Int): Result<WeatherOutlook> =
    withContext(Dispatchers.IO) {
        safely("weather-outlook") {
            WeatherMath.parseOutlook(Http.get(WeatherMath.outlookUrl(at.lat, at.lon), connectMs = 8_000, readMs = 10_000), dayOffset, fromHour, toHour)
                ?: error("no hourly forecast in reply")
        }
    }

/**
 * Keeps [snapshot] fresh while [enabled]: a new forecast every [REFRESH_MS] or after moving
 * [REFRESH_MOVE_M]. [onAlert] fires once per rain spell and once per cold spell, not on every refresh.
 */
class WeatherController(
    private val scope: CoroutineScope,
    private val location: StateFlow<LocationState>,
    private val enabled: StateFlow<Boolean>,
    private val repo: WeatherRepository = WeatherRepository(),
    /** Gust speed (km/h) from which the wind is worth a warning for this vehicle; null for none. */
    private val windAlertKmh: () -> Int? = { null },
    private val onAlert: (WeatherAlert, WeatherSnapshot) -> Unit,
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot

    private var lastFetchAt = 0L
    private var lastFetchPoint: GeoPoint? = null
    private var rainAnnounced = false
    private var iceAnnounced = false
    private var windAnnounced = false

    fun start() {
        scope.launch {
            while (true) {
                tick()
                // Until the first forecast arrives, look again quickly so the chip shows up right after the first fix.
                delay(if (_snapshot.value == null) FIRST_TICK_MS else TICK_MS)
            }
        }
    }

    private suspend fun tick() {
        val fix = location.value
        if (!enabled.value) {
            _snapshot.value = null
            lastFetchAt = 0L
            return
        }
        if (fix.fixAtMs == 0L) return
        val here = GeoPoint(fix.lat, fix.lon)
        val now = System.currentTimeMillis()
        val moved = lastFetchPoint?.let { Geo.distance(it, here) } ?: Double.MAX_VALUE
        if (now - lastFetchAt < REFRESH_MS && moved < REFRESH_MOVE_M) return
        repo.fetch(here, now).onSuccess { s ->
            lastFetchAt = now
            lastFetchPoint = here
            _snapshot.value = s
            announce(s)
        }
        // A failed fetch keeps the old snapshot and tries again on the next tick.
    }

    /** True when the gusts reach this vehicle's threshold. */
    fun windy(s: WeatherSnapshot): Boolean = windAlertKmh()?.let { s.windKmh >= it } == true

    private fun announce(s: WeatherSnapshot) {
        if (s.rainInMin == null) rainAnnounced = false
        else if (!rainAnnounced) { rainAnnounced = true; onAlert(WeatherAlert.RAIN, s) }
        if (!s.iceRisk) iceAnnounced = false
        else if (!iceAnnounced) { iceAnnounced = true; onAlert(WeatherAlert.ICE, s) }
        if (!windy(s)) windAnnounced = false
        else if (!windAnnounced) { windAnnounced = true; onAlert(WeatherAlert.WIND, s) }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val FIRST_TICK_MS = 5_000L
        const val REFRESH_MS = 15 * 60_000L
        const val REFRESH_MOVE_M = 10_000.0
    }
}
