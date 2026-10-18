package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.Geo
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.GpxImport
import dev.ygtlab.carplay.data.GpxMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxImportTest {
    private fun parse(xml: String) = GpxImport.parse(xml.byteInputStream())

    @Test
    fun readsATrackWithItsName() {
        val t = parse("""<?xml version="1.0"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>File name</name></metadata>
              <trk><name>Sahil yolu</name><trkseg>
                <trkpt lat="41.0" lon="29.0"><ele>10</ele></trkpt>
                <trkpt lat="41.001" lon="29.002"/>
                <trkpt lat="41.002" lon="29.004"/>
              </trkseg></trk>
            </gpx>""")!!
        assertEquals("Sahil yolu", t.name)
        assertEquals(3, t.points.size)
        assertEquals(GeoPoint(41.001, 29.002), t.points[1])
    }

    @Test
    fun aPlannedRouteIsUsedWhenThereIsNoTrack() {
        val t = parse("""<gpx xmlns="http://www.topografix.com/GPX/1/1">
              <rte><name>Plan</name><rtept lat="40.0" lon="28.0"/><rtept lat="40.1" lon="28.1"/></rte>
            </gpx>""")!!
        assertEquals("Plan", t.name)
        assertEquals(2, t.points.size)
    }

    @Test
    fun trackBeatsRouteAndRouteBeatsWaypoints() {
        val t = parse("""<gpx xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="1" lon="1"><name>Cafe</name></wpt><wpt lat="2" lon="2"/>
              <rte><rtept lat="3" lon="3"/><rtept lat="4" lon="4"/></rte>
              <trk><trkseg><trkpt lat="5" lon="5"/><trkpt lat="6" lon="6"/></trkseg></trk>
            </gpx>""")!!
        assertEquals(GeoPoint(5.0, 5.0), t.points.first())
        val onlyWaypoints = parse("""<gpx><wpt lat="1" lon="1"/><wpt lat="2" lon="2"/></gpx>""")!!
        assertEquals(2, onlyWaypoints.points.size)
    }

    @Test
    fun aWaypointNameIsNotTheTrackName() {
        val t = parse("""<gpx xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Whole trip</name></metadata>
              <wpt lat="1" lon="1"><name>Cafe</name></wpt>
              <trk><trkseg><trkpt lat="5" lon="5"/><trkpt lat="6" lon="6"/></trkseg></trk>
            </gpx>""")!!
        assertEquals("Whole trip", t.name)
    }

    @Test
    fun filesWithoutANamespaceStillParse() {
        assertNotNull(parse("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="2" lon="2"/></trkseg></trk></gpx>"""))
    }

    @Test
    fun brokenOrTooShortFilesGiveNull() {
        assertNull(parse("<gpx><trk><trkseg><trkpt lat=\"1\""))
        assertNull(parse("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/></trkseg></trk></gpx>"""))
        assertNull(parse("""<gpx><trk><trkseg><trkpt lat="x" lon="1"/><trkpt lat="95" lon="1"/></trkseg></trk></gpx>"""))
        assertNull(parse("not xml at all"))
    }

    @Test
    fun aDoctypeIsRefusedSoNoEntityCanLeakAFile() {
        val xml = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx><trk><name>&x;</name><trkseg><trkpt lat="1" lon="1"/><trkpt lat="2" lon="2"/></trkseg></trk></gpx>"""
        val t = parse(xml)
        assertTrue(t == null || !t.name.contains("root:"))
    }

    @Test
    fun thinningKeepsBothEndsAndDropsClosePoints() {
        val dense = (0..1000).map { GeoPoint(41.0 + it * 0.00001, 29.0) } // 1.1 m apart, 1.1 km long
        val thin = GpxMath.thin(dense)
        assertEquals(dense.first(), thin.first())
        assertEquals(dense.last(), thin.last())
        assertTrue(thin.size < 60)
    }

    @Test
    fun thinningWidensTheSpacingForVeryLongTracks() {
        val long = (0..20_000).map { GeoPoint(41.0 + it * 0.0003, 29.0) } // 33 m apart, 660 km
        val thin = GpxMath.thin(long)
        assertTrue(thin.size <= GpxMath.MAX_POINTS)
        assertEquals(long.last(), thin.last())
        val gaps = thin.zipWithNext { a, b -> Geo.distance(a, b) }
        assertTrue(gaps.all { it > 30.0 })
    }

    @Test
    fun tinyTracksAreLeftAlone() {
        val two = listOf(GeoPoint(1.0, 1.0), GeoPoint(1.0, 1.0001))
        assertEquals(two, GpxMath.thin(two))
    }
}
