package com.yuku.browser.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroGlareIf
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.Glassy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.aeroPopIf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.Tab
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiCrtIf
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.PageBg
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.yuku.browser.ui.theme.SpecialCornerScale
import com.yuku.browser.ui.theme.specialCorner

/**
 * The lightweight alternative to [TabSwitcher]'s zoomed card grid — a plain
 * scrolling list of rounded-rect rows, no thumbnail rendering (cheap to show
 * even with many tabs open) and an explicit close button per row instead of
 * the grid's swipe-to-close gesture.
 *
 * It is a SHEET, the same object the + and menu sheets are: an opaque surface
 * with rounded corners, pinned to the bottom edge, sliding up out of it over
 * a dimmed page, and tall enough for exactly the tabs it holds — three tabs
 * is a short sheet, twenty is a full-height one that scrolls. That is the
 * whole difference from the grid: the grid REPLACES the page (the page shrinks
 * into one of its cards), where this is a control PUT IN FRONT of it, and
 * every other control of that shape in this app is a bottom sheet. It used to
 * arrive as a circle opening out of the tabs button onto a ground that faded
 * away above the topmost row; that said "grew out of the button" clearly
 * enough, but it also made the list a one-off — a surface with no edge, whose
 * height was only ever implied by where its ground gave out. A sheet says the
 * same thing (it comes from the bottom, where the button is) with an edge you
 * can see, and it says it in the vocabulary the rest of the app already uses.
 *
 * It arrives the way every other sheet here does: at its full height, slid up
 * from below the bottom edge, over a scrim. Not by growing — a sheet that
 * unrolls its own height wipes itself onto the screen, which is a different
 * movement from the one the + and menu sheets make, and this is the same kind
 * of object as those.
 *
 * BrowserScreen's `pageShrinkSuppressed` is the other half — the page is
 * pinned at fullscreen for as long as this mode is up, because it is the thing
 * the sheet is in front of, and it does not react to the sheet at all.
 *
 * Selecting a row hands off to the exact same zoom-from-this-card mechanics
 * the grid uses (see BrowserScreen's selectTabFromCard) via
 * [onCurrentRowPositioned] reporting the current row's real on-screen rect the
 * same way the grid's current card does.
 */
