package com.yuku.browser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The page is laid out UNDER the status bar (see `PageTopInset.setStrip`).
 * A card is still the page BOX — from a sticky header's top (the header is
 * held at the bar's edge) to the bottom of the screen. What is under the strip
 * is content scrolled up past that header, or the document's top padding, and
 * on screen it is only ever seen through the drawn strip; raw on a card it was
 * content over the header and a band of fill. The strip grows back in above
 * the box as the card zooms to full screen (`shrinkGeometry`), painted as the
 * screen paints it (`drawStatusStrip`), from `Tab.thumbnailTop`.
 */
internal object PageTopStrip {
    /** The strip's height in px: the switcher's ground reaches up under it. */
    var px by mutableFloatStateOf(0f)
}

/** Top-to-bottom mask with a flat upper fifth and a zero-slope lower tail.
 * Keep the pre-Android-12 CSS fallback in PageTopInset on the same curve.
 */
internal val StatusBarEffectStops = FloatArray(33) { it / 32f }
internal fun statusBarEffectStrength(position: Float): Float {
    val x = ((1f - position) / 0.8f).coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * The page's last measurement of what is under the status bar, as snapshot
 * state read only in DRAW: the header's share moves every frame of a header's
 * slide, and reading it in composition would recompose the page host per frame.
 * Colours ease between readings (a section boundary passing under the bar).
 */
@androidx.compose.runtime.Stable
internal class StatusStripPaint {
    val edge = androidx.compose.animation.Animatable(Color.White)
    val head = androidx.compose.animation.Animatable(Color.Transparent)
    /**
     * The header is either an opaque extension of the page or no extension at
     * all.  Keep the transition separate from its measured share: pages such
     * as Amazon replace a sticky filter header in one report while scrolling,
     * and drawing that report literally made the system-bar band pop.
     */
    val headFill = androidx.compose.animation.core.Animatable(0f)
    var share by mutableFloatStateOf(0f)
    var ramp by mutableFloatStateOf(0f)
    var on by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
internal fun rememberStatusStripPaint(
    reports: kotlinx.coroutines.flow.StateFlow<Map<Long, com.yuku.browser.core.StatusStripReport>>,
    tabId: Long,
): StatusStripPaint {
    val paint = remember(tabId) { StatusStripPaint() }
    androidx.compose.runtime.LaunchedEffect(tabId, reports) {
        val scope = this
        reports.collect { all ->
            val r = all[tabId]
            if (r == null) {
                // A page can lose its strip altogether (rather than sending a
                // final zero-share report). Leave the last paint installed
                // until its fill has faded away, otherwise this path still
                // produces a one-frame hard cut.
                if (paint.on && paint.headFill.targetValue != 0f) {
                    scope.launchStrip {
                        paint.headFill.animateTo(0f, STATUS_STRIP_MODE_TWEEN)
                        if (paint.headFill.targetValue == 0f) paint.on = false
                    }
                } else if (paint.headFill.value == 0f) {
                    paint.on = false
                }
                return@collect
            }
            val first = !paint.on
            paint.share = r.headShare
            paint.ramp = r.ramp
            val edge = Color(r.edge)
            val head = if (r.head != 0) Color(r.head) else paint.head.value
            if (first) {
                paint.edge.snapTo(edge)
                paint.head.snapTo(head)
            } else {
                if (paint.edge.targetValue != edge) {
                    scope.launchStrip { paint.edge.animateTo(edge, androidx.compose.animation.core.tween(200)) }
                }
                if (paint.head.targetValue != head) {
                    scope.launchStrip { paint.head.animateTo(head, STATUS_STRIP_COLOUR_TWEEN) }
                }
            }
            // The page reports this on each frame of a site's own header
            // transition. Do not start our wipe while that header is still
            // moving: wait for its listener to report it fully out of the
            // strip, then run one distinct native wipe.
            val fill = when {
                first -> if (r.head != 0) r.headShare else 0f
                r.head == 0 || r.headShare <= STATUS_HEADER_HIDDEN_SHARE -> 0f
                // A header returning from hidden may fill in normally.
                paint.headFill.targetValue <= STATUS_HEADER_HIDDEN_SHARE -> r.headShare
                // Likewise, keep following a header that is still entering.
                r.headShare > paint.headFill.targetValue -> r.headShare
                // A shrinking share is the site's animation; hold the native
                // fill until the report says the header is completely gone.
                else -> null
            }
            if (fill != null && first) {
                paint.headFill.snapTo(fill)
            } else if (fill != null && paint.headFill.targetValue != fill) {
                scope.launchStrip { paint.headFill.animateTo(fill, STATUS_STRIP_MODE_TWEEN) }
            }
            paint.on = true
        }
    }
    return paint
}

private fun kotlinx.coroutines.CoroutineScope.launchStrip(block: suspend () -> Unit) {
    launch { block() }
}

/**
 * The strip over the page, in the node's own space: the status bar is
 * `[-stripPx, 0)` above the page box. The veil fades from 35% at the
 * screen edge through explicit contrast stops to zero at the box's top,
 * without extending into the page.
 * [amount] is how much of it there is over the page BOX — 1 on a page that
 * fills the screen, 0 on a card, in between as one zooms into the other. The
 * part ABOVE the box is only uncovered by that same zoom (the shrink window
 * drops it on a card), so it is painted as the screen paints it whenever any
 * of it shows: the raw page under the bar is never seen, and nothing about
 * its colour changes when the zoom lands. [amount] scales the veil's opacity.
 *
 * A visible header owns the strip with its solid paint. Otherwise a light
 * contrast veil follows the page tone: white over a light page, black over a
 * dark one. It reaches transparency exactly at the status bar's bottom.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStatusStrip(
    paint: StatusStripPaint,
    stripPx: Float,
    amount: Float,
    softenWipeEdge: Boolean = true,
) {
    if (!paint.on || stripPx <= 0f) return
    val top = -stripPx
    val width = size.width
    val amountIn = amount.coerceIn(0f, 1f)
    // The reported edge already includes the page's dark-mode filter. Use
    // the same perceived-brightness threshold as StatusStripReport.dark:
    // linear-light luminance at 0.5 incorrectly treats light grey as dark.
    val edge = paint.edge.value
    val brightness = 0.2126f * edge.red + 0.7152f * edge.green + 0.0722f * edge.blue
    val veil = if (brightness < 0.5f) Color.Black else Color.White
    fun veilAt(alpha: Float) = veil.copy(alpha = alpha * amountIn)
    // Keep the transparent-state veil underneath while the header fades. That
    // makes this a real crossfade between the two modes, rather than a fade
    // to raw page pixels followed by a final veil pop.
    drawRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to veilAt(0.35f),
            0.50f to veilAt(0.25f),
            0.75f to veilAt(0.10f),
            0.90f to veilAt(0.05f),
            1f to veilAt(0f),
            startY = top,
            endY = 0f,
        ),
        Offset(0f, top),
        Size(width, stripPx),
    )
    val headFill = paint.headFill.value
    if (headFill > 0.003f) {
        // This is a wipe, not an opacity fade. As the header leaves, its
        // bottom edge travels upward through the status-bar band, exposing
        // the transparent-state veil behind it. A tiny soft edge prevents a
        // one-pixel seam without leaving a lingering line at the bottom.
        val wipeBottom = top + stripPx * headFill
        val softEdge = if (softenWipeEdge) {
            minOf(stripPx * (1f - headFill), stripPx * 0.18f)
        } else 0f
        val solidHeight = (wipeBottom - top - softEdge).coerceAtLeast(0f)
        if (solidHeight > 0f) {
            drawRect(paint.head.value, Offset(0f, top), Size(width, solidHeight))
        }
        if (softEdge > 0f) {
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to paint.head.value,
                    1f to Color.Transparent,
                    startY = wipeBottom - softEdge,
                    endY = wipeBottom,
                ),
                Offset(0f, wipeBottom - softEdge),
                Size(width, softEdge),
            )
        }
    }
}

private const val STATUS_HEADER_HIDDEN_SHARE = 0.003f

/** A brief native-like crossfade for a sticky header entering or leaving. */
private val STATUS_STRIP_MODE_TWEEN = androidx.compose.animation.core.tween<Float>(120)
private val STATUS_STRIP_COLOUR_TWEEN = androidx.compose.animation.core.tween<Color>(120)

/** SwipeRefreshLayout's own spinner placement, restated to offset it (dp). */
internal const val SPINNER_DIAMETER_DP = 40
internal const val SPINNER_REST_DP = 64

/** Both effects end at the status bar's bottom, without page overspill. */
internal val STATUS_STRIP_FADE = 0.dp

/** The preview's top row, averaged across it — the colour its strip extends. */
internal fun Bitmap.topEdgeColour(): Color {
    if (isRecycled || width == 0 || height == 0) return Color.White
    val samples = 9
    var r = 0
    var g = 0
    var b = 0
    for (i in 0 until samples) {
        val pixel = getPixel((width - 1) * i / (samples - 1), 0)
        r += android.graphics.Color.red(pixel)
        g += android.graphics.Color.green(pixel)
        b += android.graphics.Color.blue(pixel)
    }
    return Color(r / samples, g / samples, b / samples)
}

/**
 * A tab's preview as a card shows it: the page box alone, COVERING the card
 * (`ContentScale.Crop`'s rule). [centred] crops it about its centre, which is
 * the window the live page shrinks through; otherwise it is pinned to the top.
 */
@Composable
internal fun PagePreviewImage(
    bitmap: Bitmap,
    centred: Boolean,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Spacer(
        modifier.drawWithCache {
            val bw = image.width.toFloat()
            val bh = image.height.toFloat()
            val scale = if (bw > 0f && bh > 0f) {
                maxOf(size.width / bw, size.height / bh)
            } else 1f
            val dw = (bw * scale).roundToInt()
            val left = ((size.width - dw) / 2f).roundToInt()
            val imageTop = if (centred) ((size.height - bh * scale) / 2f).roundToInt() else 0
            val imageH = (bh * scale).roundToInt()
            onDrawBehind {
                clipRect {
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(left, imageTop),
                        dstSize = IntSize(dw, imageH),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        },
    )
}
