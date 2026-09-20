package com.yuku.browser.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing as CubicBezier
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yuku.browser.ui.theme.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.CustomTabEvents
import com.yuku.browser.core.CustomTabRequest
import com.yuku.browser.core.ImageSaver
import com.yuku.browser.core.WebContextTarget
import com.yuku.browser.core.Tab
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.PageBg
import com.yuku.browser.ui.theme.Secure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import com.yuku.browser.ui.theme.specialCorner

/**
 * Taller than the 56dp the browser's own bars are, and deliberately: the pill
 * inside it is 44dp, so a 56dp row leaves it 6dp of air top and bottom — which
 * reads as crammed against the page below when the whole strip is being
 * compared against the status bar above it. 64dp gives it 10dp either side and
 * lands it visually centred in the band between the status bar and the page.
 */
private val HEADER_HEIGHT = 64.dp

/**
 * The header is Chromium's browser controls, and these are Chromium's own
 * numbers for them (`cc/input/browser_controls_offset_manager.cc`):
 * `kShowHideMaxDurationMs` 200, `kShowHideMinDurationMs` 75, and both of
 * `top_controls_show_threshold` / `top_controls_hide_threshold` at 0.5.
 *
 * The duration is interpolated between the two by how far there is left to
 * go, so a bar nudged 10% out snaps back in 87ms rather than taking the same
 * fifth of a second a full traverse does — Chromium interpolates on the
 * scroll's velocity, which is the same idea reached through the one quantity
 * a WebView's scroll callback does not hand over.
 */
private const val CONTROLS_MAX_MS = 200
private const val CONTROLS_MIN_MS = 75
private const val CONTROLS_SNAP_THRESHOLD = 0.5f

/**
 * How quiet the scrolling has to go before the snap runs — see snapHeader.
 * Chromium is TOLD when a scroll gesture ends; this is the nearest thing a
 * scroll-offset callback can offer.
 */
private const val CONTROLS_SCROLL_END_MS = 90L

/**
 * `gfx::Tween::EASE_OUT`, which is what the show/hide animation is set up
 * with. Compose has no equivalent by that shape — [Overshoot] and friends all
 * carry past their mark, and a control snapping to an edge of the screen must
 * not — so it is stated here as the cubic it is.
 */
private val ControlsEaseOut: Easing = CubicBezier(0f, 0f, 0.58f, 1f)

private fun controlsSnapMs(from: Float, to: Float): Int {
    val remaining = kotlin.math.abs(to - from).coerceIn(0f, 1f)
    return (CONTROLS_MIN_MS + (CONTROLS_MAX_MS - CONTROLS_MIN_MS) * remaining).toInt()
}

/**
 * How far the pull-to-refresh spinner travels either side of the page's top
 * edge — Material's own 64dp release point, and the same again above it for
 * the circle to be hidden in before the pull starts.
 */
private val SPINNER_TRAVEL = 64.dp

/** The address pill, at header scale — see [AddressPill]. */
private val ADDRESS_HEIGHT = 44.dp
private val ADDRESS_CORNER = 20.dp

/**
 * The turn between the bar's two states, in both directions. One number for
 * the width change and the cross-fade together: they are the same event seen
 * from two places, and letting them run on different clocks is what makes a
 * control look like it is coming apart.
 */
private const val ADDRESS_TURN_MS = 200
private const val ADDRESS_FADE_MS = 140

/**
 * Wider than Material's own default, which sizes itself to its longest label
 * and leaves the icon buttons above bunched into the middle of a narrow
 * column. This is close to the 280dp cap a dropdown is allowed.
 */
private val MENU_WIDTH = 260.dp

/**
 * The menu's own rhythm, which is Material 3's menu spec rather than anything
 * invented here: 48dp rows, a 12dp gutter, a 24dp leading icon, 8dp either
 * side of a divider (the menu container's own 8dp top and bottom come from
 * `DropdownMenu` itself).
 *
 * The point of stating them as constants is that the top row of icon buttons
 * has to obey them too — its buttons are one row tall and exactly as wide, so
 * they carry the same touch target as the rows below. Their spacing is the one
 * thing that isn't taken from the rows: they are spread evenly across the full
 * width (`SpaceEvenly`, equal gaps between them and at both ends) rather than
 * aligned to the rows' 12dp gutter, which pushes the outer two into the
 * corners and reads as three separate things instead of one row.
 */
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_GUTTER = 12.dp
private val MENU_DIVIDER_GAP = 8.dp

/**
 * Every icon in this screen, stated once and applied explicitly rather than
 * left to each component's default: the header's two buttons, the menu's top
 * row and the leading icon of every menu item are all the same 24dp glyph on
 * the same grid. (The lock beside the origin is deliberately not one of them —
 * it is punctuation inside a 12sp line, not a control.)
 *
 * They are also all from ONE family, Outlined, for the same reason. The
 * exception is the app's own filled/outlined convention for state: filled
 * means on, so a bookmarked page and desktop mode fill their glyph in.
 */
private val ICON_SIZE = 24.dp

/**
 * Optical, not geometric. Material's icons are all drawn in a 24dp box but
 * they do not fill it equally: the refresh arrow is inset noticeably further
 * than the share and copy glyphs beside it, so at a matching 24dp box it
 * reads as the smaller icon. This is the difference given back — the icons
 * are then the same SIZE to the eye, which is what "the same size" means for
 * a row of glyphs.
 */
private val ICON_SIZE_REFRESH = 27.dp

/**
 * How far down the menu hangs: the height of the button it comes out of, so
 * its top edge meets that button's bottom edge.
 */
private val MENU_ANCHOR = 48.dp

