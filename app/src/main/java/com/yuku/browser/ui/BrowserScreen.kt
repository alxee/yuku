package com.yuku.browser.ui

import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.drawSoftRule
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.flow.distinctUntilChanged
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.key
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.NewTabPlacement
import com.yuku.browser.core.PullGate
import com.yuku.browser.core.ImageSaver
import com.yuku.browser.core.WebContextTarget
import com.yuku.browser.core.SiteSettings
import com.yuku.browser.core.Tab
import com.yuku.browser.core.TabManagerMode
import com.yuku.browser.core.ThemeMode
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.BrowserTheme
import com.yuku.browser.ui.theme.LocalThemeCrossfade
import com.yuku.browser.ui.theme.LocalChromeDarkness
import com.yuku.browser.ui.theme.LocalPrivacyProgress
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.PageBg
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yuku.browser.ui.theme.SpecialCornerScale
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroPageGlass
import com.yuku.browser.ui.theme.frostedSheetGlass
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.BEVEL_BAND
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.bevel98Colors
import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiCrtIf
import com.yuku.browser.ui.theme.tuiSurfaceTextureIf
import com.yuku.browser.ui.theme.specialCorner

private enum class Sheet {
    NewTab,
    Menu,

    /**
     * How the reader draws an article, reached from the menu's Reader mode
     * row. A sheet of its own rather than a full-screen destination because
     * every control on it changes the article live, and the page has to stay
     * visible above it — see ReaderSettingsSheet.
     */
    ReaderSettings,

    /**
     * What this one site gets that the rest of the web does not, reached from
     * the menu's Site settings row. A sheet for the reader's reason: every
     * control on it changes the page live and the page has to stay visible
     * above it — see SiteSettingsSheet.
     */
    SiteSettings,
}

private val SheetDragHandleHeight = 24.dp
private val SheetHandleThickness = 4.dp

/**
 * How far the omnibox's drop shadow reaches above the bar's own top edge.
 *
 * MenuSheet's bar is the first thing inside a `verticalScroll`, which clips to
 * its bounds, so it has to pad this much in or the shadow's top band — and
 * with it the edge that band wraps — is cut off. The strip above it gives the
 * same amount back, so the bar still starts where the + sheet's does: the two
 * are one omnibox in two states, and they must not sit at different heights.
 */
internal val SheetBarShadowRoom = 6.dp

/** Corner radius of the sheet — see the Surface's shape for why it's uniform. */
/** Where a sheet settles when it opens, as a fraction of the screen. */
private const val SHEET_REST_FRACTION = 2f / 3f

/** The + sheet and the menu crossfade over this when one turns into the other. */
private const val SHEET_FACE_FADE_MS = 150

private val SHEET_CORNER = 28.dp

/**
 * How long the outgoing page is held over the incoming one when a row of the
 * tab list is tapped. The same 240ms the grid's zoom out of a card takes, so
 * the two ways of choosing a tab last the same length of time.
 */
private const val LIST_SWITCH_FADE_MS = 240

// How far past its own height an upward swipe stretches a sheet that has
// nowhere left to grow. These are the platform's own numbers, out of
// android.widget.EdgeEffect — the stretch overscroll every list in Settings
// and every page in Chrome gives — rather than a feel invented here, because
// the whole point of the gesture is that it is the one the user already
// knows. Note how SMALL it is: the exponential term saturates at 1.6% of the
// container almost immediately and the linear one adds 1.6% per further
// container-height of pull, so even a hard swipe is a couple of percent. The
// resistance IS this curve; there is no separate damping on the input.
private const val STRETCH_LINEAR_INTENSITY = 0.016f
private const val STRETCH_EXP_INTENSITY = 0.016f
private const val STRETCH_EXP_RANGE = 0.33f

/**
 * Ceiling on the accumulated pull, in container-heights. The platform lets
 * this grow without bound (the curve above is what keeps the result sane);
 * capping it here keeps a long swipe from banking pull that then has to be
 * spent back before the surface visibly moves at all on the way out.
 */
private const val STRETCH_MAX_PULL = 1f

/**
 * The downward speed, in pixels per second, at which a swipe on the + / menu
 * sheet dismisses it wherever it got to — the same rule, and the same number,
 * as the tab list's own (see TabListSwitcher's FLING_DISMISS_VELOCITY). A
 * flick is a short, fast movement: judged on the collapse THRESHOLD alone, a
 * sheet unmistakably thrown away had travelled well under the 96dp it takes
 * and sprang straight back, so the gesture worked exactly as often as it
 * happened to be slow enough to be a drag.
 */
private const val SHEET_FLING_DISMISS_VELOCITY = 500f

/** The damped stretch, as a fraction of the sheet's height, for a given pull. */
private fun stretchFraction(pull: Float): Float {
    if (pull <= 0f) return 0f
    val linear = STRETCH_LINEAR_INTENSITY * pull
    val exponential = STRETCH_EXP_INTENSITY *
        (1f - exp(-pull * (E / STRETCH_EXP_RANGE).toFloat()))
    return linear + exponential
}

/**
 * Measure and lay this subtree out as normal, but only place it when [active].
 *
 * Unlike hiding with `alpha`, an unplaced subtree costs no draw pass and takes
 * no touches; unlike removing it from composition, it keeps every child — and
 * every LazyColumn row inside it — composed and measured, ready to be shown on
 * a single frame's notice.
 */
private fun Modifier.placedWhen(active: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (active) placeable.place(0, 0) }
}

/** Which switcher presentation is currently shown when open. */
private enum class TabViewMode { Grid, List }

/** How far an upward drag from the tabs button must travel to reach full progress. */
private val DRAG_TRAVEL = 280.dp

/** A fast-enough upward flick opens the switcher even from a short, quick swipe. */
private const val FLING_VELOCITY_THRESHOLD = -800f

// The tabs-button drag is one continuous [0, MAX_PROGRESS] range. progress
// drives two independent things: SHRINK_PROGRESS (below) for scale, and the
// open/close/cancel outcome thresholds. It does NOT drive position — see
// offsetX/offsetY in BrowserScreen.
private const val MAX_PROGRESS = 4f

// Not private: TabSwitcher's own row-zoom needs to finish shrinking at
// exactly the same progress this does, or the grid and the live content
// driving this gesture visibly desync. Tap and swipe are otherwise separate
// paths (see openSwitcher/closeSwitcher vs onTabsDrag*), but they share this
// same constant so a tap-triggered settle lands at the same visual size a
// swipe-triggered one would.
//
// This is the point in `progress` where the shrink finishes: the content
// scales from fullscreen down to tab-viewer size across [0, this], and stays
// that size for the rest of the range. Position is entirely separate —
// during a live drag it just follows the finger 1:1 from the first pixel
// (see onTabsDrag), and the switcher slot is only ever reached by an
// animation started at release (see onTabsDragEnd).
//
// This is also, now, what "fully open" / "at rest" MEANS for `progress` —
// NOT 1f. Settle animations (animateIntoSwitcher, and the swipe-down family)
// all target this value, not 1f: scale is a clamped function of `progress`
// that saturates here, so if the settle animated `progress` past this point,
// scale would visibly finish (and stop moving) partway through the tween
// while position kept animating for the remainder — two different things
// visibly finishing at different times instead of one motion completing all
// at once. Targeting this value directly instead means scale and position
// share the exact same start and finish.
const val MIDPOINT_PROGRESS = 0.5f

/**
 * How far past each end of the shrink the SIZE is allowed to carry, in the
 * same units [shrinkOf] returns — i.e. a fraction of the whole fullscreen-to-
 * card travel. This is what gives expanding and contracting a tab the same
 * inertia everything else in the app arrives with: the page grows a hair past
 * fullscreen and settles back onto it, and shrinks a hair past its card slot
 * and settles back out to it.
 *
 * It is a BAND on the mapping rather than a second animated value, so the live
 * page, its static stand-in and the grid's own row-zoom cannot possibly
 * disagree about it — they all read the one function. The cost of that is at
 * the top end: dragging past "fully open" now spends its first ~1.2% of travel
 * finishing this band instead of holding, which is about three pixels of
 * continued shrink before the card locks to its size. Set to match
 * Motion.kt's own overshoot, since it exists to let that curve through.
 */
private const val SHRINK_INERTIA = 0.012f

/**
 * `progress` as the shrink actually reads it: 0 at fullscreen, 1 at the card,
 * and a narrow band past each end for the settle to carry into (see
 * [SHRINK_INERTIA]). Beyond that band it saturates — past
 * [MIDPOINT_PROGRESS] the gesture is translating an already-shrunk page
 * around, not shrinking it further.
 *
 * Shared by [WebViewHost], [AnimatedThumbnailHost], TabSwitcher's row zoom and
 * TabListSwitcher, which is the point: any one of them mapping it differently
 * shows up as two elements of the same motion at different sizes.
 */
fun shrinkOf(progress: Float): Float =
    (progress / MIDPOINT_PROGRESS).coerceIn(-SHRINK_INERTIA, 1f + SHRINK_INERTIA)

// 0.3 progress units (≈84dp) past "fully open" (MIDPOINT_PROGRESS, not 1f).
private const val CLOSE_COMMIT_PROGRESS = MIDPOINT_PROGRESS + 0.3f
private const val REST_EPSILON = 0.0005f

/** Scale to assume before the switcher has reported the real target size. */
private const val FALLBACK_SHRINK_SCALE = 0.6f

// A new tab opened over the switcher enters exactly the way the full-screen
// destinations (Settings, Bookmarks, History) do — deliberately the same
// numbers AND the same curve, straight out of Motion.kt, so "a new surface
// arrives over what you were looking at" reads as one motion everywhere in
// the app: it rises a little past where it lands and settles back onto it.
// Slide and fade run on their own durations, same as AnimatedVisibility runs
// its two specs independently — and only the slide takes the overshoot, since
// alpha has nowhere past 1 to overshoot TO.
private const val NEW_TAB_ENTER_RISE_FRACTION = 1f / 3f
private const val NEW_TAB_ENTER_SLIDE_MS = SURFACE_ENTER_MS
private const val NEW_TAB_ENTER_FADE_MS = SURFACE_ENTER_FADE_MS

/** How long the static thumbnail outlives the live page taking over — see `handoffCover`. */
private const val HANDOFF_COVER_FRAMES = 4

// The page cover (see BrowserViewModel.coveredTabIds): a tab's last preview,
// held over its WebView while that WebView builds the page again from
// nothing, so opening a tab looks like a resume instead of a blank screen
// filling in. A relaunch is the obvious case; a tab whose view was evicted,
// or parked since the last launch, is the same thing on a smaller scale.
//
// The cap is what keeps a page that never paints — no network, a URL that
// hangs, a server sitting on the connection — from leaving a still image of a
// page the user can't interact with. Generous, because everything under it is
// already live and the cover is over the very page coming up behind it: too
// early is a visible flash of white, too late is invisible right up until it
// isn't.
private const val PAGE_COVER_MAX_MS = 6_000L
/** Long enough to read as the page settling in, short enough not to feel held. */
private const val PAGE_COVER_FADE_MS = 220


// Switching tabs without the switcher: the double-tap flip (see
// `quickSwitchTabs`) and the sideways swipe off the toolbar's end buttons
// (see `beginSwipeSwitch`), both drawn by QuickSwitchOverlay.
private const val QUICK_SWITCH_MS = 300
/**
 * How far the pair pulls back at the midpoint of the slide. Small on purpose:
 * this is the whole of the depth cue now, and the two pages are full-screen
 * images — 8% is a page visibly stepping back from the glass, where the 18%
 * this replaced was a card being thrown into a stack.
 */
private const val QUICK_SWITCH_ZOOM = 0.08f
/**
 * The strip of background between the two pages, which is what says there are
 * two of them rather than one image sliding. Held at exactly this width for
 * the whole slide — see the separation the overlay positions them by.
 */
private val QUICK_SWITCH_GAP = 8.dp
/**
 * Which way the deck turns for the DOUBLE-TAP flip — fixed, not per flip. The
 * tab a flip lands on is moved to the end of the row, so "the other tab" is
 * always the one to the left of the current one: the incoming card always
 * enters from the left and the outgoing one always leaves to the right. Also
 * the resting value of `quickSwitchDirection`, which a sideways swipe sets
 * per gesture instead (that one genuinely goes both ways).
 */
private const val QUICK_SWITCH_DIRECTION = -1f
/**
 * The longest the incoming tab's still image is held over its WebView while
 * that WebView re-attaches and rasters (see `awaitPagePainted`). Generous,
 * because everything under it is the page the user asked for and the cover is
 * a picture of that same page — but not unbounded: a cover that outlives its
 * paint is a frozen app, same reasoning as PAGE_COVER_MAX_MS.
 */
private const val QUICK_SWITCH_PAINT_MAX_MS = 1_000L
/**
 * How much of the screen a sideways swipe off an end button has to pull the
 * incoming tab across before releasing commits to it. Deliberately under a
 * third: the deck is already showing the tab being arrived at by then, and a
 * gesture that shows you where it is going and then puts it back reads as
 * having failed.
 */
private const val SWIPE_SWITCH_COMMIT = 0.3f
/** …or a flick, in px/s, whatever distance it covered. */
private const val SWIPE_SWITCH_FLING = 700f
private val QUICK_SWITCH_CORNER = 18.dp

/**
 * How long a card flicked off the top from the tabs button takes to accelerate
 * off the edge (see onTabsDragEnd's close branch). The row closes up over the
 * gap it leaves underneath it, and the gesture comes to rest in the switcher.
 *
 * Not private: the grid times its own gap-closing against this flight, so that
 * the row waits until the card is clear of it (see recenterForClose).
 */
internal const val CLOSE_FLY_AWAY_MS = 300

/**
 * How long the flick-to-close waits for the grid to finish closing the row up
 * before it actually removes the tab. Long enough to cover the recentring
 * scroll (which starts partway through the flight and runs on past it), short
 * enough that a grid which never answers — no row on screen, or a recentring
 * cancelled by the user grabbing the row — is not a tab that fails to close.
 */
private const val CLOSE_RECENTRE_TIMEOUT_MS = 600L

// Raw per-event scroll deltas are noisy (a single WebView scroll callback can
// fire for a 1px correction), so the toolbar only reacts once a run of
// same-direction scrolling adds up to this many px — otherwise tiny jitter
// at rest would flicker it.
private const val SCROLL_HIDE_THRESHOLD_PX = 24f

/** The reader's progress indicator: thin enough to be a rule, thick enough to read. */
private val READER_PROGRESS_HEIGHT = 3.dp

/**
 * How far through the open article the reader is, drawn on the app's own
 * chrome: a rule resting on the toolbar's top edge — the loading line's edge —
 * that rides the bar down as it hides and comes to rest on the system
 * navigation bar's line, on a floor of [BarBg] that fades in as the bar
 * leaves. Without that floor the rule would be a mark hanging over moving
 * article with nothing under it.
 *
 * Everything that moves is read in the DRAW phase: the fraction through a
 * [State] the composition never touches and the toolbar's slide through a
 * lambda. A scrolling article therefore invalidates one layer's draw and
 * recomposes nothing — the whole reason this is a leaf of its own rather than
 * a few lines in [BrowserScreen], which reads the flow's value nowhere.
 */
@Composable
private fun ReaderProgressChrome(
    vm: BrowserViewModel,
    slide: () -> Float,
    linePx: Int,
    fillPx: Int,
) {
    val fraction = vm.readerProgress.collectAsStateWithLifecycle()
    val floor = BarBg
    val ink = Ink
    val track = InkMuted
    val height = with(LocalDensity.current) { READER_PROGRESS_HEIGHT.toPx() }
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val t = slide().coerceIn(0f, 1f)
                // Where the rule's TOP edge sits, measured up from the screen's
                // bottom: the toolbar's top edge while the bar is up, and with
                // the bar gone, directly on top of the system navigation bar —
                // its bottom edge on that strip's top, the gesture pill in the
                // strip below it. Its own thickness is in the measure because
                // `rest` is the TOP edge and what has to land on the navigation
                // bar is the BOTTOM one.
                val collapsed = fillPx + height
                val rest = linePx + (collapsed - linePx) * t
                val top = size.height - rest
                // The rule hangs BELOW that line rather than standing on it,
                // which is where the loading line sits on the toolbar (its
                // first few pixels, not the page's last few) — and it is what
                // keeps both this and the ground under it inside the strip
                // every preview already stops short of (see pageOverflow), so
                // a tab's picture is the page and nothing of ours.
                //
                // The ground goes all the way to the screen's edge: with the
                // bar gone the rule would otherwise be a mark hanging over
                // moving article, which is the whole reason it is here. It is
                // the bar's own colour, so the two are one surface as the bar
                // leaves.
                if (t > 0f) {
                    drawRect(
                        color = floor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, rest),
                        alpha = t,
                    )
                }
                // No article position, nothing to say — an article too short
                // to scroll reports none.
                val f = fraction.value ?: return@drawBehind
                drawRect(track.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, height))
                drawRect(ink.copy(alpha = 0.72f), Offset(0f, top), Size(size.width * f, height))
            },
    )
}

/** How long the toolbar takes to slide out of / back into its slot. */
private const val TOOLBAR_SLIDE_MS = 220

// The bar making way for a sheet, and coming back after one. Leaving is the
// quicker of the two (it has to be gone before the sheet is over it); arriving
// decelerates onto the edge with no overshoot, since past 0 it would lift off
// the bottom of the screen.
private const val SHEET_BAR_OUT_MS = 140
private const val SHEET_BAR_IN_MS = 200

