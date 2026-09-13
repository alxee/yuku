package com.yuku.browser.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Tab
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yuku.browser.ui.theme.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuku.browser.core.LinkPreview
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.PageBg
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.yuku.browser.ui.theme.SpecialCornerScale
import com.yuku.browser.ui.theme.specialCorner

/** How much of the screen's height the card takes. */
private const val PREVIEW_HEIGHT_FRACTION = 2f / 3f + 0.05f

/** How much of the screen is left showing either side of it. */
private val PREVIEW_SIDE_MARGIN = 18.dp

private val PREVIEW_CORNER = 24.dp

private val PREVIEW_SHADOW = 16.dp

/** Between the card and the actions under it. */
private val PREVIEW_ACTION_GAP = 12.dp

private val ACTION_BAR_HEIGHT = 62.dp

/**
 * How small the card starts and ends. Further from 1 than the app's menus
 * scale (0.9): those pop out of a button a few dp away, where this is a
 * full-width card arriving over a page, and a shallow zoom on something that
 * large reads as a jump rather than as growth.
 */
private const val PREVIEW_ENTER_SCALE = 0.85f

/**
 * The card growing into the page, when the preview is promoted — Open, or
 * Open in new tab. Longer than the card's own entrance: it is crossing the
 * rest of the screen rather than the last 15% of its own size, and it is the
 * movement that says the thing being read is now the thing you are on.
 *
 * Taken on [pop], which is [Overshoot] sized for a shorter journey: the tab
 * switcher's expand carries 1.2% of a page-sized travel, which is around eight
 * pixels, and this window's travel is a fraction of that — so the same eight
 * pixels of carry has to be asked for as a bigger percentage. A card growing
 * into a page and a card growing into a page are the same gesture; they should
 * not arrive differently, and matching the NUMBER rather than the fraction is
 * what makes them feel the same.
 *
 * Everything the overshoot touches has to tolerate values past 1: the window
 * grows a hair beyond the page's rectangle and settles back onto it, which is
 * the point, while the corner radius and the scrim's alpha are coerced,
 * because neither is allowed to go negative.
 */
private const val PREVIEW_EXPAND_MS = 340

/**
 * The card leaving, as the time-reverse of it arriving.
 *
 * The entrance moves for [SURFACE_ENTER_MS] and fades in over the first
 * [SURFACE_ENTER_FADE_MS] of that, so the card is fully opaque while it is
 * still growing. Reversed, the fade belongs at the END: the card holds its
 * opacity through the gather ([Anticipate] peaks at ~41% of the movement) and
 * fades over the shrink, landing with it. Started at zero instead, the fade
 * is half gone by the time the overshoot happens and the whole exit reads as
 * a plain dissolve — the movement is there, nobody can see it.
 */
private const val PREVIEW_EXIT_MS = 280
private const val PREVIEW_EXIT_FADE_MS = 200
private const val PREVIEW_EXIT_FADE_DELAY_MS = 80

/**
 * How far down the card has to be dragged, as a fraction of its own height,
 * for the release to close it. Low, because the gesture is only available
 * with the page at its top and only downward — there is nothing else it could
 * have been meant as, so making the user prove it twice is just friction.
 */
private const val SWIPE_CLOSE_FRACTION = 0.16f

/** …or this fast, in pixels per second, however far it got. */
private const val SWIPE_CLOSE_VELOCITY = 1_400f

/** How much of itself the card gives up at the far end of a drag. */
private const val SWIPE_MIN_SCALE = 0.90f

/**
 * How far the drag has to go before the card is at [SWIPE_MIN_SCALE] — a
 * fraction of the card's height. Deliberately well past the release
 * threshold: the shrink is feedback about what the gesture is doing, not a
 * countdown to it.
 */
private const val SWIPE_SCALE_TRAVEL = 0.55f

