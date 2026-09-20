package com.yuku.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How far in from each edge of the page the bend begins when there is no
 * navigation bar to measure it by. Normally the depth is measured off the
 * navigation bar's own height (see BrowserScreen).
 */
internal val PAGE_LENS_DEPTH = 25.dp

/** How much deeper than that measure (or [PAGE_LENS_DEPTH]) the bend reaches. */
internal const val PAGE_LENS_SCALE = 1.5f

/**
 * The squeeze: the page's slope at the very edge is 1 + 3·SQUEEZE. It only
 * ever increases from 1 at the bend line, so the band shows MORE page than it
 * has room for — [PAGE_LENS_REACH] bend depths of it past the edge.
 */
internal const val PAGE_LENS_SQUEEZE = 0.75f

/** Red squeezes this much more than green, blue this much less. */
internal const val PAGE_LENS_DISPERSION = 0.45f

/**
 * Keystone: bent content is drawn in toward the middle as it curves away,
 * reaching 1 + SKEW at the very edge. Small on purpose — what it pulls in from
 * past the screen's sides is nothing, drawn black, and at this size that only
 * ever falls inside the corners' own arcs.
 */
internal const val PAGE_LENS_SKEW = 0.06f

/**
 * How far past each visible edge the curve reads page, in bend depths: the
 * squeeze at the edge, for the channel that squeezes most. BrowserScreen hangs
 * the WebView this far above the page's box so that page is real (above only —
 * see [PageLens]).
 */
internal const val PAGE_LENS_REACH = PAGE_LENS_SQUEEZE * (1f + PAGE_LENS_DISPERSION)

/** Width of the fade into the bezel along the top and bottom, in bend depths. */
internal const val LENS_HALO = 0.12f

/** Corner radius, in bend depths, on a display that reports none. */
internal const val FALLBACK_CORNER = 1.2f

/**
 * Where in the shrink's strength the curve and the dispersion begin: only as
 * the page actually reaches the screen's edges, never on a preview still on
 * its way there ([PageLens.setStrength], [PageLensBrush]). The black bars and
 * the display corners (drawn in screen space by BrowserScreen, not in this
 * shader, which would carry them on the zooming page) fade in at the RAW
 * strength instead.
 */
internal const val LENS_CORNER_START = 0.85f

/**
 * The screen-space corners' size against the display's radius. They are a
 * SUPERELLIPSE (|x|³ + |y|³ = R³), whose curvature falls to zero where it meets
 * the straight edge — a circle's jumps there, which reads as a joint — and a
 * superellipse this much larger stands as far in at its diagonal as the
 * display's own circle does.
 */
internal const val LENS_CORNER_EXTENT = 1.0f

/**
 * The zoom's rounded crop at full screen, against the display's radius: a
 * circle that small lies wholly inside the [LENS_CORNER_EXTENT] superellipse,
 * so the corners cover it when the crop lets go at rest.
 */
internal const val LENS_CROP_CORNER = 0.8f

