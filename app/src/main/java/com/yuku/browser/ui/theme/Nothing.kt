// `Font(..., variationSettings = ...)` and `FontVariation` are still marked
// experimental; they are how a variable font is instantiated at a weight, and
// the app already relies on the same API for the placeholder headline.
@file:OptIn(ExperimentalTextApi::class)

package com.yuku.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yuku.browser.R

/**
 * The Nothing theme's three faces, and the rule that decides between them.
 *
 * The stack is the one the `nothing-design` skill names, and the reason it
 * names it is provenance rather than taste: **Space Grotesk and Space Mono
 * are Colophon Foundry's, the same house that drew Nothing's own
 * typefaces**, so the sans, the monospace and the real thing share their
 * design DNA. Doto is the OFL stand-in for NDot 57, which is proprietary and
 * not ours to ship.
 *
 * The split between them is by SIZE, and it is a hard line rather than a
 * preference:
 *
 * - **Doto — display sizes only, 36sp and up.** A dot-matrix face is a grid
 *   of holes. At 36sp and above the grid is coarse enough that you read
 *   letterforms with a texture; below it you read texture with letterforms
 *   somewhere in it, and the word comes out looking a size or two smaller
 *   and a shade greyer than the plain text beside it. That is the whole of
 *   why the dot face "looked too small" everywhere it was used: it was being
 *   asked to set 14sp list-row titles, which is a size it cannot carry. The
 *   fix is not a bigger dot face — it is the sans, which is what a heading
 *   is set in.
 * - **Space Grotesk — every heading, every title, all body copy.**
 * - **Space Mono — labels, and only labels**, which is also the signal to
 *   case them up (see `SpecialText`): the mono face IS the label face here,
 *   so "set in the mono face" and "is a label" are one fact and there is no
 *   second list of which call sites count.
 *
 * Weights are on a budget, as the skill has it — two per family at most.
 * Doto sits at 600, the sans at 400 for body and 500 for anything above it,
 * the mono at 400. Hierarchy is carried by size and spacing; weight is not
 * asked to help.
 */
// Doto — the variable dot-matrix face (SIL OFL, google/fonts), ROND axis at
// the top so the grid reads as bored holes rather than as square pixels,
// which is the difference between this and the TUI theme's terminal.
//
// Only two instances, because it now only sets two or three lines in the
// whole app. Weight in a dot face is EXPOSURE — how much of each cell the
// hole fills — not emphasis, and at display sizes 600 is where the holes
// look bored rather than pricked.
val NothingDotFamily = FontFamily(
    Font(R.font.doto, weight = FontWeight.Medium, variationSettings = dotVariation(500)),
    Font(R.font.doto, weight = FontWeight.SemiBold, variationSettings = dotVariation(600)),
)

/**
 * NType 82 — the headline face, and the only place in this theme where a
 * face is Nothing's own rather than a stand-in for one. It sets the four
 * HEADER roles (headlineLarge/Medium/Small and titleLarge) and nothing else:
 * everything under them is Geist, which is the system face and the one that
 * has to hold up in dense rows.
 *
 * It ships as a single static cut (CFF, no variable axes), so the family
 * declares ONE weight and the header roles ask for exactly that. Asking for
 * Medium would get a synthesised fake-bold — a smeared outline of a face
 * chosen for its outline. It is declared here as Normal whatever its own
 * OS/2 says (the file stamps itself 100, Thin, under a name of Regular):
 * what matters is that the request and the declaration agree, since that is
 * the whole of what decides whether Compose synthesises.
 *
 * **Its cmap is 218 codepoints — Latin only, no Cyrillic and no Greek.** A
 * custom Typeface gets no system fallback chain for a glyph it lacks, so
 * anything set in it outside Latin comes up as tofu. That is survivable
 * exactly as long as these four roles set APP STRINGS, which are the
 * headings and section titles; the moment one of them is pointed at page or
 * bookmark text it is pointed at every script at once. This is the hazard
 * the 98 theme already paid for once with Jersey 10.
 */
val NothingHeaderFamily = FontFamily(
    Font(R.font.ntype_82, weight = FontWeight.Normal),
)

