package dev.ygtlab.carplay.data

/** What a trouble code is about; the UI maps each to a translated description. */
enum class DtcArea {
    MAP_MAF_SENSOR, INTAKE_AIR_TEMP, ENGINE_TEMP_SENSOR, THROTTLE_SENSOR, OXYGEN_SENSOR, FUEL_TRIM,
    INJECTOR, MISFIRE, CRANK_CAM_SENSOR, KNOCK_SENSOR, VEHICLE_SPEED_SENSOR, IDLE_CONTROL, SYSTEM_VOLTAGE,
    FUEL_AIR, IGNITION, EMISSION, SPEED_IDLE, ECU, TRANSMISSION, MANUFACTURER, CHASSIS, BODY, NETWORK, UNKNOWN,
}

object DtcInfo {
    // Inclusive ranges of generic SAE codes, most specific first.
    private val specific = listOf(
        "P0100" to "P0109" to DtcArea.MAP_MAF_SENSOR,
        "P0110" to "P0114" to DtcArea.INTAKE_AIR_TEMP,
        "P0115" to "P0119" to DtcArea.ENGINE_TEMP_SENSOR,
        "P0120" to "P0124" to DtcArea.THROTTLE_SENSOR,
        "P0130" to "P0167" to DtcArea.OXYGEN_SENSOR,
        "P0170" to "P0175" to DtcArea.FUEL_TRIM,
        "P0200" to "P0212" to DtcArea.INJECTOR,
        "P0300" to "P0312" to DtcArea.MISFIRE,
        "P0325" to "P0334" to DtcArea.KNOCK_SENSOR,
        "P0335" to "P0349" to DtcArea.CRANK_CAM_SENSOR,
        "P0500" to "P0503" to DtcArea.VEHICLE_SPEED_SENSOR,
        "P0505" to "P0509" to DtcArea.IDLE_CONTROL,
        "P0560" to "P0563" to DtcArea.SYSTEM_VOLTAGE,
    )

    fun area(code: String): DtcArea {
        val c = code.uppercase()
        if (c.length != 5) return DtcArea.UNKNOWN
        specific.firstOrNull { (range, _) -> c >= range.first && c <= range.second }?.let { return it.second }
        return when (c[0]) {
            'C' -> DtcArea.CHASSIS
            'B' -> DtcArea.BODY
            'U' -> DtcArea.NETWORK
            'P' -> if (c[1] == '1' || c[1] == '3') DtcArea.MANUFACTURER else when (c[2]) {
                '1', '2' -> DtcArea.FUEL_AIR
                '3' -> DtcArea.IGNITION
                '4' -> DtcArea.EMISSION
                '5' -> DtcArea.SPEED_IDLE
                '6' -> DtcArea.ECU
                '7', '8', '9' -> DtcArea.TRANSMISSION
                else -> DtcArea.UNKNOWN
            }
            else -> DtcArea.UNKNOWN
        }
    }
}
