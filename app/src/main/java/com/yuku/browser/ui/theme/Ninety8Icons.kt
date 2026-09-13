package com.yuku.browser.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The 98 theme's icons: Open Iconic (useiconic.com/open, Iconic/Waybury, MIT
 * + SIL OFL), vendored as the SVG path data it ships.
 *
 * **Why this set and not Material's.** The theme's type is an outline
 * grotesque now (see [Ninety8Family]) and its icons came back from
 * Pixelarticons to Material Sharp with it — but Sharp is a modern set with
 * its corners squared, and squared corners are not what makes a glyph look
 * like 1998. What does is the DRAWING: a period toolbar icon was cut on a
 * 16-pixel grid, so its strokes are thick and even, its terminals blunt, its
 * metaphors literal objects rather than abstractions — a filing folder, a
 * globe with continents on it, a monitor on a stand, a knob with a pointer.
 *
 * Open Iconic is drawn on an EIGHT-unit grid, for use at 8px and up, and
 * that constraint puts it in the same place by the same route: nothing in it
 * can be thinner than a stroke or subtler than a right angle. It is a vector
 * set, so it is smooth at 400dpi — the pixels are gone and the proportions
 * they forced are what is kept, which is the same trade the face makes.
 *
 * **Every glyph is placed, not just parsed** — see [Glyph]. The set is drawn
 * to the EDGES of its 8x8 box and several glyphs sit against one of them, so
 * dropped into a 24dp slot as they ship they come out both oversized beside
 * Material's own icons and, for the ones that are not symmetrical, visibly
 * off-centre.
 *
 * **Coverage is complete on purpose.** Every key in this table has a glyph;
 * nothing falls through to Material, because a row that mixes an eight-unit
 * glyph with a Material one is a row with two icon sets in it. Where Open
 * Iconic has no equivalent the nearest OBJECT stands in rather than the
 * nearest abstraction — a cookie banner is a dialog (`comment-square`), the
 * keyboard setting is the machine that has one (`laptop`), a tap target is
 * `target`, the tuning row is a `dial`. That is how the period drew things
 * too: it had no vocabulary of gestures and pictured the hardware instead.
 *
 * Bookmarks are a BOOK, not Open Iconic's `bookmark`. That one is the
 * ribbon: 4 units wide against a full 8 tall, so even centred exactly it
 * reads as off-centre — a thin vertical mark in a row of glyphs that all
 * fill their width, with the eye taking the empty thirds either side of it
 * as the icon sitting wrong rather than as the shape being narrow. The book
 * is 7 by 8 with a ribbon in it, which says the same thing at the weight of
 * everything beside it, and it is the more period object anyway.
 *
 * Private mode is a MOON, and it got there by elimination: the set has an
 * `eye` and no crossed-out one, and `ban` — a circle with a bar through it,
 * which is what stood there first — is the international sign for FORBIDDEN.
 * A private tab is not a blocked one, and a badge saying so on every card in
 * the switcher reads as an error state. (`ban` earns its place elsewhere: it
 * is the ad blocker, where forbidden is exactly what is meant.) The moon is
 * the same metaphor the app's own private palette runs on. It doubles as
 * page-dark, which the two can afford: they are never on screen together.
 *
 * Vendored as `d` strings rather than as hand-written path builders because
 * these are somebody else's drawings and the point is that they arrive
 * unedited — a builder transcription is a place to introduce a difference.
 * The placement is kept OUT of the path data for the same reason: it is a
 * translation stated beside the drawing, not a rewrite of it.
 * A `d` string is not reviewable by eye, so the check is the one the Nothing
 * set used: rasterise the lot to a contact sheet and look at it.
 */
private class Glyph(
    /** Open Iconic's own path data, verbatim, on its 8x8 grid. */
    val path: String,
    /** How far to move it so its ink is centred in [BOX]. */
    val dx: Float,
    val dy: Float,
)

