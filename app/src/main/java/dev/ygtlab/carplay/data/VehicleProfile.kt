package dev.ygtlab.carplay.data

/** What the phone is mounted on. Chosen at first launch, changeable in Settings > Setup. */
enum class VehicleProfile {
    MOTORCYCLE, CAR, BICYCLE;

    val config: ProfileConfig get() = ProfileConfig.of(this)

    companion object {
        fun fromName(name: String?): VehicleProfile? = entries.firstOrNull { it.name == name }
    }
}

/** What a rider, driver or cyclist might stop for. */
enum class StopKind { FUEL, CHARGE, BIKE_REPAIR, BIKE_SHOP, WATER }

/** One OSM tag filter, e.g. `amenity=fuel`, and the Nominatim search that finds the same kind of place. */
data class StopSource(val kind: StopKind, val key: String, val value: String, val nominatimQuery: String, val nominatimType: String)

/**
 * Everything that differs between the vehicles in one place, so the rest of the app asks the profile
 * instead of branching on it. Pure data: unit tested without Android.
 */
data class ProfileConfig(
    /** Valhalla `costing` name; also the key of `costing_options`. */
    val costing: String,
    /** Speed gauge full-scale, km/h. */
    val gaugeMaxKmh: Float,
    /** Accelerometer magnitude (g) that counts as a crash impact. */
    val crashImpactG: Float,
    /** Speed above which the vehicle counts as "moving" for crash detection, km/h. */
    val crashMovingKmh: Int,
    /** Posted-limit display and warning is on by default. */
    val speedLimitDefault: Boolean,
    /** The stop button (⛽ on the map) looks for these. */
    val stops: List<StopSource>,
    /** No charger on board: the map drops to low frame rate when the battery runs low. */
    val assumesNoCharger: Boolean,
    /** The phone may sit upright on the handlebar. */
    val allowsPortrait: Boolean,
    /** Wind gusts from this speed (km/h) raise a banner; null for no wind alert. */
    val windAlertKmh: Int?,
) {
    companion object {
        private val FUEL = StopSource(StopKind.FUEL, "amenity", "fuel", "fuel", "fuel")
        private val CHARGE = StopSource(StopKind.CHARGE, "amenity", "charging_station", "charging station", "charging_station")
        private val REPAIR = StopSource(StopKind.BIKE_REPAIR, "amenity", "bicycle_repair_station", "bicycle repair station", "bicycle_repair_station")
        private val SHOP = StopSource(StopKind.BIKE_SHOP, "shop", "bicycle", "bicycle", "bicycle")
        private val WATER = StopSource(StopKind.WATER, "amenity", "drinking_water", "drinking water", "drinking_water")

        private val MOTORCYCLE = ProfileConfig(
            costing = "motorcycle", gaugeMaxKmh = 200f, crashImpactG = 4.0f, crashMovingKmh = 25,
            speedLimitDefault = true, stops = listOf(FUEL), assumesNoCharger = false, allowsPortrait = false, windAlertKmh = null,
        )

        // A real car crash is far harder than anything on the road, and the phone sits in a mount on a
        // solid body, so the threshold is higher than a bike's; it also counts as moving a bit faster.
        private val CAR = ProfileConfig(
            costing = "auto", gaugeMaxKmh = 240f, crashImpactG = 5.0f, crashMovingKmh = 30,
            speedLimitDefault = true, stops = listOf(FUEL, CHARGE), assumesNoCharger = false, allowsPortrait = false, windAlertKmh = null,
        )

        // A fall at 20 km/h is gentler than a motorcycle crash, so the thresholds sit lower.
        private val BICYCLE = ProfileConfig(
            costing = "bicycle", gaugeMaxKmh = 60f, crashImpactG = 3.0f, crashMovingKmh = 10,
            speedLimitDefault = false, stops = listOf(REPAIR, SHOP, WATER), assumesNoCharger = true, allowsPortrait = true, windAlertKmh = 35,
        )

        fun of(profile: VehicleProfile): ProfileConfig = when (profile) {
            VehicleProfile.MOTORCYCLE -> MOTORCYCLE
            VehicleProfile.CAR -> CAR
            VehicleProfile.BICYCLE -> BICYCLE
        }
    }
}
