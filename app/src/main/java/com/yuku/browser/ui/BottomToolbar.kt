package com.yuku.browser.ui

import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.frostedAccentIf
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.BEVEL_BAND
import com.yuku.browser.ui.theme.Bevel
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.LocalTui
import com.yuku.browser.ui.theme.Glassy
import com.yuku.browser.ui.theme.LocalAero
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.aeroPopIf
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiCrtIf
import com.yuku.browser.ui.theme.tuiGlowAroundIf
import com.yuku.browser.ui.theme.Text
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// How long a single tap on the tabs button waits to find out it was really
// the first half of a double tap. Far below the platform's 300ms default: on
// a button whose ordinary job is "open the tab manager", any wait at all
// reads as a stall, so this is tuned for the single tap rather than for
// catching every double tap — a slow double tap lands outside the window and
// simply opens the switcher instead.
private const val TABS_DOUBLE_TAP_TIMEOUT_MS = 100L

// The loading line's clock — see [LoadingLine]. Chrome's own numbers, scaled
// to a 2dp line: its ALPHA_ANIMATION_DURATION_MS is 140 (kept), its
// FINISH_ANIMATION_DURATION_MS 1000 and its hide delay 100-300.
private const val LOAD_FADE_MS = 140
private const val LOAD_FAST_START = 0.08f
private const val LOAD_FAST_START_MS = 140
private const val LOAD_STEP_MS = 380
private const val LOAD_FINISH_MS = 220
private const val LOAD_HOLD_MS = 120L
// Chrome's ANIMATION_START_THRESHOLD, unchanged: below five seconds this
// fires between an ordinary page's own progress reports.
private const val STALL_AFTER_MS = 5000L
private const val STALL_SWEEP_MS = 900
private const val STALL_SWEEP_GAP_MS = 400
private const val STALL_SEGMENT_FRACTION = 0.35f
private const val STALL_SEGMENT_ALPHA = 0.45f

/**
 * The ambient [ViewConfiguration] with only the double-tap window shortened.
 * `detectTapGestures` reads its timeout from whatever configuration is in
 * scope, so this is the supported way to change it for one control without
 * hand-rolling the gesture — everything else (touch slop, long press) keeps
 * the platform's own values, which the rest of the toolbar shares.
 */
private class FastDoubleTapViewConfiguration(
    private val base: ViewConfiguration,
) : ViewConfiguration by base {
    override val doubleTapTimeoutMillis: Long get() = TABS_DOUBLE_TAP_TIMEOUT_MS
}

/**
 * What a drag starting on one of the toolbar's end buttons turned out to be.
 * The tabs button carries two gestures on one node — drag the page up into
 * the switcher, or swipe sideways to the next tab along — so the first
 * movement past the dead zone decides which, once, and the rest of the
 * gesture is that one. [Rejected] is a horizontal drag with no tab to go to
 * (or the wrong way round): it must still be swallowed for the whole
 * gesture rather than left to be re-tested a few pixels later, or a swipe
 * against the end of the row would turn into a switcher drag halfway through.
 */
private enum class ToolbarDrag { None, Rejected, Switcher, Swipe }

/**
 * How far inside its touch target a toolbar button is DRAWN under the 98
 * theme — see `Modifier.bevel98`'s `inset`.
 *
 * The three controls are 48dp boxes in a 56dp bar, which is the right target
 * and the wrong drawing: a 48dp button leaves 4dp of bar above it, so its
 * lit top edge lands almost on the bar's own, and the two edges read as one
 * thick line rather than as a button sitting in a toolbar. At 5dp the button
 * is 38dp tall with 9dp of bar above and below, which is about the
 * proportion the original's toolbars ran at. The target does not move.
 */
private val TOOLBAR_BUTTON_INSET = 5.dp

/**
 * The cap-to-cap extent of the tabs button's glyph, in BOTH axes — see
 * [TabViewModeIcon], where being equal in both is the requirement.
 */
private val TAB_ICON_INK = 14.dp

/**
 * Every callback the toolbar's gesture blocks reach for, in a plain object
 * rewritten from composition each frame.
 *
 * A `pointerInput` block keeps everything it CLOSED OVER until one of its
 * keys changes — the lambdas included. The tap detector's only key is
 * [BottomToolbar]'s `doubleTapSwitchesTab`, which is false with no tabs and
 * still false with one, so opening the first tab from the empty screen left
 * the block holding the empty screen's `onTabs` — and that one answers a tap
 * by raising the + sheet again instead of the switcher. It only came right
 * once a SECOND tab flipped the key. The drag blocks are keyed on `Unit` and
 * so were stale for the whole composition.
 *
 * Not a snapshot state: a `MutableState` read inside a `pointerInput` is read
 * through a snapshot that does not advance with composition, which is the
 * other half of the same trap.
 */
private class ToolbarCallbacks {
    var onTabs: () -> Unit = {}
    var onTabsPress: () -> Unit = {}
    var onTabsDoubleTap: () -> Unit = {}
    var onTabsDragStart: (Offset) -> Unit = {}
    var onTabsDrag: (Offset) -> Unit = {}
    var onTabsDragEnd: (Offset) -> Unit = {}
    var onTabsDragCancel: () -> Unit = {}
    var onSwipeTabStart: (Int) -> Boolean = { false }
    var onSwipeTabDrag: (Float) -> Unit = {}
    var onSwipeTabEnd: (Float) -> Unit = {}
    var onSwipeTabCancel: () -> Unit = {}
    var onCoverPress: () -> Unit = {}
}