@Composable
fun TabListSwitcher(
    tabs: List<Tab>,
    currentId: Long,
    // Whether the switcher is the thing on screen. Not `progress` — this
    // surface has its own entrance and takes nothing from the page's shrink,
    // which in this mode does not happen at all.
    open: Boolean,
    onSelect: (Long, LayoutCoordinates) -> Unit,
    onClose: (Long) -> Unit,
    onCloseAll: () -> Unit,
    // Tapping the dimmed page above the sheet, or throwing the sheet down past
    // its threshold, closes the switcher — what every other sheet here does,
    // and now that there IS a visible surface with an outside, the outside is
    // somewhere a tap can mean something.
    onDismiss: () -> Unit,
    onCurrentRowPositioned: (LayoutCoordinates) -> Unit,
    // The toolbar's height. Zero when the caller already stops this surface
    // above the bar; non-zero when it is laid over the WHOLE screen (Aero),
    // so the scrim reaches the navigation bar while the sheet still clears
    // the toolbar.
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    // Where the sheet is, in root coordinates, for the page glass to frost
    // the page under it. Only Aero passes one.
    paneBounds: ListPaneBounds? = null,
    // Whether the sheet, open, reaches up under the status bar — the bar's
    // icons then sit on the sheet rather than on the page.
    onCoversStatusBar: (Boolean) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // The scrim's fade. Its own Animatable rather than a reading of the page's
    // `progress`: progress carries an [Overshoot] easing and saturates in its
    // first fifth, which as an alpha reads as a cut rather than a fade. It
    // starts at 0 because a tap sets switcherOpen in the same frame this
    // composable first appears — a value seeded from the target would seed at
    // 1 and there would be no entrance at all.
    val scrim = remember { Animatable(0f) }
    val scrimValue: () -> Float = { scrim.value }
    LaunchedEffect(open) {
        if (open) scrim.animateTo(1f, tween(SCRIM_FADE_IN_MS))
        else scrim.animateTo(0f, tween(SCRIM_FADE_OUT_MS))
    }

    // `tabs` is oldest-first. Fed in reverse (newest-first) to a
    // reverseLayout=true column, index 0 (newest) lands at the visual bottom
    // and index size-1 (oldest) at the visual top — oldest-to-newest reading
    // top-to-bottom, latest tab anchored nearest the toolbar.
    val newestFirst = remember(tabs) { tabs.asReversed() }

    val parentCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sheetWidthPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Under Aero the sheet is a floating BUBBLE rather than a surface
    // pinned to the toolbar: its glass is translucent, and a sheet whose
    // overhang runs down behind the (equally translucent) toolbar shows
    // through it as a second pane stacked on the bar. So it keeps a gap on
    // every side, rounds all four corners and never meets an edge.
    val aero = com.yuku.browser.ui.theme.LocalAero.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // The page runs under the status bar (PageTopStrip), so the list
            // does too — the scrim dims the strip with the page, and a sheet
            // that fills the screen reaches the screen's top edge rather than
            // stopping under the bar over a band of undimmed page. Laid out
            // taller by the strip and placed that much higher, exactly as
            // TabSwitcher's root. Aero is already laid over the whole window.
            .then(
                if (aero) Modifier else Modifier.layout { measurable, constraints ->
                    val up = PageTopStrip.px.roundToInt()
                    val height = constraints.maxHeight + up
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -up) }
                }
            )
            .onGloballyPositioned { parentCoords.value = it },
    ) {
        val density = LocalDensity.current
        val bubbleGap = if (aero) AERO_BUBBLE_GAP else 0.dp
        val bubbleGapPx = with(density) { bubbleGap.toPx() }
        val bottomInsetPx = with(density) { bottomInset.toPx() }
        // Let the closing sheet reach into the toolbar's rounded top corners,
        // but mask it to that outline before it can show through the bar.
        val fadeExtend = if (aero) {
            minOf(bottomInset, com.yuku.browser.ui.theme.aeroCornerOf(AERO_TOOLBAR_CORNER))
        } else 0.dp
        val fadeExtendPx = with(density) { fadeExtend.toPx() }
        val fadeBandPx = if (aero) bubbleGapPx else 0f
        // Laid over the whole window under Aero, so a list long enough to fill
        // the screen would otherwise run up under the status bar.
        // Everywhere else the box itself reaches up under the bar by the strip.
        val statusTopPx = if (aero) {
            androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density).toFloat()
        } else PageTopStrip.px.roundToInt().toFloat()
        val availablePx = (constraints.maxHeight - statusTopPx - bottomInsetPx - bubbleGapPx * 2).coerceAtLeast(0f)
        // What the sheet would be if nothing capped it: the header plus one
        // pitch per tab. Computed from constants rather than measured, because
        // this number is the ANIMATION's target and has to exist before the
        // rows do — a height read back out of the laid-out list would only be
        // known one frame after the frame that needed it.
        val contentPx = with(density) {
            (SHEET_HEADER_HEIGHT + LIST_TOP_PADDING + LIST_BOTTOM_PADDING).toPx() +
                (ROW_HEIGHT + ROW_GAP * 2).toPx() * tabs.size
        }
        // "Close all" is a row of the list itself (see the item below), so it
        // is only ever inside a sheet that already fills the screen — its
        // height is never part of what decides one.

        // A sheet short enough to leave a strip of dimmed page above it keeps
        // one: that strip is what says there is still a page back there to
        // dismiss onto. But there are only two honest answers, and a sheet
        // stopped a finger's width short of the top is neither — it is a full
        // screen with a sliver of something else showing. So once the tabs
        // outgrow that cap the sheet takes the WHOLE space instead of settling
        // just under it, and its corners square off against the status bar
        // (see the shape below), which is what a surface that has run out of
        // room to be a sheet should look like.
        val politeCeilingPx = (availablePx - with(density) { SHEET_TOP_INSET.toPx() })
            .coerceAtLeast(0f)
        val fullScreen = contentPx > politeCeilingPx
        // Full screen means the SCREEN: under the status bar too (not under
        // Aero, whose bubble keeps clear of every edge). The header is padded
        // back down out from under the bar — see sheetTopPad.
        val underBarPx = if (aero) 0f else statusTopPx
        val targetPx = if (fullScreen) availablePx + underBarPx else contentPx

        // How far the sheet is below its resting place, as a fraction of its
        // own height: 0 at rest, 1 with the whole of it off the bottom edge.
        // ONE number for both the entrance and the drag, which is what keeps a
        // sheet grabbed halfway through its arrival from having two opinions
        // about where it is. Read in the draw phase only (see the layer that
        // uses it), never in this composition scope.
        val slide = remember { Animatable(1f) }
        val slideValue: () -> Float = { slide.value }

        // Set as the INITIAL scroll position (rather than scrolling to it
        // after first composition) so a just-opened or just-selected tab is
        // already in view on the very first frame — mirrors TabSwitcher's own
        // initialIndex. Only when the list can actually scroll, though: a list
        // that fits has one right position, and anchoring a bottom-anchored
        // list on an item part way up it would hold that item at the bottom
        // edge while the sheet grew and then drop the whole stack into place
        // when the content turned out to fit after all.
        val initialIndex = remember(fullScreen, newestFirst, currentId) {
            if (!fullScreen) 0
            else newestFirst.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        LaunchedEffect(open) {
            if (open) slide.animateTo(0f, arrive(SURFACE_ENTER_MS))
            else slide.animateTo(1f, depart(SURFACE_EXIT_MS))
        }

        // The sheet's height is not animated on the way in — the slide is the
        // whole entrance — but it still has to give a row's worth back when a
        // tab is closed under it, and dropping that instantly while the row
        // itself animates out is a jump. So: snapped to its target the first
        // time (and while off-screen, where nothing can see it move), animated
        // to it afterwards. Read in the LAYOUT phase only (see the layout
        // modifier), or every frame of that settle would recompose the list.
        val heightAnim = remember { Animatable(targetPx) }
        LaunchedEffect(targetPx) {
            if (!open) heightAnim.snapTo(targetPx)
            else heightAnim.animateTo(targetPx, arrive(SNAP_BACK_MS))
        }

        // A drag on the header, the sheet's own dismissal. Everything the
        // gesture needs lives on a plain (non-snapshot) latch written from
        // composition: state read inside a pointerInput is read through a
        // snapshot that does not advance with composition, and the block keeps
        // whatever it closed over — callbacks included — until its key
        // changes. See CLAUDE.md's pointerInput note.
        val drag = remember { DragLatch() }
        // Where a drag or a swipe on the list leaves the sheet once the finger
        // goes: back to its own height, or out of the way entirely. One
        // function for both routes in, so a throw down the handle and a throw
        // down the rows cannot disagree about what counts as far enough.
        // [velocityY] is the finger's own speed at the lift, in pixels per
        // second, downward positive. It is half of the answer and often the
        // whole of it: a flick is a short, fast movement, and judged on
        // DISTANCE alone a sheet flicked hard enough to be unmistakably thrown
        // away travelled a third of its height and sprang straight back. That
        // was the swipe "working half the time" — it was working exactly as
        // often as the gesture happened to be slow enough to be a drag.
        // How far down counts as thrown away. A fraction of the sheet's own
        // height, but never more than a fixed distance — on a sheet that fills
        // the screen a third of it is most of a thumb's reach, and a swipe that
        // long is not a swipe.
        val dismissDistancePx = with(density) { DISMISS_DISTANCE.toPx() }
        fun settleSheet(velocityY: Float) {
            val travelled = slide.value * targetPx
            val enough = minOf(targetPx * DISMISS_FRACTION, dismissDistancePx)
            if (velocityY > FLING_DISMISS_VELOCITY || travelled > enough) {
                // Dismissed now, not when the slide finishes: the scrim's fade
                // and the page coming back forward are the other half of this
                // exit, and they are started by the same flag. The slide runs
                // it out the rest of the way underneath them.
                onDismiss()
                scope.launch { slide.animateTo(1f, depart(SURFACE_EXIT_MS)) }
            } else {
                scope.launch { slide.animateTo(0f, arrive(SNAP_BACK_MS)) }
            }
        }
        // Whether a downward drag is the LIST's to use rather than the
        // sheet's.
        //
        // `canScrollForward` is the right one of the two, and it is worth
        // saying why, because `reverseLayout` makes both readings sound
        // plausible and the wrong one silently swaps scrolling for dismissal.
        // Measured on the device: a freshly opened list reports
        // forward=true, backward=false with the newest tab at the bottom and
        // the older ones off the top — so "forward" is the direction that
        // brings the older tabs into view, and the finger movement that does
        // it is DOWNWARD (reverseLayout inverts the axis, not the reading of
        // it). Read in composition, where it flips only at the list's ends,
        // and handed to the gesture on the latch.
        //
        // AND [fullScreen], which is the part that took a device to find: a
        // sheet that fits is sized to its own content, so the list inside it
        // has a scroll range of a rounded pixel or two — sometimes zero,
        // sometimes not, depending on where the row heights land. On the
        // frames where it was "scrollable" the list took the drag, had a
        // pixel's worth of use for it, and the sheet never moved: the swipe
        // that worked four times in five and then didn't. Only a list that
        // outgrew the screen is really scrollable, and that is exactly what
        // fullScreen means.
        val listTakesDown = fullScreen && listState.canScrollForward
        // Leaving composition takes the frosted region with it: the holder
        // otherwise keeps answering the sheet's last open position, and the
        // page stayed blurred under a list that was gone.
        androidx.compose.runtime.DisposableEffect(paneBounds) {
            onDispose {
                paneBounds?.rect = { Rect.Zero }
                paneBounds?.fade = { Offset.Zero }
            }
        }
        // The page glass that frosts under this sheet is a DIFFERENT layer, and
        // while the sheet moves its frosted region lands a frame behind the
        // sheet's own translation — on a downward swipe the previous, higher
        // rect showed as blur sticking out past the top corners. So the rect
        // is pulled in on its LEADING edge by the last frame's travel; once
        // the slide has been still for a moment the guard drops to zero.
        val paneMotion = remember { PaneMotion() }
        val paneRestTick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { slide.value }.collect {
                paneMotion.restJob?.cancel()
                paneMotion.restJob = launch {
                    kotlinx.coroutines.delay(PANE_REST_MS)
                    paneRestTick.intValue++
                }
            }
        }
        SideEffect {
            onCoversStatusBar(open && fullScreen && underBarPx > 0f)
            drag.target = targetPx
            drag.listTakesDown = listTakesDown
            drag.onDismiss = onDismiss
            drag.settle = ::settleSheet
            paneBounds?.rect = rect@{
                // The animated values FIRST, so the page layer reading this
                // re-runs every frame of the slide even before coordinates exist.
                val shift = slide.value * (targetPx + bubbleGapPx + fadeExtendPx)
                val h = heightAnim.value
                val w = sheetWidthPx.intValue
                val parent = parentCoords.value?.takeIf { it.isAttached } ?: return@rect Rect.Zero
                if (h <= 0f || w == 0) return@rect Rect.Zero
                val origin = parent.localToRoot(Offset.Zero)
                val left = origin.x + (parent.size.width - w) / 2f
                val clipBottom = origin.y + parent.size.height - bottomInsetPx
                // Followed all the way down, NOT cut at the toolbar line: the
                // frosting fades out over the same band the sheet does (see
                // `fade`), so the two leave together.
                val containerBottom = clipBottom + fadeExtendPx
                val bottom = clipBottom - bubbleGapPx + shift
                val top = bottom - h
                if (top >= containerBottom) return@rect Rect.Zero
                val tick = paneRestTick.intValue
                if (shift != paneMotion.last) {
                    paneMotion.prev = paneMotion.last
                    paneMotion.last = shift
                    paneMotion.tick = tick
                }
                val delta = if (paneMotion.tick == tick) paneMotion.last - paneMotion.prev else 0f
                val guardedTop = top + delta.coerceAtLeast(0f) * PANE_LAG_FRAMES
                val guardedBottom = bottom + delta.coerceAtMost(0f) * PANE_LAG_FRAMES
                if (guardedBottom <= guardedTop) return@rect Rect.Zero
                Rect(left, guardedTop, left + w, guardedBottom)
            }
            paneBounds?.fade = fade@{
                if (fadeBandPx <= 0f) return@fade Offset.Zero
                val parent = parentCoords.value?.takeIf { it.isAttached } ?: return@fade Offset.Zero
                // The sheet itself curves around the toolbar, but the page
                // frost must be gone at the bar's top edge; otherwise the
                // glass makes a rectangular remnant visible behind the bar.
                val barTop = parent.localToRoot(Offset.Zero).y + parent.size.height - bottomInsetPx
                Offset(barTop - fadeBandPx, barTop)
            }
        }

        // A swipe down anywhere on the list closes it, not just one on the
        // handle — a list of tabs is mostly rows, and a surface that can only
        // be thrown away by its 40dp header is one most swipes miss.
        //
        // The leftover, in onPostScroll: the rows are offered the movement
        // first and the sheet only takes what the list itself could not use,
        // which is what keeps a scroll of a long list from also dragging the
        // sheet down under it. The mirror is in onPreScroll — an upward swipe
        // on a sheet that has already been pulled part-way down gives its
        // height back BEFORE the list scrolls, so one gesture can push it
        // down and pull it back without ever moving the rows.
        //
        // Deliberately not [drag]'s own detector on the whole surface: two
        // vertical detectors on one node race, and the list would lose.
        val nested = remember {
            object : NestedScrollConnection {
                // Whether THIS gesture may hand its leftover to the sheet,
                // decided once, from the list's state at the moment it began.
                //
                // Without it one long swipe does two things: it scrolls the
                // list to its end and then, still in the same movement, drags
                // the sheet away — which put the end of the list (where "Close
                // all" lives) permanently out of reach, since arriving there
                // and dismissing were the same gesture. A scroll that runs out
                // of list now simply stops; the sheet's turn comes on the NEXT
                // swipe, which starts with the list already at its end.
                var handsOff = false
                var started = false

                private fun begin() {
                    if (started) return
                    started = true
                    handsOff = !drag.listTakesDown
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) begin()
                    val dy = available.y
                    if (dy >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                    val height = drag.target
                    if (height <= 0f) return Offset.Zero
                    val current = slide.value * height
                    if (current <= 0f) return Offset.Zero
                    val used = min(-dy, current)
                    scope.launch { slide.snapTo((current - used) / height) }
                    return Offset(0f, -used)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = available.y
                    if (dy <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (!handsOff) return Offset.Zero
                    val height = drag.target
                    if (height <= 0f) return Offset.Zero
                    val current = slide.value * height
                    val room = height - current
                    if (room <= 0f) return Offset.Zero
                    val used = min(dy, room)
                    scope.launch { slide.snapTo((current + used) / height) }
                    return Offset(0f, used)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    // The finger is gone; the next scroll is a new gesture and
                    // gets its own answer. Taken here rather than in
                    // onPostFling, which waits for the child's own fling to
                    // finish — a hold as long as the fling.
                    started = false
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    // The lift, whatever the finger left behind — a fling's
                    // own deltas never reach the two hooks above (they are
                    // finger-only), so this is where a sheet dragged part-way
                    // down decides whether it goes back up or goes away. The
                    // velocity the list could not use is the same velocity the
                    // finger had, which is exactly what the settle wants.
                    //
                    // Only when this gesture actually moved the sheet: a fling
                    // that stayed inside the list has nothing to settle, and
                    // asking anyway would judge it on a velocity that belongs
                    // to the rows.
                    if (handsOff) drag.settle(available.y)
                    return Velocity.Zero
                }
            }
        }

        // The scrim. Dismisses on a tap like every other sheet's does, and its
        // alpha is read in the DRAW phase — the fade moves once per frame, and
        // reading it here would recompose the list on each of those.
        // The dim covers everything, the toolbar included; the TOUCH area
        // stops at the toolbar's top edge, so a tap on the + or menu button
        // reaches the bar (which closes the list and opens that sheet) rather
        // than being swallowed here as a plain dismissal.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimValue() }
                .background(Color.Black.copy(alpha = when {
                    com.yuku.browser.ui.theme.LocalAero.current -> 0.12f
                    com.yuku.browser.ui.theme.LocalFrosted.current -> 0.20f
                    else -> SCRIM_ALPHA
                })),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset)
                // The dismissing tap AND the swallowing of everything else, in
                // one gesture loop — which is the point, because they cannot be
                // two. The page behind this is the LIVE page (see
                // BrowserScreen's liveVisible), and a live WebView is a real
                // view whose touch area is its full layout bounds however it is
                // drawn, so every touch that lands up here has to be consumed
                // or it scrolls the page underneath. A `clickable` beside a
                // loop that consumes everything never fires: the loop is the
                // inner node, the Main pass runs child-to-parent, and by the
                // time the click detector is offered the down it has already
                // been consumed. So the tap is decided here: a press that lifts
                // without travelling past touch slop is a tap, and a tap on the
                // strip of dimmed page above the sheet dismisses it, the same
                // as every other sheet's outside.
                //
                // On the SCRIM, not on the switcher's root, and that
                // distinction cost an afternoon: at the root it is an ancestor
                // of the sheet, so it saw — and consumed — the rows' own
                // drags. A consumed change reports no movement to anything
                // that reads it afterwards, so the list's scroller was handed
                // a stream of zero-length drags and the tabs would not scroll
                // at all. Here it is a SIBLING the sheet is drawn over, and a
                // touch that lands on the sheet never reaches it.
                //
                // Keyed on Unit, and the only thing it closes over is the
                // latch — a permanently-running gesture loop must not capture
                // a callback (see the pointerInput note in CLAUDE.md), and
                // [drag] is where the live one already is.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            travelled += change.positionChange()
                            if (!change.pressed) {
                                if (travelled.getDistance() <= slop) drag.onDismiss()
                                break
                            }
                        }
                    }
                },
        )

        val cornerPx = with(density) { SHEET_CORNER.toPx() } * SpecialCornerScale
        val cornerFillPx = with(density) { CORNER_FILL_DISTANCE.toPx() }
        // The corner only flattens against the top of the SCREEN, and it only
        // reaches it when the sheet is full-bleed: turned sideways the sheet
        // is half the width (see sheetWidth), so there is page either side of
        // it at every height and its top corners are edges the whole time.
        val cornerFlattens = !aero && LocalConfiguration.current.orientation !=
            Configuration.ORIENTATION_LANDSCAPE
        // The sheet's travel is clipped at the toolbar's top edge, so it rises
        // out from under the bar and sinks back under it; the scrim above is
        // outside this clip and still reaches the navigation bar.
        // Under Aero the clip reaches into the toolbar only as far as its
        // rounded top corners. While the sheet moves, a shader fades it to
        // that same curve, so no rectangular sliver can show through the
        // translucent navbar. Offscreen only while moving — a full-screen
        // layer at rest would be a buffer spent on nothing.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset - fadeExtend)
                .graphicsLayer {
                    clip = true
                    compositingStrategy =
                        if (fadeBandPx > 0f && slideValue() > 0f) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        else androidx.compose.ui.graphics.CompositingStrategy.Auto
                }
                .aeroToolbarFadeIf(
                    fadeBandPx = fadeBandPx,
                    toolbarCornerPx = with(density) {
                        com.yuku.browser.ui.theme.aeroCornerOf(AERO_TOOLBAR_CORNER).toPx()
                    },
                    active = { slideValue() > 0f },
                ),
        ) {
        Box(
            modifier = Modifier
                // Full-bleed upright, half the screen turned sideways — the
                // same sheet width every other sheet takes. See sheetWidth.
                .then(sheetWidth())
                .align(Alignment.BottomCenter)
                .padding(start = bubbleGap, end = bubbleGap, bottom = bubbleGap + fadeExtend)
                .onSizeChanged { sheetWidthPx.intValue = it.width }
                // The entrance and the drag, on the SURFACE's own layer so its
                // background, corners and rows all travel together — and read
                // in the draw phase, where a moving value costs one layer
                // property and no recomposition. Declared before the layout
                // modifier for the same reason it is a layer at all: the sheet
                // slides, it is not re-laid-out per frame.
                .graphicsLayer { translationY = slideValue() * (targetPx + bubbleGapPx + fadeExtendPx) }
                // Read in the LAYOUT phase, not during composition — see
                // heightAnim above, and the identical modifier on
                // BrowserScreen's sheet for the measurements behind it.
                .layout { measurable, constraints ->
                    val h = heightAnim.value.roundToInt().coerceIn(0, constraints.maxHeight)
                    // Measured one corner-radius TALLER than it reports itself
                    // to be. The parent aligns this node's own (h-tall) box to
                    // the bottom, so the surplus hangs off that edge — under
                    // the toolbar, which is the same colour — and the two
                    // bottom corners round out of sight there. The Column
                    // inside subtracts the same amount back as padding.
                    val overhang = if (aero) 0 else SHEET_CORNER.roundToPx()
                    // Measured at the height the sheet reports, not at some
                    // settled value it is animating towards. That only matters
                    // now for the settle after a tab is closed, and it matters
                    // because this list is BOTTOM-anchored (reverseLayout,
                    // newest tab nearest the toolbar): re-measuring to the new
                    // target instantly while the surface took 230ms to follow
                    // left a growing band of empty sheet under the last row for
                    // the whole of it. Measured here, the list's viewport IS
                    // the sheet's height on every frame of that settle, so the
                    // rows stay exactly where they are against the toolbar.
                    val contentH = (h + overhang).coerceAtMost(constraints.maxHeight + overhang)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = contentH, maxHeight = contentH),
                    )
                    // Measured-but-not-placed while closed: the contents stay
                    // composed and laid out, ready for the next open, without
                    // costing a draw pass for something entirely off-screen.
                    layout(placeable.width, h) { if (h > 0) placeable.place(0, 0) }
                },
        ) {
            // The sheet's own face, and the node that is actually painted and
            // clipped. It is the TALLER one — the layout modifier above
            // reports the sheet's height while measuring this at that height
            // plus the overhang — which is the whole reason the corner can
            // be animated at all: a clip is applied at the node's own bounds,
            // so a clip on the node above would round the sheet's bottom
            // corners at the visible bottom edge instead of below it.
            //
            // Uniform on all four corners, NOT topStart/topEnd only — a
            // RoundedCornerShape whose corners aren't all equal can't be
            // expressed as a RenderNode outline, so Compose falls back to
            // clipping with a Path and forces the whole sheet through an
            // offscreen buffer on every draw. Uniform, it is one cheap
            // rounded-rect outline however often the radius changes, and the
            // bottom two are simply kept off-screen (see the overhang).
            Box(
                Modifier
                    .fillMaxSize()
                    // The corner fills in as the sheet's top edge approaches
                    // the top of the screen and rounds back out as it leaves:
                    // a rounded corner is the edge of a surface with something
                    // behind it, and by the time there is nothing behind it
                    // there is no edge to draw. Continuous rather than a state
                    // change at the full-height threshold — the sheet grows
                    // into that height and is dragged back out of it, and a
                    // radius that snapped at some point along the way would be
                    // the one frame in the whole movement that looks like a
                    // different sheet.
                    //
                    // Both terms of the sheet's position are here: the height
                    // it has grown to, and how far the finger has since pushed
                    // it back down. Draw phase, like everything else that
                    // moves per frame.
                    .graphicsLayer {
                        val top = availablePx + underBarPx - heightAnim.value + slideValue() * targetPx
                        val filled =
                            if (cornerFlattens) (top / cornerFillPx).coerceIn(0f, 1f) else 1f
                        shape = RoundedCornerShape(cornerPx * filled)
                        clip = true
                    }
                    // Under Aero, the same glass the + and menu sheets are made
                    // of — their colour and alpha, frosted by the page glass
                    // (see paneBounds) — rather than the thinner toolbar glass.
                    // Under Aero, the toolbar's own glass — its fill, rim and
                    // grain, nothing denser — so every pane reads as one material.
                    .background(
                        if (com.yuku.browser.ui.theme.LocalFrosted.current) com.yuku.browser.ui.theme.frostedSheetFill(
                            BarBg, com.yuku.browser.ui.theme.AccentColor, com.yuku.browser.ui.theme.LocalNothing.current,
                        ) else BarBg
                    )
                    .then(
                        if (aero) Modifier.aeroDroplet(SHEET_CORNER).grain(AERO_BAR_GRAIN)
                        else Modifier
                    )
                    .tuiCrtIf()
                    .tuiBloomIf(),
            ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    // Keeps the handle and rows out from under the status bar
                    // by however much of the bar the sheet has grown into.
                    // Off the HEIGHT only, in layout — the slide carries the
                    // padded content down with the surface, so it need not
                    // re-lay the list out per frame of it.
                    .layout { measurable, constraints ->
                        val pad = (underBarPx - (availablePx + underBarPx - heightAnim.value))
                            .coerceIn(0f, underBarPx).roundToInt()
                        val h = (constraints.maxHeight - pad).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = minOf(constraints.minHeight, h), maxHeight = h),
                        )
                        layout(placeable.width, constraints.maxHeight) { placeable.place(0, pad) }
                    }
                    // Gives back the off-screen overhang the layout modifier
                    // added for the bottom corners.
                    .padding(bottom = if (aero) 0.dp else SHEET_CORNER)
                    // The list inside dispatches its scroll here first — see
                    // the connection's own note.
                    .nestedScroll(nested)
                    // …and this is the other half of the same gesture, for the
                    // case that connection never hears about: a drag that
                    // starts on a ROW. A LazyColumn whose rows already fit —
                    // which is most of them, since the sheet is sized to its
                    // tabs — still takes the gesture for its own overscroll
                    // and reports nothing onward, so the swipe worked on the
                    // handle and on the padding between rows and essentially
                    // nowhere else.
                    //
                    // So the sheet takes it FIRST, in the Initial pass, which
                    // travels parent to child: this runs before the rows and
                    // before the list's scroller see anything at all. What
                    // keeps that from stealing the list's own scrolling is the
                    // condition, not the ordering — it claims a downward drag
                    // only when the list has nowhere left to go with one
                    // (drag.listTakesDown), and until it claims it consumes
                    // NOTHING, so an unclaimed gesture reaches the rows in the
                    // Main pass untouched. A row's own tap is likewise
                    // unaffected: a click is decided on the up, and nothing
                    // here claims anything before touch slop.
                    //
                    // Keyed on Unit: a key change cancels the press in flight,
                    // and everything that varies is on the latch.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val height = drag.target
                            if (height <= 0f) return@awaitEachGesture
                            val slop = viewConfiguration.touchSlop
                            val velocity = VelocityTracker()
                            velocity.addPosition(down.uptimeMillis, down.position)
                            var total = 0f
                            var claimed = false
                            var pulledFrom = 0f
                            var travelled = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) {
                                    if (claimed) drag.settle(velocity.calculateVelocity().y)
                                    break
                                }
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val dy = change.positionChange().y
                                if (!claimed) {
                                    total += dy
                                    if (total > slop && !drag.listTakesDown) {
                                        // Ours: measured from where the sheet
                                        // already is, and from past the slop,
                                        // so the surface does not jump the
                                        // width of it on the first frame.
                                        claimed = true
                                        pulledFrom = slide.value
                                        travelled = total - slop
                                    } else if (abs(total) > slop) {
                                        // Someone else's — an upward drag, or
                                        // a downward one the list can still
                                        // use. Left entirely alone: nothing
                                        // has been consumed, so the rows and
                                        // their scroller see this gesture in
                                        // the Main pass exactly as if this
                                        // block were not here, and the sheet's
                                        // turn (should the list run out
                                        // mid-scroll) comes through the
                                        // nested-scroll connection above.
                                        break
                                    }
                                } else {
                                    travelled += dy
                                }
                                if (claimed) {
                                    change.consume()
                                    val next = (pulledFrom + travelled / height)
                                        .coerceIn(0f, 1f)
                                    if (next != slide.value) {
                                        scope.launch { slide.snapTo(next) }
                                    }
                                }
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SHEET_HEADER_HEIGHT),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            // Placed by its top rather than centred in the
                            // header: what has to be equal is the gap to the
                            // sheet's own edge and the gap to the first row,
                            // and the space below the handle is split between
                            // the header and the list's own padding — a
                            // centred handle is only equidistant from the two
                            // halves of the header, which is not a thing
                            // anyone can see. See SHEET_HANDLE_GAP.
                            .padding(top = SHEET_HANDLE_GAP)
                            .size(width = 32.dp, height = SHEET_HANDLE_HEIGHT)
                            .clip(specialCorner(2.dp))
                            .background(InkMuted),
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Aero: rows bend away at an edge with more tabs past
                        // it, like the curve of a tube, instead of being cut.
                        // A content-fitting sheet may report a pixel or two of
                        // scroll range from rounding, but that is not enough
                        // content to justify the effect (or a scroll gesture).
                        .crtEdgesIf(
                            enabled = aero && fullScreen,
                            top = listState.canScrollForward,
                            bottom = listState.canScrollBackward,
                        ),
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = LIST_TOP_PADDING, bottom = LIST_BOTTOM_PADDING),
                ) {
                    itemsIndexed(newestFirst, key = { _, tab -> tab.id }) { _, tab ->
                        TabListRow(
                            modifier = Modifier.animateItem(placementSpec = arrive(280)),
                            tab = tab,
                            current = tab.id == currentId,
                            onClick = { coords -> onSelect(tab.id, coords) },
                            onClose = { onClose(tab.id) },
                            onCurrentRowPositioned = onCurrentRowPositioned,
                        )
                    }
                    // Past the oldest tab, and only when there is scrolling to
                    // do — a row of the list rather than a fixture of the
                    // sheet, so it is somewhere the user arrives at the end of
                    // reading the tabs rather than something in front of them
                    // the whole time. `reverseLayout` puts the last item at the
                    // TOP, which is where the end of this list is.
                    if (fullScreen) {
                        item(key = CLOSE_ALL_KEY) {
                            // The slot is a tab row's: the same width, the
                            // same gap either side, the same height, so the
                            // end of the column keeps the column's rhythm.
                            // The BUTTON inside it is the one the vertical
                            // switcher uses (see TabSwitcher's CloseAllSlot) —
                            // an OutlinedButton at its own natural size,
                            // centred. Both switchers offer the same action
                            // and it should be the same control in both; what
                            // changes between them is only the space it is
                            // given to sit in.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = ROW_GAP)
                                    .height(ROW_HEIGHT)
                                    .animateItem(placementSpec = arrive(280)),
                                contentAlignment = Alignment.Center,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.confirm()
                                        onCloseAll()
                                    },
                                    shape = specialCorner(if (aero) 24.dp else 18.dp),
                                    // Under Aero, the omnibox's pill at button
                                    // size: its thin well of light, its field
                                    // glass, its glare and its pop — and no flat
                                    // outline, which the glass rim replaces.
                                    border = if (aero) null else androidx.compose.material3.ButtonDefaults.outlinedButtonBorder(enabled = true),
                                    modifier = if (aero) Modifier
                                        .aeroPopIf()
                                        .clip(specialCorner(24.dp))
                                        .background(FieldBg.copy(alpha = 0.22f))
                                        .aeroGlassIf(24.dp, Glassy.Field)
                                        .aeroGlareIf(24.dp)
                                    else Modifier,
                                ) {
                                    Text("Close all", color = Ink)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        }
    }
}