/**
 * The menu's own arrival and departure. Shorter than the app's full-screen
 * surfaces (`SURFACE_ENTER_MS`, 300) because a menu is a small thing near the
 * finger rather than something crossing the screen — but the same curves, so
 * it reads as the same app: [Overshoot] in, [Accelerate] out. The scale is
 * what makes it come out of the button rather than merely appear near it; it
 * starts a little under and settles at 1, and the overshoot is 1.2% OF THAT
 * travel, i.e. invisible as a wobble and visible only as the movement not
 * stopping dead.
 */
/**
 * How long a promoted link preview's expanded card is held over the page
 * taking its place, waiting for it to paint. Same cap, and the same reason, as
 * the browser's own handoffs.
 */
private const val PREVIEW_PAINT_MAX_MS = 1_000L

private const val MENU_ENTER_MS = 180
// Shorter than the scale it runs alongside: the panel finishes becoming
// opaque while it is still growing, so nothing about it is still arriving by
// alpha at the moment it settles.
private const val MENU_ENTER_FADE_MS = 100
private const val MENU_EXIT_MS = 140
private const val MENU_EXIT_FADE_MS = 120
private const val MENU_ENTER_SCALE = 0.90f
private const val MENU_EXIT_SCALE = 0.94f


/**
 * Rounder than Material's own menu (which is `shapes.extraSmall`, 4dp) and in
 * the same family as the rest of the app's surfaces — the sheets are 28dp,
 * the omnibox 24dp. 20dp on a 260dp menu reads as a rounded panel without
 * the corner arc eating into the top row's outer icons.
 */
private val MENU_CORNER = 20.dp

