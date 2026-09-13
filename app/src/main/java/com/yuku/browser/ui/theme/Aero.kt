// `Font(..., variationSettings = ...)` and `FontVariation` are still marked
// experimental; they are how a variable font is instantiated at a weight, and
// this file uses the same API the Nothing theme's faces do.
@file:OptIn(ExperimentalTextApi::class)

package com.yuku.browser.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuku.browser.ui.grain
import com.yuku.browser.R

/**
 * Aero, as a theme for this app: Frutiger Aero by way of Windows 7, Vista and
 * Aqua — the decade when an interface was made of something.
 *
 * The look is three decisions and everything else follows from them.
 *
 * **Chrome is GLASS, and the page is what is behind it.** The other three
 * special themes recolour opaque surfaces; this one makes them translucent
 * and lets the document show through. That is not decoration here — this
 * app's toolbar is already drawn OVER the live WebView's bottom strip (the
 * page is full-height under it), and its sheets are already drawn over the
 * page, so there is a real backdrop at every surface this theme frosts. The
 * glass is what finally admits it.
 *
 * **The glass is TINTED, and that is the whole difference from clear glass.**
 * Windows 7 shipped a colour picker for its chrome and called it
 * colorization, and it is the single decision that separates Aero's glass
 * from the near-clear material interfaces have gone back to twenty years
 * later. Clear glass has one failure mode and it is the important one: text
 * on it is legible or not depending on what happens to be behind it, which
 * is a property of somebody else's web page. So the material here carries
 * COLOUR — its own icy blue, plus the user's accent mixed into it
 * ([AERO_TINT]) — and it is kept opaque enough to read against
 * (panes sit near 0.68, with denser fills in text fields).
 * What the user gets is glass they can see the
 * page moving behind, over type that never has to compete with it.
 *
 * **Depth is REFRACTED, not cast.** 98 draws depth as two hard bevel bands;
 * this theme draws it as light behaving like light in a thick, wet object.
 * Three marks, all in [Modifier.aeroGlass] and all of them lit from directly
 * above:
 *
 * - a **specular gloss** over the top [GLOSS_SPAN] of the surface, ending in
 *   a HARD terminator rather than fading out. That hard line is the signature
 *   of the whole era — Aqua's lozenge, Vista's buttons, every Web 2.0 badge
 *   — and it is what says the surface is a curved solid catching a light
 *   source, rather than a flat panel with a gradient on it. A gloss that
 *   fades out reads as a gradient; one that stops reads as a reflection.
 * - a **lens band** just inside the edge ([LENS_BAND]), brighter at the top
 *   than at the bottom. This is the refraction: the thickness of the glass
 *   seen edge-on, where whatever is behind the surface is bent as it passes
 *   through the curved rim. It is drawn rather than sampled, and it has to
 *   be — the backdrop at these surfaces is a WebView, and Chromium's output
 *   is not readable by a Compose draw pass at any price (see CLAUDE.md on
 *   `View.draw(Canvas)`). So the rim is what a real backdrop blur would have
 *   produced at the one place a viewer actually reads thickness off, and the
 *   translucency does the rest honestly.
 * - a **bounce** along the inside of the bottom edge: light that went
 *   through the object, hit what is under it and came back up. It is what
 *   keeps the bottom of a pane from reading as a cut.
 *
 * A sunken surface ([Glassy.Field]) is the same three marks rearranged, not
 * a second effect: a hole in glass has its shadow at the TOP (nothing is
 * lighting the inside of a cavity from above) and its bright bounce at the
 * bottom. One function draws both, exactly as `bevel98` draws a raised
 * control and its negative.
 *
 * Corners are BOOSTED rather than capped or squared (see `specialCorner`):
 * Aqua's control is a lozenge and Aero's is a bubble, and a tight radius is
 * the one shape a blown, wet-looking object cannot have.
 */
private object Glass {

    // ---- The light end: sky, and white glass over it ---------------------
    //
    // The hues are all one family — a cold blue around 205-210° — because
    // this look's ground is the sky and its material is the water and glass
    // in front of it. The greys are deliberately NOT neutral here, which is
    // the opposite of the call the Nothing theme makes: there a hue across
    // the canvas was a cast spoiling a monochrome premise, and here the cast
    // IS the premise.

    /** Behind a page that has not painted. Barely blue — it is still paper. */
    val page = Color(0xFFFCFDFF)

    /**
     * The canvas the tab switcher and the empty screen share: open sky.
     *
     * The one surface in this theme that is a COLOUR rather than a material,
     * and the same call 98 makes for its desktop. A switcher card is a pane
     * of glass and it has to be held up against something; every other look
     * gives it a shade off the toolbar, which under glass would be a sheet
     * of glass resting on a sheet of glass. This is the sky they are all
     * held up against.
     */
    val sky = Color(0xFFD6E7F7)

    /** The watermark on it: one step deeper into the same sky. */
    val skyGlyph = Color(0xFFC0D8EF)

    // The three glass surfaces, in the alphas that make them readable. A
    // sheet is the most opaque of the three because it is the one carrying
    // whole rows of text; the toolbar is thinner because it is a strip and
    // because the page sliding under it is most of what sells the look; a
    // field is the brightest AND the most opaque, since it is a hole filled
    // with light that someone is about to type into.
    /** Sheets, menus, cards — a pane held over the page. */
    val paneLight = Color(0xA3F4F9FF)

    /** The toolbar: the thinnest glass in the app, and the most looked past. */
    val barLight = Color(0x85EDF5FE)

    /** A field: a hole in the pane with light standing in it. */
    val wellLight = Color(0xE8FFFFFF)

    /** A pane lifted clear of the others — the find bar over a live page. */
    val paneHighLight = Color(0xE4F7FBFF)

    val inkLight = Color(0xFF15242F)
    val inkSoftLight = Color(0xFF243B49)
    val inkQuietLight = Color(0xFF374D5B)
    val inkFaintLight = Color(0xFF8AA5B6)

    /** Hairlines. Blue, like everything else, and translucent like glass. */
    val ruleLight = Color(0x66507A96)
    val ruleSoftLight = Color(0x38618CA8)

    /**
     * The theme's own blue, for the frames before an accent is put through
     * it — Windows 7's default glass, which is the colour anyone picturing
     * this look is picturing. Every swatch replaces it (see `tintedWith`).
     */
    val azureLight = Color(0xFF1B7FD4)
    val azureDark = Color(0xFF5FB8F5)

    val errorLight = Color(0xFFC42B1C)
    val errorDark = Color(0xFFFF8A7A)

    val secureLight = Color(0xFF0E7A4E)
    val secureDark = Color(0xFF5FD3A0)

    // ---- The dark end: night glass ---------------------------------------
    //
    // Not the light end darkened. Aero at night is the same material over a
    // dark wallpaper, which is a different object: the glass stops being
    // white and becomes almost black, the gloss on it drops by a factor of
    // four (a reflection is as bright as what it reflects, and at night
    // there is less to reflect), and the RIM becomes the thing that draws
    // the shape. That is why the rim alphas below fall much less than the
    // gloss ones do — on a dark screen the edge is the object.

