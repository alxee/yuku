package com.yuku.browser.core

import org.json.JSONObject

/**
 * Cookie-banner hiding: the cosmetic half of filtering, which is a different
 * job from [AdBlocker] and needs different machinery.
 *
 * A tracker is a request to somebody else's server, so it can be cut off at
 * the network by hostname. A consent banner usually isn't: the overwhelming
 * majority are markup the site itself renders, from a script bundled into its
 * own origin, so there is no request to refuse. What identifies them instead
 * is the markup — `#onetrust-consent-sdk`, `#CybotCookiebotDialog` — which is
 * what a cosmetic filter list enumerates and what this injects as CSS.
 *
 * Three parts, in the order they matter:
 *
 *  - **Generic rules** (`##selector`), which apply to every page. These are
 *    injected at document start, before the page's own first paint, so a
 *    banner is never briefly visible. ~15k of them come from EasyList's cookie
 *    list; [FALLBACK_SELECTORS] covers the major consent platforms on its own
 *    for the window before anything has been downloaded.
 *  - **Site-specific rules** (`example.com##selector`), ~7k, looked up by host
 *    and injected on navigation. A page-load late rather than document-start
 *    early, which is fine: the banners these describe are drawn by scripts
 *    that have barely started at that point.
 *  - **Scroll unlocking.** A banner that was a modal usually left the document
 *    scroll-locked behind it, so hiding it alone leaves a page that can't be
 *    scrolled. Undone only when a known consent modal is actually present, and
 *    only on the properties actually locked — see [SCRIPT].
 *
 * What this deliberately does NOT do is click "accept" for the user. That is a
 * legal declaration made in their name; hiding the banner and refusing the
 * scripts behind it is the honest version.
 */
object CosmeticFilters {

    /** Parsed form of one page's worth of rules. */
    data class Rules(
        /** Ready-to-inject CSS for every page. */
        val genericCss: String = "",
        /** Selectors keyed by the domain they apply to. */
        val specific: Map<String, List<String>> = emptyMap(),
        val genericCount: Int = 0,
    ) {
        val specificCount: Int get() = specific.values.sumOf { it.size }
        val total: Int get() = genericCount + specificCount
    }

    /**
     * One cosmetic rule as it came off a filter list: a selector, plus the
     * domains it is scoped to (empty means every page).
     */
    data class CosmeticRule(val domains: List<String>, val selector: String)

