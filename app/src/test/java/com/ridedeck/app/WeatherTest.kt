package com.ridedeck.app

import com.ridedeck.app.data.WeatherMath
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val offset = ZoneOffset.ofTotalSeconds(10800)
    private val now = LocalDateTime.parse("2026-10-18T11:50").toEpochSecond(offset) * 1000

    private fun reply(temp: Double, code: Int, vararg mm: Double): String {
        val times = listOf("11:45", "12:00", "12:15", "12:30", "12:45", "13:00", "13:15", "13:30")
        val t = times.take(mm.size).joinToString(",") { "\"2026-10-18T$it\"" }
        return """{"utc_offset_seconds":10800,"current":{"temperature_2m":$temp,"weather_code":$code},
            "minutely_15":{"time":[$t],"precipitation":[${mm.joinToString(",")}]}}"""
    }

    @Test
    fun dryForecastHasNoRain() {
        val s = WeatherMath.parse(reply(14.7, 3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), now)!!
        assertEquals(15, s.tempC)
        assertEquals(3, s.code)
        assertNull(s.rainInMin)
    }

    @Test
    fun rainInTheNextHourIsFoundWithItsDelay() {
        // 12:15 slot is 25 minutes after 11:50.
        val s = WeatherMath.parse(reply(12.0, 61, 0.0, 0.0, 0.5, 0.6, 0.0, 0.0, 0.0, 0.0), now)!!
        assertEquals(25, s.rainInMin)
    }

    @Test
    fun rainBeyondTheHourIsIgnored() {
        // The 13:15 slot is 85 minutes away.
        val s = WeatherMath.parse(reply(12.0, 3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0), now)!!
        assertNull(s.rainInMin)
    }

    @Test
    fun alreadyRainingIsZeroMinutes() {
        val s = WeatherMath.parse(reply(12.0, 63, 0.8, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), now)!!
        assertEquals(0, s.rainInMin)
    }

    @Test
    fun scatteredDrizzleBelowTheThresholdDoesNotWarn() {
        val s = WeatherMath.parse(reply(12.0, 51, 0.0, 0.1, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0), now)!!
        assertNull(s.rainInMin)
    }

    @Test
    fun steadyDrizzleAddsUpToAWarning() {
        val s = WeatherMath.parse(reply(12.0, 51, 0.0, 0.1, 0.1, 0.1, 0.1, 0.0, 0.0, 0.0), now)!!
        assertNotNull(s.rainInMin)
    }

    @Test
    fun iceRiskStartsAtThreeDegrees() {
        assertTrue(WeatherMath.iceRisk(3))
        assertTrue(WeatherMath.iceRisk(-2))
        assertFalse(WeatherMath.iceRisk(4))
    }

    @Test
    fun replyWithoutCurrentBlockIsRejected() {
        assertNull(WeatherMath.parse("""{"error":true}""", now))
    }

    @Test
    fun urlRoundsTheCoordinates() {
        val url = WeatherMath.url(41.023456, 28.941234)
        assertTrue(url.contains("latitude=41.02&longitude=28.94"))
    }
}