/** The effect's share at [strength] — see [LENS_CORNER_START]. */
internal fun lensCornerRamp(strength: Float): Float {
    val t = ((strength - LENS_CORNER_START) / (1f - LENS_CORNER_START)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The shader's `softReach`: the curve's read eased into the last readable row. */
internal fun lensReach(s: Float, a: Float): Float {
    val x = s / a
    return a * x / Math.cbrt((1f + x * x * x).toDouble()).toFloat()
}

/** AGSL is API 33; below it the switch is shown disabled and nothing is built. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
internal val pageLensSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** The display's own corner radius, px; null before attach or on a square display. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun displayCornerRadius(view: View): Float? =
    view.rootWindowInsets
        ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
        ?.radius?.toFloat()?.takeIf { it > 0f }

/**
 * Experimental: the page as the face of a CRT, framed in black. Flat across
 * the screen; toward its top and bottom edge it curves away from the viewer.
 * The status bar and the toolbar are black while it is on, so the page ends
 * against the same black at both edges.
 *
 * Kept deliberately SIMPLE, because every extra term showed as motion: the
 * curve is one squeeze that only ever increases toward the edge, fixed — not
 * driven by scroll position or speed — and straight across. A squeeze like
 * that shows more page than the band has room for, and there is no page past
 * a view's edge: clamped, the first row repeats down the edge as a line. So
 * BrowserScreen hangs the WebView [PAGE_LENS_REACH] bend depths ABOVE the
 * page's box, under the black status bar, and pads the document by the same
 * amount, and what the curve reads there is real page. [configure]'s
 * `overscanPx` is that distance; everything outside the visible page is drawn
 * black, which is the bezel it sits in anyway.
 *
 * Above ONLY. A WebView hanging past the window's visible bottom — the
 * screen's edge, or the keyboard's top — is handed a visual viewport shorter
 * than its layout viewport by exactly the overhang, and the first scroll down
 * pans it: every `position: fixed` box on the page rose by it, headers into
 * the curve under the status bar (see BrowserScreen's `pageOverflowPx`). The
 * bottom end has real page of its own under a raised toolbar, and with the
 * toolbar away the curve reads the window's last row down to the edge.
 *
 * **Touches follow the picture.** The curve moves what is DRAWN, not what is
 * hit, so a finger in a band used to land on page further out than what was
 * under it — a strip of the page away at the bottom edge, which is a
 * different link. [PageLensTouchContainer] moves each pointer to the page the
 * shader sampled for that pixel ([pageTouch]).
 *
 * Dispersion: red squeezes a touch more than green and blue a touch less, so
 * the channels agree at the bend line and part gradually toward the edge.
 *
 * The visible page's corners are true circular arcs at the display's own
 * corner radius (`RoundedCorner`). The fade into the bezel is measured from
 * that outline, and its WIDTH follows how much the outline faces up or down:
 * the full width along the top and bottom edges, narrowing smoothly round each
 * arc to a single antialiasing pixel where the arc meets the screen's side. A
 * fade of one fixed width round the arc would be sliced by the screen's edge
 * there, which shows as a straight line through the gradient.
 *
 * **Strength** scales the whole effect from none (0) to full (1). It follows
 * how far the page has shrunk toward its card, so a page grows into the curve
 * on its way out of the tab switcher and loses it on the way in; and the
 * switcher's pictures are FLAT — [unwarp] takes the curve back out of every
 * capture — with [pageLens] putting it back on at the same strength wherever a
 * picture stands in for the live page, so the handoff between the two never
 * shows a jump.
 *
 * A RenderEffect on the WebView's CONTAINER, not on the WebView itself.
 * Android's stretch overscroll is applied to the WebView's own render node
 * AFTER any effect on that node, so an effect there stretched along with a
 * hard flick — bezel, corners, curve and all — when only the page under the
 * glass should. On the container the stretch happens inside it and the curve
 * stays put. The hold cover (`HoldCoverView`) shares that container, so its
 * `PixelCopy` of the screen, which has the curve in it, is flattened first
 * ([unwarp]) or it would be curved twice. The container is shared by every
 * tab a host shows, so a lens only ever takes off an effect that is its own.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class PageLens private constructor(private val web: WebView) {
    /** The lens of whichever WebView a host is currently showing. */
    class Slot {
        var lens: PageLens? = null
    }

    private val shader: RuntimeShader? = compileLens()
    private var enabled = false
    private var depthPx = 1f
    private var overscanPx = 0f
    private var strength = 1f
    private var applied = false
    // The container the effect was last put on.
    private var target: View? = null
    // w, h, topEdge, bottomEdge, depth, radius, strength, bottomDepth,
    // readBottom — what is on screen.
    private val last = FloatArray(9) { Float.NaN }
    private val windowLocation = IntArray(2)

    /**
     * How deep the BOTTOM bend is, read per update; NaN (the default) for the
     * same depth as the top. Shallower under a page's own bottom bar, so the
     * bar is never bent (see [PAGE_LENS_BAR_CLEARANCE]).
     */
    var bottomDepthPx: () -> Float = { Float.NaN }

    /**
     * How far above the view's bottom the visible page ends — the overscan,
     * plus whatever of the toolbar's strip the toolbar is covering — read per
     * update.
     */
    var bottomCoverPx: () -> Float = { 0f }

    init {
        // Size changes (the keyboard, rotation, first attach) move the bottom
        // edge, and attaching is what makes the display's corners readable.
        web.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    }

    /** [overscanPx] is how far the view hangs past the visible page at each end. */
    fun configure(enabled: Boolean, depthPx: Float, overscanPx: Float) {
        this.enabled = enabled
        this.depthPx = depthPx.coerceAtLeast(1f)
        this.overscanPx = overscanPx.coerceAtLeast(0f)
        update()
    }

    /** 0 = no effect at all, 1 = full. See the class doc. */
    fun setStrength(value: Float) {
        val v = lensCornerRamp(value.coerceIn(0f, 1f))
        if (v == strength) return
        strength = v
        update()
    }

    fun update() {
        val shader = shader
        val container = web.parent as? View
        if (!enabled || shader == null || strength <= 0f || container == null) {
            release()
            return
        }
        // Reparented into another host's container: let go of the old one.
        if (target !== container) {
            release()
            target = container
        }
        val w = web.width.toFloat()
        val h = web.height.toFloat()
        if (w <= 0f || h <= 0f) return

        val topEdge = overscanPx.coerceAtMost(h)
        val bottomEdge = (h - bottomCoverPx().coerceIn(0f, h)).coerceAtLeast(topEdge)
        // Null until the view is attached (the layout listener brings us
        // back), and zero on a square display — both fall back.
        val radius = displayCornerRadius(web) ?: (depthPx * FALLBACK_CORNER)

        // The update lambda, layout passes, the toolbar's slide and every
        // frame of a shrink all land here; a new RenderEffect is an allocation
        // and an invalidate, so only make one when something actually moved.
        // Another tab's lens may have put its own effect on the shared
        // container since, so "unchanged" also means "still ours".
        val bottomDepth = bottomDepthPx().takeIf { it.isFinite() }?.coerceIn(1f, depthPx) ?: depthPx
        // The lowest row the curve may read: the window's bottom edge, in this
        // view's coordinates. Chromium draws nothing outside the window — a
        // strip there is the view's plain background, which the curve pulled
        // in as a white line along the bottom edge when the view still hung
        // past the screen — so the last row actually on screen carries on to
        // the edge instead. (The top overscan, and the strip under a raised
        // toolbar, are inside the window and are real page.)
        container.getLocationInWindow(windowLocation)
        val readBottom = (container.rootView.height - windowLocation[1]).toFloat()
            .coerceIn(bottomEdge, h)
        if (applied && owners[container] === this && last[0] == w && last[1] == h && last[2] == topEdge &&
            last[3] == bottomEdge && last[4] == depthPx && last[5] == radius &&
            last[6] == strength && last[7] == bottomDepth && last[8] == readBottom
        ) return
        last[0] = w; last[1] = h; last[2] = topEdge; last[3] = bottomEdge
        last[4] = depthPx; last[5] = radius; last[6] = strength; last[7] = bottomDepth
        last[8] = readBottom

        shader.setLensUniforms(w, h, topEdge, bottomEdge, depthPx, bottomDepth, radius, strength, readBottom)
        // Uniforms are copied when the effect is built, so a change needs a
        // new effect rather than a mutated shader.
        container.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        owners[container] = this
        applied = true
    }

    /** Takes the effect off the container — only while it is still this lens's. */
    private fun release() {
        val t = target
        if (applied && t != null && owners[t] === this) {
            t.setRenderEffect(null)
            owners.remove(t)
        }
        applied = false
        last.fill(Float.NaN)
    }

    /**
     * Takes the curve back out of a capture of this view's page box, so the
     * tab switcher's pictures are flat: [bitmap] is the box — from the visible
     * top down — at 1/[factor] scale.
     *
     * Every row inside a band is replaced by the screen row that SHOWED its
     * page: the squeeze is monotonic, so each page row has exactly one, found
     * by a few Newton steps on the same cubic the shader uses. That screen row
     * sits far enough in to be clear of the fade at full strength. The
     * corners' arcs were never page at all; a row's first clear pixel stands
     * in for them, out to the side. (Dispersion is not undone — red and blue
     * are off by under a pixel there.)
     */
    fun unwarp(bitmap: Bitmap, factor: Int, originY: Int) {
        if (!applied || !bitmap.isMutable) return
        val top = last[2]
        val bottom = last[3]
        val depth = last[4]
        val radius = last[5]
        val k = PAGE_LENS_SQUEEZE * last[6]
        val soft = depth * LENS_HALO
        val reach = maxOf(depth, radius)
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) return
        val src = IntArray(bw * bh)
        bitmap.getPixels(src, 0, bw, 0, 0, bw, bh)
        val out = src.copyOf()
        var touched = false
        for (row in 0 until bh) {
            val y = originY + (row + 0.5f) * factor
            val fromTop = (y - top) <= (bottom - y)
            val rawDist = if (fromTop) y - top else bottom - y
            if (rawDist >= reach) continue
            // Outside the visible page (a full-view copy's strip under the
            // status bar or the toolbar) the shader drew bezel, not page. The
            // page's own edge row stands in there, so that curving the copy
            // again reads page rather than black.
            val edgeDist = maxOf(rawDist, 0f)
            var screenDist = edgeDist
            // The keystone the shader drew this screen row with (1 = none).
            var keystone = 1f
            // The bottom bend can be shallower than the top (see bottomDepthPx).
            val bandDepth = if (fromTop) depth else last[7]
            if (edgeDist < bandDepth) {
                val v = 1f - edgeDist / bandDepth
                // The same eased read the shader draws with (softReach).
                val bendY = if (fromTop) top + bandDepth else bottom - bandDepth
                val maxY = min(last[1], last[8]) - 1f
                val reach = max((if (fromTop) bendY else maxY - bendY) / bandDepth, 0.001f)
                fun m(x: Float) = lensReach(x + k * x * x * x, reach)
                var u = v
                repeat(8) {
                    val d = (m(u + 1e-3f) - m(u - 1e-3f)) / 2e-3f
                    if (d > 1e-4f) u = (u - (m(u) - v) / d).coerceIn(0f, 1f)
                }
                u = u.coerceIn(0f, 1f)
                screenDist = bandDepth * (1f - u)
                keystone = 1f + PAGE_LENS_SKEW * last[6] * u * u
            }
            val screenY = if (fromTop) top + screenDist else bottom - screenDist
            val srcRow = ((screenY - originY) / factor).toInt().coerceIn(0, bh - 1)
            var clear = 0
            if (screenDist < radius) {
                val dy = radius - screenDist
                val fadeWidth = maxOf(soft * (dy / radius), 1f)
                val r = radius - fadeWidth
                val inner = r * r - dy * dy
                val xView = if (inner > 0f) radius - sqrt(inner) else radius
                clear = ceil(xView / factor).toInt().coerceIn(0, bw / 2 - 1)
            }
            val so = srcRow * bw
            val o = row * bw
            // The screen drew page column c at cx + (c - cx) / keystone, so
            // that is where this row's column is read back from.
            val centre = bw * factor / 2f
            for (x in 0 until bw) {
                val viewX = (x + 0.5f) * factor
                val drawnAt = ((centre + (viewX - centre) / keystone) / factor).toInt()
                val sx = when {
                    drawnAt < clear -> clear
                    drawnAt > bw - 1 - clear -> bw - 1 - clear
                    else -> drawnAt
                }.coerceIn(0, bw - 1)
                out[o + x] = src[so + sx]
            }
            touched = true
        }
        if (touched) bitmap.setPixels(out, 0, bw, 0, 0, bw, bh)
    }

    // Scratch for drawnFrom: page x, y.
    private val point = FloatArray(2)

    /**
     * The page pixel the shader sampled to draw screen pixel ([x], [y]) — the
     * green channel's row, keystone included — into [out]; false where it drew
     * the page where it is (outside the bands, or in the bezel). Continuous:
     * the identity at each bend line, reaching further out toward the edge.
     */
    private fun drawnFrom(x: Float, y: Float, out: FloatArray): Boolean {
        val w = last[0]
        val top = last[2]
        val bottom = last[3]
        val s = last[6]
        val distTop = y - top
        val distBot = bottom - y
        val edgeDist = min(distTop, distBot)
        if (edgeDist < 0f) return false
        val fromBottom = distBot < distTop
        val band = if (fromBottom) max(last[7], 1f) else last[4]
        if (edgeDist >= band) return false
        val u = 1f - edgeDist / band
        val dir = if (fromBottom) 1f else -1f
        val bendY = (if (fromBottom) bottom else top) - dir * band
        val k = PAGE_LENS_SQUEEZE * s
        val maxY = min(last[1], last[8]) - 1f
        val reach = max((if (fromBottom) maxY - bendY else bendY) / band, 0.001f)
        out[1] = (bendY + dir * lensReach(u + k * u * u * u, reach) * band).coerceIn(0f, maxY)
        val cx = w * 0.5f
        out[0] = (cx + (x - cx) * (1f + PAGE_LENS_SKEW * s * u * u)).coerceIn(0f, w - 1f)
        return true
    }

    /**
     * [ev] with every pointer moved onto the page the lens drew under it, or
     * null when no pointer is in a band (the event then goes through as it
     * is). A drag inside a band is carried as far as the picture under it
     * moves, which is further than the finger toward the edge. A lone pointer
     * is OFFSET, so its history rides along and its raw screen position —
     * which the long-press menu is placed by — stays the finger's; a
     * multi-touch event is rebuilt pointer by pointer.
     */
    fun pageTouch(ev: MotionEvent): MotionEvent? {
        if (!applied) return null
        val n = ev.pointerCount
        var inBand = false
        for (i in 0 until n) {
            if (drawnFrom(ev.getX(i), ev.getY(i), point)) {
                inBand = true
                break
            }
        }
        if (!inBand) return null
        if (n == 1) {
            return MotionEvent.obtain(ev).apply { offsetLocation(point[0] - ev.x, point[1] - ev.y) }
        }
        val props = Array(n) { MotionEvent.PointerProperties().also { p -> ev.getPointerProperties(it, p) } }
        val coords = Array(n) { MotionEvent.PointerCoords() }
        // pos: a historical sample, or -1 for the current one.
        fun sample(pos: Int) {
            for (i in 0 until n) {
                val c = coords[i]
                if (pos < 0) ev.getPointerCoords(i, c) else ev.getHistoricalPointerCoords(i, pos, c)
                if (drawnFrom(c.x, c.y, point)) {
                    c.x = point[0]
                    c.y = point[1]
                }
            }
        }
        val history = ev.historySize
        sample(if (history > 0) 0 else -1)
        val out = MotionEvent.obtain(
            ev.downTime, if (history > 0) ev.getHistoricalEventTime(0) else ev.eventTime,
            ev.action, n, props, coords, ev.metaState, ev.buttonState,
            ev.xPrecision, ev.yPrecision, ev.deviceId, ev.edgeFlags, ev.source, ev.flags,
        )
        for (pos in 1 until history) {
            sample(pos)
            out.addBatch(ev.getHistoricalEventTime(pos), coords, ev.metaState)
        }
        if (history > 0) {
            sample(-1)
            out.addBatch(ev.eventTime, coords, ev.metaState)
        }
        return out
    }

    companion object {
        private val lenses = WeakHashMap<WebView, PageLens>()

        /** Which lens's effect is on each container right now. */
        private val owners = WeakHashMap<View, PageLens>()

        fun of(web: WebView): PageLens = lenses.getOrPut(web) { PageLens(web) }

        /** The lens whose effect is on [container] right now, if any. */
        fun onContainer(container: View): PageLens? = owners[container]?.takeIf { it.applied }

        /** The lens already on [web], if any — never builds one. */
        fun peek(web: WebView): PageLens? = lenses[web]
    }
}

