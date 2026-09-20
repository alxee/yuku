package com.yuku.browser.ui

import androidx.compose.runtime.SideEffect
import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiScreenIf
import com.yuku.browser.ui.theme.tuiGlowAroundIf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.yuku.browser.core.Tab
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.BEVEL_BAND
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.PageBg
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.aeroPopIf
import androidx.compose.ui.draw.shadow
import com.yuku.browser.ui.theme.aeroRefractIf
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.SwitcherBg
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.yuku.browser.ui.theme.specialCorner

// Fraction of screen width a card occupies, upright. Narrow enough that
// neighboring cards peek in well past both edges as a hint to swipe.
private const val CARD_WIDTH_FRACTION = 0.60f

// Turned sideways a card is SQUARE, and sized off the HEIGHT — the short axis
// there — rather than off the width.
//
// Upright, a card is a replica of the screen because that costs nothing: the
// page's own ratio is tall, so a 60%-wide card is still the tallest thing that
// fits. Sideways the same rule gives a card 60% of a very wide screen and a
// matching sliver of height — a letterbox strip with one band of the page in
// it, and two of them on a display with room for four. So the card takes the
// height it can have and keeps its width to match, which crops the thumbnail
// to the page's middle (ContentScale.Crop) and leaves the row wide enough for
// several tabs plus their neighbours' edges — which is the whole point of the
// extra width.
private const val LANDSCAPE_CARD_HEIGHT_FRACTION = 0.62f

// …and never so wide that the row is back to two cards on a squarish screen.
private const val LANDSCAPE_CARD_MAX_WIDTH_FRACTION = 0.40f

// How much bigger than its resting size the current card must be to fill the
// screen — i.e. the "zoomed all the way in" end of the row's scale range.
// Upright this is exact in both axes at once (a card IS the page, scaled), so
// the row's zoom and the page's own shrink are the same movement. A square
// card can only match one axis, and width is the one that matters: the pivot
// card is hidden for the whole gesture (the live page stands in for it) and
// what the zoom actually places is its NEIGHBOURS, which are spaced by width.
private const val ZOOM_SCALE = 1f / CARD_WIDTH_FRACTION

// A card that is a third of the screen wide would ask for a 3x sweep, which is
// a lot of travel to spend on cards that are leaving anyway.
private const val MAX_ZOOM_SCALE = 2.4f

// How far a card that has been gathered into the pile keeps its outer edge
// sticking out past the one in front of it, per tier of depth — the pile
// reads as the centered card with a slightly smaller neighbour behind each
// side and a slightly smaller one again behind that. This is the edge's real
// visible protrusion: the shrink each tier applies is compensated for
// separately, since scaling about the card's own center otherwise eats most
// of this back.
private val STACK_PEEK = 14.dp
private const val STACK_SCALE_STEP = 0.05f

// How far a piled card leans away from the centered one: the first tier's
// angle, how much more each tier out gets, and the spread of the per-tab
// wobble on top of that. Degrees.
private const val STACK_TILT = 3.5f
private const val STACK_TILT_STEP = 2.5f
private const val STACK_TILT_JITTER = 3f

// Tiers of edge showing on each side of the pile — so 5 cards read as
// distinct (1 centered + 2 per side) whenever there are that many tabs.
// Everything deeper lands flush behind the outermost tier.
private const val STACK_SIDE_TIERS = 2

// How far the gathered pile must be swiped up before releasing clears every
// tab — same feel as the per-card close swipe.
private val STACK_CLOSE_THRESHOLD = 64.dp

/** 0f..1f, stable per tab id — the pile's per-card tilt wobble. */
private fun Long.tiltNoise(): Float =
    (((this * 2654435761L) ushr 8) and 0xFFFF).toFloat() / 0xFFFF

private data class DraggedCard(val tab: Tab, val baseRect: Rect, val width: Dp, val height: Dp)