    /**
     * Parses `##selector` and `domain.com,other.org##selector`. Returns null
     * for everything else, including the rule types this can't honor:
     *
     *  - `#@#` exceptions (they un-hide, and there is nothing to un-hide from
     *    a stylesheet that was built without them),
     *  - `#?#`, `#$#` and any selector using extended pseudo-classes
     *    (`:has-text`, `:contains`, `:upward`, `:style` …), which are uBlock/
     *    AdGuard engine features, not CSS. A browser drops the whole rule they
     *    appear in, so one of these left in would silently take its chunk of
     *    real selectors down with it.
     *  - `~excluded.com##...` — an exception domain, same reasoning as `#@#`.
     */
    fun parseCosmetic(raw: String): CosmeticRule? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) return null
        val marker = line.indexOf("##")
        if (marker < 0) return null
        // #@#, #?#, #$# and #%# all put a character between the two hashes.
        if (marker > 0 && line[marker - 1] in "@?$%") return null
        val selector = line.substring(marker + 2).trim()
        if (!isPlainCssSelector(selector)) return null
        val scope = line.substring(0, marker)
        if (scope.isEmpty()) return CosmeticRule(emptyList(), selector)
        val domains = scope.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        // A rule with any exception domain is skipped whole: honoring the
        // positive half of it would hide the element on the very sites the
        // list author carved out.
        if (domains.any { it.startsWith('~') || !it.contains('.') }) return null
        return CosmeticRule(domains, selector)
    }

    /** Extended-syntax pseudo-classes a real stylesheet cannot express. */
    private val EXTENDED = listOf(
        ":has-text(", ":contains(", ":xpath(", ":upward(", ":nth-ancestor(",
        ":matches-css", ":matches-attr", ":matches-path", ":min-text-length(",
        ":watch-attr(", ":remove(", ":style(", ":others(", ":if(", ":if-not(",
    )

    private fun isPlainCssSelector(selector: String): Boolean {
        if (selector.isEmpty() || selector.length > 400) return false
        // `{`/`}` would close the rule this selector is spliced into; a
        // comment marker would swallow the rest of the stylesheet.
        if (selector.contains('{') || selector.contains('}') || selector.contains("/*")) return false
        if (EXTENDED.any { selector.contains(it, ignoreCase = true) }) return false
        // Unbalanced brackets or quotes would run on into the next selector in
        // the chunk and take it out too.
        var round = 0
        var square = 0
        var quote: Char? = null
        for (c in selector) {
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> round++
                c == ')' -> if (--round < 0) return false
                c == '[' -> square++
                c == ']' -> if (--square < 0) return false
            }
        }
        return round == 0 && square == 0 && quote == null
    }

    /**
     * Selectors into a stylesheet, in chunks rather than one enormous rule.
     * CSS has no forgiving selector parsing outside `:is()`: a single selector
     * the browser dislikes invalidates the entire rule it sits in. Chunking
     * bounds that blast radius to [CHUNK] selectors instead of all fifteen
     * thousand.
     */
    fun buildCss(selectors: Collection<String>): String {
        if (selectors.isEmpty()) return ""
        return selectors.asSequence()
            .distinct()
            .chunked(CHUNK)
            .joinToString("\n") { chunk -> chunk.joinToString(",") + HIDE }
    }

    /**
     * The document-start script: the generic stylesheet plus scroll
     * unlocking. Registered once per WebView (see BrowserViewModel), so the
     * CSS is held once rather than shipped across on every navigation.
     */
    fun documentStartScript(css: String): String = """
        (function(){
          if (window.__yukuCosmetic) return;
          var installed = [];
          function inject(text){
            if (!text) return null;
            try {
              var root = document.head || document.documentElement;
              if (!root) return null;
              var el = document.createElement('style');
              el.setAttribute('type', 'text/css');
              // Tagged so a site the user turned banner hiding off for can
              // have exactly our stylesheets disabled and nothing else — see
              // [disableScript]. The flag is read here as well, for the
              // stylesheet a frame injects AFTER that switch was thrown.
              el.setAttribute('data-yuku-cosmetic', '1');
              el.textContent = text;
              root.appendChild(el);
              if (window.__yukuCosmeticOff) el.disabled = true;
              installed.push(el);
              return el;
            } catch (e) { return null; }
          }
          window.__yukuCosmetic = inject;
          var CSS = ${quote(css)};
          // At document start the parser may not have produced <html> yet, in
          // which case there is nothing to append to; wait for it rather than
          // dropping the stylesheet and letting the banner paint.
          if (document.documentElement) {
            inject(CSS);
          } else {
            var mo = new MutationObserver(function(){
              if (document.documentElement) { mo.disconnect(); inject(CSS); }
            });
            try { mo.observe(document, { childList: true }); } catch (e) {}
          }
          $UNLOCK
        })();
    """.trimIndent()

    /**
     * Site-specific rules for one host, injected on navigation. Self-contained
     * rather than calling `__yukuCosmetic`: the document-start script and
     * `onPageStarted` race, and losing that race must not cost the rules.
     */
    fun hostScript(css: String): String = """
        (function(){
          var text = ${quote(css)};
          function add(){
            try {
              var root = document.head || document.documentElement;
              if (!root) return false;
              var el = document.createElement('style');
              el.setAttribute('type', 'text/css');
              el.setAttribute('data-yuku-cosmetic', '1');
              el.textContent = text;
              root.appendChild(el);
              if (window.__yukuCosmeticOff) el.disabled = true;
              return true;
            } catch (e) { return true; }
          }
          if (!add()) document.addEventListener('DOMContentLoaded', add);
        })();
    """.trimIndent()

    /**
     * Undoes the hiding for one document, for a site the user has asked to
     * see banners on.
     *
     * The stylesheet is registered per WebView as a document-start script, so
     * a site-level exception cannot be honoured by simply not registering it:
     * the document on screen already has it, and the registration is what the
     * NEXT navigation gets. Both halves are therefore needed —
     * BrowserViewModel takes the registration off for the pages to come, and
     * evaluates this to undo the one that is up.
     *
     * `disabled` rather than removing the element: the same page can be given
     * the hiding back (the user changes their mind while standing on it) with
     * nothing to rebuild, and a stylesheet that was taken out of the document
     * would have to be shipped across again.
     */
    fun disableScript(): String =
        "(function(){try{window.__yukuCosmeticOff=true;" +
            "var s=document.querySelectorAll('style[data-yuku-cosmetic]');" +
            "for(var i=0;i<s.length;i++)s[i].disabled=true;}catch(e){}})();"

    /** The other direction, for a site put back to ordinary. */
    fun enableScript(): String =
        "(function(){try{window.__yukuCosmeticOff=false;" +
            "var s=document.querySelectorAll('style[data-yuku-cosmetic]');" +
            "for(var i=0;i<s.length;i++)s[i].disabled=false;}catch(e){}})();"

    /**
     * The consent platforms whose banner is a scroll-locking modal. Also the
     * only selectors [UNLOCK] will act on.
     */
    private val MODAL_ROOTS = listOf(
        "#onetrust-consent-sdk",
        "#CybotCookiebotDialog",
        ".qc-cmp2-container",
        "#didomi-host",
        "div[id^='sp_message_container']",
        "#usercentrics-root",
        ".fc-consent-root",
        "#cookiescript_injected",
        "#axeptio_overlay",
        "#tarteaucitronRoot",
        "#cookiefirst-root",
        "#iubenda-cs-banner",
        ".osano-cm-window",
        "#BorlabsCookieBox",
        "#cmplz-cookiebanner-container",
        ".cky-consent-container",
        "#termly-code-snippet-support",
        "#truste-consent-track",
    )

    /**
     * Undoes the scroll lock a consent modal leaves behind. Gated twice over,
     * because wrongly re-enabling scrolling breaks real dialogs: it runs only
     * when one of [MODAL_ROOTS] — an actual named consent platform's modal —
     * is in the document, and then only touches the properties that are
     * actually locked, read off the computed style rather than assumed.
     */
    private val UNLOCK = """
          var ROOTS = ${jsArray(MODAL_ROOTS)};
          function locked(){
            for (var i = 0; i < ROOTS.length; i++) {
              try { if (document.querySelector(ROOTS[i])) return true; } catch (e) {}
            }
            return false;
          }
          function unlock(){
            if (!locked()) return false;
            [document.documentElement, document.body].forEach(function(el){
              if (!el) return;
              try {
                var cs = getComputedStyle(el);
                if (cs.overflow === 'hidden' || cs.overflowY === 'hidden') {
                  el.style.setProperty('overflow', 'auto', 'important');
                }
                // A lock implemented as position:fixed also throws away the
                // scroll offset; static is the only way back.
                if (cs.position === 'fixed') {
                  el.style.setProperty('position', 'static', 'important');
                }
              } catch (e) {}
            });
            return true;
          }
          // Consent scripts insert themselves whenever they finish loading, so
          // one pass at DOMContentLoaded misses most of them. A handful of
          // spaced attempts covers the realistic range and then stops -- a
          // permanent observer on every page is not worth this.
          var tries = 0;
          var timer = setInterval(function(){
            if (unlock() || ++tries > 12) clearInterval(timer);
          }, 400);
          document.addEventListener('DOMContentLoaded', unlock);
    """.trimIndent()

    /**
     * Enough on its own to cover the consent platforms behind most banners,
     * for the window before EasyList's list has been downloaded — and kept in
     * the stylesheet afterwards, since a list is only ever as fresh as its
     * last update.
     */
    val FALLBACK_SELECTORS = listOf(
        // OneTrust / CookiePro
        "#onetrust-consent-sdk", "#onetrust-banner-sdk", ".onetrust-pc-dark-filter",
        // Cookiebot
        "#CybotCookiebotDialog", "#CybotCookiebotDialogBodyUnderlay", "#CookiebotWidget",
        // Quantcast
        ".qc-cmp2-container", ".qc-cmp-cleanslate", ".qc-cmp2-persistent-link",
        // Didomi
        "#didomi-host", "#didomi-notice",
        // Sourcepoint
        "div[id^='sp_message_container']", ".sp_veil", "#sp_privacy_manager_container",
        // Usercentrics
        "#usercentrics-root", "#uc-banner-modal", "#usercentrics-button",
        // Google Funding Choices
        ".fc-consent-root",
        // Iubenda
        "#iubenda-cs-banner", ".iubenda-cs-overlay",
        // Osano
        ".osano-cm-window", ".osano-cm-dialog",
        // TrustArc
        "#truste-consent-track", "#consent_blackbar", ".truste_overlay",
        // CookieYes / GDPR Cookie Consent
        ".cky-consent-container", ".cky-overlay", "#cookie-law-info-bar", "#cookie-law-info-again",
        // Complianz
        "#cmplz-cookiebanner-container", ".cmplz-cookiebanner",
        // Borlabs
        "#BorlabsCookieBox", "#BorlabsCookieBoxWrap",
        // Klaro
        "#klaro", ".klaro .cookie-modal", ".klaro .cookie-notice",
        // Termly
        "#termly-code-snippet-support",
        // Cookie Script
        "#cookiescript_injected", "#cookiescript_injected_wrapper",
        // Axeptio
        "#axeptio_overlay", "#axeptio_main_button",
        // tarteaucitron
        "#tarteaucitronRoot", "#tarteaucitronAlertBig",
        // CookieFirst
        "#cookiefirst-root", "[aria-labelledby='cookiefirst-root']",
        // Ketch
        "#ketch-consent-banner", "#lanyard_root",
        // Insites Cookie Consent, still everywhere
        ".cc-window.cc-banner", ".cc-window.cc-floating",
        // WordPress plugins
        "#moove_gdpr_cookie_info_bar", "#eu-cookie-bar", "#catapult-cookie-bar",
        // Generic markup that is almost never anything else
        "#cookie-consent-banner", "#cookieConsentBanner", "#cookie-notice",
        ".cookie-consent-banner", ".cookie-notice-container", "#gdpr-consent-tool-wrapper",
    )

    private const val CHUNK = 40
    private const val HIDE = "{display:none!important}"

    /** JSON string quoting is JS string quoting, and it's already on Android. */
    private fun quote(text: String): String = JSONObject.quote(text)

    private fun jsArray(items: List<String>): String =
        items.joinToString(prefix = "[", postfix = "]") { quote(it) }

    /**
     * The rules that apply to one host: its own, plus every parent domain's —
     * a rule written for `example.com` is meant for `www.example.com` too.
     */
    fun selectorsFor(specific: Map<String, List<String>>, host: String): List<String> {
        if (specific.isEmpty() || host.isEmpty()) return emptyList()
        val lower = host.lowercase()
        val found = ArrayList<String>()
        var index = 0
        while (index < lower.length) {
            specific[lower.substring(index)]?.let(found::addAll)
            val dot = lower.indexOf('.', index)
            if (dot == -1) break
            index = dot + 1
        }
        return found
    }
}
