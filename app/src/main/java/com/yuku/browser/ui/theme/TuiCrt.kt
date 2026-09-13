package com.yuku.browser.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuku.browser.R
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

/**
 * The TUI theme's face: **Iosevka Term** (be5invis/Iosevka, SIL OFL), unhinted
 * and subset to Latin, Greek, Cyrillic, punctuation, arrows and box drawing.
 *
 * A 0.5em cell against Droid Sans Mono's 0.6 — condensed — with Roboto's own
 * x-height (0.52), round bowls and soft terminals. It replaced IBM 3270
 * Condensed, which was as narrow but read thin, small (x-height 0.425) and
 * angular: a face traced off a vector terminal is all straight strokes.
 *
 * Set a step HEAVY on purpose: Normal is the Medium cut, Medium the SemiBold,
 * SemiBold and Bold the Bold. A lit trace on a tube had more body than a
 * hairline, and a regular monospace weight at label sizes reads spindly.
 */
internal val TuiMonoFamily = FontFamily(
    Font(R.font.iosevka_term_medium, FontWeight.Normal),
    Font(R.font.iosevka_term_semibold, FontWeight.Medium),
    Font(R.font.iosevka_term_bold, FontWeight.SemiBold),
    Font(R.font.iosevka_term_bold, FontWeight.Bold),
)

/** A touch over Roboto's optical size: a monospace cell carries less ink per em. */
internal const val TUI_SIZE_PARITY = 1.05f

internal const val TUI_LINE_PARITY = 1.0f

/**
 * The FALLBACK glow for API < 31, where [tuiBloomIf] has no RenderEffect to
 * draw with: a plain text shadow in the phosphor's colour, dark end only.
 * From 31 up this returns null and the halation does the job, on icons as
 * well as type.
 */
@Composable
internal fun tuiGlow(color: Color): Shadow? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null
    if (!LocalTui.current || !color.isSpecified) return null
    val d = LocalChromeDarkness.current
    if (d < 0.02f || color.alpha == 0f) return null
    val radius = with(LocalDensity.current) { SHADOW_GLOW_RADIUS.toPx() }
    val phosphor = MaterialTheme.colorScheme.primary
    return Shadow(phosphor.copy(alpha = color.alpha * SHADOW_GLOW_ALPHA * d), Offset.Zero, radius)
}

private val SHADOW_GLOW_RADIUS = 4.dp
private const val SHADOW_GLOW_ALPHA = 0.6f

/**
 * The tube's HALATION around everything drawn in a node's content — text,
 * icons, rules, switch borders — tinted with the phosphor (the accent), at
 * both ends of the theme, each the way that kind of screen actually behaves.
 *
 * **Dark end — light spreading.** Built the way CRT shaders build it
 * (crt-lottes-halation, crt-royale's bloom): the LIT pixels (alpha from
 * luminance, less a floor) are blurred at two radii — a tight core that makes
 * the stroke look emissive, a wide weak skirt that is light scattering in the
 * glass — and blended back ADDITIVELY ([ComposeBlendMode.Plus]). Additive can
 * only add light where light already is, so the ground never hazes over.
 *
 * **Light end — ink bleeding.** On a paper-white tube the ground is the lit
 * thing and the type is the gap in it: the beam's spot is wider than the
 * stroke is thin, so dark type goes soft at its edges and the ground's own
 * light eats into it. Drawn as the inverse: the DARK pixels (alpha from
 * 1 − luminance), same two radii, tinted with the accent and laid over with
 * [ComposeBlendMode.Multiply], which can only darken — a faint coloured
 * shade round every stroke, never a grey veil over the page.
 *
 * Either way the RGB of the recorded pixels is replaced by the phosphor and
 * the result is masked back to where the content is not transparent (an
 * inverted luminance of an empty pixel would otherwise be solid colour). A
 * last trace of grain keeps the halo from reading as a vector gradient.
 *
 * Place it AFTER [tuiCrtIf] in a chain, so the raster behind the content is
 * not recorded and does not glow.
 *
 * @param strength scales both — a page preview is a whole picture of lit
 * pixels and takes a fraction of what a line of type does.
 */
