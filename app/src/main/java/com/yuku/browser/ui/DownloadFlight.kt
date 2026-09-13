package com.yuku.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.util.lerp
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.Icon
import com.yuku.browser.ui.theme.SpecialCircle
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.bevel98If

/** The whole flight, card to button. */
private const val FLIGHT_MS = 760

/** How far above the card's badge the icon lifts before it falls. */
private val FLIGHT_HOP = 48.dp

/**
 * How big the badge is when it goes into the button, as a fraction of its
 * size on the card — about the chevron's own size, so it reads as going IN.
 */
private const val FLIGHT_END_SCALE = 0.45f

/**
 * Where a composable was last placed — held OUTSIDE snapshot state, because
 * it is read every frame from a `graphicsLayer` block and writing it must not
 * recompose anything. The coordinates are live: asking them for a position
 * later answers with wherever the node is now, layer translations included,
 * which is what lets the flight land on a toolbar still sliding back in.
 */
class PlacedNode {
    var coordinates: LayoutCoordinates? = null
}

/**
 * One confirmed download's icon on its way into the menu button, starting at
 * [originInWindow] — the centre of the confirmation card's badge. A plain
 * class, not a data class: two downloads confirmed from the same spot are two
 * flights, and a `remember(flight)` keyed on equality would not restart.
 */
class DownloadFlight(val originInWindow: Offset)

/**
 * The accent disc with a download arrow on it: the confirmation card's
 * header, and the thing that leaves the card on a yes. One composable for
 * both, so the icon that flies is the icon that was on the card.
 */
@Composable
internal fun DownloadBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(SpecialCircle)
            .background(AccentColor)
            .bevel98If()
            // A bead of glass in the accent's own colour. Half the badge's
            // own 48dp as a corner is a circle, which is what it is clipped
            // to — the glass has to be told, since a draw modifier cannot
            // ask the node for its shape.
            .aeroGlassIf(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The download's badge dropping off the confirmation card into the toolbar's
 * menu button, which is where its progress is then drawn.
 *
 * It starts exactly where the card's own badge was — the card hides its copy
 * and fades out under it, so what the eye follows is the one icon leaving.
 * The path is a quadratic Bézier with its control point above the start,
 * walked at a LINEAR rate — which is exactly a projectile under gravity: a
 * short lift off the card, a stall at the top, and a fall that gathers speed
 * all the way into the button. No easing is laid on top; the curve already is
 * the physics. It shrinks as it falls, mostly near the end, so it reads as
 * going IN rather than landing ON the button, and fades over the last stretch.
 *
 * Nothing here takes a touch: the overlay has no pointer handling, so the
 * page stays live under the flight.
 */
@Composable
fun DownloadFlightOverlay(flight: DownloadFlight, target: PlacedNode, onLanded: () -> Unit) {
    val hop = with(LocalDensity.current) { FLIGHT_HOP.toPx() }
    val landed by rememberUpdatedState(onLanded)
    val t = remember(flight) { Animatable(0f) }
    LaunchedEffect(flight) {
        t.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
        landed()
    }
    val self = remember { PlacedNode() }

    Box(Modifier.fillMaxSize().onGloballyPositioned { self.coordinates = it }) {
        DownloadBadge(
            Modifier.graphicsLayer {
                // Read FIRST, before anything can return early. A layer block
                // re-runs only when a state it READ changes, and on its first
                // run this node's coordinates do not exist yet (placement
                // comes before the positioned callbacks): returning before
                // reading `t` left a block that read nothing, never ran
                // again, and held the badge at alpha 0 for the whole flight.
                val p = t.value
                val here = self.coordinates?.takeIf { it.isAttached }
                val button = target.coordinates?.takeIf { it.isAttached }
                if (here == null || button == null) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val start = here.windowToLocal(flight.originInWindow)
                val end = here.localPositionOf(button, button.size.center.toOffset())
                val control = Offset(
                    lerp(start.x, end.x, 0.15f),
                    (start.y - hop).coerceAtLeast(0f),
                )
                val q = 1f - p
                val x = q * q * start.x + 2f * q * p * control.x + p * p * end.x
                val y = q * q * start.y + 2f * q * p * control.y + p * p * end.y
                translationX = x - size.width / 2f
                translationY = y - size.height / 2f

                val scale = lerp(1f, FLIGHT_END_SCALE, p * p)
                scaleX = scale
                scaleY = scale
                alpha = 1f - ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
            },
        )
    }
}
