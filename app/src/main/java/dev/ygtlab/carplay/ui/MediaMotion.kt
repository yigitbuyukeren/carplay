package dev.ygtlab.carplay.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * The cover changes like a page turn: after "next" the old one leaves to the left and the new one comes
 * in from the right, after "previous" the other way round. [direction] is +1 for next, -1 for previous.
 */
fun trackTransition(direction: Int): ContentTransform =
    (slideInHorizontally(tween(380)) { it * direction } + fadeIn(tween(380))) togetherWith
        (slideOutHorizontally(tween(380)) { -it * direction } + fadeOut(tween(260)))
