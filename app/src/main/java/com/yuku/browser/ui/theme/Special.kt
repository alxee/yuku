package com.yuku.browser.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which whole-look override (see `SpecialTheme`) is in force for this part of
 * the tree — the two booleans every call site actually branches on.
 *
 * Composition locals rather than global flags because a subtree can still
 * re-provide the theme for its own reasons — Settings re-provides it to drop
 * the private space's violet, since that is the screen where the ordinary
 * accent is chosen. The special look itself is carried THROUGH that
 * re-provision: it is the user's pick, not a mode any screen opts out of.
 *
 * Read the shape rules through [specialCorner], [SpecialCircle] and the
 * `Text` wrappers in `SpecialText.kt` rather than branching at each call
 * site; those are what "no round corners" / "lowercase" (TUI) and "capped
 * corners" / "uppercase" (Nothing) actually reduce to.
 */
val LocalTui = compositionLocalOf { false }

/** @see LocalTui */
val LocalNothing = compositionLocalOf { false }

/** @see LocalTui */
val LocalNinety8 = compositionLocalOf { false }

/** @see LocalTui */
val LocalAero = compositionLocalOf { false }

/**
 * Translucent sheets are on AND in force: the setting, under the Default or
 * Nothing look, on a device that can frost (API 33). Sheets, the toolbar, the
 * find bar and the controls on them read this; see `frostedSheetGlass`.
 */
val LocalFrosted = compositionLocalOf { false }

/**
 * The Translucency slider, 0 (nearly transparent) to 1 (nearly solid). Read
 * through `frostedSheetFill` and `FROSTED_ELEMENT_ALPHA`; see there.
 */
val LocalFrostOpacity = compositionLocalOf { com.yuku.browser.core.DEFAULT_TRANSLUCENCY }

/**
 * The corner radius a call site asks for, as each special theme would have
 * it — the one place "how angular is this" is decided.
 *
 * TUI takes every corner to zero, and so does 98 — a rounded rectangle is
 * not a shape either of those interfaces could draw, and in 98's case the
 * corner is where its two bevel edges meet, which a radius has nowhere to
 * put. Nothing caps the corner instead: its cards are technical rather than
 * square, and past ~16dp a surface stops reading as a machined part and
 * starts reading as a bubble.
 *
 * Aero is the only one that goes the other way and GAINS radius, which is
 * the same sentence read from the far end: a bubble is exactly what that
 * look is after. Aqua's control is a lozenge and Aero's is a blown, wet
 * object, and the one shape neither can have is a tight corner — glass with
 * a 4dp radius reads as a cut sheet rather than as something moulded. The
 * floor matters more than the gain: it is what lifts the app's small radii
 * (a 4dp chip, an 8dp row) into the range where a highlight can run round
 * the corner instead of stopping at it.
 *
 * Returns a [RoundedCornerShape] rather than
 * [androidx.compose.ui.graphics.RectangleShape] so callers that reshape it
 * (`.copy(bottomStart = ...)`) keep working, and so a shape animated from a
 * radius simply animates from zero.
 */
@Composable
fun specialCorner(size: Dp): RoundedCornerShape = RoundedCornerShape(
    when {
        LocalTui.current || LocalNinety8.current -> 0.dp
        LocalNothing.current -> size.coerceAtMost(NOTHING_MAX_CORNER)
        LocalAero.current -> aeroCornerOf(size)
        else -> size
    }
)

/**
 * Aero's radius for a nominal one — the gain and the floor in ONE place,
 * because two things have to agree about it exactly: the shape a surface is
 * CLIPPED to, and the rim `Modifier.aeroGlass` draws round it.
 *
 * They did not agree at first, and it was visible immediately: a call site
 * passing `SHEET_CORNER` got a 37.8dp clip from [specialCorner] and a 28dp
 * rim from the glass, so the highlight cut across the corner instead of
 * running round it. A draw modifier cannot ask its node what shape it was
 * given (see `aeroGlass`), so the only fix is that both sides put the same
 * nominal number through the same function — which is this one.
 *
 * Zero stays zero rather than taking the floor: a full-bleed surface (the
 * toolbar, the loading bar) asks for no corner at all, and rounding one onto
 * it would put a curve on an edge that runs off the screen.
 */
