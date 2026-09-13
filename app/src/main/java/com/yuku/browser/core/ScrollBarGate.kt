package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
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
    /** Whether the host will allow a bar at all right now — see WebViewHost. */
    private var hostAllows = false

    /** Whether a finger is currently on the page. */
    private var touchDown = false

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
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchDown = false
        }
    }

    /** Passed every scroll the page reports, from the host's scroll listener. */
    fun onScroll(scrollY: Int) {
        // Not showing and no finger down: something other than the user moved
        // the page — a resync, an anchor jump, a script, a layout settling.
        // Once showing, further scrolls keep it alive whether or not the
        // finger is still there, which is what carries the bar through a
        // fling.
        if (!showing) {
            if (!touchDown) return
            if (abs(scrollY - anchorScrollY) < travelPx()) return
            showing = true
            apply()
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
