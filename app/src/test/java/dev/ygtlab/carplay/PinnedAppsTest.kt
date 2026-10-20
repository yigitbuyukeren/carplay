package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.PinnedApps
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedAppsTest {
    @Test
    fun jsonRoundTripKeepsOrder() {
        val list = listOf("a.one", "b.two", "c.three")
        assertEquals(list, PinnedApps.fromJson(PinnedApps.toJson(list)))
    }

    @Test
    fun badOrMissingJsonIsEmpty() {
        assertEquals(emptyList<String>(), PinnedApps.fromJson(null))
        assertEquals(emptyList<String>(), PinnedApps.fromJson("not json"))
    }

    @Test
    fun addIgnoresDuplicatesAndRemoveDrops() {
        val once = PinnedApps.add(listOf("a"), "b")
        assertEquals(listOf("a", "b"), once)
        assertEquals(once, PinnedApps.add(once, "b"))
        assertEquals(listOf("b"), PinnedApps.remove(once, "a"))
    }

    @Test
    fun moveStopsAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), PinnedApps.move(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), PinnedApps.move(list, "b", 1))
        assertEquals(list, PinnedApps.move(list, "a", -1))
        assertEquals(list, PinnedApps.move(list, "c", 1))
        assertEquals(list, PinnedApps.move(list, "zz", 1))
    }
}