@Composable
fun BottomToolbar(
    loading: Boolean,
    progress: Int,
    switcherOpen: Boolean,
    listViewActive: Boolean,
    onTabs: () -> Unit,
    onTabsPress: () -> Unit,
    doubleTapSwitchesTab: Boolean,
    onTabsDoubleTap: () -> Unit,
    onTabsDragStart: (fingerWindowPosition: Offset) -> Unit,
    onTabsDrag: (Offset) -> Unit,
    onTabsDragEnd: (velocity: Offset) -> Unit,
    onTabsDragCancel: () -> Unit,
    // Swiping sideways off one of the end buttons walks the tab row: right
    // off the tabs button (the left end of the bar) brings in the tab to the
    // left, left off the menu button (the right end) brings in the one to the
    // right. `delta` is that step, and the start returns whether there was
    // anything there to step to — false leaves the touch to be swallowed
    // rather than tracking a card that doesn't exist.
    onSwipeTabStart: (delta: Int) -> Boolean,
    onSwipeTabDrag: (dragX: Float) -> Unit,
    onSwipeTabEnd: (velocityX: Float) -> Unit,
    onSwipeTabCancel: () -> Unit,
    onNewTab: () -> Unit,
    onMenu: () -> Unit,
    // Pointer DOWN on either of the two buttons that raise a sheet over the
    // page. Same reasoning as [onTabsPress] and the same deadline: the sheet
    // goes up on the RELEASE, and a copy of the page asked for then lands
    // against a screen that is already covered. The press is tens of
    // milliseconds earlier, which is all the frames the copy needs.
    onCoverPress: () -> Unit,
    // Downloads in flight: the menu chevron turns to point DOWN and a ring
    // round it fills with their progress. [downloadFraction] moves several
    // times a second, so it is a lambda read in the draw phase and in a
    // snapshotFlow, never in this composition. [downloadLandings] counts the
    // icons that have flown into the button — each one bumps it.
    downloading: Boolean = false,
    downloadFraction: () -> Float? = { null },
    downloadLandings: Int = 0,
    onMenuButtonPlaced: (LayoutCoordinates) -> Unit = {},
    // The page lens's black bezel over the bar's ground, 0..1, read in draw:
    // it comes in with the lens's strength exactly as the status bar's does.
    bezel: () -> Float = { 0f },
    // Overrides the top rule's colour; null for the palette's HairLine.
    dividerColor: Color? = null,
) {
    // The chevron does NOT turn when the menu opens: the sheet covers this
    // corner of the bar the whole time it is open, so the turn was animating
    // a glyph nobody can see.
    val haptics = rememberHaptics()

    // See [ToolbarCallbacks]: the gesture blocks below call through this, not
    // through the parameters they would otherwise capture. SideEffect, not
    // LaunchedEffect — a touch arriving in the frame that just changed must
    // find the callbacks that frame composed with.
    val live = remember { ToolbarCallbacks() }
    SideEffect {
        live.onTabs = onTabs
        live.onTabsPress = onTabsPress
        live.onTabsDoubleTap = onTabsDoubleTap
        live.onTabsDragStart = onTabsDragStart
        live.onTabsDrag = onTabsDrag
        live.onTabsDragEnd = onTabsDragEnd
        live.onTabsDragCancel = onTabsDragCancel
        live.onSwipeTabStart = onSwipeTabStart
        live.onSwipeTabDrag = onSwipeTabDrag
        live.onSwipeTabEnd = onSwipeTabEnd
        live.onSwipeTabCancel = onSwipeTabCancel
        live.onCoverPress = onCoverPress
    }

    val ninety8 = LocalNinety8.current
    val aero = LocalAero.current
    val nothing = LocalNothing.current
    // A Windows toolbar is not separated from what is under it by a line, it
    // is RAISED off it — one lit band along its top edge, which is the same
    // hairline's worth of pixels saying something else with them. The bevel
    // is drawn on the whole bar and only its top edge is on screen; the
    // other three are past the window.
    //
    // Aero spends the same hairline a third way. This bar is drawn OVER the
    // live page's bottom strip, so under that theme it is the one piece of
    // chrome with a genuine backdrop — it takes the glass, and the top edge
    // becomes the lit rim of it with the refraction band under that. See
    // [Glassy.Bar] for why the other three edges are left undrawn.
    Box {
    Surface(
        // Translucent sheets: the same frosted material as the sheets, over
        // the page strip `frostedSheetGlass` blurs under the bar.
        color = if (com.yuku.browser.ui.theme.LocalFrosted.current) com.yuku.browser.ui.theme.frostedSheetFill(
            BarBg, com.yuku.browser.ui.theme.AccentColor, com.yuku.browser.ui.theme.LocalNothing.current,
        ) else BarBg,
        shape = if (aero) com.yuku.browser.ui.theme.specialCorner(22.dp).copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
        ) else androidx.compose.ui.graphics.RectangleShape,
        modifier = Modifier
            .bevel98If(Bevel.RaisedThin)
            .then(if (aero) Modifier.clip(com.yuku.browser.ui.theme.specialCorner(22.dp).copy(
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            )) else Modifier)
            .aeroDroplet(22.dp, openBottom = true),
    ) {
        // Under the TUI the bar is part of the tube: the raster behind its
        // buttons, the phosphor bloom around them.
        Box(
            modifier = (if (aero) Modifier.grain(AERO_BAR_GRAIN) else Modifier)
                // Over the Surface's fill, under the divider and buttons.
                .drawBehind {
                    val a = bezel().coerceIn(0f, 1f)
                    if (a > 0f) drawRect(Color.Black, alpha = a)
                }
                .tuiCrtIf().tuiBloomIf()
        ) {
            Column {
                // The divider is what separates the bar from the page in the
                // ordinary look. 98 replaces it with a bevel and Aero with a
                // lit rim, and either drawn under a divider is two lines
                // doing one line's job.
                // Under the page lens it keeps the THEME's rule (dividerColor,
                // read outside the lens palette, so it never inverts at the
                // palette's halfway turn) and fades out as the bar's ground
                // goes black with the zoom (`bezel`).
                if (!ninety8 && !aero) HorizontalDivider(
                    color = dividerColor ?: HairLine,
                    modifier = Modifier.graphicsLayer { alpha = 1f - bezel().coerceIn(0f, 1f) },
                )
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Scoped to this one control, and only while the gesture is
                // actually armed — the shortened window is a property of the
                // tabs button's double tap, not of the app.
                val baseViewConfiguration = LocalViewConfiguration.current
                val viewConfiguration = remember(baseViewConfiguration, doubleTapSwitchesTab) {
                    if (doubleTapSwitchesTab) {
                        FastDoubleTapViewConfiguration(baseViewConfiguration)
                    } else {
                        baseViewConfiguration
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
                // Where this button sits in the window, so a drag can report
                // the finger's absolute position (not just its offset within
                // the 48dp box) — BrowserScreen anchors the shrinking page's
                // bottom-left corner to it.
                var buttonWindowOrigin by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        // Under 98 the three controls in this bar are
                        // BUTTONS — the same silver as the bar with a raised
                        // edge around them, which is the only thing that
                        // tells a Windows toolbar button from the toolbar.
                        // Drawn INSIDE the 48dp box rather than on it (see
                        // TOOLBAR_BUTTON_INSET): the target stays 48dp and
                        // the button gets air above and below it.
                        .aeroPopIf()
                        .bevel98If(inset = TOOLBAR_BUTTON_INSET)
                        .aeroDroplet(20.dp, inset = 4.dp, filled = true, glare = true)
                        .onGloballyPositioned {
                            buttonWindowOrigin = it.localToWindow(Offset.Zero)
                        }
                        // A plain tap opens instantly; a swipe up drags the
                        // current page into the switcher live, floating with
                        // the finger until release. Two separate detectors on
                        // the same node — Compose's own arbitration resolves
                        // which one a given touch turns out to be.
                        // Only when the Behavior setting is on does this
                        // detector take a doubleTap handler at all: passing
                        // one makes every single tap wait out the
                        // double-tap timeout before it can fire, so the
                        // default path keeps its instant open/close.
                        .pointerInput(doubleTapSwitchesTab) {
                            detectTapGestures(
                                // Pointer DOWN, before this touch is known to
                                // be a tap at all. The switcher's preview of
                                // the page it is leaving is a PixelCopy, which
                                // lands a frame or two after it is asked for
                                // and is dropped if the page is no longer the
                                // thing on screen by then (see captureExact) —
                                // asking at the release, which starts the
                                // shrink in the same frame, is asking too
                                // late. A finger takes tens of milliseconds to
                                // lift, so asking here gives the copy the
                                // frames it needs.
                                onPress = { live.onTabsPress() },
                                onTap = {
                                    haptics.tap()
                                    live.onTabs()
                                },
                                onDoubleTap = if (doubleTapSwitchesTab) {
                                    {
                                        // Distinct from the single tap's
                                        // click: the second tap does something
                                        // else entirely (flip to the last
                                        // tab), so it shouldn't feel like a
                                        // repeat of the first.
                                        haptics.confirm()
                                        live.onTabsDoubleTap()
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            val velocityTracker = VelocityTracker()
                            // detectDragGestures folds however far the finger
                            // moved crossing the touch-slop threshold into the
                            // FIRST onDrag call's dragAmount, not just that
                            // frame's own incremental movement — applying it
                            // as-is pops the content by that whole distance
                            // the instant the drag is recognized, before it
                            // visibly starts tracking the finger. Discard it.
                            var isFirstDrag = true
                            // Beyond that: this box has a SEPARATE tap
                            // detector right above sharing the same touch
                            // stream, and a plain tap's natural finger jitter
                            // is sometimes enough on its own to cross
                            // detectDragGestures' touch-slop threshold — which
                            // used to fire onTabsDragStart/onTabsDrag for
                            // what was really just a tap, visibly nudging the
                            // content ("goes slightly up") right before the
                            // tap's own instant open snapped it back. Nothing
                            // here — not even onTabsDragStart — fires until
                            // real movement clears this dead zone, so a tap
                            // can never touch drag state at all.
                            //
                            // Which gesture it is, is decided once, by
                            // NET displacement from where the drag started
                            // rather than by distance travelled: a diagonal
                            // that ends up mostly sideways is a tab swipe
                            // however wanderingly it got there, where summing
                            // |dy| would have committed it to the switcher on
                            // the way past.
                            var mode = ToolbarDrag.None
                            val deadZonePx = 12.dp.toPx()
                            var travel = Offset.Zero
                            // Where the sideways gesture was recognized. The
                            // dead zone is travel the user has already spent,
                            // so reporting raw `travel.x` would start the
                            // incoming card 12dp along instead of at nothing.
                            var swipeOrigin = 0f
                            detectDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    isFirstDrag = true
                                    mode = ToolbarDrag.None
                                    travel = Offset.Zero
                                    swipeOrigin = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPointerInputChange(change)
                                    if (isFirstDrag) {
                                        isFirstDrag = false
                                    } else {
                                        travel += dragAmount
                                        when (mode) {
                                            ToolbarDrag.None -> {
                                                val acrossX = kotlin.math.abs(travel.x)
                                                val alongY = kotlin.math.abs(travel.y)
                                                if (alongY > deadZonePx && alongY >= acrossX) {
                                                    mode = ToolbarDrag.Switcher
                                                    // Fires exactly when the
                                                    // page actually starts
                                                    // moving with the finger
                                                    // (past the dead zone),
                                                    // not when the pointer
                                                    // went down — the point is
                                                    // to confirm the drag took
                                                    // hold.
                                                    haptics.gestureStart()
                                                    live.onTabsDragStart(buttonWindowOrigin + change.position)
                                                    live.onTabsDrag(dragAmount)
                                                } else if (acrossX > deadZonePx) {
                                                    // Rightward only: this
                                                    // button is the bar's left
                                                    // end, so the gesture is
                                                    // pulling the tab on the
                                                    // left in from that edge.
                                                    // Swiping the other way
                                                    // here would be dragging a
                                                    // card in from the side it
                                                    // is leaving towards.
                                                    mode = if (travel.x > 0f && live.onSwipeTabStart(-1)) {
                                                        haptics.gestureStart()
                                                        swipeOrigin = travel.x
                                                        ToolbarDrag.Swipe
                                                    } else {
                                                        ToolbarDrag.Rejected
                                                    }
                                                }
                                            }
                                            ToolbarDrag.Switcher -> live.onTabsDrag(dragAmount)
                                            ToolbarDrag.Swipe -> live.onSwipeTabDrag(travel.x - swipeOrigin)
                                            ToolbarDrag.Rejected -> Unit
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val v = velocityTracker.calculateVelocity()
                                    when (mode) {
                                        ToolbarDrag.Switcher -> live.onTabsDragEnd(Offset(v.x, v.y))
                                        ToolbarDrag.Swipe -> live.onSwipeTabEnd(v.x)
                                        else -> Unit
                                    }
                                },
                                onDragCancel = {
                                    when (mode) {
                                        ToolbarDrag.Switcher -> live.onTabsDragCancel()
                                        ToolbarDrag.Swipe -> live.onSwipeTabCancel()
                                        else -> Unit
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Under the TUI theme the three buttons are their words —
                    // "tab", "new", "etc" — which is what a terminal would
                    // have put there. The rotating bars still carry which tab
                    // manager is up in the ordinary theme; the word doesn't
                    // try to, because there is no second word for it.
                    if (LocalTui.current) ToolbarWord("tab")
                    else TabViewModeIcon(listViewActive = listViewActive)
                }
                }

                IconButton(
                    onClick = {
                        haptics.tap()
                        onNewTab()
                    },
                    // A second detector on the same node, consuming nothing,
                    // so the button keeps its ripple and its click.
                    modifier = Modifier
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                live.onCoverPress()
                            }
                        }
                        // The Nothing theme's oval is wider than the 48dp
                        // slot an IconButton gives its glyph, and a target
                        // smaller than the thing drawn in it is the one way
                        // this could go wrong — so the BUTTON is grown, not
                        // just the drawing, and the ripple and the touch
                        // target grow with it. `requiredSize`, because
                        // IconButton pins its own 40dp inside whatever it is
                        // handed and only a required constraint outranks
                        // that (`size` would silently no-op).
                        .then(
                            if (LocalNothing.current) Modifier.requiredSize(width = 82.dp, height = 48.dp)
                            else Modifier
                        )
                        .aeroPopIf()
                        .bevel98If(inset = TOOLBAR_BUTTON_INSET)
                        .aeroDroplet(20.dp, inset = 4.dp, filled = true, glare = true),
                ) {
                    if (LocalTui.current) ToolbarWord("new")
                    else if (LocalNothing.current) NothingNewTabGlyph()
                    else Icon(Icons.Default.Add, contentDescription = "New tab", tint = Ink)
                }

                // The mirror of the tabs button's sideways swipe, on the
                // bar's other end: dragging left off the menu button pulls the
                // NEXT tab in from the right edge. Hung on the IconButton's
                // own modifier rather than replacing it with a bare Box, so
                // the tap keeps its ripple — the drag detector only takes the
                // touch once it has crossed slop, which cancels the press the
                // same way any scroll would.
                IconButton(
                    onClick = onMenu,
                    modifier = Modifier
                        .aeroPopIf()
                        .bevel98If(inset = TOOLBAR_BUTTON_INSET)
                        // The target a download's icon flies into.
                        .aeroDroplet(20.dp, inset = 4.dp, filled = true, glare = true)
                        .onGloballyPositioned { onMenuButtonPlaced(it) }
                        .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            live.onCoverPress()
                        }
                    }.pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        var isFirstDrag = true
                        var mode = ToolbarDrag.None
                        val deadZonePx = 12.dp.toPx()
                        var travel = Offset.Zero
                        var swipeOrigin = 0f
                        detectDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                isFirstDrag = true
                                mode = ToolbarDrag.None
                                travel = Offset.Zero
                                swipeOrigin = 0f
                            },
                            onDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                if (isFirstDrag) {
                                    isFirstDrag = false
                                } else {
                                    travel += dragAmount
                                    when (mode) {
                                        ToolbarDrag.None -> {
                                            val acrossX = kotlin.math.abs(travel.x)
                                            if (acrossX > deadZonePx && acrossX >= kotlin.math.abs(travel.y)) {
                                                mode = if (travel.x < 0f && live.onSwipeTabStart(1)) {
                                                    change.consume()
                                                    haptics.gestureStart()
                                                    swipeOrigin = travel.x
                                                    ToolbarDrag.Swipe
                                                } else {
                                                    ToolbarDrag.Rejected
                                                }
                                            }
                                        }
                                        ToolbarDrag.Swipe -> {
                                            change.consume()
                                            live.onSwipeTabDrag(travel.x - swipeOrigin)
                                        }
                                        else -> Unit
                                    }
                                }
                            },
                            onDragEnd = {
                                if (mode == ToolbarDrag.Swipe) {
                                    live.onSwipeTabEnd(velocityTracker.calculateVelocity().x)
                                }
                            },
                            onDragCancel = {
                                if (mode == ToolbarDrag.Swipe) live.onSwipeTabCancel()
                            },
                        )
                    },
                ) {
                    MenuButtonFace(
                        downloading = downloading,
                        downloadFraction = downloadFraction,
                        landings = downloadLandings,
                    )
                }
            }
            }
            // Overlaid on the toolbar's top edge rather than laid out in the
            // Column, so it never reserves space of its own — the toolbar's
            // height is identical whether or not a load is in progress, and
            // the line simply appears/disappears on top of it.
            // Under Aero the line is the bar's own lit edge, so it gets the
            // whole bar to trace (see [AeroLoadingRim]).
            if (!nothing) {
                LoadingLine(
                    loading = loading,
                    progress = progress,
                    modifier = if (aero) Modifier.matchParentSize() else Modifier.align(Alignment.TopStart),
                    wrapBar = aero,
                )
            }
        }
    }
    // Nothing's ruler is taller than a rule and would run into the + oval, so
    // it gets a band of its own ABOVE the bar, outside the Surface (which
    // clips) and reporting zero height, so it never changes the toolbar's
    // measured size — the page's bottom inset is read off that, and a band
    // growing through layout would resize the page on every frame.
    if (nothing) {
        LoadingLine(
            loading = loading,
            progress = progress,
            bezel = bezel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = LOAD_RULER_STRIP.roundToPx()),
                    )
                    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
                }
                .height(LOAD_RULER_STRIP),
            wrapBar = false,
        )
    }
    }
}

