package com.ridedeck.app.util

import kotlin.math.roundToInt

object SpeedUtil {
    private const val STANDSTILL_KMH = 2f

    fun msToKmh(metersPerSecond: Float): Int {
        val kmh = metersPerSecond * 3.6f
        return if (kmh < STANDSTILL_KMH) 0 else kmh.roundToInt()
    }
}