/**
 * A link from another app, drawn over that app.
 *
 * Everything below the header is the ordinary browser — the same WebView the
 * ViewModel builds for a tab, with the ad blocker, page darkening, the
 * long-press menu, find on page and the password prompts already on it. What
 * is deliberately absent is the browser's own furniture: no toolbar, no tab
 * row, no switcher, no sheets. The user is here for one page, and the way out
 * is the X (back to where they came from) or "Open in Yuku" (into the browser
 * proper, as a real tab).
 *
 * See [com.yuku.browser.CustomTabActivity] for the task placement that makes
 * closing this land back in the calling app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTabScreen(
    vm: BrowserViewModel,
    request: CustomTabRequest,
    onClose: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val currentTabId by vm.currentTabId.collectAsStateWithLifecycle()
    // The CURRENT tab rather than the first, which are the same thing for as
    // long as the overlay is the one page it is meant to be. They come apart
    // exactly once: a window the page itself opens (`window.open` — an OAuth
    // popup), which is a real second tab with the first as its opener. The
    // overlay follows it in and, when it closes itself, follows the fallback
    // back out to the page that raised it. Showing the first tab through all
    // of that would leave the flow running somewhere nobody can see.
    val tab = tabs.firstOrNull { it.id == currentTabId } ?: tabs.firstOrNull()
    val findState by vm.findState.collectAsStateWithLifecycle()
    val contextTarget by vm.contextTarget.collectAsStateWithLifecycle()
    val linkPreview by vm.linkPreview.collectAsStateWithLifecycle()
    val desktopMode by vm.desktopMode.collectAsStateWithLifecycle()
    val readerAvailable by vm.readerAvailable.collectAsStateWithLifecycle()
    val readerActive by vm.readerActive.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val searchEngine by vm.searchEngine.collectAsStateWithLifecycle()
    // Hoisted rather than kept inside the header: back has to be able to
    // cancel an edit before it means anything else.
    var editingAddress by remember { mutableStateOf(false) }
    val linkStripper by vm.linkStripperEnabled.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun copyLink(url: String) {
        clipboard.setText(AnnotatedString(if (linkStripper) UrlUtils.stripTrackingParams(url) else url))
    }

    // The image half of the long-press menu, wired exactly as the browser's
    // own is: the image is re-requested with the page it came from as
    // Referer, and below API 29 writing into Downloads still needs the
    // storage permission (see the manifest's maxSdkVersion cap).
    val imageReferer = tab?.url
    fun saveImage(target: WebContextTarget) {
        scope.launch {
            try {
                val name = ImageSaver.saveToDownloads(context, target, imageReferer)
                toast("Saved $name to Downloads")
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't save that image")
            }
        }
    }

    var pendingImageSave by remember { mutableStateOf<WebContextTarget?>(null) }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = pendingImageSave
        pendingImageSave = null
        when {
            target == null -> Unit
            granted -> saveImage(target)
            else -> toast("Storage permission is needed to save images")
        }
    }

    fun downloadImage(target: WebContextTarget) {
        val needsPermission = Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingImageSave = target
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImage(target)
        }
    }

    SystemBarIcons(ordinaryDark = false, ignorePrivacy = false)

    // The caller is told when its page starts and finishes, exactly as it
    // would be by Chrome — some clients hang their own spinner on it.
    LaunchedEffect(tab?.loading, tab?.loadFailed) {
        when {
            tab == null -> Unit
            tab.loading -> CustomTabEvents.navigationStarted(request.token)
            tab.loadFailed -> CustomTabEvents.navigationFailed(request.token)
            else -> CustomTabEvents.navigationFinished(request.token)
        }
    }

    // Back walks the page's own history first — the same thing the system
    // back gesture does in Chrome's custom tab — and only closes the overlay
    // once there is nothing left to go back to. Find and the long-press menu
    // come first, as they do everywhere else in this app.
    // The page's own text-selection toolbar, if one is up — see
    // [ActionModeOwner]: back is delivered here rather than to it, so
    // clearing the selection has to be done from this chain.
    val pageSelection = (LocalContext.current as? ActionModeOwner)?.activeActionMode

    // Everything back can mean BEFORE it means "close the overlay". While
    // any of these is true the gesture is an ordinary back press and nothing
    // is animated; the moment none of them is, back is the window closing and
    // the gesture drives it — see backProgress below.
    val hasInnerBack = editingAddress || linkPreview != null || contextTarget != null ||
        pageSelection != null || findState != null || readerActive || tab?.canGoBack == true

    BackHandler(enabled = hasInnerBack) {
        when {
            editingAddress -> {
                keyboard?.hide()
                focusManager.clearFocus()
                editingAddress = false
            }

            // A page of its own over the page — its own history first, then
            // the card. See LinkPreviewOverlay.
            linkPreview != null -> if (!vm.previewGoBack()) vm.closeLinkPreview()
            contextTarget != null -> vm.dismissContextMenu()
            // Text selected in the page: back clears it before it means
            // anything else, same as the browser proper.
            pageSelection != null -> pageSelection.finish()
            findState != null -> {
                keyboard?.hide()
                focusManager.clearFocus()
                vm.closeFind()
            }

            // A view over the page rather than a page of its own, so it has
            // no history to walk: back simply hands the article back to the
            // document. Above the page's history for the reason every other
            // overlay here is — see the browser proper's own chain.
            readerActive -> vm.toggleReaderMode()
            tab?.canGoBack == true -> vm.goBack()
            else -> onClose()
        }
    }

    // Note what is deliberately NOT here: a handler for the last level.
    //
    // With no enabled callback in the composition, the back gesture belongs
    // to the PLATFORM (the manifest's enableOnBackInvokedCallback), and the
    // platform is the only thing that can draw what this gesture is actually
    // about — the app the link came from, live, underneath. It is another
    // process's window: nothing in here can render it, so anything drawn from
    // inside this Activity can only shrink our own window over our own
    // background and hope it reads as a reveal. It doesn't. The system's
    // cross-activity animation pulls this window away and brings the caller
    // up behind it, which is what closing a custom tab IS.
    //
    // This is also why the chain above is `enabled = hasInnerBack` rather
    // than always on: an enabled handler anywhere in the composition takes
    // the whole gesture, animation included, which is what made an earlier
    // version of this screen close with no animation at all.
    //
    // Below API 34 there is no such animation and back simply finishes the
    // Activity — on custom_tab_exit, which slides it off the bottom.

    // The page ends above the keyboard rather than being covered by it: the
    // window is `adjustNothing`, so unless the view it is in actually gets
    // shorter the renderer never learns the keyboard is there and never
    // scrolls the focused field into view. `imeAnimationTarget` is the height
    // the keyboard is GOING to be, known from the first frame, so the page
    // reflows once instead of once per frame of the slide.
    val imeBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val pageBottom = with(density) { max(imeBottomPx, navBottomPx).toDp() }
    val imeUp = imeBottomPx > 0

    // Hide-on-scroll, the browser's own toolbar seen from the other end of
    // the screen — and laid out the same way, which is the thing to
    // understand about this whole screen.
    //
    // The page does not move. It is laid out at full height from under the
    // status bar to the bottom edge and the header is drawn OVER its top,
    // exactly as the toolbar is drawn over the page's bottom, so the header
    // leaving simply uncovers page that was always rendered under it.
    // Nothing is re-laid-out and nothing is translated but the header itself.
    //
    // Moving the page instead is the obvious version and it is wrong: the
    // page is ALREADY moving, by the very scroll that is driving the header,
    // so shifting the view as well carries the content twice as far as the
    // finger. See [PageTopInset], which is where the top of the document gets
    // the room the header is standing in.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerPx = with(density) { HEADER_HEIGHT.toPx() }

    // How far the header is away, 0 (fully up) to 1 (fully gone), and it is
    // the SCROLL that moves it rather than a threshold that flips it. The
    // header travels the page's own distance, pixel for pixel, so it is one
    // movement with the content under the finger instead of a decision made
    // about the finger somewhere along the way — which is what "hides on
    // scroll" means everywhere it is done well, and what a threshold can
    // never be: a threshold is a bar that ignores the first 24px and then
    // jumps 64.
    //
    // An [Animatable], because this value is driven two different ways — by
    // the finger (snapTo, one value per scroll callback) and by the settle
    // that follows it (animateTo) — and a single one of them is what lets the
    // settle start from wherever the finger left it rather than from a state
    // the scroll was quantised into.
    val headerOffset = remember { Animatable(0f) }
    // Where the header is going to BE, kept in step by hand. Every write to
    // an [Animatable] is a suspend function and so lands a dispatch later
    // than the scroll callback that asked for it — so a callback reading the
    // Animatable back reads the value from BEFORE the previous callback's
    // write, and every decision made from it is one scroll event stale. That
    // is what dropped the snap: the first upward scroll off a fully hidden
    // header read 1.0, took the "already at an end, nothing to settle" branch
    // and scheduled nothing, leaving the bar wherever the gesture stopped.
    var headerAt by remember { mutableStateOf(0f) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    // The three things that raise the header whatever the page is doing. Each
    // is a moment where the header is the thing being used rather than
    // something in the way of the page: the address bar is IN it, find is a
    // search of this page that its address belongs above, and a keyboard
    // means a field the page must not be shifted out from under. Chromium
    // calls this a forced BrowserControlsState::kShown.
    val headerForcedUp = imeUp || editingAddress || findState != null

    // Raised, and left there — the next downward scroll starts it moving
    // again from where it is, which is what Chrome does when its omnibox is
    // dismissed. It does not spring back to wherever the page happens to be
    // scrolled to.
    //
    // Deliberately NOT keyed on the tab's URL. Plenty of pages rewrite it
    // while being scrolled — a scroll-spy updating the fragment, a feed
    // pushing a new entry's address as it comes into view — and keyed on that
    // the bar would spring back on the very scroll that just sent it away.
    // A navigation the user actually made sets `loading`.
    LaunchedEffect(tab?.id, tab?.loading, headerForcedUp) {
        snapJob?.cancel()
        if (headerAt != 0f) {
            headerAt = 0f
            headerOffset.animateTo(0f, tween(CONTROLS_MAX_MS, easing = ControlsEaseOut))
        }
    }

    // Chromium's ScrollEnd: a control left part-way goes to whichever end it
    // is nearer (`top_controls_show_threshold` / `hide_threshold`, both 0.5).
    // Not the direction of travel — a bar dragged 90% of the way out and
    // nudged back a pixel is still on its way out, and rounding to the
    // nearest end is what makes the result predictable from what is on
    // screen rather than from what the finger last did.
    //
    // A scroll "ending" is a thing Chromium is told (GestureScrollEnd) and
    // this is not: the WebView reports offsets, not gestures. The scrolling
    // going quiet is the observable equivalent.
    fun snapHeader() {
        snapJob?.cancel()
        val at = headerAt
        if (at <= 0f || at >= 1f) return
        val target = if (at >= CONTROLS_SNAP_THRESHOLD) 1f else 0f
        snapJob = scope.launch {
            delay(CONTROLS_SCROLL_END_MS)
            headerAt = target
            headerOffset.animateTo(target, tween(controlsSnapMs(at, target), easing = ControlsEaseOut))
        }
    }

    fun onPageScroll(deltaY: Int, scrollY: Int) {
        if (headerForcedUp) return
        if (scrollY <= 0) {
            // Fully shown at the top of the document, always. Chromium gets
            // this for free — its compositor cannot scroll past zero, so the
            // ratio can only be 1 there — but a WebView simply stops
            // reporting, which would leave a hidden bar hidden at the top of
            // a page with no scroll left to bring it back.
            snapJob?.cancel()
            if (headerAt != 0f) {
                headerAt = 0f
                snapJob = scope.launch {
                    headerOffset.animateTo(0f, tween(CONTROLS_MAX_MS, easing = ControlsEaseOut))
                }
            }
            return
        }
        if (deltaY == 0) return
        // Chromium's ScrollByPrecise, which is the whole of the behaviour:
        // the shown ratio takes the scroll delta over the control's height,
        // one for one, in BOTH directions. Not the scroll POSITION — that
        // ties the bar to the top of the document and leaves it unreachable
        // anywhere else — and not a threshold either. Scroll down and it
        // leaves by exactly as much as the page moved; scroll up a finger's
        // width anywhere on the page and exactly that much of it comes back.
        val next = (headerAt + deltaY / headerPx).coerceIn(0f, 1f)
        if (next != headerAt) {
            headerAt = next
            scope.launch { headerOffset.snapTo(next) }
        }
        snapHeader()
    }
    val headerSlide = headerOffset.asState()
    // The strip of the page the header is standing on, given back to the page
    // — as room at the document's start, which never changes, and as an
    // offset for whatever the page has FIXED to the viewport's top, which is
    // in force only while the header is all the way up. See [PageTopInset]:
    // both moments this second value flips are moments the header is covering
    // the strip the site's own bar moves across, which is what keeps it from
    // reading as a jump.
    //
    // Read as a boolean off the animated offset, so the header moving every
    // frame does not put a script evaluation on every frame with it.
    val headerFullyUp by remember { derivedStateOf { headerSlide.value == 0f } }
    SideEffect {
        vm.setPageTopInset(headerPx.toInt(), if (headerFullyUp) headerPx.toInt() else 0)
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Box(Modifier.fillMaxSize()) {
            // Laid out at full height under the status bar and pushed down by
            // the header while it is up — see the comment above.
            Box(
                Modifier
                    .fillMaxSize()
                    // The status bar at the top and the keyboard (or the
                    // system navigation bar) at the bottom are the page's
                    // real edges. The header is not one of them — it is over
                    // the page, not above it.
                    .padding(top = statusTop, bottom = pageBottom),
            ) {
                if (tab != null) {
                    CustomTabPage(
                        vm = vm,
                        tab = tab,
                        // Where the pull-to-refresh spinner comes from. Left
                        // at its default it would be released from the top of
                        // this box, i.e. from behind the header, and travel
                        // most of its way down before appearing at all.
                        topInset = HEADER_HEIGHT,
                        barInset = if (headerFullyUp) HEADER_HEIGHT else 0.dp,
                        onScroll = { d, y -> onPageScroll(d, y) },
                    )
                }
                FadingWebErrorState(tab?.loadFailed == true, tab?.loadError)
                // While the address bar is being edited the page is not the
                // thing being driven, and a tap on it means "never mind" —
                // the bar goes back to showing where the user actually is.
                // Without this the tap reaches the WebView, which takes the
                // focus and drops the keyboard while leaving the bar sitting
                // in a half-finished edit.
                if (editingAddress) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    editingAddress = false
                                }
                            },
                    )
                }
            }
            CustomTabHeader(
                slide = { headerSlide.value },
                tab = tab,
                editingAddress = editingAddress,
                onEditingChange = { editing ->
                    if (!editing) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                    editingAddress = editing
                },
                onNavigate = { typed ->
                    keyboard?.hide()
                    focusManager.clearFocus()
                    editingAddress = false
                    vm.load(UrlUtils.toUrlOrSearch(typed, searchEngine))
                },
                desktopMode = desktopMode,
                readerAvailable = readerAvailable,
                readerActive = readerActive,
                isBookmarked = tab != null && bookmarks.any { it.url == tab.url },
                onToggleBookmark = { tab?.let(vm::toggleBookmark) },
                extraItems = request.menuItems,
                onClose = onClose,
                onReload = vm::reload,
                onToggleDesktop = vm::toggleDesktopMode,
                onToggleReader = vm::toggleReaderMode,
                onFind = vm::openFind,
                onOpenInBrowser = { tab?.url?.takeIf { it.isNotBlank() }?.let(onOpenInBrowser) },
                onExtraItem = { item -> request.send(context, item, tab?.url.orEmpty()) },
                linkStripper = linkStripper,
            )
        }

        WebContextMenu(
            target = contextTarget,
            onOpen = { url ->
                vm.dismissContextMenu()
                vm.load(url)
            },
            // An overlay has no second tab to open anything in, so a link the
            // user wants kept goes where the tabs are: the browser proper.
            onOpenInNewTab = { url ->
                vm.dismissContextMenu()
                onOpenInBrowser(url)
            },
            onCopyLink = { url ->
                vm.dismissContextMenu()
                copyLink(url)
            },
            onDownloadImage = { target ->
                vm.dismissContextMenu()
                downloadImage(target)
            },
            onCopyImage = { target ->
                vm.dismissContextMenu()
                scope.launch {
                    try {
                        ImageSaver.copyToClipboard(context, target, imageReferer)
                        if (Build.VERSION.SDK_INT < 33) toast("Image copied")
                    } catch (e: Exception) {
                        toast(e.message ?: "Couldn't copy that image")
                    }
                }
            },
            onDismiss = vm::dismissContextMenu,
        )

        LinkPreviewOverlay(
            preview = linkPreview,
            // The overlay's page fills its window (the header floats over it,
            // giving the document a top inset of its own rather than taking
            // the space), so the promotion's destination is the whole of it.
            pageRect = null,
            webViewFor = vm::previewWebView,
            onRelease = vm::releasePreviewView,
            onOpen = { url ->
                vm.load(url)
                withFrameNanos { }
                withFrameNanos { }
                vm.awaitPagePainted(vm.currentTabId.value, PREVIEW_PAINT_MAX_MS)
            },
            // An overlay has no second tab to open anything in, so a link
            // worth keeping goes where the tabs are: the browser proper. The
            // card is not held for a paint here — what it hands over to is
            // another app's window arriving, not a page of ours.
            onOpenInNewTab = { url -> onOpenInBrowser(url) },
            // Copying an address is something taken away from the page, not a
            // way of leaving it: the card stays.
            onCopyLink = ::copyLink,
            onDismiss = vm::closeLinkPreview,
        )

        val findForPage = findState?.takeIf {
            it.tabId == tab?.id && contextTarget == null && linkPreview == null
        }
        if (findForPage != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = pageBottom),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FindBar(
                    state = findForPage,
                    onQueryChange = vm::setFindQuery,
                    onNext = { vm.findNext(forward = true) },
                    onPrevious = { vm.findNext(forward = false) },
                    onClose = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        vm.closeFind()
                    },
                )
            }
        }

        // The overlay is a browser too: a link tapped in Telegram can land on
        // a download, a file input, a video that wants the screen, a script
        // that asks a question, or a certificate nobody trusts. One line,
        // because all five are held on the session rather than on the screen.
        // Last inside the Box for the same reason it is last in
        // BrowserScreen — see WebPlatform.
        WebPlatform(vm)
    }
}

/**
 * Close on the left, the address bar in the middle, everything else behind the
 * three dots.
 *
 * The bar is the browser's own omnibox in miniature — same pill, same
 * `FieldBg`, same centred lock-and-domain, and the same behaviour when tapped:
 * it becomes a field prefilled with the full URL and SELECTED, so the first
 * keystroke replaces it, with Go navigating this overlay's page. An overlay is
 * one page, but the page it is on is not necessarily the last one the user
 * wants to see, and a bar that only reports is a dead end.
 *
 * While editing, the two flanking buttons give up their space to the field
 * (the same trade the menu sheet's address row makes) — close and the menu
 * are about the page being left behind, and the only control that still
 * applies is a clear button.
 *
 * [slide] is the hide-on-scroll: 0 while the header is up, 1 once it is away.
 * Only the ROW moves — the status bar's own band stays where it is, since a
 * page running under the clock is not what hiding a toolbar is for — and it
 * moves in the DRAW phase only, read here through a lambda so a hide costs
 * one draw per frame rather than a recomposition of the header's contents.
 */
