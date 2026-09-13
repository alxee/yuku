package com.yuku.browser.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.LocalAero
import com.yuku.browser.ui.theme.LocalChromeDarkness
import com.yuku.browser.ui.theme.LocalTui
import com.yuku.browser.ui.theme.crt
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.AERO_MATTE
import com.yuku.browser.ui.theme.aeroSky
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A film-grain wash for the app's own flat surfaces — the canvas behind an
 * empty tab, the tab switcher, the placeholder a card shows before its
 * preview exists, and the page background a loading tab paints before the
 * renderer has anything to put on it. The launch surface carries the same
 * grain, baked (`drawable/splash_window.xml`, `tools/render_grain_tile.py`),
 * so the app doesn't open out of a flat field into a textured one.
 *
 * One strength in every theme: light, dark and private all take the same
 * wash. The dark end was tried heavier — a near-black field is where flatness
 * and banding show, so it can carry more — and it read as noise on the screen
 * rather than as material, so it came back down to meet the light one.
 *
 * Three themes do not take it. Nothing's canvases are printed rather than
 * photographed, so [Modifier.grainedBackground] hands those a
 * [Modifier.dotField] instead — same surfaces, same job, a different idea of
 * what a surface is made of. 98's take NOTHING: its surfaces are neither
 * material nor printed, they are FILLED — a solid run of one indexed colour,
 * which is what a 256-colour desktop was made of and what its depth is drawn
 * against. A texture over them would be dither, and dither in that system
 * meant a colour the display could not hold. And Aero's are a view of
 * something with depth in it rather than a surface at all, so they take a
 * plain sky gradient (`Modifier.aeroSky`): brighter at the top, deeper at the
 * bottom, no texture anywhere in it.
 */

/**
 * The noise tile, in device pixels. Drawn 1:1 — a [Shader] is not scaled by
 * density — so the grain is always one screen pixel per sample, which is what
 * makes it read as grain rather than as a pattern. It repeats every 128px,
 * which is invisible at these alphas because there is no structure in it to
 * recognise.
 */
private const val GRAIN_TILE = 128

/** The toolbar's glass grain under Aero — shared by every pane that matches it. */
const val AERO_BAR_GRAIN = 0.45f

/** Alpha of the wash — barely a couple of levels either side of the surface. */
private const val GRAIN_ALPHA = 0.028f

/**
 * Below this the wash is a fraction of an 8-bit step and costs a full-screen
 * shader draw to say nothing.
 */
private const val GRAIN_FLOOR = 0.004f

/**
 * One tile, built once for the process — 64KB, shared by every surface that
 * asks for it. Seeded rather than left to chance so the same speckle comes
 * back after a process death instead of the field visibly re-rolling.
 *
 * Every sample is full white or full black at FULL alpha, and the strength
 * comes from the alpha the whole rect is drawn at. That uniform amplitude is
 * what keeps the field even: giving each sample its own random alpha as well
 * varies the two things at once, and runs of near-opaque samples landing next
 * to each other are exactly the blotches that reads as. Balanced white and
 * black, because a wash of one polarity would lift (or crush) the surface by
 * its own average and the colour underneath would stop being the colour the
 * palette chose; balanced, it only adds variance.
 */
private val GrainShader: Shader by lazy {
    val random = Random(0x5EED_CA75)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) {
        val value = if (random.nextBoolean()) 0xFF else 0x00
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    val bitmap = Bitmap.createBitmap(pixels, GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888)
    ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
}

/**
 * [Modifier.background] plus the grain over it, which is how every one of
 * these surfaces wants it: the texture belongs to the canvas, so it goes under
 * the content rather than over it (a page preview, a kaomoji or a row of tab
 * titles seen through a speckle looks like a rendering fault, not like film).
 *
 * @param strength scales the wash — 0 turns it off outright, for a surface
 * whose fill is itself conditional.
 * @param dotSpacing widens the Nothing theme's grid on the surfaces that can
 * afford a sparser one — see [dotField]. Ignored by the grain, which has no
 * grid to space out.
 */
