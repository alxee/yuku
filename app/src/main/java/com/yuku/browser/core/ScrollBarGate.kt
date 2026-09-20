package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * Decides when a WebView's own scrollbar is allowed to be drawn.
 *
 * WebView awakens its scrollbar from `onScrollChanged`, i.e. from ANY change
 * of the exposed scroll offset — and plenty of those are not the user
 * scrolling the page. A tab arriving from the quick switch resyncs its offset
 * to Chromium's the moment its view goes VISIBLE; a sideways swipe between
 * tabs, a tap that nudges the page by a pixel, a layout settling after a load
 * all land the same way. Each of them flashed a scrollbar down the right edge
 * of a page nobody had scrolled, and — because a preview is a copy of the
 * window's own pixels, taken [SETTLE_MS] after the page last moved, which is
 * well inside the bar's ~550ms fade — baked it into the tab's card too.
 *
 * So the bar is drawn only for a scroll the FINGER drove and that actually
 * went somewhere: it takes hold once the page has travelled [TRAVEL_PX] from
 * where it stood when the finger went down, and stays alive through the fling
 * that follows the lift. Everything else moves the page silently.
 *
 * The gate never draws anything itself. It only owns
 * `isVerticalScrollBarEnabled`, which is what View consults when it comes to
 * paint the bar — the fade's own clock keeps running underneath whatever this
 * says, which is why [retract] has to invalidate: a bar that is mid-fade when
 * it is switched off is still on the last frame drawn, and that frame is what
 * a [PixelCopy][android.view.PixelCopy] would otherwise put on the card.
 */
class ScrollBarGate(private val web: WebView) {
    /**
     * Whether the scroll being reported now is the USER's: a finger is on the
     * page, or a finger-driven scroll (and its fling) is still moving it. False
     * for a page moving itself — duckduckgo.com's image viewer locks the body
     * with `position: fixed`, which drops the offset to 0 in one jump.
     */
    val userDriven: Boolean get() = if (touchDown) gestureDragged else showing

    /** Whether the host will allow a bar at all right now — see WebViewHost. */
    private var hostAllows = false

    /** Whether a finger is currently on the page. */
    private var touchDown = false

    /**
     * Whether the finger currently down has physically moved enough to be a
     * drag. This deliberately comes from touch coordinates, not the document
     * offset: a site is free to reposition its document while handling a tap.
     * Keeping it distinct from [showing] is important when a tap arrives while
     * the previous fling's scrollbar is still fading.
     */
    private var gestureDragged = false

    /** The Y coordinate at which the current touch started. */
    private var downY = 0f

    /** Where the page stood when that finger went down. */
    private var anchorScrollY = 0

    /** Whether the current scroll has earned a bar. */
    private var showing = false

    init {
        // A WebView arrives with its scrollbar enabled, i.e. showing on any
        // scroll at all, which is the state this exists to replace. Nothing
        // draws one again until [onScroll] says a finger earned it.
        web.isVerticalScrollBarEnabled = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { if (showing) { showing = false; apply() } }

    /** Passed every touch the page sees, from the WebView's own touch listener. */
    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                anchorScrollY = web.scrollY
                downY = event.y
                gestureDragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - downY) >= touchSlopPx()) gestureDragged = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                gestureDragged = false
            }
        }
    }

    /** Passed every scroll the page reports, from the host's scroll listener. */
    fun onScroll(scrollY: Int) {
        // A document offset changing during a touch is not evidence of a
        // scroll. Sites commonly do that while handling a menu click (for
        // example by locking the body with position: fixed), and Keddr does it
        // while its menu is first prepared. Require an actual finger drag
        // before accepting its offset change, even if a previous fling has
        // left [showing] true. Once the finger lifts, that existing state
        // carries a genuine fling as before.
        if (touchDown) {
            if (!gestureDragged) return
            if (!showing && abs(scrollY - anchorScrollY) >= travelPx()) {
                showing = true
                apply()
            }
        } else if (!showing) {
            // Not showing and no finger down: a resync, anchor jump, script,
            // or layout settling moved the page, not the user.
            return
        }
        // Outlives the platform's own fade (~300ms delay + ~250ms fade), so
        // the bar is never cut off mid-fade by this timer — only ever by
        // [retract].
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, HOLD_MS)
    }

    /** Whether the host is showing the page at a moment a bar would make sense. */
    fun setHostAllows(allows: Boolean) {
        if (hostAllows == allows) return
        hostAllows = allows
        apply()
    }

    /**
     * Takes the bar off the page NOW, for a capture: a preview is the window's
     * pixels, and a bar three quarters faded is still ink on them. The next
     * real scroll brings it straight back.
     */
    fun retract() {
        handler.removeCallbacks(expire)
        if (!showing) return
        showing = false
        apply()
        // The bar is drawn by the host View, not by Chromium, so this is all
        // it takes to get a frame without it — but a frame there has to be:
        // nothing else about the page has changed, so nothing else would ask
        // for one.
        web.invalidate()
    }

    private fun apply() {
        val enabled = hostAllows && showing
        if (web.isVerticalScrollBarEnabled != enabled) web.isVerticalScrollBarEnabled = enabled
    }

    private fun travelPx(): Float = TRAVEL_DP * web.resources.displayMetrics.density

    private fun touchSlopPx(): Float = ViewConfiguration.get(web.context).scaledTouchSlop.toFloat()

    private companion object {
        /**
         * How far the page has to actually move under the finger before the
         * bar is worth drawing. Above a jiggle and a mis-hit tap, below any
         * scroll a user meant — a deliberate flick passes it within a couple
         * of frames.
         */
        const val TRAVEL_DP = 24f

        /** How long a bar stays armed after the last scroll it saw. */
        const val HOLD_MS = 700L
    }
}
