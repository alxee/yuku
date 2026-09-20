package com.yuku.browser.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuku.browser.R

/**
 * Windows 98, as a theme for this app.
 *
 * The look is three decisions and everything else follows from them.
 *
 * **A teal desktop under silver windows.** The 1998 default desktop is that
 * blue-green, and it is the only large area of colour in the whole system;
 * everything drawn on top of it — every window, every toolbar, every button
 * — is the same silver [Silver.face]. This app already has a surface whose
 * job is "the ground the windows sit on": the tab switcher's, which is also
 * the empty-tabs screen's (see `BrowserPalette.emptyBg`). That surface takes
 * the desktop, the cards on it take the silver, and the mapping is exact —
 * a tab card IS a window and the switcher IS the desktop it is open on.
 *
 * **One blue, spent on selection.** Navy is the title bar of the active
 * window and the fill behind selected text, and it is not used for anything
 * else. It lands on `primary`, which is what this app draws switches, active
 * tiles and chips in — the same idea, that the blue marks the one thing
 * currently chosen.
 *
 * **Depth is drawn, not cast.** No shadow, no elevation, no blur: a control
 * is raised because its top and left edges are lit and its bottom and right
 * edges are dark, and sunken because those are swapped. The light source is
 * fixed at the top left of the screen and never moves. That is what
 * [Modifier.bevel98] draws, and it is why this theme needs no `surfaceTint`
 * (pointed back at the surface, like Nothing's) and no elevation: Material's
 * way of saying "this is above that" is a shadow, and this look has another
 * one.
 *
 * The one place a real shadow appears is [Modifier.hardShadow98] — a solid
 * black rectangle offset down and right, with no blur in it. That is the
 * period's own drop shadow (menus and tooltips had one, and nothing else
 * did), and being hard-edged it is a drawn shape rather than a light effect.
 */
private object Silver {
    // ---- The light end: Windows 98's own system colours -----------------

    /** `COLOR_BACKGROUND` — the desktop. The one large colour in the system. */
    val desktop = Color(0xFF008080)

    /** A step up from the desktop, for the watermark drawn on it. */
    val desktopGlyph = Color(0xFF1A9191)

    /** `COLOR_3DFACE` — every window, toolbar, button and sheet. */
    val face = Color(0xFFC0C0C0)

    /** `COLOR_3DLIGHT` — the inner lit edge of a raised control. */
    val faceLight = Color(0xFFDFDFDF)

    /** `COLOR_3DHILIGHT` — the outer lit edge. Paper white. */
    val hilight = Color(0xFFFFFFFF)

    /** `COLOR_3DSHADOW` — the inner dark edge, and every hairline. */
    val shadow = Color(0xFF808080)

    /** `COLOR_3DDKSHADOW` — the outer dark edge. */
    val darkShadow = Color(0xFF0A0A0A)

    /** `COLOR_WINDOW` — the inside of a document or a text field. */
    val window = Color(0xFFFFFFFF)

    val text = Color(0xFF000000)
    val textSoft = Color(0xFF262626)

    /**
     * `COLOR_GRAYTEXT`. Windows draws a disabled label as this grey plus a
     * white shadow one pixel down-right; the shadow is a bitmap-era trick
     * for legibility at 8pt and comes off as an emboss at phone sizes, so
     * only the grey survives here.
     */
    val grayText = Color(0xFF6E6E6E)
    val grayTextFaint = Color(0xFF8A8A8A)

    /** `COLOR_ACTIVECAPTION` — the active title bar, and selection. */
    val navy = Color(0xFF000080)

    /** `COLOR_GRADIENTACTIVECAPTION` — the 98 addition, the bar's far end. */
    val navyBright = Color(0xFF1084D0)

    val navyPale = Color(0xFFB4C8E8)

    val error = Color(0xFF800000)
    val secure = Color(0xFF007800)