// Kept under the page while the toolbar is hidden and the page has a
// bottom-anchored bar of its own (see PageBottomBar) — a floor for the system
// navigation bar's inset, which is 0 on the handful of devices/displays that
// report none, and a bar flush against the very bottom edge of the glass
// reads as clipped even where there's nothing there to collide with.
private val MIN_PAGE_BOTTOM_BUFFER = 8.dp

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun BrowserScreen(vm: BrowserViewModel) {
    val allTabs by vm.tabs.collectAsStateWithLifecycle()
    val privateMode by vm.privateMode.collectAsStateWithLifecycle()
    val keepPrivateTabs by vm.keepPrivateTabs.collectAsStateWithLifecycle()
    // One space at a time: the private tabs and the ordinary ones are never
    // both on screen, so everything below this line — the switcher, the empty
    // state, the quick-switch flip — sees only the space that is open.
    // Filtered here rather than in the ViewModel's flow so it lands on the
    // very first composition, synchronously: a frame of "no tabs" would open
    // the new-tab sheet over a session that is actually there.
    val tabs = remember(allTabs, privateMode) { allTabs.filter { it.isPrivate == privateMode } }
    // …and the swap between the two is INSTANT, while the ground they sit on
    // takes PRIVATE_FADE_MS to turn (see BrowserTheme). So a page waiting in
    // the space being opened simply appeared, fully drawn, over a theme that
    // was still half way to violet. It is faded in on the turn's own curve
    // instead, so the page and the ground it rests on arrive together.
    //
    // Expressed as distance TRAVELLED rather than as the turn's own value, so
    // it reads 0 at whichever end the toggle started from — in both
    // directions, and including the frame of the swap itself, which is a
    // frame before the animation has been dispatched at all (a LaunchedEffect
    // runs at the start of the NEXT frame; alpha derived from where the turn
    // has got to is 0 on that frame either way, so there is no cut to fade
    // in from).
    //
    // Called in the draw phase, never read in composition: it changes every
    // frame of the turn, and the alternative is recomposing the whole screen
    // for 420ms to fade one element.
    val privacyProgress = LocalPrivacyProgress.current
    val spaceEnterAlpha: () -> Float = {
        val p = privacyProgress()
        if (privateMode) p else 1f - p
    }
    val currentId by vm.currentTabId.collectAsStateWithLifecycle()
    // Which tabs' pages are showing bottom-anchored controls of their own
    // right now — see PageBottomBar and pageBottomInset below.
    val pageBottomBars by vm.pageBottomBars.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val mostVisited by vm.mostVisited.collectAsStateWithLifecycle()
    val omniboxHistory by vm.omniboxHistory.collectAsStateWithLifecycle()
    val recentlyVisited by vm.recentlyVisited.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        ThemeMode.System -> systemInDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Only Compose can see the live system setting for ThemeMode.System —
    // push the resolved value down so the ViewModel can drive per-page
    // darkening off it without needing a Compose dependency of its own.
    LaunchedEffect(effectiveDark) { vm.setEffectiveDark(effectiveDark) }
    val pageDarkMode by vm.pageDarkMode.collectAsStateWithLifecycle()
    val readerAvailable by vm.readerAvailable.collectAsStateWithLifecycle()
    val readerActive by vm.readerActive.collectAsStateWithLifecycle()
    val readerSettings by vm.readerSettings.collectAsStateWithLifecycle()
    val pageDark by vm.pageDarkActive.collectAsStateWithLifecycle()
    val desktopMode by vm.desktopMode.collectAsStateWithLifecycle()
    val adBlockOn by vm.adBlockEnabled.collectAsStateWithLifecycle()
    val blocklistConfig by vm.blocklistConfig.collectAsStateWithLifecycle()
    val blocklistMeta by vm.blocklistMeta.collectAsStateWithLifecycle()
    val blocklistRuleCount by vm.blocklistRuleCount.collectAsStateWithLifecycle()
    val blocklistUpdating by vm.blocklistUpdating.collectAsStateWithLifecycle()
    val cosmeticRuleCount by vm.cosmeticRuleCount.collectAsStateWithLifecycle()
    val blocklistImportError by vm.blocklistImportError.collectAsStateWithLifecycle()
    val blockedOnPage by vm.blockedOnPage.collectAsStateWithLifecycle()
    val searchEngine by vm.searchEngine.collectAsStateWithLifecycle()
    val allSearchEngines by vm.allSearchEngines.collectAsStateWithLifecycle()
    val enabledSearchEngines by vm.enabledSearchEngines.collectAsStateWithLifecycle()
    val disabledSearchEngines by vm.disabledSearchEngines.collectAsStateWithLifecycle()
    val searchSuggestionsEnabled by vm.searchSuggestionsEnabled.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val accentTheme by vm.accentTheme.collectAsStateWithLifecycle()
    val specialTheme by vm.specialTheme.collectAsStateWithLifecycle()
    val autoFocusNewTabKeyboard by vm.autoFocusNewTabKeyboard.collectAsStateWithLifecycle()
    val openNewTabSheetOnLaunch by vm.openNewTabSheetOnLaunch.collectAsStateWithLifecycle()
    val newTabHistorySort by vm.newTabHistorySort.collectAsStateWithLifecycle()
    val tabManagerMode by vm.tabManagerMode.collectAsStateWithLifecycle()
    val linkStripperEnabled by vm.linkStripperEnabled.collectAsStateWithLifecycle()
    val openExternalLinksInOverlay by vm.openExternalLinksInOverlay.collectAsStateWithLifecycle()
    val openLinksInApps by vm.openLinksInApps.collectAsStateWithLifecycle()
    val pullToRefreshEnabled by vm.pullToRefreshEnabled.collectAsStateWithLifecycle()
    val linkPreviewEnabled by vm.linkPreviewEnabled.collectAsStateWithLifecycle()
    val pageLens by vm.pageLens.collectAsStateWithLifecycle()
    val statusBarBlur by vm.statusBarBlur.collectAsStateWithLifecycle()
    val translucentSheets by vm.translucentSheets.collectAsStateWithLifecycle()
    val translucency by vm.translucency.collectAsStateWithLifecycle()
    // Only the Default and Nothing looks: TUI and 98 are opaque by nature,
    // and Aero's sheets are glass already.
    // Provided above BrowserScreen (MainActivity) as LocalFrosted.
    val frostedSheets = com.yuku.browser.ui.theme.LocalFrosted.current
    val frostNothing = com.yuku.browser.ui.theme.LocalNothing.current
    val frostSheetCorner = if (frostNothing) minOf(SHEET_CORNER, 16.dp) else SHEET_CORNER
    val newTabPlacement by vm.newTabPlacement.collectAsStateWithLifecycle()
    val doubleTapTabsSwitchesTab by vm.doubleTapTabsSwitchesTab.collectAsStateWithLifecycle()
    val swipeToSwitchTabs by vm.swipeToSwitchTabs.collectAsStateWithLifecycle()
    val flickToCloseTab by vm.flickToCloseTab.collectAsStateWithLifecycle()
    val savePasswordsEnabled by vm.savePasswordsEnabled.collectAsStateWithLifecycle()
    val externalPasswordManager by vm.externalPasswordManager.collectAsStateWithLifecycle()
    val savedPasswords by vm.savedPasswords.collectAsStateWithLifecycle()
    val neverSavedSites by vm.neverSavedSites.collectAsStateWithLifecycle()
    val passwordPrompt by vm.passwordPrompt.collectAsStateWithLifecycle()
    val passwordSuggestion by vm.passwordSuggestion.collectAsStateWithLifecycle()
    val previousTabId by vm.previousTabId.collectAsStateWithLifecycle()
    // What a long press on the page landed on, if anything — see WebContextMenu.
    val contextTarget by vm.contextTarget.collectAsStateWithLifecycle()
    // …and the link it landed on, shown as a page rather than a menu. See
    // LinkPreviewOverlay.
    val linkPreview by vm.linkPreview.collectAsStateWithLifecycle()
    val findState by vm.findState.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val pageZoom by vm.pageZoom.collectAsStateWithLifecycle()
    // Anything the page put over itself — a fullscreen video, a script's
    // dialog, a certificate warning (see WebPlatform) — is over the page in
    // the same sense a sheet is, so previews and the back chain both have to
    // count it.
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val jsDialog by vm.jsDialog.collectAsStateWithLifecycle()
    val sslPrompt by vm.sslPrompt.collectAsStateWithLifecycle()
    val permissionAsk by vm.permissionAsk.collectAsStateWithLifecycle()
    // Read here rather than in the settings pane so the whole screen keeps
    // taking its state from one place; both are small and change rarely.
    val permissionRules by vm.permissionRules.collectAsStateWithLifecycle()
    val sitePermissions by vm.sitePermissions.collectAsStateWithLifecycle()
    val autofillSuggestion by vm.autofillSuggestion.collectAsStateWithLifecycle()
    val savedAddresses by vm.savedAddresses.collectAsStateWithLifecycle()
    val savedCards by vm.savedCards.collectAsStateWithLifecycle()
    val fillAddresses by vm.fillAddresses.collectAsStateWithLifecycle()
    val fillPaymentMethods by vm.fillPaymentMethods.collectAsStateWithLifecycle()
    // The tabs whose live view has yet to paint anything, and whose last
    // preview is therefore standing in for it — see
    // BrowserViewModel.coveredTabIds and PageCover below.
    val coveredTabIds by vm.coveredTabIds.collectAsStateWithLifecycle()

    val current = tabs.firstOrNull { it.id == currentId }
    // The site the menu's Site settings row is scoped to, and its record.
    // Derived here rather than exposed as a flow of its own: it is two reads
    // of things this screen already collects, and a flow combining them would
    // have to be seeded with a value from before either arrived.
    val siteSettingsMap by vm.siteSettings.collectAsStateWithLifecycle()
    val currentSite = remember(current?.url) {
        current?.url?.takeIf { it.isNotBlank() }?.let(UrlUtils::registrableDomain).orEmpty()
    }
    val currentSiteSettings = siteSettingsMap[currentSite] ?: SiteSettings()
    // Set at the start of a centered-card expansion, before selectTab has
    // recomposed. It prevents the previously active card from briefly being
    // hidden and animated as the live WebView during that handoff.
    var expandingTabId by remember { mutableStateOf<Long?>(null) }
    // The tab being flicked off the top from the tabs button, for as long as
    // its card is in the air. The grid reads it to close the row up over the
    // gap while the card is still flying (see TabSwitcher.recenterForClose).
    var closingTabId by remember { mutableStateOf<Long?>(null) }
    // Completed by the grid once it has finished closing the row up over that
    // card (see TabSwitcher's onCloseRecentred). The removal waits on it —
    // shortening the tab list while the row's recentring scroll is still
    // running blanks every card for a frame and fades them all back in.
    val closeRecentred = remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    // Retains the bitmap for exactly one rendered frame while the AndroidView
    // becomes visible. Keeping it for the full drag created a duplicate card;
    // hiding it immediately exposed a flash before WebView drew.
    var expansionPreviewVisible by remember { mutableStateOf(false) }
    // Keeps the static thumbnail up for a few frames PAST the instant the live
    // page takes over at full screen. Coming back from GONE (and, when the
    // expanded tab wasn't the one already hosted, from a reparent) costs a
    // layout pass plus however long Chromium needs to raster — the frames in
    // between are blank white. The cover is an image of the very page coming
    // up behind it, so holding it a beat too long is invisible, while dropping
    // it too early is exactly the flash this exists to prevent.
    var handoffCover by remember { mutableStateOf(false) }
    // The double-tap flip between the current tab and the last one (Settings
    // > Behavior). While a flip is running these hold the two tabs whose
    // pictures are sliding across each other; the live WebView is only handed
    // the new tab at the very end, under the cover of the incoming image, so
    // its reparent/re-raster happens off-screen exactly like every other
    // switch in here.
    var quickSwitchFromId by remember { mutableStateOf<Long?>(null) }
    var quickSwitchToId by remember { mutableStateOf<Long?>(null) }
    val quickSwitchProgress = remember { Animatable(0f) }
    val quickSwitching = quickSwitchFromId != null
    // Which side the incoming card comes in from: -1 from the left, +1 from
    // the right. The double-tap flip is always -1 (see QUICK_SWITCH_DIRECTION);
    // a sideways swipe is whichever way it is walking the row, so the card
    // enters from the edge the finger came off.
    var quickSwitchDirection by remember { mutableFloatStateOf(QUICK_SWITCH_DIRECTION) }
    // The live sideways swipe. `swipeSwitchToId` is set the moment the gesture
    // is accepted — before the overlay itself, which waits a frame for the
    // outgoing page's own picture — so a release inside that frame still knows
    // what it was going to.
    var swipeSwitchToId by remember { mutableStateOf<Long?>(null) }

    val floatingTabId = expandingTabId ?: currentId
    val floatingTab = tabs.firstOrNull { it.id == floatingTabId }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Non-null while the + sheet is standing in for the menu's address bar:
    // the URL that was in that bar when it was tapped.
    //
    // Editing an address is a change of SHEET, not a field that grows inside
    // the menu — the two are one omnibox in two states, and everything a
    // half-typed address wants (matching history, live search suggestions,
    // the private-space button) already lives on the + sheet. So the tap just
    // swaps `sheet` over, which is exactly what these two sheets sharing one
    // surface was for, and this string is what tells the + sheet it is
    // editing a page rather than opening one.
    //
    // Hoisted here rather than held in either sheet so back can undo the swap
    // before it means "close the sheet" — entering the edit is the last thing
    // the user did, so it is the first thing back undoes.
    var addressEdit by remember { mutableStateOf<String?>(null) }
    // Bumped by every open below, and the + sheet keys its field's `remember`
    // on it: the field is then rebuilt DURING the composition that opens the
    // sheet, so its first frame is already correct.
    //
    // It cannot be an effect inside the sheet, which is where this started.
    // An effect runs after the frame that opened it, so frame one rendered
    // whatever the last open left in the field — most visibly a previous
    // address, matched against history and drawn as a one-row "Matching
    // history" list before the real one arrived a frame later. The state has
    // to be gone before anything reads it, and only the caller knows the
    // moment it went stale.
    var newTabSheetOpens by remember { mutableStateOf(0) }
    // Every way INTO the + sheet, so the edit target and the reset above can
    // never disagree about which open this is. Deliberately the only writer
    // of `addressEdit`: it is set on the way in and left alone on the way
    // out, since clearing it while the sheet is still animating away would
    // flip the list back to matching-history for the length of the exit.
    // Whether the + sheet on screen was reached FROM the menu (its address
    // bar tapped). Such a sheet keeps the menu's height: it is the same
    // surface changing faces, and a resize under the tap reads as one sheet
    // replaced by another. Written here, in the event, so the height is
    // right on the frame `sheet` changes; an ordinary open clears it.
    var newTabKeepsMenuHeight by remember { mutableStateOf(false) }
    fun openNewTabSheet(editing: String? = null) {
        newTabKeepsMenuHeight = sheet == Sheet.Menu || sheet == Sheet.SiteSettings ||
            sheet == Sheet.ReaderSettings
        addressEdit = editing
        newTabSheetOpens++
        sheet = Sheet.NewTab
    }
    // Closing the very last tab — one at a time, not just via "close all" —
    // lands on EmptyState same as closeAllTabs does; both should open
    // straight into the new-tab sheet rather than leaving the user to find
    // the "+" button over a blank screen. Also the path for cold start, since
    // BrowserViewModel.init no longer seeds a tab.
    LaunchedEffect(tabs.isEmpty()) {
        if (tabs.isEmpty()) openNewTabSheet()
    }
    // Opt-in (Settings > Behavior): a relaunch with tabs restored still opens
    // into the new-tab sheet, so the browser starts ready to search rather
    // than on whatever page the last session ended on. Keyed on Unit so it's
    // once per process, not once per time the sheet is dismissed; the
    // ViewModel restores settings synchronously in init, so the flag is
    // already correct on this first composition.
    LaunchedEffect(Unit) {
        if (vm.openNewTabSheetOnLaunch.value) openNewTabSheet()
    }
    // A launcher shortcut ("New tab" / "Private tab"). Keyed on the counter,
    // not on Unit: the request can arrive long after this composition, and a
    // second shortcut while the app is up has to open the sheet again. Zero
    // is "nothing asked", i.e. an ordinary launch.
    val newTabSheetRequests by vm.newTabSheetRequests.collectAsStateWithLifecycle()
    LaunchedEffect(newTabSheetRequests) {
        if (newTabSheetRequests == 0) return@LaunchedEffect
        openNewTabSheet()
        vm.newTabSheetRequestHandled()
    }
    // Full-screen, not a sheet — it's a destination, not a quick action over
    // the current page. settingsPane resets on close (see the LaunchedEffect
    // below) so reopening always starts at the root instead of wherever the
    // user last drilled into.
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsPane by remember { mutableStateOf(SettingsPane.Root) }
    LaunchedEffect(settingsOpen) { if (!settingsOpen) settingsPane = SettingsPane.Root }
    // Same full-screen pattern as Settings — destinations, not quick actions.
    var bookmarksOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var downloadsOpen by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    // The switcher remains composed through its close animation. If it is
    // reopened before that animation unmounts it, a remembered LazyListState
    // would otherwise keep the previous session's centered card.
    var switcherSession by remember { mutableIntStateOf(0) }
    // Whether the page lens's black bezel can be up at all: the lens is on, there
    // is a page, and no full-screen destination is drawing its own ground under
    // the status bar. HOW MUCH of it is up is the lens's strength, which follows
    // the page's shrink (see lensStrength) — not whether the switcher is open,
    // which is true for the whole of a held drag, including the moment the page
    // is back at full size under the finger.
    val lensAvailable = pageLens && pageLensSupported && currentId != null &&
        !settingsOpen && !bookmarksOpen && !historyOpen && !downloadsOpen
    // The setting is the whole of it: there is no in-place switch between the
    // two presentations, so this is a reading of the setting rather than a
    // state of its own that has to be kept in step with it.
    val tabViewMode = when (tabManagerMode) {
        TabManagerMode.Vertical -> TabViewMode.Grid
        TabManagerMode.Horizontal -> TabViewMode.List
    }
    // True for the duration of a live tabs-button drag. Only the grid reads
    // the drag continuously; in list mode nothing is shown until the finger
    // lifts, which `switcherOpen` already says.
    var tabsButtonDragging by remember { mutableStateOf(false) }
    // Chrome-style hide-on-scroll. Only the page's own scroll drives this —
    // the switcher and any sheet always force it back visible below, since
    // there's no page underneath them to have scrolled in the first place.
    var toolbarVisible by remember { mutableStateOf(true) }
    // Hiding is purely a draw-phase translation of the bar off the bottom
    // edge; nothing is ever re-laid-out by it. Hoisted up here (rather than
    // living in the bottomBar slot where it's drawn) because the page's own
    // bottom inset has to know not just where the bar is going but whether
    // it has got there yet — see pageBottomInset.
    val toolbarSlide = animateFloatAsState(
        targetValue = if (toolbarVisible) 0f else 1f,
        animationSpec = tween(TOOLBAR_SLIDE_MS),
        label = "toolbarSlide",
    )
    // Read as a boolean, so the per-frame float doesn't recompose anything
    // outside the one draw lambda that actually animates on it.
    val toolbarSettled by remember { derivedStateOf { toolbarSlide.value == 0f } }
    // Accumulates same-direction scroll since the last direction reversal;
    // reset whenever the page reverses so a down-then-up flick needs to
    // cross the threshold again in the new direction rather than banking
    // leftover distance from the old one.
    var pendingScroll by remember { mutableStateOf(0f) }
    LaunchedEffect(currentId, switcherOpen, sheet, readerActive) {
        // A fresh tab, the switcher, or a sheet coming up should never leave
        // the bar hidden with nothing the user did on THIS page to explain it.
        // The reader is in that list at both ends: opening one puts a new
        // document in front of the user at its own top, and closing one gives
        // back a page whose scroll never moved while the article was up.
        toolbarVisible = true
        pendingScroll = 0f
    }
    // A new document is brought in with the bar up; this used to fall out of
    // the scroll rule below (a fresh page reports offset 0), which a page
    // moving ITSELF can no longer reach.
    val currentLoading = current?.loading == true
    LaunchedEffect(currentLoading) {
        if (currentLoading) {
            toolbarVisible = true
            pendingScroll = 0f
        }
    }
    fun onWebViewScroll(deltaY: Int, scrollY: Int, userDriven: Boolean = true) {
        if (switcherOpen || sheet != null) return
        // Only the user's own scrolling moves the bar. A page that locks its
        // scroll for a viewer or a menu (duckduckgo.com's image viewer sets
        // the body `position: fixed`) drops the offset to 0 in one jump, and
        // the rule below read that as reaching the top — the bar sprang up
        // over the image being opened, and hid again when it closed.
        if (!userDriven) return
        if (scrollY <= 0) {
            // Always visible at the top of the page — otherwise a hidden bar
            // right as the page loads (before any real scroll) would need an
            // upward scroll that doesn't exist yet to bring it back.
            toolbarVisible = true
            pendingScroll = 0f
            return
        }
        if (deltaY == 0) return
        if ((deltaY > 0) != (pendingScroll > 0)) pendingScroll = 0f
        pendingScroll += deltaY
        when {
            pendingScroll > SCROLL_HIDE_THRESHOLD_PX -> {
                toolbarVisible = false
                pendingScroll = 0f
            }
            pendingScroll < -SCROLL_HIDE_THRESHOLD_PX -> {
                toolbarVisible = true
                pendingScroll = 0f
            }
        }
    }
    // The reader scrolls a div inside a shadow root, so the view under it
    // never moves and the hook above never fires — the article reports its own
    // scroll instead (see ReaderMode.attach), in the same device pixels, and
    // it lands in the same rule. Nothing about hiding is reader-specific: the
    // bar covers the article exactly as it covers a page, and its inset is
    // already handed to the reader as the bar moves.
    LaunchedEffect(vm) {
        vm.readerScroll.collect { onWebViewScroll(it.deltaY, it.scrollY) }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberHaptics()

    // Downloads in flight. The activity itself changes per poll, so this
    // screen reads only whether there IS one; the fraction goes down as a
    // lambda to the two places that draw it.
    val downloadActivity = vm.downloadActivity.collectAsStateWithLifecycle()
    val downloading by remember { derivedStateOf { downloadActivity.value != null } }
    val downloadFraction = remember(downloadActivity) { { downloadActivity.value?.fraction } }
    val menuButton = remember { PlacedNode() }
    var downloadFlight by remember { mutableStateOf<DownloadFlight?>(null) }
    var downloadLandings by remember { mutableIntStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Which sheet's content to keep rendering while the close animation plays
    // — `sheet` itself goes null immediately on dismiss (see closeSheet),
    // but AnimatedVisibility still needs a `when` target for the fade/slide
    // out, same trick settingsPane's own LaunchedEffect-reset relies on.
    var lastSheet by remember { mutableStateOf(Sheet.NewTab) }
    LaunchedEffect(sheet) { sheet?.let { lastSheet = it } }

    // ---- the menu's own drill-down -----------------------------------------
    //
    // Site settings and Reader settings are pages OF the menu, reached by a
    // row and left by the same back arrow, so they move between each other
    // the way Settings' panes do: a shared axis, both layers travelling the
    // same way, crossing over. Menu lives on the LEFT of that axis and the
    // page it opened on the right — going in, the menu drifts left and is
    // gone while the page arrives from the right; going back, exactly
    // mirrored. See SettingsScreen's transitionSpec for where the shape came
    // from and Motion.kt for the constants; they are the same ones, so the
    // two drill-downs stay in step.
    //
    // Hand-rolled rather than AnimatedContent because these sheets are
    // composed at all times and only PLACED (see placedWhen): handing them to
    // AnimatedContent would dispose and recompose a sheet's worth of rows on
    // the frame the animation starts from, which is the cost every sheet here
    // is arranged to avoid. So one linear 0..1 progress is animated and each
    // layer applies its own easing inside its graphicsLayer — read in the
    // draw phase, so none of this recomposes anything.
    val menuPane = sheet ?: lastSheet
    val paneTarget = if (menuPane == Sheet.SiteSettings || menuPane == Sheet.ReaderSettings) 1f else 0f
    val paneProgress = remember { Animatable(paneTarget) }
    // Which direction the live transition is going, which is what decides
    // which layer is arriving (decelerate, fade up late) and which is leaving
    // (accelerate, fade out early).
    var paneForward by remember { mutableStateOf(paneTarget == 1f) }
    // Which layers are placed, off the PROGRESS itself rather than off a flag
    // raised when the animation starts. A flag is set from the effect, which
    // runs at the start of the NEXT frame, and the frame in between is the one
    // where `sheet` has already moved on: the menu was unplaced for it and the
    // page arriving had alpha 0, so every transition opened on one frame of
    // empty sheet. Measured on the release build, from a 120Hz screen
    // recording: the sheet's mean luminance went to the bare paper value on
    // the first frame of the crossing and back on the next. Read through
    // `derivedStateOf`, so the two booleans recompose their readers twice per
    // crossing and not once per frame, and both are true of the frame the
    // progress moves on — at rest either end, the layer that is showing is
    // placed and the other is not.
    val menuPanePlaced by remember { derivedStateOf { paneProgress.value < 1f } }
    val subPanePlaced by remember { derivedStateOf { paneProgress.value > 0f } }
    // Which sub-page to keep placed while the menu comes back over it.
    var lastSubSheet by remember { mutableStateOf(Sheet.SiteSettings) }
    LaunchedEffect(menuPane) {
        if (menuPane == Sheet.SiteSettings || menuPane == Sheet.ReaderSettings) lastSubSheet = menuPane
    }
    // Was the sheet already open the last time this ran? Only a change made
    // WITHIN an open sheet is a drill-down; a sheet that opens straight onto
    // a page it was last closed on has no journey to animate, and one that
    // closes leaves as whatever page it was showing.
    var sheetWasOpen by remember { mutableStateOf(sheet != null) }
    LaunchedEffect(paneTarget, sheet != null) {
        val open = sheet != null
        if (!open || !sheetWasOpen) {
            sheetWasOpen = open
            paneProgress.snapTo(paneTarget)
            return@LaunchedEffect
        }
        if (paneProgress.value == paneTarget) return@LaunchedEffect
        paneForward = paneTarget == 1f
        paneProgress.animateTo(paneTarget, tween(PANE_SLIDE_MS, easing = LinearEasing))
    }
    // How far through the current crossing, 0 at its start and 1 at its end,
    // whichever way it is going. Called in the draw phase only.
    fun paneCrossing(): Float =
        if (paneForward) paneProgress.value else 1f - paneProgress.value

    /** The travel of the layer that is ARRIVING, as a fraction of its rest offset. */
    fun paneArriveOffset(u: Float) = 1f - PaneDecelerate.transform(u)

    /** The travel of the layer that is LEAVING, from 0 at rest. */
    fun paneDepartOffset(u: Float) = Accelerate.transform(u)

    /** The incoming layer's alpha: nothing until the outgoing one has gone. */
    fun paneArriveAlpha(u: Float) =
        ((u * PANE_SLIDE_MS - PANE_FADE_OUT_MS) / (PANE_SLIDE_MS - PANE_FADE_OUT_MS)).coerceIn(0f, 1f)

    /** The outgoing layer's alpha: gone over the first PANE_FADE_OUT_MS. */
    fun paneDepartAlpha(u: Float) = 1f - (u * PANE_SLIDE_MS / PANE_FADE_OUT_MS).coerceIn(0f, 1f)

    // ---- + sheet <-> menu: a quick crossfade ---------------------------------
    //
    // Tapping the menu's address bar turns the surface into the + sheet, and
    // back cancels it the other way. Same place, same height (see
    // newTabKeepsMenuHeight), so there is no journey — only the faces swap,
    // over SHEET_FACE_FADE_MS. Same shape as the pane crossing above: one
    // progress (1 = the + sheet showing), read in the draw phase, and
    // placement derived off the progress so the tap's own frame still has the
    // outgoing face placed at full alpha.
    val sheetFaceIsNewTab = (sheet ?: lastSheet) == Sheet.NewTab
    val newTabFace = remember { Animatable(if (sheetFaceIsNewTab) 1f else 0f) }
    // The two faces' omnibox bars pop on ONE scale, so the crossfade a tap on
    // the address bar starts never shows them at two sizes (see aeroPopIf).
    val omniboxPop = com.yuku.browser.ui.theme.rememberAeroPop()
    val newTabFacePlaced by remember { derivedStateOf { newTabFace.value > 0f } }
    val menuFacePlaced by remember { derivedStateOf { newTabFace.value < 1f } }
    var sheetFaceWasOpen by remember { mutableStateOf(sheet != null) }
    LaunchedEffect(sheetFaceIsNewTab, sheet != null) {
        val target = if (sheetFaceIsNewTab) 1f else 0f
        val open = sheet != null
        if (!open || !sheetFaceWasOpen) {
            sheetFaceWasOpen = open
            newTabFace.snapTo(target)
            return@LaunchedEffect
        }
        newTabFace.animateTo(target, tween(SHEET_FACE_FADE_MS, easing = LinearEasing))
    }

    var sheetKeyboardHiddenByUs by remember { mutableStateOf(false) }
    // Dismissing or collapsing the sheet (tap-outside or drag-down) should
    // take the keyboard with it — NewTabSheet/MenuSheet's text fields stay
    // composed (just at zero height) between opens, so the keyboard would
    // otherwise linger with nothing visible to attach to.
    fun hideSheetKeyboard() {
        // Marks the hide as the app's own, so the effect below doesn't read
        // it as the user's back gesture. Set before the request, since the
        // inset target can change within it.
        sheetKeyboardHiddenByUs = true
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun closeSheet() {
        sheet = null
        hideSheetKeyboard()
    }

    // Hands the sheet back to the menu, its address bar reporting the
    // current page again — the state it was in before the bar was tapped.
    // The keyboard goes with the field: the menu has nothing to type into.
    fun cancelAddressEdit() {
        addressEdit = null
        sheet = Sheet.Menu
        hideSheetKeyboard()
    }

    // Back with the sheet's keyboard up takes BOTH down, in one gesture.
    //
    // The app never sees that back press: a visible IME is its own window and
    // consumes the key (and the gesture) itself, so the sheet was still there
    // afterwards and needed a second back. What DOES reach us is the keyboard
    // going away, which is enough — as long as we can tell the ones we asked
    // for from the one the user asked for. hideSheetKeyboard flags its own,
    // so what's left is back.
    //
    // Read off imeAnimationTarget, not ime: the target is known on the first
    // frame of the hide, so the sheet leaves alongside the keyboard rather
    // than after it, which is what makes it read as one dismissal.
    val imeTargetUp = WindowInsets.imeAnimationTarget.getBottom(density) > 0
    var sheetHadKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(imeTargetUp) {
        if (imeTargetUp) {
            sheetKeyboardHiddenByUs = false
            sheetHadKeyboard = sheet != null
            return@LaunchedEffect
        }
        val ours = sheetKeyboardHiddenByUs
        sheetKeyboardHiddenByUs = false
        val hadKeyboard = sheetHadKeyboard
        sheetHadKeyboard = false
        // Not ours, and the sheet is still up: the keyboard was dismissed by
        // back. Collapsing or dismissing the sheet by drag goes through
        // hideSheetKeyboard and so is flagged — a drag that only collapses
        // the sheet from fullscreen must not close it.
        //
        // Same order as the BackHandler's own: an address edit is the last
        // thing that happened, so it is what this back press undoes, and the
        // sheet stays.
        if (ours || !hadKeyboard || sheet == null) return@LaunchedEffect
        when {
            sheet == Sheet.NewTab && addressEdit != null -> cancelAddressEdit()
            sheet == Sheet.ReaderSettings || sheet == Sheet.SiteSettings -> sheet = Sheet.Menu
            else -> closeSheet()
        }
    }

    // Three fixed landmarks the sheet's height animates between — never
    // driven by the keyboard (which only ever covers the bottom third,
    // "floating" the omnibox + results above it), only by drag gestures on
    // the handle and open/dismiss.
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // The page is laid out under the status bar and veils itself there (see
    // PageTopInset.setStrip) — except under the page lens, whose own black
    // bezel and overscan already own that strip.
    val statusStripPx = if (pageLens && pageLensSupported) 0 else statusBarPx
    val statusStripFadePx = with(density) { STATUS_STRIP_FADE.roundToPx() }
    val sheetExpandedHeightPx = screenHeightPx - statusBarPx
    // The + sheet rests at two thirds. The menu rests at two thirds TOO,
    // unless its contents genuinely do not fit there — in which case it
    // rests at exactly the height they need and not a pixel more.
    //
    // It used to be a chosen fraction (0.74), picked by measuring the rows
    // on one screen and rounding up. That is a guess in two directions at
    // once: on this screen it left ~39dp of dead sheet under the last row —
    // a sheet made taller to show nothing, which is the very thing the
    // fraction was raised to avoid — and on a screen where the rows came out
    // taller it would have gone back to scrolling. The height is measured
    // now: MenuSheet reports its own contents' height from the layout phase
    // (see its `onContentHeight`), and what the sheet has to add to that is
    // the two fixed things above and below it, the drag handle and the
    // navigation-bar inset. The floor is the + sheet's own rest height, so
    // a short menu still opens to the height every other sheet opens to.
    // The strip is shorter for the menu by exactly the shadow room its own
    // contents pad in — see SheetBarShadowRoom — and that padding is already
    // inside the reported content height, so counting the full strip here
    // would count those 6dp twice.
    val menuChromePx = with(density) { (SheetDragHandleHeight - SheetBarShadowRoom).toPx() } +
        WindowInsets.navigationBars.getBottom(density)
    var menuContentHeightPx by remember { mutableStateOf(0f) }
    val restFloorPx = screenHeightPx * SHEET_REST_FRACTION
    // Read off `sheet ?: lastSheet` for the same reason `sheetExpandable`
    // below is: the height must not jump back mid-close, when `sheet` has
    // already gone null.
    // The two sheets reached FROM the menu keep the menu's height. They are
    // not other sheets: the menu is still the surface, and Site settings /
    // Reader settings are pages of it, reached by a row and left by the same
    // back arrow. A surface that resized under the tap would say a second
    // sheet had replaced the first, and the resize happens twice — once in,
    // once back out. So the menu's measured height is the height of all
    // three, and their contents are kept short enough to fit it (which is
    // why Site settings' switches carry no caption). It can only ever be
    // MORE room than they used to get, since the menu's own height is
    // floored at the 2/3 they rested at.
    val menuRestHeightPx = (menuContentHeightPx + menuChromePx)
        .coerceIn(restFloorPx, sheetExpandedHeightPx)
    val sheetRestHeightPx = when (sheet ?: lastSheet) {
        Sheet.Menu, Sheet.SiteSettings, Sheet.ReaderSettings -> menuRestHeightPx
        // Editing the address from the menu: see newTabKeepsMenuHeight.
        Sheet.NewTab -> if (newTabKeepsMenuHeight) menuRestHeightPx else restFloorPx
        else -> restFloorPx
    }
    val sheetCollapseThresholdPx = sheetRestHeightPx - with(density) { 96.dp.toPx() }
    // Only the + sheet grows to fullscreen. MenuSheet is a fixed list of
    // rows — there is nothing further down to reveal, so an upward swipe on
    // it should hand the movement to its own scroller and end in the
    // platform's stretch overscroll rather than dragging the whole surface
    // up to the status bar. Read off `sheet ?: lastSheet` so the ceiling
    // doesn't jump back up mid-close, when `sheet` is already null.
    val sheetExpandable = (sheet ?: lastSheet) == Sheet.NewTab
    // The tallest a drag or scroll may take the sheet right now.
    val sheetCeilingPx = if (sheetExpandable) sheetExpandedHeightPx else sheetRestHeightPx
    val sheetHeightAnim = remember { Animatable(0f) }
    var aeroSheetBounds by remember { mutableStateOf(Rect.Zero) }
    // The sheet's top corner radius as drawn (same `specialCorner` as its
    // Surface), so the toolbar underneath is clipped to its arcs.
    val sheetCornerPx = specialCorner(SHEET_CORNER).topStart
        .toPx(Size(1_000_000f, 1_000_000f), LocalDensity.current)
    // The find bar's bounds, for the page glass to frost under it (Aero).
    var aeroFindBounds by remember { mutableStateOf(Rect.Zero) }
    // Under Aero the tab LIST is drawn over the whole screen rather than in
    // the switcher's box (see the overlay after the Scaffold): its scrim then
    // reaches the navigation bar, and the page glass can frost the page under
    // it without frosting the list itself.
    val aeroListOverlay = com.yuku.browser.ui.theme.LocalAero.current
    val aeroListPane = remember { ListPaneBounds() }
    // Full-screen destinations use the same frosted page-glass pass as
    // sheets. Their own Aero surface is translucent by design, so without
    // this bound the live page simply showed through it unblurred.
    var aeroDestinationBounds by remember { mutableStateOf(Rect.Zero) }
    var aeroDestinationPaneAlpha by remember { mutableStateOf<() -> Float>({ 0f }) }
    var listToolbarInset by remember { mutableStateOf(0.dp) }
    var aeroBarCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // What an upward swipe on a sheet that can't grow does instead: the whole
    // surface stretches, the way a bottomed-out page does, and springs back
    // the moment the finger lifts. In pixels of extra height beyond the
    // sheet's own, never negative.
    //
    // Hand-driven rather than left to the platform's edge effect, for two
    // reasons. MenuSheet's rows usually FIT the sheet, and a
    // Modifier.verticalScroll with nothing to scroll never overscrolls at
    // all — so the swipe landed on nothing. And an edge effect only ever
    // deforms the CONTENT of the node it is applied to: the sheet's own
    // canvas — its background and its rounded top edge — stayed put while
    // the rows inside it moved, which reads as the rows sliding under a
    // stationary lid rather than as the sheet being pulled.
    // Held as the raw PULL — how far the finger has dragged past the end,
    // in sheet-heights — exactly what EdgeEffect accumulates, with
    // [stretchFraction] applied at draw time. Keeping the undamped quantity
    // is what makes the curve behave: damping the input and then animating
    // the damped value back would let a long swipe bank a stretch that
    // outlives the gesture.
    val sheetStretchPull = remember { Animatable(0f) }
    fun stretchSheet(leftoverUpPx: Float) {
        val height = sheetHeightAnim.value
        if (height <= 0f) return
        val next = (sheetStretchPull.value + leftoverUpPx / height)
            .coerceIn(0f, STRETCH_MAX_PULL)
        if (next != sheetStretchPull.value) scope.launch { sheetStretchPull.snapTo(next) }
    }
    // No inertia on the way back: the stretch belongs to the finger, so it
    // goes when the finger does. Critically damped, so it returns without
    // overshooting into a wobble, and stiffer than the platform's own 200 —
    // a sheet is a small surface and has less distance to cover than a
    // full-height list. Deliberately NOT one of Motion.kt's tweens: those
    // describe a surface travelling somewhere, and this is one snapping back
    // to where it already is.
    fun releaseSheetStretch() {
        if (sheetStretchPull.value == 0f && !sheetStretchPull.isRunning) return
        scope.launch {
            sheetStretchPull.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
        }
    }
    // The height the sheet's CONTENTS are measured at, which is deliberately
    // not the height the sheet is currently drawn at. It only ever changes at
    // the animation's landmarks (open → rest, settle → rest/expanded), so the
    // contents are measured once per gesture instead of once per frame; the
    // layout modifier below reports the animated height to the parent while
    // still handing this one down as the child's constraint. See there for
    // why the surplus is invisible.
    var sheetContentHeightPx by remember { mutableStateOf(0f) }
    // Whether the sheet is currently settled at its fullscreen landmark.
    // Once it is, a downward drag can only take it back to the 2/3 rest
    // height — never straight out the bottom — so the full list can't be
    // dismissed by one long swipe that was only meant to shrink it. It takes
    // a second, separate swipe from rest to actually close.
    var sheetExpanded by remember { mutableStateOf(false) }
    // The floor a drag/scroll may pull the sheet down to right now.
    fun sheetFloorPx() = if (sheetExpanded) sheetRestHeightPx else 0f
    LaunchedEffect(sheet, sheetRestHeightPx) {
        if (sheet != null) {
            sheetExpanded = false
            // Let the sheet's contents compose AND LAY OUT AT FULL HEIGHT on a
            // frame where nothing is moving yet. Opening a sheet composes it
            // for the first time — icons, text layout and all — which measured
            // at 47–77ms on the very frame the animation was starting from,
            // i.e. the sheet visibly hitching the moment it began to move.
            //
            // Setting the content height *before* this frame is what makes the
            // delay actually worth taking: at 0 height the LazyColumn composes
            // no rows at all, so the old version pre-warmed an empty list and
            // then paid for each row as the growing viewport reached it, one or
            // two per frame, for the whole 260ms. Now every row is composed and
            // measured on this one invisible frame, and the animation that
            // follows is pure placement.
            // Measured to the top of the OVERSHOOT, not to the rest height:
            // the sheet arrives a little past where it settles (see Motion.kt),
            // and a content box measured only to the rest height would be
            // re-measured on every frame of that overshoot — which is the one
            // thing this pre-warm frame exists to prevent.
            sheetContentHeightPx = sheetRestHeightPx * OVERSHOOT_PEAK
            withFrameNanos { }
            sheetHeightAnim.animateTo(sheetRestHeightPx, arrive(SURFACE_ENTER_MS))
            // Back down to the settled height once nothing is moving — one
            // measure pass, off the animation, and it is what puts the bottom
            // navigation-bar padding back on screen. Same trick, same reason,
            // as the collapse branch of settleSheet below.
            sheetContentHeightPx = sheetRestHeightPx
        } else {
            // sheetContentHeightPx is deliberately NOT reset here: keeping the
            // contents measured at their last height is what makes every
            // subsequent open free, and at zero animated height nothing of it
            // is placed or drawn (see the layout modifier).
            sheetHeightAnim.animateTo(0f, depart(SURFACE_EXIT_MS))
        }
    }

    // Where a drag/scroll gesture on the sheet settles once released —
    // shared by the plain drag (non-scrollable areas) and the nested-scroll
    // connection below (scrollable areas), so both land on the same three
    // landmarks.
    // [velocityY] is the finger's own speed at the lift, in pixels per second,
    // downward positive — half of the answer and often the whole of it. Zero
    // means "distance only", for the routes that have no velocity to offer.
    fun settleSheet(velocityY: Float = 0f) {
        val current = sheetHeightAnim.value
        val thrown = velocityY > SHEET_FLING_DISMISS_VELOCITY
        when {
            !sheetExpanded && (thrown || current < sheetCollapseThresholdPx) -> {
                hideSheetKeyboard()
                sheet = null
            }
            // `!thrown` so a hard flick DOWN from the fullscreen sheet
            // collapses it, rather than springing back up because the
            // surface had not yet travelled past the midpoint when the
            // finger left it. From expanded that collapse ends at the rest
            // height, never straight out the bottom — see sheetFloorPx.
            sheetExpandable && !thrown && current > (sheetRestHeightPx + sheetExpandedHeightPx) / 2f -> {
                sheetExpanded = true
                // Grow the contents to the target in one measure pass up
                // front, so the animation that follows only has to place
                // them. The reverse case doesn't need it — collapsing never
                // asks for more content than is already measured.
                sheetContentHeightPx = sheetExpandedHeightPx
                // The one settle with no overshoot on it: this target is the
                // top of the screen, so there is nothing above it to carry
                // into — an overshoot here would simply pin at fullscreen for
                // a few frames and read as the animation stalling at the end.
                scope.launch { sheetHeightAnim.animateTo(sheetExpandedHeightPx, tween(220)) }
            }
            else -> {
                sheetExpanded = false
                hideSheetKeyboard()
                scope.launch {
                    sheetHeightAnim.animateTo(sheetRestHeightPx, arrive(SNAP_BACK_MS))
                    // Only once the sheet has finished shrinking. Trimming the
                    // contents on the way down would re-measure them on every
                    // frame of the collapse (the surplus is below the screen
                    // edge the whole time, so there is nothing to see); doing
                    // it here costs one pass, and is what puts the bottom
                    // navigation-bar padding back on screen where it belongs.
                    sheetContentHeightPx = sheetRestHeightPx
                }
            }
        }
    }

    // Lets a swipe on NewTabSheet's history list (or MenuSheet's scrollable
    // content) grow/shrink the sheet instead of scrolling, right up until
    // the sheet is fully expanded or back at its floor — only then does the
    // list itself actually scroll. Swiping up also takes the keyboard down
    // with it, since that's what "reveal more of the list" means here.
    val sheetNestedScrollConnection = remember(sheetCeilingPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy >= 0f) {
                    // A downward drag over the list, before anyone has decided
                    // whether it scrolls the list or shrinks the sheet: the
                    // keyboard goes either way, for the same reason the plain
                    // drag below takes it down. A finger only — the deltas of
                    // a fling arrive here too, long after the gesture.
                    if (dy > 0f && source == NestedScrollSource.UserInput) hideSheetKeyboard()
                    return Offset.Zero
                }
                val current = sheetHeightAnim.value
                val room = sheetCeilingPx - current
                if (room <= 0f) return Offset.Zero
                hideSheetKeyboard()
                val used = min(-dy, room)
                scope.launch { sheetHeightAnim.snapTo(current + used) }
                return Offset(0f, -used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = available.y
                val current = sheetHeightAnim.value
                val floor = sheetFloorPx()
                if (dy < 0f && current >= sheetCeilingPx) {
                    // Up, with the content bottomed out and no room left to
                    // grow. Taken here rather than left to the child's own
                    // overscroll so that both routes into the swipe reach the
                    // one stretch — this connection sees the leftover before
                    // the scroller's own edge effect does.
                    //
                    // A FINGER only. The deltas a fling delivers after the
                    // finger has gone arrive here just the same, and feeding
                    // those was the long hold a hard swipe used to end in:
                    // the stretch kept growing for as long as the fling took
                    // to decay, and only came back after it.
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    stretchSheet(-dy)
                    return available
                }
                if (dy <= 0f || current <= floor) return Offset.Zero
                val used = min(dy, current - floor)
                scope.launch { sheetHeightAnim.snapTo(current - used) }
                return Offset(0f, used)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // The instant the finger lifts — onPostFling waits for the
                // child's fling to finish first, which is a hold the length
                // of the fling.
                releaseSheetStretch()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Leftover velocity after the list's own fling stopped dead
                // at its top bound — a fast downward flick over the list
                // otherwise has nothing left to collapse the sheet with,
                // since a stopped fling produces no further scroll deltas
                // for onPostScroll to catch. Continue that motion into the
                // sheet instead of just leaving it pinned at full height.
                // Already released in onPreFling; this is only insurance for
                // a fling that reaches the end of a list without one.
                releaseSheetStretch()
                if (available.y > 0f && sheetHeightAnim.value > sheetRestHeightPx) {
                    sheetExpanded = false
                    scope.launch { sheetHeightAnim.animateTo(sheetRestHeightPx, arrive(SNAP_BACK_MS)) }
                } else {
                    // The leftover velocity IS the finger's, which is exactly
                    // what the settle wants — but only when this gesture
                    // actually pulled the sheet down with it. A flick that
                    // stayed inside the list and merely ran out of rows has
                    // nothing to throw away, and judging it on that velocity
                    // would dismiss the sheet at the end of every fast scroll.
                    val movedSheet = sheetHeightAnim.value < sheetRestHeightPx
                    settleSheet(if (movedSheet) available.y else 0f)
                }
                return Velocity.Zero
            }
        }
    }

    // Drives WebViewHost's own scale between fullscreen and its card in the
    // switcher — the tab's content never cross-fades into a second element,
    // it just changes size and position as one continuous view. A raw
    // Animatable (not animateFloatAsState) so both a tap AND a live drag can
    // drive it — snapTo while dragging, animateTo when settling afterward.
    val progress = remember { Animatable(0f) }
    // The page lens's strength as the page itself is drawn with it (see
    // WebViewHost / AnimatedThumbnailHost): full at fullscreen, none at the
    // card; always full in list mode, where the page never shrinks. Deferred —
    // read in the draw phase by the status bar's backdrop below.
    val lensStrength: () -> Float = {
        if (!lensAvailable) 0f
        else if (tabViewMode == TabViewMode.List) 1f
        else 1f - shrinkOf(progress.value)
    }
    // The same, as the one boolean composition needs: which way the status
    // bar's icons and the toolbar's palette go. Past halfway, like the icons'
    // own light/dark turn.
    val lensChromeBlack by remember(lensAvailable, tabViewMode) {
        derivedStateOf {
            lensAvailable && (tabViewMode == TabViewMode.List || 1f - shrinkOf(progress.value) > 0.5f)
        }
    }
    // The tab manager owns the system-status-bar strip for its whole
    // lifetime, including the empty-tabs screen and both halves of the page
    // transition. The live page itself still reaches under that strip, so a
    // background behind it is not enough: the manager's ground is painted
    // back over the strip below, after the page has drawn.
    val emptyTabManager = tabs.isEmpty()
    val tabManagerOwnsStatusBar by remember(emptyTabManager) {
        derivedStateOf { emptyTabManager || switcherOpen || progress.value > 0f }
    }
    // Status/nav bar icon contrast has to track the app's own resolved
    // theme (which can be pinned to Light or Dark regardless of the system
    // setting), not the system's — that's not something the static XML
    // theme can express, so it's set imperatively here instead.
    // Whether the page's own strip is what sits under the status bar: a page
    // at (or past half of) full size, with no destination over it.
    val statusStripDark by vm.statusStripDark.collectAsStateWithLifecycle()
    // Reported by TabListSwitcher: its open sheet reaches under the status bar.
    var listCoversStatusBar by remember { mutableStateOf(false) }
    val pageStripUnderStatusBar by remember(
        statusStripPx > 0, currentId, settingsOpen, bookmarksOpen, historyOpen, downloadsOpen, tabViewMode,
    ) {
        derivedStateOf {
            statusStripPx > 0 && currentId != null &&
                !settingsOpen && !bookmarksOpen && !historyOpen && !downloadsOpen &&
                (tabViewMode == TabViewMode.List || 1f - shrinkOf(progress.value) > 0.5f)
        }
    }
    SystemBarIcons(
        ordinaryDark = effectiveDark,
        ignorePrivacy = settingsOpen,
        // A tab list that fills the screen is under the bar instead of the
        // page, and it is chrome, so the icons follow the theme.
        pageStatusDark = if (!tabManagerOwnsStatusBar && pageStripUnderStatusBar &&
            !(listCoversStatusBar && switcherOpen && tabViewMode == TabViewMode.List)
        ) currentId?.let { statusStripDark[it] } else null,
        blackStatusBar = lensChromeBlack && !tabManagerOwnsStatusBar,
        // A sheet covers the navigation bar with its own light surface.
        blackNavigationBar = lensChromeBlack && sheet == null,
    )
    // The CURRENT rendered position, as an offset from screen center — always
    // exactly this, nothing derives it from progress. During a live drag this
    // just accumulates raw finger movement (see onTabsDrag); it only ever
    // animates toward the switcher slot's position, or away from it, as a
    // deliberate step taken at release (see onTabsDragEnd) or from a tap
    // (openSwitcher/closeSwitcher) — never continuously during the drag
    // itself. That split is what makes tap and swipe genuinely separate
    // gestures instead of two triggers for the same continuous animation.
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    // All three of the above move once per frame for the whole of any
    // switcher gesture, and this composable is the largest one in the app —
    // reading `.value` in this scope recomposes the Scaffold, the toolbar,
    // both sheets' full contents, every card in the switcher, and
    // WebViewHost's AndroidView update lambda (which does real View-world
    // work per run) on each of those frames. Exactly the cost the sheet's own
    // height modifier documents, on a bigger surface.
    //
    // So nothing here reads the values. Composition gets these three
    // BOOLEANS, each of which changes at most a couple of times per gesture;
    // everything that needs the number itself is handed a lambda (see
    // effectiveProgress below) and reads it in the layout or draw phase,
    // where a read invalidates nothing.
    // `<= 0f`, not `== 0f`: a settle back out to fullscreen carries a hair
    // past zero (see SHRINK_INERTIA), and the whole of that tail is the page
    // at fullscreen. Testing for exactly zero would hide the live WebView
    // behind its own still image for the last frames of every expand.
    val progressAtZero by remember { derivedStateOf { progress.value <= 0f } }
    val progressAtRestOpen by remember {
        derivedStateOf { kotlin.math.abs(progress.value - MIDPOINT_PROGRESS) < REST_EPSILON }
    }
    val progressEngaged by remember { derivedStateOf { progress.value > 0f } }
    val switchProgressOf: () -> Float = { progress.value }

    // A tab created from the New Tab sheet while the switcher is up has no
    // card of its own to zoom out of — it doesn't exist in the grid until the
    // instant it's created, and the usual close-the-switcher zoom would run
    // from whatever card WAS current, i.e. a completely unrelated tab. So this
    // one entrance is its own motion instead: the new page slides up from the
    // bottom and fades in over the grid, which holds perfectly still
    // underneath (see their uses below) until it's done. Two separate values,
    // not one: the slide and the fade have different durations, exactly as
    // they do for the full-screen destinations this mirrors. 0 = fully below /
    // fully transparent, 1 = arrived.
    val newTabEnterSlide = remember { Animatable(1f) }
    val newTabEnterFade = remember { Animatable(1f) }
    var newTabEntering by remember { mutableStateOf(false) }

    // Coordinates used to find the switcher slot's on-screen position, for
    // both the render below and the drag handlers above it (which need to
    // know that target at release time) — declared up here so both can see
    // them.
    var boxCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // How much of that box the live page's BOX does NOT fill: the page is
    // boxed above the toolbar and only renders under it (see pageBottomInset
    // in the Scaffold content, which is the only place the bar's height is
    // known, hence the hoist). Everything below that positions the shrinking
    // page has to measure from the PAGE's center, not the box's, or the page
    // animates half an inset above the card it settles into.
    var pageBottomInsetPx by remember { mutableIntStateOf(0) }
    // Where the bar's top edge is right now, for the things that sit above it
    // rather than under it — unlike the page's box, this one follows the bar
    // as it hides.
    var chromeBottomInsetPx by remember { mutableIntStateOf(0) }
    // The two lines the reader's progress indicator lives between: the
    // toolbar's own top edge, which is where the loading line is drawn, and
    // the system navigation bar's, which is as far down as anything of ours
    // goes. Measured inside the Scaffold, where the insets are, and read at
    // the top level, where the indicator is drawn — the same wire
    // chromeBottomInsetPx uses for the find bar.
    var readerLineInsetPx by remember { mutableIntStateOf(0) }
    var readerFillInsetPx by remember { mutableIntStateOf(0) }
    var currentCardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Whichever grid card is centered in the row right now, and its on-screen
    // position — so the tabs button can expand THAT card when tapped to
    // close, instead of always falling back to whatever was current before
    // the row was scrolled.
    var centeredTabId by remember { mutableStateOf<Long?>(null) }
    var centeredCardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Set from the card that actually began an expand drag. Keeping this
    // explicit avoids a frame where targetOffset() still points at the
    // previously active tab while the newly centered tab is being selected.
    var expansionTarget by remember { mutableStateOf<Offset?>(null) }
    val targetRect = run {
        val box = boxCoordinates
        val card = currentCardCoordinates
        if (box != null && card != null && card.isAttached) {
            // Measured through the card's CENTER, not its top-left corner.
            // The switcher's own row is mid-zoom (see TabSwitcher's
            // ZOOM_SCALE) with its pivot set exactly to this card's center,
            // so that point alone reports a stable on-screen position
            // throughout the animation — every other point on the card
            // (including the corner) sweeps outward from it as the row's
            // scale changes, which is what skewed this off-center before.
            val halfSize = Offset(card.size.width / 2f, card.size.height / 2f)
            val center = box.localPositionOf(card, halfSize)
            Rect(Offset(center.x - halfSize.x, center.y - halfSize.y), card.size.toSize())
        } else {
            null
        }
    }
    // Where the live page's own center sits inside the box — above the box's
    // center by half the toolbar inset, since the page stops at the toolbar.
    fun pageCenterY(box: LayoutCoordinates) = (box.size.height - pageBottomInsetPx) / 2f

    // The switcher slot's center, as an offset from the page's center — the
    // value offsetX/offsetY animate TO when settling into the switcher, or
    // FROM when settling back to fullscreen. Null until the switcher has
    // actually laid out the current card once. Deliberately self-contained
    // (re-reads boxCoordinates/currentCardCoordinates fresh every call,
    // rather than closing over the `targetRect` val above) so it's safe to
    // call repeatedly from a coroutine that outlives the composition pass
    // that created it — see animateOffsetToTarget below.
    fun targetOffset(): Offset? {
        val box = boxCoordinates
        val card = currentCardCoordinates
        if (box == null || card == null || !card.isAttached) return null
        val center = box.localPositionOf(card, Offset(card.size.width / 2f, card.size.height / 2f))
        return Offset(center.x - box.size.width / 2f, center.y - pageCenterY(box))
    }

    fun targetOffsetFor(card: LayoutCoordinates): Offset? {
        val box = boxCoordinates ?: return null
        if (!card.isAttached) return null
        val center = box.localPositionOf(card, Offset(card.size.width / 2f, card.size.height / 2f))
        return Offset(center.x - box.size.width / 2f, center.y - pageCenterY(box))
    }

    // Where the finger was when the tabs-button drag became a drag, and how
    // far it has travelled since — in the same local space offsetX/offsetY
    // live in. Kept separately from the Animatables because the rendered
    // offset is the sum of this raw travel and the anchor correction below,
    // and the correction changes on its own as the shrink progresses.
    var dragAnchor by remember { mutableStateOf<Offset?>(null) }
    var dragAccum by remember { mutableStateOf(Offset.Zero) }

    // How far the shrinking page has to be moved, on top of the finger's raw
    // travel, for the card's BOTTOM-LEFT corner to sit under the finger
    // instead of the page's center sitting where the page's center was.
    //
    // The page is one fullscreen layer scaled about its own center (see
    // applyShrinkTransform), so at shrink `p` the drawn card is
    // `size * scale(p)` around (box centre x, pageCenterY). Putting its
    // bottom-left corner at the finger means moving its centre to
    // (finger.x + cardW/2, finger.y - cardH/2). Scaled by `p` so it's zero
    // while the page is still fullscreen — the drag must not jump the instant
    // it's recognized — and exact by the time the card reaches its final size.
    fun dragAnchorCorrection(rawProgress: Float): Offset {
        val box = boxCoordinates ?: return Offset.Zero
        val finger = dragAnchor ?: return Offset.Zero
        val p = (rawProgress / MIDPOINT_PROGRESS).coerceIn(0f, 1f)
        val pageWidth = box.size.width.toFloat()
        val pageHeight = (box.size.height - pageBottomInsetPx).toFloat()
        // Same fallback as applyShrinkTransform, so the anchor never
        // disagrees with the scale actually being drawn.
        val rect = targetRect
        val cardWidth = rect?.width ?: (pageWidth * FALLBACK_SHRINK_SCALE)
        val cardHeight = rect?.height ?: (pageHeight * FALLBACK_SHRINK_SCALE)
        return Offset(
            p * (finger.x + cardWidth / 2f - pageWidth / 2f),
            p * (finger.y - cardHeight / 2f - pageCenterY(box)),
        )
    }

    // Resolves the switcher slot's offset, waiting a few frames if needed.
    // The very first frame a fresh switcher-open begins (from a tap, or a
    // drag release, or selecting a different tab), targetOffset() is still
    // null — TabSwitcher hasn't measured a layout pass for that card yet —
    // so using it immediately had nowhere real to go but a fallback of
    // (0, 0)/screen center, and once the real position became known a frame
    // or two later there was nothing left to correct it: whatever had
    // already committed toward the wrong point either settled there and
    // visibly popped into its actual slot at the very end, or (worse) had
    // already snapped straight there with no animation to hide it at all.
    // Waiting a few frames here instead is imperceptible; using the wrong
    // value wasn't.
    suspend fun resolveTargetOffset(): Offset {
        var target = targetOffset()
        var attempts = 0
        while (target == null && attempts < 15) {
            withFrameNanos { }
            target = targetOffset()
            attempts++
        }
        return target ?: Offset.Zero
    }

    // Animates progress, offsetX and offsetY into the switcher slot
    // together, from the exact same starting instant — resolving the target
    // first (above), then launching all three from that single point rather
    // than as independent coroutines that could each start on a different
    // frame and so finish out of step with each other too. That was enough
    // on its own to read as a visible "jump" even once each one was
    // individually animating toward the correct value: scale settling
    // slightly before position (or vice versa) breaks the single "zoom from
    // center to center" motion this needs to be into two things visibly
    // catching up to each other.
    //
    // All three carry a little past the target and settle back onto it, on the
    // same curve over the same duration — so the page's size and its position
    // still finish together, which is the invariant this function exists for.
    // The size half of that is what [SHRINK_INERTIA] exists for: `progress`
    // itself overshoots [MIDPOINT_PROGRESS], and the shrink mapping has a band
    // past each end for it to land in rather than saturating the instant it
    // gets there.
    fun animateIntoSwitcher(durationMillis: Int) {
        scope.launch {
            val resolved = resolveTargetOffset()
            launch { progress.animateTo(MIDPOINT_PROGRESS, arrive(durationMillis)) }
            launch { offsetX.animateTo(resolved.x, arrive(durationMillis)) }
            launch { offsetY.animateTo(resolved.y, arrive(durationMillis)) }
        }
    }

    // Settles a swipe-down-to-expand card back into its own resting slot when
    // the gesture is released or cancelled before committing to fullscreen.
    // Unlike animateIntoSwitcher, expandingTabId must stay non-null for the
    // WHOLE animation, not just its kickoff: TabSwitcher freezes the grid's
    // own zoom (freezeRowForExpansion) only while it's set, and
    // targetOffset() resolves off whichever card is `current` (i.e.
    // floatingTabId). Nulling it up front — the previous behavior — flipped
    // both a frame early, so the grid's shared row-scale unfroze and
    // targetOffset() jumped to the ORIGINAL current tab's card instead of the
    // touched one, making every other card visibly hop instead of just this
    // one easing back into place.
    fun settleExpandingCardBack(durationMillis: Int) {
        val settlingId = expandingTabId
        scope.launch {
            val resolved = resolveTargetOffset()
            launch { progress.animateTo(MIDPOINT_PROGRESS, arrive(durationMillis)) }
            launch { offsetX.animateTo(resolved.x, arrive(durationMillis)) }
            offsetY.animateTo(resolved.y, arrive(durationMillis))
            if (expandingTabId == settlingId) expandingTabId = null
        }
    }

    // Tapping is its own, simple animation: a straight zoom to/from the
    // switcher slot, with no notion of a finger position at all — distinct
    // from a swipe, which never animates toward that slot until release (see
    // onTabsDragEnd) or not at all if it's released past the midpoint.
    fun openSwitcher() {
        if (tabs.isEmpty()) return
        // Whatever a sideways swipe left owing: the row is about to be shown
        // in most-recently-used order, and the tab those swipes landed on is
        // the most recently used one (see BrowserViewModel.stepToTab).
        vm.promoteSteppedTab()
        vm.captureAllThumbnails()
        if (!switcherOpen) {
            switcherSession++
            currentCardCoordinates = null
            centeredTabId = null
            centeredCardCoordinates = null
        }
        switcherOpen = true
        animateIntoSwitcher(280)
    }

    fun closeSwitcher() {
        switcherOpen = false
        // The SIZE carries past fullscreen and settles back — the page grows a
        // hair bigger than the screen, which the window clips, so it uncovers
        // nothing. The POSITION does not: past 0 the page is off centre at full
        // size, which shows a strip of bare background along one edge. See
        // onTabsDragEnd's snap-back branch, which shares this split.
        scope.launch { progress.animateTo(0f, arrive(280)) }
        scope.launch { offsetX.animateTo(0f, tween(280)) }
        scope.launch { offsetY.animateTo(0f, tween(280)) }
    }

    /**
     * The tabs sheet's own dismissal, for the two buttons that raise a sheet
     * of their own. Nothing at all in grid mode, where the switcher is not a
     * sheet and both of them are meant to open over it.
     */
    fun closeTabsSheetForSheet() {
        if (switcherOpen && tabViewMode == TabViewMode.List) closeSwitcher()
    }

    /**
     * The tail every tab flip shares: the deck has landed on [toId], so the
     * tab actually changes now and the incoming image is held over its WebView
     * until that WebView has painted — NOT for a fixed number of frames.
     * Switching tabs reparents it (see WebViewHost), so it has been detached
     * since it left the screen and Chromium rasters it from nothing; how long
     * that takes belongs to the page, and a heavy one uncovered white halfway
     * through it.
     *
     * Two frames first, for the reparent itself: it happens in WebViewHost's
     * update lambda, i.e. in the composition this state change schedules, and
     * asking a WebView that isn't in the tree yet for its next frame is asking
     * nothing at all.
     */
    suspend fun landQuickSwitch(toId: Long, select: () -> Unit) {
        select()
        repeat(2) { withFrameNanos { } }
        vm.awaitPagePainted(toId, QUICK_SWITCH_PAINT_MAX_MS)
        // The callback says the frame is ready; this is it going up.
        withFrameNanos { }
    }

    /**
     * A sideways swipe off one of the toolbar's end buttons, walking the tab
     * row one step: [delta] is -1 (the tab on the left, entering from the left
     * edge) or +1. Returns whether there was anything to walk to — the whole
     * gesture is swallowed if not, rather than tracking a card that doesn't
     * exist.
     *
     * The same deck the double-tap flip turns, driven by the finger instead of
     * a clock. Nothing commits until release, so the tab itself doesn't change
     * until then and a gesture abandoned halfway simply puts the page back.
     */
    fun beginSwipeSwitch(delta: Int): Boolean {
        // Turned off, the gesture is refused at its very first frame, which
        // the toolbar's axis lock reads as Rejected and swallows for the whole
        // drag — so the buttons keep their tap (and the tabs button its drag
        // up into the switcher) untouched.
        if (!swipeToSwitchTabs) return false
        if (quickSwitching || swipeSwitchToId != null) return false
        // Only from plain fullscreen browsing: over an open switcher the
        // buttons mean something else entirely, and under a sheet the page
        // isn't what the user is looking at.
        if (switcherOpen || progress.value > 0f || sheet != null) return false
        val fromId = currentId
        val target = vm.neighbourTab(delta) ?: return false
        if (target.id == fromId) return false
        swipeSwitchToId = target.id
        quickSwitchDirection = if (delta < 0) -1f else 1f
        scope.launch {
            // Ask for a copy of the page being left BEFORE anything covers it,
            // and give that request a frame to be serviced against the
            // uncovered window (see BrowserViewModel.captureExact) — the
            // outgoing image is one whole half of this animation.
            vm.captureThumbnail(fromId)
            withFrameNanos { }
            // Released inside that frame: the gesture is already over and
            // raising the deck now would leave it up with nothing driving it.
            if (swipeSwitchToId != target.id) return@launch
            quickSwitchProgress.snapTo(0f)
            quickSwitchFromId = fromId
            quickSwitchToId = target.id
        }
        return true
    }

    /** Tracks the finger: [dragX] is signed px travelled since the gesture took hold. */
    fun swipeSwitchDrag(dragX: Float) {
        if (swipeSwitchToId == null) return
        val width = (boxCoordinates?.size?.width?.toFloat() ?: 0f).coerceAtLeast(1f)
        // Both gestures pull the incoming card AWAY from the edge it entered
        // by, so travel counts positively in the direction opposite to it.
        val travelled = (dragX * -quickSwitchDirection / width).coerceIn(0f, 1f)
        scope.launch { quickSwitchProgress.snapTo(travelled) }
    }

    fun swipeSwitchEnd(velocityX: Float) {
        val toId = swipeSwitchToId ?: return
        swipeSwitchToId = null
        val p = quickSwitchProgress.value
        // A flick counts whichever way it is going, so the velocity is read
        // along the same axis the travel is: away from the entering edge.
        val flung = velocityX * -quickSwitchDirection > SWIPE_SWITCH_FLING
        val commit = p > SWIPE_SWITCH_COMMIT || flung
        if (commit) haptics.gestureEnd() else haptics.reject()
        scope.launch {
            try {
                if (commit) {
                    // What is left of the travel, at the speed a whole flip
                    // takes — a card released nearly home shouldn't take the
                    // full duration to cover the last few pixels.
                    val remaining = (QUICK_SWITCH_MS * (1f - p)).toInt().coerceAtLeast(120)
                    quickSwitchProgress.animateTo(1f, arriveSoft(remaining))
                    // Positionally: the row has to hold still for the NEXT
                    // swipe to mean what this one did (see stepToTab).
                    landQuickSwitch(toId) { vm.stepToTab(toId) }
                } else {
                    // Nothing to settle onto — the card is going back off the
                    // edge it came from.
                    quickSwitchProgress.animateTo(0f, depart((QUICK_SWITCH_MS * p).toInt().coerceAtLeast(120)))
                }
            } finally {
                quickSwitchFromId = null
                quickSwitchToId = null
                quickSwitchDirection = QUICK_SWITCH_DIRECTION
            }
        }
    }

    fun swipeSwitchCancel() {
        if (swipeSwitchToId == null) return
        swipeSwitchToId = null
        scope.launch {
            try {
                quickSwitchProgress.animateTo(0f, depart(SNAP_BACK_MS))
            } finally {
                quickSwitchFromId = null
                quickSwitchToId = null
                quickSwitchDirection = QUICK_SWITCH_DIRECTION
            }
        }
    }

    /**
     * Flips to whichever tab was current before this one and back again — the
     * double-tap on the tabs button. Nothing about the switcher is involved:
     * the two tabs' previews slide across the screen over the live page, and
     * only once that has landed does the tab actually change, so the WebView
     * swap is never on screen.
     */
    fun quickSwitchTabs() {
        if (quickSwitchFromId != null) return
        val fromId = currentId
        // No tab has been switched away from yet this session (a cold start
        // is the usual case) — the row is in most-recently-used order, so the
        // last tab that isn't this one is the one "before" it either way.
        val toId = previousTabId.takeIf { it != 0L && tabs.any { t -> t.id == it } }
            ?: tabs.lastOrNull { it.id != fromId }?.id
            ?: return
        if (toId == fromId) return
        scope.launch {
            // Ask for a copy of the page being left BEFORE anything covers
            // it, and give that request a frame to be serviced against the
            // uncovered window (see BrowserViewModel.captureExact) — the
            // outgoing image is about to be the whole left half of this
            // animation, so a stale one would be plainly visible.
            vm.captureThumbnail(fromId)
            withFrameNanos { }
            quickSwitchDirection = QUICK_SWITCH_DIRECTION
            quickSwitchFromId = fromId
            quickSwitchToId = toId
            quickSwitchProgress.snapTo(0f)
            try {
                quickSwitchProgress.animateTo(1f, arriveSoft(QUICK_SWITCH_MS))
                // The flip is a jump to the most recently used tab rather than
                // a walk along the row, so this one DOES reorder.
                landQuickSwitch(toId) { vm.selectTab(toId) }
            } finally {
                quickSwitchFromId = null
                quickSwitchToId = null
            }
        }
    }

    // Sheets (and the full-screen destinations they lead to) can now be opened
    // with the switcher still up behind them, so anything that actually puts a
    // page on screen — a new tab, an address-bar Go, a reload, a bookmark or
    // history entry — has to take the switcher down with it, or the result of
    // that action would land behind a grid the user is still looking at. A
    // no-op when the switcher isn't open.
    fun closeSwitcherForNavigation() {
        if (switcherOpen || progress.value > 0f) closeSwitcher()
    }

    // The one navigation that can't use closeSwitcherForNavigation above: a
    // brand-new tab has no switcher slot to zoom out of, so it enters on its
    // own terms instead — see newTabEnterSlide/Fade. Only over an open switcher;
    // from ordinary browsing this is just newTab, unchanged.
    fun openNewTabFromSheet(
        url: String,
        private: Boolean = privateMode,
        openerId: Long = 0L,
        background: Boolean = false,
    ) {
        val overSwitcher = switcherOpen || progress.value > 0f
        vm.newTab(private = private, url = url, openerId = openerId, background = background)
        // Nothing arrives on screen for a background tab, so there is no
        // entrance to play: the page the user is reading stays exactly where
        // it was, which is the whole of what they asked for.
        if (background || !overSwitcher) return
        switcherOpen = false
        scope.launch {
            // Park the shared open/close animation at "fullscreen" without
            // animating it — nothing about that gesture is what's playing
            // here, and leaving it mid-shrink would scale the entering page.
            progress.snapTo(0f)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            newTabEnterSlide.snapTo(0f)
            newTabEnterFade.snapTo(0f)
            // Only now, with progress already at 0 and the entrance already
            // at its start values: this flag is what gates the transform (and
            // suppresses the floating label) for the whole entrance, so it
            // must never be observed a frame before the values it's guarding.
            newTabEntering = true
            try {
                // Started together, finishing on their own durations — the
                // fade lands slightly before the slide settles, same as the
                // destinations' enter transition.
                launch { newTabEnterFade.animateTo(1f, tween(NEW_TAB_ENTER_FADE_MS)) }
                newTabEnterSlide.animateTo(1f, arrive(NEW_TAB_ENTER_SLIDE_MS))
            } finally {
                newTabEntering = false
            }
        }
    }

    // Shared by both switcher presentations (grid card tap, list row tap) —
    // zooms out from THAT card/row's own on-screen position, the same way a
    // completed swipe-down expand does, instead of always animating from
    // wherever the previously current card happened to sit.
    // The tab the list was opened over, held as a still picture over the page
    // while the tab that was tapped takes its place behind it, and faded out.
    // Null whenever nothing is crossing over.
    var listSwitchFromId by remember { mutableStateOf<Long?>(null) }
    val listSwitchFade = remember { Animatable(0f) }

    /**
     * Selecting a tab out of the LIST.
     *
     * The grid's move (below) is a zoom: the page shrank into a card on the
     * way in, so on the way out it grows back out of the card that was tapped,
     * and the tab changing is something that happens inside that one
     * continuous transform. The list never shrank anything — the page stayed
     * exactly where it was, with the sheet in front of it — so there is
     * no card for a page to come out of, and zooming one out of a row claims a
     * relationship between a 68dp strip of text and a full screen of pixels
     * that isn't there.
     *
     * What is actually happening is one page replacing another in the same
     * place, so that is what is drawn: the outgoing page is held over the
     * incoming one and faded out while the sheet leaves and the page comes
     * back to the glass. A cut is what this was, and a cut in the middle of
     * two other animations reads as a frame being dropped rather than as a
     * change of tab.
     */
    fun selectTabFromRow(id: Long) {
        closeSwitcher()
        if (id == currentId) return
        val from = currentId
        // The switch itself happens NOW, at the top of the fade, so the
        // incoming tab spends the whole of it painting behind the picture of
        // the outgoing one — which is the same window the handoff cover uses
        // for the reparent, and the reason there is anything to cross to.
        vm.selectTab(id)
        listSwitchFromId = from
        scope.launch {
            listSwitchFade.snapTo(1f)
            try {
                listSwitchFade.animateTo(0f, tween(LIST_SWITCH_FADE_MS))
            } finally {
                // In `finally`, like every other cover here: a stranded one
                // freezes the page under a still image of a tab the user has
                // left.
                if (listSwitchFromId == from) listSwitchFromId = null
            }
        }
    }

    fun selectTabFromCard(id: Long, cardCoordinates: LayoutCoordinates) {
        // No card to come out of in list mode — see selectTabFromRow.
        if (tabViewMode == TabViewMode.List) {
            selectTabFromRow(id)
            return
        }
        expandingTabId = id
        expansionPreviewVisible = true
        switcherOpen = false
        scope.launch {
            progress.stop()
            offsetX.stop()
            offsetY.stop()
            val target = targetOffsetFor(cardCoordinates)
            if (target != null) {
                offsetX.snapTo(target.x)
                offsetY.snapTo(target.y)
            }
            progress.snapTo(MIDPOINT_PROGRESS)
            withFrameNanos { }
            expansionPreviewVisible = false
            launch { offsetX.animateTo(0f, tween(240)) }
            launch { offsetY.animateTo(0f, tween(240)) }
            try {
                progress.animateTo(0f, arrive(240))
                // Reorder to the rightmost slot only now that the fullscreen
                // animation has actually finished (switchProgress back at 0),
                // which is also what unmounts the grid (see `switcherOpen ||
                // switchProgress > 0f` around the switcher's render site).
                // Reordering any earlier moves this card's own slot in the grid
                // while the grid is still on screen underneath, which showed up
                // as the surrounding cards visibly shifting mid-zoom.
                vm.selectTab(id)
            } finally {
                // In `finally` so an interrupted zoom (another gesture taking
                // over this Animatable cancels the animateTo above) can't
                // strand this set — see onExpandDragEnd for what a stranded
                // expandingTabId does to the switcher.
                if (expandingTabId == id) expandingTabId = null
            }
        }
    }

    // Safety net for the invariant every expand/collapse path above is
    // individually responsible for: once the switcher has actually settled at
    // its resting open state, no expansion is in flight any more, so nothing
    // live may still be floating over the grid. Keyed on the transition INTO
    // that settled state, so a card's own swipe-down-to-expand — which begins
    // from exactly this state and only moves `progress` on its second event —
    // isn't cleared out from under itself the moment it sets expandingTabId.
    val settledOpen = switcherOpen && progressAtRestOpen
    LaunchedEffect(settledOpen) {
        if (settledOpen) expandingTabId = null
    }

    // What is over the page, computed here rather than at each call site
    // below: the back chain has to know about these too, and it is declared
    // above them.
    //
    // Find on page sits above the keyboard it comes up with, and only while
    // the page is what's on screen — the same test, and the same anchor, the
    // password cards use.
    val pageIsWhatsOnScreen = sheet == null && !switcherOpen && progressAtZero &&
        !settingsOpen && !bookmarksOpen && !historyOpen && !downloadsOpen &&
        contextTarget == null && linkPreview == null
    val findForPage = findState?.takeIf { it.tabId == currentId && pageIsWhatsOnScreen }
    // Saved passwords put two things over the page, never at once: the offer
    // to keep a login that was just submitted, and the offer to fill one that
    // already is. Both sit above the toolbar, and both are for the page — so
    // neither appears while anything else is: a sheet, the switcher, a
    // destination, the context menu, or a shrink in progress.
    // Find takes the same spot above the keyboard, and is the one the user
    // is actively driving — so it wins for as long as it's up.
    // The page's own text-selection toolbar, while the page is what's on
    // screen — see [ActionModeOwner]. Not a composable of ours to dismiss:
    // back finishes the mode, which is what takes the toolbar and the
    // selection with it.
    val pageSelection = (LocalContext.current as? ActionModeOwner)
        ?.activeActionMode?.takeIf { pageIsWhatsOnScreen }
    val passwordsOverPage = findForPage == null && pageIsWhatsOnScreen
    val promptForPage = passwordPrompt?.takeIf { passwordsOverPage && it.tabId == currentId }
    val suggestionForPage = passwordSuggestion?.takeIf {
        passwordsOverPage && it.tabId == currentId && promptForPage == null
    }
    // The checkout equivalent, under both of the above: the ViewModel already
    // refuses to raise one while a password bar is up, and this is the same
    // rule spelled out where the three are actually drawn.
    val autofillForPage = autofillSuggestion?.takeIf {
        passwordsOverPage && it.tabId == currentId &&
            promptForPage == null && suggestionForPage == null
    }

    // A tab opened from a link on another tab, with no page history of its
    // own left to walk: back closes it and lands back on the tab it came
    // from. The opener has to still be there — `openerTab` checks, and this
    // reads `tabs` (the open space's list) so the same recomposition that
    // drops the opener's card also drops this branch.
    val backLeavesChildTab = current?.let { c ->
        !c.canGoBack && c.openerTabId != 0L && tabs.any { it.id == c.openerTabId }
    } == true

    // Back is a five-level decision: context menu, destinations/sheet,
    // switcher, page history, then out of a tab a link opened. Falling
    // through all of them leaves the callback DISABLED, so the system — not
    // us — handles the gesture and the app closes with the platform's own
    // predictive-back animation (see enableOnBackInvokedCallback in the
    // manifest). Never call finish() here: that closes the app with no
    // animation at all, which is exactly what this is meant to avoid.
    BackHandler(
        enabled = linkPreview != null || contextTarget != null || settingsOpen || bookmarksOpen || historyOpen ||
            downloadsOpen || sheet != null || findState != null || switcherOpen ||
            pageSelection != null || promptForPage != null || suggestionForPage != null ||
            autofillForPage != null || readerActive || current?.canGoBack == true || backLeavesChildTab,
    ) {
        when {
            // The preview is a page of its own over the page: back walks ITS
            // history first, exactly as it does for a tab, and closes the card
            // only once there is nowhere left to go inside it.
            linkPreview != null -> if (!vm.previewGoBack()) vm.closeLinkPreview()
            // Innermost thing on screen, and the only one anchored to a spot
            // on the page — it goes first.
            contextTarget != null -> vm.dismissContextMenu()
            // One step back within Settings itself before falling through to
            // dismissing it entirely, same as its own in-page back arrow.
            settingsOpen && settingsPane != SettingsPane.Root ->
                settingsPane = settingsPane.parent ?: SettingsPane.Root
            settingsOpen -> settingsOpen = false
            bookmarksOpen -> bookmarksOpen = false
            historyOpen -> historyOpen = false
            downloadsOpen -> downloadsOpen = false
            // Returns to the menu rather than taking the whole sheet down
            // at once — the bar goes back to reporting the page it always
            // was, which is the step the tap took.
            sheet == Sheet.NewTab && addressEdit != null -> cancelAddressEdit()
            // Likewise one step back within the sheet before it comes down:
            // the reader's settings were reached FROM the menu, so back is
            // the way in reversed rather than the whole surface going.
            sheet == Sheet.ReaderSettings || sheet == Sheet.SiteSettings -> sheet = Sheet.Menu
            sheet != null -> closeSheet()
            // Below the sheets (which cover it) and above the page: back
            // out of the find bar before it starts walking page history.
            findState != null -> vm.closeFind()
            switcherOpen -> closeSwitcher()
            // Dismissible offers over the page, and the last thing the app
            // put there — so they go before the page's own history, or back
            // would navigate out from under a card that stayed on screen.
            // Declining is what back means here: "not now" for the save
            // offer, and the fill bar simply going away — never "never for
            // this site", which is a decision, not a dismissal.
            // Text selected in the page: back clears it, the same as every
            // other browser and every ordinary text field on the platform.
            // Above the password cards because it is the more recent thing —
            // the selection is made by hand, the cards arrive on their own.
            pageSelection != null -> pageSelection.finish()
            promptForPage != null -> vm.dismissPasswordPrompt()
            suggestionForPage != null -> vm.dismissPasswordSuggestion()
            autofillForPage != null -> vm.dismissAutofillSuggestion()
            // The reader is a view OVER the page, not a page of its own: it
            // has no history to walk, so back simply gives the article back
            // to the document it came from. Above page history for the
            // reason every other overlay here is — leaving it up while the
            // page navigates underneath would show an article from a
            // document that is no longer loaded.
            readerActive -> vm.toggleReaderMode()
            current?.canGoBack == true -> vm.goBack()
            // Only reached when the page itself has nowhere left to go back
            // to: closeTab lands on the opener itself, since it prefers a
            // closed tab's opener over the most recent one.
            backLeavesChildTab -> current?.let { vm.closeTab(it.id) }
        }
    }

    Scaffold(
        containerColor = PageBg,
        bottomBar = {
            // The bar always occupies its slot in the Scaffold, so the
            // content padding it reports never changes and nothing below is
            // ever re-laid-out by a hide/show. Hiding is purely a draw-phase
            // translation off the bottom edge — the page is already drawn
            // under the bar (see the content padding below), so sliding it
            // away reveals live page rather than a strip of empty background.
            // No fade either: fading an opaque bar over the page it sits on
            // reads as a smear, where a plain slide reads as the bar leaving.
            // A sheet takes the bar's place, so the bar leaves by the same
            // edge rather than blinking out: a quick slide down as a sheet
            // opens, and back up once the sheet has fully gone (it is still
            // on screen for its own close animation after `sheet` is null,
            // hence the height too). Composition sees only the boolean.
            // Full-screen destinations only. A SHEET does not slide the bar
            // away (the slide was long, and under a translucent sheet it was
            // seen going): the bar simply stops drawing the moment the sheet's
            // top edge — its address bar — has risen over it, and comes back
            // the moment it sinks below, so the swap happens under the sheet.
            val barCoveredByOverlay by remember {
                derivedStateOf { settingsOpen || bookmarksOpen || historyOpen || downloadsOpen }
            }
            val sheetBarSlide = animateFloatAsState(
                targetValue = if (barCoveredByOverlay) 1f else 0f,
                animationSpec = if (barCoveredByOverlay) {
                    tween(SHEET_BAR_OUT_MS, easing = Accelerate)
                } else {
                    tween(SHEET_BAR_IN_MS, easing = PaneDecelerate)
                },
                label = "sheetBarSlide",
            )
            Box(
                // Read in the draw phase (inside the lambda), so each frame
                // of the slide invalidates only this layer, not the whole
                // Scaffold composition.
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    aeroBarCoordinates = coordinates
                }.graphicsLayer {
                    val sheetSlide = sheetBarSlide.value
                    val sheetH = sheetHeightAnim.value
                    val cornerR = sheetCornerPx
                    // Covered only once the sheet's rounded CORNERS have risen
                    // past the bar's top too, not just its top edge: until then
                    // the bar shows beside each arc, already in place.
                    val underSheet = sheetH - cornerR >= size.height
                    translationY = size.height * maxOf(toolbarSlide.value, sheetSlide)
                    // Fully off the edge is also invisible, so nothing of the
                    // bar (a glow, a shadow) is left showing under the sheet.
                    alpha = if (sheetSlide >= 1f || underSheet) 0f else 1f
                    // The bar is always there; only the part of it NOT under a
                    // sheet is drawn. Clipped to the sheet's own top outline —
                    // flat edge AND rounded corners (local space, so less the
                    // slide) — which keeps a translucent sheet from showing the
                    // bar through itself, and leaves no hole beside an arc.
                    // Open upward, for the loading ruler's band above the bar.
                    if (sheetH > 0f && !underSheet) {
                        val visibleBottom = size.height - sheetH - translationY
                        val sheetBounds = aeroSheetBounds
                        val barLeft = aeroBarCoordinates?.takeIf { it.isAttached }
                            ?.localToRoot(Offset.Zero)?.x ?: 0f
                        val sheetLeft = if (sheetBounds.isEmpty) 0f else sheetBounds.left - barLeft
                        val sheetRight = if (sheetBounds.isEmpty) size.width else sheetBounds.right - barLeft
                        shape = object : androidx.compose.ui.graphics.Shape {
                            override fun createOutline(
                                size: Size,
                                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                density: androidx.compose.ui.unit.Density,
                            ): androidx.compose.ui.graphics.Outline {
                                if (cornerR <= 0f) return androidx.compose.ui.graphics.Outline.Rectangle(
                                    Rect(0f, -size.height, size.width, visibleBottom),
                                )
                                val far = size.height * 2f + cornerR * 2f
                                val visible = androidx.compose.ui.graphics.Path().apply {
                                    addRect(Rect(0f, -size.height, size.width, visibleBottom + cornerR))
                                }
                                val covered = androidx.compose.ui.graphics.Path().apply {
                                    addRect(Rect(0f, visibleBottom, sheetLeft, far))
                                    addRect(Rect(sheetRight, visibleBottom, size.width, far))
                                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                        Rect(sheetLeft, visibleBottom, sheetRight, far),
                                        CornerRadius(cornerR),
                                    ))
                                }
                                return androidx.compose.ui.graphics.Outline.Generic(
                                    androidx.compose.ui.graphics.Path().apply {
                                        op(visible, covered, androidx.compose.ui.graphics.PathOperation.Difference)
                                    },
                                )
                            }
                        }
                        clip = true
                    } else {
                        shape = androidx.compose.ui.graphics.RectangleShape
                        clip = false
                    }
                }.then(
                    // The page lens's black under the WHOLE bar slot, behind
                    // the bar itself: a look whose bar is not a plain rectangle
                    // (Aero's rounded glass, clipped) or not opaque lets the
                    // page through its corners and its glass, and the bezel
                    // the bar stands for has no page in it. Inside the slide's
                    // layer, so it leaves with the bar.
                    if (!lensAvailable) Modifier
                    else Modifier.drawBehind {
                        drawRect(Color.Black, alpha = lensStrength().coerceIn(0f, 1f))
                    }
                ),
            ) {
            // Under the page lens the bar is part of the black bezel the
            // page's curved edges end in (see PageLens): the dark inks on a
            // black ground, whatever the app's own theme is.
            val themeHairLine = com.yuku.browser.ui.theme.LocalBrowserPalette.current.hairLine
            androidx.compose.runtime.CompositionLocalProvider(
                com.yuku.browser.ui.theme.LocalBrowserPalette provides
                    // The inks turn at halfway, like the system bars' icons;
                    // the black GROUND comes in gradually (`bezel` below), so
                    // the bar keeps the theme's own fill under it.
                    if (lensChromeBlack) com.yuku.browser.ui.theme.LensBezelPalette.copy(
                        barBg = com.yuku.browser.ui.theme.LocalBrowserPalette.current.barBg,
                    )
                    else com.yuku.browser.ui.theme.LocalBrowserPalette.current,
            ) {
            BottomToolbar(
                // The line reports the page you are LOOKING at, not every
                // load the session has in flight. A tab whose view had to be
                // built again — closing a tab hands the screen to one, so
                // does opening any tab parked since the last launch — is
                // loading behind the switcher, and a bar filling for a page
                // that is not on screen is the app reporting its own
                // housekeeping. See BrowserViewModel.pageOnScreen, which
                // holds the same tab's name and favicon still for the same
                // reason.
                loading = pageIsWhatsOnScreen && current?.loading == true,
                progress = current?.progress ?: 0,
                switcherOpen = switcherOpen,
                listViewActive = tabViewMode == TabViewMode.List,
                // Only asks for double-tap detection when there is actually
                // a tab to flip to: passing a doubleTap handler delays every
                // single tap by the double-tap timeout, and paying that for a
                // gesture that could only ever no-op would make the button
                // feel worse for nothing. Two tabs is the whole condition —
                // NOT "previousTabId is set", which is empty on every cold
                // start and so switched the gesture off exactly when the
                // setting had just been turned on.
                doubleTapSwitchesTab = doubleTapTabsSwitchesTab && tabs.size > 1,
                onTabsDoubleTap = {
                    if (switcherOpen || progress.value > 0f) closeSwitcher() else quickSwitchTabs()
                },
                onTabsPress = {
                    // The page's own preview, taken while it is still the
                    // thing on screen — see onPress in BottomToolbar.
                    if (!switcherOpen && progress.value <= 0f) vm.captureAllThumbnails()
                },
                onTabs = {
                    if (switcherOpen) {
                        // Grid mode only: the row can be scrolled without
                        // changing currentId, so a plain tap should expand
                        // whatever card is centered on screen right now, not
                        // whichever tab was current before the switcher was
                        // opened/scrolled. Reuses the same path a direct card
                        // tap takes for identical animation behavior.
                        val targetId = centeredTabId
                        val targetCoords = centeredCardCoordinates
                        if (tabViewMode == TabViewMode.Grid && targetId != null && targetId != currentId && targetCoords != null) {
                            selectTabFromCard(targetId, targetCoords)
                        } else {
                            closeSwitcher()
                        }
                    } else if (tabs.isEmpty()) {
                        // Nothing to manage: openSwitcher refuses an empty
                        // space (there are no cards and no page to shrink into
                        // them), so the button pressed, buzzed and did
                        // nothing at all until a tab existed again. The empty
                        // screen has exactly one thing to offer, and it is
                        // what closing the last tab already opens by itself —
                        // so the tabs button offers it too rather than
                        // answering a press with silence.
                        openNewTabSheet()
                    } else {
                        openSwitcher()
                    }
                },
                onTabsDragStart = { fingerWindowPosition ->
                    if (tabs.isNotEmpty()) {
                        tabsButtonDragging = true
                        // Where the finger actually is, in the same local
                        // space offsetX/offsetY live in — the page is anchored
                        // to it as it shrinks (see onTabsDrag).
                        dragAnchor = boxCoordinates?.windowToLocal(fingerWindowPosition)
                        dragAccum = Offset.Zero
                        scope.launch { progress.stop() }
                        scope.launch { offsetX.stop() }
                        scope.launch { offsetY.stop() }
                        // Only worth the cost when coming from fullscreen
                        // browsing — captureAllThumbnails draws every open
                        // tab's WebView into a bitmap synchronously on the UI
                        // thread, so calling it again on every re-drag from an
                        // already-open switcher (nothing on screen has
                        // changed since it was last captured) stalls exactly
                        // the first frames of the new drag, which is what was
                        // showing up as a jump instead of tracking the finger.
                        if (!switcherOpen) {
                            vm.promoteSteppedTab()
                            vm.captureAllThumbnails()
                        }
                    }
                },
                onTabsDrag = { delta ->
                    if (tabs.isNotEmpty()) {
                        val travelPx = with(density) { DRAG_TRAVEL.toPx() }
                        // Dragging UP is a negative Y delta, so it should
                        // increase progress; dragging back down retreats it
                        // — all the way back past "open" to "cancelled" if
                        // the finger keeps going, since it's one continuous
                        // range and nothing commits until release.
                        val newProgress = (progress.value - delta.y / travelPx).coerceIn(0f, MAX_PROGRESS)
                        scope.launch { progress.snapTo(newProgress) }
                        dragAccum += delta
                        // The page doesn't just follow the finger, it shrinks
                        // toward it: the corner nearest the tabs button (the
                        // card's bottom-left) ends up UNDER the finger rather
                        // than the page's center staying pinned to where the
                        // page center was, which drew the card well above the
                        // touch point the whole way up. The correction is
                        // itself faded in by how far the shrink has got, so
                        // it's zero at fullscreen (nothing jumps when the drag
                        // is recognized) and exact once the card has reached
                        // its final size.
                        val anchorCorrection = dragAnchorCorrection(newProgress)
                        // Horizontal: floats with the finger the whole live
                        // drag, raw — position never tracks the switcher slot
                        // until release, so there's nothing to lean back
                        // toward yet.
                        scope.launch { offsetX.snapTo(dragAccum.x + anchorCorrection.x) }
                        // Vertical: exactly like the horizontal axis —
                        // raw, 1:1 with the finger for the WHOLE drag, from
                        // the first pixel. It used to stay pinned at 0 until
                        // progress passed MIDPOINT_PROGRESS and only track
                        // the finger past that, which meant the shrinking
                        // page sat glued to screen center for the first
                        // ~140dp of travel and then started moving — and,
                        // dragging back down, stuck to center again. Around
                        // that boundary the page kept alternating between
                        // "following my finger" and "snapped to the middle".
                        // Now it never centers itself mid-gesture at all;
                        // centering is purely a release-time animation (see
                        // onTabsDragEnd -> animateIntoSwitcher).
                        scope.launch { offsetY.snapTo(dragAccum.y + anchorCorrection.y) }
                    }
                },
                onTabsDragEnd = { velocity ->
                    tabsButtonDragging = false
                    if (tabs.isNotEmpty()) {
                        val p = progress.value
                        when {
                            // Dragged (or flicked, past "open") far enough to
                            // commit to closing — continue the SAME live,
                            // uncentered position further up and off-screen
                            // from wherever it already was, rather than
                            // snapping into the switcher slot first. Then the
                            // tab is actually removed once it's off-screen.
                            // List mode never takes this branch at all — see
                            // below.
                            // With flick-to-close off this branch is simply
                            // never taken: the drag itself is unchanged — the
                            // page shrinks and follows the finger exactly as
                            // far as it did — and the release falls through to
                            // the branch below, which settles it into the
                            // switcher slot instead of throwing it off the top.
                            flickToCloseTab &&
                                tabViewMode != TabViewMode.List &&
                                (p > CLOSE_COMMIT_PROGRESS || (p > MIDPOINT_PROGRESS && velocity.y < FLING_VELOCITY_THRESHOLD)) -> {
                                // Closing a tab is the one outcome of this
                                // gesture that destroys something, so it gets
                                // the heavier "committed" effect rather than
                                // the settle the other two branches share.
                                haptics.confirm()
                                val idToClose = currentId
                                // Whether anything is left behind to hold the
                                // screen — the last tab of a space has nothing
                                // to slide in after it.
                                val hasSuccessor = tabs.any { it.id != idToClose }
                                // The gesture ENDS IN THE SWITCHER, not on a
                                // page: the card is thrown off the top and the
                                // row closes up over the gap, with the
                                // neighbour gliding into the centre. Opening
                                // whatever inherited the screen is then the
                                // user's own next move, the same as it would be
                                // after closing a card from inside the grid.
                                switcherOpen = hasSuccessor
                                // The grid needs this for the whole flight —
                                // its own recentring scroll has to overlap the
                                // card leaving, not follow it.
                                closingTabId = idToClose
                                val settled = CompletableDeferred<Unit>()
                                closeRecentred.value = settled
                                val flyAwayPx = (boxCoordinates?.size?.height?.toFloat() ?: 2400f) * 2.5f
                                scope.launch {
                                    try {
                                        offsetY.animateTo(
                                            offsetY.value - flyAwayPx,
                                            depart(CLOSE_FLY_AWAY_MS),
                                        )
                                        // The row's scroll OUTLIVES the
                                        // flight, so the card landing is not
                                        // the moment to shorten the list under
                                        // it — see closeRecentred. Bounded,
                                        // because the grid may not be there to
                                        // answer (an empty space has no row,
                                        // and a recentring that is cancelled
                                        // never reports): the tab still closes,
                                        // just as it used to.
                                        withTimeoutOrNull(CLOSE_RECENTRE_TIMEOUT_MS) {
                                            settled.await()
                                        }
                                        vm.closeTab(idToClose)
                                    } finally {
                                        closingTabId = null
                                        if (closeRecentred.value === settled) {
                                            closeRecentred.value = null
                                        }
                                    }
                                    if (hasSuccessor) {
                                        // Park the floating layer back at the
                                        // switcher's own resting state, on the
                                        // tab that inherited it. None of this
                                        // is on screen: at rest the grid draws
                                        // that card itself (see
                                        // hideCurrentThumbnail) and the
                                        // floating one is hidden — it only has
                                        // to agree with where the card sits so
                                        // the NEXT gesture starts from the
                                        // right place.
                                        progress.snapTo(MIDPOINT_PROGRESS)
                                        val resolved = resolveTargetOffset()
                                        offsetX.snapTo(resolved.x)
                                        offsetY.snapTo(resolved.y)
                                    } else {
                                        progress.snapTo(0f)
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                }
                            }
                            // Committed to opening — NOW, for the first time,
                            // animate from wherever it currently is into the
                            // actual switcher slot ("centers itself"). List
                            // mode never let the drag show anything at all no
                            // matter how far the finger traveled, so the real
                            // Animatables are snapped back to match what was
                            // on screen before being animated: the page is
                            // pinned there either way, but `progress` is what
                            // keeps this switcher MOUNTED, and leaving it at
                            // whatever the drag reached would have it settle
                            // from a value the eye never saw.
                            p > 0.15f || velocity.y < FLING_VELOCITY_THRESHOLD -> {
                                haptics.gestureEnd()
                                if (!switcherOpen) {
                                    switcherSession++
                                    currentCardCoordinates = null
                                    centeredTabId = null
                                    centeredCardCoordinates = null
                                }
                                switcherOpen = true
                                if (tabViewMode == TabViewMode.List) {
                                    scope.launch {
                                        progress.snapTo(0f)
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                        val resolved = resolveTargetOffset()
                                        launch { progress.animateTo(MIDPOINT_PROGRESS, arrive(240)) }
                                        launch { offsetX.animateTo(resolved.x, arrive(240)) }
                                        launch { offsetY.animateTo(resolved.y, arrive(240)) }
                                    }
                                } else {
                                    animateIntoSwitcher(240)
                                }
                            }
                            else -> {
                                // Released without ever committing — the page
                                // is going straight back where it came from.
                                haptics.reject()
                                switcherOpen = false
                                // The size carries past fullscreen and settles
                                // back (the window clips it, so it uncovers
                                // nothing); the position must not, or the page
                                // sits off centre at full size and shows bare
                                // background along one edge.
                                scope.launch { progress.animateTo(0f, arrive(SNAP_BACK_MS)) }
                                scope.launch { offsetX.animateTo(0f, tween(SNAP_BACK_MS)) }
                                scope.launch { offsetY.animateTo(0f, tween(SNAP_BACK_MS)) }
                            }
                        }
                    }
                },
                onSwipeTabStart = { delta -> beginSwipeSwitch(delta) },
                onSwipeTabDrag = { dragX -> swipeSwitchDrag(dragX) },
                onSwipeTabEnd = { velocityX -> swipeSwitchEnd(velocityX) },
                onSwipeTabCancel = { swipeSwitchCancel() },
                onTabsDragCancel = {
                    tabsButtonDragging = false
                    switcherOpen = false
                    scope.launch { progress.animateTo(0f, arrive(200)) }
                    scope.launch { offsetX.animateTo(0f, tween(200)) }
                    scope.launch { offsetY.animateTo(0f, tween(200)) }
                },
                // Both sheets open OVER the switcher, leaving it exactly as it
                // is underneath (same scroll position, same centered card)
                // rather than first zooming back out to the current page —
                // that zoom was visible through/behind the sheet as a jump to
                // a page the user hadn't asked to go back to. The switcher is
                // only actually dismissed once something commits to a page
                // (see closeSwitcherForNavigation).
                // …with one exception, and it is what the note above is
                // really about: in LIST mode the switcher is itself a bottom
                // sheet, and a second sheet rising over the first is two of
                // the same object stacked on one screen, the lower one
                // unreachable behind the higher. So the tabs sheet goes first
                // there — the page it was over comes back forward as the
                // asked-for sheet arrives over it. The grid keeps the
                // behaviour above exactly.
                onNewTab = { closeTabsSheetForSheet(); openNewTabSheet() },
                onMenu = { closeTabsSheetForSheet(); sheet = Sheet.Menu },
                // Both of those raise a sheet over the page, which is the end
                // of any chance to photograph it. Asked for at the press.
                onCoverPress = { vm.captureThumbnail(currentId) },
                downloading = downloading,
                downloadFraction = downloadFraction,
                downloadLandings = downloadLandings,
                onMenuButtonPlaced = { menuButton.coordinates = it },
                bezel = lensStrength,
                // The theme's own rule, not the lens palette's: it fades out
                // with the zoom instead of flipping at halfway with the inks.
                dividerColor = themeHairLine,
            )
            }
            }
        },
    ) { padding ->
        // The bar's own height is deliberately NOT applied to the content —
        // the page is laid out full height and drawn underneath it, which is
        // what lets the bar slide away without leaving a gap. Everything that
        // isn't the page (the switcher below) re-applies it by hand.
        val toolbarInset = padding.calculateBottomPadding()
        val lensHostView = LocalView.current
        val lensCornerBrush = remember { if (pageLensSupported) LensCornerBrush() else null }
        SideEffect { listToolbarInset = toolbarInset }

        // The switcher's previews are copied straight out of the window's own
        // pixels (BrowserViewModel.captureThumbnail) rather than re-drawn by
        // Chromium, which is the only way vector/composited content comes out
        // at the right size and place. That copy is only the page while the
        // page is what's actually filling the screen, unshrunk and with
        // NOTHING of the browser's own over it — anything else bakes a
        // sheet's scrim, a handoff cover or a half-shrunk page into the card.
        //
        // It is built ON TOP of [pageIsWhatsOnScreen] rather than beside it,
        // which is the fix for previews that came back with the browser in
        // them: that test is already the app's one answer to "is the page the
        // thing the user is looking at" (it carries the sheets, the switcher,
        // every full-screen destination, the long-press menu and the link
        // preview), and a second hand-written list of the same states drifts —
        // Downloads and the context menu were in one of them and not the
        // other. What is added here is only what that test deliberately does
        // NOT count as covering the page, because those surfaces sit BESIDE
        // the page rather than over it — but each of them is still the
        // browser's own pixels inside the window, so each of them is a thing a
        // copy must not contain: the find bar, both password cards, and the
        // covers and transitions the switcher raises.
        //
        // The text-selection toolbar is deliberately NOT in the list: it is a
        // floating window of the platform's own, outside this one's surface,
        // so a copy never sees it.
        val pageExposed = pageIsWhatsOnScreen && !handoffCover &&
            floatingTabId !in coveredTabIds &&
            !quickSwitching && !newTabEntering && expandingTabId == null &&
            listSwitchFromId == null &&
            fullscreen == null && jsDialog == null && sslPrompt == null &&
            permissionAsk == null &&
            findForPage == null && promptForPage == null && suggestionForPage == null &&
            autofillForPage == null
        // A SideEffect, not a LaunchedEffect: the latter is dispatched through
        // AndroidUiDispatcher, i.e. at the START OF THE NEXT FRAME, so the
        // frame that first draws a sheet over the page was still telling the
        // ViewModel the page was exposed — which is exactly the frame a copy
        // asked for on that press lands against. A SideEffect runs at the end
        // of the composition that changed the state, before anything is drawn.
        SideEffect { vm.setPageExposed(pageExposed) }
        // The same question one level up, and the ViewModel wants both: a
        // load only shows itself — loading line, title, favicon — while the
        // page it belongs to is the screen, and the surfaces added above sit
        // BESIDE the page rather than over it, so they rule out a copy
        // without ruling out the page. See BrowserViewModel.pageOnScreen.
        SideEffect { vm.setPageShowing(pageIsWhatsOnScreen) }
        // How much of the screen's bottom the toolbar is holding, and what
        // the page does about it.
        //
        // The page's own BOX — what the shrink transform, the switcher's card
        // aspect and every capture are measured against — is the screen minus
        // the toolbar, always, whether the bar is up or not. The live WebView
        // then OVERFLOWS that box by [pageOverflow] and renders under the bar
        // (see WebViewHost), so hiding the bar uncovers page that is already
        // rendered there.
        //
        // Nothing is re-laid-out by a hide or a show, which is the point: the
        // page used to grow into the strip on the first frame of the hide,
        // and for the frame or two Chromium's compositor took to catch up
        // with its new size, nothing painted that strip at all — what showed
        // through was the WINDOW background, which comes from the XML theme
        // and so follows the SYSTEM light/dark setting rather than the app's
        // own. With the app pinned to Dark on a system that is light, that is
        // a white band between the retreating bar and the page.
        //
        // A tab bar, player or checkout button must clear the gesture pill.
        // Detection can still control the overlay's treatment, but must not
        // change the ordinary WebView's bounds after the page has painted.
        val pageHasBottomBar = pageBottomBars.containsKey(currentId)
        val navigationBarInset = with(density) {
            WindowInsets.navigationBars.getBottom(density).toDp()
        }
        // Keep the native viewport independent of asynchronous DOM reports.
        // Discovering a footer after first paint used to shorten the WebView
        // by 24 CSS px, reflowing viewport-height heroes while their fixed
        // headers stayed put. Reserve the safe floor before navigation.
        val pageFloorInset = maxOf(navigationBarInset, MIN_PAGE_BOTTOM_BUFFER)
        val pageBottomInset = toolbarInset
        // The keyboard is the one thing the page must be made SHORTER for,
        // rather than shifted out of the way of. Chromium does not resize a
        // WebView's viewport for the on-screen keyboard — "the Android app is
        // responsible for sizing the WebView" — and until the view it is in
        // actually gets shorter the renderer doesn't know the keyboard exists:
        // `visualViewport` never changes, and the field the user just tapped
        // is never scrolled into view, because as far as the page is concerned
        // it already is. So while the IME is up the WebView ends at the top of
        // it (this is also the standard edge-to-edge recipe: the container
        // takes systemBars + displayCutout + ime).
        //
        // The ANIMATION TARGET, not the live inset: the live one climbs frame
        // by frame as the keyboard slides up, and following it is a reflow of
        // the page per frame. The target is the height it is going to be, from
        // the first frame of the animation, so the page reflows exactly once
        // — and once again, early, when it starts going away.
        //
        // And only while the keyboard is the PAGE's. The omnibox, the new-tab
        // sheet and every full-screen destination bring one up too, and that
        // one has nothing to do with the document underneath: shortening the
        // WebView for it reflows a page nobody is looking at, on the exact
        // frames the sheet is animating in. On an ordinary page that is
        // invisible; on a canvas app it is not. kontur.systems is a WebGL map
        // — the resize reallocates its GL surface and re-centres the map on
        // its new container height, so opening the new-tab sheet visibly
        // shoved the map up and cost frames doing it. The find bar and the
        // password cards are NOT excluded: those keyboards are for the page,
        // and a match (or a field) scrolled to under the keyboard is the bug
        // the shortening exists to prevent.
        val imeTargetPx = WindowInsets.imeAnimationTarget.getBottom(density)
        val imeOverPage = sheet == null && !switcherOpen &&
            !settingsOpen && !bookmarksOpen && !historyOpen && !downloadsOpen
        val imeUp = imeTargetPx > 0 && imeOverPage
        // The WebView always runs to the screen's edge. If the page owns a
        // bottom bar, PageBottomBar lifts that bar clear of the navigation
        // inset and paints the exposed strip in the bar's own colour; changing
        // the native viewport after DOM detection would reflow the page.
        val aeroChrome = com.yuku.browser.ui.theme.LocalAero.current
        val webBottomInset = when {
            imeUp -> maxOf(with(density) { imeTargetPx.toDp() }, pageFloorInset)
            // The page runs beneath the transparent navigation bar in every
            // normal look. Ending the WebView at `pageFloorInset` left the
            // browser's pale fallback canvas behind the gesture handle, which
            // is a filled navigation bar even when the system bar itself is
            // transparent. `pageEndPadPx` below keeps the document's final
            // content clear of that handle.
            else -> 0.dp
        }
        // Negative while the keyboard is up — the WebView is then SHORTER than
        // the page's box rather than hanging below it.
        val pageOverflow = toolbarInset - webBottomInset
        // The page lens (see PageLens): the bend's depth, measured off the
        // navigation bar — its handle is centred in that inset, so its height
        // is the distance from the screen's edge to as far above the handle as
        // the handle sits above the edge — scaled; and how far ABOVE the page's
        // box the WebView hangs, under the status bar, so that its curve reads
        // real page rather than a clamped first row (above only — see
        // pageOverflowPx). Capped at the status bar's height, since it lives
        // under it.
        val lensDepthPx = if (pageLens && pageLensSupported) {
            (WindowInsets.navigationBars.getBottom(density).toFloat()
                .takeIf { it > 0f } ?: with(density) { PAGE_LENS_DEPTH.toPx() }) * PAGE_LENS_SCALE
        } else {
            0f
        }
        val lensOverscanPx = kotlin.math.ceil(lensDepthPx * PAGE_LENS_REACH).toInt()
            .coerceAtMost(statusBarPx)
        // Kept as a separate value for the WebView host's lens API. The
        // navigation-bar clearance itself is supplied below for every visual
        // theme, rather than being a lens-only treatment.
        val lensBarLiftPx = 0
        // No lens overscan at this end. It used to ride the overflow — more
        // strip under the bar, past the screen's edge once the bar had gone —
        // and a WebView hanging past the window's visible bottom (the screen's
        // edge, or the keyboard's top) is given a SHORTER visual viewport by
        // Chromium, by exactly the overhang. The first scroll down then pans
        // that shorter viewport down inside the layout viewport before the
        // document itself moves, carrying everything `position: fixed` up the
        // screen with it: a site's header slid under the black status bar into
        // the curve, and every bottom bar rose off the line it had been put on
        // (auto.ria.com's nav, 29 CSS px too high). Nothing needs it: under a
        // raised toolbar the strip the bar covers is deeper than the curve's
        // reach, and with the bar gone the curve cannot read past the window's
        // bottom anyway (see PageLens.update's readBottom).
        val pageOverflowPx = with(density) { pageOverflow.roundToPx() }
        // The strip under the bar is page, but it is page nobody can see, so
        // it is not part of the tab's picture either — a preview is the page
        // box, exactly as it always was, with no toolbar baked into it.
        // Never negative for the capture: a preview is the page's box, and
        // with the keyboard up the WebView is smaller than that box, not
        // bigger — there is no strip under the toolbar to leave out.
        val capturedOverflowPx = pageOverflowPx.coerceAtLeast(0)
        SideEffect { vm.setPageOverflow(capturedOverflowPx) }
        // The same strip, but only while the bar is actually over it — this
        // one goes INTO the page, which moves its own bottom-anchored controls
        // up by it rather than leaving them under the toolbar until a scroll
        // happens to hide it (see PageBottomBar). Driven off the target rather
        // than the slide's own value: the page shifts in one step while the
        // bar takes TOOLBAR_SLIDE_MS to get there, so a bar coming up finds
        // the space already made for it, and one going away hands the strip
        // back while it is still covering it.
        // Nothing to make room for while the keyboard is up: the page now
        // ENDS above it, and the toolbar is behind it.
        // With the browser bar gone, every detected page bar gets the system
        // navigation inset. This is deliberately independent of theme: the
        // gesture handle must sit on a filled continuation of the site bar,
        // never over the site's controls.
        val pageChromeInsetPx = when {
            toolbarVisible && !imeUp -> capturedOverflowPx
            pageHasBottomBar && !imeUp ->
                with(density) { pageFloorInset.roundToPx() }
            else -> lensBarLiftPx
        }
        // Where the page runs under the navigation bar (no floor), the END of
        // the document still has to clear it, or its last row rests under the
        // gesture pill. Used by the page only while the inset above is 0.
        val pageEndPadPx = if (!imeUp && webBottomInset == 0.dp) {
            with(density) { navigationBarInset.roundToPx() }
        } else {
            0
        }
        SideEffect {
            vm.setPageChromeInset(pageChromeInsetPx, pageEndPadPx)
        }
        // The page itself reaches beneath the transparent status bar. Normal
        // flow is padded down by the same amount, so a document's first row is
        // safe; a fixed masthead deliberately remains at top:0 and therefore
        // supplies the real pixels behind the system icons. Keeping the view
        // below the bar made a transparent bar reveal the browser's black
        // fallback canvas instead of the page.
        val pageTopOverscanPx = if (lensDepthPx > 0f) lensOverscanPx else statusBarPx
        val pageTopContentInsetPx = if (lensDepthPx > 0f) lensOverscanPx else statusBarPx
        SideEffect {
            vm.setPageTopOverscan(pageTopOverscanPx)
            // Flow content and viewport-fixed site headers both need the safe
            // top inset. The header remains fixed, but rests immediately below
            // the system bar; the strip above it is painted from that header's
            // sampled colour (PageTopInset.setStrip). Leaving a fixed header at
            // top:0 puts its controls beneath the opaque strip and makes the
            // masthead appear to vanish.
            vm.setPageTopInset(pageTopContentInsetPx, pageTopContentInsetPx)
            vm.setPageTopStrip(statusStripPx, statusStripFadePx)
        }
        SideEffect {
            PageTopStrip.px = statusStripPx.toFloat()
        }
        // Previews are the page, not the page as the lens draws it: every
        // capture has the curve taken back out (see PageLens.unwarp).
        // Bars and the document's start paint their overscan in their own
        // colour, so the curve reading it pulls in the header, not the page.
        //
        // The fill covers the SYSTEM NAVIGATION BAR strip under a page's own
        // bottom bar once the toolbar has gone. The bar is lifted off the
        // gesture pill by pageChromeInsetPx, and the exposed strip is painted
        // from the site's bar itself (auto.ria.com's nav, for example), so it
        // reads as one continuous surface in every browser theme. Keyed to the
        // toolbar's target, like the inset, so both change together.
        val pageBarFillPx = when {
            lensDepthPx > 0f -> kotlin.math.ceil(lensDepthPx * maxOf(1f, PAGE_LENS_REACH)).toInt()
            pageHasBottomBar && !toolbarVisible && !imeUp ->
                with(density) { pageFloorInset.roundToPx() }
            else -> 0
        }
        // The top edge's: the lens's bend, or — with the strip — the whole
        // status bar, painted above a header in the header's colour.
        // No fill for the status bar strip: a fill is paint in the PAGE, and
        // the page is what a preview captures — the app draws the strip.
        val pageTopFillPx = if (lensDepthPx > 0f) kotlin.math.ceil(lensDepthPx * maxOf(1f, PAGE_LENS_REACH)).toInt() else 0
        SideEffect { vm.setPageBarFill(pageTopFillPx, pageBarFillPx) }
        androidx.compose.runtime.DisposableEffect(vm) {
            vm.captureUnwarp = { web, copy, factor, originY ->
                if (pageLensSupported) PageLens.peek(web)?.unwarp(copy, factor, originY)
            }
            onDispose { vm.captureUnwarp = null }
        }
        // What sits ABOVE the toolbar rather than under it — the find bar and
        // the password cards — still follows it in and out, so it rides the
        // bar down instead of hanging over a page with no bar beneath it.
        val chromeBottomInset =
            if (toolbarSettled && toolbarVisible) toolbarInset else pageFloorInset
        val chromeBottomInsetPxNow = with(density) { chromeBottomInset.roundToPx() }
        val readerLineInsetPxNow = with(density) { toolbarInset.roundToPx() }
        val readerFillInsetPxNow = with(density) { navigationBarInset.roundToPx() }
        val pageBottomInsetPxNow = with(density) { pageBottomInset.roundToPx() }
        SideEffect {
            pageBottomInsetPx = pageBottomInsetPxNow
            chromeBottomInsetPx = chromeBottomInsetPxNow
            readerLineInsetPx = readerLineInsetPxNow
            readerFillInsetPx = readerFillInsetPxNow
        }
        // True only once settled exactly at "fully open" (progress reaches
        // MIDPOINT_PROGRESS and stops there — see that constant for why it's
        // not 1f) — anywhere else in the [0, MAX_PROGRESS] range, including
        // the close half past it, WebViewHost is still the one live element
        // doing the animating.
        //
        // …and never while the tabs button is still being dragged. A drag
        // passes THROUGH MIDPOINT_PROGRESS on its way up, and one pixel of
        // travel is only a couple of REST_EPSILONs, so a slow finger lands
        // inside the band for a frame: the page handed off to the card in its
        // slot and came straight back to the finger — a one-frame snap into
        // place. "At rest" is a settle's end, which a live gesture never is.
        val atRestOpen = progressAtRestOpen && !tabsButtonDragging
        // Named once and reused by both the grid card (which hides its own
        // static title/thumbnail while this is true) and FloatingTabLabel
        // (which only renders while this is true) so the two can never both
        // be visible, or both be absent, for the same frame. A new tab's
        // entrance is the one case where neither should appear: `progress` is
        // parked at 0 throughout (so atRestOpen reads false) even though
        // nothing is animating between fullscreen and a card, and the tab it
        // would track has only just been appended to the far right of the row.
        val hideCurrentThumbnail = !newTabEntering && !atRestOpen && !expansionPreviewVisible

        // List mode does not move the page at ALL: the list is a bottom sheet
        // that slides up over it (see TabListSwitcher), and the page behind a
        // sheet is simply the page. So the live WebView, its static stand-in
        // and the label that tracks them all stay pinned at their fullscreen
        // resting values for as long as that mode is the switcher on screen.
        // `progress` itself keeps running underneath and is still what every
        // threshold, every handoff and the list's own fade are measured
        // against — it is only the PAGE that stops reading it.
        val pageShrinkSuppressed = tabViewMode == TabViewMode.List
        val effectiveProgress: () -> Float = { if (pageShrinkSuppressed) 0f else progress.value }
        val effectiveOffsetX: () -> Float = { if (pageShrinkSuppressed) 0f else offsetX.value }
        val effectiveOffsetY: () -> Float = { if (pageShrinkSuppressed) 0f else offsetY.value }

        // The switcher goes away the instant a new tab's entrance begins,
        // rather than staying mounted underneath it — the entering page fades
        // in from fully transparent, so a grid left behind it would be plainly
        // visible through the page for the first half of the animation. It
        // rises over the plain page background instead.
        val switcherMounted = switcherOpen || progressEngaged
        val tabManagerBg = com.yuku.browser.ui.theme.SwitcherBg

        Box(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                )
                .fillMaxSize()
                .background(if (switcherMounted || emptyTabManager) tabManagerBg else PageBg)
                // The switcher and empty-state canvases themselves extend
                // beneath the transparent status bar. Keep the TUI substrate
                // continuous across the containing box as well.
                .tuiSurfaceTextureIf(fine = switcherMounted)
                // The page lens's bezel behind the status bar, at the lens's
                // strength (read in draw), and only the status bar's height.
                .then(
                    if (!lensAvailable) Modifier
                    else Modifier.drawBehind {
                        drawRect(
                            Color.Black,
                            size = androidx.compose.ui.geometry.Size(size.width, statusBarPx.toFloat()),
                            alpha = lensStrength().coerceIn(0f, 1f),
                        )
                    }
                )
                // The page lens's bezel behind the status bar: black at the
                // lens's own strength, read per frame in the draw phase. It
                // used to be the Scaffold's container colour, switched by a
                // boolean that turned off the moment the switcher counted as
                // open — which it still does while a drag is held with the
                // page back at full size, and the view's own black overscan
                // strip then sat under a white bar. Drawn HERE, over this
                // box's ground and under its content: this box reaches up
                // under the status bar (its top padding is inside the glass
                // layer), so a strip drawn before it was painted over, and
                // the bar showed the page ground instead of black.
                // The lens's display corners, in SCREEN space rather than in
                // the page's shader, so a page zooming out of its card does
                // not carry them in: pinned under the status bar and on the
                // toolbar's top (or the screen's edge once it has gone), fading
                // in over the last stretch of the zoom (LENS_CORNER_START) —
                // by which point the page's own crop has eased to the same
                // arcs (applyShrinkTransform's fullCornerPx). Over the content.
                .then(
                    if (!lensAvailable) Modifier
                    else Modifier.drawWithContent {
                        drawContent()
                        // In step with the status bar strip and the toolbar's
                        // ground, which fade in at exactly this strength.
                        // Aero's sheets are glass: the corners would show
                        // through one (the bottom pair left floating where the
                        // toolbar was), so they go as a sheet rises and come
                        // back as it closes.
                        val sheetHide = if (aeroChrome) {
                            (sheetHeightAnim.value / (screenHeightPx * 0.25f)).coerceIn(0f, 1f)
                        } else 0f
                        val t = lensStrength().coerceIn(0f, 1f) * (1f - sheetHide)
                        if (t <= 0f) return@drawWithContent
                        // The superellipse's size (LENS_CORNER_EXTENT), not the circle's.
                        val r = lensCornerFor(lensHostView, lensDepthPx) * LENS_CORNER_EXTENT
                        if (r <= 0f) return@drawWithContent
                        val top = statusBarPx.toFloat()
                        // On the page's visible bottom edge, as the lens has it
                        // (WebViewHost's bottomCoverPx): the toolbar's top,
                        // sliding down to the screen's edge.
                        val bottom = size.height - toolbarInset.toPx() * (1f - toolbarSlide.value)
                        if (bottom - top < 2f * r) return@drawWithContent
                        // Soft only round the arcs, tapering to a hard edge
                        // where each meets a side or a bar (LensCornerBrush).
                        val soft = 6.dp.toPx().coerceAtMost(r * 0.5f)
                        val brush = lensCornerBrush?.brush(size.width, top, bottom, r, soft)
                            ?: return@drawWithContent
                        val w = size.width
                        // Just the four corner squares, not the whole page.
                        drawRect(brush, Offset(0f, top), androidx.compose.ui.geometry.Size(r, r), alpha = t)
                        drawRect(brush, Offset(w - r, top), androidx.compose.ui.geometry.Size(r, r), alpha = t)
                        drawRect(brush, Offset(0f, bottom - r), androidx.compose.ui.geometry.Size(r, r), alpha = t)
                        drawRect(brush, Offset(w - r, bottom - r), androidx.compose.ui.geometry.Size(r, r), alpha = t)
                    }
                )
                .aeroPageGlass(
                    paneInRoot = {
                        if (sheetHeightAnim.value > 0f) aeroSheetBounds
                        else if (!aeroDestinationBounds.isEmpty) aeroDestinationBounds
                        else if (aeroListOverlay && tabViewMode == TabViewMode.List) aeroListPane.rect()
                        else Rect.Zero
                    },
                    findInRoot = { aeroFindBounds },
                    paneFadeInRoot = {
                        if (sheetHeightAnim.value <= 0f && aeroListOverlay && tabViewMode == TabViewMode.List) aeroListPane.fade()
                        else Offset.Zero
                    },
                    paneAlpha = {
                        if (!aeroDestinationBounds.isEmpty) aeroDestinationPaneAlpha() else 1f
                    },
                    barInRoot = {
                        // The bar stops drawing once a sheet's top is over it.
                        val sheetH = sheetHeightAnim.value
                        if (lensChromeBlack || toolbarSlide.value >= 1f) Rect.Zero
                        else aeroBarCoordinates?.takeIf { it.isAttached && sheetH - sheetCornerPx < it.size.height }?.let { coordinates ->
                            // Use the same animation clock as the toolbar.
                            // Reading transformed coordinates here can see
                            // the preceding frame's graphics-layer position.
                            val rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
                            val height = coordinates.size.height.toFloat()
                            val left = coordinates.localToRoot(Offset.Zero).x
                            val top = rootHeight - height * (1f - toolbarSlide.value)
                            Rect(left, top, left + coordinates.size.width, top + height + with(density) { 30.dp.toPx() })
                        } ?: Rect.Zero
                    },
                )
                .frostedSheetGlass(
                    enabled = frostedSheets,
                    corner = frostSheetCorner,
                    paneInRoot = { if (sheetHeightAnim.value > 0f) aeroSheetBounds else Rect.Zero },
                    // The toolbar is square and runs to the screen's bottom
                    // edge, over the navigation bar; it has slid away while a
                    // sheet is up, where the sheet's own region takes over.
                    barInRoot = {
                        // Frosted exactly while the bar is drawn: until a
                        // sheet's top edge has risen over it.
                        val sheetH = sheetHeightAnim.value
                        if (lensChromeBlack || toolbarSlide.value >= 1f) Rect.Zero
                        else aeroBarCoordinates?.takeIf { it.isAttached && sheetH - sheetCornerPx < it.size.height }?.let { coordinates ->
                            val rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
                            val height = coordinates.size.height.toFloat()
                            val left = coordinates.localToRoot(Offset.Zero).x
                            val top = rootHeight - height * (1f - toolbarSlide.value)
                            // Nothing's loading ruler rises out of the bar
                            // in a band of the bar's own frosted fill.
                            val band = if (frostNothing) with(density) { RulerBand.height.toPx() } * RulerBand.reveal() else 0f
                            Rect(left, top - band, left + coordinates.size.width, rootHeight)
                        } ?: Rect.Zero
                    },
                    findInRoot = { aeroFindBounds },
                    findCorner = if (frostNothing) 16.dp else 24.dp,
                )
                // The status bar's padding goes INSIDE the glass layer: a
                // RenderEffect is cut to its layer's bounds, and the page hangs
                // above this box under the status bar (PageTopInset.setStrip) —
                // with the padding outside, the strip was cropped away for as
                // long as the toolbar's glass was on, i.e. at every page's top.
                .padding(top = padding.calculateTopPadding())
                .onGloballyPositioned { boxCoordinates = it }
        ) {
            if (tabs.isEmpty()) {
                // Centered in the space above the bar, like everything else
                // that isn't the page itself.
                // The ground itself runs under the bar (see EmptyState).
                EmptyState(
                    private = privateMode,
                    topInset = padding.calculateTopPadding(),
                    bottomInset = toolbarInset,
                )
            } else {
                if (switcherMounted) {
                    // Only actually animates when tabViewMode itself changes
                    // (a toggle-tap) — opening the switcher for the first
                    // time in whichever mode it's already set to renders
                    // straight in, no zoom/fade.
                    AnimatedContent(
                        targetState = tabViewMode,
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(180)))
                        },
                        // Unlike the page, the switcher must clear the
                        // toolbar — its cards are meant to sit above the bar,
                        // not behind it, and it force-shows the bar anyway.
                        modifier = Modifier
                            .fillMaxSize()
                            // The page is composed AFTER the switcher and so
                            // draws over it, which is right for the grid (the
                            // page shrinks INTO it, in front of it the whole
                            // way) and exactly wrong for the list, which is an
                            // overlay ON the page: without this the ground's
                            // fade and the rows' entrance played underneath a
                            // fullscreen copy of the page and the first thing
                            // anyone saw was that copy being taken away, with
                            // the list already fully arrived behind it.
                            // Ordering, not composition order — the WebView
                            // must stay exactly where it is (see WebViewHost).
                            .zIndex(if (pageShrinkSuppressed) 1f else 0f)
                            .padding(bottom = toolbarInset)
                            // Arrives with the space it belongs to, exactly as
                            // the page below does — the switcher is the other
                            // thing a space can have on screen.
                            .graphicsLayer { alpha = spaceEnterAlpha() },
                        label = "tabViewMode",
                    ) { mode ->
                        if (mode == TabViewMode.List) {
                            if (!aeroListOverlay) TabListSwitcher(
                                tabs = tabs,
                                currentId = currentId,
                                sessionKey = switcherSession,
                                // Not `progress`: the list fades on its own
                                // clock, and a tabs-button drag has committed
                                // to nothing until it is released — which is
                                // exactly when this flag turns true.
                                open = switcherOpen,
                                onSelect = ::selectTabFromCard,
                                onClose = vm::closeTab,
                                onCloseAll = {
                                    vm.closeAllTabs()
                                    closeSwitcher()
                                    openNewTabSheet()
                                },
                                // A tap on the dimmed page above the sheet,
                                // or the sheet thrown down, is a dismissal —
                                // the same one the toolbar's tabs button
                                // performs.
                                onDismiss = ::closeSwitcher,
                                onCurrentRowPositioned = { currentCardCoordinates = it },
                                onCoversStatusBar = { listCoversStatusBar = it },
                                paneBounds = if (frostedSheets) aeroListPane else null,
                            )
                        } else {
                            TabSwitcher(
                                tabs = tabs,
                                currentId = currentId,
                                sessionKey = switcherSession,
                                floatingTabId = floatingTabId,
                                freezeRowForExpansion = expandingTabId != null,
                                progress = switchProgressOf,
                                // While the live WebView overlay is up (see below), the
                                // current tab's own card slot stays visually empty
                                // underneath it rather than also drawing a thumbnail.
                                hideCurrentThumbnail = hideCurrentThumbnail,
                                // The switcher's box stops above the toolbar
                                // but the page it's animating doesn't — see
                                // the card aspect ratio in TabSwitcher.
                                onSelect = ::selectTabFromCard,
                                onClose = vm::closeTab,
                                onCloseAll = {
                                    vm.closeAllTabs()
                                    closeSwitcher()
                                    openNewTabSheet()
                                },
                                onCurrentCardPositioned = { currentCardCoordinates = it },
                                closingTabId = closingTabId,
                                onCloseRecentred = {
                                    closeRecentred.value?.complete(Unit)
                                },
                                onCenteredCardPositioned = { id, coords ->
                                    centeredTabId = id
                                    centeredCardCoordinates = coords
                                },
                        // Swipe-down-to-open on a card is the mirror of the
                        // tabs button's swipe-up-to-close: the tab becomes
                        // current immediately, positioned exactly at its
                        // switcher slot, then this drag interpolates position
                        // and scale from there toward fullscreen as the
                        // finger moves down.
                        onExpandDragStart = { id, cardCoordinates ->
                            expandingTabId = id
                            // Not vm.selectTab(id) yet — that also reorders
                            // the tab to the end of the list (see its own
                            // comment), which should only happen once this
                            // gesture actually commits to opening (below) or
                            // via an explicit tap/select, not just from
                            // touching a card to preview it. floatingTabId
                            // already resolves to `id` via expandingTabId
                            // above, so the live WebView still floats over
                            // the right tab during the drag regardless.
                            switcherOpen = true
                            scope.launch { progress.stop() }
                            scope.launch { offsetX.stop() }
                            scope.launch { offsetY.stop() }
                            // The touched card is already measured and is the
                            // exact visual center of this gesture. Use it
                            // immediately instead of waiting for selectTab's
                            // recomposition, which used to briefly apply the
                            // old active tab's target and cause a jerk.
                            val target = targetOffsetFor(cardCoordinates)
                            expansionTarget = target
                            // The one-frame "show the static card instead of
                            // the live WebView" preview only matters when
                            // offsetX/Y are actually about to jump somewhere
                            // new — e.g. the very first touch on a card,
                            // where WebViewHost hasn't been positioned yet.
                            // Reversing back into an expand right after an
                            // abandoned close (see onExpandDragAbandon, which
                            // already snapped offsetX/Y to this exact same
                            // card's target) needs no such jump, so skip the
                            // flash there — firing it unconditionally on
                            // every reversal is what made rapidly wobbling a
                            // drag near the commit threshold visibly flicker.
                            val epsilonPx = 1f
                            val alreadyAtTarget = target != null &&
                                kotlin.math.abs(offsetX.value - target.x) < epsilonPx &&
                                kotlin.math.abs(offsetY.value - target.y) < epsilonPx
                            if (!alreadyAtTarget) {
                                expansionPreviewVisible = true
                                scope.launch {
                                    withFrameNanos { }
                                    expansionPreviewVisible = false
                                }
                            }
                            target?.let {
                                scope.launch {
                                    offsetX.snapTo(it.x)
                                    offsetY.snapTo(it.y)
                                }
                            }
                        },
                        onExpandDrag = { fraction ->
                            // fraction 0 = at the card (fully open, i.e.
                            // MIDPOINT_PROGRESS — see that constant), 1 =
                            // fully expanded to fullscreen (progress 0).
                            scope.launch { progress.snapTo(MIDPOINT_PROGRESS * (1f - fraction)) }
                            val target = expansionTarget ?: targetOffset()
                            scope.launch { offsetX.snapTo((target?.x ?: 0f) * (1f - fraction)) }
                            scope.launch { offsetY.snapTo((target?.y ?: 0f) * (1f - fraction)) }
                        },
                        onExpandDragEnd = { fraction ->
                            val shouldExpand = fraction > 0.3f
                            switcherOpen = !shouldExpand
                            if (shouldExpand) {
                                // Only now does the gesture actually commit
                                // to opening this tab — reorder it to the
                                // rightmost slot at this point, not back when
                                // the drag merely started.
                                val openingId = expandingTabId
                                openingId?.let(vm::selectTab)
                                scope.launch { offsetX.animateTo(0f, tween(240)) }
                                scope.launch { offsetY.animateTo(0f, tween(240)) }
                                // expandingTabId has to survive the whole
                                // zoom-to-fullscreen (it's what keeps the live
                                // WebView floating and the grid card hidden),
                                // but it MUST be cleared once that's done —
                                // leaving it set makes liveVisible below stay
                                // true forever, which parks the interactive
                                // WebView over the switcher's centered slot on
                                // every subsequent open: it swallows every
                                // touch meant for the cards (its hit area is
                                // fullscreen no matter what scale it's drawn
                                // at), doesn't follow the row's scroll, and
                                // strands its outline ring mid-screen.
                                scope.launch {
                                    try {
                                        progress.animateTo(0f, arrive(240))
                                    } finally {
                                        if (expandingTabId == openingId) expandingTabId = null
                                    }
                                }
                            } else {
                                settleExpandingCardBack(240)
                            }
                            expansionTarget = null
                            expansionPreviewVisible = false
                        },
                        onExpandDragCancel = {
                            expansionTarget = null
                            expansionPreviewVisible = false
                            switcherOpen = true
                            settleExpandingCardBack(200)
                        },
                        // The card reversed straight into closing without
                        // letting go first — unlike onExpandDragCancel (which
                        // eases back to the switcher because the gesture is
                        // simply over), this needs WebViewHost gone THIS
                        // frame: TabSwitcher's own close overlay is about to
                        // represent this same card immediately, and animating
                        // this one back out over its own tween would overlap
                        // with it, which is what "duplicated" it.
                        onExpandDragAbandon = {
                            expansionTarget = null
                            expandingTabId = null
                            expansionPreviewVisible = false
                            switcherOpen = true
                            scope.launch { progress.snapTo(MIDPOINT_PROGRESS) }
                            val target = targetOffset()
                            scope.launch { offsetX.snapTo(target?.x ?: 0f) }
                            scope.launch { offsetY.snapTo(target?.y ?: 0f) }
                        },
                            )
                        }
                    }
                }

                // Composed unconditionally whenever there's a current tab — it
                // never gets torn down and rebuilt, so the underlying WebView is
                // never reparented into a fresh container. What used to gate
                // this composable's presence (hiding it once settled into the
                // card, so it wouldn't swallow taps meant for the switcher —
                // its Compose layout bounds are always fullscreen, regardless of
                // the graphicsLayer scale it's drawn at) is now just the
                // `visible` flag below, toggling the same AndroidView's own
                // Android-side visibility. That's cheap and instant either way,
                // but critically it's the SAME element the whole time instead of
                // a fresh one handed off to — going from settled, into another
                // drag, and back, there's nothing to visibly duplicate.
                floatingTab?.let { tab ->
                    val box = boxCoordinates
                    // The live page is only ever what the user actually SEES at
                    // full screen. Every intermediate state of every switcher
                    // gesture — the tabs button's own drag AND a card's
                    // swipe-up/swipe-down — is drawn from the static
                    // AnimatedThumbnailHost below instead.
                    //
                    // Showing live content mid-gesture is what made a reversed
                    // swipe flash blank: each time a card's drag flips between
                    // closing and expanding, this host is handed a different
                    // tab (floatingTabId follows expandingTabId), and switching
                    // tabs reparents that tab's WebView into this container —
                    // Chromium re-attaches and re-rasters its whole surface,
                    // several frames of white before the page paints. Worst on
                    // any tab that wasn't already the hosted one, which is why
                    // it was near-guaranteed on the previous tab and only
                    // occasionally catchable on the current one.
                    //
                    // The exception is the list, which is an overlay ON the
                    // page rather than a screen replacing it (see
                    // TabListSwitcher): what shows through above the rows has
                    // to be the page at the page's own resolution, and a
                    // thumbnail is a few hundred pixels wide blown up to the
                    // whole screen. The gesture reasoning above still holds
                    // for everything else here, which is why this is
                    // `switcherOpen` and not `switcherMounted`: a tabs-button
                    // DRAG is mid-gesture by definition and keeps the static
                    // path (and the handoff cover on release), and a row tap
                    // clears switcherOpen in the same frame it changes the
                    // current tab, so the reparent that follows is covered
                    // exactly as it was.
                    val liveVisible = progressAtZero ||
                        (pageShrinkSuppressed && switcherOpen)
                    // …but the WebView still has to be attached, measured and
                    // rendering throughout an expansion, so that by the time
                    // it IS revealed it has already painted. A GONE view is
                    // never measured (so it can't warm up at all, and would
                    // just move the same white flash to the end of the
                    // gesture), hence a separate flag: while this is set the
                    // view stays VISIBLE to Android and is simply drawn at
                    // alpha 0, behind the static thumbnail covering it.
                    val liveWarming = expandingTabId != null
                    // There is only something to hand off FROM once the live
                    // page has actually been away — shrunk into the switcher,
                    // or reparented mid-gesture. On a cold start it never was:
                    // liveVisible is true from the first composition, and
                    // covering that with the thumbnail restored from disk
                    // flashes the last session's picture of the page over the
                    // page for a few frames before the real load appears.
                    var handoffArmed by remember { mutableStateOf(false) }
                    // Cancelled (and so reset via its `finally`) the moment
                    // liveVisible goes false again, or if this whole block
                    // leaves composition — a cover that outlived its handoff
                    // would freeze the page under a still image.
                    LaunchedEffect(liveVisible, tab.id) {
                        if (!liveVisible) {
                            handoffArmed = true
                            return@LaunchedEffect
                        }
                        if (!handoffArmed) return@LaunchedEffect
                        handoffCover = true
                        try {
                            repeat(HANDOFF_COVER_FRAMES) { withFrameNanos { } }
                        } finally {
                            handoffCover = false
                        }
                    }
                    // Everything that represents this tab moves as one for a
                    // new tab's entrance — the page, the cover thumbnail that
                    // hides its first blank frames, and any error state — so
                    // the transform lives on a wrapper here rather than being
                    // threaded through each of them. Fullscreen and untouched
                    // (identity transform) at every other time.
                    val pageFallback = PageBg
                    val pageGround = remember(tab.thumbnailFull, tab.thumbnail, pageFallback) {
                        val image = tab.thumbnailFull ?: tab.thumbnail
                        image?.takeUnless { it.isRecycled }?.let {
                            Color(it.getPixel(it.width / 2, it.height - 1))
                        } ?: pageFallback
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // The tab list sheet's frost. On the PAGE's own
                            // layer, not the shared box above: the list is
                            // composed inside that box, and blurring it there
                            // would blur the list too.
                            // A RenderEffect is cut to its layer's bounds and
                            // the page hangs above this box under the status
                            // bar, so the layer is laid out up by the strip and
                            // its content padded back down — otherwise the bar
                            // showed the app's ground over a short list.
                            .then(
                                if (!(frostedSheets && tabViewMode == TabViewMode.List) || statusStripPx <= 0) Modifier
                                else Modifier.layout { measurable, constraints ->
                                    val up = statusStripPx
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minHeight = constraints.minHeight + up,
                                            maxHeight = constraints.maxHeight + up,
                                        ),
                                    )
                                    layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
                                }
                            )
                            .frostedSheetGlass(
                                enabled = frostedSheets && tabViewMode == TabViewMode.List,
                                corner = frostSheetCorner,
                                paneInRoot = { aeroListPane.rect() },
                            )
                            .then(
                                if (!(frostedSheets && tabViewMode == TabViewMode.List) || statusStripPx <= 0) Modifier
                                else Modifier.padding(top = with(density) { statusStripPx.toDp() })
                            )
                            // The page lens's bezel carried on below the status
                            // bar — solid for a bend's depth, then easing out —
                            // at the lens's strength. HERE, on the page's own
                            // layer, which draws after the switcher: a zooming
                            // preview has not reached the bar yet, and in the
                            // gap the tab's own card was still fading out behind
                            // it, a grey band the preview's top fade read as a
                            // dark line against until the tab landed. Under the
                            // page's ground, so the live page covers it.
                            .then(
                                if (!lensAvailable) Modifier
                                else Modifier.drawBehind {
                                    val a = lensStrength().coerceIn(0f, 1f)
                                    if (a <= 0f) return@drawBehind
                                    drawRect(
                                        Color.Black,
                                        size = androidx.compose.ui.geometry.Size(size.width, lensDepthPx),
                                        alpha = a,
                                    )
                                    drawRect(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0f to Color.Black, 1f to Color.Transparent,
                                            startY = lensDepthPx, endY = 2f * lensDepthPx,
                                        ),
                                        topLeft = Offset(0f, lensDepthPx),
                                        size = androidx.compose.ui.geometry.Size(size.width, lensDepthPx),
                                        alpha = a,
                                    )
                                }
                            )
                            // Painted before the inset is taken off, so the
                            // strip the toolbar sits over reads as page rather
                            // than as a hole for the frames between the bar
                            // starting to move and the page reflowing. Only
                            // while the page IS the screen — this Box covers
                            // the whole of it, so an unconditional background
                            // would paint over the switcher behind it.
                            .grainedBackground(
                                color = if (liveVisible) pageGround else Color.Transparent,
                                // Zero, not just a transparent color: the
                                // grain is drawn whatever the fill is, and
                                // this Box covers the whole screen — a wash
                                // left on would speckle the switcher behind
                                // it for the same reason the fill has to go.
                                // Nothing's dot field is off here outright: this
                                // ground is the PAGE's own colour, and it shows
                                // through the navigation bar strip under a site's
                                // bottom bar (auto.ria.com) — a printed grid there
                                // reads as chrome leaking under the page.
                                strength = if (liveVisible && !com.yuku.browser.ui.theme.LocalNothing.current) 1f else 0f,
                            )
                            .padding(bottom = pageBottomInset)
                            .graphicsLayer {
                                // A space being opened fades its page in (see
                                // spaceEnterAlpha); 1f at every other time, so
                                // this multiplies with the new tab's own
                                // entrance rather than either overriding it.
                                alpha = spaceEnterAlpha()
                                if (!newTabEntering) return@graphicsLayer
                                translationY = size.height * NEW_TAB_ENTER_RISE_FRACTION *
                                    (1f - newTabEnterSlide.value)
                                alpha *= newTabEnterFade.value
                            },
                    ) {
                    WebViewHost(
                        vm = vm,
                        tab = tab,
                        overflowBottomPx = pageOverflowPx,
                        overscanTopPx = pageTopOverscanPx,
                        pageTopContentInsetPx = pageTopContentInsetPx,
                        statusBarHeightPx = statusBarPx,
                        stripAbovePx = statusStripPx.toFloat(),
                        stripFadePx = statusStripFadePx.toFloat(),
                        statusBarBlur = statusBarBlur,
                        lensDepthPx = lensDepthPx,
                        lensBarLiftPx = lensBarLiftPx,
                        pageChromeInsetPx = pageChromeInsetPx,
                        pageEndPadPx = pageEndPadPx,
                        pageTopFillPx = pageTopFillPx,
                        pageBottomFillPx = pageBarFillPx,
                        progress = effectiveProgress,
                        targetRect = targetRect,
                        offsetX = effectiveOffsetX,
                        offsetY = effectiveOffsetY,
                        settled = progressAtZero,
                        visible = liveVisible,
                        warming = liveWarming,
                        toolbarSlide = remember { { toolbarSlide.value } },
                        onScroll = { deltaY, scrollY, userDriven ->
                            // Any scroll invalidates this tab's last preview,
                            // and the stillness after one is when the next
                            // copy gets taken.
                            vm.notePageMoved(tab.id)
                            vm.notePageScrolled(tab.id)
                            onWebViewScroll(deltaY, scrollY, userDriven)
                        },
                    )
                    // The static counterpart of the live WebView above — shown
                    // whenever the switcher UI is up but nothing is live (see
                    // liveVisible), so the tabs-button open/close animation
                    // shrinks/grows the last-captured image instead of the
                    // real page.
                    AnimatedThumbnailHost(
                        tab = tab,
                        fullGrab = com.yuku.browser.ui.theme.LocalAero.current || com.yuku.browser.ui.theme.LocalFrosted.current,
                        lensDepthPx = lensDepthPx,
                        stripAbovePx = statusStripPx.toFloat(),
                        stripFadePx = statusStripFadePx.toFloat(),
                        stripReports = vm.statusStrips,
                        statusBarBlur = statusBarBlur,
                        progress = effectiveProgress,
                        targetRect = targetRect,
                        offsetX = effectiveOffsetX,
                        offsetY = effectiveOffsetY,
                        // !atRestOpen too — once settled at the grid card's
                        // own resting slot there's nothing left to animate,
                        // and this needs to actually go away rather than sit
                        // there at shrinkProgress 1 (same "stuck ring" bug
                        // this whole thing exists to avoid, just on the
                        // static path instead of the live one).
                        //
                        // …except in list mode, where it stays for as long as
                        // the switcher does: the list is an OVERLAY, drawn
                        // over the page it was opened from and fading out to
                        // transparent above its topmost row (see
                        // TabListSwitcher's ground), so there has to be a page
                        // still there to see through it. Pinned fullscreen the
                        // whole time, like everything else this mode
                        // suppresses the shrink of, and it is the static copy
                        // rather than the live view on purpose — a WebView
                        // behind a half-transparent surface is a live
                        // compositing cost and a touch target, and the page is
                        // frozen under there anyway.
                        visible = (
                            !liveVisible && switcherMounted &&
                                (pageShrinkSuppressed || !atRestOpen)
                            ) || handoffCover,
                    )
                    // A fresh load without usable saved state still needs
                    // time to fetch and render. Successful restores skip
                    // this cover so the cached page is visible immediately.
                    // This holds the preview saved on the way out over it
                    // until the real page has painted. Gated on liveVisible
                    // so it never fights the switcher for the screen — every
                    // other state already draws the same image, from the host
                    // above, and at the same crop, so the moment this takes
                    // over is not a moment the picture moves.
                    PageCover(
                        tab = tab,
                        lensDepthPx = lensDepthPx,
                        visible = tab.id in coveredTabIds && liveVisible,
                        onGiveUp = { vm.dismissPageCover(tab.id) },
                    )
                    // The switcher's own card for this tab has no label of its
                    // own right now (see hideCurrentThumbnail) — this one tracks
                    // the live WebView instead, so the name and favicon move
                    // and settle together with the content, not separately.
                    // Unlike WebViewHost this has no native view to preserve, so
                    // it's fine for it to stay conditionally composed. Tied to
                    // hideCurrentThumbnail (not just !atRestOpen) so this and the
                    // grid card's own static label are always exact opposites —
                    // this also now covers a card's own swipe-down-to-expand drag,
                    // which used to show neither (the card's own label hides via
                    // hideCurrentThumbnail the moment the drag starts, and this
                    // was separately gated off by expandingTabId being set).
                    // Never in list mode: nothing is travelling to a card
                    // there — the row draws its own label, and this one would
                    // be a second copy of it sitting over the page at
                    // fullscreen size for the length of the fade.
                    if (hideCurrentThumbnail && !pageShrinkSuppressed &&
                        targetRect != null && box != null
                    ) {
                        FloatingTabLabel(
                            tab = tab,
                            progress = effectiveProgress,
                            targetRect = targetRect,
                            offsetX = effectiveOffsetX,
                            offsetY = effectiveOffsetY,
                            fullWidthPx = box.size.width.toFloat(),
                            // The page's height, not the box's — this label
                            // tracks the page's transform, and offsetY is now
                            // measured from the page's center.
                            fullHeightPx = (box.size.height - pageBottomInsetPx).toFloat(),
                        )
                    }
                    // Only while actually browsing fullscreen — not mid-shrink
                    // into the switcher, where the live WebView is animating
                    // away and a static overlay would just visibly detach
                    // from it.
                    // Faded both ways once there (see FadingWebErrorState);
                    // leaving fullscreen still cuts it, and a different tab
                    // is a different screen rather than this one fading.
                    if (progressAtZero) {
                        key(tab.id) {
                            // Up under the status bar like the page it stands
                            // in for (PageTopInset.setStrip) — otherwise the
                            // engine's own page shows in that strip, a white
                            // band over the error screen's ground.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        val up = statusStripPx
                                        val height = constraints.maxHeight + up
                                        val placeable = measurable.measure(
                                            constraints.copy(minHeight = height, maxHeight = height),
                                        )
                                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -up) }
                                    }
                            ) {
                                FadingWebErrorState(tab.loadFailed, tab.loadError)
                            }
                        }
                    }
                    // The list's own handoff (see selectTabFromRow), over
                    // the page and its error state for the same reason the
                    // flip below is.
                    ListSwitchFade(
                        outgoing = listSwitchFromId?.let { id -> tabs.firstOrNull { it.id == id } },
                        alpha = { listSwitchFade.value },
                    )
                    // Last child of the page box, so the flip's two images
                    // cover the live page (and its error state) rather than
                    // sliding underneath them.
                    QuickSwitchOverlay(
                        outgoing = quickSwitchFromId?.let { id -> tabs.firstOrNull { it.id == id } },
                        incoming = quickSwitchToId?.let { id -> tabs.firstOrNull { it.id == id } },
                        progress = { quickSwitchProgress.value },
                        direction = quickSwitchDirection,
                    )
                    }
                }
            }
        }
    }

    // How much article is left, and a ground for it to stand on.
    //
    // Drawn on the app's chrome rather than inside the reader, which is where
    // it started: the rule has to sit on the TOOLBAR'S TOP EDGE — the loading
    // line's own edge — and then near the navigation bar's line once the
    // toolbar hides, and neither of those is a height the page can be told.
    // The page's viewport bottom is not the screen's, and the two differ by
    // whatever the WebView is hanging past its box that frame. So the page
    // reports the one thing only it knows (how far down the article it is)
    // and this places it.
    //
    // BEFORE the scrim and the sheet on purpose, so it is UNDER them rather
    // than gated off by them: `sheet` goes null the instant a sheet is
    // dismissed and the sheet then takes another animation to leave, so a
    // rule conditioned on it appeared over a sheet that was still on its way
    // out. Under the sheet it is simply uncovered as the sheet goes, which is
    // what every other thing on the page does.
    //
    // The switcher is a different matter and still a gate: there is no
    // article on screen there at all.
    if (readerActive && !switcherOpen) {
        ReaderProgressChrome(
            vm = vm,
            slide = remember { { toolbarSlide.value } },
            linePx = readerLineInsetPx,
            fillPx = readerFillInsetPx,
        )
    }

    // Aero's tab list, over the whole screen — see aeroListOverlay.
    if (aeroListOverlay && tabViewMode == TabViewMode.List &&
        (switcherOpen || progressEngaged) && tabs.isNotEmpty()
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = spaceEnterAlpha() }) {
            TabListSwitcher(
                tabs = tabs,
                currentId = currentId,
                sessionKey = switcherSession,
                open = switcherOpen,
                onSelect = ::selectTabFromCard,
                onClose = vm::closeTab,
                onCloseAll = {
                    vm.closeAllTabs()
                    closeSwitcher()
                    openNewTabSheet()
                },
                onDismiss = ::closeSwitcher,
                onCurrentRowPositioned = { currentCardCoordinates = it },
                bottomInset = listToolbarInset,
                paneBounds = aeroListPane,
            )
        }
    }

    // Hand-rolled instead of Material3's ModalBottomSheet: that component
    // hardcodes `.imePadding()` on its root, which shoves the whole sheet up
    // above the keyboard the instant it appears. We want the opposite for
    // NewTabSheet/MenuSheet's text fields — the keyboard should overlay the
    // sheet in place, not shift it (and everything behind it) upward.
    AnimatedVisibility(
        visible = sheet != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Lighter over frosted glass: the scrim is under the sheet
                // too, and at full strength it muddies the fill.
                .background(Color.Black.copy(alpha = when {
                    com.yuku.browser.ui.theme.LocalAero.current -> 0.12f
                    frostedSheets -> 0.20f
                    else -> 0.32f
                }))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { closeSheet() },
        )
    }
    val sheetOverhang = if (com.yuku.browser.ui.theme.LocalAero.current) {
        com.yuku.browser.ui.theme.aeroCornerOf(SHEET_CORNER)
    } else SHEET_CORNER
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            // Under Aero, the toolbar's glass exactly (see the Column below).
            // Translucent sheets: the same ground, tinted and see-through,
            // over the page frosted by `frostedSheetGlass`.
            color = if (frostedSheets) {
                com.yuku.browser.ui.theme.frostedSheetFill(
                    BarBg, com.yuku.browser.ui.theme.AccentColor, com.yuku.browser.ui.theme.LocalNothing.current,
                )
            } else BarBg,
            // Uniform on all four corners, NOT topStart/topEnd only — a
            // RoundedCornerShape whose corners aren't all equal can't be
            // expressed as a RenderNode outline, so Compose falls back to
            // clipping with a Path, which forces the entire sheet through an
            // offscreen buffer on every draw. With the sheet's size changing
            // each frame of the open/close/drag animation, that outline was
            // also being rebuilt from scratch every frame: it measured at 8–26ms
            // of `Record View#draw()` per frame, against an 8.3ms budget, and
            // dropped to ~1ms the moment the corners were made uniform. The
            // bottom two corners are simply kept off-screen instead — see
            // SHEET_CORNER and the layout modifier below.
            shape = specialCorner(SHEET_CORNER),
            modifier = Modifier
                // Full-bleed upright, half the screen turned sideways — see
                // sheetWidth. The Box around this is BottomCenter-aligned, so
                // the narrower surface centres itself with no offset here.
                .then(sheetWidth())
                // And under Aero it is a pane of glass held over the page:
                // the sheet's own colour is already translucent (see
                // `AeroLightScheme`), and this is the light on it — the
                // gloss across its top, the refraction inside its rim, the
                // bounce along the bottom. The corner is handed over
                // explicitly because a draw modifier cannot ask the surface
                // what shape it was given, and this one is `specialCorner`'d
                // like everything else.
                // The stretch, applied to the SURFACE rather than to its
                // contents, so the sheet's own canvas — background, hairline
                // top edge, rounded corners — is pulled along with the rows
                // instead of holding still while they move inside it.
                // Anchored at the bottom of the screen, the edge the sheet is
                // pinned to, so the growth all appears at the top, which is
                // the end the finger is pulling toward.
                //
                // Outside the layout modifier below on purpose: there the
                // node's height is the sheet's own, not the taller box its
                // contents are measured in, so the anchor is exactly the
                // bottom edge. And read here rather than in composition,
                // like every other per-frame value in this file — a
                // graphicsLayer block runs in the draw phase, so a stretch
                // invalidates nothing but the layer.
                .graphicsLayer {
                    val stretch = stretchFraction(sheetStretchPull.value)
                    if (stretch > 0f) {
                        scaleY = 1f + stretch
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                }
                // A sheet is a window, and under 98 a window has an edge: the
                // raised bevel that every panel in that system is told from
                // its background by. Keep it INSIDE the stretch layer above,
                // so its top and side bands transform with the sheet edge
                // instead of being redrawn at the unstretched layout bounds.
                // Only three of its four sides are ever on screen — the fourth
                // is past the bottom of the window with the two rounded
                // corners this surface is already hiding down there.
                .bevel98If()
                // Read in the LAYOUT phase, not during composition. Reading
                // sheetHeightAnim.value straight into a .height() modifier
                // reads it in BrowserScreen's own composition scope, so every
                // single frame of the open/close/drag animation invalidated
                // this whole (very large) composable: the Scaffold, the
                // toolbar, the switcher, both sheets' full contents, and —
                // worst of all — WebViewHost's AndroidView update block, which
                // does real View-world work on each run. That was enough to
                // push frame times to ~14ms on a 120Hz display (8.3ms budget),
                // i.e. the sheet visibly animating at a fraction of the screen's
                // refresh rate. Measuring to the same height from here produces
                // the identical layout while only ever invalidating layout.
                .layout { measurable, constraints ->
                    val h = sheetHeightAnim.value.roundToInt().coerceIn(0, constraints.maxHeight)
                    // Measured one corner-radius TALLER than it reports itself
                    // to be. The parent aligns this node's own (h-tall) box to
                    // the bottom of the screen, so the surplus hangs off the
                    // bottom edge — which is exactly where the two bottom
                    // corners then round, out of sight. The Column inside
                    // subtracts the same amount back as padding, so the
                    // content box is identical to a plain h-tall sheet's.
                    val overhang = sheetOverhang.roundToPx()
                    // The contents are measured at the settled height, not the
                    // animated one, so that growing the sheet is placement-only
                    // — no re-measure, no newly-composed LazyColumn rows, on any
                    // frame of the open/expand animation. The `max` covers a
                    // live drag past the current target, where the finger really
                    // is asking for more content than has been measured.
                    //
                    // Whenever that exceeds h the surplus simply continues past
                    // the node's reported bottom edge — which sits at the bottom
                    // of the screen, so it is clipped away by the window. The
                    // contents are top-anchored either way, meaning the rows on
                    // screen land at identical positions; the difference is only
                    // that the ones below the fold already exist.
                    val contentH = maxOf(h, sheetContentHeightPx.roundToInt())
                        .coerceIn(0, constraints.maxHeight) + overhang
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = contentH, maxHeight = contentH),
                    )
                    // Measured-but-not-placed while closed: the contents stay
                    // composed and laid out, ready for the next open, without
                    // costing a draw pass for something entirely off-screen.
                    layout(placeable.width, h) { if (h > 0) placeable.place(0, 0) }
                },
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    // Inside Surface's clip and layout: the rim and material
                    // now use the same measured bounds and stretch as the sheet.
                    .then(
                        if (com.yuku.browser.ui.theme.LocalAero.current) Modifier
                            .aeroDroplet(SHEET_CORNER, openBottom = true)
                            .grain(AERO_BAR_GRAIN)
                        else Modifier
                    )
                    // And under the TUI, the tube's raster behind the rows and
                    // the phosphor bloom around them.
                    .tuiCrtIf()
                    .tuiBloomIf()
                    .onGloballyPositioned { coordinates ->
                        aeroSheetBounds = Rect(
                            coordinates.localToRoot(Offset.Zero),
                            coordinates.localToRoot(Offset(
                                coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
                            )),
                        )
                    }
                    // Gives back the off-screen overhang the layout modifier
                    // above added for the bottom corners, so content still
                    // ends where it always did.
                    .padding(bottom = sheetOverhang)
                    .navigationBarsPadding()
                    // Scrollable descendants (NewTabSheet's history list,
                    // MenuSheet's own verticalScroll) dispatch here first —
                    // see sheetNestedScrollConnection above: a swipe grows or
                    // shrinks the sheet before the list scrolls at all, and
                    // only scrolls the list once the sheet is fully expanded
                    // or back at its floor.
                    .nestedScroll(sheetNestedScrollConnection)
                    // Plain drag, for the non-scrollable parts of each sheet
                    // (the search/address bar row, MenuSheet's tiles) — taps
                    // aren't affected, this only ever fires once touch slop
                    // is crossed. Drags all the way to the top expand the
                    // sheet fullscreen; dragging back down returns it to the
                    // 2/3-screen rest height (taking the keyboard down with
                    // it); dragging further below that dismisses it.
                    .pointerInput(sheetRestHeightPx, sheetCeilingPx) {
                        // Tracked by hand: detectVerticalDragGestures reports
                        // the deltas but not the speed they arrived at, and
                        // the speed is what tells a flick from a slow drag —
                        // see settleSheet.
                        val velocity = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragEnd = {
                                settleSheet(velocity.calculateVelocity().y)
                                velocity.resetTracking()
                                releaseSheetStretch()
                            },
                            onDragCancel = {
                                velocity.resetTracking()
                                sheetExpanded = false
                                releaseSheetStretch()
                                scope.launch { sheetHeightAnim.animateTo(sheetRestHeightPx, arrive(SNAP_BACK_MS)) }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                velocity.addPosition(change.uptimeMillis, change.position)
                                // Either direction, on the first pixel past
                                // touch slop. Up is "reveal more of the list";
                                // down is "I am done with this sheet" — and
                                // both are answered by the keyboard going
                                // away. Down matters most: the collapse can
                                // end in a dismissal, and the page underneath
                                // is laid out to END at the top of the
                                // keyboard (see pageOverflow/imeUp), so a
                                // keyboard still up as the sheet clears leaves
                                // an unpainted strip where it was. Starting
                                // the hide here gives the page its reflow
                                // while the sheet is still on its way down.
                                hideSheetKeyboard()
                                val current = sheetHeightAnim.value
                                val next = (current - dragAmount)
                                    .coerceIn(sheetFloorPx(), sheetCeilingPx)
                                if (next != current) scope.launch { sheetHeightAnim.snapTo(next) }
                                // Whatever the sheet couldn't use, upward,
                                // goes into the stretch. `dragAmount` is
                                // positive downward, so the sheet's growth
                                // (next - current) is what it consumed of it.
                                val leftover = dragAmount + (next - current)
                                if (leftover < 0f) stretchSheet(-leftover)
                            },
                        )
                    },
            ) {
                // The handle sits the same distance below the sheet's top
                // edge in every sheet, and the strip below it is what varies:
                // the menu pads SheetBarShadowRoom in for its address bar's
                // shadow, so the strip hands that back here. Without it the
                // handle reads as pushed up against the top edge, with more
                // air under it than over it, and the menu's bar sits 6dp
                // lower than the identical one on the + sheet.
                //
                // The strip itself is a CONSTANT, and the 6dp is spent by
                // each sheet instead — MenuSheet in its own top padding,
                // every other sheet in the padding on its box below. It used
                // to be this strip that shrank for the menu, which was the
                // same picture while a sheet swap was instant and is not one
                // while the menu and its pages cross: the strip changed with
                // `sheet` on the frame of the tap, so the page still on
                // screen jumped 6dp before it had begun to move.
                val handleTopGap = (SheetDragHandleHeight - SheetHandleThickness) / 2
                val stripHeight = SheetDragHandleHeight - SheetBarShadowRoom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = handleTopGap)
                            .size(width = 32.dp, height = SheetHandleThickness)
                            .clip(specialCorner(2.dp))
                            .background(InkMuted),
                    )
                }
            // Both sheets are composed at all times; only the live one is
            // placed. Switching between them (tapping the address bar to edit
            // it) used to dispose one and compose the other from scratch, at
            // full sheet height, on a single frame — the same 47–77ms cost the
            // open animation's pre-warm frame exists to hide, except here there
            // is no animation to hide it behind. `sheet ?: lastSheet` keeps the
            // outgoing one on screen for its close animation, which is what
            // lastSheet was introduced for.
            val liveSheet = sheet ?: lastSheet
            Box(Modifier.weight(1f)) {
            // The shadow room the strip above no longer takes off for the
            // menu: every sheet that is not the menu spends it here instead.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = SheetBarShadowRoom)
                    .graphicsLayer { alpha = newTabFace.value }
                    .placedWhen(liveSheet == Sheet.NewTab || newTabFacePlaced),
            ) {
                NewTabSheet(
                    frosted = frostedSheets,
                    visible = sheet == Sheet.NewTab,
                    omniboxPop = omniboxPop,
                    recentlyVisited = recentlyVisited,
                    mostVisited = mostVisited,
                    omniboxHistory = omniboxHistory,
                    historySort = newTabHistorySort,
                    searchEngine = searchEngine,
                    engines = enabledSearchEngines,
                    searchSuggestionsEnabled = searchSuggestionsEnabled,
                    autoFocusKeyboard = autoFocusNewTabKeyboard,
                    editingUrl = addressEdit,
                    openKey = newTabSheetOpens,
                    privateMode = privateMode,
                    // The sheet's private button is now the way in and out of
                    // the private SPACE, not a property of the tab this sheet
                    // is about to open: the app recolors, the tab list swaps
                    // over, and the search that follows opens in whichever
                    // space is showing.
                    onTogglePrivate = {
                        // Going private abandons the address being edited:
                        // the tab it belonged to isn't in the space the user
                        // just switched to, so there is nothing left for Go
                        // to navigate. Re-opening the sheet onto itself is
                        // what empties the field — in the same composition,
                        // for the same reason the open does. An ordinary
                        // typed search is left alone.
                        if (addressEdit != null) openNewTabSheet()
                        vm.togglePrivateMode()
                    },
                    onGo = { url ->
                        // An edit navigates the tab whose address it is; a +
                        // opens a new one. Read before closeSheet, which
                        // clears the target.
                        val editing = addressEdit != null
                        closeSheet()
                        if (editing) {
                            vm.load(url)
                            closeSwitcherForNavigation()
                        } else {
                            openNewTabFromSheet(url)
                        }
                    },
                    onRemoveHistory = vm::removeHistory,
                )
            }

            // The left half of the drill-down axis: at rest when the menu
            // itself is showing, drifting off to the left while one of its
            // pages is up. Placed through the crossing so it is still there
            // to drift.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val u = paneCrossing()
                        val travel = -size.width / PANE_SLIDE_FRACTION
                        val face = 1f - newTabFace.value
                        if (paneForward) {
                            translationX = travel * paneDepartOffset(u)
                            alpha = paneDepartAlpha(u) * face
                        } else {
                            translationX = travel * paneArriveOffset(u)
                            alpha = paneArriveAlpha(u) * face
                        }
                    }
                    .placedWhen(
                        menuPanePlaced && (
                            (liveSheet == Sheet.NewTab && menuFacePlaced) ||
                            liveSheet == Sheet.Menu ||
                                liveSheet == Sheet.SiteSettings ||
                                liveSheet == Sheet.ReaderSettings
                            ),
                    ),
            ) {
                MenuSheet(
                    open = sheet == Sheet.Menu,
                    omniboxPop = omniboxPop,
                    tab = current,
                    pageDark = pageDark,
                    readerAvailable = readerAvailable,
                    readerActive = readerActive,
                    desktopMode = desktopMode,
                    siteLabel = currentSite,
                    isBookmarked = current?.url != null && bookmarks.any { it.url == current.url },
                    linkStripperEnabled = linkStripperEnabled,
                    onEditAddress = { openNewTabSheet(current?.url.orEmpty()) },
                    onBack = vm::goBack,
                    onForward = vm::goForward,
                    onReload = { vm.reload(); closeSheet(); closeSwitcherForNavigation() },
                    onTogglePageDark = vm::togglePageDark,
                    onToggleDesktop = vm::toggleDesktopMode,

                    onToggleBookmark = { current?.let(vm::toggleBookmark) },
                    onOpenBookmarks = { closeSheet(); bookmarksOpen = true },
                    onOpenHistory = { closeSheet(); historyOpen = true },
                    onOpenDownloads = { closeSheet(); vm.refreshDownloads(); downloadsOpen = true },
                    downloading = downloading,
                    // The sheet STAYS, unlike every other row here that acts
                    // on the page. The reader is a switch, not a destination:
                    // the article appears in the third of the screen above
                    // the sheet, which is enough to see that it worked and to
                    // flip it straight back if it did not. Taking the menu
                    // down would make undoing it a second trip.
                    onToggleReader = vm::toggleReaderMode,
                    onOpenReaderSettings = { sheet = Sheet.ReaderSettings },
                    onOpenFind = { vm.openFind() },
                    // The sheet stays open through a zoom: the page is
                    // reflowing behind it, and a slider is dragged rather
                    // than pressed once.
                    // Stays a sheet, and stays on top of this one's place in
                    // the chain: back returns to the menu it was reached
                    // from, exactly as the reader's settings do.
                    onOpenSiteSettings = { sheet = Sheet.SiteSettings },
                    onOpenSettings = {
                        // The two overlap on purpose: the sheet slides down
                        // while Settings slides up over it, instead of one
                        // visibly finishing before the other starts.
                        closeSheet()
                        settingsOpen = true
                    },
                    onContentHeight = { menuContentHeightPx = it.toFloat() },
                    onDismiss = ::closeSheet,
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = SheetBarShadowRoom)
                    // The right half of the axis: it arrives from the right
                    // over the menu's exit and leaves the same way it came.
                    .graphicsLayer {
                        val u = paneCrossing()
                        val travel = size.width / PANE_SLIDE_FRACTION
                        if (paneForward) {
                            translationX = travel * paneArriveOffset(u)
                            alpha = paneArriveAlpha(u)
                        } else {
                            translationX = travel * paneDepartOffset(u)
                            alpha = paneDepartAlpha(u)
                        }
                    }
                    .placedWhen(
                        subPanePlaced && (
                            liveSheet == Sheet.SiteSettings ||
                                (liveSheet == Sheet.Menu && lastSubSheet == Sheet.SiteSettings)
                            ),
                    ),
            ) {
                SiteSettingsSheet(
                    site = currentSite,
                    settings = currentSiteSettings,
                    adBlockOn = adBlockOn,
                    trackerBlockingOn = blocklistConfig.blockTrackers,
                    cookieBannersOn = blocklistConfig.hideCookieBanners,
                    appZoom = pageZoom,
                    onChange = { vm.setSiteSettings(currentSite, it) },
                    onSetZoomStep = { vm.setSiteZoomStep(currentSite, it) },
                    onReset = { vm.clearSiteSettings(currentSite) },
                    onBack = { sheet = Sheet.Menu },
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = SheetBarShadowRoom)
                    // The right half of the axis: it arrives from the right
                    // over the menu's exit and leaves the same way it came.
                    .graphicsLayer {
                        val u = paneCrossing()
                        val travel = size.width / PANE_SLIDE_FRACTION
                        if (paneForward) {
                            translationX = travel * paneArriveOffset(u)
                            alpha = paneArriveAlpha(u)
                        } else {
                            translationX = travel * paneDepartOffset(u)
                            alpha = paneDepartAlpha(u)
                        }
                    }
                    .placedWhen(
                        subPanePlaced && (
                            liveSheet == Sheet.ReaderSettings ||
                                (liveSheet == Sheet.Menu && lastSubSheet == Sheet.ReaderSettings)
                            ),
                    ),
            ) {
                ReaderSettingsSheet(
                    settings = readerSettings,
                    readerAvailable = readerAvailable,
                    readerActive = readerActive,
                    onToggleReader = vm::toggleReaderMode,
                    onSetTextStep = vm::setReaderTextStep,
                    onSetTheme = vm::setReaderTheme,
                    onSetFont = vm::setReaderFont,
                    onSetSpacing = vm::setReaderSpacing,
                    onBack = { sheet = Sheet.Menu },
                )
            }
            }
            }
            }
    }

    // ------------------------------------------------ page context menu

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Every image action re-requests the image with the page it came from as
    // Referer (see ImageSaver) — sites routinely refuse a bare request for an
    // image that renders fine in place.
    val imageReferer = current?.url
    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun saveImage(target: WebContextTarget) {
        scope.launch {
            try {
                val name = ImageSaver.saveToDownloads(context, target, imageReferer)
                // The Downloads screen reads MediaStore, which now has one
                // more row than the list it last read.
                vm.refreshDownloads()
                toast("Saved $name to Downloads")
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't save that image")
            }
        }
    }

    // Writing into Downloads needs WRITE_EXTERNAL_STORAGE up to API 28 and
    // nothing at all from 29 (see the manifest). Held here so the save can
    // resume on the far side of the system dialog.
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

    // MediaStore is the list (see DownloadsStore), and anything on the device
    // may have changed it since it was last read — so re-read on every open
    // rather than trusting what's already in hand.
    LaunchedEffect(downloadsOpen) { if (downloadsOpen) vm.refreshDownloads() }

    // Anything that opens over the page takes the menu with it: it points at
    // a spot on a page that is no longer the thing being looked at.
    LaunchedEffect(sheet != null, switcherOpen, settingsOpen, bookmarksOpen, historyOpen, downloadsOpen, currentId) {
        vm.dismissContextMenu()
        vm.closeLinkPreview()
    }

    WebContextMenu(
        target = contextTarget,
        onOpen = { url ->
            vm.dismissContextMenu()
            vm.load(url)
            closeSwitcherForNavigation()
        },
        onOpenInNewTab = { url ->
            vm.dismissContextMenu()
            // Opens in the space that's open, same as every other way a link
            // leaves the page it was on — and remembers the page it left, so
            // back out of the new tab returns to it instead of closing the
            // app (see the BackHandler chain).
            val background = newTabPlacement == NewTabPlacement.Background
            openNewTabFromSheet(url, openerId = currentId, background = background)
            // A background tab changes nothing on screen — no switch, and no
            // count anywhere in the chrome to tick over — so this is the only
            // thing that says it happened at all.
            if (background) toast("Opened in a new tab")
        },
        onCopyLink = { url ->
            vm.dismissContextMenu()
            clipboard.setText(
                AnnotatedString(if (linkStripperEnabled) UrlUtils.stripTrackingParams(url) else url),
            )
            // No toast: Android 13+ shows its own clipboard confirmation,
            // same as the menu's copy-address button.
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

    // What the promoted page's own tab needs before the expanded card can be
    // taken off it: a frame or two for composition to build (or re-attach)
    // that WebView, and then its first real paint. `postVisualStateCallback`
    // asked for straight after a load answers for the page being loaded, which
    // is exactly the question here.
    suspend fun awaitPromotedPaint() {
        withFrameNanos { }
        withFrameNanos { }
        vm.awaitPagePainted(vm.currentTabId.value, QUICK_SWITCH_PAINT_MAX_MS)
    }

    // Where the tab's own page is, in this overlay's coordinates — the app is
    // edge-to-edge, so the Compose root spans the window and a window position
    // is directly usable here (the same thing the context menu relies on for
    // its anchor). The promotion's expand lands exactly on this rectangle,
    // which is what lets the card's page be laid out against it from the
    // start. Its height stops short of the toolbar, since that strip of the
    // WebView is drawn under the bar and is not page anybody is looking at.
    val previewPageRect = boxCoordinates?.let { box ->
        val origin = box.positionInWindow()
        Rect(
            left = origin.x,
            top = origin.y,
            right = origin.x + box.size.width,
            bottom = origin.y + box.size.height - pageBottomInsetPx,
        )
    }

    LinkPreviewOverlay(
        preview = linkPreview,
        pageRect = previewPageRect,
        webViewFor = vm::previewWebView,
        onRelease = vm::releasePreviewView,
        onOpen = { url ->
            vm.load(url)
            closeSwitcherForNavigation()
            awaitPromotedPaint()
        },
        onOpenInNewTab = { url ->
            // Same as every other way a link leaves the page it was on: into
            // the space that's open, remembering the tab it came from.
            openNewTabFromSheet(url, openerId = currentId)
            awaitPromotedPaint()
        },
        // A background tab is not a promotion: the card has nothing to grow
        // into, since the page underneath is the page the user is staying on.
        // It opens the tab, says so, and the card leaves the ordinary way.
        onOpenInBackgroundTab = if (newTabPlacement != NewTabPlacement.Background) null else { url ->
            openNewTabFromSheet(url, openerId = currentId, background = true)
            toast("Opened in a new tab")
        },
        // Deliberately does NOT dismiss: copying an address is something taken
        // away from the page, not a way of leaving it — and the reason the
        // preview was raised (is this worth going to?) is usually still open
        // afterwards.
        onCopyLink = { url ->
            clipboard.setText(
                AnnotatedString(if (linkStripperEnabled) UrlUtils.stripTrackingParams(url) else url),
            )
        },
        onDismiss = vm::closeLinkPreview,
    )

    if (findForPage != null) {
        val findImeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val findToolbarBottom = with(density) { chromeBottomInsetPx.toDp() }
        Box(
            Modifier
                .fillMaxSize()
                // Above the keyboard, falling back to above the toolbar —
                // see the password cards below for the `adjustNothing`
                // caveat this shares.
                .padding(bottom = maxOf(findImeBottom, findToolbarBottom)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { aeroFindBounds = Rect.Zero }
            }
            FindBar(
                onBounds = { aeroFindBounds = it },
                state = findForPage,
                onQueryChange = vm::setFindQuery,
                onNext = { vm.findNext(forward = true) },
                onPrevious = { vm.findNext(forward = false) },
                onClose = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    vm.closeFind()
                },
            )
        }
    }

    if (promptForPage != null || suggestionForPage != null || autofillForPage != null) {
        val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val toolbarBottom = with(density) { chromeBottomInsetPx.toDp() }
        Box(
            Modifier
                .fillMaxSize()
                // Above the keyboard when there is one, above the toolbar when
                // there isn't — never both, since the keyboard already covers
                // the toolbar. Note the window is `adjustNothing`, so on API
                // 29 and below the IME reports no inset at all and the card
                // stays where the toolbar left it.
                .padding(bottom = maxOf(imeBottom, toolbarBottom)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (promptForPage != null) {
                SavePasswordCard(
                    prompt = promptForPage,
                    onSave = vm::acceptPasswordPrompt,
                    onNotNow = vm::dismissPasswordPrompt,
                    onNever = { vm.neverSaveForHost(promptForPage.host) },
                )
            } else if (suggestionForPage != null) {
                PasswordSuggestionBar(
                    suggestion = suggestionForPage,
                    onFill = { entry -> vm.fillPassword(suggestionForPage.tabId, entry.username, entry.password) },
                    onDismiss = vm::dismissPasswordSuggestion,
                )
            } else if (autofillForPage != null) {
                AutofillSuggestionBar(
                    suggestion = autofillForPage,
                    onFillAddress = { entry -> vm.fillAddress(autofillForPage.tabId, entry) },
                    onFillCard = { entry -> vm.fillCard(autofillForPage.tabId, entry) },
                    onDismiss = vm::dismissAutofillSuggestion,
                )
            }
        }
    }

    Destination(
        visible = settingsOpen,
        onBounds = { aeroDestinationBounds = it },
        onPaneAlpha = { aeroDestinationPaneAlpha = it },
    ) {
        // The one screen the private space does NOT recolor. Settings is
        // where the ordinary theme is chosen — an accent swatch list rendered
        // in violet would be showing the user a lie about what they're
        // picking — so it re-provides the app's own theme over the top of the
        // private one for its own subtree, and hands it straight back on the
        // way out. The special theme is NOT dropped along with it: it is the
        // user's chosen look rather than a mode to escape from, and the grid
        // that picks it has to show what it does — a screen exempt from the
        // look is a screen showing a theme nobody selected.
        val themeCrossfade = LocalThemeCrossfade.current
        BrowserTheme(
            accent = accentTheme,
            themeMode = themeMode,
            special = specialTheme,
            privateMode = false,
        ) {
        SettingsScreen(
            pane = settingsPane,
            searchEngine = searchEngine,
            searchEngines = allSearchEngines,
            disabledSearchEngines = disabledSearchEngines,
            searchSuggestionsEnabled = searchSuggestionsEnabled,
            themeMode = themeMode,
            pageDarkMode = pageDarkMode,
            accentTheme = accentTheme,
            specialTheme = specialTheme,
            pageZoom = pageZoom,
            autoFocusNewTabKeyboard = autoFocusNewTabKeyboard,
            openNewTabSheetOnLaunch = openNewTabSheetOnLaunch,
            newTabHistorySort = newTabHistorySort,
            tabManagerMode = tabManagerMode,
            doubleTapTabsSwitchesTab = doubleTapTabsSwitchesTab,
            swipeToSwitchTabs = swipeToSwitchTabs,
            flickToCloseTab = flickToCloseTab,
            linkStripperEnabled = linkStripperEnabled,
            openExternalLinksInOverlay = openExternalLinksInOverlay,
            openLinksInApps = openLinksInApps,
            pullToRefreshEnabled = pullToRefreshEnabled,
            linkPreviewEnabled = linkPreviewEnabled,
            pageLens = pageLens,
            statusBarBlur = statusBarBlur,
            translucentSheets = translucentSheets,
            translucency = translucency,
            newTabPlacement = newTabPlacement,
            keepPrivateTabs = keepPrivateTabs,
            savePasswords = savePasswordsEnabled,
            externalPasswordManager = externalPasswordManager,
            savedPasswords = savedPasswords,
            neverSavedSites = neverSavedSites,
            savedAddresses = savedAddresses,
            savedCards = savedCards,
            fillAddresses = fillAddresses,
            fillPaymentMethods = fillPaymentMethods,
            permissionRules = permissionRules,
            sitePermissions = sitePermissions,
            adBlockEnabled = adBlockOn,
            blocklistConfig = blocklistConfig,
            blocklistMeta = blocklistMeta,
            blocklistRuleCount = blocklistRuleCount,
            cosmeticRuleCount = cosmeticRuleCount,
            blockedOnPage = blockedOnPage,
            blocklistUpdating = blocklistUpdating,
            blocklistImportError = blocklistImportError,
            onNavigate = { settingsPane = it },
            onBack = {
                val parent = settingsPane.parent
                if (parent != null) settingsPane = parent else settingsOpen = false
            },
            onSelectSearchEngine = vm::setSearchEngine,
            onArrangeSearchEngines = vm::setSearchEngineArrangement,
            onAddCustomSearchEngine = vm::addCustomSearchEngine,
            onRemoveCustomSearchEngine = vm::removeCustomSearchEngine,
            onToggleSearchSuggestions = vm::toggleSearchSuggestions,
            onSelectThemeMode = vm::setThemeMode,
            onSelectPageDarkMode = vm::setPageDarkMode,
            onSelectAccent = vm::setAccentTheme,
            onSelectSpecialTheme = { theme ->
                if (theme != specialTheme) themeCrossfade { vm.setSpecialTheme(theme) }
            },
            onSetZoomStep = vm::setZoomStep,
            onTogglePageLens = vm::togglePageLens,
            onToggleStatusBarBlur = vm::toggleStatusBarBlur,
            onToggleTranslucentSheets = vm::toggleTranslucentSheets,
            onSetTranslucency = vm::setTranslucency,
            onToggleAutoFocusNewTabKeyboard = vm::toggleAutoFocusNewTabKeyboard,
            onToggleOpenNewTabSheetOnLaunch = vm::toggleOpenNewTabSheetOnLaunch,
            onSelectNewTabHistorySort = vm::setNewTabHistorySort,
            onSelectTabManagerMode = vm::setTabManagerMode,
            onToggleDoubleTapTabsSwitchesTab = vm::toggleDoubleTapTabsSwitchesTab,
            onToggleSwipeToSwitchTabs = vm::toggleSwipeToSwitchTabs,
            onToggleFlickToCloseTab = vm::toggleFlickToCloseTab,
            onToggleLinkStripper = vm::toggleLinkStripper,
            onToggleOpenExternalLinksInOverlay = vm::toggleOpenExternalLinksInOverlay,
            onToggleOpenLinksInApps = vm::toggleOpenLinksInApps,
            onTogglePullToRefresh = vm::togglePullToRefresh,
            onToggleLinkPreview = vm::toggleLinkPreview,
            onSelectNewTabPlacement = vm::setNewTabPlacement,
            onToggleKeepPrivateTabs = vm::toggleKeepPrivateTabs,
            onToggleSavePasswords = vm::toggleSavePasswords,
            onSetExternalPasswordManager = vm::setExternalPasswordManager,
            onRemoveSavedPassword = vm::removeSavedPassword,
            onAllowSavingForHost = vm::allowSavingForHost,
            onClearSavedPasswords = vm::clearSavedPasswords,
            onToggleFillAddresses = vm::toggleFillAddresses,
            onToggleFillPaymentMethods = vm::toggleFillPaymentMethods,
            onSaveAddress = vm::saveAddress,
            onRemoveAddress = vm::removeAddress,
            onClearAddresses = vm::clearAddresses,
            onSaveCard = vm::saveCard,
            onRemoveCard = vm::removeCard,
            onClearCards = vm::clearCards,
            onSetPermissionRule = vm::setPermissionRule,
            onClearSitePermission = vm::clearSitePermission,
            onClearSitePermissions = vm::clearSitePermissions,
            onClearBrowsingData = vm::clearBrowsingData,
            onToggleAdBlock = vm::toggleAdBlock,
            onToggleTrackerBlocking = vm::toggleTrackerBlocking,
            onToggleCookieBanners = vm::toggleCookieBanners,
            onToggleBlocklistAutoUpdate = vm::toggleBlocklistAutoUpdate,
            onUpdateBlocklist = vm::updateBlocklist,
            onAddBlocklistFeedUrl = vm::addBlocklistFeedUrl,
            onRemoveBlocklistFeedUrl = vm::removeBlocklistFeedUrl,
            onImportBlocklistFile = vm::importBlocklistFile,
            onRemoveImportedBlocklist = vm::removeImportedBlocklist,
            onSetBlocklistCustomEntries = vm::setBlocklistCustomEntries,
        )
        }
    }

    Destination(
        visible = bookmarksOpen,
        onBounds = { aeroDestinationBounds = it },
        onPaneAlpha = { aeroDestinationPaneAlpha = it },
    ) {
        BookmarksScreen(
            bookmarks = bookmarks,
            // A bookmark opens in its own tab rather than replacing whatever
            // page is on screen — the tab the user came from stays put.
            onSelect = { url ->
                bookmarksOpen = false
                openNewTabFromSheet(url)
            },
            onRemove = vm::removeBookmark,
            onBack = { bookmarksOpen = false },
        )
    }

    Destination(
        visible = historyOpen,
        onBounds = { aeroDestinationBounds = it },
        onPaneAlpha = { aeroDestinationPaneAlpha = it },
    ) {
        HistoryScreen(
            history = history,
            // Same as bookmarks: a history entry opens in a new tab.
            onSelect = { url ->
                historyOpen = false
                openNewTabFromSheet(url)
            },
            onRemove = vm::removeHistory,
            onClearAll = vm::clearHistory,
            onBack = { historyOpen = false },
        )
    }

    Destination(
        visible = downloadsOpen,
        onBounds = { aeroDestinationBounds = it },
        onPaneAlpha = { aeroDestinationPaneAlpha = it },
    ) {
        // Collected in here, not up with the rest: it changes on every poll,
        // and only this screen's rows draw it.
        val activeDownloads by vm.activeDownloads.collectAsStateWithLifecycle()
        DownloadsScreen(
            downloads = downloads,
            active = activeDownloads,
            onCancel = vm::cancelDownload,
            onDelete = vm::deleteDownload,
            onBack = { downloadsOpen = false },
        )
    }

    // Last in composition on purpose, and both halves of that matter: what it
    // draws is raised BY the page and belongs over everything else on screen,
    // and a BackHandler registered later wins over the ones above it. See
    // WebPlatform.
    WebPlatform(
        vm,
        onDownloadConfirmed = { origin ->
            // A bar hidden by scrolling is brought back for the icon to land
            // in; the flight follows the button as it slides up.
            toolbarVisible = true
            downloadFlight = DownloadFlight(origin)
        },
    )
    // After it, so the icon is drawn OVER the card it leaves while that card
    // fades. It registers no back handler, so being later costs the chain
    // nothing.
    downloadFlight?.let { flight ->
        DownloadFlightOverlay(
            flight = flight,
            target = menuButton,
            onLanded = {
                if (downloadFlight === flight) downloadFlight = null
                downloadLandings++
                haptics.tick()
            },
        )
    }
}

/**
 * The WebView is owned by the ViewModel and merely displayed here. The FrameLayout
 * is the stable Compose-side node; swapping tabs re-parents the existing WebView
 * into it rather than creating a new one.
 *
 * This host is ALWAYS laid out fullscreen — [progress], [targetRect], [offsetX]
 * and [offsetY] only drive a graphicsLayer transform, never a relayout. That
 * means there is exactly one surface for "this tab's content" across the WHOLE
 * gesture: scale shrinks it from fullscreen toward the switcher card as
 * [progress] goes 0 to [MIDPOINT_PROGRESS], and [offsetX]/[offsetY] — driven
 * entirely from outside, never derived from [progress] — carry it wherever the
 * gesture (drag or tap, see BrowserScreen) currently wants it. Nothing hands
 * off to a second element at any point, so there's nothing to duplicate or
 * desync.
 *
 * This composable itself is also never conditionally removed from composition
 * by its caller (that's the other half of "one element, no duplicate hand-off"
 * — see the call site) — [visible] instead toggles the wrapped Android View's
 * own visibility. Flipping that is instant and doesn't touch the WebView's
 * parentage at all, unlike composing/decomposing this composable would, which
 * tears down and rebuilds the AndroidView and re-parents the WebView into a
 * fresh container — that reparenting is what caused a visible duplicate flash
 * when a new drag started right after settling into the switcher.
 */
@Composable
private fun WebViewHost(
    vm: BrowserViewModel,
    tab: Tab,
    overflowBottomPx: Int,
    // How far the view hangs ABOVE the page's box, under the status bar, and
    // the bend's depth — both zero unless the page lens is on (see PageLens).
    overscanTopPx: Int = 0,
    // The part of the top overscan that is not a system safe area and must
    // therefore be reserved in the document itself (page lens only).
    pageTopContentInsetPx: Int = 0,
    // The spinner lives in this band, even when the page lens supplies the
    // overscan instead of the page's status-strip overlay.
    statusBarHeightPx: Int = 0,
    // The status bar strip the page veils itself under (PageTopInset.setStrip):
    // part of the picture, so the shrink's window reaches up over it.
    stripAbovePx: Float = 0f,
    // How far below the status bar's edge the drawn strip fades out.
    stripFadePx: Float = 0f,
    // The preference applies to the wipe's leading edge as well as the page
    // content behind the status strip.
    statusBarBlur: Boolean = true,
    lensDepthPx: Float = 0f,
    // How far a page's own bottom bar is lifted while the toolbar is away,
    // or 0 when it has none (see BrowserScreen's lensBarLiftPx).
    lensBarLiftPx: Int = 0,
    // These are also published immediately before a WebView is created below.
    // A document-start script reads them synchronously; publishing only from
    // a Compose effect lets a fast page paint once at zero, then reflow when
    // the effect adds the system-bar room.
    pageChromeInsetPx: Int = 0,
    pageEndPadPx: Int = 0,
    pageTopFillPx: Int = 0,
    pageBottomFillPx: Int = 0,
    // Deferred reads: these three move once per frame for a whole switcher
    // gesture and are only ever consumed inside the graphicsLayer below.
    // Taken as values instead, this composable — and so the AndroidView update
    // lambda inside it, which does real View-world work every time it runs —
    // would recompose on each of those frames.
    progress: () -> Float,
    targetRect: Rect?,
    offsetX: () -> Float,
    offsetY: () -> Float,
    // Whether the page is settled at fullscreen. A boolean rather than a test
    // on `progress`, for the same reason: it changes twice per gesture.
    settled: Boolean,
    visible: Boolean,
    warming: Boolean = false,
    // How far the toolbar has slid away (0 = up, 1 = gone), read per frame by
    // the page lens only — it decides where the page's visible bottom is.
    toolbarSlide: () -> Float = { 0f },
    onScroll: (deltaY: Int, scrollY: Int, userDriven: Boolean) -> Unit = { _, _, _ -> },
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 16.dp.toPx() } * SpecialCornerScale
    val hostView = LocalView.current
    // The scroll listener below is installed on a long-lived WebView from the
    // update lambda, so it outlives any single recomposition — read [onScroll]
    // through a state holder rather than capturing the lambda that happened to
    // be current when the listener was set.
    val currentOnScroll = rememberUpdatedState(onScroll)
    val pullToRefreshEnabled by vm.pullToRefreshEnabled.collectAsStateWithLifecycle()
    // Read here rather than inside the factory lambda below, which is plain
    // View-world code and can't call a composable.
    val hostHaptics = rememberHaptics()
    // Experimental glass edges (see PageLens). The toolbar's slide moves the
    // bottom one per frame, so it is collected here and handed straight to the
    // lens — a draw-side read, never a recomposition of this host.
    val pageLensOn = lensDepthPx > 0f
    val lensSlot = remember { PageLens.Slot() }
    val currentToolbarSlide = rememberUpdatedState(toolbarSlide)
    val currentProgress = rememberUpdatedState(progress)
    if (pageLensSupported && pageLensOn) {
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { currentToolbarSlide.value() }
                .collect { lensSlot.lens?.update() }
        }
        // The effect's strength follows the shrink: full at fullscreen, none
        // at the card, in step with the pictures that stand in for this page
        // (AnimatedThumbnailHost), so the handoff between them never jumps.
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { 1f - shrinkOf(currentProgress.value()) }
                .collect { lensSlot.lens?.setStrength(it) }
        }
    }
    // The round spinner should only ever appear for a pull-triggered reload —
    // not the reload button, not ordinary navigation — even though all three
    // drive the exact same tab.loading flag. True from the moment the pull
    // gesture commits until a load is actually observed finishing afterward;
    // loadSeenSincePull gates that finish check on a load having genuinely
    // started first, since reload() doesn't flip tab.loading synchronously —
    // clearing on the first "not loading" read right after the pull would
    // just be reading the still-stale pre-reload state and hide the spinner
    // a frame after showing it, before the real load ever began.
    var pullRefreshing by remember { mutableStateOf(false) }
    var loadSeenSincePull by remember { mutableStateOf(false) }
    // The status-strip veil is Compose content painted after AndroidView, so
    // it would otherwise cover SwipeRefreshLayout's native circle. Keep it
    // out of that one layer for the lifetime of a pull.
    var pullIndicatorActive by remember { mutableStateOf(false) }
    // Read here, in composable scope, and passed down as plain Int — the
    // factory/update lambdas below are ordinary View-world callbacks, not
    // @Composable themselves, so they can't read these theme-derived colors
    // directly.
    // Plain black on white (white on black at the dark end) in EVERY look,
    // not the theme's ink on BarBg: a native circle cannot carry Aero's
    // glass, so its translucent BarBg came out as a see-through disc with
    // the page's text behind the arrow, and the special looks' inks and
    // grounds made the same control a different object in each theme.
    val spinnerDark = com.yuku.browser.ui.theme.LocalChromeDarkness.current >= 0.5f
    val spinnerArgb = (if (spinnerDark) Color.White else Color.Black).toArgb()
    val spinnerBgArgb = (if (spinnerDark) Color.Black else Color.White).toArgb()
    // What shows through wherever Chromium hasn't painted yet — most visibly
    // the strip uncovered as the toolbar slides away, which the page is
    // resized into a frame before it can raster it. WebView's own default is
    // white, so in dark mode that strip flashed white; this makes the
    // not-yet-painted area the same background the page sits on.
    val pageBgArgb = PageBg.toArgb()
    // Under Aero the strip under the glass toolbar is part of what is seen, so
    // the shrink's window reaches over it — the same window the thumbnail
    // stand-in uses (see AnimatedThumbnailHost), or the handoff would jump.
    // Translucent sheets too: their toolbar is frosted over the page, so a
    // zoom that stops at the bar's top edge changes the bar's colour on landing.
    val shrinkBelowPx = if (com.yuku.browser.ui.theme.LocalAero.current || com.yuku.browser.ui.theme.LocalFrosted.current) {
        overflowBottomPx.coerceAtLeast(0).toFloat()
    } else 0f
    val cardMarks = com.yuku.browser.ui.theme.rememberAeroCardMarks()
    // The status bar strip, drawn over the page (see drawStatusStrip) in
    // proportion to how much of the screen the page fills.
    val stripPaint = rememberStatusStripPaint(vm.statusStrips, tab.id)

    // The transform lives on this Box — the page's BOX, the screen minus the
    // toolbar — rather than on the AndroidView, which is [overflowBottomPx]
    // taller than it and hangs under the bar. Everything the shrink is
    // measured against (the card's aspect, the thumbnail that stands in for
    // this at the handoff, targetRect itself) is that box, so scaling the
    // taller view by its own size would squash the page against its own
    // still image. The overflow is what the bar covers: unclipped at rest, so
    // it shows the moment the bar slides away, and clipped for every frame of
    // a shrink, since a card is the page box and nothing more.
    Box(
        Modifier
            .fillMaxSize()
            .ninety8ShrinkChrome(
                visible = visible,
                targetRect = targetRect,
                progress = progress,
                offsetX = offsetX,
                offsetY = offsetY,
                belowPx = { _, _ -> shrinkBelowPx },
                abovePx = stripAbovePx,
            )
            .graphicsLayer {
                val shrinkProgress = shrinkOf(progress())
                applyShrinkTransform(
                    targetRect, offsetX(), offsetY(), shrinkProgress, cornerRadiusPx, shrinkBelowPx, stripAbovePx,
                    fullCornerPx = lensCornerFor(hostView, lensDepthPx) * LENS_CROP_CORNER,
                )
                // A private page goes out of focus as it shrinks toward its
                // card and comes back as it is drawn out — the live WebView,
                // since this one element is the whole gesture (see above).
                privateShrinkBlur(tab.isPrivate, shrinkProgress)
                // While merely [warming] the view is attached, measured and
                // rendering, but the static thumbnail is what the user is
                // looking at — so it must not composite. Alpha 0 (rather than
                // View.INVISIBLE) keeps it out of the picture without taking
                // it out of layout, which is the whole point of warming.
                alpha = if (visible) 1f else 0f
            }
            // The status bar strip over the page, going as the page shrinks:
            // a card is the page alone.
            .then(
                if (stripAbovePx <= 0f || pullIndicatorActive) Modifier
                else Modifier.drawWithContent {
                    drawContent()
                    drawStatusStrip(
                        stripPaint, stripAbovePx,
                        1f - shrinkOf(progress()).coerceIn(0f, 1f),
                        softenWipeEdge = statusBarBlur,
                    )
                }
            )
            // The card's rim and glare, coming in as the live page shrinks —
            // the same marks the thumbnail stand-in draws (drawShrinkCardMarks).
            .then(
                if (cardMarks == null) Modifier
                else Modifier.drawWithContent {
                    drawContent()
                    drawShrinkCardMarks(cardMarks, targetRect, shrinkOf(progress()), shrinkBelowPx, cornerRadiusPx, stripAbovePx)
                }
            )
            // The TUI card's text-colour rule, arriving as the page shrinks.
            .tuiShrinkOutline(targetRect, progress, shrinkBelowPx, stripAbovePx)
    ) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            // Measured taller than the slot it is placed in, and placed at
            // its top: the extra rides under the toolbar. A plain height
            // modifier would be coerced back into the incoming constraints
            // (the same trap the switcher's overscan row hit), and the parent
            // must keep reporting its own size, not the view's.
            .layout { measurable, constraints ->
                // The page lens also hangs the view above the box, under the
                // status bar, by the same kind of overscan (see PageLens).
                val height = constraints.maxHeight + overscanTopPx + overflowBottomPx
                val placeable = measurable.measure(
                    constraints.copy(minHeight = height, maxHeight = height),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, -overscanTopPx)
                }
            }
            .statusBarBlur(statusBarBlur && !pullIndicatorActive, stripAbovePx),
        factory = { ctx ->
            WebViewSwipeRefreshLayout(ctx).apply {
                // The picture the reload is held on is taken as the pull
                // BEGINS, before the spinner is in it (see
                // BrowserViewModel.pendingHolds).
                onPullStarting = {
                    pullIndicatorActive = true
                    vm.preparePullReload()
                }
                onPullFinished = { pullIndicatorActive = false }
                setOnRefreshListener {
                    // The pull crossed its threshold and a reload is actually
                    // starting — the only moment in that gesture worth
                    // announcing.
                    hostHaptics.confirm()
                    pullRefreshing = true
                    loadSeenSincePull = false
                    vm.reload(fromPull = true)
                }
            }
        },
        update = { swipeRefresh ->
            // Off means off outright: SwipeRefreshLayout stops intercepting
            // anything, so the page sees every drag exactly as it would
            // without this layout in the tree.
            swipeRefresh.isEnabled = pullToRefreshEnabled
            swipeRefresh.setColorSchemeColors(spinnerArgb)
            swipeRefresh.setProgressBackgroundColorSchemeColor(spinnerBgArgb)
            // The host hangs above the page box under the status bar. Keep
            // the refresh circle in that band rather than compensating its
            // position back into the page below it. The pull distance stays
            // unchanged, so this only changes where the circle lands.
            val d = swipeRefresh.resources.displayMetrics.density
            val spinnerDiameterPx = (SPINNER_DIAMETER_DP * d).toInt()
            val spinnerTravelPx = ((SPINNER_REST_DP + SPINNER_DIAMETER_DP) * d).toInt()
            val spinnerEnd = overscanTopPx + statusBarHeightPx / 2 - spinnerDiameterPx / 2
            if (swipeRefresh.spinnerTopOffsetPx != spinnerEnd) {
                swipeRefresh.spinnerTopOffsetPx = spinnerEnd
                swipeRefresh.setProgressViewOffset(
                    false,
                    spinnerEnd - spinnerTravelPx,
                    spinnerEnd,
                )
            }
            // GONE rather than INVISIBLE — it also drops out of touch dispatch,
            // which is the reason this used to need removing from composition
            // entirely: an AndroidView's hit-test area is always its full
            // layout bounds regardless of the graphicsLayer scale it's drawn
            // at, so once settled into the small card it would otherwise keep
            // silently swallowing every tap meant for the switcher beneath it.
            //
            // [warming] deliberately opts back INTO being laid out while
            // invisible: a GONE WebView is 0×0, so it can neither raster the
            // page nor even be captured as a thumbnail, and revealing one is
            // always a few blank frames. During an expansion the alpha-0 layer
            // above hides it instead, and by the time it's revealed it has
            // already painted. Taps aren't a concern in that window — a card's
            // own gesture detector already owns the pointer.
            swipeRefresh.visibility = if (visible || warming) View.VISIBLE else View.GONE
            // Never driven true from here — that's left entirely to
            // SwipeRefreshLayout's own gesture handling (see the listener
            // above), so a reload from the toolbar button or plain
            // navigation never shows this spinner. Only clearing it, once
            // the pull-triggered load has actually finished, happens here.
            if (pullRefreshing) {
                if (tab.loading) {
                    loadSeenSincePull = true
                } else if (loadSeenSincePull) {
                    swipeRefresh.isRefreshing = false
                    pullRefreshing = false
                    loadSeenSincePull = false
                    pullIndicatorActive = false
                }
            } else {
                swipeRefresh.isRefreshing = false
            }
            // This must precede webViewFor(): create() attaches the
            // document-start inset bridges and immediately starts navigation.
            // Keeping the values ready here makes the first document layout
            // match the already-measured AndroidView bounds, rather than
            // correcting it a frame later via a Compose effect.
            vm.setPageTopOverscan(overscanTopPx)
            vm.setPageTopInset(pageTopContentInsetPx, pageTopContentInsetPx)
            vm.setPageTopStrip(stripAbovePx.roundToInt(), stripFadePx.roundToInt())
            vm.setPageChromeInset(pageChromeInsetPx, pageEndPadPx)
            vm.setPageBarFill(pageTopFillPx, pageBottomFillPx)
            val webContainer = swipeRefresh.webContainer
            val web = vm.webViewFor(tab)
            // Nothing but a finger-driven scroll of a settled page is
            // allowed to put a bar down the right edge. WebView awakens its
            // own from any change of the exposed scroll offset, and this host
            // manufactures several that are not scrolls at all: flipping the
            // AndroidView GONE→VISIBLE (tapping a card open, landing a swipe
            // between tabs) makes it resync scrollY to Chromium's real
            // offset, which arrives as a perfectly ordinary onScrollChanged
            // while the page is still mid-expand. The gate answers that by
            // asking whose movement it was rather than by timing the fade
            // out; this only tells it whether a bar would make sense at all
            // right now. See [ScrollBarGate].
            val scrollBarGate = vm.scrollBarGate(web)
            scrollBarGate.setHostAllows(settled)
            // Guarded rather than set unconditionally: this lambda re-runs on
            // every recomposition of the host, and setBackgroundColor
            // invalidates the view each time.
            if ((web.background as? ColorDrawable)?.color != pageBgArgb) {
                web.setBackgroundColor(pageBgArgb)
            }
            // Chrome-style hide-on-scroll (and the preview invalidation that
            // rides along with it) is driven from here — the WebView is created
            // by the ViewModel and shared across hosts, so the listener is
            // (re)claimed by whichever host is currently showing this tab.
            val lens = if (pageLensSupported) PageLens.of(web) else null
            web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                scrollBarGate.onScroll(scrollY)
                currentOnScroll.value(scrollY - oldScrollY, scrollY, scrollBarGate.userDriven)
            }
            if (webContainer.getChildAt(0) !== web) {
                webContainer.removeAllViews()
                (web.parent as? ViewGroup)?.removeView(web)
                webContainer.addView(
                    web,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            if (lens != null) {
                lensSlot.lens = lens
                val overscan = overscanTopPx.toFloat()
                // The bottom edge is built like the top one: the bend, its
                // fade and the corners sit on the toolbar's top and ride it
                // as it slides, reading the strip under the bar — real page,
                // as the top reads the strip under the status bar. With the
                // bar gone the page runs to the screen's edge, no bezel strip:
                // the curve stretches the last rows into the bend (the view
                // cannot hang past the window — see pageOverflowPx). Nothing
                // while the keyboard is up: the view ends at its top.
                val underBar = overflowBottomPx.toFloat()
                lens.bottomCoverPx = {
                    if (underBar < 0f) 0f
                    else underBar * (1f - currentToolbarSlide.value().coerceIn(0f, 1f))
                }
                // Same depth at both ends.
                lens.bottomDepthPx = { Float.NaN }
                // A view bound mid-shrink starts at the shrink's strength; read
                // unobserved, or this update block would re-run every frame.
                lens.setStrength(
                    1f - shrinkOf(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { progress() })
                )
                lens.configure(pageLensOn, lensDepthPx, overscan)
            }
        },
    )
    }
}