    val pageDark = Color(0xFF0A0F15)

    /** Sky after dark: a deep blue that is still unmistakably a sky. */
    val skyDark = Color(0xFF08121C)
    val skyGlyphDark = Color(0xFF102030)

    val paneDark = Color(0xAD0E1721)
    val barDark = Color(0x990B131C)
    val wellDark = Color(0xDE121E2A)
    val paneHighDark = Color(0xDE101A25)

    val inkDark = Color(0xFFE6F0F8)
    val inkSoftDark = Color(0xFFDEEBF5)
    val inkQuietDark = Color(0xFFBED0DF)
    val inkFaintDark = Color(0xFF5C7186)

    val ruleDark = Color(0x6689B4D6)
    val ruleSoftDark = Color(0x3D6C93B4)

    /** What is drawn ON the accent at either end. */
    val onAzureLight = Color(0xFFFFFFFF)
    val onAzureDark = Color(0xFF04121D)
}

/**
 * Aero as a Material scheme. Hand-mapped, like the other three special
 * themes, and for a reason none of them has: **several of these roles are
 * translucent**, and there is no tonal generator that produces an alpha. The
 * surface family is the material itself, so its alphas are as much a part of
 * the palette as its hues are.
 *
 * `surfaceContainerHigh` is the app's field background and is the BRIGHTEST
 * and most opaque surface here, where 98 makes it window white and Nothing
 * makes it a sunk grey: a field in this language is a well full of light.
 * `surfaceTint` points back at the surface, exactly as Nothing's and 98's do
 * — Material mixes it into a raised surface, and a surface that is already
 * glass does not want a second, opaque idea of elevation mixed into it.
 *
 * `background` stays OPAQUE and always must: it is what a tab paints before
 * the renderer has produced a frame, and a translucent one would show the
 * window's black behind a page that is merely still loading.
 */
internal val AeroLightScheme: ColorScheme = lightColorScheme(
    primary = Glass.azureLight,
    onPrimary = Glass.onAzureLight,
    primaryContainer = Color(0xFFCFE6FA),
    onPrimaryContainer = Color(0xFF07314F),
    secondary = Glass.inkSoftLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDEAF6),
    onSecondaryContainer = Glass.inkLight,
    tertiary = Color(0xFF1E9C8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEEE8),
    onTertiaryContainer = Color(0xFF063B34),
    background = Glass.page,
    onBackground = Glass.inkLight,
    surface = Glass.paneLight,
    onSurface = Glass.inkLight,
    surfaceVariant = Color(0xDAE4EFFA),
    onSurfaceVariant = Glass.inkSoftLight,
    surfaceTint = Glass.paneLight,
    inverseSurface = Glass.paneDark,
    inverseOnSurface = Glass.inkDark,
    inversePrimary = Glass.azureDark,
    error = Glass.errorLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DAD6),
    onErrorContainer = Glass.errorLight,
    outline = Glass.ruleLight,
    outlineVariant = Glass.ruleSoftLight,
    // The scrim under a sheet. A cool one rather than neutral black: it is
    // seen THROUGH the sheet's own glass at every edge, and a neutral scrim
    // under a blue pane reads as a dirty smear along the rim.
    scrim = Color(0xFF041018),
    surfaceBright = Glass.wellLight,
    surfaceDim = Color(0xDADCE9F6),
    surfaceContainerLowest = Glass.wellLight,
    surfaceContainerLow = Glass.barLight,
    surfaceContainer = Glass.paneLight,
    surfaceContainerHigh = Glass.wellLight,
    surfaceContainerHighest = Glass.paneHighLight,
)

internal val AeroDarkScheme: ColorScheme = darkColorScheme(
    primary = Glass.azureDark,
    onPrimary = Glass.onAzureDark,
    primaryContainer = Color(0xFF10354F),
    onPrimaryContainer = Color(0xFFCBE6FA),
    secondary = Glass.inkSoftDark,
    onSecondary = Glass.pageDark,
    secondaryContainer = Color(0xFF16242F),
    onSecondaryContainer = Glass.inkDark,
    tertiary = Color(0xFF5FD8C4),
    onTertiary = Color(0xFF04211D),
    tertiaryContainer = Color(0xFF0F3A34),
    onTertiaryContainer = Color(0xFFBFEFE7),
    background = Glass.pageDark,
    onBackground = Glass.inkDark,
    surface = Glass.paneDark,
    onSurface = Glass.inkDark,
    surfaceVariant = Color(0xD4152332),
    onSurfaceVariant = Glass.inkSoftDark,
    surfaceTint = Glass.paneDark,
    inverseSurface = Glass.paneLight,
    inverseOnSurface = Glass.inkLight,
    inversePrimary = Glass.azureLight,
    error = Glass.errorDark,
    onError = Color(0xFF320A05),
    errorContainer = Color(0xFF44140E),
    onErrorContainer = Glass.errorDark,
    outline = Glass.ruleDark,
    outlineVariant = Glass.ruleSoftDark,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xDE182634),
    surfaceDim = Glass.pageDark,
    surfaceContainerLowest = Glass.pageDark,
    surfaceContainerLow = Glass.barDark,
    surfaceContainer = Glass.paneDark,
    surfaceContainerHigh = Glass.wellDark,
    surfaceContainerHighest = Glass.paneHighDark,
)

/**
 * The two quiet inks. Same argument as the other three special themes make
 * (see [TuiInks]): `outline`/`outlineVariant` here are TRANSLUCENT
 * — they are hairlines drawn on glass — and a translucent ink over a live
 * web page is text whose legibility belongs to somebody else's background
 * image. These two are opaque for that reason alone.
 */
internal val AeroInkMutedLight = Glass.inkQuietLight
internal val AeroInkMutedDark = Glass.inkQuietDark
internal val AeroInkFaintLight = Glass.inkFaintLight
internal val AeroInkFaintDark = Glass.inkFaintDark

/** The lock's green, at this palette's saturation. */
internal val AeroSecureLight = Glass.secureLight
internal val AeroSecureDark = Glass.secureDark

/**
 * The sky, and the watermark on it — the two colours [BrowserTheme] takes
 * from here rather than from the scheme, exactly as it takes 98's desktop.
 * See [Glass.sky] for why this look needs one at all.
 */
internal val AeroSkyLight = Glass.sky
internal val AeroSkyDark = Glass.skyDark
internal val AeroSkyGlyphLight = Glass.skyGlyph
internal val AeroSkyGlyphDark = Glass.skyGlyphDark

/**
 * The sky, coloured by the accent: the switcher's and the empty screen's
 * ground. A near-white (or, at night, a near-black) with the accent mixed in —
 * enough to read as that colour's sky and to change with the pick, little
 * enough that a card of glass standing on it still has a ground rather than a
 * coloured wall. [glyph] is the watermark on it, one step further in.
 *
 * [accent] is the live scheme's primary, which is what carries the private
 * space's violet here as well. (The splash resources still carry the old
 * fixed sky — no app state is readable at splash time.)
 */
