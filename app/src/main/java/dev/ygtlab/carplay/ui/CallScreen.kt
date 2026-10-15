package dev.ygtlab.carplay.ui

import androidx.compose.ui.res.stringResource
import dev.ygtlab.carplay.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.data.CallPhase
import dev.ygtlab.carplay.data.CallState
import kotlinx.coroutines.delay

@Composable
fun CallScreen(call: CallState, callerName: String?, onAnswer: () -> Unit, onHangUp: () -> Unit) {
    val elapsed by produceState(0L, call.startedAtMs, call.phase) {
        while (call.phase == CallPhase.ACTIVE) {
            value = (System.currentTimeMillis() - call.startedAtMs) / 1000
            delay(1000)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF2000000)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                if (call.phase == CallPhase.RINGING) stringResource(R.string.call_incoming) else stringResource(R.string.call_active),
                fontSize = 22.sp, color = MotoColors.Gray,
            )
            Text(
                callerName ?: "Bilinmeyen numara",
                fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            if (call.phase == CallPhase.ACTIVE) {
                Text("%02d:%02d".format(elapsed / 60, elapsed % 60), fontSize = 28.sp, color = MotoColors.Green)
            }
            Spacer(Modifier.size(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                if (call.phase == CallPhase.RINGING) {
                    RoundAction(Icons.Rounded.CallEnd, MotoColors.Red, onHangUp)
                    RoundAction(Icons.Rounded.Call, MotoColors.Green, onAnswer)
                } else {
                    RoundAction(Icons.Rounded.CallEnd, MotoColors.Red, onHangUp)
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(112.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
    }
}