/**
 * What the header's drag gesture reads, held in a plain object rather than in
 * snapshot state — see the note where it is written.
 */
/**
 * The list sheet's bounds in root coordinates, computed on demand from its
 * live animation values — read inside the page glass's `graphicsLayer`, so a
 * slide re-runs only that layer. A plain holder, written by a SideEffect.
 */
class ListPaneBounds {
    // Snapshot state, so the page layer reading it re-runs when the lambda is
    // first installed — a plain var read nothing, and the blur never started.
    var rect: () -> Rect by mutableStateOf({ Rect.Zero })

    /** (top, bottom) y, in root coordinates, of the band the sheet fades out over. */
    var fade: () -> Offset by mutableStateOf({ Offset.Zero })
}

/** The last two frosted-rect shifts, for the lag guard — see paneMotion. */
private class PaneMotion {
    var last = 0f
    var prev = 0f
    var tick = 0
    var restJob: kotlinx.coroutines.Job? = null
}

/** How many frames of travel the frosted rect is pulled in by while moving. */
private const val PANE_LAG_FRAMES = 1f

/** How long the slide must be still before the guard is dropped. */
private const val PANE_REST_MS = 60L

private class DragLatch {
    var target: Float = 0f
    // Whether a downward drag is the LIST's to use — true only while there
    // are rows above the ones on screen for it to scroll to. Written from
    // composition, because a gesture may not read snapshot state (the list
    // state included) and have it advance under its own fingers.
    var listTakesDown: Boolean = false
    var onDismiss: () -> Unit = {}
    var settle: (Float) -> Unit = {}
}