/**
 * What the menu button draws: the chevron, which turns DOWN while anything is
 * downloading, with a ring round it carrying the progress.
 *
 * Down because that is where a download is going — the same glyph turned
 * rather than swapped, the way the tabs button turns its bars, so the eye
 * sees one arrow change its mind rather than two icons trading places. The
 * ring fills clockwise from the top when the sizes are known and spins a
 * quarter arc when they are not (see [DownloadActivity.fraction]); the finish
 * closes the circle, holds, then fades as the chevron turns back up.
 *
 * Under the TUI the button is a word, and a word has no ring: it becomes the
 * percentage instead, which is what a terminal would have printed.
 *
 * Each icon that flies in ([landings]) bumps the button — up quickly, then
 * back with a pop — so the flight has something to land in.
 */
@Composable
private fun MenuButtonFace(downloading: Boolean, downloadFraction: () -> Float?, landings: Int) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(landings) {
        if (landings == 0) return@LaunchedEffect
        bump.animateTo(MENU_LANDING_SCALE, tween(MENU_LANDING_IN_MS, easing = Accelerate))
        bump.animateTo(1f, pop(MENU_LANDING_OUT_MS))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = bump.value
            scaleY = bump.value
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            LocalTui.current && downloading -> DownloadPercentWord(downloadFraction)
            LocalTui.current -> ToolbarWord("etc")
            else -> DownloadRingChevron(downloading, downloadFraction)
        }
    }
}

