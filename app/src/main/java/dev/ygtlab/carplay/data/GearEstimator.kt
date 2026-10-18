package dev.ygtlab.carplay.data

import kotlin.math.abs

/**
 * Guesses the gear from speed / rpm, since bike ECUs rarely report it. Every gear has a fixed ratio
 * (km/h per 1000 rpm); steady samples are clustered and the clusters, sorted, are gears 1..n.
 * Clutch-in, neutral and wheelspin give ratios that never stay put long enough to form a cluster.
 */
class GearEstimator(initial: List<Float> = emptyList()) {
    private class Cluster(var ratio: Float, var hits: Int)

    private val clusters = initial.map { Cluster(it, MIN_HITS) }.toMutableList()
    private val recent = ArrayDeque<Float>()

    /** Learned gear ratios, lowest gear first; persisted so the next ride starts knowing them. */
    val ratios: List<Float> get() = confirmed().map { it.ratio }

    private fun confirmed() = clusters.filter { it.hits >= MIN_HITS }.sortedBy { it.ratio }

    /** Feeds one sample and returns the current gear (1-based) or null when unknown. */
    fun update(speedKmh: Int, rpm: Int): Int? {
        if (speedKmh < MIN_SPEED || rpm < MIN_RPM) {
            recent.clear()
            return null
        }
        val ratio = speedKmh * 1000f / rpm
        recent.addLast(ratio)
        if (recent.size > STEADY_SAMPLES) recent.removeFirst()
        val steady = recent.size == STEADY_SAMPLES && recent.all { abs(it - ratio) / ratio < STEADY_TOLERANCE }
        // Integer km/h is too coarse at low speed to learn from.
        if (steady && speedKmh >= LEARN_SPEED) learn(ratio)
        return gearFor(ratio)
    }

    fun gearFor(ratio: Float): Int? {
        val gears = confirmed()
        val index = gears.indexOfFirst { abs(it.ratio - ratio) / it.ratio < MATCH_TOLERANCE }
        return if (index >= 0) index + 1 else null
    }

    private fun learn(ratio: Float) {
        val near = clusters.minByOrNull { abs(it.ratio - ratio) }
        if (near != null && abs(near.ratio - ratio) / near.ratio < MATCH_TOLERANCE) {
            near.ratio += (ratio - near.ratio) * 0.05f
            near.hits++
        } else if (clusters.size < MAX_CLUSTERS) {
            clusters += Cluster(ratio, 1)
        } else {
            // Forget the weakest unconfirmed guess to make room.
            clusters.filter { it.hits < MIN_HITS }.minByOrNull { it.hits }?.let {
                it.ratio = ratio
                it.hits = 1
            }
        }
    }

    fun reset() {
        clusters.clear()
        recent.clear()
    }

    companion object {
        const val MIN_SPEED = 8
        const val LEARN_SPEED = 15
        const val MIN_RPM = 1500
        const val STEADY_SAMPLES = 3
        const val STEADY_TOLERANCE = 0.03f
        const val MATCH_TOLERANCE = 0.07f
        const val MIN_HITS = 12
        const val MAX_CLUSTERS = 12
    }
}
