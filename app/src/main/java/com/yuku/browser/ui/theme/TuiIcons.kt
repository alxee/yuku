package com.yuku.browser.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.yuku.browser.R
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp

/**
 * The TUI theme's icons, in two halves — and neither half is a set someone
 * drew for the occasion.
 *
 * **Half one: the keys.** Where the thing an icon means is a character a
 * keyboard actually has, the theme draws that character, in the interface's
 * own monospace face — `<` and `>` for back and forward, `^` and `v` for the
 * two the find bar steps through, `x` closes, `+` adds, `/` searches (the
 * search key in `less`, `vim` and `man`), `:` is the menu (it is what opens
 * one in a modal editor), `\` and `/` are the two diagonal arrows, `=` the
 * drag handle. Nothing here is a picture of a key: it IS the key, set in the
 * same face at the same weight as the words beside it, which is what a
 * terminal has instead of an icon.
 *
 * That list is deliberately short. An earlier pass ran the idea through the
 * whole interface — single letters for the nouns, `b` for bookmark, `h` for
 * history — and a letter standing in for a picture reads as a keyboard
 * legend rather than as a control. A character earns its place only where it
 * is the symbol for the thing rather than the initial of its name.
 *
 * **Half two: Material Sharp.** Everything else is the SAME glyph the app
 * already draws, in Material's square-cornered cut. That is the cheapest
 * possible answer and the right one: the theme's whole grammar is that every
 * radius in the interface is zero ([specialCorner]), and Sharp is that
 * grammar applied to the icons by the people who drew them — same silhouette,
 * same weight, same optical size, corners squared off. Nothing is vendored,
 * nothing drifts out of step with Material, and a row that mixes a mapped
 * icon with an unmapped one still reads as one set.
 *
 * A pixel set was tried here first (Pixelarticons, borrowed from the 98
 * theme) and dropped: a bitmap glyph belongs to a machine with visible
 * pixels, where a TUI is a face on a grid, and mapping only four of them
 * left a row with a pixel chevron beside a Material padlock.
 */
internal val TuiIconGlyphs: Map<ImageVector, String> = mapOf(
    Icons.AutoMirrored.Filled.KeyboardArrowLeft to "<",
    Icons.AutoMirrored.Filled.KeyboardArrowRight to ">",
    Icons.Filled.KeyboardArrowUp to "^",
    Icons.Filled.KeyboardArrowDown to "v",
    Icons.Filled.Close to "x",
    Icons.Outlined.Close to "x",
    Icons.Filled.Add to "+",
    Icons.Filled.Search to "/",
    Icons.Outlined.Search to "/",
    Icons.Outlined.MoreVert to ":",
    // The suggestion row's "put this in the field" arrow points up and to
    // the left, and so does a backslash; its sibling points the other way.
    Icons.Filled.NorthWest to "\\",
    Icons.Filled.NorthEast to "/",
    Icons.Filled.DragHandle to "=",
)

/**
 * One [TuiIconGlyphs] character, drawn where an icon would have been.
 *
 * Sized off the box rather than off the ambient text style: an `Icon` call
 * site says how big its glyph is with a `Modifier.size`, and the character
 * has to answer the same measurement — 24dp when nothing is said, which is
 * Material's own default icon size and what [BoxWithConstraints] cannot be
 * asked for when the parent leaves the axis unbounded.
 *
 * [GLYPH_FILL] is under 1 because a font's em box is not its ink: at 1.0 a
 * `<` measures noticeably larger than the Material glyph it replaces in the
 * same row. The face is asked for BOLD for the opposite reason — one
 * character has a fraction of the ink a silhouette puts on the page, and at
 * a bar button's size a regular-weight `x` reads as text that wandered in
 * rather than as a control.
 */
@Composable
internal fun TuiGlyphIcon(
    glyph: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    BoxWithConstraints(
        modifier = modifier
            .defaultMinSize(TUI_GLYPH_DEFAULT, TUI_GLYPH_DEFAULT)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Only a FIXED constraint is a size someone asked for. An `Icon`
        // called with no modifier is 24dp because that is a vector's own
        // intrinsic size — Material never grows one to fill what it is
        // placed in — whereas BoxWithConstraints reports the maximum it is
        // ALLOWED, which inside an IconButton is the button's whole 40dp.
        // Taking that as the box drew every unmodified glyph at 40dp: the
        // settings pane's back chevron came up two thirds larger than the
        // Material one it replaces, next to a title measured against the
        // Material size.
        val c = constraints
        val w = if (c.hasFixedWidth && maxWidth.isFinite) maxWidth else TUI_GLYPH_DEFAULT
        val h = if (c.hasFixedHeight && maxHeight.isFinite) maxHeight else TUI_GLYPH_DEFAULT
        val box = minOf(w, h)
        val color = tint.takeOrElse { LocalContentColor.current }
        val glow = tuiGlow(color)
        val context = LocalContext.current
        val paint = remember(context) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ResourcesCompat.getFont(context, R.font.iosevka_term_bold)
            }
        }
        // Centred by INK, not by line box. A character's box is the font's
        // ascent and descent, and `<` `>` sit on the maths axis inside it —
        // centred as text they hung low and off to one side of the button
        // beside a Material glyph centred on its own 24 units.
        Canvas(Modifier.size(box)) {
            paint.textSize = box.toPx() * GLYPH_FILL
            paint.color = color.toArgb()
            if (glow != null) paint.setShadowLayer(glow.blurRadius, 0f, 0f, glow.color.toArgb())
            else paint.clearShadowLayer()
            val ink = android.graphics.Rect()
            paint.getTextBounds(glyph, 0, glyph.length, ink)
            drawIntoCanvas {
                it.nativeCanvas.drawText(glyph, center.x - ink.exactCenterX(), center.y - ink.exactCenterY(), paint)
            }
        }
    }
}

private val TUI_GLYPH_DEFAULT = 24.dp
// Above the old 0.86: 3270's x-height is ~20% under Droid Sans Mono's, so the
// same em puts less of a `<` on the page. Same correction as TUI_SIZE_PARITY.
private const val GLYPH_FILL = 1.0f