internal fun aeroAccentSky(accent: Color, darkness: Float, glyph: Boolean = false): Color {
    val light = lerp(Color(0xFFF5F8FC), accent, if (glyph) 0.27f else 0.17f)
    val dark = lerp(Color(0xFF05080C), accent, if (glyph) 0.25f else 0.15f)
    return lerp(light, dark, darkness.coerceIn(0f, 1f))
}

/**
 * The palette's material re-hued to the accent: every role drawn from
 * [Glass]'s cold blue family (surfaces, fields, hairlines, inks, the page)
 * takes [seed]'s hue, keeping its own lightness, saturation and ALPHA — so the
 * glass stays exactly as clear and as legible as it was, and only the colour
 * in it changes. A pale seed (Graphite, a grey wallpaper) thins the saturation
 * with it, so a grey pick is grey glass rather than blue glass.
 *
 * Error, the tertiary sea green and the accent roles themselves are left to
 * [tintedWith] / the palette: they are marks, not the material.
 */
internal fun ColorScheme.aeroRehued(seed: Color): ColorScheme {
    val seedHsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), seedHsl)
    val satScale = (seedHsl[1] / 0.35f).coerceIn(0f, 1f)
    fun Color.re(): Color {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(copy(alpha = 1f).toArgb(), hsl)
        hsl[0] = seedHsl[0]
        hsl[1] *= satScale
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl)).copy(alpha = alpha)
    }
    return copy(
        primaryContainer = primaryContainer.re(),
        onPrimaryContainer = onPrimaryContainer.re(),
        secondary = secondary.re(),
        secondaryContainer = secondaryContainer.re(),
        onSecondaryContainer = onSecondaryContainer.re(),
        background = background.re(),
        onBackground = onBackground.re(),
        surface = surface.re(),
        onSurface = onSurface.re(),
        surfaceVariant = surfaceVariant.re(),
        onSurfaceVariant = onSurfaceVariant.re(),
        surfaceTint = surfaceTint.re(),
        inverseSurface = inverseSurface.re(),
        inverseOnSurface = inverseOnSurface.re(),
        outline = outline.re(),
        outlineVariant = outlineVariant.re(),
        scrim = scrim.re(),
        surfaceBright = surfaceBright.re(),
        surfaceDim = surfaceDim.re(),
        surfaceContainerLowest = surfaceContainerLowest.re(),
        surfaceContainerLow = surfaceContainerLow.re(),
        surfaceContainer = surfaceContainer.re(),
        surfaceContainerHigh = surfaceContainerHigh.re(),
        surfaceContainerHighest = surfaceContainerHighest.re(),
    )
}

/** [aeroRehued] for a lone ink (the quiet inks live outside the scheme). */
internal fun Color.aeroRehued(seed: Color): Color {
    val seedHsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), seedHsl)
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(copy(alpha = 1f).toArgb(), hsl)
    hsl[0] = seedHsl[0]
    hsl[1] *= (seedHsl[1] / 0.35f).coerceIn(0f, 1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl)).copy(alpha = alpha)
}

/**
 * Open Sans — the system face, standing in for Segoe UI.
 *
 * The stand-in is chosen the way the 98 theme chooses Arimo: by asking what
 * the original actually IS and finding the nearest thing that is ours to
 * ship. Segoe UI is Microsoft's and not redistributable, and it is a
 * humanist sans in the Frutiger line — which is the same line this whole
 * look is named after. **Steve Matteson drew both Segoe UI and Open Sans**,
 * the second as an open commission a few years after the first, from the
 * same humanist skeleton with the same open apertures and the same upright
 * stress. So this is not a lookalike picked by eye; it is the same
 * designer's other cut of the same idea, under the SIL OFL.
 *
 * (Frutiger itself, the face, is Linotype's and equally not ours. Its own
 * open descendants would have been a second-hand route to somewhere Open
 * Sans reaches directly.)
 *
 * TWO weights, as everywhere else here: 400 for body and rows, 600 for
 * anything that heads them. Not 700 — this interface carries its hierarchy
 * in size, in the gloss and in the accent, and a bold heading on glass reads
 * as a heavier pane rather than as a louder line.
 */
val AeroFamily = FontFamily(
    Font(R.font.open_sans, weight = FontWeight.Normal, variationSettings = aeroWeight(400)),
    Font(R.font.open_sans, weight = FontWeight.Medium, variationSettings = aeroWeight(500)),
    Font(R.font.open_sans, weight = FontWeight.SemiBold, variationSettings = aeroWeight(600)),
)

