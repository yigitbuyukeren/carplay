package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ygtlab.carplay.data.BatteryState

@Composable
fun Dock(
    battery: BatteryState,
    onNavigation: () -> Unit,
    onMusic: () -> Unit,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxHeight().width(96.dp).padding(vertical = 8.dp)) {
        // Tiles take whatever height the status block leaves, up to a glove-sized 76dp (never below the old 62dp).
        val gap = 8.dp
        val tile = ((maxHeight - STATUS_HEIGHT - gap * 4) / 4).coerceIn(62.dp, 76.dp)
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                DockTile(Icons.Rounded.Navigation, MotoColors.Green, tile, onNavigation)
                DockTile(Icons.Rounded.MusicNote, MotoColors.Red, tile, onMusic)
                DockTile(Icons.Rounded.Call, Color(0xFF34C759), tile, onPhone)
                DockTile(Icons.Rounded.Settings, MotoColors.Gray, tile, onSettings)
            }
            DockStatus(battery)
        }
    }
}

/** Height of the clock, battery and temperature block under the tiles. */
private val STATUS_HEIGHT = 78.dp

@Composable
private fun DockTile(icon: ImageVector, color: Color, tile: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pressClickable(onClick = onClick)
            .size(tile)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.28f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(tile * 0.55f))
    }
}
