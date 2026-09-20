package com.yuku.browser.core

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * What the page under the status bar looks like, measured by the page and
 * drawn by the app (`ui/StatusStrip.kt`): the ground at the bar's edge, a
 * header's own paint (0 = none) and the share of that header on screen, and
 * how far through the first strip of scroll the page is. Colours are opaque
 * ARGB as the screen shows them — through the dark mode's filter.
 */
data class StatusStripReport(
    val edge: Int,
    val head: Int,
    val headShare: Float,
    val ramp: Float,
) {
    /** Whether the colour under the status bar's icons is a dark one. */
    val dark: Boolean
        get() {
            val c = if (head != 0 && headShare >= 0.5f) head else edge
            val lum = 0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
            return lum / 255.0 < 0.5
        }
}

/**
 * A strip of the page's TOP edge that the browser is drawing over, given back
 * to the page in the two different ways the page can need it.
 *
 * This is the other half of [PageBottomBar], and it exists because a header
 * that hides on scroll can only be built one of two ways, and only one of them
 * is right.
 *
 * The wrong one is to move the page: lay the WebView out below the header and
 * slide it up as the header goes. It looks correct in a still frame and is
 * wrong in motion — the page is ALREADY moving, by the scroll that is driving
 * the header, so shifting the view as well moves the content twice as far as
 * the finger. That is not a subtle error at 60fps; it reads as the page
 * jumping around under the bar and the bar overlapping content it should be
 * uncovering.
 *
 * The right one is Chromium's: the page never moves at all. The WebView is
 * laid out at full height with the header drawn OVER its top, exactly as the
 * browser's own toolbar is drawn over the page's bottom, and the header's
 * departure simply UNCOVERS page that was always rendered there. Content then
 * moves by exactly the scroll, once, and the header slides off it at the same
 * rate — which is the whole of what "the bar moves with the page" means.
 *
 * What that leaves is the top of the document sitting behind the header, and
 * it is not one problem but two, because a page has two ways of putting
 * something at its top:
 *
 * **The document's own start** gets [CONTENT] — `padding-top` on the body.
 * Everything in normal flow is pushed down by it, and so is anything
 * `position: sticky`, which is laid out in flow until it sticks. This number
 * is CONSTANT for the life of the view: the header is always the same height
 * whether it is on its way out or not, so nothing here ever reflows a page in
 * response to a scroll.
 *
 * **Anything the page FIXES to the viewport** gets [BAR] — its own `top`,
 * plus the inset. Padding cannot reach these: a fixed element is positioned
 * against the viewport, and the viewport is the whole WebView, whose top edge
 * is behind the header. Without this a site's own navigation bar is invisible
 * while our header is up and appears from behind it on the way out. Chromium
 * has no equivalent problem because its visual viewport itself starts below
 * its controls; that is renderer-internal, so the offsets are moved by hand
 * here, exactly as [PageBottomBar] moves the ones resting on the other edge.
 *
 * **[BAR] is two-state — the full height while the header is all the way up,
 * zero the instant it starts to leave — and the timing of that is the whole
 * reason it does not read as a jump.** A viewport-anchored bar ought to track
 * the header frame by frame, which from outside the renderer would be one
 * script evaluation per frame per page. It does not have to: at BOTH moments
 * the value flips, the header is covering the strip the site's bar is moving
 * across, so the move happens behind it. Standing down happens on the first
 * frame of the slide, when the header still covers everything above it, and
 * coming back happens once the header is fully up and covering it again. The
 * only case that shows is a site bar taller than our header, where the part
 * hanging below it visibly steps.
 *
 * **Two more kinds of box are moved where the inset is CONSTANT** — which is
 * the page lens's overscan and nothing else (see `steady`), because both moves
 * are in plain sight and the flip above is only invisible for what the header
 * itself covers:
 *
 * - **A bar STACKED on another one**, resting at its own `top` rather than on
 *   either line: a second-tier nav, YouTube's filter chips under its masthead.
 *   Nothing moved it, so the bar above it came off the covered strip and this
 *   one stayed in it, half of it hidden behind the bar it sits under.
 * - **A PANEL** — a drawer, a lightbox, a consent wall, an app's own
 *   full-screen layer — which is too big to shove down the screen and is
 *   SHORTENED from the top instead (`capPanel`), its own bottom left where it
 *   was. Leaving it alone is what put a full-screen overlay's title row and
 *   close button in the strip the browser covers, behind the lens's bezel and
 *   bent into its curve, reachable only through the last few pixels of it.
 *   This is [PageBottomBar]'s panel rule from the other end.
 */
object PageTopInset {

    private const val BRIDGE_NAME = "__topInsetBridge"