private fun aeroWeight(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/**
 * The type scale: Material's ladder in one humanist face at two weights.
 *
 * No size correction, and for the reason the 98 theme spells out at length
 * for Arimo: Open Sans's x-height is 54.5 units per 100 against Roboto's 52,
 * i.e. within a couple of percent, so the app's rows, tiles and sheets —
 * all of them measured around type of Roboto's proportions — hold at the
 * same nominal sp. It runs a touch WIDER than Roboto, which is the one
 * thing to watch if a label ever has to be squeezed; the menu's quick-tile
 * labels are the tightest text in the app and clear it.
 *
 * Tracking is Material's, except at the display sizes, where it is pulled
 * in slightly: a humanist face at 36sp and up sets loose next to a
 * grotesque, and these are the two placeholder headlines that are meant to
 * read as one big object rather than as a row of letters.
 *
 * Sentence case throughout (`SpecialText` gets `AsIs`). Windows 7 and Mac OS
 * X both wrote their menus and labels in sentence and title case; neither
 * had anything to say in capitals, and the TUI's lowercase and Nothing's
 * capitals are each that theme's own voice rather than a slot this one has
 * to fill.
 */
internal val AeroTypography: Typography = Typography().let { t ->
    val ui = AeroFamily
    fun TextStyle.body() = copy(fontFamily = ui, fontWeight = FontWeight.Medium)
    fun TextStyle.head() = copy(fontFamily = ui, fontWeight = FontWeight.SemiBold)
    t.copy(
        displayLarge = t.displayLarge.head(),
        displayMedium = t.displayMedium.head(),
        displaySmall = t.displaySmall.head(),
        headlineLarge = t.headlineLarge.head(),
        headlineMedium = t.headlineMedium.head(),
        headlineSmall = t.headlineSmall.head(),
        titleLarge = t.titleLarge.head(),
        titleMedium = t.titleMedium.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.body(),
        bodyMedium = t.bodyMedium.body(),
        bodySmall = t.bodySmall.body(),
        labelLarge = t.labelLarge.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
    )
}

/**
 * The light this theme draws with, at whichever end the chrome currently is.
 *
 * Read through [aeroGlassColors] rather than taken off the scheme per call
 * site, for the reason [Bevel98Colors] gives: these are the values of a
 * MATERIAL, not roles of a palette, and a surface drawn from three sources
 * comes apart the moment one of them moves.
 */
internal data class AeroGlassColors(
    /** The specular band at its brightest, along the very top. */
    val gloss: Color,
    /** The same band where it meets the terminator — see [GLOSS_SPAN]. */
    val glossEnd: Color,
    /** Light that passed through and came back up off what is underneath. */
    val bounce: Color,
    /** The lit outer edge: the top of the object. */
    val rim: Color,
    /** The same edge at the bottom, where much less light reaches it. */
    val rimLow: Color,
    /** The refraction band just inside the rim — the thickness of the glass. */
    val lens: Color,
    /** The shadow at the top INSIDE a sunken well: a cavity is lit from nowhere. */
    val cavity: Color,
)

private val AeroGlassLight = AeroGlassColors(
    gloss = Color.White.copy(alpha = 0.10f),
    glossEnd = Color.White.copy(alpha = 0.02f),
    bounce = Color.White.copy(alpha = 0.04f),
    rim = Color.White.copy(alpha = 0.85f),
    rimLow = Color.White.copy(alpha = 0.30f),
    lens = Color.White.copy(alpha = 0.42f),
    cavity = Color(0xFF0A2438).copy(alpha = 0.05f),
)

/**
 * Night glass. The gloss falls by roughly a factor of four and the rim by
 * barely a third — see [Glass]'s dark section: a reflection is only as
 * bright as what it reflects, but the EDGE is what draws the object once
 * there is nothing bright left to reflect.
 */
private val AeroGlassDark = AeroGlassColors(
    gloss = Color.White.copy(alpha = 0.13f),
    glossEnd = Color.White.copy(alpha = 0.035f),
    bounce = Color.White.copy(alpha = 0.07f),
    rim = Color.White.copy(alpha = 0.30f),
    rimLow = Color.White.copy(alpha = 0.09f),
    lens = Color.White.copy(alpha = 0.16f),
    cavity = Color.Black.copy(alpha = 0.12f),
)

/**
 * The glass values for the end of the theme the chrome is at, interpolated
 * across the light-to-dark turn like everything else.
 *
 * Reads [LocalChromeDarkness], so it recomposes its caller for the ~300ms
 * that turn takes and only then — the same trade [bevel98Colors] makes, and
 * kept off [BrowserPalette] for the same reason: seven more fields on the
 * app-wide palette that three other themes would never read.
 */
@Composable
internal fun aeroGlassColors(): AeroGlassColors {
    val t = LocalChromeDarkness.current
    if (t <= 0f) return AeroGlassLight
    if (t >= 1f) return AeroGlassDark
    return AeroGlassColors(
        gloss = lerp(AeroGlassLight.gloss, AeroGlassDark.gloss, t),
        glossEnd = lerp(AeroGlassLight.glossEnd, AeroGlassDark.glossEnd, t),
        bounce = lerp(AeroGlassLight.bounce, AeroGlassDark.bounce, t),
        rim = lerp(AeroGlassLight.rim, AeroGlassDark.rim, t),
        rimLow = lerp(AeroGlassLight.rimLow, AeroGlassDark.rimLow, t),
        lens = lerp(AeroGlassLight.lens, AeroGlassDark.lens, t),
        cavity = lerp(AeroGlassLight.cavity, AeroGlassDark.cavity, t),
    )
}

/** What kind of glass a surface is — see [Modifier.aeroGlass]. */
enum class Glassy {
    /** A pane held over the page: a sheet, a card, a menu, a tile. */
    Pane,

    /**
     * The toolbar. A pane whose left, right and bottom edges are off the
     * screen, so it takes the gloss and the top rim and nothing else —
     * drawing a rim down two sides that are past the window is three
     * hairlines' worth of work to produce one line the user cannot see, and
     * drawing the bottom one would put a bright rule across the middle of
     * the navigation bar.
     */
    Bar,

    /**
     * A hole in the glass, filled with light: a text field, an address bar,
     * a progress well. The gloss is inverted into a cavity shadow at the top
     * and the bounce doubles as the bottom edge — a well is lit by what
     * comes back up out of it.
     */
    Field,
}

/**
 * The theme's one drawing primitive: the light on a piece of glass.
 *
 * Three marks, all lit from directly above, all described at length in this
 * file's header — a specular [gloss][AeroGlassColors.gloss] over the top
 * [GLOSS_SPAN] ending in a hard terminator, a [lens][AeroGlassColors.lens]
 * band inside the rim carrying the refraction, and a
 * [bounce][AeroGlassColors.bounce] along the bottom inside edge.
 *
 * **The fill is NOT drawn here.** Every call site already has its own
 * `background(...)` or `Surface(color = ...)` in the palette's own
 * translucent surface colour, and this modifier goes after it: a draw
 * modifier runs in chain order, so what lands here is the glass's own
 * colour, and the marks are laid on top of it and still UNDER the node's
 * content. That is the whole reason the gloss can be this strong without
 * washing out a single row of text.
 *
 * The rim is the exception and is drawn AFTER the content
 * ([drawWithContent]), for the reason `bevel98` draws its bands there: the
 * edge belongs to the object rather than to what is inside it, and a fill or
 * a page preview painted to the same bounds would otherwise cover it.
 *
 * @param corner the radius the caller clipped itself to. Passed rather than
 * inferred because a draw modifier cannot ask the node what shape it was
 * given, and a rim drawn square around a clipped-round surface is the one
 * error in this whole file that would be visible from across the room.
 * @param tint the accent, mixed into the material — see [AERO_TINT]. Handed
 * in rather than read here so the modifier stays usable from a draw pass
 * that already has it.
 */
@Composable
fun Modifier.aeroGlass(
    corner: Dp = 0.dp,
    style: Glassy = Glassy.Pane,
    tint: Color = AccentColor,
): Modifier {
    val c = aeroGlassColors()
    val colorization = tint.copy(alpha = AERO_TINT)
    // The SAME number the surface was clipped to — see [aeroCornerOf]. The
    // call site passes the nominal radius it also handed `specialCorner`, and
    // both put it through one function, because a draw modifier cannot ask
    // its node what shape it was given and a rim half a corner out is the one
    // error here visible from across the room.
    val shaped = aeroCornerOf(corner)
    return this
        .drawBehind {
            val radius = shaped.toPx().coerceAtMost(size.minDimension / 2f)
            val r = CornerRadius(radius, radius)
            // The colorization, under everything: Windows 7's own, and the
            // reason this glass reads as a material with a colour rather
            // than as a hole in the interface. It is the whole surface, not
            // a gradient — a tint that varies is a lighting effect, and the
            // lighting is what the three marks below are for.
            drawRoundRect(color = colorization, cornerRadius = r)
            when (style) {
                Glassy.Pane -> Unit
                Glassy.Field -> {
                    // A cavity: dark at the top, bright at the bottom. The
                    // exact inverse of a pane, and the same three colours.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to c.cavity,
                            (CAVITY_SPAN).coerceAtMost(1f) to Color.Transparent,
                        ),
                        cornerRadius = r,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            1f - BOUNCE_SPAN to Color.Transparent,
                            1f to c.bounce,
                        ),
                        cornerRadius = r,
                    )
                }
                else -> {
                    // The specular band, and the hard line at the end of it.
                    // `size.height * GLOSS_SPAN` as a pixel stop rather than
                    // a fraction stop, so the terminator sits at a fixed
                    // FRACTION of the object however tall it is — a sheet
                    // that grows as it is dragged keeps its highlight in
                    // proportion instead of unrolling it.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to c.gloss,
                            GLOSS_SPAN to c.glossEnd,
                            (GLOSS_SPAN + 0.001f) to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        cornerRadius = r,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            1f - BOUNCE_SPAN to Color.Transparent,
                            1f to c.bounce,
                        ),
                        cornerRadius = r,
                    )
                }
            }
        }
        .drawWithContent {
            drawContent()
            val band = LENS_BAND.toPx()
            val hair = AERO_RIM.toPx()
            when (style) {
                Glassy.Bar -> {
                    // One lit line along the top and the lens under it. The
                    // other three edges are off the window — see [Glassy.Bar].
                    drawRect(color = c.rim, size = Size(size.width, hair))
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(c.lens, Color.Transparent),
                            startY = hair,
                            endY = hair + band,
                        ),
                        topLeft = Offset(0f, hair),
                        size = Size(size.width, band),
                    )
                }
                else -> {
                    val radius = shaped.toPx().coerceAtMost(size.minDimension / 2f)
                    val r = CornerRadius(radius, radius)
                    // The lens: a band INSIDE the edge, brightest at the top,
                    // gone by the bottom. This is the refraction — the
                    // thickness of the object seen through its own curve.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(c.lens, Color.Transparent),
                            startY = 0f,
                            endY = size.height * LENS_FALLOFF,
                        ),
                        topLeft = Offset(hair, hair),
                        size = Size(
                            (size.width - hair * 2).coerceAtLeast(0f),
                            (size.height - hair * 2).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius(
                            (r.x - hair).coerceAtLeast(0f),
                            (r.y - hair).coerceAtLeast(0f),
                        ),
                        style = Stroke(width = band),
                    )
                    // The outer edge itself, lit at the top and nearly gone
                    // at the bottom — one light source, directly above.
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c.rim, c.rimLow)),
                        topLeft = Offset(hair / 2f, hair / 2f),
                        size = Size(
                            (size.width - hair).coerceAtLeast(0f),
                            (size.height - hair).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius(
                            (radius - hair / 2f).coerceAtLeast(0f),
                            (radius - hair / 2f).coerceAtLeast(0f),
                        ),
                        style = Stroke(width = hair),
                    )
                }
            }
        }
        // Stationary, pixel-sized grain beneath labels: enough tooth to read
        // as etched glass without a moving noise field or an opaque wash.
        // The shared grain's base alpha is 2.8%, so these remain very light.
        // Less of it at night: on a dark pane the same speckle stands out
        // several times further than on a pale one, and read as noise.
        .grain((if (style == Glassy.Pane) 1.05f else 0.55f) * (1f - AERO_GRAIN_NIGHT_CUT * LocalChromeDarkness.current))
}

