package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Reader mode: the page reduced to what it was written for — a headline, a
 * byline, the prose, and the pictures that belong to it — with the navigation,
 * the recirculation rails, the sticky share bars, the newsletter interstitial
 * and the consent furniture left behind.
 *
 * ## Why this is a document-start script and not a button that reads the DOM
 *
 * The obvious shape for a reader is "when the user taps, look at the document
 * and pull the article out". That shape cannot read a metered page. The way a
 * soft paywall works is that the server sends the whole article — it has to,
 * because the same HTML is what the search crawler is shown — and then a
 * script on the page takes it away again: it truncates the body to the first
 * two paragraphs, or replaces it with a teaser, or leaves it in place under a
 * fixed overlay and locks the scroll. By the time a tap arrives, seconds
 * later, the article is already gone from the DOM.
 *
 * So the extraction does not wait to be asked. This script is registered at
 * document start on every WebView, and from the moment the document has a
 * body it keeps extracting, on a handful of scheduled passes and on a
 * throttled MutationObserver — and it KEEPS THE LONGEST RESULT IT HAS EVER
 * SEEN for the current URL. The paywall script's truncation is just another
 * mutation: it produces a shorter extraction, which loses to the one taken
 * before it. Nothing is defeated and nothing is spoofed; the browser simply
 * remembers what the server sent it.
 *
 * The same principle covers the other two shapes a paywalled article arrives
 * in, and both are read alongside the DOM:
 *
 *  * **`ld+json`** — schema.org `articleBody`, which publishers ship for
 *    search engines and almost never truncate, since it is the thing being
 *    indexed. Frequently the full text of an article whose DOM shows three
 *    paragraphs.
 *  * **Hydration blobs** — `__NEXT_DATA__` and the `application/json` islands
 *    every React/Vue framework emits, where the article's body sits as a
 *    string under a plausibly-named key, again ahead of any client-side
 *    metering.
 *
 * Three sources, and the longest wins. That last part matters more than the
 * cleverness of any one of them: an extraction that is a fifth of the length
 * of a competing one is a truncation, whatever produced it.
 *
 * ## What the extraction itself is
 *
 * A compact reimplementation of Mozilla's Readability — the algorithm behind
 * Firefox's own reader — because that algorithm is right and there is no
 * point inventing a worse one. Text-bearing leaves (`p`, `pre`, `td`,
 * `blockquote`, and text-only `div`s for the sites that never learned about
 * `<p>`) score one point plus a point per comma plus a capped bonus for
 * length; each leaf pushes its score up to five ancestors with a divisor per
 * level, so the score accumulates on the container that actually holds the
 * prose; class and id names that read like an article add, ones that read
 * like furniture subtract; and the winner is the highest score discounted by
 * its link density, since a menu is mostly links and an article is mostly
 * not. The winner's siblings come along if they score well themselves, which
 * is what collects an article split into several blocks.
 *
 * Two deliberate departures from Readability, both because of paywalls:
 *
 *  * **Invisibility is not disqualifying.** Readability skips `display: none`
 *    subtrees, which is exactly where a metered article's remaining
 *    paragraphs are put. Hidden text scores here like any other; the link
 *    density and the class-name filters are what keep hidden navigation out,
 *    and they are enough.
 *  * **`hidden` is not in the negative class pattern**, for the same reason —
 *    it is Readability's, and on a metered page it votes against the article.
 *
 * ## How it is drawn
 *
 * Not by rewriting the document. The page is left completely intact
 * underneath — its scripts running, its state where it was, its scroll
 * position kept — and the reader is a `<dialog>` opened with `showModal()`
 * over the top of it. That buys three things no `z-index` can: the top layer,
 * which beats a paywall overlay's `z-index: 2147483647` without having to
 * outbid it; inertness of everything beneath, so the page cannot steal the
 * scroll or the focus; and an exit that is nothing more than closing the
 * dialog, which is why toggling reader off is instant and lossless rather
 * than a reload.
 *
 * Inside the dialog the article lives in a **shadow root**, which is what
 * keeps the site's own stylesheet — `p { font-size: 13px }`, a `* { }` reset,
 * a `body.paywall-locked` rule — from reaching into the reader's typography.
 * The dialog cannot host a shadow root itself (it is not on the spec's list
 * of allowed hosts), so it holds one plain `div` that does.
 */
/**
 * One scroll of an open article, in device pixels: how far it moved and where
 * it now is. The same pair `WebView.onScrollChanged` reports, deliberately —
 * it is read by the same hide-on-scroll rule.
 */
data class ReaderScroll(val deltaY: Int, val scrollY: Int)

internal object ReaderMode {

    private const val BRIDGE_NAME = "__yukuReaderBridge"

