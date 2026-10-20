package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.VoicePhase
import dev.ygtlab.carplay.data.VoiceUi

/** A round microphone button; 64dp so it works with gloves. */
@Composable
fun MicButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MotoColors.Blue).pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Mic, stringResource(R.string.voice_mic), tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

/** The listening card, the call countdown and short results, floating above everything except an SOS countdown. */
@Composable
fun VoiceOverlay(ui: VoiceUi, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    if (ui.phase == VoicePhase.IDLE) return
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color(0xF2182430)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (ui.phase) {
                VoicePhase.LISTENING -> {
                    Icon(Icons.Rounded.Mic, null, tint = MotoColors.Red, modifier = Modifier.size(44.dp))
                    Text(
                        ui.text.ifBlank { stringResource(R.string.voice_listening) },
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                VoicePhase.CONFIRM -> {
                    Text(ui.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    Text("${ui.secondsLeft}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MotoColors.Orange)
                }
                else -> Text(ui.text, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (ui.phase == VoicePhase.LISTENING || ui.phase == VoicePhase.CONFIRM) {
                PillButton(stringResource(R.string.voice_cancel), MotoColors.PanelHigh, onCancel, Modifier.fillMaxWidth())
            }
        }
    }
}
