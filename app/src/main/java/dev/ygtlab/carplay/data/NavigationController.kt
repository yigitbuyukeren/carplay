package dev.ygtlab.carplay.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavUi(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val destination: Place? = null,
    val route: Route? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val navigating: Boolean = false,
    val rerouting: Boolean = false,
    val arrived: Boolean = false,
    val maneuverIndex: Int = 0,
    val distToNextM: Int = 0,
    val remainingM: Int = 0,
    val remainingS: Int = 0,
)

/**
 * Search -> route -> turn-by-turn guidance. Matches the GPS fix to the route polyline,
 * announces manoeuvres in Turkish and re-routes when the rider leaves the route.
 */
class NavigationController(
    context: Context,
    private val scope: CoroutineScope,
    private val location: StateFlow<LocationState>,
) {
    private val repo = RouteRepository()
    private val voice = Voice(context)

    private val _ui = MutableStateFlow(NavUi())
    val ui: StateFlow<NavUi> = _ui

    private var searchJob: Job? = null
    private var trackJob: Job? = null
    private var progressIndex = 0
    private var offRouteFixes = 0
    private val spoken = HashSet<Int>()

    private fun here(): GeoPoint? =
        location.value.takeIf { it.fixAtMs > 0 }?.let { GeoPoint(it.lat, it.lon) }

    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query, error = null) }
    }

    /** Runs on explicit submit only: Nominatim's usage policy forbids search-as-you-type. */
    fun search() {
        val query = _ui.value.query.trim()
        if (query.length < 2) return
        searchJob?.cancel()
        _ui.update { it.copy(searching = true, results = emptyList(), error = null) }
        searchJob = scope.launch {
            repo.search(query, here())
                .onSuccess { places ->
                    _ui.update {
                        it.copy(results = places, searching = false, error = if (places.isEmpty()) "Sonuç bulunamadı" else null)
                    }
                }
                .onFailure { _ui.update { s -> s.copy(searching = false, error = "Arama başarısız, internet bağlantısını kontrol et") } }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        val from = here()
        if (from == null) {
            _ui.update { it.copy(error = "GPS konumu bekleniyor") }
            return
        }
        _ui.update { it.copy(destination = place, query = place.name, results = emptyList(), loading = true, error = null) }
        scope.launch {
            repo.route(from, place.point)
                .onSuccess { route -> _ui.update { it.copy(route = route, loading = false) } }
                .onFailure { _ui.update { s -> s.copy(loading = false, destination = null, error = "Rota alınamadı") } }
        }
    }

    fun startNavigation() {
        val route = _ui.value.route ?: return
        progressIndex = 0
        offRouteFixes = 0
        spoken.clear()
        _ui.update {
            it.copy(navigating = true, arrived = false, maneuverIndex = 1.coerceAtMost(route.maneuvers.lastIndex),
                remainingM = route.distanceM.toInt(), remainingS = route.durationS.toInt())
        }
        route.maneuvers.firstOrNull()?.let { voice.speak(it.verbal) }
        trackJob?.cancel()
        trackJob = scope.launch { location.collect(::onFix) }
    }

    fun stopNavigation() {
        trackJob?.cancel()
        voice.stop()
        _ui.value = NavUi()
    }

    private fun onFix(fix: LocationState) {
        val state = _ui.value
        val route = state.route ?: return
        if (!state.navigating || fix.fixAtMs == 0L || route.shape.size < 2) return
        val p = GeoPoint(fix.lat, fix.lon)

        // Match to the nearest segment within a window ahead of the last known position.
        var bestIdx = progressIndex
        var best = Geo.Projection(Double.MAX_VALUE, 0.0)
        val end = minOf(route.shape.size - 2, progressIndex + MATCH_WINDOW)
        for (i in progressIndex..end) {
            val proj = Geo.project(p, route.shape[i], route.shape[i + 1])
            if (proj.distanceM < best.distanceM) {
                best = proj
                bestIdx = i
            }
        }

        if (best.distanceM > OFF_ROUTE_M) {
            offRouteFixes++
            if (offRouteFixes >= OFF_ROUTE_FIXES && !state.rerouting) reroute(p)
            return
        }
        offRouteFixes = 0
        progressIndex = bestIdx

        val travelled = route.cumulative[bestIdx] +
            best.fraction * (route.cumulative[bestIdx + 1] - route.cumulative[bestIdx])
        val remaining = (route.distanceM - travelled).coerceAtLeast(0.0)

        if (remaining <= ARRIVAL_M) {
            voice.speak("Hedefe ulaştınız")
            trackJob?.cancel()
            _ui.update { it.copy(navigating = false, arrived = true, remainingM = 0, remainingS = 0, distToNextM = 0) }
            return
        }

        val next = route.maneuvers.indexOfFirst { it.beginIndex > bestIdx }
            .takeIf { it >= 0 } ?: route.maneuvers.lastIndex
        val maneuver = route.maneuvers[next]
        val distToNext = (route.cumulative[maneuver.beginIndex.coerceAtMost(route.shape.lastIndex)] - travelled)
            .coerceAtLeast(0.0)

        if (distToNext < ALERT_M && maneuver.alert.isNotBlank() && spoken.add(next * 10)) {
            if (distToNext > PRE_M + 50) voice.speak(maneuver.alert)
        }
        if (distToNext < PRE_M && spoken.add(next * 10 + 1)) voice.speak(maneuver.verbal)

        _ui.update {
            it.copy(
                maneuverIndex = next,
                distToNextM = distToNext.toInt(),
                remainingM = remaining.toInt(),
                remainingS = (route.durationS * remaining / route.distanceM).toInt(),
            )
        }
    }

    private fun reroute(from: GeoPoint) {
        val dest = _ui.value.destination ?: return
        _ui.update { it.copy(rerouting = true) }
        voice.speak("Rota yeniden hesaplanıyor")
        scope.launch {
            repo.route(from, dest.point)
                .onSuccess { route ->
                    progressIndex = 0
                    offRouteFixes = 0
                    spoken.clear()
                    _ui.update { it.copy(route = route, rerouting = false) }
                }
                .onFailure {
                    offRouteFixes = 0
                    _ui.update { it.copy(rerouting = false) }
                }
        }
    }

    fun shutdown() {
        trackJob?.cancel()
        voice.shutdown()
    }

    private companion object {
        const val MATCH_WINDOW = 80
        const val OFF_ROUTE_M = 60.0
        const val OFF_ROUTE_FIXES = 4
        const val ARRIVAL_M = 25.0
        const val ALERT_M = 600.0
        const val PRE_M = 150.0
    }
}
