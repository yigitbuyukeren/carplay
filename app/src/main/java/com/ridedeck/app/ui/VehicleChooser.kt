package com.ridedeck.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedeck.app.R
import com.ridedeck.app.data.VehicleProfile

/**
 * First-launch question: motorcycle, car or bicycle. Big cards for gloves; side by side on a wide screen,
 * stacked on an upright one. Covers everything, including the settings page that opens without permissions.
 */
@Composable
fun VehicleChooser(onChoose: (VehicleProfile) -> Unit) {
    val palette = LocalMotoPalette.current
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    Box(modifier = Modifier.fillMaxSize().background(palette.wallpaper).clickable(enabled = false) {}) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.vehicle_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = palette.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.vehicle_sub), fontSize = 16.sp, color = palette.muted, textAlign = TextAlign.Center)
            val cards = listOf(
                Triple(VehicleProfile.MOTORCYCLE, Icons.Rounded.TwoWheeler, R.string.vehicle_moto to R.string.vehicle_moto_desc),
                Triple(VehicleProfile.CAR, Icons.Rounded.DirectionsCar, R.string.vehicle_car to R.string.vehicle_car_desc),
                Triple(VehicleProfile.BICYCLE, Icons.Rounded.PedalBike, R.string.vehicle_bike to R.string.vehicle_bike_desc),
            )
            if (portrait) {
                cards.forEach { (profile, icon, text) -> VehicleCard(icon, text.first, text.second, { onChoose(profile) }, Modifier.fillMaxWidth()) }
            } else {
                Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards.forEach { (profile, icon, text) -> VehicleCard(icon, text.first, text.second, { onChoose(profile) }, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(icon: ImageVector, title: Int, desc: Int, onClick: () -> Unit, modifier: Modifier) {
    val palette = LocalMotoPalette.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(palette.cardHigh).clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MotoColors.Blue).padding(10.dp))
        Text(stringResource(title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.text)
        Text(stringResource(desc), fontSize = 15.sp, color = palette.muted, textAlign = TextAlign.Center)
    }
}
