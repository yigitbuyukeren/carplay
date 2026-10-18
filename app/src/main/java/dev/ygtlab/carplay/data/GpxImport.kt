package dev.ygtlab.carplay.data

import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

data class GpxTrack(val name: String, val points: List<GeoPoint>)

object GpxImport {
    /** Far more than any real ride; stops a hostile file from eating the phone's memory. */
    private const val MAX_POINTS = 300_000

    /**
     * Reads a GPX file. Recorded tracks (`trkpt`) win over planned routes (`rtept`), which win over
     * loose waypoints (`wpt`). The name is the first `name` inside the track or route, else the
     * file's own metadata name. Returns null when there are fewer than two points.
     */
    fun parse(input: InputStream): GpxTrack? {
        val handler = Handler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // No DOCTYPE, so no external entities: a GPX from a stranger is still just text.
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
        }
        try {
            factory.newSAXParser().parse(input, handler)
        } catch (e: StopParsing) {
            // Enough points; keep what was read.
        } catch (e: SAXException) {
            return null
        }
        val points = handler.track.ifEmpty { handler.route }.ifEmpty { handler.waypoints }
        if (points.size < 2) return null
        val name = (if (handler.track.isNotEmpty()) handler.trackName else if (handler.route.isNotEmpty()) handler.routeName else "")
            .ifBlank { handler.metadataName }
        return GpxTrack(name.trim(), points)
    }

    private class StopParsing : SAXException("too many points")

    private class Handler : DefaultHandler() {
        val track = ArrayList<GeoPoint>()
        val route = ArrayList<GeoPoint>()
        val waypoints = ArrayList<GeoPoint>()
        var trackName = ""
        var routeName = ""
        var metadataName = ""

        private val path = ArrayList<String>()
        private val text = StringBuilder()
        private var total = 0

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName).orEmpty()
            path += tag
            text.setLength(0)
            val target = when (tag) {
                "trkpt" -> track
                "rtept" -> route
                "wpt" -> waypoints
                else -> return
            }
            val lat = attributes.getValue("lat")?.toDoubleOrNull() ?: return
            val lon = attributes.getValue("lon")?.toDoubleOrNull() ?: return
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
            if (++total > MAX_POINTS) throw StopParsing()
            target += GeoPoint(lat, lon)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (path.lastOrNull() == "name" && text.isNotBlank()) {
                val parent = path.getOrNull(path.size - 2)
                when (parent) {
                    "trk" -> if (trackName.isEmpty()) trackName = text.toString()
                    "rte" -> if (routeName.isEmpty()) routeName = text.toString()
                    "metadata" -> if (metadataName.isEmpty()) metadataName = text.toString()
                }
            }
            if (path.isNotEmpty()) path.removeAt(path.size - 1)
            text.setLength(0)
        }
    }
}

object GpxMath {
    const val MAX_POINTS = 900
    const val MIN_SPACING_M = 30.0

    /**
     * Thins a track for the map matcher: always keeps both ends, drops points closer than
     * [MIN_SPACING_M], and widens that spacing until at most [MAX_POINTS] remain.
     */
    fun thin(points: List<GeoPoint>, maxPoints: Int = MAX_POINTS): List<GeoPoint> {
        if (points.size <= 2) return points
        var spacing = MIN_SPACING_M
        while (true) {
            val kept = ArrayList<GeoPoint>()
            kept += points.first()
            for (i in 1 until points.lastIndex) {
                if (Geo.distance(kept.last(), points[i]) >= spacing) kept += points[i]
            }
            kept += points.last()
            if (kept.size <= maxPoints) return kept
            spacing *= 1.5
        }
    }
}
