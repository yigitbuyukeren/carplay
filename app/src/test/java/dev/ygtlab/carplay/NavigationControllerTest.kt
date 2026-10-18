package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.FuelFinder
import dev.ygtlab.carplay.data.FuelStation
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.Maneuver
import dev.ygtlab.carplay.data.NavigationController
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.Route
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
    override suspend fun route(from: GeoPoint, to: GeoPoint): Result<Route> {
        requests += from to to
        return Result.success(routes.removeFirstOrNull() ?: straightRoute(from))
    }
    override suspend fun search(query: String, near: GeoPoint?): Result<List<Place>> = Result.success(emptyList())
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
}
