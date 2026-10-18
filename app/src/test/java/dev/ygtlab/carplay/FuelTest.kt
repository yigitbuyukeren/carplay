package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.FuelMath
import dev.ygtlab.carplay.data.Geo
import dev.ygtlab.carplay.data.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTest {
    private val here = GeoPoint(41.0, 29.0)

    @Test
    fun parsesNodesAndWayCentersNearestFirst() {
        val json = """{"elements":[
            {"type":"way","id":2,"center":{"lat":41.02,"lon":29.0},"tags":{"amenity":"fuel","name":"Shell"}},
            {"type":"node","id":1,"lat":41.005,"lon":29.0,"tags":{"amenity":"fuel","brand":"Opet","name":"Opet Merkez","operator":"Alpet"}}
        ]}"""
        val list = FuelMath.parse(json, here)
        assertEquals(listOf("Opet Merkez", "Shell"), list.map { it.place.name })
        assertEquals("Opet", list[0].place.detail)
        assertEquals("", list[1].place.detail)
        assertTrue(list[0].distanceM in 500..600)
    }

    @Test
    fun skipsHotelsTaggedAsFuel() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":41.0,"lon":29.0,"tags":{"amenity":"fuel","tourism":"hotel","name":"Hotel"}},
            {"type":"node","id":2,"lat":41.001,"lon":29.0,"tags":{"amenity":"fuel","name":"BP"}}
        ]}"""
        assertEquals(listOf("BP"), FuelMath.parse(json, here).map { it.place.name })
    }

    @Test
    fun unnamedStationFallsBackToBrandThenBlank() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":41.001,"lon":29.0,"tags":{"amenity":"fuel","brand":"Petrol Ofisi"}},
            {"type":"node","id":2,"lat":41.002,"lon":29.0,"tags":{"amenity":"fuel"}}
        ]}"""
        val list = FuelMath.parse(json, here)
        assertEquals("Petrol Ofisi", list[0].place.name)
        assertEquals("", list[1].place.name)
    }

    @Test
    fun emptyOrMissingElementsGiveNothing() {
        assertTrue(FuelMath.parse("""{"elements":[]}""", here).isEmpty())
        assertTrue(FuelMath.parse("""{}""", here).isEmpty())
    }

    @Test
    fun routeSamplingKeepsEndsAndStaysUnderTheCap() {
        // 100 km straight north in 100 m steps.
        val shape = (0..1000).map { GeoPoint(41.0 + it * 0.0009, 29.0) }
        val cumulative = DoubleArray(shape.size).also { c -> for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distance(shape[i - 1], shape[i]) }
        val points = FuelMath.samplePoints(shape, cumulative, 0)
        assertEquals(shape.first(), points.first())
        assertEquals(shape.last(), points.last())
        assertTrue(points.size <= FuelMath.MAX_ROUTE_POINTS + 1)
    }

    @Test
    fun shortRouteIsSampledFromTheCurrentPosition() {
        val shape = (0..100).map { GeoPoint(41.0 + it * 0.0009, 29.0) }
        val cumulative = DoubleArray(shape.size).also { c -> for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distance(shape[i - 1], shape[i]) }
        val points = FuelMath.samplePoints(shape, cumulative, 90)
        assertEquals(shape[90], points.first())
        assertEquals(shape.last(), points.last())
    }

    @Test
    fun queriesNameTheRadiusAndCoordinates() {
        assertTrue(FuelMath.nearQuery(here).contains("(around:5000,41.00000,29.00000)"))
        assertTrue(FuelMath.routeQuery(listOf(here, GeoPoint(41.1, 29.1))).contains("(around:500,41.00000,29.00000,41.10000,29.10000)"))
    }
}