    // ---- The dark end ---------------------------------------------------
    //
    // Windows 98 has no dark mode, so there is nothing to copy and something
    // to derive: this is the same system with the light source unchanged and
    // the material darkened. The bevel is what makes that possible at all —
    // a lit edge and a cast edge still read as lit and cast on a dark face,
    // where a palette that carried depth in a drop shadow would have had to
    // invent one.
    //
    // The first derivation went only half way down and it showed: a #3B3B3B
    // face under a #6E6E6E lit edge is a MID grey, and a screen of mid grey
    // beside a black status bar reads as a light theme that has been dimmed
    // rather than as a dark one. The ramp below is a full stop darker at
    // every rung — the face is now nearer the window's black than the lit
    // edge — which is also what lets the bevel keep its range: the four
    // edge colours have to fit between the face and the two ends, and a
    // face sitting at the middle grey leaves them nowhere to go.
    //
    // The desktop teal and title-bar blue are the theme's identity, not
    // illumination. Keep both fixed across light and dark chrome so changing
    // mode darkens the windows without changing the desktop or active state.
    val desktopDark = desktop
    val desktopGlyphDark = desktopGlyph

    val faceDark = Color(0xFF1E1E1E)
    val faceLightDark = Color(0xFF303030)
    val hilightDark = Color(0xFF4A4A4A)
    val shadowDark = Color(0xFF0E0E0E)
    val darkShadowDark = Color(0xFF000000)

    val windowDark = Color(0xFF070707)
    val textDark = Color(0xFFE8E8E8)
    val textSoftDark = Color(0xFFCFCFCF)
    val grayTextDark = Color(0xFF9C9C9C)
    val grayTextFaintDark = Color(0xFF6F6F6F)

    val navyDark = navy

    val navyBrightDark = navyBright

    val navyPaleDark = Color(0xFF101B45)

    val errorDark = Color(0xFFE06C6C)
    val secureDark = Color(0xFF5FBF5F)
}

/**
 * Windows 98 as a Material scheme. Hand-mapped, like the other two special
 * themes and for the third variation on the same reason: these are SYSTEM
 * COLOURS, sixteen fixed values an entire operating system was drawn from,
 * and a tonal scheme seeded from the navy would replace all sixteen with
 * shades of navy.
 *
 * Two mappings are worth reading twice.
 *
 * `surfaceContainerHigh` is the app's field background, and here it is
 * WINDOW WHITE rather than a shade of the silver — a text field in this
 * system is a hole cut in the face with white paper behind it, and it is the
 * bevel around that hole that makes it a field. `surfaceContainerLow` is the
 * toolbar and stays silver, so the two read as what they are: chrome, and a
 * document inside it.
 *
 * `surfaceTint` points back at the surface, exactly as Nothing's does. It is
 * what Material mixes into a raised surface, and in this look a raised
 * surface is not a different colour — it is the same silver with a lit edge.
 */
internal val Ninety8LightScheme: ColorScheme = lightColorScheme(
    primary = Silver.navy,
    onPrimary = Silver.hilight,
    primaryContainer = Silver.navyPale,
    onPrimaryContainer = Silver.navy,
    secondary = Silver.navyBright,
    onSecondary = Silver.hilight,
    secondaryContainer = Silver.faceLight,
    onSecondaryContainer = Silver.text,
    tertiary = Silver.desktop,
    onTertiary = Silver.hilight,
    tertiaryContainer = Silver.faceLight,
    onTertiaryContainer = Silver.text,
    background = Silver.window,
    onBackground = Silver.text,
    surface = Silver.face,
    onSurface = Silver.text,
    surfaceVariant = Silver.faceLight,
    onSurfaceVariant = Silver.textSoft,
    surfaceTint = Silver.face,
    inverseSurface = Silver.navy,
    inverseOnSurface = Silver.hilight,
    inversePrimary = Silver.navyBright,
    error = Silver.error,
    onError = Silver.hilight,
    errorContainer = Silver.faceLight,
    onErrorContainer = Silver.error,
    outline = Silver.shadow,
    outlineVariant = Silver.shadow,
    scrim = Silver.darkShadow,
    surfaceBright = Silver.hilight,
    surfaceDim = Silver.shadow,
    surfaceContainerLowest = Silver.window,
    surfaceContainerLow = Silver.face,
    surfaceContainer = Silver.face,
    surfaceContainerHigh = Silver.window,
    surfaceContainerHighest = Silver.faceLight,
)

