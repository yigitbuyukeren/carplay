package dev.ygtlab.carplay.data

import dev.ygtlab.carplay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavUi(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val destination: Place? = null,
    /** Set while heading for a fuel stop: where the trip continues afterwards. */
    val finalDestination: Place? = null,
    val route: Route? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val navigating: Boolean = false,
    val rerouting: Boolean = false,
    /** Name of the GPX track being followed (null for ordinary routes). */
    val track: String? = null,
    /** Following a track but the rider has left it; guidance resumes when they are back on it. */
    val offTrack: Boolean = false,
    /** Riding to the point where the GPX track is joined; [finalDestination] is its end. */
    val joiningTrack: Boolean = false,
    val arrived: Boolean = false,
    val maneuverIndex: Int = 0,
    val distToNextM: Int = 0,
    val remainingM: Int = 0,
    val remainingS: Int = 0,
)

data class FuelUi(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val stations: List<FuelStation> = emptyList(),
    val error: String? = null,
    /** True when the list covers the road ahead rather than the surroundings. */
    val alongRoute: Boolean = false,
)

/**
 * Search -> route -> turn-by-turn guidance. Matches the GPS fix to the route polyline,
 * announces manoeuvres and re-routes when the rider leaves the route. A fuel stop is routed as its
 * own short trip; arriving there offers to continue to the original destination.
 *
 * Everything outside (routing service, voice, strings, fuel lookup) is injected so the logic is
 * unit tested without Android.
 */
