package com.yuku.browser.core

/**
 * Page dark mode: what the browser does to a site that has no dark theme of
 * its own.
 *
 * ## Why this isn't Chromium's algorithmic darkening
 *
 * `setAlgorithmicDarkeningAllowed(true)` is documented to do nothing unless
 * the app's theme reports `android:isLightTheme="false"`, and it hands the
 * whole look over to Chromium with no way to tune it. What this app uses that
 * theme attribute for instead is the *other* half of dark mode — telling
 * sites the device is dark so their own dark theme kicks in (see
 * `Theme.Browser.PageDark` and [BrowserViewModel.webViewContext]). Sites that
 * have one are then left completely alone; only the rest reach the filter
 * below.
 *
 * ## How the filter works
 *
 * The near-universal approach (Dark Reader's "filter" mode, and every
 * one-liner dark-mode bookmarklet) is `invert(1) hue-rotate(180deg)` on the
 * root element. Inversion flips light to dark, and the hue rotation is there
 * to undo the hue flip inversion causes — otherwise every blue link comes out
 * orange. The trouble is that CSS `hue-rotate` is a linear approximation, so
 * colors come back noticeably darker and muddier than they went in, and pure
 * inversion drives white to #000 — a pitch-black page that Material's dark
 * theme guidance specifically warns against, since black kills the sense of
 * depth and maximizes eye strain against light text.
 *
 * So instead of two approximate passes this uses one exact `feColorMatrix`.
 * Its color part is the hue-preserving inversion
 *
 *     R' = R - G - B      G' = -R + G - B      B' = -R - G + B     (+1)
 *
 * which flips light and dark while leaving hue where it was: white goes to
 * black, a saturated red stays red, and a #0645AD link comes out a legible
 * #218CFF rather than muddy orange. Rather than landing on 0 and 1, the whole
 * range is then squeezed into [0.094, 0.906] — a #181818 page under #E7E7E7
 * text, the same soft near-black/off-white pair Dark Reader settled on
 * (#181A1B / #E8E6E3). Folding the squeeze into the same matrix keeps it one
 * GPU pass:
 *
 *     c' = 0.812 * (M . c) + 0.906
 *
 * Both filters declare `color-interpolation-filters="sRGB"`. SVG filters
 * default to linearRGB, which would apply all of this in the wrong space and
 * wash the result out.
 *
 * ## Keeping content intact
 *
 * Photos, video and canvas are pictures, not color choices — inverting them
 * is how you get a negative of someone's face. They get a second filter that
 * is the algebraic inverse of the first, so passing through both lands them
 * back where they started (the endpoints land at 0.094/0.906 rather than
 * exactly 0/1, so an image's whites match the page's whites instead of
 * glaring against them).
 *
 * Filters compose down the tree, so re-inverting a container un-inverts
 * everything inside it. That's why the background-image scan only tags small
 * elements — tagging a full-bleed hero or `body` would revert the whole page
 * to light — and why images nested inside a tagged element are excluded:
 * they're already back at their original colors by way of their ancestor.
 * **A childless element is not automatically small.** An empty full-bleed
 * `<div>` carrying its picture as a `background-image` is one of the most
 * common shapes on the web, and an ad slot is another; whatever the reason
 * for the size, un-inverting most of the viewport reads as the page flipping
 * back to light rather than as a photo being spared, so the area cap applies
 * to leaves too — just at a looser threshold, since a leaf really is more
 * likely to be a picture than a container is.
 *
 * The same tag carries the other thing that must not be inverted: **a section
 * that is already dark**. The page-level rule — inverting something dark is
 * how you end up with a light page — does not stop at the document, and the
 * filter cannot tell a dark document from a dark band inside a light one.
 * zdnet.com's footer is 1560px of #080A12, its nav is another 231, and in dark
 * mode both came back as full screens of pale lavender. Here the area cap runs
 * the OTHER WAY from the picture case: only surfaces at SECTION SCALE qualify
 * (a quarter of the viewport and up), because below that the treatment is
 * wrong. A dark button on a white card has to become a light button on a dark
 * card — contrast with the surface around it is the thing it is for, and
 * keeping it dark is how it disappears — whereas a band the size of the
 * viewport has no surface around it. It *is* the surface, so it carries its
 * own colour scheme and keeps it.
 *
 * ## Frames
 *
 * The document-start script is registered for every frame, because that is
 * the only way to reach content the top document cannot see. But **a filter
 * on the top document already covers every iframe under it** — the frame is
 * rasterised and the ancestor filter applies to the result — so a subframe
 * that darkens itself as well is inverted TWICE, and an ad in a white iframe
 * comes back white: a light slab occupying half of an otherwise dark page,
 * which is exactly what it looked like on zdnet.com. A subframe therefore
 * installs the picture-preserving half only (`img`, `video`, `canvas`), so
 * its photos survive the ancestor's inversion, and never the page filter or
 * the white canvas.
 *
 * That leaves the subframe needing to know something it cannot read
 * cross-origin: whether the top document stood down. If it did, nothing is
 * inverting the frame and re-inverting its images is a negative. So the
 * verdict is broadcast down the frame tree with `postMessage` (each frame
 * passing it on to its own children), and a subframe holds its filter off
 * until it hears one.
 *
 * ## Not fighting the site
 *
 * A page that is already dark is left alone entirely; inverting one is how you
 * end up with a white page in dark mode. That check is what makes this safe to
 * run alongside the `prefers-color-scheme: dark` signal — when a site responds
 * to it with its own dark theme, the filter sees a dark background and stands
 * down.
 *
 * Five things about that check are load-bearing, and each of them is a site
 * that got themed over its own dark theme — or flickered between the two —
 * before it was there:
 *
 *  * **It measures with our own stylesheet switched off.** The filter's CSS
 *    forces `html { background-color: #fff }` — the canvas colour has to be
 *    something for the invert to land on — so reading the root's background
 *    back while that rule is live reads *our* white and concludes the site is
 *    light, forever. The detection is self-reinforcing, which is why a site
 *    that turns dark a moment after load (the usual shape: the theme comes
 *    from a class the client-side script sets) could never be noticed. Every
 *    measurement therefore runs with the style element `disabled`, inside one
 *    task, so nothing is painted in between.
 *
 *  * **It reads the site's own theme flags first.** `html[dark]` is exactly
 *    what YouTube sets, and `data-theme="dark"` and friends are what most of
 *    the rest set; these are true before first paint, so honouring them is
 *    what keeps a themed site from flashing inverted for a frame. A stated
 *    `dark` is answered as stated and nothing further is measured; a `light`
 *    flag is still not a veto — it only stops the search for `dark` in the
 *    same string, and the page is measured as usual.
 *
 *  * **The document's own canvas outranks what is on top of it.** `html` and
 *    `body`'s background is the one surface an overlay cannot move: a
 *    full-screen ad, a consent modal's scrim, a sticky video, a dark hero
 *    photo under the sample points — each of them is most of the viewport for
 *    a moment and none of them is the page's colour scheme. Reading them as
 *    one was the flicker: the verdict was recomputed on every observer tick,
 *    so an interstitial arriving stood the filter down and the whole page
 *    went light until it left.
 *
 *  * **It samples where the page is actually painted** only when the canvas
 *    says nothing. On m.youtube.com — and on any app-shell site — `body` is
 *    transparent and the background belongs to a container several levels in
 *    (`ytm-app`), so a canvas read finds nothing on either root box. Points
 *    across the viewport are hit-tested and the first ancestor of each with
 *    an opaque background is what votes — with `fixed`/`sticky` boxes and
 *    iframes passed OVER on the way up rather than counted, since a scrim, a
 *    sticky player and an ad frame are all things laid on the page rather
 *    than the page. Passed over, not abandoned: the chain they hang off is
 *    still the shell, so it keeps the vote it had.
 *
 *  * **Nothing painted is an answer, not a silence.** A document whose `html`
 *    and `body` are transparent and whose sampled points find no painted
 *    surface above them is not unmeasurable — it is showing the UA's own
 *    canvas, which is white, which is why the filter has to paint one for the
 *    invert to land on. Reporting "nothing to say" there is what left
 *    zdnet.com — an ordinary document that paints no background anywhere —
 *    with a null verdict for as long as it was open, and a null verdict is a
 *    door that never closes: the settle window below only freezes a verdict
 *    that exists. Scrolling into that 1560px footer, at any point in the life
 *    of the page, was then a viewport measuring dark, and the filter stood
 *    down for good — a near-black page under near-black text, which is what
 *    "dark mode is broken on zdnet" looked like. A page that genuinely cannot
 *    be measured (no viewport, no layout yet) latches light once the window
 *    is past, for the same reason.
 *
 * A theme flip is caught by a `MutationObserver` on the two elements that
 * carry those flags, so switching a site's own dark mode on stands the filter
 * down in the same frame rather than up to a poll away. But a verdict reached
 * by measurement is **latched**, and only the first few seconds of a
 * document's life can change it — the shape it is guarding against is a site
 * that themes itself late, which happens once, not a page whose viewport
 * keeps changing colour for as long as it is open. After that window only a
 * theme flag can flip it, and a flag is the site speaking rather than the
 * page being measured.
 */