internal val Ninety8DarkScheme: ColorScheme = darkColorScheme(
    primary = Silver.navyDark,
    onPrimary = Silver.hilight,
    primaryContainer = Silver.navyPaleDark,
    onPrimaryContainer = Silver.hilight,
    secondary = Silver.navyBrightDark,
    onSecondary = Silver.hilight,
    secondaryContainer = Silver.faceLightDark,
    onSecondaryContainer = Silver.textDark,
    tertiary = Silver.desktopGlyphDark,
    onTertiary = Silver.textDark,
    tertiaryContainer = Silver.faceLightDark,
    onTertiaryContainer = Silver.textDark,
    background = Silver.windowDark,
    onBackground = Silver.textDark,
    surface = Silver.faceDark,
    onSurface = Silver.textDark,
    surfaceVariant = Silver.faceLightDark,
    onSurfaceVariant = Silver.textSoftDark,
    surfaceTint = Silver.faceDark,
    inverseSurface = Silver.face,
    inverseOnSurface = Silver.text,
    inversePrimary = Silver.navy,
    error = Silver.errorDark,
    onError = Silver.windowDark,
    errorContainer = Silver.faceLightDark,
    onErrorContainer = Silver.errorDark,
    outline = Silver.hilightDark,
    outlineVariant = Silver.shadowDark,
    scrim = Silver.darkShadowDark,
    surfaceBright = Silver.faceLightDark,
    surfaceDim = Silver.windowDark,
    surfaceContainerLowest = Silver.windowDark,
    surfaceContainerLow = Silver.faceDark,
    surfaceContainer = Silver.faceDark,
    surfaceContainerHigh = Silver.windowDark,
    surfaceContainerHighest = Silver.faceLightDark,
)

/**
 * The two quiet inks. Same argument as the other two special themes make
 * (see [TuiInks]): `outline` and `outlineVariant` are picked here to
 * draw BEVELS — they are the lit and cast edges — and neither is a colour to
 * set secondary text in. Windows has its own answer for that anyway, and it
 * is `COLOR_GRAYTEXT`.
 */
internal val Ninety8InkMutedLight = Silver.grayText
internal val Ninety8InkMutedDark = Silver.grayTextDark
internal val Ninety8InkFaintLight = Silver.grayTextFaint
internal val Ninety8InkFaintDark = Silver.grayTextFaintDark

/** The lock icon's green, at the saturation the rest of this palette runs at. */
internal val Ninety8SecureLight = Silver.secure
internal val Ninety8SecureDark = Silver.secureDark

/**
 * The desktop, and the watermark on it — the two colours [BrowserTheme] takes
 * from here rather than from the scheme, because there is no Material role
 * for "the ground the windows are open on". Everything else in this theme
 * comes out of [Ninety8LightScheme].
 */
internal val Ninety8DesktopLight = Silver.desktop
internal val Ninety8DesktopDark = Silver.desktopDark
internal val Ninety8DesktopGlyphLight = Silver.desktopGlyph
internal val Ninety8DesktopGlyphDark = Silver.desktopGlyphDark

/**
 * Arimo — the system face, standing in for MS Sans Serif.
 *
 * MS Sans Serif is Microsoft's and a BITMAP face, so it has to be stood in
 * for, and the question is what it was a bitmap OF. It is a neo-grotesque:
 * Helvetica's skeleton, hand-fitted to a pixel grid at 8, 10 and 12pt, and
 * when Microsoft needed a scalable version of the same typeface they drew
 * Microsoft Sans Serif — the same letterforms as outlines, metrically
 * compatible with Arial. So the honest stand-in is an Arial-metric
 * grotesque, which is what Arimo is (it is Liberation Sans, drawn to
 * Arial's widths), and it sets a dialog in what looks like the original's
 * own type.
 *
 * **This replaced a pixel face, and that was the wrong idea twice over.**
 * The theme was set first in Pixelify Sans, then Jersey 10, then Tiny5, all
 * on the reasoning that a bitmap face should be stood in for by a face made
 * of visible pixels. But MS Sans Serif's pixels were never the POINT of it
 * — they were the constraint it was drawn under, on a 640x480 screen where
 * a pixel was a visible thing. At 400dpi that constraint is gone, and a
 * face built out of deliberate stairs is not the same typeface rendered
 * faithfully, it is a different typeface that quotes the hardware. It reads
 * as an arcade cabinet or a games menu, which is the one thing Windows 98's
 * interface was trying not to be. The pixels this theme keeps are the ones
 * that carried MEANING — the bevels, and the 16px icon set — and its type
 * is the type, at the resolution the phone actually has.
 *
 * It also solves what the last swap was for: Arimo carries 3010 glyphs
 * including the full Cyrillic block, where Jersey 10 had 332 and no
 * Cyrillic at all and set a Russian bookmark title as a row of tofu.
 *
 * ONE WEIGHT still, and now by choice rather than by constraint: Windows 98
 * set its menus, its list rows, its labels and its tooltips in one weight of
 * one face at one size, and what a heading has instead of boldness is size
 * and the bevel around whatever it heads.
 */
