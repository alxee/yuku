package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * The page-side half of saved passwords: noticing a login being submitted,
 * noticing a login form the user is standing in front of, and filling one in.
 *
 * A real browser does this inside the renderer, where the password manager is
 * part of the engine. From outside WebView there is exactly one way in — a
 * document-start script and a `@JavascriptInterface` bridge — so that is what
 * this is, built around three signals:
 *
 *  - **A submission carrying a password.** Not one hook but several, because
 *    "the user just logged in" has no single event: a classic `<form>` submit,
 *    a script calling `form.submit()` (which fires no submit event at all),
 *    Enter in the password field, a click on the button that plays the part of
 *    one, the page being navigated away from, and an SPA route change pushed
 *    through `history`. Whichever fires first wins; the rest are deduped in
 *    the page against the last thing reported.
 *  - **A login form being present**, reported once the document has one, which
 *    is what auto sign-in waits for rather than guessing from the URL.
 *  - **Focus landing in one**, which is when a suggestion is worth putting on
 *    screen and not a moment before.
 *
 * Filling goes the other way, and only ever from Kotlin: [fillScript] is
 * evaluated with the credential the user picked. **Nothing on this bridge
 * returns a password.** A page's own scripts can call every method here — it
 * is the same window — so the bridge is built so that the worst a hostile page
 * can do with it is ask for a save prompt whose contents the user can see and
 * decline. It cannot ask for a fill, and it cannot read what is stored.
 *
 * The username paired with a password field is the last text-ish input before
 * it in the same form (or, for the formless login pages that are now common,
 * in the document), preferring one the site itself labelled with
 * `autocomplete="username"`. That heuristic is the same one every password
 * manager uses, and it is wrong on the same pages theirs are.
 *
 * Cross-origin iframes are out of reach here, as they are for any script: a
 * login rendered inside an embedded identity provider's frame is not seen.
 */
object PasswordForms {

    private const val BRIDGE_NAME = "__pwBridge"

    /** Which kind of field the user's cursor just landed in. */
    enum class Field { Username, Password }

    interface Listener {
        /** A submission carried this credential. Always on the main thread. */
        fun onCaptured(username: String, password: String)

        /** Whether this document currently has a usable login form. */
        fun onLoginFormPresent(present: Boolean)

        /** Focus entered ([field] non-null) or left a login field. */
        fun onFieldFocus(field: Field?)
    }

