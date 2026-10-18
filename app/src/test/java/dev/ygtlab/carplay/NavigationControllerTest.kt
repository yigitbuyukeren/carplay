package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.FuelFinder
import dev.ygtlab.carplay.data.FuelStation
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.Maneuver
import dev.ygtlab.carplay.data.NavigationController
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.Route
import dev.ygtlab.carplay.data.RouteOptions
import dev.ygtlab.carplay.data.Routing
import dev.ygtlab.carplay.data.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val STEP = 0.0009 // about 100 m of latitude

/** A straight road north: depart at 0, turn at point 15, arrive at point 20 (2 km). */
private fun straightRoute(from: GeoPoint = GeoPoint(41.0, 29.0), points: Int = 21, turnAt: Int = 15): Route {
    val shape = (0 until points).map { GeoPoint(from.lat + it * STEP, from.lon) }
    val maneuvers = listOf(
        Maneuver(1, "Depart", "", "Head north", 1500.0, 0),
        Maneuver(10, "Turn right", "Turn right ahead", "Turn right now", 500.0, turnAt),
        Maneuver(4, "Arrive", "", "You have arrived", 0.0, points - 1),
    )
    val length = (points - 1) * 100.0
    return Route(shape, maneuvers, length, length / 10)
}

private class FakeRouting(private val routes: ArrayDeque<Route>) : Routing {
    val requests = mutableListOf<Pair<GeoPoint, GeoPoint>>()
    val optionsSeen = mutableListOf<RouteOptions>()
    override suspend fun route(from: GeoPoint, to: GeoPoint, options: RouteOptions): Result<Route> {
        requests += from to to
        optionsSeen += options
        return Result.success(routes.removeFirstOrNull() ?: straightRoute(from))
    }
    override suspend fun search(query: String, near: GeoPoint?): Result<List<Place>> = Result.success(emptyList())
    var traceResult: Result<Route>? = null
    var tracedPoints = 0
    override suspend fun traceRoute(points: List<GeoPoint>, options: RouteOptions): Result<Route> {
        tracedPoints = points.size
        return traceResult ?: Result.success(straightRoute())
    }
    var address: Place? = null
    override suspend fun reverse(point: GeoPoint): Result<Place?> = Result.success(address)
}

private class RecordingSpeaker : Speaker {
    val said = mutableListOf<String>()
    override fun speak(text: String) { said += text }
    override fun stop() {}
    override fun shutdown() {}
}

private class FakeFuel : FuelFinder {
    var nearCalls = 0
    var alongCalls = 0
    var alongPoints = 0
    private val station = FuelStation(Place("Opet", "", GeoPoint(41.05, 29.0)), 300)
    override suspend fun near(here: GeoPoint): Result<List<FuelStation>> { nearCalls++; return Result.success(listOf(station)) }
    override suspend fun along(route: List<GeoPoint>, here: GeoPoint): Result<List<FuelStation>> {
        alongCalls++; alongPoints = route.size; return Result.success(listOf(station))
    }
}

class NavigationControllerTest {
    private val start = GeoPoint(41.0, 29.0)
    private val dest = Place("Dest", "", GeoPoint(41.018, 29.0))
    private val text = { id: Int -> "t$id" }

    private lateinit var scope: CoroutineScope
    private lateinit var location: MutableStateFlow<LocationState>
    private lateinit var speaker: RecordingSpeaker
    private lateinit var routing: FakeRouting
    private lateinit var fuel: FakeFuel
    private lateinit var nav: NavigationController
    private var clock = 1L

    private fun fixAt(p: GeoPoint) = LocationState(speedKmh = 50, fixAtMs = ++clock, lat = p.lat, lon = p.lon)