@Composable
fun Modifier.grainedBackground(
    color: Color,
    strength: Float = 1f,
    dotSpacing: Float = 1f,
): Modifier = when {
    LocalNothing.current -> this.background(color).dotField(Ink, strength, spacing = dotSpacing)
    LocalNinety8.current -> this.background(color)
    // A tube's raster, drawn UNDER the content: one of these surfaces is the
    // box the live WebView sits in, and scanlines over it would dim the page.
    // The empty canvases (the wider dot spacing marks them) take the tube's
    // edge fall-off too; a card or the page ground does not.
    // No film grain: a tube has raster, not grain.
    LocalTui.current -> this.background(color).crt(
        darkness = LocalChromeDarkness.current,
        phosphor = MaterialTheme.colorScheme.primary,
        overContent = false,
        vignette = dotSpacing > 1f,
        strength = strength,
    )
    // Sky, then a matte tooth over it: the gradient and its blooms say the
    // canvas has depth, and the grain says the glass in front of it is cast
    // rather than polished. See [AERO_MATTE].
    LocalAero.current -> this.background(color).grain(strength * AERO_MATTE)
    else -> this.background(color).grain(strength)
}

/**
 * The Nothing theme's texture, in place of the grain — see [grainedBackground].
 *
 * Grain is a photographic idea: an even, structureless variance that says the
 * surface is *material*. This look's surfaces are not material, they are
 * PRINTED, and the thing that is printed on them is a matrix of holes — the
 * same grid its type is set on, its glyph interface is lit through and its
 * wallpapers are drilled with. So the canvas takes a regular field of dots at
 * a fixed pitch, which reads as the paper this theme's letterforms were
 * punched out of rather than as a surface with a grain in it.
 *
 * Structured where the grain is deliberately not, so the two rules the grain
 * gets to ignore both bind here. The pitch is in **dp, not pixels**: a
 * one-pixel grid is invisible on a 500dpi phone and a grid is only a grid if
 * you can see it is one. And it is sampled **bilinearly**, not
 * nearest-neighbour — the opposite of the grain's choice and for the same
 * reason. Nearest-neighbour is what keeps unstructured noise honest under a
 * scale; under a scale a regular grid it turns into visible beat patterns
 * crawling across every card in the switcher, so here the dots are allowed to
 * soften instead.
 *
 * @param color the ink the dots are bored in — the tile itself is drawn white
 * and tinted, so one bitmap serves both ends of the theme.
 * @param spacing multiplies the pitch, leaving the hole the size it was. The
 * EMPTY canvases take a wider one ([EMPTY_DOT_SPACING]): everywhere else the
 * field is read past something — a card, a page preview, a row of titles —
 * and the grid is what says the surface is printed, but on a screen with one
 * kaomoji on it the field IS what is on screen, and at the ordinary pitch a
 * whole empty display of it reads as a tone rather than as countable holes.
 * Fewer dots, same hole, same ink.
 */