@Composable
fun Modifier.tuiBloomIf(strength: Float = 1f, overContent: Boolean = false): Modifier {
    if (!LocalTui.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || strength <= 0f) return this
    val d = LocalChromeDarkness.current
    val density = LocalDensity.current.density
    val phosphor = MaterialTheme.colorScheme.primary
    val darkAlpha = BLOOM_ALPHA_DARK * d * strength
    val lightAlpha = BLOOM_ALPHA_LIGHT * (1f - d) * strength
    val darkLayer = rememberGraphicsLayer()
    val lightLayer = rememberGraphicsLayer()
    val darkEffect = remember(density, phosphor) { bloomEffect(density, phosphor, lit = true).asComposeRenderEffect() }
    val lightEffect = remember(density, phosphor) { bloomEffect(density, phosphor, lit = false).asComposeRenderEffect() }
    return this.drawWithContent {
        // UNDER the sharp content by default. The halo overlaps the thing it
        // comes from, and drawn on top it tinted that thing too: a red accent
        // swatch in Settings came out orange-ish in dark and muddy in light.
        // Underneath, anything opaque covers its own halo and only the spill
        // past its edge shows — which is all a glow is. A page preview is the
        // exception: it IS the lit picture, so its (weak) bloom goes over it.
        if (overContent) drawContent()
        if (darkAlpha > FLOOR) {
            darkLayer.renderEffect = darkEffect
            darkLayer.alpha = darkAlpha
            darkLayer.blendMode = ComposeBlendMode.Plus
            darkLayer.record { this@drawWithContent.drawContent() }
            drawLayer(darkLayer)
        }
        if (lightAlpha > FLOOR) {
            lightLayer.renderEffect = lightEffect
            lightLayer.alpha = lightAlpha
            lightLayer.blendMode = ComposeBlendMode.Multiply
            lightLayer.record { this@drawWithContent.drawContent() }
            drawLayer(lightLayer)
        }
        if (!overContent) drawContent()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun bloomEffect(density: Float, phosphor: Color, lit: Boolean): android.graphics.RenderEffect {
    val k = BLOOM_GAIN
    // lit: alpha = k·lum − floor. Ink: alpha = k·(1 − lum) − floor.
    val sign = if (lit) 1f else -1f
    val bias = (if (lit) 0f else k) - BLOOM_FLOOR
    val tint = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, phosphor.red * 255f,
                    0f, 0f, 0f, 0f, phosphor.green * 255f,
                    0f, 0f, 0f, 0f, phosphor.blue * 255f,
                    0.2126f * k * sign, 0.7152f * k * sign, 0.0722f * k * sign, 0f, bias * 255f,
                ),
            ),
        ),
    )
    // Only where the content has coverage: DST_IN against the untouched source.
    val source = android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    val masked = android.graphics.RenderEffect.createBlendModeEffect(tint, source, BlendMode.DST_IN)
    fun blur(radiusDp: Float, input: android.graphics.RenderEffect) =
        (radiusDp * density).let { r ->
            android.graphics.RenderEffect.createBlurEffect(r, r, input, android.graphics.Shader.TileMode.DECAL)
        }
    val noise = android.graphics.RenderEffect.createShaderEffect(
        BitmapShader(BloomNoise, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT),
    )
    val core = blur(if (lit) BLOOM_CORE_DP else BLEED_CORE_DP, masked)
    val skirt = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, BLOOM_SKIRT, 0f,
                ),
            ),
        ),
        blur(if (lit) BLOOM_SKIRT_DP else BLEED_SKIRT_DP, masked),
    )
    val halo = android.graphics.RenderEffect.createBlendModeEffect(core, skirt, BlendMode.PLUS)
    return android.graphics.RenderEffect.createBlendModeEffect(halo, noise, BlendMode.DST_IN)
}

/**
 * Per-pixel random alpha between 1 − [BLOOM_GRAIN] and 1, seeded like the
 * grain's so the speckle does not re-roll after a process death.
 */
