package com.yuku.browser.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Nothing theme's own icon set.
 *
 * Material's glyphs are the wrong voice for this look and no amount of
 * tinting fixes it: they are drawn as SILHOUETTES — a solid bookmark, a
 * solid shield, a filled play triangle — and a silhouette is a picture of a
 * thing. This language draws the thing's DIAGRAM instead. So every glyph
 * here is one weight of line and nothing else, and where Material draws a
 * pictogram this set draws the primitive underneath it: a bin is a lid and a
 * U, an eye is a lens and a pupil, settings is two rails and two handles.
 *
 * The rules are the `nothing-design` skill's iconography section, and they
 * are rules rather than tendencies — an icon set is only a set if every
 * member was drawn to the same constraints:
 *
 * - **Monoline, [STROKE] units on a 24-unit grid, no fill anywhere.**
 * - **Round caps and joins.** This reverses the call made for the new-tab
 *   key's plus, which was drawn with butt caps on the argument that the look
 *   is machined. It is — but at a 1.5-unit stroke a butt cap is a visible
 *   corner on a line that has no business having one, and the reference set
 *   (Lucide, Phosphor Thin) is round-capped throughout. The plus was brought
 *   over to match; one set, one terminal.
 * - **A 20-unit live area** inside the 24-unit box, so a glyph never runs to
 *   the edge and every icon in a row optically weighs the same.
 * - **Five or six strokes, maximum.** The count is the discipline: it is
 *   what stops a glyph turning back into a picture.
 *
 * The set is deliberately PARTIAL, and the line it is drawn along is the
 * SURFACE rather than a count of glyphs: it covers the toolbar, the menu
 * sheet, the find bar, the switcher, the link overlay and the list screens
 * whole, because a row that mixes two icon languages is worse than a row in
 * either one. Settings and the ad blocker pane turn the special theme off
 * for their own subtrees and so keep Material's set, which is why nothing
 * that appears only there is drawn here — a mapping that can never fire is
 * a glyph nobody will ever check. Anything unmapped falls through to
 * Material (see `SpecialIcon.kt`).
 *
 * **Filled and outlined variants of one concept map to the SAME glyph**, by
 * the skill's "no filled icons" rule. That has a consequence worth knowing:
 * this app uses the filled/outlined pair to carry on/off on the menu's quick
 * tiles, and under this theme that signal is gone from the glyph. It is
 * carried instead by the tile's outline going to the accent and by the
 * glyph's own tint — which is why the outlined-tile treatment in `MenuSheet`
 * is load-bearing here and not decoration.
 *
 * These are hand-written path coordinates and nobody can read a glyph off
 * them. They were checked by transpiling this file's paths into an SVG
 * contact sheet and rasterising it; do that again rather than adding one
 * unseen.
 */
internal object NothingIcons {

    // ---- Marks -----------------------------------------------------------

    val Close = icon("Close") {
        moveTo(5f, 5f); lineTo(19f, 19f)
        moveTo(19f, 5f); lineTo(5f, 19f)
    }

    val Add = icon("Add") {
        moveTo(12f, 4.5f); lineTo(12f, 19.5f)
        moveTo(4.5f, 12f); lineTo(19.5f, 12f)
    }