@Composable
private fun CustomTabHeader(
    slide: () -> Float,
    tab: Tab?,
    editingAddress: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    desktopMode: Boolean,
    // Whether the page has an article in it, and whether it is being shown as
    // one. The first is what the row is enabled by — see MenuSheet's own
    // reader row, which this is the overlay's version of.
    readerAvailable: Boolean,
    readerActive: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    extraItems: List<CustomTabRequest.MenuItem>,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleReader: () -> Unit,
    onFind: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onExtraItem: (CustomTabRequest.MenuItem) -> Unit,
    linkStripper: Boolean,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val hasPage = !tab?.url.isNullOrBlank()

    // A Box rather than a Column, and the ORDER of its two children is the
    // whole of how this hides. The bar slides up by exactly its own height,
    // which puts it entirely above the status band's bottom edge — so with
    // the band drawn LAST, over it, the bar disappears under a solid line of
    // BarBg and what is left is the status bar, unbroken, with the page
    // directly beneath it. Drawn the other way round (the band first) the bar
    // slides over the clock instead of under it, and a half-height address
    // pill smeared across the status bar is not a hidden toolbar.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = statusBar.calculateTopPadding())
                // Before the background, so the bar's own colour travels with
                // it and what it leaves behind is page rather than a band of
                // BarBg sitting over one. The loading line goes with it too —
                // it belongs to the bar's bottom edge, not to the screen's.
                .graphicsLayer { translationY = -HEADER_HEIGHT.toPx() * slide() }
                .background(BarBg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The two flanking buttons don't vanish and reappear —
                // they make way. Shrinking their width is what hands their
                // space to the pill, so the field's growth and their exit are
                // the same movement rather than two.
                FlankingButton(visible = !editingAddress) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Ink,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    }
                }

                AddressPill(
                    tab = tab,
                    editing = editingAddress,
                    onEditingChange = onEditingChange,
                    onNavigate = onNavigate,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )

                FlankingButton(visible = !editingAddress) { Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "More",
                            tint = Ink,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    }
                    // Material's own DropdownMenu animates on a clock of its
                    // own that can't be reached from outside it (120ms in,
                    // 75ms out), which is why this is a Popup instead: the
                    // menu arrives and leaves in the app's vocabulary, growing
                    // out of the button it hangs from and shrinking back into
                    // it. The transition state is what keeps the popup alive
                    // for the length of its own exit — dropped on `menuOpen`
                    // alone, the window is torn down on the first frame and
                    // there is nothing left to animate.
                    val menuState = remember { MutableTransitionState(false) }
                    menuState.targetState = menuOpen
                    if (menuState.currentState || menuState.targetState || !menuState.isIdle) {
                        val anchorHeight = with(LocalDensity.current) { MENU_ANCHOR.roundToPx() }
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, anchorHeight),
                            onDismissRequest = { menuOpen = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            // Fully qualified: this call sits lexically
                            // inside a Row, so the bare name resolves to
                            // RowScope's overload, which the Popup's content
                            // lambda is not.
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = menuState,
                                // Out of the top-right corner, i.e. out of the
                                // button itself, rather than out of its own
                                // middle.
                                enter = fadeIn(tween(MENU_ENTER_FADE_MS)) + scaleIn(
                                    initialScale = MENU_ENTER_SCALE,
                                    transformOrigin = TransformOrigin(1f, 0f),
                                    animationSpec = tween(MENU_ENTER_MS, easing = Overshoot),
                                ),
                                exit = fadeOut(tween(MENU_EXIT_FADE_MS, easing = Accelerate)) + scaleOut(
                                    targetScale = MENU_EXIT_SCALE,
                                    transformOrigin = TransformOrigin(1f, 0f),
                                    animationSpec = tween(MENU_EXIT_MS, easing = Accelerate),
                                ),
                            ) {
                                Surface(
                                    modifier = Modifier.width(MENU_WIDTH),
                                    shape = specialCorner(MENU_CORNER),
                                    color = BarBg,
                                    // A hairline, and NO elevation shadow.
                                    // The platform draws an elevation shadow
                                    // from the layer's outline and refuses to
                                    // draw one at all while that layer's alpha
                                    // is below 1 — so on a surface that fades,
                                    // the shadow snaps off on the first frame
                                    // of the exit and snaps back on at the end
                                    // of the entrance. Measured: the page just
                                    // outside the panel's edge goes from a
                                    // 220→237 gradient to a flat 238 in one
                                    // frame, before the panel has visibly
                                    // moved, and a corner losing its halo
                                    // reads as a corner changing shape. A
                                    // border is drawn as content, so it fades
                                    // and scales with the panel and there is
                                    // nothing left to pop.
                                    border = BorderStroke(1.dp, HairLine),
                                ) {
                                    Column(Modifier.padding(vertical = MENU_DIVIDER_GAP)) {
                        // The three actions that act on the page in place —
                        // no navigation, nothing to read — as icons across the
                        // top, the same shape the menu sheet's address row
                        // uses for the same three-ish jobs. They are the whole
                        // of their own row, so they are NOT repeated as
                        // labelled rows below.
                        Row(
                            // Evenly across the full width — equal gaps
                            // between the buttons AND at both ends — rather
                            // than pinned to the menu's corners. Three icons
                            // pushed out to the edges read as three separate
                            // things; spaced evenly they read as one row.
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MENU_ITEM_HEIGHT),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MenuAction(
                                Icons.Outlined.Refresh,
                                "Reload",
                                enabled = hasPage,
                                // See ICON_SIZE_REFRESH: this glyph is drawn
                                // small inside its own box and needs the
                                // difference back to match the two beside it.
                                size = ICON_SIZE_REFRESH,
                            ) {
                                menuOpen = false
                                onReload()
                            }
                            MenuSeparator()
                            MenuAction(Icons.Outlined.Share, "Share", enabled = hasPage) {
                                menuOpen = false
                                val url = tab?.url.orEmpty()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        if (linkStripper) UrlUtils.stripTrackingParams(url) else url,
                                    )
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                            MenuSeparator()
                            MenuAction(Icons.Outlined.ContentCopy, "Copy link", enabled = hasPage) {
                                menuOpen = false
                                val url = tab?.url.orEmpty()
                                clipboard.setText(
                                    AnnotatedString(
                                        if (linkStripper) UrlUtils.stripTrackingParams(url) else url,
                                    ),
                                )
                                // No toast: Android 13+ shows its own clipboard
                                // confirmation, same as the menu sheet's copy.
                            }
                        }
                        HorizontalDivider(
                            color = HairLine,
                            modifier = Modifier.padding(vertical = MENU_DIVIDER_GAP),
                        )
                        OverlayMenuItem(Icons.Outlined.OpenInBrowser, "Open in Yuku", enabled = hasPage) {
                            menuOpen = false
                            onOpenInBrowser()
                        }
                        OverlayMenuItem(
                            // Filled means on, outlined means off — the pair
                            // convention every stateful control in this app
                            // uses.
                            icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            label = if (isBookmarked) "Bookmarked" else "Bookmark",
                            enabled = hasPage,
                            checked = isBookmarked,
                        ) {
                            haptics.toggle(!isBookmarked)
                            menuOpen = false
                            onToggleBookmark()
                        }
                        OverlayMenuItem(
                            // Filled while it is on, same pair convention as
                            // the bookmark above and the menu sheet's tiles.
                            icon = if (desktopMode) Icons.Filled.DesktopWindows else Icons.Outlined.DesktopWindows,
                            label = "Desktop site",
                            enabled = hasPage,
                            checked = desktopMode,
                        ) {
                            // A state change, so it buzzes — the same rule the
                            // menu sheet's tiles follow.
                            haptics.toggle(!desktopMode)
                            menuOpen = false
                            onToggleDesktop()
                        }
                        // Text and pictures, with the page's furniture — and
                        // whatever a metered site put over its article — left
                        // behind. Disabled rather than hidden on a page with
                        // nothing to read: where a row lives is part of
                        // knowing the menu. See ReaderMode.
                        OverlayMenuItem(
                            icon = if (readerActive) Icons.AutoMirrored.Filled.MenuBook
                            else Icons.AutoMirrored.Outlined.MenuBook,
                            label = "Reader mode",
                            enabled = readerAvailable || readerActive,
                            checked = readerActive,
                        ) {
                            haptics.toggle(!readerActive)
                            menuOpen = false
                            onToggleReader()
                        }
                        OverlayMenuItem(Icons.Outlined.Search, "Find on page", enabled = hasPage) {
                            menuOpen = false
                            onFind()
                        }
                        // Rows the CALLING app added to this menu — "Open in
                        // <app>", "Save to…", whatever it wired up. Kept below
                        // ours and behind a divider so the overlay's own
                        // vocabulary stays in one place.
                        if (extraItems.isNotEmpty()) {
                            HorizontalDivider(
                                color = HairLine,
                                modifier = Modifier.padding(vertical = MENU_DIVIDER_GAP),
                            )
                            extraItems.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            item.label,
                                            color = InkStrong,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = MENU_GUTTER),
                                    modifier = Modifier.height(MENU_ITEM_HEIGHT),
                                    onClick = {
                                        menuOpen = false
                                        onExtraItem(item)
                                    },
                                )
                            }
                        }
                                    }
                                }
                            }
                        }
                    }
                } }
            }
            // On the header's BOTTOM edge — the page is below it — where the
            // toolbar's own line sits on its top edge for the same reason: it is
            // the seam between the chrome and the page.
            LoadingLine(
                loading = tab?.loading == true,
                progress = tab?.progress ?: 0,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        // The status bar's own band. It never moves — hiding a toolbar is
        // about giving the page the toolbar's height, not about running the
        // page under the clock — and it is last so it is the thing the bar
        // vanishes behind.
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(statusBar.calculateTopPadding())
                .align(Alignment.TopStart)
                .background(BarBg),
        )
    }
}