// The sheet's shape and skeleton. Deliberately the same numbers as
// BrowserScreen's + and menu sheets: this is meant to be recognised as the
// same object, and a corner radius that is nearly-but-not-quite theirs is the
// one way to make two surfaces look like a mistake rather than a family.
private val SHEET_CORNER = 28.dp

/** Under Aero, the clear space kept round the floating sheet on every side. */
private val AERO_BUBBLE_GAP = 10.dp

/**
 * The Aero toolbar's top-corner radius. The tab sheet's exit mask follows this
 * exact outline, so its fade wraps around the navbar rather than cutting a
 * straight line across it.
 */
private val AERO_TOOLBAR_CORNER = 22.dp

/**
 * How close the sheet's top edge gets to the top of the screen before its
 * corner has filled in completely — the distance the rounding is spent over,
 * rather than a height at which it switches. Roughly the corner's own diameter,
 * so the curve is gone about when the space it was drawn in is.
 */
private val CORNER_FILL_DISTANCE = 56.dp

/** The drag handle, and the gap it keeps on both sides of itself. */
private val SHEET_HANDLE_HEIGHT = 4.dp
private val SHEET_HANDLE_GAP = 16.dp

/**
 * None: the space under the handle is [SHEET_HANDLE_GAP] and is spent inside
 * the header, so anything here would be added to it and the handle would sit
 * nearer the sheet's edge than the first row.
 */
