package dev.ygtlab.carplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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

private const val MAX_SPEED = 200f

@Composable
fun SpeedCard(gps: GpsUi, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = (gps.speedKmh / MAX_SPEED).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "speed",
    )
    val trackColor = LocalMotoPalette.current.track
    Box(modifier = modifier.card(), contentAlignment = Alignment.Center) {
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
                fontSize = 92.sp,
                fontWeight = FontWeight.Bold,
                color = LocalMotoPalette.current.text,
            )
            Text(stringResource(R.string.speed_unit), fontSize = 22.sp, color = LocalMotoPalette.current.muted, fontWeight = FontWeight.Medium)
            if (!gps.hasFix) {
                Text(stringResource(R.string.gps_waiting), fontSize = 16.sp, color = MotoColors.Orange, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
