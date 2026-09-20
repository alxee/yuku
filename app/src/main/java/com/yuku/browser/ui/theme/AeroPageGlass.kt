package com.yuku.browser.ui.theme

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

private const val GLASS_REGIONS = """
    uniform shader content;
    uniform float2 size;
    uniform float4 pane;
    uniform float4 bar;
    uniform float4 find;
    // A small, foreground droplet can ask the page pass beneath it for a
    // stronger lens. Unlike a modifier on the button, this has the actual
    // page pixels available to refract.
    uniform float4 droplet;
    uniform float radius;
    uniform float barRadius;
    uniform float findRadius;
    uniform float dropletRadius;
    // The pane's coverage fades to nothing between these two y's (top, bottom)
    // — the same band the tab list fades out over. bottom <= top: no fade.
    uniform float2 paneFade;
    // Full-screen destinations fade their pane with their own page instead of
    // leaving the frosted backdrop behind until their slide has finished.
    uniform float paneAlpha;
    uniform float band;
    uniform float bend;
    float distanceTo(float2 p, float4 rect, float corner) {
        if (rect.z <= rect.x || rect.w <= rect.y) return 100000.0;
        float2 h = (rect.zw - rect.xy) * 0.5;
        float r = min(corner, min(h.x, h.y));
        float2 q = abs(p - (rect.xy + h)) - (h - r);
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    float paneFadeAt(float2 p) {
        if (paneFade.y <= paneFade.x) return 1.0;
        return clamp((paneFade.y - p.y) / (paneFade.y - paneFade.x), 0.0, 1.0);
    }
    float coverage(float2 p) {
        float cp = (1.0 - smoothstep(-0.75, 0.75, distanceTo(p, pane, radius))) * paneFadeAt(p) * paneAlpha;
        float co = 1.0 - smoothstep(-0.75, 0.75, min(min(distanceTo(p, bar, barRadius), distanceTo(p, find, findRadius)), distanceTo(p, droplet, dropletRadius)));
        return max(cp, co);
    }
"""

private const val CLEAR_REGION = """
    half4 main(float2 p) {
        return content.eval(p) * half(1.0 - coverage(p));
    }
"""

