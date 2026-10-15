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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.GpsUi

private const val MAX_SPEED = 200f

@Composable
fun SpeedCard(gps: GpsUi, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = (gps.speedKmh / MAX_SPEED).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "speed",
    )
    Box(modifier = modifier.card(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(20.dp)
        ) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val brush = Brush.sweepGradient(
                0f to MotoColors.Cyan,
                0.28f to MotoColors.Blue,
                0.5f to MotoColors.Orange,
                0.667f to MotoColors.Red,
                1f to MotoColors.Red,
            )
            rotate(150f) {
                drawArc(Color(0x22FFFFFF), 0f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0.004f) {
                    drawArc(brush, 0f, 240f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                gps.speedKmh.toString(),
                fontSize = 108.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text("km/sa", fontSize = 22.sp, color = MotoColors.Gray, fontWeight = FontWeight.Medium)
            if (!gps.hasFix) {
                Text("GPS bekleniyor…", fontSize = 16.sp, color = MotoColors.Orange, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
