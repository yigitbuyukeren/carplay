package dev.ygtlab.carplay.data

import dev.ygtlab.carplay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class ServiceItem(val defaultKm: Int, val label: Int) {
    OIL(3000, R.string.svc_oil),
    CHAIN(500, R.string.svc_chain),
    AIR_FILTER(6000, R.string.svc_air_filter),
    BRAKES(8000, R.string.svc_brakes),
    TYRES(1500, R.string.svc_tyres),
}

/** [doneAtM] is the odometer reading, in metres, when the rider last pressed "done". */
data class ServiceState(val item: ServiceItem, val intervalKm: Int, val doneAtM: Double)

data class ServiceDue(val item: ServiceItem, val remainingKm: Int)

object OdometerMath {
    private const val MAX_GAP_MS = 30_000L
    private const val MAX_PLAUSIBLE_MPS = 70.0
    private const val MOVING_KMH = 2
    private const val NOISE_M = 3.0

    /** Metres to add for the step from [prev] to [cur]: only while moving, never across a GPS gap or jump. */
    fun step(prev: LocationState?, cur: LocationState): Double {
        if (prev == null || prev.fixAtMs == 0L || cur.fixAtMs == 0L) return 0.0
        val dt = cur.fixAtMs - prev.fixAtMs
        if (dt <= 0 || dt > MAX_GAP_MS || cur.speedKmh < MOVING_KMH) return 0.0
        val d = Geo.distance(GeoPoint(prev.lat, prev.lon), GeoPoint(cur.lat, cur.lon))
        if (d < NOISE_M || d / (dt / 1000.0) > MAX_PLAUSIBLE_MPS) return 0.0
        return d
    }
}

object MaintenanceMath {
    const val SOON_KM = 100
    const val MIN_INTERVAL_KM = 100
    const val MAX_INTERVAL_KM = 30_000

    fun defaults(): List<ServiceState> = ServiceItem.entries.map { ServiceState(it, it.defaultKm, 0.0) }

    /** Kilometres left until the item is due; negative once it is overdue. */
    fun remainingKm(s: ServiceState, odometerM: Double): Double = s.intervalKm - (odometerM - s.doneAtM) / 1000.0

    /** The most urgent item that is overdue or within [SOON_KM], or null when everything is fine. */
    fun mostUrgent(list: List<ServiceState>, odometerM: Double): ServiceDue? =
        list.map { ServiceDue(it.item, Math.floor(remainingKm(it, odometerM)).toInt()) }
            .filter { it.remainingKm <= SOON_KM }
            .minByOrNull { it.remainingKm }

    fun markDone(list: List<ServiceState>, item: ServiceItem, odometerM: Double): List<ServiceState> =
        list.map { if (it.item == item) it.copy(doneAtM = odometerM) else it }

    /** Interval steps are 100 km for short ones and 500 km from 3 000 km up. */
    fun stepKm(intervalKm: Int): Int = if (intervalKm >= 3000) 500 else 100

    fun changeInterval(list: List<ServiceState>, item: ServiceItem, deltaSteps: Int): List<ServiceState> =
        list.map {
            if (it.item != item) it
            else it.copy(intervalKm = (it.intervalKm + deltaSteps * stepKm(it.intervalKm)).coerceIn(MIN_INTERVAL_KM, MAX_INTERVAL_KM))
        }

    fun toJson(list: List<ServiceState>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("item", it.item.name).put("interval", it.intervalKm).put("doneAtM", it.doneAtM)) }
    }.toString()

    /** Missing or damaged data falls back to the defaults; items added later appear with their defaults. */
    fun fromJson(json: String?): List<ServiceState> {
        val saved = try {
            if (json.isNullOrBlank()) emptyList() else JSONArray(json).let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val item = runCatching { ServiceItem.valueOf(o.getString("item")) }.getOrNull() ?: return@mapNotNull null
                    ServiceState(item, o.optInt("interval", item.defaultKm).coerceIn(MIN_INTERVAL_KM, MAX_INTERVAL_KM), o.optDouble("doneAtM", 0.0))
                }
            }
        } catch (e: JSONException) {
            emptyList()
        }
        return defaults().map { d -> saved.firstOrNull { it.item == d.item } ?: d }
    }
}

/**
 * Distance travelled while the app was running, from the GPS fixes. Persisted every [SAVE_EVERY_M]
 * (and on [flush]) through [persist]. It is the app's own count, not the bike's real odometer.
 */
class Odometer(
    scope: CoroutineScope,
    location: StateFlow<LocationState>,
    initialM: Double,
    private val persist: (Double) -> Unit,
) {
    private val _meters = MutableStateFlow(initialM)
    val meters: StateFlow<Double> = _meters

    private var prev: LocationState? = null
    private var lastSaved = initialM

    init {
        scope.launch {
            location.collect { cur ->
                val add = OdometerMath.step(prev, cur)
                prev = cur
                if (add > 0) {
                    _meters.update { it + add }
                    if (_meters.value - lastSaved >= SAVE_EVERY_M) flush()
                }
            }
        }
    }

    fun flush() {
        lastSaved = _meters.value
        persist(lastSaved)
    }

    private companion object {
        const val SAVE_EVERY_M = 500.0
    }
}