private val GLYPHS: Map<String, Glyph> = mapOf(
    "action-redo" to Glyph("M3.5 0c-1.93 0-3.5 1.57-3.5 3.5 0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v.5h-1l2 2 2-2h-1v-.5c0-1.93-1.57-3.5-3.5-3.5z", 1.0f, 1.0f),
    "action-undo" to Glyph("M4.5 0c-1.93 0-3.5 1.57-3.5 3.5v.5h-1l2 2 2-2h-1v-.5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5c0-1.93-1.57-3.5-3.5-3.5z", 1.0f, 1.0f),
    "arrow-thick-left" to Glyph("M3 0l-3 3.03 3 2.97v-2h5v-2h-5v-2z", 1.0f, 1.0f),
    "ban" to Glyph("M4 0c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 1c.54 0 1.04.15 1.47.4l-4.06 4.06c-.26-.42-.41-.92-.41-1.46 0-1.66 1.34-3 3-3zm2.59 1.53c.26.43.41.93.41 1.47 0 1.66-1.34 3-3 3-.54 0-1.04-.15-1.47-.41l4.06-4.06z", 1.0f, 1.0f),
    "book" to Glyph("M1 0c-.07 0-.13.01-.19.03-.39.08-.7.39-.78.78-.03.06-.03.12-.03.19v5.5c0 .83.67 1.5 1.5 1.5h5.5v-1h-5.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5h5.5v-5.5c0-.28-.22-.5-.5-.5h-.5v3l-1-1-1 1v-3h-3z", 1.5f, 1.0f),
    "browser" to Glyph("M.34 0a.5.5 0 0 0-.34.5v7a.5.5 0 0 0 .5.5h7a.5.5 0 0 0 .5-.5v-7a.5.5 0 0 0-.5-.5h-7a.5.5 0 0 0-.09 0 .5.5 0 0 0-.06 0zm1.16 1c.28 0 .5.22.5.5s-.22.5-.5.5-.5-.22-.5-.5.22-.5.5-.5zm2 0h3c.28 0 .5.22.5.5s-.22.5-.5.5h-3c-.28 0-.5-.22-.5-.5s.22-.5.5-.5zm-2.5 2h6v4h-6v-4z", 1.0f, 1.0f),
    "brush" to Glyph("M7.44.03c-.03 0-.04.02-.06.03l-3.75 2.66c-.04.03-.1.11-.13.16l-.13.25c.72.23 1.27.78 1.5 1.5l.25-.13c.05-.03.12-.08.16-.13l2.66-3.75c.03-.05.04-.09 0-.13l-.44-.44c-.02-.02-.04-.03-.06-.03zm-4.78 3.97c-.74 0-1.31.61-1.31 1.34 0 .99-.55 1.85-1.34 2.31.39.22.86.34 1.34.34 1.47 0 2.66-1.18 2.66-2.66 0-.74-.61-1.34-1.34-1.34z", 1.01f, 0.99f),
    "check" to Glyph("M6.41 0l-.69.72-2.78 2.78-.81-.78-.72-.72-1.41 1.41.72.72 1.5 1.5.69.72.72-.72 3.5-3.5.72-.72-1.44-1.41z", 1.07f, 0.83f),
    "chevron-bottom" to Glyph("M1.5 0l-1.5 1.5 4 4 4-4-1.5-1.5-2.5 2.5-2.5-2.5z", 1.0f, 1.25f),
    "chevron-left" to Glyph("M4 0l-4 4 4 4 1.5-1.5-2.5-2.5 2.5-2.5-1.5-1.5z", 1.25f, 1.0f),
    "chevron-right" to Glyph("M1.5 0l-1.5 1.5 2.5 2.5-2.5 2.5 1.5 1.5 4-4-4-4z", 1.25f, 1.0f),
    "chevron-top" to Glyph("M4 0l-4 4 1.5 1.5 2.5-2.5 2.5 2.5 1.5-1.5-4-4z", 1.0f, 1.25f),
    "clipboard" to Glyph("M3.5 0c-.28 0-.5.22-.5.5v.5h-.75c-.14 0-.25.11-.25.25v.75h3v-.75c0-.14-.11-.25-.25-.25h-.75v-.5c0-.28-.22-.5-.5-.5zm-3.25 1c-.14 0-.25.11-.25.25v6.5c0 .14.11.25.25.25h6.5c.14 0 .25-.11.25-.25v-6.5c0-.14-.11-.25-.25-.25h-.75v2h-5v-2h-.75z", 1.5f, 1.0f),
    "clock" to Glyph("M4 0c-2.2 0-4 1.8-4 4s1.8 4 4 4 4-1.8 4-4-1.8-4-4-4zm0 1c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm-.5 1v2.22l.16.13.5.5.34.38.72-.72-.38-.34-.34-.34v-1.81h-1z", 1.0f, 1.0f),
    "cloud" to Glyph("M4.5 0c-1.21 0-2.27.86-2.5 2-1.1 0-2 .9-2 2s.9 2 2 2h4.5c.83 0 1.5-.67 1.5-1.5 0-.65-.42-1.29-1-1.5v-.5c0-1.38-1.12-2.5-2.5-2.5z", 1.0f, 1.0f),
    "cog" to Glyph("M3.5 0l-.5 1.19c-.1.03-.19.08-.28.13l-1.19-.5-.72.72.5 1.19c-.05.1-.09.18-.13.28l-1.19.5v1l1.19.5c.04.1.08.18.13.28l-.5 1.19.72.72 1.19-.5c.09.04.18.09.28.13l.5 1.19h1l.5-1.19c.09-.04.19-.08.28-.13l1.19.5.72-.72-.5-1.19c.04-.09.09-.19.13-.28l1.19-.5v-1l-1.19-.5c-.03-.09-.08-.19-.13-.28l.5-1.19-.72-.72-1.19.5c-.09-.04-.19-.09-.28-.13l-.5-1.19h-1zm.5 2.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5-1.5-.67-1.5-1.5.67-1.5 1.5-1.5z", 1.0f, 0.99f),
    "comment-square" to Glyph("M.09 0c-.06 0-.09.04-.09.09v5.81c0 .05.04.09.09.09h5.91l2 2v-7.91c0-.06-.04-.09-.09-.09h-7.81z", 1.0f, 1.01f),
    "data-transfer-download" to Glyph("M3 0v3h-2l3 3 3-3h-2v-3h-2zm-3 7v1h8v-1h-8z", 1.0f, 1.0f),
    "delete" to Glyph("M2 0l-2 3 2 3h6v-6h-6zm1.5.78l1.5 1.5 1.5-1.5.72.72-1.5 1.5 1.5 1.5-.72.72-1.5-1.5-1.5 1.5-.72-.72 1.5-1.5-1.5-1.5.72-.72z", 1.0f, 1.0f),
    "dial" to Glyph("M4 0c-2.2 0-4 1.8-4 4h1c0-1.66 1.34-3 3-3s3 1.34 3 3h1c0-2.2-1.8-4-4-4zm-.59 2.09c-.81.25-1.41 1.01-1.41 1.91 0 1.11.9 2 2 2 1.11 0 2-.89 2-2 0-.9-.59-1.65-1.41-1.91l-.59.88-.59-.88z", 1.0f, 1.0f),
    "document" to Glyph("M0 0v8h7v-4h-4v-4h-3zm4 0v3h3l-3-3zm-3 2h1v1h-1v-1zm0 2h1v1h-1v-1zm0 2h4v1h-4v-1z", 1.5f, 1.0f),
    "ellipses" to Glyph("M0 0v2h2v-2h-2zm3 0v2h2v-2h-2zm3 0v2h2v-2h-2z", 1.0f, 1.0f),
    "external-link" to Glyph("M0 0v8h8v-2h-1v1h-6v-6h1v-1h-2zm4 0l1.5 1.5-2.5 2.5 1 1 2.5-2.5 1.5 1.5v-4h-4z", 1.0f, 1.0f),
    "eye" to Glyph("M4.03 0c-2.53 0-4.03 3-4.03 3s1.5 3 4.03 3c2.47 0 3.97-3 3.97-3s-1.5-3-3.97-3zm-.03 1c1.11 0 2 .9 2 2 0 1.11-.89 2-2 2-1.1 0-2-.89-2-2 0-1.1.9-2 2-2zm0 1c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1c0-.1-.04-.19-.06-.28-.08.16-.24.28-.44.28-.28 0-.5-.22-.5-.5 0-.2.12-.36.28-.44-.09-.03-.18-.06-.28-.06z", 1.0f, 1.0f),
    "file" to Glyph("M0 0v8h7v-4h-4v-4h-3zm4 0v3h3l-3-3z", 1.5f, 1.0f),
    "folder" to Glyph("M0 0v2h8v-1h-5v-1h-3zm0 3v4.5c0 .28.22.5.5.5h7c.28 0 .5-.22.5-.5v-4.5h-8z", 1.0f, 1.0f),
    "globe" to Glyph("M4 0c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 1c.33 0 .64.09.94.19-.21.2-.45.38-.41.56.04.18.69.13.69.5 0 .27-.42.35-.13.66.35.35-.64.98-.66 1.44-.03.83.84.97 1.53.97.42 0 .53.2.5.44-.54.77-1.46 1.25-2.47 1.25-.38 0-.73-.09-1.06-.22.22-.44-.28-1.31-.75-1.59-.23-.23-.72-.14-1-.25-.09-.27-.18-.54-.19-.84.03-.05.08-.09.16-.09.19 0 .45.38.59.34.18-.04-.74-1.31-.31-1.56.2-.12.6.39.47-.16-.12-.51.36-.28.66-.41.26-.11.45-.41.13-.59-.06-.03-.13-.1-.22-.19.45-.27.97-.44 1.53-.44zm2.31 1.09c.18.22.32.46.44.72 0 .01 0 .02 0 .03-.04.07-.11.11-.22.22-.28.28-.32-.21-.44-.31-.13-.12-.6.02-.66-.13-.07-.18.5-.42.88-.53z", 1.0f, 1.0f),
    "grid-three-up" to Glyph("M0 0v2h2v-2h-2zm3 0v2h2v-2h-2zm3 0v2h2v-2h-2zm-6 3v2h2v-2h-2zm3 0v2h2v-2h-2zm3 0v2h2v-2h-2zm-6 3v2h2v-2h-2zm3 0v2h2v-2h-2zm3 0v2h2v-2h-2z", 1.0f, 1.0f),
    "hard-drive" to Glyph("M.19 0c-.11 0-.19.08-.19.19v3.31c0 .28.22.5.5.5h6c.28 0 .5-.22.5-.5v-3.31c0-.11-.08-.19-.19-.19h-6.63zm-.19 4.91v2.91c0 .11.08.19.19.19h6.63c.11 0 .19-.08.19-.19v-2.91c-.16.06-.32.09-.5.09h-6c-.18 0-.34-.04-.5-.09zm5.5 1.09c.28 0 .5.22.5.5s-.22.5-.5.5-.5-.22-.5-.5.22-.5.5-.5z", 1.49f, 1.0f),
    "image" to Glyph("M0 0v8h8v-8h-8zm1 1h6v3l-1-1-1 1 2 2v1h-1l-4-4-1 1v-3z", 1.0f, 1.0f),
    "key" to Glyph("M5.5 0c-1.38 0-2.5 1.12-2.5 2.5 0 .16 0 .32.03.47l-3.03 3.03v2h3v-2h2v-1l.03-.03c.15.03.31.03.47.03 1.38 0 2.5-1.12 2.5-2.5s-1.12-2.5-2.5-2.5zm.5 1c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1z", 1.0f, 1.0f),
    "laptop" to Glyph("M1.34 0a.5.5 0 0 0-.34.5v3.5h-1v1.5c0 .28.22.5.5.5h7.010000000000001c.28 0 .5-.22.5-.5v-1.5h-1v-3.5a.5.5 0 0 0-.5-.5h-5a.5.5 0 0 0-.09 0 .5.5 0 0 0-.06 0zm.66 1h4v3h-1v1h-2v-1h-1v-3z", 0.99f, 1.0f),
    "layers" to Glyph("M0 0v4h4v-4h-4zm5 2v3h-3v1h4v-4h-1zm2 2v3h-3v1h4v-4h-1z", 1.0f, 1.0f),
    "link-intact" to Glyph("M5.88.03c-.18.01-.36.03-.53.09-.27.1-.53.25-.75.47a.5.5 0 1 0 .69.69c.11-.11.24-.17.38-.22.35-.12.78-.07 1.06.22.39.39.39 1.04 0 1.44l-1.5 1.5c-.44.44-.8.48-1.06.47-.26-.01-.41-.13-.41-.13a.5.5 0 1 0-.5.88s.34.22.84.25c.5.03 1.2-.16 1.81-.78l1.5-1.5c.78-.78.78-2.04 0-2.81-.28-.28-.61-.45-.97-.53-.18-.04-.38-.04-.56-.03zm-2 2.31c-.5-.02-1.19.15-1.78.75l-1.5 1.5c-.78.78-.78 2.04 0 2.81.56.56 1.36.72 2.06.47.27-.1.53-.25.75-.47a.5.5 0 1 0-.69-.69c-.11.11-.24.17-.38.22-.35.12-.78.07-1.06-.22-.39-.39-.39-1.04 0-1.44l1.5-1.5c.4-.4.75-.45 1.03-.44.28.01.47.09.47.09a.5.5 0 1 0 .44-.88s-.34-.2-.84-.22z", 1.0f, 0.99f),
    "lock-locked" to Glyph("M3 0c-1.1 0-2 .9-2 2v1h-1v4h6v-4h-1v-1c0-1.1-.9-2-2-2zm0 1c.56 0 1 .44 1 1v1h-2v-1c0-.56.44-1 1-1z", 1.0f, 1.5f),
    "magnifying-glass" to Glyph("M3.5 0c-1.93 0-3.5 1.57-3.5 3.5s1.57 3.5 3.5 3.5c.59 0 1.17-.14 1.66-.41a1 1 0 0 0 .13.13l1 1a1.02 1.02 0 1 0 1.44-1.44l-1-1a1 1 0 0 0-.16-.13c.27-.49.44-1.06.44-1.66 0-1.93-1.57-3.5-3.5-3.5zm0 1c1.39 0 2.5 1.11 2.5 2.5 0 .66-.24 1.27-.66 1.72-.01.01-.02.02-.03.03a1 1 0 0 0-.13.13c-.44.4-1.04.63-1.69.63-1.39 0-2.5-1.11-2.5-2.5s1.11-2.5 2.5-2.5z", 0.96f, 0.97f),
    "menu" to Glyph("M0 0v1h8v-1h-8zm0 2.97v1h8v-1h-8zm0 3v1h8v-1h-8z", 1.0f, 0.51f),
    "monitor" to Glyph("M.34 0a.5.5 0 0 0-.34.5v5a.5.5 0 0 0 .5.5h2.5v1h-1c-.55 0-1 .45-1 1h6c0-.55-.45-1-1-1h-1v-1h2.5a.5.5 0 0 0 .5-.5v-5a.5.5 0 0 0-.5-.5h-7a.5.5 0 0 0-.09 0 .5.5 0 0 0-.06 0zm.66 1h6v4h-6v-4z", 1.0f, 1.0f),
    "moon" to Glyph("M2.72 0c-1.58.53-2.72 2.02-2.72 3.78 0 2.21 1.79 4 4 4 1.76 0 3.25-1.14 3.78-2.72-.4.13-.83.22-1.28.22-2.21 0-4-1.79-4-4 0-.45.08-.88.22-1.28z", 1.11f, 1.11f),
    "phone" to Glyph("M.19 0c-.11 0-.19.08-.19.19v7.63c0 .11.08.19.19.19h4.63c.11 0 .19-.08.19-.19v-7.63c0-.11-.08-.19-.19-.19h-4.63zm.81 1h3v5h-3v-5zm1.5 5.5c.28 0 .5.22.5.5s-.22.5-.5.5-.5-.22-.5-.5.22-.5.5-.5z", 1.49f, 1.0f),
    "plus" to Glyph("M3 0v3h-3v2h3v3h2v-3h3v-2h-3v-3h-2z", 1.0f, 1.0f),
    "reload" to Glyph("M4 0c-2.2 0-4 1.8-4 4s1.8 4 4 4c1.1 0 2.12-.43 2.84-1.16l-.72-.72c-.54.54-1.29.88-2.13.88-1.66 0-3-1.34-3-3s1.34-3 3-3c.83 0 1.55.36 2.09.91l-1.09 1.09h3v-3l-1.19 1.19c-.72-.72-1.71-1.19-2.81-1.19z", 1.0f, 1.0f),
    "share-boxed" to Glyph("M.75 0c-.41 0-.75.34-.75.75v5.5c0 .41.34.75.75.75h4.5c.41 0 .75-.34.75-.75v-1.25h-1v1h-4v-5h2v-1h-2.25zm5.25 0v1c-2.05 0-3.7 1.54-3.94 3.53.21-.88.99-1.53 1.94-1.53h2v1l2-2-2-2z", 1.0f, 1.5f),
    "shield" to Glyph("M4 0l-.19.09-3.5 1.47-.31.13v.31c0 1.66.67 3.12 1.47 4.19.4.53.83.97 1.25 1.28.42.31.83.53 1.28.53.46 0 .86-.22 1.28-.53.42-.31.85-.75 1.25-1.28.8-1.07 1.47-2.53 1.47-4.19v-.31l-.31-.13-3.5-1.47-.19-.09zm0 1.09v5.91c-.04 0-.33-.07-.66-.31s-.71-.63-1.06-1.09c-.64-.85-1.14-2.03-1.22-3.28l2.94-1.22z", 1.0f, 1.0f),
    "target" to Glyph("M4 0c-2.2 0-4 1.8-4 4s1.8 4 4 4 4-1.8 4-4-1.8-4-4-4zm0 1c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 1c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 1c.56 0 1 .44 1 1s-.44 1-1 1-1-.44-1-1 .44-1 1-1z", 1.0f, 1.0f),
    "terminal" to Glyph("M.09 0c-.06 0-.09.04-.09.09v7.81c0 .05.04.09.09.09h7.81c.05 0 .09-.04.09-.09v-7.81c0-.06-.04-.09-.09-.09h-7.81zm1.41.78l1.72 1.72-1.72 1.72-.72-.72 1-1-1-1 .72-.72zm2.5 2.22h3v1h-3v-1z", 1.0f, 1.0f),
    "transfer" to Glyph("M6 0v1h-6v1h6v1l2-1.5-2-1.5zm-4 4l-2 1.5 2 1.5v-1h6v-1h-6v-1z", 1.0f, 1.5f),
    "trash" to Glyph("M3 0c-.55 0-1 .45-1 1h-1c-.55 0-1 .45-1 1h7c0-.55-.45-1-1-1h-1c0-.55-.45-1-1-1h-1zm-2 3v4.81c0 .11.08.19.19.19h4.63c.11 0 .19-.08.19-.19v-4.81h-1v3.5c0 .28-.22.5-.5.5s-.5-.22-.5-.5v-3.5h-1v3.5c0 .28-.22.5-.5.5s-.5-.22-.5-.5v-3.5h-1z", 1.5f, 1.0f),
    "x" to Glyph("M1.41 0l-1.41 1.41.72.72 1.78 1.81-1.78 1.78-.72.69 1.41 1.44.72-.72 1.81-1.81 1.78 1.81.69.72 1.44-1.44-.72-.69-1.81-1.78 1.81-1.81.72-.72-1.44-1.41-.69.72-1.78 1.78-1.81-1.78-.72-.72z", 1.07f, 1.07f),
)