    val Check = icon("Check") {
        moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f)
    }

    val MoreVert = icon("MoreVert") {
        dot(12f, 5.5f); dot(12f, 12f); dot(12f, 18.5f)
    }

    // ---- Chevrons --------------------------------------------------------
    //
    // One chevron drawn four ways rather than four chevrons: same arm length,
    // same included angle, so a row of them reads as one control turned
    // rather than as four glyphs. The horizontal pair is auto-mirrored — a
    // "back" chevron points the other way in an RTL layout, which is why
    // Material's own left/right arrows are AutoMirrored too.

    val ChevronUp = icon("ChevronUp") {
        moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
    }

    val ChevronDown = icon("ChevronDown") {
        moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
    }

    val ChevronLeft = icon("ChevronLeft", autoMirror = true) {
        moveTo(15f, 5f); lineTo(8.5f, 12f); lineTo(15f, 19f)
    }

    val ChevronRight = icon("ChevronRight", autoMirror = true) {
        moveTo(9f, 5f); lineTo(15.5f, 12f); lineTo(9f, 19f)
    }

    // ---- Page and chrome -------------------------------------------------

    val Search = icon("Search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.3f, 15.3f); lineTo(20f, 20f)
    }

    /** Body and shackle, both drawn — a padlock is a diagram of two parts. */
    val Lock = icon("Lock") {
        moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        moveTo(8.5f, 11f); lineTo(8.5f, 8.5f)
        arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8.5f)
        lineTo(15.5f, 11f)
    }

    /**
     * A ring that does not quite close, ending in a right-angle bracket.
     *
     * The first attempt hung a floating chevron off the top of the arc as an
     * arrowhead, which read as a hook rather than an arrow and — because the
     * barbs sat above the circle — made the glyph the tallest thing in the
     * address bar. This is the [Lucide] construction instead: the ring runs
     * round and turns up into a square corner, so the arrow is the ring's
     * own end rather than a mark stuck onto it. It is also the more
     * technical of the two, which is the right voice here.
     *
     * Its ink is 17 units across, the same as every other glyph in the set —
     * that is the actual fix for it looking oversized beside the copy glyph.
     */
    val Refresh = icon("Refresh") {
        moveTo(20.55f, 12f)
        arcTo(8.55f, 8.55f, 0f, true, true, 12f, 3.45f)
        curveTo(14.39f, 3.45f, 16.68f, 4.3f, 18.4f, 5.6f)
        lineTo(20.55f, 8.2f)
        moveTo(20.55f, 3.45f); lineTo(20.55f, 8.2f); lineTo(15.8f, 8.2f)
    }

    /** A flag with a notch cut out of its foot, not a filled tag. */
    val Bookmark = icon("Bookmark") {
        moveTo(6f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(12f, 15.5f); lineTo(6f, 20f); close()
    }

    /**
     * Three nodes and the two lines between them. The only glyph in the set
     * whose primitive is literally the theme's own motif — a share is a
     * graph, and a graph is dots joined up.
     */
    val Share = icon("Share") {
        circle(17.5f, 5.5f, 2.5f)
        circle(6.5f, 12f, 2.5f)
        circle(17.5f, 18.5f, 2.5f)
        moveTo(15.6f, 6.7f); lineTo(8.4f, 10.8f)
        moveTo(8.4f, 13.2f); lineTo(15.6f, 17.3f)
    }

    /** Two overlapping sheets, the back one drawn only where it shows. */
    val ContentCopy = icon("ContentCopy") {
        moveTo(3.5f, 8.5f); lineTo(15.5f, 8.5f); lineTo(15.5f, 20.5f); lineTo(3.5f, 20.5f); close()
        moveTo(8f, 8.5f); lineTo(8f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 16f); lineTo(15.5f, 16f)
    }

    /**
     * The corner arrow that lifts a suggestion back into the field. A shaft
     * and two barbs — the same construction as [Download]'s head, turned
     * onto the diagonal. Auto-mirrored, because what it points at is the
     * START of the field's text, and an RTL layout puts that on the other
     * side.
     */
    val NorthWest = icon("NorthWest", autoMirror = true) {
        moveTo(19f, 19f); lineTo(5f, 5f)
        moveTo(5f, 13f); lineTo(5f, 5f); lineTo(13f, 5f)
    }

    val Download = icon("Download") {
        moveTo(12f, 3.5f); lineTo(12f, 15.5f)
        moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
        moveTo(4f, 20f); lineTo(20f, 20f)
    }

    /** A dial and two hands: the instrument, not the concept. */
    val History = icon("History") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 6.5f); lineTo(12f, 12f); lineTo(15.5f, 14.5f)
    }

    /**
     * Two rails and two handles. Material draws a cogwheel here — twelve
     * teeth and a hub, which is well over the stroke budget and is a picture
     * of a machine part rather than of a setting. A fader is what a setting
     * actually is.
     */
    val Sliders = icon("Sliders") {
        moveTo(4f, 8.5f); lineTo(20f, 8.5f)
        moveTo(4f, 15.5f); lineTo(20f, 15.5f)
        moveTo(15.5f, 5.5f); lineTo(15.5f, 11.5f)
        moveTo(8.5f, 12.5f); lineTo(8.5f, 18.5f)
    }

    /**
     * A cog: a rim with eight stub teeth around it. [Sliders] is what a
     * SETTING is, which is why it draws the app's settings row — but the
     * menu has two settings rows next to each other now (the site's and the
     * app's), and two fader glyphs a row apart say nothing about which is
     * which. The cog is the older, blunter sign, and it is the one the app
     * around the browser has always been drawn with.
     *
     * Two versions were drawn and thrown away, and both failures are worth
     * keeping. The first was the obvious construction — one toothed
     * polyline, twenty-four vertices, two strokes, inside the set's budget —
     * and at [STROKE] on a 24-unit grid it came back as a black rosette: a
     * notch has to clear the stroke on both of its sides to survive as a
     * notch, and at this radius there is no tooth count where it does. The
     * second put the teeth outside the rim as separate ticks, which fixed
     * the weight and drew a SHIP'S WHEEL: long handles radiating off a thin
     * ring around a hub is that object exactly, and the hub was what
     * confirmed it.
     *
     * So: no hub, and the teeth are stubs barely longer than they are wide
     * (1.6 units against a 2.25 stroke, i.e. two round caps and almost
     * nothing between them). What separates a cog from a wheel is that its
     * teeth are short, blunt and many — eight of them, not six — and that
     * there is nothing in the middle. The ring stays open, which is also
     * where the glyph gets its lightness: it holds the same colour on the
     * page as [Sliders] beside it.
     *
     * Nine strokes, over the set's five-or-six. The budget is there to stop
     * a glyph turning back into a picture; eight identical marks around a
     * circle are a pattern, not a picture, and a cog with fewer teeth is a
     * different object.
     */
    val Gear = icon("Gear") {
        circle(12f, 12f, 6.9f)
        moveTo(19.20f, 12.00f); lineTo(20.80f, 12.00f)
        moveTo(17.09f, 17.09f); lineTo(18.22f, 18.22f)
        moveTo(12.00f, 19.20f); lineTo(12.00f, 20.80f)
        moveTo(6.91f, 17.09f); lineTo(5.78f, 18.22f)
        moveTo(4.80f, 12.00f); lineTo(3.20f, 12.00f)
        moveTo(6.91f, 6.91f); lineTo(5.78f, 5.78f)
        moveTo(12.00f, 4.80f); lineTo(12.00f, 3.20f)
        moveTo(17.09f, 6.91f); lineTo(18.22f, 5.78f)
    }

    /**
     * The forbidden sign: a ring with a rule across it, at the same angle as
     * the eye's slash so the two crossings-out read as one gesture.
     */
    val Block = icon("Block") {
        circle(12f, 12f, 8.5f)
        moveTo(6f, 18f); lineTo(18f, 6f)
    }

    // ---- Eye -------------------------------------------------------------

    val Visibility = icon("Visibility") {
        lens()
        circle(12f, 12f, 3.2f)
    }

    /**
     * The same lens with a rule through it. The slash runs corner to corner
     * rather than stopping at the lens: a stroke that ends inside the glyph
     * reads as part of the drawing, and this one has to read as something
     * done TO it.
     */
    val VisibilityOff = icon("VisibilityOff") {
        lens()
        circle(12f, 12f, 3.2f)
        moveTo(4f, 20f); lineTo(20f, 4f)
    }

    // ---- Objects ---------------------------------------------------------

    /** Lid, handle, body. A bin is three lines and the shape between them. */
    val Delete = icon("Delete") {
        moveTo(4f, 6.5f); lineTo(20f, 6.5f)
        moveTo(9.5f, 6.5f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 6.5f)
        moveTo(6.5f, 6.5f); lineTo(6.5f, 19.5f); lineTo(17.5f, 19.5f); lineTo(17.5f, 6.5f)
    }

    /** A window and its chrome bar — the browser, drawn as its own frame. */
    val Window = icon("Window") {
        moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
        moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f)
    }

    /**
     * A frame with its corner open and an arrow leaving through it. The
     * frame's two open ends stop 3.5 units short of the arrow's bracket
     * rather than 2 — at this stroke the round caps of the two were within a
     * unit of touching and the corner read as a smudge.
     */
    val OpenInNew = icon("OpenInNew", autoMirror = true) {
        moveTo(10.5f, 5f); lineTo(5f, 5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 13.5f)
        moveTo(14f, 5f); lineTo(19f, 5f); lineTo(19f, 10f)
        moveTo(11f, 13f); lineTo(19f, 5f)
    }

    val Monitor = icon("Monitor") {
        moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 16f); lineTo(3.5f, 16f); close()
        moveTo(12f, 16f); lineTo(12f, 19.5f)
        moveTo(8.5f, 19.5f); lineTo(15.5f, 19.5f)
    }

    /** A handset, and the rule that is its screen's bottom edge. */
    val Phone = icon("Phone") {
        moveTo(6.5f, 2.5f); lineTo(17.5f, 2.5f); lineTo(17.5f, 21.5f); lineTo(6.5f, 21.5f); close()
        moveTo(6.5f, 18f); lineTo(17.5f, 18f)
    }

    /** Frame, sun, horizon. */
    val Image = icon("Image") {
        moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
        circle(8.5f, 9.5f, 2.1f)
        moveTo(4f, 17.5f); lineTo(9.5f, 12f); lineTo(13.5f, 16f); lineTo(16.5f, 13f); lineTo(20.5f, 17f)
    }

    /**
     * An open book: one outline for both boards and a rule down the spine.
     *
     * It replaced a column of three rules, which is the correct diagram of
     * an ARTICLE and the wrong sign for what this row does — the reader is
     * not a page of text, it is the page of text taken out of the site and
     * handed over as something to read. The rules also had a second job
     * elsewhere in the language (a stack of horizontal lines is a list, a
     * menu, a paragraph), and a glyph that means three things means none.
     *
     * The two boards sag toward the spine and lift at the outer edges,
     * which is the whole of the drawing: it is what separates an open book
     * from an envelope or a folded card at this size.
     */
    val Book = icon("Book", autoMirror = true) {
        moveTo(12f, 8.4f); lineTo(4.2f, 6.2f); lineTo(4.2f, 16.4f); lineTo(12f, 18.6f)
        lineTo(19.8f, 16.4f); lineTo(19.8f, 6.2f); close()
        moveTo(12f, 8.4f); lineTo(12f, 18.6f)
    }

    val Folder = icon("Folder") {
        moveTo(3.5f, 19f); lineTo(3.5f, 5.5f); lineTo(9.5f, 5.5f); lineTo(11.5f, 8.5f)
        lineTo(20.5f, 8.5f); lineTo(20.5f, 19f); close()
    }

    /** Three arcs and a floor — the outline of a cloud, not a blob of one. */
    val Cloud = icon("Cloud") {
        moveTo(7f, 18f)
        arcTo(4.2f, 4.2f, 0f, false, true, 7.4f, 9.7f)
        arcTo(5.2f, 5.2f, 0f, false, true, 17.2f, 11f)
        arcTo(3.6f, 3.6f, 0f, false, true, 17f, 18f)
        close()
    }

    val Key = icon("Key") {
        circle(7.5f, 12f, 3.8f)
        moveTo(11.3f, 12f); lineTo(20.5f, 12f)
        moveTo(17f, 12f); lineTo(17f, 15.5f)
        moveTo(20.5f, 12f); lineTo(20.5f, 15f)
    }

    /**
     * The crescent's two arcs are the intersection of a circle at (12,12)
     * r=8.5 with one at (16.5,7.5) r=8, computed rather than eyeballed —
     * endpoints that miss by a tenth of a unit leave a visible nick at a
     * round cap.
     */
    val DarkMode = icon("DarkMode") {
        moveTo(20.07f, 14.66f)
        arcTo(8.5f, 8.5f, 0f, true, true, 9.34f, 3.93f)
        arcTo(8f, 8f, 0f, false, false, 20.07f, 14.66f)
        close()
    }
}