/**
 * The WebView's container, which hands the page each touch where the page
 * lens DREW what is under the finger ([PageLens.pageTouch]). A plain
 * container wherever the lens is off or unsupported.
 */
internal class PageLensTouchContainer(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val moved = if (pageLensSupported) PageLens.onContainer(this)?.pageTouch(ev) else null
        if (moved == null) return super.dispatchTouchEvent(ev)
        return try {
            super.dispatchTouchEvent(moved)
        } finally {
            moved.recycle()
        }
    }
}

/**
 * The same curve on a Compose layer, for the pictures that stand in for the
 * live page (see [PageLens]'s strength). The layer is the page box itself, so
 * the curve reads no overscan: at the edge its first rows repeat for a few
 * pixels, which lands inside the fade.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PageLensBrush {
    private val shader = compileLens()

    fun effect(
        width: Float,
        height: Float,
        depthPx: Float,
        radiusPx: Float,
        strength: Float,
        belowPx: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val s = shader ?: return null
        if (strength <= 0f || width <= 0f || height <= 0f || depthPx <= 0f) return null
        val ramped = lensCornerRamp(strength.coerceIn(0f, 1f))
        if (ramped <= 0f) return null
        // The bottom edge where the LIVE lens has it with the toolbar up — on
        // the toolbar's top, [belowPx] past this box's bottom (0 for the page
        // box) — so the picture and the page bend in the same place and the
        // handoff between them is still.
        val bottom = (height + belowPx).coerceAtLeast(1f)
        // Read no higher than a few px in: there is no page above a picture,
        // and its very first row is not a clean one (see the shader's readTop).
        s.setLensUniforms(width, height, 0f, bottom, depthPx, depthPx, radiusPx, ramped, height, readTop = 4f)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}

/**
 * Puts the page lens on this layer at [strength] — read in the draw phase —
 * or does nothing when [depthPx] is zero (the lens is off) or unsupported.
 * Its own layer: apply it INSIDE any layer that scales or blurs, so the curve
 * is drawn in the page's own space first.
 */
