package com.yuku.browser.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * REAL refraction for the Aero theme: the glass actually bends what is behind
 * it, sampled per pixel, rather than being told where its highlights go.
 *
 * `Modifier.aeroGlass` draws the light ON a piece of glass — a gloss, a lens
 * band, a bounce. That is honest for a surface whose backdrop cannot be read,
 * and it is what this theme falls back to. But where the thing behind the
 * glass IS readable, the edge should do what an edge of thick glass really
 * does: displace what you see through it, most at the rim and not at all in
 * the middle, with the colours pulling apart very slightly as they bend.
 * That is this file.
 *
 * **The mechanism, and its one hard constraint.**
 * `RenderEffect.createRuntimeShaderEffect(shader, "content")` binds a node's
 * OWN CONTENT as the shader's input. So a layer can refract itself, and
 * nothing else: there is no API in Compose or the platform for sampling what
 * is painted *behind* a node. (`PageLens` leans on the same fact, from the
 * other side — it puts its shader on the WebView's container, whose content
 * is the page.) Everything about how this is applied follows from that one
 * sentence:
 *
 * - A **tab preview** already contains real page pixels — the thumbnail is
 *   the node's content — so the effect goes straight onto it and the
 *   refraction is real with nothing plumbed in. That is [aeroRefractIf].
 * - Sheets and buttons sample their current contents at the curved rim.
 *   The flat centre remains unchanged to keep labels readable. This does
 *   not sample the live WebView behind the surface: RenderEffect only has
 *   access to this node's pixels, including their transparency.
 *
 * **API 33** for `RuntimeShader`, exactly as `PageLens` requires, and the
 * compile is guarded the same way: AGSL is compiled on the device, a failure
 * is an exception rather than a build error, and a shader that will not build
 * must leave the interface working. Below 33 — and on any device where the
 * compile fails — every call here is a no-op and the drawn gloss and lens
 * band in `aeroGlass` are the whole effect, which is what the theme looked
 * like before this file existed.
 */
