package com.ridedeck.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Safe-drawing padding with the same width on both sides: a camera cutout on one edge of a landscape phone
 * would otherwise push centred content off the middle of the screen.
 */
@Composable
fun Modifier.symmetricSafePadding(): Modifier {
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val side = with(density) { maxOf(insets.getLeft(density, dir), insets.getRight(density, dir)).toDp() }
    val top = with(density) { insets.getTop(density).toDp() }
    val bottom = with(density) { insets.getBottom(density).toDp() }
    return padding(start = side, end = side, top = top.coerceAtLeast(0.dp), bottom = bottom.coerceAtLeast(0.dp))
}
