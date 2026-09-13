package com.yuku.browser.core

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/**
 * "Desktop site" — the whole of it, because a user-agent string on its own has
 * not been enough to get one since about 2020.
 *
 * A site decides which of its two faces to serve by asking three different
 * questions, and answering only the first is what made this feature look like
 * it worked on some sites and not others:
 *
 *  1. **The user-agent string.** Server-side sniffing, and the only lever this
 *     used to pull.
 *  2. **User-Agent Client Hints.** `Sec-CH-UA-Mobile: ?1` in the request and
 *     `navigator.userAgentData.mobile === true` in the page. Chromium sends
 *     these *alongside* the UA string, and they are not derived from it — so a
 *     spoofed desktop UA arrived with a header underneath it still saying
 *     "phone", and any site that trusts the newer signal over the older one
 *     (Google's properties do) served mobile anyway. Worse than a tie: see the
 *     note on the metadata override below for what WebView does when the UA is
 *     overridden and the metadata is not.
 *  3. **The viewport width.** A responsive site has no UA sniffing at all; it
 *     has `@media (min-width: 1024px)`. The page is laid out in whatever CSS
 *     width the viewport meta asks for, so `width=device-width` on a 411dp
 *     phone is a mobile layout no matter what the headers said — the DESKTOP
 *     page, in its mobile arrangement. Only widening the viewport moves it.
 *
 * So all three are answered here: the UA string ([desktopUserAgent]), the
 * client-hint metadata ([UserAgentMetadata]), and a document-start script that
 * overrides the page's own viewport meta.
 *
 * **The UA is derived from the device's own, not written out.** The real
 * default is `Mozilla/5.0 (Linux; Android 14; …; wv) AppleWebKit/537.36 …
 * Version/4.0 Chrome/126.0.6478.71 Mobile Safari/537.36`; the desktop form is
 * that same string with the platform section replaced, and `wv`, `Version/4.0`
 * and `Mobile` dropped, which is exactly the edit Chrome for Android makes.
 * Deriving it keeps the Chrome MAJOR VERSION honest — a hardcoded string goes
 * stale, and a page told "Chrome 124" by the UA and "Chrome 139" by the
 * client hints has been told two different things by the same browser. It also
 * keeps the version high enough that feature gates on the desktop site (which
 * the mobile one may not have) do not shut.
 *
 * **The metadata override is not optional once the UA is.** WebView's own rule:
 * if the overridden UA does not contain the system default UA, only the
 * low-entropy hints are generated — and `Sec-CH-UA-Mobile` and
 * `Sec-CH-UA-Platform` are both low-entropy, so they keep being sent, still
 * saying Android and mobile. There is no way to turn them off; the only way
 * out is to say something else. The defaults are captured BEFORE the first
 * override so switching back restores them — there is no "unset" for metadata,
 * only setting it to what it was.
 *
 * **1024 CSS px, not 980.** 980 is the historical wide-viewport default (and
 * Chrome's desktop-mode width) and it lands in the tablet band of every
 * current CSS framework — Tailwind's `lg` starts at 1024, Bootstrap's `xl` at
 * 1200, and a bare `min-width: 1024px` is the commonest hand-written desktop
 * breakpoint there is. A width that stops one pixel short of the breakpoint
 * everyone uses buys a wide mobile layout, which is not what the switch says.
 *
 * `initial-scale` is deliberately absent from the override: with it, the page
 * is laid out at 1024 and drawn at 1:1 on a 411dp screen, so the user arrives
 * panned into its top-left corner. Left out, `loadWithOverviewMode` scales the
 * 1024px layout down to fit the window, which is the desktop page whole — the
 * thing that was asked for. `textZoom` still applies over the top, and
 * `user-scalable=yes` is stated because the page's own meta (which the
 * override replaces) is quite often `user-scalable=no`.
 */
object DesktopMode {

    /** The layout width a desktop page is asked for. See the class doc. */
    const val VIEWPORT_WIDTH_PX = 1024

    /**
     * Used only when the device's own UA cannot be read or does not parse —
     * there is no version to be honest about in that case.
     */
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    /**
     * What the WebView said before we touched it. Captured on the first
     * [apply] and never re-read, because after the first override the getters
     * answer with our own values.
     */
    private class Defaults(val userAgent: String?, val metadata: UserAgentMetadata?)

    private val defaults = WeakHashMap<WebView, Defaults>()
    private val scripts = WeakHashMap<WebView, ScriptHandler>()

