package com.yuku.browser.core

import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Tells pull-to-refresh when a gesture belongs to the page rather than to the
 * browser.
 *
 * Chromium's own overscroll refresh knows this for free — it lives inside the
 * renderer, where the touch is dispatched to the DOM first and the browser
 * only gets what the page didn't want. `SwipeRefreshLayout` sits *above* the
 * WebView instead, so it takes the pointer before the page has said anything,
 * and everything the page would have done with that drag — a lightbox that
 * swipes away, a bottom sheet, a slider, a map, a carousel — dies the moment
 * the pull is recognized.
 *
 * This is the missing half of that conversation, in two signals, both of them
 * standard ways a page says "this touch is mine":
 *
 *  - **A non-passive touch listener** on the element under the finger or any
 *    of its ancestors. `addEventListener` is wrapped at document start so
 *    those nodes get tagged as they register ([TOUCH_FLAG]), and the hit test
 *    in `WebViewSwipeRefreshLayout` reads the tag off each ancestor it walks.
 *    Non-passive is the point: a listener that reserved the right to call
 *    `preventDefault` intends to handle panning itself, and that's precisely
 *    what the spec made the passive flag mean. Defaults differ by target —
 *    on `window`/`document`/`body`/`documentElement` these listeners are
 *    passive unless asked otherwise, so those count only when `passive:false`
 *    is explicit, which keeps the countless analytics and polyfill listeners
 *    bolted onto the document from disabling the gesture site-wide.
 *  - **An actually-prevented touchmove**, reported straight out of the
 *    renderer through [Bridge]. This one is retrospective — it can only be
 *    known once the page has already prevented something — so it's the safety
 *    net for listeners registered before the wrapper was installed (a page
 *    restored from the back/forward cache, an injection that lost the race),
 *    and it's why the layout also bails out mid-pull rather than only at
 *    intercept time.
 *
 * A single shared timestamp is enough for the second signal because only one
 * WebView can be under a finger at a time. It's compared against the current
 * gesture's down time, so nothing older than the gesture can block it.
 */
object PullGate {

    /** Name the injected script tags nodes with, read back by the hit test. */
    const val TOUCH_FLAG = "__pullGateTouch"

    private const val BRIDGE_NAME = "__pullGateBridge"

    @Volatile
    private var preventedAt = 0L

    /** True when the page prevented a touch move at any point since [since]. */
    fun preventedSince(since: Long): Boolean = preventedAt >= since && since > 0L

    private val bridge = Bridge()

    class Bridge internal constructor() {
        /** Called from a renderer thread — keep it to one volatile write. */
        @JavascriptInterface
        fun prevented() {
            preventedAt = SystemClock.uptimeMillis()
        }
    }

    /**
     * Wires both signals into [web]. The script has to run before the page's
     * own scripts do — a listener registered before the wrapper is in place is
     * invisible to it — so it goes in as a document-start script where that's
     * supported, with the `prevented()` net covering what slips through.
     */
    fun attach(web: WebView) {
        web.addJavascriptInterface(bridge, BRIDGE_NAME)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    /**
     * Runs in every frame of every page. Deliberately tiny and total: it
     * wraps one method, adds two passive listeners, and touches nothing a
     * page can observe apart from a property name on nodes that already
     * registered touch handlers.
     */
    private val SCRIPT = """
        (function(){
          if (window.__pullGateInstalled) return;
          window.__pullGateInstalled = true;
          try {
            var add = EventTarget.prototype.addEventListener;
            EventTarget.prototype.addEventListener = function(type, fn, opts) {
              try {
                if (type === 'touchmove' || type === 'touchstart') {
                  // On the document-level targets these listeners are passive
                  // unless the page explicitly says otherwise; everywhere else
                  // non-passive is the default.
                  var loose = (this === window || this === document ||
                               this === document.body || this === document.documentElement);
                  var explicit = (opts && typeof opts === 'object') ? opts.passive : undefined;
                  var passive = loose ? (explicit !== false) : (explicit === true);
                  if (!passive) this.$TOUCH_FLAG = true;
                }
              } catch (e) {}
              return add.apply(this, arguments);
            };
          } catch (e) {}
          try {
            // Bubble phase at the window, i.e. after everything the page has
            // to say about this touch.
            window.addEventListener('touchmove', function(e) {
              if (e.defaultPrevented && window.$BRIDGE_NAME) {
                try { window.$BRIDGE_NAME.prevented(); } catch (err) {}
              }
            }, { passive: true });
            window.addEventListener('touchstart', function(e) {
              if (e.defaultPrevented && window.$BRIDGE_NAME) {
                try { window.$BRIDGE_NAME.prevented(); } catch (err) {}
              }
            }, { passive: true });
          } catch (e) {}
        })();
    """.trimIndent()
}