/** Shared by [WebViewHost] and [AnimatedThumbnailHost] so the live content and its
 * static stand-in always scale/position identically — any mismatch between the two
 * would show up as a visible pop at the handoff between them. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyShrinkTransform(
    targetRect: Rect?,
    offsetX: Float,
    offsetY: Float,
    shrinkProgress: Float,
    cornerRadiusPx: Float,
    // Page hanging below the node that is part of the picture at fullscreen
    // (Aero's strip under the glass toolbar). The window reaches down over it
    // at fullscreen and tightens off it as the card lands, so it is cropped
    // exactly like the sideways surplus — one picture, one zoom.
    belowPx: Float = 0f,
    // The same above the node: the status bar strip (PageTopInset.setStrip).
    abovePx: Float = 0f,
    // The corner the window eases TO at fullscreen, in screen px: the page
    // lens's display corners (lensCornerFor), 0 without the lens. The card's
    // round corner becomes the CRT's instead of squaring off on the way.
    fullCornerPx: Float = 0f,
) {
    // The picture's own height: the node, plus whatever of it hangs below.
    // Every measurement from here down is made against the WHOLE picture, so
    // a full-screen grab zooms about its own centre and comes out to full
    // screen as one image, instead of the page box zooming and the strip
    // under it being uncovered on its own.
    val geometry = shrinkGeometry(size.width, size.height, targetRect, shrinkProgress, belowPx, abovePx)
    val below = geometry.below
    val above = geometry.above
    // ONE scale for both axes — the page keeps its own proportions the whole
    // way into the card, whatever proportions the card has.
    //
    // This used to be two: `width/width` and `height/height`, which lands the
    // page exactly on the card and is exactly right for as long as a card is
    // the same shape as the page. It is not, sideways — a card is square there
    // (see TabSwitcher) — and a page scaled by two different factors visibly
    // narrows on its way in, which is a distortion the eye reads as the page
    // itself changing rather than as the page moving.
    //
    // The larger of the two ratios, so the page COVERS the card rather than
    // fitting inside it: fitting would leave bare background along two of the
    // card's edges, and the card would not be full until whatever is behind it
    // filled it in. What that costs is the surplus on the other axis, which is
    // cropped away by the window below — and cropping is what the card's own
    // thumbnail does with the same picture (ContentScale.Crop), so the live
    // page and its still stand-in show the same part of the page throughout.
    val scale = geometry.scale
    scaleX = scale
    scaleY = scale
    translationX = offsetX
    // `offsetY` puts the NODE's centre on the card's. The picture's centre is
    // `below / 2` under that, so it is lifted by that much (at the live scale)
    // as the card is approached — nothing at fullscreen, where the picture is
    // simply the screen, and exactly centred by the time it lands.
    // A strip ABOVE the node moves the picture's centre up by half of it,
    // which is the same correction the other way.
    translationY = offsetY - (below - above) / 2f * scale * shrinkProgress.coerceIn(0f, 1f)
    // The corner is the one thing that must NOT follow the inertia band below
    // zero (see shrinkOf): a settle back out to fullscreen carries a hair past
    // it, and a negative corner radius is not a shape. Rounded to nothing at
    // fullscreen either way, which is what that end of the range means.
    val corner = shrinkProgress.coerceAtLeast(0f)
    // The window the scaled page is seen through: the full node at fullscreen,
    // the card's own box by the time it has landed. It is the CARD's two
    // dimensions that are interpolated here, not the scaled page's — which is
    // what makes the crop appear gradually instead of the page being cut to
    // its final shape on the first frame of the gesture, and what leaves the
    // window equal to the drawn page at fullscreen, where there is nothing to
    // crop.
    //
    // Centred on the node, because after the layer's own transform that is
    // where the card is: `offsetX`/`offsetY` are measured to put the page's
    // CENTRE on the card's centre, and the window rides the same transform.
    // (It is expressed in the layer's own pre-scale space, hence every
    // division by the live scale — an outline is resolved before the layer's
    // transform, the same reason the radius has always been compensated.)
    val liveScale = scale.coerceAtLeast(0.0001f)
    shape = ShrinkCropShape(
        widthPx = geometry.windowWidth,
        heightPx = geometry.windowHeight,
        radiusPx = (fullCornerPx + (cornerRadiusPx - fullCornerPx) * corner.coerceAtMost(1f)) / liveScale,
        belowPx = below,
        abovePx = above,
    )
    // Unclipped at rest even under the lens: the strip under the toolbar is
    // page, and the screen-space corners (BrowserScreen's lensCorners) are
    // the same arcs at the same place by then.
    clip = corner > 0f
}

/** The page lens's display corner radius for the shrink's window; 0 when off. */
private fun lensCornerFor(view: View, lensDepthPx: Float): Float =
    if (lensDepthPx <= 0f || !pageLensSupported) 0f
    else displayCornerRadius(view) ?: (lensDepthPx * FALLBACK_CORNER)

