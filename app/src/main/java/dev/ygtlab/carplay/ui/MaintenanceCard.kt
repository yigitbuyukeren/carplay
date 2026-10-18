package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.MaintenanceMath
import dev.ygtlab.carplay.data.ServiceItem
import dev.ygtlab.carplay.data.ServiceState
import java.util.Locale

fun formatKm(km: Int): String = String.format(Locale.getDefault(), "%,d", km)

/** Service reminders counted in kilometres ridden with the app running. */
@Composable
fun MaintenanceCard(
    odometerM: Double,
    items: List<ServiceState>,
    onDone: (ServiceItem) -> Unit,
    onInterval: (ServiceItem, Int) -> Unit,
) {
    val palette = LocalMotoPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.svc_title), fontSize = 20.sp, color = palette.text, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.svc_desc), fontSize = 15.sp, color = palette.muted)
        Text(stringResource(R.string.svc_counted_fmt, formatKm((odometerM / 1000).toInt())), fontSize = 16.sp, color = palette.text)
        items.forEach { s ->
            val remaining = MaintenanceMath.remainingKm(s, odometerM).toInt()
            val color = when {
                remaining <= 0 -> MotoColors.Red
                remaining <= MaintenanceMath.SOON_KM -> MotoColors.Orange
                else -> palette.muted
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(s.item.label), fontSize = 18.sp, color = palette.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        if (remaining <= 0) stringResource(R.string.svc_overdue_fmt, formatKm(-remaining)) else stringResource(R.string.svc_left_fmt, formatKm(remaining)),
                        fontSize = 16.sp, color = color, fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("−", palette.cardHigh, { onInterval(s.item, -1) }, textColor = palette.text, horizontalPadding = 18.dp)
                    Text(stringResource(R.string.svc_every_fmt, formatKm(s.intervalKm)), fontSize = 16.sp, color = palette.text)
                    PillButton("+", palette.cardHigh, { onInterval(s.item, +1) }, textColor = palette.text, horizontalPadding = 18.dp)
                    Row(modifier = Modifier.weight(1f)) {}
                    PillButton(stringResource(R.string.svc_done), MotoColors.Green, { onDone(s.item) })
                }
            }
        }
    }
}