/**
 * The card dissolving into the page it has just become. Short, because by then
 * the two are the same picture (see [PreviewCard]) — this is insurance against
 * the last differences between them (a scroll offset, a late image), not a
 * transition doing real work.
 */
private const val PREVIEW_HANDOFF_FADE_MS = 120

/**
 * A link, looked at without being gone to: a long press on an anchor puts the
 * page itself on screen in a card a little over two thirds of the screen tall
 * and a little narrower than it, with Open / Open in new tab / Copy link on a
 * bar floating under it.
 *
 * This replaces the menu a long press used to raise for links — the same three
 * actions are still here, under a card that answers the question a long press
 * is actually asking. A press on a bare image still gets [WebContextMenu]:
 * there is nothing to preview.
 *
 * **The page in the card is a real, live WebView** — the second one on screen,
 * the tab's own being right underneath it — so it scrolls, it runs scripts,
 * and links tapped inside it navigate WITHIN the card (see
 * `BrowserViewModel.createPreview`). That is what makes the actions worth
 * having at the end of it: they act on where the preview has got to, not on
 * the link that opened it.
 *
 * Everything around the card is a dismissal — the scrim, the gap between the
 * card and its bar, the space either side. The card and the bar take a no-op
 * tap gesture of their own so a press that misses a control doesn't fall
 * through to the scrim behind them, exactly as the find bar does over a page.
 *
 * [preview] going null starts the exit, which is why the last non-null one is
 * held: the card has to keep drawing its page for the length of it. The host
 * is keyed on [LinkPreview.token] so a preview raised while the previous card
 * is still leaving builds its own WebView instead of inheriting the departing
 * one.
 *
 * [pageRect] is where the tab's own page sits in this overlay's coordinates —
 * what a promotion lands on. See [PreviewCard] for why the card is laid out
 * against it from the first frame rather than at the end of the expand.
 */
