package com.yuku.browser.ui

import com.yuku.browser.ui.theme.tuiBloomIf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuku.browser.ui.theme.accentWash
import com.yuku.browser.ui.theme.EmptyBg
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.LocalPrivacy

/** The face that stands in for the old "204" headline. */
internal const val EmptyFace = "≽(◉˕◉≼マ"

/**
 * The watermark itself: the space's own face over its caption, centered in
 * whatever box it's given.
 *
 * A dim wash of the current accent for both lines — a watermark, not a
 * message: legible only if you're looking for it, in either theme, but tinted
 * so the picked system color still shows through here. Through [accentWash],
 * so the one theme whose canvas has to stay monochrome draws it in ink.
 *
 * @param private swaps in the private space's own face, and drops the caption
 * with it: in there the line is the only thing on screen saying anything at
 * all, which is the wrong note for the private space to end on. Same
 * watermark, different cat, so which space an empty screen belongs to is
 * legible from the screen itself.
 */
@Composable
private fun Watermark(private: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PlaceholderBlock(
            face = if (private) PrivateFace else EmptyFace,
            faceColor = accentWash(0.18f),
            caption = if (private) null else "nothing to see here",
            captionColor = accentWash(0.18f),
            modifier = Modifier.clearing(EmptyBg),
        )
    }
}

/**
 * Clears the Nothing theme's dot field out from under the watermark — the one
 * place a mark is drawn straight onto the empty canvas rather than onto a
 * surface laid over it.
 *
 * The face and its caption are a thin wash (18% of the ink), so the grid runs
 * visibly THROUGH them: at that alpha the dots are as strong as the strokes
 * they cross, and a cat with holes bored through its own outline reads as a
 * printing fault rather than as a watermark on printed paper. Paper has a
 * clearing where something is printed on it.
 *
 * Drawn as a soft radial wash of the ground colour rather than as a rect or a
 * rounded box, and that is the whole trick: a hard-edged patch would slice
 * whichever dots it crossed in half, which is the same cropped-grid complaint
 * one step in from the screen's edge. Fading the ground back in over half the
 * clearing's radius makes each dot near the boundary dim out whole instead.
 * The wash extends past the block's own bounds (nothing clips it here) so the
 * clearing has a margin around the mark, and it is squashed to the block's
 * proportions so a wide, short line of text does not clear a circle the
 * height of the screen.
 *
 * Every other theme is left alone: the grain has no structure for a mark to
 * cut across, and 98's canvases carry no texture at all.
 */
@Composable
private fun Modifier.clearing(ground: Color): Modifier {
    if (!LocalNothing.current) return this
    return this.drawBehind {
        val rx = size.width / 2f + 28.dp.toPx()
        val ry = size.height / 2f + 28.dp.toPx()
        val radius = maxOf(rx, ry)
        val brush = Brush.radialGradient(
            0f to ground,
            0.5f to ground,
            1f to ground.copy(alpha = 0f),
            center = center,
            radius = radius,
        )
        scale(rx / radius, ry / radius, pivot = center) {
            drawCircle(brush = brush, radius = radius, center = center)
        }
    }
}

/**
 * Shown when every tab is closed. The search sheet opens over the top of it.
 *
 * Deliberately the same watermark, in the same place, on the same ground as
 * [SwitcherBackdrop] — see there.
 */
@Composable
fun EmptyState(
    private: Boolean = false,
    // This composable is hosted inside the Scaffold's top padding. Grow its
    // canvas back through that inset so the system status bar is transparent
    // over the very same surface, then pad only the watermark back down.
    topInset: Dp = 0.dp,
    // How much of the bottom the toolbar covers. The GROUND runs to the
    // screen's edge under it — a translucent bar (Aero) shows whatever is
    // behind it, and a ground that stopped at the bar's top left a band of
    // page colour there — while the watermark stays centred in the space
    // above the bar, where it always was.
    bottomInset: Dp = 0.dp,
) {
    val topInsetPx = with(LocalDensity.current) { topInset.roundToPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val height = constraints.maxHeight + topInsetPx
                val placeable = measurable.measure(
                    constraints.copy(minHeight = height, maxHeight = height),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, -topInsetPx)
                }
            }
            .grainedBackground(EmptyBg, dotSpacing = EMPTY_DOT_SPACING),
    ) {
        Box(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).tuiBloomIf()) {
            Watermark(private = private)
        }
    }
}

/**
 * The tab switcher's own ground, behind the cards.
 *
 * Drawn under the LAST card as it leaves rather than after it is gone, which
 * is the whole point of it: closing that card (or swiping the gathered pile
 * away) is the switcher EMPTYING OUT, not a move to another screen, so the
 * face has to be something the card was sitting on rather than something that
 * arrives once it goes. The switcher's ground is [EmptyBg] for the same
 * reason (see `SwitcherBg`), and both surfaces center the watermark in the
 * same box — the screen above the toolbar — so nothing shifts at the handoff.
 *
 * @param visible is the one rule this obeys: while there is any chance the
 * tab STAYS, the face is not shown. So it is down for a row of cards
 * (obviously), down for the last card sitting there, and down through every
 * gesture on that last card — a swipe that may yet snap back, a tabs-button
 * drag that ends up putting the page back on screen — and it comes up only
 * once a close has actually COMMITTED, i.e. on the flick past the threshold
 * or the pile's own release, for the length of the fly-away. Anything softer
 * shows the empty screen to a user who is not about to get one.
 *
 * Instant, with no fade: it goes up on the frame the card commits, while the
 * card is still over it, and what should read as the reveal is that card
 * leaving. A fade would be the face arriving on its own behind it.
 */
@Composable
fun SwitcherBackdrop(visible: Boolean) {
    if (!visible) return
    val privacy = LocalPrivacy.current
    if (privacy < 1f) Watermark(private = false, modifier = Modifier.alpha(1f - privacy))
    if (privacy > 0f) Watermark(private = true, modifier = Modifier.alpha(privacy))
}
