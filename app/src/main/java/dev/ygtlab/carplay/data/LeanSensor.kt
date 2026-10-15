package dev.ygtlab.carplay.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.ygtlab.carplay.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max

data class LeanState(
    /** The phone has the sensors needed (a gyroscope and a rotation or gravity sensor). */
    val supported: Boolean = false,
    val calibrated: Boolean = false,
    /** The lean axis has been learned; before that no angle can be shown. */
    val ready: Boolean = false,
    /** Signed lean in degrees, positive = left. */
    val angleDeg: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
)

/** Feeds [LeanEstimator] from the phone's fused orientation sensor and gyroscope. */
class LeanSensor(private val context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val orientation: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyro: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val usesRotationVector = orientation?.type == Sensor.TYPE_GAME_ROTATION_VECTOR

    private val estimator = LeanEstimator().also { e -> Prefs.leanState(context)?.let(e::restore) }

    private val _state = MutableStateFlow(
        LeanState(supported = orientation != null && gyro != null, calibrated = estimator.calibrated, ready = estimator.ready)
    )
    val state: StateFlow<LeanState> = _state

    private var listening = false
    private val up = FloatArray(3)
    private var haveUp = false
    private val omega = FloatArray(3)
    private val matrix = FloatArray(9)
    private var smoothed = 0f
    private var primed = false
    private var maxLeft = 0f
    private var maxRight = 0f
    private var tick = 0

    @Volatile var moving = false

    fun start() {
        if (listening || !_state.value.supported) return
        sensors.registerListener(this, orientation, SensorManager.SENSOR_DELAY_GAME)
        sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensors.unregisterListener(this)
        listening = false
        persist()
    }

    /** Call while the bike stands upright. */
    fun calibrate() {
        if (!haveUp) return
        estimator.calibrate(up[0], up[1], up[2])
        smoothed = 0f; primed = false; maxLeft = 0f; maxRight = 0f
        persist()
        publish(null)
    }

    fun resetPeaks() {
        maxLeft = 0f; maxRight = 0f
        publish(if (_state.value.ready) smoothed else null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> System.arraycopy(event.values, 0, omega, 0, 3)
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                // Third row of the device-to-world matrix is world up expressed in device axes.
                onUp(matrix[6], matrix[7], matrix[8])
            }
            Sensor.TYPE_GRAVITY -> onUp(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onUp(x: Float, y: Float, z: Float) {
        up[0] = x; up[1] = y; up[2] = z
        haveUp = true
        // Yaw rate about world up; positive is counter-clockwise from above, i.e. a left turn.
        val yaw = omega[0] * x + omega[1] * y + omega[2] * z
        val angle = estimator.update(x, y, z, yaw, moving)
        if (angle != null) {
            smoothed = if (primed) smoothed + SMOOTHING * (angle - smoothed) else angle
            primed = true
            if (smoothed > 0) maxLeft = max(maxLeft, smoothed) else maxRight = max(maxRight, abs(smoothed))
        }
        if (++tick % PUBLISH_EVERY == 0) publish(angle?.let { smoothed })
    }

    private fun publish(angle: Float?) {
        _state.value = _state.value.copy(
            calibrated = estimator.calibrated,
            ready = estimator.ready,
            angleDeg = angle ?: if (estimator.ready) smoothed else 0f,
            maxLeftDeg = maxLeft,
            maxRightDeg = maxRight,
        )
    }

    private fun persist() {
        Prefs.setLeanState(context, estimator.snapshot())
    }

    private companion object {
        const val SMOOTHING = 0.2f
        const val PUBLISH_EVERY = 5
    }
}