/**
 * The numbers [applyShrinkTransform] is made of, in one place so that anything
 * drawn INSIDE the shrinking layer (the Aero card marks, [drawShrinkCardMarks])
 * lands on the very window the crop uses rather than a second copy of the
 * arithmetic that could drift from it. Window sizes are in the layer's own
 * pre-scale space, exactly as [ShrinkCropShape] takes them.
 */
private class ShrinkGeometry(
    val scale: Float,
    val windowWidth: Float,
    val windowHeight: Float,
    val pictureHeight: Float,
    val below: Float,
    val corner: Float,
    // Picture hanging ABOVE the node; the picture's top is at -above.
    val above: Float = 0f,
) {
    /** The crop window's top edge, in node space — centred on the picture. */
    fun windowTop(h: Float): Float = -above + (pictureHeight - h) / 2f
}

private fun shrinkGeometry(
    width: Float,
    height: Float,
    targetRect: Rect?,
    shrinkProgress: Float,
    belowPx: Float,
    abovePx: Float = 0f,
): ShrinkGeometry {
    val below = belowPx.coerceAtLeast(0f)
    // The status bar strip is part of the picture at full screen only: a card
    // is the page BOX (what is under the strip is content scrolled up past a
    // sticky header, or the document's top padding — neither is the page), so
    // the strip is taken off the picture as the card is approached and grows
    // back in from the box's top edge as it zooms out to full screen.
    val above = abovePx.coerceAtLeast(0f) * (1f - shrinkProgress.coerceIn(0f, 1f))
    val pictureHeight = height + below + above
    val cardWidth = targetRect?.width ?: (width * FALLBACK_SHRINK_SCALE)
    val cardHeight = targetRect?.height ?: (pictureHeight * FALLBACK_SHRINK_SCALE)
    val fill = if (width > 0f && pictureHeight > 0f) {
        maxOf(cardWidth / width, cardHeight / pictureHeight).coerceIn(0.01f, 1f)
    } else {
        FALLBACK_SHRINK_SCALE
    }
    val scale = 1f + (fill - 1f) * shrinkProgress
    val corner = shrinkProgress.coerceAtLeast(0f)
    val liveScale = scale.coerceAtLeast(0.0001f)
    return ShrinkGeometry(
        scale = scale,
        windowWidth = (width + (cardWidth - width) * corner) / liveScale,
        windowHeight = (pictureHeight + (cardHeight - pictureHeight) * corner) / liveScale,
        pictureHeight = pictureHeight,
        below = below,
        corner = corner,
        above = above,
    )
}

