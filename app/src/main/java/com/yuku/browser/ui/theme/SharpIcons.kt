package com.yuku.browser.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.sharp.MenuBook
import androidx.compose.material.icons.automirrored.sharp.InsertDriveFile
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.sharp.OpenInNew
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
import androidx.compose.material.icons.sharp.Add
import androidx.compose.material.icons.sharp.Apps
import androidx.compose.material.icons.sharp.Block
import androidx.compose.material.icons.sharp.Bookmark
import androidx.compose.material.icons.sharp.BookmarkBorder
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.Cloud
import androidx.compose.material.icons.sharp.ContentCopy
import androidx.compose.material.icons.sharp.Cookie
import androidx.compose.material.icons.sharp.DarkMode
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.sharp.DeleteOutline
import androidx.compose.material.icons.sharp.DeleteSweep
import androidx.compose.material.icons.sharp.DesktopWindows
import androidx.compose.material.icons.sharp.Download
import androidx.compose.material.icons.sharp.DragHandle
import androidx.compose.material.icons.sharp.FileOpen
import androidx.compose.material.icons.sharp.FolderOpen
import androidx.compose.material.icons.sharp.History
import androidx.compose.material.icons.sharp.Image
import androidx.compose.material.icons.sharp.Key
import androidx.compose.material.icons.sharp.Keyboard
import androidx.compose.material.icons.sharp.KeyboardArrowDown
import androidx.compose.material.icons.sharp.KeyboardArrowUp
import androidx.compose.material.icons.sharp.Layers
import androidx.compose.material.icons.sharp.Link
import androidx.compose.material.icons.sharp.Lock
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.NorthEast
import androidx.compose.material.icons.sharp.NorthWest
import androidx.compose.material.icons.sharp.OpenInBrowser
import androidx.compose.material.icons.sharp.Palette
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material.icons.sharp.PhoneAndroid
import androidx.compose.material.icons.sharp.Preview
import androidx.compose.material.icons.sharp.PrivacyTip
import androidx.compose.material.icons.sharp.Public
import androidx.compose.material.icons.sharp.Refresh
import androidx.compose.material.icons.sharp.Search
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.sharp.Share
import androidx.compose.material.icons.sharp.Storage
import androidx.compose.material.icons.sharp.SwapHoriz
import androidx.compose.material.icons.sharp.SwipeLeft
import androidx.compose.material.icons.sharp.Tab
import androidx.compose.material.icons.sharp.Terminal
import androidx.compose.material.icons.sharp.TouchApp
import androidx.compose.material.icons.sharp.Tune
import androidx.compose.material.icons.sharp.Visibility
import androidx.compose.material.icons.sharp.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material's own icons in their SHARP cut, keyed by the rounded glyph the
 * app asks for — the TUI theme's icon set.
 *
 * It is the cheapest possible answer to "this theme needs its own
 * iconography" and, for a theme whose grammar is that every radius is zero
 * ([specialCorner]), the right one: Sharp is that grammar applied to the
 * icons by the people who drew them. Same silhouette, same weight, same
 * optical size, corners squared off. Nothing is vendored, nothing drifts out
 * of step with Material as it changes, and a row that mixes a mapped icon
 * with an unmapped one still reads as one set — which is what makes a
 * partial table safe.
 *
 * A pixel set was tried here first — Pixelarticons, borrowed from the 98
 * theme — and dropped: a bitmap glyph belongs to a machine with visible
 * pixels, where a TUI is a face on a grid.
 *
 * The 98 theme shared this table briefly, between losing its own pixel set
 * and gaining [Ninety8IconOverrides]. It reads squared but modern, which is
 * right for a terminal and one era short for a 1998 desktop — see that table
 * for what a period toolbar icon is actually made of.
 *
 * The TUI reads its character half ([TuiIconGlyphs]) BEFORE this table, so
 * the entries here that a keyboard has a key for are never reached by it;
 * they are kept because the table is the app's general "square Material"
 * answer, not the TUI's private one.
 */
