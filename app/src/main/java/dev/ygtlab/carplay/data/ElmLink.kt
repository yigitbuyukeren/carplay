package dev.ygtlab.carplay.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** A text channel to an ELM327 adapter: send a command, get everything up to the `>` prompt. */
interface ElmLink {
    suspend fun open()
    suspend fun send(command: String, timeoutMs: Long): String
    fun close()
}

/** Classic Bluetooth (SPP/RFCOMM) adapter, the kind that is paired in Android's Bluetooth settings. */
class BluetoothElmLink(private val address: String) : ElmLink {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override suspend fun open() = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("no bluetooth")
        if (!adapter.isEnabled) throw IOException("bluetooth off")
        val device = adapter.getRemoteDevice(address)
        runCatching { adapter.cancelDiscovery() }
        val attempts = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(SPP) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            // Many cheap ELM327 clones only accept a connection on channel 1 without an SDP lookup.
            { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket },
        )
        var last: Exception? = null
        for (attempt in attempts) {
            val s = runCatching(attempt).getOrNull() ?: continue
            try {
                s.connect()
                socket = s
                return@withContext
            } catch (e: IOException) {
                last = e
                runCatching { s.close() }
            }
        }
        throw IOException("connect failed", last)
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("not connected")
        val input = s.inputStream
        while (input.available() > 0) input.read()
        s.outputStream.write("$command\r".toByteArray(Charsets.US_ASCII))
        s.outputStream.flush()
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val c = input.read()
                if (c < 0) throw IOException("closed")
                if (c.toChar() == '>') return@withContext out.toString()
                out.append(c.toChar())
            } else {
                delay(5)
            }
        }
        throw IOException("timeout: $command")
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * Simulated adapter for development (the emulator has no Bluetooth). Rides through five gears in a loop and
 * reports one stored code until it is cleared, answering like a CAN ELM327 with spaces off.
 */
class FakeElmLink(private val clock: () -> Long = System::currentTimeMillis) : ElmLink {
    private var codeStored = true
    private val start = clock()

    override suspend fun open() = delay(300)

    override suspend fun send(command: String, timeoutMs: Long): String {
        delay(15)
        val c = command.uppercase().replace(" ", "")
        val (speed, rpm) = ride((clock() - start) / 1000.0)
        return when {
            c == "ATZ" -> "ELM327 v1.5\r\r"
            c == "ATRV" -> "13.8V\r\r"
            c == "ATDPN" -> "A6\r\r"
            c.startsWith("AT") -> "OK\r\r"
            c == "0100" -> "4100BE1F8011\r\r"
            c == "0120" -> "4120A0000001\r\r"
            c == "0140" -> "41404000000000\r\r"
            c == "0101" -> (if (codeStored) "41018107E500" else "41010007E500") + "\r\r"
            c == "0104" -> "4104" + hex(90) + "\r\r"
            c == "0105" -> "4105" + hex(40 + 92) + "\r\r"
            c == "010C" -> "410C" + hex(rpm * 4 / 256) + hex(rpm * 4 % 256) + "\r\r"
            c == "010D" -> "410D" + hex(speed) + "\r\r"
            c == "010F" -> "410F" + hex(40 + 31) + "\r\r"
            c == "0111" -> "4111" + hex(if (rpm > 3000) 120 else 40) + "\r\r"
            c == "0142" -> "41423708\r\r"
            c == "03" -> (if (codeStored) "43010335" else "4300") + "\r\r"
            c == "04" -> { codeStored = false; "44\r\r" }
            else -> "NO DATA\r\r"
        }
    }

    override fun close() {}

    private fun hex(v: Int) = v.coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()

    /** 60 s loop: idle, accelerate through gears 1-5 with a shift every 8 s, then coast down. */
    private fun ride(t: Double): Pair<Int, Int> {
        val ratios = doubleArrayOf(4.8, 7.6, 10.2, 12.4, 14.3) // km/h per 1000 rpm
        val s = t % 60
        if (s < 5) return Pair(0, 1400)
        if (s >= 45) {
            val speed = (70 * (1 - (s - 45) / 15)).toInt().coerceAtLeast(0)
            return Pair(speed, if (speed < 5) 1400 else 2200)
        }
        val gear = ((s - 5) / 8).toInt().coerceAtMost(4)
        val inGear = (s - 5) - gear * 8
        val rpm = (3000 + inGear * 650).toInt()
        return Pair((rpm / 1000.0 * ratios[gear]).toInt(), rpm)
    }
}