/**
 * Aero's card marks (rim and glare, see `rememberAeroCardMarks`) drawn over a
 * page on its way into or out of its card, faded in by how far it has shrunk:
 * nothing at fullscreen, where the page is simply the screen, and exactly the
 * card's own marks the moment it lands — so the card taking over adds nothing
 * that was not already there, and leaving it takes nothing away at once.
 *
 * Drawn in SCREEN pixels over the crop window: the layer is scaled, so the
 * block is un-scaled around the window's corner first, which keeps a hairline
 * a hairline at every size instead of thinning with the page.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShrinkCardMarks(
    marks: com.yuku.browser.ui.theme.AeroCardMarks,
    targetRect: Rect?,
    shrinkProgress: Float,
    belowPx: Float,
    cornerRadiusPx: Float,
    abovePx: Float = 0f,
) {
    val g = shrinkGeometry(size.width, size.height, targetRect, shrinkProgress, belowPx, abovePx)
    val t = g.corner.coerceIn(0f, 1f)
    val amount = t * t * (3f - 2f * t)
    if (amount <= 0.001f) return
    val s = g.scale.coerceAtLeast(0.0001f)
    val w = g.windowWidth.coerceAtMost(size.width)
    val h = g.windowHeight.coerceAtMost(g.pictureHeight)
    val left = (size.width - w) / 2f
    val top = g.windowTop(h)
    translate(left, top) {
        scale(1f / s, 1f / s, pivot = Offset.Zero) {
            marks(Size(w * s, h * s), cornerRadiusPx * t, amount)
        }
    }
}

/**
 * The rounded window [applyShrinkTransform] shows the shrinking page through:
 * a centred rounded rect, which is the whole node while the page is fullscreen
 * and the card's own box once it has landed.
 *
 * A [Shape] rather than a clip in the draw phase on purpose. A uniform rounded
 * rect — even one that doesn't cover the node — resolves to a plain
 * `RenderNode` outline, which the platform clips to in hardware and with
 * antialiasing; a path clip laid over the content would be neither, and this
 * one is over a live WebView on every frame of the gesture.
 *
 * A data class so that the frames where nothing moves (a settled card in the
 * switcher, a page at rest) hand Compose an outline equal to the one it
 * already resolved, and it re-resolves nothing.
 */
