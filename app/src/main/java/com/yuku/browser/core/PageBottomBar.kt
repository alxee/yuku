package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Makes the page aware of the browser's own bottom toolbar, and tells the
 * browser when the page has bottom-anchored controls of its own.
 *
 * The live WebView is laid out to the full height of the screen and the
 * toolbar is drawn OVER its bottom strip — nothing is re-laid-out when the bar
 * hides, which is what keeps a hide from flashing the window background while
 * Chromium catches up with a new size (see `pageOverflow` in `BrowserScreen`).
 * The cost of that is that the bottom strip of the page is covered while
 * the bar is up: content at the end of the document can be scrolled out from
 * under it, but a bar the page has PINNED to the bottom edge — a tab bar, a
 * player, a "next chapter" strip, a checkout button — never scrolls away, so
 * it sits under the toolbar until the user happens to scroll down.
 *
 * So the inset is pushed into the page and the page is shifted out of the way
 * of it, two ways:
 *
 * - **Bottom-anchored bars have their own offsets moved by the inset**, which
 *   brings them to rest on the toolbar's top edge. The shift is WRITTEN, then
 *   MEASURED, then corrected, because which offset actually holds a box down
 *   cannot be read: `getComputedStyle` reports the USED value for the offsets
 *   of a positioned box, so a bar that never set `top` reads back the px it
 *   happens to be sitting at, exactly like one that set it. `bottom` goes on
 *   first; a box that did not move gets `margin-bottom` instead (a sticky
 *   element resting at the end of its own flow), one that SHORTENED rather
 *   than slid gets its `top` as well (a bottom sheet stretched between a top
 *   and a bottom and pushed back down by a transform of its own), and a panel
 *   that slid off the top edge gets its height capped. Nothing more than that
 *   is written, so a bar with no `top` of its own keeps none and can still
 *   grow upward when its content does. Never a `transform` of ours, which is
 *   how these bars animate themselves in and out — an inline one would fight
 *   the site's own show/hide and can pin a hidden bar on screen. The values
 *   the site already had are read before anything is written, and given back
 *   when the element stops being a bar or the inset goes away.
 * - **The document gets it as `padding-bottom` on the body**, so the END of a
 *   page can be scrolled clear of the bar. The layout viewport already
 *   includes the strip the toolbar is drawn over, so a document's scroll runs
 *   out with its last content still under it — and on a page too short to
 *   scroll at all, that content is covered for good. The padding is exactly
 *   the inset, and goes away with it.
 * - **A document that cannot scroll at all has its app shell SHORTENED**
 *   instead. That is the case the other two answers cannot reach: the toolbar
 *   only ever uncovers its strip by hiding, and it only hides when the page is
 *   scrolled, so on a full-viewport app shell (claude.ai, and every other
 *   `100dvh` layout with a footer row at the end of it) the strip is gone for
 *   good, and padding a body nothing scrolls buys nothing. Nothing down there
 *   is `fixed` either — a footer at the end of a flex column is in FLOW, and
 *   shifting a box that is in flow just drops it on top of the row above it —
 *   so the viewport-sized boxes it lives in are capped to the height the user
 *   can actually see and the shell lays itself out again inside them. Which is
 *   the same thing as handing the page a shorter viewport, done the only way a
 *   page can be told from outside one.
 *
 * `--browser-bottom-inset` is also set on the root element, for a page that
 * would rather handle it itself.
 *
 * Detection is a **hit test, not a tree walk**. Finding fixed elements by
 * enumerating the DOM means `getComputedStyle` on every node in the document,
 * which is exactly the sort of thing that costs a frame on a big page;
 * `elementFromPoint` a few pixels above the bottom edge asks the engine a
 * question it already has the answer to, and the ancestor walk from there is a
 * dozen style reads at most. Three x positions rather than one, because a
 * centered FAB or a notch in the middle of a bar would otherwise be all that
 * is found (or all that is missed) — and, once an inset is applied, two y
 * positions: a bar that has already been shifted rests on the inset's line,
 * one that has just appeared still rests on the viewport's own bottom edge,
 * and both have to be found or applying the inset would hide the bar from the
 * measurement that justifies it.
 *
 * The **outermost** qualifying ancestor of each probe is the one that gets
 * shifted, and any BAR contained by another find is dropped: shifting both a
 * bar and its own wrapper would move the contents twice as far as the
 * background behind them. Panels are the exception — see below.
 *
 * What counts as a bottom bar: `position: fixed` or `sticky`, visible, its rect
 * resting on the bottom edge, spanning most of the width, and showing little
 * of itself — what it puts ON SCREEN, not how tall the box is, since a bottom
 * sheet is a tall box hanging most of the way off the bottom of the viewport
 * and is a bar as far as the user is concerned. Once something IS being
 * shifted the size test is dropped, so a sheet dragged open doesn't fall back
 * down under the finger that is opening it.
 *
 * Once something has been shifted it is kept while it is still down there,
 * and the test for that allows it a whole inset above the resting line: our
 * own shift is what lifted it. The bottom-most bar simply moves from the
 * viewport's edge onto the inset's line and keeps being recognised, but a bar
 * STACKED on another one rests a bar-height above that and answers to neither
 * line — dropping it there restores it, which puts it back on the line, which
 * shifts it again, and the bar jumps up and down for as long as the toolbar is
 * up (auto.ria.com's call/chat/save strip over the site's own nav).
 *
 * Something resting there that is too big to be a bar is a **panel** — a modal,
 * a lightbox, a full-screen sheet (amazon.com's location picker) — and it is
 * used unless a real bar was found INSIDE it, which is the one thing that
 * makes shortening it wrong: the bar is what the user acts on, it moves on
 * its own, and moving the panel around it would move it twice. Not whether
 * the page has any bar at all — a modal covers the page it opened over, and
 * that page's own bottom bar is still fixed and still found behind it,
 * invisible to the user and no reason to leave the modal's action button
 * under the toolbar. A panel is SHORTENED by its
 * bottom rather than slid up by both offsets, which would hang it off the top
 * edge: what it holds against its own bottom (an action button, an app shell's
 * nav) then comes up with it. It is not REPORTED either, since the height is
 * what decides whether the page's inset stops at the system navigation bar,
 * and a modal is not a reason to hold that strip open for a page.
 *
 * EVERY panel in the chain is shortened, not just the outermost — the same
 * thing the shell caps do, for the same reason. A sheet built as a fixed box
 * inside another fixed box (auto.ria.com's filter sheet is a `.popup-inner`
 * with its own `top` and `bottom` inside a `.popup`) takes no height from its
 * parent, so shortening the parent alone leaves the inner one standing on the
 * viewport's own bottom edge with its confirm button still under the toolbar.
 * The exception is a panel whose fixed descendants are positioned against IT
 * rather than against the viewport — anything with a transform, a filter or a
 * containment on it — where shortening the outer already brings the inner up
 * and shortening both would move it twice.
 *
 * Reports are per WebView (unlike [PullGate]'s single shared signal) because
 * the answer belongs to a tab, not to the app, and a backgrounded tab keeps
 * its own until it next says otherwise. The inset is the app's, so it is read
 * back through the bridge at document start rather than pushed after every
 * navigation — a page that renders its bar in its first frame is already
 * shifted on that frame, and there is no window in which a fresh document
 * doesn't know about the bar.
 */
object PageBottomBar {

    private const val BRIDGE_NAME = "__bottomBarBridge"

    /**
     * Rect width below this fraction of the viewport isn't a bar — it's a
     * floating action button, a toast, a cookie chip.
     */
    private const val MIN_WIDTH_FRACTION = 0.6

    /** Above this fraction of the viewport it's an overlay, not a bar. */
    private const val MAX_HEIGHT_FRACTION = 0.35

    /** A broad side drawer is not a bottom panel — see PageTopInset. */
    private const val PANEL_WIDTH_FRACTION = 0.95

    /** Shortest thing worth reserving space for, in CSS px. */
    private const val MIN_HEIGHT_PX = 12

    /**
     * How far above the viewport's bottom edge a bar's own bottom may sit and
     * still count as anchored to it. Covers sub-pixel layout and the couple of
     * pixels a page leaves for a shadow or a rounded corner.
     */
    private const val BOTTOM_SLACK_PX = 6

    /** Smallest change in height worth another trip across the bridge. */
    private const val REPORT_EPSILON_PX = 4

    /**
     * How far apart the hit tests are across the strip the toolbar covers, in
     * CSS px. A bar resting on one of that strip's two edges is found by a
     * probe at that edge; one that clears the viewport's bottom by a margin of
     * its own rests between them and is found by nothing. Anything at least
     * this tall in there is crossed by a line — which is every bar worth
     * moving, since below it is a chip rather than a bar.
     */
    private const val PROBE_STEP_PX = 24

    /**
     * How far above the bottom edge a bar may rest and still be found while
     * NOTHING covers that edge (inset 0 — the toolbar away). Only the edge
     * line was probed then, so a bar the site holds off it by a margin
     * (duckduckgo.com's toast, `bottom: 15px`) was never reported, never got
     * the navigation-bar floor, and sat on the gesture pill.
     */
    private const val EDGE_BAND_PX = 24

    /**
     * How far a box's top may sit below the viewport's own, and how far its
     * height may fall short of it, and still be the app shell. Covers the
     * sub-pixel heights a `100dvh` layout lands on (863.238px here).
     */
    private const val SHELL_SLACK_PX = 2

    /**
     * A shell shortened below this is not worth having: something else is
     * wrong with the measurement, and a page squeezed into nothing is worse
     * than a page with its last strip covered.
     */
    private const val MIN_SHELL_HEIGHT_PX = 80

    /** At most one measurement per this many ms, however often it's asked for. */
    private const val THROTTLE_MS = 250

    /**
     * How many measurements in a row may skip [THROTTLE_MS] while something at
     * the bottom edge is still moving. Long enough to cover a sheet's entrance
     * (a few hundred ms), short enough that a page with something permanently
     * animating down there falls back to the throttle instead of measuring
     * every frame for as long as it is open.
     */
    private const val SETTLING_FRAMES = 30

    /**
     * Wires [onChanged] — always called on the main thread, with the bar's
     * height in CSS pixels, or 0 for "no bottom bar" — into [web], and lets
     * the page read the current bottom inset back through [insetPx] (device
     * pixels; see [setInset]) as soon as its document starts.
     *
     * Document-start, so a page that renders its bar in its first frame is
     * measured on that frame rather than after the first scroll. Where the
     * feature is missing there is simply no detection and no inset: the page
     * then behaves exactly as it did before this existed.
     */
    fun attach(
        web: WebView,
        insetPx: () -> Int,
        fillPx: () -> Int,
        endPx: () -> Int = { 0 },
        onChanged: (Int) -> Unit,
    ) {
        web.addJavascriptInterface(Bridge(insetPx, fillPx, endPx, onChanged), BRIDGE_NAME)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    /**
     * Tells [web]'s current document how much of its bottom edge the browser
     * is covering, in device pixels — the page converts to CSS pixels itself,
     * since only it knows the zoom it is being rendered at.
     *
     * Call on every change: a document that hasn't started yet reads the same
     * value through the bridge instead, so the two paths can't disagree.
     */
    fun setInset(web: WebView, px: Int, endPx: Int = 0) {
        web.evaluateJavascript(
            "window.__bottomBarInset && window.__bottomBarInset($px, $endPx)",
            null,
        )
    }

    /**
     * Nonzero to paint each moved bar's whole move in the colour the bar
     * shows; 0 for none. Under the page lens (`ui/PageLens.kt`), with the
     * toolbar away, a bar is lifted clear of the bend and the curve reads page
     * out of the gap under it: unpainted, whatever scrolls behind a bottom bar
     * was pulled into the curve under it.
     */
    fun setFill(web: WebView, px: Int) {
        web.evaluateJavascript("window.__bottomBarFill && window.__bottomBarFill($px)", null)
    }

    class Bridge internal constructor(
        private val insetPx: () -> Int,
        private val fillPx: () -> Int,
        private val endPx: () -> Int,
        private val onChanged: (Int) -> Unit,
    ) {
        private val main = Handler(Looper.getMainLooper())

        /**
         * Device pixels of room the document's END needs while nothing of
         * ours covers the edge but the page still runs under the navigation
         * bar; see the body padding in the script.
         */
        @JavascriptInterface
        fun end(): Int = endPx()

        /** Device pixels of spacing painted below a moved bar; see [setFill]. */
        @JavascriptInterface
        fun fill(): Int = fillPx()

        /** Called from a renderer thread. */
        @JavascriptInterface
        fun bar(height: Int) {
            main.post { onChanged(height) }
        }

        /**
         * Device pixels of the page's bottom edge the browser is covering.
         * Read once at document start; [setInset] drives every later change.
         */
        @JavascriptInterface
        fun inset(): Int = insetPx()
    }

    /**
     * Runs in the top document of every page — it is registered for every
     * frame, and stands itself down in the rest (see the guard below). Its
     * measurement reads layout and
     * never writes any, so it can't reflow the page it's measuring; the
     * shifts it then applies are guarded against writing a value that is
     * already there, or its own MutationObserver would keep it measuring
     * forever. Everything it touches is wrapped, since a page that throws
     * inside our listener is still a page that has to work.
     */
    private val SCRIPT = """
        (function(){
          // THE TOP DOCUMENT ONLY. A document-start script is registered for
          // every frame and `addJavascriptInterface` puts the bridge in every
          // frame too, so a subframe running this reports for the WHOLE TAB
          // through the same `bar()` — and it is measuring its own viewport,
          // which our toolbar is not over and which almost never has a
          // bottom bar in it. An ad frame's honest answer is therefore 0,
          // and it lands on top of the top document's own: auto.ria.com's
          // sticky nav strip was found and reported at 56px, and the
          // adtelligent sync iframe that loads a second later answered 0
          // for the tab. The app then took the floor back out from under
          // the page, so the site's nav came to rest under the system
          // navigation bar the moment our toolbar hid — and the page never
          // said otherwise again, because its own last report was still 56
          // and nothing here repeats an answer that has not changed.
          // Which frame spoke last is a race, which is why it looked like a
          // difference between builds.
          //
          // Nothing is lost by standing down here: the inset covers the top
          // document's bottom edge, a bar inside an iframe is not resting on
          // it, and padding an ad frame's body buys nobody anything.
          try { if (window.top !== window) return; } catch (e) { return; }
          if (window.__bottomBarInstalled) return;
          window.__bottomBarInstalled = true;

          // CSS px of the page's bottom edge the browser's toolbar covers:
          // what the app last pushed, and what the page is actually being
          // shifted by. They differ for one reason only — see applyInset.
          var appInset = 0;
          var inset = 0;
          // CSS px of room the document's end gets while the inset is 0.
          var endPad = 0;
          // The elements moved out from under it, each with the margin the
          // site itself asked for so it can be given back exactly.
          var shifted = [];
          // Padding added to the body of a document that cannot scroll — kept
          // separately so it can be subtracted back out of scrollHeight
          // before deciding whether the document scrolls.
          var bodyPad = 0;
          var bodyPadBase = 0;
          var bodyPadInline = '';
          var bodyPadPriority = '';
          // The viewport-sized boxes capped to the visible height, each with
          // the max-height the site itself asked for so it can be given back
          // exactly, and the top it was capped against.
          var capped = [];
          // Boxes a cap was found to clip — see applyShellCaps.
          var notShells = new WeakSet();

          // CSS px of spacing painted between a moved bar and the bottom
          // edge, in the bar's own background colour, or 0 — see writeFill.
          var fill = 0;
          try { fill = (__bottomBarBridge.fill() || 0) / (window.devicePixelRatio || 1); } catch (e) {}
          // A bar shorter than the spacing needs the shadow SPREAD, which
          // widens it past the bar's sides, so only a bar this nearly as wide
          // as the viewport may have that.
          var FILL_WIDTH_FRACTION = 0.97;
          window.__bottomBarFill = function(px){
            fill = (px || 0) / (window.devicePixelRatio || 1);
            schedule();
          };

          // Which property is written FIRST to move this element off the
          // bottom edge. Only one, and which of the others are needed as well
          // is not decided here but MEASURED, in correct() below.
          //
          // It cannot be decided here, because it cannot be read.
          // `getComputedStyle` reports the USED value for the offsets of a
          // positioned box, so a fixed bar that never set `top` reads back the
          // px it happens to be sitting at, exactly like one that set it, and
          // a fixed box with no `bottom` reads back a bottom all the same.
          // Guessing from those numbers is what put auto.ria.com's filter
          // sheet — `height: 100%` hung from `bottom: 0`, its `top` merely the
          // 16px that fell out of the two — 65px off the top of the screen
          // with its search field cropped: the guess said the box was
          // stretched between a top and a bottom and would SHORTEN, and it
          // slid instead.
          //
          // `bottom` for anything that has one, which is nearly every bar
          // there is: a sticky box is held against the scrollport by its
          // bottom inset and its margin does not enter into that at all
          // (auto.ria.com's `.main-sticky` sat exactly where it started with
          // a 57px margin on it). `margin-bottom` for the rest — a sticky
          // element resting at the end of its own flow.
          //
          // Never a `transform` of ours, which is how these bars animate
          // themselves in and out: an inline one would fight the site's own
          // show/hide and can pin a hidden bar on screen.
          function lever(el){
            var s = window.getComputedStyle(el);
            var b = parseFloat(s.bottom);
            if (s.bottom !== 'auto' && isFinite(b)){
              return [{ prop: 'bottom', base: b, sign: 1 }];
            }
            var m = parseFloat(s.marginBottom);
            return [{ prop: 'margin-bottom', base: isFinite(m) ? m : 0, sign: 1 }];
          }

          // How much of [el] is on screen at the bottom edge, or 0 if it isn't
          // something that sits there at all. The SIZE cap is the caller's:
          // what it separates is a bar from a panel, and both get moved — see
          // measure().
          //
          // `lift` is how far above the resting line this element is allowed
          // to be found — see the retention loop in measure().
          function shownHeight(el, vw, vh, lift){
            if (!el || el.nodeType !== 1 || !el.isConnected) return 0;
            if (el === document.body || el === document.documentElement) return 0;
            var s = window.getComputedStyle(el);
            var pos = s.position;
            if (pos !== 'fixed' && pos !== 'sticky') return 0;
            if (s.visibility === 'hidden' || s.display === 'none') return 0;
            if (parseFloat(s.opacity || '1') < 0.1) return 0;
            var r = el.getBoundingClientRect();
            if (r.width < vw * $MIN_WIDTH_FRACTION) return 0;
            // Resting ON the bottom edge — or on the inset's line, if this bar
            // has already been shifted up off it. A bar that has been slid off
            // screen (the site's own hide-on-scroll) or one still up the page
            // is not occupying either.
            if (r.bottom < vh - Math.max(inset, $EDGE_BAND_PX) - (lift || 0) - $BOTTOM_SLACK_PX ||
                r.top >= vh) return 0;
            // What it puts ON SCREEN, not how tall the box is: a bottom sheet
            // is a tall box hanging most of the way off the bottom of the
            // viewport, and it is a bar as far as the user is concerned.
            var shown = Math.min(r.bottom, vh) - Math.max(r.top, 0);
            if (shown < $MIN_HEIGHT_PX) return 0;
            return Math.round(shown);
          }

          // The OUTERMOST ancestor that still reads as a bar: shifting an inner
          // bar and its wrapper both would move the contents twice as far as
          // the background behind them. Every tall PANEL resting on the bottom
          // edge comes back alongside it, and they are used only when the
          // stack held no bar at all — a modal or a bottom sheet too big to be
          // a bar (amazon.com's location picker) still has an action button
          // held against its own bottom edge, which is under the toolbar until
          // the panel is shortened.
          // Whether [s]'s element is the containing block for the `fixed`
          // boxes inside it: a transform, a filter or a containment turns a
          // fixed descendant into a box positioned against IT rather than
          // against the viewport. Two panels either side of one are COUPLED —
          // shortening the outer already brings the inner up with it — which
          // is the one case where the outermost is still the only one to move.
          function fixesDescendants(s){
            return (s.transform && s.transform !== 'none') ||
              (s.filter && s.filter !== 'none') ||
              (s.perspective && s.perspective !== 'none') ||
              (s.backdropFilter && s.backdropFilter !== 'none') ||
              (s.willChange && /transform|filter|perspective/.test(s.willChange)) ||
              (s.contain && /paint|layout|strict|content/.test(s.contain));
          }

          function barFrom(node, vw, vh){
            var bar = null, barH = 0, panels = [], coupled = false;
            for (var d = 0; node && d < 16; d++){
              var h = shownHeight(node, vw, vh);
              // Only once a panel is in hand, since this is a style read the
              // walk does not otherwise need.
              if (panels.length &&
                  fixesDescendants(window.getComputedStyle(node))) coupled = true;
              if (h > 0){
                if (h <= vh * $MAX_HEIGHT_FRACTION){ bar = node; barH = h; }
                else {
                  // A side drawer may cover most of a phone's width, but it
                  // owns its vertical entrance animation. Moving or capping it
                  // here fights that animation; only a near-full-width overlay
                  // needs clearance from browser chrome.
                  var panelRect = node.getBoundingClientRect();
                  if (panelRect.width < vw * $PANEL_WIDTH_FRACTION){
                    node = node.parentElement ||
                      (node.getRootNode && node.getRootNode().host) || null;
                    continue;
                  }
                  // EVERY panel in the chain, not just the outermost, for the
                  // same reason the shells are all capped: a box sized by its
                  // own `top` and `bottom` takes no height from its parent, so
                  // shortening the parent alone leaves it standing on the
                  // viewport's own bottom edge. auto.ria.com's filter sheet is
                  // a fixed `.popup-inner` inside a fixed `.popup`, and its
                  // confirm button is at the end of the inner one's flex
                  // column — with only the outer shortened the button stayed
                  // exactly under the toolbar, all 80px of it.
                  if (coupled) panels.length = 0;
                  panels.push(node);
                  coupled = false;
                }
              }
              node = node.parentElement ||
                (node.getRootNode && node.getRootNode().host) || null;
            }
            return { bar: bar, h: barH, panels: panels };
          }

          // Whether the DOCUMENT itself can be scrolled, discounting the
          // padding we put on it: it is our own padding that makes an app
          // shell's document technically scrollable, and treating that as
          // "the user can scroll the strip into view" is what left the whole
          // question unanswered for exactly the pages that need it.
          //
          // And the room PageTopInset gives the document's START, for the same
          // reason from the other end: under the page lens it pushes a `100dvh`
          // shell down by the lens's overscan, so the shell overflows by
          // exactly that and read as a document that scrolls. It was never
          // capped, and its footer — a chat's composer and send button — sat
          // wholly under the toolbar.
          function topPad(){
            try { return window.__topInsetPad || 0; } catch (e) { return 0; }
          }

          function docScrolls(){
            var h = document.documentElement;
            if (!h) return true;
            return (h.scrollHeight - bodyPad - topPad()) > h.clientHeight + $SHELL_SLACK_PX;
          }

          // The app shell: the viewport-sized boxes a probe point sits inside.
          // On a document that cannot scroll these hold everything the user
          // can reach, and none of it is `fixed`, so none of it is a bar and
          // none of it can be shifted — a footer at the end of a flex column
          // is in FLOW, and moving it up drops it on the row above. They are
          // SHORTENED to the height that is actually visible instead, and the
          // shell lays itself out again inside them.
          //
          // Every one of them, not just the outermost: a box sized by its own
          // `top` and `bottom` (claude.ai's sidebar) takes no height from its
          // parent, so capping the parent alone leaves it hanging past the
          // bottom edge — where it is now CLIPPED by that parent rather than
          // merely covered, which is worse than what we started with.
          function shellsFrom(node, vh, into){
            // A shell starts where the room at the document's start left it.
            var pad = topPad();
            for (var d = 0; node && d < 20; d++){
              if (node.nodeType === 1 && node !== document.body &&
                  node !== document.documentElement && into.indexOf(node) < 0 &&
                  !notShells.has(node)){
                var r = node.getBoundingClientRect();
                if (r.top <= pad + $SHELL_SLACK_PX && r.height >= vh - $SHELL_SLACK_PX) {
                  into.push(node);
                }
              }
              node = node.parentElement ||
                (node.getRootNode && node.getRootNode().host) || null;
            }
          }

          function measure(){
            var out = { height: 0, bars: [], shells: [], panels: [] };
            var panels = [];
            try {
              var vw = window.innerWidth || 0;
              var vh = window.innerHeight || 0;
              if (!vw || !vh) return out;
              var shellMode = inset > 0 && !docScrolls();
              var xs = [vw * 0.5, vw * 0.12, vw * 0.88];
              // The whole strip the toolbar covers, not just its two ends.
              // Both ENDS are needed to begin with: a bar that has been
              // shifted rests on the inset's line, one that has just appeared
              // still rests on the viewport's own edge, and missing the second
              // would mean applying the inset hides the bar from the
              // measurement that justifies it.
              //
              // And ACROSS, because a page's own floating bar often clears the
              // bottom edge by a margin of its own and rests on neither line:
              // duckduckgo.com's "try our browser" toast is `bottom: 15px` and
              // 41px tall, so on an 863px viewport under an 81px toolbar it
              // sits at 806.9-848.2 with the two probes at 861 and 779.8 —
              // eligible (it is inside the strip the toolbar covers, and the
              // rect test says so) and invisible to the measurement that would
              // move it, which is a bar simply left under the toolbar. It was
              // found only while it was sliding IN, which is the frames it
              // happens to cross the bottom edge in, so whether it was lifted
              // at all came down to where the throttle landed.
              // With nothing covering the edge the band is EDGE_BAND_PX, so a
              // bar held just off it is still found and reported.
              var span = Math.max(inset, $EDGE_BAND_PX);
              var ys = [vh - 2];
              for (var yg = vh - 2 - $PROBE_STEP_PX;
                   yg > vh - span - 2 && ys.length < 8;
                   yg -= $PROBE_STEP_PX){
                ys.push(yg);
              }
              ys.push(vh - span - 2);
              for (var yi = 0; yi < ys.length; yi++){
                if (ys[yi] < 0) continue;
                for (var i = 0; i < xs.length; i++){
                  // The whole stack under the point, not just the topmost
                  // element: a full-bleed overlay (a `pointer-events: none`
                  // toast layer, a portal root) is what elementFromPoint
                  // answers, and the bar underneath it is its sibling rather
                  // than its ancestor, so an ancestor walk from the top of the
                  // stack alone never reaches it.
                  var stack = document.elementsFromPoint
                    ? document.elementsFromPoint(xs[i], ys[yi])
                    : [document.elementFromPoint(xs[i], ys[yi])];
                  for (var si = 0; si < stack.length && si < 8; si++){
                    var e = stack[si];
                    // Into shadow roots on the way down, back out through
                    // hosts on the way up — a bar inside a custom element is
                    // still a bar.
                    while (e && e.shadowRoot && e.shadowRoot.elementFromPoint){
                      var inner = e.shadowRoot.elementFromPoint(xs[i], ys[yi]);
                      if (!inner || inner === e) break;
                      e = inner;
                    }
                    // From the TOP of the stack only, like the panels: the
                    // shell is an ancestor of whatever is under the point
                    // whichever entry you start from, and walking all eight
                    // is a few hundred rect reads a frame for one answer.
                    // And from the two ENDS of the strip only, not from every
                    // line across it: a shell is viewport-tall, so the lines
                    // between them can only find it again.
                    if (shellMode && si === 0 &&
                        (yi === 0 || yi === ys.length - 1)){
                      shellsFrom(e, vh, out.shells);
                    }
                    var found = barFrom(e, vw, vh);
                    // Panels only from the TOP of the stack — the thing the
                    // user is actually looking at. Everything under it is
                    // still there and still fixed: amazon.com's location sheet
                    // sits over a scrim over `a-page`, the whole page held
                    // fixed behind the modal, and shortening THAT slides the
                    // page the user came from around behind the sheet.
                    if (si === 0){
                      for (var pi = 0; pi < found.panels.length; pi++){
                        if (panels.indexOf(found.panels[pi]) < 0) {
                          panels.push(found.panels[pi]);
                        }
                      }
                    }
                    if (!found.bar) continue;
                    // Only a bar-shaped thing is REPORTED: the height is what
                    // decides whether the page's inset stops at the system
                    // navigation bar, and a modal isn't a reason to hold that
                    // strip open for a page.
                    if (found.h > out.height) out.height = found.h;
                    if (out.bars.indexOf(found.bar) < 0) out.bars.push(found.bar);
                  }
                }
              }
              // A panel is shortened unless a real BAR was found inside it:
              // the bar is the thing the user acts on, it moves on its own,
              // and shortening the panel around it would move it twice.
              //
              // Which is a question about what the panel CONTAINS, not about
              // whether the page has any bar at all. A modal covers the page
              // it opened over, and that page's own bottom bar is still fixed
              // and still found behind it — invisible to the user, and no
              // reason to leave the modal's action button under the toolbar.
              panels = panels.filter(function(p){
                for (var b = 0; b < out.bars.length; b++){
                  if (p.contains && p.contains(out.bars[b])) return false;
                }
                return true;
              });
              out.panels = panels;
              for (var pj = 0; pj < panels.length; pj++){
                if (out.bars.indexOf(panels[pj]) < 0) out.bars.push(panels[pj]);
              }
              // Anything already shifted that is still down there stays in the
              // set: a sheet dragged open grows past any cap a bar could have,
              // and dropping it back down mid-drag because it got tall is a
              // jump under the user's finger.
              //
              // It is allowed a whole INSET above the resting line, because
              // our own shift is what put it there. A bar is recognised by
              // where it rests, and the bottom-most one keeps its line — it
              // moves from the viewport's edge onto the inset's. But a bar
              // STACKED on another one (auto.ria.com's call/chat/save strip,
              // which sits on top of the site's own nav) rests a whole
              // bar-height above that and answers to NEITHER line once it has
              // been shifted. Measured against the un-lifted test it reads as
              // gone, so it is restored — which drops it back onto the line,
              // where it is found, and shifted again. That is a bar visibly
              // jumping up and down for as long as the toolbar is up, once
              // per throttle.
              //
              // A bar held here is REPORTED like any other, which the loop
              // above cannot do for it: `out.height` is what tells the app a
              // page has a bar of its own, and the app answers that by taking
              // the system navigation bar out of the inset it pushes back —
              // which moves the bar, by exactly that strip, off every line the
              // hit test probes. Reporting only what the probes found made
              // that a LOOP: found at the full inset, reported, inset drops,
              // no longer probed, reported as gone, inset returns, found
              // again — the bar oscillating by the height of the navigation
              // bar for as long as it is up (measured: 24px, every frame, for
              // a second, on duckduckgo.com's toast). What holds a bar in the
              // shifted set has to hold it in the report as well.
              for (var si2 = 0; si2 < shifted.length; si2++){
                var rec2 = shifted[si2];
                var keptH = shownHeight(rec2.el, vw, vh, inset);
                if (keptH <= 0) continue;
                if (out.bars.indexOf(rec2.el) < 0) out.bars.push(rec2.el);
                if (!rec2.panel && keptH <= vh * $MAX_HEIGHT_FRACTION &&
                    keptH > out.height){
                  out.height = keptH;
                }
              }
              out.bars = out.bars.filter(function(el){
                // Panels are kept NESTED. The rule below is about a bar and
                // its own wrapper, where shifting both moves the contents
                // twice as far as the background behind them; two panels that
                // are each fixed to the viewport are two independent boxes
                // that both have to be shortened, and barFrom has already
                // dropped the one case where they are not.
                if (panels.indexOf(el) >= 0) return true;
                for (var j = 0; j < out.bars.length; j++){
                  var other = out.bars[j];
                  if (other !== el && other.contains && other.contains(el)) return false;
                }
                return true;
              });
            } catch (err) { return { height: 0, bars: [], shells: [], panels: [] }; }
            return out;
          }

          function restore(rec){
            for (var i = 0; i < rec.levers.length; i++){
              var l = rec.levers[i];
              try {
                if (l.inline) rec.el.style.setProperty(l.prop, l.inline, l.priority);
                else rec.el.style.removeProperty(l.prop);
              } catch (e) {}
            }
            if (rec.shadowWant){
              try {
                if (rec.shadowInline) rec.el.style.setProperty('box-shadow', rec.shadowInline, rec.shadowPriority);
                else rec.el.style.removeProperty('box-shadow');
              } catch (e) {}
              rec.shadowWant = '';
            }
            if (rec.cap){
              try {
                if (rec.cap.inline) {
                  rec.el.style.setProperty('max-height', rec.cap.inline, rec.cap.priority);
                } else rec.el.style.removeProperty('max-height');
              } catch (e) {}
              rec.cap = null;
            }
          }

          // Records the page's own value for a property before we write over
          // it, so what goes back at the end is the page's and not one of ours.
          function ownValue(rec, l){
            l.inline = rec.el.style.getPropertyValue(l.prop);
            l.priority = rec.el.style.getPropertyPriority(l.prop);
            return l;
          }

          function alphaOf(colour){
            var m = /rgba?\(([^)]*)\)/.exec(colour || '');
            if (!m) return 0;
            var parts = m[1].split(/[\s,\/]+/).filter(Boolean);
            return parts.length >= 4 ? parseFloat(parts[3]) : 1;
          }

          function opaqueOf(colour){
            var m = /rgba?\(([^)]*)\)/.exec(colour || '');
            if (!m) return colour;
            var parts = m[1].split(/[\s,\/]+/).filter(Boolean);
            return 'rgb(' + parts[0] + ', ' + parts[1] + ', ' + parts[2] + ')';
          }

          // The colour the bar actually shows along its bottom row. Its own
          // background when it has one; otherwise the deepest descendant that
          // spans that row and paints a background — a bar whose box is
          // transparent with the colour on an inner element is the common way
          // to build one. When nothing of the bar paints there, the page shows
          // through it, so the page's own canvas colour. Always OPAQUE: the gap
          // is spacing, and a translucent (frosted) bar's colour at its own
          // alpha would show the content scrolling under the gap.
          function fillColour(el){
            var r = el.getBoundingClientRect();
            var y = r.bottom - 1;
            var best = null;
            var own = window.getComputedStyle(el).backgroundColor;
            if (alphaOf(own) > 0.05) best = own;
            var queue = [el], seen = 0;
            while (queue.length && seen < 300){
              var n = queue.shift();
              seen++;
              for (var i = 0; i < n.children.length; i++){
                var c = n.children[i], rr;
                try { rr = c.getBoundingClientRect(); } catch (e) { continue; }
                if (rr.top > y || rr.bottom < y || rr.width < r.width * 0.9) continue;
                var b = window.getComputedStyle(c).backgroundColor;
                if (alphaOf(b) > 0.05) best = b;
                queue.push(c);
              }
            }
            if (!best){
              var html = window.getComputedStyle(document.documentElement).backgroundColor;
              var body = document.body ? window.getComputedStyle(document.body).backgroundColor : '';
              best = alphaOf(body) > 0.05 ? body : (alphaOf(html) > 0.05 ? html : 'rgb(255, 255, 255)');
            }
            return opaqueOf(best);
          }

          // The spacing below a moved bar: a box-shadow in the bar's own
          // background colour, offset down into the gap. A shadow changes no
          // layout, takes no hit tests and moves no bounding rect, so nothing
          // measure() reads can see it. The site's own shadow is kept
          // underneath.
          //
          // Compared against what WE last wrote, never against the live
          // style: the browser re-serialises a box-shadow in its own order,
          // so the live value never matches the string that was set, and the
          // MutationObserver would chase that mismatch forever.
          function writeFill(rec){
            var want = '';
            if (fill > 0 && rec.fill && inset > 0){
              try {
                var bg = fillColour(rec.el);
                var r = rec.el.getBoundingClientRect();
                // The WHOLE of the move, not just the bend: the page lens's
                // curve reads page from past the visible edge too (under a
                // raised toolbar, where the extra is hidden), and a strip only
                // as tall as the visible gap left that page showing at the
                // very bottom.
                var reach = inset;
                var spread = Math.max(0, Math.ceil((reach - r.height) / 2));
                var wide = r.width >= (window.innerWidth || 0) * FILL_WIDTH_FRACTION;
                if (spread === 0 || wide){
                  want = '0 ' + (reach - spread) + 'px 0 ' + spread + 'px ' + bg;
                  if (rec.ownShadow && rec.ownShadow !== 'none') want += ', ' + rec.ownShadow;
                }
              } catch (e) {}
            }
            if (want === (rec.shadowWant || '')) return;
            rec.shadowWant = want;
            try {
              if (want) rec.el.style.setProperty('box-shadow', want, 'important');
              else if (rec.shadowInline) rec.el.style.setProperty('box-shadow', rec.shadowInline, rec.shadowPriority);
              else rec.el.style.removeProperty('box-shadow');
            } catch (e) {}
          }

          function writeLevers(rec){
            for (var i = 0; i < rec.levers.length; i++){
              var lv = rec.levers[i];
              var want = (lv.base + lv.sign * inset) + 'px';
              // Important, or a site's own !important rule outranks the inline
              // style. Only written when it would actually change, so the
              // MutationObserver below doesn't chase our own writes.
              if (rec.el.style.getPropertyValue(lv.prop) !== want){
                try { rec.el.style.setProperty(lv.prop, want, 'important'); } catch (e) {}
              }
            }
          }

          // Holds [rec]'s element's top edge at [top] by capping its HEIGHT,
          // which is what shortening a box means for one that has a height of
          // its own. Measured rather than computed from the viewport: the cap
          // lands on the box's own sizing box, so a `content-box` element's
          // padding and border come off it, and the remaining error — a
          // containing block that is not the viewport, a sub-pixel height —
          // corrects itself on the next pass, since the discrepancy this reads
          // is the one that is actually left.
          function capTo(rec, top){
            var el = rec.el;
            var r = el.getBoundingClientRect();
            var s = window.getComputedStyle(el);
            var extra = 0;
            if (s.boxSizing !== 'border-box'){
              extra = (parseFloat(s.paddingTop) || 0) + (parseFloat(s.paddingBottom) || 0) +
                (parseFloat(s.borderTopWidth) || 0) + (parseFloat(s.borderBottomWidth) || 0);
            }
            var want = Math.round(r.height + (r.top - top) - extra);
            if (!(want >= $MIN_SHELL_HEIGHT_PX)) return;
            // Only ever to bring a top that went ABOVE its line back down —
            // never to grow a box whose top is already at or below it. A
            // panel's top is PageTopInset's to hold too, from the other end:
            // auto.ria.com's brand sheet is centred by `margin: auto`, top0 was
            // read before the status bar inset moved it, and growing it back
            // to top0 lifted it under the bar, which PageTopInset shortened
            // again — the sheet jumped 23px each way every 250ms.
            if (r.top >= top - 0.5 && want > r.height - extra) return;
            if (!rec.cap) rec.cap = ownValue(rec, { prop: 'max-height' });
            var css = want + 'px';
            if (el.style.getPropertyValue('max-height') !== css){
              try { el.style.setProperty('max-height', css, 'important'); } catch (e) {}
            }
          }

          // Holds [rec]'s element's BOTTOM edge on the inset's line by capping
          // its height — capTo's twin for a panel no offset moves. Relative to
          // where the box is now, so a cap already on it corrects itself.
          function capBottom(rec){
            var el = rec.el;
            var r = el.getBoundingClientRect();
            var s = window.getComputedStyle(el);
            var extra = 0;
            if (s.boxSizing !== 'border-box'){
              extra = (parseFloat(s.paddingTop) || 0) + (parseFloat(s.paddingBottom) || 0) +
                (parseFloat(s.borderTopWidth) || 0) + (parseFloat(s.borderBottomWidth) || 0);
            }
            // The VIEWPORT's line, not where the box was first seen: a panel
            // is only ever taken for resting on the bottom edge, and the
            // first sighting can be mid-entrance — duckduckgo.com's viewer,
            // first opened after a reload, read 23px short of the edge and
            // was capped that far above the toolbar, page showing between.
            // A max-height only ever shortens, so a panel that ends higher
            // on its own is untouched.
            var line = (window.innerHeight || 0) - inset;
            // Up: a fraction short of the line shows page between the panel
            // and the toolbar; a fraction past it is under the toolbar.
            var want = Math.ceil(r.height - (r.bottom - line) - extra);
            if (!(want >= $MIN_SHELL_HEIGHT_PX)) return;
            if (!rec.cap) rec.cap = ownValue(rec, { prop: 'max-height' });
            var css = want + 'px';
            if (el.style.getPropertyValue('max-height') !== css){
              try { el.style.setProperty('max-height', css, 'important'); } catch (e) {}
            }
          }

          // What the write was supposed to achieve, checked against the page
          // instead of assumed — see lever() for why none of this can be read
          // off the styles. Three things can be wrong and each has one answer:
          //
          // - **the bottom edge did not move at all**: the offset written is
          //   not what holds this box down (a sticky element resting at the
          //   end of its own flow, a box whose `bottom` is over-constrained
          //   away), so the shift moves to `margin-bottom`;
          // - **the bottom moved and the top did not**: the box is STRETCHED
          //   between a top and a bottom (kontur.systems' sheet, `top: <n>;
          //   bottom: 0` pushed back down by a transform of its own), so the
          //   write SHORTENED it and left its visible peek exactly where it
          //   was. A bar has to slide, so its `top` comes along, which keeps
          //   its height and whatever transform the site is animating it with;
          // - **a panel slid off the top edge**: it is a box with a height of
          //   its own hung from its bottom (auto.ria.com's `.popup-inner`),
          //   so moving the bottom moved the whole thing and the top of the
          //   sheet is now cropped by the viewport. A panel is SHORTENED by
          //   definition — what it holds against its own bottom is what has to
          //   come up, not the box — so its height is capped instead.
          //
          // A bar that slides off the top is left alone: it is bar-shaped, so
          // what is above the screen is a few pixels of a tall sheet's body,
          // and shortening it there would pull the sheet's own content out
          // from under the peek the user is looking at.
          // How long a write into [prop] on [el] will take to LAND, in ms:
          // a site that transitions the property the shift is written into
          // does not move the box on the frame the value is set.
          function settleMs(el, prop){
            try {
              var s = window.getComputedStyle(el);
              var props = (s.transitionProperty || '').split(',');
              var durs = (s.transitionDuration || '0s').split(',');
              var delays = (s.transitionDelay || '0s').split(',');
              var out = 0;
              for (var i = 0; i < props.length; i++){
                var p = props[i].trim();
                if (p !== 'all' && p !== prop) continue;
                var t = secs(durs[i % durs.length]) + Math.max(0, secs(delays[i % delays.length]));
                if (t > out) out = t;
              }
              return out * 1000;
            } catch (e) { return 0; }
          }

          function secs(v){
            v = (v || '').trim();
            var n = parseFloat(v);
            if (!isFinite(n)) return 0;
            return /ms$/.test(v) ? n / 1000 : n;
          }

          function correct(rec){
            var el = rec.el;
            var r = el.getBoundingClientRect();
            // None of the tests below can be read while the write is still on
            // its way. A site that TRANSITIONS the property the shift goes
            // into — duckduckgo.com's "try our browser" toast is
            // `transition: all .25s` on the same box its `bottom: 15px` holds
            // down — moves nothing on the frame the value is set: the box is
            // still at its old place with a transition just started, which
            // reads exactly like a lever that does not hold this box. And the
            // answer to that, restoring the write and moving to
            // `margin-bottom`, is a SECOND transition back down across the
            // first, and a third up again — the toast rises, drops and rises
            // over the top of its own entrance, which is the jump.
            //
            // So while the write is still travelling the correction is
            // DEFERRED, and the rec asks to be looked at again on a later
            // pass. The write itself is left exactly where it is: the site's
            // own transition is what carries it, which is the shift arriving
            // in the same 250ms as the entrance it is part of.
            rec.recheck = false;
            if (rec.bottom0 - r.bottom < 1 && Date.now() < (rec.settleBy || 0)){
              rec.recheck = true;
              return;
            }
            // A PANEL whose bottom did not move is shortened, not given a
            // margin: duckduckgo.com's image viewer is a fixed box with a
            // height of its own under a `top` PageTopInset moved, so `bottom`
            // is over-constrained away and `margin-bottom` moves nothing
            // either — the caption under a tall image stayed under the
            // toolbar. Its height is capped so its bottom lands on the inset.
            if (rec.panel && rec.bottom0 - r.bottom < 1){
              restore(rec);
              rec.levers = [];
              rec.capBottom = true;
              capBottom(rec);
              return;
            }
            if (rec.bottom0 - r.bottom < 1 && rec.levers[0].prop !== 'margin-bottom'){
              restore(rec);
              var m = parseFloat(window.getComputedStyle(el).marginBottom);
              rec.levers = [ownValue(rec, {
                prop: 'margin-bottom', base: isFinite(m) ? m : 0, sign: 1,
              })];
              writeLevers(rec);
              r = el.getBoundingClientRect();
            }
            if (rec.panel){
              if (r.top < -0.5 && rec.top0 >= -0.5) capTo(rec, rec.top0);
              return;
            }
            if (rec.bottom0 - r.bottom > 1 && r.top > rec.top0 - 1){
              // Read AFTER the bottom write: a box that shortened still has
              // the top the site gave it, which is the base to move from.
              var t = parseFloat(window.getComputedStyle(el).top);
              if (isFinite(t)){
                rec.levers.push(ownValue(rec, { prop: 'top', base: t, sign: -1 }));
                writeLevers(rec);
              }
            }
          }

          // [panels] are the members of [bars] that are too big to be bars —
          // they are SHORTENED where a bar is slid, which is the one thing
          // correct() cannot work out for itself.
          function applyShifts(bars, panels){
            for (var i = shifted.length - 1; i >= 0; i--){
              if (inset > 0 && bars.indexOf(shifted[i].el) >= 0) continue;
              restore(shifted[i]);
              shifted.splice(i, 1);
            }
            if (!(inset > 0)) return;
            // Three passes, and they cannot be folded together: every baseline
            // has to be read before the first write lands, or a bar nested in
            // another one is measured against a page that has already moved,
            // and every write has to land before the first correction is read,
            // for the same reason the other way round.
            var fresh = [];
            for (var j = 0; j < bars.length; j++){
              var el = bars[j], rec = null;
              for (var k = 0; k < shifted.length; k++){
                if (shifted[k].el === el) rec = shifted[k];
              }
              if (!rec){
                var r0 = el.getBoundingClientRect();
                rec = {
                  el: el, levers: [], cap: null,
                  panel: (panels || []).indexOf(el) >= 0,
                  top0: r0.top, bottom0: r0.bottom,
                };
                var levers = lever(el);
                for (var m2 = 0; m2 < levers.length; m2++) ownValue(rec, levers[m2]);
                rec.levers = levers;
                // The spacing is only for a bar that rests ON the bottom edge
                // — not a panel, and not a toast the site holds off the edge
                // on purpose, whose gap is the site's own.
                var cs0 = window.getComputedStyle(el);
                rec.fill = !rec.panel && (levers[0].prop === 'bottom'
                  ? Math.abs(levers[0].base) <= 2
                  : ((window.innerHeight || 0) - r0.bottom) <= 6);
                rec.ownShadow = cs0.boxShadow;
                rec.shadowInline = el.style.getPropertyValue('box-shadow');
                rec.shadowPriority = el.style.getPropertyPriority('box-shadow');
                rec.shadowWant = '';
                // Read BEFORE the write, since the transition it asks about
                // is the one that write is about to start.
                rec.settleBy = Date.now() + settleMs(el, levers[0].prop);
                shifted.push(rec);
                fresh.push(rec);
              } else if (rec.cap || rec.capBottom) {
                // A cap holds a box against an inset, so a change of inset
                // leaves it holding the wrong line — and the box has slid
                // again by the time this runs, which is what capTo measures.
                fresh.push(rec);
              } else if (rec.recheck) {
                // A correction deferred while the site's own transition was
                // still carrying our write — see correct().
                fresh.push(rec);
              }
            }
            for (var n = 0; n < shifted.length; n++){
              writeLevers(shifted[n]);
              writeFill(shifted[n]);
            }
            for (var c = 0; c < fresh.length; c++){
              if (fresh[c].capBottom) capBottom(fresh[c]);
              else if (fresh[c].cap) capTo(fresh[c], fresh[c].top0);
              else correct(fresh[c]);
            }
          }

          // Caps every shell found to the height the user can see. The
          // element's own top is remembered rather than re-read, so the cap
          // lands the box's bottom edge exactly on the toolbar's top edge
          // whatever the inset becomes later.
          //
          // Once capped a shell is no longer viewport-tall, so the next
          // measurement cannot find it again — it is held until the reason
          // for capping it goes away (the toolbar hides, the page grows
          // something to scroll, the element leaves the document), and given
          // its max-height back then.
          function applyShellCaps(shells){
            var on = inset > 0 && !docScrolls();
            for (var i = capped.length - 1; i >= 0; i--){
              var rec = capped[i];
              // A cap that CLIPS its box's content was never on a shell: the
              // box only measured viewport-tall while the page was still
              // short. duckduckgo.com's image results sit in a
              // `min-height: 100vh; overflow: hidden` wrapper that grows with
              // the grid; capped during load, it clipped every row after the
              // first screen, which kept the document from scrolling, which
              // kept the cap — the last captions under the toolbar for good.
              // A real shell lays itself out inside the cap. Rejected for the
              // document's life, so it cannot be found and capped again.
              var clips = false;
              if (on && rec.el.isConnected){
                try { clips = rec.el.scrollHeight > rec.el.clientHeight + $SHELL_SLACK_PX; } catch (e) {}
                if (clips) notShells.add(rec.el);
              }
              if (on && rec.el.isConnected && !clips) continue;
              try {
                if (rec.inline) rec.el.style.setProperty('max-height', rec.inline, rec.priority);
                else rec.el.style.removeProperty('max-height');
              } catch (e) {}
              capped.splice(i, 1);
            }
            if (!on) return;
            var vh = window.innerHeight || 0;
            if (!vh) return;
            for (var j = 0; j < shells.length; j++){
              var el = shells[j], r2 = null;
              for (var k = 0; k < capped.length; k++){
                if (capped[k].el === el) r2 = capped[k];
              }
              if (!r2){
                // Read before anything is written, so what goes back is the
                // page's own value rather than one of ours.
                r2 = {
                  el: el,
                  inline: el.style.getPropertyValue('max-height'),
                  priority: el.style.getPropertyPriority('max-height'),
                  top: el.getBoundingClientRect().top,
                };
                capped.push(r2);
              }
              var want = Math.round(vh - inset - r2.top);
              if (want < $MIN_SHELL_HEIGHT_PX) continue;
              var css = want + 'px';
              // Important, and only written when it would actually change —
              // same reasons as the shifts above.
              if (el.style.getPropertyValue('max-height') !== css){
                try { el.style.setProperty('max-height', css, 'important'); } catch (e) {}
              }
            }
          }

          // The layout viewport already includes the strip the toolbar is
          // drawn over, so the document's scroll ENDS with its last content
          // under the bar: at the bottom of a page there is nowhere further
          // to scroll, and pulling back up to reach the footer is what brings
          // the bar back over it. The page is given exactly the inset as extra
          // room at its end, so the end of the content can be scrolled clear
          // of the bar — and it is given back the moment the bar hides, since
          // the strip is the page's own again then.
          //
          // Applied to a capped shell too, and it costs nothing there: the
          // cap frees exactly the inset and the padding fills exactly that,
          // so the document ends where it always did and still doesn't
          // scroll. What it is there for is the shell that does NOT account
          // for everything — content overflowing a `min-height: 100vh`
          // wrapper, which the cap moves nothing of — where it is again the
          // only way to reach the last strip.
          function applyBodyPad(){
            var b = document.body;
            if (!b) return;
            // With the toolbar away and no floor, the page runs under the
            // navigation bar and its end rested under the gesture pill
            // (duckduckgo.com's last row of image captions): the app's end
            // room stands in for the inset then.
            var want = inset > 0 ? inset : (fullscreenElement() ? 0 : endPad);
            if (want === bodyPad) return;
            if (!bodyPad){
              bodyPadInline = b.style.getPropertyValue('padding-bottom');
              bodyPadPriority = b.style.getPropertyPriority('padding-bottom');
              bodyPadBase = parseFloat(window.getComputedStyle(b).paddingBottom) || 0;
            }
            bodyPad = want;
            try {
              if (want) b.style.setProperty('padding-bottom', (bodyPadBase + want) + 'px', 'important');
              else if (bodyPadInline) b.style.setProperty('padding-bottom', bodyPadInline, bodyPadPriority);
              else b.style.removeProperty('padding-bottom');
            } catch (e) {}
          }

          var last = -1;
          var lastBars = [];
          var lastPanels = [];
          var lastShells = [];
          var pending = false;
          var lastRun = 0;
          // What the bottom edge looked like at the previous measurement, and
          // how many measurements in a row have disagreed with the one before.
          // A bar that is MOVING is a bar in the middle of its entrance, and
          // catching it half way up is what makes the shift read as a jerk:
          // the sheet slides, jumps 81px, and slides on. So while the picture
          // keeps changing the next measurement is taken on the next FRAME
          // rather than at the end of the throttle — the shift then lands on
          // the first frame the thing is eligible at all, with a few pixels of
          // it showing, and the rest of its own animation carries it to the
          // right place. The throttle is what a busy page (amazon.com mutates
          // continuously) otherwise holds saturated, which is how a 250ms-late
          // measurement caught a sheet at the middle of its slide.
          var lastShape = '';
          var settling = 0;

          function shapeOf(bars){
            var out = [];
            for (var i = 0; i < bars.length; i++){
              var r = bars[i].getBoundingClientRect();
              out.push(Math.round(r.top) + ':' + Math.round(r.bottom));
            }
            return out.join(',');
          }

          function report(){
            pending = false;
            lastRun = Date.now();
            var m = measure();
            lastBars = m.bars;
            lastPanels = m.panels;
            lastShells = m.shells;
            applyShifts(m.bars, m.panels);
            applyShellCaps(m.shells);
            applyBodyPad();
            // AFTER the shift, so the frame this settles on is the one that
            // already has it: the comparison is against the last measurement's
            // picture, and our own move is part of what has to settle.
            var shape = shapeOf(m.bars);
            settling = (shape === lastShape) ? 0 : settling + 1;
            lastShape = shape;
            keepSettling();
            var h = m.height;
            // Presence flipping matters at any size; a height that only
            // wobbled by a pixel or two doesn't.
            if (last >= 0 && (h > 0) === (last > 0) &&
                Math.abs(h - last) < $REPORT_EPSILON_PX) return;
            last = h;
            try { window.$BRIDGE_NAME.bar(h); } catch (e) {}
          }

          // Nothing else has to fire for the next frame to be measured: a CSS
          // transition produces no events until it ENDS, which is exactly the
          // frames this is here to watch.
          function keepSettling(){
            if (settling > 0 && settling <= $SETTLING_FRAMES) schedule();
          }

          // Called by the MutationObserver (with its records) — see
          // PageTopInset's schedule for why a change the observer saw is
          // answered inside its own microtask, before the frame is painted,
          // and why that is bounded per burst.
          var nowRuns = 0, nowFrameQueued = false, nowSecStart = 0, nowSecRuns = 0;
          function runNowAllowed(){
            var t = Date.now();
            if (t - nowSecStart > 1000){ nowSecStart = t; nowSecRuns = 0; }
            if (nowRuns >= 4 || nowSecRuns >= 24) return false;
            nowRuns++; nowSecRuns++;
            if (!nowFrameQueued && window.requestAnimationFrame){
              nowFrameQueued = true;
              requestAnimationFrame(function(){ nowFrameQueued = false; nowRuns = 0; });
            }
            return true;
          }
          function schedule(records){
            // Whatever is pending (see PageTopInset's schedule): a queued
            // run still happens and only finds nothing left to write.
            if (Array.isArray(records) && runNowAllowed()){
              try { report(); } catch (e) {}
              return;
            }
            if (pending) return;
            pending = true;
            var fast = settling > 0 && settling <= $SETTLING_FRAMES;
            var wait = fast ? 0 : Math.max(0, $THROTTLE_MS - (Date.now() - lastRun));
            setTimeout(function(){
              // rAF so the measurement lands after the frame's own layout,
              // not in the middle of the page building it.
              if (window.requestAnimationFrame) requestAnimationFrame(report);
              else report();
            }, wait);
          }

          // How much of the page's bottom edge the browser is covering, in
          // DEVICE px — the page converts, since only it knows the zoom it is
          // being rendered at. Applied straight through rather than scheduled:
          // this one is the toolbar moving under the user's finger, not the
          // page changing under us.
          window.__bottomBarInset = function(px, endPx){
            appInset = (px || 0) / (window.devicePixelRatio || 1);
            endPad = (endPx || 0) / (window.devicePixelRatio || 1);
            applyInset();
            // applyInset returns early when only the end room changed.
            applyBodyPad();
          };

          // Nothing of ours is over a page in fullscreen. The toolbar is not
          // drawn on that window at all — Chromium's own view is what is on
          // screen — so the strip this whole file exists to account for isn't
          // covering anything, and the honest inset for those frames is zero.
          //
          // It has to be zero rather than merely unused, because the page's
          // fullscreen element is exactly the shape this looks for: `fixed`,
          // the full width, resting on the bottom edge. It is found as a
          // PANEL (too tall to be a bar) and shortened by its own `bottom` —
          // which is a write that outlives the fullscreen it was made in.
          // YouTube's player container comes back to a `top: 48px` inline
          // layout still carrying `bottom: 81px !important` from ours,
          // stretches to fill the space between the two, and the page is a
          // black full-height player from then on. And it never recovers on
          // its own: the shift is what keeps the box resting on the inset's
          // line, which is what keeps it recognised as a panel.
          function fullscreenElement(){
            return document.fullscreenElement ||
              document.webkitFullscreenElement || null;
          }

          function applyInset(){
            var want = fullscreenElement() ? 0 : appInset;
            if (Math.abs(want - inset) < 0.5) return;
            inset = want;
            try {
              document.documentElement.style.setProperty(
                '--browser-bottom-inset', inset + 'px');
            } catch (e) {}
            // The bars already known are moved BEFORE anything is measured
            // again, because a bar is recognised by where it is resting: one
            // still carrying the old inset's margin is resting on neither the
            // viewport's bottom edge nor the new inset's line, and would be
            // measured as having gone away — which is exactly the report that
            // takes the floor under it away too.
            applyShifts(lastBars, lastPanels);
            // And the shells, for the same reason: a cap computed against the
            // old inset holds the shell on the old line, and the retention
            // pass is the only thing that can find a box it has already made
            // too short to be recognised.
            applyShellCaps(lastShells);
            report();
          }

          try {
            // Bars appear and disappear for all of these: the site's own
            // hide-on-scroll, a rotation, a sheet opening, a late hydration.
            var opts = { passive: true, capture: true };
            window.addEventListener('scroll', schedule, opts);
            // Answered NOW, like an observed change: `resize` is dispatched in
            // the frame's own rendering steps, so a measurement inside it
            // lands in the first frame painted at the new size (the keyboard
            // coming up is a resize, and a throttled answer showed a centred
            // sheet at the old cap for a frame, then at its own size).
            window.addEventListener('resize', function(){ schedule([]); }, opts);
            window.addEventListener('orientationchange', schedule, opts);
            window.addEventListener('transitionend', schedule, opts);
            // Both ways: entering gives back every shift before the fullscreen
            // element can be found as one, leaving restores the inset for the
            // page that is on screen again.
            document.addEventListener('fullscreenchange', applyInset, opts);
            document.addEventListener('webkitfullscreenchange', applyInset, opts);
            window.addEventListener('animationend', schedule, opts);
            window.addEventListener('load', schedule, opts);
            document.addEventListener('visibilitychange', schedule, opts);
            if (window.MutationObserver){
              // Throttled by schedule() like everything else, so a chatty
              // page costs one measurement per THROTTLE_MS, not one per
              // mutation record.
              new MutationObserver(schedule).observe(document.documentElement, {
                childList: true, subtree: true,
                attributes: true, attributeFilter: ['style', 'class'],
              });
            }
          } catch (e) {}

          // The inset belongs to the app, not to the document, so a fresh
          // document asks for it rather than waiting to be told — otherwise
          // every navigation would land a page under the toolbar until the
          // next push.
          try { window.__bottomBarInset(window.$BRIDGE_NAME.inset(), window.$BRIDGE_NAME.end()); } catch (e) {}

          schedule();
          // A bar that arrives with late CSS, a framework's first paint, or a
          // consent banner going away has no event of its own to fire.
          setTimeout(schedule, 600);
          setTimeout(schedule, 2000);
        })();
    """.trimIndent()
}
