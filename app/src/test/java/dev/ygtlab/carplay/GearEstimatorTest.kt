package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.GearEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearEstimatorTest {
    private val bike = listOf(4.8f, 7.6f, 10.2f, 12.4f, 14.3f)

    private fun GearEstimator.ride(gear: Int, samples: Int = 40) {
        repeat(samples) { i ->
            val rpm = 3000 + i * 50
            update((rpm / 1000f * bike[gear - 1]).toInt(), rpm)
        }
    }

    @Test
    fun unknownUntilLearned() {
        val g = GearEstimator()
        assertNull(g.update(30, 4000))
    }

    @Test
    fun learnsGearsInOrder() {
        val g = GearEstimator()
        // Learn out of order: the numbering comes from the ratio, not from the sequence.
        listOf(3, 1, 5, 2, 4).forEach { g.ride(it) }
        assertEquals(5, g.ratios.size)
        assertEquals(1, g.update((4000 * 4.8f / 1000).toInt(), 4000))
        assertEquals(4, g.update((6000 * 12.4f / 1000).toInt(), 6000))
    }

    @Test
    fun clutchSlipDoesNotCreateAGear() {
        val g = GearEstimator()
        g.ride(2)
        // Revving with the clutch in: ratio jumps around every sample.
        repeat(60) { i -> g.update(30, if (i % 2 == 0) 3000 else 6500) }
        assertEquals(1, g.ratios.size)
    }

    @Test
    fun idleAndLowSpeedAreUnknown() {
        val g = GearEstimator(bike)
        assertNull(g.update(0, 1400))
        assertNull(g.update(20, 1200))
        assertEquals(3, g.update((5000 * 10.2f / 1000).toInt(), 5000))
    }

    @Test
    fun resetForgets() {
        val g = GearEstimator(bike)
        g.reset()
        assertEquals(0, g.ratios.size)
    }
}