private data class ShrinkCropShape(
    private val widthPx: Float,
    private val heightPx: Float,
    private val radiusPx: Float,
    // How far the PICTURE hangs below the node (Aero's full-screen grab, the
    // strip under the glass toolbar). The window is centred on the picture,
    // not on the node, so it crops the two ends of the grab evenly.
    private val belowPx: Float = 0f,
    // The same above the node (the status bar strip); the picture's top is
    // at -abovePx.
    private val abovePx: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val above = abovePx.coerceAtLeast(0f)
        val pictureHeight = size.height + belowPx.coerceAtLeast(0f) + above
        val w = widthPx.coerceIn(0f, size.width)
        val h = heightPx.coerceIn(0f, pictureHeight)
        val left = (size.width - w) / 2f
        val top = -above + (pictureHeight - h) / 2f
        return Outline.Rounded(
            RoundRect(
                rect = Rect(left, top, left + w, top + h),
                cornerRadius = CornerRadius(radiusPx.coerceAtLeast(0f)),
            ),
        )
    }
}

/**
 * The switch between two tabs, drawn as the pages themselves: the pair steps
 * back from the glass a little, slides across by exactly one screen, and comes
 * back to the glass as it lands. The page being left goes out one side, the
 * one arriving comes in the other. Nothing here is interactive — the tab
 * itself only changes once this has finished (see `quickSwitchTabs` and
 * `swipeSwitchEnd`), so what is on screen for the whole animation is two baked
 * images.
 *
 * Both pages carry the SAME zoom on every frame and travel by the same amount,
 * which is what makes it read as one strip being pushed along rather than two
 * cards moving independently. A version with a perspective tilt and each card
 * at its own depth (the old recents drum) was here first and was deliberately
 * dropped: at full-screen size the near edge stretches, and the turn is a lot
 * of motion to spend on what is one step sideways.
 *
 * The zoom peaks in the middle and is zero at both ends, so the animation
 * begins and ends on a page at exactly the size and place the live one behind
 * it is at — there is no seam at the handoff in either direction. `direction`
 * says which side the incoming page enters from: fixed for the double-tap flip
 * (see QUICK_SWITCH_DIRECTION), per gesture for the sideways swipe.
 *
 * Both are placed by the distance between their CENTRES (see [separation]),
 * not by a screen width plus an offset. A page scales about its own centre, so
 * two of them a fixed distance apart pull their facing edges away from each
 * other as they zoom out — an 8% zoom on a 1080px page opens ~86px of
 * background between them, exactly in the middle of the slide where it is most
 * visible, and closes it again at the ends. Deriving the separation from the
 * live scale instead holds the strip between them at [QUICK_SWITCH_GAP] for
 * every frame.
 *
 * A tab with no preview yet (never captured, never restored) slides as its own
 * name and favicon on plain page background — the switcher's own label row,
 * centered. It is never a hole onto what's behind (the point of the cover is
 * that the WebView swap underneath is never seen) and it is never a blank
 * screen either, which is what a bare background read as for the whole
 * animation: the switch appeared to land on an empty page.
 */
