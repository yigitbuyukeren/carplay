package dev.ygtlab.carplay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.LeanState
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_SHOWN_DEG = 60f

/** Rear view of the bike tilting with the lean, the angle, and the peaks since the last reset. Tap to reset peaks. */
@Composable
fun LeanGauge(state: LeanState, onResetPeaks: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalMotoPalette.current
    if (!state.supported) return
    val angle = state.angleDeg.coerceIn(-MAX_SHOWN_DEG, MAX_SHOWN_DEG)
    val color = when {
        abs(angle) < 30 -> palette.text
        abs(angle) < 45 -> MotoColors.Orange
        else -> MotoColors.Red
    }
    Column(
        modifier = modifier.clickable(onClick = onResetPeaks),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!state.calibrated || !state.ready) {
            Text(
                stringResource(if (!state.calibrated) R.string.lean_needs_calibration else R.string.lean_learning),
                fontSize = 15.sp, color = MotoColors.Orange,
            )
            return@Column
        }
        Canvas(modifier = Modifier.size(width = 120.dp, height = 44.dp)) {
            val w = size.width
            val h = size.height
            val pivot = Offset(w / 2, h)
            // Ground line stays level; the bike tilts around the contact patch. Positive angle is left.
            drawLine(palette.track, Offset(0f, h - 2f), Offset(w, h - 2f), strokeWidth = 4f, cap = StrokeCap.Round)
            rotate(degrees = -angle, pivot = pivot) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w / 2 - 6.dp.toPx(), 0f),
                    size = Size(12.dp.toPx(), h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawLine(color, Offset(w / 2 - 22.dp.toPx(), 4.dp.toPx()), Offset(w / 2 + 22.dp.toPx(), 4.dp.toPx()),
                    strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "${state.maxLeftDeg.roundToInt()}°", fontSize = 15.sp, color = palette.muted,
                modifier = Modifier.width(52.dp),
            )
            Text(
                "${abs(angle).roundToInt()}°" + when {
                    angle > 1 -> " ◀"
                    angle < -1 -> " ▶"
                    else -> ""
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color,
            )
            Text(
                "${state.maxRightDeg.roundToInt()}°", fontSize = 15.sp, color = palette.muted,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}