internal val SharpIconOverrides: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        Icons.AutoMirrored.Filled.MenuBook to Icons.AutoMirrored.Sharp.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook to Icons.AutoMirrored.Sharp.MenuBook,
        Icons.AutoMirrored.Filled.InsertDriveFile to Icons.AutoMirrored.Sharp.InsertDriveFile,
        Icons.AutoMirrored.Filled.OpenInNew to Icons.AutoMirrored.Sharp.OpenInNew,
        Icons.Filled.Apps to Icons.Sharp.Apps,
        Icons.Filled.Bookmark to Icons.Sharp.Bookmark,
        Icons.Filled.Check to Icons.Sharp.Check,
        Icons.Filled.Cloud to Icons.Sharp.Cloud,
        Icons.Filled.ContentCopy to Icons.Sharp.ContentCopy,
        Icons.Outlined.ContentCopy to Icons.Sharp.ContentCopy,
        Icons.Filled.Cookie to Icons.Sharp.Cookie,
        Icons.Filled.DarkMode to Icons.Sharp.DarkMode,
        Icons.Outlined.DarkMode to Icons.Sharp.DarkMode,
        Icons.Filled.Delete to Icons.Sharp.Delete,
        Icons.Filled.DeleteOutline to Icons.Sharp.DeleteOutline,
        Icons.Filled.DeleteSweep to Icons.Sharp.DeleteSweep,
        Icons.Filled.DesktopWindows to Icons.Sharp.DesktopWindows,
        Icons.Outlined.DesktopWindows to Icons.Sharp.DesktopWindows,
        Icons.Filled.Download to Icons.Sharp.Download,
        Icons.Filled.FileOpen to Icons.Sharp.FileOpen,
        Icons.Filled.FolderOpen to Icons.Sharp.FolderOpen,
        Icons.Filled.History to Icons.Sharp.History,
        Icons.Outlined.Image to Icons.Sharp.Image,
        Icons.Filled.Key to Icons.Sharp.Key,
        Icons.Filled.Keyboard to Icons.Sharp.Keyboard,
        Icons.Filled.Layers to Icons.Sharp.Layers,
        Icons.Filled.Link to Icons.Sharp.Link,
        Icons.Filled.Lock to Icons.Sharp.Lock,
        Icons.Outlined.MoreVert to Icons.Sharp.MoreVert,
        Icons.Filled.OpenInBrowser to Icons.Sharp.OpenInBrowser,
        Icons.Outlined.OpenInBrowser to Icons.Sharp.OpenInBrowser,
        Icons.Outlined.Palette to Icons.Sharp.Palette,
        Icons.Filled.Password to Icons.Sharp.Password,
        Icons.Filled.PhoneAndroid to Icons.Sharp.PhoneAndroid,
        Icons.Filled.Preview to Icons.Sharp.Preview,
        Icons.Filled.PrivacyTip to Icons.Sharp.PrivacyTip,
        Icons.Filled.Public to Icons.Sharp.Public,
        Icons.Filled.Refresh to Icons.Sharp.Refresh,
        Icons.Outlined.Refresh to Icons.Sharp.Refresh,
        Icons.Filled.Settings to Icons.Sharp.Settings,
        Icons.Outlined.Share to Icons.Sharp.Share,
        Icons.Filled.Block to Icons.Sharp.Block,
        Icons.Filled.Storage to Icons.Sharp.Storage,
        Icons.Filled.SwapHoriz to Icons.Sharp.SwapHoriz,
        Icons.Filled.SwipeLeft to Icons.Sharp.SwipeLeft,
        Icons.Filled.Tab to Icons.Sharp.Tab,
        Icons.Outlined.Terminal to Icons.Sharp.Terminal,
        Icons.Filled.TouchApp to Icons.Sharp.TouchApp,
        Icons.Filled.Tune to Icons.Sharp.Tune,
        Icons.Filled.Visibility to Icons.Sharp.Visibility,
        Icons.Filled.VisibilityOff to Icons.Sharp.VisibilityOff,
        Icons.Outlined.BookmarkBorder to Icons.Sharp.BookmarkBorder,
        Icons.AutoMirrored.Filled.KeyboardArrowLeft to Icons.AutoMirrored.Sharp.KeyboardArrowLeft,
        Icons.AutoMirrored.Filled.KeyboardArrowRight to Icons.AutoMirrored.Sharp.KeyboardArrowRight,
        Icons.Filled.KeyboardArrowUp to Icons.Sharp.KeyboardArrowUp,
        Icons.Filled.KeyboardArrowDown to Icons.Sharp.KeyboardArrowDown,
        Icons.Filled.Close to Icons.Sharp.Close,
        Icons.Outlined.Close to Icons.Sharp.Close,
        Icons.Filled.Add to Icons.Sharp.Add,
        Icons.Filled.Search to Icons.Sharp.Search,
        Icons.Outlined.Search to Icons.Sharp.Search,
        Icons.Filled.NorthWest to Icons.Sharp.NorthWest,
        Icons.Filled.NorthEast to Icons.Sharp.NorthEast,
        Icons.Filled.DragHandle to Icons.Sharp.DragHandle,
    )
}