/**
 * [Modifier.aeroGlass] under the Aero theme and nothing at all under any
 * other — the form every call site outside this file wants, and the exact
 * shape `bevel98If` takes for the same reason: glass is not an effect the
 * other looks can wear, so the branch belongs here rather than at each of
 * the dozen surfaces that would otherwise carry it.
 */
@Composable
fun Modifier.aeroGlassIf(corner: Dp = 0.dp, style: Glassy = Glassy.Pane): Modifier =
    if (LocalAero.current) aeroGlass(corner, style) else this

/** Convex water rim; the content stays sharp inside two translucent edges. */
@Composable
fun Modifier.aeroDroplet(corner: Dp = 16.dp, inset: Dp = 0.dp, filled: Boolean = false, openBottom: Boolean = false, thin: Boolean = false, glare: Boolean = false): Modifier {
    if (!LocalAero.current) return this
    val colors = aeroGlassColors()
    val tint = AccentColor
    val radius = aeroCornerOf(corner)
    return drawWithContent {
        val pad = inset.toPx()
        val extent = Size((size.width - pad * 2).coerceAtLeast(0f), (size.height - pad * 2 + if (openBottom) radius.toPx() else 0f).coerceAtLeast(0f))
        val r = radius.toPx().coerceAtMost(extent.minDimension / 2)
        if (filled) drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), tint.copy(alpha = 0.08f))),
            topLeft = Offset(pad, pad), size = extent, cornerRadius = CornerRadius(r),
        )
        drawContent()
        drawDropletMarks(pad, extent, r, colors, tint, thin = thin, glare = glare, openBottom = openBottom)
    }
}

/**
 * Everything a droplet draws OVER its content — the rim, the glare, and (for a
 * thick drop) the inner lens line — at [amount] of full strength. Split out of
 * [aeroDroplet] so a surface that BECOMES a droplet over an animation (a page
 * shrinking into its tab card) can bring the same marks in gradually and land
 * on exactly what the card draws, instead of the card's marks popping on at
 * the handoff.
 */
internal fun DrawScope.drawDropletMarks(
    pad: Float,
    extent: Size,
    r: Float,
    colors: AeroGlassColors,
    tint: Color,
    thin: Boolean,
    glare: Boolean,
    openBottom: Boolean = false,
    amount: Float = 1f,
) {
    val a = amount.coerceIn(0f, 1f)
    if (a <= 0f || extent.width <= 0f || extent.height <= 0f) return
    val hair = (if (thin) 0.65.dp else 1.dp).toPx()
    // An open-bottomed drop (the toolbar) runs its sides off the bottom of
    // the screen, and on a display with rounded corners two bright lines
    // meeting a curved edge read as a fault, most of all on a dark ground.
    // So there the rim is lit at the top and fades out down the sides, gone
    // well before the screen's edge: the glass is lit from above, and its
    // lower edge was never on screen to catch anything.
    val fadeEnd = pad + (extent.height - r).coerceAtLeast(1f) * OPEN_RIM_FADE
    drawRoundRect(
        brush = if (openBottom) {
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.85f),
                0.4f to colors.rimLow,
                1f to Color.Transparent,
                startY = pad,
                endY = fadeEnd,
            )
        } else {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), colors.rimLow, tint.copy(alpha = 0.55f)))
        },
        topLeft = Offset(pad + hair / 2, pad + hair / 2),
        size = Size((extent.width - hair).coerceAtLeast(0f), (extent.height - hair).coerceAtLeast(0f)),
        cornerRadius = CornerRadius((r - hair / 2).coerceAtLeast(0f)), style = Stroke(hair),
        alpha = a,
    )
    // Glare only where it was asked for: on a broad surface (the bar itself) a
    // highlight reads as a smudge, not as a drop. A tab card takes the whole
    // glare too, not just light on its corners.
    // A thin drop is a tab card: a picture of a page someone is trying to
    // recognise, so its glare is kept well under a control's.
    if (glare) drawDropletGlare(pad, extent, r, colors.rim.alpha, tint, openBottom = openBottom, amount = a * if (thin) CARD_GLARE else 1f)
    if (thin) return
    val inner = 2.6.dp.toPx()
    drawRoundRect(
        brush = if (openBottom) {
            Brush.verticalGradient(
                0f to tint.copy(alpha = 0.24f),
                1f to Color.Transparent,
                startY = pad,
                endY = fadeEnd * 0.7f,
            )
        } else {
            Brush.verticalGradient(listOf(tint.copy(alpha = 0.24f), Color.Transparent, Color.White.copy(alpha = 0.6f)))
        },
        topLeft = Offset(pad + inner, pad + inner),
        size = Size((extent.width - inner * 2).coerceAtLeast(0f), (extent.height - inner * 2).coerceAtLeast(0f)),
        cornerRadius = CornerRadius((r - inner).coerceAtLeast(0f)), style = Stroke(1.8.dp.toPx()),
        alpha = a,
    )
}