@Composable
internal fun Modifier.pageLens(depthPx: Float, belowPx: Float = 0f, strength: () -> Float): Modifier {
    if (depthPx <= 0f || !pageLensSupported) return this
    val view = LocalView.current
    val brush = remember { PageLensBrush() }
    return graphicsLayer {
        val radius = displayCornerRadius(view) ?: (depthPx * FALLBACK_CORNER)
        renderEffect = brush.effect(size.width, size.height, depthPx, radius, strength(), belowPx)
    }
}

/**
 * The four display corners BrowserScreen draws in SCREEN space over the page
 * (see LENS_CORNER_START for why not in the lens shader): black outside each
 * arc, with a soft edge that is widest at the arc's middle and tapers to one
 * antialiasing pixel at both ends, so the arc meets the screen's side and the
 * bars' edges as hard as they are — nothing sticks out along either.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LensCornerBrush {
    private val shader: RuntimeShader? = runCatching { RuntimeShader(CORNERS_AGSL) }
        .onFailure { android.util.Log.e("PageLens", "Corner shader failed to compile", it) }
        .getOrNull()

    /** A brush for the rect from ([top]) to ([bottom]) across [width]; null if the shader failed. */
    fun brush(width: Float, top: Float, bottom: Float, radius: Float, soft: Float): androidx.compose.ui.graphics.ShaderBrush? {
        val s = shader ?: return null
        s.setFloatUniform("width", width)
        s.setFloatUniform("top", top)
        s.setFloatUniform("bottom", bottom)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("soft", soft)
        return androidx.compose.ui.graphics.ShaderBrush(s)
    }
}

