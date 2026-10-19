package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.BikeType
import dev.ygtlab.carplay.data.CrashDetector
import dev.ygtlab.carplay.data.FuelMath
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.RouteOptions
import dev.ygtlab.carplay.data.StopKind
import dev.ygtlab.carplay.data.VehicleProfile
import dev.ygtlab.carplay.data.WeatherMath
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {
    @Test
    fun eachVehicleRoutesWithItsOwnValhallaCosting() {
        assertEquals("motorcycle", VehicleProfile.MOTORCYCLE.config.costing)
        assertEquals("auto", VehicleProfile.CAR.config.costing)
        assertEquals("bicycle", VehicleProfile.BICYCLE.config.costing)
    }

    @Test
    fun onlyTheBicycleMayStandUpright() {
        assertFalse(VehicleProfile.MOTORCYCLE.config.allowsPortrait)
        assertFalse(VehicleProfile.CAR.config.allowsPortrait)
        assertTrue(VehicleProfile.BICYCLE.config.allowsPortrait)
    }

    @Test
    fun savedNamesComeBackAndUnknownOnesAreNull() {
        VehicleProfile.entries.forEach { assertEquals(it, VehicleProfile.fromName(it.name)) }
        assertNull(VehicleProfile.fromName(null))
        assertNull(VehicleProfile.fromName("TRUCK"))
    }

    @Test
    fun theCarLooksForFuelAndChargingTheBicycleForRepairShopsAndWater() {
        assertEquals(listOf(StopKind.FUEL), VehicleProfile.MOTORCYCLE.config.stops.map { it.kind })
        assertEquals(listOf(StopKind.FUEL, StopKind.CHARGE), VehicleProfile.CAR.config.stops.map { it.kind })
        assertEquals(
            listOf(StopKind.BIKE_REPAIR, StopKind.BIKE_SHOP, StopKind.WATER),
            VehicleProfile.BICYCLE.config.stops.map { it.kind },
        )
    }

    // ---- route options ---------------------------------------------------------------------

    @Test
    fun aCarAvoidsHighwaysUnderItsOwnCostingKey() {
        val o = RouteOptions(avoidHighways = true, avoidTolls = true, profile = VehicleProfile.CAR).costingOptions()!!
        assertFalse(o.has("motorcycle"))
        assertEquals(0, o.getJSONObject("auto").getInt("use_highways"))
        assertEquals(0, o.getJSONObject("auto").getInt("use_tolls"))
    }

    @Test
    fun aCarWithNoPreferencesSendsNoOptions() {
        assertNull(RouteOptions(profile = VehicleProfile.CAR).costingOptions())
    }

    @Test
    fun aBicycleAlwaysNamesItsTypeAndIgnoresHighwayAndTollSwitches() {
        val plain = RouteOptions(profile = VehicleProfile.BICYCLE, avoidHighways = true, avoidTolls = true).costingOptions()!!
            .getJSONObject("bicycle")
        assertEquals("Hybrid", plain.getString("bicycle_type"))
        assertFalse(plain.has("use_highways"))
        assertFalse(plain.has("use_tolls"))
    }

    @Test
    fun hillsAndBadSurfacesAndTheBikeTypeReachValhalla() {
        val o = RouteOptions(
            profile = VehicleProfile.BICYCLE, avoidHills = true, avoidBadSurfaces = true, bikeType = BikeType.ROAD,
        ).costingOptions()!!.getJSONObject("bicycle")
        assertEquals("Road", o.getString("bicycle_type"))
        assertEquals(0, o.getInt("use_hills"))
        assertEquals(1, o.getInt("avoid_bad_surfaces"))
        assertEquals("Mountain", RouteOptions(profile = VehicleProfile.BICYCLE, bikeType = BikeType.MOUNTAIN).costingOptions()!!
            .getJSONObject("bicycle").getString("bicycle_type"))
    }

    // ---- stops -----------------------------------------------------------------------------

    private val here = GeoPoint(41.0, 29.0)

    @Test
    fun theCarQueryAsksForFuelAndChargingTogether() {
        val q = FuelMath.nearQuery(here, VehicleProfile.CAR.config.stops)
        assertTrue(q.contains("\"amenity\"=\"fuel\""))
        assertTrue(q.contains("\"amenity\"=\"charging_station\""))
        assertFalse(q.contains("drinking_water"))
    }

    @Test
    fun theBicycleRouteQueryAsksForRepairShopAndWater() {
        val q = FuelMath.routeQuery(listOf(here, GeoPoint(41.1, 29.1)), VehicleProfile.BICYCLE.config.stops)
        assertTrue(q.contains("\"amenity\"=\"bicycle_repair_station\""))
        assertTrue(q.contains("\"shop\"=\"bicycle\""))
        assertTrue(q.contains("\"amenity\"=\"drinking_water\""))
        assertFalse(q.contains("\"fuel\""))
    }

    @Test
    fun theMotorcycleQueryIsStillFuelOnly() {
        val q = FuelMath.nearQuery(here)
        assertTrue(q.contains("\"amenity\"=\"fuel\""))
        assertFalse(q.contains("charging_station"))
    }

    @Test
    fun elementsAreLabelledByTheirTags() {
        val json = """{"elements":[
            {"type":"node","lat":41.001,"lon":29.0,"tags":{"amenity":"charging_station","operator":"ZES"}},
            {"type":"node","lat":41.002,"lon":29.0,"tags":{"amenity":"fuel","name":"Opet"}},
            {"type":"node","lat":41.003,"lon":29.0,"tags":{"amenity":"drinking_water"}},
            {"type":"node","lat":41.004,"lon":29.0,"tags":{"shop":"bicycle","name":"Pedal"}},
            {"type":"node","lat":41.005,"lon":29.0,"tags":{"amenity":"bicycle_repair_station"}}]}"""
        val kinds = FuelMath.parse(json, here).associate { (it.place.name.ifBlank { it.kind.name }) to it.kind }
        assertEquals(StopKind.CHARGE, kinds["ZES"])
        assertEquals(StopKind.FUEL, kinds["Opet"])
        assertEquals(StopKind.WATER, kinds["WATER"])
        assertEquals(StopKind.BIKE_SHOP, kinds["Pedal"])
        assertEquals(StopKind.BIKE_REPAIR, kinds["BIKE_REPAIR"])
    }

    @Test
    fun nominatimKeepsOnlyTheWantedTypesAndTagsThem() {
        val json = """[{"lat":"41.01","lon":"29.0","name":"Şarj","type":"charging_station"},
            {"lat":"41.02","lon":"29.0","name":"Hotel","type":"hotel"},
            {"lat":"41.03","lon":"29.0","name":"Opet","type":"fuel"}]"""
        val stops = FuelMath.parseNominatim(json, here, VehicleProfile.CAR.config.stops)
        assertEquals(listOf("Şarj", "Opet"), stops.map { it.place.name })
        assertEquals(listOf(StopKind.CHARGE, StopKind.FUEL), stops.map { it.kind })
        assertEquals(listOf("Opet"), FuelMath.parseNominatim(json, here).map { it.place.name })
    }

    // ---- crash thresholds --------------------------------------------------------------------

    /** Rides at [kmh] for two seconds, takes a hit of [g], then lies still. True if an alarm came. */
    private fun crashAlarm(detector: CrashDetector, kmh: Int, g: Float): Boolean {
        var t = 0L
        repeat(20) { detector.onSample(t, 1f, kmh); t += 100 }
        detector.onSample(t, g, kmh)
        t += 100
        repeat(130) {
            if (detector.onSample(t, 1f, 0)) return true
            t += 100
        }
        return false
    }

    @Test
    fun aBicycleFallAt15KmhRaisesTheAlarmButNotOnAMotorcycle() {
        val bike = VehicleProfile.BICYCLE.config
        val moto = VehicleProfile.MOTORCYCLE.config
        assertTrue(crashAlarm(CrashDetector({ bike.crashImpactG }, { bike.crashMovingKmh }), 15, 3.2f))
        assertFalse(crashAlarm(CrashDetector({ moto.crashImpactG }, { moto.crashMovingKmh }), 15, 3.2f))
    }

    @Test
    fun aCarIgnoresAHardBumpThatAMotorcycleWouldTreatAsAnImpact() {
        val car = VehicleProfile.CAR.config
        val moto = VehicleProfile.MOTORCYCLE.config
        assertFalse(crashAlarm(CrashDetector({ car.crashImpactG }, { car.crashMovingKmh }), 60, 4.5f))
        assertTrue(crashAlarm(CrashDetector({ moto.crashImpactG }, { moto.crashMovingKmh }), 60, 4.5f))
    }

    @Test
    fun theDefaultDetectorKeepsTheMotorcycleThresholds() {
        assertTrue(crashAlarm(CrashDetector(), 60, 4.5f))
        assertFalse(crashAlarm(CrashDetector(), 60, 3.5f))
    }

    // ---- wind ------------------------------------------------------------------------------

    @Test
    fun theForecastCarriesTheGustSpeed() {
        val offset = ZoneOffset.ofTotalSeconds(0)
        val now = LocalDateTime.parse("2026-10-18T11:50").toEpochSecond(offset) * 1000
        val json = """{"utc_offset_seconds":0,"current":{"temperature_2m":12.0,"weather_code":3,"wind_gusts_10m":41.6}}"""
        assertEquals(42, WeatherMath.parse(json, now)!!.windKmh)
        assertEquals(0, WeatherMath.parse("""{"current":{"temperature_2m":12.0}}""", now)!!.windKmh)
    }

    @Test
    fun onlyTheBicycleHasAWindThreshold() {
        assertNull(VehicleProfile.MOTORCYCLE.config.windAlertKmh)
        assertNull(VehicleProfile.CAR.config.windAlertKmh)
        assertTrue(VehicleProfile.BICYCLE.config.windAlertKmh!! > 0)
    }

    @Test
    fun aPlentifulKindDoesNotCrowdOutTheRareOnes() {
        val water = (1..20).map { dev.ygtlab.carplay.data.FuelStation(dev.ygtlab.carplay.data.Place("w$it", "", here), it, StopKind.WATER) }
        val repair = dev.ygtlab.carplay.data.FuelStation(dev.ygtlab.carplay.data.Place("fix", "", here), 500, StopKind.BIKE_REPAIR)
        val mixed = FuelMath.mix((water + repair).sortedBy { it.distanceM })
        assertEquals(9, mixed.size)
        assertTrue(mixed.any { it.kind == StopKind.BIKE_REPAIR })
        assertEquals(20, FuelMath.mix(water).size) // one kind only: untouched
    }
}
