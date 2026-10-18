package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.PlaceKind
import dev.ygtlab.carplay.data.SavedPlace
import dev.ygtlab.carplay.data.SavedPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesTest {
    private fun saved(kind: PlaceKind, name: String, lat: Double = 41.0, lon: Double = 29.0) =
        SavedPlace(kind, Place(name, "", GeoPoint(lat, lon)))

    @Test
    fun newHomeReplacesTheOldOne() {
        val list = SavedPlaces.put(listOf(saved(PlaceKind.HOME, "A")), saved(PlaceKind.HOME, "B", 40.0))
        assertEquals(listOf("B"), list.map { it.place.name })
    }

    @Test
    fun homeAndWorkComeBeforeFavorites() {
        var list = emptyList<SavedPlace>()
        list = SavedPlaces.put(list, saved(PlaceKind.FAVORITE, "Fav", 40.0))
        list = SavedPlaces.put(list, saved(PlaceKind.WORK, "Work", 40.1))
        list = SavedPlaces.put(list, saved(PlaceKind.HOME, "Home", 40.2))
        assertEquals(listOf("Home", "Work", "Fav"), list.map { it.place.name })
    }

    @Test
    fun sameSpotIsKeptOnceAsFavorite() {
        var list = SavedPlaces.put(emptyList(), saved(PlaceKind.FAVORITE, "Cafe", 41.0, 29.0))
        list = SavedPlaces.put(list, saved(PlaceKind.FAVORITE, "Cafe again", 41.0001, 29.0))
        assertEquals(listOf("Cafe again"), list.map { it.place.name })
    }

    @Test
    fun oldestFavoriteDropsWhenFull() {
        var list = emptyList<SavedPlace>()
        for (i in 0..SavedPlaces.MAX_FAVORITES) list = SavedPlaces.put(list, saved(PlaceKind.FAVORITE, "F$i", 30.0 + i))
        assertEquals(SavedPlaces.MAX_FAVORITES, list.size)
        assertTrue(list.none { it.place.name == "F0" })
        assertEquals("F${SavedPlaces.MAX_FAVORITES}", list.last().place.name)
    }

    @Test
    fun removeTakesOnlyThatEntry() {
        val a = saved(PlaceKind.HOME, "A")
        val b = saved(PlaceKind.WORK, "B", 40.0)
        assertEquals(listOf(b), SavedPlaces.remove(listOf(a, b), a))
    }

    @Test
    fun jsonRoundTrips() {
        val list = listOf(saved(PlaceKind.HOME, "Ev", 41.5, 29.5), saved(PlaceKind.FAVORITE, "Kafe", 40.5, 28.5))
        assertEquals(list, SavedPlaces.fromJson(SavedPlaces.toJson(list)))
    }

    @Test
    fun damagedJsonGivesAnEmptyList() {
        assertTrue(SavedPlaces.fromJson("{not json").isEmpty())
        assertTrue(SavedPlaces.fromJson(null).isEmpty())
    }
}