class NavigationController(
    private val text: (Int) -> String,
    private val scope: CoroutineScope,
    private val location: StateFlow<LocationState>,
    private val voice: Speaker,
    private val repo: Routing = RouteRepository(),
    private val fuelFinder: FuelFinder = FuelRepository(),
    initialOptions: RouteOptions = RouteOptions(),
    private val onOptionsChanged: (RouteOptions) -> Unit = {},
) {
    private val _options = MutableStateFlow(initialOptions)
    val options: StateFlow<RouteOptions> = _options
    private var routeJob: Job? = null

    private val _ui = MutableStateFlow(NavUi())
    val ui: StateFlow<NavUi> = _ui

    private val _fuel = MutableStateFlow(FuelUi())
    val fuel: StateFlow<FuelUi> = _fuel

    private var searchJob: Job? = null
    private var fuelJob: Job? = null
    private var trackJob: Job? = null
    private var progressIndex = 0
    private var offRouteFixes = 0
    private val spoken = HashSet<Int>()

    /** The snapped GPX route while one is loaded or being followed. */
    private var trackRoute: Route? = null

    /** Where to pick the track up after the rider has reached the join point or a fuel stop. */
    private var pendingTrackIndex: Int? = null

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
                        it.copy(results = places, searching = false, error = if (places.isEmpty()) text(R.string.no_results) else null)
                    }
                }
                .onFailure { _ui.update { s -> s.copy(searching = false, error = text(R.string.search_failed)) } }
        }
    }

    /** A spot picked on the map: routed at once under a generic name, renamed when its address arrives. */
    fun selectPoint(point: GeoPoint) {
        selectPlace(Place(text(R.string.selected_place), "", point))
        scope.launch {
            repo.reverse(point).onSuccess { named ->
                if (named == null) return@onSuccess
                _ui.update { if (it.destination?.point == point) it.copy(destination = named, query = named.name) else it }
            }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        closeFuel()
        val from = here()
        if (from == null) {
            _ui.update { it.copy(error = text(R.string.gps_waiting)) }
            return
        }
        trackRoute = null
        pendingTrackIndex = null
        _ui.update {
            it.copy(destination = place, finalDestination = null, query = place.name, results = emptyList(), loading = true, error = null,
                track = null, joiningTrack = false, offTrack = false)
        }
        routeJob?.cancel()
        routeJob = scope.launch {
            repo.route(from, place.point, _options.value)
                .onSuccess { route -> _ui.update { it.copy(route = route, loading = false) } }
                .onFailure { _ui.update { s -> s.copy(loading = false, destination = null, error = text(R.string.route_failed)) } }
        }
    }

    /**
     * Avoid-highways / avoid-tolls. While a route is only being previewed it is recomputed at once;
     * during guidance the new choice applies to the next re-route.
     */
    fun setOptions(options: RouteOptions) {
        if (options == _options.value) return
        _options.value = options
        onOptionsChanged(options)
        val state = _ui.value
        val dest = state.destination
        val from = here()
        if (dest == null || from == null || state.route == null || state.navigating) return
        _ui.update { it.copy(loading = true, error = null) }
        routeJob?.cancel()
        routeJob = scope.launch {
            repo.route(from, dest.point, options)
                .onSuccess { route -> _ui.update { it.copy(route = route, loading = false) } }
                .onFailure { _ui.update { s -> s.copy(loading = false, error = text(R.string.route_failed)) } }
        }
    }

    fun startNavigation() {
        val state = _ui.value
        val route = state.route ?: return
        if (state.track == null) {
            begin(route, 0)
            return
        }
        // A GPX track: join it where the rider is, or ride to it first when it is far away.
        val from = here()
        if (from == null) {
            _ui.update { it.copy(error = text(R.string.gps_waiting)) }
            return
        }
        val (index, distance) = nearestSegment(route, from)
        if (distance <= TRACK_JOIN_M) begin(route, index) else approachTrack(route, index)
    }

    private fun begin(route: Route, startIndex: Int) {
        progressIndex = startIndex
        offRouteFixes = 0
        spoken.clear()
        val next = route.maneuvers.indexOfFirst { it.beginIndex > startIndex }.takeIf { it >= 0 } ?: route.maneuvers.lastIndex
        val remaining = (route.distanceM - route.cumulative[startIndex]).coerceAtLeast(0.0)
        _ui.update {
            it.copy(navigating = true, arrived = false, offTrack = false, maneuverIndex = next.coerceAtLeast(0),
                remainingM = remaining.toInt(), remainingS = (route.durationS * remaining / route.distanceM).toInt())
        }
        if (startIndex == 0) route.maneuvers.firstOrNull()?.let { voice.speak(it.verbal) }
        trackJob?.cancel()
        trackJob = scope.launch { location.collect(::onFix) }
    }

    // ---- GPX ---------------------------------------------------------------------------------

    /**
     * Previews a GPX track: it is snapped to the roads by the routing service and shown like any route.
     * No GPS fix is needed yet; that is only asked for when guidance starts.
     */
    fun followTrack(name: String, points: List<GeoPoint>) {
        searchJob?.cancel()
        trackJob?.cancel()
        closeFuel()
        trackRoute = null
        pendingTrackIndex = null
        val end = points.lastOrNull() ?: return
        _ui.value = NavUi(query = name, destination = Place(name, "GPX", end), loading = true, track = name)
        routeJob?.cancel()
        routeJob = scope.launch {
            repo.traceRoute(GpxMath.thin(points), _options.value)
                .onSuccess { route ->
                    trackRoute = route
                    _ui.update { it.copy(route = route, loading = false) }
                }
                .onFailure { _ui.value = NavUi(error = text(R.string.gpx_failed)) }
        }
    }

    /** Shown on the search panel; used when a file could not be read at all. */
    fun reportError(message: String) {
        _ui.update { it.copy(error = message) }
    }

    private fun approachTrack(route: Route, index: Int) {
        val from = here() ?: return
        val state = _ui.value
        val name = state.track.orEmpty()
        val joinPoint = route.shape[index]
        val join = Place(text(R.string.gpx_join_point), name, joinPoint)
        _ui.update { it.copy(loading = true, error = null) }
        routeJob?.cancel()
        routeJob = scope.launch {
            repo.route(from, joinPoint, _options.value)
                .onSuccess { approach ->
                    pendingTrackIndex = index
                    _ui.update {
                        it.copy(destination = join, finalDestination = Place(name, "GPX", route.shape.last()), route = approach,
                            track = null, joiningTrack = true, loading = false)
                    }
                    begin(approach, 0)
                }
                .onFailure { _ui.update { s -> s.copy(loading = false, error = text(R.string.route_failed)) } }
        }
    }

    private fun nearestSegment(route: Route, p: GeoPoint): Pair<Int, Double> {
        var bestIndex = 0
        var best = Double.MAX_VALUE
        for (i in 0 until route.shape.lastIndex) {
            val d = Geo.project(p, route.shape[i], route.shape[i + 1]).distanceM
            if (d < best) {
                best = d
                bestIndex = i
            }
        }
        return bestIndex to best
    }

    fun stopNavigation() {
        trackJob?.cancel()
        fuelJob?.cancel()
        trackRoute = null
        pendingTrackIndex = null
        voice.stop()
        _ui.value = NavUi()
        _fuel.value = FuelUi()
    }

    // ---- fuel -------------------------------------------------------------------------------

    /** Fuel stations along the road ahead while guiding, otherwise around the rider. */
    fun findFuel() {
        val from = here()
        if (from == null) {
            _fuel.value = FuelUi(visible = true, error = text(R.string.gps_waiting))
            return
        }
        val state = _ui.value
        val route = state.route.takeIf { state.navigating }
        fuelJob?.cancel()
        _fuel.value = FuelUi(visible = true, loading = true, alongRoute = route != null)
        fuelJob = scope.launch {
            val result = if (route != null) {
                fuelFinder.along(FuelMath.samplePoints(route.shape, route.cumulative, progressIndex), from)
            } else {
                fuelFinder.near(from)
            }
            result
                .onSuccess { list ->
                    _fuel.update {
                        it.copy(loading = false, stations = list, error = if (list.isEmpty()) text(R.string.fuel_none) else null)
                    }
                }
                .onFailure { _fuel.update { it.copy(loading = false, error = text(R.string.fuel_failed)) } }
        }
    }

    fun closeFuel() {
        fuelJob?.cancel()
        _fuel.value = FuelUi()
    }

    /** Turns the trip into "fuel stop first, then the original destination". */
    fun detourVia(stop: Place) {
        val state = _ui.value
        val from = here() ?: return
        val final = state.finalDestination ?: state.destination ?: return
        closeFuel()
        // On a GPX track the trip resumes along the track from the stop, not by routing to its end.
        val track = trackRoute.takeIf { state.track != null || pendingTrackIndex != null }
        if (track != null) pendingTrackIndex = nearestSegment(track, stop.point).first
        _ui.update { it.copy(rerouting = true) }
        scope.launch {
            repo.route(from, stop.point, _options.value)
                .onSuccess { route ->
                    progressIndex = 0
                    offRouteFixes = 0
                    spoken.clear()
                    _ui.update {
                        it.copy(destination = stop, finalDestination = final, route = route, rerouting = false,
                            track = null, joiningTrack = false, offTrack = false,
                            maneuverIndex = 1.coerceAtMost(route.maneuvers.lastIndex),
                            remainingM = route.distanceM.toInt(), remainingS = route.durationS.toInt())
                    }
                    route.maneuvers.firstOrNull()?.let { voice.speak(it.verbal) }
                }
                .onFailure { _ui.update { s -> s.copy(rerouting = false, error = text(R.string.route_failed)) } }
        }
    }

    /** At the fuel stop: route on to the original destination and keep guiding. */
    fun continueToFinal() {
        val track = trackRoute
        val resumeAt = pendingTrackIndex
        if (track != null && resumeAt != null) {
            pendingTrackIndex = null
            val name = _ui.value.finalDestination?.name.orEmpty()
            _ui.update {
                it.copy(route = track, destination = Place(name, "GPX", track.shape.last()), finalDestination = null,
                    track = name, joiningTrack = false, arrived = false, loading = false)
            }
            begin(track, resumeAt)
            return
        }
        val final = _ui.value.finalDestination ?: return
        val from = here() ?: return
        _ui.update { it.copy(loading = true, arrived = false) }
        scope.launch {
            repo.route(from, final.point, _options.value)
                .onSuccess { route ->
                    _ui.update { it.copy(destination = final, finalDestination = null, route = route, loading = false) }
                    startNavigation()
                }
                .onFailure { _ui.update { s -> s.copy(loading = false, arrived = true, error = text(R.string.route_failed)) } }
        }
    }

    // ---- guidance ---------------------------------------------------------------------------

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
            if (offRouteFixes >= OFF_ROUTE_FIXES && !state.rerouting) {
                if (state.track != null) leftTrack(route, p) else reroute(p)
            }
            return
        }
        offRouteFixes = 0
        progressIndex = bestIdx
        if (state.offTrack) _ui.update { it.copy(offTrack = false) }

        val travelled = route.cumulative[bestIdx] +
            best.fraction * (route.cumulative[bestIdx + 1] - route.cumulative[bestIdx])
        val remaining = (route.distanceM - travelled).coerceAtLeast(0.0)

        if (remaining <= ARRIVAL_M) {
            voice.speak(text(if (state.joiningTrack) R.string.gpx_joined else if (state.finalDestination != null) R.string.stop_arrived else R.string.arrived))
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

    /**
     * A GPX track is not re-routed (that would throw the track away). The rider is matched against the
     * whole track again, so a shortcut rejoins where it meets it; otherwise guidance simply waits.
     */
    private fun leftTrack(route: Route, p: GeoPoint) {
        val (index, distance) = nearestSegment(route, p)
        if (distance <= OFF_ROUTE_M) {
            progressIndex = index
            offRouteFixes = 0
            _ui.update { it.copy(offTrack = false) }
            return
        }
        if (!_ui.value.offTrack) {
            voice.speak(text(R.string.gpx_off_track))
            _ui.update { it.copy(offTrack = true) }
        }
        offRouteFixes = OFF_ROUTE_FIXES // keep looking on every fix
    }

    private fun reroute(from: GeoPoint) {
        val dest = _ui.value.destination ?: return
        _ui.update { it.copy(rerouting = true) }
        voice.speak(text(R.string.rerouting_voice))
        scope.launch {
            repo.route(from, dest.point, _options.value)
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
        fuelJob?.cancel()
        voice.shutdown()
    }

    companion object {
        const val MATCH_WINDOW = 80
        const val OFF_ROUTE_M = 60.0
        const val OFF_ROUTE_FIXES = 4
        const val ARRIVAL_M = 25.0
        const val ALERT_M = 600.0
        const val PRE_M = 150.0
        const val TRACK_JOIN_M = 300.0
    }
}
