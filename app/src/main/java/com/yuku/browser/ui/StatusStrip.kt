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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
                paint.on = false
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
                    scope.launchStrip { paint.head.animateTo(head, androidx.compose.animation.core.tween(200)) }
                }
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
 * `[-stripPx, 0)` above the page box and the fade runs `fadePx` into it.
 * [amount] is how much of it there is over the page BOX — 1 on a page that
 * fills the screen, 0 on a card, in between as one zooms into the other. The
 * part ABOVE the box is only uncovered by that same zoom (the shrink window
 * drops it on a card), so it is painted as the screen paints it whenever any
 * of it shows: the raw page under the bar is never seen, and nothing about
 * its colour changes when the zoom lands. The veil eases from full at the
 * screen's edge to [amount] at the box's top, so it has no seam there.
 *
 * Three looks, never a switch: the VEIL (the ground at the edge, strongest at
 * the screen's edge and eased out below the bar — content stays faintly
 * visible, Safari's scroll edge), the CAP (solid, only at the page's top,
 * where the bar is over the page's own blank padding) and the HEAD (a header's
 * paint, at the share of the header on screen; the veil gives way to it).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStatusStrip(
    paint: StatusStripPaint,
    stripPx: Float,
    fadePx: Float,
    amount: Float,
) {
    if (!paint.on || stripPx <= 0f) return
    val a = amount.coerceIn(0f, 1f)
    val top = -stripPx
    val height = stripPx + fadePx
    val width = size.width
    val edge = paint.edge.value
    val share = paint.share
    val veil = 1f - share
    if (veil > 0.003f) {
        drawIntoCanvas { canvas ->
            // Native, for the dither: a short, faint ramp over a flat page is
            // exactly where 8-bit steps show, and Compose's paint has none.
            val hold = (stripPx / height) * VEIL_HOLD
            val colors = IntArray(VEIL_STOPS + 2)
            val positions = FloatArray(VEIL_STOPS + 2)
            // Full above the box, [a] from its top edge down.
            fun reach(pos: Float): Float {
                val y = top + height * pos
                return if (y >= 0f) a else a + (1f - a) * (-y / stripPx).coerceIn(0f, 1f)
            }
            fun alphaAt(pos: Float): Float {
                val u = ((pos - hold) / (1f - hold)).coerceIn(0f, 1f)
                val e = u * u * u * (u * (u * 6f - 15f) + 10f)
                return VEIL_ALPHA * (1f - e) * veil * reach(pos)
            }
            colors[0] = edge.copy(alpha = VEIL_ALPHA * veil).toArgb()
            for (i in 0 until VEIL_STOPS) {
                val u = i / (VEIL_STOPS - 1f)
                positions[i + 1] = hold + (1f - hold) * u
            }
            // A stop exactly on the box's top edge, where the reach turns.
            positions[VEIL_STOPS + 1] = stripPx / height
            positions.sort(1)
            for (i in 1 until positions.size) colors[i] = edge.copy(alpha = alphaAt(positions[i])).toArgb()
            val native = android.graphics.Paint().apply {
                isDither = true
                shader = android.graphics.LinearGradient(
                    0f, top, 0f, top + height, colors, positions, android.graphics.Shader.TileMode.CLAMP,
                )
            }
            canvas.nativeCanvas.drawRect(0f, top, width, top + height, native)
        }
    }
    // Both lie wholly above the box: seen only where the zoom uncovers them.
    val cap = 1f - paint.ramp
    if (cap > 0.003f) drawRect(edge, Offset(0f, top), Size(width, stripPx), alpha = cap)
    val head = share
    if (head > 0.003f) drawRect(paint.head.value, Offset(0f, top), Size(width, stripPx), alpha = head)
}

private const val VEIL_ALPHA = 0.72f
private const val VEIL_HOLD = 0.25f
private const val VEIL_STOPS = 13

/**
 * The strip's softening: a blur of the page itself under the status bar,
 * masked by the same eased ramp, as a RenderEffect on the WebView VIEW. On the
 * view rather than in the page (a `backdrop-filter` was in every capture) and
 * rather than on a Compose layer (a RenderEffect is cut to its layer, and the
 * strip hangs above the page box): the view spans the strip, and a capture
 * draws the view's content, never its own node's effect.
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
internal class StatusBlur {
    private val shaders = runCatching {
        android.graphics.RuntimeShader(BLUR_MASK) to android.graphics.RuntimeShader(BLUR_MASK)
    }.onFailure { android.util.Log.e("StatusBlur", "Shader compilation failed", it) }.getOrNull()

    fun effect(stripPx: Float, fadePx: Float, amount: Float, density: Float): android.graphics.RenderEffect? {
        val (sharp, soft) = shaders ?: return null
        if (amount <= 0f || stripPx <= 0f) return null
        for ((shader, keep) in listOf(sharp to 1f, soft to 0f)) {
            shader.setFloatUniform("strip", stripPx)
            shader.setFloatUniform("fade", fadePx)
            shader.setFloatUniform("amount", amount)
            shader.setFloatUniform("keep", keep)
        }
        val radius = BLUR_DP * density
        val glass = android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(soft, "content"),
            android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP),
        )
        return android.graphics.RenderEffect.createBlendModeEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(sharp, "content"),
            glass,
            android.graphics.BlendMode.PLUS,
        )
    }
}

private const val BLUR_DP = 6f

// y is the view's own, whose top is the screen's while the strip is on.
private const val BLUR_MASK = """
    uniform shader content;
    uniform float strip;
    uniform float fade;
    uniform float amount;
    uniform float keep;
    float mask(float y) {
        float h = strip + fade;
        float hold = strip * 0.4;
        if (y <= hold) return 0.8 * amount;
        float u = clamp((y - hold) / max(h - hold, 1.0), 0.0, 1.0);
        float e = u * u * u * (u * (u * 6.0 - 15.0) + 10.0);
        return 0.8 * (1.0 - e) * amount;
    }
    half4 main(float2 p) {
        float m = mask(p.y);
        return content.eval(p) * half(keep > 0.5 ? 1.0 - m : m);
    }
"""

/** SwipeRefreshLayout's own spinner placement, restated to offset it (dp). */
internal const val SPINNER_DIAMETER_DP = 40
internal const val SPINNER_REST_DP = 64

/** How far below the status bar's edge the strip fades out. */
internal val STATUS_STRIP_FADE = 24.dp

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
