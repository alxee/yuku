package com.yuku.browser.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon as MaterialIcon

/**
 * `Icon`, with the special theme's own glyph swapped in where that theme has
 * one — the app's UI files import this instead of Material's, exactly as
 * they import [Text] instead of Material's.
 *
 * Five tables go through here, and they are of very different kinds:
 * Nothing's ([NothingIconOverrides]) and 98's ([Ninety8IconOverrides])
 * replace the vocabulary wholesale with sets drawn to their own rules, the
 * TUI takes Material's own Sharp cut ([SharpIconOverrides]) and Aero its
 * Rounded one ([AeroIconOverrides]) — the two ends of the same range, each
 * being that theme's corner rule applied to the icons by the people who drew
 * them — and the TUI adds a dozen characters on top that are not vectors at
 * all ([TuiIconGlyphs]). That they share this one hook is the point: a theme
 * that wants its own iconography adds a table, not a branch at every call
 * site.
 *
 * A wrapper for the same reason the text one is: there is no hook between an
 * `Icon` call and what it draws, and the alternative is a `LocalNothing`
 * branch at forty call sites, each of which is a place for the two sets to
 * drift apart. Here the swap is one lookup in one table
 * ([NothingIconOverrides]) and a call site simply asks for the icon it
 * means.
 *
 * Unmapped vectors fall through to Material untouched, which is also what
 * every other theme gets for every icon.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    // The TUI's first half is not vectors at all — where a keyboard has a
    // character for the thing, the theme draws that character in the
    // interface's own face (see [TuiIconGlyphs]) — so it is answered before
    // the override tables.
    val tuiGlyph = if (LocalTui.current) TuiIconGlyphs[imageVector] else null
    if (tuiGlyph != null) {
        TuiGlyphIcon(
            glyph = tuiGlyph,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        return
    }
    MaterialIcon(
        imageVector = when {
            LocalNothing.current -> NothingIconOverrides[imageVector] ?: imageVector
            LocalNinety8.current -> Ninety8IconOverrides[imageVector] ?: imageVector
            // Material's own Sharp cut: "every radius is zero" applied to the
            // icons by the people who drew them. The TUI's character half is
            // answered above this, so what reaches here is the rest.
            LocalTui.current -> SharpIconOverrides[imageVector] ?: imageVector
            // And the far end of the same range: "every corner is blown out"
            // applied to the icons by the same hands. See [AeroIconOverrides].
            LocalAero.current -> AeroIconOverrides[imageVector] ?: imageVector
            else -> imageVector
        },
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
