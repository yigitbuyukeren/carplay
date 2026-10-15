package dev.ygtlab.carplay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import dev.ygtlab.carplay.data.MediaRepository
import dev.ygtlab.carplay.data.MediaState

@Composable
fun MediaCard(
    state: MediaState,
    media: MediaRepository,
    onGrantAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = remember(state.art) {
        state.art?.let { bmp ->
            Palette.from(bmp).generate().getDarkMutedColor(0xFF1C1C1E.toInt())
        }?.let { Color(it) } ?: MotoColors.Card
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(tint, Color(0xFF0E0E10))))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(28.dp))
    ) {
        when {
            !state.hasAccess -> Prompt("Müzik kontrolü için bildirim erişimi ver", "Erişim ver", onGrantAccess)
            !state.hasSession -> Prompt("Müzik çalmıyor", "Müzik uygulamasını aç", onOpenMusicApp)
            else -> Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)).background(MotoColors.CardHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = state.art
                    if (art != null) {
                        Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, tint = MotoColors.Gray, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title.ifBlank { "Bilinmeyen parça" },
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.artist,
                        fontSize = 16.sp, color = Color(0xB3FFFFFF),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Icons.AutoMirrored.Rounded.VolumeDown, 48, onClick = media::volumeDown)
                        ControlButton(Icons.Rounded.SkipPrevious, 56, onClick = { media.previous() })
                        ControlButton(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            72, filled = true, onClick = media::togglePlay,
                        )
                        ControlButton(Icons.Rounded.SkipNext, 56, onClick = { media.next() })
                        ControlButton(Icons.AutoMirrored.Rounded.VolumeUp, 48, onClick = media::volumeUp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size((size * 0.62f).dp))
    }
}

@Composable
private fun Prompt(text: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 18.sp, color = Color(0xB3FFFFFF))
        Spacer(Modifier.size(12.dp))
        PillButton(button, MotoColors.Blue, onClick)
    }
}