/**
 * Where a Material glyph gives way to one of [NothingIcons].
 *
 * Keyed by the vector itself: `ImageVector` is a data-like type with a real
 * `equals`, so `Icons.Default.Close` looks up correctly however Material
 * chose to cache it — which an identity map would not survive a change to.
 *
 * Filled and outlined variants of a concept share an entry on purpose; see
 * the object's doc. Every entry here is reachable from a surface that
 * actually renders under this theme — see there too.
 */
internal val NothingIconOverrides: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        Icons.Filled.Close to NothingIcons.Close,
        Icons.Outlined.Close to NothingIcons.Close,
        Icons.Filled.Add to NothingIcons.Add,
        Icons.Filled.Check to NothingIcons.Check,
        Icons.Outlined.MoreVert to NothingIcons.MoreVert,

        Icons.Filled.KeyboardArrowUp to NothingIcons.ChevronUp,
        Icons.Filled.KeyboardArrowDown to NothingIcons.ChevronDown,
        Icons.AutoMirrored.Filled.KeyboardArrowLeft to NothingIcons.ChevronLeft,
        Icons.AutoMirrored.Filled.KeyboardArrowRight to NothingIcons.ChevronRight,

        Icons.Filled.Search to NothingIcons.Search,
        Icons.Outlined.Search to NothingIcons.Search,
        Icons.Filled.Lock to NothingIcons.Lock,
        Icons.Filled.Refresh to NothingIcons.Refresh,
        Icons.Outlined.Refresh to NothingIcons.Refresh,
        Icons.Filled.Bookmark to NothingIcons.Bookmark,
        Icons.Outlined.BookmarkBorder to NothingIcons.Bookmark,
        Icons.Outlined.Share to NothingIcons.Share,
        Icons.Filled.ContentCopy to NothingIcons.ContentCopy,
        Icons.Outlined.ContentCopy to NothingIcons.ContentCopy,
        Icons.Filled.Download to NothingIcons.Download,
        Icons.Filled.NorthWest to NothingIcons.NorthWest,
        Icons.Filled.History to NothingIcons.History,
        Icons.Filled.Settings to NothingIcons.Gear,
        Icons.Filled.Tune to NothingIcons.Sliders,
        Icons.Filled.Block to NothingIcons.Block,

        Icons.Filled.Visibility to NothingIcons.Visibility,
        Icons.Filled.VisibilityOff to NothingIcons.VisibilityOff,

        Icons.Filled.Delete to NothingIcons.Delete,
        Icons.Filled.DeleteOutline to NothingIcons.Delete,
        Icons.Filled.DeleteSweep to NothingIcons.Delete,
        Icons.Filled.Tab to NothingIcons.Window,
        Icons.Filled.OpenInBrowser to NothingIcons.OpenInNew,
        Icons.Outlined.OpenInBrowser to NothingIcons.OpenInNew,
        Icons.AutoMirrored.Filled.OpenInNew to NothingIcons.OpenInNew,
        Icons.Filled.DesktopWindows to NothingIcons.Monitor,
        Icons.Outlined.DesktopWindows to NothingIcons.Monitor,
        Icons.Filled.PhoneAndroid to NothingIcons.Phone,
        Icons.Outlined.Image to NothingIcons.Image,
        Icons.AutoMirrored.Filled.MenuBook to NothingIcons.Book,
        Icons.AutoMirrored.Outlined.MenuBook to NothingIcons.Book,
        Icons.Filled.FolderOpen to NothingIcons.Folder,
        Icons.Filled.Cloud to NothingIcons.Cloud,
        Icons.Filled.Key to NothingIcons.Key,
        Icons.Filled.Password to NothingIcons.Key,
        Icons.Filled.DarkMode to NothingIcons.DarkMode,
        Icons.Outlined.DarkMode to NothingIcons.DarkMode,
    )
}

