package dev.ygtlab.carplay.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import java.util.Locale
import dev.ygtlab.carplay.data.SpeedLimitMath
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.GpsUi
import dev.ygtlab.carplay.R

@Composable
fun SpeedCard(
    gps: GpsUi,
    limit: Int?,
    modifier: Modifier = Modifier,
    onSos: (() -> Unit)? = null,
    /** Full-scale of the gauge, km/h; depends on the vehicle. */
    maxKmh: Float = 200f,
    onClick: () -> Unit = {},
) {
    val fraction by animateFloatAsState(
        targetValue = (gps.speedKmh / maxKmh).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "speed",
    )
    val trackColor = LocalMotoPalette.current.track
    Box(modifier = modifier.card().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(20.dp)
        ) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val progressColor = when {
                fraction < 0.4f -> lerp(MotoColors.Cyan, MotoColors.Blue, fraction / 0.4f)
                fraction < 0.65f -> lerp(MotoColors.Blue, MotoColors.Orange, (fraction - 0.4f) / 0.25f)
                else -> lerp(MotoColors.Orange, MotoColors.Red, ((fraction - 0.65f) / 0.25f).coerceAtMost(1f))
            }
            rotate(150f) {
                drawArc(trackColor, 0f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0.004f) {
                    drawArc(progressColor, 0f, 240f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                gps.speedKmh.toString(),
                fontSize = cappedSp(92f),
                fontWeight = FontWeight.Bold,
                color = if (SpeedLimitMath.isOver(gps.speedKmh, limit)) MotoColors.Red else LocalMotoPalette.current.text,
            )
            Text(stringResource(R.string.speed_unit), fontSize = cappedSp(22f), color = LocalMotoPalette.current.muted, fontWeight = FontWeight.Medium)
            if (!gps.hasFix) {
                Text(stringResource(R.string.gps_waiting), fontSize = cappedSp(16f), color = MotoColors.Orange, modifier = Modifier.padding(top = 8.dp))
            } else {
                GpsInfo(gps, Modifier.padding(top = 10.dp))
            }
        }
        if (onSos != null) SosButton(onSos, Modifier.align(Alignment.BottomStart).padding(16.dp))
        if (limit != null) SpeedLimitSign(limit, Modifier.align(Alignment.TopEnd).padding(16.dp), size = 76.dp)
    }
}

/** Altitude and how good the GPS fix is: the accuracy radius turned into a word and a colour. */
@Composable
private fun GpsInfo(gps: GpsUi, modifier: Modifier = Modifier) {
    val muted = LocalMotoPalette.current.muted
    val (label, color) = when {
        gps.accuracyM == null -> R.string.gps_fair to MotoColors.Orange
        gps.accuracyM <= GPS_GOOD_M -> R.string.gps_good to MotoColors.Green
        gps.accuracyM <= GPS_FAIR_M -> R.string.gps_fair to MotoColors.Orange
        else -> R.string.gps_poor to MotoColors.Red
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        gps.altitudeM?.let { Text(stringResource(R.string.altitude_fmt, it), fontSize = cappedSp(17f), maxLines = 1, fontWeight = FontWeight.Medium, color = muted) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(stringResource(label), fontSize = cappedSp(17f), fontWeight = FontWeight.Medium, color = muted, maxLines = 1)
        }
    }
}

private const val GPS_GOOD_M = 10
private const val GPS_FAIR_M = 30
