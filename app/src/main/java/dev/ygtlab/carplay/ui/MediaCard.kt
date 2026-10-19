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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.MediaRepository
import dev.ygtlab.carplay.data.MediaState

@Composable
fun MediaCard(
    state: MediaState,
    media: MediaRepository,
    onGrantAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMotoPalette.current
    val tint = remember(state.art, palette) { mediaTint(state.art, palette) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(tint, palette.mediaBase)))
            .border(1.dp, palette.border, RoundedCornerShape(28.dp))
    ) {
        when {
            !state.hasAccess -> Prompt(stringResource(R.string.media_grant_prompt), stringResource(R.string.grant_access), onGrantAccess)
            !state.hasSession -> Prompt(stringResource(R.string.media_not_playing), stringResource(R.string.open_music_app), onOpenMusicApp)
            else -> Row(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(MotoColors.CardHigh)
                        .clickable(onClick = onOpenNowPlaying),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = state.art
                    if (art != null) {
                        Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, tint = palette.muted, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title.ifBlank { stringResource(R.string.unknown_track) },
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.artist,
                        fontSize = 16.sp, color = palette.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Icons.AutoMirrored.Rounded.VolumeDown, 52, onClick = media::volumeDown)
                        ControlButton(Icons.Rounded.SkipPrevious, 58, onClick = { media.previous() })
                        ControlButton(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            72, filled = true, onClick = media::togglePlay,
                        )
                        ControlButton(Icons.Rounded.SkipNext, 58, onClick = { media.next() })
                        ControlButton(Icons.AutoMirrored.Rounded.VolumeUp, 52, onClick = media::volumeUp)
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
            .background(if (filled) LocalMotoPalette.current.strong else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (filled) LocalMotoPalette.current.onStrong else LocalMotoPalette.current.text, modifier = Modifier.size((size * 0.62f).dp))
    }
}

@Composable
private fun Prompt(text: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 18.sp, color = LocalMotoPalette.current.textSecondary)
        Spacer(Modifier.size(12.dp))
        PillButton(button, MotoColors.Blue, onClick)
    }
}

/** Album-art tint for the media cards: a dark swatch at night, a light one by day. */
internal fun mediaTint(art: android.graphics.Bitmap?, palette: MotoPalette): Color {
    val fallback = palette.card
    val bmp = art ?: return fallback
    val swatch = Palette.from(bmp).generate()
    val rgb = if (palette.isDay) swatch.getLightMutedColor(fallback.toArgb()) else swatch.getDarkMutedColor(fallback.toArgb())
    return Color(rgb)
}
