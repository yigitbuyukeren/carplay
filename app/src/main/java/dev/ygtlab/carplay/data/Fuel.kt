package dev.ygtlab.carplay.data

import android.util.Log
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FuelStation(val place: Place, val distanceM: Int)

interface FuelFinder {
    suspend fun near(here: GeoPoint): Result<List<FuelStation>>
    suspend fun along(route: List<GeoPoint>, here: GeoPoint): Result<List<FuelStation>>
}

object FuelMath {
    const val NEAR_RADIUS_M = 5000
    const val ROUTE_RADIUS_M = 500
    const val MAX_ROUTE_POINTS = 40
    private const val MIN_SPACING_M = 1500.0
    private const val LIMIT = 25

    private fun around(radiusM: Int, points: List<GeoPoint>): String =
        "(around:$radiusM," + points.joinToString(",") { "%.5f,%.5f".format(Locale.US, it.lat, it.lon) } + ")"

    // Nodes and ways only: relations make the public servers scan far more for no gain here.
    private fun query(around: String) =
        "[out:json][timeout:15];(node[\"amenity\"=\"fuel\"]$around;way[\"amenity\"=\"fuel\"]$around;);out center 80;"

    fun nearQuery(here: GeoPoint): String = query(around(NEAR_RADIUS_M, listOf(here)))

    fun routeQuery(points: List<GeoPoint>): String = query(around(ROUTE_RADIUS_M, points))

    /**
     * Thins the rest of a route down to a handful of points, no closer than [MIN_SPACING_M] and never
     * more than [MAX_ROUTE_POINTS], so one Overpass request covers the road ahead.
     */
    fun samplePoints(shape: List<GeoPoint>, cumulative: DoubleArray, fromIndex: Int): List<GeoPoint> {
        if (shape.isEmpty()) return emptyList()
        val start = fromIndex.coerceIn(0, shape.lastIndex)
        val total = cumulative[shape.lastIndex] - cumulative[start]
        val spacing = maxOf(MIN_SPACING_M, total / MAX_ROUTE_POINTS)
        val out = ArrayList<GeoPoint>()
        var nextAt = 0.0
        for (i in start..shape.lastIndex) {
            if (cumulative[i] - cumulative[start] >= nextAt) {
                out += shape[i]
                nextAt += spacing
            }
        }
        if (out.last() != shape.last()) out += shape.last()
        return out.take(MAX_ROUTE_POINTS + 1)
    }

    /** Stations nearest to [from] first. Skips mapping mistakes such as hotels tagged as fuel. */
    fun parse(json: String, from: GeoPoint): List<FuelStation> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val found = ArrayList<FuelStation>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            if (tags.has("tourism") || tags.optString("disused") == "yes") continue
            val point = when {
                e.has("lat") -> GeoPoint(e.getDouble("lat"), e.getDouble("lon"))
                e.optJSONObject("center") != null -> e.getJSONObject("center").let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
                else -> continue
            }
            val brand = tags.optString("brand").ifBlank { tags.optString("operator") }
            val name = tags.optString("name").ifBlank { brand }
            // Unnamed stations get a generic label from the UI layer (blank name).
            val detail = brand.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }.orEmpty()
            found += FuelStation(Place(name, detail, point), Geo.distance(from, point).toInt())
        }
        return found.sortedBy { it.distanceM }.distinctBy { it.place.point.lat to it.place.point.lon }.take(LIMIT)
    }

    /** South, west, north, east. */
    data class Box(val south: Double, val west: Double, val north: Double, val east: Double)

    fun boundingBox(points: List<GeoPoint>, marginDeg: Double): Box = Box(
        south = points.minOf { it.lat } - marginDeg,
        west = points.minOf { it.lon } - marginDeg,
        north = points.maxOf { it.lat } + marginDeg,
        east = points.maxOf { it.lon } + marginDeg,
    )

    /** Nominatim answers "fuel" inside a box in well under a second, which the Overpass mirrors often cannot. */
    fun nominatimUrl(box: Box): String =
        "https://nominatim.openstreetmap.org/search?format=jsonv2&q=fuel&bounded=1&limit=40" +
            "&viewbox=${"%.5f,%.5f,%.5f,%.5f".format(Locale.US, box.west, box.north, box.east, box.south)}"

    fun parseNominatim(json: String, from: GeoPoint): List<FuelStation> {
        val items = org.json.JSONArray(json)
        val found = ArrayList<FuelStation>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("type") != "fuel") continue
            val point = GeoPoint(item.getDouble("lat"), item.getDouble("lon"))
            found += FuelStation(Place(item.optString("name"), "", point), Geo.distance(from, point).toInt())
        }
        return found.sortedBy { it.distanceM }.take(LIMIT)
    }

    /** Keeps the stations within [maxM] of the line through [route]. */
    fun alongRoute(stations: List<FuelStation>, route: List<GeoPoint>, maxM: Double = ROUTE_RADIUS_M * 1.4): List<FuelStation> {
        if (route.size < 2) return stations.filter { s -> route.any { Geo.distance(it, s.place.point) <= maxM } }
        return stations.filter { s ->
            (0 until route.lastIndex).any { Geo.project(s.place.point, route[it], route[it + 1]).distanceM <= maxM }
        }
    }
}


