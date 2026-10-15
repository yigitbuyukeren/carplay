package dev.ygtlab.carplay.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.data.BatteryState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CarPlay-style clock + battery readout at the bottom of the dock. */
@Composable
fun DockStatus(battery: BatteryState) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val tempColor = when {
        battery.tempC >= 45f -> MotoColors.Red
        battery.tempC >= 40f -> MotoColors.Orange
        else -> LocalMotoPalette.current.muted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LocalMotoPalette.current.text)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = if (battery.charging) MotoColors.Green else LocalMotoPalette.current.muted,
                modifier = Modifier.size(16.dp),
            )
            Text("%${battery.percent}", fontSize = 13.sp, color = LocalMotoPalette.current.muted)
        }
        Text("${battery.tempC.toInt()}°C", fontSize = 13.sp, color = tempColor, fontWeight = FontWeight.Medium)
    }
}