/**
 * The overlay's address bar: the browser's omnibox at header size.
 *
 * At rest it is the registrable domain with its lock, centred — the domain is
 * the largest thing in the bar because it is the only phishing signal the user
 * gets, which is the same reasoning the menu sheet's bar is built on. Tapped,
 * it becomes a field carrying the FULL url, pre-selected so one keystroke
 * replaces it.
 *
 * The corner is 20dp on a 44dp pill, holding the same ratio the browser's
 * 24-on-56 has, and the shadow is lighter than the sheet's 6dp: this one sits
 * directly on the bar rather than floating over a page.
 */
@Composable
private fun AddressPill(
    tab: Tab?,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        val url = tab?.url.orEmpty()
        // Pre-selected, not merely pre-filled — the keyboard comes up ready to
        // replace the whole address, same as tapping a real omnibox.
        field = TextFieldValue(url, selection = TextRange(0, url.length))
    }

    // The turn between the pill's two faces, as one number: the outgoing face
    // fades out over the first half and the incoming one fades in over the
    // second, through a blank middle.
    //
    // Deliberately NOT a Crossfade, which would be the obvious way to write
    // this: Crossfade puts its content in a Box of its own, and the field's
    // `weight(1f)` is RowScope parent data that the enclosing Row would then
    // never see — it compiles (the receiver is still in lexical scope) and
    // silently lays the field out at its own text width, leaving the clear
    // button on top of it. One face at a time, inside the Row, is what keeps
    // the layout honest.
    val turn by animateFloatAsState(
        targetValue = if (editing) 1f else 0f,
        animationSpec = tween(ADDRESS_TURN_MS),
        label = "addressTurn",
    )
    val showField = turn > 0.5f
    // 0 at the halfway point, 1 at either end: whichever face is up is fading
    // in from the middle of the turn or sitting at rest.
    val faceAlpha = (if (showField) (turn - 0.5f) else (0.5f - turn)) * 2f

    Row(
        modifier = modifier
            .height(ADDRESS_HEIGHT)
            // No shadow, unlike the browser's own omnibox: that one floats
            // over a page on a sheet, this one is set INTO a solid bar, and a
            // drop shadow inside a flat header only muddies the pill's edge.
            .clip(specialCorner(ADDRESS_CORNER))
            .background(FieldBg)
            .then(if (editing) Modifier else Modifier.clickable { onEditingChange(true) })
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showField) {
            // The field asks for focus when it ENTERS composition, which is
            // half a turn after `editing` went true — a FocusRequester that
            // is not attached to anything yet has nothing to give focus to.
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
                cursorBrush = SolidColor(InkStrong),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onNavigate(field.text) }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = faceAlpha }
                    .focusRequester(focus),
            )
            if (field.text.isNotEmpty()) {
                IconButton(
                    onClick = { field = TextFieldValue("") },
                    modifier = Modifier.size(ADDRESS_HEIGHT),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = Ink,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
            }
        } else {
            // The lock and the domain are centred INSIDE a weighted row, not
            // laid out as the pill's own children: `fadeEdges` needs a
            // settled width to fade into, and on a wrap-content Text — which
            // is exactly as wide as its own letters — it eats the first and
            // last character of every domain instead of the empty space
            // beside it.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = faceAlpha }
                    .then(fadeEdges(edge = 10.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab?.secure == true) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Secure connection",
                        tint = Secure,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    // Full host with the subdomain muted, same as the browser's
                    // own bar ([addressLabel]); with no page yet there is
                    // nothing to show but what tapping it does.
                    text = addressLabel(tab?.url.orEmpty(), tab?.host.orEmpty()),
                    color = if (tab?.host.isNullOrEmpty()) InkMuted else InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One of the header's edge buttons, giving up its width to the address bar
 * while that bar is being typed into.
 *
 * `expandHorizontally`/`shrinkHorizontally` rather than a fade or an outright
 * removal: the pill takes its space through the Row's own layout, so animating
 * the WIDTH is what makes the two read as one movement — the field growing
 * INTO the space the button is vacating. Arriving carries [Overshoot],
 * leaving takes [Accelerate], same as everything else in the app.
 */
@Composable
private fun RowScope.FlankingButton(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(ADDRESS_TURN_MS, easing = Overshoot), clip = false) +
            fadeIn(tween(ADDRESS_TURN_MS)),
        exit = shrinkHorizontally(tween(ADDRESS_TURN_MS, easing = Accelerate), clip = false) +
            fadeOut(tween(ADDRESS_FADE_MS, easing = Accelerate)),
    ) {
        content()
    }
}

