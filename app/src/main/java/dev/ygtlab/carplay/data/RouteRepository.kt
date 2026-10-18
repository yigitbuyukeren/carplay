package dev.ygtlab.carplay.data

import android.util.Log
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Place(val name: String, val detail: String, val point: GeoPoint)

data class Maneuver(
    val type: Int,
    val instruction: String,
    /** Spoken far in advance ("Sağa dönün"), may be empty. */
    val alert: String,
    /** Spoken close to the manoeuvre. */
    val verbal: String,
    val lengthM: Double,
    val beginIndex: Int,
)

data class Route(
    val shape: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val distanceM: Double,
    val durationS: Double,
) {
    /** cumulative[i] = metres from the start to shape[i]. */
    val cumulative: DoubleArray = DoubleArray(shape.size).also { c ->
        for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distance(shape[i - 1], shape[i])
    }
}

/** What the rider wants to avoid. Everything off leaves Valhalla's own motorcycle defaults untouched. */
data class RouteOptions(val avoidHighways: Boolean = false, val avoidTolls: Boolean = false) {
    /** Valhalla `costing_options`, or null when there is nothing to change. */
    fun costingOptions(): JSONObject? {
        if (!avoidHighways && !avoidTolls) return null
        val motorcycle = JSONObject()
        if (avoidHighways) motorcycle.put("use_highways", 0)
        if (avoidTolls) motorcycle.put("use_tolls", 0)
        return JSONObject().put("motorcycle", motorcycle)
    }
}

interface Routing {
    suspend fun route(from: GeoPoint, to: GeoPoint, options: RouteOptions): Result<Route>
    suspend fun search(query: String, near: GeoPoint?): Result<List<Place>>

    /** Snaps a recorded or planned track to the roads and returns it as a route with turn instructions. */
    suspend fun traceRoute(points: List<GeoPoint>, options: RouteOptions): Result<Route> =
        Result.failure(UnsupportedOperationException("trace_route"))

    /** The address of a spot picked on the map; null when there is nothing worth naming. */
    suspend fun reverse(point: GeoPoint): Result<Place?> = Result.success(null)
}

object PlaceNames {
    /** Nominatim `reverse` reply to a short name and a one-line detail, e.g. "Cadde 12" / "Fatih, İstanbul". */
    fun fromReverse(json: String, at: GeoPoint): Place? {
        val root = JSONObject(json)
        if (root.has("error")) return null
        val address = root.optJSONObject("address")
        val road = address?.optString("road").orEmpty()
        val number = address?.optString("house_number").orEmpty()
        val name = root.optString("name").ifBlank { listOf(road, number).filter { it.isNotBlank() }.joinToString(" ") }
        if (name.isBlank()) return null
        val detail = listOfNotNull(
            address?.optString("suburb")?.takeIf { it.isNotBlank() } ?: address?.optString("neighbourhood")?.takeIf { it.isNotBlank() },
            address?.optString("city")?.takeIf { it.isNotBlank() } ?: address?.optString("town")?.takeIf { it.isNotBlank() }
                ?: address?.optString("county")?.takeIf { it.isNotBlank() },
        ).filter { it != name }.joinToString(", ")
        return Place(name, detail, at)
    }
}

/** Valhalla (OSM) motorcycle routing and Nominatim geocoding, both public no-key services. */
class RouteRepository : Routing {
    override suspend fun route(from: GeoPoint, to: GeoPoint, options: RouteOptions): Result<Route> = withContext(Dispatchers.IO) {
        safely("route") {
            val body = JSONObject()
                .put("locations", JSONArray()
                    .put(JSONObject().put("lat", from.lat).put("lon", from.lon))
                    .put(JSONObject().put("lat", to.lat).put("lon", to.lon)))
                .put("costing", "motorcycle")
                .put("directions_options", JSONObject().put("language", valhallaLanguage()).put("units", "kilometers"))
            options.costingOptions()?.let { body.put("costing_options", it) }
            parseTrip(Http.postJson("https://valhalla1.openstreetmap.de/route", body.toString()))
        }
    }

    override suspend fun traceRoute(points: List<GeoPoint>, options: RouteOptions): Result<Route> = withContext(Dispatchers.IO) {
        safely("trace_route") {
            val shape = JSONArray().also { arr -> points.forEach { arr.put(JSONObject().put("lat", it.lat).put("lon", it.lon)) } }
            val body = JSONObject()
                .put("shape", shape)
                .put("costing", "motorcycle")
                .put("shape_match", "map_snap")
                .put("directions_options", JSONObject().put("language", valhallaLanguage()).put("units", "kilometers"))
                // Wide enough for tracks recorded every few kilometres, such as route-planner exports.
                .put("trace_options", JSONObject().put("search_radius", 50).put("gps_accuracy", 10).put("breakage_distance", 3000))
            options.costingOptions()?.let { body.put("costing_options", it) }
            parseTrip(Http.postJson("https://valhalla1.openstreetmap.de/trace_route", body.toString(), readMs = 40_000))
        }
    }

    /** Parses a Valhalla `route` or `trace_route` reply. */
    private fun parseTrip(reply: String): Route {
        val json = JSONObject(reply)
        val trip = json.getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val maneuvers = leg.getJSONArray("maneuvers").let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                Maneuver(
                    type = m.getInt("type"),
                    instruction = m.optString("instruction"),
                    alert = m.optString("verbal_transition_alert_instruction"),
                    verbal = m.optString("verbal_pre_transition_instruction", m.optString("instruction")),
                    lengthM = m.getDouble("length") * 1000,
                    beginIndex = m.getInt("begin_shape_index"),
                )
            }
        }
        val summary = trip.getJSONObject("summary")
        return Route(
            shape = Geo.decodePolyline(leg.getString("shape")),
            maneuvers = maneuvers,
            distanceM = summary.getDouble("length") * 1000,
            durationS = summary.getDouble("time"),
        )
    }

    override suspend fun reverse(point: GeoPoint): Result<Place?> = withContext(Dispatchers.IO) {
        safely("reverse") {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=${Locale.getDefault().language}" +
                "&lat=${"%.6f".format(Locale.US, point.lat)}&lon=${"%.6f".format(Locale.US, point.lon)}"
            PlaceNames.fromReverse(Http.get(url), point)
        }
    }

    override suspend fun search(query: String, near: GeoPoint?): Result<List<Place>> = withContext(Dispatchers.IO) {
        safely("search") {
            val view = near?.let {
                "&viewbox=${it.lon - 1.0},${it.lat + 1.0},${it.lon + 1.0},${it.lat - 1.0}"
            }.orEmpty()
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=${Locale.getDefault().language}" +
                "&q=${URLEncoder.encode(query, "UTF-8")}$view"
            val items = JSONArray(Http.get(url))
            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val full = item.optString("display_name")
                val name = item.optString("name").ifBlank { full.substringBefore(",").trim() }
                if (name.isBlank()) return@mapNotNull null
                val detail = full.split(",").map { it.trim() }.drop(1).take(3).joinToString(", ")
                Place(name, detail, GeoPoint(item.getDouble("lat"), item.getDouble("lon")))
            }
        }
    }
}

/** Valhalla only ships a fixed set of instruction languages; fall back to English. */
private fun valhallaLanguage(): String = when (Locale.getDefault().language) {
    "tr" -> "tr-TR"
    else -> "en-US"
}
