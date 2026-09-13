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
        onStrip: (StatusStripReport?) -> Unit = {},
    ) {
        web.addJavascriptInterface(
            Bridge(contentPx, barPx, fillPx, stripPx, stripFadePx, onStrip),
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
    fun setStrip(web: WebView, px: Int, fadePx: Int) {
        web.evaluateJavascript("window.__topInsetStrip && window.__topInsetStrip($px, $fadePx)", null)
    }

    class Bridge internal constructor(
        private val contentPx: () -> Int,
        private val barPx: () -> Int,
        private val fillPx: () -> Int,
        private val stripPx: () -> Int,
        private val stripFadePx: () -> Int,
        private val onStrip: (StatusStripReport?) -> Unit,
    ) {
        private val main = android.os.Handler(android.os.Looper.getMainLooper())

        /** Device pixels of status bar strip; see [setStrip]. */
        @JavascriptInterface
        fun strip(): Int = stripPx()

        /** Device pixels the strip fades out over, below the bar's edge. */
        @JavascriptInterface
        fun stripFade(): Int = stripFadePx()

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
            var want = content > 0 ? content : 0;
            applyScrollPad(want);
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
            if (r.bottom - rec.bottom0 <= 1) return;
            var s, extra = 0;
            try { s = window.getComputedStyle(el); } catch (e) { return; }
            if (s.boxSizing !== 'border-box'){
              extra = (parseFloat(s.paddingTop) || 0) + (parseFloat(s.paddingBottom) || 0) +
                (parseFloat(s.borderTopWidth) || 0) + (parseFloat(s.borderBottomWidth) || 0);
            }
            var want = Math.round(rec.bottom0 - r.top - extra);
            if (!(want >= MIN_PANEL_HEIGHT_PX)) return;
            if (!rec.cap){
              rec.cap = {
                inline: el.style.getPropertyValue('max-height'),
                priority: el.style.getPropertyPriority('max-height'),
              };
            }
            var css = want + 'px';
            if (el.style.getPropertyValue('max-height') !== css){
              try { el.style.setProperty('max-height', css, 'important'); } catch (e) {}
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
              hidden: rect ? rect.bottom <= 1 : false,
              // Where its own bottom was before anything of ours moved it,
              // which is the line capPanel holds it to.
              bottom0: rect ? rect.bottom : 0,
              cap: null,
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
                if (!((fixed || shell) && onEdge && anchored && steady())) continue;
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
            var kept = [];
            for (var j = 0; j < out.length; j++){
              var anc = out[j].parentElement;
              var nested = false;
              while (anc){
                if (out.indexOf(anc) >= 0 || held(anc) >= 0){ nested = true; break; }
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
          var strip = 0, stripFade = 0;
          try {
            var dpr0 = window.devicePixelRatio || 1;
            strip = (__topInsetBridge.strip() || 0) / dpr0;
            stripFade = (__topInsetBridge.stripFade() || 0) / dpr0;
          } catch (e) {}
          var stripScroll = 0, stripReported = '';
          var edgeColour = 'rgb(255, 255, 255)', headColour = '', headEl0 = null;
          var loopUntil = 0, loopQueued = false, lastSample = 0;
          var SAMPLE_MS = 120;

          // Nothing of ours is in the page any more; kept for the observer.
          function ours(el){ return false; }

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
                var p = paintOf(el) || pseudoPaint(el);
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
            for (var i = 0; i < heads.length && i < 100; i++){
              var el = heads[i], r, s;
              try { r = el.getBoundingClientRect(); s = window.getComputedStyle(el); } catch (e) { continue; }
              if (s.display === 'none' || s.visibility === 'hidden') continue;
              if (s.position === 'fixed' || s.position === 'sticky' || s.position === '-webkit-sticky') continue;
              if (r.width < vw * MIN_WIDTH_FRACTION || r.height <= 0 || r.bottom < edge || r.top > edge + TOP_SLACK_PX) continue;
              var paint = headerPaint(el);
              if (paint) return paint;
            }
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
            var h = headerNow();
            var share = h.share;
            if (h.el && (sample || h.el !== headEl0)){
              var hp = null;
              try { hp = headerPaint(h.el); } catch (e) {}
              headEl0 = h.el;
              headColour = hp || '';
            }
            if (!headColour) share = 0;
            if (sample){
              lastSample = now;
              try { edgeColour = sampleEdge(); } catch (e) {}
            }
            // At its top a page has nothing under the bar but its own blank
            // padding (the app covers it solid); over the first strip's worth
            // of scroll that gives way to the veil.
            var ramp = Math.max(0, Math.min(1, stripScroll / Math.max(8, strip)));
            reportStrip(argb(onScreen(edgeColour)), headColour ? argb(onScreen(headColour)) : 0,
              Math.round(share * 50), Math.round(ramp * 50));
            // What the cap shows over the band: the header where one rests on
            // the edge (StatusStripReport's own rule), otherwise the ground.
            paintTopFill(headColour && share >= 0.5 ? headColour : edgeColour);
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
              if (shifted[p2].panel) capPanel(shifted[p2]);
            }
            // After the bars, so one being moved is known to be held.
            applyStartFill();
            // After both: the strip takes its colour from what they found.
            try { paintStrip(true); } catch (e) {}
          }

          var pending = false;
          var lastRun = 0;
          function schedule(){
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

          window.__topInsetStrip = function(px, fadePx){
            var dpr = window.devicePixelRatio || 1;
            strip = (px || 0) / dpr;
            stripFade = (fadePx || 0) / dpr;
            try { measure(); } catch (e) {}
          };

          function boot(){
            try { window.__topInset(__topInsetBridge.content(), __topInsetBridge.bar()); } catch (e) {}
            try {
              // Our own strip's writes are not the page changing.
              new MutationObserver(function(list){
                for (var i = 0; i < list.length; i++){
                  if (!ours(list[i].target)){ schedule(); return; }
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
            // Stylesheets and web fonts land without a mutation; the ground
            // read before them is the unstyled page's white.
            window.addEventListener('load', function(){ try { paintStrip(true); } catch (e) {} });
            [300, 1000, 2500].forEach(function(ms){
              setTimeout(function(){ try { paintStrip(true); } catch (e) {} }, ms);
            });
            window.addEventListener('resize', schedule);
            window.addEventListener('orientationchange', schedule);
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