/**
 * One of the top row's icon buttons: exactly one menu row tall and as wide,
 * so its icon sits on the same grid as the leading icons below it and its
 * touch target is the same size as theirs. It carries its label as the
 * content description rather than on screen.
 */
@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    size: Dp = ICON_SIZE,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(MENU_ITEM_HEIGHT)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Ink else InkMuted,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Between the top row's buttons: shorter than the row, so it reads as a hair
 * separating two controls rather than as the menu being cut into columns.
 */
@Composable
private fun MenuSeparator() {
    VerticalDivider(
        color = HairLine,
        modifier = Modifier.height(ICON_SIZE),
    )
}

@Composable
private fun OverlayMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    checked: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        enabled = enabled,
        modifier = Modifier.height(MENU_ITEM_HEIGHT),
        contentPadding = PaddingValues(horizontal = MENU_GUTTER),
        text = {
            Text(
                label,
                color = if (checked) MaterialTheme.colorScheme.primary else InkStrong,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else Ink,
                modifier = Modifier.size(ICON_SIZE),
            )
        },
        onClick = onClick,
    )
}

/**
 * The live page. The browser's own host does all this too, wrapped in the
 * shrink transform the tab switcher drives; there is no switcher here, so
 * this is the same WebView with none of that around it.
 */
