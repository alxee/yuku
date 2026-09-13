package com.yuku.browser.core

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * The page-side half of address and card autofill: noticing that the cursor
 * has landed in a checkout form, and filling one in.
 *
 * Built exactly like [PasswordForms], because from outside WebView there is
 * exactly one way in — a document-start script and a `@JavascriptInterface`
 * bridge — and the same two rules apply to it:
 *
 *  - **Nothing on the bridge returns anything.** A page's own scripts can call
 *    every method here; the worst that does is tell the browser a field took
 *    focus, which the browser already knew. Filling is Kotlin-initiated only,
 *    through `window.__afFill`, and only ever from a chip the user tapped.
 *  - **A field is classified by what the SITE said it was**, first and always:
 *    the `autocomplete` tokens the HTML standard defines for this exact
 *    purpose. The name/id/label patterns below are the fallback for the sites
 *    that never wrote them, and they are matched in a fixed order because
 *    "address" appears inside "email address" and "name" inside "cardholder
 *    name" — the more specific pattern has to be asked first.
 *
 * The one thing this deliberately does not do is report an address form on a
 * page that has a password field on it. An email box above a password is a
 * sign-in, not a checkout, and offering to fill an address into it would put
 * two bars in front of one keyboard.
 */
object FormFields {

    private const val BRIDGE_NAME = "__afBridge"

    /** Which of the two kinds of form the cursor is standing in. */
    enum class Group(val id: String) {
        Address("address"),
        Card("card");

        companion object {
            fun byId(id: String?): Group? = entries.firstOrNull { it.id == id }
        }
    }

    interface Listener {
        /** Focus entered ([group] non-null) or left a fillable form. Main thread. */
        fun onFieldFocus(group: Group?)
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
        fun focus(group: String?) {
            val parsed = Group.byId(group)
            main.post { listener.onFieldFocus(parsed) }
        }
    }

    /**
     * Every spelling of an address this knows how to put somewhere, so that a
     * form asking for two name fields and one asking for one both get filled
     * from the same record. Built here rather than in the page: what the
     * vault holds and what the standard's tokens are called is Kotlin's
     * business, and the script's job is only to find the boxes.
     */
    fun fillAddressScript(address: SavedAddress): String {
        val values = JSONObject().apply {
            put("name", address.name)
            put("given-name", address.givenName)
            put("family-name", address.familyName)
            put("organization", address.organization)
            put("street-address", address.street)
            // A two-line form gets the street on the first line and nothing
            // on the second: guessing a split out of one stored line puts
            // half a street name in an "apartment" box.
            put("address-line1", address.street.lineSequence().firstOrNull().orEmpty())
            put("address-line2", address.street.lineSequence().drop(1).joinToString(" ").trim())
            put("address-level2", address.city)
            put("address-level1", address.region)
            put("postal-code", address.postalCode)
            put("country", address.country)
            put("country-name", address.country)
            put("email", address.email)
            put("tel", address.phone)
        }
        return fillScript(Group.Address, values)
    }

    fun fillCardScript(card: SavedCard): String {
        val month = if (card.expiryMonth in 1..12) "%02d".format(card.expiryMonth) else ""
        val year = if (card.expiryYear > 0) card.expiryYear.toString() else ""
        val values = JSONObject().apply {
            put("cc-name", card.cardholder)
            // Digits only: a number typed with spaces is the same number, and
            // a field with a maxlength or its own formatter chokes on them.
            put("cc-number", card.digits)
            put("cc-exp-month", month)
            put("cc-exp-year", year)
            put("cc-exp", if (month.isNotEmpty() && year.isNotEmpty()) "$month/${year.takeLast(2)}" else "")
            // cc-csc is deliberately absent. See [SavedCard].
        }
        return fillScript(Group.Card, values)
    }

    private fun fillScript(group: Group, values: JSONObject): String =
        "(function(k,v){ try { return window.__afFill(k,v); } catch(e) { return false; } })(" +
            "${JSONObject.quote(group.id)},$values)"