@Composable
private fun QuickSwitchOverlay(
    outgoing: Tab?,
    incoming: Tab?,
    // Deferred, same as everywhere else here: the flip's value is only ever
    // consumed inside the two graphicsLayer blocks below.
    progress: () -> Float,
    // Which side the incoming card enters from — see quickSwitchDirection.
    // Not deferred: it is fixed for the whole of any one turn, and reading it
    // in composition is what lets the two layers agree on it.
    direction: Float = QUICK_SWITCH_DIRECTION,
) {
    if (outgoing == null && incoming == null) return
    val density = LocalDensity.current
    val ninety8 = LocalNinety8.current
    val cornerRadiusPx = with(density) { QUICK_SWITCH_CORNER.toPx() } * SpecialCornerScale
    val gapPx = with(density) { QUICK_SWITCH_GAP.toPx() }
    Box(Modifier.fillMaxSize()) {
        // What the pair slides in front of. Plain page background, not a
        // scrim: the live WebView underneath still holds the page being left,
        // untransformed, and it would otherwise show through the gap between
        // the two pages as a second, flat copy of the same page.
        Box(Modifier.fillMaxSize().background(PageBg))
        outgoing?.let { tab ->
            // Leaving: full screen and flat at 0, a whole separation off the
            // side by 1.
            QuickSwitchFace(tab, chromeAmount = { pullBack(progress()) }) {
                val progress = progress()
                val pulled = pullBack(progress)
                val scale = 1f - QUICK_SWITCH_ZOOM * pulled
                scaleX = scale
                scaleY = scale
                translationX = -direction * progress * separation(size.width, scale, gapPx)
                shape = RoundedCornerShape((cornerRadiusPx / scale) * pulled)
                clip = pulled > 0f && !ninety8
            }
        }
        incoming?.let { tab ->
            // Arriving: a whole screen width off to the side at 0 — genuinely
            // out of frame, so there's nothing to fade in — landing flat and
            // full size at 1.
            // The slide carries a little past its mark and comes back (the
            // overshoot in `progress` — see Motion.kt), and only the SLIDE is
            // allowed to. `remaining` goes slightly negative for those frames,
            // which is a few pixels of travel the other way on translationX
            // and exactly the weight the switch wants; fed to the zoom it
            // would be a page growing past full size, and — since a corner
            // radius cannot be negative — an invalid shape. `pullBack` clamps
            // for exactly that reason.
            QuickSwitchFace(tab, chromeAmount = { pullBack(progress()) }) {
                val remaining = 1f - progress()
                val pulled = pullBack(1f - remaining)
                val scale = 1f - QUICK_SWITCH_ZOOM * pulled
                scaleX = scale
                scaleY = scale
                translationX = direction * remaining * separation(size.width, scale, gapPx)
                shape = RoundedCornerShape((cornerRadiusPx / scale) * pulled)
                clip = pulled > 0f && !ninety8
            }
        }
    }
}

/**
 * How far into the pull-back the switch is at [progress] — 0 at both ends, 1
 * across the middle. A parabola rather than a triangle so the step back and
 * the return to the glass are both eased, and clamped because `progress`
 * carries slightly past 1 (see Motion.kt): unclamped this goes negative there,
 * i.e. a page briefly larger than the screen with a negative corner radius.
 */
/**
 * How far apart to hold the two pages' centres so that the visible strip
 * between their facing edges is exactly [gapPx] wide, whatever [scale] the
 * pair is currently zoomed to: each page shows `scale * width` of itself, so
 * the centres want that plus the gap. At rest (scale 1) that is one screen
 * plus the gap, i.e. the incoming page is genuinely out of frame.
 */
private fun separation(width: Float, scale: Float, gapPx: Float): Float =
    width * scale + gapPx

private fun pullBack(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 4f * p * (1f - p)
}