@Composable
private fun CustomTabPage(
    vm: BrowserViewModel,
    tab: Tab,
    topInset: Dp,
    barInset: Dp,
    onScroll: (deltaY: Int, scrollY: Int) -> Unit,
) {
    val pullToRefresh by vm.pullToRefreshEnabled.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // The spinner is released from under the header rather than from the top
    // of the view, since the top of the view is behind the header.
    val spinnerStart = with(density) { (topInset - SPINNER_TRAVEL).roundToPx() }
    val spinnerEnd = with(density) { (topInset + SPINNER_TRAVEL).roundToPx() }
    val haptics = rememberHaptics()
    // The listener below is installed on a WebView that outlives any single
    // recomposition (it belongs to the ViewModel), so the callback is read
    // through a state holder rather than captured as whichever lambda
    // happened to be current when it was set — the same thing WebViewHost
    // does with it in the browser proper.
    val currentOnScroll = rememberUpdatedState(onScroll)
    val pageBgArgb = PageBg.toArgb()
    val spinnerArgb = InkStrong.toArgb()
    val spinnerBgArgb = BarBg.toArgb()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebViewSwipeRefreshLayout(ctx).apply {
                onPullStarting = { vm.preparePullReload() }
                setOnRefreshListener {
                    haptics.confirm()
                    vm.reload(fromPull = true)
                }
            }
        },
        update = { swipeRefresh ->
            swipeRefresh.isEnabled = pullToRefresh
            swipeRefresh.setProgressViewOffset(false, spinnerStart, spinnerEnd)
            swipeRefresh.setColorSchemeColors(spinnerArgb)
            swipeRefresh.setProgressBackgroundColorSchemeColor(spinnerBgArgb)
            if (!tab.loading) swipeRefresh.isRefreshing = false
            // `webViewFor` attaches the document-start bridge and starts a
            // fresh navigation. Seed its header room before that happens so
            // the first page layout is not corrected after its first paint.
            val topInsetPx = with(density) { topInset.roundToPx() }
            val barInsetPx = with(density) { barInset.roundToPx() }
            vm.setPageTopInset(topInsetPx, barInsetPx)
            val web = vm.webViewFor(tab)
            if ((web.background as? ColorDrawable)?.color != pageBgArgb) {
                web.setBackgroundColor(pageBgArgb)
            }
            // The overlay's page is always the whole screen, so a bar is
            // always allowed in principle — the gate is here for the other
            // half of what it does: only a scroll the finger actually drove
            // gets one. See [ScrollBarGate].
            val scrollBarGate = vm.scrollBarGate(web)
            scrollBarGate.setHostAllows(true)
            // Hide-on-scroll is driven from here. The WebView is built by the
            // ViewModel and can be shown by either screen, so the listener is
            // (re)claimed by whichever host currently has it.
            web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                scrollBarGate.onScroll(scrollY)
                currentOnScroll.value(scrollY - oldScrollY, scrollY)
            }
            val container = swipeRefresh.webContainer
            if (container.getChildAt(0) !== web) {
                container.removeAllViews()
                (web.parent as? ViewGroup)?.removeView(web)
                container.addView(
                    web,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
    )
}
