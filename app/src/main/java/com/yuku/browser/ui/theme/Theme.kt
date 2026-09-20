package com.yuku.browser.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.yuku.browser.core.AccentTheme
import com.yuku.browser.core.PAGE_DARK_FADE_MS
import com.yuku.browser.core.SpecialTheme
import com.yuku.browser.core.ThemeMode

/**
 * The app's neutral chrome colors — everything that ISN'T the accent. Named
 * (not raw hex) so call sites read `Ink`, `PageBg` etc. exactly as before;
 * only their backing values now come from [LocalBrowserPalette] instead of
 * being fixed constants, so every existing usage picks up light/dark and
 * Dynamic-vs-fixed theming for free.
 */
data class BrowserPalette(
    val ink: Color,
    val inkStrong: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val pageBg: Color,
    val barBg: Color,
    val fieldBg: Color,
    val hairLine: Color,
    // The ground behind the tab switcher AND behind the empty-tabs screen:
    // one surface, deliberately. Closing the last tab is the switcher
    // emptying out, not a move to another screen, so the two must not differ
    // by a shade — see [SwitcherBg], which reads this same value.
    val emptyBg: Color,
    val emptyGlyph: Color,
    val secure: Color,
)

private val LightPalette = BrowserPalette(
    ink = Color(0xFF3A3A38),
    inkStrong = Color(0xFF1F1F1D),
    inkMuted = Color(0xFF87857E),
    inkFaint = Color(0xFFA3A19A),
    pageBg = Color(0xFFFFFFFF),
    barBg = Color(0xFFFBFAF7),
    fieldBg = Color(0xFFEDEBE3),
    hairLine = Color(0xFFD8D5CC),
    emptyBg = Color(0xFFFAF9F5),
    emptyGlyph = Color(0xFFF1EFE8),
    secure = Color(0xFF0F6E56),
)

private val DarkPalette = BrowserPalette(
    ink = Color(0xFFC9C7C0),
    inkStrong = Color(0xFFF2F1ED),
    inkMuted = Color(0xFF8B8981),
    inkFaint = Color(0xFF66645D),
    pageBg = Color(0xFF151513),
    barBg = Color(0xFF1D1D1B),
    fieldBg = Color(0xFF262624),
    hairLine = Color(0xFF3A3A38),
    emptyBg = Color(0xFF1A1A18),
    emptyGlyph = Color(0xFF242422),
    secure = Color(0xFF5FCB9F),
)

/**
 * The toolbar's palette while the page lens is on (`ui/PageLens.kt`): the dark
 * chrome's inks on a BLACK bar, so the bar reads as the same black bezel the
 * status bar and the page's curved edges end in. The hairline goes black too —
 * a rule between two blacks is a grey line drawn across the bezel.
 */
val LensBezelPalette: BrowserPalette = DarkPalette.copy(barBg = Color.Black, hairLine = Color.Black)

/**
 * The fixed swatches — a look independent of the wallpaper, one entry per
 * major hue.
 *
 * **Bold, not tasteful.** These used to be Material's 600 ramp (#E53935,
 * #FB8C00, ...), which is the ramp a component library picks when it has to
 * look unobtrusive under someone else's brand; a row of them reads as nine
 * shades of the same muted decision, and the swatch a user taps to say "make
 * this thing red" came out brick. So every hue is now taken at (or within a
 * point of) FULL saturation — Material's own A-series accents, which is what
 * that ramp exists for.
 *
 * What actually reaches the screen is the hue and the SATURATION, not this
 * value's lightness: [tonalColorScheme] rebuilds every role at its own tone
 * (see [tone]), so the only thing a bolder swatch changes is how much colour
 * survives that rebuild — which is precisely what was missing.
 *
 * [AccentTheme.Graphite] stays the app's neutral and is deliberately not one
 * of the hues: it is the "no colour picked" pick, and it is a pick like any
 * other — it tints the special themes too, where a grey accent is a real
 * choice rather than an abstention (see [BrowserTheme]).
 */
fun AccentTheme.color(): Color = when (this) {
    AccentTheme.Dynamic -> Color(0xFF00C853) // only used as a pre-S fallback seed; see BrowserTheme.
    AccentTheme.Graphite -> Color(0xFF3A3A38)
    AccentTheme.Red -> Color(0xFFE00000)
    AccentTheme.Orange -> Color(0xFFFF6D00)
    AccentTheme.Yellow -> Color(0xFFFFC400)
    AccentTheme.Green -> Color(0xFF00C853)
    AccentTheme.Teal -> Color(0xFF00BFA5)
    AccentTheme.Blue -> Color(0xFF2962FF)
    AccentTheme.Purple -> Color(0xFF9500FF)
    AccentTheme.Pink -> Color(0xFFF50057)
}

/**
 * The private space's tint. Not one of the [AccentTheme] swatches and not
 * pickable: it is the signal that the browser is in its private space, so it
 * has to be the same colour every time and unlike whatever the user chose for
 * the ordinary one. Seeded dark and deeply saturated — [tonalColorScheme]
 * takes the hue and saturation from a seed and its own tone for lightness, so
 * what this really fixes is "violet, and not a pastel one".
 */
val PrivateAccent = Color(0xFF4A2A7A)

