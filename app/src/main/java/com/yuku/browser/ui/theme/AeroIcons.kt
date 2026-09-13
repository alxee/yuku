package com.yuku.browser.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material's own icons in their ROUNDED cut, keyed by the glyph the app asks
 * for — the Aero theme's icon set.
 *
 * The same answer the TUI theme gets from [SharpIconOverrides], pointed at
 * the other end of Material's own range, and right for the same reason. This
 * theme's grammar is that every corner is BLOWN rather than cut
 * ([specialCorner] gains a radius where the TUI takes it to zero), and
 * Rounded is that grammar applied to the icons by the people who drew them:
 * same silhouette, same weight, same optical size, every terminal and corner
 * radiused. A bubble interface under square-cut icons is two languages in
 * one row.
 *
 * Nothing is vendored and nothing drifts out of step with Material as it
 * changes — which is what makes a PARTIAL table safe here, where the Nothing
 * and 98 sets have to cover whole surfaces or not appear on them: an
 * unmapped glyph falls through to the same family it came from, one cut
 * away, rather than to a different draughtsman's idea of a bin.
 *
 * No hand-drawn set was attempted, and that is deliberate. The icon of this
 * era was a full-colour glossy OBJECT — a 128px aqua orb with a reflection
 * and a drop shadow, sitting in perspective — and there is no monoline
 * vector approximation of one. Half a set of those beside Material's
 * silhouettes would be worse than either; the gloss in this theme is on the
 * chrome, where it can be drawn honestly (see [Modifier.aeroGlass]), and the
 * glyphs stay a clean rounded set that the chrome is drawn around.
 *
 * Unlike the Nothing and 98 sets, this one keeps `BarButton`'s per-call-site
 * `iconSize` numbers: those correct for how much of its 24-unit box each
 * MATERIAL glyph fills, and a Rounded glyph fills exactly the box its Filled
 * twin does. They are the same drawings.
 */
internal val AeroIconOverrides: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        Icons.AutoMirrored.Filled.MenuBook to Icons.AutoMirrored.Rounded.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook to Icons.AutoMirrored.Rounded.MenuBook,
        Icons.AutoMirrored.Filled.InsertDriveFile to Icons.AutoMirrored.Rounded.InsertDriveFile,
        Icons.AutoMirrored.Filled.OpenInNew to Icons.AutoMirrored.Rounded.OpenInNew,
        Icons.Filled.Apps to Icons.Rounded.Apps,
        Icons.Filled.Bookmark to Icons.Rounded.Bookmark,
        Icons.Filled.Check to Icons.Rounded.Check,
        Icons.Filled.Cloud to Icons.Rounded.Cloud,
        Icons.Filled.ContentCopy to Icons.Rounded.ContentCopy,
        Icons.Outlined.ContentCopy to Icons.Rounded.ContentCopy,
        Icons.Filled.Cookie to Icons.Rounded.Cookie,
        Icons.Filled.DarkMode to Icons.Rounded.DarkMode,
        Icons.Outlined.DarkMode to Icons.Rounded.DarkMode,
        Icons.Filled.Delete to Icons.Rounded.Delete,
        Icons.Filled.DeleteOutline to Icons.Rounded.DeleteOutline,
        Icons.Filled.DeleteSweep to Icons.Rounded.DeleteSweep,
        Icons.Filled.DesktopWindows to Icons.Rounded.DesktopWindows,
        Icons.Outlined.DesktopWindows to Icons.Rounded.DesktopWindows,
        Icons.Filled.Download to Icons.Rounded.Download,
        Icons.Filled.FileOpen to Icons.Rounded.FileOpen,
        Icons.Filled.FolderOpen to Icons.Rounded.FolderOpen,
        Icons.Filled.History to Icons.Rounded.History,
        Icons.Outlined.Image to Icons.Rounded.Image,
        Icons.Filled.Key to Icons.Rounded.Key,
        Icons.Filled.Keyboard to Icons.Rounded.Keyboard,
        Icons.Filled.Layers to Icons.Rounded.Layers,
        Icons.Filled.Link to Icons.Rounded.Link,
        Icons.Filled.Lock to Icons.Rounded.Lock,
        Icons.Outlined.MoreVert to Icons.Rounded.MoreVert,
        Icons.Filled.OpenInBrowser to Icons.Rounded.OpenInBrowser,
        Icons.Outlined.OpenInBrowser to Icons.Rounded.OpenInBrowser,
        Icons.Outlined.Palette to Icons.Rounded.Palette,
        Icons.Filled.Password to Icons.Rounded.Password,
        Icons.Filled.PhoneAndroid to Icons.Rounded.PhoneAndroid,
        Icons.Filled.Preview to Icons.Rounded.Preview,
        Icons.Filled.PrivacyTip to Icons.Rounded.PrivacyTip,
        Icons.Filled.Public to Icons.Rounded.Public,
        Icons.Filled.Refresh to Icons.Rounded.Refresh,
        Icons.Outlined.Refresh to Icons.Rounded.Refresh,
        Icons.Filled.Settings to Icons.Rounded.Settings,
        Icons.Outlined.Share to Icons.Rounded.Share,
        Icons.Filled.Block to Icons.Rounded.Block,
        Icons.Filled.Storage to Icons.Rounded.Storage,
        Icons.Filled.SwapHoriz to Icons.Rounded.SwapHoriz,
        Icons.Filled.SwipeLeft to Icons.Rounded.SwipeLeft,
        Icons.Filled.Tab to Icons.Rounded.Tab,
        Icons.Outlined.Terminal to Icons.Rounded.Terminal,
        Icons.Filled.TouchApp to Icons.Rounded.TouchApp,
        Icons.Filled.Tune to Icons.Rounded.Tune,
        Icons.Filled.Visibility to Icons.Rounded.Visibility,
        Icons.Filled.VisibilityOff to Icons.Rounded.VisibilityOff,
        Icons.Outlined.BookmarkBorder to Icons.Rounded.BookmarkBorder,
        Icons.AutoMirrored.Filled.KeyboardArrowLeft to Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
        Icons.AutoMirrored.Filled.KeyboardArrowRight to Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        Icons.Filled.KeyboardArrowUp to Icons.Rounded.KeyboardArrowUp,
        Icons.Filled.KeyboardArrowDown to Icons.Rounded.KeyboardArrowDown,
        Icons.Filled.Close to Icons.Rounded.Close,
        Icons.Outlined.Close to Icons.Rounded.Close,
        Icons.Filled.Add to Icons.Rounded.Add,
        Icons.Filled.Search to Icons.Rounded.Search,
        Icons.Outlined.Search to Icons.Rounded.Search,
        Icons.Filled.NorthWest to Icons.Rounded.NorthWest,
        Icons.Filled.NorthEast to Icons.Rounded.NorthEast,
        Icons.Filled.DragHandle to Icons.Rounded.DragHandle,
    )
}