/** A tab card's glare, as a fraction of a control's. */
private const val CARD_GLARE = 0.45f

/** How much of the glass grain is taken away at the dark end of the theme. */
private const val AERO_GRAIN_NIGHT_CUT = 0.6f

/**
 * How far down an open-bottomed rim (the toolbar's) its light survives, as a
 * fraction of the part of it on screen — gone by here, well clear of rounded
 * screen corners.
 */
private const val OPEN_RIM_FADE = 0.62f

/** Draws a tab card's droplet marks into [extent] at a radius, at an amount. */
typealias AeroCardMarks = DrawScope.(extent: Size, radiusPx: Float, amount: Float) -> Unit

/**
 * The marks a tab card carries (`aeroDroplet(16.dp, thin = true, glare = true)`),
 * for something drawing its way INTO a card — null under every other theme.
 * Colours are read here, in composition; the returned block only draws.
 */
@Composable
fun rememberAeroCardMarks(): AeroCardMarks? {
    if (!LocalAero.current) return null
    val colors = aeroGlassColors()
    val tint = AccentColor
    return { extent, radiusPx, amount ->
        drawDropletMarks(
            0f, extent, radiusPx.coerceIn(0f, extent.minDimension / 2f), colors, tint,
            thin = true, glare = true, amount = amount,
        )
    }
}

/**
 * The light on a convex glass control, built the way the three interfaces
 * that did this best built theirs, and cut from the element's OWN outline so
 * no part can land anywhere the shape is not:
 *
 * - **Specular (Aqua).** The outline contracted a few points, filled white to
 *   nearly clear from the top, and cut off by a wide ellipse so it ends in a
 *   shallow curve — the reflection of a window bent by the moulded face.
 * - **Bottom glow (Aqua).** That highlight flipped, pushed down and blurred
 *   hard, in a LIGHT TINT of the accent: light that passed through coloured
 *   glass comes out that colour, not white.
 * - **Inner glow (Aqua).** A faint deep shade of the accent round the inside
 *   edge, which is what gives the body depth for the light to sit in.
 * - **Fresnel rim (Liquid Glass).** Glass reflects more at a grazing angle, so
 *   a narrow band inside the edge is brighter than the face, most at the top.
 *
 * Every light is SCREENED onto what is under it (Liquid Glass adds its
 * highlights to the colour beneath rather than painting over it), which is
 * what stops the glare reading as a white overlay: it brightens the glass and
 * whatever shows through it, and can never wash either out. [lit] is the rim's
 * alpha at this end of the theme, so all of it dims at night; an [openBottom]
 * shape has no lower edge on screen to glow.
 */
