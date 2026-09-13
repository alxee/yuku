package com.yuku.browser.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Web Notifications, which WebView does not have at all: `window.Notification`
 * is simply absent, and no client callback is ever asked about one.
 *
 * So the API is supplied from outside, like every other page-side subsystem
 * here — a document-start script defines `Notification` (and
 * `ServiceWorkerRegistration.showNotification`, and the `notifications` answer
 * of `navigator.permissions.query`), and posts what the page does to Kotlin,
 * which draws it as an Android notification.
 *
 * The bridge is a WEB MESSAGE LISTENER, not a `@JavascriptInterface`, and that
 * is the point of it: a message arrives with the ORIGIN of the frame that sent
 * it, stamped by the engine. A permission is keyed by origin, and an interface
 * object cannot say which frame called it — a hostile iframe could post as its
 * parent. Nothing the page sends is trusted beyond "this origin wants this".
 *
 * `Notification.permission` must answer SYNCHRONOUSLY from document start (a
 * page reads it before deciding whether to show a bell), so the answers are
 * compiled INTO the script: [install] is called again whenever any of them
 * change, replacing the handler. The document already on screen keeps the
 * value it started with, except where it asked — that answer is posted back.
 *
 * What is NOT here, and cannot be: push. A notification exists only while its
 * page is loaded in a tab; nothing runs a site's service worker in the
 * background to receive one.
 */
object WebNotifications {

    private const val BRIDGE_NAME = "__yukuNotify"

    const val CHANNEL_ID = "site_notifications"

    /** The intent a tapped notification opens the browser with. */
    const val ACTION_OPEN = "com.yuku.browser.action.OPEN_NOTIFICATION"
    const val EXTRA_TAB_ID = "tabId"
    const val EXTRA_URL = "url"
    const val EXTRA_KEY = "key"

    /** What the script reports `Notification.permission` as. */
    enum class State(val js: String) { Granted("granted"), Denied("denied"), Default("default") }

    /** One `new Notification(…)` / `showNotification(…)`, as the page asked for it. */
    data class Request(
        val jsId: Int,
        val title: String,
        val body: String,
        val tag: String,
        val silent: Boolean,
        val requireInteraction: Boolean,
        val renotify: Boolean,
        val pageUrl: String,
    )

    interface Listener {
        /** The page wants to know whether it may notify. Main thread. */
        fun onRequestPermission(origin: String, mainFrame: Boolean, reply: (State) -> Unit)

        /** The page raised a notification. Answer `true` if it was shown. Main thread. */
        fun onShow(origin: String, request: Request, proxy: JavaScriptReplyProxy): Boolean

        /** The page closed one of its own. Main thread. */
        fun onClose(origin: String, jsId: Int, proxy: JavaScriptReplyProxy)
    }

    val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** Wires the bridge. Once per WebView, before its first load. */
    fun attach(web: WebView, listener: Listener) {
        if (!supported) return
        WebViewCompat.addWebMessageListener(web, BRIDGE_NAME, setOf("*")) { _, message, sourceOrigin, isMainFrame, proxy ->
            handle(listener, message, sourceOrigin, isMainFrame, proxy)
        }
    }

    /**
     * Registers the script with these answers baked in, replacing [previous].
     * [sites] maps an origin (as `location.origin` spells it) to its answer;
     * [fallback] is what every other origin gets.
     */
    fun install(web: WebView, previous: ScriptHandler?, fallback: State, sites: Map<String, State>): ScriptHandler? {
        previous?.remove()
        if (!supported) return null
        val state = JSONObject().apply {
            put("d", fallback.js)
            put("s", JSONObject().apply { sites.forEach { (origin, answer) -> put(origin, answer.js) } })
        }
        return WebViewCompat.addDocumentStartJavaScript(web, SCRIPT.replace("%STATE%", state.toString()), setOf("*"))
    }