private val BloomNoise: Bitmap by lazy {
    val random = Random(0x7E1E_6105)
    val pixels = IntArray(BLOOM_NOISE_TILE * BLOOM_NOISE_TILE) {
        val a = ((1f - BLOOM_GRAIN * random.nextFloat()) * 255f).roundToInt()
        (a shl 24) or 0xFFFFFF
    }
    Bitmap.createBitmap(pixels, BLOOM_NOISE_TILE, BLOOM_NOISE_TILE, Bitmap.Config.ARGB_8888)
}

private const val BLOOM_NOISE_TILE = 128

/** Dark end: the tight core (the stroke made emissive) and the wide skirt (scatter in the glass). */
private const val BLOOM_CORE_DP = 2f
private const val BLOOM_SKIRT_DP = 9f

/** Light end: tighter — ink softening at its edges, not a shadow cast. */
private const val BLEED_CORE_DP = 1f
private const val BLEED_SKIRT_DP = 3.5f

private const val BLOOM_SKIRT = 0.6f

/** Additive, so this can sit high without hazing the ground. */
private const val BLOOM_ALPHA_DARK = 1.0f

/** Multiply darkens whatever it touches — kept well under the dark end's. */
private const val BLOOM_ALPHA_LIGHT = 0.22f

// A steeper curve than it was (1.6 / 0.12): the glow now tracks how BRIGHT a
// stroke is, not merely that it is there. White type still saturates it; a
// faint grey (a disabled tile's label on an empty tab) stays close to dark,
// where the old curve gave it nearly white type's halo — a bright ring round
// dim text, the one combination that reads as broken rather than as a glow.
private const val BLOOM_GAIN = 2.0f
private const val BLOOM_FLOOR = 0.35f
private const val BLOOM_GRAIN = 0.18f

/**
 * The CRT raster behind a piece of chrome's content — its background only,
 * never the type or icons on it. A no-op in every other theme, the way
 * `bevel98If` owns its branch.
 */
@Composable
fun Modifier.tuiCrtIf(): Modifier =
    if (LocalTui.current) {
        crt(LocalChromeDarkness.current, MaterialTheme.colorScheme.primary, overContent = false, vignette = false)
    } else {
        this
    }

/**
 * A tab PREVIEW as a picture on a tube: raster and grille drawn OVER the page
 * image (it is the image the tube is showing, unlike chrome, whose raster is
 * its ground). No vignette and no bloom inside the card — both read as an
 * inward shadow on a small card; its light goes OUTWARD instead, see
 * [tuiGlowAroundIf].
 */
@Composable
fun Modifier.tuiScreenIf(amount: () -> Float = { 1f }): Modifier {
    if (!LocalTui.current) return this
    val ink = MaterialTheme.colorScheme.primary
    // No raster over the picture: scanlines across a page preview read as
    // a damaged thumbnail, not a tube. The bezel alone says "screen".
    return this
        // A hairline in the ACCENT round the picture — the bezel's
        // edge, which is what tells a card from the page it shows. Scaled by
        // [amount] (read in draw) so it arrives as the card does.
        .drawWithContent {
            drawContent()
            val a = amount().coerceIn(0f, 1f)
            if (a > FLOOR) {
                val w = PREVIEW_OUTLINE.toPx()
                drawSoftRule(ink, PREVIEW_OUTLINE_ALPHA * a, w / 2f, w / 2f, size.width - w, size.height - w, w, SOFT_RULE_BLUR.toPx())
            }
        }
}

/**
 * The TUI's outline: a 1dp rule that is very slightly out of focus, the way a
 * line on a tube is — the beam's spot is wider than the line — rather than
 * the razor edge `Modifier.border` draws. Drawn OVER content, like a border.
 *
 * Place it AFTER [tuiBloomIf] in a chain, so the rule is inside what the
 * bloom records and glows with everything else instead of sitting on top of
 * the halo as a separate, colder line. TUI corners are square, so it is a
 * rect. No-op in every other theme.
 */
@Composable
fun Modifier.tuiSoftOutlineIf(color: Color, width: androidx.compose.ui.unit.Dp = 1.dp): Modifier {
    if (!LocalTui.current) return this
    return drawWithContent {
        drawContent()
        val w = width.toPx()
        drawSoftRule(color, 1f, w / 2f, w / 2f, size.width - w, size.height - w, w, SOFT_RULE_BLUR.toPx())
    }
}