/** The TUI's readout — its own composition scope, so only this word recomposes per poll. */
@Composable
private fun DownloadPercentWord(downloadFraction: () -> Float?) {
    val fraction = downloadFraction()
    ToolbarWord(if (fraction == null) "..." else "${(fraction * 100).roundToInt()}%")
}

@Composable
private fun DownloadRingChevron(downloading: Boolean, downloadFraction: () -> Float?) {
    val turn = animateFloatAsState(
        targetValue = if (downloading) 180f else 0f,
        animationSpec = arrive(MENU_TURN_MS),
        label = "menuChevronTurn",
    )
    val ringAlpha = animateFloatAsState(
        targetValue = if (downloading) 1f else 0f,
        animationSpec = tween(if (downloading) RING_FADE_IN_MS else RING_FADE_OUT_MS),
        label = "menuRingAlpha",
    )
    // Every report animated toward, like the loading line: a poll is a step,
    // and a ring that jumps a quarter-turn at a time reads as stuttering.
    // Backwards only by snapping — that is a new download starting, not this
    // one losing ground.
    val fill = remember { Animatable(0f) }
    var sized by remember { mutableStateOf(false) }
    val fraction by rememberUpdatedState(downloadFraction)
    LaunchedEffect(Unit) {
        snapshotFlow { fraction() }.collectLatest { f ->
            sized = f != null
            if (f == null) return@collectLatest
            if (f < fill.value) fill.snapTo(f)
            else fill.animateTo(f, tween(RING_STEP_MS, easing = LinearEasing))
        }
    }
    val spin = if (downloading && !sized) {
        rememberInfiniteTransition(label = "menuRingSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_SPIN_MS, easing = LinearEasing)),
            label = "menuRingSpinAngle",
        )
    } else {
        null
    }
    val track = HairLine
    val accent = AccentColor
    Box(
        modifier = Modifier
            .size(MENU_RING_SIZE)
            .drawBehind {
                val alpha = ringAlpha.value
                if (alpha <= 0f) return@drawBehind
                val stroke = MENU_RING_STROKE.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = alpha,
                    style = Stroke(stroke),
                )
                val sweep = if (spin != null) 90f else 360f * fill.value
                if (sweep <= 0f) return@drawBehind
                drawArc(
                    color = accent,
                    startAngle = (spin?.value ?: 0f) - 90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = alpha,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = if (downloading) {
                "Address bar and quick settings, downloading"
            } else {
                "Address bar and quick settings"
            },
            tint = Ink,
            modifier = Modifier.graphicsLayer { rotationZ = turn.value },
        )
    }
}

/** The ring round the menu chevron: inside the 40dp button, clear of 98's bevel. */
private val MENU_RING_SIZE = 34.dp
private val MENU_RING_STROKE = 2.dp
private const val MENU_TURN_MS = 360
private const val RING_FADE_IN_MS = 180
private const val RING_FADE_OUT_MS = 240
private const val RING_STEP_MS = 300
private const val RING_SPIN_MS = 900
private const val MENU_LANDING_SCALE = 1.18f
private const val MENU_LANDING_IN_MS = 90
private const val MENU_LANDING_OUT_MS = 260

/**
 * One of the TUI theme's three toolbar words. Sized as a label rather than as
 * body text: it stands in for a 24dp glyph, and has to sit in the same
 * 48dp box without crowding it.
 */