private val LIST_TOP_PADDING = 0.dp

/**
 * The gap under the lowest row.
 *
 * Wider than the top one on purpose: the sheet's bottom edge is the toolbar,
 * not empty screen, so a row that ends where the sheet does is a row pressed
 * against the tabs button — and the row nearest the bar is the newest tab,
 * which is the one most often reached for.
 */
private val LIST_BOTTOM_PADDING = 20.dp

/**
 * The item key of the "Close all" row, past the oldest tab. A constant of its
 * own, and deliberately not a number a tab id could also be — keys share one
 * namespace with the rows'.
 */
private const val CLOSE_ALL_KEY = "close-all"

/**
 * The strip of dimmed page a sheet that fits keeps above itself.
 *
 * What is left visible up there is the page the list was opened from, which is
 * both what says the page hasn't gone anywhere and where a dismissing tap
 * lands. It is also the threshold: a list that would need this strip to fit is
 * one that has stopped being a sheet, and takes the whole screen instead of
 * ending a thumb's width short of the top.
 */
private val SHEET_TOP_INSET = 72.dp

/** A row, and the pitch of the list: the row plus the gap either side of it. */
private val ROW_HEIGHT = 68.dp
private val ROW_GAP = 3.dp

/**
 * The header, sized so the handle is exactly [SHEET_HANDLE_GAP] from the
 * sheet's top edge AND from the top row's own edge: the gap above it, the
 * handle, then the gap below minus what the list already puts there itself.
 *
 * Declared after the two it subtracts — top-level vals in one file initialise
 * in declaration order, and a forward reference here would silently read 0.
 */
