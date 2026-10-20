package com.ridedeck.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * The cover changes like a page turn: after "next" the old one leaves to the left and the new one comes
 * in from the right, after "previous" the other way round. [direction] is +1 for next, -1 for previous.
 */
fun trackTransition(direction: Int): ContentTransform =
    // Slides only, both over the same time: a fade that ends sooner leaves a gap between the two covers.
    slideInHorizontally(tween(SLIDE_MS)) { it * direction } togetherWith
        slideOutHorizontally(tween(SLIDE_MS)) { -it * direction }

private const val SLIDE_MS = 380

/**
 * The album cover with its page-turn slide. The slide is keyed on the track (title and artist) only:
 * music apps re-send the metadata, with a fresh copy of the picture each time, several times while a
 * track changes, and keying on the picture made the cover slide back and forth for each of them. A
 * picture that arrives late for the same track just fades in. Each track keeps its own picture, so the
 * cover that is sliding out never shows the new one.
 */
@Composable
fun TrackCover(title: String, artist: String, art: Bitmap?, direction: () -> Int, iconSize: Dp) {
    val key = title to artist
    val arts = remember { LinkedHashMap<Pair<String, String>, Bitmap?>() }
    if (art != null || key !in arts) {
        arts[key] = art
        while (arts.size > KEPT_COVERS) arts.remove(arts.keys.first { it != key })
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { trackTransition(direction()) },
        label = "cover",
    ) { shown ->
        Crossfade(arts[shown], animationSpec = tween(300), label = "coverArt") { picture ->
            if (picture != null) {
                Image(picture.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = LocalMotoPalette.current.muted, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

private const val KEPT_COVERS = 3
