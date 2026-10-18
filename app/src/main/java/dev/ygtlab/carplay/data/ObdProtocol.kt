package dev.ygtlab.carplay.data

/** Standard mode 01 PIDs MotoDash reads. */
object Pid {
    const val MONITOR_STATUS = 0x01
    const val ENGINE_LOAD = 0x04
    const val COOLANT_TEMP = 0x05
    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val INTAKE_TEMP = 0x0F
    const val THROTTLE = 0x11
    const val MODULE_VOLTAGE = 0x42
}

/** Pure parsing of ELM327 replies; no I/O so it can be unit tested. */
object ObdProtocol {

    /**
     * Splits a raw ELM327 reply into hex-only lines: drops the prompt, echo, status words like
     * SEARCHING..., spaces, ISO-TP frame prefixes ("0:", "1:") and the bare length line of a multi-frame reply.
     */
    fun cleanLines(raw: String): List<String> =
        raw.replace(">", "")
            .split('\r', '\n')
            .map { it.replace(" ", "").uppercase() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") && !it.startsWith("BUSINIT") }
            .map { it.substringAfter(':') }
            .filter { line -> line.isNotEmpty() && line.all { it.isHexDigit() } }
            .filterNot { it.length == 3 }

    /** True when the reply means the ECU did not answer (ignition off, wrong protocol, unsupported PID). */
    fun isNoAnswer(raw: String): Boolean {
        val r = raw.uppercase()
        return listOf("NO DATA", "NODATA", "UNABLE TO CONNECT", "UNABLETOCONNECT", "ERROR", "STOPPED", "?", "CAN ERROR")
            .any { r.contains(it) } && cleanLines(raw).none { it.startsWith("4") }
    }

    /** Data bytes of the first `41 <pid>` reply, or null. */
    fun mode01Data(raw: String, pid: Int): IntArray? {
        val prefix = "41" + pid.hex2()
        val line = cleanLines(raw).firstOrNull { it.startsWith(prefix) } ?: return null
        return line.substring(4).toBytes()
    }

    /** PIDs announced by a `01 00` / `01 20` / `01 40` bitmask reply starting at [base]. */
    fun supportedPids(raw: String, base: Int): Set<Int> {
        val data = mode01Data(raw, base) ?: return emptySet()
        if (data.size < 4) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 0 until 32) {
            val byte = data[i / 8]
            if (byte and (0x80 shr (i % 8)) != 0) result += base + i + 1
        }
        return result
    }

    fun rpm(d: IntArray): Int = (d[0] * 256 + d[1]) / 4
    fun temperatureC(d: IntArray): Int = d[0] - 40
    fun speedKmh(d: IntArray): Int = d[0]
    fun percent(d: IntArray): Int = (d[0] * 100 + 127) / 255
    fun moduleVoltage(d: IntArray): Float = (d[0] * 256 + d[1]) / 1000f

    /** Malfunction lamp state and stored code count from PID 01. */
    fun monitorStatus(d: IntArray): Pair<Boolean, Int> = Pair(d[0] and 0x80 != 0, d[0] and 0x7F)

    /** `ATRV` reply such as "12.6V". */
    fun adapterVoltage(raw: String): Float? =
        Regex("""(\d{1,2}\.\d)\s*V""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toFloatOrNull()

    /** `ATDPN` reply ("A6", "6", "5") -> true when the bus is CAN (protocols 6 to C). */
    fun isCanProtocol(raw: String): Boolean {
        val code = raw.replace(">", "").trim().uppercase().removePrefix("A").firstOrNull() ?: return false
        return code in '6'..'9' || code in 'A'..'C'
    }

    /**
     * Stored trouble codes from a mode 03 reply. On CAN the first byte after `43` is the code count and a
     * long reply is split into ISO-TP frames; on K-line every line is `43` followed by three 2-byte codes.
     */
    fun troubleCodes(raw: String, can: Boolean): List<String> {
        val lines = cleanLines(raw)
        val payloads: List<IntArray> = if (can) {
            val multiFrame = raw.contains(Regex("""(^|[\r\n])\s*[0-9A-Fa-f]:"""))
            val chunks = if (multiFrame) listOf(lines.joinToString("")) else lines
            chunks.mapNotNull { chunk ->
                val start = chunk.indexOf("43").takeIf { it >= 0 && it % 2 == 0 } ?: return@mapNotNull null
                val bytes = chunk.substring(start + 2).toBytes()
                if (bytes.isEmpty()) return@mapNotNull null
                val count = bytes[0]
                bytes.drop(1).take(count * 2).toIntArray()
            }
        } else {
            lines.filter { it.startsWith("43") }.map { it.substring(2).toBytes() }
        }
        return payloads.flatMap { p ->
            (0 until p.size / 2).map { i -> Pair(p[2 * i], p[2 * i + 1]) }
                .filter { (a, b) -> a != 0 || b != 0 }
                .map { (a, b) -> formatCode(a, b) }
        }.distinct()
    }

    /** Two DTC bytes -> "P0335". */
    fun formatCode(a: Int, b: Int): String {
        val letter = "PCBU"[(a shr 6) and 0x3]
        val first = (a shr 4) and 0x3
        return "$letter$first" + (a and 0xF).toString(16).uppercase() + b.hex2()
    }
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'A'..'F'

private fun Int.hex2() = toString(16).padStart(2, '0').uppercase()

private fun String.toBytes(): IntArray =
    chunked(2).filter { it.length == 2 }.map { it.toInt(16) }.toIntArray()