private fun DrawScope.drawDropletGlare(
    pad: Float,
    extent: Size,
    radius: Float,
    lit: Float,
    tint: Color,
    openBottom: Boolean = false,
    amount: Float = 1f,
) {
    val visibleH = if (openBottom) extent.height - radius else extent.height
    if (extent.width <= 0f || visibleH <= 0f || amount <= 0f) return
    // [lit] runs from ~0.30 at night to ~0.85 by day. Steeper than the rim it
    // is taken from, so the night end sits lower (≈0.52 of full, was 0.63):
    // on a dark ground the same light stands out further than on a pale one.
    val strength = (0.3f + 0.75f * lit) * amount
    val inset = 1.dp.toPx()
    val left = pad + inset
    val right = pad + extent.width - inset
    val top = pad + inset
    val bottom = pad + visibleH - inset
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return
    val short = minOf(w, h)
    val cx = (left + right) / 2f
    val r = (radius - inset).coerceIn(0f, short / 2f)
    val op = androidx.compose.ui.graphics.PathOperation
    fun rounded(l: Float, t: Float, rr: Float, b: Float, rad: Float) =
        androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, rr, b, CornerRadius(rad.coerceAtLeast(0f))))
        }
    fun oval(l: Float, t: Float, rr: Float, b: Float) =
        androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(l, t, rr, b)) }
    fun combine(a: androidx.compose.ui.graphics.Path, b: androidx.compose.ui.graphics.Path, how: androidx.compose.ui.graphics.PathOperation) =
        androidx.compose.ui.graphics.Path().apply { op(a, b, how) }
    fun ink(c: Color, alpha: Float) = c.copy(alpha = (alpha * strength).coerceIn(0f, 1f)).toArgb()
    val clear = Color.Transparent.toArgb()
    val screen = android.graphics.PorterDuff.Mode.SCREEN
    val shell = rounded(left, top, right, bottom, r)

    // Aqua's dark inner glow: the body gathers a little depth round its inside
    // edge, in a deep shade of its own colour rather than grey.
    val edge = (short * 0.18f).coerceIn(2.dp.toPx(), 8.dp.toPx())
    val deep = lerp(tint, Color.Black, 0.7f)
    drawGlarePath(
        path = combine(shell, rounded(left + edge, top + edge, right - edge, bottom - edge, (r - edge).coerceAtLeast(r * 0.6f)), op.Difference),
        shader = android.graphics.LinearGradient(0f, top, 0f, bottom, ink(deep, 0.07f), ink(deep, 0.1f), android.graphics.Shader.TileMode.CLAMP),
        blur = edge * 0.8f,
    )

    // The bottom glow — Aqua's highlight flipped, pushed down, blurred hard and
    // coloured a LIGHT TINT of the body rather than white: light that went
    // through a coloured glass comes out that colour. Screened, so it lifts
    // what is under it instead of painting over it.
    if (!openBottom) {
        val glow = lerp(tint, Color.White, 0.55f)
        val lens = combine(shell, oval(cx - w * 0.72f, top + h * 0.5f, cx + w * 0.72f, bottom + h * 0.55f), op.Intersect)
        drawGlarePath(
            path = lens,
            shader = android.graphics.LinearGradient(
                0f, bottom, 0f, top + h * 0.45f,
                intArrayOf(ink(glow, 0.55f), ink(glow, 0.2f), clear),
                floatArrayOf(0f, 0.45f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            ),
            blur = (short * 0.12f).coerceIn(2.dp.toPx(), 10.dp.toPx()),
            mode = screen,
        )
    }

    // The Fresnel rim (Liquid Glass): glass reflects more at a grazing angle,
    // so a narrow band just inside the edge is brighter than the face — most
    // at the top, where the light is.
    val rim = (short * 0.06f).coerceIn(1.2.dp.toPx(), 2.5.dp.toPx())
    drawGlarePath(
        path = combine(shell, rounded(left + rim, top + rim, right - rim, bottom - rim, r - rim), op.Difference),
        shader = android.graphics.LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(ink(Color.White, 0.42f), ink(Color.White, 0.1f), ink(Color.White, 0.2f)),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        ),
        blur = rim * 0.5f,
        mode = screen,
    )

    // The specular — Aqua's highlight: the element's own outline, contracted,
    // cut off by a wide ellipse so its lower edge is a shallow CURVE (the
    // reflection of a window bent by the moulded face) rather than a straight
    // terminator. White to nearly clear top to bottom, with a soft but
    // readable edge where it ends. Screened.
    // Capped in dp: on a big surface (a tab card) a proportional inset grew
    // larger than the corner radius itself, and the contracted outline came
    // out with SQUARE top corners. The radius is concentric with the element's
    // but never collapses — it keeps most of the corner, so the highlight
    // curves round the shoulder the way the glass under it does.
    val hx = if (w / h > 2.5f) maxOf(w * 0.025f, 3.dp.toPx()) else (short * 0.08f).coerceIn(2.dp.toPx(), 6.dp.toPx())
    val hy = (short * 0.05f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
    val bodyR = (r - minOf(hx, hy)).coerceAtLeast(r * 0.75f)
    val body = rounded(left + hx, top + hy, right - hx, bottom - hy, bodyR)
    val arcBottom = top + h * 0.56f
    val cap = oval(cx - w * 0.85f, arcBottom - h * 1.3f, cx + w * 0.85f, arcBottom)
    drawGlarePath(
        path = combine(body, cap, op.Intersect),
        shader = android.graphics.LinearGradient(
            0f, top + hy, 0f, arcBottom,
            intArrayOf(ink(Color.White, 0.62f), ink(Color.White, 0.26f), ink(Color.White, 0.07f)),
            floatArrayOf(0f, 0.45f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        ),
        // Softer on a bigger surface, so its edge melts into the glass there
        // instead of reading as a cut-out laid on top of a card.
        blur = (short * 0.012f).coerceIn(GLARE_SHARP_BLUR.toPx(), 3.dp.toPx()),
        mode = screen,
    )
}

/**
 * One part of a glare: [path] filled with [shader], its edge softened by
 * [blur], composited with [mode] (SCREEN for light, so it brightens the glass
 * and whatever is seen through it rather than laying white over both). A mask
 * blur rather than a layer effect, so it costs one draw and no offscreen
 * buffer.
 */
private fun DrawScope.drawGlarePath(
    path: androidx.compose.ui.graphics.Path,
    shader: android.graphics.Shader,
    blur: Float,
    mode: android.graphics.PorterDuff.Mode? = null,
) {
    if (path.isEmpty) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            if (blur > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            if (mode != null) xfermode = android.graphics.PorterDuffXfermode(mode)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/** The specular's edge: nearly crisp, as a smooth surface reflects. */
private val GLARE_SHARP_BLUR = 0.8.dp

/**
 * The press response of Aero's raised glass controls: a drop dimpling under
 * the finger and springing back PAST its size before it settles.
 *
 * It answers the FINGER, not the action: a press that slides off, turns into a
 * scroll or a drag, or lands on a control with nothing to do still gets the
 * pop on release — the glass was touched either way. Watched on the Initial
 * pass without consuming anything, so the control's own click, long-press and
 * drag detectors see exactly what they saw before.
 *
 * The animations run on the COMPOSITION's scope, not the gesture's: a pointer
 * handler is restarted whenever its node is updated, and a restart mid-press
 * cancelled a pop launched inside it, leaving the control dipped or never
 * answering at all. For the same reason a gesture torn down before its release
 * still pops (the `finally`).
 *
 * [shared] hands several controls ONE scale. The menu's address bar and the
 * + sheet's search bar are the omnibox in two states, and tapping the first
 * crossfades the sheet over to the second while the pop is still running: with
 * a scale each, the fade crossed a bar mid-bounce with one at rest, two sizes
 * of the same bar on screen at once, which read as a jump. Sharing it, the
 * incoming bar is already exactly as big as the outgoing one.
 */
@Composable
fun Modifier.aeroPopIf(
    shared: AeroPop? = null,
    // A control INSIDE another popping control (a button in the omnibox):
    // it claims the press, so only it pops.
    claim: Boolean = false,
    // The container of such controls: a press a child claimed is left alone,
    // so a tap on a button pops the button and a tap on the text pops the bar.
    yieldToChildren: Boolean = false,
): Modifier {
    if (!LocalAero.current) return this
    val own = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    val scale = shared?.scale ?: own
    val peak = shared?.peak ?: POP_PEAK
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val handler: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit =
        androidx.compose.runtime.remember(scale, scope, peak, claim, yieldToChildren) {
            {
                // Up past full size, back a little UNDER it, then home: a
                // damped bounce, so the drop reads as springy rather than as
                // two tweens. Still tweens, not a spring, like the rest of the
                // app's motion.
                fun release() = scope.launch {
                    scale.animateTo(peak, tween(POP_UP_MS, easing = com.yuku.browser.ui.Overshoot))
                    scale.animateTo(POP_REBOUND, tween(POP_REBOUND_MS, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, tween(POP_SETTLE_MS, easing = FastOutSlowInEasing))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (claim) AeroPopClaim.id = down.id
                    if (yieldToChildren) {
                        // Children see the down after this Initial pass; by
                        // the Final pass a claiming child has spoken.
                        var event = awaitPointerEvent(PointerEventPass.Final)
                        if (AeroPopClaim.id == down.id) {
                            while (event.changes.any { it.pressed }) event = awaitPointerEvent(PointerEventPass.Final)
                            return@awaitEachGesture
                        }
                        if (event.changes.none { it.pressed }) {
                            scope.launch {
                                scale.animateTo(POP_PRESSED, tween(POP_DOWN_MS, easing = FastOutSlowInEasing))
                                release().join()
                            }
                            return@awaitEachGesture
                        }
                    }
                    scope.launch { scale.animateTo(POP_PRESSED, tween(POP_DOWN_MS, easing = FastOutSlowInEasing)) }
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        released = true
                        release()
                    } finally {
                        if (!released) release()
                    }
                }
            }
        }
    return this
        .pointerInput(Unit, handler)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/** Which pointer a nested control has claimed — see [aeroPopIf]'s `claim`. */
internal object AeroPopClaim {
    var id: androidx.compose.ui.input.pointer.PointerId? = null
}

private const val POP_PRESSED = 0.95f
private const val POP_PEAK = 1.05f
private const val POP_DOWN_MS = 110
private const val POP_UP_MS = 170
private const val POP_SETTLE_MS = 170
private const val POP_REBOUND = 0.985f
private const val POP_REBOUND_MS = 140

/**
 * The droplet glare on a control that is not itself a droplet — the omnibox
 * fields and the menu's quick tiles. Drawn BEHIND the content, so a label or
 * an address typed over the highlight keeps its contrast. Nothing under any
 * other theme.
 */
@Composable
fun Modifier.aeroGlareIf(corner: Dp): Modifier {
    if (!LocalAero.current) return this
    val lit = aeroGlassColors().rim.alpha
    val tint = AccentColor
    val radius = aeroCornerOf(corner)
    return drawBehind {
        val r = radius.toPx().coerceAtMost(size.minDimension / 2f)
        drawDropletGlare(0f, size, r, lit, tint)
    }
}

/**
 * The sky behind the app's own flat canvases, in place of the film grain —
 * see `Modifier.grainedBackground`.
 *
 * Grain says a surface is photographic and Nothing's dot field says it is
 * printed; this look's canvases are neither. They are a view of something
 * with depth in it, and the one thing every Frutiger Aero image has is a
 * sky: brighter toward the top, deeper toward the bottom, with no texture in
 * it at all. So the wash is a plain vertical gradient over the flat colour,
 * which is also the cheapest of the three to draw — one rect, no shader, no
 * bitmap per density.
 *
 * Deliberately subtle at [SKY_LIFT]: the empty screen and the switcher are
 * read AS surfaces (there is one kaomoji or a row of cards on them), and a
 * gradient strong enough to notice on an empty screen is a band across every
 * card that stands on it.
 */
@Composable
fun Modifier.aeroSky(strength: Float = 1f): Modifier {
    val c = aeroGlassColors()
    val lift = c.gloss.copy(alpha = c.gloss.alpha * SKY_LIFT * strength)
    // The blooms: light in the sky rather than light on a surface. Two soft
    // radial washes, one warm-white high on the left and one cooler low on
    // the right, at a fraction of the lift's own alpha.
    //
    // They are what "a small blur" on this canvas actually reduces to. A
    // RenderEffect blur here would be a full-screen offscreen pass to soften
    // a gradient that is already smooth — there is nothing on this surface
    // with an edge for a blur to find. What was missing is not softness, it
    // is DEPTH: an even gradient reads as a painted wall, and the thing that
    // makes it read as air is unevenness at a scale too large to see the
    // shape of. A radial gradient is that, and it is blurred by construction
    // — no layer, no shader, no per-density bitmap.
    val bloom = c.gloss.copy(alpha = c.gloss.alpha * SKY_BLOOM * strength)
    val bloomLow = c.bounce.copy(alpha = c.bounce.alpha * SKY_BLOOM * strength)
    return this.drawBehind {
        drawRect(brush = Brush.verticalGradient(listOf(lift, Color.Transparent)))
        // Radii in a fraction of the WIDTH, so the two blooms keep their
        // shape on a tall phone and a short landscape window alike, and are
        // centred off the surface's own edges rather than at fixed points —
        // this is drawn at every size from a 40%-wide card to the whole
        // screen.
        val r1 = size.width * SKY_BLOOM_RADIUS
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(bloom, Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.12f),
                radius = r1,
            ),
            radius = r1,
            center = Offset(size.width * 0.22f, size.height * 0.12f),
        )
        val r2 = size.width * SKY_BLOOM_RADIUS * 1.3f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(bloomLow, Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.78f),
                radius = r2,
            ),
            radius = r2,
            center = Offset(size.width * 0.86f, size.height * 0.78f),
        )
    }
}

