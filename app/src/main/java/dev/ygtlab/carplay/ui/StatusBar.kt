package dev.ygtlab.carplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun StatusBar(battery: BatteryState, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("d MMMM, EEEE", Locale.forLanguageTag("tr")).format(Date(now))
    val tempColor = when {
        battery.tempC >= 45f -> MotoColors.Red
        battery.tempC >= 40f -> MotoColors.Orange
        else -> Color.White
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Column(modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)) {
                Text(date, fontSize = 16.sp, color = MotoColors.Gray)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${battery.tempC.toInt()}°C", fontSize = 18.sp, color = tempColor, fontWeight = FontWeight.Medium)
            Icon(
                if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = if (battery.charging) MotoColors.Green else Color.White,
                modifier = Modifier.padding(start = 14.dp).size(26.dp),
            )
            Text("%${battery.percent}", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