fun Modifier.dotField(
    color: Color,
    strength: Float = 1f,
    topPx: (DrawScope.() -> Float)? = null,
    spacing: Float = 1f,
): Modifier {
    val alpha = DOT_ALPHA * strength
    if (alpha < GRAIN_FLOOR) return this
    return this.drawWithCache {
        // Built here rather than once for the process, because unlike the
        // grain's this tile has a size in dp — it is a different bitmap on a
        // different density, and `drawWithCache` is already keyed on that.
        // It is keyed on the SIZE too, which is what the fit below needs.
        val pitch = (DOT_PITCH_DP * spacing * density).roundToInt().coerceAtLeast(4)
        val bitmap = Bitmap.createBitmap(pitch, pitch, Bitmap.Config.ARGB_8888)
        AndroidCanvas(bitmap).drawCircle(
            pitch / 2f,
            pitch / 2f,
            (DOT_RADIUS_DP * density).coerceAtLeast(0.6f),
            AndroidPaint().apply {
                isAntiAlias = true
                this.color = AndroidColor.WHITE
            },
        )
        // The pitch is FITTED to the surface, per axis: a whole number of
        // cells across and a whole number down, so the field ends where the
        // surface does. A tile repeated from the origin at its nominal pitch
        // divides an arbitrary screen (or card, or sheet) into a whole number
        // of cells plus a remainder, and the remainder is a strip of clipped
        // half-dots down the right edge and along the bottom — the grid
        // running off the page on two sides and stopping cleanly on the other
        // two, which is the one thing a printed matrix never does. Rounding
        // the cell COUNT and then dividing the surface by it puts the error
        // where it cannot be seen (a pitch off by a fraction of a percent)
        // instead of where it can (a column of cropped dots). The dot sits at
        // its cell's centre, so the margin at every edge is the same half
        // cell and the field is centred on both axes for free.
        val cellsX = (size.width / pitch).roundToInt().coerceAtLeast(1)
        val cellsY = (size.height / pitch).roundToInt().coerceAtLeast(1)
        val scaleX = (size.width / cellsX) / pitch
        val scaleY = (size.height / cellsY) / pitch
        val tile = ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
        // Scaled by a local matrix rather than by building the bitmap at the
        // fitted size: the fitted pitch is fractional, and an integer bitmap
        // rounded to it drifts a fraction of a pixel per repeat, which is the
        // same clipped edge again a hundred cells later. Bilinear sampling
        // (below) is already what this tile asks for, so a scale within a
        // percent of 1 costs the dots nothing.
        if (scaleX != 1f || scaleY != 1f) {
            tile.setLocalMatrix(AndroidMatrix().apply { setScale(scaleX, scaleY) })
        }
        val paint = Paint().apply {
            shader = tile
            filterQuality = FilterQuality.Low
            colorFilter = ColorFilter.tint(color)
            this.alpha = alpha.coerceIn(0f, 1f)
        }
        onDrawBehind {
            val top = topPx?.invoke(this)?.coerceIn(0f, size.height) ?: 0f
            drawIntoCanvas { canvas -> canvas.drawRect(0f, top, size.width, size.height, paint) }
        }
    }
}

/**
 * The grid's pitch. Wide enough that the dots are countable rather than a
 * tone — at a grain's pitch this would just be a lighter grain, and the
 * point of it is that you can see the grid.
 */
private const val DOT_PITCH_DP = 13f

/**
 * How much wider that pitch runs on the empty screen and the switcher's own
 * ground — see [dotField]'s `spacing`. Not a different texture, the same one
 * further apart.
 */
const val EMPTY_DOT_SPACING = 1.8f

/** Radius of one hole. Under a dp it stops being round and starts being dust. */
private const val DOT_RADIUS_DP = 1.05f

/**
 * Stronger than the grain by more than double, and it has to be: the grain
 * is hiding, and a field of dots that cannot be seen is a full-screen shader
 * draw saying nothing. Still a watermark — read off the page at arm's length,
 * not from across the room.
 */
private const val DOT_ALPHA = 0.065f

/**
 * The wash on its own, [strength] scaling it.
 *
 * Painted through a [Paint] this holds onto rather than through
 * `drawRect(brush = ...)`, for [FilterQuality.None]: Compose's default paint
 * samples a shader bilinearly, and several of these surfaces are inside a
 * layer that is being scaled (every tab card, the whole page as it shrinks
 * into the switcher), where interpolating a one-pixel noise tile smears it
 * into moiré that crawls as the scale changes. Nearest-neighbour keeps the
 * grain the same grain at any scale.
 *
 * @param topPx where the wash starts, for a surface that does not fill its own
 * bounds — the tab list's ground fades out to transparent above its topmost
 * row, and grain carried on over that is a speckle sitting on the page showing
 * through, which is the one place this texture reads as a fault rather than as
 * material. Evaluated per draw (it follows the list as it scrolls) and in the
 * canvas's own coordinates, so the tile keeps its phase however far down it
 * begins. Null (the default) is the whole surface.
 */
fun Modifier.grain(strength: Float = 1f, topPx: (DrawScope.() -> Float)? = null): Modifier {
    val alpha = GRAIN_ALPHA * strength
    if (alpha < GRAIN_FLOOR) return this
    return this.drawWithCache {
        val paint = Paint().apply {
            shader = GrainShader
            filterQuality = FilterQuality.None
            this.alpha = alpha.coerceIn(0f, 1f)
        }
        onDrawBehind {
            val top = topPx?.invoke(this)?.coerceIn(0f, size.height) ?: 0f
            drawIntoCanvas { canvas -> canvas.drawRect(0f, top, size.width, size.height, paint) }
        }
    }
}
