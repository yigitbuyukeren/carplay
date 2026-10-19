package dev.ygtlab.carplay.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records rides automatically: a trip starts after a few consecutive fixes above [START_KMH] and
 * ends after [STOP_AFTER_MS] without movement. Trips too short to mean anything are discarded.
 * Points are written as they arrive, so a killed process loses at most the last fix; unfinished
 * trips are closed the next time the recorder starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorder(
    private val store: TripStore,
    scope: CoroutineScope,
    location: StateFlow<LocationState>,
    private val enabled: StateFlow<Boolean>,
    /** Called with the last moving point of every kept trip: that is where the bike was left. */
    private val onParked: (TripPoint) -> Unit = {},
) {
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    /** Bumped whenever the list of trips changes. */
    val version = MutableStateFlow(0)

    private var tripId: Long? = null
    private var startStreak = 0
    private var lastFixAt = 0L
    private var lastMovingAt = 0L

    init {
        scope.launch {
            withContext(io) { store.unfinishedIds().forEach { finish(it, store.lastPointTime(it)) } }
            version.value++
            location.collect { onFix(it) }
        }
    }

    private suspend fun onFix(fix: LocationState) {
        if (fix.fixAtMs == 0L || fix.fixAtMs == lastFixAt) return
        lastFixAt = fix.fixAtMs
        val point = TripPoint(fix.fixAtMs, fix.lat, fix.lon, fix.speedKmh)
        val active = tripId

        if (!enabled.value) {
            if (active != null) end(active, point.t)
            startStreak = 0
            return
        }
        if (active == null) {
            startStreak = if (fix.speedKmh >= START_KMH) startStreak + 1 else 0
            if (startStreak >= START_FIXES) {
                startStreak = 0
                val id = withContext(io) { store.startTrip(point.t).also { store.addPoint(it, point) } }
                tripId = id
                lastMovingAt = point.t
                _recording.value = true
            }
            return
        }
        withContext(io) { store.addPoint(active, point) }
        if (fix.speedKmh >= MOVING_KMH) lastMovingAt = point.t
        if (point.t - lastMovingAt > STOP_AFTER_MS) end(active, lastMovingAt)
    }

    private suspend fun end(id: Long, endedAt: Long) {
        tripId = null
        _recording.value = false
        val parked = withContext(io) { finish(id, endedAt) }
        version.value++
        parked?.let(onParked)
    }

    /** Runs on [io]. Returns the point where the bike stopped when the trip was kept, null when it was discarded. */
    private fun finish(id: Long, endedAt: Long): TripPoint? {
        val all = store.points(id)
        // The minutes spent standing still before the trip was closed are not part of the ride.
        val points = all.filter { it.t <= endedAt }.ifEmpty { all }
        val stats = TripMath.compute(points)
        val started = points.firstOrNull()?.t ?: endedAt
        if (stats.distanceM < MIN_DISTANCE_M || stats.movingMs < MIN_MOVING_MS) {
            store.delete(id)
            return null
        }
        store.finishTrip(id, maxOf(endedAt, started + 1), stats)
        return points.lastOrNull { it.t <= endedAt } ?: points.lastOrNull()
    }

    suspend fun delete(id: Long) {
        withContext(io) { store.delete(id) }
        version.value++
    }

    suspend fun deleteAll() {
        withContext(io) { store.deleteAll() }
        version.value++
    }

    suspend fun trips(): List<Trip> = withContext(io) { store.trips() }

    suspend fun points(id: Long): List<TripPoint> = withContext(io) { store.points(id) }

    private companion object {
        const val START_KMH = 8
        const val START_FIXES = 3
        const val MOVING_KMH = 2
        const val STOP_AFTER_MS = 3 * 60_000L
        const val MIN_DISTANCE_M = 300.0
        const val MIN_MOVING_MS = 60_000L
    }
}