internal val aeroRefractionSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * How far in from the rim the bend reaches. Wider than the drawn lens band
 * ([Modifier.aeroGlass]'s `LENS_BAND`, 2.5dp) and deliberately so: that band
 * is a HIGHLIGHT, which is a line, where this is the THICKNESS of the glass,
 * which is a zone. Too narrow and the displacement reads as a crawling
 * artefact along the edge; too wide and the whole surface swims, which is the
 * thing people mean when they say a glass effect is distracting.
 */
private val REFRACT_BAND = 14.dp

/**
 * The furthest a pixel is pulled, at the very edge. Under about 2dp there is
 * nothing to see; much over 6dp and straight lines crossing the rim visibly
 * break rather than bend.
 */
// Kept near the bottom of that range on purpose: a preview is a page someone
// is trying to recognise, and at 6dp its edge swam.
private val REFRACT_AMOUNT = 2.5.dp

/**
 * How far the red and blue samples are taken past the green one, as a
 * fraction of the displacement. This is the whole difference between "the
 * edge is distorted" and "the edge is GLASS": a real lens disperses, and the
 * eye reads even a fraction of a pixel of colour fringing at an edge as
 * thickness. `PageLens` spends 0.35 on the same effect at the screen's own
 * curve; this is smaller because it is seen at a much smaller radius.
 */
private const val REFRACT_DISPERSION = 0.3f

/**
 * The bend itself.
 *
 * The displacement is `u³` — nothing across most of the band and everything
 * in the last few pixels — because that is what a convex edge does: the
 * surface is nearly flat where it meets the middle and turns hard at the rim.
 * A linear ramp reads as a smear, which is the usual tell of a fake glass
 * edge.
 *
 * Sampling is pulled INWARD (`p - n * d`), so the rim magnifies: it shows
 * what is slightly further inside the shape, which is what looking through a
 * thick edge does. Pushing outward instead would read as a bevel cut into the
 * picture.
 *
 * The rounded-rectangle distance is the standard SDF, and the normal is taken
 * from it by cases rather than by a derivative: AGSL has no `dFdx` here, and
 * for a rounded rect the answer is exact anyway — radial inside a corner's
 * arc, axis-aligned along a side.
 */
private const val REFRACT_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float2 origin;
    uniform float radius;
    uniform float band;
    uniform float amount;
    uniform float disperse;
    uniform float frost;

    half4 sampleGlass(float2 p, float spread) {
        float2 hi = max(size - 1.0, float2(0.0));
        return content.eval(origin + clamp(p, float2(0.0), hi)) * 0.5
            + content.eval(origin + clamp(p + float2(spread, 0.0), float2(0.0), hi)) * 0.125
            + content.eval(origin + clamp(p - float2(spread, 0.0), float2(0.0), hi)) * 0.125
            + content.eval(origin + clamp(p + float2(0.0, spread), float2(0.0), hi)) * 0.125
            + content.eval(origin + clamp(p - float2(0.0, spread), float2(0.0), hi)) * 0.125;
    }

    half4 main(float2 pixel) {
        float2 p = pixel - origin;
        float2 half2size = size * 0.5;
        float2 centred = p - half2size;
        // Distance outside the inner (un-rounded) box, per axis.
        float2 q = abs(centred) - (half2size - radius);
        float outside = length(max(q, 0.0));
        float inner = min(max(q.x, q.y), 0.0);
        // Negative inside the shape; how far in we are is its negation.
        float dist = outside + inner - radius;
        float inside = -dist;
        if (inside >= band) {
            return content.eval(pixel);
        }
        float2 sgn = sign(centred);
        // The outward normal: radial within a corner's arc, axis-aligned
        // along a side. `sgn` puts it in the right quadrant.
        float2 n;
        if (q.x > 0.0 && q.y > 0.0) {
            n = normalize(max(q, 0.0001)) * sgn;
        } else if (q.x > q.y) {
            n = float2(sgn.x, 0.0);
        } else {
            n = float2(0.0, sgn.y);
        }
        // 0 where the band meets the middle, 1 at the very rim.
        float u = 1.0 - clamp(inside / band, 0.0, 1.0);
        // A shallow concave shoulder, like the CRT lens: source pixels
        // nearer the edge are squeezed inward, with zero displacement at
        // both ends of the band (no clamped bright strip at the boundary).
        float d = -amount * 4.0 * u * (1.0 - u) * u;
        float2 maxP = max(size - 1.0, float2(0.0));
        float2 pg = clamp(p - n * d, float2(0.0), maxP);
        float2 pr = clamp(p - n * (d * (1.0 + disperse)), float2(0.0), maxP);
        float2 pb = clamp(p - n * (d * (1.0 - disperse)), float2(0.0), maxP);
        half4 green = sampleGlass(pg, frost * u);
        half4 red = sampleGlass(pr, frost * u);
        half4 blue = sampleGlass(pb, frost * u);
        // Inputs are premultiplied already. Multiplying by alpha again
        // darkens translucent edges; preserve coverage across dispersion.
        half aa = green.a;
        half3 rgb = half3(red.r / max(red.a, 0.0001),
                         green.g / max(green.a, 0.0001),
                         blue.b / max(blue.a, 0.0001));
        return half4(rgb * aa, aa);
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileRefraction(): RuntimeShader? =
    // Compiled on the device, exactly as `PageLens` compiles its own: a
    // failure here is an exception at the first frame that draws glass, and
    // it must never take the browser down with it.
    runCatching { RuntimeShader(REFRACT_AGSL) }
        .onFailure { android.util.Log.e("AeroRefraction", "Refraction shader failed to compile", it) }
        .getOrNull()

/**
 * One compiled shader per composable that asks for one, remembered across
 * recompositions. The uniforms are copied when the effect is built, so a
 * change of size or radius needs a NEW effect rather than a mutated shader —
 * the same rule `PageLens` documents.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RefractionBrush {
    private val shader = compileRefraction()

    fun effect(
        width: Float,
        height: Float,
        radiusPx: Float,
        bandPx: Float,
        amountPx: Float,
        originX: Float = 0f,
        originY: Float = 0f,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val s = shader ?: return null
        if (width <= 0f || height <= 0f || bandPx <= 0f || amountPx <= 0f) return null
        // A band deeper than the shape's own half-width would bend every
        // pixel in it, which is a lens rather than an edge.
        val band = bandPx.coerceAtMost(minOf(width, height) * 0.5f)
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("origin", originX, originY)
        s.setFloatUniform("radius", radiusPx.coerceIn(0f, minOf(width, height) * 0.5f))
        s.setFloatUniform("band", band)
        s.setFloatUniform("amount", amountPx.coerceAtMost(band * 0.22f))
        s.setFloatUniform("disperse", REFRACT_DISPERSION)
        s.setFloatUniform("frost", amountPx * 0.16f)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}

/**
 * Bends this node's own content at its rim — for a surface whose content IS
 * the thing being seen through the glass. A tab preview is the case that
 * matters: the card is a pane over a picture of a page, the picture is the
 * node's content, and so the refraction here is the real thing rather than a
 * drawing of it.
 *
 * A no-op in every other theme, below API 33, and wherever the shader could
 * not be compiled — see this file's header.
 *
 * @param corner the NOMINAL radius the surface was shaped with, put through
 * [aeroCornerOf] here exactly as `aeroGlass` puts it — the bend has to follow
 * the same outline the clip and the drawn rim do, or the three disagree about
 * where the edge is.
 */
@Composable
fun Modifier.aeroRefractIf(
    corner: Dp = 0.dp,
    progress: () -> Float = { 1f },
    targetSize: () -> Size? = { null },
): Modifier {
    if (!LocalAero.current || !aeroRefractionSupported) return this
    val brush = remember { RefractionBrush() }
    val shaped = aeroCornerOf(corner)
    return this.graphicsLayer {
        val p = progress().coerceIn(0f, 1f)
        val target = targetSize() ?: size
        val fill = if (size.width > 0f && size.height > 0f) {
            maxOf(target.width / size.width, target.height / size.height).coerceIn(0.01f, 1f)
        } else 1f
        val scale = 1f + (fill - 1f) * p
        val width = (size.width + (target.width - size.width) * p) / scale
        val height = (size.height + (target.height - size.height) * p) / scale
        renderEffect = brush.effect(
            width = width,
            height = height,
            radiusPx = shaped.toPx() * p / scale,
            bandPx = REFRACT_BAND.toPx() / scale,
            amountPx = REFRACT_AMOUNT.toPx() * p / scale,
            originX = (size.width - width) / 2f,
            originY = (size.height - height) / 2f,
        )
    }
}
