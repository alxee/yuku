package com.yuku.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

/**
 * The TUI palette is a MONOCHROME MONITOR, built from the accent.
 *
 * A green-screen terminal did not have "neutral greys and one accent": it had
 * one phosphor, and everything on the screen was that phosphor at some drive
 * level — the bright text, the dim text, and the unlit glass itself, which is
 * never black but a dark tint of the phosphor (cool-retro-term's Apple ][
 * profile is #4DFF6B on #001100 for exactly this reason). So the dark end's
 * grounds and inks all carry the seed's hue, and the swatch picks the tube:
 * a green pick is P1, amber is P3, Graphite is P4 paper-white — a seed with
 * no hue stays grey all the way through.
 *
 * The light end is the tube's other life, hardcopy: paper with the faintest
 * tint of the same ink, ribbon-dark type. It replaced a fixed milk + orange /
 * grey + mint pair, which gave an amber pick a mint lock and a grey glass
 * that no terminal was ever made of.
 *
 * The grounds' ORDER is unchanged from the old palette — page lightest,
 * toolbar a step under it, floor under that — and the dark levels stay near
 * the OLED floor for the reasons given at `Dot.void`.
 */
private class Ramp(private val seed: Color, isDark: Boolean) {
    private fun at(lightness: Float, saturation: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed.toArgb(), hsl)
        hsl[1] = if (hsl[1] <= 0.15f) 0f else saturation
        hsl[2] = lightness
        return Color(ColorUtils.HSLToColor(hsl))
    }

    // Grounds: page, toolbar, floor. Dark: near-black with only a breath of
    // the hue — the colour on this end is LIGHT (the neon halation, see
    // `tuiBloomIf`), and a tinted ground under a tinted glow reads as the
    // whole screen being one muddy colour instead of dark glass with colour
    // coming off the type.
    val base = if (isDark) at(0.035f, 0.12f) else at(0.985f, 0.55f)
    val mantle = if (isDark) at(0.022f, 0.12f) else at(0.965f, 0.40f)
    val crust = if (isDark) at(0.012f, 0.12f) else at(0.945f, 0.32f)

    // Inks, strongest first. Dark: NEAR-WHITE — the white-hot core of a lit
    // stroke, which is what makes a coloured glow around it read as
    // saturated. Tinting the ink (it was L 87% at 60% saturation) spent the
    // hue on the stroke and left the halo nothing to be more colourful than.
    val text = if (isDark) at(0.95f, 0.18f) else at(0.12f, 0.20f)
    val subtext1 = if (isDark) at(0.88f, 0.12f) else at(0.22f, 0.14f)
    val subtext0 = if (isDark) at(0.74f, 0.08f) else at(0.33f, 0.10f)
    val overlay2 = if (isDark) at(0.60f, 0.06f) else at(0.45f, 0.08f)
    val overlay0 = if (isDark) at(0.34f, 0.06f) else at(0.69f, 0.08f)

    // Raised grounds; `surface1` is also every hairline.
    val surface0 = if (isDark) at(0.07f, 0.10f) else at(0.925f, 0.24f)
    val surface1 = if (isDark) at(0.11f, 0.08f) else at(0.885f, 0.20f)

    val red = if (isDark) Color(0xFFF87171) else Color(0xFFB3261E)
}

/**
 * The scheme for one end, before [tintedWith] puts the accent itself in.
 * `secondary`/`tertiary` are the accent too (a terminal with two colours is
 * showing two kinds of thing); `surfaceTint` points back at the surface.
 */
internal fun tuiScheme(seed: Color, isDark: Boolean): ColorScheme {
    val r = Ramp(seed, isDark)
    val o = Ramp(seed, !isDark)
    // The accent roles are filled in by `tintedWith`; the ink holds them until then.
    return if (isDark) darkScheme(r, o, r.text) else lightScheme(r, o, r.text)
}

private fun lightScheme(r: Ramp, o: Ramp, accent: Color) = lightColorScheme(
    primary = accent, onPrimary = r.base, primaryContainer = r.surface0, onPrimaryContainer = r.text,
    secondary = accent, onSecondary = r.base, secondaryContainer = r.surface0, onSecondaryContainer = r.text,
    tertiary = accent, onTertiary = r.base, tertiaryContainer = r.surface0, onTertiaryContainer = r.text,
    background = r.base, onBackground = r.text, surface = r.base, onSurface = r.text,
    surfaceVariant = r.surface0, onSurfaceVariant = r.subtext1, surfaceTint = r.surface0,
    inverseSurface = o.base, inverseOnSurface = o.text, inversePrimary = accent,
    error = r.red, onError = r.base, errorContainer = r.surface0, onErrorContainer = r.red,
    outline = r.overlay0, outlineVariant = r.surface1, scrim = r.crust,
    surfaceBright = r.base, surfaceDim = r.crust,
    surfaceContainerLowest = r.base, surfaceContainerLow = r.mantle, surfaceContainer = r.crust,
    surfaceContainerHigh = r.surface0, surfaceContainerHighest = r.surface1,
)