/** A color at the same hue as [seed] but a given HSL lightness (0-100, matching
 * Material's tone scale) and an optional saturation multiplier — used to build
 * a full tonal scheme out of a single seed color the same way Android derives
 * one from a wallpaper, just without the wallpaper. */
private fun tone(seed: Color, tone: Int, saturationScale: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
    hsl[2] = (tone / 100f).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun hueShifted(seed: Color, degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees).mod(360f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * The dark end's accent, kept BOLD — the one place this app departs from
 * Material's tone table on purpose.
 *
 * Material puts a dark scheme's `primary` at tone 80 because its own colour
 * space is chroma-limited: a tone-80 role there is as colourful as that hue
 * can be at that lightness. [tone] is plain HSL, where lightness 80 is 80%
 * of the way to WHITE whatever the saturation says — so the same table run
 * through it turns every accent pastel the moment the theme darkens. A
 * bright red picked in daylight came up pink at night, which is not the
 * colour anybody tapped.
 *
 * So the dark accent is pinned near the middle of the ramp instead, where a
 * hue is actually at its strongest, and held to a saturation FLOOR — some
 * seeds (a wallpaper's muted primary) arrive washed out and re-toning alone
 * would only make them muddy. The floor applies to seeds that carry a hue at
 * all: a neutral one ([AccentTheme.Graphite], a grey wallpaper) is left
 * neutral, or the swatch the user picked precisely because it has no colour
 * would come back with one.
 *
 * Contrast is not lost by this: at tone 62 a saturated hue still sits around
 * 5:1 against the near-black grounds this scheme builds, and what is drawn
 * ON it is chosen by measurement rather than by table — see [accentInk].
 */
private const val DARK_ACCENT_TONE = 62

/** How much of a fixed swatch's saturation the default look's GROUNDS keep,
 * per end — set to land near Material You's wallpaper neutrals, so a swatch
 * tints the canvas as visibly as Auto does. */
private const val GROUND_SAT_LIGHT = 0.4f
private const val GROUND_SAT_DARK = 0.3f

/** What the accent has to be readable ON: not the darkest ground in the app
 * but the LIGHTEST of the dark ones — the sunk surfaces a field or a raised
 * container is drawn in, around #151515 in every dark scheme here (the
 * ordinary theme's tone 8, Nothing's `voidSunk`, the TUI's `surface0`).
 * Contrast against the page itself, which is near black, is easier by a wide
 * margin, so a colour that clears the bar here clears it everywhere. */
private val DarkestGround = Color(0xFF151513)

private fun darkAccent(seed: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    if (hsl[1] > 0.15f) hsl[1] = maxOf(hsl[1], 0.62f)
    // Deep hues — blue, violet — carry less luminance than the rest of the
    // wheel at the same HSL lightness, so the one tone that suits a red
    // leaves them at about 4.2:1 against the ground, and the accent sets
    // section headers. Lifted a notch at a time until they clear 4.6, and no
    // further: this is the smallest lift that reads, where a flat higher
    // tone for everyone would have cost every other hue its boldness.
    var t = DARK_ACCENT_TONE
    while (t < 72) {
        hsl[2] = t / 100f
        if (contrastRatio(Color(ColorUtils.HSLToColor(hsl)), DarkestGround) >= 4.6f) break
        t += 2
    }
    hsl[2] = t / 100f
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * What to draw on an accent: the seed's own colour taken almost to black or
 * almost to white, whichever actually contrasts with it. Measured rather
 * than tabulated because a bold accent is no longer at a known lightness — a
 * saturated yellow and a saturated blue at the same tone want opposite
 * answers.
 */
private fun accentInk(accent: Color): Color {
    val dark = tone(accent, 12)
    val light = tone(accent, 99)
    return if (contrastRatio(accent, dark) >= contrastRatio(accent, light)) dark else light
}

/**
 * Builds a full Material tonal [ColorScheme] from a single seed color — the
 * same shape of scheme [dynamicLightColorScheme]/[dynamicDarkColorScheme]
 * derive from the wallpaper, just seeded by a fixed accent pick instead. This
 * is what makes a fixed swatch (Sage, Ocean, ...) recolor surfaces/background
 * roles too, not just [ColorScheme.primary].
 */
private fun tonalColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val tertiarySeed = hueShifted(seed, 60f)
    return if (!isDark) {
        lightColorScheme(
            primary = tone(seed, 40),
            onPrimary = tone(seed, 100),
            primaryContainer = tone(seed, 90),
            onPrimaryContainer = tone(seed, 10),
            secondary = tone(seed, 40, 0.35f),
            onSecondary = tone(seed, 100),
            secondaryContainer = tone(seed, 90, 0.35f),
            onSecondaryContainer = tone(seed, 10, 0.35f),
            tertiary = tone(tertiarySeed, 40),
            onTertiary = tone(tertiarySeed, 100),
            tertiaryContainer = tone(tertiarySeed, 90),
            onTertiaryContainer = tone(tertiarySeed, 10),
            // Grounds carry the accent about as strongly as Material You's
            // wallpaper neutrals do (HSL saturation ~0.3–0.45 at these tones),
            // so a picked swatch colours the canvas — Settings included — the
            // way Auto does; at 0.12 they read as plain white.
            background = tone(seed, 98, GROUND_SAT_LIGHT),
            onBackground = tone(seed, 10, 0.12f),
            surface = tone(seed, 98, GROUND_SAT_LIGHT),
            onSurface = tone(seed, 10, 0.12f),
            surfaceVariant = tone(seed, 90, 0.35f),
            onSurfaceVariant = tone(seed, 30, 0.2f),
            surfaceContainerLow = tone(seed, 96, GROUND_SAT_LIGHT),
            surfaceContainerHigh = tone(seed, 92, GROUND_SAT_LIGHT),
            surfaceDim = tone(seed, 87, 0.3f),
            surfaceBright = tone(seed, 98, GROUND_SAT_LIGHT),
            surfaceContainerLowest = tone(seed, 100, GROUND_SAT_LIGHT),
            surfaceContainer = tone(seed, 94, GROUND_SAT_LIGHT),
            surfaceContainerHighest = tone(seed, 90, 0.35f),
            outline = tone(seed, 50, 0.2f),
            outlineVariant = tone(seed, 80, 0.2f),
        )
    } else {
        darkColorScheme(
            // Not tone 80 — see [darkAccent]. The containers below stay on
            // the table: they are grounds, and a ground at tone 30 was never
            // the thing going pastel.
            primary = darkAccent(seed),
            onPrimary = accentInk(darkAccent(seed)),
            primaryContainer = tone(seed, 30),
            onPrimaryContainer = tone(seed, 90),
            secondary = tone(seed, 80, 0.35f),
            onSecondary = tone(seed, 20, 0.35f),
            secondaryContainer = tone(seed, 30, 0.35f),
            onSecondaryContainer = tone(seed, 90, 0.35f),
            tertiary = darkAccent(tertiarySeed),
            onTertiary = accentInk(darkAccent(tertiarySeed)),
            tertiaryContainer = tone(tertiarySeed, 30),
            onTertiaryContainer = tone(tertiarySeed, 90),
            // The grounds sit where the Nothing theme's do — page ~#030303,
            // sheet ~#0D0D0D, field ~#151515 — and the reasoning is that
            // theme's (see `Dot.void`), applied here because it is about the
            // display rather than about the look: a ground of tone 1 never
            // switches an OLED pixel fully off, so there is no smear when
            // light content scrolls over it and no bloom around white type,
            // while being low enough that the panel's own low-grey
            // unevenness barely shows. The seed's hue survives it — these
            // are still `tone(seed, …)`, so a picked accent tints the greys
            // exactly as it did, there is just less light in them to tint.
            //
            // The ramp above the page is what changed shape. It ran 10/17/25
            // against Material's 10/13/22, opened up because three tone
            // points is a step you can measure and not one you can see. At
            // this ground the same argument runs the other way: a step
            // measured against near-black reads as a COLOUR rather than as a
            // height, and a sheet eight points up covering most of the
            // screen came back as a grey screen replacing the page. So the
            // steps are four points each, and what separates a sheet from
            // the page is the scrim under it and the hairline round it.
            background = tone(seed, 1, GROUND_SAT_DARK),
            onBackground = tone(seed, 90, 0.14f),
            surface = tone(seed, 1, GROUND_SAT_DARK),
            onSurface = tone(seed, 90, 0.14f),
            surfaceVariant = tone(seed, 12, GROUND_SAT_DARK),
            onSurfaceVariant = tone(seed, 80, 0.2f),
            surfaceContainerLow = tone(seed, 5, GROUND_SAT_DARK),
            surfaceContainerHigh = tone(seed, 8, GROUND_SAT_DARK),
            // The rest of the family, which used to fall through to
            // Material's baseline dark scheme — a purple-grey ramp from
            // nobody's accent, several values brighter than the four above
            // and visible wherever a component reaches for one of them.
            surfaceDim = tone(seed, 1, GROUND_SAT_DARK),
            surfaceBright = tone(seed, 12, GROUND_SAT_DARK),
            surfaceContainerLowest = tone(seed, 1, GROUND_SAT_DARK),
            surfaceContainer = tone(seed, 5, GROUND_SAT_DARK),
            surfaceContainerHighest = tone(seed, 11, GROUND_SAT_DARK),
            outline = tone(seed, 60, 0.2f),
            outlineVariant = tone(seed, 30, 0.2f),
        )
    }
}

/**
 * The contrast ratio between two opaque colours, WCAG's formula — used to
 * pick what is drawn ON a tinted accent rather than guessing from its
 * lightness: a fully saturated yellow and a fully saturated blue at the same
 * tone want opposite answers.
 */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * A hand-mapped special theme's scheme with the user's accent put through
 * it: the accent roles only, at the tone this end of the theme can carry,
 * and every grey left exactly where the theme put it.
 *
 * This is the whole mechanism behind "the TUI and Nothing looks can be
 * tinted". It is deliberately NOT [tonalColorScheme] re-seeded — that would
 * regenerate the sixteen greys from the accent's hue, which is the one thing
 * both of those palettes spend their entire colour budget avoiding (see
 * [tuiScheme], [NothingLightScheme]). A tint here changes the mark and
 * nothing the mark sits on.
 *
 * The tone is fixed by the END, not by the seed: 42 on a light ground and
 * [DARK_ACCENT_TONE] with its saturation floor on a dark one, so a bold
 * swatch stays legible on milk and stays BOLD on black rather than washing
 * out to a pastel of itself. `onPrimary` is then whichever of the theme's
 * two extremes (its ground or its ink) actually contrasts with the result —
 * the theme's own, not a derived near-black: these palettes are hand-mapped,
 * and what sits on a mark in them is one of their sixteen values.
 *
 * @param singleVoice for a palette whose `secondary`/`tertiary` ARE the
 * accent (the TUI's — a terminal that has two colours is showing you two
 * kinds of thing). Nothing's are greys on purpose and are left alone.
 */
internal fun ColorScheme.tintedWith(
    seed: Color,
    isDark: Boolean,
    singleVoice: Boolean,
    // A theme that picks its own accent tone from the seed (the TUI's
    // phosphor — see `tuiAccent`) rather than this end's standard one.
    accentOverride: Color? = null,
): ColorScheme {
    val accent = accentOverride ?: if (isDark) darkAccent(seed) else tone(seed, 42)
    val on = if (contrastRatio(accent, surface) >= contrastRatio(accent, onSurface)) surface else onSurface
    return copy(
        primary = accent,
        onPrimary = on,
        // The other end's tone, which is what `inversePrimary` is for: the
        // accent as it will look once the light/dark turn lands.
        inversePrimary = if (isDark) tone(seed, 42) else darkAccent(seed),
        secondary = if (singleVoice) accent else secondary,
        onSecondary = if (singleVoice) on else onSecondary,
        tertiary = if (singleVoice) accent else tertiary,
        onTertiary = if (singleVoice) on else onTertiary,
    )
}

/**
 * Android's own dark scheme with its accent roles deepened — [darkAccent]
 * applied to the wallpaper's `primary`/`tertiary` and nothing else.
 *
 * Material You builds a dark scheme's accents at tone 80, which is a pale
 * one by design; the rest of this app's dark accents no longer are, and one
 * pastel among them would be the system pick reading as the washed-out
 * option rather than as the phone's own colour. Every surface, ink and
 * outline is left exactly as Android derived it: those are the roles the
 * wallpaper palette is actually good at, and this is a correction to one
 * mark, not a second opinion about the whole scheme.
 */
private fun ColorScheme.boldDarkAccents(): ColorScheme {
    val p = darkAccent(primary)
    val t = darkAccent(tertiary)
    return copy(
        primary = p,
        onPrimary = accentInk(p),
        tertiary = t,
        onTertiary = accentInk(t),
    )
}

/**
 * Android's dark grounds brought down to the levels the fixed swatches build
 * — the other half of the Auto pick behaving like every other pick.
 *
 * `Auto` is [AccentTheme.Dynamic], and its scheme is the whole wallpaper
 * palette rather than a seed: surfaces included. Material You builds those at
 * its own tone 6/10/12, so choosing Auto lifted every ground in the app by
 * ten values against the same choice made with a swatch — the near-black
 * canvas the rest of the app now has, unless you happened to pick the option
 * that is on by default.
 *
 * Re-toned rather than replaced: [tone] keeps the hue and saturation Android
 * derived from the wallpaper and moves only the lightness, so the greys are
 * still the phone's own greys, with the light taken out of them. The levels
 * are [tonalColorScheme]'s dark branch, role for role. Inks, outlines and
 * `inverseSurface` are untouched — those are the roles the wallpaper palette
 * is good at, and this, like [boldDarkAccents], is a correction rather than a
 * second opinion about the scheme.
 */
private fun ColorScheme.darkGrounds(): ColorScheme = copy(
    background = tone(surface, 1),
    surface = tone(surface, 1),
    surfaceVariant = tone(surfaceVariant, 12),
    surfaceDim = tone(surfaceDim, 1),
    surfaceBright = tone(surfaceBright, 12),
    surfaceContainerLowest = tone(surfaceContainerLowest, 1),
    surfaceContainerLow = tone(surfaceContainerLow, 5),
    surfaceContainer = tone(surfaceContainer, 5),
    surfaceContainerHigh = tone(surfaceContainerHigh, 8),
    surfaceContainerHighest = tone(surfaceContainerHighest, 11),
)


/**
 * How far through the chrome's light-to-dark turn the UI currently is, 0 to 1.
 * Exposed for the handful of things that can't be interpolated and have to
 * pick a side instead — the system bars' icon contrast, which has to wait
 * until the bar behind it has actually gone dark rather than flipping at the
 * start of the animation and spending a moment invisible.
 *
 * Read it from as small a composable as possible: it changes every frame of
 * the turn, and everything reading it recomposes with it.
 */
val LocalChromeDarkness = compositionLocalOf { 0f }

/** How long the turn into the private space (and back) takes. */
const val PRIVATE_FADE_MS = 420

/**
 * How far through that turn the UI is, 0 to 1 — for the few things the
 * recolored scheme can't express on its own and that have to animate
 * alongside it. Today that is the private tab previews' blur (see
 * `Modifier.privatePreview`) and the switcher's backdrop.
 *
 * Dynamic, like [LocalChromeDarkness] and for the same reason: it changes
 * every frame of the turn, so only what actually reads it should recompose
 * with it.
 */
val LocalPrivacy = compositionLocalOf { 0f }

/**
 * The same turn as [LocalPrivacy], read in the layout or draw phase instead of
 * in composition: [LocalPrivacy] is a value, so anything reading it recomposes
 * on every frame of the turn — right for the couple of places that need the
 * number as a composition INPUT (a blur radius that is a layout modifier's
 * argument), wrong for anything that only draws with it.
 *
 * The lambda is stable for the life of the theme, so being handed it costs a
 * reader nothing; calling it inside a `graphicsLayer` block subscribes that
 * layer alone to the animation.
 */
val LocalPrivacyProgress = compositionLocalOf<() -> Float> { { 0f } }

/**
 * Dynamic rather than static on purpose: the palette animates between light
 * and dark (see [BrowserTheme]), and a static local invalidates the whole
 * subtree under its provider on every change — every frame of that animation
 * would recompose the entire UI rather than just what actually reads a color.
 */
val LocalBrowserPalette = compositionLocalOf { LightPalette }

val Ink: Color @Composable get() = LocalBrowserPalette.current.ink
val InkStrong: Color @Composable get() = LocalBrowserPalette.current.inkStrong
val InkMuted: Color @Composable get() = LocalBrowserPalette.current.inkMuted
val InkFaint: Color @Composable get() = LocalBrowserPalette.current.inkFaint
val PageBg: Color @Composable get() = LocalBrowserPalette.current.pageBg
val BarBg: Color @Composable get() = LocalBrowserPalette.current.barBg
val FieldBg: Color @Composable get() = LocalBrowserPalette.current.fieldBg
/** An alias for [EmptyBg], not a colour of its own: the switcher's ground and
 * the empty screen's are the same surface. Kept as a name so switcher code
 * still reads as the switcher's. */
val SwitcherBg: Color @Composable get() = LocalBrowserPalette.current.emptyBg
val HairLine: Color @Composable get() = LocalBrowserPalette.current.hairLine
val EmptyBg: Color @Composable get() = LocalBrowserPalette.current.emptyBg
val EmptyGlyph: Color @Composable get() = LocalBrowserPalette.current.emptyGlyph
val Secure: Color @Composable get() = LocalBrowserPalette.current.secure

/** The one color a user's accent pick changes: switch tracks, active tiles, chips. */
val AccentColor: Color @Composable get() = MaterialTheme.colorScheme.primary

/** Android's default type scale (Roboto), matching current Material 3 guidance. */
private val BrowserTypography = Typography()

/**
 * @param accent [AccentTheme.Dynamic] inherits Android's own Material You
 * palette (wallpaper-derived, Android 12+; falls back to a fixed seed on
 * older versions). Any other value is a fixed swatch, independent of
 * wallpaper or system accent. Under the TUI and Nothing looks the pick is
 * put through the theme's accent roles alone (see [tintedWith]) instead of
 * seeding a scheme; every swatch reaches them, Graphite included.
 * @param themeMode drives light/dark; [ThemeMode.System] follows the device setting.
 * @param special a whole-look override on top of the accent —
 * [SpecialTheme.Tui] (gruvbox light/dark, a monospace type scale, and
 * through [LocalTui] square corners and lowercase labels) or
 * [SpecialTheme.Nothing] (a monochrome canvas with one red, a dot-matrix
 * type scale, and through [LocalNothing] capped corners and uppercase
 * labels) or [SpecialTheme.Ninety8] (Windows 98's system colours over a
 * teal desktop, a pixel-grid face, and through [LocalNinety8] square corners
 * and the drawn bevels that carry depth in place of shadow) or
 * [SpecialTheme.Aero] (a sky under tinted glass, a humanist face, and
 * through [LocalAero] corners blown out rather than cut, with depth carried
 * by the light in a thick wet object). It replaces the
 * accent's SCHEME outright — the greys are the theme, and no accent
 * regenerates them — but the TUI's, Nothing's and Aero's accent roles are
 * still the user's to colour. 98's are not: sixteen fixed system values are the whole
 * of that look, so an accent tapped while it is on drops it back to
 * [SpecialTheme.Default] rather than tinting it. [SpecialTheme.Default] is
 * the app's own Material look — one of the choices rather than the absence
 * of one, which is why no screen passes it to opt a subtree out: the look
 * reaches everywhere.
 * @param privateMode replaces the whole scheme with the dark [PrivateAccent]
 * one for as long as the private space is open — accent AND light/dark: the
 * private space is dark violet whatever the app or the system is set to, since
 * a pale violet browser is not a signal anyone would read as "private". It is
 * a lerp, not a switch (see [LocalPrivacy]), so the turn into it and back out
 * is the same kind of movement the light-to-dark turn is. The user's own pick
 * and light/dark setting are untouched underneath and come straight back on
 * the way out.
 */
@Composable
fun BrowserTheme(
    accent: AccentTheme = AccentTheme.Dynamic,
    themeMode: ThemeMode = ThemeMode.System,
    special: SpecialTheme = SpecialTheme.Default,
    privateMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaperDynamic = accent == AccentTheme.Dynamic && dynamicAvailable
    // Both ends are built up front and the live scheme is a point between
    // them, so the whole turn is driven by ONE animation rather than by three
    // dozen per-color ones — and when it isn't running, `scheme` is one of
    // these two objects rather than a fresh copy per composition.
    val tui = special == SpecialTheme.Tui
    val nothing = special == SpecialTheme.Nothing
    val ninety8 = special == SpecialTheme.Ninety8
    val aero = special == SpecialTheme.Aero
    // What the accent means to a hand-mapped special theme, per end: EVERY
    // swatch, [AccentTheme.Graphite] included. Graphite used to mean "leave
    // this look wearing its own colour", which made the app's neutral the
    // one pick these themes could not take — and a grey accent is a real
    // choice under both of them, not the absence of one: it is what a
    // terminal with no colour at all looks like, and it is Nothing's own
    // premise. So it tints like the rest, and [darkAccent]'s saturation
    // floor is deliberately skipped for a neutral seed so it stays grey.
    //
    // Dynamic hands over the wallpaper's own primary, taken from the end it
    // is going to be drawn at — the system colour, tinted the way any other
    // pick is. Below API 31 there is no wallpaper to read and it falls
    // through to the fixed pre-S seed, exactly as the ordinary look does.
    val specialSeed: (Boolean) -> Color = { isDark ->
        if (wallpaperDynamic) {
            (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        } else {
            accent.color()
        }
    }
    // 98 is the exception and stays untinted: its colours are sixteen fixed
    // values an operating system was drawn from, and a navy title bar in
    // somebody's pink is not that system with an accent, it is a different
    // one. Picking an accent while it is on drops the theme instead (see
    // `setAccentTheme`), so this branch is only ever reached for the frames
    // of the turn out of it.
    val lightScheme = remember(accent, wallpaperDynamic, special, context) {
        if (tui) tuiScheme(specialSeed(false), false).tintedWith(
            specialSeed(false).vivid(), false, singleVoice = true,
            accentOverride = tuiAccent(specialSeed(false), isDark = false),
        )
        else if (nothing) NothingLightScheme.nothingElementsTinted(specialSeed(false))
            .tintedWith(specialSeed(false), false, singleVoice = false)
        // Aero is tinted like the other two, and for a reason the other two
        // do not have: Windows 7 shipped a colour picker for its glass, so
        // the swatch is not merely allowed here, it IS the colorization
        // control. `singleVoice` is false — this palette's secondary and
        // tertiary are its own ink and a sea green, and both are doing work.
        else if (aero) AeroLightScheme.aeroRehued(specialSeed(false))
            .tintedWith(specialSeed(false), false, singleVoice = false)
        else if (ninety8) Ninety8LightScheme
        else if (wallpaperDynamic) dynamicLightColorScheme(context)
        else tonalColorScheme(seed = accent.color(), isDark = false)
    }
    val darkScheme = remember(accent, wallpaperDynamic, special, context) {
        if (tui) tuiScheme(specialSeed(true), true).tintedWith(
            specialSeed(true).vivid(), true, singleVoice = true,
            accentOverride = tuiAccent(specialSeed(true), isDark = true),
        )
        else if (nothing) NothingDarkScheme.nothingElementsTinted(specialSeed(true))
            .tintedWith(specialSeed(true), true, singleVoice = false)
        else if (aero) AeroDarkScheme.aeroRehued(specialSeed(true))
            .tintedWith(specialSeed(true), true, singleVoice = false)
        else if (ninety8) Ninety8DarkScheme
        // The wallpaper's own dark scheme has Material's tone-80 accent in
        // it, which is the pastel this app's fixed swatches now avoid (see
        // [darkAccent]). Deepened here too: a system colour that goes pale
        // at night is the same complaint whether the colour came from a
        // swatch or from the wallpaper. Its GROUNDS come down to this app's
        // levels for the same kind of reason (see [darkGrounds]) — Auto is a
        // pick like any other and should not be the one that leaves the
        // canvas ten values brighter. Inks and outlines stay Android's.
        else if (wallpaperDynamic) dynamicDarkColorScheme(context).boldDarkAccents().darkGrounds()
        else tonalColorScheme(seed = accent.color(), isDark = true)
    }
    val tuiInks = remember(accent, wallpaperDynamic, special, context) {
        if (tui) TuiInks(specialSeed(false), specialSeed(true)) else null
    }
    // Nothing's quiet inks and empty-screen watermark are elements, so they
    // take the accent's hue like the scheme's own element roles do.
    val nothingTints = remember(accent, wallpaperDynamic, special, context) {
        if (!nothing) null else {
            val l = specialSeed(false)
            val d = specialSeed(true)
            listOf(
                nothingTint(NothingInkMutedLight, l, 0.2f), nothingTint(NothingInkMutedDark, d, 0.2f),
                nothingTint(NothingInkFaintLight, l, 0.2f), nothingTint(NothingInkFaintDark, d, 0.2f),
                nothingTint(NothingEmptyGlyphLight, l, 0.2f), nothingTint(NothingEmptyGlyphDark, d, 0.2f),
            )
        }
    }
    // The third end. Always the dark one — being dark is half of what makes
    // the private space recognisable — so it is built once and never depends
    // on the accent or on themeMode.
    //
    // Except under Aero, whose surfaces are TRANSLUCENT glass with inks of
    // its own: swapping in an opaque tonal scheme there put violet slabs
    // under Aero's inks and killed the glass in one go, which is where the
    // poor contrast came from. Its private end is Aero's own dark glass with
    // the violet put in as the accent instead — still dark, still violet,
    // still the same material as the rest of the look.
    val privateScheme = remember(aero) {
        if (aero) AeroDarkScheme.aeroRehued(PrivateAccent)
            .tintedWith(PrivateAccent, isDark = true, singleVoice = false)
        else tonalColorScheme(seed = PrivateAccent, isDark = true)
    }

    // 0 is light, 1 is dark. Seeded AT the current value so a cold start (or
    // an accent change) lands already themed instead of animating in from the
    // wrong end; only a genuine light/dark change animates.
    val darkness = remember { Animatable(if (isDark) 1f else 0f) }
    LaunchedEffect(isDark) {
        darkness.animateTo(
            if (isDark) 1f else 0f,
            tween(PAGE_DARK_FADE_MS.toInt(), easing = FastOutSlowInEasing),
        )
    }
    val t = darkness.value

    // 0 is the user's own theme, 1 is the private one. Seeded at the current
    // value for the same reason `darkness` is: an Activity recreate inside
    // the private space must land already violet rather than animating in
    // from the ordinary theme it was never showing.
    val privacy = remember { Animatable(if (privateMode) 1f else 0f) }
    LaunchedEffect(privateMode) {
        privacy.animateTo(
            if (privateMode) 1f else 0f,
            tween(PRIVATE_FADE_MS, easing = FastOutSlowInEasing),
        )
    }
    val p = privacy.value
    // Remembered so the local's value is the SAME lambda across the turn —
    // a fresh one per frame would invalidate every reader in composition,
    // which is exactly what reading it in draw is for.
    val privacyProgress = remember { { privacy.value } }

    val ordinaryScheme = when {
        t <= 0f -> lightScheme
        t >= 1f -> darkScheme
        else -> lerp(lightScheme, darkScheme, t)
    }
    // Layered on top rather than folded into the light/dark lerp: the two
    // animations are independent (the system can flip to dark halfway through
    // going private), and this one has to win outright at its own end
    // whatever `darkness` is doing.
    val colorScheme = when {
        p <= 0f -> ordinaryScheme
        p >= 1f -> privateScheme
        else -> lerp(ordinaryScheme, privateScheme, p)
    }
    // The private scheme IS a dark one, so anything that has to pick a side
    // rather than interpolate — the system bars' icon contrast — has to
    // count it as dark, in step with the turn rather than at either edge of
    // it.
    val chromeDarkness = t + (1f - t) * p
    // Every accent — wallpaper-derived Dynamic or a fixed swatch — now hands
    // back a full tonal scheme, so the neutral chrome (surfaces, dividers,
    // muted text) rides along with the pick everywhere, not just the accent
    // controls. Only the "secure" lock-icon green stays fixed regardless of
    // accent, since it's a status color, not chrome.
    val palette = BrowserPalette(
        ink = colorScheme.onSurfaceVariant,
        inkStrong = colorScheme.onSurface,
        // The TUI theme keeps its own inks for these two: see
        // [TuiInks]. Everything else, hairlines included, still
        // comes from the scheme.
        inkMuted = when {
            tuiInks != null -> lerp(tuiInks.mutedLight, tuiInks.mutedDark, chromeDarkness)
            nothingTints != null -> lerp(nothingTints[0], nothingTints[1], chromeDarkness)
            ninety8 -> lerp(Ninety8InkMutedLight, Ninety8InkMutedDark, chromeDarkness)
            // Aero's `outline`/`outlineVariant` are TRANSLUCENT — they are
            // hairlines drawn on glass — and translucent secondary text over
            // a live web page is legibility that belongs to somebody else's
            // background image. See [AeroInkMutedLight].
            aero -> lerp(AeroInkMutedLight, AeroInkMutedDark, chromeDarkness).aeroRehued(colorScheme.primary)
            else -> colorScheme.outline
        },
        inkFaint = when {
            tuiInks != null -> lerp(tuiInks.faintLight, tuiInks.faintDark, chromeDarkness)
            nothingTints != null -> lerp(nothingTints[2], nothingTints[3], chromeDarkness)
            ninety8 -> lerp(Ninety8InkFaintLight, Ninety8InkFaintDark, chromeDarkness)
            aero -> lerp(AeroInkFaintLight, AeroInkFaintDark, chromeDarkness).aeroRehued(colorScheme.primary)
            else -> colorScheme.outlineVariant
        },
        pageBg = colorScheme.background,
        // TUI is one terminal face from the page through the navbar and its
        // status-bar fill. A lighter toolbar band made a visible seam at the
        // system inset, especially once both sides carried the same texture.
        barBg = if (tui) colorScheme.background else colorScheme.surfaceContainerLow,
        fieldBg = colorScheme.surfaceContainerHigh,
        hairLine = colorScheme.outlineVariant,
        // The switcher's ground and the empty screen's — one surface, see
        // [BrowserPalette.emptyBg]. Every theme takes the app's own answer
        // for it (a shade off the toolbar) except two. 98's answer is the
        // DESKTOP: the cards standing on that ground are windows, and a
        // window stands on a desktop rather than on a slightly different
        // toolbar. It is the one place that theme needs a colour Material
        // has no role for at all. Nothing's is the PAGE ground, i.e. a true
        // zero at the dark end, so that a dark page's thumbnail has an edge
        // against it — see [NothingEmptyDark].
        emptyBg = when {
            ninety8 -> lerp(Ninety8DesktopLight, Ninety8DesktopDark, chromeDarkness)
            nothing -> lerp(NothingEmptyLight, NothingEmptyDark, chromeDarkness)
            // The TUI's tab canvas runs under the transparent system status
            // bar. Keeping both on the same terminal face removes the dark
            // band at their join; texture supplies the material distinction.
            tui -> colorScheme.background
            // Aero's is the SKY, and it is the same call 98 makes for its
            // desktop: a switcher card under this look is a pane of glass,
            // and a pane of glass has to be held up against something. The
            // app's ordinary answer — a shade off the toolbar — would be a
            // sheet of glass resting on a sheet of glass.
            // Coloured by the ACCENT (the scheme's primary, so the private
            // space's violet reaches it too) rather than a fixed blue.
            aero -> aeroAccentSky(colorScheme.primary, chromeDarkness)
            else -> colorScheme.surfaceContainerLow
        },
        emptyGlyph = when {
            ninety8 -> lerp(Ninety8DesktopGlyphLight, Ninety8DesktopGlyphDark, chromeDarkness)
            nothingTints != null -> lerp(nothingTints[4], nothingTints[5], chromeDarkness)
            aero -> aeroAccentSky(colorScheme.primary, chromeDarkness, glyph = true)
            else -> colorScheme.surfaceContainerHigh
        },
        // The status green comes from whichever palette the rest of the
        // chrome is drawn from, or it is the one colour on screen the theme
        // did not pick.
        secure = when {
            tui -> lerp(TuiSecureLight, darkScheme.primary, chromeDarkness)
            nothing -> lerp(NothingSecureLight, NothingSecureDark, chromeDarkness)
            ninety8 -> lerp(Ninety8SecureLight, Ninety8SecureDark, chromeDarkness)
            aero -> lerp(AeroSecureLight, AeroSecureDark, chromeDarkness)
            else -> lerp(LightPalette.secure, DarkPalette.secure, chromeDarkness)
        },
    )

    CompositionLocalProvider(
        LocalBrowserPalette provides palette,
        LocalChromeDarkness provides chromeDarkness,
        LocalPrivacy provides p,
        LocalPrivacyProgress provides privacyProgress,
        LocalTui provides tui,
        LocalNothing provides nothing,
        LocalNinety8 provides ninety8,
        LocalAero provides aero,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = when {
                tui -> TuiTypography
                nothing -> NothingTypography
                ninety8 -> Ninety8Typography
                aero -> AeroTypography
                else -> BrowserTypography
            },
            // Not `MaterialTheme.shapes`: Settings nests a second
            // BrowserTheme INSIDE this one, and a nested theme has to decide
            // its shapes from the `special` it was handed rather than inherit
            // whatever the theme around it happened to be drawing.
            shapes = when {
                tui -> TuiShapes
                nothing -> NothingShapes
                ninety8 -> Ninety8Shapes
                aero -> AeroShapes
                else -> OrdinaryShapes
            },
            content = content,
        )
    }
}

/**
 * A scheme partway between two others — what makes the chrome turn from light
 * to dark as a movement rather than a cut. Every role is interpolated, not
 * just the ones this app happens to read today: a half-lerped scheme with a
 * few fields left at one end would show up as a flash of the wrong color in
 * whichever Material component reads one of them.
 */
private fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = a.copy(
    primary = lerp(a.primary, b.primary, t),
    onPrimary = lerp(a.onPrimary, b.onPrimary, t),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
    onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, t),
    secondary = lerp(a.secondary, b.secondary, t),
    onSecondary = lerp(a.onSecondary, b.onSecondary, t),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, t),
    onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, t),
    tertiary = lerp(a.tertiary, b.tertiary, t),
    onTertiary = lerp(a.onTertiary, b.onTertiary, t),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, t),
    onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, t),
    background = lerp(a.background, b.background, t),
    onBackground = lerp(a.onBackground, b.onBackground, t),
    surface = lerp(a.surface, b.surface, t),
    onSurface = lerp(a.onSurface, b.onSurface, t),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
    onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
    surfaceTint = lerp(a.surfaceTint, b.surfaceTint, t),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, t),
    inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, t),
    error = lerp(a.error, b.error, t),
    onError = lerp(a.onError, b.onError, t),
    errorContainer = lerp(a.errorContainer, b.errorContainer, t),
    onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, t),
    outline = lerp(a.outline, b.outline, t),
    outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
    scrim = lerp(a.scrim, b.scrim, t),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, t),
    surfaceDim = lerp(a.surfaceDim, b.surfaceDim, t),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
)
