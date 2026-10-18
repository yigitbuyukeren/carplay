package dev.ygtlab.carplay.ui

import dev.ygtlab.carplay.data.LeanState
import dev.ygtlab.carplay.data.ObdState
import dev.ygtlab.carplay.data.ObdAlerts
import androidx.compose.foundation.layout.BoxScope
import dev.ygtlab.carplay.data.ObdStatus
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

private const val MAX_SPEED = 200f
private const val MAX_RPM = 10000f
private const val RPM_RED_FROM = 0.85f

@Composable
fun SpeedCard(
    gps: GpsUi,
    limit: Int?,
    lean: LeanState?,
    onResetLeanPeaks: () -> Unit,
    obd: ObdState? = null,
    onSos: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val fraction by animateFloatAsState(
        targetValue = (gps.speedKmh / MAX_SPEED).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "speed",
    )
    val engine = obd?.takeIf { it.status == ObdStatus.CONNECTED }
    val rpm = engine?.data?.rpm
    val rpmFraction by animateFloatAsState(
        targetValue = ((rpm ?: 0) / MAX_RPM).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 300f),
        label = "rpm",
    )
    val trackColor = LocalMotoPalette.current.track
    val rpmBase = LocalMotoPalette.current.text
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
            if (rpm != null) {
                // Thin rev arc inside the speed arc; turns red near the limiter.
                val rpmStroke = 8.dp.toPx()
                val rpmInset = stroke + 10.dp.toPx() + rpmStroke / 2
                val rpmSize = Size(size.width - 2 * rpmInset, size.height - 2 * rpmInset)
                val rpmColor = if (rpmFraction >= RPM_RED_FROM) MotoColors.Red else rpmBase.copy(alpha = 0.85f)
                rotate(150f) {
                    drawArc(trackColor, 0f, 240f, false, Offset(rpmInset, rpmInset), rpmSize, style = Stroke(rpmStroke, cap = StrokeCap.Round))
                    drawArc(MotoColors.Red.copy(alpha = 0.35f), 240f * RPM_RED_FROM, 240f * (1 - RPM_RED_FROM), false,
                        Offset(rpmInset, rpmInset), rpmSize, style = Stroke(rpmStroke))
                    if (rpmFraction > 0.004f) {
                        drawArc(rpmColor, 0f, 240f * rpmFraction, false, Offset(rpmInset, rpmInset), rpmSize, style = Stroke(rpmStroke, cap = StrokeCap.Round))
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                gps.speedKmh.toString(),
                fontSize = 92.sp,
                fontWeight = FontWeight.Bold,
                color = if (SpeedLimitMath.isOver(gps.speedKmh, limit)) MotoColors.Red else LocalMotoPalette.current.text,
            )
            Text(stringResource(R.string.speed_unit), fontSize = 22.sp, color = LocalMotoPalette.current.muted, fontWeight = FontWeight.Medium)
            if (rpm != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GearChip(engine.gear)
                    Text(stringResource(R.string.obd_rpm_fmt, rpm), fontSize = 16.sp, color = LocalMotoPalette.current.muted)
                }
            }
            if (!gps.hasFix) {
                Text(stringResource(R.string.gps_waiting), fontSize = 16.sp, color = MotoColors.Orange, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (lean != null) LeanGauge(lean, onResetLeanPeaks, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        if (engine != null) EngineCorners(engine)
        if (onSos != null) SosButton(onSos, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (limit != null) SpeedLimitSign(limit, Modifier.align(Alignment.TopEnd).padding(16.dp), size = 76.dp)
    }
}

/** Temperature and voltage (bottom left), trouble codes (bottom right). */
@Composable
private fun BoxScope.EngineCorners(engine: ObdState) {
    val palette = LocalMotoPalette.current
    val d = engine.data
    Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
        d.coolantC?.let { t ->
            val hot = t >= ObdAlerts.OVERHEAT_C
            Text("🌡 $t°", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (hot) MotoColors.Red else palette.text)
        }
        d.voltage?.let { v ->
            val bad = v < ObdAlerts.LOW_V && (d.rpm ?: 0) > 1000 || v > ObdAlerts.HIGH_V
            Text("🔋 " + String.format(Locale.getDefault(), "%.1fV", v), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = if (bad) MotoColors.Orange else palette.text)
        }
    }
    val codes = engine.codes.orEmpty()
    if (d.milOn || codes.isNotEmpty()) {
        Text(
            "⚠ " + (codes.firstOrNull() ?: stringResource(R.string.obd_codes_badge, d.codeCount)),
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MotoColors.Orange,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/** Estimated gear next to the revs; a dash until the gear table is learned. */
@Composable
private fun GearChip(gear: Int?) {
    val palette = LocalMotoPalette.current
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.cardHigh).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.obd_gear), fontSize = 10.sp, color = palette.muted, fontWeight = FontWeight.SemiBold)
        Text(gear?.toString() ?: "–", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.text)
    }
}