    private val metadataSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)

    private val scriptSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /**
     * Puts [web] into (or out of) desktop mode. Idempotent, and safe to call
     * on a view that has already loaded — though the viewport script only
     * takes effect from the next navigation, so a caller flipping the switch
     * reloads.
     */
    fun apply(web: WebView, on: Boolean) {
        // A view that has never been in desktop mode and is not going into it
        // has nothing to restore, and its defaults are its defaults — reading
        // and writing them back would be the only thing that ever touched them.
        if (!on && !defaults.containsKey(web)) return
        val settings = web.settings
        val saved = defaults.getOrPut(web) {
            Defaults(
                userAgent = settings.userAgentString,
                metadata = if (metadataSupported) {
                    runCatching { WebSettingsCompat.getUserAgentMetadata(settings) }.getOrNull()
                } else {
                    null
                },
            )
        }

        settings.userAgentString = if (on) desktopUserAgent(saved.userAgent) else null

        if (metadataSupported) {
            runCatching {
                val next = if (on) {
                    val builder = saved.metadata
                        ?.let { UserAgentMetadata.Builder(it) }
                        ?: UserAgentMetadata.Builder()
                    builder
                        .setMobile(false)
                        .setPlatform("Linux")
                        // Chrome on Linux sends its kernel version here. We do
                        // not have one to tell the truth about, and an empty
                        // hint is a hint the site can still see is not Android.
                        .setPlatformVersion("")
                        .setArchitecture("x86")
                        .setBitness(64)
                        // The device model is the loudest "this is a phone"
                        // left in the hints once the rest are answered.
                        .setModel("")
                        .setWow64(false)
                        .build()
                } else {
                    saved.metadata
                }
                if (next != null) WebSettingsCompat.setUserAgentMetadata(settings, next)
            }
        }

        applyViewportScript(web, on)
    }

    /**
     * The viewport override lives as long as desktop mode does. A document-
     * start script cannot read a flag that changes after it was installed, so
     * it is added and removed rather than gated — [ScriptHandler.remove] takes
     * effect from the next load, which is the same frame the reload lands on.
     */
    private fun applyViewportScript(web: WebView, on: Boolean) {
        if (!scriptSupported) return
        val existing = scripts[web]
        if (on) {
            if (existing != null) return
            runCatching { WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*")) }
                .onSuccess { scripts[web] = it }
        } else {
            if (existing == null) return
            scripts.remove(web)
            runCatching { existing.remove() }
        }
    }

    /**
     * The device's UA rewritten as the desktop build of the same Chrome. See
     * the class doc for why it is derived rather than written out. Internal
     * rather than private so it can be checked off-device.
     */
    internal fun desktopUserAgent(default: String?): String {
        val ua = default?.takeIf { it.isNotBlank() } ?: return FALLBACK_USER_AGENT
        // The FIRST parenthesised group is the platform section; the second is
        // `(KHTML, like Gecko)` and must survive.
        var out = ua.replaceFirst(Regex("""\([^()]*\)"""), "(X11; Linux x86_64)")
        // WebView-only tokens. Desktop Chrome has neither.
        out = out.replace(Regex("""\s*Version/\S+"""), "")
        out = out.replace(Regex("""\bMobile\b\s*"""), "")
        out = out.replace(Regex("""\s{2,}"""), " ").trim()
        // A string with no Chrome version in it was not the shape we thought.
        return if (out.contains("Chrome/") && out.contains("(X11; Linux x86_64)")) {
            out
        } else {
            FALLBACK_USER_AGENT
        }
    }

    /**
     * Widens the viewport and takes the touch flags off the two places a
     * feature detector looks.
     *
     * The page's own viewport metas are not edited or removed — ours is simply
     * kept LAST, because Chromium processes each viewport meta as it is
     * inserted and the most recent description wins. That makes the override
     * survive a site that writes its meta from script or re-renders its
     * `<head>`, and costs nothing if it never does.
     *
     * The touch flags are the third-commonest way to be handed a mobile page
     * (`'ontouchstart' in window` is what Modernizr and every hand-rolled
     * check test) and they are the riskiest thing here, so they are the
     * smallest possible edit: the DETECTION is answered, while `TouchEvent`,
     * touch listeners and real touch dispatch are all left alone, so a page
     * that registers touch handlers anyway still gets them.
     */
    private val SCRIPT = """
        (function () {
          var CONTENT = 'width=${VIEWPORT_WIDTH_PX}, user-scalable=yes';
          var mine = null;
          var writing = false;

          function root() { return document.head || document.documentElement; }

          function ensure() {
            var head = root();
            if (!head) return;
            writing = true;
            try {
              if (!mine || !mine.isConnected) {
                mine = document.createElement('meta');
                mine.setAttribute('name', 'viewport');
                mine.setAttribute('content', CONTENT);
                head.appendChild(mine);
                return;
              }
              if (mine.getAttribute('content') !== CONTENT) {
                mine.setAttribute('content', CONTENT);
              }
              // Re-appending is a removal and an insertion, which is what
              // makes Chromium read the description again.
              if (mine.parentNode !== head || mine !== head.lastChild) {
                head.appendChild(mine);
              }
            } finally {
              writing = false;
            }
          }

          function isViewport(node) {
            return node && node.nodeType === 1 && node !== mine &&
              node.tagName === 'META' &&
              (node.getAttribute('name') || '').toLowerCase() === 'viewport';
          }

          function onMutations(records) {
            if (writing) return;
            for (var i = 0; i < records.length; i++) {
              var r = records[i];
              if (r.type === 'attributes') {
                if (isViewport(r.target) || r.target === mine) { ensure(); return; }
                continue;
              }
              for (var j = 0; j < r.addedNodes.length; j++) {
                var n = r.addedNodes[j];
                if (isViewport(n) || n === mine) { ensure(); return; }
              }
              for (var k = 0; k < r.removedNodes.length; k++) {
                if (r.removedNodes[k] === mine) { ensure(); return; }
              }
            }
          }

          try {
            ensure();
            new MutationObserver(onMutations).observe(document, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['name', 'content'],
            });
            document.addEventListener('DOMContentLoaded', ensure, true);
            window.addEventListener('load', ensure, true);
          } catch (e) {}

          // `delete obj.prop` only removes an OWN property, and these are
          // interface attributes living on a prototype somewhere up the
          // chain, so the owner has to be found before it can be dropped.
          function drop(obj, prop) {
            for (var o = obj; o; o = Object.getPrototypeOf(o)) {
              if (Object.prototype.hasOwnProperty.call(o, prop)) {
                try { delete o[prop]; } catch (e) {}
                return;
              }
            }
          }

          try {
            drop(window, 'ontouchstart');
            drop(document, 'ontouchstart');
            if (document.documentElement) drop(document.documentElement, 'ontouchstart');
            Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {
              get: function () { return 0; },
              configurable: true,
            });
            Object.defineProperty(Navigator.prototype, 'platform', {
              get: function () { return 'Linux x86_64'; },
              configurable: true,
            });
          } catch (e) {}
        })();
    """.trimIndent()
}