@Composable
fun LinkPreviewOverlay(
    preview: LinkPreview?,
    pageRect: Rect?,
    webViewFor: (Long) -> WebView?,
    onRelease: (Long) -> Unit,
    onOpen: suspend (String) -> Unit,
    onOpenInNewTab: suspend (String) -> Unit,
    /**
     * Set only while "Open in new tab" is meant to open in the BACKGROUND
     * (Settings > Behavior > Links), and it replaces [onOpenInNewTab] when it
     * is: the card cannot be promoted into a tab the user is not being taken
     * to — the page it would grow into is the one they are staying on — so it
     * opens the tab and leaves the way a dismissal does.
     */
    onOpenInBackgroundTab: ((String) -> Unit)? = null,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var last by remember { mutableStateOf<LinkPreview?>(null) }
    LaunchedEffect(preview) { preview?.let { last = it } }
    val shown = preview ?: last ?: return
    val scope = rememberCoroutineScope()
    // The promotion: the card grows into the page underneath while that page
    // loads, and dissolves once it has painted. [promoting] latches so a
    // second press during the flight is ignored — the card is on its way to
    // being a tab and there is nothing left to press it for.
    var promoting by remember { mutableStateOf(false) }
    // The card arriving and leaving (0 -> 1), the promotion (0 -> 1) and the
    // dissolve at the end of one (1 -> 0). Three separate animations because
    // they overlap: a promotion runs while the card is fully open, and its
    // dissolve runs while the expand holds at its end.
    val open = remember { Animatable(0f) }
    // The opacity, on its own clock — shorter than the movement it runs
    // alongside, the way every other surface in this app fades (see
    // Motion.kt). Not taken off [open]: that one carries past its mark, and
    // an alpha has nowhere past 1 to carry to.
    val opacity = remember { Animatable(0f) }
    val expand = remember { Animatable(0f) }
    val handoff = remember { Animatable(1f) }
    // How far down the finger has taken the card. Driven from the View world
    // (see [PreviewDragHost]) so the page can be taken OUT of the gesture the
    // moment it becomes one, rather than sharing it.
    //
    // A plain snapshot float, written synchronously by the touch handler, and
    // NOT an Animatable: `snapTo` is a suspend call behind a MutatorMutex, so
    // one launched per touch event puts a frame of coroutine dispatch — and,
    // under cancellation, no ordering guarantee at all — between the finger
    // and the card. That is a gesture that visibly stutters. The snap-back is
    // the only part that animates, and it writes the same field.
    var dragPx by remember { mutableFloatStateOf(0f) }
    var snapBack by remember { mutableStateOf<Job?>(null) }

    // Hand-driven rather than AnimatedVisibility: a promoted card must NOT
    // take the exit transition on its way out — it has just become the page,
    // and shrinking it back to card size would undo what it just did. It is
    // dropped outright instead, by clearing [last].
    LaunchedEffect(preview != null) {
        if (preview != null) {
            launch { opacity.animateTo(1f, tween(SURFACE_ENTER_FADE_MS)) }
            open.animateTo(1f, pop(SURFACE_ENTER_MS))
        } else if (!promoting) {
            launch {
                opacity.animateTo(
                    0f,
                    tween(PREVIEW_EXIT_FADE_MS, delayMillis = PREVIEW_EXIT_FADE_DELAY_MS),
                )
            }
            // The mirror of the way in: [departPop] gathers the same ~5%
            // before it goes, so the card leaves as the same object that
            // arrived rather than one that only knows how to accelerate.
            open.animateTo(0f, departPop(PREVIEW_EXIT_MS))
            last = null
        }
    }

    fun promote(action: suspend (String) -> Unit) {
        if (promoting) return
        promoting = true
        val url = shown.url
        scope.launch {
            // Started first and left running: the tab has the whole length of
            // the expand to load the page, which is the point of doing them
            // together rather than one after the other.
            val navigation = launch { action(url) }
            expand.animateTo(1f, pop(PREVIEW_EXPAND_MS))
            navigation.join()
            handoff.animateTo(0f, tween(PREVIEW_HANDOFF_FADE_MS))
            onDismiss()
            last = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val overlay = with(density) {
            Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx())
        }
        // Where the promotion lands. Falling back to the whole overlay is only
        // for the frame before the page has been positioned — an expand that
        // ends on the wrong rectangle is a seam, not a crash.
        val page = pageRect?.takeIf { it.width > 0f && it.height > 0f } ?: overlay

        // The resting layout, worked out once: card, gap, bar, the lot
        // centred as a column. Everything the animations do is expressed as a
        // journey between two rectangles of this, so nothing below reads a
        // single animated value during composition.
        val marginPx = with(density) { PREVIEW_SIDE_MARGIN.toPx() }
        val gapPx = with(density) { PREVIEW_ACTION_GAP.toPx() }
        val barPx = with(density) { ACTION_BAR_HEIGHT.toPx() }
        val cardWidth = overlay.width - marginPx * 2
        val cardHeight = overlay.height * PREVIEW_HEIGHT_FRACTION
        val columnTop = (overlay.height - (cardHeight + gapPx + barPx)) / 2f
        val cardRect = Rect(marginPx, columnTop, marginPx + cardWidth, columnTop + cardHeight)
        val barTop = cardRect.bottom + gapPx

        Box(
            Modifier
                .fillMaxSize()
                // Leaves in step with the expand, on the expand's own curve —
                // which is what makes a promotion read as ONE movement rather
                // than a card growing on top of a dimmed app. The strips the
                // card never reaches, the status bar's and the toolbar's, are
                // the whole of what the eye can still see darkened by then, so
                // they are also the whole of what would otherwise sit there
                // dark until the card had already arrived.
                //
                // Coerced: [pop] carries past 1, and an alpha is no more
                // allowed below zero than a corner radius is.
                .graphicsLayer {
                    val dragging = (dragPx / (cardRect.height * SWIPE_SCALE_TRAVEL))
                        .coerceIn(0f, 1f)
                    alpha = opacity.value * (1f - expand.value).coerceIn(0f, 1f) *
                        handoff.value * (1f - dragging * 0.7f)
                }
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    // Nothing dismisses a card that is already on its way to
                    // being a tab.
                    enabled = !promoting,
                    onClick = onDismiss,
                ),
        )

        val haptics = rememberHaptics()
        // The gesture's own arithmetic, in one place: how far is "closed", and
        // how far the card has been taken as a fraction of that.
        val closeDistance = cardRect.height * SWIPE_CLOSE_FRACTION
        val scaleTravel = cardRect.height * SWIPE_SCALE_TRAVEL

        PreviewCard(
            preview = shown,
            cardRect = cardRect,
            pageRect = page,
            open = { open.value },
            opacity = { opacity.value },
            expand = { expand.value },
            fade = { handoff.value },
            drag = { dragPx },
            dragScale = { lerp(1f, SWIPE_MIN_SCALE, (dragPx / scaleTravel).coerceIn(0f, 1f)) },
            // Only while the card is sitting still: a promotion in flight owns
            // the card, and a preview still arriving has no page to be at the
            // top of yet.
            dragEnabled = { !promoting && expand.value == 0f },
            onDragStart = {
                snapBack?.cancel()
                haptics.gestureStart()
            },
            onDrag = { dy -> dragPx = dy },
            onDragEnd = { distance, velocity ->
                if (distance > closeDistance || velocity > SWIPE_CLOSE_VELOCITY) {
                    haptics.confirm()
                    // The ordinary close, started from where the finger left
                    // the card: [dragPx] is simply left where it is, so the
                    // gather-shrink-fade plays about the position it is being
                    // held at instead of snapping home first and leaving from
                    // somewhere the user was not looking.
                    onDismiss()
                } else {
                    haptics.reject()
                    snapBack = scope.launch {
                        animate(dragPx, 0f, animationSpec = arrive(SNAP_BACK_MS)) { value, _ ->
                            dragPx = value
                        }
                    }
                }
            },
            webViewFor = webViewFor,
            onRelease = onRelease,
        )

        Box(
            Modifier
                .offset { IntOffset(0, barTop.roundToInt()) }
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            PreviewActions(
                // Where the preview has actually got to, so following a link
                // inside the card and then opening it lands on the page being
                // read rather than back at the link.
                url = shown.url,
                // Out of the way of the card coming over it, and gone well
                // before it arrives.
                fade = {
                    opacity.value * (1f - expand.value * 2.5f).coerceIn(0f, 1f) *
                        (1f - (dragPx / (cardRect.height * SWIPE_SCALE_TRAVEL) * 2f))
                            .coerceIn(0f, 1f)
                },
                // Carried down with the card rather than left behind for it to
                // slide over — the two are one object while the finger is on
                // them.
                offsetY = { dragPx },
                scale = { lerp(PREVIEW_ENTER_SCALE, 1f, open.value) },
                onOpen = { promote(onOpen) },
                onOpenInNewTab = onOpenInBackgroundTab?.let { open ->
                    { url: String ->
                        open(url)
                        onDismiss()
                    }
                } ?: { _: String -> promote(onOpenInNewTab) },
                onCopyLink = onCopyLink,
            )
        }
    }
}

