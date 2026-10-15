package dev.ygtlab.carplay.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.util.NAV_APPS
import dev.ygtlab.carplay.util.isInstalled
import dev.ygtlab.carplay.util.launchPackage

@Composable
fun NavCard(context: Context, modifier: Modifier = Modifier) {
    val installed = remember { NAV_APPS.filter { context.isInstalled(it.packageName) } }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF15202B))
            .border(1.dp, Color(0x26FFFFFF), shape),
    ) {
        MapBackdrop()
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0xCC000000)))
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Navigasyon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (installed.isEmpty()) {
                Text("Harita uygulaması bulunamadı", fontSize = 14.sp, color = MotoColors.Gray)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    installed.take(2).forEach { app ->
                        PillButton(
                            app.label,
                            if (app.packageName == "com.waze") Color(0xFF2AA9E0) else MotoColors.Green,
                            onClick = { context.launchPackage(app.packageName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Stylised street grid with a route and a position marker — evokes the CarPlay map tile. */
@Composable
private fun MapBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minor = Color(0xFF1E2C3A)
        val major = Color(0xFF2B3E52)
        val thin = 6.dp.toPx()
        val thick = 12.dp.toPx()
        listOf(0.18f, 0.5f, 0.82f).forEach { drawLine(minor, Offset(0f, h * it), Offset(w, h * it), thin) }
        listOf(0.15f, 0.42f, 0.7f, 0.92f).forEach { drawLine(minor, Offset(w * it, 0f), Offset(w * it, h), thin) }
        drawLine(major, Offset(0f, h * 0.7f), Offset(w, h * 0.3f), thick, StrokeCap.Round)
        drawLine(major, Offset(w * 0.3f, h), Offset(w * 0.62f, 0f), thick, StrokeCap.Round)

        val route = Path().apply {
            moveTo(w * 0.5f, h * 0.62f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.2f)
        }
        drawPath(route, MotoColors.Blue, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 13.dp.toPx(), Offset(w * 0.5f, h * 0.62f))
        drawCircle(MotoColors.Blue, 9.dp.toPx(), Offset(w * 0.5f, h * 0.62f))
        drawCircle(MotoColors.Red, 8.dp.toPx(), Offset(w * 0.7f, h * 0.2f))
    }
}