/** One page-sized still of [tab], transformed by [layer]. */
@Composable
private fun QuickSwitchFace(
    tab: Tab,
    chromeAmount: () -> Float,
    layer: GraphicsLayerScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(layer)
            // Inside the moving layer: each page carries its own frame and
            // shadow instead of leaving stationary chrome behind.
            .quickSwitchChrome98(chromeAmount)
            .background(PageBg),
    ) {
        val preview = tab.thumbnail
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // The same row the card in the switcher wears above its preview,
            // so a tab with no picture still turns as recognisably that tab.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                TabLabelRow(tab = tab, width = maxWidth)
            }
        }
    }
}

@Composable
private fun Modifier.quickSwitchChrome98(amount: () -> Float): Modifier {
    if (!LocalNinety8.current) return this
    val colors = bevel98Colors()
    return drawWithContent {
        val a = amount().coerceIn(0f, 1f)
        if (a <= 0.004f) {
            drawContent()
            return@drawWithContent
        }
        val band = BEVEL_BAND.toPx()
        val inset = (band * 2f * a).coerceAtMost(size.minDimension / 2f)

        fun drawBand(topLeft: Color, bottomRight: Color, pad: Float) {
            val width = (size.width - pad * 2f).coerceAtLeast(0f)
            val height = (size.height - pad * 2f).coerceAtLeast(0f)
            val thickness = band.coerceAtMost(width).coerceAtMost(height)
            drawRect(topLeft.copy(alpha = topLeft.alpha * a), Offset(pad, pad), Size(width, thickness))
            drawRect(topLeft.copy(alpha = topLeft.alpha * a), Offset(pad, pad + thickness), Size(thickness, (height - thickness).coerceAtLeast(0f)))
            drawRect(bottomRight.copy(alpha = bottomRight.alpha * a), Offset(pad, pad + height - thickness), Size(width, thickness))
            drawRect(bottomRight.copy(alpha = bottomRight.alpha * a), Offset(pad + width - thickness, pad), Size(thickness, (height - thickness).coerceAtLeast(0f)))
        }

        drawBand(colors.hilight, colors.dark, 0f)
        drawBand(colors.light, colors.shadow, band)
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            this@drawWithContent.drawContent()
        }
    }
}

/**
 * The static counterpart to [WebViewHost] — same shrink/grow transform and ring,
 * but drawing the tab's last-captured [Tab.thumbnail] instead of the live page.
 * Used for the tabs-button-driven open/close animation (see `liveVisible` at the
 * call site), so that transition never shows an interactive, scrollable page —
 * only ever a baked image shrinking or growing in place.
 */
/**
 * Status/nav bar icon contrast, which has to track the app's own resolved
 * theme (which can be pinned to Light or Dark regardless of the system
 * setting) rather than the system's — not something the static XML theme can
 * express, so it's set imperatively.
 *
 * Its own composable purely to contain a recomposition: it reads
 * [LocalChromeDarkness], which ticks every frame while the chrome turns
 * between light and dark, and reading that from BrowserScreen's root scope
 * would recompose the entire screen on each of those frames. The icons flip
 * at the halfway point rather than at the start — light icons over a bar
 * that is still light would be invisible for the first half of the turn.
 */
@Composable
internal fun SystemBarIcons(
    ordinaryDark: Boolean,
    ignorePrivacy: Boolean,
    // True while the page lens has painted the status bar's strip black
    // under a live page: that strip wants light icons whatever the theme.
    blackStatusBar: Boolean = false,
    // The same for the navigation bar, which sits over the lens's black
    // toolbar (or the page's black bottom edge once the toolbar has slid).
    blackNavigationBar: Boolean = false,
    // What the page shows under the status bar (see PageTopInset.setStrip):
    // true dark, false light, null not the page's strip — follow the theme.
    pageStatusDark: Boolean? = null,
) {
    // Settings re-provides the app's own theme over the private one (see its
    // AnimatedVisibility below), and it covers the whole screen — bars
    // included — so while it's up the icons have to follow that theme rather
    // than the violet underneath it. Reading the flag instead of the local in
    // that case also keeps this from subscribing to a turn it isn't following.
    val dark = if (ignorePrivacy) ordinaryDark else LocalChromeDarkness.current > 0.5f
    val view = LocalView.current
    val aero = com.yuku.browser.ui.theme.LocalAero.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // The browser deliberately supplies the complete colour behind
            // both transparent system bars. Leaving the platform's automatic
            // status-bar contrast scrim enabled adds a black shade over that
            // paint (most obvious as a green-to-black "gradient" on a site's
            // solid masthead). Icon contrast is selected explicitly below.
            window.isStatusBarContrastEnforced = false
            // Same rule at the bottom: this window supplies the surface under
            // the transparent navigation bar, and the platform scrim is the
            // grey band that otherwise appears behind the gesture handle.
            window.isNavigationBarContrastEnforced = false
        }
        // `enableEdgeToEdge()` establishes this initially, but system/theme
        // changes can replace it. Reassert it for every browser look, not just
        // Aero, or ordinary themes regain a grey navigation-bar background.
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = when {
            blackStatusBar -> false
            pageStatusDark != null -> !pageStatusDark
            else -> !dark
        }
        controller.isAppearanceLightNavigationBars = !dark && !blackNavigationBar
    }
}

/**
 * A tab's last preview, held at full screen over its live WebView while that
 * WebView loads the page again from nothing — see
 * `BrowserViewModel.coveredTabIds` for why, and `PAGE_COVER_MAX_MS`
 * for how long at the outside.
 *
 * Deliberately has no pointer input of its own: a touch during the cover goes
 * through to the page underneath, which is the live one and is where the
 * touch belongs — the cover is a picture of that same page, so what the user
 * aims at is what they hit.
 *
 * There's no enter transition. The cover exists from the app's very first
 * frame; fading it IN would mean fading up from the blank it's there to hide.
 */
@Composable
private fun PageCover(
    tab: Tab,
    visible: Boolean,
    onGiveUp: () -> Unit,
    lensDepthPx: Float = 0f,
    // How far the live view hangs below this box (see AnimatedThumbnailHost).
    lensBelowPx: Float = 0f,
) {
    val bitmap = tab.thumbnail
    LaunchedEffect(visible, bitmap == null) {
        if (!visible) return@LaunchedEffect
        // A tab with no saved preview has nothing to cover with, and holding
        // the flag for it would only keep pageExposed false — i.e. stop the
        // page being captured — for as long as the timeout runs.
        if (bitmap == null) {
            onGiveUp()
            return@LaunchedEffect
        }
        delay(PAGE_COVER_MAX_MS)
        // This is a visual watchdog, not a navigation deadline. A slow page
        // remains allowed to finish after its stale preview is taken away.
        onGiveUp()
    }
    AnimatedVisibility(
        visible = visible && bitmap != null,
        enter = EnterTransition.None,
        exit = fadeOut(tween(PAGE_COVER_FADE_MS)),
    ) {
        // Read again inside the content: during the fade the flag is already
        // false, and this lambda keeps composing until the fade ends.
        tab.thumbnail?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                // The picture is flat (see PageLens.unwarp) and stands over a
                // full-size page, which the lens curves at full strength.
                modifier = Modifier.fillMaxSize().pageLens(lensDepthPx, lensBelowPx) { 1f },
            )
        }
    }
}

/**
 * The picture of the tab being left, over the tab being arrived at, fading
 * out — the whole of the tab list's handoff (see selectTabFromRow).
 *
 * Nothing at all when the outgoing tab has no picture, which is every private
 * tab (they are excluded from the thumbnail store on purpose) and any tab
 * whose capture never landed. A crossfade from a placeholder would be a fade
 * from a blank rectangle, which is worse than the cut it replaces.
 *
 * [alpha] is deferred and read in the draw phase, like every other per-frame
 * value here.
 */
@Composable
private fun ListSwitchFade(outgoing: Tab?, alpha: () -> Float) {
    val bitmap = outgoing?.thumbnail ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Matched to [AnimatedThumbnailHost]'s, so the image the incoming tab
        // is covered by and the one it is uncovered from are cropped the same
        // way — anything else is a jump at the moment the fade ends.
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() },
    )
}

@Composable
private fun AnimatedThumbnailHost(
    tab: Tab,
    fullGrab: Boolean = false,
    // Deferred, exactly as in [WebViewHost] — and it must be, or the live
    // element and its stand-in would be reading the same animation on
    // different phases.
    progress: () -> Float,
    targetRect: Rect?,
    offsetX: () -> Float,
    offsetY: () -> Float,
    visible: Boolean,
    // The page lens's bend depth, or 0 when it is off (see PageLens).
    lensDepthPx: Float = 0f,
    // How far the live WebView hangs below this box (the toolbar's strip):
    // the live lens bends at THAT bottom, so this picture does too.
    lensBelowPx: Float = 0f,
    // The status bar strip, as in [WebViewHost]: the live page's picture
    // runs up over it, so this one does too — extended in the preview's top
    // edge colour (see PageTopStrip), or the handoff uncovers a bare band.
    stripAbovePx: Float = 0f,
    stripFadePx: Float = 0f,
    stripReports: kotlinx.coroutines.flow.StateFlow<Map<Long, com.yuku.browser.core.StatusStripReport>>? = null,
    statusBarBlur: Boolean = true,
) {
    // Before the early return, so the paint (and its eased colours) outlives
    // the frames this stand-in is not shown.
    val stripPaint = if (stripReports != null) rememberStatusStripPaint(stripReports, tab.id) else null
    if (!visible) return
    val aero = com.yuku.browser.ui.theme.LocalAero.current
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 16.dp.toPx() } * SpecialCornerScale
    val hostView = LocalView.current
    val cardMarks = com.yuku.browser.ui.theme.rememberAeroCardMarks()
    // Under Aero the stand-in is the FULL-SCREEN grab — the page box and the
    // strip under the glass toolbar in one picture — so the card grows into
    // exactly what the screen shows rather than a page box with a strip
    // uncovered under it. It hangs below the node the way the live WebView
    // does, and the shrink is measured against the whole of it.
    val full = tab.thumbnailFull?.takeIf { fullGrab && !it.isRecycled && it.width > 0 }
    val bitmap = full ?: tab.thumbnail ?: return
    val stripColour = remember(bitmap, stripAbovePx > 0f) {
        if (stripAbovePx > 0f) bitmap.topEdgeColour() else Color.Transparent
    }
    // The page itself under the status bar, when it was captured with it.
    val stripImage = remember(tab.thumbnailTop) {
        tab.thumbnailTop?.takeUnless { it.isRecycled }?.asImageBitmap()
    }
    Box(
        Modifier
            .fillMaxSize()
            .ninety8ShrinkChrome(
                visible = true,
                targetRect = targetRect,
                progress = progress,
                offsetX = offsetX,
                offsetY = offsetY,
                belowPx = { width, height ->
                    if (full != null && width > 0f) {
                        (full.height * width / full.width - height).coerceAtLeast(0f)
                    } else 0f
                },
                abovePx = stripAbovePx,
            )
            .graphicsLayer {
                val shrinkProgress = shrinkOf(progress())
                val below = if (full != null && size.width > 0f) {
                    (full.height * size.width / full.width - size.height).coerceAtLeast(0f)
                } else 0f
                applyShrinkTransform(
                    targetRect, offsetX(), offsetY(), shrinkProgress, cornerRadiusPx, below, stripAbovePx,
                    fullCornerPx = lensCornerFor(hostView, lensDepthPx) * LENS_CROP_CORNER,
                )
                if (aero) shadowElevation = 8.dp.toPx() * shrinkProgress.coerceIn(0f, 1f)
                // In step with the live element's, or the handoff
                // between the two would be a jump in focus.
                privateShrinkBlur(tab.isPrivate, shrinkProgress)
            }
            // The drawn strip, as over the live page: none on the card, all of
            // it at full screen — the picture under it is the page alone.
            .then(
                if (stripPaint == null || stripAbovePx <= 0f) Modifier
                else Modifier.drawWithContent {
                    drawContent()
                    drawStatusStrip(
                        stripPaint, stripAbovePx,
                        1f - shrinkOf(progress()).coerceIn(0f, 1f),
                        softenWipeEdge = statusBarBlur,
                    )
                }
            )
            .then(
                if (cardMarks == null) Modifier
                else Modifier.drawWithContent {
                    drawContent()
                    val below = if (full != null && size.width > 0f) {
                        (full.height * size.width / full.width - size.height).coerceAtLeast(0f)
                    } else 0f
                    drawShrinkCardMarks(cardMarks, targetRect, shrinkOf(progress()), below, cornerRadiusPx, stripAbovePx)
                }
            )
            // In step with the live page's, or the handoff would blink it.
            .tuiShrinkOutline(targetRect, progress, 0f, stripAbovePx)
            // The strip, above the node and inside the layer, so it rides
            // the same zoom and the same crop as the picture under it.
            .then(
                if (stripAbovePx <= 0f) Modifier
                else Modifier.drawBehind {
                    val image = stripImage
                    if (image != null) {
                        drawImage(
                            image,
                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset(0, -stripAbovePx.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), stripAbovePx.toInt()),
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                        )
                    } else {
                        drawRect(stripColour, Offset(0f, -stripAbovePx), Size(size.width, stripAbovePx))
                    }
                }
            ),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                // Measured at the grab's own aspect and placed at the top;
                // the parent keeps reporting its own size (see WebViewHost's
                // AndroidView for the same trick).
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = if (full != null) {
                        (full.height.toLong() * w / full.width).toInt().coerceAtLeast(constraints.maxHeight)
                    } else constraints.maxHeight
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                }
                // Do not refract the travelling preview.  A settled Aero card
                // has a fixed rim, so the small edge bend in TabSwitcher is
                // contained at that rim.  Here the crop boundary travels
                // across the captured page as a card is expanded; refracting
                // against that moving boundary bends whichever glyphs it
                // crosses and reads as the page text stretching under a slow
                // drag.  The outer card marks still provide the glass cue,
                // while the page image itself keeps a single, uniform scale.
                .pageLens(lensDepthPx, lensBelowPx) { 1f - shrinkOf(progress()) },
        )
    }
}

/**
 * SwipeRefreshLayout decides whether a downward drag should pull-to-refresh
 * by asking whether its direct child can still scroll up. [webContainer] —
 * the stable wrapper this holds so the WebView can be reparented into it
 * without recreating it (see [WebViewHost]) — never scrolls itself, so that
 * check is delegated to the actual WebView living inside it instead; that's
 * what makes the gesture only trigger once the page itself is scrolled to
 * the top, rather than always.
 *
 * That check alone is not enough, though, because it only knows about the
 * page's *outermost* scroll. Two very common things it says nothing about:
 *
 *  - A panel, drawer, feed or comment list that scrolls inside the page
 *    (an `overflow: auto` box). The document itself never moves, so the
 *    WebView reports "at the top" forever and every downward flick inside
 *    that box reloaded the page instead of scrolling it back up. Whole web
 *    apps are built this way (`body { overflow: hidden }` + one scrolling
 *    container), where the pull hijacked essentially all scrolling.
 *  - A sideways gesture — carousels, swipeable menus, image galleries,
 *    maps. SwipeRefreshLayout only ever looks at the vertical component of
 *    a drag, so a swipe that drifts a few degrees below horizontal was
 *    taken as a pull and the sideways gesture died mid-swipe.
 *
 * So two extra gates sit in front of it:
 *
 *  - **Direction lock.** The first movement past touch slop decides the
 *    gesture: predominantly horizontal means the page owns it and this
 *    layout stays out of the way for the rest of the gesture. Latched, not
 *    re-evaluated per event, so a pull that curves doesn't flip ownership
 *    halfway.
 *  - **A hit test in the page.** On every touch-down the element under the
 *    finger is probed (see [probePullBlocked]) for an ancestor that is
 *    itself scrolled down, that has claimed the gesture via `touch-action`,
 *    or that registered a non-passive touch listener (see [PullGate]) — the
 *    standard way a page says it means to handle panning itself, and what
 *    catches the hand-written swipe gestures that own no scroller and set no
 *    `touch-action`: a lightbox that swipes away, a bottom sheet, a slider.
 *    Any of them means the page has something to do with this drag, and the
 *    pull stands down.
 *  - **A prevented touch move**, reported out of the renderer as it happens.
 *    Retrospective by nature, so it's the one gate that can arrive after the
 *    pull has started — hence the mid-gesture bail in [onTouchEvent], which
 *    retracts the spinner rather than reloading a page the user was swiping
 *    something on.
 *
 * The probe is asynchronous — it's a round trip into the renderer — and the
 * answer normally lands long before the finger has moved the ~24dp that
 * makes the decision matter. If it doesn't, the gesture just behaves as it
 * did before: the flag is only ever read, never waited on, because blocking
 * touch dispatch on the renderer is exactly the stall this gesture cannot
 * afford.
 *
 * SwipeRefreshLayout also adds its own progress-circle view as a child in
 * its constructor, ahead of anything added afterward — so [webContainer]
 * can't be identified later by child index, only by keeping this direct
 * reference to the exact instance created here.
 */
internal class WebViewSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    // Hands the page each touch where the page lens drew it (see PageLens).
    val webContainer = PageLensTouchContainer(context).also {
        addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    /** The top offset the spinner was last placed for (see WebViewHost). */
    var spinnerTopOffsetPx = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var directionDecided = false
    private var horizontalDrag = false
    /** Set from the renderer's answer to [probePullBlocked] for the current gesture. */
    private var contentOwnsGesture = false
    /** Bumped per touch-down, so a probe answering late can tell it's stale. */
    private var gestureId = 0
    /** [MotionEvent.getDownTime] of the gesture in progress, or 0 between them. */
    private var gestureDownTime = 0L
    private var cancelledForPage = false

    /**
     * Called once per gesture, the moment a drag is recognised as a pull —
     * before the spinner has been drawn — so the reload it may end in can be
     * held on a picture of the page without the spinner in it.
     */
    var onPullStarting: (() -> Unit)? = null
    /** Called when a non-refreshing pull has settled back out. */
    var onPullFinished: (() -> Unit)? = null
    private var pullNoticed = false

    private fun finishUncommittedPull() {
        if (!pullNoticed) return
        // SwipeRefreshLayout invokes its listener while processing the up
        // event. Post this check so a committed refresh keeps its circle,
        // while a released partial pull gives the status-strip veil back.
        post {
            if (!isRefreshing) onPullFinished?.invoke()
        }
        pullNoticed = false
    }

    private val web: WebView? get() = webContainer.getChildAt(0) as? WebView

    /** Everything that says this drag is the page's, not the browser's. */
    private fun pageOwnsGesture(): Boolean =
        horizontalDrag || contentOwnsGesture || PullGate.preventedSince(gestureDownTime)

    override fun canChildScrollUp(): Boolean {
        // Consulted by super on touch-down, i.e. usually before the probe has
        // answered — it's here for the case where it has, not as the main gate.
        if (contentOwnsGesture) return true
        val child = web ?: return super.canChildScrollUp()
        return child.canScrollVertically(-1)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                directionDecided = false
                horizontalDrag = false
                contentOwnsGesture = false
                cancelledForPage = false
                pullNoticed = false
                gestureDownTime = ev.downTime
                gestureId++
                if (isEnabled) probePullBlocked(ev.x, ev.y)
            }

            MotionEvent.ACTION_MOVE -> if (!directionDecided) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) >= touchSlop || abs(dy) >= touchSlop) {
                    directionDecided = true
                    horizontalDrag = abs(dx) > abs(dy)
                    if (!pullNoticed && isEnabled && !isRefreshing && dy > 0 &&
                        !pageOwnsGesture() && !canChildScrollUp()
                    ) {
                        pullNoticed = true
                        onPullStarting?.invoke()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishUncommittedPull()
        }
        // Deliberately skipping super entirely rather than returning its
        // answer: super must not see the move stream of a gesture it doesn't
        // own, or it accumulates drag distance and grabs the pointer the
        // moment the finger happens to travel downward.
        if (pageOwnsGesture()) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Reached once this layout owns the pointer (or when the page didn't
        // consume the gesture at all). A page can still speak up after that —
        // the first move it prevents may be the first one it saw — so the
        // pull is abandoned here too, by handing super a cancel: that settles
        // the spinner back without firing the refresh listener. The page's
        // own gesture is already lost by then, which is exactly why the two
        // gates that CAN answer up front matter; this is the net under them.
        if (pageOwnsGesture()) {
            if (!cancelledForPage) {
                cancelledForPage = true
                val cancel = MotionEvent.obtain(ev)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.onTouchEvent(cancel)
                cancel.recycle()
            }
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                finishUncommittedPull()
            }
            return false
        }
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishUncommittedPull()
        }
        return handled
    }

    /**
     * Asks the page whether the element under ([x], [y]) — view pixels — sits
     * inside something that wants this drag: a scroll container that isn't at
     * its own top, an element whose `touch-action` says it handles panning
     * itself (maps, sliders, swipe menus), or one that registered a
     * non-passive touch listener ([PullGate]). Answers into
     * [contentOwnsGesture].
     *
     * `elementFromPoint` takes CSS pixels relative to the viewport, so the
     * view coordinate is divided by the page's current scale (and not offset
     * by scrollX/Y — the viewport is what it's relative to, not the document).
     * Shadow roots are stepped into on the way down and back out of on the way
     * up, since a custom element would otherwise hide its own scroller.
     */
    private fun probePullBlocked(x: Float, y: Float) {
        val child = web ?: return
        val asked = gestureId
        @Suppress("DEPRECATION")
        val scale = child.scale.takeIf { it > 0f } ?: resources.displayMetrics.density
        val cssX = x / scale
        val cssY = y / scale
        val js = """
            (function(){try{
              var x=$cssX, y=$cssY;
              var e=document.elementFromPoint(x,y);
              while(e&&e.shadowRoot&&e.shadowRoot.elementFromPoint){
                var inner=e.shadowRoot.elementFromPoint(x,y);
                if(!inner||inner===e)break;
                e=inner;
              }
              for(var i=0;e&&i<64;i++){
                if(e['${PullGate.TOUCH_FLAG}'])return true;
                var s=window.getComputedStyle(e);
                var ta=(s.touchAction||s.msTouchAction||'').trim();
                if(ta==='none'||ta==='pinch-zoom'||ta.indexOf('pan-x')===0)return true;
                var oy=s.overflowY;
                if((oy==='auto'||oy==='scroll'||oy==='overlay')&&
                   e.scrollHeight-e.clientHeight>1&&e.scrollTop>0)return true;
                e=e.parentElement||(e.getRootNode&&e.getRootNode().host)||null;
              }
              return false;
            }catch(err){return false;}})()
        """.trimIndent()
        child.evaluateJavascript(js) { result ->
            // Only for the gesture that asked. A probe from the previous
            // touch can land after the next one has begun, and applying it
            // would block a drag whose own probe said the opposite.
            if (asked == gestureId && result == "true") contentOwnsGesture = true
        }
    }
}

/**
 * The current tab's name and favicon, tracking WebViewHost's own live position
 * and size rather than sitting statically in the switcher's grid — so while
 * it's floating with a drag or animating to its settle point, the label moves
 * and fades right along with the content instead of waiting for it in place.
 */
@Composable
private fun FloatingTabLabel(
    tab: Tab,
    progress: () -> Float,
    targetRect: Rect,
    offsetX: () -> Float,
    offsetY: () -> Float,
    fullWidthPx: Float,
    fullHeightPx: Float,
) {
    // The one place the animation IS read in composition, unavoidably: the
    // label's width is a layout input (TabLabelRow measures its text to it),
    // not something a transform can express — a scaled label would render its
    // text at the wrong size and then have to un-scale it back.
    //
    // It is read HERE, though, inside this small composable's own scope,
    // rather than by the caller. That is what confines the per-frame
    // recomposition to a row holding a favicon and one line of text, instead
    // of taking the whole screen with it.
    val progressNow = progress()
    if (progressNow <= 0f || fullWidthPx <= 0f || fullHeightPx <= 0f) return
    val density = LocalDensity.current
    // Clamped, unlike everything else that reads the shrink: this one is a
    // LAYOUT input (see below), and following the inertia band would re-measure
    // the label's text for a sub-pixel excursion at the very end of a settle.
    val shrinkProgress = shrinkOf(progressNow).coerceIn(0f, 1f)

    val currentWidth = fullWidthPx + (targetRect.width - fullWidthPx) * shrinkProgress
    val currentHeight = fullHeightPx + (targetRect.height - fullHeightPx) * shrinkProgress
    // Tracks WebViewHost's own translation exactly — offsetX/offsetY are an
    // offset from screen center, same as this label's natural (untranslated)
    // center would be.
    val currentCenterX = fullWidthPx / 2f + offsetX()
    val currentCenterY = fullHeightPx / 2f + offsetY()
    val currentLeft = currentCenterX - currentWidth / 2f
    val currentTop = currentCenterY - currentHeight / 2f
    // The exact same gap the static card leaves above its thumbnail, measured
    // from the same edge — this label and the card's own are the same
    // composable drawn the same distance above the same rect, so when the
    // content settles into its slot and the two swap over there is nothing
    // left to jump.
    val labelBlockPx = with(density) { TAB_TITLE_BLOCK_HEIGHT.toPx() }
    val currentWidthDp = with(density) { currentWidth.toDp() }

    TabLabelRow(
        tab = tab,
        width = currentWidthDp,
        modifier = Modifier
            .offset { IntOffset(currentLeft.roundToInt(), (currentTop - labelBlockPx).roundToInt()) }
            .graphicsLayer { alpha = shrinkProgress },
    )
}

/**
 * A full-screen destination arriving over the page: Settings, Bookmarks,
 * History, Downloads, the ad blocker. It rises from a third of a screen below,
 * fades in, carries a little past where it lands and settles onto it — the
 * same motion a new tab's entrance uses, from the same constants (see
 * Motion.kt and NEW_TAB_ENTER_RISE_FRACTION).
 *
 * Hand-rolled rather than [AnimatedVisibility], for one reason: an
 * AnimatedVisibility composes its content on the very frame the animation
 * starts from. These screens are lists, several dozen rows of text and icons
 * each, and composing one costs tens of milliseconds against an 8.3ms frame —
 * so the first thing every one of them did was visibly hitch, exactly as the
 * sheet did before its own pre-warm frame (see sheetContentHeightPx in
 * BrowserScreen, which is the same fix).
 *
 * Here that frame is the one where [rise] is still 0: the content is composed
 * and laid out at full size, and the draw is skipped outright — not merely
 * drawn at alpha 0, which would still rasterise the whole screen on the frame
 * that can least afford it. Everything after it is placement and alpha.
 *
 * Slide and fade run on their own durations, the way AnimatedVisibility runs
 * its two specs independently, and only the slide takes the overshoot: alpha
 * has nowhere past 1 to overshoot to.
 */
@Composable
private fun Destination(
    visible: Boolean,
    onBounds: (Rect) -> Unit = {},
    onPaneAlpha: (() -> Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Read in composition; the draw lambda below is not a composable scope.
    val destinationBg = BarBg
    // Stays true through the exit animation, and only then drops the content.
    var mounted by remember { mutableStateOf(visible) }
    val rise = remember { Animatable(if (visible) 1f else 0f) }
    val fade = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            // The pre-warm frame. Nothing is moving on it and nothing is
            // drawn; it exists purely to pay for the composition.
            withFrameNanos { }
            launch { fade.animateTo(1f, tween(SURFACE_ENTER_FADE_MS)) }
            rise.animateTo(1f, arrive(SURFACE_ENTER_MS))
        } else {
            if (!mounted) return@LaunchedEffect
            launch { fade.animateTo(0f, tween(SURFACE_EXIT_FADE_MS)) }
            rise.animateTo(0f, depart(SURFACE_EXIT_MS))
            mounted = false
        }
    }
    if (!mounted) return
    SideEffect { onPaneAlpha { fade.value.coerceIn(0f, 1f) } }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            onBounds(Rect.Zero)
            onPaneAlpha { 0f }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                onBounds(
                    Rect(
                        coordinates.localToRoot(Offset.Zero),
                        coordinates.localToRoot(
                            Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                        ),
                    ),
                )
            }
            .graphicsLayer {
                // Overshoots past 1, which puts this a few pixels ABOVE where
                // it comes to rest before it drops back — hence no clamp.
                translationY = size.height * NEW_TAB_ENTER_RISE_FRACTION * (1f - rise.value)
                alpha = fade.value.coerceIn(0f, 1f)
            }
            // The overshoot puts this a few pixels above where it lands,
            // which would uncover a strip of the page along the bottom edge
            // of the screen for the length of the settle. Every one of these
            // screens is BarBg from edge to edge, so its own surface is
            // extended past its bottom edge to fill that strip — drawn inside
            // the layer, so it travels with the screen, and nothing here
            // clips, so it reaches beyond the box's own bounds.
            .drawBehind {
                drawRect(
                    color = destinationBg,
                    size = Size(size.width, size.height * OVERSHOOT_PEAK),
                )
            }
            // See above: the warm frame lays out but does not rasterise.
            .drawWithContent { if (fade.value > 0f) drawContent() }
            .tuiCrtIf()
            .tuiBloomIf(),
    ) {
        content()
    }
}

/**
 * The TUI's card outline (see `tuiScreenIf`) on the page while it SHRINKS
 * into its card, so the rule comes in progressively instead of appearing at
 * the handoff. Drawn inside the shrink layer, after its transform: the same
 * window [ShrinkCropShape] crops to, inset by half a stroke so the clip does
 * not eat its outer half, with the stroke divided by the live scale so it
 * lands at 1dp on screen. Nothing at fullscreen, full at the card.
 */
@Composable
private fun Modifier.tuiShrinkOutline(targetRect: Rect?, progress: () -> Float, belowPx: Float, abovePx: Float = 0f): Modifier {
    if (!com.yuku.browser.ui.theme.LocalTui.current) return this
    val ink = Ink
    return drawWithContent {
        drawContent()
        val p = shrinkOf(progress()).coerceIn(0f, 1f)
        if (p <= 0.004f) return@drawWithContent
        val g = shrinkGeometry(size.width, size.height, targetRect, p, belowPx, abovePx)
        val sw = 0.5.dp.toPx() / g.scale.coerceAtLeast(0.0001f)
        val w = g.windowWidth.coerceIn(0f, size.width)
        val h = g.windowHeight.coerceIn(0f, g.pictureHeight)
        val left = (size.width - w) / 2f
        val top = g.windowTop(h)
        drawSoftRule(ink, 0.85f * p, left + sw / 2f, top + sw / 2f, w - sw, h - sw, sw, com.yuku.browser.ui.theme.SOFT_RULE_BLUR.toPx() / g.scale.coerceAtLeast(0.0001f))
    }
}

/**
 * The 98 frame around a page becoming a tab preview.
 *
 * This wraps the shrink layer instead of drawing inside it. That gives it the
 * final screen-space crop rectangle—including the independently growing
 * status-bar strip—so the top edge follows the preview's faster expansion.
 * The frame is painted first and the page is clipped inward over it: chrome
 * stays underneath page pixels while being progressively revealed.
 */
@Composable
private fun Modifier.ninety8ShrinkChrome(
    visible: Boolean,
    targetRect: Rect?,
    progress: () -> Float,
    offsetX: () -> Float,
    offsetY: () -> Float,
    belowPx: (width: Float, height: Float) -> Float,
    abovePx: Float = 0f,
): Modifier {
    if (!LocalNinety8.current || !visible) return this
    val colors = bevel98Colors()
    return drawWithContent {
        val p = shrinkOf(progress()).coerceIn(0f, 1f)
        if (p <= 0.004f) {
            drawContent()
            return@drawWithContent
        }

        val g = shrinkGeometry(size.width, size.height, targetRect, p, belowPx(size.width, size.height), abovePx)
        val localWidth = g.windowWidth.coerceIn(0f, size.width)
        val localHeight = g.windowHeight.coerceIn(0f, g.pictureHeight)
        if (localWidth <= 0f || localHeight <= 0f) {
            drawContent()
            return@drawWithContent
        }
        val scale = g.scale.coerceAtLeast(0.0001f)
        val localLeft = (size.width - localWidth) / 2f
        val localTop = g.windowTop(localHeight)
        val translationY = offsetY() - (g.below - g.above) / 2f * scale * p
        val left = size.width / 2f + (localLeft - size.width / 2f) * scale + offsetX()
        val top = size.height / 2f + (localTop - size.height / 2f) * scale + translationY
        val width = localWidth * scale
        val height = localHeight * scale
        val bandPx = BEVEL_BAND.toPx()

        // The top of a transparent-status-bar page reaches the screen edge
        // before the other three sides reach fullscreen. Fade and cover that
        // edge from its actual remaining travel, so it vanishes on arrival
        // instead of hanging over the status bar until global progress is 0.
        val targetTop = targetRect?.top ?: 0f
        val topAmount = if (targetTop > 1f) (top / targetTop).coerceIn(0f, 1f) else p

        fun drawBand(topLeft: Color, bottomRight: Color, inset: Float) {
            val innerWidth = (width - inset * 2f).coerceAtLeast(0f)
            val innerHeight = (height - inset * 2f).coerceAtLeast(0f)
            if (innerWidth <= 0f || innerHeight <= 0f) return
            val thickness = bandPx.coerceAtMost(innerWidth).coerceAtMost(innerHeight)
            drawRect(topLeft.copy(alpha = topLeft.alpha * topAmount), Offset(left + inset, top + inset), Size(innerWidth, thickness))
            drawRect(topLeft.copy(alpha = topLeft.alpha * p), Offset(left + inset, top + inset + thickness), Size(thickness, (innerHeight - thickness).coerceAtLeast(0f)))
            drawRect(bottomRight.copy(alpha = bottomRight.alpha * p), Offset(left + inset, top + inset + innerHeight - thickness), Size(innerWidth, thickness))
            drawRect(bottomRight.copy(alpha = bottomRight.alpha * p), Offset(left + inset + innerWidth - thickness, top + inset), Size(thickness, (innerHeight - thickness).coerceAtLeast(0f)))
        }

        drawBand(colors.hilight, colors.dark, 0f)
        drawBand(colors.light, colors.shadow, bandPx)

        // Reveal the frame from underneath by pulling the preview's edge in
        // on the same curve. At the card endpoint this is exactly the inset
        // used by TabCard; at fullscreen it is zero.
        val previewInset = (bandPx * 2f * p).coerceAtMost(width / 2f).coerceAtMost(height / 2f)
        val previewTopInset = (bandPx * 2f * topAmount).coerceAtMost(height / 2f)
        clipRect(
            left = left + previewInset,
            top = top + previewTopInset,
            right = left + width - previewInset,
            bottom = top + height - previewInset,
        ) {
            this@drawWithContent.drawContent()
        }
    }
}
