package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.MaintenanceMath
import dev.ygtlab.carplay.data.Odometer
import dev.ygtlab.carplay.data.OdometerMath
import dev.ygtlab.carplay.data.ServiceDue
import dev.ygtlab.carplay.data.ServiceItem
import dev.ygtlab.carplay.data.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerMathTest {
    private fun fix(t: Long, lat: Double, kmh: Int) = LocationState(kmh, t, lat, 29.0)

    @Test
    fun movingOneStepAddsItsLength() {
        // 0.0003 degrees of latitude is about 33 m.
        val d = OdometerMath.step(fix(1_000, 41.0, 60), fix(2_000, 41.0003, 60))
        assertEquals(33.4, d, 1.0)
    }

    @Test
    fun standingStillAddsNothingEvenWithJitter() {
        assertEquals(0.0, OdometerMath.step(fix(1_000, 41.0, 0), fix(2_000, 41.00002, 1)), 0.0)
    }

    @Test
    fun gpsGapsAndJumpsAreIgnored() {
        assertEquals(0.0, OdometerMath.step(fix(1_000, 41.0, 60), fix(60_000, 41.01, 60)), 0.0) // 59 s without a fix
        assertEquals(0.0, OdometerMath.step(fix(1_000, 41.0, 60), fix(2_000, 41.01, 60)), 0.0)  // 1.1 km in a second
    }

    @Test
    fun theFirstFixAndMissingFixesAddNothing() {
        assertEquals(0.0, OdometerMath.step(null, fix(1_000, 41.0, 60)), 0.0)
        assertEquals(0.0, OdometerMath.step(LocationState(), fix(1_000, 41.0, 60)), 0.0)
    }

    @Test
    fun theOdometerCountsAndSavesEveryHalfKilometre() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val location = MutableStateFlow(fix(1_000, 41.0, 60))
        val saved = mutableListOf<Double>()
        val odometer = Odometer(scope, location, initialM = 10_000.0) { saved += it }
        var t = 1_000L
        var lat = 41.0
        repeat(30) { t += 1_000; lat += 0.0003; location.value = fix(t, lat, 60) } // about 1 km
        assertEquals(11_000.0, odometer.meters.value, 60.0)
        assertTrue(saved.size >= 1)
        odometer.flush()
        assertEquals(odometer.meters.value, saved.last(), 0.0)
    }
}

class MaintenanceTest {
    private val oil = ServiceState(ServiceItem.OIL, 3000, 0.0)

    @Test
    fun remainingCountsDownFromTheLastService() {
        assertEquals(2000.0, MaintenanceMath.remainingKm(oil, 1_000_000.0), 1e-6)
        assertEquals(-500.0, MaintenanceMath.remainingKm(oil, 3_500_000.0), 1e-6)
        assertEquals(3000.0, MaintenanceMath.remainingKm(oil.copy(doneAtM = 5_000.0), 5_000.0), 1e-6)
    }

    @Test
    fun onlyOverdueOrAlmostDueItemsAreUrgentAndTheWorstComesFirst() {
        val list = listOf(
            oil,
            ServiceState(ServiceItem.CHAIN, 500, 0.0),
            ServiceState(ServiceItem.TYRES, 1500, 0.0),
        )
        assertNull(MaintenanceMath.mostUrgent(list, 100_000.0))                      // 100 km ridden: nothing near
        assertEquals(ServiceDue(ServiceItem.CHAIN, 80), MaintenanceMath.mostUrgent(list, 420_000.0)) // chain within 100 km
        assertEquals(ServiceDue(ServiceItem.CHAIN, -700), MaintenanceMath.mostUrgent(list, 1_200_000.0)) // most overdue wins
    }

    @Test
    fun markingDoneRestartsOnlyThatItem() {
        val list = MaintenanceMath.markDone(listOf(oil, ServiceState(ServiceItem.CHAIN, 500, 0.0)), ServiceItem.OIL, 4_000_000.0)
        assertEquals(4_000_000.0, list[0].doneAtM, 0.0)
        assertEquals(0.0, list[1].doneAtM, 0.0)
    }

    @Test
    fun intervalStepsAre100KmBelow3000And500KmFromThere() {
        var list = listOf(ServiceState(ServiceItem.CHAIN, 500, 0.0))
        list = MaintenanceMath.changeInterval(list, ServiceItem.CHAIN, +1)
        assertEquals(600, list.single().intervalKm)
        list = listOf(oil)
        assertEquals(3500, MaintenanceMath.changeInterval(list, ServiceItem.OIL, +1).single().intervalKm)
        assertEquals(2500, MaintenanceMath.changeInterval(list, ServiceItem.OIL, -1).single().intervalKm)
    }

    @Test
    fun intervalsStayWithinSaneLimits() {
        val tiny = listOf(ServiceState(ServiceItem.CHAIN, 100, 0.0))
        assertEquals(100, MaintenanceMath.changeInterval(tiny, ServiceItem.CHAIN, -1).single().intervalKm)
        val huge = listOf(ServiceState(ServiceItem.OIL, 30_000, 0.0))
        assertEquals(30_000, MaintenanceMath.changeInterval(huge, ServiceItem.OIL, +1).single().intervalKm)
    }

    @Test
    fun jsonRoundTripsAndFillsInMissingOrDamagedData() {
        val list = MaintenanceMath.defaults().map { if (it.item == ServiceItem.OIL) it.copy(intervalKm = 4000, doneAtM = 123.0) else it }
        assertEquals(list, MaintenanceMath.fromJson(MaintenanceMath.toJson(list)))
        assertEquals(MaintenanceMath.defaults(), MaintenanceMath.fromJson("{broken"))
        assertEquals(MaintenanceMath.defaults(), MaintenanceMath.fromJson(null))
        // A file written before an item existed still yields every item.
        val partial = """[{"item":"OIL","interval":3500,"doneAtM":10.0}]"""
        val loaded = MaintenanceMath.fromJson(partial)
        assertEquals(ServiceItem.entries.size, loaded.size)
        assertEquals(3500, loaded.first { it.item == ServiceItem.OIL }.intervalKm)
    }
}