@Composable
private fun ToolbarWord(word: String) {
    Text(
        text = word,
        color = Ink,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

/**
 * The Nothing theme's new-tab button: a filled oval with a wide, thin plus
 * cut across it.
 *
 * The one button in the bar drawn as a KEY rather than as a glyph, and the
 * one place the theme's accent is spent — a colour here is an interrupt, not
 * a step in the hierarchy, so it goes on the single action the bar exists to
 * offer and on nothing beside it. The plus is drawn rather than taken from
 * the icon set because the proportion is the point: its horizontal arm is
 * two and a half times its vertical one, which is what makes it read as
 * belonging to the oval instead of sitting inside it, and no Material plus
 * is that shape.
 *
 * The oval is wider than the 48dp slot an IconButton gives its glyph and
 * nearly as tall as the 56dp bar it sits in — it is far and away the largest
 * thing in the bar, which is the point of it — so the button around it is
 * grown to match (see its `requiredSize`) rather than the drawing alone,
 * leaving a 2dp margin so the ripple still reads as a ring around the oval
 * rather than as an edge of it.
 *
 * Its height stops 6dp short of the bar's at each end rather than filling it:
 * what makes a key read as sitting IN a bar is the strip of bar left showing
 * above and below it, and an oval flush with the divider reads as a shape cut
 * out of the toolbar instead.
 *
 * Round caps, matching `NothingIcons` — which reverses the call this glyph
 * originally made (butt caps, on the argument that the look is machined).
 * It is machined, but the skill's icon set is round-capped throughout and a
 * bar holding one butt-capped mark beside a row of round-capped ones is a
 * bar with two icon sets in it. The plus lost the argument to consistency.
 */
@Composable
private fun NothingNewTabGlyph() {
    // Translucent sheets: the oval lets the frosted bar through a little.
    val fill = AccentColor.frostedAccentIf()
    val mark = MaterialTheme.colorScheme.onPrimary
    Canvas(
        Modifier
            .size(width = 78.dp, height = 44.dp)
            .semantics { contentDescription = "New tab" }
    ) {
        drawRoundRect(color = fill, cornerRadius = CornerRadius(size.height / 2f))
        val stroke = 2.5.dp.toPx()
        // The horizontal arm is taken off the oval's width and the vertical
        // one off the ARM, not off the height: the oval's own proportion is
        // no longer far from square, so a plus scaled from both dimensions
        // would come out square with it — and being wide is the whole
        // character of this glyph.
        val across = size.width * 0.44f
        val down = across / 2.4f
        val mid = Offset(size.width / 2f, size.height / 2f)
        drawLine(
            color = mark,
            start = Offset(mid.x - across / 2f, mid.y),
            end = Offset(mid.x + across / 2f, mid.y),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
        drawLine(
            color = mark,
            start = Offset(mid.x, mid.y - down / 2f),
            end = Offset(mid.x, mid.y + down / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    }
}

/**
 * Three parallel bars, always the same glyph — grid/carousel mode draws them
 * upright, list mode draws them flat. Rather than swapping icons, the whole
 * glyph rotates 90°, so the bars themselves visibly sweep from vertical to
 * horizontal instead of cross-fading between two unrelated shapes.
 */
@Composable
private fun TabViewModeIcon(listViewActive: Boolean, modifier: Modifier = Modifier) {
    val rotation = animateFloatAsState(
        targetValue = if (listViewActive) 90f else 0f,
        animationSpec = arrive(340),
        label = "tabViewModeIcon",
    )
    val lineColor = Ink
    Canvas(
        // The same 24dp box every other glyph in this bar gets, so the three
        // buttons are one row of equal icons rather than two icons and a
        // smaller third.
        modifier = modifier
            .size(24.dp)
            .graphicsLayer { rotationZ = rotation.value },
    ) {
        val barCount = 3
        val strokeWidth = 1.8.dp.toPx()
        // The glyph's ink is a SQUARE, and that is the whole point of laying
        // it out from a fixed extent rather than as a fraction of the box: the
        // icon rotates 90° between the two presentations, so ink that is wider
        // than it is tall becomes taller than it is wide, and the button
        // visibly changes size in a bar whose other two icons do not. Square
        // ink rotates to exactly itself.
        //
        // [TAB_ICON_INK] is that extent, cap to cap — the round caps hang half
        // a stroke past each end of a line, so the bars are drawn one stroke
        // shorter than it and their centres one stroke closer together. It is
        // matched to the ink of the Material glyphs either side (Add spans 14
        // of its own 24dp box), which is what "the same size" means for an
        // icon: the marks agree, not the boxes around them.
        val barLength = TAB_ICON_INK.toPx() - strokeWidth
        val gap = barLength / (barCount - 1)
        val centerX = size.width / 2f
        val top = (size.height - barLength) / 2f
        val bottom = top + barLength
        for (i in 0 until barCount) {
            val x = centerX + (i - (barCount - 1) / 2f) * gap
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(x, top),
                end = androidx.compose.ui.geometry.Offset(x, bottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Sits at the toolbar's top edge instead of the usual top-of-screen spot, since
 * the address bar lives in the menu sheet, not a top bar. Drawn as an overlay
 * on top of the toolbar rather than a sibling in its layout, so it never
 * reserves its own height — the toolbar is the same size whether or not it's
 * showing, and the line just appears/disappears over the divider.
 *
 * The shape of the animation is Chrome's own `ToolbarProgressBar`, which is
 * worth stating because the obvious version of this is wrong in a way that is
 * easy to miss. A bar whose width simply follows `onProgressChanged` and
 * whose alpha simply follows `loading` does not report a load — it reports a
 * sequence of numbers. WebView delivers those in a handful of jumps, and the
 * last thing it does is drop `loading` while the bar is at whatever fraction
 * it happened to reach, so the bar VANISHES part-way across on every single
 * page. The end of a load is the one moment the bar has something definite to
 * say, and it was the one moment it was silent.
 *
 * So, in Chrome's order: a fast start off zero so the first frames say
 * something is happening; every step animated toward rather than jumped to;
 * and a finish that runs to the full width, holds there, and only then fades
 * ([LOAD_FINISH_MS], [LOAD_HOLD_MS], [LOAD_FADE_MS] — Chrome's own are 1000 /
 * 100-300 / 140, shortened here because this line is 2dp of chrome rather
 * than the top of a browser window).
 *
 * What is deliberately NOT taken from Chrome is any forward creep while
 * nothing is happening: a bar that walks past the fraction it has actually
 * reached is reporting something untrue. A stalled load gets the same answer
 * Chrome gives it instead — a segment sweeping along the part already filled
 * ([STALL_AFTER_MS]), which says "still going" without claiming ground.
 */
@Composable
internal fun LoadingLine(
    loading: Boolean,
    progress: Int,
    modifier: Modifier = Modifier,
    // Aero only: trace the whole toolbar's outline (see [AeroLoadingRim])
    // rather than rule a line across its top. The caller hands the bar's
    // full size in [modifier] when this is set.
    wrapBar: Boolean = false,
    // The page lens's black, 0..1, read in draw (see BottomToolbar's `bezel`).
    bezel: () -> Float = { 0f },
) {
    // One [Animatable] rather than a pair of animateFloatAsState, because the
    // three parts of a load are three different animations of the same value
    // — a fast start, a run of re-targets and a finish that has to be allowed
    // to COMPLETE before the fade begins — and only a single animatable can
    // be handed from one to the next without the value jumping between them.
    val fraction = remember { Animatable(0f) }
    // Not `loading`: the bar outlives it by the length of the finish. This is
    // what is actually on screen.
    var showing by remember { mutableStateOf(false) }
    val alpha = animateFloatAsState(
        targetValue = if (showing) 1f else 0f,
        animationSpec = tween(LOAD_FADE_MS),
        label = "loadingAlpha",
    )

    LaunchedEffect(loading) {
        if (loading) {
            fraction.snapTo(0f)
            showing = true
            // Off zero immediately. Nothing has actually loaded yet, but the
            // first progress report can be a second away on a slow connection
            // and a bar sitting at zero for a second reads as a bar that is
            // broken.
            fraction.animateTo(LOAD_FAST_START, tween(LOAD_FAST_START_MS))
        } else if (showing) {
            // The finish, which is the whole point of the rewrite: all the
            // way across first, then a beat at full width so the eye has
            // something to land on, and only then the fade.
            fraction.animateTo(1f, tween(LOAD_FINISH_MS, easing = LinearEasing))
            delay(LOAD_HOLD_MS)
            showing = false
            delay(LOAD_FADE_MS.toLong())
            fraction.snapTo(0f)
        }
    }

    // Every step animated toward. Re-targeting an Animatable cancels the
    // animation in flight and carries on from wherever it had got to, so a
    // report arriving mid-travel is a change of destination rather than a
    // jump. Only ever forwards: WebView can report a smaller number on a
    // redirect, and a load bar going backwards is worse than one that stalls.
    LaunchedEffect(progress, loading) {
        if (!loading) return@LaunchedEffect
        val target = (progress / 100f).coerceIn(0f, 1f)
        if (target > fraction.value) {
            fraction.animateTo(target, tween(LOAD_STEP_MS, easing = LinearEasing))
        }
    }

    // A load that has gone quiet. Chrome waits five seconds before saying
    // anything about it, and five seconds is right: below that this fires on
    // ordinary pages between two of their own progress reports.
    var stalled by remember { mutableStateOf(false) }
    LaunchedEffect(progress, loading) {
        stalled = false
        if (!loading) return@LaunchedEffect
        delay(STALL_AFTER_MS)
        stalled = true
    }

    // Which look the bar is drawn in. Three of the four break the run up
    // rather than pouring it: see [LoadingCells] for what each one does with
    // its cells and why the three answers differ.
    val nothing = LocalNothing.current
    val tui = LocalTui.current
    val ninety8 = LocalNinety8.current
    val aero = LocalAero.current
    val cells = loadingCells(nothing = nothing, tui = tui, ninety8 = ninety8, aero = aero)
    val accent = AccentColor
    if (aero && wrapBar) {
        AeroLoadingRim(
            fraction = { fraction.value },
            alpha = { alpha.value },
            stalled = stalled,
            accent = accent,
            track = HairLine,
            modifier = modifier,
        )
        return
    }
    if (cells.tick) {
        RulerLoading(
            fraction = { fraction.value },
            alpha = { alpha.value },
            stalled = stalled,
            ink = Ink,
            accent = accent,
            track = HairLine,
            // The band rises out of the bar, so it is the bar's material.
            ground = if (com.yuku.browser.ui.theme.LocalFrosted.current) com.yuku.browser.ui.theme.frostedSheetFill(
                BarBg, accent, com.yuku.browser.ui.theme.LocalNothing.current,
            ) else BarBg,
            modifier = modifier,
            bezel = bezel,
        )
        return
    }
    // 98 draws a real progress control, which is a WELL: the face colour with
    // a sunken edge round it, not a hairline rule. Nothing's track is not a
    // rule either — it is the UNLIT half of its own dot row (see
    // [LoadingCells]), so the bar is a matrix of holes whichever end of the
    // load it is at, and only their colour says how far it has got.
    val hairline = HairLine
    val track = when {
        ninety8 -> Modifier.background(MaterialTheme.colorScheme.surface)
        // Aero's track is a WELL — the same glass as everything else, sunk
        // instead of raised, which is what its own [Glassy.Field] draws. The
        // bar is not a rule on the toolbar under this look either; it is a
        // channel cut into it with something bright running along the bottom.
        aero -> Modifier.background(hairline)
        cells.dot -> Modifier.segmentedFill(cells, hairline)
        else -> Modifier.background(hairline)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cells.height)
            .graphicsLayer { this.alpha = alpha.value }
            .then(track)
            // Drawn after the content, so the well's edge stays on top of the
            // chunks that fill it.
            .bevel98If(Bevel.Sunken),
    ) {
        Box(
            modifier = Modifier
                // Inside the well, never over its edge. A no-op everywhere
                // else, where the track has no edge to keep clear of.
                .padding(cells.wellInset)
                .fillMaxHeight()
                // Measured in the layout phase from the animated value rather
                // than composed against it, same as everything else here.
                .layout { measurable, constraints ->
                    val full = constraints.maxWidth
                    val raw = (full * fraction.value).coerceIn(0f, full.toFloat())
                    val width = quantiseToCells(raw, full, cells, this)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(full, placeable.height) { placeable.place(0, 0) }
                }
                // Under the TUI the lit run glows outward in the accent —
                // after the width is set, so the halo hugs what has loaded
                // and not the whole track. Several times a card's strength
                // and a tighter radius: a 3dp line has almost no area to
                // glow from.
                .tuiGlowAroundIf(strength = 4f, radius = 6.dp)
                .then(
                    if (cells.pitch > 0.dp) Modifier.segmentedFill(cells, accent)
                    else Modifier.background(accent)
                ),
        ) {
            if (stalled) StallSweep(cells)
        }
    }
}

/**
 * How one look breaks the loading run up, and how tall it draws it.
 *
 * A progress bar is a READOUT, and the ordinary theme draws that readout as
 * what it literally is: a distance travelled, poured continuously. The three
 * special looks each have a reason not to, and — this is the point — three
 * DIFFERENT reasons, so they get three different bars rather than one shared
 * one:
 *
 * - **Nothing** counts in DOTS, which is the theme's whole alphabet: its
 *   display face is a dot matrix, its canvases are fields of bored holes, and
 *   a row of dots lighting up left to right is the one readout this language
 *   already owns. The dots are there the whole time — the track is the same
 *   row drawn in the hairline colour — so the bar is a matrix at both ends of
 *   the load and only the COLOUR of a dot says whether its part has happened.
 *   Nothing partial is ever drawn: a dot is lit or it is not, which is what
 *   makes the arrival of the next one an event, and a clipped dot is not a
 *   dot ([LoadingCells.dot] drops it rather than drawing an egg).
 * - **TUI** counts in CHARACTERS. A terminal's progress bar is one unbroken
 *   run of cells — `####    ` — with no gap between them, because it is being
 *   written into a text grid where a gap would be a space character. So the
 *   bar stays a single continuous line and only its LENGTH is quantised,
 *   advancing a whole cell at a time.
 * - **98** is a Windows progress control, which is a sunken well with a row
 *   of separate blocks stepping across it. Its chunks are the chunkiest of
 *   the three and it is the only one with a border, because in that system a
 *   control is a thing with an edge — the bar is not a line on the toolbar,
 *   it is a widget sitting on it.
 *
 * All three are honest about the same thing for the same reason: WebView
 * reports progress in jumps, and a smooth ramp is a story the animation tells
 * about numbers that arrived as steps.
 *
 * **Aero is the exception, and it is the interesting one**: it POURS, like
 * the ordinary theme, and then glazes what it poured. That is not a lapse
 * back to the default — it is what that era's bar actually was. Vista and 7
 * drew a continuous glossy lozenge filling a sunken well, with a highlight
 * running the length of it, precisely because the material was liquid: a
 * segmented bar is XP's, one generation earlier, and breaking this one into
 * cells would be quoting the wrong decade. It is also the only look here
 * where the cells would cost something real — a gloss is a single specular
 * run across a curved solid, and chopping it into blocks leaves a row of
 * separate little highlights rather than one reflection.
 */
/**
 * Aero's loading line: a thin glass TUBE of the accent that follows the
 * toolbar's own outline — up from the bottom of the left edge, round the
 * rounded corner, across the top, round the other corner and down the right —
 * so the load wraps the bar rather than ruling a line across it.
 *
 * The tube reads as 3D from three strokes laid along the same path: a soft
 * accent glow under it, the body, and a hairline of white riding its upper
 * half (the specular line a lit cylinder carries). The whole outline is laid
 * down faintly as the track first. A stalled load sends a bright pulse along
 * what has been filled, instead of claiming more ground.
 *
 * Everything per frame is read in the draw phase.
 */
@Composable
private fun AeroLoadingRim(
    fraction: () -> Float,
    alpha: () -> Float,
    stalled: Boolean,
    accent: Color,
    track: Color,
    modifier: Modifier,
) {
    val corner = com.yuku.browser.ui.theme.aeroCornerOf(22.dp)
    val sweep = if (stalled) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rimStall")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "rimStallPhase",
        )
    } else null
    Box(
        modifier = modifier.drawWithCache {
            val stroke = LOAD_AERO_TUBE.toPx()
            val inset = stroke / 2f + 0.5.dp.toPx()
            val w = size.width
            val h = size.height
            val r = (corner.toPx() - inset).coerceIn(0f, minOf(w / 2f, h) - inset)
            // The sides stop where the bar's own rim has faded out (see the
            // open-bottomed rim in Aero.kt) rather than running into rounded
            // screen corners.
            val sideEnd = maxOf(inset + r, h * 0.62f)
            val outline = androidx.compose.ui.graphics.Path().apply {
                moveTo(inset, sideEnd)
                lineTo(inset, inset + r)
                arcTo(androidx.compose.ui.geometry.Rect(inset, inset, inset + 2 * r, inset + 2 * r), 180f, 90f, false)
                lineTo(w - inset - r, inset)
                arcTo(androidx.compose.ui.geometry.Rect(w - inset - 2 * r, inset, w - inset, inset + 2 * r), 270f, 90f, false)
                lineTo(w - inset, sideEnd)
            }
            val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(outline, false) }
            val length = measure.length
            val filled = androidx.compose.ui.graphics.Path()
            val pulse = androidx.compose.ui.graphics.Path()
            // The tube THINS toward both ends of the outline (the bottoms of
            // the two sides). Drawn as ONE filled polygon per layer — the
            // outline sampled every [LOAD_AERO_SAMPLE], offset either side
            // along the normal by the tapered half-width. Short overlapping
            // translucent strokes stacked their alpha at every joint, which
            // read as pixelation and a seam where the pieces met.
            val androidMeasure = android.graphics.PathMeasure(outline.asAndroidPath(), false)
            val sampleStep = LOAD_AERO_SAMPLE.toPx()
            val count = (kotlin.math.ceil(length / sampleStep).toInt() + 1).coerceAtLeast(2)
            val sd = FloatArray(count)
            val sx = FloatArray(count)
            val sy = FloatArray(count)
            val nx = FloatArray(count)
            val ny = FloatArray(count)
            val sk = FloatArray(count)
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            val taperLen = (length * LOAD_AERO_TAPER).coerceAtLeast(1f)
            fun taperAt(d: Float): Float {
                val t = (minOf(d, length - d) / taperLen).coerceIn(0f, 1f)
                return LOAD_AERO_TAPER_MIN + (1f - LOAD_AERO_TAPER_MIN) * (t * t * (3f - 2f * t))
            }
            for (i in 0 until count) {
                val d = minOf(i * sampleStep, length)
                androidMeasure.getPosTan(d, pos, tan)
                sd[i] = d; sx[i] = pos[0]; sy[i] = pos[1]
                nx[i] = -tan[1]; ny[i] = tan[0]
                sk[i] = taperAt(d)
            }
            fun buildTube(target: androidx.compose.ui.graphics.Path, upTo: Float, width: Float) {
                target.reset()
                if (upTo <= 0f) return
                val half = width / 2f
                var last = 0
                while (last + 1 < count && sd[last + 1] < upTo) last++
                androidMeasure.getPosTan(upTo, pos, tan)
                val ex = pos[0]; val ey = pos[1]; val enx = -tan[1]; val eny = tan[0]
                val eh = half * taperAt(upTo)
                target.moveTo(sx[0] + nx[0] * half * sk[0], sy[0] + ny[0] * half * sk[0])
                for (i in 1..last) target.lineTo(sx[i] + nx[i] * half * sk[i], sy[i] + ny[i] * half * sk[i])
                target.lineTo(ex + enx * eh, ey + eny * eh)
                target.lineTo(ex - enx * eh, ey - eny * eh)
                for (i in last downTo 0) target.lineTo(sx[i] - nx[i] * half * sk[i], sy[i] - ny[i] * half * sk[i])
                target.close()
            }
            val trackPath = androidx.compose.ui.graphics.Path().also { buildTube(it, length, 1.dp.toPx()) }
            val glowPath = androidx.compose.ui.graphics.Path()
            val bodyPath = androidx.compose.ui.graphics.Path()
            val specPath = androidx.compose.ui.graphics.Path()
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            onDrawBehind {
                val a = alpha()
                if (a <= 0f || length <= 0f) return@onDrawBehind
                drawPath(trackPath, track.copy(alpha = track.alpha * a))
                val end = length * fraction().coerceIn(0f, 1f)
                if (end <= 0f) return@onDrawBehind
                // Glow, body, then the specular line on top of it.
                buildTube(glowPath, end, stroke * 2.6f)
                buildTube(bodyPath, end, stroke)
                buildTube(specPath, end, stroke * 0.3f)
                drawPath(glowPath, accent.copy(alpha = 0.28f * a))
                drawPath(bodyPath, accent.copy(alpha = a))
                drawPath(specPath, Color.White.copy(alpha = 0.7f * a))
                val phase = sweep?.value
                if (phase != null) {
                    val pulseLen = 36.dp.toPx().coerceAtMost(end)
                    val start = (end + pulseLen) * phase - pulseLen
                    pulse.reset()
                    measure.getSegment(start.coerceAtLeast(0f), (start + pulseLen).coerceAtMost(end), pulse, true)
                    drawPath(pulse, Color.White.copy(alpha = 0.8f * a), style = Stroke(stroke, cap = cap))
                }
            }
        },
    )
}

private data class LoadingCells(
    /** How tall the whole bar is drawn. */
    val height: Dp,
    /** One lit cell. Zero when the look pours the run instead of counting it. */
    val cell: Dp,
    /** Cell plus the track showing between two of them; zero = not quantised. */
    val pitch: Dp,
    /**
     * Round cells, and whole ones only. A dot's shape is the point of it, so
     * unlike a block it is never clipped by the end of the run — a cut circle
     * reads as a smaller mark rather than as a dot half arrived.
     */
    val dot: Boolean = false,
    /**
     * Ruler ticks: [cell] wide, hanging from the bar's top edge, every
     * [LOAD_TICK_MAJOR_EVERY]th one full height and the rest shorter. Whole
     * ones only, like a dot — a clipped tick is a different mark.
     */
    val tick: Boolean = false,
    /** How far inside its own bounds the fill is drawn — 98's well edge. */
    val wellInset: Dp = 0.dp,
)

@Composable
private fun loadingCells(
    nothing: Boolean,
    tui: Boolean,
    ninety8: Boolean,
    aero: Boolean,
): LoadingCells = when {
    ninety8 -> LoadingCells(
        height = LOAD_WELL_HEIGHT,
        cell = LOAD_CHUNK_98,
        pitch = LOAD_CHUNK_98 + LOAD_CHUNK_98_GAP,
        wellInset = BEVEL_BAND * 2,
    )
    // Poured, not counted — see [LoadingCells]. Taller than the ordinary
    // rule because a gloss needs something to be a gloss ON: at 2dp the
    // specular band and its terminator land inside one pixel of each other.
    aero -> LoadingCells(height = LOAD_AERO_HEIGHT, cell = 0.dp, pitch = 0.dp)
    // A caliper's scale along the toolbar's top edge: an instrument reading
    // out a distance, which is the theme's panel language for a readout.
    nothing -> LoadingCells(
        height = LOAD_TICK_MAJOR,
        cell = LOAD_TICK_WIDTH,
        pitch = LOAD_TICK_PITCH,
        tick = true,
    )
    // One cell, no gap: the run is continuous and only its length steps.
    tui -> LoadingCells(height = LOAD_HEIGHT, cell = LOAD_TUI_CELL, pitch = LOAD_TUI_CELL)
    else -> LoadingCells(height = LOAD_HEIGHT, cell = 0.dp, pitch = 0.dp)
}

/**
 * The fill's width, snapped DOWN to the last cell it has completely covered.
 *
 * This is what makes the segments appear one by one rather than growing: the
 * width does not move at all while the load crosses a cell, and then the
 * whole cell is there. A part-lit cell would be the same continuous readout
 * with a comb over it.
 *
 * The one exception is the finish, which must reach the end — a bar that
 * stops a cell short of the edge on a load that completed is reporting
 * something untrue. There the raw width is taken and the last cell is
 * clipped by it (see [segmentedFill]).
 */
private fun quantiseToCells(raw: Float, full: Int, cells: LoadingCells, density: Density): Int {
    if (cells.pitch <= 0.dp || raw >= full) return raw.roundToInt().coerceIn(0, full)
    val cell = with(density) { cells.cell.toPx() }
    val pitch = with(density) { cells.pitch.toPx() }
    if (raw < cell) return 0
    val n = ((raw - cell) / pitch).toInt() + 1
    return ((n - 1) * pitch + cell).roundToInt().coerceIn(0, full)
}

/**
 * The row of cells itself, drawn in the FILL's own coordinates — the fill's
 * width is the progress (see the `layout` above), so the cells are pinned at
 * the left end and are simply there or not as it grows.
 *
 * Because the width is quantised, every cell drawn here is a whole one; the
 * `min` matters only on the finish, where the run runs to the edge and the
 * last cell is cut by it. A cut shorter than [LOAD_CELL_STUB] of a cell is
 * dropped rather than drawn as a nub against the end of the bar — and a DOT
 * is dropped the moment it is cut at all.
 *
 * Also the drawing for Nothing's unlit TRACK, which is the identical row in
 * the hairline colour across the bar's full width: one function, because the
 * lit dots and the dark ones are the same matrix.
 */
private fun Modifier.segmentedFill(cells: LoadingCells, color: Color): Modifier = drawBehind {
    val cell = cells.cell.toPx()
    val pitch = cells.pitch.toPx()
    var x = 0f
    while (x < size.width) {
        val w = min(cell, size.width - x)
        if (cells.dot) {
            if (w >= cell) {
                drawCircle(color = color, radius = cell / 2f, center = Offset(x + cell / 2f, size.height / 2f))
            }
        } else if (w >= cell * LOAD_CELL_STUB) {
            drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height))
        }
        x += pitch
    }
}

/**
 * Nothing's loading line: a caliper's scale on a band that rises out of the
 * toolbar's top edge while a load runs and folds back into it after (the
 * band's reveal is the line's own fade, read in draw). Ticks hang from the
 * band's top line, edge to edge, laid out FROM THE CENTRE: a major tick
 * stands at the exact middle and every tenth one either side of it, so the
 * scale is mirror-symmetric whatever the width. Lit ticks are ink and the
 * newest one is the accent (the reading head); a stalled load sends the head
 * scanning back over what is lit. Whole ticks only.
 */
@Composable
private fun RulerLoading(
    fraction: () -> Float,
    alpha: () -> Float,
    stalled: Boolean,
    ink: Color,
    accent: Color,
    track: Color,
    ground: Color,
    modifier: Modifier,
    // The page lens's black over the band's ground, as over the bar's.
    bezel: () -> Float = { 0f },
) {
    val sweep = if (stalled) {
        rememberInfiniteTransition(label = "rulerStall").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(STALL_SWEEP_MS, delayMillis = STALL_SWEEP_GAP_MS),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rulerStallPhase",
        )
    } else null
    Canvas(modifier) {
        val a = alpha()
        if (a <= 0f) return@Canvas
        val tick = LOAD_TICK_WIDTH.toPx()
        val pitch = LOAD_TICK_PITCH.toPx()
        val line = 1.dp.toPx()
        // The band: rises from the bar's top edge, and runs one hairline down
        // over the bar's own divider so there are never two lines.
        val top = size.height * (1f - a)
        drawRect(ground, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + line))
        val b = bezel().coerceIn(0f, 1f)
        if (b > 0f) {
            drawRect(Color.Black, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + line), alpha = b)
        }
        drawRect(track, topLeft = Offset(0f, top), size = Size(size.width, line))
        // Centre tick at the middle, then k pitches either side while a whole
        // tick still fits: index 0 is the leftmost, major where the distance
        // from the centre is a whole decade.
        val centreX = (size.width - tick) / 2f
        val side = (centreX / pitch).toInt()
        if (side <= 0) return@Canvas
        val count = side * 2 + 1
        val left = centreX - side * pitch
        val f = fraction()
        val lit = if (f >= 1f) count else (f * count).toInt().coerceIn(0, count)
        val tickTop = top + line
        val phase = sweep?.value
        val segment = size.width * STALL_SEGMENT_FRACTION
        val sweepX = if (phase != null) (lit * pitch + segment) * phase - segment else 0f
        for (i in 0 until count) {
            val major = abs(i - side) % LOAD_TICK_MAJOR_EVERY == 0
            // Cut at the band's floor while it is still rising, so no tick
            // pokes down into the bar over the + oval.
            val h = min((if (major) LOAD_TICK_MAJOR else LOAD_TICK_MINOR).toPx(), size.height - tickTop)
            if (h <= 0f) continue
            // Every major tick is the accent — the decades are the scale's
            // own marks — dimmed while the load has not reached it.
            var color = when {
                major && i >= lit -> accent.copy(alpha = accent.alpha * LOAD_TICK_MAJOR_UNLIT)
                major -> accent
                i >= lit -> track
                i == lit - 1 -> accent
                else -> ink
            }
            if (phase != null && !major && i < lit - 1) {
                val centre = i * pitch + tick / 2f
                val k = 1f - abs((centre - (sweepX + segment / 2f)) / (segment / 2f))
                if (k > 0f) color = androidx.compose.ui.graphics.lerp(ink, accent, k)
            }
            drawRect(
                color = color.copy(alpha = color.alpha * a),
                topLeft = Offset(left + i * pitch, tickTop),
                size = Size(tick, h),
            )
        }
    }
}