@Composable
fun TabSwitcher(
    tabs: List<Tab>,
    currentId: Long,
    sessionKey: Int,
    floatingTabId: Long,
    freezeRowForExpansion: Boolean,
    progress: () -> Float,
    hideCurrentThumbnail: Boolean,
    onSelect: (Long, LayoutCoordinates) -> Unit,
    onClose: (Long) -> Unit,
    onCloseAll: () -> Unit,
    onCurrentCardPositioned: (LayoutCoordinates) -> Unit,
    // Reports whichever card is currently centered in the row, and its
    // on-screen position, so the tabs button can expand THAT card (rather
    // than whatever was current before the switcher was scrolled) when
    // tapped to close.
    onCenteredCardPositioned: (Long, LayoutCoordinates) -> Unit = { _, _ -> },
    onExpandDragStart: (Long, LayoutCoordinates) -> Unit,
    onExpandDrag: (Float) -> Unit,
    onExpandDragEnd: (Float) -> Unit,
    onExpandDragCancel: () -> Unit,
    onExpandDragAbandon: () -> Unit,
    // Set while a tab is being closed from OUTSIDE the grid — the flick off
    // the toolbar's tabs button — for the whole of that card's flight, and
    // back to null once the tab is actually gone. The row has to close up over
    // the gap the same way it does for its own card-close, and that has to
    // happen while the card is still flying (see recenterForClose).
    closingTabId: Long? = null,
    // Fired once the row has finished closing up over a card thrown off by
    // the toolbar. The caller commits the removal on this, not on the end of
    // the flight, for the reason spelled out at the card-close above: a list
    // shortened under a running scroll animation blanks the whole row.
    onCloseRecentred: () -> Unit = {},
) {
    val ninety8 = LocalNinety8.current
    // "Close all" is only worth offering once there are enough tabs that
    // hunting one down individually (or just eyeballing the row) stops being
    // the faster option — for a handful of tabs it's more likely to catch a
    // stray tap than actually get used. When it is there it takes item 0, so
    // every tab's index in the LazyRow is its index in `tabs` plus this offset.
    val showCloseAll = tabs.size > 5
    val itemIndexOffset = if (showCloseAll) 1 else 0
    // Set as the INITIAL scroll position (rather than scrolling to it after
    // first composition) so the current tab is already centered on the very
    // first frame — no visible scroll correction before the zoom-in plays.
    val initialIndex = remember(tabs, currentId, itemIndexOffset) {
        tabs.indexOfFirst { it.id == currentId }.coerceAtLeast(0) + itemIndexOffset
    }
    val listState = key(sessionKey) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    }
    // The row can be scrolled without changing currentId. Expansion must
    // follow the card the user is looking at in the center, not whichever tab
    // happened to be selected before that scroll.
    val centeredTabId by remember(listState, currentId) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            (layout.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs(item.offset + item.size / 2 - viewportCenter)
            }?.key as? Long) ?: currentId
        }
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // A scroll this code started itself, rather than the user's finger.
    // `isScrollInProgress` can't tell the two apart, so the tick effect below
    // reads this to stay quiet through it.
    var programmaticScroll by remember { mutableStateOf(false) }

    /**
     * Closes the row up over the card being removed, if it is the one card
     * whose removal the row cannot absorb on its own.
     *
     * Closing the row's LAST card is the one case nothing can slide left into
     * the gap it leaves — there's nothing to its right. All the list can do is
     * clamp its scroll back by one item, which lands the left-hand neighbor
     * dead center instantly, while animateItem separately animates that same
     * card in from its old (left-of-center) placement: the card visibly slides
     * over itself from the left. Scrolling to that neighbor here instead —
     * while the closed card is still flying away — is what makes it genuinely
     * glide to the center, and means that by the time the tab is removed
     * there's no clamp, and therefore no placement animation, left to happen at
     * all. Cards before the closed one keep their index either way.
     *
     * It waits [FLY_AWAY_CLEAR_FRACTION] of [flightMs] before moving, so the
     * neighbour arrives in a slot the closed card has already left rather than
     * sliding across it while it is still there.
     *
     * Suspends until the row has settled, so the caller can hold
     * [programmaticScroll] for the whole of it — released while the row is
     * still moving, the tick effect gets back in for the tail of the same
     * close.
     */
    suspend fun recenterForClose(closedId: Long, flightMs: Int) {
        val closedIndex = tabs.indexOfFirst { it.id == closedId }
        val recenterIndex = (closedIndex - 1).takeIf {
            closedIndex == tabs.lastIndex &&
                it >= 0 &&
                // Scrolled far enough right that removing the last card
                // actually shortens the row out from under the current
                // position. Anywhere further left there's no clamp to
                // pre-empt, and scrolling would be a visible jolt of its own.
                listState.firstVisibleItemIndex >= it + itemIndexOffset
        } ?: return
        // Read the row's state above, wait, then move: the tab list is
        // unchanged for the whole flight (the close commits when it lands), so
        // the index worked out before the wait is still the right one after it.
        delay((flightMs * FLY_AWAY_CLEAR_FRACTION).toLong())
        listState.animateScrollToItem(recenterIndex + itemIndexOffset)
    }

    // The toolbar's flick-to-close: the card is thrown off the top by
    // BrowserScreen rather than by a drag on the card itself, so the row is
    // told about it separately. Keyed on the id, so it runs once at the start
    // of that flight and the scroll overlaps it exactly as it does for a
    // card's own close.
    LaunchedEffect(closingTabId) {
        val closing = closingTabId ?: return@LaunchedEffect
        programmaticScroll = true
        try {
            recenterForClose(closing, CLOSE_FLY_AWAY_MS)
        } finally {
            programmaticScroll = false
        }
        // After the try, not inside it: a cancelled recentring has not
        // settled, and the caller times out rather than being told it did.
        onCloseRecentred()
    }

    // One tick per tab passed while scrolling the row — the detent the snap
    // fling is heading for, felt as it goes by, the way a picker clicks
    // through its values. Only while the row is actually being scrolled or
    // flung: centeredTabId also changes when a tab is closed or the switcher
    // opens on a different tab, and neither of those is the user moving
    // through the row. Closing a card also *animates* the row back by one
    // item, which is a scroll in progress by every measure `listState` has —
    // hence `programmaticScroll`, so that close rings once (its confirm) and
    // not once more as the row re-centers itself.
    LaunchedEffect(listState) {
        snapshotFlow { centeredTabId }
            .drop(1)
            .collect { if (listState.isScrollInProgress && !programmaticScroll) haptics.tick() }
    }

    var switcherRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The centered card's own box, kept here as well as reported outward: a
    // tap on the background expands that card once the row has scrolled to
    // it, and the zoom has to start from where it actually sits.
    var centeredCardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Long-pressing any card gathers every tab into a single pile beneath
    // the centered one; one swipe up on that pile then clears them all.
    // Kept here rather than hoisted into BrowserScreen because nothing
    // outside the grid needs to know about it — it exists only between a
    // long press and the release that either clears the tabs or lets them
    // fan back out. Declared out here (rather than inside the box below)
    // only so the background tap handler on the root can read it.
    var stacked by remember { mutableStateOf(false) }

    // A tap on the empty space around the cards goes to the most recently
    // used tab — the rightmost card, since selecting a tab moves it to the
    // end of the list. The row scrolls to it and it then opens through the
    // same path a direct tap on that card takes, so it zooms out of its own
    // slot rather than appearing from nowhere.
    fun openLatestTab() {
        val latest = tabs.lastOrNull() ?: return
        // One tap for the whole action, fired at the touch rather than at
        // the end of the travel — the row's own per-tab ticks stay silent
        // through it (programmaticScroll below), so this is the only thing
        // felt.
        haptics.tap()
        scope.launch {
            if (centeredTabId != latest.id) {
                programmaticScroll = true
                try {
                    val targetIndex = tabs.lastIndex + itemIndexOffset
                    val layout = listState.layoutInfo
                    val target = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    if (target != null) {
                        // animateScrollToItem's own spec is built to get
                        // anywhere in a fixed time, which over a card or two
                        // reads as a snap with a stop at the end. Scrolling
                        // by the measured distance instead lets one eased
                        // curve carry the row the whole way and settle into
                        // the centre — the same ease-out the card's zoom
                        // then continues from. Distances land the card dead
                        // centre because the row's contentPadding centres
                        // every slot at scroll offset 0.
                        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val delta = (target.offset + target.size / 2f) - viewportCenter
                        // Longer for a longer trip, but only to a point:
                        // past a few cards the row is a blur either way and
                        // the wait is all the user feels.
                        val itemsAway = kotlin.math.abs(delta) / target.size.coerceAtLeast(1)
                        val duration = (240 + itemsAway * 90f).toInt().coerceAtMost(520)
                        listState.animateScrollBy(delta, tween(duration, easing = FastOutSlowInEasing))
                    } else {
                        // Too far off-screen to measure — nothing to ease
                        // between, so the stock scroll is as good as it gets.
                        listState.animateScrollToItem(targetIndex)
                    }
                } finally {
                    programmaticScroll = false
                }
            }
            // The coordinates are a live reference to the card's node, so
            // they read as its settled position — but only once the card
            // reporting them IS the latest one, which takes the layout pass
            // after the scroll lands.
            var tries = 0
            while (centeredTabId != latest.id && tries < 3) {
                withFrameNanos { }
                tries++
            }
            val coords = centeredCardCoords
            if (centeredTabId == latest.id && coords != null && coords.isAttached) {
                onSelect(latest.id, coords)
            }
        }
    }

    // Handed to the cards for their TUI outline and glow, which fade in with
    // the page's shrink. A plain holder, not a parameter threaded through
    // TabCard, and read only in draw — see [SwitcherShrink].
    val switcherShrink: () -> Float = { shrinkOf(progress()).coerceIn(0f, 1f) }
    SideEffect { SwitcherShrink.amount = switcherShrink }
    val stripUpDp = with(androidx.compose.ui.platform.LocalDensity.current) { PageTopStrip.px.toDp() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // The page runs under the status bar (PageTopStrip), so the
            // ground it shrinks away from does too: laid out taller by the
            // strip and placed that much higher, with the content padded
            // straight back down — otherwise the bar's strip is the Scaffold's
            // page colour over the switcher's ground.
            .layout { measurable, constraints ->
                val up = PageTopStrip.px.roundToInt()
                val height = constraints.maxHeight + up
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -up) }
            }
            // Same widened grid as the empty screen's: the two are one
            // ground (see [SwitcherBackdrop]) and a switcher emptying out
            // must not change texture as the last card leaves it.
            .grainedBackground(SwitcherBg, dotSpacing = EMPTY_DOT_SPACING)
            .padding(top = stripUpDp)
            .onGloballyPositioned { switcherRootCoords = it }
            // On the ROOT rather than on a background sibling behind the
            // row: the LazyRow's own scrollable node spans the full width
            // including the side padding the cards are centered by, so a
            // sibling underneath would never be hit for a tap right beside
            // the centered card — the most obvious place to aim. Up here the
            // event arrives after the children have had it (Main pass), so a
            // card's own click, which consumes the down, keeps it. A scroll
            // or fling started in that padding cancels this the same way,
            // since scrollable consumes the drag.
            .pointerInput(tabs, stacked, freezeRowForExpansion) {
                detectTapGestures {
                    when {
                        // Gathered, a tap anywhere is the way back out, same
                        // as a tap on the pile itself.
                        stacked -> stacked = false
                        // An expansion is already in flight — a second one
                        // would fight it for the live element.
                        freezeRowForExpansion -> Unit
                        else -> openLatestTab()
                    }
                }
            },
    ) {
        // A card being swiped up to close, kept as a sibling of the LazyRow
        // (not a child of it) the entire time — from the very first pixel of
        // upward drag, not just after release — so it's never clipped to the
        // row's own viewport the way its normal card would be. Same Animatable
        // carries it through the live drag (snapTo, following the finger 1:1)
        // and the post-release fly-away (animateTo), so there's no handoff
        // seam between the two where the background could show through.
        var draggedCard by remember { mutableStateOf<DraggedCard?>(null) }
        val dragOffsetY = remember { Animatable(0f) }

        // Set the moment a close COMMITS and never cleared: the flick past
        // the threshold on the last card, or the pile's release past its own.
        // Until then every gesture on that card might still end with it back
        // on screen, and the empty screen's face has no business being shown
        // to a user who isn't about to get one — so this, rather than the tab
        // count, is what the backdrop follows. Nothing has to clear it: once
        // the close lands the tab list is empty, and the switcher goes with
        // it (BrowserScreen renders EmptyState instead), taking this remember.
        var closeCommitted by remember { mutableStateOf(false) }

        // First child, so it sits behind the row: the cards, the pile's hint
        // and the close-all slot all draw over it. Raised only once the last
        // tab is actually on its way out (see closeCommitted), so the card
        // flying away uncovers a face that was already under it rather than
        // summoning one.
        SwitcherBackdrop(visible = closeCommitted)

        val landscape = maxWidth > maxHeight
        // Upright: close to the device's own aspect ratio, so a card reads like
        // a tiny replica of the screen rather than an arbitrary rectangle.
        // Measured against the LIVE PAGE's height, which is exactly this box's:
        // both are the screen minus the toolbar, since the page is laid out
        // above the bar rather than under it (see pageBottomInset in
        // BrowserScreen). They have to agree — the page shrinking into a card
        // is scaled by targetRect.width/height over its own size, so a card
        // proportioned differently from the page scales x and y by different
        // factors and visibly squashes it on the way in, and the captured
        // thumbnail (the page, exactly) would be cropped to fill the card.
        //
        // Sideways a card is square and the page is not, so that agreement
        // cannot hold — and it no longer has to. applyShrinkTransform scales
        // the page by ONE factor on both axes and crops the surplus away with
        // a window, so a card of any shape gets the page at its own
        // proportions, cropped exactly the way this card's own thumbnail is
        // (ContentScale.Crop). What the two have to agree on now is only that
        // the card is COVERED, which the larger of the two ratios guarantees
        // for any shape at all.
        val cardWidth: Dp
        val cardHeight: Dp
        if (landscape) {
            val side = minOf(
                maxHeight * LANDSCAPE_CARD_HEIGHT_FRACTION,
                maxWidth * LANDSCAPE_CARD_MAX_WIDTH_FRACTION,
            )
            cardWidth = side
            cardHeight = side
        } else {
            cardWidth = maxWidth * CARD_WIDTH_FRACTION
            // The page BOX's shape: the status bar strip above it is dropped
            // from the picture as the page lands (see shrinkGeometry).
            cardHeight = (cardWidth * (maxHeight / maxWidth)).coerceAtMost(maxHeight * 0.72f)
        }
        // The zoom that puts the pivot card at the page's width — a fraction
        // upright, a measurement sideways, where the card's width is no longer
        // a fixed share of the screen's.
        val maxZoom = if (cardWidth > 0.dp) {
            (maxWidth / cardWidth).coerceIn(1f, MAX_ZOOM_SCALE)
        } else {
            ZOOM_SCALE
        }
        // A LazyRow only composes what intersects its viewport, and at this
        // card width that is the centered card plus one neighbour on each
        // side — so the pile's second tier simply did not exist to be drawn.
        // The row is therefore laid out one slot wider than the screen on
        // each side and shifted back by the same amount. Every card keeps the
        // exact screen position it had (the matching bump to sidePadding
        // below cancels the shift), scroll range is unchanged (content and
        // viewport both grow by 2 * overscan), and viewportCenter — which
        // centeredTabId and the snap both read — is still screen center,
        // because the widened row is still centered on it. All the overscan
        // does is keep two more cards alive off-screen.
        // Three slots each way: a one-sided pile alternates its cards between
        // the two sides, so its outermost tier is the card FOUR slots from
        // the centered one, and a slot only has to intersect the viewport to
        // be composed.
        val rowOverscan = (cardWidth + 16.dp) * 3
        val rowWidth = maxWidth + rowOverscan * 2
        // Padding this wide means scrolling any card, including the first or
        // last, all the way to scrollOffset 0 lands it dead center in the row.
        val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp) + rowOverscan

        // The card block sits at true screen center; the gathered pile's hint
        // is then centered separately in whatever gap is left below it down to
        // the nav bar, using real measured heights rather than guessed spacing.
        var blockHeightPx by remember { mutableFloatStateOf(0f) }
        var hintHeightPx by remember { mutableFloatStateOf(0f) }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val cardBlockBottomPx = maxHeightPx / 2f + blockHeightPx / 2f
        val gapBelowPx = (maxHeightPx - cardBlockBottomPx).coerceAtLeast(0f)
        val hintTopPx = cardBlockBottomPx + (gapBelowPx - hintHeightPx) / 2f

        val stackProgress = remember { Animatable(0f) }
        // The whole gathered pile rides this together, so a swipe up moves it
        // as one object instead of dragging a card out of its own stack.
        val stackDragY = remember { Animatable(0f) }
        // The gather animation's value is read in the draw phase (see
        // zoomScale above for why); what composition needs from it is only
        // whether a pile exists at all, and whether it has formed — each of
        // which changes twice per gather rather than once per frame.
        val piled by remember { derivedStateOf { stackProgress.value > 0f } }
        val pileFormed by remember { derivedStateOf { stackProgress.value >= 0.5f } }
        val stackProgressOf: () -> Float = { stackProgress.value }
        LaunchedEffect(stacked) {
            stackProgress.animateTo(if (stacked) 1f else 0f, arriveSoft(340))
        }
        // Registered inside the grid, so it takes priority over BrowserScreen's
        // own chain: back ungroups the pile before it closes the switcher.
        BackHandler(enabled = stacked) { stacked = false }

        val stackStridePx = with(density) { (cardWidth + 16.dp).toPx() }
        val stackPeekPx = with(density) { STACK_PEEK.toPx() }
        val centeredIndex = tabs.indexOfFirst { it.id == centeredTabId }.coerceAtLeast(0)

        // The row's zoom (below), as a plain value rather than only inside the
        // layer lambda — each card needs it too, to cancel out the sideways
        // sweep that zooming about the current card would otherwise give it
        // (see zoomScale/zoomDiff in TabCard).
        //
        // Only the [0, MIDPOINT_PROGRESS] "shrinking in place" half of the
        // gesture zooms the grid at all — past that the tabs-button drag is
        // just translating the already fully-shrunk WebViewHost around (see
        // BrowserScreen), and the rest of the grid behind it should hold
        // perfectly still rather than keep shrinking with the same unclamped
        // value. Shares the exact same threshold as WebViewHost's own scale,
        // or the grid and the live content driving this gesture would visibly
        // desync mid-shrink. The centered card expands as a live overlay, so
        // during that handoff (freezeRowForExpansion) the row stays at rest
        // instead, or side cards lurch outward for a frame.
        //
        // A lambda, not a value, and that is the whole point: `progress` moves
        // every frame of a switcher gesture, and reading it HERE — in this
        // composable's own scope — recomposes the entire grid, every card in
        // it, on each of those frames. Read from inside a graphicsLayer block
        // instead it is a draw-phase read, which invalidates nothing. Same
        // reasoning, and the same fix, as the sheet's height in BrowserScreen.
        val zoomScale: () -> Float = {
            if (freezeRowForExpansion) {
                1f
            } else {
                val shrinkProgress = shrinkOf(progress())
                1f + (maxZoom - 1f) * (1f - shrinkProgress)
            }
        }
        // Shift the side cards start with, on top of their resting offset:
        // enough for the nearest one's inner edge to clear the screen edge, so
        // they slide in from off-screen rather than appearing mid-screen.
        val slideStartExtraPx = with(density) {
            (maxWidth / 2 + cardWidth / 2 - (cardWidth + 16.dp)).coerceAtLeast(0.dp).toPx()
        }
        // The card the row's zoom pivots on — the same one the live page is
        // shrinking into, so every other card measures its distance from it.
        val pivotIndex = tabs.indexOfFirst { it.id == floatingTabId }
            .let { if (it >= 0) it else centeredIndex }

        // Sends the pile off the top and clears everything. Shared by the
        // release past the threshold and the accessibility action.
        fun clearStack() {
            scope.launch {
                // Every tab is going, so the face is under all of them for
                // the flight — the pile's swipe-up is the one gesture that
                // empties the switcher without a tab count of one.
                closeCommitted = true
                stackDragY.animateTo(-maxHeightPx * 1.5f, depart(260))
                onCloseAll()
                stacked = false
                stackDragY.snapTo(0f)
            }
        }

        // Coordinates used to compute where exactly the current card's own
        // box sits within the row, so the row's zoom below can pivot on that
        // precise point instead of assuming it's dead center of the row.
        var rowBoxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var currentCardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val pivot = run {
            val row = rowBoxCoords
            val card = currentCardCoords
            if (row != null && card != null && card.isAttached && row.size.width > 0 && row.size.height > 0) {
                val center = row.localPositionOf(card, Offset(card.size.width / 2f, card.size.height / 2f))
                TransformOrigin(
                    (center.x / row.size.width).coerceIn(0f, 1f),
                    (center.y / row.size.height).coerceIn(0f, 1f),
                )
            } else {
                TransformOrigin.Center
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .onGloballyPositioned {
                    blockHeightPx = it.size.height.toFloat()
                }
                // Only ever non-zero while the pile is being swiped up, and
                // it carries the entire row, so the cards keep their relative
                // stacking as the whole group leaves the screen.
                .graphicsLayer { translationY = stackDragY.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The whole row zooms out together as it settles into place — like
            // it was always a bigger canvas that had been zoomed into the
            // active tab a moment ago — rather than neighbors sliding in from
            // off-screen independently. The zoom is what makes every card's
            // apparent size track the live page's own shrink exactly; each
            // card then cancels the sideways sweep the zoom would otherwise
            // add to it (see zoomScale in TabCard), so they all shrink in
            // place, in step with the page, instead of the neighbors spending
            // the first third of the animation still off-screen and then
            // flying in late.
            Box(
                modifier = Modifier
                    .onGloballyPositioned { rowBoxCoords = it }
                    .graphicsLayer {
                        val zoom = zoomScale()
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = pivot
                    },
            ) {
                LazyRow(
                    state = listState,
                    // A scroll would slide the pile out from under the card
                    // it was gathered onto — there is nothing to scroll to
                    // while everything is in one place anyway.
                    userScrollEnabled = !stacked,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    // requiredWidth, not width: the latter is coerced back
                    // into the incoming constraints, which are the screen's —
                    // the row would keep its old width while the overscan
                    // padding pushed every card off to one side. No offset to
                    // re-center it either: the Box around it reports the full
                    // child width (Box does not clamp to its own constraints),
                    // so the Column's CenterHorizontally already hangs the
                    // extra width off both edges evenly.
                    modifier = Modifier.requiredWidth(rowWidth),
                ) {
                    if (showCloseAll) {
                        // Deliberately NOT keyed by a Long: centeredTabId
                        // below reads the centered item's key as a tab id, and
                        // this slot has none — scrolled onto, it falls back to
                        // the current tab, so the tabs button still expands a
                        // real tab rather than nothing.
                        item(key = "close-all") {
                            CloseAllSlot(
                                width = cardWidth,
                                height = cardHeight,
                                // Gathered, the pile's own swipe already does
                                // exactly this — see the hint below, which
                                // takes over while it's up.
                                enabled = !pileFormed,
                                alpha = { 1f - stackProgressOf() },
                                // One slot to the left of the first tab.
                                zoomDiff = -1 - pivotIndex,
                                zoomScale = zoomScale,
                                maxZoom = maxZoom,
                                stackStridePx = stackStridePx,
                                slideStartExtraPx = slideStartExtraPx,
                                onCloseAll = onCloseAll,
                            )
                        }
                    }
                    itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                        TabCard(
                            // Cards whose index shifts because a tab ahead of
                            // them closed animate smoothly into their new
                            // slot instead of snapping — this is what makes
                            // the next tab over visibly slide in to fill the
                            // gap. Cards before the closed one never move
                            // (their index doesn't change), so this is a
                            // no-op for them.
                            // …and NO fade-out, which is not a style choice:
                            // the default one RETAINS the removed item, drawn
                            // at the slot it last held, for the length of the
                            // fade — and that card is visible, since
                            // `isBeingClosed` (which hid the original for the
                            // whole flight) goes false in the same breath as
                            // the removal that starts the fade. A tab thrown
                            // off the top has left; it should not come back
                            // for a frame somewhere else.
                            // …and no fade-IN either, for a reason of the
                            // same shape: cards only ever appear in a row
                            // that is itself arriving (the switcher's own
                            // entrance animates them all together), so this
                            // fade has nothing legitimate to do here — while
                            // any measure pass that places no items at all
                            // resets LazyList's item animator, and every card
                            // then reads as new and fades up from nothing over
                            // a quarter of a second. Waiting for the row to
                            // settle before removing anything (see
                            // onCloseDragEnd) is what stops that happening;
                            // this is what keeps it invisible if it ever
                            // does.
                            modifier = Modifier.animateItem(
                                placementSpec = arrive(280),
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                            tab = tab,
                            width = cardWidth,
                            height = cardHeight,
                            // During an expansion this follows the touched
                            // centered card immediately, rather than waiting
                            // for currentId to update. TabCard reports this
                            // position to drive the row's zoom pivot too.
                            current = tab.id == floatingTabId,
                            centered = tab.id == centeredTabId,
                            // How far this card sits from the one everything
                            // gathers onto, in whole card slots — cards are
                            // uniform width, so this alone is the distance it
                            // has to travel to land on the pile.
                            stackDiff = index - centeredIndex,
                            // Whole card slots away from the card the row's
                            // zoom pivots on, and how far that zoom currently
                            // is — together they undo the zoom's sideways
                            // sweep for this card.
                            zoomDiff = index - pivotIndex,
                            zoomScale = zoomScale,
                            maxZoom = maxZoom,
                            slideStartExtraPx = slideStartExtraPx,
                            stackProgress = stackProgressOf,
                            piled = piled,
                            stackStridePx = stackStridePx,
                            stackPeekPx = stackPeekPx,
                            // With the centered card at one end of the row,
                            // every other tab comes from the same side —
                            // fan them out both ways anyway, or the pile
                            // has a visible stack of edges on one side and
                            // a bare cut on the other.
                            stackOneSided = centeredIndex == 0 || centeredIndex == tabs.lastIndex,
                            stacked = stacked,
                            onLongPress = { stacked = !stacked },
                            hideThumbnail = tab.id == floatingTabId && hideCurrentThumbnail,
                            // The overlay below is what actually represents
                            // this tab for the whole close gesture — without
                            // hiding the original too, it just sits static and
                            // clipped to the row's viewport underneath it.
                            isBeingClosed = draggedCard?.tab?.id == tab.id,
                            onCurrentCardPositioned = { coords ->
                                currentCardCoords = coords
                                onCurrentCardPositioned(coords)
                            },
                            onCenteredCardPositioned = { coords ->
                                centeredCardCoords = coords
                                onCenteredCardPositioned(tab.id, coords)
                            },
                            onSelect = { coords -> onSelect(tab.id, coords) },
                            onExpandDragStart = { coords -> onExpandDragStart(tab.id, coords) },
                            onExpandDrag = onExpandDrag,
                            onExpandDragEnd = onExpandDragEnd,
                            onExpandDragCancel = onExpandDragCancel,
                            onExpandDragAbandon = onExpandDragAbandon,
                            onCloseDragStart = { cardCoords ->
                                // Gathered: the pile is dragged as a whole via
                                // stackDragY on the Column above, so there is
                                // no single-card overlay to lift out of the row.
                                // The overlay below is a sibling of this
                                // Column, both direct children of the root
                                // BoxWithConstraints — so its position needs
                                // to be measured relative to THAT root, not
                                // relative to the Column, or it renders
                                // wherever the Column's own top-left happens
                                // to sit instead of where the card actually
                                // is (visible as a jump to the card's own
                                // position minus the Column's offset).
                                val root = switcherRootCoords
                                if (!stacked && root != null && cardCoords.isAttached) {
                                    draggedCard = DraggedCard(
                                        tab = tab,
                                        baseRect = Rect(
                                            root.localPositionOf(cardCoords, Offset.Zero),
                                            cardCoords.size.toSize(),
                                        ),
                                        width = cardWidth,
                                        height = cardHeight,
                                    )
                                    scope.launch { dragOffsetY.snapTo(0f) }
                                }
                            },
                            onCloseDrag = { liveOffsetY ->
                                if (stacked) {
                                    // Upward only — a downward pull on the
                                    // pile has nothing to open.
                                    scope.launch { stackDragY.snapTo(kotlin.math.min(liveOffsetY, 0f)) }
                                } else if (draggedCard?.tab?.id == tab.id) {
                                    scope.launch { dragOffsetY.snapTo(liveOffsetY) }
                                }
                            },
                            onCloseDragEnd = { finalOffsetY ->
                                if (stacked) {
                                    if (finalOffsetY < with(density) { -STACK_CLOSE_THRESHOLD.toPx() }) {
                                        // Every tab at once — the heaviest
                                        // thing this screen can do.
                                        haptics.confirm()
                                        clearStack()
                                    } else {
                                        // Let go without clearing anything —
                                        // the settle, not the commit.
                                        haptics.gestureEnd()
                                        scope.launch { stackDragY.animateTo(0f, arrive(SNAP_BACK_MS)) }
                                    }
                                } else if (draggedCard?.tab?.id == tab.id) {
                                    val closeThresholdPx = with(density) { -64.dp.toPx() }
                                    if (finalOffsetY < closeThresholdPx) {
                                        haptics.confirm()
                                        val closingId = tab.id
                                        // Committed: this card is leaving, so
                                        // if it is the last one the ground it
                                        // uncovers is the empty screen's.
                                        if (tabs.size <= 1) closeCommitted = true
                                        scope.launch {
                                            programmaticScroll = true
                                            try {
                                                // Overlaps the flight, rather
                                                // than following it — see
                                                // recenterForClose.
                                                val recenter = launch { recenterForClose(closingId, FLY_AWAY_MS) }
                                                dragOffsetY.animateTo(-maxHeightPx * 3f, depart(FLY_AWAY_MS))
                                                // The removal waits for that
                                                // scroll to SETTLE, and this
                                                // ordering is the whole point:
                                                // shortening the list under a
                                                // scroll animation that is
                                                // still running leaves the row
                                                // measuring one frame with no
                                                // items placed at all, which
                                                // resets LazyList's item
                                                // animator — so every
                                                // remaining card reads as
                                                // newly appeared on the next
                                                // frame and fades back in from
                                                // nothing. On screen that is a
                                                // grid that blanks, then fades
                                                // up wherever the scroll had
                                                // got to, in place of the
                                                // neighbour gliding to the
                                                // centre. Nothing is waiting on
                                                // the removal: the card is
                                                // already off the top and the
                                                // row is where it will stay.
                                                // Only the row's LAST card
                                                // recentres at all, so every
                                                // other close is unaffected
                                                // (recenterForClose returns
                                                // immediately).
                                                recenter.join()
                                                onClose(closingId)
                                                draggedCard = null
                                            } finally {
                                                programmaticScroll = false
                                            }
                                        }
                                    } else {
                                        // Same release, other outcome: the
                                        // card drops back into its slot.
                                        haptics.gestureEnd()
                                        scope.launch {
                                            dragOffsetY.animateTo(0f, arrive(SNAP_BACK_MS))
                                            draggedCard = null
                                        }
                                    }
                                }
                            },
                            onCloseDragCancel = {
                                if (stacked) {
                                    scope.launch { stackDragY.animateTo(0f, arrive(160)) }
                                } else if (draggedCard?.tab?.id == tab.id) {
                                    scope.launch {
                                        dragOffsetY.animateTo(0f, arrive(160))
                                        draggedCard = null
                                    }
                                }
                            },
                            // Reversing straight from closing into expanding
                            // hands this same card off to a DIFFERENT element
                            // (WebViewHost, driven by onExpandDragStart, which
                            // fires in the same delta event right after this) —
                            // so this overlay needs to be gone in that same
                            // frame, not fading out over its own tween while
                            // the live content is already animating in on top
                            // of it. That overlap was the duplicate.
                            onCloseDragAbandon = {
                                if (draggedCard?.tab?.id == tab.id) {
                                    draggedCard = null
                                }
                            },
                        )
                    }
                }
            }
        }

        draggedCard?.let { dc ->
            // Both the card and its label move as one — the original TabCard
            // is fully hidden (label included) for the whole gesture via
            // isBeingClosed, so without carrying the label along here the name
            // and favicon simply blinked out the instant a swipe-up started
            // instead of flying off the top with their tab.
            Box(
                modifier = Modifier.offset {
                    IntOffset(dc.baseRect.left.roundToInt(), (dc.baseRect.top + dragOffsetY.value).roundToInt())
                },
            ) {
                TabLabelRow(
                    tab = dc.tab,
                    width = dc.width,
                    modifier = Modifier.offset(y = -TAB_TITLE_BLOCK_HEIGHT),
                )
                Box(
                    modifier = Modifier
                        .width(dc.width)
                        .height(dc.height)
                        // Fading as it is thrown: gone by half a screen up.
                        .tuiGlowAroundIf { (1f + dragOffsetY.value / (maxHeightPx * 0.5f)).coerceIn(0f, 1f) }
                        .shadow(if (com.yuku.browser.ui.theme.LocalAero.current) 10.dp else 0.dp, specialCorner(16.dp), clip = false)
                        .clip(specialCorner(16.dp))
                        .background(if (ninety8) MaterialTheme.colorScheme.surface else Color.Transparent)
                        // The card being dragged is the same window as the
                        // one it was lifted out of — see the TabCard above.
                        .bevel98If()
                        .aeroDroplet(16.dp, thin = true, glare = true)
                        .tuiScreenIf { (1f + dragOffsetY.value / (maxHeightPx * 0.5f)).coerceIn(0f, 1f) },
                ) {
                    val full = dc.tab.thumbnailFull?.takeIf {
                        (com.yuku.browser.ui.theme.LocalAero.current || com.yuku.browser.ui.theme.LocalFrosted.current) && !it.isRecycled
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .previewInset98If(ninety8)
                            .grainedBackground(if (dc.tab.isPrivate) Color(0xFF121212) else PageBg),
                    ) {
                        (full ?: dc.tab.thumbnail)?.let { bitmap ->
                            PagePreviewImage(
                                bitmap = bitmap,
                                centred = full != null,
                                // The card being dragged bends its page exactly
                                // as the one it was lifted out of does.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .privatePreview(dc.tab.isPrivate)
                                    .aeroRefractIf(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // The gathered pile is not self-explanatory on its own, and its own
        // "Close all" slot has been swept into the pile with everything else —
        // this fades in on the same curve the cards gather on, in the gap
        // below them.
        if (piled) {
            Text(
                text = "Swipe up to close all ${tabs.size} tabs",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(density) { hintTopPx.toDp() })
                    .onGloballyPositioned { hintHeightPx = it.size.height.toFloat() }
                    .graphicsLayer { alpha = stackProgressOf().coerceIn(0f, 1f) },
            )
        }
    }
}

// How far a downward swipe on a card must travel to fully open it — mirrors
// DRAG_TRAVEL for the tabs-button gesture in BrowserScreen.
private val EXPAND_TRAVEL = 280.dp
// This needs room for the text's full line box (including descenders such as
// the "g" in Google). A shorter fixed row clips it and makes the favicon look
// vertically offset beside the title.
internal val TAB_TITLE_HEIGHT = 24.dp
internal val TAB_TITLE_GAP = 8.dp
// The label spans the card's straight edge, not its full width: it starts
// where the top border finishes its rounded corner and ends where the far
// corner begins, so a long title lines up with the flat part of the card
// above it rather than running out past the curve. Matches the card's
// RoundedCornerShape(16.dp).
private val TAB_TITLE_INSET = 16.dp
internal val TAB_TITLE_BLOCK_HEIGHT = TAB_TITLE_HEIGHT + TAB_TITLE_GAP

/**
 * A tab's favicon + name, as drawn above its card in the switcher.
 *
 * Shared verbatim with BrowserScreen's FloatingTabLabel (the copy that tracks
 * the live/animating content) — these two are exact opposites of each other,
 * one visible whenever the other isn't, so any difference between them in
 * height, alignment, icon set or overflow treatment shows up as the label
 * jumping the instant the handoff happens. Keeping it as one composable, laid
 * out at exactly [TAB_TITLE_BLOCK_HEIGHT] above the card top in both places,
 * is what makes that handoff invisible.
 */
@Composable
internal fun TabLabelRow(
    tab: Tab,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val iconWidth = (if (tab.isPrivate) 18.dp else 0.dp) + (if (tab.favicon != null) 19.dp else 0.dp)
    // The title retains its natural width up to the card's available space.
    // That lets the row center the favicon and title as one group; a
    // full-width title box would center only the text and strand the favicon
    // at the left edge. Once it does fill that space it pins to the start
    // instead and fades out at its trailing edge.
    var overflowing by remember(tab.id, tab.label) { mutableStateOf(false) }

    val textWidth = (width - TAB_TITLE_INSET * 2 - iconWidth).coerceAtLeast(0.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (overflowing) Arrangement.Start else Arrangement.Center,
        modifier = modifier.tuiBloomIf()
            .width(width)
            .height(TAB_TITLE_HEIGHT)
            .padding(horizontal = TAB_TITLE_INSET),
    ) {
        if (tab.isPrivate) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.size(5.dp))
        }
        tab.favicon?.let { icon ->
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .clip(specialCorner(3.dp)),
            )
            Spacer(Modifier.size(5.dp))
        }
        Text(
            text = tab.label,
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            // softWrap = false is what makes the fade a fade. With wrapping
            // on, the single line is broken at a word boundary and the whole
            // trailing word is dropped, so the gradient lands on the empty
            // space after it and the title just looks cut short; without it
            // the line runs to the edge and the fade eats the final glyphs.
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { result: TextLayoutResult -> overflowing = result.hasVisualOverflow },
            modifier = if (overflowing) {
                Modifier.width(textWidth)
                    .then(fadeEdges(edge = 12.dp, bothSides = false))
            } else {
                Modifier.widthIn(max = textWidth)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    modifier: Modifier = Modifier,
    tab: Tab,
    width: Dp,
    height: Dp,
    current: Boolean,
    centered: Boolean,
    // Whole-card-slots away from the card the pile gathers onto; 0 for that
    // card itself, negative to its left, positive to its right.
    stackDiff: Int,
    // Whole card slots away from the card the row's zoom pivots on (the one
    // the live page shrinks into), and the row's current zoom factor.
    zoomDiff: Int,
    zoomScale: () -> Float,
    // The top of that zoom's range, which is a measurement rather than a
    // constant once a card stops being a fixed share of the screen's width.
    maxZoom: Float,
    // How far off its resting slot a non-pivot card starts, so the nearest one
    // begins fully off-screen (in px, at the card's final size).
    slideStartExtraPx: Float,
    // Deferred reads, both of them — see zoomScale in TabSwitcher: these move
    // every frame and are only ever consumed inside a graphicsLayer.
    stackProgress: () -> Float,
    // …and the coarse "is there a pile at all", which is what the parts that
    // genuinely need composition (z-order, hit testing) read instead.
    piled: Boolean,
    stackStridePx: Float,
    stackPeekPx: Float,
    stackOneSided: Boolean,
    stacked: Boolean,
    onLongPress: () -> Unit,
    hideThumbnail: Boolean,
    isBeingClosed: Boolean,
    onCurrentCardPositioned: (LayoutCoordinates) -> Unit,
    onCenteredCardPositioned: (LayoutCoordinates) -> Unit,
    onSelect: (LayoutCoordinates) -> Unit,
    onExpandDragStart: (LayoutCoordinates) -> Unit,
    onExpandDrag: (Float) -> Unit,
    onExpandDragEnd: (Float) -> Unit,
    onExpandDragCancel: () -> Unit,
    onExpandDragAbandon: () -> Unit,
    onCloseDragStart: (LayoutCoordinates) -> Unit,
    onCloseDrag: (Float) -> Unit,
    onCloseDragEnd: (Float) -> Unit,
    onCloseDragCancel: () -> Unit,
    onCloseDragAbandon: () -> Unit,
) {
    val density = LocalDensity.current
    val ninety8 = LocalNinety8.current
    var offsetY by remember(tab.id) { mutableFloatStateOf(0f) }
    var cardCoords by remember(tab.id) { mutableStateOf<LayoutCoordinates?>(null) }
    var expanding by remember(tab.id) { mutableStateOf(false) }
    var closing by remember(tab.id) { mutableStateOf(false) }
    val expandTravelPx = with(density) { EXPAND_TRAVEL.toPx() }
    val haptics = rememberHaptics()
    // A one-sided pile alternates its cards between the two sides, so it needs
    // twice the depth to dress the same number of tiers per side.
    val maxDepth = if (stackOneSided) STACK_SIDE_TIERS * 2 else STACK_SIDE_TIERS
    val depth = kotlin.math.min(kotlin.math.abs(stackDiff), maxDepth)
    // Which side of the pile this card's edge shows on, and how many edges are
    // already outside it there. Normally the side is simply the one it came
    // from and every step is its own tier; one-sided piles alternate sides
    // instead, so both edges of the pile are dressed either way and each tier
    // covers two steps of depth.
    val stackSide = if ((stackDiff < 0) != (stackOneSided && depth % 2 == 0)) -1f else 1f
    val stackRank = if (stackOneSided) (depth + 1) / 2 else depth
    // Fanned out like a hand of cards: each tier leans a little further away
    // from the centered one, plus a wobble that is stable per tab (hashed off
    // its id, not remembered or random) so no two neighbours sit at quite the
    // same angle and a card keeps its own tilt across regroupings.
    val stackTilt = stackSide *
        (STACK_TILT + STACK_TILT_STEP * (stackRank - 1) + STACK_TILT_JITTER * tab.id.tiltNoise())
    // Everything under the front of the pile is inert: its layout slot never
    // moves (only its drawing does), so leaving it interactive would mean taps
    // out at the row's edges hitting a card that visually sits in the middle.
    val inPile = piled && stackDiff != 0

    // The card, rather than this whole title-and-card component, must sit at
    // the row's center. The equal invisible title block below the card
    // counterbalances the visible title block above it; without it, centering
    // the old Column placed every card slightly below center and the live
    // WebView zoomed precisely to that incorrect target.
    Box(
        modifier = modifier
            .width(width)
            .height(height + TAB_TITLE_BLOCK_HEIGHT * 2)
            // Nearer the centered card = nearer the top of the pile, so they
            // land in a believable order rather than in list order.
            .zIndex(if (piled) -depth.toFloat() else 0f)
            .graphicsLayer {
                // Downward movement doesn't move the card itself — the live
                // WebView growing over it (driven by onExpandDrag) is the
                // visual. Upward movement doesn't move it either — once
                // `closing` actually commits (past the dead zone below),
                // isBeingClosed hides this original card the same frame the
                // overlay takes over, so this translation is never even
                // visible; before that point it needs to stay at 0, or the
                // raw pre-commit offsetY (which can wander a few px in
                // either direction as the touch-slop crossing settles) would
                // nudge the real card despite never committing to closing.
                // While gathered, an upward drag moves the whole pile at
                // once (see stackDragY on the Column in TabSwitcher) — moving
                // this card too would double it and pull it out of its stack.
                translationY = if (closing && !stacked) offsetY else 0f
                alpha = if (isBeingClosed) 0f else 1f
                // Only the pivot card — the one the live page is shrinking
                // into — actually zooms. Every other card is a passenger of
                // the row's shared zoom layer, which would both blow it up and
                // fling it a screen-width out to the side; it instead holds
                // its final, resting size the whole time (cancel the row's
                // scale with 1/zoom) and simply slides in from just off the
                // edge it belongs to, arriving exactly as the zoom lands.
                //
                // Both corrections are expressed in the row's own pre-scale
                // space, so `apparent` — the card's on-screen offset from the
                // pivot — is what the arithmetic below targets, and the row's
                // scale then maps it back to exactly that. What ends up
                // clipped at the row's edges is still only what is off-screen
                // anyway: the row's bounds map to the screen edges at zoom 1
                // and outside them above it.
                applyRowZoomCompensation(zoomDiff, zoomScale(), maxZoom, stackStridePx, slideStartExtraPx)
                val stackT = stackProgress()
                if (stackDiff != 0 && stackT > 0f) {
                    // Scale follows the tier, not the raw depth: the two
                    // cards making up a one-sided pile's first tier sit on
                    // opposite sides of the same pile and have to match.
                    rotationZ = stackTilt * stackT
                    val pileScale = 1f - STACK_SCALE_STEP * stackRank * stackT
                    scaleX = pileScale
                    scaleY = pileScale
                    // The card is scaled about its own center, which pulls
                    // each edge in by half of what it lost. Add that back, or
                    // a deep card ends up narrower than its peek and never
                    // actually sticks out.
                    val shrinkInset = (1f - pileScale) * size.width / 2f
                    val peek = stackSide * (stackPeekPx * stackRank * stackT + shrinkInset)
                    // Added to (not overwriting) the zoom compensation above —
                    // gathering only ever happens with the row at rest, where
                    // that term is 0, but the two are independent offsets.
                    translationX += -stackDiff * stackStridePx * stackT + peek
                }
            }
            // Swipe up dismisses — zooming out live with the finger via the
            // overlay in TabSwitcher, the whole way until it's gone. Swipe
            // down opens it, zooming in live the same way the tabs button's
            // swipe-up zooms out. Horizontal falls through to the LazyRow.
            .pointerInput(tab.id, inPile) {
                // Buried in the pile: inert, see `inPile`.
                if (inPile) return@pointerInput
                // detectVerticalDragGestures folds however far the finger
                // moved crossing the touch-slop threshold into the FIRST
                // onVerticalDrag call's delta, not just that frame's own
                // incremental movement — applying it as-is pops the card by
                // that whole distance the instant the drag is recognized.
                // Discard it, same as the tabs button's own drag detector.
                var isFirstDrag = true
                // One drag, one "it took hold" buzz. Reversing direction
                // mid-gesture switches which of closing/expanding is running,
                // and used to fire gestureStart again on every flip — wobbling
                // near the dead zone turned into a stream of them.
                var startedHaptic = false
                // On top of that, the touch-slop crossing itself can go the
                // "wrong" way first — natural finger jitter right as a drag
                // starts is often enough to cross that threshold in the
                // opposite direction from the swipe being made, which
                // briefly fired the wrong one of closing/expanding before
                // flipping to the right one a moment later ("goes slightly
                // up" on what's meant to be a swipe down). This dead zone
                // requires more net movement than that jitter before
                // committing to either direction at all — closing/expanding
                // never start until it's crossed, so there's nothing to
                // flip away from.
                val deadZonePx = with(density) { 12.dp.toPx() }
                detectVerticalDragGestures(
                    onDragStart = {
                        isFirstDrag = true
                        startedHaptic = false
                    },
                    onDragEnd = {
                        when {
                            closing -> {
                                // No haptic here: the threshold this is
                                // compared against lives in the grid
                                // (onCloseDragEnd), so only that handler knows
                                // whether letting go destroyed a tab or just
                                // dropped the card back into its slot. It
                                // fires exactly one effect for the release —
                                // firing one here too was two buzzes for one
                                // gesture.
                                onCloseDragEnd(offsetY)
                                closing = false
                                offsetY = 0f
                            }
                            expanding -> {
                                haptics.gestureEnd()
                                onExpandDragEnd((offsetY / expandTravelPx).coerceIn(0f, 1f))
                                expanding = false
                                offsetY = 0f
                            }
                            else -> offsetY = 0f
                        }
                    },
                    onDragCancel = {
                        if (closing) onCloseDragCancel()
                        if (expanding) onExpandDragCancel()
                        closing = false
                        expanding = false
                        offsetY = 0f
                    },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        if (isFirstDrag) {
                            isFirstDrag = false
                        } else {
                            offsetY += delta
                            when {
                                !closing && !expanding && kotlin.math.abs(offsetY) <= deadZonePx -> {
                                    // Still inside the dead zone — neither
                                    // direction has committed yet, so
                                    // there's nothing to update.
                                }
                                offsetY < 0f -> {
                                    if (expanding) {
                                        // Reversed straight from expanding
                                        // into closing without ever crossing
                                        // back through the dead zone — the
                                        // live WebViewHost this was driving
                                        // needs to be gone THIS frame, not
                                        // mid-animateTo, since the close
                                        // overlay below is about to occupy
                                        // the same spot immediately after.
                                        expanding = false
                                        onExpandDragAbandon()
                                    }
                                    if (!closing) {
                                        closing = true
                                        if (!startedHaptic) {
                                            startedHaptic = true
                                            haptics.gestureStart()
                                        }
                                        cardCoords?.let { onCloseDragStart(it) }
                                    }
                                    onCloseDrag(offsetY)
                                }
                                offsetY > 0f -> {
                                    if (closing) {
                                        // Same reversal, the other way —
                                        // abandon (not the animated cancel)
                                        // so the close overlay doesn't linger,
                                        // fading out, while the live content
                                        // it's about to hand off to is
                                        // already animating in on top of it.
                                        closing = false
                                        onCloseDragAbandon()
                                    }
                                    // Swipe-down-to-expand follows the card
                                    // currently centered in the carousel. It
                                    // may differ from `current` after the row
                                    // has been scrolled; BrowserScreen selects
                                    // it before positioning the live WebView.
                                    // Downward on a gathered pile opens
                                    // nothing — the only gesture it answers to
                                    // is the swipe up that clears every tab.
                                    if (centered && !stacked && !expanding) {
                                        expanding = true
                                        if (!startedHaptic) {
                                            startedHaptic = true
                                            haptics.gestureStart()
                                        }
                                        cardCoords?.let(onExpandDragStart)
                                    }
                                    if (centered && !stacked) {
                                        onExpandDrag((offsetY / expandTravelPx).coerceIn(0f, 1f))
                                    }
                                }
                                else -> {
                                    if (closing) {
                                        closing = false
                                        onCloseDragCancel()
                                    }
                                    if (expanding) {
                                        expanding = false
                                        onExpandDragCancel()
                                    }
                                }
                            }
                        }
                    },
                )
            }
            // A swipe is unreachable by TalkBack, and there is no close button
            // any more, so the gesture needs an explicit accessible equivalent.
            .semantics {
                contentDescription = tab.label
                customActions = listOf(
                    CustomAccessibilityAction("Close tab") {
                        cardCoords?.let {
                            onCloseDragStart(it)
                            onCloseDragEnd(-1f) // any negative value clears the threshold
                        }
                        true
                    },
                    // The long press and the swipe that follows it are both
                    // unreachable by TalkBack, so the pair gets one action.
                    CustomAccessibilityAction(if (stacked) "Close all tabs" else "Group tabs") {
                        if (stacked) onCloseDragEnd(-1f) else onLongPress()
                        true
                    },
                )
            },
    ) {
        // Invisible (not omitted, so the row's own height stays stable) while
        // the current tab's live-floating label is doing the job instead —
        // see FloatingTabLabel, which draws this exact same composable at the
        // same offset above the animating content.
        // Only the card at the front of the pile keeps its name — a dozen
        // titles overlapping in the same spot is unreadable, and the pile is
        // one object at that point anyway.
        TabLabelRow(
            tab = tab,
            width = width,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = when {
                        hideThumbnail -> 0f
                        stackDiff != 0 -> (1f - stackProgress()).coerceIn(0f, 1f)
                        else -> 1f
                    }
                },
        )

        // Single thumbnail draw, hidden in lockstep with the card's own
        // background below (both gated by the same hideThumbnail
        // alpha) — while a live element (WebViewHost or its static
        // AnimatedThumbnailHost counterpart, see BrowserScreen) is
        // representing this exact tab elsewhere on screen (mid tabs-button
        // drag, or expanding/closing), this card's own slot goes fully
        // invisible instead of leaving a duplicate copy sitting behind/under
        // it. Only reserves the layout space so the grid around it doesn't
        // reflow.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = TAB_TITLE_BLOCK_HEIGHT)
                .width(width)
                .height(height)
                .onGloballyPositioned { coords ->
                    cardCoords = coords
                    if (current) onCurrentCardPositioned(coords)
                    if (centered) onCenteredCardPositioned(coords)
                }
                // Under the TUI the card's light spills outward. Before the
                // clip, which would cut it off — and before the alpha, so it
                // stays while the live page stands in for a hidden card, and
                // fades in with the switcher itself as the page shrinks.
                .tuiGlowAroundIf { SwitcherShrink.amount() }
                .alpha(if (hideThumbnail) 0f else 1f)
                .shadow(if (com.yuku.browser.ui.theme.LocalAero.current) 8.dp else 0.dp, specialCorner(16.dp), clip = false)
                .clip(specialCorner(16.dp))
                // The 98 frame owns the outside of the card; the page is
                // inset below so it never paints underneath those bands.
                .background(if (ninety8) MaterialTheme.colorScheme.surface else Color.Transparent)
                // Keep the page beneath the full two-band period bevel.
                .bevel98If()
                // Under Aero a card is a pane of glass with the page laid
                // under it, standing on the sky (see `AeroSkyLight`) — so it
                // takes the same three marks every other pane in this theme
                // does, over the preview rather than under it for the reason
                // the bevel above is drawn there.
                .aeroDroplet(16.dp, thin = true, glare = true)
                // Under the TUI the preview is a picture on a tube.
                .tuiScreenIf { SwitcherShrink.amount() }
                .combinedClickable(
                    // Gathered, a tap is the way back out — selecting a tab
                    // out of a pile the user just asked to treat as one thing
                    // would be the wrong reading of the same gesture.
                    onClick = {
                        haptics.tap()
                        if (stacked) onLongPress() else cardCoords?.let(onSelect)
                    },
                    onLongClick = {
                        haptics.longPress()
                        onLongPress()
                    },
                    enabled = !inPile,
                ),
        ) {
            // Under Aero, the full-screen grab, cropped about its CENTRE —
            // the same picture and the same crop the page zooms out of (see
            // AnimatedThumbnailHost), so the landing is not a jump.
            // Translucent sheets too: their toolbar is frosted over the page.
            val full = tab.thumbnailFull?.takeIf {
                (com.yuku.browser.ui.theme.LocalAero.current || com.yuku.browser.ui.theme.LocalFrosted.current) && !it.isRecycled
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (hideThumbnail) 0f else 1f)
                    .previewInset98If(ninety8)
                    .grainedBackground(if (tab.isPrivate) Color(0xFF121212) else PageBg),
            ) {
                (full ?: tab.thumbnail)?.let { bitmap ->
                    // The page box alone: the status bar strip grows in only as
                    // the card zooms to full screen (see shrinkGeometry).
                    PagePreviewImage(
                        bitmap = bitmap,
                        centred = full != null,
                        // A private tab's card shows that there IS a page, and
                        // nothing about what it is.
                        // The enclosing glass samples this preview once, after
                        // privacy treatment, alongside the card's other content.
                        modifier = Modifier
                            .fillMaxSize()
                            .privatePreview(tab.isPrivate)
                            .aeroRefractIf(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Undoes the row's shared zoom for one slot that isn't the pivot: it holds its
 * final, resting size the whole time (cancelling the row's scale with 1/zoom)
 * and slides in from just off the edge it belongs to, arriving exactly as the
 * zoom lands. Shared by [TabCard] and [CloseAllSlot] so the button files into
 * the row alongside the cards instead of being swept out sideways by a zoom it
 * doesn't compensate for.
 *
 * Both corrections are expressed in the row's own pre-scale space, so
 * `apparent` — the slot's on-screen offset from the pivot — is what the
 * arithmetic targets, and the row's scale then maps it back to exactly that.
 */
private fun GraphicsLayerScope.applyRowZoomCompensation(
    zoomDiff: Int,
    zoomScale: Float,
    maxZoom: Float,
    stackStridePx: Float,
    slideStartExtraPx: Float,
) {
    if (zoomDiff != 0 && zoomScale != 1f) {
        // 0 while zoomed all the way into the pivot card, 1 at rest — the same
        // linear ramp the page's own shrink runs on, so the slide is in step
        // with it. Against the row's OWN top of range (see maxZoom), not the
        // upright constant: sideways the two differ.
        val settled = ((maxZoom - zoomScale) / (maxZoom - 1f)).coerceIn(0f, 1f)
        val resting = zoomDiff * stackStridePx
        // The group starts shifted far enough toward its own side for the
        // nearest card to sit fully off-screen, keeping the cards' spacing
        // intact as they file in together.
        val offscreen = resting + kotlin.math.sign(resting) * slideStartExtraPx
        val apparent = offscreen + (resting - offscreen) * settled
        translationX = apparent / zoomScale - resting
        scaleX = 1f / zoomScale
        scaleY = 1f / zoomScale
    } else {
        translationX = 0f
    }
}

/**
 * "Close all" as the row's own left-hand end stop: one card-sized slot sitting
 * before the first tab, scrolled to like any other card, rather than a button
 * parked under the cards. Card-sized on purpose — the row's centering
 * (contentPadding) and its snap fling both assume every slot is the same
 * width, so a narrower one would leave every card slightly off centre.
 */
@Composable
private fun CloseAllSlot(
    width: Dp,
    height: Dp,
    enabled: Boolean,
    alpha: () -> Float,
    zoomDiff: Int,
    zoomScale: () -> Float,
    maxZoom: Float,
    stackStridePx: Float,
    slideStartExtraPx: Float,
    onCloseAll: () -> Unit,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .width(width)
            // Matches TabCard's slot exactly (card plus its title block above
            // and the invisible counterweight below), so the button lands on
            // the cards' own vertical centre line.
            .height(height + TAB_TITLE_BLOCK_HEIGHT * 2)
            .graphicsLayer {
                this.alpha = alpha().coerceIn(0f, 1f)
                applyRowZoomCompensation(zoomDiff, zoomScale(), maxZoom, stackStridePx, slideStartExtraPx)
            },
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = {
                haptics.confirm()
                onCloseAll()
            },
            enabled = enabled,
            shape = specialCorner(18.dp),
            // Under Aero, the same glass drop as the list's Close all and the
            // toolbar's buttons: filled, rim and glare, its own pop, no outline.
            border = if (com.yuku.browser.ui.theme.LocalAero.current) null
            else androidx.compose.material3.ButtonDefaults.outlinedButtonBorder(enabled = enabled),
            modifier = if (com.yuku.browser.ui.theme.LocalAero.current) Modifier
                .aeroPopIf()
                .aeroDroplet(18.dp, filled = true, glare = true)
            else Modifier,
        ) {
            Text("Close all", color = Ink)
        }
    }
}

/**
 * How far the page has shrunk into the switcher, 0 (page on screen) to 1
 * (cards at rest), for the TUI's card outline and outward glow — so a card's
 * bezel arrives with the card rather than being on while the page is still
 * full screen. Written from TabSwitcher's composition (one switcher exists),
 * read only inside draw blocks, where the lambda's state read invalidates
 * just that draw.
 */
internal object SwitcherShrink {
    var amount: () -> Float = { 1f }
}

/** Clips paint to the 98 frame's inside without changing thumbnail layout. */
private fun Modifier.previewInset98If(enabled: Boolean): Modifier {
    if (!enabled) return this
    return drawWithContent {
        val inset = (BEVEL_BAND * 2).toPx()
            .coerceAtMost(size.width / 2f)
            .coerceAtMost(size.height / 2f)
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            this@drawWithContent.drawContent()
        }
    }
}