internal fun aeroCornerOf(size: Dp): Dp =
    if (size <= 0.dp) 0.dp else (size * AERO_CORNER_GAIN).coerceAtLeast(AERO_MIN_CORNER)

/**
 * The percentage overload. Nothing leaves these alone: a percentage corner is
 * how this app writes "a pill", and pills are the shape Nothing's own buttons
 * are — capping them in dp is exactly the wrong move. 98 squares them off
 * with the rest: this system has no pill anywhere in it. Aero leaves them
 * alone too, and for the reverse of Nothing's reason: a pill is already the
 * shape this look is trying to get every other corner to, so there is
 * nothing left to gain.
 */
@Composable
fun specialCorner(percent: Int): RoundedCornerShape =
    RoundedCornerShape(percent = if (LocalTui.current || LocalNinety8.current) 0 else percent)

/**
 * How much of a corner radius survives, as a multiplier — for the handful of
 * radii computed in the layout/draw phase (a card's corner shrinking with a
 * live scale), where a shape can't be asked for in composition. Read this in
 * composition and fold it into the number.
 *
 * Aero's gain reaches here but its FLOOR does not — a multiplier cannot
 * express one. That costs nothing in practice: every radius computed in the
 * draw phase is a card's (16dp and up), which is a long way clear of
 * [AERO_MIN_CORNER], so the drawn corner and the shaped one agree wherever
 * they are actually seen together — which is the whole of what this constant
 * is for.
 */
val SpecialCornerScale: Float @Composable get() = when {
    LocalTui.current || LocalNinety8.current -> 0f
    LocalAero.current -> AERO_CORNER_GAIN
    else -> 1f
}

/** [CircleShape], squared off under the TUI and 98 themes; Nothing keeps its ovals. */
val SpecialCircle: Shape @Composable
    get() = if (LocalTui.current || LocalNinety8.current) RoundedCornerShape(0.dp) else CircleShape

/**
 * The accent thinned to a WASH — a tint behind or beneath something, rather
 * than a mark drawn in the accent itself.
 *
 * The one use of colour the Nothing theme refuses. A yellow glyph or a yellow
 * track is that theme working as intended: colour as an interrupt, spent on
 * one thing. A yellow spread thinly under a tile, a chip or a whole empty
 * canvas is the opposite — a hue lying across the canvas, which is what
 * "monochrome is the canvas" rules out — so there the accent gives way to
 * the ink and the emphasis is carried by value instead.
 *
 * Every other theme gets exactly what it always got.
 */
@Composable
fun accentWash(alpha: Float): Color =
    if (LocalNothing.current) Ink.copy(alpha = alpha) else AccentColor.copy(alpha = alpha)

/** The largest corner Nothing draws: see [specialCorner]. */
internal val NOTHING_MAX_CORNER = 16.dp

/** How much rounder Aero draws every corner, and the least it will draw one
 * at — see [specialCorner]. */
internal const val AERO_CORNER_GAIN = 1.35f
internal val AERO_MIN_CORNER = 10.dp


/**
 * Every Material shape role squared off. Components this app doesn't shape
 * itself — a Slider's track, a Switch's thumb, the Material dialogs and
 * menus — take their corners from here, so without this a special theme
 * would be its own shape everywhere the app draws and Material's everywhere
 * Material does.
 */
internal val OrdinaryShapes = Shapes()

internal val TuiShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/**
 * 98's Material shapes, which are the TUI's: both interfaces square every
 * corner, and two identical Shapes objects would be two things to keep the
 * same rather than one fact. Named separately so a call site reads as the
 * theme it is about, and so the day one of them stops being square there is
 * somewhere to say so.
 */
internal val Ninety8Shapes = TuiShapes

/**
 * The same roles, blown out — see [specialCorner]. Material's own ladder put
 * through Aero's gain and floor, so a component this app does not shape
 * itself (a menu, a dialog, a Slider's thumb) is the same lozenge as
 * everything the app does shape.
 */
internal val AeroShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

/** The same roles, capped rather than squared — see [specialCorner]. */
internal val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(NOTHING_MAX_CORNER),
    extraLarge = RoundedCornerShape(NOTHING_MAX_CORNER),
)