    private fun handle(
        listener: Listener,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        proxy: JavaScriptReplyProxy,
    ) {
        val m = runCatching { JSONObject(message.data ?: return) }.getOrNull() ?: return
        // An opaque origin ("null") has nobody to attribute a notification to.
        val origin = sourceOrigin.toString().trimEnd('/')
        if (origin.isBlank() || origin == "null") return
        when (m.optString("t")) {
            "request" -> {
                val r = m.optInt("r")
                listener.onRequestPermission(origin, isMainFrame) { state ->
                    post(proxy, JSONObject().put("t", "perm").put("r", r).put("p", state.js))
                }
            }
            "show" -> {
                val request = Request(
                    jsId = m.optInt("id"),
                    title = m.optString("title").take(MAX_TITLE),
                    body = m.optString("body").take(MAX_BODY),
                    tag = m.optString("tag").take(MAX_TAG),
                    silent = m.optBoolean("silent"),
                    requireInteraction = m.optBoolean("requireInteraction"),
                    renotify = m.optBoolean("renotify"),
                    pageUrl = m.optString("url"),
                )
                val shown = listener.onShow(origin, request, proxy)
                post(proxy, JSONObject().put("t", if (shown) "show" else "error").put("id", request.jsId))
            }
            "close" -> listener.onClose(origin, m.optInt("id"), proxy)
        }
    }

    /** Tells a page one of its notifications was tapped or went away. */
    fun dispatch(proxy: JavaScriptReplyProxy, jsId: Int, event: String) {
        post(proxy, JSONObject().put("t", event).put("id", jsId))
    }

    // A proxy belongs to a document that may have navigated away or been
    // destroyed since; posting to it then is dropped, or throws on an old
    // provider.
    private fun post(proxy: JavaScriptReplyProxy, json: JSONObject) {
        runCatching { proxy.postMessage(json.toString()) }
    }

