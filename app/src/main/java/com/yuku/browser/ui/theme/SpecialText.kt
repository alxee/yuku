package com.yuku.browser.ui.theme

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text as MaterialText

/**
 * `Text`, with each special theme's casing folded in — the app's UI files
 * import this instead of Material's own, so a label is written the way it
 * reads in every other theme and is re-cased only where the theme says so.
 *
 * A wrapper rather than a transformation somewhere central because there
 * isn't one: Compose has no hook between a `Text` call and its layout, and a
 * `TextStyle` cannot change the characters. Deliberately NOT applied to text
 * FIELDS — an address the user is typing is content, not chrome, and
 * re-casing it would edit what they typed.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = MaterialText(
    text = recased(text, fontFamily, style),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = onTextLayout,
    style = tuiGlowing(style, color),
)

/**
 * The styled-text overload. The spans have to survive the case change — the
 * address bar's muted subdomain is one — so the string is rebuilt through its
 * own builder with the ranges kept and only the characters replaced, which is
 * safe here because `lowercase()` on the whole string is length-preserving
 * for every script this chrome shows.
 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    style: TextStyle = LocalTextStyle.current,
) = MaterialText(
    text = when (casing(fontFamily, style)) {
        Casing.Lower -> text.recased(String::lowercase)
        Casing.Upper -> text.recased(String::uppercase)
        Casing.AsIs -> text
    },
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = onTextLayout,
    inlineContent = inlineContent,
    style = tuiGlowing(style, color),
)

private enum class Casing { AsIs, Lower, Upper }

/**
 * The TUI's phosphor bloom (see [tuiGlow]), resolved against the colour the
 * text will actually be drawn in. A style that already carries a shadow keeps
 * its own.
 */
@Composable
private fun tuiGlowing(style: TextStyle, color: Color): TextStyle {
    if (!LocalTui.current || style.shadow != null) return style
    val resolved = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val glow = tuiGlow(resolved) ?: return style
    return style.copy(shadow = glow)
}

/**
 * Which way this piece of text is cased.
 *
 * The TUI answer is the whole interface: a terminal is lowercase and there is
 * nothing to decide. Nothing's is narrower — its labels are set in caps and
 * its titles and body are not — and rather than keep a second list of which
 * call sites count as labels, it reads the face the text is about to be drawn
 * in: `NothingMonoFamily` IS the label face in [NothingTypography], so "set
 * in the mono face" and "is a label" are the same fact. An explicit
 * `fontFamily` argument outranks the style's, exactly as it does in layout.
 *
 * 98 falls through to [Casing.AsIs] and that is a decision rather than an
 * omission: Windows 98 wrote its menus in sentence case ("Save as...",
 * "Print preview") and its labels in the same sentence case as its titles.
 * Casing them up would be another interface's habit wearing this one's
 * clothes.
 *
 * Aero falls through for the same kind of reason. Windows 7 and Mac OS X
 * both wrote in sentence and title case and neither had anything to say in
 * capitals; what carries a label in that language is the gloss and the
 * lozenge around it, not the shape of its letters.
 */
@Composable
private fun casing(fontFamily: FontFamily?, style: TextStyle): Casing = when {
    LocalTui.current -> Casing.Lower
    LocalNothing.current && (fontFamily ?: style.fontFamily) === NothingMonoFamily -> Casing.Upper
    else -> Casing.AsIs
}

@Composable
private fun recased(text: String, fontFamily: FontFamily?, style: TextStyle): String =
    when (casing(fontFamily, style)) {
        Casing.Lower -> text.lowercase()
        Casing.Upper -> text.uppercase()
        Casing.AsIs -> text
    }

/** Same characters re-cased, same spans over the same ranges. */
private fun AnnotatedString.recased(transform: (String) -> String): AnnotatedString {
    val cased = transform(text)
    // A locale whose cased form is a different length (there are a few) would
    // move every span; leave such a string alone rather than mangle it.
    if (cased.length != text.length) return this
    return AnnotatedString(
        text = cased,
        spanStyles = spanStyles,
        paragraphStyles = paragraphStyles,
    )
}