/**
 * A stroked rect through a blur mask — shared by every TUI rule, including
 * the shrinking page's in BrowserScreen, so they all have the same focus.
 * One paint for the process: draw runs on the UI thread, and a mask filter is
 * rebuilt only when the radius changes (it does per frame only while a page
 * is shrinking, where the radius is divided by the live scale).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftRule(
    color: Color,
    alpha: Float,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    strokePx: Float,
    blurPx: Float,
) {
    if (width <= 0f || height <= 0f || alpha <= 0f) return
    SoftRule.paint.strokeWidth = strokePx
    SoftRule.paint.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    SoftRule.blur(blurPx)
    drawIntoCanvas { it.nativeCanvas.drawRect(left, top, left + width, top + height, SoftRule.paint) }
}

private object SoftRule {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }
    private var radius = -1f

    fun blur(r: Float) {
        if (r == radius) return
        radius = r
        paint.maskFilter = if (r > 0.1f) {
            android.graphics.BlurMaskFilter(r, android.graphics.BlurMaskFilter.Blur.NORMAL)
        } else null
    }
}

/** Just past crisp: a dp and a bit of stroke read as lit, not as a hairline in fog. */
internal val SOFT_RULE_BLUR = 0.9.dp

/** A hairline — at 1dp the rule competed with the page inside it. */
private val PREVIEW_OUTLINE = 0.5.dp
private const val PREVIEW_OUTLINE_ALPHA = 0.85f

/**
 * A lit card's light spilling OUTSIDE it: a phosphor-coloured halo drawn
 * behind the node, outer blur only ([android.graphics.BlurMaskFilter.Blur.OUTER]
 * — nothing is drawn inside the bounds, so the preview is untouched). Goes
 * BEFORE the card's `clip` in its chain, or the clip cuts it off. TUI corners
 * are square, so the halo is a rect. Stronger on the tube, a faint accent
 * shade on paper.
 */
@Composable
fun Modifier.tuiGlowAroundIf(
    // Multiplies the card's alpha — a thin line has a sliver of the card's
    // area to glow from and needs several times the strength to be seen.
    strength: Float = 1f,
    radius: androidx.compose.ui.unit.Dp = CARD_GLOW_RADIUS,
    amount: () -> Float = { 1f },
): Modifier {
    if (!LocalTui.current) return this
    val d = LocalChromeDarkness.current
    val phosphor = MaterialTheme.colorScheme.primary
    val alpha = ((CARD_GLOW_LIGHT + (CARD_GLOW_DARK - CARD_GLOW_LIGHT) * d) * strength).coerceAtMost(1f)
    return drawWithCache {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = phosphor.copy(alpha = 1f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                radius.toPx(),
                android.graphics.BlurMaskFilter.Blur.OUTER,
            )
        }
        onDrawBehind {
            // [amount] is read HERE, in draw, so a card fading in as the
            // switcher opens (or out as it is thrown away) costs no
            // recomposition per frame.
            val a = (alpha * amount().coerceIn(0f, 1f) * 255f).roundToInt()
            if (a > 0) {
                paint.alpha = a
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
            }
        }
    }
}

private val CARD_GLOW_RADIUS = 12.dp
private const val CARD_GLOW_DARK = 0.16f
private const val CARD_GLOW_LIGHT = 0.08f

/**
 * The raster itself, after cool-retro-term's and crt-royale's pipelines with
 * the parts that move (flicker, jitter, sync) left out — chrome that crawls
 * is a fault, not a period detail:
 *
 * - **Scanlines** with a soft, grained beam profile — see [ScanTiles].
 *   Plainly there on paper too: a paper-white monitor had them, dark gaps
 *   across a lit ground, which is where they read most clearly.
 * - **Aperture grille**: the phosphor stripes — a red, green and blue column
 *   repeated across the screen, multiplied in at a few percent. On a light
 *   ground this is the colour fringe a white tube had under a magnifier; on a
 *   dark one it only shows where something is lit, which is also true.
 * - **Raster lift** (dark end, under content only): the lit rows tinted with
 *   the phosphor at a couple of percent — an unlit tube is glass with the
 *   phosphor faintly in it, not black.
 * - **Vignette** (optional): the tube's edges falling off, as four edge
 *   fades rather than one radial gradient, which on a 1:2.2 phone reaches
 *   the corners only.
 *
 * @param overContent raster over the node's content rather than under it —
 * for a page preview, which is the picture itself; chrome passes false.
 */