/**
 * The one stroke weight in the set, in the 24-unit viewport's own units.
 *
 * The skill asks for 1.5 and 1.5 is what a Lucide-style set on a WEB page
 * wants. On a phone it came out thin, and the reason is what it sits beside:
 * these glyphs share their rows with Material's own icons wherever the set
 * falls through, and with type — and a Material glyph is a SILHOUETTE, so
 * its ink is two or three units wide everywhere a monoline glyph is one and
 * a half. A wireframe set has to be drawn heavier than its nominal weight to
 * hold the same colour on the page as the solid one it replaces.
 *
 * 2.25 is where it stops reading as a hairline and starts reading as drawn,
 * without the tight interiors (the eye's pupil, the share graph's nodes,
 * the key's bow) closing up.
 */
private const val STROKE = 2.25f

private fun icon(
    name: String,
    autoMirror: Boolean = false,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = "Nothing.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
).apply {
    // Black, and then never seen: `Icon` tints the whole painter through a
    // SrcIn colour filter, which keeps the antialiased edge's alpha and
    // replaces the colour. So the stroke colour here is a placeholder and
    // the glyph inherits the content colour like any Material one.
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()

/** A full circle as two half arcs — there is no circle command in a path. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

/**
 * One dot of the matrix — a hair of a line with a round cap, so it comes out
 * as a disc of exactly [STROKE] across.
 *
 * That is the honest construction rather than a trick: a dot in this set IS
 * a stroke terminal, the same mark that ends every other line, which is what
 * lets a dotted glyph and a drawn one sit in the same row. Drawn as a tiny
 * stroked CIRCLE instead it comes out as a RING — the stroke straddles the
 * radius and leaves the middle open — which is a different mark entirely,
 * and the theme whose whole motif is a field of dots cannot afford to draw
 * one wrong. The segment is 0.01 long rather than 0 because a renderer is
 * entitled to drop a zero-length one.
 */
private fun PathBuilder.dot(cx: Float, cy: Float) {
    moveTo(cx, cy)
    lineTo(cx + 0.01f, cy)
}

/**
 * The eye's outline: two symmetric curves meeting in a point at each side.
 * Shared by [NothingIcons.Visibility] and [NothingIcons.VisibilityOff] so
 * the slash is visibly the only difference between them.
 */
private fun PathBuilder.lens() {
    moveTo(3f, 12f)
    curveTo(6.2f, 6.8f, 17.8f, 6.8f, 21f, 12f)
    curveTo(17.8f, 17.2f, 6.2f, 17.2f, 3f, 12f)
    close()
}