private val SHEET_HEADER_HEIGHT =
    SHEET_HANDLE_GAP * 2 + SHEET_HANDLE_HEIGHT - ROW_GAP - LIST_TOP_PADDING

/**
 * The rows' own corner. Well short of a stadium — at half of [ROW_HEIGHT] the
 * row's ends turn into caps and the favicon at the start of one sits in the
 * curve rather than beside it — but round enough to read as the same family
 * of shapes as the sheet holding them.
 */
private val ROW_CORNER = 18.dp

// The scrim's fade. Plain tweens, no easing from Motion.kt: those curves are
// about a thing travelling and settling, and an alpha has nowhere past 1 to
// settle onto. Leaving is shorter than arriving, the rule everywhere else
// here, and both match the sheets' own scrim.
private const val SCRIM_ALPHA = 0.32f
private const val SCRIM_FADE_IN_MS = 180
private const val SCRIM_FADE_OUT_MS = 180

/**
 * How far down the sheet has to have travelled, as a fraction of its own
 * height, for a SLOW lift to dismiss it rather than put it back.
 */
private const val DISMISS_FRACTION = 0.3f

/**
 * …capped at this, so a full-screen sheet is not harder to throw away than a
 * short one — and short enough that a flick nobody would describe as a long
 * drag still clears it on distance alone, whatever a velocity estimate made of
 * its last few samples.
 */