/**
 * One Open Iconic glyph, centred in a box a unit wider than the grid it was
 * drawn on.
 *
 * [BOX] is 10 to the set's 8, so a glyph that fills its grid edge to edge
 * ends up covering 80% of the slot — which is about the live area Material
 * draws its own icons to, and is why these no longer tower over the ones
 * they sit beside. The translation is per glyph and comes from the ink's
 * BOUNDS: a clipboard is drawn hard against the left edge of the grid and a
 * padlock against the top, so centring the box they ship in would leave both
 * off-centre on screen.
 */
private fun oi(name: String): ImageVector {
    val glyph = GLYPHS.getValue(name)
    return ImageVector.Builder(
        name = "OpenIconic.$name",
        defaultWidth = ICON_SIZE,
        defaultHeight = ICON_SIZE,
        viewportWidth = BOX,
        viewportHeight = BOX,
    )
        .addGroup(name = name, translationX = glyph.dx, translationY = glyph.dy)
        .addPath(
            pathData = PathParser().parsePathString(glyph.path).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()
}

/** The slot a glyph is centred in, in the units of its own 8-unit grid. */
private const val BOX = 10f

private val ICON_SIZE = 24.dp

/** @see GLYPHS */
internal val Ninety8IconOverrides: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        // Reader mode. Open Iconic ships exactly one book, and Bookmark
        // above already spends it — two identical glyphs one row apart in
        // the same menu is worse than the near miss, so the reader keeps
        // the page of text, which is what it makes of a page anyway.
        Icons.AutoMirrored.Filled.MenuBook to oi("document"),
        Icons.AutoMirrored.Outlined.MenuBook to oi("document"),
        Icons.AutoMirrored.Filled.InsertDriveFile to oi("file"),
        Icons.AutoMirrored.Filled.OpenInNew to oi("external-link"),
        Icons.AutoMirrored.Filled.KeyboardArrowLeft to oi("chevron-left"),
        Icons.AutoMirrored.Filled.KeyboardArrowRight to oi("chevron-right"),
        Icons.Filled.KeyboardArrowUp to oi("chevron-top"),
        Icons.Filled.KeyboardArrowDown to oi("chevron-bottom"),
        Icons.Filled.Add to oi("plus"),
        Icons.Filled.Apps to oi("grid-three-up"),
        Icons.Filled.Block to oi("ban"),
        Icons.Filled.Bookmark to oi("book"),
        Icons.Outlined.BookmarkBorder to oi("book"),
        Icons.Filled.Check to oi("check"),
        Icons.Filled.Close to oi("x"),
        Icons.Outlined.Close to oi("x"),
        Icons.Filled.Cloud to oi("cloud"),
        Icons.Filled.ContentCopy to oi("clipboard"),
        Icons.Outlined.ContentCopy to oi("clipboard"),
        Icons.Filled.Cookie to oi("comment-square"),
        Icons.Filled.DarkMode to oi("moon"),
        Icons.Outlined.DarkMode to oi("moon"),
        Icons.Filled.Delete to oi("trash"),
        Icons.Filled.DeleteOutline to oi("trash"),
        Icons.Filled.DeleteSweep to oi("delete"),
        Icons.Filled.DesktopWindows to oi("monitor"),
        Icons.Outlined.DesktopWindows to oi("monitor"),
        Icons.Filled.Download to oi("data-transfer-download"),
        Icons.Filled.DragHandle to oi("menu"),
        Icons.Filled.FileOpen to oi("file"),
        Icons.Filled.FolderOpen to oi("folder"),
        Icons.Filled.History to oi("clock"),
        Icons.Outlined.Image to oi("image"),
        Icons.Filled.Key to oi("key"),
        Icons.Filled.Password to oi("key"),
        Icons.Filled.Keyboard to oi("laptop"),
        Icons.Filled.Layers to oi("layers"),
        Icons.Filled.Link to oi("link-intact"),
        Icons.Filled.Lock to oi("lock-locked"),
        Icons.Outlined.MoreVert to oi("ellipses"),
        Icons.Filled.NorthWest to oi("action-undo"),
        Icons.Filled.NorthEast to oi("action-redo"),
        Icons.Filled.OpenInBrowser to oi("external-link"),
        Icons.Outlined.OpenInBrowser to oi("external-link"),
        Icons.Outlined.Palette to oi("brush"),
        Icons.Filled.PhoneAndroid to oi("phone"),
        Icons.Filled.Preview to oi("eye"),
        Icons.Filled.PrivacyTip to oi("shield"),
        Icons.Filled.Public to oi("globe"),
        Icons.Filled.Refresh to oi("reload"),
        Icons.Outlined.Refresh to oi("reload"),
        Icons.Filled.Search to oi("magnifying-glass"),
        Icons.Outlined.Search to oi("magnifying-glass"),
        Icons.Filled.Settings to oi("cog"),
        Icons.Outlined.Share to oi("share-boxed"),
        Icons.Filled.Storage to oi("hard-drive"),
        Icons.Filled.SwapHoriz to oi("transfer"),
        Icons.Filled.SwipeLeft to oi("arrow-thick-left"),
        Icons.Filled.Tab to oi("browser"),
        Icons.Outlined.Terminal to oi("terminal"),
        Icons.Filled.TouchApp to oi("target"),
        Icons.Filled.Tune to oi("dial"),
        Icons.Filled.Visibility to oi("eye"),
        Icons.Filled.VisibilityOff to oi("moon"),
    )
}
