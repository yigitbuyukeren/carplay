package dev.ygtlab.carplay.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.DtcArea
import dev.ygtlab.carplay.data.DtcInfo
import dev.ygtlab.carplay.data.ObdRepository
import dev.ygtlab.carplay.data.ObdState
import dev.ygtlab.carplay.data.ObdStatus
import dev.ygtlab.carplay.util.shareTextFile
import dev.ygtlab.carplay.data.BluetoothScanner
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ObdSettingsCard(
    obd: ObdRepository,
    bluetooth: BluetoothScanner,
    state: ObdState,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    device: String?,
    onDevice: (String) -> Unit,
    moving: Boolean,
) {
    val context = LocalContext.current
    val palette = LocalMotoPalette.current
    // Bumped after a permission or Bluetooth-on dialog so availability is read again.
    var tick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        bluetooth.refreshPaired()
        obd.start()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        bluetooth.refreshPaired()
        obd.restart()
    }
    val hasPermission = remember(tick) { bluetooth.hasPermissions() }
    val btOn = remember(tick) { bluetooth.isEnabled }
    val paired by bluetooth.paired.collectAsStateWithLifecycle()
    val found by bluetooth.found.collectAsStateWithLifecycle()
    val scanning by bluetooth.scanning.collectAsStateWithLifecycle()
    val pairing by bluetooth.pairing.collectAsStateWithLifecycle()
    var scannedOnce by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, tick) { if (enabled) bluetooth.refreshPaired() }
    DisposableEffect(Unit) { onDispose { bluetooth.stopScan() } }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.obd_title), fontSize = 20.sp, color = palette.text, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.obd_desc), fontSize = 15.sp, color = palette.muted)
            }
            Switch(checked = enabled, onCheckedChange = { on ->
                onEnabled(on)
                if (on && !bluetooth.hasPermissions()) permissionLauncher.launch(bluetooth.requiredPermissions())
            })
        }
        if (!enabled) return@Column

        if (!bluetooth.supported) return@Column
        if (!hasPermission) {
            PillButton(stringResource(R.string.obd_grant), MotoColors.Blue, { permissionLauncher.launch(bluetooth.requiredPermissions()) })
            return@Column
        }
        if (!btOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.obd_bt_off), fontSize = 16.sp, color = MotoColors.Orange)
                PillButton(stringResource(R.string.obd_bt_enable), MotoColors.Blue, {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                })
            }
            return@Column
        }

        Text(stringResource(R.string.obd_paired_devices), fontSize = 15.sp, color = palette.muted)
        if (paired.isEmpty()) {
            Text(stringResource(R.string.obd_no_paired), fontSize = 15.sp, color = MotoColors.Orange)
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                paired.forEach { d ->
                    val selected = d.address == device
                    PillButton(d.name, if (selected) MotoColors.Blue else palette.cardHigh, { onDevice(d.address) },
                        textColor = if (selected) Color.White else palette.text)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(stringResource(R.string.obd_scan), if (scanning) palette.cardHigh else MotoColors.Blue, {
                scannedOnce = true
                if (scanning) bluetooth.stopScan() else bluetooth.startScan()
            }, textColor = if (scanning) palette.text else Color.White)
            when {
                scanning -> Text(stringResource(R.string.obd_scanning), fontSize = 15.sp, color = palette.muted)
                pairing != null -> Text(stringResource(R.string.obd_pairing) + " " + stringResource(R.string.obd_pin_hint), fontSize = 15.sp, color = palette.muted)
                scannedOnce && found.isEmpty() -> Text(stringResource(R.string.obd_found_none), fontSize = 15.sp, color = MotoColors.Orange)
            }
        }
        if (found.isNotEmpty()) {
            Text(stringResource(R.string.obd_new_devices), fontSize = 15.sp, color = palette.muted)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                found.forEach { d ->
                    val busy = d.address == pairing
                    PillButton(d.name, if (BluetoothScanner.looksLikeObd(d.name)) MotoColors.Green else palette.cardHigh,
                        { if (pairing == null) bluetooth.pair(d.address) },
                        textColor = if (BluetoothScanner.looksLikeObd(d.name)) Color.White else palette.text,
                        modifier = Modifier.alpha(if (busy) 0.6f else 1f))
                }
            }
        }

        val (statusText, statusColor) = when (state.status) {
            ObdStatus.OFF -> stringResource(R.string.obd_status_off) to palette.muted
            ObdStatus.CONNECTING -> stringResource(R.string.obd_status_connecting) to palette.muted
            ObdStatus.NO_ADAPTER -> stringResource(R.string.obd_status_no_adapter) to MotoColors.Orange
            ObdStatus.NO_ECU -> stringResource(R.string.obd_status_no_ecu) to MotoColors.Orange
            ObdStatus.CONNECTED -> stringResource(R.string.obd_status_connected, state.supported.size) to MotoColors.Green
        }
        Text(statusText, fontSize = 16.sp, color = statusColor, fontWeight = FontWeight.Medium)

        if (state.status == ObdStatus.CONNECTED) {
            val codes = state.codes.orEmpty()
            if (codes.isEmpty() && !state.data.milOn) {
                Text(stringResource(R.string.obd_codes_none), fontSize = 15.sp, color = palette.muted)
            }
            codes.forEach { code ->
                Text("$code · " + stringResource(dtcText(DtcInfo.area(code))), fontSize = 17.sp, color = MotoColors.Orange)
            }
            if (codes.isNotEmpty() || state.data.milOn) {
                if (!confirmClear) {
                    PillButton(stringResource(R.string.obd_clear_codes), palette.cardHigh, {
                        if (moving) Toast.makeText(context, R.string.obd_clear_moving, Toast.LENGTH_SHORT).show()
                        else confirmClear = true
                    }, textColor = palette.text)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.obd_clear_confirm), fontSize = 15.sp, color = palette.text, modifier = Modifier.weight(1f))
                        PillButton(stringResource(R.string.cancel), palette.cardHigh, { confirmClear = false }, textColor = palette.text)
                        PillButton(stringResource(R.string.obd_clear_yes), MotoColors.Red, {
                            confirmClear = false
                            obd.clearCodes { ok ->
                                Toast.makeText(context, if (ok) R.string.obd_cleared else R.string.obd_clear_failed, Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(stringResource(R.string.obd_reset_gears), palette.cardHigh, obd::resetGears, textColor = palette.text)
            PillButton(stringResource(R.string.obd_share_log), palette.cardHigh, {
                context.shareTextFile("motodash-obd.txt", obd.logText())
            }, textColor = palette.text)
            if (state.status == ObdStatus.CONNECTED) {
                Text(stringResource(R.string.obd_gears_learned, state.learnedGears), fontSize = 15.sp, color = palette.muted)
            }
        }
    }
}

fun dtcText(area: DtcArea): Int = when (area) {
    DtcArea.MAP_MAF_SENSOR -> R.string.dtc_MAP_MAF_SENSOR
    DtcArea.INTAKE_AIR_TEMP -> R.string.dtc_INTAKE_AIR_TEMP
    DtcArea.ENGINE_TEMP_SENSOR -> R.string.dtc_ENGINE_TEMP_SENSOR
    DtcArea.THROTTLE_SENSOR -> R.string.dtc_THROTTLE_SENSOR
    DtcArea.OXYGEN_SENSOR -> R.string.dtc_OXYGEN_SENSOR
    DtcArea.FUEL_TRIM -> R.string.dtc_FUEL_TRIM
    DtcArea.INJECTOR -> R.string.dtc_INJECTOR
    DtcArea.MISFIRE -> R.string.dtc_MISFIRE
    DtcArea.CRANK_CAM_SENSOR -> R.string.dtc_CRANK_CAM_SENSOR
    DtcArea.KNOCK_SENSOR -> R.string.dtc_KNOCK_SENSOR
    DtcArea.VEHICLE_SPEED_SENSOR -> R.string.dtc_VEHICLE_SPEED_SENSOR
    DtcArea.IDLE_CONTROL -> R.string.dtc_IDLE_CONTROL
    DtcArea.SYSTEM_VOLTAGE -> R.string.dtc_SYSTEM_VOLTAGE
    DtcArea.FUEL_AIR -> R.string.dtc_FUEL_AIR
    DtcArea.IGNITION -> R.string.dtc_IGNITION
    DtcArea.EMISSION -> R.string.dtc_EMISSION
    DtcArea.SPEED_IDLE -> R.string.dtc_SPEED_IDLE
    DtcArea.ECU -> R.string.dtc_ECU
    DtcArea.TRANSMISSION -> R.string.dtc_TRANSMISSION
    DtcArea.MANUFACTURER -> R.string.dtc_MANUFACTURER
    DtcArea.CHASSIS -> R.string.dtc_CHASSIS
    DtcArea.BODY -> R.string.dtc_BODY
    DtcArea.NETWORK -> R.string.dtc_NETWORK
    DtcArea.UNKNOWN -> R.string.dtc_UNKNOWN
}
