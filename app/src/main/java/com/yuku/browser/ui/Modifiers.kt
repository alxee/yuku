package com.yuku.browser.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fade text to transparency at its edges instead of ellipsizing.
 *
 * There is no reliable built-in for this on Android — `ellipsize` is what we're
 * replacing and `requiresFadingEdge` only engages under scrolling. So: render the
 * content offscreen, then punch a gradient through the alpha channel with DstIn.
 *
 * Apply to a container with a settled width, never to a wrap-content Text, or the
 * fade eats real characters instead of empty space.
 *
 * Both the gradient and the modifier chain itself are cached, and deliberately
 * so: this is applied per row in lists that can be several dozen rows long, and
 * a `Brush.horizontalGradient` built inside the draw lambda compiles a fresh
 * shader on every draw of every row. [drawWithCache] rebuilds it only when the
 * measured width actually changes, and remembering the whole `Modifier` keeps
 * that cache alive across recompositions instead of discarding it along with
 * the lambda identity it's keyed on.
 */
@Composable
fun fadeEdges(edge: Dp = 10.dp, bothSides: Boolean = true): Modifier =
    remember(edge, bothSides) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                // CacheDrawScope is itself a Density, so this needs no
                // composition-side LocalDensity read — and it re-runs on a
                // size or density change, which is exactly when edgePx or the
                // gradient stops being valid.
                if (size.width <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
                val f = (edge.toPx() / size.width).coerceIn(0f, 0.5f)
                val brush = if (bothSides) {
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        f to Color.Black,
                        1f - f to Color.Black,
                        1f to Color.Transparent,
                    )
                } else {
                    Brush.horizontalGradient(
                        0f to Color.Black,
                        1f - f to Color.Black,
                        1f to Color.Transparent,
                    )
                }
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = brush, blendMode = BlendMode.DstIn)
                }
            }
    }

/**
 * A sheet's width. Full-bleed in portrait, HALF the screen — centred — in
 * landscape.
 *
 * A bottom sheet is anchored to one edge and sized to the reach of a thumb on
 * that edge; a phone turned sideways has the same thumb and twice the width, so
 * a full-bleed sheet there is a strip of rows with a screen's worth of empty
 * surface beside each one, and the eye crosses the whole display to read a
 * label and its switch. Half is the same sheet at the same rest height with its
 * rows back at a readable measure.
 *
 * A minimum, not a fraction alone: on a small landscape screen half of it is
 * narrower than the sheet's own content wants, and a sheet whose rows wrap is
 * worse than one that is a little wide. The maximum is the screen, so the floor
 * can never make it overhang.
 *
 * The two rounded bottom corners are off the bottom edge of the window either
 * way (see the sheets' own layout modifiers), so narrowing only ever exposes
 * the two at the top, which are the ones the shape is for.
 */
@Composable
fun sheetWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Modifier.width((screenWidth / 2).coerceIn(SHEET_MIN_WIDTH.coerceAtMost(screenWidth), screenWidth))
    } else {
        Modifier.fillMaxWidth()
    }
}

private val SHEET_MIN_WIDTH = 360.dp