private const val CORNERS_AGSL = """
    uniform float width;
    uniform float top;
    uniform float bottom;
    uniform float radius;
    uniform float soft;

    half4 main(float2 p) {
        float cx = p.x < width * 0.5 ? radius : width - radius;
        float cy = p.y < (top + bottom) * 0.5 ? top + radius : bottom - radius;
        float2 off = abs(p - float2(cx, cy));
        // Only the corner squares, and only on their outer side.
        bool outX = p.x < radius || p.x > width - radius;
        bool outY = p.y < top + radius || p.y > bottom - radius;
        if (!(outX && outY)) return half4(0.0);
        float len = length(off);
        if (len < 0.001) return half4(0.0);
        // A true circle, tangent to both edges. The joint stays invisible
        // because the soft edge tapers to one pixel there and is centred on
        // the arc (below), so the pixels along each edge come out clear.
        float d = len;
        // sin(2θ): 0 where the curve meets a side or a bar, 1 at its middle.
        float taper = 2.0 * (off.x / len) * (off.y / len);
        float w = max(soft * taper, 1.0);
        // CENTRED on the curve, not inside it: where the arc meets a side or
        // a bar, the first pixel row/column in from that edge is half a pixel
        // off it (d = radius - 0.5), and a ramp ending AT the radius gave
        // those pixels half black — a 1px step sticking out along the edge
        // exactly as long as the corner square.
        float a = smoothstep(radius - w * 0.5, radius + w * 0.5, d);
        return half4(0.0, 0.0, 0.0, half(a));
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileLens(): RuntimeShader? =
    // AGSL is compiled here, at runtime, and a compile error is an exception —
    // thrown from inside a host's update block, at launch when the setting is
    // already on. A shader that fails to build leaves the page flat and says
    // why in the log; it must never take the browser down.
    runCatching { RuntimeShader(LENS_AGSL) }
        .onFailure { android.util.Log.e("PageLens", "Lens shader failed to compile", it) }
        .getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setLensUniforms(
    width: Float,
    height: Float,
    topEdge: Float,
    bottomEdge: Float,
    depth: Float,
    bottomDepth: Float,
    radius: Float,
    strength: Float,
    readBottom: Float,
    readTop: Float = 0f,
) {
    setFloatUniform("readTop", readTop)
    setFloatUniform("size", width, height)
    setFloatUniform("topEdge", topEdge)
    setFloatUniform("bottomEdge", bottomEdge)
    setFloatUniform("readBottom", readBottom)
    setFloatUniform("depth", depth)
    setFloatUniform("bottomDepth", bottomDepth)
    // No arcs in the shader: the corners are drawn in screen space (see
    // LENS_CORNER_START), so the page's own picture never carries them.
    setFloatUniform("radius", 0f)
    setFloatUniform("squeeze", PAGE_LENS_SQUEEZE)
    setFloatUniform("dispersion", PAGE_LENS_DISPERSION)
    setFloatUniform("halo", LENS_HALO)
    setFloatUniform("strength", strength)
    setFloatUniform("skew", PAGE_LENS_SKEW)
}

// Beware SkSL's reserved words when naming things here (`flat`, `smooth`,
// `sample`, `filter`, `input`, `output`...): a clash is a compile error on the
// device, not in the build.
private const val LENS_AGSL = """
    uniform shader content;
    // Spectral taps per pixel in the bands, and the sideways softening (px)
    // at the very edge.
    const int LENS_TAPS = 9;
    const float LENS_SPREAD_X = 1.5;

    // The curve's read s (band depths past the bend line), eased into the
    // last readable row [a] instead of clamped at it. Clamped, the rows past
    // the page's end repeated as one hard stretched strip; eased, the slope
    // falls off smoothly, so the page stretches more the closer the edge.
    // Nearly the identity where there is page to spare. Mirrored in Kotlin
    // by lensReach().
    float softReach(float s, float a) {
        float x = s / a;
        return a * x / pow(1.0 + x * x * x, 1.0 / 3.0);
    }
    uniform float2 size;
    // The visible page, in layer px: below the status bar's strip and above
    // whatever the toolbar (or the screen's edge) hides.
    uniform float topEdge;
    uniform float bottomEdge;
    // The lowest row that holds drawn page: the window's bottom. Nothing past
    // it is ever read (see PageLens.update).
    uniform float readBottom;
    // The highest row the curve may read. 0 for the live view (real page
    // hangs above its edge); a few px in for a picture, whose edge row is
    // often a pixel of fade or crop and would otherwise be repeated across
    // the whole squeezed band as a dark stripe.
    uniform float readTop;
    uniform float depth;
    // The bottom bend's own depth: shallower under a page's bottom bar.
    uniform float bottomDepth;
    // The display's corner radius, px.
    uniform float radius;
    uniform float squeeze;
    uniform float dispersion;
    // Width of the fade along the top and bottom, in bend depths.
    uniform float halo;
    // 0 = the page untouched, 1 = the full effect.
    uniform float strength;
    // Keystone at the very edge: content drawn in by this fraction.
    uniform float skew;

    half4 main(float2 p) {
        float distTop = p.y - topEdge;
        float distBot = bottomEdge - p.y;
        float edgeDist = min(distTop, distBot);
        // Outside the visible page: the bezel.
        if (edgeDist < 0.0) {
            return half4(content.eval(p).rgb * half(1.0 - strength), 1.0);
        }
        if (edgeDist >= max(radius, depth)) {
            return content.eval(p);
        }

        // Distance inside the page's rounded outline, and how much the outline
        // faces up or down at the nearest point of it (ny): 1 along the top and
        // bottom, 0 along the sides, turning smoothly round each corner's arc.
        float sideDist = min(p.x, size.x - p.x);
        float inside = edgeDist;
        float ny = 1.0;
        if (sideDist < radius && edgeDist < radius) {
            float2 off = float2(radius - sideDist, radius - edgeDist);
            float len = max(length(off), 0.001);
            inside = radius - len;
            ny = off.y / len;
        } else if (sideDist < edgeDist) {
            inside = sideDist;
            ny = 0.0;
        }
        if (inside <= 0.0) {
            return half4(content.eval(p).rgb * half(1.0 - strength), 1.0);
        }
        // Full width along the edges, one pixel where an arc meets the side:
        // the fade never meets the screen's edge at an angle.
        // No fade at the TOP edge: the black there is the status bar's and
        // stops at its edge, not a halo spilling onto the page below it.
        float soft = distTop <= distBot ? 1.0 : max(depth * halo * ny, 1.0);
        float fade = 1.0 - strength * (1.0 - smoothstep(0.0, soft, inside));
        float edgeY = topEdge;
        // Direction from the bend line toward its edge.
        float dir = -1.0;
        // This edge's bend depth.
        float band = depth;
        if (distBot < distTop) {
            edgeY = bottomEdge;
            dir = 1.0;
            band = max(bottomDepth, 1.0);
        }
        if (edgeDist >= band) {
            return half4(content.eval(p).rgb * half(fade), 1.0);
        }
        // 0 at the bend line, 1 at the edge.
        float u = 1.0 - edgeDist / band;
        float u3 = u * u * u;
        float k = squeeze * strength;
        // The last stretch of the curve shows rows from well past the page's
        // edge squeezed into a few pixels, fringed — a thin strip of unrelated
        // content. Only that stretch fades out and closes its fringe.
        // Not at the top, where real page hangs past the edge (no strip of
        // unrelated rows) and any darkening reads as the black growing below
        // the status bar.
        float beyond = dir < 0.0 ? 0.0 : smoothstep(0.88, 1.0, u);
        float disp = dispersion * (1.0 - beyond);
        fade *= 1.0 - strength * beyond;
        float bendY = edgeY - dir * band;
        float maxY = min(size.y, readBottom) - 1.0;
        float minY = min(readTop, maxY);
        // How much page there is to read past the bend line, in band depths.
        float reach = max((dir < 0.0 ? bendY - minY : maxY - bendY) / band, 0.001);
        // Keystone: the further round the bend, the more the row is drawn in
        // toward the middle (reading wider, displayed narrower). What it reads
        // from past the sides is nothing — black, with a pixel of edge — and
        // at this size that only happens inside the corners' arcs.
        float cx = size.x * 0.5;
        float maxX = size.x - 1.0;
        float sx = cx + (p.x - cx) * (1.0 + skew * strength * u * u);
        float within = clamp(sx + 0.5, 0.0, 1.0) * clamp(maxX + 0.5 - sx, 0.0, 1.0);
        sx = clamp(sx, 0.0, maxX);
        // A SPECTRUM rather than three channels: taps spread across the
        // dispersion range, each row a wavelength with its own squeeze, and
        // each tap's colour weighted by overlapping bell curves for red (most
        // squeezed), green and blue — so the fringe is a continuous rainbow
        // smear that blurs into itself, not three offset copies of the page.
        // The spread grows with u, so the band's inner end stays sharp. A
        // little spread across as well, since real glass softens both ways.
        half3 acc = half3(0.0);
        half3 wsum = half3(0.0);
        float across = LENS_SPREAD_X * u * u;
        for (int i = 0; i < LENS_TAPS; i++) {
            // -1 (blue end) .. +1 (red end)
            float f = float(i) / float(LENS_TAPS - 1) * 2.0 - 1.0;
            float syi = clamp(bendY + dir * softReach(u + k * (1.0 + disp * f) * u3, reach) * band, minY, maxY);
            // Alternate sides tap by tap, so the sideways spread is a blur
            // rather than a slant.
            float side = 1.0 - 2.0 * mod(float(i), 2.0);
            float sxi = clamp(sx + across * f * side, 0.0, maxX);
            half3 c = content.eval(float2(sxi, syi)).rgb;
            half3 wt = half3(
                exp(-((f - 0.75) * (f - 0.75)) / 0.32),
                exp(-(f * f) / 0.32),
                exp(-((f + 0.75) * (f + 0.75)) / 0.32)
            );
            acc += c * wt;
            wsum += wt;
        }
        half3 rgb = acc / wsum;
        return half4(rgb * half(fade * within), 1.0);
    }
"""
