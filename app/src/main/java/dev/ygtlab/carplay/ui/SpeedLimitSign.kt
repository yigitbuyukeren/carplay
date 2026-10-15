package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** European-style round speed limit sign: white disc, red ring, black number. */
@Composable
fun SpeedLimitSign(limit: Int, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(size * 0.09f, Color(0xFFE30613), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            limit.toString(),
            fontSize = (size.value * if (limit >= 100) 0.30f else 0.38f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
    }
}