    /**
     * Wires the harvester into [web]. Always attached, on every WebView,
     * whether or not the user ever opens the reader: the extraction has to
     * have happened BEFORE the tap, and by the time the tap arrives a metered
     * page has already taken its article back (see the class doc).
     *
     * [onAvailable] is called on the main thread whenever the answer to "is
     * there an article here" changes — that is what the menu row's switch is
     * enabled by, so a page with nothing to read says so rather than offering
     * a toggle that does nothing. [onClosed] fires when the reader goes away
     * without being asked to (the page navigating out from under it, the
     * dialog being dismissed), so the toggle doesn't stay on over a reader
     * that isn't there.
     *
     * [onScroll] is the article's own scroll, in DEVICE pixels and shaped like
     * `WebView.onScrollChanged`'s (delta, absolute) — the reader scrolls a div
     * inside a shadow root, so the WebView itself never moves and the toolbar's
     * hide-on-scroll has nothing to hear. This is that signal, coming the only
     * way it can.
     *
     * [onProgress] is how far down the article the reader is, 0..1, or null
     * for an article too short to scroll. The app draws that indicator on its
     * own chrome — see [refreshProgress] and updateProgress in the script.
     */
    fun attach(
        web: WebView,
        onAvailable: (Boolean) -> Unit,
        onClosed: () -> Unit,
        onScroll: (deltaY: Int, scrollY: Int) -> Unit = { _, _ -> },
        onProgress: (Float?) -> Unit = {},
    ) {
        web.addJavascriptInterface(Bridge(onAvailable, onClosed, onScroll, onProgress), BRIDGE_NAME)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    /**
     * Opens the reader over whatever [web] is showing, set the way
     * [settings] asks for. [dark] is the page's own dark verdict, which is
     * what [ReaderTheme.Auto] resolves against; the two insets (device
     * pixels, the same ones [PageBottomBar] and [PageTopInset] hand the page)
     * keep the first line clear of the overlay's header and the last line
     * clear of the toolbar.
     *
     * The callback is told whether there was in fact anything to open, since
     * a harvest can come up empty on a page that looked promising a moment
     * ago — the caller uses that to leave the toggle off rather than showing
     * "on" over an unchanged page.
     */
    fun open(
        web: WebView,
        settings: ReaderSettings,
        dark: Boolean,
        topInsetPx: Int,
        bottomInsetPx: Int,
        onResult: (Boolean) -> Unit,
    ) {
        web.evaluateJavascript(
            "window.__yukuReaderOpen && window.__yukuReaderOpen({" +
                styleJson(settings, dark) + ",top:$topInsetPx,bottom:$bottomInsetPx})",
        ) { onResult(it == "true") }
    }

    /**
     * A reader setting changed. Restyles the article in place if one is on
     * screen — the scroll position is the one thing a reader holds that the
     * user cannot get back, so nothing here rebuilds it — and is remembered
     * by the page either way, so the next open is set the same.
     *
     * Sent to every tab whose reader is open rather than only the current
     * one: the settings are the browser's, like the page zoom, and a
     * background article that kept the old size would change under the user
     * when they switched to it.
     */
    fun restyle(web: WebView, settings: ReaderSettings, dark: Boolean) {
        web.evaluateJavascript(
            "window.__yukuReaderStyle && window.__yukuReaderStyle({" + styleJson(settings, dark) + "})",
            null,
        )
    }

    /**
     * The four style fields as a JS object body (no braces), shared by
     * [open] and [restyle] so the two can never describe the look
     * differently.
     *
     * [ReaderTheme.Auto] is resolved HERE rather than in the page: "what
     * counts as dark" is the browser's own answer — the tab's override, else
     * the app's page-dark setting — and the page has no way to ask.
     */
    private fun styleJson(settings: ReaderSettings, dark: Boolean): String {
        val pal = when (settings.theme) {
            ReaderTheme.Auto -> if (dark) "dark" else "light"
            ReaderTheme.Light -> "light"
            ReaderTheme.Sepia -> "sepia"
            ReaderTheme.Dark -> "dark"
        }
        return "pal:'" + pal + "'" +
            ",font:'" + settings.font.css.replace("'", "\\'") + "'" +
            ",size:" + settings.textScale +
            ",line:" + settings.spacing.lineHeight
    }

    fun close(web: WebView) {
        web.evaluateJavascript("window.__yukuReaderClose && window.__yukuReaderClose()", null)
    }

    /** The toolbar moved, or the overlay's header did. Cheap, and a no-op when closed. */
    fun setInsets(web: WebView, topPx: Int, bottomPx: Int) {
        web.evaluateJavascript(
            "window.__yukuReaderInset && window.__yukuReaderInset($topPx,$bottomPx)",
            null,
        )
    }

    /**
     * Ask an open reader to report its scroll position again. For a tab coming
     * back to the screen: the app holds no progress for it and no scroll is
     * coming to produce one, so the indicator would be absent until the user
     * moved the article.
     */
    fun refreshProgress(web: WebView) {
        web.evaluateJavascript("window.__yukuReaderProgress && window.__yukuReaderProgress()", null)
    }

    class Bridge internal constructor(
        private val onAvailable: (Boolean) -> Unit,
        private val onClosed: () -> Unit,
        private val onScroll: (deltaY: Int, scrollY: Int) -> Unit = { _, _ -> },
        private val onProgress: (Float?) -> Unit = {},
    ) {
        private val main = Handler(Looper.getMainLooper())

        /** Called from a renderer thread whenever the harvest's verdict flips. */
        @JavascriptInterface
        fun available(value: Boolean) {
            main.post { onAvailable(value) }
        }

        @JavascriptInterface
        fun closed() {
            main.post { onClosed() }
        }

        /** The article scrolled. Device pixels, already converted in the page. */
        @JavascriptInterface
        fun scrolled(deltaY: Int, scrollY: Int) {
            main.post { onScroll(deltaY, scrollY) }
        }

        /**
         * How far through the article, in ten-thousandths — an integer because
         * the bridge is happier with one, and finer than any screen can draw.
         * [scrollable] false means there is nothing to indicate.
         */
        @JavascriptInterface
        fun progress(parts: Int, scrollable: Boolean) {
            val f = if (scrollable) (parts / 10000f).coerceIn(0f, 1f) else null
            main.post { onProgress(f) }
        }
    }

    /**
     * Runs in every frame of every page. Everything it does is wrapped: a
     * page that throws inside our harvest is still a page that has to work,
     * and the harvest runs on a timer where a thrown exception would
     * otherwise take the rest of the passes with it.
     */
    private val SCRIPT = """
(function () {
  if (window.__yukuReaderInstalled) return;
  window.__yukuReaderInstalled = true;

  // The article is in the TOP frame, and only there. This script runs in
  // every frame and the bridge object is injected into every frame too, so a
  // subframe that harvested would answer for the whole tab: an ad or embed
  // frame is always short, so its verdict is always "no article", and it
  // loads AFTER the main document — its false is the last word and takes the
  // page's true with it. That is why the reader looked unavailable on news
  // sites (a dozen ad frames) and fine on a page with none.
  try { if (window.top !== window) return; } catch (e) { return; }

  // Read on each use rather than captured: this script installs at document
  // start, and the interface is a property of the window it is injected into.
  function bridge() { return window.$BRIDGE_NAME; }
  var ROOT_ID = '__yuku_reader__';

  // Total extracted characters below which there is no article here. Mozilla
  // calls a document readerable at a cumulated score of 20 over nodes of 140
  // characters; this is the same judgement made on the finished extraction,
  // which is the thing actually about to be shown.
  var MIN_TEXT = 500;
  // A single leaf has to carry a sentence or so before it counts as prose.
  var MIN_LEAF = 25;

  // Readability's own patterns, with one change in each: `hidden` is gone
  // from NEGATIVE, and the visibility check it implies is gone with it,
  // because a metered page's remaining paragraphs are hidden ones. See the
  // Kotlin doc above.
  var RE_UNLIKELY = /-ad-|ai2html|banner|breadcrumb|combx|comment|community|cover-wrap|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote|masthead|share|newsletter|promo|subscribe|signup|onetrust|consent|cookie|nav-|-nav|navbar/i;
  var RE_MAYBE = /and|article|body|column|content|main|shadow|story|post|entry|text/i;
  var RE_POSITIVE = /article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story|paragraph|prose/i;
  var RE_NEGATIVE = /-ad-|banner|combx|comment|com-|contact|foot|footer|footnote|gdpr|masthead|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|widget|social|newsletter|subscribe|teaser|trending|recirc|toolbar|breadcrumb/i;
  // Elements that are furniture whatever they are called.
  var DROP_TAGS = 'script,style,link,meta,noscript,form,input,select,textarea,button,object,embed,canvas,svg,nav,aside,dialog,template,iframe,ins,label,fieldset';
  // What survives sanitising. Everything else is unwrapped (its children kept)
  // rather than deleted, so an article wrapped in a custom element or six
  // nested layout divs does not come out empty.
  var KEEP_TAGS = {
    P:1, BR:1, HR:1, H1:1, H2:1, H3:1, H4:1, H5:1, H6:1, BLOCKQUOTE:1, Q:1,
    PRE:1, CODE:1, KBD:1, SAMP:1, EM:1, I:1, STRONG:1, B:1, U:1, S:1, SUP:1,
    SUB:1, MARK:1, SMALL:1, CITE:1, ABBR:1, TIME:1, A:1, UL:1, OL:1, LI:1,
    DL:1, DT:1, DD:1, TABLE:1, THEAD:1, TBODY:1, TFOOT:1, TR:1, TD:1, TH:1,
    CAPTION:1, FIGURE:1, FIGCAPTION:1, IMG:1, PICTURE:1, SOURCE:1, VIDEO:1,
    AUDIO:1, TRACK:1, SPAN:1, DIV:1
  };
  // Attributes worth carrying over. Everything else — class, id, style, every
  // data-*, every on* handler — is dropped, which is most of what makes the
  // reader immune to the page it came from.
  var KEEP_ATTRS = {
    href:1, src:1, srcset:1, sizes:1, alt:1, title:1, colspan:1, rowspan:1,
    datetime:1, cite:1, controls:1, poster:1, start:1, reversed:1, dir:1,
    lang:1, width:1, height:1
  };
  // Where a lazy-loaded image keeps its real source. Read before the
  // attributes are stripped: the reader draws the article outside whatever
  // IntersectionObserver was going to fill these in, so an untouched lazy
  // image would be a permanent blank.
  var LAZY_SRC = ['data-src', 'data-original', 'data-lazy-src', 'data-hi-res-src', 'data-full-src', 'data-image-src'];
  var LAZY_SET = ['data-srcset', 'data-lazy-srcset', 'data-responsive-src'];

  // ------------------------------------------------------------------ state

  // The best extraction seen so far for the CURRENT url, which is the whole
  // paywall story: a later, shorter harvest never replaces an earlier, longer
  // one. Reset when the location changes, since an SPA route change is a
  // different article and the old body must not be offered for it.
  var best = null;
  var bestUrl = '';
  var announced = null;
  var passes = 0;
  var lastHarvest = 0;
  var harvestTimer = 0;
  var openState = null;
  var insetTop = 0;
  var insetBottom = 0;
  // The last progress reported, so a re-report costs nothing when nothing
  // moved — updateProgress runs on every scroll event and on every resize of
  // a document whose images are still landing.
  var lastParts = -1;

  function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }

  function classId(el) {
    var c = el.className;
    if (c && typeof c !== 'string') c = c.baseVal || '';
    return (c || '') + ' ' + (el.id || '');
  }

  function linkDensity(el) {
    var len = norm(el.textContent).length;
    if (!len) return 0;
    var links = el.getElementsByTagName('a');
    var linked = 0;
    for (var i = 0; i < links.length; i++) {
      var href = links[i].getAttribute('href') || '';
      // An in-page anchor is a footnote marker, not navigation away.
      var weight = href.charAt(0) === '#' ? 0.3 : 1;
      linked += norm(links[i].textContent).length * weight;
    }
    return linked / len;
  }

  function tagScore(el) {
    var t = el.tagName;
    if (t === 'ARTICLE' || t === 'MAIN') return 10;
    if (t === 'DIV' || t === 'SECTION') return 5;
    if (t === 'PRE' || t === 'TD' || t === 'BLOCKQUOTE') return 3;
    if (t === 'ADDRESS' || t === 'OL' || t === 'UL' || t === 'DL' || t === 'DD' ||
        t === 'DT' || t === 'LI' || t === 'FORM') return -3;
    if (t === 'TH' || /^H[1-6]$/.test(t)) return -5;
    return 0;
  }

  function initScore(el) {
    var s = tagScore(el);
    var cls = classId(el);
    if (RE_NEGATIVE.test(cls)) s -= 25;
    if (RE_POSITIVE.test(cls)) s += 25;
    return s;
  }

  /** True for a node sitting under something that reads like furniture. */
  function unlikely(el) {
    var n = el, depth = 0;
    while (n && n.nodeType === 1 && depth < 12) {
      if (n.tagName === 'ARTICLE' || n.tagName === 'MAIN') return false;
      var cls = classId(n);
      if (cls.length > 1 && RE_UNLIKELY.test(cls) && !RE_MAYBE.test(cls)) return true;
      if (n.getAttribute && n.getAttribute('role') === 'navigation') return true;
      n = n.parentElement;
      depth++;
    }
    return false;
  }

  // -------------------------------------------------------- DOM extraction

  function domExtract() {
    var body = document.body;
    if (!body) return null;

    // The text-bearing leaves. A `div` counts only when it holds no block of
    // its own — that is the shape of a site that writes paragraphs as divs,
    // and it keeps a layout wrapper from being scored as if it were prose.
    var leaves = [];
    var all = body.getElementsByTagName('*');
    for (var i = 0; i < all.length && i < 12000; i++) {
      var el = all[i];
      var t = el.tagName;
      if (t === 'P' || t === 'PRE' || t === 'BLOCKQUOTE' || t === 'TD') leaves.push(el);
      else if (t === 'DIV' && !el.querySelector('div,p,section,article,ul,ol,table,pre,blockquote,figure')) leaves.push(el);
    }

    var scores = [];
    var scored = new Map();
    function bump(el, by) {
      if (!scored.has(el)) { scored.set(el, initScore(el)); scores.push(el); }
      scored.set(el, scored.get(el) + by);
    }

    for (var j = 0; j < leaves.length; j++) {
      var leaf = leaves[j];
      var text = norm(leaf.textContent);
      if (text.length < MIN_LEAF) continue;
      if (unlikely(leaf)) continue;
      var base = 1 + (text.split(',').length - 1) + Math.min(Math.floor(text.length / 100), 3);
      var anc = leaf.parentElement, level = 0;
      while (anc && anc !== document.documentElement && level < 5) {
        var divisor = level === 0 ? 1 : (level === 1 ? 2 : level * 3);
        bump(anc, base / divisor);
        anc = anc.parentElement;
        level++;
      }
    }
    if (!scores.length) return null;

    var ranked = [];
    for (var k = 0; k < scores.length; k++) {
      var cand = scores[k];
      if (cand === document.body || cand === document.documentElement) continue;
      // A menu scores well on raw text and badly here, which is the point.
      scored.set(cand, scored.get(cand) * (1 - linkDensity(cand)));
      ranked.push(cand);
    }
    if (!ranked.length) return null;
    ranked.sort(function (a, b) { return scored.get(b) - scored.get(a); });
    var top = ranked[0];
    var topScore = scored.get(top);

    // The score only travels five levels up from the paragraph that earned
    // it, which is Readability's own limit and is not enough on a site that
    // wraps every paragraph in three divs of its own — a styled-components
    // news page nests the article's own text eight or ten levels deep, so
    // nothing above the per-paragraph wrapper accumulates and the "article"
    // comes out as one paragraph. What identifies the real container there is
    // that it HOLDS several of the strong candidates: the runners-up are the
    // article's other paragraphs, in wrappers exactly like the winner's. So
    // walk up from the winner until three of them are underneath, which is
    // Readability's own alternative-candidate rule and the reason this shape
    // works in Firefox.
    var alts = [];
    for (var n = 1; n < ranked.length && n < 5; n++) {
      if (topScore > 0 && scored.get(ranked[n]) / topScore >= 0.75) alts.push(ranked[n]);
    }
    if (alts.length >= 3) {
      var wrap = top.parentElement;
      while (wrap && wrap !== document.body) {
        var holds = 0;
        for (var w = 0; w < alts.length; w++) if (wrap.contains(alts[w])) holds++;
        if (holds >= 3) { top = wrap; break; }
        wrap = wrap.parentElement;
      }
      if (!scored.has(top)) scored.set(top, initScore(top));
      topScore = scored.get(top);
    }

    // And then the ordinary climb: a container that scored BETTER than the
    // winner is the winner's own article body, one wrapper out.
    var up = top.parentElement, lastScore = topScore, floor = topScore / 3;
    while (up && up !== document.body && up !== document.documentElement) {
      if (!scored.has(up)) { up = up.parentElement; continue; }
      var s = scored.get(up);
      if (s < floor) break;
      if (s > lastScore) { top = up; topScore = s; break; }
      lastScore = s;
      up = up.parentElement;
    }

    // A candidate that is its parent's only child is really the parent —
    // otherwise the sibling pass below has nothing to look at.
    while (top.parentElement && top.parentElement !== document.body &&
           top.parentElement.children.length === 1) {
      top = top.parentElement;
    }

    var container = document.createElement('div');
    var threshold = Math.max(10, topScore * 0.2);
    var siblings = top.parentElement ? top.parentElement.children : [top];
    for (var m = 0; m < siblings.length; m++) {
      var sib = siblings[m];
      var take = sib === top;
      if (!take && scored.has(sib) && scored.get(sib) >= threshold) take = true;
      if (!take && sib.tagName === 'P') {
        var st = norm(sib.textContent);
        if (st.length > 80 && linkDensity(sib) < 0.25) take = true;
      }
      if (take) container.appendChild(sib.cloneNode(true));
    }
    if (!container.childNodes.length) container.appendChild(top.cloneNode(true));

    lift(container);
    sanitize(container);
    var len = norm(container.textContent).length;
    if (len < MIN_LEAF * 2) return null;
    return { html: container.innerHTML, len: len, title: pageTitle(), byline: byline() };
  }

  // --------------------------------------------------- structured sources

  function walkJson(value, out, depth) {
    if (depth > 8 || value == null) return;
    if (typeof value === 'string') return;
    if (Array.isArray(value)) {
      for (var i = 0; i < value.length && i < 400; i++) walkJson(value[i], out, depth + 1);
      return;
    }
    if (typeof value !== 'object') return;
    for (var key in value) {
      if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
      var v = value[key];
      if (typeof v === 'string') {
        if (/^(articleBody|body|bodyHtml|bodyHTML|content|contentHtml|contentHTML|text|fullText|storyHtml)${'$'}/i.test(key) &&
            v.length > 800 && v.length > out.len) {
          out.len = v.length;
          out.raw = v;
        }
      } else {
        walkJson(v, out, depth + 1);
      }
    }
  }

  /** Turns a body string — plain text or a fragment of HTML — into an article. */
  function fromRaw(raw, title, who) {
    var holder;
    if (/<(p|div|br|h[1-6]|figure|img)\b/i.test(raw)) {
      // Parsed in an inert document: an off-document `innerHTML` still
      // resolves image URLs in some engines, and nothing here needs loading.
      var doc = document.implementation.createHTMLDocument('');
      doc.body.innerHTML = raw;
      holder = document.createElement('div');
      while (doc.body.firstChild) holder.appendChild(document.adoptNode(doc.body.firstChild));
    } else {
      holder = document.createElement('div');
      var parts = raw.split(/\n\s*\n|\r\n\s*\r\n|\n/);
      for (var i = 0; i < parts.length; i++) {
        var line = parts[i].trim();
        if (!line) continue;
        var p = document.createElement('p');
        p.textContent = line;
        holder.appendChild(p);
      }
    }
    lift(holder);
    sanitize(holder);
    var len = norm(holder.textContent).length;
    if (len < MIN_TEXT) return null;
    return { html: holder.innerHTML, len: len, title: title || pageTitle(), byline: who || byline() };
  }

  function fromJsonLd() {
    var nodes = document.querySelectorAll('script[type="application/ld+json"]');
    var out = { len: 0, raw: '' };
    var title = '', who = '';
    for (var i = 0; i < nodes.length && i < 12; i++) {
      var text = nodes[i].textContent || '';
      if (text.length > 3000000) continue;
      var data;
      try { data = JSON.parse(text); } catch (e) { continue; }
      var stack = [data], seen = 0;
      while (stack.length && seen < 500) {
        var node = stack.pop();
        seen++;
        if (Array.isArray(node)) { for (var a = 0; a < node.length; a++) stack.push(node[a]); continue; }
        if (!node || typeof node !== 'object') continue;
        if (node['@graph']) stack.push(node['@graph']);
        if (typeof node.articleBody === 'string' && node.articleBody.length > out.len) {
          out.len = node.articleBody.length;
          out.raw = node.articleBody;
          if (typeof node.headline === 'string') title = node.headline;
          var au = node.author;
          if (Array.isArray(au)) au = au[0];
          if (au && typeof au === 'object' && typeof au.name === 'string') who = au.name;
          else if (typeof au === 'string') who = au;
        }
      }
    }
    if (!out.raw) return null;
    return fromRaw(out.raw, title, who);
  }

  function fromHydration() {
    var nodes = document.querySelectorAll('script#__NEXT_DATA__,script[type="application/json"]');
    var out = { len: 0, raw: '' };
    for (var i = 0; i < nodes.length && i < 10; i++) {
      var text = nodes[i].textContent || '';
      if (!text || text.length > 3000000) continue;
      var data;
      try { data = JSON.parse(text); } catch (e) { continue; }
      walkJson(data, out, 0);
    }
    if (!out.raw) return null;
    // A hydration blob is the least trustworthy of the three sources — the
    // key names are guessed at, not specified — so it has to look like prose
    // before it is allowed to stand in for an article.
    var sentences = (out.raw.match(/[.!?]["')\]]?\s/g) || []).length;
    if (sentences < 6) return null;
    return fromRaw(out.raw, '', '');
  }

  // ------------------------------------------------------------- cleaning

  /**
   * Pulls a lazy image's real source into `src`/`srcset` before sanitising
   * throws the data attributes away, and promotes a `<noscript>` image — the
   * markup a lazy loader leaves for clients that will not run it, and often
   * the only unblurred copy on the page.
   */
  function lift(root) {
    var imgs = root.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i], k;
      var src = img.getAttribute('src') || '';
      if (!src || /^data:image\/(gif|svg)/i.test(src) || /\bplaceholder\b|\bblank\b|\bspacer\b/i.test(src)) {
        for (k = 0; k < LAZY_SRC.length; k++) {
          var v = img.getAttribute(LAZY_SRC[k]);
          if (v) { img.setAttribute('src', v); break; }
        }
      }
      if (!img.getAttribute('srcset')) {
        for (k = 0; k < LAZY_SET.length; k++) {
          var s = img.getAttribute(LAZY_SET[k]);
          if (s) { img.setAttribute('srcset', s); break; }
        }
      }
    }
    var noscripts = root.querySelectorAll('noscript');
    for (var n = 0; n < noscripts.length; n++) {
      var ns = noscripts[n];
      var inner = ns.textContent || '';
      if (!/<img\b/i.test(inner) || inner.length > 4000) continue;
      var doc = document.implementation.createHTMLDocument('');
      doc.body.innerHTML = inner;
      var replacement = doc.body.querySelector('img');
      if (replacement) ns.parentNode.replaceChild(document.adoptNode(replacement), ns);
    }
  }

  function absolute(url) {
    if (!url) return '';
    try { return new URL(url, document.baseURI).href; } catch (e) { return ''; }
  }

  function absoluteSet(set) {
    return set.split(',').map(function (part) {
      var bits = part.trim().split(/\s+/);
      if (!bits[0]) return '';
      bits[0] = absolute(bits[0]);
      return bits.join(' ');
    }).filter(Boolean).join(', ');
  }

  /**
   * Strips [root] down to prose and pictures, in place. Unknown elements are
   * UNWRAPPED rather than removed — a `<my-paragraph>` or a stack of layout
   * wrappers holds real text, and deleting it would delete the article.
   */
  function sanitize(root) {
    var junk = root.querySelectorAll(DROP_TAGS);
    for (var i = 0; i < junk.length; i++) {
      if (junk[i].parentNode) junk[i].parentNode.removeChild(junk[i]);
    }

    // Named furniture, judged before the class names are thrown away. A
    // block is only dropped when it is mostly links or nearly textless:
    // "share" in a class name is a strong hint and a poor proof, and an
    // article whose wrapper happens to say `post-share-body` must survive it.
    var suspects = root.querySelectorAll('div,section,ul,ol,figure,header,footer,span,p');
    for (var j = 0; j < suspects.length; j++) {
      var el = suspects[j];
      if (!el.parentNode) continue;
      if (!RE_NEGATIVE.test(classId(el))) continue;
      if (RE_POSITIVE.test(classId(el))) continue;
      var text = norm(el.textContent).length;
      if (el.querySelector('img,video,picture') && text < 400) continue;
      if (text < 120 || linkDensity(el) > 0.5) el.parentNode.removeChild(el);
    }

    var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, null);
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);

    for (var k = 0; k < nodes.length; k++) {
      var node = nodes[k];
      if (!node.parentNode) continue;
      if (!KEEP_TAGS[node.tagName]) {
        while (node.firstChild) node.parentNode.insertBefore(node.firstChild, node);
        node.parentNode.removeChild(node);
        continue;
      }
      var attrs = node.attributes;
      for (var a = attrs.length - 1; a >= 0; a--) {
        var name = attrs[a].name;
        if (!KEEP_ATTRS[name.toLowerCase()]) { node.removeAttribute(name); continue; }
        if (name === 'href' || name === 'src' || name === 'poster' || name === 'cite') {
          var abs = absolute(attrs[a].value);
          if (abs) node.setAttribute(name, abs); else node.removeAttribute(name);
        } else if (name === 'srcset') {
          node.setAttribute('srcset', absoluteSet(attrs[a].value));
        }
      }
      if (node.tagName === 'A') { node.setAttribute('target', '_self'); node.setAttribute('rel', 'noreferrer'); }
      // An image the page never got round to filling in is a broken icon in
      // the reader, where nothing is going to fill it in later.
      if (node.tagName === 'IMG' && !node.getAttribute('src') && !node.getAttribute('srcset')) {
        node.parentNode.removeChild(node);
      }
    }

    // Empty shells left over from all of the above.
    for (var e = 0; e < 3; e++) {
      var empties = root.querySelectorAll('p,div,span,li,figure,blockquote,h1,h2,h3,h4,h5,h6');
      var removed = 0;
      for (var f = 0; f < empties.length; f++) {
        var box = empties[f];
        if (!box.parentNode) continue;
        if (norm(box.textContent).length) continue;
        if (box.querySelector('img,video,picture,br,hr,audio')) continue;
        box.parentNode.removeChild(box);
        removed++;
      }
      if (!removed) break;
    }
  }

  // -------------------------------------------------------------- metadata

  function meta(names) {
    for (var i = 0; i < names.length; i++) {
      var el = document.querySelector(names[i]);
      var v = el && (el.getAttribute('content') || el.textContent);
      if (v && norm(v)) return norm(v);
    }
    return '';
  }

  function pageTitle() {
    var t = meta(['meta[property="og:title"]', 'meta[name="twitter:title"]', 'h1']);
    if (!t) t = norm(document.title);
    // Publishers hang the masthead off the title with a separator; the
    // reader already says which site it is, under the headline.
    return t.replace(/\s+[|–—·•]\s+[^|–—·•]{0,40}${'$'}/, '');
  }

  function byline() {
    return meta([
      'meta[name="author"]', 'meta[property="article:author"]',
      '[itemprop="author"] [itemprop="name"]', '[itemprop="author"]',
      '[rel="author"]', '.byline', '.author'
    ]).slice(0, 120);
  }

  function published() {
    var raw = meta([
      'meta[property="article:published_time"]', 'meta[name="date"]',
      'meta[itemprop="datePublished"]', 'time[datetime]'
    ]);
    if (!raw) return '';
    var d = new Date(raw);
    if (isNaN(d.getTime())) return '';
    try {
      return d.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
    } catch (e) { return ''; }
  }

  // -------------------------------------------------------------- harvest

  function consider(result) {
    if (!result || result.len < MIN_LEAF * 2) return;
    // The line the whole paywall story rests on: longer wins, whenever it
    // arrived and whichever source it came from. A truncation is short.
    if (!best || result.len > best.len) best = result;
  }

  function harvest() {
    try {
      if (isOpen()) return;
      var here = location.href;
      if (here !== bestUrl) { bestUrl = here; best = null; passes = 0; }
      if (!document.body) return;
      lastHarvest = Date.now();
      passes++;
      consider(domExtract());
      consider(fromJsonLd());
      consider(fromHydration());
      var ok = !!(best && best.len >= MIN_TEXT);
      if (ok !== announced) {
        announced = ok;
        var b = bridge();
        if (b) { try { b.available(ok); } catch (e) {} }
      }
    } catch (e) {}
  }

  function scheduleHarvest() {
    if (harvestTimer || isOpen()) return;
    var since = Date.now() - lastHarvest;
    var wait = since >= 900 ? 0 : 900 - since;
    harvestTimer = setTimeout(function () { harvestTimer = 0; harvest(); }, wait);
  }

  // The scheduled passes. The early ones are for a page whose article is in
  // its first HTML; the later ones are for one that fetches it. Both are
  // ahead of a reader tap, which is the whole point.
  [0, 250, 800, 1800, 3500, 6000].forEach(function (ms) {
    setTimeout(harvest, ms);
  });
  document.addEventListener('DOMContentLoaded', harvest, true);
  window.addEventListener('load', harvest, true);
  // And every later change, throttled — up to a bound, so a page with a
  // ticker in it is not re-scanned forever.
  try {
    var observer = new MutationObserver(function () {
      if (passes > 60) { observer.disconnect(); return; }
      scheduleHarvest();
    });
    var start = function () {
      if (document.body) observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    };
    if (document.body) start(); else document.addEventListener('DOMContentLoaded', start, true);
  } catch (e) {}

  // ---------------------------------------------------------------- render

  // The three papers. Named rather than a light/dark boolean because sepia
  // is neither: it is a third answer the user can give, and one the page's
  // own dark verdict has no way to express.
  var PALETTES = {
    light: { bg: '#FBF9F6', ink: '#1A1A1A', quiet: '#6B6B6B', rule: '#E4DFD8', link: '#1A4FBF', code: '#F0EDE7' },
    sepia: { bg: '#F4ECD8', ink: '#3A2F21', quiet: '#7A6A55', rule: '#DFD2B8', link: '#1D4E89', code: '#EADFC4' },
    dark:  { bg: '#181818', ink: '#E7E7E7', quiet: '#9A9A9A', rule: '#2E2E2E', link: '#7FB6FF', code: '#222222' }
  };

  // What the reader is currently set in. Kept here rather than passed to
  // every call, so that the settings survive a reader being closed and
  // opened again on the same page without Kotlin having to re-state them —
  // and so that a change made while the reader is open is one property
  // written on the same object the next open reads.
  //
  // The defaults are ReaderSettings' defaults; in practice the first open
  // overwrites all four.
  var look = { pal: 'light', font: '"Noto Serif",Georgia,"Times New Roman",serif', size: 100, line: 1.65 };

  function adoptLook(opts) {
    if (!opts) return;
    if (opts.pal && PALETTES[opts.pal]) look.pal = opts.pal;
    if (opts.font) look.font = opts.font;
    if (opts.size > 0) look.size = opts.size;
    if (opts.line > 0) look.line = opts.line;
  }

  function palette() { return PALETTES[look.pal] || PALETTES.light; }

  function css() {
    var p = palette();
    var bg = p.bg, ink = p.ink, quiet = p.quiet, rule = p.rule, link = p.link, codeBg = p.code;
    // The one absolute size in the sheet; everything else is in em off it,
    // so a scale is one number changed and the whole article keeps its
    // proportions — headings, captions, code and the measure alike.
    var base = (19 * look.size / 100);
    return [
      ':host{all:initial;}',
      '*{box-sizing:border-box;}',
      '.scroll{position:absolute;inset:0;overflow-y:auto;overflow-x:hidden;background:' + bg + ';-webkit-overflow-scrolling:touch;overscroll-behavior:contain;}',
      '.page{max-width:40em;margin:0 auto;padding:28px 22px 40px;color:' + ink + ';',
      'font-family:' + look.font + ';font-size:' + base.toFixed(2) + 'px;line-height:' + look.line + ';',
      '-webkit-text-size-adjust:100%;word-wrap:break-word;}',
      'h1.t{font-size:1.75em;line-height:1.22;margin:0 0 12px;font-weight:700;letter-spacing:-0.01em;}',
      '.meta{font-family:system-ui,-apple-system,"Roboto",sans-serif;font-size:0.72em;line-height:1.5;',
      'color:' + quiet + ';margin:0 0 20px;letter-spacing:0.02em;}',
      '.meta b{font-weight:600;color:' + ink + ';}',
      '.rule{border:0;border-top:1px solid ' + rule + ';margin:0 0 24px;}',
      '.body p{margin:0 0 1.1em;}',
      '.body h1,.body h2,.body h3,.body h4{line-height:1.3;margin:1.6em 0 0.5em;font-weight:700;}',
      '.body h1{font-size:1.4em;} .body h2{font-size:1.28em;} .body h3{font-size:1.14em;} .body h4{font-size:1em;}',
      '.body a{color:' + link + ';text-decoration:underline;text-underline-offset:2px;}',
      '.body img,.body video,.body picture{display:block;max-width:100%;height:auto;margin:1.4em auto;border-radius:8px;}',
      '.body figure{margin:1.6em 0;}',
      '.body figcaption{font-family:system-ui,-apple-system,sans-serif;font-size:0.72em;line-height:1.5;',
      'color:' + quiet + ';text-align:center;margin-top:0.6em;}',
      '.body blockquote{margin:1.4em 0;padding:0 0 0 1em;border-left:3px solid ' + rule + ';color:' + quiet + ';font-style:italic;}',
      '.body ul,.body ol{margin:0 0 1.1em;padding-left:1.4em;}',
      '.body li{margin:0 0 0.4em;}',
      '.body pre{background:' + codeBg + ';padding:12px 14px;border-radius:8px;overflow-x:auto;',
      'font-family:ui-monospace,"Roboto Mono",monospace;font-size:0.8em;line-height:1.5;}',
      '.body code{background:' + codeBg + ';border-radius:4px;padding:0.1em 0.35em;',
      'font-family:ui-monospace,"Roboto Mono",monospace;font-size:0.85em;}',
      '.body pre code{background:none;padding:0;font-size:1em;}',
      '.body table{width:100%;border-collapse:collapse;font-size:0.82em;margin:1.4em 0;display:block;overflow-x:auto;}',
      '.body td,.body th{border:1px solid ' + rule + ';padding:6px 9px;text-align:left;}',
      '.body hr{border:0;border-top:1px solid ' + rule + ';margin:2em 0;}'
    ].join('');
  }

  // The dialog's armour. Inline and !important, because the page's
  // stylesheet can name `dialog` too, and this one element is the only part
  // of the reader that is not behind a shadow boundary.
  //
  // NOTHING is animated on this element, and nothing can be: `all:initial`
  // with !important expands to every longhand there is, so `opacity` on the
  // dialog is `initial !important` — and an important author declaration
  // beats a Web Animation outright. The crossfade therefore runs on the HOST
  // inside it (see HOST_STYLE), whose reset is not important. That is also
  // why the dialog is TRANSPARENT and the paper is painted by `.scroll` in
  // the shadow root: an opaque shell would sit at full alpha under the
  // fading article and there would be nothing to see the page through.
  var SHELL_STYLE =
    'all:initial!important;position:fixed!important;inset:0!important;' +
    'width:100%!important;height:100%!important;max-width:none!important;' +
    'max-height:none!important;margin:0!important;padding:0!important;' +
    'border:0!important;overflow:hidden!important;display:block!important;' +
    'z-index:2147483647!important;background:transparent!important;';

  var HOST_STYLE = 'all:initial;display:block;position:absolute;inset:0;';

  /**
   * The one rule that has to live in the PAGE's stylesheet rather than in the
   * shadow root or on an element: Chromium's UA sheet gives a modal dialog's
   * `::backdrop` a 10% black wash, which is invisible under an opaque reader
   * and a hard step under a fading one. A pseudo-element cannot be reached
   * from an inline style, so this is a `<style>` of our own, removed again on
   * the way out.
   */
  var BACKDROP_CSS = '#' + ROOT_ID + '::backdrop{background:transparent!important;}';

  // ------------------------------------------------------------- motion
  //
  // A CROSSFADE, and nothing else: the reader and the page are two views of
  // the same document, in the same place, at the same size. There is no
  // journey between them to animate — the article does not come from
  // anywhere, it is what the page already said — so the transition is the one
  // dissolving into the other. Movement here would be inventing an arrival
  // that did not happen, and on a full-screen surface any of it that reaches
  // its mark from off-centre uncovers bare page at the edge on the way.
  //
  // The clocks are the app's own (Motion.kt's SURFACE_ENTER_FADE_MS /
  // SURFACE_EXIT_FADE_MS), restated because this runs in the page where none
  // of that file is reachable; keep the two in step. Leaving is shorter than
  // arriving, everywhere in this app: an exit has nothing to settle onto.
  var ENTER_FADE_MS = 240, EXIT_FADE_MS = 160;
  var DECELERATE = 'cubic-bezier(0.05,0.7,0.1,1)';
  var ACCELERATE = 'cubic-bezier(0.3,0,0.8,0.15)';

  function still() {
    try {
      return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch (e) { return false; }
  }

  /**
   * Runs [frames] on [el] and hands back the animation, or null where the
   * platform has no Web Animations (or the user has asked for no motion) —
   * in which case the caller's already-written end state is simply what
   * shows, and everything still works with no movement at all.
   */
  function animate(el, frames, ms, easing) {
    if (!el || typeof el.animate !== 'function' || still() || ms <= 0) return null;
    try {
      return el.animate(frames, { duration: ms, easing: easing, fill: 'both' });
    } catch (e) { return null; }
  }

  function enterAnimation(host) {
    // End state written first, animation started from the old one: after the
    // animation is done the inline value is what remains, so there is no
    // frame where the surface reverts to where it began.
    host.style.opacity = '';
    var running = [];
    var a = animate(host, [{ opacity: 0 }, { opacity: 1 }], ENTER_FADE_MS, DECELERATE);
    if (a) running.push(a);
    return running;
  }

  function stop(list) {
    if (!list) return;
    for (var i = 0; i < list.length; i++) { try { list[i].cancel(); } catch (e) {} }
  }

  function applyInsets() {
    if (!openState) return;
    var dpr = window.devicePixelRatio || 1;
    var page = openState.shadow.querySelector('.page');
    if (!page) return;
    page.style.paddingTop = (28 + insetTop / dpr) + 'px';
    page.style.paddingBottom = (40 + insetBottom / dpr) + 'px';
    // The measure it reports just changed.
    updateProgress();
  }

  /**
   * How far down the article the scroller is, reported to the app — which is
   * what DRAWS the indicator, on its own chrome. The reader could paint a
   * line at the bottom of itself, and did; but the line has to rest on the
   * toolbar's top edge and then on the navigation bar's once the toolbar
   * hides, and neither of those is a measure the page can be told reliably —
   * they are chrome, in the app's own space. So the page reports the one
   * thing only it knows, and the app places the one thing only it can.
   *
   * Not reported at all on an article that does not scroll: a bar that can
   * only ever say "all of it" is answering a question nobody asked. Read off
   * the scroller each time rather than remembered, because the span changes
   * under it as images land and whenever the text size does.
   */
  function updateProgress() {
    if (!openState) return;
    var b = bridge();
    if (!b || !b.progress) return;
    var el = openState.scroller;
    var span = el.scrollHeight - el.clientHeight;
    if (!(span > 4)) {
      if (lastParts !== -1) { lastParts = -1; try { b.progress(0, false); } catch (e) {} }
      return;
    }
    var f = el.scrollTop / span;
    if (!(f > 0)) f = 0;
    if (f > 1) f = 1;
    var parts = Math.round(f * 10000);
    if (parts === lastParts) return;
    lastParts = parts;
    try { b.progress(parts, true); } catch (e) {}
  }

  /**
   * Asked for a fresh report with nothing having moved — a tab coming back to
   * the screen with its reader still open, where the app has no value for it
   * and no scroll is coming to produce one.
   */
  window.__yukuReaderProgress = function () {
    lastParts = -1;
    try { updateProgress(); } catch (e) {}
  };

  window.__yukuReaderInset = function (topPx, bottomPx) {
    insetTop = topPx || 0;
    insetBottom = bottomPx || 0;
    applyInsets();
  };

  /**
   * A setting changed while the article is on screen. Restyling in place
   * rather than rebuilding: the reader is being ADJUSTED, and a rebuild
   * would throw away the scroll position — which is the one thing the reader
   * has that the user cannot get back.
   *
   * Also the way the settings reach a reader that is not open: `look` is
   * what the next open reads.
   */
  window.__yukuReaderStyle = function (opts) {
    try {
      adoptLook(opts);
      if (!openState) return false;
      // The whole look is in the shadow root's one stylesheet, so a restyle
      // is one assignment — and the article's nodes, and its scroll position
      // with them, are never touched.
      openState.style.textContent = css();
      applyInsets();
      return true;
    } catch (e) { return false; }
  };

  window.__yukuReaderOpen = function (opts) {
    try {
      opts = opts || {};
      insetTop = opts.top || 0;
      insetBottom = opts.bottom || 0;
      adoptLook(opts);
      if (openState) {
        // Asked to open one that is halfway out: the article is still there
        // and still scrolled where it was, so it comes back rather than
        // being rebuilt.
        if (openState.leaving) {
          stop(openState.leaving);
          openState.leaving = null;
          // The entrance that first put it here is still forward-filling
          // the property about to be animated again; a stale fill on the same
          // property is one more thing for the compositor to resolve every
          // frame, and there is no reason to keep it.
          stop(openState.entering);
          openState.style.textContent = css();
          openState.entering = enterAnimation(openState.host);
          applyInsets();
        }
        return true;
      }
      // One last look before giving up — a tap can land between passes.
      if (!best || best.len < MIN_TEXT) harvest();
      if (!best || best.len < MIN_TEXT) return false;

      var dlg = document.createElement('dialog');
      dlg.id = ROOT_ID;
      dlg.setAttribute('style', SHELL_STYLE);

      var host = document.createElement('div');
      host.setAttribute('style', HOST_STYLE);
      dlg.appendChild(host);

      var backdrop = document.createElement('style');
      backdrop.textContent = BACKDROP_CSS;
      (document.head || document.documentElement).appendChild(backdrop);
      (document.body || document.documentElement).appendChild(dlg);

      var shadow = host.attachShadow({ mode: 'open' });
      var style = document.createElement('style');
      style.textContent = css();
      shadow.appendChild(style);

      var scroll = document.createElement('div');
      scroll.className = 'scroll';
      // The toolbar hides on scroll, and what it listens to is the WebView's
      // own offset — which never changes here, because the thing scrolling is
      // this div inside a shadow root. So the article reports its own motion
      // in the same shape the view would have: a delta and an absolute
      // position, both in DEVICE pixels, since the threshold it is measured
      // against is in view pixels. Passive: nothing here can cancel a scroll,
      // and a non-passive listener on the scroller would cost a frame.
      var lastTop = 0;
      scroll.addEventListener('scroll', function () {
        try {
          var dpr = window.devicePixelRatio || 1;
          var top = scroll.scrollTop;
          var delta = Math.round((top - lastTop) * dpr);
          lastTop = top;
          updateProgress();
          var b = bridge();
          if (b && b.scrolled) b.scrolled(delta, Math.round(top * dpr));
        } catch (e) {}
      }, { passive: true });
      var page = document.createElement('div');
      page.className = 'page';

      var head = document.createElement('h1');
      head.className = 't';
      head.textContent = best.title || document.title || '';
      page.appendChild(head);

      var bits = [];
      if (best.byline) bits.push('<b>' + best.byline.replace(/[<>&]/g, '') + '</b>');
      var host_ = '';
      try { host_ = location.hostname.replace(/^www\./, ''); } catch (e) {}
      if (host_) bits.push(host_);
      var when = published();
      if (when) bits.push(when);
      var minutes = Math.max(1, Math.round(best.len / 5 / 200));
      bits.push(minutes + ' min read');
      var metaLine = document.createElement('p');
      metaLine.className = 'meta';
      metaLine.innerHTML = bits.join(' &middot; ');
      page.appendChild(metaLine);

      var rule = document.createElement('hr');
      rule.className = 'rule';
      page.appendChild(rule);

      var body = document.createElement('div');
      body.className = 'body';
      body.innerHTML = best.html;
      page.appendChild(body);

      scroll.appendChild(page);
      shadow.appendChild(scroll);


      // The page beneath keeps its scroll position — it is only stopped from
      // scrolling, so leaving the reader lands exactly where entering it did.
      var html = document.documentElement;
      openState = {
        dialog: dlg,
        host: host,
        backdrop: backdrop,
        shadow: shadow,
        style: style,
        scroller: scroll,
        resize: null,
        entering: null,
        leaving: null,
        overflow: html.style.getPropertyValue('overflow'),
        overflowPriority: html.style.getPropertyPriority('overflow')
      };
      html.style.setProperty('overflow', 'hidden', 'important');
      applyInsets();

      // How long the article is is not known at open: images arrive with no
      // dimensions of their own and every one of them lengthens it. Watching
      // the page element is what keeps the indicator honest about a measure
      // that is still settling — and it is bounded by the reader's own life,
      // unlike the harvest's observer.
      try {
        if (window.ResizeObserver) {
          openState.resize = new ResizeObserver(function () { updateProgress(); });
          openState.resize.observe(page);
        }
      } catch (e) {}

      if (typeof dlg.showModal === 'function') {
        // The top layer: above a paywall overlay without having to outbid its
        // z-index, and inert underneath so the page cannot take the scroll.
        try { dlg.showModal(); } catch (e) {}
      }
      // Started after showModal, so the first sampled frame is one the
      // element is actually in the top layer for.
      openState.entering = enterAnimation(host);
      // A dialog closed by anything other than us — Escape, a page script —
      // still has to leave the toggle telling the truth.
      dlg.addEventListener('close', function () {
        if (openState && openState.dialog === dlg) {
          teardown();
          var b = bridge();
          if (b) { try { b.closed(); } catch (e) {} }
        }
      });
      return true;
    } catch (e) {
      return false;
    }
  };

  function teardown() {
    if (!openState) return;
    var state = openState;
    openState = null;
    stop(state.entering);
    stop(state.leaving);
    if (state.resize) { try { state.resize.disconnect(); } catch (e) {} }
    var html = document.documentElement;
    if (state.overflow) html.style.setProperty('overflow', state.overflow, state.overflowPriority);
    else html.style.removeProperty('overflow');
    if (state.dialog.parentNode) state.dialog.parentNode.removeChild(state.dialog);
    if (state.backdrop.parentNode) state.backdrop.parentNode.removeChild(state.backdrop);
  }

  /**
   * The exit. The dialog is kept in the DOM, still modal and still holding
   * the page's scroll, until the animation has finished — so the article
   * fades out over the page it came from instead of being cut to it.
   *
   * The state is reported as closed the moment this is called, not when the
   * animation lands: `readerActive` is what the menu's switch draws, and a
   * switch that waits 200ms to move is a switch that did not take the tap.
   */
  window.__yukuReaderClose = function () {
    try {
      if (!openState || openState.leaving) return false;
      var state = openState;
      var dlg = state.dialog;
      var done = function () {
        // Only if this is still the same reader: an open during the exit
        // takes `leaving` back off, and this must not then tear down the
        // article the user is looking at again.
        if (openState !== state || !state.leaving) return;
        teardown();
        if (typeof dlg.close === 'function') { try { dlg.close(); } catch (e) {} }
      };
      var running = [];
      var a = animate(state.host, [{ opacity: 1 }, { opacity: 0 }], EXIT_FADE_MS, ACCELERATE);
      if (a) running.push(a);
      state.leaving = running;
      if (!running.length) { state.leaving = true; done(); return true; }
      running[running.length - 1].onfinish = done;
      // A cancelled animation (the tab going away mid-exit) must not leave
      // the dialog on the page forever.
      running[running.length - 1].oncancel = function () {};
      return true;
    } catch (e) { return false; }
  };

  function isOpen() { return !!openState && !openState.leaving; }

  window.__yukuReaderIsOpen = isOpen;
})();
"""
}