private const val FROSTED_REGION = """
    uniform float saturation;
    uniform float luminosity;
    uniform float targetLuma;
    uniform float rim;
    uniform float rimWidth;
    half4 samplePage(float2 p) {
        return content.eval(clamp(p, float2(0.0), max(size - 1.0, float2(0.0))));
    }
    half4 main(float2 p) {
        float cover = coverage(p);
        if (cover <= 0.0) return half4(0.0);
        float dP = distanceTo(p, pane, radius);
        float dB = distanceTo(p, bar, barRadius);
        float dF = distanceTo(p, find, findRadius);
        float dD = distanceTo(p, droplet, dropletRadius);
        float4 rect = pane;
        float corner = radius;
        if (dB < dP && dB <= dF && dB <= dD) { rect = bar; corner = barRadius; }
        else if (dF < dP && dF < dB && dF <= dD) { rect = find; corner = findRadius; }
        else if (dD < dP && dD < dB && dD < dF) { rect = droplet; corner = dropletRadius; }
        float2 h = (rect.zw - rect.xy) * 0.5;
        float r = min(corner, min(h.x, h.y));
        float2 centred = p - (rect.xy + h);
        float2 q = abs(centred) - (h - r);
        float dist = distanceTo(p, rect, r);
        float2 n;
        if (q.x > 0.0 && q.y > 0.0) n = normalize(max(q, 0.0001)) * sign(centred);
        else if (q.x > q.y) n = float2(sign(centred.x), 0.0);
        else n = float2(0.0, sign(centred.y));
        float u = 1.0 - clamp(-dist / band, 0.0, 1.0);
        // A foreground droplet is FROSTED only, not bent: the toolbar's
        // buttons have plain frosted glass under them, and a strong lens
        // round a small drop pulled the rows in at its edge so it read as a
        // hole stamped in the list rather than a drop standing on it.
        float d = bend * u * u * (dD < dP && dD < dB && dD < dF ? 0.0 : 1.0);
        // All channels sample the Gaussian-blurred input. Mixing sharp
        // pixels back into the rim made the previous effect oversharpened.
        half4 red = samplePage(p - n * d * 1.55);
        half4 green = samplePage(p - n * d);
        half4 blue = samplePage(p - n * d * 0.45);
        half a = green.a;
        half3 rgb = half3(red.r / max(red.a, 0.0001),
                         green.g / max(green.a, 0.0001),
                         blue.b / max(blue.a, 0.0001));
        // Frosted material (all three are neutral for Aero's defaults):
        // saturation lifted so the page reads as colour rather than grey
        // through the fill; then its LIGHTNESS pulled toward the material's
        // own (Acrylic's luminosity layer), which is what keeps contrast on
        // the glass the same over a white page and a black one; then a thin
        // lit rim along the edge, the glass's thickness.
        const half3 W = half3(0.2126, 0.7152, 0.0722);
        half l0 = dot(rgb, W);
        rgb = clamp(mix(half3(l0), rgb, half(saturation)), 0.0, 1.0);
        half l1 = dot(rgb, W);
        rgb = clamp(rgb + (mix(l1, half(targetLuma), half(luminosity)) - l1), 0.0, 1.0);
        if (rim > 0.0) {
            half edge = half((1.0 - smoothstep(0.0, rimWidth, -dist)) * rim);
            rgb = mix(rgb, half3(1.0), edge);
        }
        return half4(rgb * a, a) * half(cover);
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PageGlassEffect {
    private val shaders = runCatching {
        RuntimeShader(GLASS_REGIONS + CLEAR_REGION) to RuntimeShader(GLASS_REGIONS + FROSTED_REGION)
    }.onFailure { android.util.Log.e("AeroPageGlass", "Shader compilation failed", it) }.getOrNull()

    fun effect(
        width: Float, height: Float, pane: Rect, bar: Rect, find: Rect, droplet: Rect, paneFade: Offset, paneAlpha: Float, density: Float,
        // Dp values. Aero's defaults; the plain frosted sheet asks for a
        // heavier blur, no bend, and the sheet's own corner.
        blurDp: Float = 7f,
        bendDp: Float = 8f,
        paneCornerDp: Float = aeroCornerOf(28.dp).value,
        barCornerDp: Float = aeroCornerOf(22.dp).value,
        findCornerDp: Float = aeroCornerOf(24.dp).value,
        // The frosted material's treatment of the page; neutral for Aero.
        material: FrostMaterial = FrostMaterial.Clear,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val (clear, frosted) = shaders ?: return null
        if (width <= 0f || height <= 0f || (pane.isEmpty && bar.isEmpty && find.isEmpty && droplet.isEmpty)) return null
        for (s in listOf(clear, frosted)) {
            s.setFloatUniform("pane", pane.left, pane.top, pane.right, pane.bottom)
            s.setFloatUniform("bar", bar.left, bar.top, bar.right, bar.bottom)
            s.setFloatUniform("radius", paneCornerDp * density)
            s.setFloatUniform("barRadius", barCornerDp * density)
            s.setFloatUniform("find", find.left, find.top, find.right, find.bottom)
            s.setFloatUniform("findRadius", findCornerDp * density)
            s.setFloatUniform("droplet", droplet.left, droplet.top, droplet.right, droplet.bottom)
            s.setFloatUniform("dropletRadius", aeroCornerOf(24.dp).value * density)
            s.setFloatUniform("paneFade", paneFade.x, paneFade.y)
            s.setFloatUniform("paneAlpha", paneAlpha.coerceIn(0f, 1f))
        }
        frosted.setFloatUniform("size", width, height)
        frosted.setFloatUniform("band", 22f * density)
        frosted.setFloatUniform("bend", bendDp * density)
        frosted.setFloatUniform("saturation", material.saturation)
        frosted.setFloatUniform("luminosity", material.luminosity)
        frosted.setFloatUniform("targetLuma", material.targetLuma)
        frosted.setFloatUniform("rim", material.rim)
        frosted.setFloatUniform("rimWidth", RIM_WIDTH_DP * density)
        val sharp = RenderEffect.createRuntimeShaderEffect(clear, "content")
        // Preserve background colour and movement while suppressing detail
        // that would compete with the sharp labels on clearer glass.
        val blur = RenderEffect.createBlurEffect(blurDp * density, blurDp * density, Shader.TileMode.CLAMP)
        val glass = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(frosted, "content"), blur,
        )
        // Complementary coverage sums to one, preserving source alpha.
        return RenderEffect.createBlendModeEffect(sharp, glass, BlendMode.PLUS).asComposeRenderEffect()
    }
}

/**
 * The Default and Nothing looks' translucent sheet: the same region pass as
 * Aero's glass (the page is blurred under the sheet's bounds only, since a
 * WebView cannot be read by a Compose draw pass), but plain frost — a heavy
 * blur, no bend, no rim — which is what Android's own shade does over the
 * launcher. The tinted fill on top is [frostedSheetFill].
 */
@Composable
internal fun Modifier.frostedSheetGlass(
    enabled: Boolean,
    corner: androidx.compose.ui.unit.Dp,
    paneInRoot: () -> Rect,
    // The toolbar (square) and the find bar, frosted the same way.
    barInRoot: () -> Rect = { Rect.Zero },
    findInRoot: () -> Rect = { Rect.Zero },
    findCorner: androidx.compose.ui.unit.Dp = 24.dp,
): Modifier {
    if (!enabled || !aeroRefractionSupported) return this
    val effect = remember { PageGlassEffect() }
    val ground = BarBg
    val dark = LocalChromeDarkness.current >= 0.5f
    val setting = LocalFrostOpacity.current
    val material = remember(ground, dark, setting) { FrostMaterial.over(ground, dark, setting) }
    val blurDp = frostBlurDp(setting)
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { origin = it.positionInRoot() }.graphicsLayer {
        val pane = paneInRoot()
        val bar = barInRoot()
        val find = findInRoot()
        renderEffect = if (pane.isEmpty && bar.isEmpty && find.isEmpty) null else effect.effect(
            size.width, size.height,
            if (pane.isEmpty) Rect.Zero else pane.translate(-origin),
            if (bar.isEmpty) Rect.Zero else bar.translate(-origin),
            if (find.isEmpty) Rect.Zero else find.translate(-origin),
            Rect.Zero, Offset.Zero, 1f, density,
            blurDp = blurDp, bendDp = 0f, paneCornerDp = corner.value,
            barCornerDp = 0f, findCornerDp = findCorner.value,
            material = material,
        )
    }
}

/**
 * A fill that becomes see-through under translucent sheets ([LocalFrosted]):
 * fields, tiles and rows let the frosted surface they sit on show through
 * rather than stamping an opaque slab into it.
 */
@Composable
internal fun Color.frostedIf(alpha: Float): Color =
    if (LocalFrosted.current) copy(alpha = this.alpha * alpha) else this

/**
 * How the frosted material treats the page under it, after the blur — the
 * recipe of Windows' Acrylic and Apple's materials, which agree on it:
 * - [saturation]: the backdrop's colour is LIFTED (Apple's materials and the
 *   common `saturate(180%)`), since blur and a fill both drain it to grey.
 * - [luminosity] toward [targetLuma]: Acrylic's luminosity layer. The page's
 *   lightness is pulled toward the material's own, so type and fields on the
 *   glass keep the same contrast over a white page and a black one — without
 *   it, legibility is a property of whatever site is open. Hue survives.
 * - [rim]: a faint lit edge (NN/g: a low-opacity stroke gives glass its
 *   thickness and separates it from a backdrop of the same colour).
 */
internal class FrostMaterial(
    val saturation: Float,
    val luminosity: Float,
    val targetLuma: Float,
    val rim: Float,
) {
    companion object {
        val Clear = FrostMaterial(saturation = 1f, luminosity = 0f, targetLuma = 0f, rim = 0f)

        /**
         * The page is pulled toward the lightness of the SOLID sheet's own
         * ground, so the glass composites near the value the opaque sheet has
         * and every element on it keeps its contrast. How hard it is pulled
         * follows the slider ([setting], 0 clear .. 1 solid): at the clear end
         * the page keeps most of its own light and colour (and the rim does
         * more of the work of saying there is glass at all); at the solid end
         * it is flattened almost to the ground and only tints it.
         */
        fun over(ground: Color, dark: Boolean, setting: Float): FrostMaterial {
            val s = setting.coerceIn(0f, 1f)
            return FrostMaterial(
                saturation = lerpF(1.55f, 1.15f, s),
                luminosity = lerpF(0.35f, 0.95f, s),
                targetLuma = 0.2126f * ground.red + 0.7152f * ground.green + 0.0722f * ground.blue,
                rim = (if (dark) 0.12f else 0.35f) * lerpF(1.5f, 0.7f, s),
            )
        }
    }
}

private const val RIM_WIDTH_DP = 1.25f

/**
 * The frost's blur for the slider. Always far heavier than Aero's 7dp at the
 * solid end — frosted glass the page only COLOURS, as in the system shade —
 * but at the clear end light enough that the page's shapes still read
 * through it: a nearly clear pane with a 40dp blur is a smear, not glass.
 */
internal fun frostBlurDp(setting: Float): Float = lerpF(10f, 56f, setting.coerceIn(0f, 1f))

/**
 * How much of a field's / tile's / row's own fill survives on frosted glass.
 * Follows the slider with the canvas but always stays MORE solid than it, so
 * elements read as controls standing on the glass at every point, and reach
 * nearly clear at the transparent end and fully solid at the other.
 */
internal val FROSTED_ELEMENT_ALPHA: Float
    @Composable get() = frostElementAlpha(LocalFrostOpacity.current)


/**
 * The frosted sheet's fill: the SOLID sheet's own ground, unmixed, at
 * [FROSTED_CANVAS_ALPHA]. The shader pulls the page under it to that ground's
 * lightness (see [FrostMaterial.over]), so the composite lands on the opaque
 * sheet's value and every element keeps the contrast it has there; the tint
 * — Auto's wallpaper colour included — is the scheme's, exactly as when solid.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun frostedSheetFill(ground: Color, accent: Color, nothing: Boolean): Color =
    ground.copy(alpha = frostCanvasAlpha(LocalFrostOpacity.current))

/**
 * The canvas's alpha for the user's setting: all but clear (2%) to all but
 * solid (98.5%). Eased so the middle of the slider stays in the band where
 * the frost reads as a material; the extremes are where the range widened.
 */
internal fun frostCanvasAlpha(setting: Float): Float =
    lerpF(0.02f, 0.985f, Math.pow(setting.coerceIn(0f, 1f).toDouble(), 1.3).toFloat())

internal fun frostElementAlpha(setting: Float): Float =
    lerpF(0.10f, 1f, Math.pow(setting.coerceIn(0f, 1f).toDouble(), 0.7).toFloat())


private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Filters the shared page/switcher layer below chrome; labels stay sharp. */
@Composable
internal fun Modifier.aeroPageGlass(
    paneInRoot: () -> Rect,
    barInRoot: () -> Rect = { Rect.Zero },
    findInRoot: () -> Rect = { Rect.Zero },
    /** Bounds of a foreground droplet that needs actual backdrop refraction. */
    dropletInRoot: () -> Rect = { Rect.Zero },
    // (top, bottom) y of a band the pane fades out over, in root coordinates.
    paneFadeInRoot: () -> Offset = { Offset.Zero },
    // Independent opacity for a pane whose surface has its own fade.
    paneAlpha: () -> Float = { 1f },
): Modifier {
    if (!LocalAero.current || !aeroRefractionSupported) return this
    val effect = remember { PageGlassEffect() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { origin = it.positionInRoot() }.graphicsLayer {
        val pane = paneInRoot()
        val bar = barInRoot()
        val find = findInRoot()
        val droplet = dropletInRoot()
        val fade = paneFadeInRoot()
        val alpha = paneAlpha()
        renderEffect = if (pane.isEmpty && bar.isEmpty && find.isEmpty && droplet.isEmpty) null else effect.effect(
            size.width, size.height, pane.translate(-origin), bar.translate(-origin),
            if (find.isEmpty) Rect.Zero else find.translate(-origin),
            if (droplet.isEmpty) Rect.Zero else droplet.translate(-origin),
            if (fade.y > fade.x) Offset(fade.x - origin.y, fade.y - origin.y) else Offset.Zero,
            alpha,
            density,
        )
    }
}