private val DISMISS_DISTANCE = 100.dp

/**
 * …and the speed, in pixels per second, at which the distance stops mattering:
 * anything thrown downward faster than this is gone wherever it got to. Around
 * a finger's width of travel in a tenth of a second — deliberately low enough
 * that a deliberate flick always clears it, since the cost of being wrong is a
 * sheet that can be swiped straight back up.
 */
private const val FLING_DISMISS_VELOCITY = 500f

@Composable
private fun TabListRow(
    modifier: Modifier = Modifier,
    tab: Tab,
    current: Boolean,
    onClick: (LayoutCoordinates) -> Unit,
    onClose: () -> Unit,
    onCurrentRowPositioned: (LayoutCoordinates) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var rowCoords by remember(tab.id) { mutableStateOf<LayoutCoordinates?>(null) }
    // Tapping the close button shrinks and fades this exact row in place —
    // the list's own equivalent of the grid card's swipe-away — before the
    // tab is actually removed, so animateItem (see the caller) then has a
    // clean, already-gone row to reflow the rest around rather than a jump.
    val closeScale = remember(tab.id) { Animatable(1f) }
    val closeAlpha = remember(tab.id) { Animatable(1f) }
    var closing by remember(tab.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ROW_GAP)
            // Fixed rather than sized by its content: the sheet's height is
            // computed from the tab COUNT before a single row exists (see
            // contentPx), so the row that count is multiplied by has to be a
            // number this file knows, not one the layout discovers.
            .height(ROW_HEIGHT)
            .graphicsLayer {
                scaleX = closeScale.value
                scaleY = closeScale.value
                alpha = closeAlpha.value
            }
            .onGloballyPositioned { coords ->
                rowCoords = coords
                if (current) onCurrentRowPositioned(coords)
            }
            .clip(specialCorner(ROW_CORNER))
            .then(
                // Under Aero a row is a droplet of glass in the glass sheet —
                // translucent, with the tab cards' rim and glare — rather than
                // an opaque slab with a hairline round it.
                if (com.yuku.browser.ui.theme.LocalAero.current) Modifier
                    .background(
                        if (tab.isPrivate) Color(0xFF121212).copy(alpha = 0.45f)
                        else com.yuku.browser.ui.theme.FieldBg.copy(alpha = 0.22f),
                    )
                    .aeroDroplet(ROW_CORNER, thin = true, glare = true)
                // Translucent sheets: a see-through slab, no grain, with its
                // hairline, over the frosted sheet.
                else if (com.yuku.browser.ui.theme.LocalFrosted.current) Modifier
                    .background(
                        (if (tab.isPrivate) Color(0xFF121212) else PageBg)
                            .copy(alpha = com.yuku.browser.ui.theme.FROSTED_ELEMENT_ALPHA),
                    )
                    .border(width = 1.dp, color = HairLine, shape = specialCorner(ROW_CORNER))
                else Modifier
                    .then(
                        // Under Nothing a row is a flat slab: the dot field is
                        // for canvases, and on a stack of rows it reads as noise.
                        if (com.yuku.browser.ui.theme.LocalNothing.current) Modifier
                            .background(if (tab.isPrivate) Color(0xFF121212) else PageBg)
                        else Modifier
                            .grainedBackground(if (tab.isPrivate) Color(0xFF121212) else PageBg)
                    )
                    .border(width = 1.dp, color = HairLine, shape = specialCorner(ROW_CORNER))
            )
            .clickable(enabled = !closing) {
                haptics.tap()
                rowCoords?.let(onClick)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.isPrivate) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
            }
            tab.favicon?.let { icon ->
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(specialCorner(3.dp)),
                )
            } ?: Box(modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                text = tab.label,
                color = Ink,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(4.dp))
            // A plain clickable box rather than IconButton — IconButton
            // enforces its own minimum touch target and padding, which
            // together would floor this row's height above what a dense,
            // desktop-like tab strip calls for. 44dp is as close to the 48dp
            // guideline as the row height allows.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(specialCorner(12.dp))
                    .clickable(enabled = !closing) {
                        haptics.confirm()
                        closing = true
                        scope.launch {
                            launch { closeScale.animateTo(0.85f, depart(160)) }
                            closeAlpha.animateTo(0f, depart(160))
                            onClose()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


/**
 * A CRT tube's curvature at a scrolling list's top and bottom edges, instead of
 * a straight crop: inside [CRT_EDGE_BAND] of an edge the rows are squeezed
 * toward it (the source runs [CRT_EDGE_SQUEEZE]x faster at the very edge and
 * blends back to 1:1 at the band's inner end, so the interior is untouched),
 * pinched inward, and dimmed a little — a surface turning away from the eye.
 * Each edge only bends while there is content past it (reverseLayout: forward
 * = older tabs above, backward = newer below), eased so it does not snap.
 *
 * API 33 (RuntimeShader); a no-op below it or if the shader fails to build.
 */
@Composable
private fun Modifier.crtEdgesIf(enabled: Boolean, top: Boolean, bottom: Boolean): Modifier {
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember {
        runCatching { android.graphics.RuntimeShader(CRT_EDGES_SHADER) }
            .onFailure { android.util.Log.e("TabListSwitcher", "CRT edge shader failed", it) }
            .getOrNull()
    } ?: return this
    val topAmount = androidx.compose.animation.core.animateFloatAsState(if (top) 1f else 0f, tween(180), label = "crtTop")
    val bottomAmount = androidx.compose.animation.core.animateFloatAsState(if (bottom) 1f else 0f, tween(180), label = "crtBottom")
    return this.graphicsLayer {
        val t = topAmount.value
        val b = bottomAmount.value
        if ((t <= 0f && b <= 0f) || size.width <= 0f || size.height <= 0f) {
            renderEffect = null
            return@graphicsLayer
        }
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("band", CRT_EDGE_BAND.toPx())
        shader.setFloatUniform("squeeze", CRT_EDGE_SQUEEZE)
        shader.setFloatUniform("pinch", CRT_EDGE_PINCH)
        shader.setFloatUniform("amount", t, b)
        renderEffect = android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

private const val CRT_EDGES_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float band;
    uniform float squeeze;
    uniform float pinch;
    uniform float2 amount; // top, bottom
    // Distance from the edge the output pixel shows, for an output distance d.
    float bend(float d, float k) {
        float t = clamp(d / band, 0.0, 1.0);
        return d + (squeeze - 1.0) * k * d * (1.0 - t) * (1.0 - t);
    }
    half4 main(float2 p) {
        float y = p.y;
        float w = 0.0;
        // How much of the dim is spent at this edge: the TOP fades all the way
        // out (nothing is left there to read as a crop line), the bottom only
        // dims, since the newest tab sits against it.
        float fadeDepth = 0.45;
        if (p.y < band && amount.x > 0.0) {
            fadeDepth = 1.0;
            y = bend(p.y, amount.x);
            w = (1.0 - p.y / band) * amount.x;
        } else if (size.y - p.y < band && amount.y > 0.0) {
            float d = size.y - p.y;
            y = size.y - bend(d, amount.y);
            w = (1.0 - d / band) * amount.y;
        }
        float cx = size.x * 0.5;
        float x = cx + (p.x - cx) * (1.0 + pinch * w * w);
        if (x < 0.0 || x > size.x || y < 0.0 || y > size.y) return half4(0.0);
        return content.eval(float2(x, y)) * half(1.0 - fadeDepth * w * w);
    }
"""

/** How far in from a list edge the curvature reaches. */
private val CRT_EDGE_BAND = 36.dp

/** How much faster the source runs at the very edge — the squeeze. */
private const val CRT_EDGE_SQUEEZE = 2.4f

/** How far the rows pull in from the sides at the very edge. */
private const val CRT_EDGE_PINCH = 0.08f

/**
 * Fades a closing Aero tab sheet into the open-topped outline of the bottom
 * toolbar. The sheet is allowed into the toolbar corner area so the fade can
 * follow that curve, but its alpha is already zero at the outline itself.
 */
@Composable
private fun Modifier.aeroToolbarFadeIf(
    fadeBandPx: Float,
    toolbarCornerPx: Float,
    active: () -> Boolean,
): Modifier {
    if (fadeBandPx <= 0f || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        return this
    }
    val shader = remember {
        runCatching { android.graphics.RuntimeShader(AERO_TOOLBAR_FADE_SHADER) }
            .onFailure { android.util.Log.e("TabListSwitcher", "Aero toolbar fade shader failed", it) }
            .getOrNull()
    } ?: return this
    return this.graphicsLayer {
        if (!active() || size.width <= 0f || size.height <= 0f) {
            renderEffect = null
            return@graphicsLayer
        }
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("fadeBand", fadeBandPx)
        shader.setFloatUniform("corner", toolbarCornerPx)
        renderEffect = android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

private const val AERO_TOOLBAR_FADE_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float fadeBand;
    uniform float corner;

    // The toolbar is open at its bottom edge and rounded only at its top.
    // Return that top outline at x, in this layer's local coordinates.
    float toolbarTop(float x) {
        float r = min(corner, size.x * 0.5);
        float flatTop = size.y - r;
        if (x < r) {
            float dx = x - r;
            return flatTop + r - sqrt(max(0.0, r * r - dx * dx));
        }
        if (x > size.x - r) {
            float dx = x - (size.x - r);
            return flatTop + r - sqrt(max(0.0, r * r - dx * dx));
        }
        return flatTop;
    }

    half4 main(float2 p) {
        float edge = toolbarTop(p.x);
        float alpha = clamp((edge - p.y) / fadeBand, 0.0, 1.0);
        return content.eval(p) * half(alpha);
    }
"""