val Ninety8Family = FontFamily(Font(R.font.arimo, weight = FontWeight.Normal))

/**
 * The type scale: Material's own, in one face at one weight.
 *
 * There is no size correction here any more, and its absence is the point.
 * While this theme was set in a pixel face the ladder had to be multiplied
 * by a measured `PIXEL_SIZE_PARITY` — 1.21 for Jersey 10, 1.04 for Tiny5 —
 * because those faces keep much less of the em box than the app's ordinary
 * type does, so a nominal 15sp row came out reading like 12sp and the whole
 * theme had quietly shrunk. Arimo's x-height is 52.8 units per 100 against
 * Roboto's 52, and it sets "Bookmarks" in 500 units against Roboto's 499:
 * it is the same optical size and the same width at the same nominal sp, so
 * the correct factor is 1.0 and the mechanism is gone rather than left in
 * as a multiplication by one.
 *
 * That parity is also what makes this face safe in the layouts: every row,
 * sheet and tile in the app is measured around type of these proportions,
 * the menu's quick-tile labels — the tightest text here — included.
 *
 * Tracking is Material's now too. It was forced to zero for the pixel
 * faces, whose sidebearings are whole pixels of their own grid and which
 * land on fractions of one the moment they are tracked; an outline face has
 * no such grid to fall off.
 *
 * Every role is FontWeight.Normal, headings included — see [Ninety8Family].
 *
 * There is no case change either (`SpecialText` gets `AsIs`): Windows 98
 * wrote its menus in sentence case — "Save as...", "Print preview" — and its
 * labels in the same sentence case as its titles. Casing them up would be
 * another interface's habit wearing this one's clothes.
 */
internal val Ninety8Typography: Typography = Typography().let { t ->
    val ui = Ninety8Family
    fun TextStyle.system() = copy(fontFamily = ui, fontWeight = FontWeight.Normal)
    t.copy(
        displayLarge = t.displayLarge.system(),
        displayMedium = t.displayMedium.system(),
        displaySmall = t.displaySmall.system(),
        headlineLarge = t.headlineLarge.system(),
        headlineMedium = t.headlineMedium.system(),
        headlineSmall = t.headlineSmall.system(),
        titleLarge = t.titleLarge.system(),
        titleMedium = t.titleMedium.system(),
        titleSmall = t.titleSmall.system(),
        bodyLarge = t.bodyLarge.system(),
        bodyMedium = t.bodyMedium.system(),
        bodySmall = t.bodySmall.system(),
        labelLarge = t.labelLarge.system(),
        labelMedium = t.labelMedium.system(),
        labelSmall = t.labelSmall.system(),
    )
}

/**
 * The four edge colours a bevel is drawn from, at whichever end of the theme
 * the chrome currently is.
 *
 * Read through [bevel98Colors] rather than taken off the scheme at each call
 * site: `outline` and `outlineVariant` carry two of these four, but the other
 * two are the white and the near-black that only this theme has a use for,
 * and a bevel drawn from three sources would come apart the moment one of
 * them moved.
 */
internal data class Bevel98Colors(
    /** The outer lit edge — top and left of a raised control. */
    val hilight: Color,
    /** The inner lit edge, one step down from it. */
    val light: Color,
    /** The inner cast edge — bottom and right. */
    val shadow: Color,
    /** The outer cast edge, the darkest of the four. */
    val dark: Color,
)

private val Bevel98Light = Bevel98Colors(
    hilight = Silver.hilight,
    light = Silver.faceLight,
    shadow = Silver.shadow,
    dark = Silver.darkShadow,
)

private val Bevel98Dark = Bevel98Colors(
    hilight = Silver.hilightDark,
    light = Silver.faceLightDark,
    shadow = Silver.shadowDark,
    dark = Silver.darkShadowDark,
)

/**
 * The bevel palette for the end of the theme the chrome is at, interpolated
 * across the light-to-dark turn like everything else.
 *
 * Reads [LocalChromeDarkness], which changes every frame of that turn — so
 * this recomposes its caller for the ~300ms the turn takes, and only then.
 * Kept out of [BrowserPalette] on purpose: four more fields on the app-wide
 * palette, three themes of which would never read them, is a worse trade
 * than one composable that only this theme's call sites ask for.
 */