/**
 * Fuel stations from two free sources. The volunteer-run Overpass servers take turns being down or slow,
 * so a search asks all of them at once and takes the first good answer; Nominatim is the quick fallback
 * (and the first choice around the rider). Searches are rare, explicit taps.
 */
class FuelRepository : FuelFinder {
    private val mirrors = listOf(
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** Around the rider: Nominatim first (fast), the Overpass mirrors when it finds nothing or fails. */
    override suspend fun near(here: GeoPoint): Result<List<FuelStation>> = withContext(Dispatchers.IO) {
        safely("fuel") {
            val viaNominatim = try {
                FuelMath.parseNominatim(Http.get(FuelMath.nominatimUrl(FuelMath.boundingBox(listOf(here), NEAR_BOX_DEG))), here)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MotoDash", "nominatim fuel failed", e)
                emptyList()
            }
            viaNominatim.ifEmpty { FuelMath.parse(firstAnswer(FuelMath.nearQuery(here)), here) }
        }
    }

    /** Along the road ahead: the exact Overpass corridor first, a Nominatim box filtered to the corridor otherwise. */
    override suspend fun along(route: List<GeoPoint>, here: GeoPoint): Result<List<FuelStation>> = withContext(Dispatchers.IO) {
        safely("fuel") {
            try {
                FuelMath.parse(firstAnswer(FuelMath.routeQuery(route)), here)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // About the next 20 km: a box any wider returns whatever Nominatim ranks first, not what is ahead.
                val ahead = route.take(FALLBACK_POINTS)
                val box = FuelMath.boundingBox(ahead, ROUTE_BOX_MARGIN_DEG)
                FuelMath.alongRoute(FuelMath.parseNominatim(Http.get(FuelMath.nominatimUrl(box)), here), ahead)
            }
        }
    }

    private suspend fun firstAnswer(body: String): String = firstAnswerOf(body, mirrors)

    private companion object {
        const val NEAR_BOX_DEG = 0.03
        const val ROUTE_BOX_MARGIN_DEG = 0.01
        const val FALLBACK_POINTS = 14
    }
}

/** Asks every server at once with the same Overpass query and returns the first good reply. */
private suspend fun firstAnswerOf(query: String, mirrors: List<String>): String = coroutineScope {
    val body = "data=" + URLEncoder.encode(query, "UTF-8")
    val results = Channel<Result<String>>(mirrors.size)
    val requests = mirrors.map { url ->
        launch(Dispatchers.IO) {
            val outcome = try {
                Result.success(Http.postForm(url, body, connectMs = 5_000, readMs = 10_000))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MotoDash", "overpass mirror failed: $url ${e.message}")
                Result.failure(e)
            }
            results.send(outcome)
        }
    }
    var lastFailure: Throwable? = null
    repeat(mirrors.size) {
        val outcome = results.receive()
        if (outcome.isSuccess) {
            requests.forEach { it.cancel() }
            return@coroutineScope outcome.getOrThrow()
        }
        lastFailure = outcome.exceptionOrNull()
    }
    throw lastFailure ?: IllegalStateException("no Overpass mirror")
}