/**
 * The card, and the whole of the seam-free promotion.
 *
 * The trick is that **the page inside the card is laid out at the size it will
 * end at, from the first frame** — the tab's own page rect — and drawn into the
 * card scaled DOWN to fit its width. So a promotion is a scale of `0.91 -> 1`
 * that lands on exactly the layout the tab underneath is showing: same text
 * size, same line breaks, same column widths, same place on screen. The
 * dissolve at the end is then between two identical pictures, which is what
 * "seamless" actually requires — a card whose page was laid out at card width
 * has DIFFERENT line breaks from the page it becomes, and no crossfade can
 * hide a reflow.
 *
 * Everything moves in the layout and draw phases only: the window's size comes
 * from a [layout] block, its position from an [offset]-style placement, and
 * the page's scale from a [graphicsLayer] — the WebView itself is measured
 * once, at the destination size, and never re-measured. A WebView resized per
 * frame reflows its page per frame, which is the one cost this app never pays
 * (see the keyboard's `imeAnimationTarget` for the same rule).
 *
 * This is the container transform, done the way the Material spec describes
 * it: fit ONE axis (the width), mask the other, and cross-fade the contents
 * at the end. What the spec cannot say, because it assumes two views of the
 * same app's own content, is that with a web page the fit has to be decided
 * before the page is laid out rather than after.
 */
