package dev.ygtlab.carplay.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.CallPhase
import dev.ygtlab.carplay.data.CallState
import kotlinx.coroutines.delay

/** Full-screen call UI: who, what state, and big buttons; landscape, so info on the left and actions on the right. */
@Composable
fun CallScreen(
    call: CallState,
    callerName: String?,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit = {},
    onSpeaker: () -> Unit = {},
) {
    val elapsed by produceState(0L, call.startedAtMs, call.phase) {
        while (call.phase == CallPhase.ACTIVE) {
            value = ((System.currentTimeMillis() - call.startedAtMs) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val unknown = stringResource(R.string.phone_unknown)
    val name = callerName?.takeIf { it.isNotBlank() } ?: unknown
    // A raw number as the title looks like a name slot filled with digits; keep it as the headline anyway but never twice.
    val initial = name.firstOrNull()?.takeIf { it.isLetter() }?.uppercase()
    val ringing = call.phase == CallPhase.RINGING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D2247), Color(0xFF05080F), Color.Black)))
            .clickable(enabled = false) {},
    ) {
        Row(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Avatar(initial, pulsing = call.phase != CallPhase.ACTIVE)
                Text(
                    name, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                when (call.phase) {
                    CallPhase.ACTIVE -> Text(
                        "%02d:%02d".format(elapsed / 60, elapsed % 60),
                        fontSize = 32.sp, fontWeight = FontWeight.Medium, color = MotoColors.Green,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                    CallPhase.DIALING -> Text(stringResource(R.string.call_dialing) + "…", fontSize = 22.sp, color = MotoColors.Gray)
                    else -> Text(stringResource(R.string.call_incoming), fontSize = 22.sp, color = MotoColors.Gray)
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (ringing) {
                    ActionPill(Icons.Rounded.Call, stringResource(R.string.call_answer), MotoColors.Green, onAnswer)
                    ActionPill(Icons.Rounded.CallEnd, stringResource(R.string.call_decline), MotoColors.Red, onHangUp)
                } else {
                    if (call.managed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ToggleAction(Icons.Rounded.MicOff, stringResource(R.string.call_mute), call.muted, onMute, Modifier.weight(1f))
                            ToggleAction(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.call_speaker), call.speaker, onSpeaker, Modifier.weight(1f))
                        }
                    }
                    ActionPill(Icons.Rounded.CallEnd, stringResource(R.string.call_hangup), MotoColors.Red, onHangUp)
                }
            }
        }
    }
}

@Composable
private fun Avatar(initial: String?, pulsing: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.35f, label = "scale",
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier.size(104.dp).scale(pulse).alpha((1.35f - pulse) * 1.6f).clip(CircleShape).background(MotoColors.Blue),
            )
        }
        Box(
            modifier = Modifier.size(104.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF5AA9FF), Color(0xFF1F5FD6)))),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) Text(initial, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            else Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(color).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(88.dp).clip(RoundedCornerShape(24.dp))
            .background(if (on) Color.White else Color(0x26FFFFFF)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(32.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White)
    }
}
