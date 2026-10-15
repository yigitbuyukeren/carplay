package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MotoColors {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val CardHigh = Color(0xFF2C2C2E)
    val Blue = Color(0xFF0A84FF)
    val Cyan = Color(0xFF64D2FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Gray = Color(0xFF8E8E93)
}

@Composable
fun MotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = MotoColors.Background,
            surface = MotoColors.Card,
            primary = MotoColors.Blue,
        ),
        content = content,
    )
}

fun Modifier.card(shape: Int = 28): Modifier =
    clip(RoundedCornerShape(shape.dp)).background(
        Brush.verticalGradient(listOf(MotoColors.CardHigh, MotoColors.Card))
    )