internal object PageDarkening {

    private const val STYLE_ID = "__yuku_page_dark__"
    private const val SVG_ID = "__yuku_page_dark_svg__"
    private const val KEEP_ATTR = "data-yuku-dark-keep"

    /**
     * Registered as a document-start script (so it runs before the page's own
     * scripts, on every navigation and in every frame) and also evaluated
     * directly when the toggle flips, so the page on screen changes without a
     * reload. Idempotent: a second run just re-arms the one installed copy.
     */
    val ENABLE = """
(function () {
  var ID = '$STYLE_ID';
  var SVG_ID = '$SVG_ID';
  var KEEP = '$KEEP_ATTR';
  var DARK_F = '__yuku_dark_filter__';
  var KEEP_F = '__yuku_keep_filter__';
  var TOP = window.parent === window;
  // How long a measured verdict stays open to revision. Long enough for a
  // site that themes itself from script (the reason the vote exists at all),
  // short enough that an ad arriving later cannot reopen it.
  var SETTLE_MS = 10000;
  // Where light stops and dark starts, for a document and for one surface
  // inside it alike.
  var DARK_L = 0.35;

  // c' = 0.812 * (hue-preserving invert of c) + 0.906  ->  white lands on
  // #181818, black on #E7E7E7, hues stay put.
  var DARK_MATRIX =
    ' 0.812 -0.812 -0.812 0 0.906' +
    ' -0.812  0.812 -0.812 0 0.906' +
    ' -0.812 -0.812  0.812 0 0.906' +
    ' 0 0 0 1 0';
  // The exact inverse of the above, for content that should survive the trip.
  var KEEP_MATRIX =
    ' 0 -0.6158 -0.6158 0 1.1158' +
    ' -0.6158 0 -0.6158 0 1.1158' +
    ' -0.6158 -0.6158 0 0 1.1158' +
    ' 0 0 0 1 0';

  var KEEP_CSS =
    'img,video,canvas,embed,object,[' + KEEP + ']' +
    '{filter:url(#' + KEEP_F + ') !important}' +
    '[' + KEEP + '] img,[' + KEEP + '] video,[' + KEEP + '] canvas,[' + KEEP + '] embed,' +
    '[' + KEEP + '] object,[' + KEEP + '] [' + KEEP + ']{filter:none !important}';
  // The top document inverts everything and paints the canvas the invert
  // lands on; a subframe is inverted by its ancestor and does neither.
  var CSS = TOP
    ? 'html{filter:url(#' + DARK_F + ') !important;background-color:#fff !important}' + KEEP_CSS
    : KEEP_CSS;

  if (window.__yukuPageDark) {
    window.__yukuPageDark.on = true;
    window.__yukuPageDark.since = Date.now();
    window.__yukuPageDark.verdict = null;
    window.__yukuPageDark.apply();
    return;
  }

  var SVG_NS = 'http://www.w3.org/2000/svg';

  function filterEl(id, matrix) {
    var f = document.createElementNS(SVG_NS, 'filter');
    f.setAttribute('id', id);
    // SVG filters interpolate in linearRGB by default, which is the wrong
    // space for this and washes everything out.
    f.setAttribute('color-interpolation-filters', 'sRGB');
    var m = document.createElementNS(SVG_NS, 'feColorMatrix');
    m.setAttribute('type', 'matrix');
    m.setAttribute('values', matrix);
    f.appendChild(m);
    return f;
  }

  function ensureFilters(parent) {
    var svg = document.getElementById(SVG_ID);
    if (!svg) {
      svg = document.createElementNS(SVG_NS, 'svg');
      svg.setAttribute('id', SVG_ID);
      svg.setAttribute('aria-hidden', 'true');
      svg.setAttribute('style', 'position:absolute;width:0;height:0;overflow:hidden;pointer-events:none');
      svg.appendChild(filterEl(DARK_F, DARK_MATRIX));
      svg.appendChild(filterEl(KEEP_F, KEEP_MATRIX));
    }
    if (svg.parentNode !== parent) parent.appendChild(svg);
  }

  function luminance(color) {
    var m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,/\s]+([\d.]+))?/.exec(color || '');
    if (!m) return -1;
    // A transparent background says nothing about how the page renders.
    if (m[4] !== undefined && parseFloat(m[4]) < 0.5) return -1;
    return (0.2126 * +m[1] + 0.7152 * +m[2] + 0.0722 * +m[3]) / 255;
  }

  // Our own stylesheet forces the root white so the invert has something to
  // land on -- which is also exactly what would be read back as "this site is
  // light". Every measurement runs with it off. Synchronous, within one task,
  // so no frame is painted un-darkened.
  function measured(fn) {
    var el = document.getElementById(ID);
    var live = !!el && !el.disabled;
    if (live) el.disabled = true;
    try {
      return fn();
    } finally {
      if (live) el.disabled = false;
    }
  }

  // Tri-state: true dark, false light, null nothing said. A 'light' flag is
  // deliberately NOT a veto, only a reason to stop looking for 'dark' in the
  // same string ('light-theme' next to a stray 'dark' in some other class) --
  // it returns null and lets the page be measured, because a stale light
  // class on a site painting dark is common and a wrong stand-down is the
  // expensive direction.
  function flagged(el) {
    if (!el) return null;
    // YouTube's own signal, set on <html> before first paint.
    if (el.hasAttribute('dark')) return true;
    var flags = (el.getAttribute('data-theme') || '') + ' ' +
      (el.getAttribute('data-color-mode') || '') + ' ' +
      (el.getAttribute('data-bs-theme') || '') + ' ' +
      (el.getAttribute('data-color-scheme') || '') + ' ' +
      (el.className && el.className.baseVal !== undefined ? el.className.baseVal : (el.className || ''));
    flags = (' ' + flags + ' ').toLowerCase();
    if (/[\s_-]light\b|\blight[-_]?(theme|mode)/.test(flags)) return null;
    if (/\bdark\b|dark[-_]?(theme|mode)|\bnight\b|\btheme[-_]dark\b/.test(flags)) return true;
    return null;
  }

  // A site that says it FOLLOWS the system -- GitHub's Primer writes
  // data-color-mode="auto" with the dark theme it will pick beside it -- has
  // given its answer before its stylesheet exists, provided the question is
  // the one its CSS will ask. matchMedia is that question, and it is also
  // right when the filter is on but the WebView was built light.
  function followsDarkSystem(el) {
    if (!el || (el.getAttribute('data-color-mode') || '').toLowerCase() !== 'auto') return false;
    if ((el.getAttribute('data-dark-theme') || '').toLowerCase().indexOf('dark') === -1) return false;
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  // Whether this document has painted a frame yet. Before it has, a page
  // with nothing painted on it is a page whose stylesheets have not arrived,
  // not one showing the UA's white.
  function painted() {
    try {
      return performance.getEntriesByType('paint').length > 0;
    } catch (e) {
      return true;
    }
  }

  // What the site declares, from the two cheapest and most authoritative
  // sources. Neither is affected by our stylesheet, so this costs no
  // disable/enable round trip.
  function declared() {
    var root = document.documentElement;
    if (!root) return null;
    var f = flagged(root);
    if (f === null) f = flagged(document.body);
    if (f !== null) return f;
    if (followsDarkSystem(root)) return true;
    var scheme = getComputedStyle(root).colorScheme || '';
    // A dark-ONLY declaration is a promise; 'light dark' only means the site
    // follows the UA, and whether it went dark shows up in what it paints.
    // 'light' alone is left to be measured for the reason a light flag is.
    if (scheme.indexOf('dark') !== -1 && scheme.indexOf('light') === -1) return true;
    return null;
  }

  // The colour of the document's own canvas. This is the one surface nothing
  // laid over the page can move, which is why it is asked before the viewport
  // is sampled: an ad, a modal scrim or a sticky player is most of the screen
  // for a moment and none of them is the site's colour scheme.
  function canvasDarkness() {
    var root = document.documentElement;
    var l = root ? luminance(getComputedStyle(root).backgroundColor) : -1;
    if (l < 0 && document.body) l = luminance(getComputedStyle(document.body).backgroundColor);
    return l;
  }

  // The luminance of the first ancestor of `el` that actually paints a
  // background. An app shell hangs its colour several levels below <body>,
  // so starting from a hit-tested node and walking UP is the only way to
  // find the surface the user is looking at.
  //
  // A fixed or sticky box is passed OVER rather than counted, and the walk
  // carries on through its ancestors: what is floating above the page is not
  // the page, but the chain it hangs off still is, so a modal scrim gets no
  // vote while the shell underneath it keeps the one it had. Abandoning the
  // point instead would leave an app shell with a sticky header reading as
  // nothing painted at all, which falls through to 'light' and inverts a
  // dark page.
  function surfaceLuminance(el) {
    for (var i = 0; el && i < 24; i++, el = el.parentElement) {
      var cs = getComputedStyle(el);
      var pos = cs.position;
      if (pos === 'fixed' || pos === 'sticky') continue;
      var l = luminance(cs.backgroundColor);
      if (l >= 0) return l;
    }
    return -1;
  }

  // The page as painted, voted on across the viewport rather than read off
  // one element: a light card over a dark page shouldn't decide this on its
  // own. Only reached when the canvas itself is transparent.
  function paintedDarkness() {
    var w = window.innerWidth, h = window.innerHeight;
    if (!w || !h || !document.elementFromPoint) return -1;
    var pts = [
      [w * 0.5, h * 0.5], [w * 0.5, h * 0.85],
      [w * 0.08, h * 0.35], [w * 0.92, h * 0.35],
      [w * 0.08, h * 0.7], [w * 0.92, h * 0.7]
    ];
    var dark = 0, seen = 0, hit = 0;
    for (var i = 0; i < pts.length; i++) {
      var el = document.elementFromPoint(pts[i][0], pts[i][1]);
      if (!el || el === document.documentElement) continue;
      var tag = el.tagName;
      // An ad's own document is not this one's colour scheme, and its frame
      // box tells us nothing about what it paints inside -- an ad script
      // painting its own frame white would otherwise vote as the page. The
      // walk starts above it, on the slot the page put it in.
      if (tag === 'IFRAME' || tag === 'FRAME' || tag === 'EMBED' || tag === 'OBJECT') {
        el = el.parentElement;
        if (!el) continue;
      }
      hit++;
      var l = surfaceLuminance(el);
      if (l < 0) continue;
      seen++;
      if (l < DARK_L) dark++;
    }
    // Points that landed on real content and found no painted surface above
    // them are not a failed measurement -- they are the measurement. What
    // shows through a chain of transparent boxes is the UA's own canvas, and
    // the UA's own canvas is white, which is why our stylesheet has to paint
    // one for the invert to land on. Answering 'nothing to say' here is what
    // left zdnet.com -- a document that paints no background anywhere -- with
    // no verdict at all for as long as it was open, and so permanently open
    // to being flipped by the first dark thing to fill the viewport.
    // Until the first frame, though, that is the stylesheets not having
    // arrived, and latching it would make the site's real dark canvas a
    // DISAGREEING measurement that has to repeat -- one frame painted inverted.
    if (seen === 0) return hit === 0 || !painted() ? -1 : 0;
    return dark / seen;
  }

  // The latched answer to "is this site already dark". A stated theme is
  // honoured whenever it appears; a MEASURED one is settled once and then
  // left alone, because the thing worth re-measuring for -- a site that
  // themes itself from script -- happens in the first seconds, and
  // everything that happens later is the viewport changing rather than the
  // site. Within that window a disagreeing measurement has to repeat before
  // it is believed, so one unlucky frame cannot flip the page.
  function alreadyDark() {
    var state = window.__yukuPageDark;
    var say = declared();
    if (say !== null) {
      state.verdict = say;
      state.disagree = 0;
      return say;
    }
    var settling = Date.now() - state.since < SETTLE_MS;
    if (state.verdict !== null && !settling) return state.verdict;
    var vote = measured(function () {
      var l = canvasDarkness();
      if (l >= 0) return l < DARK_L ? 1 : 0;
      return paintedDarkness();
    });
    if (vote < 0) {
      // Nothing could be measured at all -- no viewport, or a document with
      // no layout yet. Light is the answer either way, and once the window is
      // past it becomes the LATCHED one: a verdict that was never reached is
      // a door left open for the life of the document, and the thing that
      // walks through it is the viewport changing rather than the site.
      if (!settling) state.verdict = false;
      return state.verdict === null ? false : state.verdict;
    }
    var dark = vote >= 0.6;
    if (state.verdict === null || dark === state.verdict) {
      state.verdict = dark;
      state.disagree = 0;
    } else if (++state.disagree >= 2) {
      state.verdict = dark;
      state.disagree = 0;
    }
    return state.verdict;
  }

  function keepable(el) {
    if (el === document.body || el === document.documentElement) return false;
    var r = el.getBoundingClientRect();
    var area = r.width * r.height;
    var viewport = window.innerWidth * window.innerHeight;
    if (!viewport) return el.children.length === 0;
    // A leaf really is more likely to be a picture than a container is, so it
    // gets the looser cap -- but not an unlimited one: an empty full-bleed
    // div is a hero, a slab of ad, or the page's own backdrop, and
    // un-inverting any of them reads as dark mode failing rather than as a
    // photo being spared.
    return area < viewport * (el.children.length === 0 ? 0.6 : 0.4);
  }

  // A surface that already paints an opaque dark background of its own. The
  // page-level rule -- inverting something dark is how you end up with a light
  // page -- is just as true of a section as it is of a document, and the
  // filter has no way to tell the two apart: zdnet.com's footer is 1560px of
  // #080A12, so in dark mode it came out as a full screen of pale lavender.
  // Handing it the inverse filter passes it through at its own colours.
  //
  // SECTION SCALE is the whole guard, and the reason it has to be there is
  // that below it the same treatment is wrong. A dark button on a white card
  // has to become a light button on a dark card, because contrast with the
  // surface around it is the thing it is for, and keeping it dark is how it
  // disappears. A band the size of the viewport has no surface around it --
  // it IS the surface -- so it carries its own colour scheme and keeps it.
  function darkSurface(el, cs) {
    var viewport = window.innerWidth * window.innerHeight;
    if (!viewport) return false;
    var r = el.getBoundingClientRect();
    if (r.width * r.height < viewport * 0.25) return false;
    var l = luminance(cs.backgroundColor);
    return l >= 0 && l < DARK_L;
  }

  // The two things a selector cannot find on its own, both tagged with the
  // same attribute because both want the same treatment -- the inverse filter,
  // so the subtree comes back out where it went in. Bitmap backgrounds set
  // from a stylesheet are one (gradients are colour choices, not pictures, so
  // only url() counts, and they invert with everything else); a section that
  // is already dark is the other.
  // Bounded because this is a getComputedStyle call per element on someone
  // else's page, and each element is only ever asked once: apply() runs on
  // every observer tick, and re-reading four thousand computed styles each
  // time is the kind of cost that shows up as dropped frames on exactly the
  // ad-heavy pages that tick most.
  function scanSurfaces() {
    if (!document.body) return;
    var state = window.__yukuPageDark;
    var nodes = document.body.querySelectorAll('*');
    var limit = nodes.length < 4000 ? nodes.length : 4000;
    for (var i = 0; i < limit; i++) {
      var el = nodes[i];
      if (state.scanned.has(el)) continue;
      state.scanned.add(el);
      if (el.hasAttribute(KEEP)) continue;
      var cs = getComputedStyle(el);
      var bg = cs.backgroundImage;
      if (bg && bg.indexOf('url(') !== -1 && keepable(el)) el.setAttribute(KEEP, '');
      else if (darkSurface(el, cs)) el.setAttribute(KEEP, '');
    }
  }

  // A subframe is inverted by the top document's filter, so all it installs
  // is the picture-preserving half -- and only once it has been told the top
  // document actually is inverting, since re-inverting an image under a page
  // that stood down is a negative.
  function broadcast(on) {
    var frames = window.frames;
    for (var i = 0; i < frames.length; i++) {
      try { frames[i].postMessage({ __yukuDark: on }, '*'); } catch (e) {}
    }
  }

  function styleEl() {
    var el = document.getElementById(ID);
    if (!el) {
      el = document.createElement('style');
      el.id = ID;
      el.textContent = CSS;
    }
    return el;
  }

  function install(el) {
    var root = document.documentElement;
    ensureFilters(document.body || root);
    el.disabled = false;
    // Re-appended rather than assumed present: a page that replaces its own
    // <head> (or moves nodes around while hydrating) can drop the style.
    if (el.parentNode !== root) root.appendChild(el);
  }

  function apply() {
    var state = window.__yukuPageDark;
    if (!state || !state.on) return;
    var root = document.documentElement;
    if (!root) return;
    var el = document.getElementById(ID);
    if (!TOP) {
      // Nothing to decide here: the answer came down the frame tree.
      if (!state.inverting) {
        if (el) el.disabled = true;
        return;
      }
      install(styleEl());
      scanSurfaces();
      return;
    }
    // Standing down is `disabled`, not removal: the site may turn its own
    // dark theme back off, and re-arming should not mean re-parsing.
    var down = alreadyDark();
    if (down !== state.told || window.frames.length !== state.frameCount) {
      state.told = down;
      state.frameCount = window.frames.length;
      broadcast(!down);
    }
    if (down) {
      if (el) el.disabled = true;
      return;
    }
    // A filter can only reference a definition in its own document, so the
    // <svg> holding them has to live in the page. It goes in the body once
    // there is one: a stray element between <head> and <body> is a layout
    // risk that isn't worth taking.
    install(styleEl());
    scanSurfaces();
  }

  window.__yukuPageDark = {
    on: true,
    apply: apply,
    since: Date.now(),
    verdict: null,
    disagree: 0,
    told: null,
    frameCount: -1,
    inverting: false,
    scanned: new WeakSet()
  };
  if (!TOP) {
    window.addEventListener('message', function (e) {
      var d = e.data;
      if (!d || typeof d !== 'object' || !('__yukuDark' in d)) return;
      if (e.source !== window.parent) return;
      var state = window.__yukuPageDark;
      if (!state) return;
      var on = !!d.__yukuDark;
      if (state.inverting !== on) {
        state.inverting = on;
        apply();
      }
      broadcast(on);
    });
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
  window.addEventListener('load', apply);

  // The FIRST FRAME is decided inside that frame. At document start there is
  // no stylesheet, so a site whose dark theme lives in its CSS (GitHub's
  // color-scheme and canvas both come from `prefers-color-scheme`) measures
  // as nothing and the filter goes on. Its CSS then blocks rendering until it
  // arrives, and the first frame after that was painted with the filter still
  // on -- a dark site inverted to light -- until DOMContentLoaded or a timer
  // got round to looking, which is the snap. A rAF callback runs after the
  // styles are in and before that frame paints, so the stand-down lands
  // before anything is seen. Counted in FRAMES rather than time, since no
  // frame is produced while rendering is blocked however long that takes, and
  // stopped a few frames past the first paint (the entry lags presentation).
  if (TOP && window.requestAnimationFrame) {
    var framesLeft = 120, afterPaint = 0;
    var beforePaint = function () {
      var state = window.__yukuPageDark;
      if (!state || !state.on) return;
      apply();
      if (--framesLeft <= 0 || (painted() && ++afterPaint > 3)) return;
      window.requestAnimationFrame(beforePaint);
    };
    window.requestAnimationFrame(beforePaint);
  }

  // A site toggling its own theme moves an attribute on one of the two root
  // elements; watching them is what makes standing down immediate rather
  // than up to one tick late.
  if (window.MutationObserver) {
    var pending = false;
    var observer = new MutationObserver(function () {
      var state = window.__yukuPageDark;
      if (!state || !state.on || pending) return;
      pending = true;
      requestAnimationFrame(function () {
        pending = false;
        apply();
      });
    });
    // A LAZY LOADER hands an element its picture long after the element was
    // first read, and each element is only ever read once -- which is the
    // whole point of the scanned set, and is also how a picture ends up
    // inverted with the page. keddr.com's article thumbnails are
    // `background-image` on an <a>, and until they scroll into view that
    // background is a grey placeholder GRADIENT: read once, no url(), no
    // keep, recorded as answered for good. The photo that arrives a screen
    // later lands in an element nothing will look at again, and comes out
    // as a negative in the middle of a page of correct ones.
    //
    // What changes when the picture lands is the element's own `style` or
    // `class` -- every lazy loader writes one or the other -- so those are
    // watched across the document and the elements that ACTUALLY CHANGED are
    // dropped from the set. The re-read is then bounded by what moved rather
    // than by the size of the document, which is the cost the set is there
    // to avoid; nothing of ours is in the filter, so our own tagging cannot
    // feed it.
    var rescan = new MutationObserver(function (records) {
      var state = window.__yukuPageDark;
      if (!state || !state.on) return;
      for (var i = 0; i < records.length; i++) {
        var t = records[i].target;
        if (t && t.nodeType === 1) state.scanned.delete(t);
      }
      if (pending) return;
      pending = true;
      requestAnimationFrame(function () {
        pending = false;
        apply();
      });
    });
    var watch = { attributes: true, attributeFilter: ['class', 'dark', 'style', 'data-theme', 'data-color-mode', 'data-dark-theme', 'data-bs-theme', 'data-color-scheme'] };
    observer.observe(document.documentElement, watch);
    // From DOCUMENT START, on <html> rather than <body>: a lazy loader is a
    // script in the body and runs WHILE the document parses, so the pictures
    // already in view are handed their photo before DOMContentLoaded. On
    // keddr.com that is ~700ms in, the placeholder had been scanned at ~330
    // by the first-frame loop, and an observer waiting for DOMContentLoaded
    // missed exactly the thumbnails on screen -- which stayed negatives for
    // the life of the page. <html> exists at document start (and when this
    // is evaluated late), and a subtree watch on it survives a replaced body.
    rescan.observe(document.documentElement, {
      attributes: true,
      subtree: true,
      attributeFilter: ['class', 'style'],
    });
    var watchBody = function () {
      if (document.body) observer.observe(document.body, watch);
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', watchBody);
    else watchBody();
  }

  // Late-arriving content (lazy images, client-rendered views) needs another
  // look; a handful of ticks costs little and stops well before it could
  // matter for battery.
  var ticks = 0;
  var timer = setInterval(function () {
    var state = window.__yukuPageDark;
    if (!state || !state.on || ++ticks > 12) {
      clearInterval(timer);
      return;
    }
    apply();
  }, 400);
})();
"""

    /** Undoes [ENABLE] on the live page, so turning dark mode off is instant too. */
    val DISABLE = """
(function () {
  if (window.__yukuPageDark) window.__yukuPageDark.on = false;
  // Subframes never see this script -- an evaluate reaches the main frame
  // only -- so the word that the page has stopped inverting goes down the
  // same wire the verdict does.
  var frames = window.frames;
  for (var i = 0; i < frames.length; i++) {
    try { frames[i].postMessage({ __yukuDark: false }, '*'); } catch (e) {}
  }
  ['$STYLE_ID', '$SVG_ID'].forEach(function (id) {
    var el = document.getElementById(id);
    if (el && el.parentNode) el.parentNode.removeChild(el);
  });
  var kept = document.querySelectorAll('[$KEEP_ATTR]');
  for (var i = 0; i < kept.length; i++) kept[i].removeAttribute('$KEEP_ATTR');
})();
"""
}