/**
 * The ruler's measures: a 1dp tick every 4dp, 4dp long, every tenth 8dp,
 * hanging in a 12dp band (hairline, 8dp tick, 3dp of air under it).
 */
private val LOAD_RULER_STRIP = 12.dp
/** An unreached major tick's accent alpha. */
private const val LOAD_TICK_MAJOR_UNLIT = 0.35f
private val LOAD_TICK_WIDTH = 1.dp
private val LOAD_TICK_PITCH = 4.dp
private val LOAD_TICK_MINOR = 4.dp
private val LOAD_TICK_MAJOR = 8.dp
private const val LOAD_TICK_MAJOR_EVERY = 10

/** The ordinary rule — see [LoadingCells]. */
private val LOAD_HEIGHT = 2.dp

/**
 * Nothing's dot, which is also its bar's height, and the hole between two —
 * the same measure, so the row is dot, gap, dot at one rhythm. A gap wider
 * than the mark reads as a dotted rule with the dashes taken out; one equal
 * to it is the theme's own matrix, where the holes and the paper between
 * them are the same size.
 */
private val LOAD_DOT = 3.dp
private val LOAD_DOT_GAP = LOAD_DOT

/** The TUI's cell, which is a character's worth of a run with no gap in it. */
private val LOAD_TUI_CELL = 9.dp

