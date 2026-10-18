package dev.ygtlab.carplay.data

import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

    private fun query(around: String) = "[out:json][timeout:15];nwr[\"amenity\"=\"fuel\"]$around;out center 80;"

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
}

/**
 * Overpass `amenity=fuel`. The public instances come and go, so each request walks the list of
 * mirrors and takes the first one that answers.
 */
class FuelRepository : FuelFinder {
    private val mirrors = listOf(
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    override suspend fun near(here: GeoPoint): Result<List<FuelStation>> = run(FuelMath.nearQuery(here), here)

    override suspend fun along(route: List<GeoPoint>, here: GeoPoint): Result<List<FuelStation>> =
        run(FuelMath.routeQuery(route), here)

    private suspend fun run(query: String, here: GeoPoint): Result<List<FuelStation>> = withContext(Dispatchers.IO) {
        safely("fuel") {
            val body = "data=" + URLEncoder.encode(query, "UTF-8")
            var failure: Exception? = null
            for (url in mirrors) {
                try {
                    return@safely FuelMath.parse(Http.postForm(url, body, connectMs = 6_000, readMs = 15_000), here)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e
                }
            }
            throw failure ?: IllegalStateException("no Overpass mirror")
        }
    }
}