private fun darkScheme(r: Ramp, o: Ramp, accent: Color) = darkColorScheme(
    primary = accent, onPrimary = r.crust, primaryContainer = r.surface1, onPrimaryContainer = r.text,
    secondary = accent, onSecondary = r.crust, secondaryContainer = r.surface1, onSecondaryContainer = r.text,
    tertiary = accent, onTertiary = r.crust, tertiaryContainer = r.surface1, onTertiaryContainer = r.text,
    background = r.base, onBackground = r.text, surface = r.base, onSurface = r.text,
    surfaceVariant = r.surface0, onSurfaceVariant = r.subtext1, surfaceTint = r.surface0,
    inverseSurface = o.base, inverseOnSurface = o.text, inversePrimary = accent,
    error = r.red, onError = r.crust, errorContainer = r.surface1, onErrorContainer = r.red,
    outline = r.overlay0, outlineVariant = r.surface1, scrim = r.crust,
    surfaceBright = r.surface0, surfaceDim = r.crust,
    surfaceContainerLowest = r.crust, surfaceContainerLow = r.mantle, surfaceContainer = r.mantle,
    surfaceContainerHigh = r.surface0, surfaceContainerHighest = r.surface1,
)

/**
 * The seed at FULL saturation, for the accent roles — a phosphor is as pure a
 * colour as the tube can make, and a wallpaper's muted primary (the default,
 * Auto) came out as a washed trace. A seed without a hue (Graphite, a grey
 * wallpaper) is returned untouched: P4 paper-white stays white.
 */
internal fun Color.vivid(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    if (hsl[1] <= 0.15f) return this
    hsl[1] = 1f
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * The TUI's accent: the phosphor, as PURE as it can be and still be read.
 *
 * The shared `darkAccent` sits at HSL lightness 62, and in HSL a lightness
 * above 50 is the hue mixed toward white whatever the saturation says — at 62
 * a quarter of the colour is white, which is the pastel the glow was being
 * washed out by. So this starts at the pure hue (L 50, S 100) and only lifts
 * the lightness, two points at a time, for the deep hues that cannot clear
 * 4.5:1 against the raised ground as they are. The light end goes the other
 * way from L 40 toward black. A seed with no hue is P4 white: a light grey on
 * the tube, a dark one on paper.
 */
internal fun tuiAccent(seed: Color, isDark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val ground = Ramp(seed, isDark).let { if (isDark) it.surface0 else it.base }.toArgb()
    if (hsl[1] <= 0.15f) {
        hsl[1] = 0f
        hsl[2] = if (isDark) 0.82f else 0.25f
        return Color(ColorUtils.HSLToColor(hsl))
    }
    hsl[1] = 1f
    var l = if (isDark) 0.50f else 0.40f
    while (true) {
        hsl[2] = l
        val c = ColorUtils.HSLToColor(hsl)
        if (ColorUtils.calculateContrast(c, ground) >= 4.5 || l !in 0.2f..0.8f) return Color(c)
        l += if (isDark) 0.02f else -0.02f
    }
}

/**
 * The two quiet inks, a step further from the ground than `outline` /
 * `outlineVariant`, which stay right for hairlines and are too faint for
 * monospace text at label sizes. Per seed, since the inks carry its hue.
 */
internal class TuiInks(lightSeed: Color, darkSeed: Color) {
    private val light = Ramp(lightSeed, false)
    private val dark = Ramp(darkSeed, true)
    val mutedLight = light.subtext0
    val mutedDark = dark.subtext0
    val faintLight = light.overlay2
    val faintDark = dark.overlay2
}

/**
 * The lock's green on paper. On the tube the lock is the phosphor itself
 * (BrowserTheme takes the dark scheme's primary): a second green beside an
 * amber trace is a colour that monitor could not show.
 */
internal val TuiSecureLight = Color(0xFF1E7A3A)

/**
 * The whole scale in [TuiMonoFamily], lifted by [TUI_SIZE_PARITY] and with
 * tracking at zero — a terminal's cells are the tracking. Every style is
 * rewritten: one stray proportional heading gives the game away.
 */
internal val TuiTypography: Typography = Typography().let { t ->
    fun TextStyle.term() = copy(
        fontFamily = TuiMonoFamily,
        fontSize = fontSize * TUI_SIZE_PARITY,
        lineHeight = lineHeight * TUI_LINE_PARITY,
        letterSpacing = 0.sp,
    )
    t.copy(
        displayLarge = t.displayLarge.term(),
        displayMedium = t.displayMedium.term(),
        displaySmall = t.displaySmall.term(),
        headlineLarge = t.headlineLarge.term(),
        headlineMedium = t.headlineMedium.term(),
        headlineSmall = t.headlineSmall.term(),
        titleLarge = t.titleLarge.term(),
        titleMedium = t.titleMedium.term(),
        titleSmall = t.titleSmall.term(),
        bodyLarge = t.bodyLarge.term(),
        bodyMedium = t.bodyMedium.term(),
        bodySmall = t.bodySmall.term(),
        labelLarge = t.labelLarge.term(),
        labelMedium = t.labelMedium.term(),
        labelSmall = t.labelSmall.term(),
    )
}