fun Modifier.crt(
    darkness: Float,
    phosphor: Color,
    overContent: Boolean,
    vignette: Boolean,
    strength: Float = 1f,
): Modifier {
    if (strength <= 0f) return this
    val d = darkness.coerceIn(0f, 1f)
    val scanAlpha = (SCAN_ALPHA_LIGHT + (SCAN_ALPHA_DARK - SCAN_ALPHA_LIGHT) * d) * strength
    val maskAlpha = (MASK_ALPHA_LIGHT + (MASK_ALPHA_DARK - MASK_ALPHA_LIGHT) * d) * strength
    // Over a picture there is no ground to lift — the picture is the light.
    val liftAlpha = if (overContent) 0f else RASTER_LIFT_DARK * d * strength
    val edgeAlpha = if (vignette) (VIGNETTE_LIGHT + (VIGNETTE_DARK - VIGNETTE_LIGHT) * d) * strength else 0f
    return drawWithCache {
        val pitch = (SCANLINE_PITCH_DP * density).roundToInt().coerceAtLeast(3)
        val tiles = ScanTiles.forPitch(pitch)
        fun shader(bitmap: Bitmap) = ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
        val scanPaint = Paint().apply {
            shader = shader(tiles.first)
            filterQuality = FilterQuality.None
            alpha = scanAlpha.coerceIn(0f, 1f)
        }
        val liftPaint = Paint().apply {
            shader = shader(tiles.second)
            filterQuality = FilterQuality.None
            colorFilter = ColorFilter.tint(phosphor)
            alpha = liftAlpha.coerceIn(0f, 1f)
        }
        val maskPaint = Paint().apply {
            shader = shader(GrilleTiles.forStripe((GRILLE_STRIPE_DP * density).roundToInt().coerceAtLeast(1)))
            filterQuality = FilterQuality.None
            blendMode = ComposeBlendMode.Multiply
            alpha = maskAlpha.coerceIn(0f, 1f)
        }
        val w = size.width
        val h = size.height
        val ex = w * VIGNETTE_EDGE
        val ey = h * VIGNETTE_EDGE * 0.5f
        val black = Color.Black
        val clear = Color.Transparent
        val left = Brush.horizontalGradient(listOf(black, clear), startX = 0f, endX = ex)
        val right = Brush.horizontalGradient(listOf(clear, black), startX = w - ex, endX = w)
        val top = Brush.verticalGradient(listOf(black, clear), startY = 0f, endY = ey)
        val bottom = Brush.verticalGradient(listOf(clear, black), startY = h - ey, endY = h)

        onDrawWithContent {
            if (liftAlpha > FLOOR) drawIntoCanvas { it.drawRect(0f, 0f, w, h, liftPaint) }
            if (overContent) drawContent()
            if (maskAlpha > FLOOR) drawIntoCanvas { it.drawRect(0f, 0f, w, h, maskPaint) }
            if (scanAlpha > FLOOR) drawIntoCanvas { it.drawRect(0f, 0f, w, h, scanPaint) }
            if (edgeAlpha > FLOOR) {
                drawRect(left, Offset.Zero, Size(ex, h), alpha = edgeAlpha)
                drawRect(right, Offset(w - ex, 0f), Size(ex, h), alpha = edgeAlpha)
                drawRect(top, Offset.Zero, Size(w, ey), alpha = edgeAlpha)
                drawRect(bottom, Offset(0f, h - ey), Size(w, ey), alpha = edgeAlpha)
            }
            if (!overContent) drawContent()
        }
    }
}