@Composable
internal fun bevel98Colors(): Bevel98Colors {
    val t = LocalChromeDarkness.current
    if (t <= 0f) return Bevel98Light
    if (t >= 1f) return Bevel98Dark
    return Bevel98Colors(
        hilight = lerp(Bevel98Light.hilight, Bevel98Dark.hilight, t),
        light = lerp(Bevel98Light.light, Bevel98Dark.light, t),
        shadow = lerp(Bevel98Light.shadow, Bevel98Dark.shadow, t),
        dark = lerp(Bevel98Light.dark, Bevel98Dark.dark, t),
    )
}

/** Which way the light falls on an edge — see [Modifier.bevel98]. */
enum class Bevel {
    /** A button, a card, a panel: lit top-left, cast bottom-right. */
    Raised,

    /** A text field, a well, a pressed button: the same edges, swapped. */
    Sunken,

    /**
     * One band instead of two — a status bar's divider, a toolbar's own top
     * edge, a group box. Windows draws these wherever a full button edge
     * would be too much furniture for what is only a change of surface.
     */
    RaisedThin,

    /** [RaisedThin], swapped. */
    SunkenThin,
}

/**
 * The theme's one drawing primitive: a two-tone edge that makes whatever it
 * is applied to read as raised out of the surface or sunk into it.
 *
 * Windows draws it as four lines per band, two bands deep. Outermost on a
 * raised control is white along the top and left and near-black along the
 * bottom and right; inside that, [Bevel98Colors.light] and
 * [Bevel98Colors.shadow] repeat the pair one step closer to the face. A
 * sunken control is the identical construction with the two sides exchanged,
 * which is why one function draws both: they are not two effects, they are
 * one effect and its negative.
 *
 * Drawn AFTER the content (`drawWithContent`) rather than as a border under
 * it, because the edge belongs to the control rather than to what is inside
 * it — a fill, a page preview or a selection highlight painted to the same
 * bounds would otherwise cover it.
 *
 * A band is [BEVEL_BAND] — a real dp rather than a hairline. The original is
 * one physical pixel because in 1998 a pixel was a visible thing; at 400dpi
 * one is invisible, and a bevel you cannot see is a flat edge with a cost.
 *
 * [inset] pulls the whole edge in from the bounds, which is how a control
 * is drawn SMALLER than the box that catches the touch. The toolbar's
 * buttons use it: a 48dp target is the accessibility floor and a 48dp button
 * in a 56dp bar leaves 4dp of bar above it, which reads as a button jammed
 * against the toolbar's own top edge rather than sitting in it. The inset
 * moves the drawing, never the target.
 *
 * No corners are mitred and none should be: Windows butts these lines, which
 * leaves the top-left and bottom-right corners in one colour and the other
 * two corners in whichever line was drawn second. That asymmetry is a real
 * part of the look — it is what a bevel drawn by a loop over four rects
 * looks like — and rounding it off would be tidying away the thing itself.
 */
@Composable
fun Modifier.bevel98(
    style: Bevel = Bevel.Raised,
    inset: Dp = 0.dp,
): Modifier {
    val colors = bevel98Colors()
    val thin = style == Bevel.RaisedThin || style == Bevel.SunkenThin
    val flipped = style == Bevel.Sunken || style == Bevel.SunkenThin
    // The lit edge and the cast one, for the outer band. A thin bevel is a
    // single band and takes the QUIETER of the two cast colours — white
    // against grey rather than white against near-black, which at one band's
    // depth reads as an edge instead of as a hairline drawn in two colours.
    val lit = colors.hilight
    val cast = if (thin) colors.shadow else colors.dark
    return this.drawWithContent {
        drawContent()
        val band = BEVEL_BAND.toPx()
        val pad = inset.toPx()
        drawBevelBand(if (flipped) cast else lit, if (flipped) lit else cast, pad, band)
        if (!thin) {
            drawBevelBand(
                if (flipped) colors.shadow else colors.light,
                if (flipped) colors.light else colors.shadow,
                pad + band,
                band,
            )
        }
    }
}

/**
 * One band of a bevel: [topLeft] along the top and left edges, [bottomRight]
 * along the bottom and right, [inset] in from the bounds, [band] thick.
 *
 * The lines are butted rather than mitred — see [Modifier.bevel98] — so the
 * top runs the full width and the left starts under it.
 */