/**
 * How far down the surface the specular band runs before it stops dead.
 *
 * Just under half, which is where every interface of this era put it —
 * Aqua's lozenge, Vista's glass button, the Web 2.0 badge. Above half the
 * object reads as lit from in front rather than from above; below about a
 * third it reads as a rule along the top rather than as a reflection in a
 * curved face.
 */
private const val GLOSS_SPAN = 0.46f

/** How far up the bottom edge the bounce reaches. */
private const val BOUNCE_SPAN = 0.30f

/** How far down a well its cavity shadow reaches. */
private const val CAVITY_SPAN = 0.42f

/**
 * How far down the object the lens band survives. Shorter than the object
 * itself: refraction is brightest where the light is, and a lens band of
 * even weight all the way round is a stroke rather than a bend.
 */
private const val LENS_FALLOFF = 0.75f

/** The thickness of the glass, seen at the rim. */
private val LENS_BAND = 2.5.dp

/** The lit edge itself — a hairline, but a real one at this density. */
private val AERO_RIM = 1.dp

/**
 * How much of the user's accent is mixed into the material.
 *
 * This is Windows 7's colorization slider, fixed at one value, and it is the
 * number that decides whether this reads as Aero or as the clear glass every
 * interface has now. Low enough that a white pane stays white and the type
 * on it keeps its contrast; high enough that picking Pink and picking Teal
 * are visibly two different browsers, which is the whole point of a tinted
 * material.
 */
private const val AERO_TINT = 0.08f

/** How much of the gloss the canvas gradient is worth — see [aeroSky]. */
private const val SKY_LIFT = 0.34f

/** And the two blooms in it, against the same reference. */
private const val SKY_BLOOM = 0.55f

/** Their size, as a fraction of the surface's WIDTH — see [aeroSky]. */
private const val SKY_BLOOM_RADIUS = 0.78f

/**
 * The matte, as a fraction of the film grain's own strength — applied by
 * `Modifier.grainedBackground`, which owns the texture branch for every look.
 *
 * Glass is not a polished surface here; it is a cast, slightly cloudy one,
 * and the tell is a fine even tooth that catches the light. Well under the
 * grain's full weight because this canvas is a SKY: the grain's job elsewhere
 * is to say a flat field is a material, and too much of it here would say the
 * air is one.
 */
internal const val AERO_MATTE = 0.55f

/**
 * One [aeroPopIf] scale shared by controls that stand in for each other.
 * [peak] is lower than a button's for the omnibox bars: they run the sheet's
 * full width less 12dp a side, so 5% (plus the Overshoot past it) carried
 * their ends into the sheet's rounded corners.
 */
class AeroPop(internal val peak: Float = 1.025f) {
    internal val scale = androidx.compose.animation.core.Animatable(1f)
}

@Composable
fun rememberAeroPop(): AeroPop = androidx.compose.runtime.remember { AeroPop() }