/**
 * The raster's two tiles for one pitch in pixels: the dark gaps and the lit
 * rows between them. A beam is not a hard-edged band — its spot has a
 * roughly Gaussian profile (crt-royale's beam model) — so each row's
 * darkness follows a raised cosine across the pitch, sharpened by
 * [SCANLINE_SHARPNESS] so the gap stays a gap rather than a tone. And
 * [SCANLINE_GRAIN] of per-pixel noise runs through both, so the lines break
 * up the way a real tube's do under a magnifier instead of ruling the
 * surface like a CSS gradient.
 *
 * 128px wide and a whole number of pitches tall, so it tiles seamlessly;
 * built once per pitch (a density) and kept, not per draw — the modifier's
 * cache is rebuilt every frame of a light/dark turn.
 */
private object ScanTiles {
    private var pitch = -1
    private lateinit var tiles: Pair<Bitmap, Bitmap>

    fun forPitch(p: Int): Pair<Bitmap, Bitmap> {
        if (p == pitch) return tiles
        val w = SCAN_TILE
        val h = p * ((SCAN_TILE + p - 1) / p)
        val random = Random(0x5CA7_11E5)
        val dark = IntArray(w * h)
        val lit = IntArray(w * h)
        for (y in 0 until h) {
            val phase = (y % p + 0.5f) / p
            val gap = ((1f + kotlin.math.cos(2f * Math.PI.toFloat() * phase)) / 2f)
                .let { Math.pow(it.toDouble(), SCANLINE_SHARPNESS.toDouble()).toFloat() }
            for (x in 0 until w) {
                val n = 1f - SCANLINE_GRAIN * random.nextFloat()
                val a = (gap * n * 255f).roundToInt().coerceIn(0, 255)
                val b = ((1f - gap) * n * 255f).roundToInt().coerceIn(0, 255)
                dark[y * w + x] = a shl 24
                lit[y * w + x] = (b shl 24) or 0xFFFFFF
            }
        }
        tiles = Bitmap.createBitmap(dark, w, h, Bitmap.Config.ARGB_8888) to
            Bitmap.createBitmap(lit, w, h, Bitmap.Config.ARGB_8888)
        pitch = p
        return tiles
    }
}

/**
 * The aperture grille's tile: three columns — red, green, blue — each one
 * stripe wide, one pixel tall. Multiplied, so a red column takes a few
 * percent of green and blue out of whatever is under it. Kept per stripe
 * width, like [ScanTiles].
 */
private object GrilleTiles {
    private var stripe = -1
    private lateinit var tile: Bitmap

    fun forStripe(s: Int): Bitmap {
        if (s == stripe) return tile
        val colors = intArrayOf(0xFFFF5A5A.toInt(), 0xFF5AFF5A.toInt(), 0xFF5A5AFF.toInt())
        tile = Bitmap.createBitmap(IntArray(s * 3) { colors[it / s] }, s * 3, 1, Bitmap.Config.ARGB_8888)
        stripe = s
        return tile
    }
}

private const val SCAN_TILE = 128

/** ~290 lines down a phone's height — countable at arm's length, as a tube's were. */
private const val SCANLINE_PITCH_DP = 3f
private const val SCANLINE_SHARPNESS = 1.6f
private const val SCANLINE_GRAIN = 0.35f
/** Light: a lit ground shows its gaps plainly, so far less than the dark end needs. */
private const val SCAN_ALPHA_LIGHT = 0.02f
private const val SCAN_ALPHA_DARK = 0.24f

/** A stripe per ~0.4dp: three to a dp-and-a-bit, close to a real grille's pitch at arm's length. */
private const val GRILLE_STRIPE_DP = 0.4f
private const val MASK_ALPHA_LIGHT = 0.015f
private const val MASK_ALPHA_DARK = 0.05f

private const val RASTER_LIFT_DARK = 0.045f
// The empty screen's and the switcher's edge fall-off. Halved from 0.14 /
// 0.5: at full strength it read as a shadow round a blank screen rather than
// as a tube's edge.
private const val VIGNETTE_LIGHT = 0.07f
private const val VIGNETTE_DARK = 0.25f
private const val VIGNETTE_EDGE = 0.2f
private const val FLOOR = 0.004f
