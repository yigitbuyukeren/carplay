package dev.ygtlab.carplay.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.RoundaboutRight
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.UTurnRight
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps Valhalla manoeuvre type codes to an arrow icon. */
fun maneuverIcon(type: Int): ImageVector = when (type) {
    4, 5, 6 -> Icons.Rounded.Flag
    9, 18, 20, 23 -> Icons.Rounded.TurnSlightRight
    10 -> Icons.Rounded.TurnRight
    11 -> Icons.Rounded.TurnSharpRight
    12 -> Icons.Rounded.UTurnRight
    13 -> Icons.Rounded.UTurnLeft
    14 -> Icons.Rounded.TurnSharpLeft
    15 -> Icons.Rounded.TurnLeft
    16, 19, 21, 24 -> Icons.Rounded.TurnSlightLeft
    25 -> Icons.Rounded.Merge
    26, 27 -> Icons.Rounded.RoundaboutRight
    else -> Icons.Rounded.Straight
}
