package com.yuku.browser.core

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Turns off cross-document view transitions, because in WebView they never
 * finish and the page they were meant to reveal never paints.
 *
 * The symptom that led here: tapping Google Search's "AI Mode" tab left the
 * previous page frozen on screen for 15-30 seconds. Measured in the renderer,
 * the incoming document was in perfect health — `readyState` complete, the
 * answer fully in the DOM within about five seconds — with two numbers that
 * say what was actually wrong:
 *
 * ```
 * performance.getEntriesByType('paint')  ->  []
 * requestAnimationFrame counter          ->  1, and never again
 * ```
 *
 * No first paint at all. That is not a slow page, it is a page that was never
 * allowed to draw one. `@view-transition { navigation: auto; }` is in Google's
 * stylesheet (alongside a set of `::view-transition-old/new` animations keyed
 * on an `aimc` transition type), which opts the navigation into a *cross-*
 * document view transition: the outgoing document is snapshotted, and the
 * incoming one has its rendering **blocked** until the transition is ready to
 * play. Here it never becomes ready, so the block never lifts, and the old
 * page simply stays up until something — a touch, anything that forces a
 * frame — knocks it loose.
 *
 * It reproduces on the way back out too, since the reverse navigation is the
 * same kind of transition, and that matches the other half of the report:
 * returning from AI Mode to ordinary results hangs the same way.
 *
 * Why it looks like a WebView-only bug: it is one. Chrome runs the same
 * stylesheet and transitions instantly.
 *
 * **Worth knowing if you go looking for this yourself**: it only reproduces
 * under a *real* touch. Clicking the same anchor from a devtools
 * `Runtime.evaluate` loads instantly, because that click carries no user
 * activation and the transition is skipped — so a synthetic click "proves"
 * a fix that isn't one. Drive it with `Input.dispatchTouchEvent` or
 * `adb shell input tap`. For the same reason, do not measure this with
 * `Page.captureScreenshot`: asking for a screenshot forces a frame, which
 * restarts the very loop whose absence is the bug, and the page then looks
 * healthy for as long as you keep polling it.
 *
 * The opt-out has to be the *last* `@view-transition` rule the document has,
 * since that descriptor is resolved last-rule-wins and the page's own rule
 * arrives long after a document-start script could append a `<style>`. An
 * adopted stylesheet sidesteps the ordering fight entirely — the document's
 * adopted sheets come after its author sheets, whenever they were added — so
 * one constructed sheet installed at document start is both enough and final.
 * The `<style>` path below is only for a WebView too old for constructible
 * stylesheets, and it re-appends itself to stay at the end.
 *
 * Opting out costs the animation and nothing else: the navigation still
 * happens, it just cuts rather than cross-fades — which is what WebView
 * already does everywhere a page hasn't asked for a transition.
 */
object ViewTransitionGate {

    fun attach(web: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    private val SCRIPT = """
        (function () {
          var css = '@view-transition{navigation:none;}';
          try {
            var sheet = new CSSStyleSheet();
            sheet.replaceSync(css);
            document.adoptedStyleSheets = document.adoptedStyleSheets.concat(sheet);
            return;
          } catch (e) {}
          try {
            var el = document.createElement('style');
            el.textContent = css;
            var put = function () {
              (document.head || document.documentElement).appendChild(el);
            };
            put();
            document.addEventListener('DOMContentLoaded', put, true);
            window.addEventListener('load', put, true);
          } catch (e) {}
        })();
    """.trimIndent()
}
