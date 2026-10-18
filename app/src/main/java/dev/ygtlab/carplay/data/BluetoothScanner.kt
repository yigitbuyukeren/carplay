package dev.ygtlab.carplay.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Finds and pairs the OBD adapter from inside the app: lists paired devices, runs a classic Bluetooth
 * discovery for new ones and bonds the one the rider taps (Android shows its own PIN dialog).
 */
class BluetoothScanner(private val context: Context, private val onPaired: (String) -> Unit) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _paired = MutableStateFlow<List<ObdDevice>>(emptyList())
    val paired: StateFlow<List<ObdDevice>> = _paired.asStateFlow()

    private val _found = MutableStateFlow<List<ObdDevice>>(emptyList())
    val found: StateFlow<List<ObdDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Address being bonded right now, so the UI can show it as pending. */
    private val _pairing = MutableStateFlow<String?>(null)
    val pairing: StateFlow<String?> = _pairing.asStateFlow()

    private var registered = false

    val supported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Classic discovery needs location before Android 12; the app already asks for it for GPS.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions().all { context.hasPermission(it) }

    @SuppressLint("MissingPermission")
    fun refreshPaired() {
        val a = adapter
        if (!hasPermissions() || a == null) {
            _paired.value = emptyList()
            return
        }
        _paired.value = runCatching {
            a.bondedDevices.map { ObdDevice(it.name ?: it.address, it.address) }.sortedWith(obdFirst)
        }.getOrDefault(emptyList())
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.device() ?: return
                    if (device.bondState == BluetoothDevice.BOND_BONDED) return
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: runCatching { device.name }.getOrNull()
                    val entry = ObdDevice(name ?: device.address, device.address)
                    if (_found.value.none { it.address == entry.address }) {
                        _found.value = (_found.value + entry).sortedWith(obdFirst)
                    } else if (name != null) {
                        _found.value = _found.value.map { if (it.address == entry.address) entry else it }.sortedWith(obdFirst)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.device() ?: return
                    if (device.address != _pairing.value) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> {
                            _pairing.value = null
                            _found.value = _found.value.filterNot { it.address == device.address }
                            refreshPaired()
                            onPaired(device.address)
                        }
                        BluetoothDevice.BOND_NONE -> _pairing.value = null
                    }
                }
            }
        }
    }

    private fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val a = adapter ?: return
        if (!hasPermissions() || !a.isEnabled) return
        register()
        _found.value = emptyList()
        runCatching {
            if (a.isDiscovering) a.cancelDiscovery()
            _scanning.value = a.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        _scanning.value = false
    }

    /** Bonds a newly found device; [onPaired] fires once Android reports it bonded. */
    @SuppressLint("MissingPermission")
    fun pair(address: String) {
        val a = adapter ?: return
        register()
        stopScan()
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            onPaired(address)
            return
        }
        _pairing.value = address
        if (runCatching { device.createBond() }.getOrDefault(false) != true) _pairing.value = null
    }

    fun release() {
        stopScan()
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private val OBD_NAMES = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "CAN")

        /** Names that look like an OBD adapter. */
        fun looksLikeObd(name: String): Boolean = OBD_NAMES.any { name.uppercase().contains(it) }

        /** OBD-looking names first, then alphabetical. */
        val obdFirst: Comparator<ObdDevice> =
            compareBy<ObdDevice> { !looksLikeObd(it.name) }.thenBy { it.name.lowercase() }
    }
}