/** Geist is the current Nothing OS system face: clear in dense, everyday UI. */
val GeistSansFamily = FontFamily(
    Font(R.font.geist, weight = FontWeight.Light, variationSettings = weightVariation(300)),
    Font(R.font.geist, weight = FontWeight.Normal, variationSettings = weightVariation(400)),
    Font(R.font.geist, weight = FontWeight.Medium, variationSettings = weightVariation(500)),
    Font(R.font.geist, weight = FontWeight.Bold, variationSettings = weightVariation(700)),
)

/** The matching mono is reserved for compact labels and numeric readouts. */
val GeistMonoFamily = FontFamily(
    Font(R.font.geist_mono, weight = FontWeight.Normal, variationSettings = weightVariation(400)),
    Font(R.font.geist_mono, weight = FontWeight.Medium, variationSettings = weightVariation(500)),
    Font(R.font.geist_mono, weight = FontWeight.Bold, variationSettings = weightVariation(700)),
)

/** Nothing now uses the current Geist system type stack throughout. */
val NothingSansFamily = GeistSansFamily
val NothingMonoFamily = GeistMonoFamily

private fun dotVariation(weight: Int) =
    FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 100f))

private fun weightVariation(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/**
 * The theme's greys — **and they are greys, at exactly zero saturation.**
 *
 * They were warm once: an off-white page at hue 45 and a ramp above it that
 * got warmer as it darkened, up to 7% saturation at the outline. The comment
 * here used to claim the greys were spaced by distance from the page rather
 * than by hue and that the theme had one hue, the yellow below. Both halves
 * of that were false at the same time — a 7%-saturated warm grey IS a hue,
 * and spread across every surface, every hairline and every muted ink it is
 * the largest hue on the screen by area, which put a beige cast over a look
 * whose whole premise is that colour is an interrupt. The skill's own light
 * ramp is 0% throughout for this reason, and so is Nothing's.
 *
 * Neutralised by LUMINANCE, not by eye: each value is the grey with the same
 * relative luminance as the colour it replaces, so every contrast ratio in
 * the theme is exactly what it was and only the chroma is gone. That is also
 * why they are not round numbers.
 *
 * The hue that survives is the accent, the error red and the status green —
 * three colours, all of them marks on things rather than the things
 * themselves.
 */
private object Dot {
    // The canvas is the ORDINARY theme's canvas, tone for tone: neutral
    // tone 98 for the page, 96/92/90 above it, which is exactly what
    // `tonalColorScheme` builds for every other look. The theme's premise is
    // monochrome, not dim — a ramp started a few points down read as a
    // greyer, duller version of the same app rather than as a different one.
    val paper = Color(0xFFFAFAFA)
    val paperDim = Color(0xFFF2F2F2)
    val paperSunk = Color(0xFFEBEBEB)
    val paperRaised = Color(0xFFE0E0E0)
    val ink = Color(0xFF141414)
    val inkSoft = Color(0xFF3B3B3B)
    val inkQuiet = Color(0xFF6D6D6D)
    val inkFaint = Color(0xFF979797)
    val ruleSoft = Color(0xFFD1D1D1)
    val rule = Color(0xFFB9B9B9)

    /**
     * The dark end used to be OLED black — a true zero — with the raised
     * tones a few points above it (#0C0C0C, #161616). Two things were wrong
     * with that at once. It sat a whole step below every other look's dark
     * ground, so the theme read as DIM rather than as monochrome; and the
     * step from the ground to a sheet was four values wide, which on a phone
     * screen is no step at all — the page behind and the sheet in front were
     * both simply black, and a sheet that cannot be told from what it covers
     * has lost the only thing its surface is for.
     *
     * The fix is the SEPARATION, not the level, and the level is the part
     * that keeps being got wrong: every attempt to buy the step by lifting
     * the ground (tone 10, then 5) traded the look for it — the canvas came
     * back as plain grey, which is the one thing a near-black monochrome
     * theme cannot be. So the ground is as close to black as it can be
     * WITHOUT being black, and the ramp above it is compressed hard.
     *
     * Not a true zero, and that is a display fact rather than a taste. A
     * black pixel on OLED is a pixel switched off, and switching it back on
     * lags: scrolling light content across a field of #000000 leaves the
     * smear those panels are known for, and white type on a true zero blooms
     * for anyone with astigmatism. The conventional answer is Material's
     * #121212, which is a long way up and reads as the dark grey this look
     * refuses; the narrow one is a value in the low single digits — dark
     * enough to be read as black, lit enough that the pixel is never fully
     * off. That is where the ground sits.
     *
     * It sits at THREE rather than the five that guidance usually names,
     * because the value buys two things that behave differently. Against the
     * smear it is all-or-nothing — any drive at all clears it, and 3 is as
     * much a lit pixel as 5. The other artefact of that band is not:
     * a screen of pixels all lit at their lowest drive is where an OLED's
     * per-pixel binning shows, so the field comes up slightly uneven — worst
     * at the edges and corners — and that unevenness scales with the drive.
     * Three keeps the pixel on at about half the mura. (The render itself is
     * flat to well under one 8-bit level, measured off a framebuffer
     * capture, so anything visible in that field is the panel and not this
     * ramp.)
     *
     * Above it the steps are SMALL, because a step measured against black is
     * read as a colour rather than as a height: at #1A1A1A (and even at
     * #141414) a sheet covering most of the screen stopped being a surface
     * over the page and became a grey screen replacing it. The sheet is
     * eight values up, and what actually separates it from the page is not
     * that height — it is the scrim under it (a true black wash, which takes
     * the page DOWN rather than the sheet up) and the hairline around it.
     * Both of those are free of the grey the ramp costs.
     */
    val void = Color(0xFF030303)

    /** A hair off the ground: the switcher's canvas, see [NothingEmptyDark].
     * Low enough to stay under a dark page's own thumbnail, not the page's
     * own value, because the surface the cards sit on is the one place the
     * theme is read as an empty field rather than past something. */
    val voidLift = Color(0xFF050505)
    val voidRaised = Color(0xFF0D0D0D)
    val voidSunk = Color(0xFF151515)
    val voidHigh = Color(0xFF1E1E1E)

    /** True black, and the one role that wants it: the scrim behind a sheet.
     * It is the theme's main tool for separating a surface from the page now
     * that the ramp above the ground is only a few values tall — it takes the
     * PAGE down instead of lifting the sheet, which costs no grey. */
    val black = Color(0xFF000000)
    val glow = Color(0xFFF6F6F6)
    val glowSoft = Color(0xFFD0D0D0)
    val glowQuiet = Color(0xFF979797)
    val glowFaint = Color(0xFF646464)
    // They track the grounds under them — a hairline has to clear the
    // surface it is drawn on — but they do NOT come all the way down with
    // this ramp: with the surfaces only a few values apart, the line IS the
    // separation, so it keeps the contrast the compressed ramp gave up.
    val wireSoft = Color(0xFF252525)
    val wire = Color(0xFF3A3A3A)

    /**
     * The one colour in the theme: a warm yellow rather than a lemon. Not a
     * step in the hierarchy — it is an interrupt — so it is spent on the
     * accent and on nothing else, and the dark end takes it a little
     * brighter and paler because a warm yellow at full saturation on true
     * black reads as amber signage.
     *
     * It carries the warmth on its own now that the greys under it do not,
     * which is the arrangement that makes it read as an interrupt at all:
     * one warm mark on a neutral ground is an event, and the same mark on a
     * ground that was already leaning the same way was just the warmest
     * thing in a warm room.
     *
     * Unlike the red it replaced, it is a LIGHT colour: whatever sits on it
     * has to be the ink, not the paper, at both ends — see [onAmber].
     */
    val amber = Color(0xFFE3A81C)
    val amberGlow = Color(0xFFF5C64A)

    /** What is drawn ON the accent. Dark at both ends: the accent is light. */
    val onAmber = Color(0xFF171410)

    /**
     * Red survives as exactly one thing — an error — which is what a second
     * hue is for in a monochrome canvas. It is not the accent any more, so
     * nothing but a failure is drawn in it.
     */
    val red = Color(0xFFD71921)
    val redGlow = Color(0xFFE8323B)

    /** Status, not chrome: the one place a second hue is honest. */
    val greenLight = Color(0xFF3F7D4F)
    val greenDark = Color(0xFF77B98A)
}

/**
 * Nothing as a Material scheme. Hand-mapped, like the TUI one and for the
 * same reason: a tonal scheme seeded from the yellow would tint all sixteen
 * greys with it, and a monochrome canvas with a warm cast in the greys is
 * exactly what this look is not. Only `primary` (and the containers that
 * follow it) is allowed the hue.
 */
internal val NothingLightScheme: ColorScheme = lightColorScheme(
    primary = Dot.amber,
    onPrimary = Dot.onAmber,
    primaryContainer = Dot.paperSunk,
    onPrimaryContainer = Dot.ink,
    secondary = Dot.inkSoft,
    onSecondary = Dot.paper,
    secondaryContainer = Dot.paperSunk,
    onSecondaryContainer = Dot.ink,
    tertiary = Dot.inkQuiet,
    onTertiary = Dot.paper,
    tertiaryContainer = Dot.paperSunk,
    onTertiaryContainer = Dot.ink,
    background = Dot.paper,
    onBackground = Dot.ink,
    surface = Dot.paper,
    onSurface = Dot.ink,
    surfaceVariant = Dot.paperSunk,
    onSurfaceVariant = Dot.inkSoft,
    // NOT the accent: `surfaceTint` is what Material mixes into a surface as
    // it rises, and every sheet and menu in the app is a raised surface — an
    // accent here is a hue across the whole canvas, which is the one thing
    // this look does not do. Pointed back at the surface, elevation stops
    // changing the colour at all.
    surfaceTint = Dot.paper,
    inverseSurface = Dot.void,
    inverseOnSurface = Dot.glow,
    inversePrimary = Dot.amberGlow,
    error = Dot.red,
    onError = Dot.paper,
    errorContainer = Dot.paperSunk,
    onErrorContainer = Dot.red,
    outline = Dot.rule,
    outlineVariant = Dot.ruleSoft,
    scrim = Dot.black,
    surfaceBright = Dot.paper,
    surfaceDim = Dot.paperRaised,
    surfaceContainerLowest = Dot.paper,
    surfaceContainerLow = Dot.paperDim,
    surfaceContainer = Dot.paperDim,
    surfaceContainerHigh = Dot.paperSunk,
    surfaceContainerHighest = Dot.paperRaised,
)

internal val NothingDarkScheme: ColorScheme = darkColorScheme(
    primary = Dot.amberGlow,
    onPrimary = Dot.onAmber,
    primaryContainer = Dot.voidHigh,
    onPrimaryContainer = Dot.glow,
    secondary = Dot.glowSoft,
    onSecondary = Dot.void,
    secondaryContainer = Dot.voidSunk,
    onSecondaryContainer = Dot.glow,
    tertiary = Dot.glowQuiet,
    onTertiary = Dot.void,
    tertiaryContainer = Dot.voidSunk,
    onTertiaryContainer = Dot.glow,
    background = Dot.void,
    onBackground = Dot.glow,
    surface = Dot.void,
    onSurface = Dot.glow,
    surfaceVariant = Dot.voidSunk,
    onSurfaceVariant = Dot.glowSoft,
    surfaceTint = Dot.void,
    inverseSurface = Dot.paper,
    inverseOnSurface = Dot.ink,
    inversePrimary = Dot.amber,
    error = Dot.redGlow,
    onError = Dot.glow,
    errorContainer = Dot.voidSunk,
    onErrorContainer = Dot.redGlow,
    outline = Dot.wire,
    outlineVariant = Dot.wireSoft,
    scrim = Dot.black,
    surfaceBright = Dot.voidHigh,
    surfaceDim = Dot.void,
    surfaceContainerLowest = Dot.void,
    surfaceContainerLow = Dot.voidRaised,
    surfaceContainer = Dot.voidRaised,
    surfaceContainerHigh = Dot.voidSunk,
    surfaceContainerHighest = Dot.voidHigh,
)

/**
 * Nothing 2 keeps the same independent monochrome canvas, but reduces the
 * number of raised tones. The result is a calmer, flatter field where an
 * outline and the user's selected accent carry state instead of stacked
 * containers or a decorative dot texture.
 */
/**
 * The two quiet inks. Same argument as the TUI theme's (see
 * [TuiInks]): `outline`/`outlineVariant` are picked to draw
 * hairlines on this canvas and are too faint to read as secondary TEXT on
 * it, so the ink steps in from the grey ramp instead and the lines stay
 * where they are.
 */
internal val NothingInkMutedLight = Dot.inkQuiet
internal val NothingInkMutedDark = Dot.glowQuiet
internal val NothingInkFaintLight = Dot.inkFaint
internal val NothingInkFaintDark = Dot.glowFaint

/**
 * The switcher's ground and the empty screen's — one surface. Every other
 * look takes the app's own answer for it (`surfaceContainerLow`, a shade off
 * the toolbar); this theme takes the PAGE's ground instead, which at the
 * dark end is a true zero.
 *
 * The reason is what stands on it: a switcher card is a picture of a page,
 * and a dark page's thumbnail is black or a few values off it. On the
 * raised #1A1A1A the theme uses for its toolbar and sheets, those cards were
 * within a handful of values of the ground they were standing on, so the
 * canvas and the previews ran together and the row read as one dark field
 * with corners in it. Against zero the ground is by construction the
 * darkest thing on screen and every card has an edge. The toolbar keeps the
 * raised tone, so it still reads as chrome laid over the canvas rather than
 * as part of it.
 *
 * It is not quite the page's own value, though: a whole screen of it with
 * cards on it is the one surface here that is read AS a surface rather than
 * past, and it wants a floor under the eye. #050505 is that floor — two
 * values off the page and still far below the darkest tone a dark page's
 * thumbnail renders at, so every card keeps its edge.
 *
 * Light needs none of this — a page preview there is paper on paper and the
 * ground is already the darker of the two — so it keeps the ordinary answer.
 */
internal val NothingEmptyLight = Dot.paperDim
internal val NothingEmptyDark = Dot.voidLift

/** The watermark on that ground: one step up from it at either end, where
 * the ordinary answer is `surfaceContainerHigh`. */
internal val NothingEmptyGlyphLight = Dot.paperSunk
internal val NothingEmptyGlyphDark = Dot.voidRaised

/** The lock icon's green, muted to sit inside a monochrome canvas. */
internal val NothingSecureLight = Dot.greenLight
internal val NothingSecureDark = Dot.greenDark

/**
 * The type scale, straight off the skill's own table, with Compose's thirteen
 * roles laid over its nine tokens.
 *
 * | skill token   | size | tracking | lands on                    |
 * |---------------|------|----------|-----------------------------|
 * | `display-xl`  | 72   | -0.03em  | displayLarge   (Geist)      |
 * | `display-lg`  | 48   | -0.02em  | displayMedium  (Geist)      |
 * | `display-md`  | 36   | -0.02em  | displaySmall   (Geist)      |
 * | —             | 28→36| -0.01em  | headlineLarge  (NType 82)   |
 * | `heading`     | 24→34| -0.01em  | headlineMedium (NType 82)   |
 * | —             | 20→32|  0       | headlineSmall  (NType 82)   |
 * | `subheading`  | 18→30|  0       | titleLarge     (NType 82)   |
 * | `body`        | 16   |  0       | titleMedium, bodyLarge      |
 * | `body-sm`     | 14   |  0.01em  | titleSmall, bodyMedium      |
 * | `caption`     | 12   |  0.04em  | bodySmall, labelMedium      |
 * | `label`       | 11   |  0.08em  | labelSmall     (mono caps)  |
 *
 * Two things in it are worth not undoing later.
 *
 * **NType 82 sets the four HEADER roles and stops.** 28/24/20/18sp — every
 * screen heading and section title, and nothing under them. It is the one
 * face in this theme that is Nothing's own rather than a stand-in, so it is
 * spent where a face is read as a mark: a heading is looked AT, where a row
 * title is read THROUGH on the way to the row. Everything below is Geist,
 * which is the system face and the one that has to hold up in dense rows,
 * at every weight and in every script — see [NothingHeaderFamily].
 *
 * **The four header sizes are well above the skill's tokens, and the band
 * they sit in is COMPRESSED to 30-36sp.** Both facts are measured rather
 * than picked, and they answer two different complaints.
 *
 * The first was x-height parity. NType 82's x-height is 47.4 per 100em
 * against Geist's 53.0, so at equal sp it sets ~11% smaller than the rows
 * under it; cap heights are within a unit of each other (70.0 against 71.0),
 * so the deficit is all in the lowercase, which is what these sentence-case
 * headings are mostly made of. That is the same measurement the 98 theme ran
 * for `PIXEL_SIZE_PARITY` — which came out at 1.0 for Arimo and was deleted
 * for it. Here it came out at 1.12, and 1.12 was not enough, because parity
 * with the rows is not what a heading needs.
 *
 * The second is the real one: a screen title has to read as a HEADING rather
 * than as a large row. Measured off the device's own Settings app on a
 * 420dpi screen, its heading sets the word "Settings" 96px tall against our
 * 47px — 2.04x. We deliberately do not match that: its title owns a line of
 * its own, where ours shares a 64dp row with the back arrow. ~3/4 of it is
 * the most that fits, which is where 30sp lands, and the row is fixed-height
 * and centred (`PaneHeader`, `ListScreenScaffold`), so a taller title grows
 * about its own centre and cannot push the arrow or the list down. At
 * 30sp/38sp the line box is 38dp inside a 64dp row, leaving 26dp of slack.
 *
 * The band is compressed because this app has ONE kind of header. Only
 * `titleLarge` is ever drawn in this theme (the two screen-title rows); the
 * three headline roles above it are reached by surfaces that turn the theme
 * off. Spreading four roles across an octave to serve one of them would
 * make the one that renders the smallest of the four. So they step 30/32/34
 * /36 and stay in order. `headlineLarge` meets `displaySmall` at 36sp, which
 * is allowed exactly once: they are different FACES (NType against Geist)
 * and no surface puts them next to each other.
 *
 * **The header roles ask for Normal, not Medium.** NType 82 ships as one
 * static cut here; a heavier request is synthesised, and a fake-bold outline
 * is the one thing this face cannot survive. The step up from the rows under
 * it is size and the face itself, which is enough.
 *
 * **The display roles are Geist too.** They were declared as Doto in this
 * table long after the binding had moved to the sans; it now says what it
 * does. [NothingDotFamily] is kept because the dot face is still the
 * theme's own alphabet everywhere it is DRAWN (the dot field, the loading
 * matrix), but it sets no type.
 *
 * Labels are the one place a size is spent on rhythm rather than reading:
 * mono, capped, tracked 0.06-0.08em, 11-13sp. The skill calls these
 * "instrument panel" labels and the tracking is most of what makes them so.
 */
internal val NothingTypography: Typography = Typography().let { t ->
    val display = NothingSansFamily
    val header = NothingHeaderFamily
    val sans = NothingSansFamily
    val mono = NothingMonoFamily
    t.copy(
        displayLarge = t.displayLarge.copy(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.03).em,
        ),
        displayMedium = t.displayMedium.copy(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em,
        ),
        displaySmall = t.displaySmall.copy(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
        ),
        headlineLarge = t.headlineLarge.copy(
            fontFamily = header, fontWeight = FontWeight.Normal,
            fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em,
        ),
        headlineMedium = t.headlineMedium.copy(
            fontFamily = header, fontWeight = FontWeight.Normal,
            fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em,
        ),
        headlineSmall = t.headlineSmall.copy(
            fontFamily = header, fontWeight = FontWeight.Normal,
            fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.em,
        ),
        titleLarge = t.titleLarge.copy(
            fontFamily = header, fontWeight = FontWeight.Normal,
            fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.em,
        ),
        titleMedium = t.titleMedium.copy(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.em,
        ),
        titleSmall = t.titleSmall.copy(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em,
        ),
        bodyLarge = t.bodyLarge.copy(
            fontFamily = sans, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.em,
        ),
        bodyMedium = t.bodyMedium.copy(
            fontFamily = sans, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.01.em,
        ),
        bodySmall = t.bodySmall.copy(
            fontFamily = sans, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.04.em,
        ),
        // 13sp is the skill's button/label size, and labelLarge is what this
        // app's buttons and tiles are set in.
        labelLarge = t.labelLarge.copy(
            fontFamily = mono, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.06.em,
        ),
        labelMedium = t.labelMedium.copy(
            fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em,
        ),
        labelSmall = t.labelSmall.copy(
            fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em,
        ),
    )
}