    fun attach(web: WebView, listener: Listener) {
        web.addJavascriptInterface(Bridge(listener), BRIDGE_NAME)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SCRIPT, setOf("*"))
        }
    }

    class Bridge internal constructor(private val listener: Listener) {
        private val main = Handler(Looper.getMainLooper())

        /** Called from a renderer thread. */
        @JavascriptInterface
        fun captured(username: String?, password: String?) {
            val pass = password.orEmpty()
            if (pass.isEmpty()) return
            main.post { listener.onCaptured(username.orEmpty(), pass) }
        }

        @JavascriptInterface
        fun form(present: Boolean) {
            main.post { listener.onLoginFormPresent(present) }
        }

        @JavascriptInterface
        fun focus(kind: String?) {
            val field = when (kind) {
                "password" -> Field.Password
                "username" -> Field.Username
                else -> null
            }
            main.post { listener.onFieldFocus(field) }
        }
    }

    /**
     * Puts [username]/[password] into the login form, as a script to hand to
     * `evaluateJavascript`. Values go in through the native `value` setter and
     * are followed by `input` and `change` events, which is what a framework-
     * rendered form (React's synthetic events in particular) needs to see —
     * assigning `el.value` alone leaves the site's own state untouched, so the
     * field looks filled and the submission goes out empty.
     */
    fun fillScript(username: String, password: String): String =
        "(function(u,p){ try { return window.__pwFill(u,p); } catch(e) { return false; } })(" +
            "${JSONObject.quote(username)},${JSONObject.quote(password)})"

    /**
     * Runs in every frame of every page. Everything is wrapped: a page that
     * throws inside our listener is still a page that has to work, and this
     * one sits on `submit`, which is the single event a login page cannot
     * afford to have broken.
     */
    private val SCRIPT = """
        (function(){
          if (window.__pwInstalled) return;
          window.__pwInstalled = true;

          function bridge(){ return window.$BRIDGE_NAME; }

          function visible(el){
            if (!el || el.disabled) return false;
            var r = el.getBoundingClientRect();
            if (r.width < 2 || r.height < 2) return false;
            var s = window.getComputedStyle(el);
            return s.visibility !== 'hidden' && s.display !== 'none' &&
                   parseFloat(s.opacity || '1') > 0.05;
          }

          // Shadow roots are walked because a login form inside a custom
          // element is still a login form; cross-origin frames are not
          // reachable from here at all.
          function deep(root, out){
            var nodes;
            try { nodes = root.querySelectorAll('input'); } catch (e) { return out; }
            for (var i = 0; i < nodes.length; i++) out.push(nodes[i]);
            var all;
            try { all = root.querySelectorAll('*'); } catch (e) { return out; }
            for (var j = 0; j < all.length; j++){
              if (all[j].shadowRoot) deep(all[j].shadowRoot, out);
            }
            return out;
          }

          function inputs(){ return deep(document, []); }

          function passwords(){
            return inputs().filter(function(el){
              return (el.type || '').toLowerCase() === 'password' && visible(el);
            });
          }

          function textish(el){
            var t = (el.type || 'text').toLowerCase();
            return (t === 'text' || t === 'email' || t === 'tel' || t === 'url' ||
                    t === 'number' || t === '') && visible(el);
          }

          // The site's own label first, then position: the nearest text field
          // ahead of the password in the same form, or in the document when
          // the page never wrapped its login in one.
          function usernameFor(pw){
            var scope = pw.form ? Array.prototype.slice.call(pw.form.elements) : inputs();
            var idx = scope.indexOf(pw);
            if (idx < 0) idx = scope.length;
            var declared = null, nearest = null;
            for (var i = 0; i < idx; i++){
              var el = scope[i];
              if (!el || el.tagName !== 'INPUT' || !textish(el)) continue;
              var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
              if (ac.indexOf('username') >= 0 || ac.indexOf('email') >= 0) declared = el;
              nearest = el;
            }
            return declared || nearest;
          }

          var lastSent = '';

          function report(){
            try {
              var pws = passwords().filter(function(el){ return el.value; });
              // A sign-up form's confirmation field holds the same secret as
              // the one before it; a change-password form's LAST field is the
              // new one. Either way the last filled field is the one worth
              // keeping.
              var pw = pws[pws.length - 1];
              if (!pw) return;
              var user = usernameFor(pw);
              var u = user ? (user.value || '') : '';
              var key = u + ' ' + pw.value;
              if (key === lastSent) return;
              lastSent = key;
              var b = bridge();
              if (b) b.captured(u, pw.value);
            } catch (e) {}
          }

          function soon(){ setTimeout(report, 0); }

          try {
            // Capture phase: the page's own handler may well call
            // preventDefault and navigate by hand, and by the time it has,
            // the fields can already be gone.
            document.addEventListener('submit', soon, true);
            document.addEventListener('keydown', function(e){
              if (e.key === 'Enter' || e.keyCode === 13) soon();
            }, true);
            document.addEventListener('click', function(e){
              var el = e.target;
              for (var d = 0; el && d < 4; d++){
                var tag = (el.tagName || '').toLowerCase();
                var type = (el.type || '').toLowerCase();
                if (tag === 'button' || type === 'submit' || type === 'button' ||
                    (el.getAttribute && el.getAttribute('role') === 'button')){
                  // Late enough that a script which clears the form on click
                  // has done so, early enough to beat the navigation.
                  setTimeout(report, 60);
                  return;
                }
                el = el.parentElement;
              }
            }, true);
            // The page going away is the last chance to look at its fields,
            // and the one signal a login that navigates cannot avoid.
            window.addEventListener('pagehide', report, true);
            window.addEventListener('beforeunload', report, true);
          } catch (e) {}

          // A single-page app signs in without ever leaving the document, so
          // its "navigation" is a history entry.
          try {
            ['pushState', 'replaceState'].forEach(function(name){
              var orig = history[name];
              if (typeof orig !== 'function') return;
              history[name] = function(){
                try { report(); } catch (e) {}
                return orig.apply(this, arguments);
              };
            });
            window.addEventListener('popstate', soon, true);
          } catch (e) {}

          // form.submit() fires no submit event at all — the spec says so —
          // so a login posted by script would otherwise be invisible.
          try {
            var nativeSubmit = HTMLFormElement.prototype.submit;
            HTMLFormElement.prototype.submit = function(){
              try { report(); } catch (e) {}
              return nativeSubmit.apply(this, arguments);
            };
          } catch (e) {}

          try {
            document.addEventListener('focusin', function(e){
              var el = e.target;
              if (!el || el.tagName !== 'INPUT') return;
              var b = bridge();
              if (!b) return;
              if ((el.type || '').toLowerCase() === 'password') { b.focus('password'); return; }
              // A username field is only interesting while there is a
              // password field with it — otherwise it is a search box.
              if (textish(el) && passwords().length) b.focus('username');
            }, true);
            document.addEventListener('focusout', function(){
              // A tap from the username field to the password field is a
              // focusout followed immediately by a focusin, and reporting the
              // gap would blink the suggestion off and on again.
              setTimeout(function(){
                try {
                  var a = document.activeElement;
                  if (a && a.tagName === 'INPUT' &&
                      ((a.type || '').toLowerCase() === 'password' || textish(a))) return;
                  var b = bridge();
                  if (b) b.focus('');
                } catch (e) {}
              }, 120);
            }, true);
          } catch (e) {}

          // Whether there is a login form here at all — throttled, since the
          // answer only changes when the page builds or tears one down.
          var lastForm = null;
          var pendingForm = false;
          function reportForm(){
            pendingForm = false;
            try {
              var present = passwords().length > 0;
              if (present === lastForm) return;
              lastForm = present;
              var b = bridge();
              if (b) b.form(present);
            } catch (e) {}
          }
          function scheduleForm(){
            if (pendingForm) return;
            pendingForm = true;
            setTimeout(reportForm, 300);
          }

          try {
            document.addEventListener('DOMContentLoaded', scheduleForm, true);
            window.addEventListener('load', scheduleForm, true);
            if (window.MutationObserver){
              new MutationObserver(scheduleForm).observe(document.documentElement, {
                childList: true, subtree: true,
              });
            }
            scheduleForm();
          } catch (e) {}

          // Called only from Kotlin, with a credential the user picked.
          window.__pwFill = function(u, p){
            try {
              var pws = passwords();
              if (!pws.length) return false;
              // The one the user is standing in, if they are standing in one.
              var active = document.activeElement;
              var pw = pws[0];
              if (active && (active.type || '').toLowerCase() === 'password' &&
                  pws.indexOf(active) >= 0) {
                pw = active;
              } else if (active && active.form) {
                for (var i = 0; i < pws.length; i++){
                  if (pws[i].form === active.form) { pw = pws[i]; break; }
                }
              }
              var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
              var setter = desc && desc.set;
              function put(el, v){
                if (!el) return;
                try { el.focus(); } catch (e) {}
                if (setter) setter.call(el, v); else el.value = v;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              }
              if (u) put(usernameFor(pw), u);
              put(pw, p);
              // Suppresses only the immediate echo of the fill itself — a
              // submission after it is still worth reporting, since the user
              // may well have edited what was put in.
              lastSent = (u || '') + ' ' + p;
              return true;
            } catch (e) { return false; }
          };
        })();
    """.trimIndent()
}