    /**
     * Wires [web] up to read the current insets as soon as each of its
     * documents starts. Document-start, so the room is there before the first
     * paint rather than appearing under content already laid out without it.
     */
    fun attach(
        web: WebView,
        contentPx: () -> Int,
        barPx: () -> Int,
        fillPx: () -> Int = { 0 },
        stripPx: () -> Int = { 0 },
        stripFadePx: () -> Int = { 0 },
        stripBlur: () -> Boolean = { true },
        onStrip: (StatusStripReport?) -> Unit = {},
    ) {
        web.addJavascriptInterface(
            Bridge(contentPx, barPx, fillPx, stripPx, stripFadePx, stripBlur, onStrip),
            BRIDGE_NAME,
        )
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    /**
     * Both insets, in device pixels — the page converts to CSS pixels itself,
     * since only it knows the zoom it is being rendered at.
     *
     * Call on every change: a document that hasn't started yet reads the same
     * values through the bridge instead, so the two paths can't disagree.
     */
    fun setInset(web: WebView, contentPx: Int, barPx: Int) {
        web.evaluateJavascript(
            "window.__topInset && window.__topInset($contentPx, $barPx)",
            null,
        )
    }

    /**
     * Nonzero to paint each moved bar's whole move — and the room given the
     * document's start — in the colour that bar (or that start) shows; 0 for
     * none. The page lens (`ui/PageLens.kt`) moves things only off its
     * overscan, hidden under the status bar, but its curve reads page out of
     * that strip: unpainted, whatever scrolls behind a header was pulled into
     * the curve above it.
     */
    fun setFill(web: WebView, px: Int) {
        web.evaluateJavascript("window.__topInsetFill && window.__topInsetFill($px)", null)
    }

    /**
     * The STATUS BAR STRIP: [px] of page the status bar sits over, plus [fadePx]
     * below its edge. The browser lays the page out under the status bar
     * (the document is padded down by the same amount, so nothing starts
     * there), and what scrolls up under it is veiled IN the page by a fixed
     * element of ours:
     *
     * - an ordinary page gets the canvas colour, solid under the icons and
     *   fading out across the bar's edge — faded in by the scroll, so a page
     *   at its top shows its own start (painted up by the start fill) with
     *   nothing laid over its first rows;
     * - a page with a header resting on the edge gets that header's colour,
     *   solid, and no fade — the header is the edge.
     *
     * In the page rather than drawn by the app, because everything the page
     * is put through then takes the strip with it for free: the dark filter
     * (a colour read in Kotlin would be the un-inverted one), every capture,
     * and the shrink into a card. 0 turns it off.
     */
    fun setStrip(web: WebView, px: Int, fadePx: Int, blur: Boolean = true) {
        // Native strip rendering cannot be hidden by page stacking contexts.
        val cssBlur = blur && android.os.Build.VERSION.SDK_INT < 31
        web.evaluateJavascript("window.__topInsetStrip && window.__topInsetStrip($px, $fadePx, $cssBlur)", null)
    }

    class Bridge internal constructor(
        private val contentPx: () -> Int,
        private val barPx: () -> Int,
        private val fillPx: () -> Int,
        private val stripPx: () -> Int,
        private val stripFadePx: () -> Int,
        private val stripBlur: () -> Boolean,
        private val onStrip: (StatusStripReport?) -> Unit,
    ) {
        private val main = android.os.Handler(android.os.Looper.getMainLooper())

        /** Device pixels of status bar strip; see [setStrip]. */
        @JavascriptInterface
        fun strip(): Int = stripPx()

        /** Device pixels the strip fades out over, below the bar's edge. */
        @JavascriptInterface
        fun stripFade(): Int = stripFadePx()

        @JavascriptInterface
        fun stripBlur(): Boolean = stripBlur.invoke() && android.os.Build.VERSION.SDK_INT < 31

        /**
         * The strip's measurement (see [StatusStripReport]): colours as ARGB
         * the way the screen shows them, the header's share and the scroll
         * ramp in fiftieths. A negative share is "no strip" (off, or the page
         * is in fullscreen). Reported on change only.
         */
        @JavascriptInterface
        fun stripState(edge: Int, head: Int, share: Int, ramp: Int) {
            val report = if (share < 0) null else StatusStripReport(
                edge = edge,
                head = head,
                headShare = (share / 50f).coerceIn(0f, 1f),
                ramp = (ramp / 50f).coerceIn(0f, 1f),
            )
            main.post { onStrip(report) }
        }

        /** Device pixels of spacing painted above a moved bar; see [setFill]. */
        @JavascriptInterface
        fun fill(): Int = fillPx()

        /** Device pixels of room the document's start is given. */
        @JavascriptInterface
        fun content(): Int = contentPx()

        /** Device pixels anything fixed to the viewport's top is moved by. */
        @JavascriptInterface
        fun bar(): Int = barPx()
    }

    /**
     * Runs in the top document of every page — it is registered for every
     * frame, and stands itself down in the rest (see the guard below). Its
     * measurement reads layout and never writes any, so it can't reflow the
     * page it's measuring; the shifts it then applies are guarded against
     * writing a value that is already there, or its own MutationObserver
     * would keep it measuring forever. Everything it touches is wrapped,
     * since a page that throws inside our code is still a page that has to
     * work.
     */
    private val SCRIPT = """
        (function(){
          // THE TOP DOCUMENT ONLY, as PageBottomBar. What the browser covers
          // is the top document's top edge; an iframe is somewhere down the
          // page, nothing of ours is over it, and it was being given the
          // inset anyway — every embed and ad frame under the page lens came
          // up with a band of padding across its top, painted in its first
          // element's colour, and its own fixed bars pushed down by it.
          try { if (window.top !== window) return; } catch (e) { return; }
          if (window.__topInset) return;

          // CSS pixels. `content` is the document's own room and never
          // changes; `bar` is what viewport-fixed bars are moved by and is
          // either the header's height or nothing.
          var content = 0;
          var bar = 0;
          // CSS pixels of spacing painted between a moved bar and the edge
          // it was moved off, in the bar's own background colour, or 0.
          var fill = 0;
          try { fill = (__topInsetBridge.fill() || 0) / (window.devicePixelRatio || 1); } catch (e) {}
          // The spacing is a box-shadow; a bar shorter than the spacing needs
          // it SPREAD, which widens it past the bar's sides, so only a bar
          // this nearly as wide as the viewport may have that.
          var FILL_WIDTH_FRACTION = 0.97;

          // A bar spans the screen and is short. The width test is what keeps
          // a floating button or a toast out of it, and the height test is
          // what keeps a full-screen fixed overlay — a modal, a menu, a
          // consent wall — from being shoved down the screen.
          var MIN_WIDTH_FRACTION = 0.6;
          var MAX_HEIGHT_FRACTION = 0.35;
          // A panel that does not span the viewport is ordinarily a dialog or
          // a floating card, whose vertical position belongs to the site. A
          // side drawer is the exception: it is anchored to an edge and its
          // whole top row is just as covered by the status bar as a header.
          // It must take the same top inset and lose that room from its
          // height, or its first menu items (and its close affordance) are
          // unreachable behind the system bar.
          var PANEL_WIDTH_FRACTION = 0.95;
          var MIN_HEIGHT_PX = 12;
          // A bar is recognised by RESTING on the top edge, and once it has
          // been moved it rests on the inset's line instead — so both lines
          // are probed, with a few pixels of slack for a border or a shadow.
          var TOP_SLACK_PX = 6;
          var THROTTLE_MS = 250;
          // A ceiling on the scan, so a page that builds tens of thousands of
          // nodes cannot turn a measurement into a frame drop. Anything at
          // the top edge of a document is near the start of it.
          var MAX_SCAN = 6000;

          // A panel shortened below this is not worth having — the same floor
          // the shell caps keep.
          var MIN_PANEL_HEIGHT_PX = 80;

          var padEl = null, pad = 0, padInline = null, padPriority = null, padBase = 0;
          var shifted = [];

          // Whether the inset is CONSTANT rather than flipping with a header's
          // slide. The fill is only ever set by the page lens
          // (BrowserViewModel.setPageBarFill), whose inset is its overscan and
          // does not move while a page is on screen; the overlay's header is
          // the other caller of setInset, and its two-state flip is invisible
          // only for what the header itself covers — which is why the two
          // kinds of move below are taken here and nowhere else.
          // The status bar strip is constant too (no fill of its own: a fill
          // is paint in the page, and the page is what a preview captures).
          function steady(){ return fill > 0 || strip > 0; }

          // Nothing of ours is over a page in fullscreen: Chromium's own view
          // is what is on screen, so the honest inset for those frames is
          // zero. It has to be given back rather than merely unused, because
          // the fullscreen element is exactly the shape a panel is — `fixed`,
          // full width, resting on the top edge — and a `top` of ours would
          // outlive the fullscreen it was written in.
          function fullscreenElement(){
            return document.fullscreenElement ||
              document.webkitFullscreenElement || null;
          }

          function held(el){
            for (var i = 0; i < shifted.length; i++) if (shifted[i].el === el) return i;
            return -1;
          }

          // What the browser covers is also what a scroll INTO VIEW has to
          // clear: an in-page `#anchor`, a focused field, `scrollIntoView`
          // all park their target at the viewport's top, i.e. under the
          // status bar. Added to the site's own scroll padding (which covers
          // its header), never replacing it.
          var spWritten = -1, spInline = '', spPriority = '', spBase = 0;
          function applyScrollPad(want){
            var de = document.documentElement;
            if (!de || want === spWritten) return;
            try {
              if (spWritten <= 0 && want > 0){
                spInline = de.style.getPropertyValue('scroll-padding-top');
                spPriority = de.style.getPropertyPriority('scroll-padding-top');
                spBase = parseFloat(window.getComputedStyle(de).scrollPaddingTop) || 0;
              }
              spWritten = want;
              if (want > 0) de.style.setProperty('scroll-padding-top', (spBase + want) + 'px', 'important');
              else if (spInline) de.style.setProperty('scroll-padding-top', spInline, spPriority);
              else de.style.removeProperty('scroll-padding-top');
            } catch (e) {}
          }

          // ---- the document's own start -------------------------------
          function applyPad(){
            var b = document.body;
            if (!b) return;
            // A framework that swaps the whole body out takes the padding
            // with it; the new one is a different element, so the site's own
            // value is read again from it.
            if (b !== padEl){ padEl = b; pad = 0; }
            applyScrollPad(content > 0 ? content : 0);
            var want = content > 0 ? content + (startHold ? startHold.height : 0) : 0;
            if (want === pad) return;
            if (!pad){
              padInline = b.style.getPropertyValue('padding-top');
              padPriority = b.style.getPropertyPriority('padding-top');
              padBase = parseFloat(window.getComputedStyle(b).paddingTop) || 0;
            }
            pad = want;
            // Published for PageBottomBar: this room is what makes a `100dvh`
            // shell overflow, and a shell that reads as scrollable is never
            // capped (see docScrolls there).
            window.__topInsetPad = want;
            try {
              // Important, or a site's own !important rule outranks the
              // inline style. Added to what the site asked for, never
              // replacing it.
              if (want) b.style.setProperty('padding-top', (padBase + want) + 'px', 'important');
              else if (padInline) b.style.setProperty('padding-top', padInline, padPriority);
              else b.style.removeProperty('padding-top');
            } catch (e) {}
          }

          // ---- a masthead that leaves the flow -------------------------
          // keddr.com's mobile header is in flow at the document's start and
          // turns `fixed` once the scroll passes its own height — with no
          // placeholder, so its room collapses and everything under it jumps
          // up by that height (and back down on the way up). Written for a
          // header starting at 0, the switch lands while ours is still on
          // screen under the padding, where the collapse is plainly seen and
          // Chromium's scroll anchoring does not step in. So the room is HELD:
          // the element's height goes into the padding for as long as it is
          // out of the flow, in the same microtask as the site's class change,
          // and the content never moves. Only for a room that actually
          // collapsed (its parent shrank by it) — a site that leaves a
          // placeholder of its own gets nothing added.
          var startHold = null, startCands = [];
          function updateStartHold(){
            var b = document.body;
            if (!b || !(content > 0)){ startHold = null; startCands = []; return; }
            if (startHold){
              var el = startHold.el, gone = !el.isConnected;
              if (!gone){
                try {
                  var p = window.getComputedStyle(el).position;
                  gone = p !== 'fixed' && p !== 'absolute';
                } catch (e) { gone = true; }
              }
              if (gone) startHold = null;
              return;
            }
            for (var i = 0; i < startCands.length; i++){
              var c = startCands[i];
              if (!c.el.isConnected || !c.el.parentElement) continue;
              var pos;
              try { pos = window.getComputedStyle(c.el).position; } catch (e) { continue; }
              if (pos !== 'fixed' && pos !== 'absolute') continue;
              var ph = c.el.parentElement.getBoundingClientRect().height;
              if (Math.abs((c.parentH - ph) - c.height) <= 3){
                startHold = { el: c.el, height: c.height };
                startCands = [];
                return;
              }
            }
            // Who the document starts with now: the chain of short, full-width,
            // in-flow boxes across its first line, outermost first.
            var vw = window.innerWidth || 0, vh = window.innerHeight || 0;
            var y, cands = [];
            try {
              y = b.getBoundingClientRect().top + (parseFloat(window.getComputedStyle(b).paddingTop) || 0) + 1;
            } catch (e) { return; }
            var n = b, depth = 0;
            while (n && depth < 12){
              var next = null;
              for (var k = 0; k < n.children.length; k++){
                var ch = n.children[k], r, s;
                try { r = ch.getBoundingClientRect(); s = window.getComputedStyle(ch); } catch (e) { continue; }
                if (r.top > y || r.bottom < y || r.width < vw * FILL_WIDTH_FRACTION) continue;
                if (s.position === 'fixed' || s.position === 'absolute' || s.display === 'none') continue;
                if (r.height > 0 && r.height <= vh * MAX_HEIGHT_FRACTION && Math.abs(r.top - (y - 1)) <= 3){
                  cands.push({ el: ch, height: r.height, parentH: n.getBoundingClientRect().height });
                }
                next = ch;
                break;
              }
              n = next;
              depth++;
            }
            startCands = cands;
          }

          // A panel is SHORTENED, not shoved down the screen: its top comes
          // off the strip the browser covers and its own bottom stays where it
          // was, so what it holds against that bottom stays clear of the
          // toolbar — and PageBottomBar, which shortens panels from the other
          // end, has nothing to undo. Measured rather than computed: the write
          // moves the top only where `top` is what holds the box, and a box
          // stretched between a top and a bottom shortens by itself.
          function capPanel(rec){
            var el = rec.el, r;
            try { r = el.getBoundingClientRect(); } catch (e) { return; }
            // A cap only ever SHORTENS, so one taken while there was less room
            // outlives the reason: with the keyboard coming up, auto.ria's
            // sheet was capped while PageBottomBar's toolbar `bottom` was still
            // on it, the `bottom` then came off, and the sheet sat centred
            // 28px below the line with a gap over the keyboard — for good,
            // since nothing about the viewport changed again. A capped panel
            // resting BELOW the line is given its own max-height back and
            // shortened again from there; the new cap is written in the same
            // task, so the frame never shows the uncapped box.
            // "Below the line" is below where it is MEANT to rest: a panel held
            // by its own `top` rests at that top plus the inset (auto.ria's at
            // 64), and measured against the bare inset it would read as
            // stuck on every pass and be uncapped and recapped forever.
            var restLine = bar;
            if (rec.levers.length && rec.levers[0].p === 'top') {
              restLine = Math.max(bar, rec.levers[0].base + bar);
            }
            if (rec.cap && r.top > restLine + 1 &&
                /^\d+px$/.test(el.style.getPropertyValue('max-height'))){
              try {
                if (rec.cap.inline) el.style.setProperty('max-height', rec.cap.inline, rec.cap.priority);
                else el.style.removeProperty('max-height');
                r = el.getBoundingClientRect();
              } catch (e) { return; }
            }
            // Moved down past its own bottom, or still over the strip: a box
            // with `margin: auto` between a `top` and a `bottom` is CENTRED in
            // what is left, so a height that does not fit spills out of both
            // ends and the `top` written does not hold it. auto.ria.com's
            // filter sheet is `fixed; margin: auto; height: 100%` with no top
            // of its own — Chromium reports its resolved 16px as `top` — and
            // with the inset and PageBottomBar's `bottom` both written it came
            // to rest 25px ABOVE the line, title row under the status bar.
            if (r.bottom - rec.bottom0 <= 1 && r.top >= bar - 1) return;
            var s, extra = 0;
            try { s = window.getComputedStyle(el); } catch (e) { return; }
            if (s.boxSizing !== 'border-box'){
              extra = (parseFloat(s.paddingTop) || 0) + (parseFloat(s.paddingBottom) || 0) +
                (parseFloat(s.borderTopWidth) || 0) + (parseFloat(s.borderBottomWidth) || 0);
            }
            if (!rec.cap){
              rec.cap = {
                inline: el.style.getPropertyValue('max-height'),
                priority: el.style.getPropertyPriority('max-height'),
              };
            }
            // Shortened until both ends are inside: how far the top moves per
            // px taken off is 1 for a box held by its top or bottom and 1/2
            // for a centred one, so it is measured from the step before and
            // the next step sized by it — two or three layouts, not a search.
            var perPx = 1, prevOver = -1, prevCut = 0;
            for (var k = 0; k < 6; k++){
              var over = Math.max(0, bar - r.top);
              var under = Math.max(0, r.bottom - rec.bottom0);
              if (over <= 0.5 && under <= 1) break;
              if (prevOver > 0 && prevCut > 0){
                var moved = (prevOver - over) / prevCut;
                if (moved > 0.05) perPx = moved;
              }
              var cut = over > 0.5 ? over / perPx : under;
              // UP, never to nearest: a `100dvh` viewport is fractional
              // (911.238px here), and a panel rounded down stopped a quarter of
              // a CSS px short of the screen's edge — a hairline of the white
              // body under duckduckgo.com's dark image viewer.
              var want = Math.ceil(r.height - cut - extra);
              if (!(want >= MIN_PANEL_HEIGHT_PX)) return;
              var css = want + 'px';
              if (el.style.getPropertyValue('max-height') === css) break;
              try { el.style.setProperty('max-height', css, 'important'); } catch (e) { return; }
              prevOver = over; prevCut = cut;
              try { r = el.getBoundingClientRect(); } catch (e) { return; }
            }
          }

          // ---- anything fixed to the viewport's top -------------------
          // The site's own values are read with its transitions OFF for the
          // instant of the read. A property mid-transition reads as the frame
          // it is on, not the value it is going to: kontur.systems animates
          // `top` over 0.3s, so its buttons handed their value back and were
          // read a third of the way down, and the move built on that sat them
          // on the counters bar they were stepping out from under. Cancelling
          // the transition lands the value at its end for the read; the site's
          // own transition is given back straight after.
          // Only when a lever property is itself transitioning: `transition:
          // none` cancels EVERY running transition on the element, and
          // keddr.com's header changes class on each change of scroll
          // direction while sliding by a transform — cancelled, the slide
          // would jump.
          function record(el, cs, vh){
            var needs = false;
            try {
              needs = /(^|,)\s*(all|top|margin|margin-top)\s*(,|$)/.test(cs.transitionProperty || '');
            } catch (e) {}
            if (!needs) return recordNow(el, cs, vh);
            var inl = '', pri = '';
            try {
              inl = el.style.getPropertyValue('transition');
              pri = el.style.getPropertyPriority('transition');
              el.style.setProperty('transition', 'none', 'important');
            } catch (e) {}
            try {
              return recordNow(el, window.getComputedStyle(el), vh);
            } finally {
              try {
                if (inl) el.style.setProperty('transition', inl, pri);
                else el.style.removeProperty('transition');
              } catch (e) {}
            }
          }

          // A transform's vertical translation in CSS px, or null when it
          // cannot be read (a percentage is resolved against the box).
          function translateYOf(el, t){
            if (!t || t === 'none') return 0;
            var m = /^matrix\(([^)]*)\)/.exec(t);
            if (m){ var p = m[1].split(','); return parseFloat(p[5]) || 0; }
            m = /^matrix3d\(([^)]*)\)/.exec(t);
            if (m){ var q = m[1].split(','); return parseFloat(q[13]) || 0; }
            m = /^translate(Y|3d)?\(([^)]*)\)/.exec(t);
            if (m){
              var args = m[2].split(',');
              var v = (m[1] === 'Y' ? args[0] : args[1] || '0').trim();
              var n = parseFloat(v);
              if (isNaN(n)) return null;
              return /%$/.test(v) ? n / 100 * (el.offsetHeight || 0) : n;
            }
            return null;
          }

          // How far a running transform transition or animation will still
          // move the box. A header that hides by a transform (keddr.com's
          // `.scrollup`/`.scrolldown`, 0.3s) changes its class on the frame
          // the slide STARTS, so the re-base reads it off screen on its way
          // in — judged hidden, never moved, and it landed under the status
          // bar — or on screen on its way out, moved down, and came to rest
          // peeking out under the bar. Judged by where it is GOING instead.
          function settledShift(el, cs){
            var now = translateYOf(el, cs.transform);
            var end = null;
            try {
              var anims = el.getAnimations ? el.getAnimations() : [];
              for (var i = 0; i < anims.length; i++){
                if (anims[i].playState !== 'running' || !anims[i].effect) continue;
                var kf = anims[i].effect.getKeyframes();
                var last = kf.length ? kf[kf.length - 1].transform : null;
                if (last == null) continue;
                var y = translateYOf(el, last);
                if (y !== null) end = y;
              }
            } catch (e) {}
            return now === null || end === null ? 0 : end - now;
          }

          function recordNow(el, cs, vh){
            var levers = [];
            var top = parseFloat(cs.top);
            if (cs.top !== 'auto' && !isNaN(top)){
              levers.push({ p: 'top', base: top });
            } else {
              // No `top` of its own: it is where the flow left it, and the
              // only lever that moves it is its margin. (A sticky element
              // with no `top` never sticks to the top edge at all, so it is
              // rejected before this.)
              levers.push({ p: 'margin-top', base: parseFloat(cs.marginTop) || 0 });
            }
            for (var i = 0; i < levers.length; i++){
              levers[i].inline = el.style.getPropertyValue(levers[i].p);
              levers[i].priority = el.style.getPropertyPriority(levers[i].p);
            }
            // Resting ON the edge by its own offset — a sticky bar's `top: 0`
            // counts even while it is still in flow further down (the body's
            // padding holds it there until it sticks, and judged by where it
            // sat then it never got its spacing) — or, with no offset of its
            // own, sitting on the edge or on the inset's line.
            var onEdge = false;
            var rect = null;
            try {
              rect = el.getBoundingClientRect();
              onEdge = levers[0].p === 'top'
                ? Math.abs(levers[0].base) <= 2
                : rect.top <= bar + TOP_SLACK_PX;
            } catch (e) {}
            var shown = rect
              ? Math.min(rect.bottom, vh || 0) - Math.max(rect.top, 0)
              : 0;
            return {
              el: el, levers: levers,
              // The class the site's own values were read under; see the
              // re-base in measure().
              cls: el.getAttribute('class') || '',
              // Too big to be a bar — see candidates().
              panel: shown > (vh || 0) * MAX_HEIGHT_FRACTION,
              // Put wholly ABOVE the viewport by the site's own values: a
              // header it has hidden. YouTube's masthead takes `.out` on the
              // way down, which is `top: -48px` — exactly its height — and
              // with the inset added on top it came to rest at 0, i.e. in
              // the status bar strip, logo and search button under the
              // clock. A bar the site has sent off screen stays off it.
              // And a box whose own `top` is minus its height or less, which
              // is hidden wherever it sticks: androidauthority.com's header is
              // `sticky; top: -2.5rem` at 2.5rem tall, in flow and on screen at
              // scroll 0 (so the rect test passes it), and with the strip added
              // it stuck at `top: 8px` under the status bar instead of
              // scrolling away.
              hidden: rect ? (rect.bottom + settledShift(el, cs) <= 1 ||
                (levers[0].p === 'top' && levers[0].base < 0 &&
                 levers[0].base + rect.height <= 1)) : false,
              // Hidden by its own offset, which no slide changes.
              offTop: !!rect && levers[0].p === 'top' && levers[0].base < 0 &&
                levers[0].base + rect.height <= 1,
              // Where its own bottom was before anything of ours moved it,
              // which is the line capPanel holds it to.
              bottom0: rect ? rect.bottom : 0,
              cap: null,
              // What bottom0 and the cap were measured under; see measure().
              // The inline `bottom` is PageBottomBar's toolbar shift: a
              // bottom0 read while it was on is a line 57px up the screen
              // once it comes off (the keyboard rising takes it away).
              vh0: vh || 0,
              bar0: bar,
              bottomStyle0: el.style.getPropertyValue('bottom'),
              // The spacing is only for a bar resting ON the edge: one
              // sitting under another bar would paint over that bar.
              fill: onEdge,
              ownShadow: cs.boxShadow,
              shadowInline: el.style.getPropertyValue('box-shadow'),
              shadowPriority: el.style.getPropertyPriority('box-shadow'),
              shadowWant: '',
            };
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

          // The colour the bar actually shows along its top row. Its own
          // background when it has one; otherwise the deepest descendant that
          // spans that row and paints a background — a bar whose box is
          // transparent with the colour on an inner element is the common way
          // to build one. When nothing of the bar paints there, the page shows
          // through it, so the page's own canvas colour. Always OPAQUE: the gap
          // is spacing, and a translucent (frosted) header's colour at its own
          // alpha would show the content scrolling under the gap.
          function fillColour(el){
            var r = el.getBoundingClientRect();
            var y = r.top + 1;
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

          // The spacing above a moved bar: a box-shadow in the bar's own
          // background colour, offset up into the gap. A shadow changes no
          // layout and takes no hit tests, so nothing that measures bars sees
          // it. The site's own shadow is kept underneath.
          //
          // Compared against what WE last wrote, never against the live
          // style: the browser re-serialises a box-shadow in its own order,
          // so the live value never matches the string that was set, and the
          // MutationObserver would chase that mismatch forever.
          function writeFill(rec){
            var want = '';
            // Not for a PANEL under the status bar strip: the strip already
            // covers that ground, and a panel is as often a translucent scrim
            // (keddr.com's menu mask, black at 0.8) whose colour made opaque
            // is a solid black band showing through the veil.
            if (fill > 0 && rec.fill && !(strip > 0 && rec.panel)){
              try {
                var bg = fillColour(rec.el);
                var r = rec.el.getBoundingClientRect();
                // The WHOLE of the move, not just the bend: the page lens's
                // curve reads page from past the visible edge too (its
                // overscan, under the status bar), and a strip only as tall as
                // the visible gap left that page showing at the very top.
                var reach = bar;
                var spread = Math.max(0, Math.ceil((reach - r.height) / 2));
                var wide = r.width >= (window.innerWidth || 0) * FILL_WIDTH_FRACTION;
                if (spread === 0 || wide){
                  want = '0 ' + (-(reach - spread)) + 'px 0 ' + spread + 'px ' + bg;
                  if (rec.ownShadow && rec.ownShadow !== 'none') want += ', ' + rec.ownShadow;
                }
              } catch (e) {}
            }
            if (want === rec.shadowWant) return;
            rec.shadowWant = want;
            try {
              if (want) rec.el.style.setProperty('box-shadow', want, 'important');
              else if (rec.shadowInline) rec.el.style.setProperty('box-shadow', rec.shadowInline, rec.shadowPriority);
              else rec.el.style.removeProperty('box-shadow');
            } catch (e) {}
          }

          function write(rec){
            // The site's own values stand (restore() already handed them
            // back when the re-base that found it hidden ran).
            for (var i = 0; !rec.hidden && i < rec.levers.length; i++){
              var lv = rec.levers[i];
              var want = (lv.base + bar) + 'px';
              // Only written when it would actually change, or the
              // MutationObserver below chases our own writes forever.
              if (rec.el.style.getPropertyValue(lv.p) !== want){
                try { rec.el.style.setProperty(lv.p, want, 'important'); } catch (e) {}
              }
            }
            writeFill(rec);
          }

          function restore(rec){
            try { if (panelSizes) panelSizes.unobserve(rec.el); } catch (e) {}
            for (var i = 0; i < rec.levers.length; i++){
              var lv = rec.levers[i];
              try {
                if (lv.inline) rec.el.style.setProperty(lv.p, lv.inline, lv.priority);
                else rec.el.style.removeProperty(lv.p);
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

          function releaseAll(){
            for (var i = 0; i < shifted.length; i++) restore(shifted[i]);
            shifted = [];
          }

          // ---- the colour at the document's start ---------------------
          // The room [content] gives the document's start shows the page's own
          // background, and that is only the right colour when the page starts
          // on it: github.com starts with a black in-flow header on a white
          // body, so the room read as a white band over the header. The element
          // the document STARTS with, if it paints, carries its colour up
          // through that room — the same box-shadow spacing a moved bar gets,
          // on an element that scrolls away with the page.
          var startRec = null;

          // The deepest element that begins exactly at the document's start,
          // spans the page and paints a background: a transparent wrapper
          // chain around a painted header is the usual shape (github.com's is
          // five deep). Fixed and absolute boxes are not the document's start
          // — github.com's full-screen menu backdrop is fixed over it.
          function startTarget(){
            var b = document.body;
            if (!b) return null;
            var vw = window.innerWidth || 0;
            var y;
            try {
              y = b.getBoundingClientRect().top + (parseFloat(window.getComputedStyle(b).paddingTop) || 0) + 1;
            } catch (e) { return null; }
            var best = null, queue = [b], seen = 0;
            while (queue.length && seen < 400){
              var n = queue.shift();
              seen++;
              for (var i = 0; i < n.children.length; i++){
                var c = n.children[i], r;
                try { r = c.getBoundingClientRect(); } catch (e) { continue; }
                if (r.top > y || r.bottom < y || r.width < vw * FILL_WIDTH_FRACTION) continue;
                var s = window.getComputedStyle(c);
                if (s.position === 'fixed' || s.position === 'absolute') continue;
                if (s.display === 'none' || s.visibility === 'hidden') continue;
                if (r.top >= y - 3 && alphaOf(s.backgroundColor) > 0.05) best = c;
                queue.push(c);
              }
            }
            return best;
          }

          function applyStartFill(){
            var el = (fill > 0 && content > 0) ? startTarget() : null;
            // A bar already being moved has spacing of its own.
            if (el && held(el) >= 0) el = null;
            if (startRec && startRec.el !== el){
              try {
                if (startRec.inline) startRec.el.style.setProperty('box-shadow', startRec.inline, startRec.priority);
                else startRec.el.style.removeProperty('box-shadow');
              } catch (e) {}
              startRec = null;
            }
            if (!el) return;
            if (!startRec){
              // The site's own shadow is read BEFORE anything of ours lands on
              // the element, or it would be compounded into every write.
              startRec = {
                el: el,
                inline: el.style.getPropertyValue('box-shadow'),
                priority: el.style.getPropertyPriority('box-shadow'),
                own: window.getComputedStyle(el).boxShadow,
                written: '',
              };
            }
            var want;
            try {
              var s = window.getComputedStyle(el), r = el.getBoundingClientRect();
              var spread = Math.max(0, Math.ceil((content - r.height) / 2));
              want = '0 ' + (-(content - spread)) + 'px 0 ' + spread + 'px ' + opaqueOf(s.backgroundColor);
              if (startRec.own && startRec.own !== 'none') want += ', ' + startRec.own;
            } catch (e) { return; }
            // Against what we last wrote, never the live style (which the
            // browser re-serialises) — see writeFill.
            if (want === startRec.written) return;
            startRec.written = want;
            try { el.style.setProperty('box-shadow', want, 'important'); } catch (e) {}
          }

          // Who is standing at the top edge.
          //
          // NOT a hit test, and that is the whole lesson of this function.
          // `elementsFromPoint` is the obvious way to ask "what is at the top
          // of the page" and it silently cannot see the most important case:
          // it skips anything with `pointer-events: none`, and a top bar
          // whose CONTAINER is click-through so the page scrolls under its
          // gaps is a completely ordinary way to build one. YouTube's is
          // exactly that — `YTM-MOBILE-TOPBAR-RENDERER`, fixed at top 0,
          // `pointer-events: none` — so it was invisible to the probe and sat
          // behind our header on every visit while the chip bar beside it,
          // which happens to be clickable, was found and moved.
          //
          // Layout answers the question honestly. The scan is bounded, and it
          // only ever runs while [bar] is in force — which is only while the
          // header is fully up, i.e. a state the page reaches by standing
          // still.
          // Whether the DOCUMENT scrolls. A page that cannot (`overflow:
          // hidden` on the root, or content no taller than the viewport past
          // our own padding) is an app shell, and its layout usually hangs
          // off an `absolute` box sized to the viewport — which padding does
          // not move and which is neither fixed nor sticky.
          function docScrolls(){
            try {
              var de = document.documentElement, b = document.body;
              var se = document.scrollingElement || de;
              var hidden = function(e){
                var o = window.getComputedStyle(e).overflowY;
                return o === 'hidden' || o === 'clip';
              };
              if (hidden(de) || (b && hidden(b))) return false;
              return se.scrollHeight - pad > (window.innerHeight || 0) + 4;
            } catch (e) { return true; }
          }

          // An `absolute` box whose containing block is the viewport itself:
          // no positioned ancestor, so `offsetParent` is the body or nothing.
          function viewportAnchored(el){
            try {
              var op = el.offsetParent;
              if (op !== null && op !== document.body) return false;
              return window.getComputedStyle(document.body).position === 'static' &&
                window.getComputedStyle(document.documentElement).position === 'static';
            } catch (e) { return false; }
          }

          // Whether [el] is the containing block for `fixed` descendants,
          // i.e. whether moving it carries them along.
          function containsFixed(el){
            try {
              var c = window.getComputedStyle(el);
              return (c.transform && c.transform !== 'none') ||
                (c.perspective && c.perspective !== 'none') ||
                (c.filter && c.filter !== 'none') ||
                (c.backdropFilter && c.backdropFilter !== 'none') ||
                /paint|layout|strict|content/.test(c.contain || '') ||
                /transform|perspective|filter/.test(c.willChange || '');
            } catch (e) { return false; }
          }

          function candidates(vw, vh){
            var out = [];
            var b = document.body;
            if (!b) return out;
            // kontur.systems: `html`/`body` overflow hidden, a map shell
            // `absolute; top: 0` over the whole viewport, its counters bar at
            // `top: 20px` inside it — under the status bar for good.
            // At the top only: a page that LOCKS its scroll for a menu or a
            // modal reads as unscrollable too, and wherever it was scrolled to,
            // whatever absolute box sat at the top of the viewport then would
            // be taken for an app shell's and moved.
            var shellsAllowed = steady() && !docScrolls() && !(window.scrollY > 0);
            var all;
            try { all = b.querySelectorAll('*'); } catch (e) { return out; }
            var n = Math.min(all.length, MAX_SCAN);
            for (var i = 0; i < n; i++){
              var el = all[i];
              if (ours(el)) continue;
              if (held(el) >= 0) continue;
              var cs;
              try { cs = window.getComputedStyle(el); } catch (e) { continue; }
              var fixed = cs.position === 'fixed';
              var sticky = cs.position === 'sticky' || cs.position === '-webkit-sticky';
              var shell = !fixed && !sticky && shellsAllowed && cs.position === 'absolute' &&
                viewportAnchored(el);
              if (!fixed && !sticky && !shell) continue;
              // A sticky element with no `top` sticks to nothing at the top
              // edge; moving its margin would move it in flow, where the
              // body's padding has already dealt with it.
              if (sticky && cs.top === 'auto') continue;
              // The reader is ours and is given the same inset through its own
              // door (ReaderMode.setInsets); moving it here would move it
              // twice.
              if (el.id === '__yuku_reader__') continue;
              var r;
              try { r = el.getBoundingClientRect(); } catch (e) { continue; }
              var topVal = parseFloat(cs.top);
              var anchored = cs.top !== 'auto' && isFinite(topVal);
              // Measured by what it puts ON SCREEN, not by how tall its box
              // is: a bar hanging off the top edge is as tall as the part of
              // it anyone can see.
              var visible = Math.min(r.bottom, vh) - Math.max(r.top, 0);
              if (visible < MIN_HEIGHT_PX) continue;
              // No size gate for a viewport-anchored absolute box: on a page
              // that cannot scroll it IS a fixed box in all but name, so the
              // same rules sort it — a bar is moved (kontur.systems' counters,
              // `absolute; top: 20px` straight off the viewport), a panel is
              // shortened (its map shell).
              // Resting on the top edge — or on the inset's line, which is
              // where one we have already moved comes to rest.
              var onEdge = r.top <= bar + TOP_SLACK_PX;
              // A bar STACKED on another one — a site's second-tier nav,
              // YouTube's filter chips under its masthead — rests at its own
              // `top` and on neither line, so nothing found it: the bar above
              // it was moved off the covered strip and this one was left in
              // it, half of it behind the one it sits under. Only where the
              // inset is CONSTANT, since its move is not hidden by anything.
              var stacked = !onEdge && steady() && anchored && topVal > 0 &&
                topVal <= vh * MAX_HEIGHT_FRACTION &&
                Math.abs(r.top - topVal) <= TOP_SLACK_PX;
              if (!onEdge && !stacked) continue;
              if (visible > vh * MAX_HEIGHT_FRACTION){
                // Too big to be a bar: a drawer, a lightbox, a consent wall, an
                // app's own full-screen layer. It is not shoved down the screen
                // — it is SHORTENED from the top (capPanel) — but it cannot
                // simply be left either: what a full-screen layer holds against
                // its own top is a title row and a close button, and under the
                // page lens they sat in the strip the browser covers, behind
                // the bezel and bent into the curve, reachable only through the
                // last few pixels of it. `fixed` only (a sticky box this tall
                // is the page's own content) and constant insets only.
                // A panel held by its BOTTOM (`bottom: 0; height: 100%`, as a
                // filter drawer that rises into place is) has no `top` of its
                // own and is still covering the strip; its margin lever moves
                // nothing, and capPanel shortens it from the top all the same.
                if (!((fixed || shell) && onEdge && (anchored || r.top <= TOP_SLACK_PX) && steady())) continue;
                // A full-width overlay is always a panel. So is a side drawer
                // pinned to either edge: unlike a centred dialog, it owns the
                // top edge just like a header does. Keddr's mobile menu is
                // 330px wide, so it deliberately does not pass the full-width
                // test on wider phones; keeping it out here put its first rows
                // beneath the status bar.
                var spansViewport = r.width >= vw * PANEL_WIDTH_FRACTION;
                var sideDrawer = r.left <= TOP_SLACK_PX || r.right >= vw - TOP_SLACK_PX;
                if (!spansViewport && !sideDrawer) continue;
              } else if (r.width < vw * MIN_WIDTH_FRACTION){
                // Narrow: a floating button, a toast, a lightbox's close
                // control. Nothing to reserve room for while the inset flips,
                // but a constant one is covering it exactly as it covers a bar.
                if (!steady()) continue;
              }
              out.push(el);
            }
            // Nothing inside something else that is being moved. Two fixed
            // boxes are ordinarily independent of each other — each is
            // positioned against the viewport — but an ancestor with a
            // transform, a filter or a perspective becomes the containing
            // block for the fixed elements inside it, and then moving both
            // moves the inner one twice.
            // But ONLY then: without such an ancestor a fixed box is placed
            // against the viewport whatever it is nested in, and moving its
            // parent leaves it where it was. auto.ria.com's filter popup is a
            // fixed scrim (`top: 0`) holding a fixed white sheet (`top: 16px`)
            // with nothing in between that contains it: the scrim was moved,
            // the sheet skipped as nested, and the sheet's title row sat under
            // the status bar.
            var kept = [];
            for (var j = 0; j < out.length; j++){
              var anc = out[j].parentElement;
              var nested = false;
              var isFixed = false;
              try { isFixed = window.getComputedStyle(out[j]).position === 'fixed'; } catch (e) {}
              var contained = !isFixed;
              while (anc){
                if (out.indexOf(anc) >= 0 || held(anc) >= 0){
                  if (contained || containsFixed(anc)) nested = true;
                  break;
                }
                if (!contained && containsFixed(anc)) contained = true;
                anc = anc.parentElement;
              }
              if (!nested) kept.push(out[j]);
            }
            return kept;
          }

          // ---- the status bar strip (see PageTopInset.setStrip) --------
          // The page MEASURES the strip; the app DRAWS it (ui/StatusStrip.kt).
          // Drawn in the page, it was in every capture of the page — a tab's
          // card carried the fade and the header fill instead of the page, and
          // nothing could take them back out. Drawn by the app it sits over the
          // page and never in it, so a preview is the page alone and the strip
          // grows in with the zoom out of a card. What crosses the bridge:
          //   edge  — the ground at the bar's edge;
          //   head  — a header's own paint, and the share of it on screen,
          //           read on every frame while anything moves (keddr.com's
          //           header hides by a 0.3s transform AFTER the scroll);
          //   ramp  — how far through the first strip of scroll the page is;
          // colours as they look ON SCREEN (through the dark filter's matrix).
          // Default to no blur until the bridge explicitly confirms the
          // preference. On a reload the document-start script can run before
          // the Java interface is ready for its first call; defaulting to true
          // briefly painted a blur even when the setting was disabled.
          var strip = 0, stripFade = 0, stripBlur = false, stripBlurStyle = null, stripBlurEl = null;
          try {
            var dpr0 = window.devicePixelRatio || 1;
            strip = (__topInsetBridge.strip() || 0) / dpr0;
            stripFade = (__topInsetBridge.stripFade() || 0) / dpr0;
            stripBlur = __topInsetBridge.stripBlur();
          } catch (e) {}
          var stripScroll = 0, stripReported = '';
          var edgeColour = 'rgb(255, 255, 255)', headColour = '', headEl0 = null;
          var loopUntil = 0, loopQueued = false, lastSample = 0;
          var SAMPLE_MS = 120;

          // The status blur is an actual fixed node, rather than a pseudo
          // element on `html`. This is a best-effort fallback before Android
          // 12; newer devices apply a native masked blur outside page CSS.
          // Keep this node out of the fixed-bar scanner and ignore its own
          // style mutations, otherwise the bridge would try to move it too.
          function ours(el){
            for (var n = el; n && n !== document; n = n.parentNode){
              if (n.nodeType === 1 && (n.hasAttribute('data-yuku-status-blur') ||
                  n.hasAttribute('data-yuku-status-blur-style'))) return true;
            }
            return false;
          }

          // This is deliberately a page overlay instead of a blur on the
          // whole WebView: backdrop-filter samples only what is already under
          // the status bar. Match statusBarEffectStrength: fully blurred at
          // the top, with a smooth, zero-slope tail at the bar's bottom.
          function paintStripBlur(){
            try {
              if (!stripBlur || strip <= 0){
                if (stripBlurStyle && stripBlurStyle.parentNode) stripBlurStyle.parentNode.removeChild(stripBlurStyle);
                if (stripBlurEl && stripBlurEl.parentNode) stripBlurEl.parentNode.removeChild(stripBlurEl);
                return;
              }
              if (!stripBlurStyle){
                stripBlurStyle = document.createElement('style');
                stripBlurStyle.setAttribute('data-yuku-status-blur-style', '');
              }
              if (!stripBlurEl){
                stripBlurEl = document.createElement('div');
                stripBlurEl.setAttribute('data-yuku-status-blur', '');
              }
              var height = strip;
              var stops = [];
              for (var i = 0; i <= 32; i++){
                var position = i / 32;
                var x = Math.max(0, Math.min(1, (1 - position) / 0.8));
                var strength = x * x * x * (x * (x * 6 - 15) + 10);
                stops.push('rgba(0,0,0,' + strength + ') ' + (position * 100) + '%');
              }
              var mask = 'linear-gradient(to bottom,' + stops.join(',') + ')';
              stripBlurStyle.textContent =
                '[data-yuku-status-blur]{position:fixed!important;z-index:2147483647!important;'
                + 'left:0!important;top:0!important;width:100%!important;height:' + height + 'px!important;'
                + 'pointer-events:none!important;background:transparent!important;'
                // Allocate one long-lived compositor layer. Neither property
                // changes as the page scrolls, so Chromium can update its
                // backdrop without tearing down or re-promoting the filter.
                + 'transform:translate3d(0,0,0)!important;backface-visibility:hidden!important;'
                + 'will-change:backdrop-filter!important;'
                + '-webkit-backdrop-filter:blur(18px)!important;backdrop-filter:blur(18px)!important;'
                + '-webkit-mask-image:' + mask + '!important;'
                + 'mask-image:' + mask + '!important;}';
              if (!stripBlurStyle.parentNode) (document.head || document.documentElement).appendChild(stripBlurStyle);
              // Keep it directly on the document root. A real child avoids
              // the root-pseudo-element compositor bug AUTO.RIA triggers,
              // while avoiding the site-specific transforms and containment
              // that can make a fixed child of body scroll or clip.
              var blurParent = document.documentElement;
              if (stripBlurEl.parentNode !== blurParent) blurParent.appendChild(stripBlurEl);
            } catch (e) {}
          }

          // Re-read after startup as well: this covers the document-start
          // bridge becoming available a moment after the script, and makes a
          // disabled preference authoritative before any later page paint.
          function refreshStripBlur(){
            try { stripBlur = __topInsetBridge.stripBlur() === true; } catch (e) {}
            paintStripBlur();
          }

          function rgbParts(colour){
            var m = /rgba?\(([^)]*)\)/.exec(colour || '');
            if (!m) return [255, 255, 255];
            var p = m[1].split(/[\s,\/]+/).filter(Boolean);
            return [parseFloat(p[0]) || 0, parseFloat(p[1]) || 0, parseFloat(p[2]) || 0];
          }

          // What the content scrolling under the strip is drawn on.
          function canvasColour(){
            var body = '', html = '';
            try { if (document.body) body = window.getComputedStyle(document.body).backgroundColor; } catch (e) {}
            try { html = window.getComputedStyle(document.documentElement).backgroundColor; } catch (e) {}
            return opaqueOf(alphaOf(body) > 0.05 ? body : (alphaOf(html) > 0.05 ? html : 'rgb(255, 255, 255)'));
          }

          // The paint an element puts down itself: an opaque background
          // colour, or the first opaque stop of a gradient (github.com's hero
          // is `linear-gradient(rgb(0, 2, 64), …)` over a transparent
          // background colour — read by colour alone, the strip came out as
          // the body's white over a navy page). A `url()` is a picture, not a
          // ground, and says nothing.
          function paintOf(el){
            var s;
            // Not `el.pseudo`: Chromium's elements have a `pseudo` member of
            // their own, so every element took this branch and painted nothing.
            if (el && el.__yukuStyle) s = el.__yukuStyle;
            else try { s = window.getComputedStyle(el); } catch (e) { return null; }
            if (s.visibility === 'hidden' || parseFloat(s.opacity) < 0.5) return null;
            if (alphaOf(s.backgroundColor) > 0.5) return opaqueOf(s.backgroundColor);
            var img = s.backgroundImage;
            if (img && img !== 'none' && img.indexOf('url(') < 0){
              var re = /rgba?\([^)]*\)/g, m;
              while ((m = re.exec(img))){ if (alphaOf(m[0]) > 0.5) return opaqueOf(m[0]); }
            }
            return null;
          }

          var MEDIA = { IMG: 1, VIDEO: 1, CANVAS: 1, PICTURE: 1, IFRAME: 1, svg: 1, SVG: 1, OBJECT: 1, EMBED: 1 };

          function insideHeld(el){
            for (var i = 0; i < shifted.length; i++) if (shifted[i].el.contains(el)) return true;
            return false;
          }

          // The ground at the bar's edge, as the page actually stacks it: the
          // topmost element under the line that paints a ground, skipping
          // pictures (content, not a canvas), anything fixed or sticky (a
          // header is the head's business), a picture's own FRAME and anything
          // narrower than half the page. Three points, majority.
          //
          // The frame is google.com's: a result image sits in a box painted in
          // that image's dominant colour (the placeholder shown while it
          // loads), so with the image skipped its frame answered — a red
          // status bar over a white page, off a red arrow in a thumbnail. A
          // box no bigger than twice the picture it holds is the picture's.
          function sampleEdge(){
            var vw = window.innerWidth || 0;
            if (!vw) return canvasColour();
            // A normal-flow masthead is the actual surface under the status
            // bar at the document start. Check it before the hit-test stack:
            // the stack eventually reaches the opaque body and would otherwise
            // settle on that white canvas before its pseudo-painted header.
            var masthead = topHeaderPaint();
            if (masthead) return masthead;
            var y = Math.max(bar, strip) + 1;
            var xs = [vw * 0.2, vw * 0.5, vw * 0.8];
            var votes = {}, best = '', bestN = 0;
            for (var i = 0; i < xs.length; i++){
              var stack;
              try { stack = document.elementsFromPoint(xs[i], y); } catch (e) { stack = []; }
              var c = '', mediaArea = 0;
              for (var j = 0; j < stack.length; j++){
                var el = stack[j], r;
                try { r = el.getBoundingClientRect(); } catch (e) { continue; }
                if (MEDIA[el.tagName]){ mediaArea = Math.max(mediaArea, r.width * r.height); continue; }
                var pos;
                try { pos = window.getComputedStyle(el).position; } catch (e) { continue; }
                if (pos === 'fixed' || pos === 'sticky' || pos === '-webkit-sticky') continue;
                if (insideHeld(el)) continue;
                if (mediaArea > 0 && r.width * r.height <= mediaArea * 2) continue;
                if (r.width < vw * 0.5) continue;
                // The initial masthead can paint its ground on a pseudo-element
                // too. DNews uses an absolutely-positioned `::after` for the
                // mobile header, so its transparent wrapper otherwise falls
                // through to the white document canvas at the status edge.
                // Never `html`'s pseudo-elements: `html::before` is OUR top
                // fill (paintTopFill), painted in the last colour reported, so
                // reading it back latched that colour for good — zdnet.com's
                // navy header stayed on the strip over every white article.
                var p = paintOf(el) || (el === document.documentElement ? null : pseudoPaint(el));
                if (p){ c = p; break; }
              }
              if (!c) c = canvasColour();
              votes[c] = (votes[c] || 0) + 1;
              if (votes[c] > bestN){ bestN = votes[c]; best = c; }
            }
            return best;
          }

          // A header's paint may live on a pseudo-element: github.com's
          // scrolled header is black by `header::before`/`::after`, faded in
          // by opacity, over a transparent box.
          function pseudoPaint(el){
            var names = ['::before', '::after'];
            for (var i = 0; i < names.length; i++){
              var s;
              try { s = window.getComputedStyle(el, names[i]); } catch (e) { continue; }
              if (!s || s.content === 'none' || s.content === 'normal') continue;
              if (s.position !== 'absolute' && s.position !== 'fixed') continue;
              // A GROUND, not a mark: androidauthority.com's list bullets are
              // 6px absolute `li::before` squares in its green, and a wide
              // `li` under the edge turned the status bar green. A header's
              // pseudo ground spans its element (DNews's `::after`).
              var pw = parseFloat(s.width), ew = 0;
              try { ew = el.getBoundingClientRect().width; } catch (e) {}
              if (!(pw >= ew * 0.5)) continue;
              var p = paintOf({ __yukuStyle: s });
              if (p) return p;
            }
            return null;
          }

          // A header's own paint along its top row, or null for a header that
          // paints nothing there (github.com's is transparent over its hero)
          // — then it is see-through, and the ground under it is the answer.
          function headerPaint(el){
            var r = el.getBoundingClientRect();
            var y = r.top + 1;
            var found = paintOf(el) || pseudoPaint(el);
            var queue = [el], seen = 0;
            while (queue.length && seen < 200){
              var n = queue.shift();
              seen++;
              for (var i = 0; i < n.children.length; i++){
                var c = n.children[i], rr;
                try { rr = c.getBoundingClientRect(); } catch (e) { continue; }
                if (rr.width < r.width * 0.9) continue;
                // Header builders commonly put their solid ground on a
                // pseudo-element of an inner wrapper. DNews's sticky mobile
                // header does exactly this (`.tdi_11_rand_style::after`), so
                // sampling only the outer header's pseudo-elements leaves the
                // otherwise recognised header with no colour to paint.
                var onRow = rr.top <= y && rr.bottom >= y;
                // An absolutely-positioned pseudo can fill the header while
                // its empty originating element has zero layout height. The
                // regular DNews masthead uses that shape, so its pseudo needs
                // checking at the header's top even though the element itself
                // does not cover that row.
                var pseudoAtTop = rr.top >= r.top - 1 && rr.top <= y + 1;
                if (!onRow && !pseudoAtTop) continue;
                var p = onRow ? (paintOf(c) || pseudoPaint(c)) : pseudoPaint(c);
                if (p) found = p;
                queue.push(c);
              }
            }
            return found;
          }

          // A regular masthead remains in normal flow at the document's
          // start, so it is not in [shifted] and does not appear as the
          // scrolled header below. Use it as the edge ground while it is
          // physically covering the status-bar line. This path is only the
          // fallback when the point stack found no painted page element.
          function topHeaderPaint(){
            var vw = window.innerWidth || 0;
            var edge = Math.max(bar, strip);
            var heads;
            try { heads = document.querySelectorAll('[class*="header"],[class*="Header"],header'); } catch (e) { return null; }
            // Only what is actually ON SCREEN at the edge line. zdnet.com's
            // closed `site-header__dropdown-menu` is a lime box spanning the
            // line and matched by name, and the status bar stayed lime over
            // every article scrolled under it instead of following the page.
            var hit;
            try { hit = document.elementsFromPoint(vw / 2, edge + 1); } catch (e) { hit = []; }
            for (var i = 0; i < heads.length && i < 100; i++){
              var el = heads[i], r, s;
              // Page-level state classes commonly contain "header" (GitHub's
              // body has `header-overlay-fixed`). The root canvas is not a
              // masthead; accepting it masks the section actually visible
              // through a transparent fixed header.
              if (el === document.body || el === document.documentElement) continue;
              if (hit.indexOf(el) < 0) continue;
              try { r = el.getBoundingClientRect(); s = window.getComputedStyle(el); } catch (e) { continue; }
              if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) < 0.05) continue;
              if (s.position === 'fixed' || s.position === 'sticky' || s.position === '-webkit-sticky') continue;
              if (r.width < vw * MIN_WIDTH_FRACTION || r.height <= 0 || r.bottom < edge || r.top > edge + TOP_SLACK_PX) continue;
              // A masthead BEGINS the document. Being under the point is not
              // being seen: zdnet's dropdown was in the hit stack, clipped out
              // by its header, 350px down the document.
              if (r.top + (window.scrollY || 0) > strip + TOP_SLACK_PX * 2) continue;
              var paint = headerPaint(el);
              if (paint) return paint;
            }
            return null;
          }

          // A number of modern sites do not call their fixed masthead a
          // "header". Looking for a name cannot find that shape. Instead,
          // look at the elements the compositor says are ACTUALLY under the
          // top row, then walk up to the shallow, wide, viewport-anchored box
          // that owns them. A transform or transition alone is deliberately
          // insufficient: ordinary in-flow mastheads use those too, and must
          // leave the system bar transparent as they scroll away.
          //
          // Keep the last one for the short tail of its transition. A hiding
          // bar ceases to be in `elementsFromPoint` before its show animation
          // begins; retaining its identity lets the first visible frame of the
          // return use the same header rather than waiting for a class-name
          // scan to find it again.
          var visualHead = null, visualHeadUntil = 0;
          function visualHeaderOK(el, vw, vh){
            if (!el || el === document.documentElement || el === document.body) return false;
            var r, s;
            try { r = el.getBoundingClientRect(); s = window.getComputedStyle(el); } catch (e) { return false; }
            if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) < 0.05) return false;
            if (r.width < vw * MIN_WIDTH_FRACTION || r.height < 12 || r.height > vh * MAX_HEIGHT_FRACTION) return false;
            // It must be on, or immediately beside, the viewport's top. The
            // extra height admits a bar during the last frames of a translateY
            // exit, without mistaking an article card further down for chrome.
            if (r.top > TOP_SLACK_PX || r.bottom < -r.height) return false;
            return s.position === 'fixed' || s.position === 'sticky' || s.position === '-webkit-sticky';
          }

          function visualHeaderNow(){
            var vw = window.innerWidth || 0, vh = window.innerHeight || 0;
            if (!vw || !vh) return null;
            var best = null, bestShare = 0, seen = [];
            // Three probes avoid promoting a narrow menu button or logo. The
            // point is deliberately at the page's own top: fixed site chrome
            // is observed, never moved by this browser.
            var xs = [vw * 0.2, vw * 0.5, vw * 0.8];
            for (var i = 0; i < xs.length; i++){
              var stack;
              try { stack = document.elementsFromPoint(xs[i], 1); } catch (e) { stack = []; }
              for (var j = 0; j < stack.length; j++){
                var el = stack[j], depth = 0;
                while (el && depth++ < 10){
                  if (seen.indexOf(el) < 0){
                    seen.push(el);
                    if (visualHeaderOK(el, vw, vh)){
                      var r, s;
                      try { r = el.getBoundingClientRect(); s = window.getComputedStyle(el); } catch (e) { break; }
                      var op = parseFloat(s.opacity); if (isNaN(op)) op = 1;
                      var share = Math.max(0, Math.min(1, r.bottom / Math.max(1, Math.min(r.height, strip)))) * op;
                      if (share > bestShare){ best = el; bestShare = share; }
                    }
                  }
                  el = el.parentElement;
                }
              }
            }
            if (best){
              visualHead = best;
              visualHeadUntil = Date.now() + 900;
              return { el: best, share: bestShare };
            }
            // A tracked bar may be wholly hidden for part of its transition.
            // Return it with zero share until it either comes back or expires;
            // its colour is then harmless and the page edge remains visible.
            if (visualHead && Date.now() < visualHeadUntil && visualHead.isConnected){
              var rr, ss;
              try { rr = visualHead.getBoundingClientRect(); ss = window.getComputedStyle(visualHead); } catch (e) { rr = null; }
              if (rr && visualHeaderOK(visualHead, vw, vh)){
                var oo = parseFloat(ss.opacity); if (isNaN(oo)) oo = 1;
                return { el: visualHead, share: Math.max(0, Math.min(1,
                  rr.bottom / Math.max(1, Math.min(rr.height, strip)))) * oo };
              }
            }
            visualHead = null;
            return null;
          }

          // The held header resting on the edge and the share of it on
          // screen: whole while its bottom is a strip or more below the edge,
          // nothing once it has slid up under it, and times its own opacity
          // (keddr.com fades it as it slides).
          function headerNow(){
            var vw = window.innerWidth || 0;
            var best = null, bestShare = 0;
            for (var i = 0; i < shifted.length; i++){
              var s = shifted[i];
              if (!s.fill || s.panel || !s.el.isConnected) continue;
              var r, st;
              try { r = s.el.getBoundingClientRect(); st = window.getComputedStyle(s.el); } catch (e) { continue; }
              if (r.height <= 0 || r.width < vw * MIN_WIDTH_FRACTION) continue;
              if (r.top > bar + TOP_SLACK_PX) continue;
              if (st.display === 'none' || st.visibility === 'hidden') continue;
              var op = parseFloat(st.opacity);
              if (isNaN(op)) op = 1;
              var share = Math.max(0, Math.min(1, (r.bottom - bar) / Math.max(1, Math.min(r.height, strip)))) * op;
              if (share > bestShare){ bestShare = share; best = s.el; }
            }
            // The normal browser page begins below the native status bar, so
            // its fixed header must be OBSERVED rather than moved. Keeping
            // this separate from `shifted` is important: these are site
            // transitions (Keddr slides one back in while scrolling up,
            // GitHub changes its own header face), not boxes the browser owns.
            // Their visible share is enough to let the native strip follow
            // their paint without ever touching their geometry.
            if (!best){
              var heads;
              try { heads = document.querySelectorAll('header,[class*="header"],[class*="Header"]'); } catch (e) { heads = []; }
              for (var h0 = 0; h0 < heads.length && h0 < 160; h0++){
                var el0 = heads[h0], r0, st0;
                try { r0 = el0.getBoundingClientRect(); st0 = window.getComputedStyle(el0); } catch (e) { continue; }
                var p0 = st0.position;
                if (p0 !== 'fixed' && p0 !== 'sticky' && p0 !== '-webkit-sticky') continue;
                if (r0.height <= 0 || r0.width < vw * MIN_WIDTH_FRACTION) continue;
                if (r0.top > TOP_SLACK_PX || r0.bottom <= 0) continue;
                if (st0.display === 'none' || st0.visibility === 'hidden') continue;
                var op0 = parseFloat(st0.opacity);
                if (isNaN(op0)) op0 = 1;
                var share0 = Math.max(0, Math.min(1, r0.bottom / Math.max(1, Math.min(r0.height, strip)))) * op0;
                if (share0 > bestShare){ bestShare = share0; best = el0; }
              }
            }
            if (!best){
              var visual = visualHeaderNow();
              if (visual){ best = visual.el; bestShare = visual.share; }
            }
            return { el: best, share: bestShare };
          }

          // A colour as the screen shows it. Under the dark mode the whole
          // document goes through PageDarkening's `feColorMatrix`, which is in
          // the DOM, so it is applied here exactly rather than guessed at —
          // the app draws over the page, after the filter, not through it.
          function onScreen(colour){
            var rgb = rgbParts(colour);
            var filtered = false;
            try { filtered = window.getComputedStyle(document.documentElement).filter !== 'none'; } catch (e) {}
            if (!filtered) return rgb;
            var v = [];
            try {
              var fe = document.querySelector('#__yuku_dark_filter__ feColorMatrix');
              if (fe) v = (fe.getAttribute('values') || '').trim().split(/[\s,]+/).map(parseFloat);
            } catch (e) {}
            if (v.length < 20 || v.some(isNaN)) return [255 - rgb[0], 255 - rgb[1], 255 - rgb[2]];
            var r = rgb[0] / 255, g = rgb[1] / 255, b = rgb[2] / 255, out = [];
            for (var k = 0; k < 3; k++){
              var o = v[k * 5] * r + v[k * 5 + 1] * g + v[k * 5 + 2] * b + v[k * 5 + 3] + v[k * 5 + 4];
              out.push(Math.round(Math.max(0, Math.min(1, o)) * 255));
            }
            return out;
          }

          function argb(rgb){
            return (0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]) | 0;
          }

          // The blank band at the document's top that the app's cap covers.
          // Unseen at rest, but Android's stretch overscroll scales the page
          // down from the top edge while the cap stays the status bar's height,
          // so the band's own canvas colour showed as a gap under the bar.
          // Painted IN the page, in the colour the cap draws — the raw colour,
          // since the dark filter over the document turns it into the on-screen
          // one exactly as it does the rest. A pseudo-element on <html>, so no
          // hit test (the edge sampler's included) ever answers with it, and it
          // scrolls away with the padding it sits on.
          var fillStyle = null, fillKey = '';
          function paintTopFill(colour){
            // With a native safe top edge there is no blank document band
            // to paint. A pseudo-element here would cover the site's first
            // row and then scroll away independently of its fixed header.
            if (!(content > 0)) colour = '';
            var key = colour ? colour + '|' + strip : '';
            if (key === fillKey) return;
            fillKey = key;
            try {
              if (!colour){
                if (fillStyle && fillStyle.parentNode) fillStyle.parentNode.removeChild(fillStyle);
                return;
              }
              if (!fillStyle){
                fillStyle = document.createElement('style');
                fillStyle.setAttribute('data-yuku-top-fill', '');
              }
              var rgb = rgbParts(colour);
              fillStyle.textContent = 'html::before{content:""!important;display:block!important;' +
                'position:absolute!important;left:0!important;top:0!important;width:100%!important;' +
                'height:' + strip + 'px!important;background:rgb(' + rgb[0] + ',' + rgb[1] + ',' + rgb[2] + ')!important;' +
                'pointer-events:none!important;margin:0!important;padding:0!important;border:0!important}';
              if (!fillStyle.parentNode) (document.head || document.documentElement).appendChild(fillStyle);
            } catch (e) {}
          }

          function reportStrip(edge, head, share, ramp){
            var key = edge + '|' + head + '|' + share + '|' + ramp;
            if (key === stripReported) return;
            stripReported = key;
            try { __topInsetBridge.stripState(edge, head, share, ramp); } catch (e) {}
          }

          // One frame of the strip's measurement. [force] re-reads the colours
          // now rather than on the sampling beat. Reports only what changed.
          function paintStrip(force){
            if (!(strip > 0) || fullscreenElement()){
              if (stripReported !== 'off'){
                stripReported = 'off';
                try { __topInsetBridge.stripState(0, 0, -1, 0); } catch (e) {}
              }
              paintTopFill('');
              return;
            }
            var now = Date.now();
            var sample = force || now - lastSample >= SAMPLE_MS;
            if (sample){
              lastSample = now;
              try { edgeColour = sampleEdge(); } catch (e) {}
            }
            var h = headerNow();
            var share = h.share;
            if (h.el && (sample || h.el !== headEl0)){
              var hp = null;
              try { hp = headerPaint(h.el); } catch (e) {}
              headEl0 = h.el;
              // A fixed header may deliberately have no paint of its own:
              // GitHub's marketing masthead is a transparent fixed box over
              // the document's dark start, and changes its face as sections
              // pass beneath it. The colour actually seen through that box is
              // the edge ground, so it still owns the system-bar strip. Read
              // the ground first above, then use it here rather than falling
              // back to the generic black icon scrim.
              headColour = hp || edgeColour;
            }
            if (!headColour) share = 0;
            // At its top a page has nothing under the bar but its own blank
            // padding (the app covers it solid); over the first strip's worth
            // of scroll that gives way to the veil.
            var ramp = Math.max(0, Math.min(1, stripScroll / Math.max(8, strip)));
            reportStrip(argb(onScreen(edgeColour)), headColour ? argb(onScreen(headColour)) : 0,
              Math.round(share * 50), Math.round(ramp * 50));
            // Only a real viewport-fixed header owns an opaque system-bar
            // extension. An ordinary page keeps the band truly transparent;
            // painting its sampled edge here is what made sites such as ZDNET
            // jump between transparent and filled states while scrolling.
            paintTopFill(headColour && share >= 0.5 ? headColour : '');
          }

          // Runs the strip per frame for [ms] past the last thing that moved,
          // then stops: a page at rest costs nothing.
          function kickStrip(ms){
            if (!(strip > 0)) return;
            loopUntil = Math.max(loopUntil, Date.now() + (ms || 600));
            if (loopQueued) return;
            loopQueued = true;
            requestAnimationFrame(stripFrame);
          }

          function stripFrame(){
            loopQueued = false;
            try { paintStrip(false); } catch (e) {}
            if (Date.now() < loopUntil){
              loopQueued = true;
              requestAnimationFrame(stripFrame);
            }
          }

          function onStripScroll(e){
            if (!(strip > 0)) return;
            var t = e && e.target, y = null;
            if (!t || t === document || t === document.documentElement || t === document.body){
              y = window.scrollY || (document.scrollingElement ? document.scrollingElement.scrollTop : 0) || 0;
            } else if (t.nodeType === 1 && t.clientHeight >= (window.innerHeight || 0) * 0.5 &&
                t.clientWidth >= (window.innerWidth || 0) * MIN_WIDTH_FRACTION){
              // An app shell scrolling one big box instead of the document.
              y = t.scrollTop || 0;
            }
            if (y === null) return;
            stripScroll = y;
            kickStrip(700);
          }

          function measure(){
            try { updateStartHold(); } catch (e) {}
            applyPad();
            if (bar <= 0 || fullscreenElement()){
              // Nothing to hold anything off: everything is given back
              // exactly as it was found, and the next time there IS an inset
              // the site's own values are read fresh.
              if (shifted.length) releaseAll();
              applyStartFill();
              try { paintStrip(true); } catch (e) {}
              return;
            }
            var vw = window.innerWidth || 0;
            var vh = window.innerHeight || 0;
            if (!vw || !vh) return;

            // Something already being held down is NOT re-tested against the
            // geometry, and this is the difference between working and
            // flapping at 4Hz. The test asks whether a thing is RESTING on
            // the top edge — but a bar that has been moved is resting [bar]
            // lower than it was, and one whose own `top` was never zero
            // (YouTube's filter chips sit at 48, under its masthead) lands
            // nowhere near the line. It would measure as gone, be restored,
            // be found again by the next measurement, and shift and unshift
            // forever. So the geometry decides only who is TAKEN UP; leaving
            // the document or ceasing to be viewport-positioned is what puts
            // one down — and everything is put down anyway the moment the
            // header starts to leave, so nothing is held for long.
            for (var i = shifted.length - 1; i >= 0; i--){
              var el2 = shifted[i].el;
              var live = false;
              if (el2 && el2.isConnected){
                try {
                  var p2 = window.getComputedStyle(el2).position;
                  live = p2 === 'fixed' || p2 === 'sticky' || p2 === '-webkit-sticky' ||
                    (shifted[i].shell && p2 === 'absolute');
                } catch (e) {}
              }
              if (!live){ restore(shifted[i]); shifted.splice(i, 1); continue; }
              // A panel's cap only ever shortens it, and its bottom0 is a
              // line on the viewport it was measured on: the keyboard going
              // down left auto.ria.com's filter sheet capped to the half
              // screen it had above the keyboard, centred in the full one.
              // Given back and taken up fresh on the next pass (the restore
              // is a mutation, so there is one).
              if (shifted[i].panel && (shifted[i].vh0 !== vh || shifted[i].bar0 !== bar ||
                  el2.style.getPropertyValue('bottom') !== shifted[i].bottomStyle0)){
                restore(shifted[i]); shifted.splice(i, 1); continue;
              }
              // The site restyled it by class, and our inline `!important`
              // beats whatever that class says: kontur.systems' top-right
              // buttons take `.shift-down` to clear its counters bar, and
              // with the old base written over it they sat on the bar. So
              // the site's values are handed back, read again, and the move
              // redone on top of them.
              if ((el2.getAttribute('class') || '') !== shifted[i].cls){
                var old = shifted[i];
                restore(old);
                var cs3;
                try { cs3 = window.getComputedStyle(el2); } catch (e) { shifted.splice(i, 1); continue; }
                var fresh = record(el2, cs3, vh);
                fresh.shell = old.shell;
                shifted[i] = fresh;
              }
              // A bar held as hidden that the site has brought back without a
              // class change (or whose slide in has since been read): nothing
              // of ours is written to it, so its rect is the site's own.
              if (shifted[i].hidden && !shifted[i].offTop){
                try {
                  var r4 = el2.getBoundingClientRect();
                  if (r4.bottom + settledShift(el2, window.getComputedStyle(el2)) > 1) shifted[i].hidden = false;
                } catch (e) {}
              }
            }
            // A page that scrolls again (the lock came off) has no app shell:
            // its absolute boxes scroll with the document and are given back.
            if (docScrolls()){
              for (var s3 = shifted.length - 1; s3 >= 0; s3--){
                if (shifted[s3].shell){ restore(shifted[s3]); shifted.splice(s3, 1); }
              }
            }

            var found = candidates(vw, vh);
            for (var j = 0; j < found.length; j++){
              // The site's own value is read ONCE, before anything of ours is
              // written to the element — read back later it would be our own
              // number, and the shift would compound.
              var cs2;
              try { cs2 = window.getComputedStyle(found[j]); } catch (e) { continue; }
              var rec2 = record(found[j], cs2, vh);
              // Held as long as it stays absolute (see the live test above).
              rec2.shell = cs2.position === 'absolute';
              shifted.push(rec2);
            }
            for (var n2 = 0; n2 < shifted.length; n2++) write(shifted[n2]);
            // After every write, for the reason the baselines are read before
            // any of them: a panel's cap is measured against where the write
            // actually left it, and a panel nested in another one would
            // otherwise be measured against a box that has not moved yet.
            for (var p2 = 0; p2 < shifted.length; p2++){
              if (!shifted[p2].panel) continue;
              capPanel(shifted[p2]);
              try { if (panelSizes) panelSizes.observe(shifted[p2].el); } catch (e) {}
            }
            // After the bars, so one being moved is known to be held.
            applyStartFill();
            // After both: the strip takes its colour from what they found.
            try { paintStrip(true); } catch (e) {}
          }

          // A held panel that changes SIZE is re-measured in the same frame.
          // The keyboard resizes the viewport, and `resize` is dispatched a
          // frame after the new size has already been laid out and painted —
          // one frame of auto.ria's `height: 100%` sheet centred at its old
          // cap. A ResizeObserver is delivered after layout and BEFORE paint.
          // Our own cap resizes the panel too; that answer is a no-op write,
          // and the burst bound in schedule() holds either way.
          var panelSizes = null;
          try {
            if (window.ResizeObserver) {
              panelSizes = new ResizeObserver(function(){ schedule(true); });
            }
          } catch (e) {}

          var pending = false;
          var lastRun = 0;
          // A change the OBSERVER saw is answered in its own microtask, i.e.
          // before the frame it happened in is painted — not a timeout later.
          // A popup inserted by the site was otherwise painted once where the
          // site put it, once where we moved it, again where PageBottomBar's
          // write (a frame later, from its own timeout) left it, and a fourth
          // time 250ms on when the throttle let this correct that: auto.ria's
          // brand sheet visibly hopped 16 → 63 → 34 → 48 as it opened. Run
          // inside the checkpoint, the two scripts answer each other's writes
          // there too and the first painted frame is the settled one.
          // Bounded PER FRAME (and per second), so two writers that disagree
          // cost a few runs and fall back to the throttle, never a microtask
          // loop that hangs the page. Not a time window from the last run:
          // PageBottomBar's answer to the keyboard reaches the page ~50ms
          // after ours, and the keyboard itself can come 200ms after a sheet
          // opened — both landed outside a 50ms window and inside the
          // throttle, and waited 250ms for it.
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
          function schedule(now){
            // Whatever is pending: a keyboard change fires scroll events that
            // queue a throttled run first, and waiting behind it is the 230ms
            // step this path exists to remove. The queued run finds nothing
            // left to write.
            if (now === true && runNowAllowed()){
              lastRun = Date.now();
              try { measure(); } catch (e) {}
              return;
            }
            if (pending) return;
            pending = true;
            var wait = Math.max(0, THROTTLE_MS - (Date.now() - lastRun));
            setTimeout(function(){
              pending = false;
              lastRun = Date.now();
              try { measure(); } catch (e) {}
            }, wait);
          }

          window.__topInset = function(contentPx, barPx){
            var dpr = window.devicePixelRatio || 1;
            content = (contentPx || 0) / dpr;
            bar = (barPx || 0) / dpr;
            try { measure(); } catch (e) {}
          };

          window.__topInsetFill = function(px){
            fill = (px || 0) / (window.devicePixelRatio || 1);
            try { measure(); } catch (e) {}
          };

          window.__topInsetStrip = function(px, fadePx, blur){
            var dpr = window.devicePixelRatio || 1;
            strip = (px || 0) / dpr;
            stripFade = (fadePx || 0) / dpr;
            stripBlur = blur !== false;
            paintStripBlur();
            try { measure(); } catch (e) {}
          };

          function boot(){
            try { window.__topInset(__topInsetBridge.content(), __topInsetBridge.bar()); } catch (e) {}
            refreshStripBlur();
            try {
              // Our own strip's writes are not the page changing.
              new MutationObserver(function(list){
                for (var i = 0; i < list.length; i++){
                  if (!ours(list[i].target)){ schedule(true); return; }
                }
              }).observe(document.documentElement, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['style', 'class'],
              });
            } catch (e) {}
            // A bar the site itself reveals on scroll is one that did not
            // exist to be found at load.
            window.addEventListener('scroll', schedule, true);
            // The strip's veil follows the scroll directly — one style write
            // when it changes, not a measurement.
            window.addEventListener('scroll', onStripScroll, { capture: true, passive: true });
            onStripScroll(null);
            if (window.visualViewport){
              window.visualViewport.addEventListener('resize', function(){ kickStrip(300); });
            }
            // A header that hides or shows does it with a transition or an
            // animation that outlives the scroll that started it.
            ['transitionrun', 'transitionend', 'animationstart', 'animationend'].forEach(function(n){
              document.addEventListener(n, function(){ kickStrip(450); }, { capture: true, passive: true });
            });
            // A panel that slides or fades in is measured mid-entrance, off the
            // edge, and not taken up; nothing mutates when it lands, so its
            // arrival is what asks again (auto.ria.com's filters).
            ['transitionend', 'animationend'].forEach(function(n){
              document.addEventListener(n, schedule, { capture: true, passive: true });
            });
            // Stylesheets and web fonts land without a mutation; the ground
            // read before them is the unstyled page's white.
            window.addEventListener('load', function(){
              refreshStripBlur();
              try { paintStrip(true); } catch (e) {}
            });
            [300, 1000, 2500].forEach(function(ms){
              setTimeout(function(){ try { paintStrip(true); } catch (e) {} }, ms);
            });
            // Answered in the same frame — see schedule() and PageBottomBar's
            // resize listener.
            window.addEventListener('resize', function(){ schedule(true); });
            window.addEventListener('orientationchange', function(){ schedule(true); });
            window.addEventListener('load', schedule);
            // Both ways: entering gives every shift back before the fullscreen
            // element can be found as a panel, leaving restores the inset for
            // the page that is on screen again.
            document.addEventListener('fullscreenchange', schedule, true);
            document.addEventListener('webkitfullscreenchange', schedule, true);
          }

          if (document.body) boot();
          else document.addEventListener('DOMContentLoaded', boot, { once: true });
        })();
    """.trimIndent()
}