/** Aero's well: deep enough for a gloss and its hard terminator to both show. */
private val LOAD_AERO_HEIGHT = 6.dp

/** How much of the rim's length, at each end, the Aero tube thins over. */
private const val LOAD_AERO_TAPER = 0.16f

/** How thin it gets at the very ends, as a fraction of its full width. */
private const val LOAD_AERO_TAPER_MIN = 0.2f

/** The spacing the Aero tube's outline is sampled at to build its polygon. */
private val LOAD_AERO_SAMPLE = 1.5.dp

/** The Aero tube's thickness — thinner than the old 6dp well. */
private val LOAD_AERO_TUBE = 2.5.dp

/** 98's well and the blocks stepping across it. */
private val LOAD_WELL_HEIGHT = 10.dp
private val LOAD_CHUNK_98 = 6.dp
private val LOAD_CHUNK_98_GAP = 2.dp

/** How much of a cell has to survive the finish's clip to be worth drawing. */
private const val LOAD_CELL_STUB = 0.35f

/**
 * The one thing a stalled load can honestly show: a lighter segment travelling
 * along the ground already covered. It is drawn INSIDE the filled part — that
 * is what keeps it a statement about the load still being alive rather than a
 * claim about how far it has got.
 */
@Composable
private fun StallSweep(cells: LoadingCells) {
    val sweep = rememberInfiniteTransition(label = "loadingStall")
    val t = sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // A pause at the end of each pass, so it reads as a repeated
            // gesture rather than a belt going round.
            animation = tween(STALL_SWEEP_MS, delayMillis = STALL_SWEEP_GAP_MS),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loadingStallSweep",
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f) return@Canvas
        val segment = size.width * STALL_SEGMENT_FRACTION
        // Enters from off the left and leaves off the right, so no frame has
        // it half-drawn against an edge.
        val x = (size.width + segment) * t.value - segment
        // Under a dot row the sweep has to be dots too: a translucent band
        // laid over the matrix is a solid rule crossing it, which is the one
        // thing this bar is drawn to avoid. So the same gradient is sampled
        // at each dot's centre and spent on the dot itself.
        if (cells.dot) {
            val cell = cells.cell.toPx()
            val pitch = cells.pitch.toPx()
            var dot = 0f
            while (dot + cell <= size.width) {
                val centre = dot + cell / 2f
                // A triangle across the segment: nothing at either end, full
                // at the middle, which is the gradient's own shape.
                val f = 1f - abs((centre - (x + segment / 2f)) / (segment / 2f))
                if (f > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = STALL_SEGMENT_ALPHA * f),
                        radius = cell / 2f,
                        center = Offset(centre, size.height / 2f),
                    )
                }
                dot += pitch
            }
            return@Canvas
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = STALL_SEGMENT_ALPHA), Color.Transparent),
                startX = x,
                endX = x + segment,
            ),
            topLeft = Offset(x, 0f),
            size = Size(segment, size.height),
        )
    }
}
