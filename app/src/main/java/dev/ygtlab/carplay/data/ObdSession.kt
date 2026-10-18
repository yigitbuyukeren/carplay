package dev.ygtlab.carplay.data

import java.io.IOException

/** One reading of the engine; null fields are not supported by this bike or not read yet. */
data class EngineData(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val intakeC: Int? = null,
    val throttlePct: Int? = null,
    val loadPct: Int? = null,
    val voltage: Float? = null,
    val milOn: Boolean = false,
    val codeCount: Int = 0,
)

/** Speaks ELM327 over an [ElmLink]: setup, supported PID discovery and polling. No Android types, so it is unit tested. */
class ObdSession(private val link: ElmLink, private val log: (String) -> Unit = {}) {
    var supported: Set<Int> = emptySet()
        private set
    var isCan = false
        private set

    private suspend fun cmd(command: String, timeoutMs: Long = 3000): String {
        log("> $command")
        val reply = link.send(command, timeoutMs)
        log("< " + reply.trim().replace("\r", " | "))
        return reply
    }

    /** Resets the adapter and configures it for compact replies. */
    suspend fun setup() {
        cmd("ATZ", 5000)
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) cmd(c)
    }

    /**
     * Asks the ECU which PIDs it supports. The first request triggers the protocol search, which on K-line
     * bikes can take several seconds. Returns false when the ECU does not answer (usually ignition off).
     */
    suspend fun discover(): Boolean {
        val first = cmd("0100", 15_000)
        if (ObdProtocol.isNoAnswer(first)) return false
        val found = ObdProtocol.supportedPids(first, 0x00).toMutableSet()
        if (found.isEmpty()) return false
        var base = 0x20
        while (base in found && base <= 0xA0) {
            val more = ObdProtocol.supportedPids(cmd("01" + base.toString(16).padStart(2, '0').uppercase()), base)
            if (more.isEmpty()) break
            found += more
            base += 0x20
        }
        supported = found
        isCan = ObdProtocol.isCanProtocol(cmd("ATDPN"))
        return true
    }

    private suspend fun read(pid: Int): IntArray? {
        if (pid !in supported) return null
        val raw = cmd("01" + pid.toString(16).padStart(2, '0').uppercase())
        if (raw.uppercase().contains("UNABLE TO CONNECT") || raw.uppercase().contains("UNABLETOCONNECT")) {
            throw EcuLostException()
        }
        return ObdProtocol.mode01Data(raw, pid)
    }

    /** Fast values, read every cycle. */
    suspend fun readFast(prev: EngineData): EngineData {
        val rpm = read(Pid.RPM)?.takeIf { it.size >= 2 }?.let(ObdProtocol::rpm)
        val speed = read(Pid.SPEED)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::speedKmh)
        val throttle = read(Pid.THROTTLE)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::percent)
        return prev.copy(rpm = rpm ?: prev.rpm, speedKmh = speed ?: prev.speedKmh, throttlePct = throttle ?: prev.throttlePct)
    }

    /** Slow values: temperatures, load, voltage and the malfunction lamp. */
    suspend fun readSlow(prev: EngineData): EngineData {
        val coolant = read(Pid.COOLANT_TEMP)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::temperatureC)
        val intake = read(Pid.INTAKE_TEMP)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::temperatureC)
        val load = read(Pid.ENGINE_LOAD)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::percent)
        val status = read(Pid.MONITOR_STATUS)?.takeIf { it.isNotEmpty() }?.let(ObdProtocol::monitorStatus)
        val voltage = ObdProtocol.adapterVoltage(cmd("ATRV"))
            ?: read(Pid.MODULE_VOLTAGE)?.takeIf { it.size >= 2 }?.let(ObdProtocol::moduleVoltage)
        return prev.copy(
            coolantC = coolant ?: prev.coolantC,
            intakeC = intake ?: prev.intakeC,
            loadPct = load ?: prev.loadPct,
            voltage = voltage ?: prev.voltage,
            milOn = status?.first ?: prev.milOn,
            codeCount = status?.second ?: prev.codeCount,
        )
    }

    suspend fun readCodes(): List<String> = ObdProtocol.troubleCodes(cmd("03", 5000), isCan)

    /** Mode 04: clears stored codes and turns the malfunction lamp off. */
    suspend fun clearCodes(): Boolean {
        val reply = cmd("04", 5000)
        return ObdProtocol.cleanLines(reply).any { it.startsWith("44") }
    }

    class EcuLostException : IOException("ECU stopped answering")
}