    // ------------------------------------------------------------- android

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Site notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications from websites you've allowed to send them."
            },
        )
    }

    /**
     * Draws one. [androidTag] is what replaces: a page's own `tag` maps to
     * one Android tag per origin, so a chat re-raising "new messages" updates
     * the notification instead of stacking a second.
     */
    @SuppressLint("MissingPermission") // Checked: areNotificationsEnabled covers POST_NOTIFICATIONS.
    fun post(
        context: Context,
        androidTag: String,
        origin: String,
        request: Request,
        tabId: Long,
        icon: Bitmap?,
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        // By name, so core/ holds no reference to the Activity layer. The
        // context's own package is the applicationId (`.debug` included).
        val open = Intent().setClassName(context, "com.yuku.browser.MainActivity")
            .setAction(ACTION_OPEN)
            // Unique per notification, or PendingIntent folds every one into
            // the first and a tap on the third opens the first's tab.
            .setData(Uri.fromParts("yuku-notification", androidTag, null))
            .putExtra(EXTRA_TAB_ID, tabId)
            .putExtra(EXTRA_URL, request.pageUrl)
            .putExtra(EXTRA_KEY, androidTag)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            androidTag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.yuku.browser.R.drawable.ic_notification)
            .setContentTitle(request.title.ifBlank { displayOrigin(origin) })
            .setContentText(request.body.takeIf { it.isNotBlank() })
            .setStyle(request.body.takeIf { it.isNotBlank() }?.let { NotificationCompat.BigTextStyle().bigText(it) })
            // Who is talking, always: a page can put anything in the title,
            // including another site's name.
            .setSubText(displayOrigin(origin))
            .setLargeIcon(icon)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setSilent(request.silent)
            .setOnlyAlertOnce(!request.renotify)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        return runCatching { manager.notify(androidTag, 0, notification) }.isSuccess
    }

    fun cancel(context: Context, androidTag: String) {
        NotificationManagerCompat.from(context).cancel(androidTag, 0)
    }

    private const val MAX_TITLE = 200
    private const val MAX_BODY = 2000
    private const val MAX_TAG = 200

    /**
     * The page half. `%STATE%` is `{d: fallback, s: {origin: answer}}`.
     *
     * Asking requires TRANSIENT USER ACTIVATION (Firefox's rule): a page that
     * asks on load is answered "default" without anything reaching the
     * screen, since a question nobody's tap led to is a question nobody is
     * ready to answer.
     */
    private val SCRIPT = """
(function () {
  var bridge = window.$BRIDGE_NAME;
  if (!bridge || window.__yukuNotifyInstalled) return;
  Object.defineProperty(window, '__yukuNotifyInstalled', { value: true });
  var STATE = %STATE%;
  var origin = '';
  try { origin = location.origin; } catch (e) {}
  var perm = (origin && origin !== 'null') ? (STATE.s[origin] || STATE.d) : 'denied';
  var live = {};
  var pending = {};
  var seq = 0;
  var rseq = 0;

  function fire(target, type) {
    var ev = new Event(type, { cancelable: true });
    target.dispatchEvent(ev);
    var handler = target['on' + type];
    if (typeof handler === 'function') {
      try { handler.call(target, ev); } catch (e) { setTimeout(function () { throw e; }); }
    }
  }

  bridge.addEventListener('message', function (e) {
    var m;
    try { m = JSON.parse(e.data); } catch (x) { return; }
    if (m.t === 'perm') {
      perm = m.p;
      var done = pending[m.r];
      delete pending[m.r];
      if (done) done(perm);
      return;
    }
    var n = live[m.id];
    if (!n) return;
    if (m.t === 'close' || m.t === 'error') delete live[m.id];
    if (m.t === 'click') {
      delete live[m.id];
      fire(n, 'click');
      fire(n, 'close');
      return;
    }
    fire(n, m.t);
  });

  function str(v) { return v == null ? '' : String(v); }

  function absolute(u) {
    if (!u) return '';
    try { return new URL(String(u), document.baseURI).href; } catch (e) { return ''; }
  }

  function send(id, title, options) {
    bridge.postMessage(JSON.stringify({
      t: 'show',
      id: id,
      title: title,
      body: str(options.body),
      tag: str(options.tag),
      silent: !!options.silent,
      requireInteraction: !!options.requireInteraction,
      renotify: !!options.renotify,
      url: location.href
    }));
  }

  class Notification extends EventTarget {
    constructor(title, options) {
      super();
      if (arguments.length < 1) {
        throw new TypeError("Failed to construct 'Notification': 1 argument required, but only 0 present.");
      }
      options = options || {};
      var id = ++seq;
      Object.defineProperty(this, '__id', { value: id });
      this.title = str(title);
      this.body = str(options.body);
      this.tag = str(options.tag);
      this.icon = absolute(options.icon);
      this.image = absolute(options.image);
      this.badge = absolute(options.badge);
      this.data = options.data === undefined ? null : options.data;
      this.dir = options.dir || 'auto';
      this.lang = str(options.lang);
      this.silent = options.silent == null ? null : !!options.silent;
      this.renotify = !!options.renotify;
      this.requireInteraction = !!options.requireInteraction;
      this.timestamp = options.timestamp || Date.now();
      this.actions = [];
      this.vibrate = [];
      this.onclick = null;
      this.onshow = null;
      this.onclose = null;
      this.onerror = null;
      var self = this;
      if (perm !== 'granted') {
        setTimeout(function () { fire(self, 'error'); });
        return;
      }
      live[id] = this;
      send(id, this.title, options);
    }

    close() {
      var id = this.__id;
      if (!live[id]) return;
      delete live[id];
      bridge.postMessage(JSON.stringify({ t: 'close', id: id }));
      var self = this;
      setTimeout(function () { fire(self, 'close'); });
    }

    static get permission() { return perm; }

    static get maxActions() { return 0; }

    static requestPermission(callback) {
      var p = new Promise(function (resolve) {
        if (perm !== 'default') { resolve(perm); return; }
        var ua = navigator.userActivation;
        if (ua && !ua.isActive) { resolve('default'); return; }
        var r = ++rseq;
        pending[r] = resolve;
        bridge.postMessage(JSON.stringify({ t: 'request', r: r }));
      });
      if (typeof callback === 'function') p.then(callback);
      return p;
    }
  }

  Object.defineProperty(window, 'Notification', {
    value: Notification, writable: true, configurable: true, enumerable: false
  });

  var SWR = window.ServiceWorkerRegistration;
  if (SWR && SWR.prototype) {
    SWR.prototype.showNotification = function (title, options) {
      if (perm !== 'granted') {
        return Promise.reject(new TypeError('No notification permission has been granted for this origin.'));
      }
      var id = ++seq;
      send(id, str(title), options || {});
      return Promise.resolve();
    };
    SWR.prototype.getNotifications = function () { return Promise.resolve([]); };
  }

  var perms = navigator.permissions;
  if (perms && typeof perms.query === 'function') {
    var query = perms.query.bind(perms);
    perms.query = function (descriptor) {
      if (descriptor && descriptor.name === 'notifications') {
        var status = new EventTarget();
        Object.defineProperty(status, 'name', { value: 'notifications' });
        Object.defineProperty(status, 'state', {
          get: function () { return perm === 'default' ? 'prompt' : perm; }
        });
        status.onchange = null;
        return Promise.resolve(status);
      }
      return query(descriptor);
    };
  }
})();
""".trimIndent()
}