    private fun drive(index: Int) {
        location.value = fixAt(GeoPoint(start.lat + index * STEP, start.lon))
    }

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        location = MutableStateFlow(fixAt(start))
        speaker = RecordingSpeaker()
        routing = FakeRouting(ArrayDeque(listOf(straightRoute())))
        fuel = FakeFuel()
        nav = NavigationController(text, scope, location, speaker, routing, fuel)
    }

    @After
    fun tearDown() = nav.shutdown()

    private fun startGuidance() {
        nav.selectPlace(dest)
        nav.startNavigation()
    }

    @Test
    fun selectingAPlaceRoutesFromTheCurrentFix() {
        nav.selectPlace(dest)
        assertNotNull(nav.ui.value.route)
        assertEquals(start to dest.point, routing.requests.single())
        assertFalse(nav.ui.value.navigating)
    }

    @Test
    fun aPickedPointIsRenamedWhenItsAddressArrives() {
        routing.address = Place("Cadde 12", "Fatih, İstanbul", dest.point)
        nav.selectPoint(dest.point)
        assertEquals("Cadde 12", nav.ui.value.destination?.name)
        assertEquals("Cadde 12", nav.ui.value.query)
        assertNotNull(nav.ui.value.route)
    }

    @Test
    fun aPickedPointWithoutAnAddressKeepsTheGenericName() {
        nav.selectPoint(dest.point)
        assertEquals("t${R.string.selected_place}", nav.ui.value.destination?.name)
    }

    @Test
    fun changingTheOptionsInThePreviewRoutesAgainWithThem() {
        val saved = mutableListOf<RouteOptions>()
        nav = NavigationController(text, scope, location, speaker, routing, fuel, onOptionsChanged = { saved += it })
        nav.selectPlace(dest)
        assertEquals(RouteOptions(), routing.optionsSeen.last())

        nav.setOptions(RouteOptions(avoidHighways = true))
        assertEquals(2, routing.requests.size)
        assertEquals(RouteOptions(avoidHighways = true), routing.optionsSeen.last())
        assertEquals(listOf(RouteOptions(avoidHighways = true)), saved)
        assertFalse(nav.ui.value.loading)

        nav.setOptions(RouteOptions(avoidHighways = true)) // no change, no new request
        assertEquals(2, routing.requests.size)
    }

    @Test
    fun optionsChosenBeforeRoutingAreUsedForTheFirstRoute() {
        nav = NavigationController(text, scope, location, speaker, routing, fuel, initialOptions = RouteOptions(avoidTolls = true))
        nav.selectPlace(dest)
        assertEquals(RouteOptions(avoidTolls = true), routing.optionsSeen.single())
    }

    @Test
    fun optionsChangedWhileGuidingApplyToTheNextRerouteOnly() {
        startGuidance()
        nav.setOptions(RouteOptions(avoidTolls = true))
        assertEquals(1, routing.requests.size) // guidance is not interrupted
        val off = GeoPoint(start.lat + 5 * STEP, start.lon + 0.003)
        repeat(4) { location.value = fixAt(off) }
        assertEquals(RouteOptions(avoidTolls = true), routing.optionsSeen.last())
    }

    @Test
    fun withoutGpsFixSelectingShowsAnErrorAndDoesNotRoute() {
        location.value = LocationState()
        nav.selectPlace(dest)
        assertEquals("t${R.string.gps_waiting}", nav.ui.value.error)
        assertTrue(routing.requests.isEmpty())
    }

    @Test
    fun startingGuidanceSpeaksTheFirstInstruction() {
        startGuidance()
        assertTrue(nav.ui.value.navigating)
        assertEquals(listOf("Head north"), speaker.said)
    }

    @Test
    fun turnIsAnnouncedAt600AndAt150MetresOnlyOnce() {
        startGuidance()
        speaker.said.clear()
        // Turn at point 15 = 1500 m. Point 9 is 600 m away, point 14 is 100 m away.
        for (i in 1..14) drive(i)
        repeat(3) { drive(14) } // more fixes in the same spot must not repeat anything
        assertEquals(listOf("Turn right ahead", "Turn right now"), speaker.said)
    }

    @Test
    fun remainingDistanceAndNextManeuverFollowTheRider() {
        startGuidance()
        drive(10)
        val ui = nav.ui.value
        assertEquals(1, ui.maneuverIndex)
        assertEquals(1000.0, ui.remainingM.toDouble(), 30.0)
        assertEquals(500.0, ui.distToNextM.toDouble(), 30.0)
    }

    @Test
    fun fourFixesOffTheRouteTriggerOneReroute() {
        startGuidance()
        val off = GeoPoint(start.lat + 5 * STEP, start.lon + 0.003) // roughly 250 m east of the road
        repeat(3) { location.value = fixAt(off) }
        assertEquals(1, routing.requests.size)
        location.value = fixAt(off)
        assertEquals(2, routing.requests.size)
        assertEquals(off, routing.requests.last().first)
        assertTrue(speaker.said.contains("t${R.string.rerouting_voice}"))
        assertFalse(nav.ui.value.rerouting)
    }

    @Test
    fun aSingleStrayFixDoesNotReroute() {
        startGuidance()
        location.value = fixAt(GeoPoint(start.lat + 5 * STEP, start.lon + 0.003))
        drive(6)
        drive(7)
        drive(8)
        drive(9)
        assertEquals(1, routing.requests.size)
    }

    @Test
    fun reachingTheEndStopsGuidanceAndSaysArrived() {
        startGuidance()
        for (i in 1..20) drive(i)
        val ui = nav.ui.value
        assertTrue(ui.arrived)
        assertFalse(ui.navigating)
        assertEquals("t${R.string.arrived}", speaker.said.last())
    }

    @Test
    fun stoppingResetsEverything() {
        startGuidance()
        nav.stopNavigation()
        assertNull(nav.ui.value.route)
        assertNull(nav.ui.value.destination)
        assertFalse(nav.ui.value.navigating)
    }

    @Test
    fun fuelSearchUsesTheRoadAheadWhileGuidingAndTheSurroundingsOtherwise() {
        nav.findFuel()
        assertEquals(1, fuel.nearCalls)
        assertEquals(0, fuel.alongCalls)
        assertFalse(nav.fuel.value.alongRoute)

        startGuidance()
        nav.findFuel()
        assertEquals(1, fuel.alongCalls)
        assertTrue(fuel.alongPoints >= 2)
        assertTrue(nav.fuel.value.alongRoute)
        assertEquals("Opet", nav.fuel.value.stations.single().place.name)
    }

    @Test
    fun detourSetsTheStopAsDestinationAndRemembersTheFinalOne() {
        startGuidance()
        drive(3)
        val stop = Place("Opet", "", GeoPoint(start.lat + 0.005 + 3 * STEP, start.lon))
        nav.detourVia(stop)
        val ui = nav.ui.value
        assertEquals(stop, ui.destination)
        assertEquals(dest, ui.finalDestination)
        assertTrue(ui.navigating)
        assertEquals(stop.point, routing.requests.last().second)
        assertFalse(nav.fuel.value.visible)
    }

    @Test
    fun arrivingAtTheStopOffersToContinueAndContinuingRoutesToTheFinalDestination() {
        startGuidance()
        drive(3)
        val stop = Place("Opet", "", GeoPoint(start.lat + 3 * STEP + 5 * STEP, start.lon))
        nav.detourVia(stop) // the fake returns a 2 km straight road from the rider; shape ends at point 23
        location.value = fixAt(nav.ui.value.route!!.shape.last())
        assertTrue(nav.ui.value.arrived)
        assertEquals("t${R.string.stop_arrived}", speaker.said.last())

        nav.continueToFinal()
        val ui = nav.ui.value
        assertTrue(ui.navigating)
        assertFalse(ui.arrived)
        assertEquals(dest, ui.destination)
        assertNull(ui.finalDestination)
        assertEquals(dest.point, routing.requests.last().second)
    }

    // ---- GPX tracks ----------------------------------------------------------------------------

    private val gpxPoints = (0..20).map { GeoPoint(start.lat + it * STEP, start.lon) }

    @Test
    fun aGpxTrackIsPreviewedAsARouteWithoutAGpsFix() {
        location.value = LocationState()
        nav.followTrack("Sahil", gpxPoints)
        val ui = nav.ui.value
        assertEquals("Sahil", ui.track)
        assertEquals(gpxPoints.last(), ui.destination?.point)
        assertNotNull(ui.route)
        assertFalse(ui.loading)
        assertFalse(ui.navigating)
        assertTrue(routing.requests.isEmpty()) // traced, not routed
        assertEquals(gpxPoints.size.coerceAtMost(21), routing.tracedPoints)
    }

    @Test
    fun aTrackThatCannotBeMatchedShowsAnErrorAndClearsThePreview() {
        routing.traceResult = Result.failure(RuntimeException("no match"))
        nav.followTrack("Sahil", gpxPoints)
        assertEquals("t${R.string.gpx_failed}", nav.ui.value.error)
        assertNull(nav.ui.value.route)
        assertNull(nav.ui.value.track)
    }

    @Test
    fun startingNearTheBeginningFollowsTheTrackFromThere() {
        nav.followTrack("Sahil", gpxPoints)
        nav.startNavigation()
        assertTrue(nav.ui.value.navigating)
        assertEquals("Sahil", nav.ui.value.track)
        assertEquals(listOf("Head north"), speaker.said)
        assertTrue(routing.requests.isEmpty())
    }

    @Test
    fun startingInTheMiddleOfTheTrackJoinsItThere() {
        nav.followTrack("Sahil", gpxPoints)
        location.value = fixAt(GeoPoint(start.lat + 10 * STEP, start.lon + 0.0005)) // 10 points in, 40 m aside
        nav.startNavigation()
        val ui = nav.ui.value
        assertTrue(ui.navigating)
        assertEquals(1000.0, ui.remainingM.toDouble(), 80.0)
        assertTrue(routing.requests.isEmpty())
        assertFalse(speaker.said.contains("Head north")) // the start instruction is for the real start only
    }

    @Test
    fun startingFarAwayRidesToTheTrackFirstAndThenFollowsIt() {
        routing = FakeRouting(ArrayDeque()) // every route then starts where the rider is
        nav = NavigationController(text, scope, location, speaker, routing, fuel)
        nav.followTrack("Sahil", gpxPoints)
        val far = GeoPoint(start.lat - 0.05, start.lon) // about 5.5 km before the start
        location.value = fixAt(far)
        nav.startNavigation()
        // First leg: an ordinary route to the nearest point of the track.
        assertEquals(1, routing.requests.size)
        assertEquals(far, routing.requests.single().first)
        assertEquals(gpxPoints.first(), routing.requests.single().second)
        var ui = nav.ui.value
        assertTrue(ui.navigating)
        assertTrue(ui.joiningTrack)
        assertNull(ui.track)
        assertEquals("Sahil", ui.finalDestination?.name)

        // Arrive at the end of that approach route.
        location.value = fixAt(ui.route!!.shape.last())
        ui = nav.ui.value
        assertTrue(ui.arrived)
        assertEquals("t${R.string.gpx_joined}", speaker.said.last())

        // Continue: the stored track is followed, with no new routing request.
        nav.continueToFinal()
        ui = nav.ui.value
        assertTrue(ui.navigating)
        assertEquals("Sahil", ui.track)
        assertFalse(ui.joiningTrack)
        assertNull(ui.finalDestination)
        assertEquals(1, routing.requests.size)
    }

    @Test
    fun leavingTheTrackDoesNotRerouteAndRejoiningAShortcutWorks() {
        nav.followTrack("Sahil", gpxPoints)
        nav.startNavigation()
        val away = GeoPoint(start.lat + 3 * STEP, start.lon + 0.003) // about 250 m off the road
        repeat(4) { location.value = fixAt(away) }
        assertTrue(nav.ui.value.offTrack)
        assertEquals("t${R.string.gpx_off_track}", speaker.said.last())
        assertTrue(routing.requests.isEmpty()) // never re-routed away from the track
        repeat(3) { location.value = fixAt(away) }
        assertEquals(1, speaker.said.count { it == "t${R.string.gpx_off_track}" }) // said once

        // A shortcut: the rider reappears on the track much further along.
        location.value = fixAt(GeoPoint(start.lat + 15 * STEP, start.lon))
        val ui = nav.ui.value
        assertFalse(ui.offTrack)
        assertEquals(500.0, ui.remainingM.toDouble(), 60.0)
    }

    @Test
    fun aFuelStopOnATrackResumesTheTrackAfterwards() {
        routing = FakeRouting(ArrayDeque())
        nav = NavigationController(text, scope, location, speaker, routing, fuel)
        nav.followTrack("Sahil", gpxPoints)
        nav.startNavigation()
        drive(5)
        val stop = Place("Opet", "", GeoPoint(start.lat + 8 * STEP, start.lon))
        nav.detourVia(stop)
        var ui = nav.ui.value
        assertEquals(stop, ui.destination)
        assertEquals("Sahil", ui.finalDestination?.name)
        assertNull(ui.track)
        assertEquals(stop.point, routing.requests.last().second)

        location.value = fixAt(ui.route!!.shape.last())
        assertTrue(nav.ui.value.arrived)
        assertEquals("t${R.string.stop_arrived}", speaker.said.last())

        val requestsBefore = routing.requests.size
        nav.continueToFinal()
        ui = nav.ui.value
        assertEquals("Sahil", ui.track)
        assertTrue(ui.navigating)
        assertEquals(requestsBefore, routing.requests.size) // resumed from the stored track, no routing call
    }

    @Test
    fun pickingAPlaceAfterATrackLeavesTrackModeBehind() {
        nav.followTrack("Sahil", gpxPoints)
        nav.selectPlace(dest)
        assertNull(nav.ui.value.track)
        assertEquals(dest, nav.ui.value.destination)
    }
}
