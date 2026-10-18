package dev.ygtlab.carplay.data

import android.Manifest
import android.content.Context
import android.os.Build
import dev.ygtlab.carplay.util.Prefs
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ObdStatus { OFF, CONNECTING, NO_ADAPTER, NO_ECU, CONNECTED }

data class ObdDevice(val name: String, val address: String)

data class ObdState(
    val status: ObdStatus = ObdStatus.OFF,
    val data: EngineData = EngineData(),
    val gear: Int? = null,
    val codes: List<String>? = null,
    val supported: Set<Int> = emptySet(),
    val learnedGears: Int = 0,
)

/**
 * Keeps a connection to the paired ELM327 adapter while enabled, reconnecting when the adapter or the ECU
 * goes away (ignition off), and publishes engine data. A raw log of the traffic is kept for bug reports.
 */
class ObdRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAlert: (EngineAlert) -> Unit,
) {
    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val gears = GearEstimator(Prefs.obdGearRatios(context))
    private val alerts = ObdAlerts()
    private val io = Mutex()
    private var job: Job? = null
    private var session: ObdSession? = null
    private val logLines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    /** Starts (or restarts) the connection loop for the saved adapter. Idempotent. */
    fun start() {
        if (job?.isActive == true || !Prefs.obdEnabled(context)) return
        val address = Prefs.obdDevice(context) ?: return
        if (address != FAKE_ADDRESS && !hasPermission()) return
        job = scope.launch { loop(address) }
    }

    fun stop() {
        job?.cancel()
        job = null
        session = null
        _state.value = ObdState()
    }

    fun restart() {
        stop()
        start()
    }

    private suspend fun loop(address: String) {
        while (scope.isActive) {
            val link = if (address == FAKE_ADDRESS) FakeElmLink() else BluetoothElmLink(address)
            _state.update { it.copy(status = ObdStatus.CONNECTING) }
            try {
                link.open()
                log("adapter connected: $address")
                val s = ObdSession(link, ::log)
                s.setup()
                while (!s.discover()) {
                    _state.update { it.copy(status = ObdStatus.NO_ECU) }
                    delay(RETRY_MS)
                }
                log("supported PIDs: " + s.supported.sorted().joinToString { it.toString(16) } + " can=" + s.isCan)
                session = s
                _state.update { it.copy(status = ObdStatus.CONNECTED, supported = s.supported) }
                poll(s)
            } catch (e: CancellationException) {
                link.close()
                throw e
            } catch (e: ObdSession.EcuLostException) {
                log("ecu lost")
                _state.update { ObdState(status = ObdStatus.NO_ECU) }
            } catch (e: Exception) {
                log("error: $e")
                _state.update { ObdState(status = ObdStatus.NO_ADAPTER) }
            }
            session = null
            link.close()
            delay(RETRY_MS)
        }
    }

    private suspend fun poll(s: ObdSession) {
        var cycle = 0
        var data = EngineData()
        while (scope.isActive) {
            io.withLock {
                data = s.readFast(data)
                if (cycle % SLOW_EVERY == 0) {
                    data = s.readSlow(data)
                    val hasCodes = data.codeCount > 0 || data.milOn
                    when {
                        !hasCodes -> _state.update { it.copy(codes = emptyList()) }
                        _state.value.codes.isNullOrEmpty() || cycle % CODES_EVERY == 0 ->
                            s.readCodes().let { codes -> _state.update { it.copy(codes = codes) } }
                    }
                    alerts.check(data).forEach(onAlert)
                }
            }
            val speed = data.speedKmh
            val rpm = data.rpm
            val gear = if (speed != null && rpm != null) gears.update(speed, rpm) else null
            if (cycle % SAVE_GEARS_EVERY == 0) Prefs.setObdGearRatios(context, gears.ratios)
            _state.update { it.copy(data = data, gear = gear, learnedGears = gears.ratios.size) }
            cycle++
            delay(POLL_MS)
        }
    }

    /** Clears stored trouble codes. Callers must make sure the bike is standing still. */
    fun clearCodes(onDone: (Boolean) -> Unit) {
        val s = session ?: return onDone(false)
        scope.launch {
            val ok = runCatching { io.withLock { s.clearCodes() } }.getOrDefault(false)
            if (ok) _state.update { it.copy(codes = emptyList(), data = it.data.copy(milOn = false, codeCount = 0)) }
            onDone(ok)
        }
    }

    fun resetGears() {
        gears.reset()
        Prefs.setObdGearRatios(context, emptyList())
        _state.update { it.copy(learnedGears = 0, gear = null) }
    }

    @Synchronized
    private fun log(line: String) {
        logLines.addLast(clock.format(Date()) + " " + line)
        while (logLines.size > MAX_LOG) logLines.removeFirst()
    }

    @Synchronized
    fun logText(): String = logLines.joinToString("\n")

    companion object {
        const val FAKE_ADDRESS = "fake"
        private const val RETRY_MS = 5000L
        private const val POLL_MS = 150L
        private const val SLOW_EVERY = 10
        private const val CODES_EVERY = 200
        private const val SAVE_GEARS_EVERY = 100
        private const val MAX_LOG = 2000
    }
}
