package com.ridedeck.app

import com.ridedeck.app.data.GeoPoint
import com.ridedeck.app.data.PlaceNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNamesTest {
    private val at = GeoPoint(41.0, 29.0)

    @Test
    fun namedPlaceKeepsItsNameAndGetsAnAreaDetail() {
        val json = """{"name":"Opet","address":{"road":"Ana Cadde","suburb":"Fatih","city":"İstanbul"}}"""
        val place = PlaceNames.fromReverse(json, at)!!
        assertEquals("Opet", place.name)
        assertEquals("Fatih, İstanbul", place.detail)
        assertEquals(at, place.point)
    }

    @Test
    fun anUnnamedSpotUsesRoadAndHouseNumber() {
        val json = """{"name":"","address":{"road":"Atatürk Caddesi","house_number":"12","town":"Gebze"}}"""
        val place = PlaceNames.fromReverse(json, at)!!
        assertEquals("Atatürk Caddesi 12", place.name)
        assertEquals("Gebze", place.detail)
    }

    @Test
    fun nothingNameableGivesNull() {
        assertNull(PlaceNames.fromReverse("""{"error":"Unable to geocode"}""", at))
        assertNull(PlaceNames.fromReverse("""{"name":"","address":{"country":"Türkiye"}}""", at))
    }
}