@Composable
private fun PreviewCard(
    preview: LinkPreview,
    cardRect: Rect,
    pageRect: Rect,
    open: () -> Float,
    opacity: () -> Float,
    expand: () -> Float,
    fade: () -> Float,
    drag: () -> Float,
    dragScale: () -> Float,
    dragEnabled: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float, Float) -> Unit,
    webViewFor: (Long) -> WebView?,
    onRelease: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val cornerPx = with(density) { PREVIEW_CORNER.toPx() } * SpecialCornerScale
    val shadowPx = with(density) { PREVIEW_SHADOW.toPx() }
    // What the destination's width has to shrink by to sit inside the card.
    val restScale = cardRect.width / pageRect.width
    val pageSize = with(density) { DpSize(pageRect.width.toDp(), pageRect.height.toDp()) }

    Box(
        Modifier
            // The window: its position and its size both animate, and both
            // are read in the layout phase, so a whole promotion recomposes
            // nothing. The child is measured at the destination size whatever
            // the window is doing (see requiredSize below), which is what
            // keeps the page's own layout out of it.
            .offset {
                val t = expand()
                IntOffset(
                    lerp(cardRect.left, pageRect.left, t).roundToInt(),
                    lerp(cardRect.top, pageRect.top, t).roundToInt(),
                )
            }
            .graphicsLayer {
                val t = expand()
                // The entrance, about the window's own centre — a different
                // origin from the expand's, which is why the two are separate
                // layers rather than one product.
                val entering = lerp(PREVIEW_ENTER_SCALE, 1f, open()) * dragScale()
                scaleX = entering
                scaleY = entering
                // The swipe. In the layer rather than the layout, so the page
                // inside is never re-measured by a gesture that is only ever
                // going to put it back.
                translationY = drag()
                // Coerced, because [pop] carries PAST 1 before it
                // settles: a corner size is not allowed to be negative and
                // throws outright, the same rule the shrink's own radius
                // follows for the same reason.
                val closing = (1f - t).coerceAtLeast(0f)
                shape = RoundedCornerShape(cornerPx * closing)
                clip = true
                // Gone before the card is: a shadow needs something to fall
                // on, and by then it is the page.
                shadowElevation = shadowPx * closing
                alpha = opacity() * fade()
            }
            .layout { measurable, _ ->
                val t = expand()
                val width = lerp(cardRect.width, pageRect.width, t).roundToInt()
                val height = lerp(cardRect.height, pageRect.height, t).roundToInt()
                // Unbounded on purpose: the content is requiredSize'd to the
                // destination and must not be squeezed into the window it is
                // being seen through.
                val placeable = measurable.measure(Constraints())
                layout(width, height) { placeable.place(0, 0) }
            }
            .background(PageBg)
            // A press that lands on the card but on nothing in particular is
            // not a dismissal — without this it reaches the scrim underneath
            // and closes the very thing being read.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Nothing but the page. No address bar, no title, no progress line:
        // the card is a window onto the page, and a bar across the top of it
        // would be this app's chrome wrapped around somebody else's.
        Box(
            Modifier
                // Laid out at the DESTINATION size, always — the whole seam
                // depends on this.
                .requiredSize(pageSize)
                .graphicsLayer {
                    // Scaled about the window's top-left corner, so the page's
                    // origin and the window's origin are the same point at
                    // every value of the animation. Anchoring anywhere else
                    // would mean the page sliding under its own window.
                    transformOrigin = TransformOrigin(0f, 0f)
                    val scale = lerp(restScale, 1f, expand())
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            key(preview.token) {
                PreviewPage(
                    token = preview.token,
                    webViewFor = webViewFor,
                    onRelease = onRelease,
                    dragEnabled = dragEnabled,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
        }
    }
}

/**
 * The page, hosted in a [PreviewDragHost] of its own — which is both how the
 * WebView can be taken back out of the tree before the ViewModel destroys it
 * (destroying an attached WebView leaves a dead renderer's window behind) and
 * how the swipe-down-to-close is taken off the page rather than shared with
 * it.
 */
@Composable
private fun PreviewPage(
    token: Long,
    webViewFor: (Long) -> WebView?,
    onRelease: (Long) -> Unit,
    dragEnabled: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float, Float) -> Unit,
) {
    // The host is built once; these are read through it on every event, so a
    // recomposition that hands over new lambdas doesn't need a new host.
    val enabled by rememberUpdatedState(dragEnabled)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PreviewDragHost(context).apply {
                canDrag = { enabled() }
                // Qualified: the composable's own parameters of these names
                // are in scope here and would win otherwise.
                this.onDragStart = { start() }
                this.onDrag = { dy -> move(dy) }
                this.onDragEnd = { distance, velocity -> end(distance, velocity) }
                webViewFor(token)?.let { web ->
                    (web.parent as? ViewGroup)?.removeView(web)
                    addView(
                        web,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            }
        },
        onRelease = { host ->
            host.removeAllViews()
            onRelease(token)
        },
    )
}

/**
 * The page's container, and the swipe that closes the preview.
 *
 * **Why this is a ViewGroup rather than a `Modifier.pointerInput`.** The page
 * is a WebView, and a WebView is not a Compose participant: it takes the
 * touches that reach it and it keeps them. Compose can watch a gesture over a
 * WebView but it cannot take one back, and "the swipe must not also scroll the
 * page" is precisely a taking-back. `onInterceptTouchEvent` is the platform's
 * own answer — returning true from it delivers an ACTION_CANCEL to the child
 * and every event after that comes here instead, which is what makes the page
 * stop dead the instant the drag is recognised rather than following the
 * finger underneath the card.
 *
 * The gesture is only offered with the page at its top and only downward, and
 * the first movement past the touch slop decides which it is, ONCE — the same
 * axis-lock the toolbar's own two-gesture button uses. A drag that starts as
 * the page scrolling stays the page scrolling, however far sideways it wanders
 * afterwards.
 *
 * **Everything is measured in RAW screen coordinates**, which is not a detail.
 * This view sits inside the very layer the drag translates and scales, and
 * Compose hands a hosted View its pointer positions in that view's own space —
 * so a local `y` is taken through the inverse of a transform this gesture is
 * itself driving. The card moves down, the finger's local position moves back
 * up by the same amount, the next delta is measured against a moved origin,
 * and the whole thing feeds back into a stutter. `rawY` is in the window's
 * space, which nothing here can move; it also happens to be the space the card
 * is translated in, so a pixel of finger is a pixel of card. A local
 * measurement is not only unstable but inflated, since the page inside the
 * card is drawn at ~0.91.
 */
private class PreviewDragHost(context: Context) : FrameLayout(context) {
    var canDrag: () -> Boolean = { true }
    var onDragStart: () -> Unit = {}
    var onDrag: (Float) -> Unit = {}
    var onDragEnd: (Float, Float) -> Unit = { _, _ -> }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tracking = false
    private var rejected = false
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var velocity: VelocityTracker? = null

    private val page: WebView? get() = (0 until childCount)
        .map(::getChildAt)
        .filterIsInstance<WebView>()
        .firstOrNull()

    /** The document at its top — the one state where a pull down means nothing to the page. */
    private fun atTop(): Boolean = (page?.scrollY ?: 1) <= 0

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                tracking = false
                rejected = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking || rejected) return tracking
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (abs(dy) < slop && abs(dx) < slop) return false
                if (dy > 0 && abs(dy) > abs(dx) && atTop() && canDrag()) {
                    beginDrag(event)
                    return true
                }
                // Decided against, and stays decided for the whole gesture:
                // re-testing a few pixels later is how a page scroll turns
                // into a dismissal half way through.
                rejected = true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                rejected = false
            }
        }
        return tracking
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A down that lands here rather than on the page (the card's own
        // margins, say) is not the start of anything: let it through.
        if (!tracking) return false
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDrag((event.rawY - originY).coerceAtLeast(0f))
            MotionEvent.ACTION_UP -> {
                val tracker = velocity
                tracker?.computeCurrentVelocity(1_000)
                finishDrag((event.rawY - originY).coerceAtLeast(0f), tracker?.yVelocity ?: 0f)
            }
            // Handed back rather than dropped: the card is sitting off its
            // mark and something has to put it back.
            MotionEvent.ACTION_CANCEL -> finishDrag(0f, 0f)
        }
        return true
    }

    private fun beginDrag(event: MotionEvent) {
        tracking = true
        // Re-based to where the slop was crossed, so the card starts moving
        // from under the finger rather than jumping the slop's own distance.
        originY = event.rawY
        velocity = VelocityTracker.obtain()
        track(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        onDragStart()
    }

    /**
     * The velocity tracker fed in the same raw space everything else here is
     * measured in — a copy of the event with its location moved, since
     * VelocityTracker reads the local coordinates and those are the ones this
     * gesture cannot trust.
     */
    private fun track(event: MotionEvent) {
        val tracker = velocity ?: return
        val raw = MotionEvent.obtain(event)
        raw.setLocation(event.rawX, event.rawY)
        tracker.addMovement(raw)
        raw.recycle()
    }

    private fun finishDrag(distance: Float, yVelocity: Float) {
        tracking = false
        rejected = false
        velocity?.recycle()
        velocity = null
        onDragEnd(distance, yVelocity)
    }

    /**
     * Ignored while the page is at its top, which is a deliberate override of
     * the page's wishes. Chromium asks for this whenever the document handles
     * touches itself — an analytics listener on `document` is enough — and
     * honouring it there would mean the close gesture simply not existing on
     * a large share of the web. At the top of the document there is nothing
     * the page can be doing with a downward drag that the preview's own
     * dismissal should lose to; anywhere else the request stands.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !tracking && atTop() && canDrag()) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}

/**
 * The three things worth doing with a link, on a bar of their own under the
 * card rather than inside it: the card is the page, and controls drawn over a
 * page belong to the page.
 */
@Composable
private fun PreviewActions(
    url: String,
    fade: () -> Float,
    scale: () -> Float,
    offsetY: () -> Float,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    Surface(
        color = BarBg,
        shape = specialCorner(20.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .graphicsLayer {
                alpha = fade()
                val s = scale()
                scaleX = s
                scaleY = s
                translationY = offsetY()
            }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier.height(ACTION_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Action(Icons.Filled.OpenInBrowser, "Open") { onOpen(url) }
            ActionDivider()
            Action(Icons.Filled.Tab, "New tab") { onOpenInNewTab(url) }
            ActionDivider()
            Action(Icons.Filled.ContentCopy, "Copy link") {
                // Taking something away with you — the same weight the menu's
                // copy of a page address has.
                haptics.confirm()
                onCopyLink(url)
            }
        }
    }
}

@Composable
private fun ActionDivider() {
    VerticalDivider(
        color = HairLine,
        modifier = Modifier
            .height(24.dp)
            .width(1.dp),
    )
}

@Composable
private fun Action(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = InkStrong, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = InkStrong,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
