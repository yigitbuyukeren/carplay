package dev.ygtlab.carplay.ui

import androidx.compose.ui.res.stringResource
import dev.ygtlab.carplay.R
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import dev.ygtlab.carplay.data.MediaRepository
import dev.ygtlab.carplay.data.MediaState
import dev.ygtlab.carplay.util.firstInstalledMusicApp
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    state: MediaState,
    media: MediaRepository,
    onOpenMusicApp: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val query = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        status = when {
            query.isNullOrBlank() -> ""
            media.playFromSearch(query, state.packageName ?: context.firstInstalledMusicApp()) -> context.getString(R.string.playing_query_fmt, query)
            else -> context.getString(R.string.music_app_not_found)
        }
    }
    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            status = context.getString(R.string.no_speech)
        }
    }

    val tint = remember(state.art) {
        state.art?.let { Palette.from(it).generate().getDarkMutedColor(0xFF1C1C1E.toInt()) }
            ?.let { Color(it) } ?: MotoColors.Card
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(tint, Color(0xFF0E0E10))))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(28.dp))
    ) {
        if (!state.hasAccess) {
            Empty(stringResource(R.string.media_grant_prompt), stringResource(R.string.grant_access), onGrantAccess)
            return@Box
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MotoColors.CardHigh),
                contentAlignment = Alignment.Center,
            ) {
                val art = state.art
                if (art != null) {
                    Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = MotoColors.Gray, modifier = Modifier.size(72.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (state.hasSession) state.title.ifBlank { stringResource(R.string.unknown_track) } else stringResource(R.string.media_not_playing),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (state.artist.isNotBlank()) {
                        Text(
                            state.artist,
                            fontSize = 19.sp, color = Color(0xB3FFFFFF),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (status.isNotBlank()) {
                        Text(status, fontSize = 16.sp, color = MotoColors.Cyan, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (state.hasSession) Progress(state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Rounded.Mic, 64, MotoColors.Blue, onClick = startVoiceSearch)
                    RoundButton(Icons.Rounded.SkipPrevious, 64, Color.Transparent, enabled = state.hasSession) { media.previous() }
                    RoundButton(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        84, Color.White, dark = true, enabled = state.hasSession, onClick = media::togglePlay,
                    )
                    RoundButton(Icons.Rounded.SkipNext, 64, Color.Transparent, enabled = state.hasSession) { media.next() }
                    RoundButton(Icons.AutoMirrored.Rounded.OpenInNew, 64, Color(0x33FFFFFF), onClick = onOpenMusicApp)
                }
            }
        }
    }
}

@Composable
private fun Progress(state: MediaState) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val position = if (state.playing) state.positionMs + ((now - state.positionAt) * state.speed).toLong() else state.positionMs
    val clamped = if (state.durationMs > 0) position.coerceIn(0, state.durationMs) else position.coerceAtLeast(0)
    val fraction = if (state.durationMs > 0) clamped.toFloat() / state.durationMs else 0f
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(clamped), fontSize = 14.sp, color = Color(0x99FFFFFF))
            if (state.durationMs > 0) Text(clock(state.durationMs), fontSize = 14.sp, color = Color(0x99FFFFFF))
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Int,
    background: Color,
    dark: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = (if (dark) Color.Black else Color.White).copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
private fun Empty(text: String, button: String, onClick: () -> Unit) {
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