private fun DrawScope.drawBevelBand(
    topLeft: Color,
    bottomRight: Color,
    inset: Float,
    band: Float,
) {
    val w = size.width
    val h = size.height
    drawRect(topLeft, Offset(inset, inset), Size(w - inset * 2, band))
    drawRect(topLeft, Offset(inset, inset + band), Size(band, h - inset * 2 - band))
    drawRect(bottomRight, Offset(inset, h - inset - band), Size(w - inset * 2, band))
    drawRect(bottomRight, Offset(w - inset - band, inset), Size(band, h - inset * 2 - band))
}

/**
 * [Modifier.bevel98] under the 98 theme and nothing at all under any other —
 * the form every call site outside this file actually wants.
 *
 * A bevel is not an effect a surface can wear in the other looks: the app's
 * own theme says depth with a shadow, Nothing says it with an outline, and
 * TUI does not say it. So the branch belongs here rather than at each of the
 * dozen surfaces that draw one, exactly as `specialCorner` owns the corner
 * branch and `SpecialText` the casing one.
 */
@Composable
fun Modifier.bevel98If(
    style: Bevel = Bevel.Raised,
    inset: Dp = 0.dp,
): Modifier =
    if (LocalNinety8.current) bevel98(style, inset) else this

/** How thick one band of a bevel is drawn — see [Modifier.bevel98]. */
val BEVEL_BAND = 1.5.dp

/**
 * The period's drop shadow: a solid rectangle offset down and to the right,
 * with no blur in it at all.
 *
 * Windows 98 put one under menus and tooltips and nowhere else, and it is
 * not a light effect — it is a second rectangle in black, which is why it
 * has a hard edge and no falloff. Here it goes under the surfaces that float
 * over the page for the same reason Windows used it: those are the things
 * that are ABOVE the interface rather than part of it, and everything else
 * gets its depth from a bevel instead.
 *
 * Drawn behind, so it costs one rect and never touches the content.
 *
 * @param offset how far down and right the shadow falls. One value for both
 * axes, because the light in this system comes from the top left at 45°.
 */
fun Modifier.hardShadow98(offset: Dp = 4.dp, color: Color = Color.Black.copy(alpha = 0.45f)): Modifier =
    this.drawBehind {
        val d = offset.toPx()
        drawRect(color, Offset(d, d), size)
    }

/**
 * The Windows 98 answer to a switch, which is a CHECKBOX: a sunken white
 * well with a black tick in it.
 *
 * Not a switch redrawn in period costume, because there was no switch to
 * redraw — a sliding control in this system is a scrollbar, and a scrollbar
 * means position, not state. The checkbox is what every Options dialog used
 * for exactly this, and it says the same thing this app's switches say.
 *
 * The tick is drawn rather than set as a glyph: Windows' check is a specific
 * mark, a short arm and a long one meeting at a right-ish angle with square
 * ends, and it is the one place in this theme where getting the shape wrong
 * would be noticed by anyone who used the thing.
 *
 * No ripple, and no travel to animate: it is checked or it is not. The
 * haptic is left to [MenuSwitch], which owns it for every theme.
 */
@Composable
internal fun Ninety8Switch(checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val tick = if (!enabled) InkFaint else Ink
    Box(
        modifier = Modifier
            .size(NINETY8_CHECKBOX)
            .background(if (enabled) FieldBg else MaterialTheme.colorScheme.surface)
            .bevel98(Bevel.Sunken)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                // A ripple is a Material idea about touch spreading out from
                // a finger; this control is 24dp of square well and a
                // circular wash inside it is the one round thing on screen.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(NINETY8_CHECK)) {
                val w = size.width
                val h = size.height
                val arm = w * 0.2f
                // Square ends, and the two arms overlap at the elbow rather
                // than joining: this mark is two runs of pixels in the
                // original, not one path with a corner in it.
                drawLine(tick, Offset(w * 0.06f, h * 0.5f), Offset(w * 0.4f, h * 0.84f), arm, StrokeCap.Butt)
                drawLine(tick, Offset(w * 0.34f, h * 0.86f), Offset(w * 0.96f, h * 0.16f), arm, StrokeCap.Butt)
            }
        }
    }
}

/** The well. Windows' is 13x13 device pixels; this is that at a phone's scale. */
private val NINETY8_CHECKBOX = 26.dp

/** The tick inside it, with the well's own bevel left clear around it. */
private val NINETY8_CHECK = 15.dp
