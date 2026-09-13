package com.yuku.browser.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.yuku.browser.ui.theme.LocalPrivacy
import kotlin.math.round
import kotlin.math.sqrt

/**
 * The face of the private space — used everywhere the ordinary space would
 * show [EmptyFace], so the two are the same watermark in two moods rather
 * than two different ideas.
 */
internal const val PrivateFace = "/ᐠ_ ꞈ _ᐟ\\"

/**
 * How far the previews are taken past legibility, measured ON SCREEN — a card
 * is roughly two fifths of the screen across, so this is a smear over a good
 * fraction of it. Deliberately well beyond "soft": at a card's size a mild
 * blur still leaves a page's layout, its colours and often its headline
 * readable across a room, which is the exact thing a private tab's preview
 * must not do. What survives is the page's colour, which is enough to tell one
 * card from another.
 *
 * It was 36dp, and came down because of [privateShrinkBlur]: the shrinking
 * page has to ask for this radius divided by however far it has shrunk (see
 * there), so what is a comfortable sigma here is a punishing one at the far
 * end of the drag, and RenderEffect's blur visibly degrades — it downsamples
 * by more and more as sigma grows, and the level it picks shifting from frame
 * to frame is what a slow drag showed as patches of the image pulsing in
 * brightness. Both paths read this one value, so they still agree exactly at
 * the handoff.
 */
private val PreviewBlur = 20.dp

/**
 * The shrink blur's radius is rounded to a multiple of this many pixels. The
 * artifact above is driven by the radius CHANGING, not by its size: holding it
 * still for a run of frames holds the pattern still with it, and the movement
 * the eye follows is the scale, which stays perfectly smooth. Small enough
 * that the steps themselves aren't visible at these radii.
 */
private const val BLUR_QUANTUM_PX = 6f

/**
 * Hides a private tab's preview: the switcher still shows the card, but not
 * what is on the page. The radius rides [LocalPrivacy], so the previews go
 * out of focus on the same curve the chrome turns violet on, and come back
 * as it drains away.
 *
 * `Modifier.blur` is a RenderEffect, which exists only from API 31 — below
 * that it is silently a no-op, and a no-op here would show the page in full.
 * So the older path drops the preview altogether rather than half-hiding it,
 * and does it outright rather than over 420ms: a fade would be 420ms of
 * exactly the thing being hidden. The card's own dark background is what's
 * left, which is what an uncaptured tab looks like anyway.
 */
@Composable
fun Modifier.privatePreview(private: Boolean): Modifier = when {
    !private -> this
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> this.alpha(0f)
    else -> {
        // sqrt, not the raw progress: on the way IN this is 420ms during
        // which a private page is on screen at less than full blur, so the
        // curve is front-loaded — half the radius by a quarter of the way
        // through, unreadable well before the violet has finished arriving,
        // while still reading as one movement with the chrome.
        val radius = PreviewBlur * sqrt(LocalPrivacy.current)
        // A zero radius is not a no-op blur — it's an error — and mid-turn
        // the progress passes through values too small to round to a pixel.
        // Rectangle, not Unbounded: the blur is sampled inside the card's own
        // clipped bounds, so the edges stay solid to the corners instead of
        // fading out and leaving a lighter frame around the picture.
        if (radius > 0.5.dp) this.blur(radius, BlurredEdgeTreatment.Rectangle) else this
    }
}

/**
 * The same hiding, applied to a private tab's content while it is between
 * fullscreen and its switcher card: the page goes out of focus as it shrinks
 * and comes back into focus as it is drawn back out, so the blur is part of
 * the movement rather than something that snaps on at the end of it.
 *
 * Called from inside a `graphicsLayer` block, AFTER `applyShrinkTransform` —
 * it reads the scale that set, for the same reason the corner radius
 * compensates for it: a RenderEffect is applied in the layer's own coordinate
 * space, BEFORE the scale, so a fixed radius would arrive on screen multiplied
 * by however far the card has shrunk and land visibly softer than the card it
 * hands off to. Dividing it back out makes the two agree exactly at the
 * handoff.
 *
 * Linear in [shrink], not front-loaded like the mode turn's curve: this is a
 * gesture the user is driving, so it should track the finger. There's no
 * exposure to cut short either — at the sharp end of it the page is fullscreen
 * and they are looking straight at it.
 *
 * The radius is quantised (see [BLUR_QUANTUM_PX]) rather than followed
 * exactly, which is what keeps the blur from shimmering as it moves.
 *
 * Below API 31 there is no RenderEffect and so no blur here at all; the card
 * this lands on hides its preview outright instead (see [privatePreview]).
 */
fun GraphicsLayerScope.privateShrinkBlur(private: Boolean, shrink: Float) {
    if (!private || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        renderEffect = null
        return
    }
    val wanted = PreviewBlur.toPx() * shrink.coerceIn(0f, 1f) / scaleX.coerceAtLeast(0.0001f)
    val radius = round(wanted / BLUR_QUANTUM_PX) * BLUR_QUANTUM_PX
    // A zero radius isn't a no-op blur, it's an error — and at the fullscreen
    // end this passes through values too small to round to a pixel.
    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
}