    /**
     * Runs in every frame of every page. Everything is wrapped: a page that
     * throws inside our listener is still a page that has to work.
     */
    private val SCRIPT = """
        (function(){
          if (window.__afInstalled) return;
          window.__afInstalled = true;

          function bridge(){ return window.$BRIDGE_NAME; }

          function visible(el){
            if (!el || el.disabled || el.readOnly) return false;
            var r = el.getBoundingClientRect();
            if (r.width < 2 || r.height < 2) return false;
            var s = window.getComputedStyle(el);
            return s.visibility !== 'hidden' && s.display !== 'none' &&
                   parseFloat(s.opacity || '1') > 0.05;
          }

          // Shadow roots are walked for the same reason PasswordForms walks
          // them: a checkout inside a custom element is still a checkout.
          function deep(root, out){
            var nodes;
            try { nodes = root.querySelectorAll('input,select'); } catch (e) { return out; }
            for (var i = 0; i < nodes.length; i++) out.push(nodes[i]);
            var all;
            try { all = root.querySelectorAll('*'); } catch (e) { return out; }
            for (var j = 0; j < all.length; j++){
              if (all[j].shadowRoot) deep(all[j].shadowRoot, out);
            }
            return out;
          }

          function fields(){ return deep(document, []); }

          function hasPassword(){
            var f = fields();
            for (var i = 0; i < f.length; i++){
              if ((f[i].type || '').toLowerCase() === 'password' && visible(f[i])) return true;
            }
            return false;
          }

          var KNOWN = {
            'name':1,'given-name':1,'family-name':1,'additional-name':1,'organization':1,
            'street-address':1,'address-line1':1,'address-line2':1,'address-level1':1,
            'address-level2':1,'postal-code':1,'country':1,'country-name':1,
            'email':1,'tel':1,'tel-national':1,
            'cc-name':1,'cc-number':1,'cc-exp':1,'cc-exp-month':1,'cc-exp-year':1
          };
          // Everything the standard allows to sit in front of the token
          // itself: 'shipping cc-number', 'section-one billing tel'.
          var IGNORED = { 'shipping':1, 'billing':1, 'home':1, 'work':1, 'mobile':1, 'fax':1, 'pager':1 };

          // Order matters. The card patterns come first because a checkout
          // page has both kinds of box on it, and inside them the specific
          // ones come before the general: 'expiry month' contains 'month',
          // 'name on card' contains 'name', 'email address' contains
          // 'address'.
          var HINTS = [
            ['cc-csc',       /cvv|cvc|\bcsc\b|security.?code|card.?code/],
            ['cc-number',    /card.?number|cardnum|ccnum|cc.?num|creditcard|numero.?de.?carte/],
            ['cc-exp-month', /(exp|valid).{0,10}month|month.{0,10}(exp|valid)|\bexpmonth\b|\bccmonth\b/],
            ['cc-exp-year',  /(exp|valid).{0,10}year|year.{0,10}(exp|valid)|\bexpyear\b|\bccyear\b/],
            ['cc-exp',       /expir|\bexp.?date\b|valid.?thru|valid.?until/],
            ['cc-name',      /card.?holder|holder.?name|name.?on.?card|ccname|cc.?name/],
            ['email',        /e-?mail/],
            ['tel',          /phone|telephone|\btel\b|mobile|handy/],
            ['postal-code',  /postal|post.?code|\bzip\b|\bplz\b|\bcap\b/],
            ['address-level1', /\bstate\b|province|\bregion\b|county|prefecture/],
            ['address-level2', /\bcity\b|\btown\b|suburb|locality|\bort\b/],
            ['address-line2', /address.?2|addr.?2|line.?2|apartment|\bapt\b|\bsuite\b|unit\b/],
            ['address-line1', /address.?1|addr.?1|line.?1/],
            ['street-address', /street|\baddress\b|\baddr\b|strasse|adresse/],
            ['country',      /country|\bland\b|\bpays\b/],
            ['organization', /company|organi[sz]ation|\bfirm\b|business/],
            ['given-name',   /first.?name|given.?name|\bfname\b|forename|vorname/],
            ['family-name',  /last.?name|family.?name|surname|\blname\b|nachname/],
            ['name',         /full.?name|your.?name|\bname\b/]
          ];

          function labelText(el){
            try {
              if (el.id){
                var l = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                if (l) return l.textContent || '';
              }
              var p = el.closest ? el.closest('label') : null;
              return p ? (p.textContent || '') : '';
            } catch (e) { return ''; }
          }

          function kindOf(el){
            var tag = (el.tagName || '').toLowerCase();
            if (tag !== 'input' && tag !== 'select') return null;
            var type = (el.type || 'text').toLowerCase();
            if (type === 'password' || type === 'hidden' || type === 'checkbox' ||
                type === 'radio' || type === 'submit' || type === 'button' ||
                type === 'file' || type === 'search' || type === 'range') return null;
            var ac = (el.getAttribute('autocomplete') || '').toLowerCase().split(/\s+/);
            for (var i = 0; i < ac.length; i++){
              var t = ac[i];
              if (!t || IGNORED[t] || t.indexOf('section-') === 0) continue;
              if (KNOWN[t]) return t;
              // The site said something specific and it wasn't one of ours —
              // 'cc-csc', 'one-time-code', 'current-password'. Guessing past
              // that from the field's name is how a security code ends up
              // with a card number in it.
              if (t !== 'on' && t !== 'off') return null;
            }
            var hint = [
              el.getAttribute('name') || '', el.id || '',
              el.getAttribute('placeholder') || '', el.getAttribute('aria-label') || '',
              labelText(el)
            ].join(' ').toLowerCase();
            if (!hint) return null;
            for (var j = 0; j < HINTS.length; j++){
              if (HINTS[j][1].test(hint)) return HINTS[j][0];
            }
            return null;
          }

          function groupOf(k){
            if (!k) return null;
            // cc-csc is recognised so that it is never mistaken for something
            // else, and then refused: nothing here has one to give.
            if (k === 'cc-csc') return null;
            return k.indexOf('cc-') === 0 ? 'card' : 'address';
          }

          try {
            document.addEventListener('focusin', function(e){
              try {
                var el = e.target;
                if (!el) return;
                var g = groupOf(kindOf(el));
                if (!g) return;
                // A sign-in page's email box is not a checkout. See the file
                // comment: the password bar owns that form.
                if (g === 'address' && hasPassword()) return;
                var b = bridge();
                if (b) b.focus(g);
              } catch (err) {}
            }, true);
            document.addEventListener('focusout', function(){
              // Deferred for PasswordForms' reason: moving from one field of
              // a form to the next is a focusout followed by a focusin, and
              // reporting the gap would blink the bar off and on again.
              setTimeout(function(){
                try {
                  var a = document.activeElement;
                  if (a && groupOf(kindOf(a))) return;
                  var b = bridge();
                  if (b) b.focus('');
                } catch (err) {}
              }, 120);
            }, true);
          } catch (e) {}

          function setNative(el, v){
            var proto = el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
            var desc = Object.getOwnPropertyDescriptor(proto, 'value');
            var setter = desc && desc.set;
            if (setter) setter.call(el, v); else el.value = v;
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
          }

          // A <select> cannot be assigned an arbitrary string — a country
          // picker holds 'GB' or 'United Kingdom' or '826', and writing
          // anything else silently leaves it on its first option. So the
          // options are searched, and a select with no match is left alone
          // rather than cleared.
          function putSelect(el, v){
            var want = String(v).trim().toLowerCase();
            var opts = el.options || [];
            for (var pass = 0; pass < 2; pass++){
              for (var i = 0; i < opts.length; i++){
                var value = (opts[i].value || '').trim().toLowerCase();
                var text = (opts[i].text || '').trim().toLowerCase();
                var hit = pass === 0
                  ? (value === want || text === want)
                  : (value.indexOf(want) === 0 || text.indexOf(want) === 0 ||
                     want.indexOf(value) === 0 && value.length > 1);
                if (hit){
                  el.selectedIndex = i;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                  return true;
                }
              }
            }
            return false;
          }

          function put(el, v){
            try {
              try { el.focus(); } catch (e) {}
              if ((el.tagName || '').toLowerCase() === 'select') return putSelect(el, v);
              setNative(el, String(v));
              return true;
            } catch (e) { return false; }
          }

          // Called only from Kotlin, with a record the user picked.
          window.__afFill = function(kind, values){
            try {
              var all = fields();
              var n = 0;
              for (var i = 0; i < all.length; i++){
                var el = all[i];
                if (!visible(el)) continue;
                var k = kindOf(el);
                if (!k || groupOf(k) !== kind) continue;
                var v = values[k];
                if (v === undefined || v === null || v === '') continue;
                if (put(el, v)) n++;
              }
              try { if (document.activeElement && document.activeElement.blur) document.activeElement.blur(); } catch (e) {}
              return n > 0;
            } catch (e) { return false; }
          };
        })();
    """.trimIndent()
}
