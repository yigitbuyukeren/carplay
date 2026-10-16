package dev.ygtlab.carplay.util

import dev.ygtlab.carplay.data.TripPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Gpx {
    fun write(name: String, points: List<TripPoint>): String {
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"MotoDash\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            sb.append("      <trkpt lat=\"").append("%.6f".format(Locale.US, p.lat))
                .append("\" lon=\"").append("%.6f".format(Locale.US, p.lon)).append("\">")
                .append("<time>").append(time.format(Date(p.t))).append("</time>")
                .append("<extensions><speed>").append("%.2f".format(Locale.US, p.kmh / 3.6)).append("</speed></extensions>")
                .append("</trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
