package com.yuku.browser.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.view.Window
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.yuku.browser.R
import androidx.webkit.ProfileStore
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val PRIVATE_PROFILE = "private"

/** Beyond this many live WebViews the oldest background tabs get torn down. */
// Bumped from 4 -- a background WebView evicted here has to be recreated
// from scratch (fresh loadUrl) the moment it's expanded from the switcher,
// which showed as a blank flash where the static thumbnail used to be.
// Keeping a couple more warm reduces how often that eviction actually hits
// whatever tab the user just scrolled to in the switcher.
private const val MAX_LIVE_WEBVIEWS = 6

/**
 * How many posted site notifications remember the page that raised them, for
 * a tap to be reported back. Past it the oldest still opens its site; its
 * page just doesn't hear the click.
 */
private const val MAX_NOTIFICATION_ROUTES = 100

/** Previews are captured at 1/this of the screen's linear size. */
private const val FACTOR = 2

/** How still the page has to be before a preview is worth capturing. */
private const val SETTLE_MS = 250L

/** Two frames at 60Hz: long enough for a retracted scrollbar to leave the surface a copy is read from. */
private const val SCROLLBAR_RETRACT_MS = 32L

/**
 * How long a captured login waits for the navigation that would confirm it
 * before the save prompt is raised anyway. An ordinary login navigates well
 * inside this; the wait exists for the single-page apps that never do.
 */
private const val CAPTURE_SETTLE_MS = 2_000L

/**
 * What sits behind the document while page dark mode is on — visible while a
 * page loads and past the end of a short one. Matches the near-black a white
 * page lands on under [PageDarkening]'s filter, so there's no seam.
 */
private const val PAGE_DARK_BACKDROP = 0xFF181818.toInt()

// The page dark mode cross-fade (see rebuildForPageDark). The outgoing view
// is the real page, still live, so waiting costs nothing visually — but a
// load slower than these bounds is one the fade shouldn't keep waiting on.
private const val PAGE_DARK_START_TIMEOUT_MS = 700L
private const val PAGE_DARK_LOAD_TIMEOUT_MS = 2500L
/** Between "load finished" and the first frame that shows the finished page. */
private const val PAGE_DARK_PAINT_MS = 120L

/**
 * How long a lifted page cover waits for the frame that finished the load to
 * actually be on screen. A round trip to the renderer, not a guess — and
 * bounded, because a view that is detached or evicted mid-lift would never
 * call back and the cover would be stranded over a live page.
 */
private const val COVER_PAINT_MAX_MS = 1_000L

/**
 * The beat a page cover waits past the end of the load before lifting.
 *
 * A page that has just finished loading very often lays itself out once more
 * — a framework hydrating, a script that waited for `load`, a late web font —
 * and for a few of those frames the document is empty. Uncovering into that
 * is uncovering into the blank the cover exists to hide, just a second later
 * and with a fade on it. Cheap to wait: what is being held is a picture of
 * the same page, over a live one nobody can tell apart from it.
 */
private const val COVER_SETTLE_MS = 250L

// The navigation hold (see BrowserViewModel.pendingHolds).
/** How long a picture taken for a navigation stays usable while that navigation is slow to commit. */
private const val HOLD_MAX_AGE_MS = 8_000L
/**
 * The longest a LINK's hold stays up past its commit. Short: beyond this, the
 * new page drawing itself in is better than the old one frozen on screen.
 */
private const val HOLD_LINK_MAX_MS = 1_500L
/** …and a reload's or a back/forward step's, which is the same page coming back. */
private const val HOLD_RETURN_MAX_MS = 2_500L
/** The beat between the page being ready and the fade, for the frame after it. */
private const val HOLD_SETTLE_MS = 120L
private const val HOLD_POLL_MS = 50L
/** The page cover's own fade (PAGE_COVER_FADE_MS), so the two read as one thing. */
private const val HOLD_FADE_MS = 220L
/** A finger on the page: out of the way now, not in a fifth of a second. */
private const val HOLD_TOUCH_FADE_MS = 90L

/**
 * How long the error screen takes to come up. Not private: a cover over a page
 * that failed waits this long before it lifts, so what it uncovers is the error
 * screen and not the engine's own error page underneath it.
 */
internal const val ERROR_FADE_IN_MS = 200

/**
 * A navigation hold's picture (see BrowserViewModel.pendingHolds): a bitmap
 * drawn at [scale] from the top-left, which is where the copy was taken from.
 * Not an ImageView, which would scale the bitmap by its density on top of
 * that. Non-overlapping, so the fade is an alpha on one draw rather than an
 * offscreen layer the size of the screen for every frame of it.
 */
private class HoldCoverView(
    context: Context,
    private val frame: Bitmap,
    scale: Float,
    // How far down the view the picture's first row belongs: 0 for a copy of
    // the window, the page lens's top overscan for a page-box thumbnail.
    offsetY: Float = 0f,
) : View(context) {
    private val drawMatrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(0f, offsetY)
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        if (!frame.isRecycled) canvas.drawBitmap(frame, drawMatrix, paint)
    }

    override fun hasOverlappingRendering() = false
}

/**
 * Where a parked page was scrolled to, carried in the same bundle as its
 * history — see [saveStateWithScroll]. Our own key in a bundle WebView filled
 * in: `restoreState` ignores what it did not write, and a stale one is read
 * back through the same version guards the rest of the bundle is.
 */
private const val PARKED_SCROLL_Y = "com.yuku.browser.scrollY"

/**
 * How old the merged blocklist may get before auto-update refetches it. A week
 * is what OpenWrt's adblock defaults its own refresh cron to, and these feeds
 * move slowly enough that anything shorter is just traffic.
 */
private const val BLOCKLIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

/** How often a download in flight is asked how far it has got. */
private const val DOWNLOAD_POLL_MS = 250L

/** How long a finished download's full ring stays up before it goes. */
private const val DOWNLOAD_DONE_HOLD_MS = 500L

/**
 * The zoom ladder, as a percentage of the page's own text size. Six rungs
 * either side of the default rather than a desktop browser's eleven, because
 * this one is a SLIDER: every rung has to be reachable by dropping a thumb on
 * it, so the ladder is only as long as the width of the sheet can address
 * without the ticks crowding into each other. It is also deliberately narrow
 * in RANGE — a phone page at 50% is unreadable and at 200% is one word a
 * line, and neither is a place worth being able to land on by accident.
 */
internal val ZOOM_STEPS = intArrayOf(75, 80, 90, 100, 110, 120)

/** What "reset" means, and what a WebView is set to out of the box. */
internal const val DEFAULT_ZOOM = 100

/**
 * How long the page takes to turn. Not private: the chrome's own light/dark
 * animation runs off the same number (see BrowserTheme), because the two are
 * meant to be read as one movement.
 */
internal const val PAGE_DARK_FADE_MS = 420L

/**
 * [ephemeral] is a session that reads the user's settings and writes nothing
 * back: no tabs, no history, no previews, no parked pages. It exists for
 * [com.yuku.browser.CustomTabActivity], which is a second Activity — and so a
 * second ViewModel — alive at the same time as the browser proper. Both would
 * otherwise be saving their own idea of the session over each other's, and a
 * one-page overlay opened from another app has no business being the thing
 * the next launch restores.
 *
 * `@JvmOverloads` is load-bearing, not tidiness: the default
 * [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory] finds the
 * constructor by REFLECTION, looking for exactly `(Application)`. A Kotlin
 * default argument doesn't generate one — the only constructor emitted would
 * be `(Application, boolean)` — so MainActivity's plain `by viewModels()`
 * dies at launch with NoSuchMethodException. The annotation emits the one-arg
 * overload the factory is looking for.
 */
class BrowserViewModel @JvmOverloads constructor(
    app: Application,
    private val ephemeral: Boolean = false,
) : AndroidViewModel(app) {

    /**
     * Every open tab, BOTH spaces — private and ordinary — since persistence,
     * eviction, thumbnail capture and page-state parking all work across the
     * lot. The UI shows one space at a time: it filters this by
     * [privateMode], which is also what every "pick the next tab" decision in
     * here does (see [spaceTabs]).
     */
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow(0L)
    val currentTabId = _currentTabId.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history = _history.asStateFlow()

    /**
     * Visit records per page, keyed by [visitKey]. Survives pages dropping out
     * of the 20-row history window, which is the whole point of it existing
     * separately — see [VisitTally].
     */
    private val _visits = MutableStateFlow<Map<String, VisitTally>>(emptyMap())

    /**
     * Bumped when the app comes back to the front. A tally's score decays and
     * its days age out with the CLOCK, not with anything that writes
     * [_visits], so a list computed yesterday would otherwise stand until the
     * next counted visit.
     */
    private val _visitClock = MutableStateFlow(0L)

    /**
     * "Most visited": pages visited on [MIN_VISIT_DAYS] separate days of the
     * last [VISIT_WINDOW_DAYS], ranked by decayed score with the more recent
     * visit winning ties. Shorter than [MOST_VISITED_LIMIT] — empty, even —
     * rather than padded out with pages seen once, which is what made it look
     * arbitrary. Rendered as [HistoryEntry] so the New Tab list draws one kind
     * of row either way; the favicon is left null because these pages need not
     * be in history at all, and `SiteIcon` resolves one from the host (an
     * in-memory [Favicons] hit for anything seen this session).
     */
    val mostVisited: StateFlow<List<HistoryEntry>> = combine(_visits, _visitClock) { tallies, _ ->
        val now = System.currentTimeMillis()
        val today = localDay(now)
        tallies.values
            .filter { it.daysVisitedWithin(today) >= MIN_VISIT_DAYS }
            .map { it to it.scoreAt(now) }
            .sortedWith(
                compareByDescending<Pair<VisitTally, Double>> { it.second }.thenByDescending { it.first.scoredAt }
            )
            .take(MOST_VISITED_LIMIT)
            .map { (t, _) -> HistoryEntry(title = t.title, url = t.url, host = t.host, visitCount = t.visits) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * What the omnibox matches a half-typed query against: every tallied page,
     * highest current score first — whether or not it has made the list above
     * — then the recent history nothing has counted yet. A tallied page
     * borrows its recent row's captured favicon when there is one.
     */
    val omniboxHistory: StateFlow<List<HistoryEntry>> = combine(_history, _visits) { history, tallies ->
        val now = System.currentTimeMillis()
        val recent = history.associateBy { visitKey(it.url) }
        val ranked = tallies.entries
            .map { it to it.value.scoreAt(now) }
            .sortedByDescending { it.second }
            .map { (entry, _) ->
                val t = entry.value
                HistoryEntry(t.title, t.url, t.host, visitCount = t.visits, favicon = recent[entry.key]?.favicon)
            }
        ranked + history.filter { visitKey(it.url) !in tallies }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * "Recently visited": the pages the user was actually ON, latest first —
     * built from the tallies, which only a visit that passed [noteNavigation]
     * and its dwell ever reaches, rather than from [history], which is every
     * page that finished loading (reloads, back/forward, restores, search
     * results, a mis-tap abandoned after a second). [history] stays that full
     * record for the History screen; this is the New Tab sheet's list. A page
     * borrows its recent row's captured favicon when there is one.
     */
    val recentlyVisited: StateFlow<List<HistoryEntry>> = combine(_history, _visits) { history, tallies ->
        val recent = history.associateBy { visitKey(it.url) }
        tallies.entries
            .sortedByDescending { it.value.lastVisitAt }
            .take(MOST_VISITED_LIMIT)
            .map { (key, t) ->
                HistoryEntry(t.title, t.url, t.host, visitCount = t.visits, favicon = recent[key]?.favicon)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * What a tab's view last committed — see [noteNavigation]. Tied to the
     * VIEW, since a rebuilt or restored WebView has a back/forward list of its
     * own, and dropped by [destroy] so a dead view is not held here.
     */
    private class NavSnapshot(val view: WebView, val index: Int, val urls: List<String>)
    private val navSnapshots = HashMap<Long, NavSnapshot>()

    /** A visit made and not yet counted: waiting out [VISIT_DWELL_MS] on screen. See [syncVisitClock]. */
    private class PendingVisit(var url: String) {
        var shownMs = 0L
        var shownSince = -1L
        var job: Job? = null
        fun shownFor(now: Long) = shownMs + if (shownSince >= 0) now - shownSince else 0L
    }
    private val pendingVisits = HashMap<Long, PendingVisit>()

    /**
     * Tabs whose view's FIRST commit is a page the user asked for — a new tab
     * opened on an address, a popup. Any other view's first commit is a tab
     * coming back (a relaunch, an evicted view rebuilt), which is not a visit.
     */
    private val freshTabs = HashSet<Long>()

    /** Whether the hosting Activity is resumed. True by default for the same reason [pageShowing] is. */
    private var hostResumed = true

    private val _blockedOnPage = MutableStateFlow(0)
    val blockedOnPage = _blockedOnPage.asStateFlow()

    /**
     * Height, in CSS pixels, of the bottom-anchored bar each tab's page is
     * currently showing — absent or 0 for the pages that have none. See
     * [PageBottomBar]: while a tab has one, the page's bottom inset stops at
     * the system navigation bar instead of running under it when the browser's
     * own toolbar hides.
     *
     * Keyed by tab rather than collapsed to the current page because a
     * backgrounded tab keeps its answer — coming back to it should already
     * know, rather than re-measuring after the first scroll.
     */
    private val _pageBottomBars = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val pageBottomBars = _pageBottomBars.asStateFlow()

    /**
     * Per tab, whether what its page shows under the status bar is dark (see
     * [PageTopInset.setStrip]) — which way the status bar's icons go while
     * that page is on screen. Absent: no strip, follow the app's theme.
     */
    private val _statusStripDark = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val statusStripDark = _statusStripDark.asStateFlow()

    /** Per tab, the page's measurement of what is under the status bar. */
    private val _statusStrips = MutableStateFlow<Map<Long, StatusStripReport>>(emptyMap())
    val statusStrips = _statusStrips.asStateFlow()

    private fun setStatusStrip(id: Long, report: StatusStripReport?) {
        _statusStrips.update { current ->
            when {
                report == null -> if (id in current) current - id else current
                current[id] == report -> current
                else -> current + (id to report)
            }
        }
        setStatusStripDark(id, report?.dark)
    }

    private fun setStatusStripDark(id: Long, dark: Boolean?) {
        _statusStripDark.update { current ->
            when {
                dark == null -> if (id in current) current - id else current
                current[id] == dark -> current
                else -> current + (id to dark)
            }
        }
    }

    private fun setPageBottomBar(id: Long, height: Int) {
        _pageBottomBars.update { current ->
            val now = height.coerceAtLeast(0)
            if ((current[id] ?: 0) == now) return@update current
            if (now == 0) current - id else current + (id to now)
        }
    }

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode = _themeMode.asStateFlow()

    // Whether the app's chrome is CURRENTLY rendering dark. Kept in step with
    // themeMode by the UI layer, which pushes the value it resolved back down
    // via setEffectiveDark; seeded here (and at startup, see init) from
    // chromeDark() so it is right BEFORE the first composition rather than
    // one frame after it -- the first WebView is built off pageDarkActive,
    // which is resolved from this. Page darkening tracks this, not themeMode
    // directly, so "System" mode darkens pages exactly when the app's own
    // chrome is actually dark.
    private var isDark = false

    // Page darkening is its OWN setting, not a consequence of the app's
    // theme (see PageDarkMode). Only PageDarkMode.System ties the two
    // together, which is why isDark above still feeds into pageDarkActive.
    private val _pageDarkMode = MutableStateFlow(PageDarkMode.System)
    val pageDarkMode = _pageDarkMode.asStateFlow()

    /**
     * Whether the CURRENT tab's page is being darkened right now — what the
     * menu tile shows, and what [create] builds the next WebView against.
     * Follows the tab, since [pageDarkOverrides] is per tab.
     */
    private val _pageDarkActive = MutableStateFlow(false)
    val pageDarkActive = _pageDarkActive.asStateFlow()

    // ------------------------------------------------------------- reader

    /**
     * Whether the CURRENT tab has an article worth reading — the harvest's
     * own verdict, pushed up from the page (see [ReaderMode]). What the menu
     * row is enabled by: a toggle that would do nothing is a toggle that says
     * so, rather than one that flips and leaves the page as it was.
     */
    private val _readerAvailable = MutableStateFlow(false)
    val readerAvailable = _readerAvailable.asStateFlow()

    /** Whether the CURRENT tab is showing the reader right now. */
    private val _readerActive = MutableStateFlow(false)
    val readerActive = _readerActive.asStateFlow()

    /**
     * Both of the above, per tab, since both follow the page rather than the
     * browser. In memory only and deliberately: the reader is a way of
     * looking at the document that is on screen, so it is spent by navigating
     * away exactly like a find or a zoom-in, and restoring a tab from disk
     * restores the page, not a view of it.
     */
    private val readerAvailability = HashMap<Long, Boolean>()
    private val readerOpen = HashSet<Long>()

    /**
     * The open article's own scroll, for the CURRENT tab only, shaped like the
     * WebView's own (delta, absolute) and in the same device pixels.
     *
     * An event rather than state: it is consumed once, by the toolbar's
     * hide-on-scroll, and a replayed last-value would hide the bar again on
     * every recomposition. The reader scrolls a div inside a shadow root, so
     * `onScrollChanged` never fires and this is the only way that motion
     * reaches the chrome — see [ReaderMode.attach].
     */
    /**
     * How far through the open article the current tab is, 0..1 — null when
     * there is no reader on screen, or the article is too short to scroll.
     *
     * State rather than an event, unlike the scroll above: this is a position,
     * and a position is true until it changes. The app draws it on its own
     * chrome (the toolbar's top edge, then the navigation bar's line once the
     * bar hides), because the page cannot be told where either of those is —
     * its viewport's bottom is not the screen's.
     */
    private val _readerProgress = MutableStateFlow<Float?>(null)
    val readerProgress = _readerProgress.asStateFlow()

    private val _readerScroll = MutableSharedFlow<ReaderScroll>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val readerScroll = _readerScroll.asSharedFlow()

    /**
     * How the reader SETS an article — paper, face, size, leading. Persisted,
     * unlike the two above: whether the reader is open is a thing done to the
     * page in front of you, but how big the text is is the same answer for
     * every article the user will ever read. See [ReaderSettings].
     */
    private val _readerSettings = MutableStateFlow(ReaderSettings())
    val readerSettings = _readerSettings.asStateFlow()

    /**
     * The menu tile's per-tab override of [pageDarkMode], for the tabs that
     * have one. In memory only and deliberately so: the tile is "make THIS
     * page the other one", a thing done to the page in front of you like
     * Desktop or zoom, not a preference. Settings keeps the preference, and
     * a tab with nothing in here follows it. Cleared for every tab the
     * moment that preference (or what System resolves to) changes — an
     * explicit choice in Settings outranks a passing one made over a page —
     * and dropped with the tab.
     */
    private val pageDarkOverrides = HashMap<Long, Boolean>()

    /**
     * What [resolvePageDark] last came out as. Kept because the overrides
     * above are spent by the SETTING changing, and the two things that reach
     * [syncPageDark] — the page dark setting and the chrome's dark state —
     * routinely change without changing what they resolve to (light chrome to
     * dark while pages are pinned Off, say), which is not a decision about
     * pages and should not throw a tab's override away.
     */
    private var resolvedPageDark = false

    /**
     * What darkening a given tab resolves to, in the order the three answers
     * outrank each other: the menu tile's passing override for this tab, then
     * the site's own standing answer, then the app setting.
     */
    private fun pageDarkFor(id: Long): Boolean =
        pageDarkOverrides[id] ?: siteDarkFor(siteOfTab(id)) ?: resolvePageDark()

    /**
     * What each live WebView was actually BUILT with — `prefers-color-scheme`
     * is baked into its context and can't be changed afterwards, so this is
     * what decides which views a change has to rebuild.
     */
    private val builtDark = java.util.WeakHashMap<WebView, Boolean>()

    /** One handle per live WebView for its registered document-start script. */
    private val darkScripts = java.util.WeakHashMap<WebView, ScriptHandler>()

    /**
     * What [applyDarkening] last put ON a view, as against [builtDark], which
     * is what the view was CONSTRUCTED with.
     *
     * The two can disagree, and only in one direction: a site with a dark
     * override of its own is applied when the tab navigates onto it, and the
     * half of darkening that is a filter can be swapped on a live view where
     * the half that is `prefers-color-scheme` cannot — it comes from the
     * theme of the context the WebView was built with, and changing it means
     * building the view again. Doing that mid-navigation would park and
     * restore a page that is in the middle of loading, so the filter is
     * applied on its own there and the context follows on the next view this
     * tab gets. [builtDark] therefore stays honest about the context and this
     * is what says whether the filter needs touching at all.
     */
    private val darkApplied = java.util.WeakHashMap<WebView, Boolean>()

    /**
     * Navigation history parked while a tab's WebView is rebuilt around a new
     * context (see [rebuildForPageDark]). Consumed by [create].
     */
    private val pendingRestores = HashMap<Long, Bundle>()

    /**
     * Whether [restorePageStates] has finished putting the saved session's
     * parked states into [pendingRestores]. Until it has, a miss in that map
     * is not proof there's nothing on disk.
     */
    private var pageStatesLoaded = false

    /**
     * Tabs whose on-disk state has already been handed to a WebView. A file
     * is only ever good for the one restore: after it, the live view owns the
     * tab's history, and reading the file again (a tab evicted by [trimViews]
     * and recreated, say) would rewind the tab to where the app was last
     * closed.
     */
    private val diskStatesUsed = HashSet<Long>()

    private val webContexts = HashMap<Boolean, Context>()

    /**
     * The context every WebView here is ultimately built on, mutable so its
     * base can be swapped: whichever Activity is hosting them while one is,
     * the Application when none is.
     *
     * Both halves of that are load-bearing. **Autofill needs the Activity.**
     * A WebView reports its form fields to the platform autofill service
     * through the ordinary View callbacks — that is what puts saved logins in
     * the keyboard's suggestion strip, per site, from whichever provider the
     * user has chosen — but the session doing it is opened against the
     * Activity the view belongs to (`Context.getAutofillClient()`), and
     * Chromium decides whether to build an autofill provider for the WebView
     * at all by unwrapping its context looking for one. Built on the
     * Application context, which is what this used to do, there is no client,
     * no provider, and no suggestion ever appears — the feature looks
     * unimplemented while in fact it was never asked for.
     * **And these WebViews outlive the Activity**: they are held here, in a
     * ViewModel, across configuration changes, so an Activity context held
     * directly is a whole Activity leaked per tab. Swapping the base is what
     * lets both be true at once.
     *
     * The swap must be in place BEFORE a WebView is constructed — Chromium
     * reads the context once, at construction — which is why [attachHost] is
     * called from `onCreate` rather than from anything composed.
     */
    private val webHostContext = MutableContextWrapper(app)

    // The outgoing WebView of a page dark mode flip while it fades out, and
    // the job waiting on its replacement to finish loading underneath it.
    private var fadingOut: WebView? = null
    private var pageDarkJob: Job? = null
    private var pageDarkTransitioning = false

    private val _desktopMode = MutableStateFlow(false)
    val desktopMode = _desktopMode.asStateFlow()

    private val _adBlockEnabled = MutableStateFlow(true)
    val adBlockEnabled = _adBlockEnabled.asStateFlow()

    // The blocker's own state: which feeds are on and what the user typed in
    // (config), what the last download produced (meta), how many rules are
    // live right now, and whether an update is in flight. All of it is read
    // only by the ad blocker screen, so it lives here rather than in
    // BrowserStore's settings blob -- and it is persisted by BlocklistStore
    // into the same directory as the list it describes.
    private val _blocklistConfig = MutableStateFlow(BlocklistStore.Config())
    val blocklistConfig = _blocklistConfig.asStateFlow()

    private val _blocklistMeta = MutableStateFlow(BlocklistStore.Meta())
    val blocklistMeta = _blocklistMeta.asStateFlow()

    private val _blocklistRuleCount = MutableStateFlow(0)
    val blocklistRuleCount = _blocklistRuleCount.asStateFlow()

    /** Cookie-banner selectors currently injected, generic and site-specific. */
    private val _cosmeticRuleCount = MutableStateFlow(0)
    val cosmeticRuleCount = _cosmeticRuleCount.asStateFlow()

    @Volatile
    private var cosmeticRules = CosmeticFilters.Rules()

    private val cosmeticScripts = java.util.WeakHashMap<WebView, ScriptHandler>()

    /**
     * Why the last file import failed, for the row under the import button --
     * a picker hands back a URI and nothing else, so an unreadable file, one
     * that is too big, or one with no rules in it all look identical until
     * something says which it was. Cleared when the next import starts.
     */
    private val _blocklistImportError = MutableStateFlow<String?>(null)
    val blocklistImportError = _blocklistImportError.asStateFlow()

    // -------------------------------------------------------- site settings
    // What one site gets that the rest of the web does not: the blocker's
    // three switches as exceptions, plus a dark and a zoom override. See
    // [SiteSettings] for the shape and why the two halves are shaped
    // differently.

    /** Every site with a record, keyed by registrable domain. */
    private val _siteSettings = MutableStateFlow<Map<String, SiteSettings>>(emptyMap())
    val siteSettings = _siteSettings.asStateFlow()

    /**
     * What a private tab changed, and what was there before it did.
     *
     * The sheet is offered in the private space — a page that breaks under
     * the blocker breaks there too — but nothing the private space does is
     * written down, which is the whole arrangement. So its writes land in
     * [_siteSettings] like any other (that is what makes them take effect),
     * the key is noted here with whatever it displaced, [persistNow] leaves
     * those keys out, and closing the space puts them back.
     */
    private val privateSiteSettings = HashMap<String, SiteSettings?>()

    /**
     * What each tab's page is filtered by, for the resource thread to read.
     *
     * `shouldInterceptRequest` runs on a background thread for every
     * subresource, and resolving a site's record there would mean parsing the
     * tab's URL and walking a map per request. This is written once per
     * navigation instead, from the main thread, and read as one lookup.
     */
    private val tabFilters = java.util.concurrent.ConcurrentHashMap<Long, AdBlocker.Rules>()

    /** Non-null while an update runs: the feed being fetched, and how far in. */
    data class BlocklistProgress(val label: String, val index: Int, val total: Int)

    private val _blocklistUpdating = MutableStateFlow<BlocklistProgress?>(null)
    val blocklistUpdating = _blocklistUpdating.asStateFlow()

    private val _searchEngine = MutableStateFlow(SearchEngine.DuckDuckGo)
    val searchEngine = _searchEngine.asStateFlow()

    private val _disabledSearchEngines = MutableStateFlow<Set<String>>(emptySet())
    val disabledSearchEngines = _disabledSearchEngines.asStateFlow()

    private val _customSearchEngines = MutableStateFlow<List<SearchEngine>>(emptyList())
    val customSearchEngines = _customSearchEngines.asStateFlow()

    private val _searchEngineOrder = MutableStateFlow<List<String>>(emptyList())

    /**
     * Every engine there is, the user's own after the built-ins — what
     * Settings lists. The + sheet's picker takes [enabledSearchEngines]
     * instead; the difference between the two lists is exactly what the
     * switches in Settings are for.
     */
    val allSearchEngines: StateFlow<List<SearchEngine>> =
        combine(_customSearchEngines, _searchEngineOrder) { custom, order ->
            orderEngines(SearchEngine.BUILT_IN + custom, order)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.BUILT_IN)

    val enabledSearchEngines: StateFlow<List<SearchEngine>> =
        combine(allSearchEngines, _disabledSearchEngines) { all, off ->
            // Never empty: a picker with nothing in it is a search box that
            // cannot search, so the arrangement that would empty it is
            // refused (setSearchEngineArrangement) and this is the second
            // guard behind that, for a hand-edited save.
            all.filter { it.id !in off }.ifEmpty { listOf(SearchEngine.DuckDuckGo) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.BUILT_IN)

    /**
     * Applies the saved order, keeping anything it doesn't name where it
     * already was rather than sweeping it to the end: an engine added by a
     * later build should arrive among the built-ins it belongs with, not
     * below the user's own list.
     */
    private fun orderEngines(engines: List<SearchEngine>, order: List<String>): List<SearchEngine> {
        if (order.isEmpty()) return engines
        val ranked = engines.filter { it.id in order }.sortedBy { order.indexOf(it.id) }
        val rest = engines.filter { it.id !in order }
        if (rest.isEmpty()) return ranked
        val result = ranked.toMutableList()
        rest.forEach { missing ->
            // Back into its own neighbourhood: after whichever engine came
            // before it in the unordered list.
            val previous = engines.getOrNull(engines.indexOf(missing) - 1)
            val at = result.indexOfFirst { it.id == previous?.id }
            if (at >= 0) result.add(at + 1, missing) else result.add(0, missing)
        }
        return result
    }

    private val _searchSuggestionsEnabled = MutableStateFlow(true)
    val searchSuggestionsEnabled = _searchSuggestionsEnabled.asStateFlow()

    // Seeded to match [BrowserStore.Settings.accentTheme]'s default — the
    // system's colour — so the frames before the store is read are not a
    // different theme from the ones after it.
    private val _accentTheme = MutableStateFlow(AccentTheme.Dynamic)
    val accentTheme = _accentTheme.asStateFlow()

    private val _specialTheme = MutableStateFlow(SpecialTheme.Default)
    val specialTheme = _specialTheme.asStateFlow()

    private val _autoFocusNewTabKeyboard = MutableStateFlow(true)
    val autoFocusNewTabKeyboard = _autoFocusNewTabKeyboard.asStateFlow()

    /**
     * Bumped by a launcher shortcut asking for a new tab (see
     * MainActivity.handleIntent). A COUNTER rather than an event stream
     * because the two ends don't overlap: a cold start raises the request in
     * `onCreate`, before BrowserScreen exists to be told, so a SharedFlow's
     * emission would be thrown away and the shortcut would open the browser
     * on the last page instead of on the sheet. A counter is state, so the
     * first composition reads it whenever it happens; a second shortcut
     * while the app is already up changes it again and re-fires the effect.
     */
    private val _newTabSheetRequests = MutableStateFlow(0)
    val newTabSheetRequests = _newTabSheetRequests.asStateFlow()

    // Read once, at the first composition of BrowserScreen -- whether a cold
    // start lands on the new-tab sheet over the restored page instead of on
    // the page itself.
    private val _openNewTabSheetOnLaunch = MutableStateFlow(false)
    val openNewTabSheetOnLaunch = _openNewTabSheetOnLaunch.asStateFlow()

    private val _newTabHistorySort = MutableStateFlow(HistorySort.MostRecent)
    val newTabHistorySort = _newTabHistorySort.asStateFlow()

    private val _tabManagerMode = MutableStateFlow(TabManagerMode.Vertical)
    val tabManagerMode = _tabManagerMode.asStateFlow()

    private val _linkStripperEnabled = MutableStateFlow(true)
    val linkStripperEnabled = _linkStripperEnabled.asStateFlow()

    /**
     * Where a link tapped in ANOTHER app lands: on the overlay
     * ([com.yuku.browser.CustomTabActivity], drawn over that app, closing
     * back into it) or as an ordinary new tab in the browser proper.
     *
     * Read by the overlay Activity itself, before it puts anything on screen
     * — its own session restores settings synchronously, so the answer is
     * there in `onCreate`.
     */
    private val _openExternalLinksInOverlay = MutableStateFlow(true)
    val openExternalLinksInOverlay = _openExternalLinksInOverlay.asStateFlow()

    /**
     * Whether a link that another installed app claims — a `steam://` URL, an
     * `intent://` one, an https address an app has registered — is handed to
     * that app or opened here. See [ExternalLinks], which also holds the one
     * part of this the setting does not decide: schemes the browser cannot
     * render at all always leave.
     */
    private val _openLinksInApps = MutableStateFlow(true)
    val openLinksInApps = _openLinksInApps.asStateFlow()

    private val _pullToRefreshEnabled = MutableStateFlow(true)
    val pullToRefreshEnabled = _pullToRefreshEnabled.asStateFlow()

    /** Experimental glass edges on the page (`ui/PageLens.kt`). */
    private val _pageLens = MutableStateFlow(false)
    val pageLens = _pageLens.asStateFlow()

    /** Frosted-glass sheets under the Default and Nothing looks. */
    private val _translucentSheets = MutableStateFlow(false)
    val translucentSheets = _translucentSheets.asStateFlow()

    /** How solid the frosted sheets are, 0..1 — see `LocalFrostOpacity`. */
    private val _translucency = MutableStateFlow(DEFAULT_TRANSLUCENCY)
    val translucency = _translucency.asStateFlow()

    /**
     * What a long press on a link raises: the preview card
     * (`ui/LinkPreviewOverlay.kt`) or the floating Open / Open in new tab /
     * Copy link menu (`ui/WebContextMenu.kt`, which a press on an image keeps
     * either way — there is nothing to preview there).
     *
     * Both are built from the same press in [openContextMenu]; this only
     * decides which of the two states it writes.
     */
    private val _linkPreviewEnabled = MutableStateFlow(true)
    val linkPreviewEnabled = _linkPreviewEnabled.asStateFlow()

    /**
     * Where "Open in new tab" puts the tab. See [NewTabPlacement] — read by
     * the UI, which is where both the tab and the surface that asked for it
     * are, not by [newTab] itself.
     */
    private val _newTabPlacement = MutableStateFlow(NewTabPlacement.Foreground)
    val newTabPlacement = _newTabPlacement.asStateFlow()

    private val _doubleTapTabsSwitchesTab = MutableStateFlow(false)
    val doubleTapTabsSwitchesTab = _doubleTapTabsSwitchesTab.asStateFlow()

    /**
     * Whether a sideways swipe off the toolbar's end buttons walks the tab
     * row one step. Off, that drag is simply rejected and the buttons keep
     * only their tap (and, on the tabs button, its drag up into the switcher).
     */
    private val _swipeToSwitchTabs = MutableStateFlow(true)
    val swipeToSwitchTabs = _swipeToSwitchTabs.asStateFlow()

    /**
     * Whether dragging the page far enough up off the tabs button CLOSES the
     * tab. Off, the page still shrinks and follows the finger exactly as it
     * does now — only the release changes: it settles into its switcher slot
     * however far up it went, instead of being thrown off the top.
     */
    private val _flickToCloseTab = MutableStateFlow(true)
    val flickToCloseTab = _flickToCloseTab.asStateFlow()

    /**
     * Whether the browser is currently in its private space.
     *
     * Private is a MODE, not a per-tab flag any more: [tabs] shows one space
     * at a time, so a private tab and an ordinary one are never on screen
     * together, and the chrome recolors around whichever space is open (see
     * BrowserTheme). Deliberately never persisted — a relaunch always comes
     * back in the ordinary space, since private tabs themselves never reach
     * disk.
     */
    private val _privateMode = MutableStateFlow(false)
    val privateMode = _privateMode.asStateFlow()

    /**
     * What leaving private mode does to the tabs opened in it: keep them
     * parked (in memory only — they still die with the process) or close them
     * on the way out. Off by default, which is the reading most people expect
     * of "private": leaving ends the session.
     */
    private val _keepPrivateTabs = MutableStateFlow(false)
    val keepPrivateTabs = _keepPrivateTabs.asStateFlow()

    // Declared up here rather than with the other stores below because the
    // flows just under it are backed by it directly.
    private val passwordStore = PasswordStore(app)

    /**
     * Whether a login the user submits is offered for saving at all. Off does
     * not delete anything already saved — it stops the prompt, nothing more,
     * and filling from what is stored keeps working.
     */
    private val _savePasswordsEnabled = MutableStateFlow(true)
    val savePasswordsEnabled = _savePasswordsEnabled.asStateFlow()

    /**
     * Who keeps the user's logins: the password manager they have chosen on
     * this device, or this browser.
     *
     * Deliberately not a choice between this browser and *Google*. What the
     * flag actually reaches is the platform autofill service, whichever app
     * is filling that role — Google Password Manager, Bitwarden, 1Password,
     * anything registered — so naming one of them in here would be describing
     * the mechanism wrongly. Settings reads the chosen one's name off the
     * device (`ui/AutofillProviders`) and shows it.
     *
     * **On, the browser is out of it entirely.** The page's fields are handed
     * to that service (see [applyAutofillImportance]), which both saves and
     * fills them — per site, from the keyboard's own suggestion strip — and
     * everything here stands down: nothing captured, nothing prompted,
     * nothing offered, and the vault hidden from Settings. Two things
     * offering to save the same login is not a feature, and the manager's
     * copy is the one filed under the site properly and synced to the user's
     * other devices.
     *
     * **Off, this browser is the password manager** and says so to the
     * system: the fields are withheld from autofill, so the keyboard does not
     * compete with a vault it cannot see into. What is already in the vault
     * is kept either way — handing over hides the list, it does not empty it.
     */
    private val _externalPasswordManager = MutableStateFlow(true)
    val externalPasswordManager = _externalPasswordManager.asStateFlow()

    /** True while this browser, rather than the system, is handling logins. */
    private val ownPasswordsActive: Boolean get() = !_externalPasswordManager.value

    /**
     * Whether the vault's addresses and cards are offered into a checkout.
     * Separate switches on purpose — see [BrowserStore.Settings] — and both
     * dead while the platform's own service has the job, exactly like the
     * password half.
     */
    private val _fillAddresses = MutableStateFlow(true)
    val fillAddresses = _fillAddresses.asStateFlow()

    private val _fillPaymentMethods = MutableStateFlow(true)
    val fillPaymentMethods = _fillPaymentMethods.asStateFlow()

    /** Everything in the vault, for the Saved passwords screen. */
    val savedPasswords: StateFlow<List<SavedPassword>> = passwordStore.entries

    /** The other two lists in the same vault. See [SavedAddress], [SavedCard]. */
    val savedAddresses: StateFlow<List<SavedAddress>> = passwordStore.addresses
    val savedCards: StateFlow<List<SavedCard>> = passwordStore.cards

    /**
     * The offer to fill a checkout, which is the address/card counterpart of
     * [PasswordSuggestion] and is drawn in the same place by the same rules:
     * one bar above the toolbar, never at the same time as the password one.
     */
    data class AutofillSuggestion(
        val tabId: Long,
        val group: FormFields.Group,
        val addresses: List<SavedAddress>,
        val cards: List<SavedCard>,
    )

    private val _autofillSuggestion = MutableStateFlow<AutofillSuggestion?>(null)
    val autofillSuggestion = _autofillSuggestion.asStateFlow()

    /** Sites the user answered "Never" for, so the screen can undo that. */
    val neverSavedSites: StateFlow<Set<String>> = passwordStore.neverSaved

    /**
     * A captured login waiting for the user to say whether to keep it.
     * [update] is the difference between "Save password?" and "Update the
     * saved password?", which is the only thing the user needs told apart.
     */
    data class PasswordPrompt(
        val tabId: Long,
        val host: String,
        val username: String,
        val password: String,
        val update: Boolean,
    )

    private val _passwordPrompt = MutableStateFlow<PasswordPrompt?>(null)
    val passwordPrompt = _passwordPrompt.asStateFlow()

    /** What to offer while the cursor is in a login form. */
    data class PasswordSuggestion(
        val tabId: Long,
        val host: String,
        val matches: List<SavedPassword>,
    )

    private val _passwordSuggestion = MutableStateFlow<PasswordSuggestion?>(null)
    val passwordSuggestion = _passwordSuggestion.asStateFlow()

    /** See the find-in-page section below for what this holds and why. */
    private val _findState = MutableStateFlow<FindState?>(null)
    val findState = _findState.asStateFlow()

    // ------------------------------------------------- web platform requests
    // What a page can ask the browser for that needs an answer from outside
    // the page. Each is held here as state and drawn by ui/WebPlatform.kt;
    // see the clients in [create] for what raises them. Every one carries a
    // tab id, because the page that asked can stop being the page on screen
    // while the question is up.

    /**
     * A file the page is handing over, waiting on the user's yes (see
     * WebPlatform). Held here rather than in the card's own state so a
     * rotation doesn't drop the question.
     */
    private val _downloadRequest = MutableStateFlow<FileDownloads.Request?>(null)
    val downloadRequest = _downloadRequest.asStateFlow()

    /** `<input type="file">`: the picker to open, and where the answer goes. */
    data class FileChooserRequest(
        val tabId: Long,
        val intent: Intent,
        val callback: ValueCallback<Array<Uri>>,
    )

    private val _fileChooser = MutableStateFlow<FileChooserRequest?>(null)
    val fileChooser = _fileChooser.asStateFlow()

    /** Chromium's own view for a fullscreened element, and the way to hand it back. */
    data class Fullscreen(
        val tabId: Long,
        val view: View,
        val callback: WebChromeClient.CustomViewCallback,
    )

    private val _fullscreen = MutableStateFlow<Fullscreen?>(null)
    val fullscreen = _fullscreen.asStateFlow()

    enum class JsDialogKind { Alert, Confirm, Prompt, BeforeUnload }

    /**
     * A script blocked on an answer. [result] is a live handle into the
     * renderer: until it is confirmed or cancelled that page's script thread
     * is stopped, so every path out of here must answer it exactly once.
     */
    data class JsDialog(
        val tabId: Long,
        val kind: JsDialogKind,
        val url: String,
        val message: String,
        val defaultText: String,
        val result: JsResult,
    )

    private val _jsDialog = MutableStateFlow<JsDialog?>(null)
    val jsDialog = _jsDialog.asStateFlow()

    /** A certificate the device won't vouch for, and the load waiting on it. */
    data class SslPrompt(
        val tabId: Long,
        val host: String,
        val error: SslError,
        val handler: SslErrorHandler,
    )

    private val _sslPrompt = MutableStateFlow<SslPrompt?>(null)
    val sslPrompt = _sslPrompt.asStateFlow()

    /**
     * What the user already answered about a host's certificate, for the life
     * of this process only. See [WebViewClient.onReceivedSslError] in
     * [create] for why remembering it is what makes this a question rather
     * than a loop.
     */
    private val sslDecisions = HashMap<String, Boolean>()

    /**
     * A `401` with a `WWW-Authenticate` header — the challenge in front of a
     * router's admin page, a staging site, an old intranet box. Unhandled,
     * the default is [HttpAuthHandler.cancel]: the site is simply unreachable,
     * with nothing said and nowhere to type. Like [JsResult] and
     * [SslErrorHandler], the handler is a live one and must be answered
     * exactly once — a dropped one leaves the load hanging forever.
     *
     * [key] is what [httpAuthCredentials] files the answer under, carried on
     * the prompt so the answer doesn't have to recompute it from a realm the
     * server could have changed underneath.
     */
    data class HttpAuthPrompt(
        val tabId: Long,
        val host: String,
        val realm: String,
        val key: String,
        val handler: HttpAuthHandler,
    )

    private val _httpAuth = MutableStateFlow<HttpAuthPrompt?>(null)
    val httpAuth = _httpAuth.asStateFlow()

    /**
     * Credentials the user has already given, for the life of this process
     * only — the same rule [sslDecisions] follows, and for the same reason:
     * one protected page pulls twenty protected subresources, and asking
     * twenty times is asking nobody. Never written to disk. WebView keeps a
     * database of its own for exactly this and it is deliberately not used —
     * it is plaintext, it outlives the process, and this app already has a
     * place where a password is allowed to be kept (see [PasswordStore]).
     *
     * Keyed by SPACE as well as host and realm, so a private tab's login is
     * never handed to an ordinary one; the private half goes when the private
     * tabs do (see [closePrivateTabs]).
     */
    private val httpAuthCredentials = HashMap<String, Pair<String, String>>()

    /**
     * Reloading, or walking back to, a page that was the RESULT of a POST.
     * The default is [Message] `dontResend`, silently — which is why pulling
     * to refresh a set of search results or a submitted order does nothing at
     * all and says nothing about why.
     *
     * Both messages are one-shot: sending one hands the browser its answer
     * and recycles it, so exactly one of the two is ever sent and never twice.
     */
    data class FormResubmission(
        val tabId: Long,
        val url: String,
        val dontResend: Message,
        val resend: Message,
    )

    private val _formResubmission = MutableStateFlow<FormResubmission?>(null)
    val formResubmission = _formResubmission.asStateFlow()

    /**
     * A renderer that CRASHED under the tab on screen — see
     * [handleRenderProcessGone]. Raised only for the CURRENT tab: a
     * background tab is rebuilt silently the next time it is looked at, and
     * a message about a page nobody is reading explains nothing.
     *
     * Only a crash. The other way a renderer goes is the system killing it to
     * reclaim memory, and that is not the user's business: it is not their
     * page misbehaving, there is nothing for them to do about it, and the
     * page is already coming back. Both are rebuilt identically; only the
     * crash is spoken about.
     */
    data class PageCrash(val tabId: Long)

    private val _pageCrash = MutableStateFlow<PageCrash?>(null)
    val pageCrash = _pageCrash.asStateFlow()

    // ------------------------------------------------------ site permissions
    // What a page may reach on the device: the camera, the microphone, and
    // where the user is. Two doors in — [PermissionRequest] for the media
    // captures, onGeolocationPermissionsShowPrompt for location — and one
    // question out, held here and drawn by ui/WebPlatform.kt beside the other
    // things a page can ask for that only the user can answer.
    //
    // Every request is answered TWICE over, and keeping the two apart is the
    // whole shape of this: the user's answer is about the SITE, and the app's
    // own access to the device is the system's dialog, asked only once the
    // site has been allowed. Asking the system first would put a camera
    // dialog in front of a user who had not yet decided to give the site a
    // camera at all.

    /** A site's standing answers, from disk. Private tabs never write here. */
    private val _sitePermissions = MutableStateFlow<List<SitePermissionGrant>>(emptyList())
    val sitePermissions = _sitePermissions.asStateFlow()

    /** What an unanswered site gets. See [SitePermission] and [PermissionRule]. */
    private val _permissionRules = MutableStateFlow<Map<SitePermission, PermissionRule>>(emptyMap())
    val permissionRules = _permissionRules.asStateFlow()

    /**
     * "Allow this time", which is the answer this whole feature is for: it is
     * neither a decision the user has to keep making nor one they are stuck
     * with. Held for the life of the process and never persisted — the same
     * rule a private tab's everything follows, applied to every tab.
     */
    private val sessionGrants = HashMap<String, Boolean>()

    /** The question on screen, and how the page hears the answer. */
    data class PermissionAsk(
        val tabId: Long,
        val origin: String,
        // Plural because getUserMedia asks for the camera and the microphone
        // together, and a video call is one decision, not two.
        val permissions: List<SitePermission>,
        val private: Boolean,
    )

    private val _permissionAsk = MutableStateFlow<PermissionAsk?>(null)
    val permissionAsk = _permissionAsk.asStateFlow()

    /** How to answer the request the card is standing in front of. */
    private var pendingPermission: ((List<SitePermission>) -> Unit)? = null

    enum class PermissionAnswer { Allow, AllowOnce, Block }

    /**
     * The app's own missing permissions, for the UI to put the system's
     * dialog in front of. [onOsPermissionResult] is the way back.
     */
    private val _permissionOsRequest = MutableStateFlow<List<String>?>(null)
    val permissionOsRequest = _permissionOsRequest.asStateFlow()

    /** What to answer the page with once the system has answered us. */
    private var pendingOsResume: (() -> Unit)? = null

    /**
     * Each WebView's notifications script, and the answers compiled into it
     * (so an unchanged set is not re-registered). See [WebNotifications].
     */
    private val notificationScripts = java.util.WeakHashMap<WebView, ScriptHandler>()
    private val notificationScriptState = java.util.WeakHashMap<WebView, String>()

    /** A posted notification's way back to the page that raised it. */
    private data class NotificationRoute(
        val tabId: Long,
        val origin: String,
        val jsId: Int,
        val proxy: JavaScriptReplyProxy,
    )

    /** By Android tag, oldest first. Capped at [MAX_NOTIFICATION_ROUTES]. */
    private val notificationRoutes = LinkedHashMap<String, NotificationRoute>()
    private var notificationSeq = 0L

    /** Page text scale, as a percentage. See [setZoomStep]. */
    private val _pageZoom = MutableStateFlow(DEFAULT_ZOOM)
    val pageZoom = _pageZoom.asStateFlow()

    // Per-tab, per-page-load password state. Neither outlives a navigation:
    // a captured credential that was never submitted, and whether this
    // document has a login form.
    private val pendingCaptures = HashMap<Long, PasswordPrompt>()
    private val captureTimers = HashMap<Long, Job>()
    private val loginFormPresent = HashMap<Long, Boolean>()

    // The tab each space was last on, so switching back lands where it was
    // left rather than on whichever tab happens to be newest. The ordinary
    // space's copy is also what gets persisted: _currentTabId can be a
    // private tab, and none of those are in the saved tab list.
    private var normalCurrentTabId = 0L
    private var privateCurrentTabId = 0L

    // The tab that was current before the one that is now — what a
    // double-tap on the tabs button flips back to. In memory only, and
    // deliberately just one step deep: this is "the other tab", not a
    // history stack. 0L means there isn't one (fresh start, or the tab it
    // pointed at has since been closed).
    private val _previousTabId = MutableStateFlow(0L)
    val previousTabId = _previousTabId.asStateFlow()

    // In-memory only, like the other preferences above — no persistence
    // across process death yet. Keyed by URL, not tab id: "this page is
    // bookmarked" has to survive the tab that added it being closed, and
    // match any other tab open on the same URL too.
    private val _bookmarks = MutableStateFlow<List<BookmarkEntry>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()

    // What a long press on a page landed on, if it landed on a link or an
    // image — null whenever no context menu is up. Set from the WebView's own
    // long-click listener (see [create]); the menu itself, and every action
    // on it, lives in the UI layer.
    private val _contextTarget = MutableStateFlow<WebContextTarget?>(null)
    val contextTarget = _contextTarget.asStateFlow()

    /**
     * The link being previewed, if one is — see [LinkPreview] and
     * `ui/LinkPreviewOverlay.kt`. Its page is a WebView of its own: while a
     * preview is up there are two live WebViews on screen, the tab's
     * underneath and the preview's over it.
     */
    private val _linkPreview = MutableStateFlow<LinkPreview?>(null)
    val linkPreview = _linkPreview.asStateFlow()

    /**
     * The previews' own WebViews, keyed by [LinkPreview.token]. Built per
     * preview and destroyed with it rather than kept warm: a preview is a
     * page being looked THROUGH, and one held between previews would keep a
     * session, a scroll offset and a back stack belonging to a link that has
     * already been dismissed. It is not
     * in [views] either — it has no tab, so nothing that walks the tab list
     * (eviction, thumbnails, parking, page-dark rebuilds) has any business
     * touching it.
     */
    private val previewViews = HashMap<Long, WebView>()

    /** Hands each preview its own [LinkPreview.token]. */
    private var previewSeq = 0L

    // Read out of MediaStore on demand rather than kept in step with it —
    // see DownloadsStore for why the files themselves are the only list worth
    // trusting. Refreshed whenever the Downloads screen opens and after
    // anything this app saves.
    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads = _downloads.asStateFlow()

    /**
     * The downloads still in flight, summed — null when there are none. What
     * the toolbar's menu button and the menu's Downloads row animate on. See
     * [trackDownload].
     */
    private val _downloadActivity = MutableStateFlow<DownloadActivity?>(null)
    val downloadActivity = _downloadActivity.asStateFlow()

    /** The same downloads one by one, for the Downloads screen's rows. */
    private val _activeDownloads = MutableStateFlow<List<DownloadProgress.Row>>(emptyList())
    val activeDownloads = _activeDownloads.asStateFlow()

    /** DownloadManager ids being followed. Main thread only. */
    private val trackedDownloads = mutableSetOf<Long>()
    private var downloadPoll: Job? = null

    /**
     * False until the saved session is fully back in place — the JSON blob is
     * read synchronously, but the thumbnails and favicons that go with it are
     * separate files read off the IO dispatcher, and a switcher full of blank
     * cards is exactly what the splash exists to hide. Read by MainActivity,
     * which holds the system splash screen up until it flips; nothing else
     * waits on it, since the browser itself is usable the moment the tab list
     * exists.
     */
    private val _startupComplete = MutableStateFlow(false)
    val startupComplete = _startupComplete.asStateFlow()

    /**
     * The tabs whose last preview is standing in for a live page that has
     * nothing on it yet.
     *
     * A WebView built from scratch has no rendered page to show: it is
     * created, told to load, and paints white until the network and the
     * renderer are done — seconds of blank where a page used to be. That is
     * the "stutter" of relaunching, and it is not only relaunching: any tab
     * whose view was evicted ([trimViews]) or parked since the last launch
     * gets a view built the moment it is asked for, which is the moment the
     * user is opening it. The UI holds that tab's saved thumbnail over the
     * live view for exactly as long as this names it, so a tab arrives
     * looking like the card it grew out of instead of flashing white first.
     *
     * A SET rather than one id because more than one tab can be waiting: the
     * one being opened, and any the host builds behind it. Each is cleared on
     * its own when its load FINISHES and the frame that finished it is up
     * (see [liftCoverWhenPainted]), by a main-frame failure (there is a real
     * error state to show instead), or by the UI's own timeout — a cover that
     * outlives its page is a frozen app.
     *
     * Not at first paint, which is what this used to lift on and what made
     * the cover look broken: `onPageCommitVisible` is the first pixels of the
     * incoming document, and for a page being fetched again those pixels are
     * a bare shell — a header, a background, and the content still on its
     * way. Lifting there swapped a complete picture of the page for an empty
     * one, which is the flash the cover exists to prevent, just later and
     * with a fade on it. The finished load is the first moment the live page
     * can stand in for the picture of it.
     */
    private val _coveredTabIds = MutableStateFlow<Set<Long>>(emptySet())
    val coveredTabIds = _coveredTabIds.asStateFlow()

    /** Set by the UI when it gives up waiting, and by first paint. */
    fun dismissPageCover(id: Long) {
        _coveredTabIds.update { it - id }
    }

    /**
     * Holds this tab's last preview over its brand-new WebView until that
     * view has painted. Only where there is a preview to hold: a tab with no
     * picture of its own has nothing to cover with, and claiming a cover for
     * it would only keep the page from being captured (see [pageExposed])
     * while the UI worked that out.
     */
    private fun coverUntilPainted(tab: Tab) {
        if (tab.thumbnail != null) _coveredTabIds.update { it + tab.id }
    }

    /**
     * The end of a restore: the page goes back to the offset it was parked at
     * ([pendingScroll]) and only then comes out from under its own picture
     * ([coveredTabIds]) — in that order, since the whole point of the cover is
     * that what it uncovers is what it was showing.
     *
     * The cover waits for the frame the load produced to actually be up,
     * which is a question only the renderer can answer ([awaitPagePainted]);
     * lifting on the callback alone uncovers the frame BEFORE it. The scroll
     * is applied twice for the same reason in reverse — once now, and once
     * on that frame, because a document that is still growing clamps a scroll
     * past its current end without complaint.
     */
    private fun settleRestoredPage(id: Long, web: WebView) {
        val target = pendingScroll.remove(id)
        applyRestoredScroll(id, web, target)
        if (target == null && id !in _coveredTabIds.value) return
        viewModelScope.launch {
            // The frame the load produced…
            awaitPagePainted(id, COVER_PAINT_MAX_MS)
            applyRestoredScroll(id, web, target)
            // …then a beat for whatever the page does to itself immediately
            // after loading, and the frame after THAT (see COVER_SETTLE_MS).
            // The offset is re-applied on the far side because a document
            // that re-lays itself out takes the scroll back to the top.
            delay(COVER_SETTLE_MS)
            applyRestoredScroll(id, web, target)
            awaitPagePainted(id, COVER_PAINT_MAX_MS)
            dismissPageCover(id)
        }
    }

    /**
     * Puts a restored page back at the offset it was parked at. Only ever
     * forward: an attempt lands short exactly when the document had not grown
     * that far yet, and a page already at the offset needs nothing. Guarded on
     * the view still being this tab's, since a second one is a different page.
     */
    private fun applyRestoredScroll(id: Long, web: WebView, target: Int?) {
        if (target == null || views[id] !== web) return
        if (web.scrollY < target) web.scrollTo(0, target)
    }

    // ------------------------------------------------------ navigation hold

    /**
     * Which navigation a hold is over, since that decides what "ready" means.
     * A [Link] (or a typed address) is a different page, and holding the old
     * one past the moment the new one has a document is holding the user up.
     * A [Reload] or a [History] step is the same page coming back, which
     * Chromium only returns to its old offset once it has laid out that far —
     * so it is held to the finished load, or the uncovering shows the jump.
     */
    private enum class HoldKind { Link, Reload, History }

    /** A picture of the screen taken when a navigation was asked for, waiting for it to commit. */
    private class NavHold(
        val web: WebView,
        val frame: Bitmap,
        /** 1 for a copy of the window; [FACTOR] for the tab's thumbnail standing in for one. */
        val scale: Float,
        val kind: HoldKind,
        val takenAt: Long,
        /** Where in the view [frame]'s first row goes; see [HoldCoverView]. */
        val offsetY: Float = 0f,
    )

    /** A [NavHold] that is up over its page. */
    private class ActiveHold(val web: WebView, val cover: View, val kind: HoldKind) {
        var job: Job? = null
        var leaving = false
    }

    /**
     * The navigation hold: the page as it was, held over the document that is
     * replacing it until that document has something to show, then
     * cross-faded away.
     *
     * [coveredTabIds] is for a WebView built from nothing. This is for one
     * already on screen that is told to go somewhere — a reload, a link,
     * back/forward, an address typed into the sheet. Chromium's own answer to
     * those is to swap documents the moment the new one produces a frame, and
     * that frame is a shell: a background and a header, then images, fonts and
     * the scroll offset landing in visible steps.
     *
     * The picture is taken when the navigation is ASKED FOR ([prepareHold]) —
     * by commit the old document is gone — and goes up at commit
     * ([armNavHold]), where it is the same pixels as the frame it replaces.
     * It is a native view laid over the WebView INSIDE the WebView's own
     * container rather than a composable over the host, so it is exactly the
     * view's size, rides every transform the host applies, and sits under the
     * pull-to-refresh spinner instead of freezing it.
     *
     * Any touch lifts it at once — the page under it is live, and a finger on
     * a picture of another page is aiming at the wrong thing — and every hold
     * is capped, because a picture that outlives its page is a frozen browser.
     */
    private val pendingHolds = HashMap<Long, NavHold>()
    private val activeHolds = HashMap<Long, ActiveHold>()

    /**
     * Takes the picture a navigation of [id] will be held on, then runs [go] —
     * the navigation itself, deferred by the frame or two a copy takes to land,
     * so the copy is of the page BEFORE anything moved.
     *
     * Where the page is not exposed (the menu or the new-tab sheet is over it,
     * which is where reload and a typed address come from — and a closing
     * sheet is still drawn after it stops counting) a copy would be a picture
     * of the sheet, so the tab's thumbnail stands in when it is still an exact
     * copy of the page: the menu button takes one on pointer-down.
     */
    private fun prepareHold(id: Long, web: WebView, kind: HoldKind, go: () -> Unit = {}) {
        pendingHolds.remove(id)
        // Something already stands in for the page, and a copy now would be a
        // copy of that.
        if (id in activeHolds || id in _coveredTabIds.value || pageDarkTransitioning) {
            go()
            return
        }
        if (requestHoldFrame(id, web, kind, go)) return
        thumbnailHold(id, web, kind)?.let { pendingHolds[id] = it }
        go()
    }

    /** The exact half of [prepareHold]; false when no copy could be asked for. */
    private fun requestHoldFrame(id: Long, web: WebView, kind: HoldKind, go: () -> Unit): Boolean {
        if (!pageExposed || id != _currentTabId.value) return false
        if (!web.isAttachedToWindow || !web.isShown) return false
        val window = web.hostWindow() ?: return false
        val decor = window.decorView
        val location = IntArray(2)
        web.getLocationInWindow(location)
        // The whole view, the strip under the toolbar included: that strip is
        // page the moment the bar slides away.
        val source = Rect(
            location[0],
            location[1],
            minOf(location[0] + web.width, decor.width),
            minOf(location[1] + web.height, decor.height),
        )
        if (source.left < 0 || source.top < 0 || source.isEmpty) return false
        val frame = try {
            Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return false
        }
        val askedAt = SystemClock.elapsedRealtime()
        return try {
            PixelCopy.request(
                window,
                source,
                frame,
                { result ->
                    // Re-checked at landing, as captureExact does: a sheet or a
                    // cover raised in the meantime would be in the copy.
                    if (result == PixelCopy.SUCCESS && views[id] === web && pageExposed &&
                        id !in activeHolds
                    ) {
                        // The cover is drawn inside the same container the
                        // page lens curves, so the copy — curve and all — is
                        // flattened first, from the view's own top.
                        captureUnwarp?.invoke(web, frame, 1, 0)
                        pendingHolds[id] = NavHold(web, frame, 1f, kind, askedAt)
                    } else {
                        frame.recycle()
                    }
                    go()
                },
                Handler(Looper.getMainLooper()),
            )
            true
        } catch (e: IllegalArgumentException) {
            frame.recycle()
            false
        }
    }

    /**
     * The tab's thumbnail as a hold, while it is still a true picture of the
     * page: an exact copy, taken since the page last moved, at the width the
     * view still has. Half resolution, which a cross-fade of a second forgives
     * far more readily than a cut to a blank shell.
     */
    private fun thumbnailHold(id: Long, web: WebView, kind: HoldKind): NavHold? {
        val thumbnail = _tabs.value.firstOrNull { it.id == id }?.thumbnail ?: return null
        val exactAt = exactCaptureAt[id] ?: return null
        if (exactAt <= (contentChangedAt[id] ?: 0L)) return null
        if (thumbnail.isRecycled || thumbnail.width != web.width / FACTOR) return null
        // A thumbnail is the page's BOX, which starts below the page lens's
        // top overscan, not at the view's own top.
        return NavHold(
            web, thumbnail, FACTOR.toFloat(), kind, SystemClock.elapsedRealtime(),
            offsetY = pageTopOverscanPx.toFloat(),
        )
    }

    /**
     * The page moved under a finger after a hold's picture was taken — an
     * in-page anchor, which never commits a document — so the picture is not
     * the page any more and nothing may be held on it.
     */
    fun notePageScrolled(id: Long) {
        pendingHolds.remove(id)
    }

    /**
     * `onPageStarted`: the navigation has committed, and the picture taken when
     * it was asked for goes up over it. A client-side redirect commits a second
     * document under a hold that is already up, which simply starts waiting
     * again — that redirect's own blank page is exactly what it is there for.
     */
    private fun armNavHold(id: Long, web: WebView) {
        val pending = pendingHolds.remove(id)
            ?.takeIf { it.web === web && SystemClock.elapsedRealtime() - it.takenAt < HOLD_MAX_AGE_MS }
        // A load nobody is looking at has nothing to hide, and a view built
        // from nothing already has its cover.
        if (!pageOnScreen(id) || id in _coveredTabIds.value) {
            dropHold(id)
            return
        }
        val active = activeHolds[id]
        if (active != null && active.web === web && !active.leaving && active.cover.parent != null) {
            liftHoldWhenReady(id, active)
            return
        }
        if (active != null) dropHold(id)
        pending?.let { showHold(id, it) }
    }

    private fun showHold(id: Long, hold: NavHold) {
        val web = hold.web
        val container = web.parent as? ViewGroup ?: return
        val cover = HoldCoverView(container.context, hold.frame, hold.scale, hold.offsetY)
        val active = ActiveHold(web, cover, hold.kind)
        // Not consumed: the touch carries on to the live page underneath,
        // which is where it belongs. Only the picture gets out of the way.
        cover.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                releaseHold(id, active, HOLD_TOUCH_FADE_MS)
            }
            false
        }
        container.addView(
            cover,
            container.indexOfChild(web) + 1,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activeHolds[id] = active
        liftHoldWhenReady(id, active)
    }

    /**
     * Waits for the page under [hold] to be worth showing — see [HoldKind] —
     * then for the frame that shows it and the one after a settle, the same
     * two-step [settleRestoredPage] takes, and fades the picture out.
     */
    private fun liftHoldWhenReady(id: Long, hold: ActiveHold) {
        hold.job?.cancel()
        hold.job = viewModelScope.launch {
            val cap = if (hold.kind == HoldKind.Link) HOLD_LINK_MAX_MS else HOLD_RETURN_MAX_MS
            withTimeoutOrNull(cap) {
                if (hold.kind == HoldKind.Link) {
                    while (!documentReady(id, hold.web)) delay(HOLD_POLL_MS)
                } else {
                    _tabs.first { list -> list.firstOrNull { it.id == id }?.loading != true }
                }
                awaitPagePainted(id, COVER_PAINT_MAX_MS)
                delay(HOLD_SETTLE_MS)
                awaitPagePainted(id, COVER_PAINT_MAX_MS)
            }
            releaseHold(id, hold, HOLD_FADE_MS)
        }
    }

    /**
     * Whether the incoming document is past parsing: its markup is in and its
     * render-blocking styles with it, which is the first moment it is a page
     * rather than a shell. A finished load answers without asking.
     */
    private suspend fun documentReady(id: Long, web: WebView): Boolean {
        if (_tabs.value.firstOrNull { it.id == id }?.loading != true) return true
        val state = suspendCancellableCoroutine<String?> { continuation ->
            web.evaluateJavascript("document.readyState") {
                if (continuation.isActive) continuation.resume(it)
            }
        }
        return state != null && state.trim('"') != "loading"
    }

    private fun releaseHold(id: Long, hold: ActiveHold, durationMs: Long) {
        if (hold.leaving) return
        hold.leaving = true
        hold.job?.cancel()
        hold.cover.animate()
            .alpha(0f)
            .setDuration(durationMs)
            // FastOutSlowIn, as the page-dark cross-fade: two renderings of
            // the tab trading places.
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .withEndAction { removeHold(id, hold) }
            .start()
    }

    private fun removeHold(id: Long, hold: ActiveHold) {
        hold.job?.cancel()
        hold.cover.animate().cancel()
        (hold.cover.parent as? ViewGroup)?.removeView(hold.cover)
        if (activeHolds[id] === hold) {
            activeHolds.remove(id)
            // Captures were refused while the picture was up (it is of the
            // page that left); the page that arrived is owed one.
            queueCapture(id)
        }
    }

    /** Takes [id]'s hold down on the spot — its view is going, or is being replaced. */
    private fun dropHold(id: Long) {
        activeHolds[id]?.let { removeHold(id, it) }
    }

    /**
     * A main-frame failure: nothing is coming to replace a cover, and the error
     * screen is what belongs on screen instead. Lifted once that screen has
     * faded up over it — lifting first uncovers the engine's own error page for
     * the length of the fade.
     */
    private fun dismissCoversForError(id: Long) {
        viewModelScope.launch {
            delay(ERROR_FADE_IN_MS.toLong())
            dismissPageCover(id)
            dropHold(id)
        }
    }

    private val adBlocker = AdBlocker()

    /** Insertion-ordered so the oldest background tab is the first eviction candidate. */
    private val views = LinkedHashMap<Long, WebView>()

    /**
     * Who is allowed to draw a scrollbar, per WebView — see [ScrollBarGate].
     * Weak-keyed rather than kept alongside [views]: a WebView is dropped from
     * that map by half a dozen paths (eviction, parking, closing, a profile
     * swap) and a gate is worth exactly as much as the view it belongs to.
     */
    private val scrollBarGates = WeakHashMap<WebView, ScrollBarGate>()

    /** The gate for [web], made on first ask. */
    fun scrollBarGate(web: WebView): ScrollBarGate =
        scrollBarGates.getOrPut(web) { ScrollBarGate(web) }
    private var nextId = 1L

    // Whichever tab the switcher currently has centered (see
    // BrowserScreen's pin-on-centeredTabId effect) -- protected from
    // trimViews the same as the current tab, so scrolling to a background
    // tab doesn't risk it getting evicted (and needing a full cold reload)
    // right as the user starts dragging it open.
    private var pinnedTabId: Long? = null

    fun setPinnedTab(id: Long?) {
        pinnedTabId = id
    }

    private val store = BrowserStore(app)
    private val downloadsStore = DownloadsStore(app)
    private val thumbnailStore = ThumbnailStore(app)
    private val faviconStore = FaviconStore(app)
    private val pageStateStore = PageStateStore(app)
    private val blocklistStore = BlocklistStore(app)

    // Fired after any state mutation that should survive process death;
    // debounced so a page load's rapid-fire progress/title updates collapse
    // into one JSON write instead of dozens.
    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Thumbnails captured since the last disk flush, keyed by tab id. Kept
    // separate from `dirty` because captureAllThumbnails fires for every live
    // tab at once (and again on every re-drag of the tabs button), and each
    // one is a WEBP encode -- debouncing collapses a burst into a single
    // write per tab, and the map means a later capture of the same tab
    // simply replaces the pending one instead of queueing a second encode.
    private val pendingThumbnails = LinkedHashMap<Long, Bitmap>()
    private val thumbnailsDirty = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // When each tab last got a pixel-exact copy of the screen, against when
    // its content last moved under it. exact > changed means the preview on
    // the card IS what the page looks like, and nothing needs recapturing --
    // which is the whole point: the copy can only be taken while the page is
    // on screen and still, so it has to be taken as soon as the page settles
    // rather than at the instant the switcher is asked for.
    private val exactCaptureAt = HashMap<Long, Long>()
    private val contentChangedAt = HashMap<Long, Long>()
    private val idleCapture = MutableSharedFlow<Long>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Set from the UI: whether the live page is currently the unobscured
    // thing on screen. Only then is a copy of the window a copy of the page.
    private var pageExposed = false

    /**
     * Whether the page — as opposed to the switcher, a sheet or a full-screen
     * destination — is what the user is looking at. The SHOWING half of
     * [pageExposed], without the exposed half: the find bar, a JS dialog and
     * the switcher's covers all sit beside the page rather than instead of
     * it, so they disqualify a screen COPY and not the page itself. Set from
     * the UI; true by default, because a session that never reports (the link
     * overlay has no switcher and no sheets to hide behind) is a session whose
     * page is always the screen.
     */
    private var pageShowing = true

    /**
     * Whether this tab's page is the thing on screen.
     *
     * A load that runs while this is false is a BACKGROUND load, and the case
     * that made this necessary is closing a tab: the screen goes to a
     * neighbour with no live WebView any more (evicted, or parked since the
     * last launch), so the rebuilt view reloads the page — while the user is
     * still in the switcher, looking at that tab's CARD. Nobody asked for
     * that load and it is not on screen, so it does not get to move anything
     * the card shows: the title and favicon stay as the session left them.
     * The preview is already safe (a capture needs the stricter
     * [pageExposed]), and the toolbar's loading line is drawn from the same
     * question one level up (see BottomToolbar's `loading`) rather than by
     * lying about `loading` here, which several other things depend on. What
     * arrives at the end of it is the page the card was describing all
     * along, which is why nothing is lost by keeping quiet about the trip.
     *
     * Read at each callback rather than latched when the load starts, so a
     * tab opened MID-load stops being a background load from that moment —
     * see [setPageShowing], which then asks the view what it is really doing.
     */
    private fun pageOnScreen(id: Long) = pageShowing && id == _currentTabId.value

    init {
        loadBlocklist()
        installServiceWorkerFilter()
        Favicons.attach(faviconStore)
        val restoring = if (ephemeral) restoreSettingsOnly() else restoreSavedState()
        // Resolve the chrome's dark state from the settings just restored,
        // rather than waiting for the UI to report it: the first WebView is
        // created by the first composition, off pageDarkActive, and a page
        // built light and then rebuilt dark a frame later is a cross-fade the
        // user never asked for.
        isDark = chromeDark()
        resolvedPageDark = resolvePageDark()
        // Through the per-tab resolution, not the bare setting: the restored
        // tab may be on a site with a standing answer of its own, and [create]
        // builds the first WebView off this.
        _pageDarkActive.value = pageDarkFor(_currentTabId.value)
        viewModelScope.launch {
            restoring.forEach { it.join() }
            _startupComplete.value = true
        }
        // A download outlives the process that started it — DownloadManager
        // carries on without us — so a relaunch picks up whatever is still
        // going. The overlay's session leaves this to the real one.
        if (!ephemeral) {
            viewModelScope.launch {
                DownloadProgress.inFlight(getApplication()).forEach { trackDownload(it.id) }
            }
        }
        viewModelScope.launch { dirty.debounce(500).collect { persistNow() } }
        viewModelScope.launch(Dispatchers.IO) { thumbnailsDirty.debounce(500).collect { flushThumbnails() } }
        // Main thread (viewModelScope's default) -- this touches Views. The
        // debounce is what makes it "once the page has settled": a fling emits
        // on every frame, and only the stillness after it is worth copying.
        viewModelScope.launch {
            idleCapture.debounce(SETTLE_MS).collect { id ->
                // A settle is 250ms and the platform's scrollbar fade is
                // ~550ms, so the copy taken after a scroll is a copy of a
                // page with a half-faded bar down its right edge — which is
                // how one ends up living in a switcher card. Take it off and
                // let a frame carry that to the surface the copy is read
                // from; this path is a debounce already, so a couple of
                // frames more cost nothing.
                views[id]?.let { scrollBarGates[it]?.retract() }
                delay(SCROLLBAR_RETRACT_MS)
                captureThumbnail(id)
            }
        }
        // Deliberately no seeded tab when there's nothing to restore:
        // BrowserScreen opens straight into the new-tab search sheet (with
        // keyboard focus) whenever tabs is empty, so a true first launch (or
        // one where every tab had been closed) lands there instead of on
        // Tab.HOME.
    }

    /** Returns the still-running halves of the restore, for [startupComplete] to wait on. */
    private fun restoreSavedState(): List<Job> {
        val saved = store.load() ?: return emptyList()
        _tabs.value = saved.tabs.map { t -> Tab(id = t.id, url = t.url, title = t.title, host = t.host) }
        nextId = (saved.tabs.maxOfOrNull { it.id } ?: 0L) + 1
        setCurrentTab(
            if (saved.tabs.any { it.id == saved.currentTabId }) saved.currentTabId
            else saved.tabs.lastOrNull()?.id ?: 0L
        )
        _history.value = saved.history
        _visits.value = saved.visits.associateBy { visitKey(it.url) }
        _bookmarks.value = saved.bookmarks
        _sitePermissions.value = saved.sitePermissions
        _siteSettings.value = saved.siteSettings.associate { it.site to it.settings }
        applySettings(saved.settings)
        // Tallies saved before result pages were kept out of them. After
        // applySettings, which is what brings the user's own engines in.
        _visits.update { map -> map.filterValues { !isSearchResults(it.url) } }
        val ids = saved.tabs.map { it.id }
        // The current tab's parked page is read here, on the main thread,
        // rather than in the job below: its WebView is created by the first
        // composition, which can beat an IO job to it, and a state that
        // arrives after `create` has already called loadUrl is worse than
        // none — the page would be restored twice. It's one small file, and
        // the JSON blob above was read the same way for the same reason.
        _currentTabId.value.let { id -> pageStateStore.read(id)?.let { pendingRestores[id] = it } }
        // Something to cover the blank first seconds with, but only where
        // there is actually a page coming back to cover.
        if (saved.tabs.any { it.id == _currentTabId.value }) {
            _coveredTabIds.value = setOf(_currentTabId.value)
        }
        return listOf(restoreThumbnails(ids), restoreFavicons(), restorePageStates(ids))
    }

    /**
     * The [ephemeral] half of [restoreSavedState]: the user's settings and
     * nothing else. An overlay is still their browser — same accent, same
     * dark mode, same search engine, same blocking — it just doesn't inherit
     * their tabs and doesn't leave a trace of its own.
     */
    private fun restoreSettingsOnly(): List<Job> {
        val saved = store.load() ?: return emptyList()
        applySettings(saved.settings)
        // Bookmarks come along too, read-only: the overlay's menu shows
        // whether the page it is on is already bookmarked, and a star that
        // says "no" on a page the user bookmarked last week is worse than no
        // star at all. Adding one writes through [BrowserStore.updateBookmarks]
        // rather than through this session — see [toggleBookmark].
        _bookmarks.value = saved.bookmarks
        // And so do the permission answers, for the same reason: a site the
        // user allowed the camera in the browser proper should not have to be
        // asked again because the link was tapped in another app. The overlay
        // never writes them back — its "Allow" lasts the session, like
        // everything else it does.
        _sitePermissions.value = saved.sitePermissions
        // And the per-site records, for the same reason again: a site the
        // user turned the blocker off for is broken under it in the overlay
        // too. Read-only here — an overlay never writes this file.
        _siteSettings.value = saved.siteSettings.associate { it.site to it.settings }
        return emptyList()
    }

    private fun applySettings(settings: BrowserStore.Settings) = with(settings) {
        _themeMode.value = themeMode
        _pageDarkMode.value = pageDarkMode
        _desktopMode.value = desktopMode
        _adBlockEnabled.value = adBlockEnabled
        adBlocker.enabled = adBlockEnabled
        _searchEngine.value = searchEngine
        _disabledSearchEngines.value = disabledSearchEngines
        _customSearchEngines.value = customSearchEngines
        _searchEngineOrder.value = searchEngineOrder
        _searchSuggestionsEnabled.value = searchSuggestionsEnabled
        _accentTheme.value = accentTheme
        _specialTheme.value = specialTheme
        _autoFocusNewTabKeyboard.value = autoFocusNewTabKeyboard
        _openNewTabSheetOnLaunch.value = openNewTabSheetOnLaunch
        _newTabHistorySort.value = newTabHistorySort
        _tabManagerMode.value = tabManagerMode
        _linkStripperEnabled.value = linkStripperEnabled
        _openExternalLinksInOverlay.value = openExternalLinksInOverlay
        _openLinksInApps.value = openLinksInApps
        _pullToRefreshEnabled.value = pullToRefreshEnabled
        _linkPreviewEnabled.value = linkPreviewEnabled
        _pageLens.value = pageLens
        _translucentSheets.value = translucentSheets
        _translucency.value = translucency
        _newTabPlacement.value = newTabPlacement
        _doubleTapTabsSwitchesTab.value = doubleTapTabsSwitchesTab
        _swipeToSwitchTabs.value = swipeToSwitchTabs
        _flickToCloseTab.value = flickToCloseTab
        _keepPrivateTabs.value = keepPrivateTabs
        _savePasswordsEnabled.value = savePasswords
        _externalPasswordManager.value = externalPasswordManager
        _fillAddresses.value = fillAddresses
        _fillPaymentMethods.value = fillPaymentMethods
        _permissionRules.value = permissionRules
        refreshNotificationScripts()
        // Snapped, not trusted: a save from before this ladder was shortened
        // holds a percentage that is no longer a rung, and the slider has
        // nowhere to put a thumb that is between two of them.
        _pageZoom.value = ZOOM_STEPS.minByOrNull { kotlin.math.abs(it - pageZoom) } ?: DEFAULT_ZOOM
        // Same snap, same reason: the reader's ladder is the reader's, and a
        // percentage between two of its rungs has nowhere to put a thumb.
        _readerSettings.value = readerSettings.copy(
            textScale = READER_TEXT_STEPS.minByOrNull { kotlin.math.abs(it - readerSettings.textScale) }
                ?: DEFAULT_READER_TEXT_SCALE,
        )
    }

    /**
     * Background tabs' parked navigation states, off the main thread — none of
     * them has a WebView yet, and won't until it's selected or dragged open in
     * the switcher. [pageStatesLoaded] is what keeps a tab that IS opened in
     * that window from silently losing its state: until this finishes,
     * [create] reads the file itself rather than assuming the map is complete.
     */
    private fun restorePageStates(ids: List<Long>): Job =
        viewModelScope.launch(Dispatchers.IO) {
            pageStateStore.prune(ids.toSet())
            val states = ids.filter { it != _currentTabId.value }
                .mapNotNull { id -> pageStateStore.read(id)?.let { id to it } }
            withContext(Dispatchers.Main) {
                // Only fills holes, same rule as the thumbnail and favicon
                // restores: a state parked in memory since launch (a page dark
                // flip, an eviction) is the fresher one.
                states.forEach { (id, state) -> pendingRestores.putIfAbsent(id, state) }
                pageStatesLoaded = true
            }
        }

    /**
     * Puts saved icons back on restored tabs and history rows. Bookmarks carry
     * no bitmap of their own — their rows go through [Favicons], which now
     * reads the same files — so there's nothing to hydrate for them here,
     * only their hosts to keep from being pruned.
     */
    private fun restoreFavicons(): Job =
        viewModelScope.launch(Dispatchers.IO) {
            val hosts = (_tabs.value.map { it.host } +
                _history.value.map { it.host } +
                _bookmarks.value.map { it.host })
                .filter { it.isNotBlank() }
                .toSet()
            faviconStore.prune(hosts)
            val icons = hosts.mapNotNull { host -> faviconStore.read(host)?.let { host to it } }.toMap()
            if (icons.isEmpty()) return@launch
            icons.forEach { (host, icon) -> Favicons.remember(host, icon) }
            // Same "only fill a hole" rule as restoreThumbnails: a live page
            // that already reported its icon has the fresher one.
            _tabs.update { list ->
                list.map { if (it.favicon == null) it.copy(favicon = icons[it.host]) else it }
            }
            _history.update { list ->
                list.map { if (it.favicon == null) it.copy(favicon = icons[it.host]) else it }
            }
        }

    /**
     * Fills restored tabs back in with their last preview, so the switcher
     * shows real cards on a cold start instead of blanks waiting for each
     * tab's WebView to be created and captured.
     */
    private fun restoreThumbnails(ids: List<Long>): Job =
        viewModelScope.launch(Dispatchers.IO) {
            thumbnailStore.prune(ids.toSet())
            ids.forEach { id ->
                val bitmap = thumbnailStore.read(id) ?: return@forEach
                // The full-screen grab too, when one was kept: without it a
                // restored card zooms out of a page box with a blank strip
                // where the toolbar was.
                val full = thumbnailStore.readFull(id)
                // And the page under the status bar, when one was kept.
                val top = thumbnailStore.readTop(id)
                // Only fills a hole: by the time a read finishes, the tab may
                // already have been captured live, and that's the fresher one.
                _tabs.update { list ->
                    list.map {
                        if (it.id == id && it.thumbnail == null) {
                            it.copy(thumbnail = bitmap, thumbnailFull = full, thumbnailTop = top)
                        } else it
                    }
                }
            }
        }

    private fun markDirty() {
        if (ephemeral) return
        dirty.tryEmit(Unit)
    }

    private fun persistNow() {
        if (ephemeral) return
        store.save(
            BrowserStore.SavedState(
                tabs = _tabs.value
                    .filterNot { it.isPrivate }
                    .map { BrowserStore.SavedTab(id = it.id, url = it.url, title = it.title, host = it.host) },
                // The private space's current tab is never in the list
                // above, so the ordinary space's own copy is what a relaunch
                // has to come back to.
                currentTabId = normalCurrentTabId,
                history = _history.value,
                visits = _visits.value.values.toList(),
                bookmarks = _bookmarks.value,
                sitePermissions = _sitePermissions.value,
                // Minus whatever the private space changed: those keys are
                // live in the map so the pages they describe are actually
                // filtered that way, and they end with the space.
                siteSettings = (
                    _siteSettings.value.filterKeys { it !in privateSiteSettings } +
                        privateSiteSettings.mapNotNull { (site, before) -> before?.let { site to it } }
                    ).map { (site, settings) -> SiteSettingsEntry(site, settings) },
                settings = BrowserStore.Settings(
                    themeMode = _themeMode.value,
                    pageDarkMode = _pageDarkMode.value,
                    desktopMode = _desktopMode.value,
                    adBlockEnabled = _adBlockEnabled.value,
                    searchEngine = _searchEngine.value,
                    disabledSearchEngines = _disabledSearchEngines.value,
                    searchEngineOrder = _searchEngineOrder.value,
                    customSearchEngines = _customSearchEngines.value,
                    searchSuggestionsEnabled = _searchSuggestionsEnabled.value,
                    accentTheme = _accentTheme.value,
                    specialTheme = _specialTheme.value,
                    autoFocusNewTabKeyboard = _autoFocusNewTabKeyboard.value,
                    openNewTabSheetOnLaunch = _openNewTabSheetOnLaunch.value,
                    newTabHistorySort = _newTabHistorySort.value,
                    tabManagerMode = _tabManagerMode.value,
                    linkStripperEnabled = _linkStripperEnabled.value,
                    openExternalLinksInOverlay = _openExternalLinksInOverlay.value,
                    openLinksInApps = _openLinksInApps.value,
                    pullToRefreshEnabled = _pullToRefreshEnabled.value,
                    linkPreviewEnabled = _linkPreviewEnabled.value,
                    pageLens = _pageLens.value,
                    translucentSheets = _translucentSheets.value,
                    translucency = _translucency.value,
                    newTabPlacement = _newTabPlacement.value,
                    doubleTapTabsSwitchesTab = _doubleTapTabsSwitchesTab.value,
                    swipeToSwitchTabs = _swipeToSwitchTabs.value,
                    flickToCloseTab = _flickToCloseTab.value,
                    keepPrivateTabs = _keepPrivateTabs.value,
                    savePasswords = _savePasswordsEnabled.value,
                    externalPasswordManager = _externalPasswordManager.value,
                    fillAddresses = _fillAddresses.value,
                    fillPaymentMethods = _fillPaymentMethods.value,
                    permissionRules = _permissionRules.value,
                    pageZoom = _pageZoom.value,
                    readerSettings = _readerSettings.value,
                ),
            )
        )
    }

    // ---------------------------------------------------------------- tabs

    /**
     * The tabs of the space currently open — [tabs] is the whole set, both
     * spaces, since everything that persists, captures or evicts works across
     * all of them. The UI only ever sees one space at a time (see
     * BrowserScreen), and so does everything below that picks "the next tab".
     */
    private fun spaceTabs(private: Boolean = _privateMode.value): List<Tab> =
        _tabs.value.filter { it.isPrivate == private }

    /**
     * The one place [_currentTabId] is written, so the space it belongs to
     * always keeps its own copy — that's what a switch back into a space
     * lands on.
     */
    private fun setCurrentTab(id: Long) {
        // The bar belongs to the tab it was opened over (see FindState), so
        // switching away closes it rather than pointing it at a page whose
        // matches were never counted.
        if (_findState.value?.tabId != id) _findState.value = null
        _currentTabId.value = id
        if (_privateMode.value) privateCurrentTabId = id else normalCurrentTabId = id
        // Darkening is per tab now, so the tile has to report the tab it is
        // over. Nothing is rebuilt here: that tab's view was already built
        // with its own answer.
        _pageDarkActive.value = pageDarkFor(id)
        // Same for the reader: both halves of its state are per tab, and the
        // menu has to describe the tab it is now over.
        _readerAvailable.value = readerAvailability[id] == true
        _readerActive.value = id in readerOpen
        // A tab arriving with its reader already open has a position, and no
        // scroll is coming to report it — so ask. Anything else has none.
        _readerProgress.value = null
        if (id in readerOpen) views[id]?.let { ReaderMode.refreshProgress(it) }
        // A visit's dwell only runs while its tab is the one in front.
        syncVisitClock()
    }

    /**
     * Enters or leaves the private space. The two never mix: the tab list,
     * the current tab and "the other tab" all swap over, and (unless
     * [keepPrivateTabs]) leaving closes what was opened in there.
     *
     * An empty space is left empty rather than seeded with a tab —
     * BrowserScreen opens straight into the new-tab sheet whenever there are
     * no tabs, which is exactly the right landing for "I just went private".
     */
    fun setPrivateMode(on: Boolean) {
        if (_privateMode.value == on) return
        if (!on && !_keepPrivateTabs.value) closePrivateTabs()
        // The page darkening cross-fade is per-WebView and the WebView is
        // about to be swapped for another space's — same reasoning as
        // closing the current tab.
        finishPageDarkFade()
        _privateMode.value = on
        // "The other tab" is a within-space idea; carrying one across would
        // point the double-tap flip at a tab that isn't on the list. The
        // deferred move is settled rather than dropped — it belongs to the
        // space being left, which keeps its own order for the way back.
        promoteSteppedTab()
        _previousTabId.value = 0L
        val space = spaceTabs(on)
        val remembered = if (on) privateCurrentTabId else normalCurrentTabId
        setCurrentTab(space.firstOrNull { it.id == remembered }?.id ?: space.lastOrNull()?.id ?: 0L)
    }

    fun togglePrivateMode() = setPrivateMode(!_privateMode.value)

    fun toggleKeepPrivateTabs() {
        _keepPrivateTabs.value = !_keepPrivateTabs.value
        markDirty()
    }

    /**
     * Drops every private tab and its WebView. No disk work to undo: private
     * tabs are excluded from the saved tab list, the thumbnail store and the
     * parked page states, so there is nothing of theirs on disk to forget.
     */
    private fun closePrivateTabs() {
        val ids = spaceTabs(private = true).map { it.id }
        if (ids.isEmpty()) return
        ids.forEach { id ->
            views.remove(id)?.let(::destroy)
            pageDarkOverrides.remove(id)
            tabFilters.remove(id)
            failedLoads.remove(id)
            httpStatuses.remove(id)
            setPageBottomBar(id, 0)
            forgetReader(id)
            forgetPasswordState(id)
            cancelHttpAuthFor(id)
            cancelFormResubmissionFor(id)
        }
        // Everything else a private tab held is already gone with its view;
        // this is the one thing kept beside them rather than in them.
        httpAuthCredentials.keys.removeAll { it.startsWith("p|") }
        // Anything the private space said about a site goes back to what the
        // browser proper had said about it — the same rule as everything else
        // here, applied to the one kind of state the sheet can write.
        if (privateSiteSettings.isNotEmpty()) {
            _siteSettings.update { map ->
                var next = map
                privateSiteSettings.forEach { (site, before) ->
                    next = if (before == null) next - site else next + (site to before)
                }
                next
            }
            privateSiteSettings.clear()
            _tabs.value.forEach { tab -> tabFilters[tab.id] = filterRulesFor(siteKeyOf(tab.url)) }
        }
        _tabs.update { list -> list.filterNot { it.isPrivate } }
        privateCurrentTabId = 0L
    }

    /**
     * The tab [tab] was opened from, if that tab is still open and in the
     * same space. Checked rather than trusted: the opener can have been
     * closed long before the child is, and a stale id would silently take
     * the fallback below it.
     */
    fun openerTab(tab: Tab): Tab? {
        if (tab.openerTabId == 0L) return null
        return _tabs.value.firstOrNull {
            it.id == tab.openerTabId && it.isPrivate == tab.isPrivate
        }
    }

    /**
     * [openerId] is set only where the new tab is a link leaving a page the
     * user is still on — "Open in new tab". See [Tab.openerTabId] and
     * [openerTab]: it is what back walks out of the child tab into.
     */
    fun newTab(
        private: Boolean = _privateMode.value,
        url: String = Tab.HOME,
        openerId: Long = 0L,
        background: Boolean = false,
    ) {
        val tab = Tab(
            id = nextId++,
            url = url,
            host = UrlUtils.registrableDomain(url),
            isPrivate = private,
            openerTabId = openerId,
            secure = UrlUtils.isSecure(url),
        )
        // A tab can only be opened into the space it belongs to — an external
        // VIEW intent that arrives while the private space is open would
        // otherwise create a tab nothing on screen can reach.
        if (private != _privateMode.value) setPrivateMode(private)
        promoteSteppedTab()
        _tabs.update { it + tab }
        // A page the user asked for, so the first thing this tab's view
        // commits is a visit (see freshTabs). The home page was not asked for.
        if (!private && url != Tab.HOME) freshTabs += tab.id
        // A background tab is one the user has NOT been taken to, so nothing
        // about "where I am" moves for it: it is not selected, and it does
        // not become what a double-tap flip comes back to. It still goes to
        // the end of the row, which is where a new tab belongs — it is the
        // newest thing there whether or not it is being looked at.
        if (!background) {
            rememberPrevious(tab.id)
            setCurrentTab(tab.id)
        }
        markDirty()
    }

    /**
     * A tab for a window the PAGE asked for — `window.open`, a `target=_blank`
     * link — and the WebView that has to go in it, built here and now.
     *
     * Everywhere else a tab's view is made lazily by [webViewFor] when
     * something composes it; this one cannot wait, because the only thing
     * `onCreateWindow` can answer with is a live WebView handed straight back
     * through the transport, on that call. It is put in [views] under the new
     * tab's id, so the composition that arrives a frame later finds it there
     * rather than building a second one.
     *
     * It also arrives with no URL: the renderer navigates it itself the
     * moment the transport takes it, and [onPageStarted] is what fills the
     * tab's address in. Hence `load = false` — a load of ours here would be a
     * race with the page the popup was opened to show.
     *
     * The opener is recorded, so closing the popup lands back on the page
     * that raised it (see [closeTab]) — and, more to the point, so does the
     * opener relationship inside the renderer, which is the whole reason a
     * popup is a popup: an OAuth window's last act is to post its credential
     * to `window.opener` and close itself.
     */
    private fun openWindowTab(openerId: Long, private: Boolean): WebView {
        val tab = Tab(
            id = nextId++,
            url = "",
            host = "",
            isPrivate = private,
            openerTabId = openerId,
            secure = false,
        )
        promoteSteppedTab()
        _tabs.update { it + tab }
        val web = create(tab, load = false)
        views[tab.id] = web
        rememberPrevious(tab.id)
        setCurrentTab(tab.id)
        // After the switch, so the view just built is the current one and
        // cannot be the thing the trim evicts.
        trimViews()
        markDirty()
        return web
    }

    fun selectTab(id: Long) {
        // Anything a swipe deferred is settled first, so a tab stepped onto
        // and then left still ranks ahead of the ones nobody visited (and
        // behind this one, which is about to go to the very end).
        promoteSteppedTab()
        // Picking a tab from the switcher makes it the most-recently-used
        // one — moved to the end of the list so it's the rightmost card /
        // bottommost row the next time the switcher opens, not left sitting
        // wherever it was created.
        moveToEnd(id)
        rememberPrevious(id)
        setCurrentTab(id)
        markDirty()
    }

    /**
     * The tab a swipe stepped onto, still sitting where it was in the row and
     * owed its move to the end of it. See [stepToTab] / [promoteSteppedTab].
     */
    private var steppedTabId = 0L

    /**
     * The tab [delta] places along the row from the current one, or null at
     * either end — what a swipe between tabs is about to land on, asked for
     * before the gesture commits so the UI knows both whether it can run at
     * all and whose preview to slide in.
     */
    fun neighbourTab(delta: Int): Tab? {
        val space = spaceTabs()
        val index = space.indexOfFirst { it.id == _currentTabId.value }
        if (index == -1) return null
        return space.getOrNull(index + delta)
    }

    /**
     * Switches to a tab WITHOUT moving it in the row — the swipe between
     * tabs, which walks the row by position and so needs the positions to
     * hold still while it does. [selectTab]'s move-to-the-end would make the
     * neighbour you just came from the neighbour you're going to next: swipe
     * left, and the tab you left is no longer on your right.
     *
     * The move isn't cancelled, only deferred: the tab is remembered as owing
     * one, and [promoteSteppedTab] pays it the moment anything actually wants
     * the row in most-recently-used order (the switcher opening). So a run of
     * swipes reads as a row you are walking along, and the tab it ends on is
     * still the newest card when you go and look.
     */
    fun stepToTab(id: Long) {
        if (id == _currentTabId.value) return
        if (_tabs.value.none { it.id == id }) return
        rememberPrevious(id)
        setCurrentTab(id)
        steppedTabId = id
        markDirty()
    }

    /**
     * Pays off whatever [stepToTab] deferred — call it before showing the row
     * in most-recently-used order, or before any other switch reorders it.
     */
    fun promoteSteppedTab() {
        val id = steppedTabId
        if (id == 0L) return
        steppedTabId = 0L
        moveToEnd(id)
        markDirty()
    }

    private fun moveToEnd(id: Long) {
        _tabs.update { list ->
            val index = list.indexOfFirst { it.id == id }
            if (index == -1 || index == list.lastIndex) list else {
                val tab = list[index]
                list.toMutableList().apply { removeAt(index); add(tab) }
            }
        }
    }

    /**
     * Records whichever tab is current now as "the other tab", unless the
     * switch is to the tab already showing (nothing moved, so nothing to go
     * back to).
     */
    private fun rememberPrevious(newCurrentId: Long) {
        val leaving = _currentTabId.value
        if (leaving != newCurrentId && _tabs.value.any { it.id == leaving }) {
            _previousTabId.value = leaving
        }
    }

    fun closeTab(id: Long) {
        if (id == _currentTabId.value) finishPageDarkFade()
        val closed = _tabs.value.firstOrNull { it.id == id }
        views.remove(id)?.let(::destroy)
        pageDarkOverrides.remove(id)
        tabFilters.remove(id)
        failedLoads.remove(id)
        httpStatuses.remove(id)
        setPageBottomBar(id, 0)
        forgetReader(id)
        forgetPasswordState(id)
        forgetVisit(id)
        // Its notifications stay in the shade — a tap on one opens the site
        // again — but the page they would report a click to is gone.
        notificationRoutes.values.removeAll { it.tabId == id }
        // A card asking about a page that has just been closed has nothing to
        // ask about, and its handler belongs to a WebView already destroyed.
        cancelHttpAuthFor(id)
        cancelFormResubmissionFor(id)
        forgetPageStates(listOf(id))
        forgetThumbnails(listOf(id))
        _tabs.update { list -> list.filterNot { it.id == id } }
        if (_previousTabId.value == id) _previousTabId.value = 0L
        if (steppedTabId == id) steppedTabId = 0L
        if (_currentTabId.value == id) {
            // The tab this one was opened from, if it's still there — closing
            // a child lands back where it came from rather than on whatever
            // happens to be newest. Within the open space only: falling back
            // to a tab from the other one would put a page on screen that
            // isn't in the list.
            val opener = closed?.let(::openerTab)?.id
            setCurrentTab(opener ?: spaceTabs().lastOrNull()?.id ?: 0L)
        }
        markDirty()
    }

    /** Closes the open space's tabs — the other space is left as it was. */
    fun closeAllTabs() {
        val ids = spaceTabs().map { it.id }
        forgetPageStates(ids)
        forgetThumbnails(ids)
        ids.forEach { id ->
            views.remove(id)?.let(::destroy)
            pageDarkOverrides.remove(id)
            tabFilters.remove(id)
            failedLoads.remove(id)
            httpStatuses.remove(id)
            setPageBottomBar(id, 0)
            forgetPasswordState(id)
            forgetVisit(id)
            cancelHttpAuthFor(id)
            cancelFormResubmissionFor(id)
        }
        _tabs.update { list -> list.filterNot { it.id in ids } }
        setCurrentTab(0L)
        _previousTabId.value = 0L
        steppedTabId = 0L
        markDirty()
    }

    // ------------------------------------------------------------ webviews

    /**
     * The WebView for a tab, created on demand. Called from AndroidView's update
     * block — never store these in composition, or a recomposition destroys the tab.
     */
    /**
     * The tab's WebView, created on demand.
     *
     * Re-inserted on every call so [views]' insertion order is least-recently-
     * USED rather than least-recently-created — [trimViews] takes the first
     * key, and a plain `getOrPut` never reorders, so the oldest-created view
     * stayed the eviction candidate forever however recently it was on screen.
     * That is exactly the tab a double-tap flip goes back to.
     */
    fun webViewFor(tab: Tab): WebView {
        // Built HERE and nowhere else is what earns the cover: this is the
        // host asking for a tab it is about to show, so a view that has to be
        // built is a page the user is opening into and that has not been
        // fetched yet. The page-dark rebuild goes through [create] directly
        // for the opposite reason — see rebuildForPageDark, which crossfades
        // two live renderings precisely so no still image is ever shown.
        val web = views.remove(tab.id) ?: create(tab).also { coverUntilPainted(tab) }
        views[tab.id] = web
        trimViews()
        return web
    }

    /**
     * The cookie jar this WebView's page actually uses. A private tab lives
     * in its own profile, whose jar is a different one from the default —
     * reading or configuring `CookieManager.getInstance()` for it would reach
     * a session that belongs to somebody else, or to nobody.
     */
    private fun cookieManagerFor(web: WebView, isPrivate: Boolean): CookieManager =
        if (isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.getProfile(web).cookieManager
        } else {
            CookieManager.getInstance()
        }

    private fun create(tab: Tab, load: Boolean = true): WebView {
        // Seeded before anything can be fetched. `onPageStarted` keeps it in
        // step from here on, but a view built for a tab that is about to
        // restore its history would otherwise answer its first subresources
        // off the default while the record said otherwise.
        tabFilters[tab.id] = filterRulesFor(siteOfTab(tab.id))
        val dark = pageDarkFor(tab.id)
        val web = WebView(webContext(dark))
        builtDark[web] = dark

        // Must happen before any load, and throws if the WebView has been used.
        if (tab.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
            WebViewCompat.setProfile(web, PRIVATE_PROFILE)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            // Stated rather than left to the default (which is on): whether
            // a page may ask for the user's location is answered in one
            // place, and it is not this one. See onGeolocationPermissionsShowPrompt.
            setGeolocationEnabled(true)
            // A page asking for a second window. WebView's default is not to
            // refuse one — it is to turn window.open() into a navigation of
            // THIS view, with the popup's opener pointing at itself, which is
            // strictly worse than either answer. See onCreateWindow.
            setSupportMultipleWindows(true)
            textZoom = zoomFor(tab.id)
        }
        // The UA string, the client hints under it and the viewport the page is
        // laid out in — all three, or the site serves mobile anyway.
        DesktopMode.apply(web, _desktopMode.value)
        // Cookies for a document inside somebody else's page. WebView's
        // default is off, which is the right default for an app embedding a
        // page and the wrong one for a browser: an OAuth popup, a checkout
        // iframe and a sign-in widget are all a third party asking for the
        // session the user already has with it.
        runCatching { cookieManagerFor(web, tab.isPrivate).setAcceptThirdPartyCookies(web, true) }
        applyDarkening(web, dark)
        applyAutofillImportance(web)
        // Lets the page's own gesture handlers be seen from outside the
        // renderer, so pull-to-refresh can stand down for them.
        PullGate.attach(web)
        // Opts the page out of cross-document view transitions, which in
        // WebView never finish and leave the incoming page unable to paint.
        // See ViewTransitionGate.
        ViewTransitionGate.attach(web)
        // Hides cookie/consent banners before the page's first paint.
        applyCosmetic(web, cookieBannersFor(tab.id))
        // Reports the page's own bottom bar, if it has one, so the toolbar
        // hiding doesn't drop it under the system navigation bar.
        PageBottomBar.attach(web, { pageChromeInsetPx }, { pageBarFillPx }) { height ->
            setPageBottomBar(tab.id, height)
        }
        // The same at the other edge: the overlay's header, and in the
        // browser proper the status bar the page is laid out under (see
        // PageTopInset.setStrip).
        PageTopInset.attach(
            web, { pageTopContentPx }, { pageTopBarPx }, { pageTopFillPx },
            stripPx = { pageTopStripPx },
            stripFadePx = { pageTopStripFadePx },
            onStrip = { report -> setStatusStrip(tab.id, report) },
        )
        // Starts pulling the article out of every page from its first
        // paint — long before anything asks for it, which is the only moment
        // a metered page still has its article in the DOM. See ReaderMode.
        ReaderMode.attach(
            web,
            onAvailable = { setReaderAvailable(tab.id, it) },
            onClosed = { setReaderOpen(tab.id, false) },
            // Only the tab in front of the user, and only while its reader is
            // actually up: a background article cannot scroll on its own, but
            // a stray report from one would move the chrome over a page it is
            // not about.
            onScroll = { deltaY, scrollY ->
                if (tab.id == _currentTabId.value && tab.id in readerOpen) {
                    _readerScroll.tryEmit(ReaderScroll(deltaY, scrollY))
                }
            },
            // Same guard, same reason: the indicator belongs to the article in
            // front of the user.
            onProgress = { fraction ->
                if (tab.id == _currentTabId.value && tab.id in readerOpen) {
                    _readerProgress.value = fraction
                }
            },
        )
        // Watches for a login being submitted, for a login form appearing,
        // and for the cursor landing in one — the three things saved
        // passwords are built on. See PasswordForms.
        attachPasswordForms(web, tab.id)
        // And the other two things a form asks for. Separate from the above
        // because they are a separate question with a separate switch, and
        // because nothing here is ever captured — see FormFields.
        attachFormFields(web, tab.id)
        // `window.Notification`, which WebView does not have. See
        // WebNotifications and the site-permissions section.
        attachNotifications(web, tab)

        val id = tab.id

        // Where the finger currently is, so the context menu can be anchored
        // to the thing that was pressed. WebView's long-click callback says
        // WHAT was hit but never where, and the MotionEvent is the only place
        // that exists. It only watches, and everything about the touch
        // still reaches the page — until a long press raises something over
        // it, from which point it swallows the rest of the gesture.
        var touchX = 0f
        var touchY = 0f
        // Set the moment a long press raises something over the page, and
        // held until that finger comes off. See the long-click listener
        // below: the rest of the gesture belongs to the surface that was
        // just raised, not to the page under it.
        var swallowTouch = false
        @Suppress("ClickableViewAccessibility")
        web.setOnTouchListener { _, event ->
            touchX = event.rawX
            touchY = event.rawY
            // Which of the page's movements are the user's own — the one
            // thing the scrollbar is gated on. See [ScrollBarGate].
            scrollBarGate(web).onTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) swallowTouch = false
            // A page can repaint itself with nothing an app outside the
            // renderer can hear: a WebGL map pans and zooms without ever
            // scrolling the document, a canvas game draws, an SPA swaps a
            // view. Everything that queues a preview — scroll, progress,
            // navigation — misses all of it, which leaves such a tab showing
            // whatever it looked like when it finished loading. The finger
            // lifting is the one signal that is always there. Not
            // notePageMoved: that would also license a software draw to
            // replace a good copy, and the software path is exactly what
            // cannot reproduce a canvas.
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                queueCapture(id)
            }
            // Consuming here is what actually stops the page: a listener
            // that returns true skips View.onTouchEvent outright, so the
            // rest of the sequence never reaches Chromium at all. Cleared on
            // the finger coming up, so the next press starts fresh.
            if (swallowTouch) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    swallowTouch = false
                }
                true
            } else {
                false
            }
        }
        // Returning true here also suppresses WebView's own long-press
        // behavior (the text-selection ActionMode), which is exactly right on
        // a link or an image and exactly wrong on ordinary text — hence the
        // `false` for every other hit type.
        web.setOnLongClickListener { view ->
            val raised = openContextMenu(id, view as WebView, touchX, touchY)
            if (raised) {
                // The finger is still down, and Android goes on delivering
                // the rest of that gesture to whatever view it started in —
                // so a long press that put a preview card (or a menu) on
                // screen left the page underneath scrolling under a finger
                // the user thinks is driving the thing on top of it.
                //
                // Two halves: the page is told the sequence is OVER, so it
                // isn't left with a touch it will never see the end of (an
                // :active element, a half-finished touch handler), and every
                // event after this one is swallowed by the listener above so
                // it cannot start a new one either. Dispatched straight to
                // onTouchEvent rather than through dispatchTouchEvent, which
                // would hand it back to the listener that is about to start
                // refusing everything.
                // A cancel carries no meaningful position — it says the
                // gesture is off, not where it ended.
                val now = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0,
                )
                view.onTouchEvent(cancel)
                cancel.recycle()
                swallowTouch = true
            }
            raised
        }

        // findAllAsync's answer, and findNext's. Both land here; the state is
        // only taken if this tab still owns the bar, since a background tab
        // can report a stale count after the user has moved on.
        web.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            _findState.update { state ->
                if (state?.tabId != id) state
                else state.copy(
                    // Reported as a 0-based ordinal, and left at whatever it
                    // was when there are no matches at all — so it is derived
                    // from the count rather than trusted on its own.
                    activeMatch = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1,
                    matchCount = numberOfMatches,
                )
            }
        }

        // The page handing over a file instead of a page. Note this is the
        // ONLY notice of it there is: without a listener WebView's default is
        // to do nothing at all, so every download link on every site is a tap
        // that appears not to register. The cookies are read here, where the
        // WebView is, because a private tab's session lives in its own
        // profile's jar and the default one holds a different user or none.
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val cookie = runCatching {
                cookieManagerFor(web, tab.isPrivate).getCookie(url)
            }.getOrNull()
            _downloadRequest.value = FileDownloads.Request(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength,
                referer = _tabs.value.firstOrNull { it.id == id }?.url,
                cookie = cookie,
            )
        }

        web.webViewClient = object : WebViewClient() {
            /**
             * A link that belongs to somebody else's app — a `steam://` URL,
             * an `intent://` one, an https address an installed app claims.
             * Off, this is only reached for schemes the WebView could never
             * render anyway; see [ExternalLinks].
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = when (
                val route = ExternalLinks.route(
                    context = getApplication(),
                    url = request.url.toString(),
                    hasGesture = request.hasGesture(),
                    isMainFrame = request.isForMainFrame,
                    openLinksInApps = _openLinksInApps.value,
                )
            ) {
                ExternalLinks.Route.Browser -> {
                    // A document is about to replace this one: the picture it
                    // is held on is taken now, while the page is still this
                    // one. See pendingHolds.
                    if (request.isForMainFrame && !request.isRedirect) {
                        prepareHold(id, view, HoldKind.Link)
                    }
                    false
                }
                ExternalLinks.Route.Consumed -> true
                // An intent:// URL's browser_fallback_url. Loaded here rather
                // than returned as `false`, since the URL the WebView was
                // given is not the one that should be loaded.
                is ExternalLinks.Route.LoadInstead -> {
                    view.loadUrl(route.url)
                    true
                }
            }

            // Background thread. Keep it fast.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                if (adBlocker.shouldBlock(request, tabFilters[id] ?: AdBlocker.Rules.All)) {
                    AdBlocker.blockedResponse()
                } else null

            // Once per history entry the tab commits, reloads flagged — the
            // one callback that sees a pushState route and never sees a
            // server redirect's intermediate legs. See noteNavigation.
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                noteNavigation(id, view, url.orEmpty(), isReload)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // Before anything else on this page: what a document is
                // filtered, zoomed and darkened by is a property of the site
                // it belongs to, and the subresources it is about to ask for
                // are answered off [tabFilters].
                syncSiteState(id, view, url)
                applyHostCosmetic(view, url)
                // The navigation a submitted login causes IS the confirmation
                // the save prompt was waiting for — raised here rather than on
                // the submission itself, so a mistyped password that bounces
                // straight back doesn't get offered up as worth keeping.
                flushPendingCapture(id)
                resetPasswordPageState(id)
                // A find is against the document that is leaving.
                endFindFor(id)
                // A JsResult that is never answered blocks the renderer's
                // script thread for good, so the outgoing document's question
                // is declined rather than dropped.
                cancelJsDialogFor(id)
                // Same for the other two live handles a document can leave
                // behind: an unanswered auth challenge and an unanswered
                // resubmission both belong to a navigation this one replaces.
                cancelHttpAuthFor(id)
                cancelFormResubmissionFor(id)
                // And a notifications question, which (unlike the camera's)
                // WebView has no withdrawal callback for.
                cancelNotificationAskFor(id)
                // The old page's answer says nothing about the new one, and
                // the script re-measures as soon as this document has a
                // layout to measure.
                setPageBottomBar(id, 0)
                // The reader is a view of the document that is leaving, and
                // the incoming one has not been harvested yet.
                setReaderOpen(id, false)
                setReaderAvailable(id, false)
                adBlocker.resetPageCount()
                notePageMoved(id)
                _blockedOnPage.value = 0
                // `loading` and `progress` stay TRUE to what the tab is
                // doing whoever is watching — they are what the page-dark
                // crossfade waits on, what the pull-to-refresh spinner
                // clears on, and what the reload button is a stop button
                // because of. Whether any of that is drawn is the toolbar's
                // question, not this one (see BottomToolbar's `loading`).
                //
                // The favicon is different: it is the tab's IDENTITY, the
                // card in the switcher is showing it right now, and blanking
                // it here is a change to something the user is looking at
                // for the sake of a load they did not ask for. It stays
                // until the incoming page offers its own. See pageOnScreen.
                update(id) {
                    it.copy(
                        url = url,
                        host = UrlUtils.registrableDomain(url),
                        secure = UrlUtils.isSecure(url),
                        loading = true,
                        progress = 0,
                        favicon = if (pageOnScreen(id)) null else it.favicon,
                        loadFailed = false,
                        loadError = null,
                    )
                }
                // Both records belong to the document this navigation is
                // about to commit — see [FailedLoad]. Kept when the address
                // matches and this is the load they were made during;
                // dropped when the tab has gone somewhere else, or when they
                // have already been spent on a finished load, which is what
                // makes a reload of a page that failed able to succeed.
                httpStatuses[id]?.let { if (it.url != url || it.settled) httpStatuses.remove(id) }
                failedLoads[id]?.let { if (it.url != url || it.settled) failedLoads.remove(id) }
                // Last, after `loading` is true: the hold waits for it to go
                // false again. See pendingHolds.
                armNavHold(id, view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                _blockedOnPage.value = adBlocker.pageCount()
                // The title is taken here as well as at onReceivedTitle,
                // which is the one thing a background load DOES have to
                // catch up on: it skipped every title the page offered on the
                // way (see onReceivedTitle), and this is the last one, i.e.
                // the same title the card was already showing unless the page
                // has genuinely changed its own. Chromium answers with the
                // address when a document has no title of its own, exactly as
                // it does through the callback.
                val settled = view.title.orEmpty()
                update(id) {
                    it.copy(
                        url = url,
                        host = UrlUtils.registrableDomain(url),
                        title = settled.ifBlank { it.title },
                        loading = false,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward(),
                    )
                }
                _tabs.value.firstOrNull { it.id == id }?.let(::record)
                // A visit whose dwell ran out while the page was still
                // arriving was waiting for exactly this.
                commitVisit(id)
                // Back where the user left it, and only then out from
                // under its own picture — see settleRestoredPage.
                settleRestoredPage(id, view)
                // Not an immediate capture: the frame at onPageFinished is
                // rarely the finished picture (late images, web fonts, entrance
                // animations). notePageMoved takes one once it stops changing.
                notePageMoved(id)
                reapplyFailedLoad(id, url)
                settleHttpStatus(id, view, url)
            }

            // Network-level failure (DNS, connection refused, timeout, …) —
            // "it's either down". Subresource failures (an ad, a broken
            // image) are the overwhelming majority of calls here; only the
            // main document matters for the full-page placeholder.
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame && !isSpeculative(request)) {
                    // Which failure this was is the whole of what the error
                    // screen has to say — a name that doesn't resolve and a
                    // refused connection are different problems with
                    // different answers. LoadError.label is where the code
                    // becomes a sentence.
                    //
                    // A STATUS this address already came back with outranks
                    // the network code: an empty 503 reaches both hooks, the
                    // second as `ERR_HTTP_RESPONSE_CODE_FAILURE`, and "the
                    // site is unavailable" is the sentence worth printing
                    // where "something went wrong" is what is left of it.
                    val address = request.url.toString()
                    val detail = httpStatuses[id]?.takeIf { it.url == address }?.detail
                        ?: LoadError(
                            code = error.errorCode,
                            description = error.description?.toString().orEmpty(),
                            http = false,
                        )
                    failedLoads[id] = NavRecord(address, detail)
                    update(id) { it.copy(loading = false, loadFailed = true, loadError = detail) }
                    // Nothing is coming to replace the cover — WebErrorState
                    // is what should be on screen instead of a picture of the
                    // page as it was when it still loaded.
                    dismissCoversForError(id)
                }
            }

            /**
             * A certificate that doesn't check out. The default is
             * [SslErrorHandler.cancel] with nothing said, which is safe and
             * illegible: the page simply fails, with no way to find out why
             * and no way through for the cases where the user genuinely knows
             * better (a captive portal, a lab box, an expired cert on a site
             * they own).
             *
             * The answer is remembered per HOST for the life of the process,
             * which is what keeps this from being a loop rather than a
             * question: a page with twenty subresources on one broken
             * certificate raises twenty of these, and asking twenty times is
             * asking nobody. Nothing is written to disk — a relaunch asks
             * again, deliberately.
             */
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                val host = UrlUtils.registrableDomain(error.url).ifBlank { error.url }
                when (sslDecisions[host]) {
                    true -> handler.proceed()
                    false -> handler.cancel()
                    // One question at a time. A second error arriving while
                    // the card is up is refused rather than queued: it is
                    // usually the same certificate on another subresource,
                    // and it will be covered by the answer to the one being
                    // asked about.
                    null -> if (_sslPrompt.value != null) handler.cancel() else {
                        _sslPrompt.value = SslPrompt(id, host, error, handler)
                    }
                }
            }

            /**
             * An HTTP error STATUS (404, 500, …) on the main document. It is
             * only remembered here, never acted on: a status is not a
             * failure, and [httpStatuses] is where that is argued. The
             * failure, when there is one, comes through [onReceivedError],
             * which is also what this gives its sentence to. See
             * [isSpeculative] for the requests that are not this tab's
             * navigation at all.
             */
            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame && !isSpeculative(request)) {
                    httpStatuses[id] = NavRecord(
                        url = request.url.toString(),
                        detail = LoadError(
                            code = errorResponse.statusCode,
                            description = errorResponse.reasonPhrase.orEmpty(),
                            http = true,
                        ),
                    )
                }
            }

            /**
             * A site asking who is knocking. See [HttpAuthPrompt] for why the
             * default (a silent cancel) is the wrong answer.
             *
             * [HttpAuthHandler.useHttpAuthUsernamePassword] is what keeps a
             * remembered login from becoming a loop: it is false exactly when
             * the last credentials this handler was given were REJECTED, so a
             * wrong password is forgotten on the challenge it comes back as
             * rather than being resubmitted forever. It cannot be replaced by
             * a "have I already tried these" flag of our own, which a page's
             * second protected subresource would trip on its way in.
             */
            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String,
            ) {
                val key = httpAuthKey(tab.isPrivate, host, realm)
                if (handler.useHttpAuthUsernamePassword()) {
                    httpAuthCredentials[key]?.let { (user, password) ->
                        handler.proceed(user, password)
                        return
                    }
                } else {
                    httpAuthCredentials.remove(key)
                }
                // One question at a time, as with certificates: a second
                // challenge while the card is up is the same realm on another
                // subresource, and the answer being typed will cover it.
                if (_httpAuth.value != null) {
                    handler.cancel()
                    return
                }
                _httpAuth.value = HttpAuthPrompt(id, host, realm, key, handler)
            }

            /** See [FormResubmission]. */
            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                if (_formResubmission.value != null) {
                    dontResend.sendToTarget()
                    return
                }
                _formResubmission.value =
                    FormResubmission(id, view.url.orEmpty(), dontResend, resend)
            }

            /**
             * The renderer for this tab died — it crashed, or the system
             * killed it to get memory back (the common case: a browser with a
             * dozen live pages is exactly what a low-memory device reclaims
             * from).
             *
             * **Returning false, which is the default, kills this app's
             * process** — not the tab, the whole browser, taking every other
             * tab's live page with it. Returning true keeps the app up and
             * makes the dead WebView this code's problem to clear away, which
             * is what [handleRenderProcessGone] does.
             */
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                handleRenderProcessGone(id, view, detail.didCrash())
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                // A page announces its title partway through loading, and
                // often more than once — a card that is not being loaded FOR
                // should not flicker through those. The settled one is taken
                // at onPageFinished instead. See pageOnScreen.
                if (pageOnScreen(id)) update(id) { it.copy(title = title.orEmpty()) }
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                update(id) { it.copy(favicon = icon) }
                val tab = _tabs.value.firstOrNull { it.id == id }
                if (tab != null) {
                    _history.update { list ->
                        list.map { if (it.url == tab.url) it.copy(favicon = icon) else it }
                    }
                    // Shared with every non-tab row for the same site (history,
                    // bookmarks, the new-tab list) instead of each of them
                    // fetching its own copy over the network.
                    Favicons.remember(tab.host, icon)
                    // Private tabs' icons stay in memory, same rule as their
                    // tabs and previews never reaching disk.
                    if (!tab.isPrivate && tab.host.isNotBlank()) {
                        viewModelScope.launch(Dispatchers.IO) { faviconStore.write(tab.host, icon) }
                    }
                }
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                _blockedOnPage.value = adBlocker.pageCount()
                update(id) { it.copy(progress = newProgress) }
                notePageMoved(id)
            }

            /**
             * The page asking for a second window, and the thing to know is
             * that WebView's default is not silence and not refusal: with
             * multiple windows unsupported Chromium turns `window.open()`
             * into a navigation of the view that called it, and sets that
             * window's `opener` to ITSELF.
             *
             * Which is how a working sign-in comes out looking broken.
             * "Continue with Google" opens the OAuth flow in a popup; the
             * flow ran perfectly in the tab it had replaced, and the last
             * page of it (`accounts.google.com/gsi/transform`) posted the
             * credential to `window.opener` — i.e. to itself, the opener
             * having been overwritten — and called `window.close()`, which
             * does nothing to a main frame. The page the token was for had
             * been navigated away in that same view and never heard anything.
             * A blank page, forever, and no sign-in.
             *
             * So the window is real: a tab of its own, with its WebView built
             * on the spot ([openWindowTab]) because the transport is answered
             * on this call and there is nothing else to hand back.
             *
             * Gestures only. `javaScriptCanOpenWindowsAutomatically` is left
             * off, so WebView already withholds the ones a script raises on
             * its own; refusing them here too is the same answer given twice,
             * and the answer a pop-under deserves.
             */
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (!isUserGesture) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val opened = _tabs.value.firstOrNull { it.id == id } ?: return false
                transport.webView = openWindowTab(openerId = id, private = opened.isPrivate)
                resultMsg.sendToTarget()
                return true
            }

            /**
             * `window.close()`, which a page may only call on a window it
             * opened — so this is the popup above saying it is finished.
             * Closing the tab is what makes that legible: [closeTab] falls
             * back to the opener, so the flow ends on the page that started
             * it.
             *
             * Found by identity rather than by [id]: the callback carries the
             * view that is closing, which is the CHILD's, and this client
             * belongs to its parent.
             */
            override fun onCloseWindow(window: WebView) {
                val closing = views.entries.firstOrNull { it.value === window }?.key ?: return
                closeTab(closing)
            }

            // ---- the page asking for something outside the page ----------
            // WebChromeClient's interactive half, and the thing to know about
            // all of it is that the DEFAULT for every one of these is silence
            // rather than refusal: an unhandled file input does nothing when
            // tapped, an unhandled fullscreen request leaves the video in its
            // box, an unhandled confirm() resolves to false without the user
            // ever being asked. Each is put on screen by ui/WebPlatform.kt.

            /**
             * `<input type="file">`. The intent comes from [FileChooserParams]
             * rather than being built here, so the picker opens narrowed to
             * the accept types and the multiple-selection flag the form
             * actually declared.
             */
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                val intent = runCatching { params.createIntent() }.getOrNull()
                if (intent == null) {
                    callback.onReceiveValue(null)
                    return true
                }
                // A chooser already open is answered before it is replaced.
                // Dropping a ValueCallback without calling it leaves that
                // form's input permanently unresponsive — and it stays that
                // way for the life of the page, not just the gesture.
                _fileChooser.value?.callback?.onReceiveValue(null)
                _fileChooser.value = FileChooserRequest(id, intent, callback)
                return true
            }

            /**
             * Fullscreen video (and anything else the page fullscreens). The
             * View handed over is Chromium's own — it is added to the tree by
             * the composable that draws it and must be given back untouched.
             */
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (_fullscreen.value != null) {
                    // Nothing sensible to do with a second one; refusing it
                    // properly means telling the page its view is gone.
                    callback.onCustomViewHidden()
                    return
                }
                _fullscreen.value = Fullscreen(id, view, callback)
            }

            /** The PAGE leaving fullscreen — see [exitFullscreen] for the user doing it. */
            override fun onHideCustomView() {
                _fullscreen.value = null
            }

            /**
             * `getUserMedia` — the camera, the microphone, or both at once.
             * Refusing an unrecognised resource rather than passing it
             * through is deliberate: `grant` takes exactly the strings it is
             * given, and granting one the user was never shown a row for is
             * the failure this whole path exists to prevent. DRM
             * (`RESOURCE_PROTECTED_MEDIA_ID`) and MIDI fall out here, denied
             * the way an unhandled request already was.
             */
            override fun onPermissionRequest(request: PermissionRequest) {
                val wanted = request.resources.mapNotNull(SitePermission::ofResource).distinct()
                if (wanted.isEmpty()) {
                    request.deny()
                    return
                }
                askSitePermission(
                    tabId = id,
                    origin = originOf(request.origin?.toString().orEmpty()),
                    wanted = wanted,
                ) { granted ->
                    val resources = granted.mapNotNull { it.resource }.toTypedArray()
                    // The request belongs to a frame that may be gone by the
                    // time an answer arrives — the user can take as long as
                    // they like over this card, and the page cannot be made
                    // to wait for them.
                    runCatching {
                        if (resources.isEmpty()) request.deny() else request.grant(resources)
                    }
                }
            }

            /**
             * The page withdrawing the question — a call ended, the element
             * removed — while the card is still up. There is nothing left to
             * answer, so the card comes down and the reply is dropped rather
             * than sent: `deny` on a cancelled request is a call into a frame
             * that has already stopped listening.
             */
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                val ask = _permissionAsk.value ?: return
                if (ask.tabId != id) return
                if (ask.origin != originOf(request.origin?.toString().orEmpty())) return
                _permissionAsk.value = null
                pendingPermission = null
            }

            /**
             * Location, which does not come through [PermissionRequest] at
             * all — it has its own callback, and its own third argument for
             * WebView's own per-origin memory. That is passed `false` on
             * purpose: this app remembers the answer itself
             * ([SitePermissionGrant]), where the user can see it and take it
             * back, and two stores of the same fact is one the Settings pane
             * cannot reach.
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                val raw = origin.orEmpty()
                askSitePermission(id, originOf(raw), listOf(SitePermission.Location)) { granted ->
                    callback?.invoke(raw, granted.isNotEmpty(), false)
                }
            }

            /** Same withdrawal, for the same reason as the media one above. */
            override fun onGeolocationPermissionsHidePrompt() {
                val ask = _permissionAsk.value ?: return
                if (ask.tabId != id || SitePermission.Location !in ask.permissions) return
                _permissionAsk.value = null
                pendingPermission = null
            }

            override fun onJsAlert(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult,
            ): Boolean = raise(JsDialog(id, JsDialogKind.Alert, url.orEmpty(), message.orEmpty(), "", result))

            override fun onJsConfirm(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult,
            ): Boolean = raise(JsDialog(id, JsDialogKind.Confirm, url.orEmpty(), message.orEmpty(), "", result))

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean = raise(
                JsDialog(id, JsDialogKind.Prompt, url.orEmpty(), message.orEmpty(), defaultValue.orEmpty(), result)
            )

            /**
             * The "you have unsaved changes" question. The page's own message
             * is deliberately not shown — every browser stopped rendering it
             * years ago, because it is attacker-controlled text in a dialog
             * the user cannot leave.
             */
            override fun onJsBeforeUnload(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult,
            ): Boolean = raise(JsDialog(id, JsDialogKind.BeforeUnload, url.orEmpty(), "", "", result))

            /**
             * One dialog at a time. A page can call `alert()` in a loop, and
             * queuing those would be handing the browser over to it — the
             * second and later ones are declined, which is what a user who
             * closed the first would have said anyway.
             */
            private fun raise(dialog: JsDialog): Boolean {
                if (_jsDialog.value != null) {
                    dialog.result.cancel()
                    return true
                }
                _jsDialog.value = dialog
                return true
            }
        }

        // A window opened BY a page arrives empty and is navigated by the
        // renderer the moment the transport hands this view over — there is
        // no URL of ours to load, and loading one would be a race with the
        // page the popup was opened to show.
        if (!load) {
            // Raised by a gesture on a page (see openWindowTab): what it
            // commits first is somewhere the user went.
            if (!tab.isPrivate) freshTabs += tab.id
            return web
        }

        // A rebuilt WebView (page dark mode flipped, see rebuildForPageDark)
        // or a restored one (see PageStateStore) gets its back/forward history
        // — and the scroll offset it was left at — back, instead of a bare
        // load of whatever page it happened to be on.
        val parked = pendingRestores.remove(tab.id) ?: parkedFromDisk(tab.id)
        if (parked != null) {
            val restored = web.restoreState(parked)
            // A parked state older than the tab it belongs to would quietly
            // put a different page on screen than the title, URL bar and
            // preview all say. Only the state that agrees with the tab is
            // worth having; anything else falls through to a plain load.
            if (restored != null && restored.currentItem?.url == tab.url) {
                val y = parked.getInt(PARKED_SCROLL_Y, 0)
                if (y > 0) pendingScroll[tab.id] = y
                return web
            }
        }
        // A plain load starts at the top and is honest about it: there is no
        // history behind this page any more, so there is no "where it was".
        pendingScroll.remove(tab.id)
        web.loadUrl(tab.url)
        return web
    }

    /**
     * A saved session's parked state for a tab whose [restorePageStates] job
     * hasn't landed yet — the narrow window where a tab can be opened before
     * its file has been read. Spent on first use: see [diskStatesUsed].
     */
    private fun parkedFromDisk(id: Long): Bundle? {
        // An ephemeral session numbers its tabs from 1 like any other, but
        // those ids mean nothing outside it — reading id 1's parked page here
        // would be reading the REAL session's, and restoring somebody else's
        // tab into an overlay opened on a link.
        if (ephemeral) return null
        if (pageStatesLoaded || id in diskStatesUsed) return null
        diskStatesUsed += id
        return pageStateStore.read(id)
    }

    /**
     * Turns a long press into a [WebContextTarget], or lets it through
     * untouched (`false`) when it landed on something with no menu of its own.
     *
     * An anchor's href doesn't come back on [WebView.HitTestResult] in the
     * case that matters most — a linked image, where `extra` is the image's
     * src — so both anchor types go through [WebView.requestFocusNodeHref],
     * which delivers `url`/`src`/`title` asynchronously into a Message. The
     * hit result is read now (it describes the press that just happened) and
     * only the href arrives late.
     */
    private fun openContextMenu(id: Long, web: WebView, x: Float, y: Float): Boolean {
        val result = web.hitTestResult
        val extra = result.extra
        return when (result.type) {
            WebView.HitTestResult.IMAGE_TYPE -> {
                if (extra.isNullOrBlank()) false else {
                    val isPrivate = _tabs.value.firstOrNull { it.id == id }?.isPrivate == true
                    _contextTarget.value = WebContextTarget(
                        id,
                        imageUrl = extra,
                        userAgent = web.settings.userAgentString,
                        cookies = runCatching { cookieManagerFor(web, isPrivate).getCookie(extra) }.getOrNull(),
                        x = x,
                        y = y,
                    )
                    true
                }
            }

            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            -> {
                val linkedImage = result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                // Subclassed rather than the (Looper, Callback) constructor,
                // which only exists from API 28 — this app runs from 26.
                val handler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(message: Message) {
                        val data = message.data
                        // A link is a link, whatever it happens to be drawn
                        // with. Sites wrap images in anchors constantly (a
                        // logo, an icon before a label, a card that is one big
                        // <a>), and WebView reports every one of those as
                        // SRC_IMAGE_ANCHOR_TYPE with an image src alongside —
                        // which put Download/Copy image on menus for what read
                        // as ordinary links. Image actions are now reserved
                        // for a press that landed on an image and nothing
                        // else (IMAGE_TYPE, handled above).
                        val href = data.getString("url")?.takeIf { it.isNotBlank() }
                            ?: extra?.takeIf { !linkedImage && it.isNotBlank() }
                            ?: return
                        // A link raises a PREVIEW rather than a menu: the
                        // three things worth doing with a link are on a bar
                        // under the card, and the question a long press is
                        // really asking — is this worth going to — is one
                        // only the page itself can answer. Turned off
                        // (Settings > Behavior > Links), the same press
                        // raises the menu that has those three things and
                        // nothing else. Images take the menu either way
                        // (IMAGE_TYPE above): there is nothing to preview.
                        if (_linkPreviewEnabled.value) {
                            _linkPreview.value = LinkPreview(
                                token = ++previewSeq,
                                tabId = id,
                                requestedUrl = href,
                                isPrivate = _tabs.value.firstOrNull { it.id == id }?.isPrivate == true,
                            )
                        } else {
                            _contextTarget.value = WebContextTarget(
                                tabId = id,
                                linkUrl = href,
                                // The anchor's own text where the page gave
                                // one — the menu's header, which is what
                                // makes a press on a dense page legible.
                                label = data.getString("title"),
                                x = x,
                                y = y,
                            )
                        }
                    }
                }
                web.requestFocusNodeHref(handler.obtainMessage())
                true
            }

            else -> false
        }
    }

    fun dismissContextMenu() {
        _contextTarget.value = null
    }

    // -------------------------------------------------------- link previews

    /**
     * The preview's page, built on first ask and reused for the rest of that
     * preview — the UI's `AndroidView` factory is the only caller. Keyed by
     * token rather than held in a single field: a preview dismissed and
     * another raised before the first card has finished leaving means two of
     * these alive at once, and the departing one must not be handed to the
     * arriving card.
     *
     * Deliberately NOT [create]: almost everything that function attaches is
     * about a TAB. Passwords, find, the page's bottom bar, the pull-to-refresh
     * gate, thumbnail capture and the long-press menu all key off a tab id
     * this page does not have, and half of them would write a preview's state
     * over the tab underneath it. What a preview does share is how a page is
     * SHOWN: the ad blocker, the cookie-banner stylesheet, dark mode, zoom and
     * the desktop user agent.
     */
    fun previewWebView(token: Long): WebView? {
        previewViews[token]?.let { return it }
        val preview = _linkPreview.value?.takeIf { it.token == token } ?: return null
        return createPreview(preview).also { previewViews[token] = it }
    }

    private fun createPreview(preview: LinkPreview): WebView {
        val token = preview.token
        // A preview is a page like any other, so it is filtered, zoomed and
        // banner-hidden by the record of the site it is SHOWING — the link's
        // site, not that of the tab the link was pressed on. Held in a
        // reference rather than resolved per request: the intercept runs on a
        // background thread, and a link followed inside the card can move it.
        val previewSettings = settingsForSite(siteKeyOf(preview.url))
        val previewRules = java.util.concurrent.atomic.AtomicReference(
            AdBlocker.Rules(ads = previewSettings.blockAds, trackers = previewSettings.blockTrackers)
        )
        val dark = pageDarkFor(preview.tabId)
        val web = WebView(webContext(dark))
        builtDark[web] = dark

        // Must happen before any load. A link pressed in the private space is
        // previewed in the private profile, or the preview is the one part of
        // that session that lands in the ordinary cookie jar.
        if (preview.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
            WebViewCompat.setProfile(web, PRIVATE_PROFILE)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            textZoom = previewSettings.zoom ?: _pageZoom.value
        }
        // The UA string, the client hints under it and the viewport the page is
        // laid out in — all three, or the site serves mobile anyway.
        DesktopMode.apply(web, _desktopMode.value)
        applyDarkening(web, dark)
        applyAutofillImportance(web)
        ViewTransitionGate.attach(web)
        applyCosmetic(web, previewSettings.hideCookieBanners)

        web.webViewClient = object : WebViewClient() {
            /**
             * Links followed inside the preview stay inside it — the whole
             * point is a page that can be read without being committed to.
             * Only the schemes a WebView could never render leave, which is
             * [ExternalLinks] with the user's own "open links in apps" switch
             * held off: a preview handing the user to another app is the
             * opposite of a look before you leap.
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = when (
                val route = ExternalLinks.route(
                    context = getApplication(),
                    url = request.url.toString(),
                    hasGesture = request.hasGesture(),
                    isMainFrame = request.isForMainFrame,
                    openLinksInApps = false,
                )
            ) {
                ExternalLinks.Route.Browser -> false
                ExternalLinks.Route.Consumed -> true
                is ExternalLinks.Route.LoadInstead -> {
                    view.loadUrl(route.url)
                    true
                }
            }

            // Background thread. Keep it fast.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                if (adBlocker.shouldBlock(request, previewRules.get())) {
                    AdBlocker.blockedResponse()
                } else null

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // The card can be read its way onto another site, and it is
                // filtered by the one it is showing.
                previewRules.set(filterRulesFor(siteKeyOf(url)))
                applyHostCosmetic(view, url)
                updatePreview(token) { it.copy(url = url) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                updatePreview(token) { it.copy(url = url) }
            }

            /**
             * A preview's renderer is a renderer like any other, and the
             * default answer here would take the whole app down with it (see
             * the tab client's own override). There is nothing to rebuild —
             * a preview is a page being looked THROUGH, and one whose
             * renderer has died has nothing left to look at — so the card is
             * dismissed and the view goes the way every dismissed preview's
             * does, destroyed by [releasePreviewView] once the UI has taken
             * it back out of the tree.
             */
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Not [closeLinkPreview], which stops the page first: past
                // this callback the only defined things to do with the view
                // are detach and destroy it, and it has already stopped.
                if (_linkPreview.value?.token == token) _linkPreview.value = null
                return true
            }
        }

        // No WebChromeClient: a preview's card shows nothing but the page, so
        // there is no title, icon or progress for one to report to. The
        // defaults are silence — which for the rest of it (a file input, a
        // fullscreen request, an alert) is what a page being glanced at
        // should get anyway.

        web.loadUrl(preview.requestedUrl)
        return web
    }

    /**
     * Only ever touches the preview that is actually up: a departing card's
     * page goes on loading for a moment after it is dismissed, and its
     * callbacks must not write over the one that replaced it.
     */
    private fun updatePreview(token: Long, block: (LinkPreview) -> LinkPreview) {
        _linkPreview.update { if (it?.token == token) block(it) else it }
    }

    private fun previewView(): WebView? = _linkPreview.value?.let { previewViews[it.token] }

    /** Back inside the preview. False when there is nowhere left to go. */
    fun previewGoBack(): Boolean {
        val web = previewView() ?: return false
        if (!web.canGoBack()) return false
        web.goBack()
        return true
    }

    fun closeLinkPreview() {
        // The page stops the moment the card starts leaving, rather than
        // going on loading (or playing) behind an exit animation. The view
        // itself is destroyed by [releasePreviewView], once the UI has taken
        // it back out of the view tree.
        previewView()?.stopLoading()
        _linkPreview.value = null
    }

    /**
     * Called by the overlay as its host leaves composition. Destroying a
     * WebView that is still attached is what leaves a dead renderer's window
     * on screen, so this is the UI's call to make, not the dismissal's.
     */
    fun releasePreviewView(token: Long) {
        previewViews.remove(token)?.let(::destroy)
    }

    fun refreshDownloads() {
        viewModelScope.launch { _downloads.value = downloadsStore.list() }
    }

    /**
     * Follows a DownloadManager transfer until it ends, publishing the sum of
     * everything followed as [downloadActivity].
     *
     * Polled, because DownloadManager says nothing about a download between
     * enqueue and completion, and only while something is in flight: the
     * loop ends with the last one. A download that SUCCEEDED leaves the ring
     * full for [DOWNLOAD_DONE_HOLD_MS] before it goes, so the finish is a
     * circle closing rather than a ring at 80% vanishing; a failed or
     * cancelled one just goes. Published as "going, size unknown" the moment
     * it is enqueued, so the button turns in the same frame the icon lands
     * rather than a poll later.
     */
    fun trackDownload(managerId: Long) {
        trackedDownloads += managerId
        if (_downloadActivity.value?.count.let { it == null || it == 0 }) {
            _downloadActivity.value = DownloadActivity(count = trackedDownloads.size, fraction = null)
        }
        if (downloadPoll?.isActive == true) return
        downloadPoll = viewModelScope.launch {
            while (trackedDownloads.isNotEmpty()) {
                var succeeded = false
                while (trackedDownloads.isNotEmpty()) {
                    val rows = DownloadProgress.query(getApplication(), trackedDownloads.toLongArray())
                    val live = rows.filter { it.ongoing }.map { it.id }.toSet()
                    if (rows.any { it.succeeded && it.id in trackedDownloads }) {
                        succeeded = true
                        // The Downloads screen reads MediaStore, which has
                        // the file now.
                        refreshDownloads()
                    }
                    // Finished, failed, or gone altogether (cancelled from
                    // the notification removes the row) — all stop here.
                    trackedDownloads.retainAll(live)
                    // Filtered again rather than trusting `rows`: a cancel
                    // can land while this query was out.
                    val followed = rows.filter { it.id in trackedDownloads }
                    _activeDownloads.value = followed
                    if (trackedDownloads.isEmpty()) break
                    _downloadActivity.value = DownloadProgress.aggregate(followed)
                    delay(DOWNLOAD_POLL_MS)
                }
                if (succeeded) {
                    _downloadActivity.value = DownloadActivity(count = 0, fraction = 1f)
                    delay(DOWNLOAD_DONE_HOLD_MS)
                }
                // A download started during the hold is picked up by the
                // outer loop rather than left for a poll that has ended.
            }
            _downloadActivity.value = null
        }
    }

    /**
     * Stops a download and deletes what it had written so far —
     * DownloadManager's `remove` does both. Dropped from the rows at once
     * rather than at the next poll, so the row goes on the tap; the poll then
     * finds nothing left to follow and lets the ring go.
     */
    fun cancelDownload(managerId: Long) {
        trackedDownloads.remove(managerId)
        _activeDownloads.update { rows -> rows.filterNot { it.id == managerId } }
        viewModelScope.launch { DownloadProgress.cancel(getApplication(), managerId) }
    }

    fun deleteDownload(entry: DownloadEntry) {
        viewModelScope.launch {
            downloadsStore.delete(entry)
            // Re-read rather than removing the row locally: a delete that the
            // system refused (a file this app doesn't own) has to leave the
            // row on screen, not appear to work.
            _downloads.value = downloadsStore.list()
        }
    }

    /**
     * The context a WebView is built with, which is what decides the
     * `prefers-color-scheme` it reports — see the theme comments in
     * themes.xml. One instance per state, because it's fixed at construction:
     * changing which one a live WebView was built with means building it
     * again ([rebuildForPageDark]).
     */
    private fun webContext(dark: Boolean): Context = webContexts.getOrPut(dark) {
        ContextThemeWrapper(
            webHostContext,
            if (dark) R.style.Theme_Browser_PageDark else R.style.Theme_Browser_PageLight,
        )
    }

    /**
     * Points the WebViews' context at the Activity now hosting them. See
     * [webHostContext] for why they are not simply built on it.
     */
    fun attachHost(activity: Activity) {
        webHostContext.baseContext = activity
    }

    /**
     * Lets go of it again, so a destroyed Activity isn't held by every tab.
     * Identity-checked: an Activity that has already been replaced must not
     * take the replacement's place with it.
     */
    fun detachHost(activity: Activity) {
        if (webHostContext.baseContext === activity) {
            webHostContext.baseContext = getApplication()
        }
    }

    /**
     * Requests made by service workers do NOT reach WebViewClient — they need
     * their own client, and forgetting this silently leaks traffic past the filter.
     */
    private fun installServiceWorkerFilter() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(
                    request: WebResourceRequest,
                ): WebResourceResponse? =
                    if (adBlocker.shouldBlock(request)) AdBlocker.blockedResponse() else null
            }
        )
    }

    /**
     * Evicts down to [MAX_LIVE_WEBVIEWS], oldest-touched background tab first.
     *
     * Through [parkAndDrop], NOT a bare destroy: the tab's navigation history
     * and scroll offset are saved into [pendingRestores] on the way out, so
     * coming back to it restores the page it was on rather than cold-loading
     * its URL — which lost the back stack, lost the scroll offset, and put a
     * blank page on screen for however long the network took. That blank is
     * what a swap back to an evicted tab was showing, since the cover over it
     * is measured in frames and a load is not.
     */
    private fun trimViews() {
        while (views.size > MAX_LIVE_WEBVIEWS) {
            val victim = views.keys.firstOrNull { it !in protectedViewIds() } ?: return
            parkAndDrop(victim)
        }
    }

    /**
     * The live views that may not be evicted, whatever the pressure.
     *
     * A tab that OPENED one of the live windows is not a candidate, however
     * long ago it was looked at. Eviction parks the page and destroys the
     * view, and the view is what the child's `window.opener` refers to —
     * evicting it turns the popup's one way of reporting back into a
     * reference to nothing. This is a narrow set by construction: only a
     * window the page itself asked for carries an opener at all, and only
     * while it is open.
     */
    private fun protectedViewIds(): Set<Long> {
        val openers = views.keys
            .mapNotNull { live -> _tabs.value.firstOrNull { it.id == live }?.openerTabId }
            .filter { it != 0L }
        return setOfNotNull(_currentTabId.value, pinnedTabId) + openers
    }

    // ------------------------------------------------------- memory pressure
    //
    // Measured on device, a restored 16-tab session: 300MB PSS in this
    // process plus ~100MB in the renderer's. Of ours, ~42MB is bitmap — one
    // preview per tab, ARGB_8888 at half the window's dimensions, ~2.5MB
    // each on a 1080x2392 screen — and the rest is split between the live
    // WebViews and Compose.
    //
    // What is worth handing back when the system asks is what can be got
    // again from disk: the background WebViews (parked, exactly as an
    // ordinary eviction parks them) and the favicon cache (which is otherwise
    // never evicted at all).
    //
    // **Previews are deliberately NOT dropped here**, though they are the
    // biggest single thing in the process. They are held twice over: once by
    // [Tab], and once by the composition that last read it, and the second
    // one is not ours to release — `collectAsStateWithLifecycle` stops
    // collecting at `onStop`, which is BEFORE every trim level that means
    // real pressure, so the screen's `State` keeps the pre-trim list of tabs
    // and every bitmap in it until the app is resumed. Nulling them here
    // frees nothing (verified: 42 bitmaps / 46MB before and after a
    // `TRIM_MEMORY_COMPLETE`) and makes the return worse, since re-reading
    // them from disk allocates a second set alongside the ones the
    // composition is still holding. Bounding them properly means the UI
    // asking for a preview when it draws one rather than holding it in tab
    // state, which is a change to the switcher, not to this.
    //
    // Registered here rather than in `init`, which runs before this property
    // is assigned; unregistered in [onCleared].
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Kept for the pre-34 platform, which still calls it.")
        override fun onLowMemory() = handleTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        override fun onTrimMemory(level: Int) = handleTrimMemory(level)
    }.also { app.registerComponentCallbacks(it) }

    /**
     * Hands memory back, in two steps matched to what the level actually says.
     *
     * `RUNNING_LOW`/`RUNNING_CRITICAL` are the foreground ones: the user is
     * still in the app, so only the background WebViews go. That costs
     * nothing visible — coming back to one of those tabs is the same
     * park-and-restore an ordinary eviction already takes ([trimViews]).
     * (API 34 stopped sending these; the branch is for everything below it.)
     *
     * `BACKGROUND` and worse mean the app is not on screen and is being sized
     * up for killing, so the icon cache goes too — nothing is drawing it, and
     * [FaviconStore] has every icon that was ever worth keeping.
     *
     * `RUNNING_MODERATE` and `UI_HIDDEN` are deliberately ignored: the first
     * is not pressure, and the second is not memory — it is only the news
     * that the UI went away, which arrives every single time the user leaves
     * the app.
     */
    private fun handleTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        dropBackgroundViews()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) Favicons.releaseMemory()
    }

    /** Parks and drops every live view but the ones [protectedViewIds] names. */
    private fun dropBackgroundViews() {
        // A page-dark cross-fade holds a second, live WebView over the tab on
        // screen and finishes by destroying it. Cutting it short here is how
        // that one gets dropped too, and leaves the incoming view — which is
        // already the tab's — as the page.
        finishPageDarkFade()
        val keep = protectedViewIds()
        views.keys.toList().filter { it !in keep }.forEach(::parkAndDrop)
    }

    private fun destroy(web: WebView) {
        navSnapshots.values.removeAll { it.view === web }
        pendingHolds.values.removeAll { it.web === web }
        activeHolds.filterValues { it.web === web }.forEach { (id, hold) -> removeHold(id, hold) }
        (web.parent as? ViewGroup)?.removeView(web)
        web.stopLoading()
        web.destroy()
    }

    // ----------------------------------------------------------- navigation

    private fun currentView(): WebView? = views[_currentTabId.value]

    // Each of these goes through [prepareHold], which takes the picture the
    // navigation is held on and THEN navigates.

    fun goBack() {
        val id = _currentTabId.value
        val web = views[id]?.takeIf { it.canGoBack() } ?: return
        prepareHold(id, web, HoldKind.History) {
            if (views[id] === web && web.canGoBack()) web.goBack()
        }
    }

    fun goForward() {
        val id = _currentTabId.value
        val web = views[id]?.takeIf { it.canGoForward() } ?: return
        prepareHold(id, web, HoldKind.History) {
            if (views[id] === web && web.canGoForward()) web.goForward()
        }
    }

    /**
     * [fromPull]: the picture was taken as the pull began ([preparePullReload])
     * and a copy taken now would have the spinner in it — so without one, the
     * thumbnail is the only stand-in worth having.
     */
    fun reload(fromPull: Boolean = false) {
        val id = _currentTabId.value
        val web = views[id] ?: return
        if (fromPull) {
            if (pendingHolds[id]?.web !== web) {
                thumbnailHold(id, web, HoldKind.Reload)?.let { pendingHolds[id] = it }
            }
            web.reload()
            return
        }
        prepareHold(id, web, HoldKind.Reload) { if (views[id] === web) web.reload() }
    }

    /** A pull-to-refresh has begun: the page as it is, before the spinner is drawn over it. */
    fun preparePullReload() {
        val id = _currentTabId.value
        val web = views[id] ?: return
        prepareHold(id, web, HoldKind.Reload)
    }

    fun load(url: String) {
        val id = _currentTabId.value
        val web = views[id]
        if (web == null) {
            // Into the space that's open — `newTab`'s default. Naming `false`
            // here would drop out of private mode (and, by default, close
            // what was open in it) just because a load arrived with no tab to
            // put it in.
            newTab(url = url)
            return
        }
        prepareHold(id, web, HoldKind.Link) { if (views[id] === web) web.loadUrl(url) }
    }

    // ---------------------------------------------------------- find in page

    /**
     * The find bar's entire state. It is per tab, not global: the search is
     * WebView's own (`findAllAsync` highlights inside one document), so a bar
     * showing "3/12" over a different tab's page would be counting matches
     * nobody can see. Only one tab can be finding at a time, which is the
     * same thing as saying the bar belongs to whichever tab is on screen.
     *
     * [activeMatch] is 1-based for display; WebView reports a 0-based
     * ordinal, and 0 here means "no matches" rather than "the first one".
     */
    data class FindState(
        val tabId: Long,
        val query: String = "",
        val activeMatch: Int = 0,
        val matchCount: Int = 0,
    )

    /**
     * No live WebView means no document to search — the empty new-tab state
     * has a tab but nothing in it, and a find bar there would take the
     * keyboard for a search that can never match.
     */
    fun openFind() {
        val id = _currentTabId.value
        if (views[id] == null) return
        if (_findState.value?.tabId == id) return
        _findState.value = FindState(tabId = id)
    }

    fun closeFind() {
        val state = _findState.value ?: return
        _findState.value = null
        // Drops the yellow highlight WebView left on the page; without this
        // it survives the bar and stays there until the next navigation.
        views[state.tabId]?.clearMatches()
    }

    /**
     * Every keystroke re-runs the search, which is what makes the count
     * update as you type. `findAllAsync` is asynchronous by name and by
     * nature — the answer arrives on the find listener installed in [create],
     * so the counts are zeroed here rather than left showing the previous
     * query's totals for a frame or two.
     */
    fun setFindQuery(query: String) {
        val state = _findState.value ?: return
        if (state.query == query) return
        _findState.value = state.copy(query = query, activeMatch = 0, matchCount = 0)
        val web = views[state.tabId] ?: return
        // An empty query is a cleared bar, not a search for "" — WebView
        // matches nothing and reports nothing for it, so the highlight from
        // the last real query would simply stay on the page.
        if (query.isEmpty()) web.clearMatches() else web.findAllAsync(query)
    }

    /** Step to the next (or previous) match, wrapping — WebView's own. */
    fun findNext(forward: Boolean) {
        val state = _findState.value ?: return
        if (state.matchCount == 0) return
        views[state.tabId]?.findNext(forward)
    }

    /**
     * The bar goes when the page under it does. A find is against one
     * document: after a navigation the highlight is gone, the count is a
     * lie, and re-running the query against a page the user did not search
     * would scroll them somewhere they did not ask to go.
     */
    private fun endFindFor(tabId: Long) {
        if (_findState.value?.tabId == tabId) _findState.value = null
    }

    // -------------------------------------------------------------- toggles

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        // Keep the chrome's resolved state in step immediately rather than
        // waiting for the UI to report it back: page darkening in System mode
        // reads it, and a stale value there means a rebuild on the next frame.
        setEffectiveDark(chromeDark())
        markDirty()
    }

    /** The device's night setting -- what ThemeMode.System resolves to. */
    private fun systemNightMode(): Boolean =
        (getApplication<Application>().resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * What themeMode currently means for the chrome. The UI resolves the same
     * thing (with a live recomposition on the system setting, which this can't
     * see) and pushes it down through [setEffectiveDark]; this is for the
     * moments before that has happened -- startup, and the instant a mode is
     * picked.
     */
    private fun chromeDark(): Boolean = when (_themeMode.value) {
        ThemeMode.System -> systemNightMode()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    /**
     * Called from the UI layer with the app chrome's live resolved dark
     * state — only Compose knows what ThemeMode.System currently means. A
     * no-op when nothing actually changed, so recomposition churn doesn't
     * reload every WebView's darkening setting on every frame.
     */
    fun setEffectiveDark(dark: Boolean) {
        if (isDark == dark) return
        isDark = dark
        // Only matters while page darkening is following the app (System).
        syncPageDark()
    }

    fun setPageDarkMode(mode: PageDarkMode) {
        if (_pageDarkMode.value == mode) return
        _pageDarkMode.value = mode
        syncPageDark()
        markDirty()
    }

    /**
     * The menu tile: darkens the page in front of the user, or stops.
     *
     * It is an override of [pageDarkMode] for THIS tab and nothing more — it
     * does not write the setting, it does not touch the app's own theme, and
     * it is not persisted, so a relaunch is back to whatever Settings says.
     * That is the split the two controls are meant to have: Settings is where
     * "how should pages look" is answered, and this is one page being made
     * the other way for as long as the user is on it, like Desktop site or
     * zoom. Toggling back to what the setting already resolves to drops the
     * override rather than pinning the same value, so the tab rejoins the
     * setting instead of quietly ignoring the next change to it.
     */
    fun togglePageDark() {
        val id = _currentTabId.value
        if (id == 0L) return
        val toDark = !_pageDarkActive.value
        // The baseline this tab would fall back to, which is the site's own
        // answer where it has one — flipping back to what the site already
        // says drops the override rather than pinning the same value twice.
        val baseline = siteDarkFor(siteOfTab(id)) ?: resolvePageDark()
        if (toDark == baseline) pageDarkOverrides.remove(id)
        else pageDarkOverrides[id] = toDark
        if (_pageDarkActive.value == toDark) return
        _pageDarkActive.value = toDark
        rebuildForPageDark(setOf(id))
    }

    private fun resolvePageDark(): Boolean = when (_pageDarkMode.value) {
        PageDarkMode.System -> isDark
        PageDarkMode.Off -> false
        PageDarkMode.On -> true
    }

    /**
     * Pushes the resolved state out to every live WebView. A no-op when
     * nothing actually changed, so neither recomposition churn nor a repeated
     * System resolution re-registers scripts on every frame.
     */
    private fun syncPageDark() {
        val active = resolvePageDark()
        if (active == resolvedPageDark) return
        resolvedPageDark = active
        // The setting has spoken, so the tiles' passing overrides are spent.
        // A SITE's answer is not: it is a standing decision about that site,
        // the same kind of thing the setting itself is, and it goes on
        // outranking the setting exactly as it did before it changed.
        pageDarkOverrides.clear()
        _pageDarkActive.value = pageDarkFor(_currentTabId.value)
        // Not just applyDarkening over the live views: half of this setting
        // is baked into the context a WebView was built with, so they're
        // built again — which runs applyDarkening on each replacement anyway.
        // Only the views that disagree with it: one built dark under an
        // override the setting has now caught up with has nothing to redo.
        val stale = views.filter { (id, web) -> builtDark[web] != pageDarkFor(id) }.keys.toSet()
        if (stale.isNotEmpty()) rebuildForPageDark(stale)
    }

    /**
     * Rebuilds the live WebViews for a page dark mode flip, cross-fading the
     * one on screen.
     *
     * `prefers-color-scheme` comes from the theme of the context a WebView was
     * constructed with, so it can't be changed on a WebView that already
     * exists — not by a reload, not by touching its settings. Each one is
     * therefore parked (history and all), dropped, and built again.
     *
     * The tab on screen is the delicate one. Its replacement is added BENEATH
     * it and left to load there while the outgoing view carries on being the
     * page — untouched, live, and fully rendered — and only once the new one
     * has painted does the old fade away over it. So there is nothing to hide
     * and nothing to hide it with: no still image of the page is ever put on
     * screen, which is the point (a thumbnail is half-resolution at best, and
     * anything the software draw path produced has Chromium's composited
     * layers — vector art, transforms, sticky content — skewed or missing
     * outright). What the cross-fade blends is two live renderings of the same
     * page, one light and one dark.
     *
     * Beneath, specifically at the outgoing view's own index, so the container
     * keeps a WebView at child 0 throughout: that's both what the host's
     * update block checks before re-parenting and what SwipeRefreshLayout asks
     * whether it can scroll. Background tabs skip all of this — nothing of
     * them is on screen to protect, so they're simply parked and dropped, and
     * [webViewFor] builds them again when they're next needed.
     *
     * [ids] is which tabs are actually affected: every live view for a
     * setting change, one tab for the menu tile's own override.
     */
    private fun rebuildForPageDark(ids: Set<Long>) {
        // A flip arriving mid-fade ends the one in flight where it stands,
        // rather than leaving a stale view fading over a page it no longer
        // matches.
        finishPageDarkFade()
        // The cross-fade is its own transition, between two live views.
        ids.forEach(::dropHold)
        if (views.isEmpty()) return

        val currentId = _currentTabId.value
        views.keys.toList().filter { it != currentId && it in ids }.forEach(::parkAndDrop)

        if (currentId !in ids) return
        val old = views[currentId] ?: return
        val tab = _tabs.value.firstOrNull { it.id == currentId }
        val host = old.parent as? ViewGroup
        if (tab == null || host == null) {
            parkAndDrop(currentId)
            return
        }

        val index = host.indexOfChild(old)
        val params = old.layoutParams
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        val parked = Bundle()
        if (saveStateWithScroll(old, parked) != null) pendingRestores[currentId] = parked
        views.remove(currentId)
        val fresh = create(tab)
        views[currentId] = fresh
        host.addView(fresh, index, params)

        fadingOut = old
        pageDarkTransitioning = true
        // The outgoing view is on top and still live, so it would happily
        // scroll under a finger during the fade. Swallow its touches instead
        // of letting the user drag a page that's about to stop existing.
        old.setOnTouchListener { _, _ -> true }

        pageDarkJob = viewModelScope.launch {
            fun loading(list: List<Tab>) = list.firstOrNull { it.id == currentId }?.loading == true
            // The replacement's load hasn't necessarily started yet, so wait
            // for it to start before waiting for it to finish — otherwise
            // "not loading" matches immediately, on the blank frame.
            withTimeoutOrNull(PAGE_DARK_START_TIMEOUT_MS) { _tabs.first(::loading) }
            withTimeoutOrNull(PAGE_DARK_LOAD_TIMEOUT_MS) { _tabs.first { !loading(it) } }
            delay(PAGE_DARK_PAINT_MS)
            old.animate()
                .alpha(0f)
                .setDuration(PAGE_DARK_FADE_MS)
                // Compose's FastOutSlowIn, spelled out: the chrome's own turn
                // runs on that curve for the same duration, and the two are
                // meant to read as one movement rather than two things that
                // happen to start together.
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction { finishPageDarkFade() }
                .start()
        }
    }

    /**
     * A tab's parked state: its navigation history, plus where the page was
     * scrolled to.
     *
     * The scroll is ours to carry because `saveState` does not carry it —
     * it stopped storing "display data" years ago, and the current entry's
     * offset went with it. Without this a restored tab comes back at the TOP
     * of its page while the preview the switcher has been showing all along
     * is of the page where the user left it, so the moment the live page
     * takes over, the content jumps. The cover cannot help with that; it is
     * covering the right picture, and the page underneath arrives in the
     * wrong place.
     */
    private fun saveStateWithScroll(web: WebView, into: Bundle): Bundle? {
        web.saveState(into) ?: return null
        into.putInt(PARKED_SCROLL_Y, web.scrollY)
        return into
    }

    /**
     * Where a tab that is being restored has to end up, until it gets there.
     * Applied when the page has finished loading (it has no scroll range
     * before that), and once more after the frame that finished it is up —
     * a document is still growing at `onPageFinished`, and a scroll past the
     * end is silently clamped rather than refused.
     */
    private val pendingScroll = HashMap<Long, Int>()

    /** Parks a tab's navigation history and drops its view, off screen. */
    private fun parkAndDrop(id: Long) {
        val web = views.remove(id) ?: return
        val parked = Bundle()
        if (_tabs.value.any { it.id == id } && saveStateWithScroll(web, parked) != null) {
            pendingRestores[id] = parked
        }
        destroy(web)
    }

    /**
     * Ends a page dark cross-fade, from either side: the fade running out, or
     * something (another flip, the tab closing, the ViewModel going away)
     * cutting it short. Idempotent.
     */
    private fun finishPageDarkFade() {
        pageDarkJob?.cancel()
        pageDarkJob = null
        fadingOut?.let { web ->
            web.animate().cancel()
            web.setOnTouchListener(null)
            destroy(web)
        }
        fadingOut = null
        pageDarkTransitioning = false
    }

    /**
     * Two halves, both needed. The document-start script is what darkens
     * every FUTURE navigation (and every frame within it) before the page
     * paints, so there's no white flash; the direct evaluate is what darkens
     * the document already on screen, so the toggle doesn't need a reload.
     *
     * The WebView's own background follows too — that's the color behind the
     * document, visible while a page loads and past the end of a short one,
     * and it isn't touched by anything the page's stylesheet can say.
     */
    private fun applyDarkening(web: WebView, active: Boolean) {
        darkApplied[web] = active
        web.setBackgroundColor(if (active) PAGE_DARK_BACKDROP else android.graphics.Color.WHITE)
        // Explicitly off: with the context theme reporting dark, Chromium
        // would otherwise auto-darken every site that has no dark theme of
        // its own, on top of (and fighting with) the filter below.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            darkScripts.remove(web)?.remove()
            if (active) {
                darkScripts[web] = WebViewCompat.addDocumentStartJavaScript(
                    web,
                    PageDarkening.ENABLE,
                    setOf("*"),
                )
            }
        }
        web.evaluateJavascript(if (active) PageDarkening.ENABLE else PageDarkening.DISABLE, null)
    }

    /**
     * The menu's reader switch: shows the article over the page, or takes it
     * away again.
     *
     * Per tab and never persisted, for the same reason the page-dark tile is:
     * this is a thing done to the page in front of the user, not a preference
     * about how pages should look. It survives nothing — not a navigation
     * (the article is gone with the document), not a relaunch.
     *
     * Opening can fail: the harvest's verdict is a moment old, and a page
     * that had an article when it last reported may have replaced it since.
     * The state follows what the page actually did rather than what was
     * asked for, so the switch never sits on over an unchanged page.
     */
    fun toggleReaderMode() {
        val id = _currentTabId.value
        if (id == 0L) return
        val web = views[id] ?: return
        if (id in readerOpen) {
            ReaderMode.close(web)
            setReaderOpen(id, false)
            return
        }
        ReaderMode.open(
            web,
            settings = _readerSettings.value,
            dark = pageDarkFor(id),
            topInsetPx = pageTopContentPx,
            bottomInsetPx = pageChromeInsetPx,
        ) { opened ->
            setReaderOpen(id, opened)
            // A failed open is also the truest availability answer there is:
            // it was asked for the article and had none.
            if (!opened) setReaderAvailable(id, false)
        }
    }

    private fun setReaderAvailable(id: Long, value: Boolean) {
        if (readerAvailability[id] == value) return
        if (value) readerAvailability[id] = true else readerAvailability.remove(id)
        if (id == _currentTabId.value) _readerAvailable.value = value
    }

    private fun setReaderOpen(id: Long, value: Boolean) {
        val changed = if (value) readerOpen.add(id) else readerOpen.remove(id)
        if (!changed) return
        if (id == _currentTabId.value) {
            _readerActive.value = value
            // The indicator is the open reader's; a closed one has no position.
            if (!value) _readerProgress.value = null
        }
    }

    private fun forgetReader(id: Long) {
        readerAvailability.remove(id)
        readerOpen.remove(id)
        if (id == _currentTabId.value) {
            _readerAvailable.value = false
            _readerActive.value = false
            _readerProgress.value = null
        }
    }

    /**
     * The reader's text size, by the rung at [index] of [READER_TEXT_STEPS] —
     * an index rather than a percentage for the same reason [setZoomStep]
     * takes one: what the slider hands back can then never be a value the
     * ladder does not have.
     */
    fun setReaderTextStep(index: Int) =
        updateReaderSettings { it.copy(textScale = READER_TEXT_STEPS[index.coerceIn(0, READER_TEXT_STEPS.lastIndex)]) }

    fun setReaderTheme(theme: ReaderTheme) = updateReaderSettings { it.copy(theme = theme) }

    fun setReaderFont(font: ReaderFont) = updateReaderSettings { it.copy(font = font) }

    fun setReaderSpacing(spacing: ReaderSpacing) = updateReaderSettings { it.copy(spacing = spacing) }

    /**
     * One write for all four: the new settings are saved, and every reader
     * that is currently on screen is restyled in place — the settings pane is
     * a sheet over the article being read, so a size the user has to close a
     * sheet to see is a size they cannot choose.
     */
    private fun updateReaderSettings(edit: (ReaderSettings) -> ReaderSettings) {
        val next = edit(_readerSettings.value)
        if (next == _readerSettings.value) return
        _readerSettings.value = next
        readerOpen.forEach { id ->
            views[id]?.let { ReaderMode.restyle(it, next, dark = pageDarkFor(id)) }
        }
        markDirty()
    }

    /**
     * Desktop mode is one setting for the whole browser, so it is applied to
     * every live view rather than only the one on screen: a background tab
     * left on the mobile UA came back a mobile page under a switch that says
     * "Desktop", and stayed that way until something else reloaded it. Evicted
     * tabs need no help — [create] reads the flag when it rebuilds them.
     *
     * The reload is what makes it take: the UA and the client hints are read
     * when the request goes out, and the viewport script only runs at the next
     * document start.
     */
    fun toggleDesktopMode() {
        _desktopMode.update { !it }
        views.values.toList().forEach { web ->
            DesktopMode.apply(web, _desktopMode.value)
            web.reload()
        }
        markDirty()
    }

    fun toggleAdBlock() {
        _adBlockEnabled.update { !it }
        adBlocker.enabled = _adBlockEnabled.value
        currentView()?.reload()
        markDirty()
    }

    // ------------------------------------------------------- ad blocker list

    /**
     * Reads the saved feed selection and the merged list off disk, hands the
     * blocker its index, and -- if the list has gone stale and auto-update is
     * on -- refreshes it in the background. Called once, from `init`.
     */
    private fun loadBlocklist() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!ephemeral) blocklistStore.heal()
            val config = blocklistStore.loadConfig()
            _blocklistConfig.value = config
            _blocklistMeta.value = blocklistStore.loadMeta()
            rebuildBlocklistIndex(config)
            val age = System.currentTimeMillis() - _blocklistMeta.value.updatedAt
            // Two reasons to fetch, and the second is not covered by the
            // first. A first launch has nothing downloaded and is the one time
            // this matters most: without it the blocker runs on the bundled
            // seed list of twenty domains and looks broken. But a run that
            // left a timestamp behind WITHOUT leaving a list -- an interrupted
            // update, or a merged file produced by a build whose feeds are
            // gone -- looks perfectly fresh to the age test and would never be
            // retried, which is exactly the state that has the blocker
            // silently doing nothing. So the absence of a list is its own
            // trigger.
            // Never from an overlay: it reads the same files the browser
            // proper owns, and two processes' worth of sessions downloading
            // over each other is the one thing this directory has no writer
            // discipline for. An overlay still BLOCKS -- it just doesn't
            // maintain the list.
            val missing = !blocklistStore.hasDownloadedList()
            if (!ephemeral && config.autoUpdate && _adBlockEnabled.value &&
                (missing || age > BLOCKLIST_MAX_AGE_MS)
            ) {
                runBlocklistUpdate(config, reloadPage = false)
            }
        }
    }

    /**
     * IO. Rebuilds both halves from what is on disk: the hostname index for
     * [AdBlocker], and the cookie-banner stylesheet for [CosmeticFilters].
     */
    private fun rebuildBlocklistIndex(config: BlocklistStore.Config) {
        val index = blocklistStore.buildIndex(config)
        adBlocker.apply(index)
        // The tracker feed's own domains, on their own, for the sites that
        // have turned tracker blocking off. Built after the merged index and
        // never latched on: no page ever waits for this one, because a site
        // with no exception never reads it. See [AdBlocker.Rules].
        adBlocker.applyTrackers(blocklistStore.buildTrackerIndex())
        _blocklistRuleCount.value = index.size
        val rules = blocklistStore.loadCosmetic(config)
        cosmeticRules = rules
        _cosmeticRuleCount.value = rules.total
        // Live WebViews are holding the previous stylesheet as a registered
        // document-start script; it is replaced rather than added to.
        viewModelScope.launch(Dispatchers.Main) {
            views.forEach { (id, web) -> applyCosmetic(web, cookieBannersFor(id)) }
        }
    }

    /**
     * Registers (or clears) the generic cookie-banner stylesheet on one
     * WebView. Same shape as [applyDarkening]'s handling of its own script:
     * one handler per view, removed before a new one goes on, because
     * `addDocumentStartJavaScript` stacks rather than replaces.
     */
    private fun applyCosmetic(web: WebView, enabled: Boolean = true) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        cosmeticScripts.remove(web)?.remove()
        // A site the user wants to see banners on simply doesn't get the
        // stylesheet registered — see [setCosmeticEnabled] for the other half
        // of that, the document already on screen.
        if (!enabled) return
        val css = cosmeticRules.genericCss
        if (css.isBlank()) return
        cosmeticScripts[web] = WebViewCompat.addDocumentStartJavaScript(
            web,
            CosmeticFilters.documentStartScript(css),
            setOf("*"),
        )
    }

    /**
     * The site-specific half, for the page being navigated to. Injected on
     * page start rather than document start because it depends on the host,
     * which a script registered once per WebView cannot know.
     */
    private fun applyHostCosmetic(web: WebView, url: String) {
        val specific = cosmeticRules.specific
        if (specific.isEmpty()) return
        if (!settingsForSite(siteKeyOf(url)).hideCookieBanners) return
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return
        val selectors = CosmeticFilters.selectorsFor(specific, host)
        if (selectors.isEmpty()) return
        web.evaluateJavascript(CosmeticFilters.hostScript(CosmeticFilters.buildCss(selectors)), null)
    }

    /**
     * The CNAME tracker list. Like any feed this needs a download to take
     * effect, so the screen shows the difference until one runs.
     */
    fun toggleTrackerBlocking() {
        val config = _blocklistConfig.value
        saveBlocklistConfig(config.copy(blockTrackers = !config.blockTrackers))
    }

    /**
     * Cookie banners. Unlike the other two this has an immediate half -- the
     * bundled selectors need no download -- so the injected stylesheet is
     * rebuilt and the page reloaded right away, and only the downloaded
     * long tail waits for an update.
     */
    fun toggleCookieBanners() {
        val config = _blocklistConfig.value
        saveBlocklistConfig(config.copy(hideCookieBanners = !config.hideCookieBanners), rebuild = true)
    }

    fun addBlocklistFeedUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        // Anything that isn't an absolute http(s) URL would just fail at the
        // next update with a stack trace for a label; reject it here instead.
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return
        val config = _blocklistConfig.value
        if (trimmed in config.customFeeds) return
        saveBlocklistConfig(config.copy(customFeeds = config.customFeeds + trimmed))
    }

    /**
     * Takes a file the user picked and makes it one of their lists. The URI's
     * read grant is this process's, so the file is copied and parsed here and
     * now rather than remembered -- see [BlocklistStore.importList].
     *
     * The label is the picker's own display name where there is one: a
     * `content://` URI's path is an opaque document id on most providers and
     * naming a row after it tells the user nothing.
     */
    fun importBlocklistFile(uri: Uri) {
        _blocklistImportError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val label = displayNameOf(uri)
            val imported = runCatching {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Couldn't open that file")
                stream.use { blocklistStore.importList(label, it) }
            }
            val list = imported.getOrElse { error ->
                _blocklistImportError.value =
                    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                return@launch
            }
            val config = _blocklistConfig.value
            saveBlocklistConfig(
                config.copy(importedLists = config.importedLists + list),
                rebuild = true,
            )
        }
    }

    fun removeImportedBlocklist(id: String) {
        val config = _blocklistConfig.value
        val list = config.importedLists.firstOrNull { it.id == id } ?: return
        _blocklistImportError.value = null
        saveBlocklistConfig(config.copy(importedLists = config.importedLists - list), rebuild = true)
        viewModelScope.launch(Dispatchers.IO) { blocklistStore.deleteImportedList(id) }
    }

    /** The picker's display name for a URI, or its last path segment. */
    private fun displayNameOf(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Imported list"
    }

    fun removeBlocklistFeedUrl(url: String) {
        val config = _blocklistConfig.value
        saveBlocklistConfig(config.copy(customFeeds = config.customFeeds - url))
    }

    /**
     * The user's own rules, as typed. Unlike a feed this needs no download, so
     * the index is rebuilt straight away and the current page reloaded --
     * typing a domain in here should block it now, not after an update.
     */
    fun setBlocklistCustomEntries(text: String) {
        val config = _blocklistConfig.value
        if (text == config.customEntries) return
        saveBlocklistConfig(config.copy(customEntries = text), rebuild = true)
    }

    fun toggleBlocklistAutoUpdate() {
        val config = _blocklistConfig.value
        saveBlocklistConfig(config.copy(autoUpdate = !config.autoUpdate))
    }

    private fun saveBlocklistConfig(config: BlocklistStore.Config, rebuild: Boolean = false) {
        _blocklistConfig.value = config
        viewModelScope.launch(Dispatchers.IO) {
            blocklistStore.saveConfig(config)
            if (rebuild) {
                rebuildBlocklistIndex(config)
                withContext(Dispatchers.Main) { if (_adBlockEnabled.value) currentView()?.reload() }
            }
        }
    }

    /** Download every enabled feed now. Ignored while one is already running. */
    fun updateBlocklist() {
        if (_blocklistUpdating.value != null) return
        val config = _blocklistConfig.value
        viewModelScope.launch(Dispatchers.IO) { runBlocklistUpdate(config, reloadPage = true) }
    }

    private suspend fun runBlocklistUpdate(config: BlocklistStore.Config, reloadPage: Boolean) {
        _blocklistUpdating.value = BlocklistProgress(label = "", index = 0, total = 0)
        try {
            val meta = blocklistStore.update(config) { label, index, total ->
                _blocklistUpdating.value = BlocklistProgress(label, index, total)
            }
            _blocklistMeta.value = meta
            rebuildBlocklistIndex(config)
        } finally {
            _blocklistUpdating.value = null
        }
        // The page on screen was built with the old list; nothing already in
        // its DOM disappears until it is fetched again.
        if (reloadPage && _adBlockEnabled.value) {
            withContext(Dispatchers.Main) { currentView()?.reload() }
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        _searchEngine.value = engine
        markDirty()
    }

    /**
     * The whole arrangement in one call — order across both sections, and
     * which engines are in the disabled one — because the drag that produces
     * it changes both at once and two writes would publish a half-moved list.
     *
     * An arrangement that leaves nothing enabled is refused outright rather
     * than partially applied: there has to be somewhere for a typed query to
     * go. Disabling the engine that is currently the default hands the
     * default to the first one still standing, since "I don't use this one"
     * shouldn't depend on which row happens to carry the check mark.
     */
    fun setSearchEngineArrangement(order: List<String>, disabled: Set<String>) {
        if (order.all { it in disabled }) return
        _searchEngineOrder.value = order
        _disabledSearchEngines.value = disabled
        if (_searchEngine.value.id in disabled) {
            _searchEngine.value = enabledSearchEngines.value.first()
        }
        // Written through immediately rather than on the 500ms debounce.
        // Arranging this list is rare, deliberate, and several drags long,
        // and a session that ends before the debounce fires (a kill, a
        // reinstall) loses the whole arrangement while every earlier one
        // survives — which reads as the list forgetting at random. The write
        // itself is a SharedPreferences `apply()`, so it costs no more here
        // than it does there.
        persistNow()
    }

    /** Returns false when the name/URL pair isn't a usable engine. */
    fun addCustomSearchEngine(label: String, url: String): Boolean {
        val engine = SearchEngine.custom(label, url) ?: return false
        // Named in the order explicitly, at the END of what is on offer.
        // Without that it would be placed by orderEngines' rule for engines
        // the order doesn't mention — which exists for a built-in a later
        // build adds, and puts it among its neighbours rather than last.
        // A newly added engine belongs at the bottom of the list the user
        // just added it to.
        _searchEngineOrder.value = allSearchEngines.value.map { it.id } + engine.id
        _customSearchEngines.update { it + engine }
        persistNow()
        return true
    }

    fun removeCustomSearchEngine(engine: SearchEngine) {
        _customSearchEngines.update { list -> list.filterNot { it.id == engine.id } }
        _searchEngineOrder.update { order -> order.filterNot { it == engine.id } }
        // Its disabled flag goes with it, or a later engine that happened to
        // reuse the id would arrive switched off.
        _disabledSearchEngines.update { it - engine.id }
        if (_searchEngine.value.id == engine.id) {
            _searchEngine.value = enabledSearchEngines.value.first()
        }
        persistNow()
    }

    fun toggleSearchSuggestions() {
        _searchSuggestionsEnabled.update { !it }
        markDirty()
    }

    /**
     * An accent is a colour, and under most looks here it simply lands: the
     * TUI's and Nothing's palettes take the pick through their accent roles
     * and keep their own greys, so a swatch tapped under either of them is a
     * swatch that shows up.
     *
     * [SpecialTheme.Ninety8] is the one that cannot. Its palette is sixteen
     * fixed values an operating system was drawn from — a navy title bar
     * over a silver face — and there is no role in it a chosen colour could
     * occupy without being a different operating system. So a colour tapped
     * while 98 is on means the only thing it can mean: the app's own look,
     * wearing that colour. The look goes, the pick stays.
     */
    fun setAccentTheme(theme: AccentTheme) {
        _accentTheme.value = theme
        if (_specialTheme.value == SpecialTheme.Ninety8) _specialTheme.value = SpecialTheme.Default
        markDirty()
    }

    fun setSpecialTheme(theme: SpecialTheme) {
        _specialTheme.value = theme
        markDirty()
    }

    fun toggleAutoFocusNewTabKeyboard() {
        _autoFocusNewTabKeyboard.update { !it }
        markDirty()
    }

    /**
     * "New tab" / "Private tab" from the launcher's long press. The space is
     * switched first so the sheet that opens belongs to it — including the
     * private button's own state and the violet chrome behind it — and then
     * the sheet is asked for rather than a tab being created: an empty tab
     * with nothing in it is what the sheet is FOR, and the app already lands
     * there whenever a space is empty. Nothing is persisted, so a relaunch
     * that isn't from the shortcut starts wherever Settings says.
     */
    fun requestNewTabSheet(private: Boolean) {
        setPrivateMode(private)
        _newTabSheetRequests.update { it + 1 }
    }

    /**
     * Answered — the sheet is up. Cleared rather than left standing so an
     * Activity RECREATE, which keeps this ViewModel and recomposes
     * BrowserScreen from scratch, doesn't read a request from an hour ago
     * and raise the sheet over whatever the user was doing.
     */
    fun newTabSheetRequestHandled() {
        _newTabSheetRequests.value = 0
    }

    fun toggleOpenNewTabSheetOnLaunch() {
        _openNewTabSheetOnLaunch.update { !it }
        markDirty()
    }

    fun setNewTabHistorySort(sort: HistorySort) {
        _newTabHistorySort.value = sort
        markDirty()
    }

    fun setTabManagerMode(mode: TabManagerMode) {
        _tabManagerMode.value = mode
        markDirty()
    }

    fun toggleLinkStripper() {
        _linkStripperEnabled.update { !it }
        markDirty()
    }

    fun toggleOpenExternalLinksInOverlay() {
        _openExternalLinksInOverlay.update { !it }
        markDirty()
    }

    fun toggleOpenLinksInApps() {
        _openLinksInApps.update { !it }
        markDirty()
    }

    fun togglePullToRefresh() {
        _pullToRefreshEnabled.update { !it }
        markDirty()
    }

    fun togglePageLens() {
        _pageLens.update { !it }
        markDirty()
    }

    fun toggleTranslucentSheets() {
        _translucentSheets.update { !it }
        markDirty()
    }

    fun setTranslucency(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (next == _translucency.value) return
        _translucency.value = next
        markDirty()
    }

    fun toggleLinkPreview() {
        _linkPreviewEnabled.update { !it }
        // Whichever surface is up was raised by the rule that just changed —
        // a preview left on screen after the switch is off is a card the
        // setting says should not exist.
        _linkPreview.value?.let { closeLinkPreview() }
        _contextTarget.value = null
        markDirty()
    }

    fun setNewTabPlacement(placement: NewTabPlacement) {
        _newTabPlacement.value = placement
        markDirty()
    }

    fun toggleDoubleTapTabsSwitchesTab() {
        _doubleTapTabsSwitchesTab.update { !it }
        markDirty()
    }

    fun toggleSwipeToSwitchTabs() {
        _swipeToSwitchTabs.update { !it }
        markDirty()
    }

    fun toggleFlickToCloseTab() {
        _flickToCloseTab.update { !it }
        markDirty()
    }

    fun toggleBookmark(tab: Tab) {
        _bookmarks.update { list ->
            if (list.any { it.url == tab.url }) {
                list.filterNot { it.url == tab.url }
            } else {
                list + BookmarkEntry(url = tab.url, title = tab.label, host = tab.host)
            }
        }
        markDirty()
        // A bookmark is the one thing an ephemeral session is allowed to
        // keep — the user asked for it to outlast the page they are on, which
        // is the whole meaning of the word. It cannot go through
        // [persistNow], which would write this session's empty tab list over
        // the real one, so it is a read-modify-write of that one list.
        if (ephemeral) {
            val bookmarks = _bookmarks.value
            viewModelScope.launch(Dispatchers.IO) { store.updateBookmarks { bookmarks } }
        }
    }

    /**
     * Re-reads the bookmarks from disk. For the browser proper on its way back
     * to the foreground: an overlay may have added one while it was away, and
     * this session's next save would otherwise write its own older list over
     * it. Everything else in the blob is this session's to own, so only this
     * one list is taken back.
     */
    fun reloadBookmarks() {
        if (ephemeral) return
        viewModelScope.launch(Dispatchers.IO) {
            val saved = store.load()?.bookmarks ?: return@launch
            withContext(Dispatchers.Main) {
                if (saved != _bookmarks.value) _bookmarks.value = saved
            }
        }
    }

    fun removeBookmark(url: String) {
        _bookmarks.update { list -> list.filterNot { it.url == url } }
        markDirty()
    }

    /**
     * Drops a single visited page — the swipe-to-remove on the New Tab sheet's
     * list. Takes its visit tally with it: a row swiped away is meant to be
     * gone, and leaving the count behind would have it reappear under "Most
     * visited" (ranked, no less) on the next open.
     */
    fun removeHistory(url: String) {
        _history.update { list -> list.filterNot { it.url == url } }
        val key = visitKey(url)
        _visits.update { it - key }
        // Nor may a visit to it still waiting out its dwell bring it back.
        pendingVisits.filterValues { visitKey(it.url) == key }.keys.forEach(::dropVisit)
        markDirty()
    }

    /**
     * Drops the whole list — the History screen's "Clear all". Takes the visit
     * tallies with it for the same reason [removeHistory] does: a tally that
     * outlives its row comes back ranked under "Most visited", which is the
     * one place a cleared page would still be on screen.
     */
    fun clearHistory() {
        _history.value = emptyList()
        _visits.value = emptyMap()
        pendingVisits.keys.toList().forEach(::dropVisit)
        markDirty()
    }

    // ------------------------------------------------------------ passwords

    fun toggleSavePasswords() {
        _savePasswordsEnabled.update { !it }
        // Off means this browser has nothing to do with passwords, the system
        // included: there is no way to tell an autofill service "fill but
        // never offer to save", so the honest reading of the switch is to
        // stop handing it the fields at all.
        views.values.forEach(::applyAutofillImportance)
        // A prompt already on screen was raised under the old answer; leaving
        // it up after the user just switched saving off would be the browser
        // arguing with them.
        if (!_savePasswordsEnabled.value) {
            _passwordPrompt.value = null
            pendingCaptures.clear()
        }
        markDirty()
    }

    fun setExternalPasswordManager(external: Boolean) {
        if (_externalPasswordManager.value == external) return
        _externalPasswordManager.value = external
        // Every tab, not just the current one: a background tab's form would
        // otherwise keep whichever arrangement it was created under.
        views.values.forEach(::applyAutofillImportance)
        // Handing over means dropping whatever this browser was mid-way
        // through offering — a save card or a fill bar left standing would be
        // the browser doing the job it just handed away.
        if (!ownPasswordsActive) {
            _passwordPrompt.value = null
            _passwordSuggestion.value = null
            _autofillSuggestion.value = null
            pendingCaptures.clear()
            captureTimers.values.forEach { it.cancel() }
            captureTimers.clear()
        }
        markDirty()
    }

    /**
     * Switches the page's fields in or out of the platform autofill service's
     * view. `NO_EXCLUDE_DESCENDANTS` is the whole mechanism: a WebView's
     * fields are a *virtual* structure it provides on request, so declining to
     * be asked is declining the feature — no session is opened, and the
     * keyboard has nothing to put in its strip. `AUTO` is not "on" so much as
     * "as usual", which is the honest thing for a flag that only ever removes
     * something the system would otherwise do.
     *
     * Note what this cannot be narrower than: autofill has no per-field-type
     * switch reachable from the app, so withholding the fields withholds the
     * user's saved addresses and cards from the keyboard too, not only their
     * logins. That is the price of the browser keeping its own vault, and it
     * is why the default is Google.
     */
    private fun applyAutofillImportance(web: WebView) {
        val systemHandlesIt = _savePasswordsEnabled.value && _externalPasswordManager.value
        web.importantForAutofill = if (systemHandlesIt) {
            View.IMPORTANT_FOR_AUTOFILL_AUTO
        } else {
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }

    fun removeSavedPassword(host: String, username: String) {
        passwordStore.remove(host, username)
        refreshSuggestionForCurrentTab()
    }

    fun clearSavedPasswords() {
        passwordStore.clearAll()
        _passwordSuggestion.value = null
    }

    fun allowSavingForHost(host: String) = passwordStore.allowSaving(host)

    /** The vault's own view of a site, for the Saved passwords screen. */
    fun savedPasswordsFor(host: String): List<SavedPassword> = passwordStore.forHost(host)

    /**
     * Wires one tab's WebView into [PasswordForms]. Everything below is per
     * tab: a background tab's login form is still its own, and a capture from
     * one page must never be attributed to whatever the user switched to.
     */
    private fun attachPasswordForms(web: WebView, tabId: Long) {
        PasswordForms.attach(web, object : PasswordForms.Listener {
            override fun onCaptured(username: String, password: String) {
                noteCapturedCredential(tabId, username, password)
            }

            override fun onLoginFormPresent(present: Boolean) {
                loginFormPresent[tabId] = present
                if (!present && _passwordSuggestion.value?.tabId == tabId) {
                    // The form went away — a successful sign-in, a route
                    // change — and the bar is offering to fill nothing.
                    _passwordSuggestion.value = null
                }
            }

            override fun onFieldFocus(field: PasswordForms.Field?) {
                if (field == null) {
                    if (_passwordSuggestion.value?.tabId == tabId) _passwordSuggestion.value = null
                } else {
                    offerSuggestion(tabId)
                }
            }
        })
    }

    /**
     * A submission carried a credential. It is NOT prompted for here: the
     * page has only said the user pressed the button, not that the site
     * accepted it, and a prompt fired the instant a password is typed into a
     * form is a prompt that fires on typos too.
     *
     * So it is parked, and raised on the next thing that happens to this tab
     * — a navigation ([flushPendingCapture], called when the next page starts
     * loading), which is what an ordinary login does — with a timer behind it
     * for the single-page apps that sign in without one. The timer is the
     * fallback, not the path.
     */
    private fun noteCapturedCredential(tabId: Long, username: String, password: String) {
        if (!_savePasswordsEnabled.value || !ownPasswordsActive) return
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        // Nothing from the private space is written down, which is the whole
        // arrangement — and a save prompt there would be an offer to break it.
        if (tab.isPrivate) return
        val host = tab.host
        if (host.isBlank() || passwordStore.isNeverSaved(host)) return
        val change = passwordStore.classify(host, username, password)
        if (change == PasswordStore.Change.Unchanged) return
        val prompt = PasswordPrompt(
            tabId = tabId,
            host = host,
            username = username,
            password = password,
            update = change == PasswordStore.Change.Updated,
        )
        pendingCaptures[tabId] = prompt
        captureTimers.remove(tabId)?.cancel()
        captureTimers[tabId] = viewModelScope.launch {
            delay(CAPTURE_SETTLE_MS)
            flushPendingCapture(tabId)
        }
    }

    /**
     * Raises a parked capture as a prompt, if there is one. Called from the
     * WebViewClient when the tab commits its next page, and from the timer
     * above.
     */
    private fun flushPendingCapture(tabId: Long) {
        captureTimers.remove(tabId)?.cancel()
        val prompt = pendingCaptures.remove(tabId) ?: return
        if (!_savePasswordsEnabled.value || !ownPasswordsActive) return
        if (passwordStore.isNeverSaved(prompt.host)) return
        // Still worth asking? The user may have saved it from another tab, or
        // the same password may have been captured twice.
        if (passwordStore.classify(prompt.host, prompt.username, prompt.password) ==
            PasswordStore.Change.Unchanged
        ) return
        // A prompt already up wins: it is the one the user is looking at.
        if (_passwordPrompt.value == null) _passwordPrompt.value = prompt
    }

    fun dismissPasswordPrompt() {
        _passwordPrompt.value = null
    }

    /** Keeps the credential the prompt is showing. */
    fun acceptPasswordPrompt() {
        val prompt = _passwordPrompt.value ?: return
        passwordStore.save(prompt.host, prompt.username, prompt.password)
        _passwordPrompt.value = null
        refreshSuggestionForCurrentTab()
    }

    /** "Never" on the prompt: don't ask for this site again, and forget it. */
    fun neverSaveForHost(host: String) {
        passwordStore.neverSave(host)
        _passwordPrompt.value = null
        _passwordSuggestion.value = null
    }

    /**
     * Puts a saved login into the page. The only path that ever moves a
     * password from the vault into a document, and it is only ever reached
     * from something the user tapped — [PasswordForms]' bridge cannot ask for
     * this, so a page cannot help itself to what is stored.
     */
    fun fillPassword(tabId: Long, username: String, password: String) {
        val web = views[tabId] ?: return
        web.evaluateJavascript(PasswordForms.fillScript(username, password), null)
        if (_passwordSuggestion.value?.tabId == tabId) _passwordSuggestion.value = null
    }

    fun fillPasswordInCurrentTab(username: String, password: String) =
        fillPassword(_currentTabId.value, username, password)

    fun dismissPasswordSuggestion() {
        _passwordSuggestion.value = null
    }

    /**
     * Builds the suggestion bar for a tab whose login form just took focus.
     * Offered in the private space too: filling a password there reads
     * nothing out of that session and writes nothing into it, and refusing to
     * fill would only mean the user typing the same secret by hand.
     *
     * Nothing at all while Google has the job — the keyboard is offering the
     * user's logins there, and a second bar over it offering a vault that is
     * no longer being added to would be two answers to one question.
     */
    private fun offerSuggestion(tabId: Long) {
        if (tabId != _currentTabId.value || !ownPasswordsActive) return
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val matches = passwordStore.forHost(tab.host)
        // Nothing saved for this site is nothing to put on screen: a bar that
        // only ever says "no passwords" is a bar in the way of the keyboard.
        // The system's own suggestions are still there either way.
        if (matches.isEmpty()) {
            _passwordSuggestion.value = null
            return
        }
        _passwordSuggestion.value = PasswordSuggestion(
            tabId = tabId,
            host = tab.host,
            matches = matches,
        )
    }

    private fun refreshSuggestionForCurrentTab() {
        val current = _passwordSuggestion.value ?: return
        val matches = passwordStore.forHost(current.host)
        _passwordSuggestion.value = if (matches.isEmpty()) null else current.copy(matches = matches)
    }

    /** Everything a tab's password state is, thrown away when it navigates. */
    private fun resetPasswordPageState(tabId: Long) {
        loginFormPresent.remove(tabId)
        if (_passwordSuggestion.value?.tabId == tabId) _passwordSuggestion.value = null
        if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
    }

    // -------------------------------------------------- addresses and cards

    fun toggleFillAddresses() {
        _fillAddresses.update { !it }
        if (!_fillAddresses.value && _autofillSuggestion.value?.group == FormFields.Group.Address) {
            _autofillSuggestion.value = null
        }
        markDirty()
    }

    fun toggleFillPaymentMethods() {
        _fillPaymentMethods.update { !it }
        if (!_fillPaymentMethods.value && _autofillSuggestion.value?.group == FormFields.Group.Card) {
            _autofillSuggestion.value = null
        }
        markDirty()
    }

    fun saveAddress(address: SavedAddress) = passwordStore.saveAddress(address)

    fun removeAddress(id: String) {
        passwordStore.removeAddress(id)
        refreshAutofillSuggestion()
    }

    fun clearAddresses() {
        passwordStore.clearAddresses()
        refreshAutofillSuggestion()
    }

    fun saveCard(card: SavedCard) = passwordStore.saveCard(card)

    fun removeCard(id: String) {
        passwordStore.removeCard(id)
        refreshAutofillSuggestion()
    }

    fun clearCards() {
        passwordStore.clearCards()
        refreshAutofillSuggestion()
    }

    /**
     * Wires one tab's WebView into [FormFields]. Per tab like the password
     * half, and for the same reason: a background tab's checkout is its own.
     */
    private fun attachFormFields(web: WebView, tabId: Long) {
        FormFields.attach(web, object : FormFields.Listener {
            override fun onFieldFocus(group: FormFields.Group?) {
                if (group == null) {
                    if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
                } else {
                    offerAutofill(tabId, group)
                }
            }
        })
    }

    /**
     * Builds the fill bar for a checkout field that just took focus.
     *
     * Nothing at all while the platform's own service has the job — the
     * keyboard is offering the user's addresses there, and a bar over it
     * offering a vault this browser is not adding to would be two answers to
     * one question. Nothing either where the vault has nothing of that kind:
     * a bar that only ever says "no addresses" is a bar in the way of the
     * keyboard.
     *
     * Offered in the private space, like the password bar and for its reason:
     * filling reads nothing out of that session and writes nothing into it,
     * and refusing would only mean the user typing the same details by hand.
     */
    private fun offerAutofill(tabId: Long, group: FormFields.Group) {
        if (tabId != _currentTabId.value || !ownPasswordsActive) return
        // A password prompt or fill bar is already answering for this page.
        if (_passwordPrompt.value != null || _passwordSuggestion.value != null) return
        when (group) {
            FormFields.Group.Address -> {
                val addresses = passwordStore.addresses.value
                if (!_fillAddresses.value || addresses.isEmpty()) {
                    if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
                    return
                }
                _autofillSuggestion.value = AutofillSuggestion(tabId, group, addresses, emptyList())
            }

            FormFields.Group.Card -> {
                val cards = passwordStore.cards.value
                if (!_fillPaymentMethods.value || cards.isEmpty()) {
                    if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
                    return
                }
                _autofillSuggestion.value = AutofillSuggestion(tabId, group, emptyList(), cards)
            }
        }
    }

    private fun refreshAutofillSuggestion() {
        val current = _autofillSuggestion.value ?: return
        val next = current.copy(
            addresses = current.addresses.filter { a -> passwordStore.addresses.value.any { it.id == a.id } },
            cards = current.cards.filter { c -> passwordStore.cards.value.any { it.id == c.id } },
        )
        _autofillSuggestion.value =
            if (next.addresses.isEmpty() && next.cards.isEmpty()) null else next
    }

    /**
     * Puts a saved address or card into the page. The only path that moves
     * either out of the vault and into a document, and it is only ever
     * reached from a chip the user tapped — [FormFields]' bridge cannot ask
     * for this, so a page cannot help itself to what is stored.
     */
    fun fillAddress(tabId: Long, address: SavedAddress) {
        val web = views[tabId] ?: return
        web.evaluateJavascript(FormFields.fillAddressScript(address), null)
        if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
    }

    fun fillCard(tabId: Long, card: SavedCard) {
        val web = views[tabId] ?: return
        web.evaluateJavascript(FormFields.fillCardScript(card), null)
        if (_autofillSuggestion.value?.tabId == tabId) _autofillSuggestion.value = null
    }

    fun dismissAutofillSuggestion() {
        _autofillSuggestion.value = null
    }

    /** The same, plus anything parked, for a tab that is going away. */
    private fun forgetPasswordState(tabId: Long) {
        resetPasswordPageState(tabId)
        captureTimers.remove(tabId)?.cancel()
        pendingCaptures.remove(tabId)
        if (_passwordPrompt.value?.tabId == tabId) _passwordPrompt.value = null
    }

    // ----------------------------------------------------------- thumbnails

    /** Refreshes every live tab's preview, so backgrounded tabs aren't stale in the switcher. */
    fun captureAllThumbnails() {
        views.keys.toList().forEach { captureThumbnail(it) }
    }

    /**
     * Marks a tab's rendered content as having moved on (scrolled, navigated,
     * finished loading) so the previews below know their last pixel-exact copy
     * is out of date, and queues a fresh one for once the page settles.
     */
    fun notePageMoved(id: Long) {
        contentChangedAt[id] = SystemClock.elapsedRealtime()
        idleCapture.tryEmit(id)
    }

    /**
     * Asks for a preview of [id] once it settles, without claiming its content
     * changed — a no-op when the card already holds an exact copy of the page.
     * Called when the page comes back to the screen, which is the only moment
     * a tab that never got one (restored, or loaded in the background) can.
     */
    fun queueCapture(id: Long) {
        idleCapture.tryEmit(id)
    }

    /**
     * Whether the live page is, right now, the thing filling the screen —
     * nothing shrunk, no sheet, no cover, no full-screen destination over it.
     * [captureThumbnail] can only take a true copy of the screen while this is
     * true; pushed down from the UI, which is the only layer that knows.
     */
    /**
     * Suspends until [id]'s WebView has actually drawn a frame, or [timeoutMs]
     * passes — the honest end of a cover held over a tab that is being swapped
     * in.
     *
     * Switching tabs REPARENTS the WebView (see WebViewHost), so the incoming
     * one has been detached since it left the screen and Chromium has to
     * re-attach and raster it from nothing. How long that takes is a property
     * of the page, not a number of frames: covering it for a fixed count is
     * either too long on a trivial page or, on a heavy one, uncovers a white
     * screen. `postVisualStateCallback` is the platform's own answer — it
     * fires once the DOM as it stood at the request has been rendered.
     *
     * The timeout is not optional: the callback needs an attached, drawing
     * view, and if the swap is interrupted (the tab closed, the view evicted)
     * nothing would ever call it back and the cover would freeze the app.
     */
    suspend fun awaitPagePainted(id: Long, timeoutMs: Long) {
        val web = views[id] ?: return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                web.postVisualStateCallback(
                    id,
                    // An abstract class, not a functional interface — no SAM
                    // conversion from a lambda here.
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    },
                )
            }
        }
    }

    fun setPageExposed(exposed: Boolean) {
        pageExposed = exposed
    }

    /** The hosting Activity came to the front or left it. A visit's dwell only runs in front. */
    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        if (resumed) _visitClock.value = System.currentTimeMillis()
        // The user may be back from system settings with notifications
        // switched on or off; a no-op per view when nothing changed.
        if (resumed) refreshNotificationScripts()
        syncVisitClock()
    }

    fun setPageShowing(showing: Boolean) {
        val was = pageShowing
        pageShowing = showing
        syncVisitClock()
        // Coming back to the page ENDS whatever was being kept quiet for the
        // tab on it (see pageOnScreen): a background load the user has just
        // opened into is an ordinary load from here on, and the tab has been
        // ignoring its own progress and title up to now. The view is asked
        // what it is actually doing rather than waiting for the next
        // callback, which for a page that has all but arrived may never come.
        if (showing && !was) syncCurrentTabToItsView()
    }

    /**
     * Brings the current tab's name back in line with its live view. The
     * title is the one thing a background load leaves behind — everything
     * else is written as it happens, whether or not anyone is looking.
     */
    private fun syncCurrentTabToItsView() {
        val id = _currentTabId.value
        val web = views[id] ?: return
        val title = web.title.orEmpty()
        if (title.isBlank()) return
        update(id) { it.copy(title = title) }
    }

    /**
     * How far the live WebView hangs below the page's box, i.e. the strip of
     * it that renders under the toolbar (see pageOverflow in BrowserScreen).
     * It is page, but page nobody is looking at, and the toolbar is drawn
     * over it — so every capture below stops short of it. That keeps a
     * preview the shape of the card it goes into, and keeps the toolbar's own
     * pixels out of a picture that is meant to be the page.
     */
    fun setPageOverflow(px: Int) {
        pageOverflowPx = px
    }

    private var pageOverflowPx = 0

    /**
     * How much of the page's bottom edge the toolbar is currently COVERING —
     * the overflow above, but only while the bar is actually up.
     *
     * Pushed into every live page so it can move its own bottom-anchored
     * controls out from under the bar (see [PageBottomBar]); read back through
     * the bridge by each new document, so a navigation doesn't land a page
     * under the toolbar until the next change.
     */
    fun setPageChromeInset(px: Int) {
        if (pageChromeInsetPx == px) return
        pageChromeInsetPx = px
        views.values.forEach {
            PageBottomBar.setInset(it, px)
            ReaderMode.setInsets(it, pageTopContentPx, px)
        }
    }

    private var pageChromeInsetPx = 0

    /**
     * How much of the page's TOP edge the browser is covering — the overlay's
     * header, which is drawn over the page rather than above it (see
     * [PageTopInset] for why it has to be).
     *
     * Two numbers, because a page has two ways of putting something at its
     * top and they need different answers: [contentPx] is room at the
     * document's start and is constant, [barPx] moves whatever the page has
     * fixed to the viewport and is only in force while the header is all the
     * way up.
     */
    fun setPageTopInset(contentPx: Int, barPx: Int) {
        if (pageTopContentPx == contentPx && pageTopBarPx == barPx) return
        pageTopContentPx = contentPx
        pageTopBarPx = barPx
        views.values.forEach {
            PageTopInset.setInset(it, contentPx, barPx)
            ReaderMode.setInsets(it, contentPx, pageChromeInsetPx)
        }
    }

    private var pageTopContentPx = 0
    private var pageTopBarPx = 0

    /**
     * How far the live WebView hangs ABOVE the page's box — the page lens's
     * overscan (`ui/PageLens.kt`), under the black status bar, so the lens's
     * curve reads real page there. The document is padded down by the same
     * amount (so nothing moves when it is turned on) and every capture starts
     * below it, like the strip under the toolbar at the other end.
     */
    fun setPageTopOverscan(px: Int) {
        if (pageTopOverscanPx == px) return
        pageTopOverscanPx = px
        setPageTopInset(px, px)
    }

    private var pageTopOverscanPx = 0

    /**
     * Nonzero while the page lens is on: every move a bar or the document's
     * start is given — which under the lens is its overscan, hidden under the
     * status bar and the toolbar — is painted in that bar's own colour (see
     * [PageTopInset.setFill], [PageBottomBar.setFill]). Nothing sits any
     * further from the edge than it would without the lens; the paint is for
     * the curve, which reads page out of that hidden strip and would otherwise
     * pull whatever is scrolling behind a header in above it.
     */
    fun setPageBarFill(topPx: Int, bottomPx: Int) {
        if (pageTopFillPx != topPx) {
            pageTopFillPx = topPx
            views.values.forEach { PageTopInset.setFill(it, topPx) }
        }
        if (pageBarFillPx != bottomPx) {
            pageBarFillPx = bottomPx
            views.values.forEach { PageBottomBar.setFill(it, bottomPx) }
        }
    }

    // Split: the top one also carries the status bar strip's header fill,
    // which is nothing to do with the bottom edge.
    private var pageTopFillPx = 0
    private var pageBarFillPx = 0

    /**
     * The status bar strip the page is laid out under while the page lens is
     * off (see [PageTopInset.setStrip]); 0 turns it off.
     */
    fun setPageTopStrip(px: Int, fadePx: Int) {
        if (pageTopStripPx == px && pageTopStripFadePx == fadePx) return
        pageTopStripPx = px
        pageTopStripFadePx = fadePx
        views.values.forEach { PageTopInset.setStrip(it, px, fadePx) }
    }

    private var pageTopStripPx = 0
    private var pageTopStripFadePx = 0

    /**
     * Run over every exact capture (a copy of the screen) before it becomes a
     * tab's preview, to take out an effect the live view is drawn with — the
     * page lens (`ui/PageLens.kt`), which the UI installs. Arguments are the
     * view, the copy, the copy's downscale factor, and the view row the copy's
     * first row was taken from.
     */
    var captureUnwarp: ((WebView, Bitmap, Int, Int) -> Unit)? = null

    /**
     * Capture BEFORE animating into the switcher, or the card animates in blank.
     *
     * Two ways to get a picture of a tab, in order of fidelity:
     *
     * 1. [captureExact] — a [PixelCopy] out of the window's own surface, i.e.
     *    literally the pixels the user is looking at. Only possible for the
     *    tab that's actually on screen and unobscured, and it lands a frame or
     *    two later rather than immediately.
     * 2. [drawThumbnail] — `View.draw()` onto a software Canvas. Always
     *    available, but it asks Chromium to re-render the page through a path
     *    it never uses for display, and anything living in its own compositor
     *    layer (transformed/fixed elements, SVG and other vector content,
     *    filters, video) can come back missing, mis-scaled or in the wrong
     *    place. This is the "wrong size/place" the previews had.
     *
     * A copy is preferred whenever one is possible, and an existing copy is
     * never replaced by a software draw unless the page has actually moved on
     * since — which is what keeps a backgrounded tab showing the exact picture
     * taken while it was last on screen.
     */
    fun captureThumbnail(id: Long, allowExact: Boolean = true) {
        // An ephemeral session has no switcher and no card for a preview to
        // go on, and nowhere to write one — so the copy is pure cost.
        if (ephemeral) return
        // Mid page-dark cross-fade the screen holds two renderings of the
        // page blended together, which is a picture of neither.
        if (pageDarkTransitioning && id == _currentTabId.value) return
        // Under a navigation hold the screen is the page that LEFT; the one
        // arriving is queued a capture when the hold comes down.
        if (id in activeHolds) return
        val web = views[id] ?: return
        if (web.width == 0 || web.height == 0) return
        val hasThumbnail = _tabs.value.firstOrNull { it.id == id }?.thumbnail != null
        // A true copy always wins, and is always worth retaking: a page can
        // repaint itself with nothing to announce it (a clock, a feed, any
        // running animation), so "nothing changed since the last capture" is
        // never something this can assume about the tab on screen.
        // Every other way into a copy — the toolbar's pointer-down, a swipe
        // between tabs, the way out of the app — can land while a bar the last
        // scroll awakened is still fading. The copy is a frame or two out, so
        // taking the bar off here is usually enough to keep it off the card;
        // the idle path above additionally waits for it.
        scrollBarGates[web]?.retract()
        if (allowExact && captureExact(id, web)) {
            // The copy is a couple of frames out; only pay for a software draw
            // meanwhile when there's nothing at all on the card to show.
            if (!hasThumbnail) drawThumbnail(id, web)
            return
        }
        // No copy possible — a backgrounded tab, or the page already shrinking
        // into the switcher. A copy taken while it WAS on screen still beats
        // anything the software path can produce, so it's only replaced once
        // the page has actually moved on underneath it.
        if (hasThumbnail && (exactCaptureAt[id] ?: 0L) > (contentChangedAt[id] ?: 0L)) return
        drawThumbnail(id, web)
    }

    /**
     * Asks the compositor for the exact pixels of [web]'s slice of the window,
     * returning whether the request was actually issued. The result lands on
     * the main thread a frame or two later — by which time the switcher may
     * already be animating, which is fine: the copy is of the frame that was
     * up when it was asked for, and [pageExposed] was checked then.
     */
    private fun captureExact(id: Long, web: WebView): Boolean {
        if (!pageExposed || id != _currentTabId.value) return false
        if (!web.isAttachedToWindow || !web.isShown) return false
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // From the top of the status bar strip, not the page box, when
            // the page is under one: the strip is split off as its own
            // picture (Tab.thumbnailTop) and the rest is exactly as before.
            val stripRows = pageTopStripPx / FACTOR
            val grab = captureHardwarePage(web, pageTopOverscanPx - pageTopStripPx, FACTOR)
            val stripShot = if (grab != null && stripRows > 0 && grab.height > stripRows) {
                Bitmap.createBitmap(grab, 0, 0, grab.width, stripRows)
            } else null
            val full = if (grab != null && stripShot != null) {
                Bitmap.createBitmap(grab, 0, stripRows, grab.width, grab.height - stripRows).also { grab.recycle() }
            } else grab
            if (full != null) {
                if (!full.isFlat()) {
                    update(id) { it.copy(thumbnailTop = stripShot) }
                    rememberTopThumbnail(id, stripShot)
                    val split = (capturedHeight(web) / FACTOR).coerceIn(1, full.height)
                    val preview = Bitmap.createBitmap(full, 0, 0, full.width, split)
                    // The whole grab is KEPT alongside the page-box preview:
                    // it is what the screen actually showed (page under the
                    // glass toolbar included), and Aero zooms a card out to
                    // full screen from it rather than from the page box.
                    val kept = if (preview !== full) full else null
                    update(id) { it.copy(thumbnailFull = kept) }
                    rememberFullThumbnail(id, kept)
                    applyExactCapture(id, preview)
                    return true
                }
                full.recycle()
                stripShot?.recycle()
            }
        }
        val window = web.hostWindow() ?: return false
        val location = IntArray(2)
        web.getLocationInWindow(location)
        val height = capturedHeight(web)
        // Below the page lens's top overscan, which hangs under the status
        // bar — but from the top of the status bar strip when there is one,
        // split off below.
        val stripPx = pageTopStripPx
        val top = location[1] + pageTopOverscanPx - stripPx
        val source = Rect(location[0], top, location[0] + web.width, top + height + stripPx)
        val copy = Bitmap.createBitmap(web.width / FACTOR, (height + stripPx) / FACTOR, Bitmap.Config.ARGB_8888)
        return try {
            PixelCopy.request(
                window,
                source,
                copy,
                { result ->
                    // Re-checked at LANDING, not just at request time. A copy
                    // is of the surface as the compositor services it, which
                    // is a frame or two after this was asked for — long enough
                    // for a cover to have gone up over the page (the quick
                    // switch raises its overlay on the very next frame). What
                    // that baked into the card was the overlay's own flat
                    // background, i.e. an empty preview for a tab that had a
                    // perfectly good one.
                    if (result == PixelCopy.SUCCESS && id == _currentTabId.value && pageExposed &&
                        id !in activeHolds
                    ) {
                        // A copy of the screen has whatever effect the view
                        // is drawn with baked in; a preview is the page.
                        captureUnwarp?.invoke(web, copy, FACTOR, pageTopOverscanPx)
                        val rows = stripPx / FACTOR
                        val strip = if (rows > 0 && copy.height > rows) {
                            Bitmap.createBitmap(copy, 0, 0, copy.width, rows)
                        } else null
                        val page = if (strip != null) {
                            Bitmap.createBitmap(copy, 0, rows, copy.width, copy.height - rows).also { copy.recycle() }
                        } else copy
                        update(id) { it.copy(thumbnailFull = null, thumbnailTop = strip) }
                        rememberFullThumbnail(id, null)
                        rememberTopThumbnail(id, strip)
                        applyExactCapture(id, page)
                    } else {
                        copy.recycle()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            true
        } catch (e: IllegalArgumentException) {
            // No backing surface yet, or the rect fell outside it.
            copy.recycle()
            false
        }
    }

    /** Puts a landed [PixelCopy] on the tab. */
    private fun applyExactCapture(id: Long, copy: Bitmap) {
        // The tab can be closed inside the frame or two a copy takes to land.
        // `update` would simply find nothing to update, but `rememberThumbnail`
        // would queue a file for a tab id the next restore has no row to prune
        // against — a preview outliving the tab it belongs to.
        if (_tabs.value.none { it.id == id }) {
            copy.recycle()
            return
        }
        exactCaptureAt[id] = SystemClock.elapsedRealtime()
        update(id) { it.copy(thumbnail = copy) }
        rememberThumbnail(id, copy)
    }

    /** The software fallback: whatever `View.draw()` can reproduce of the page. */
    private fun drawThumbnail(id: Long, web: WebView) {
        val bitmap = drawIntoBitmap(web) ?: return
        // `View.draw()` reproduces nothing of a page that renders through
        // Chromium's own compositor and nothing at all of a canvas — a WebGL
        // map, a canvas game, a video — so on exactly the pages a preview
        // matters most it comes back one flat colour. Handing that to the card
        // does not merely fail to improve it: it THROWS AWAY the copy taken
        // while the page was on screen, and there is no way back to it. A
        // blank draw is therefore refused whenever the tab already has
        // something. (A genuinely blank page loses nothing by keeping the last
        // picture of itself, which is why the test needn't tell the two
        // apart.)
        val existing = _tabs.value.firstOrNull { it.id == id }?.thumbnail
        if (existing != null && bitmap.isFlat()) {
            bitmap.recycle()
            return
        }
        exactCaptureAt.remove(id)
        update(id) { it.copy(thumbnail = bitmap, thumbnailFull = null, thumbnailTop = null) }
        rememberFullThumbnail(id, null)
        rememberTopThumbnail(id, null)
        rememberThumbnail(id, bitmap)
    }

    /**
     * Whether every one of a coarse grid of samples is the same colour, i.e.
     * whether this is a picture of nothing. Sampled rather than scanned: this
     * runs on the main thread on the way out of the app, and a few dozen
     * `getPixel` calls answer the question a full pass would.
     */
    private fun Bitmap.isFlat(): Boolean {
        val steps = 7
        val first = getPixel(width / 2, height / 2)
        for (row in 0 until steps) {
            for (column in 0 until steps) {
                val x = (width - 1) * column / (steps - 1)
                val y = (height - 1) * row / (steps - 1)
                if (getPixel(x, y) != first) return false
            }
        }
        return true
    }

    /**
     * The page box's height: the view, less the strip of it under the toolbar
     * and the page lens's overscan above the box.
     */
    private fun capturedHeight(web: WebView): Int =
        (web.height - pageOverflowPx - pageTopOverscanPx).coerceAtLeast(FACTOR)

    private fun drawIntoBitmap(web: WebView): Bitmap? {
        if (web.width == 0 || web.height == 0) return null
        // Only ONE WebView is in the view tree at a time (see WebViewHost:
        // switching tabs reparents it, leaving the outgoing one with no
        // parent), so every background tab's view is detached — and a
        // detached WebView has no surface to draw. Its draw() came back
        // blank, and since it is reached whenever the tab's content has moved
        // on since the last copy (a background load finishing, an ad, a
        // feed), it was replacing good previews with empty ones. A tab that
        // isn't in the tree keeps the picture taken while it was.
        if (!web.isAttachedToWindow) return null
        // Drawn into an alpha-capable bitmap (was RGB_565, which has no alpha
        // channel and shows visible banding on gradients) -- RGB_565 is what
        // made translucent page content (shadows, glassy overlays, anything
        // alpha-blended) composite against the wrong background.
        val bitmap = Bitmap.createBitmap(
            web.width / FACTOR,
            capturedHeight(web) / FACTOR,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(1f / FACTOR, 1f / FACTOR)
        // The page box starts below the page lens's top overscan.
        canvas.translate(0f, -pageTopOverscanPx.toFloat())
        // draw() also draws whatever scrollbar state is currently active (mid
        // fade-out from a recent real scroll) -- suppress it for the capture so
        // it never bakes into the switcher's static preview.
        val hadScrollBar = web.isVerticalScrollBarEnabled
        web.isVerticalScrollBarEnabled = false
        // NOTE: previously this forced android.view.View.LAYER_TYPE_SOFTWARE
        // around the draw() call, on the theory that content Chromium
        // promotes to its own hardware compositor layer needed a software
        // layer to draw correctly. It did the opposite: forcing the wrapping
        // View into software mode pushes Chromium's OWN renderer onto its
        // (much less exercised) CPU rasterizer fallback, which is what caused
        // the skew (compressed vector icons, shifted rule lines). The real
        // answer to that is captureExact above; this path is only the
        // fallback for tabs that aren't on screen to be copied.
        web.draw(canvas)
        web.isVerticalScrollBarEnabled = hadScrollBar
        return bitmap
    }

    private fun View.hostWindow(): Window? {
        // The WebViews are created with the Application context, so the
        // Activity (and its Window) has to come from whatever they're attached
        // under. NOT via rootView: the DecorView's context is a DecorContext
        // whose base is the *application* context, not the Activity, so
        // unwrapping that one dead-ends. Every view in between — the
        // ComposeView included — carries the real Activity context.
        var view: View? = this
        while (view != null) {
            var context: Context? = view.context
            while (context is ContextWrapper) {
                if (context is Activity) return context.window
                context = context.baseContext
            }
            view = view.parent as? View
        }
        return null
    }

    /**
     * Called when the app is leaving the foreground: refreshes every live
     * tab's preview and gets it to disk right away, without waiting out the
     * debounce, since the process can be killed at any point after this.
     */
    fun captureAndFlushThumbnails() {
        // allowExact = false: a copy requested now would land after the window
        // is already gone, leaving nothing to persist. The page the user was
        // actually looking at is not left to this — [capturePageNow] takes it
        // one lifecycle step earlier, while the window is still up (see
        // MainActivity.onPause), which is the only moment a true copy of it can
        // still be had.
        views.keys.toList().forEach { captureThumbnail(it, allowExact = false) }
        viewModelScope.launch(Dispatchers.IO) { flushThumbnails() }
    }

    /**
     * Takes a true copy of the page on screen right now — called as the app
     * leaves the foreground, one step BEFORE [captureAndFlushThumbnails].
     *
     * The two are not the same request at two moments. By `onStop` the window
     * has no surface left to copy, so everything there is a software draw, and
     * a software draw of a canvas or a composited page is blank (see
     * [drawThumbnail]) — which is how leaving the browser and coming back used
     * to turn the card of the page you were reading into an empty rectangle.
     * At `onPause` the page is still up and still uncovered, so this is the
     * last chance to photograph it, and the copy lands during the transition
     * out while the main thread is still running.
     */
    fun capturePageNow() {
        captureThumbnail(_currentTabId.value)
    }

    /**
     * Parks every open tab's navigation state — back/forward list and scroll
     * offset — to disk, so the next cold start restores pages rather than
     * merely reopening URLs (see [PageStateStore]).
     *
     * Called when the app leaves the foreground, alongside the thumbnail
     * flush and for the same reason: that's the moment the process can be
     * killed, and the two are halves of one thing — the picture the startup
     * cover holds up, and the page that has to arrive under it at the same
     * scroll offset for the cover to lift without a jump.
     *
     * [WebView.saveState] touches the view, so the states are collected on
     * the calling (main) thread and only the writing is moved off it —
     * except from [onCleared], where the scope is already cancelled and a
     * launched write would simply never happen.
     */
    fun savePageStates(sync: Boolean = false) {
        if (ephemeral) return
        val live = views.mapNotNull { (id, web) ->
            // Private tabs never reach disk, same rule as their tabs and
            // previews.
            if (_tabs.value.none { it.id == id && !it.isPrivate }) return@mapNotNull null
            val state = Bundle()
            if (saveStateWithScroll(web, state) == null) null else id to state
        }
        // Tabs with no live view of their own are already parked in memory —
        // evicted by trimViews, or never opened since the last restore. Their
        // bundle is still the newest thing there is for them.
        val parkedOnly = pendingRestores.filterKeys { id ->
            views[id] == null && _tabs.value.any { it.id == id && !it.isPrivate }
        }
        val batch = live + parkedOnly.toList()
        if (batch.isEmpty()) return
        if (sync) batch.forEach { (id, state) -> pageStateStore.write(id, state) }
        else viewModelScope.launch(Dispatchers.IO) {
            batch.forEach { (id, state) -> pageStateStore.write(id, state) }
        }
    }

    private fun forgetPageStates(ids: Collection<Long>) {
        ids.forEach { pendingRestores.remove(it); diskStatesUsed.remove(it) }
        viewModelScope.launch(Dispatchers.IO) { ids.forEach(pageStateStore::delete) }
    }

    private fun rememberThumbnail(id: Long, bitmap: Bitmap) {
        // Private tabs never touch disk — same rule as the saved tab list.
        // Neither does an ephemeral session: its tab ids belong to nothing
        // the switcher will ever show, so a file per id is litter with no
        // restore to prune it.
        if (ephemeral) return
        if (_tabs.value.firstOrNull { it.id == id }?.isPrivate != false) return
        synchronized(pendingThumbnails) { pendingThumbnails[id] = bitmap }
        thumbnailsDirty.tryEmit(Unit)
    }

    /** The full-screen grab's own pending write; null = drop the stale file. */
    private val pendingFullThumbnails = HashMap<Long, Bitmap?>()

    private fun rememberFullThumbnail(id: Long, bitmap: Bitmap?) {
        if (ephemeral) return
        if (_tabs.value.firstOrNull { it.id == id }?.isPrivate != false) return
        synchronized(pendingThumbnails) { pendingFullThumbnails[id] = bitmap }
        thumbnailsDirty.tryEmit(Unit)
    }

    /** The status bar strip's own pending write; null = drop the stale file. */
    private val pendingTopThumbnails = HashMap<Long, Bitmap?>()

    private fun rememberTopThumbnail(id: Long, bitmap: Bitmap?) {
        if (ephemeral) return
        if (_tabs.value.firstOrNull { it.id == id }?.isPrivate != false) return
        synchronized(pendingThumbnails) { pendingTopThumbnails[id] = bitmap }
        thumbnailsDirty.tryEmit(Unit)
    }

    private fun flushThumbnails() {
        val (batch, fulls, tops) = synchronized(pendingThumbnails) {
            if (pendingThumbnails.isEmpty() && pendingFullThumbnails.isEmpty() && pendingTopThumbnails.isEmpty()) return
            Triple(pendingThumbnails.toMap(), pendingFullThumbnails.toMap(), pendingTopThumbnails.toMap()).also {
                pendingThumbnails.clear()
                pendingFullThumbnails.clear()
                pendingTopThumbnails.clear()
            }
        }
        batch.forEach { (id, bitmap) -> thumbnailStore.write(id, bitmap) }
        fulls.forEach { (id, bitmap) ->
            if (bitmap != null && !bitmap.isRecycled) thumbnailStore.writeFull(id, bitmap)
            else thumbnailStore.deleteFull(id)
        }
        tops.forEach { (id, bitmap) ->
            if (bitmap != null && !bitmap.isRecycled) thumbnailStore.writeTop(id, bitmap)
            else thumbnailStore.deleteTop(id)
        }
    }

    private fun forgetThumbnails(ids: Collection<Long>) {
        synchronized(pendingThumbnails) {
            ids.forEach(pendingThumbnails::remove)
            ids.forEach(pendingFullThumbnails::remove)
            ids.forEach(pendingTopThumbnails::remove)
        }
        ids.forEach { exactCaptureAt.remove(it); contentChangedAt.remove(it) }
        viewModelScope.launch(Dispatchers.IO) { ids.forEach(thumbnailStore::delete) }
    }

    // --------------------------------------------------------------- state

    /**
     * One thing the engine has said about a tab's current navigation: the
     * address it was about, what it was, and whether the document it belongs
     * to has finished yet.
     *
     * It has to be remembered rather than written straight to the tab
     * because the two orders the engine uses do not agree. A NETWORK failure
     * (DNS, refused, timeout) arrives inside the navigation it belongs to —
     * `onPageStarted`, then the error. An HTTP STATUS arrives BEFORE it,
     * since the status is known before the response commits, and
     * `onPageStarted` is where the tab's failure flag is cleared: a flag
     * raised from that callback is wiped by the navigation it belongs to,
     * which is why an empty 503 used to come up as Chromium's own
     * `net::ERR_…` page instead of the error screen.
     *
     * [settled] is what keeps a record from sticking to the NEXT load of the
     * same address: one already spent on a finished document is dropped by
     * the following `onPageStarted`, so a page that failed can be reloaded
     * back into existence.
     */
    private data class NavRecord(
        val url: String,
        val detail: LoadError,
        var settled: Boolean = false,
    )

    /**
     * A main-frame HTTP error status the tab's address came back with. Held
     * apart from [failedLoads] because a status is NOT a failure: GitHub's
     * 404 is a page with a search box on it, a metered article's 402 is the
     * article, and Cloudflare's challenge is a 503 — covering any of those
     * with a placeholder hides a document the user could have read or
     * clicked through. What makes one a failure is that nothing came with
     * it, which is [settleHttpStatus]'s question.
     */
    private val httpStatuses = mutableMapOf<Long, NavRecord>()

    /** The failure itself, once something has decided there was one. */
    private val failedLoads = mutableMapOf<Long, NavRecord>()

    /**
     * Raises the failure again for the document that has just finished, if
     * that document is the one that failed — see [NavRecord] for why once is
     * not enough.
     */
    private fun reapplyFailedLoad(id: Long, url: String) {
        val failure = failedLoads[id] ?: return
        if (failure.url != url) return
        failure.settled = true
        update(id) { it.copy(loading = false, loadFailed = true, loadError = failure.detail) }
        // Nothing is coming to replace the cover — WebErrorState is what
        // should be on screen instead of a picture of the page as it was
        // when it still loaded.
        dismissCoversForError(id)
    }

    /**
     * Rules on a remembered HTTP status now that its document has finished:
     * a status is a failure exactly when the site answered it with nothing.
     *
     * There is no callback for that. An error status with a body commits and
     * renders like any other page and is never mentioned again; an error
     * status with none has Chromium substitute its OWN page — the upside-down
     * Android and a `net::ERR_HTTP_RESPONSE_CODE_FAILURE` — and, measured on
     * device, that substitution does NOT come through `onReceivedError` the
     * way a network failure does. So the document is what has to be asked,
     * and [NO_PAGE_JS] asks it: nothing there, or the engine's own answer
     * standing in for nothing.
     *
     * The reply is about the document that has just finished, so a tab that
     * has moved on by the time it lands is left alone.
     */
    private fun settleHttpStatus(id: Long, web: WebView, url: String) {
        val status = httpStatuses[id] ?: return
        if (status.url != url || status.settled) return
        // Spent whatever the answer is: a page finishes more than once, and
        // this question is only worth asking of the first.
        status.settled = true
        // Something has already failed this document — the status was only
        // ever going to be its sentence, and it has been used as one.
        if (failedLoads[id]?.url == url) return
        web.evaluateJavascript(NO_PAGE_JS) { answer ->
            if (answer?.trim('"') != "1") return@evaluateJavascript
            val tab = _tabs.value.firstOrNull { it.id == id } ?: return@evaluateJavascript
            if (tab.url != url || tab.loading) return@evaluateJavascript
            failedLoads[id] = NavRecord(url, status.detail, settled = true)
            update(id) { it.copy(loadFailed = true, loadError = status.detail) }
            dismissCoversForError(id)
        }
    }

    private fun update(id: Long, block: (Tab) -> Tab) {
        _tabs.update { list -> list.map { if (it.id == id) block(it) else it } }
        markDirty()
    }

    private fun record(tab: Tab) {
        if (tab.isPrivate || tab.url.isBlank()) return
        // A same-URL match is the common case, but a page that redirects
        // or rewrites its URL after settling (search engines do this a
        // lot) fires onPageFinished again for what the user experiences
        // as one visit — matching on title+host too collapses that back
        // into a single entry instead of leaving a stale duplicate.
        fun isSameVisit(e: HistoryEntry) =
            e.url == tab.url || (tab.title.isNotBlank() && e.title == tab.label && e.host == tab.host)
        val previous = _history.value.firstOrNull(::isSameVisit)
        // A LOAD is not a visit — the visit clock decides that (see
        // noteNavigation) and the tally is authoritative for the count. The
        // row carries a copy of whatever has been counted so far, which
        // countVisit keeps in step.
        val entry = HistoryEntry(
            tab.label,
            tab.url,
            tab.host,
            visitCount = _visits.value[visitKey(tab.url)]?.visits ?: 0,
            favicon = tab.favicon ?: previous?.favicon,
        )
        _history.update { list -> (listOf(entry) + list.filterNot(::isSameVisit)).take(20) }
        markDirty()
    }

    // ---------------------------------------------------------------- visits

    /**
     * Decides whether a commit is a VISIT, i.e. a navigation the user made.
     *
     * Not one: a reload (Chromium says so — ours and the page's alike); a step
     * back or forward through the list (same size, and the entry landed on is
     * one that was already there); the same page again (an anchor, a fragment
     * route); an entry REPLACED in place — a client-side redirect or
     * `replaceState` — which retargets a visit still pending and is otherwise
     * nothing, or a map that rewrites its query as it pans would be a visit a
     * second; the first commit of a view that was restored rather than asked
     * for (see [freshTabs]); anything that is not http(s); and a search
     * engine's result page.
     *
     * A visit that IS one only starts pending: [syncVisitClock] counts it once
     * the page has been on screen for [VISIT_DWELL_MS].
     */
    private fun noteNavigation(id: Long, view: WebView, url: String, isReload: Boolean) {
        if (ephemeral) return
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        if (tab.isPrivate) return
        val list = view.copyBackForwardList()
        val index = list.currentIndex
        val urls = List(list.size) { list.getItemAtIndex(it)?.url.orEmpty() }
        val previous = navSnapshots[id]?.takeIf { it.view === view }
        navSnapshots[id] = NavSnapshot(view, index, urls)
        if (isReload || index !in urls.indices) return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        if (previous == null) {
            if (freshTabs.remove(id)) startVisit(id, url)
            return
        }
        val sameSize = urls.size == previous.urls.size
        // Everything before the current entry unchanged: not a list that
        // dropped its oldest entry to make room, which also keeps size and
        // index and is a NEW page.
        val samePrefix = index <= previous.urls.size && urls.subList(0, index) == previous.urls.subList(0, index)
        when {
            sameSize && index != previous.index && previous.urls.getOrNull(index) == urls[index] -> dropVisit(id)
            visitKey(url) == previous.urls.getOrNull(previous.index)?.let(::visitKey) -> Unit
            sameSize && index == previous.index && samePrefix -> pendingVisits[id]?.let { retargetVisit(id, it, url) }
            else -> startVisit(id, url)
        }
    }

    private fun isSearchResults(url: String) = allSearchEngines.value.any { it.isResultsPage(url) }

    private fun startVisit(id: Long, url: String) {
        dropVisit(id)
        if (isSearchResults(url)) return
        pendingVisits[id] = PendingVisit(url)
        syncVisitClock()
    }

    private fun retargetVisit(id: Long, visit: PendingVisit, url: String) {
        if (isSearchResults(url)) dropVisit(id) else visit.url = url
    }

    private fun dropVisit(id: Long) {
        pendingVisits.remove(id)?.job?.cancel()
    }

    private fun forgetVisit(id: Long) {
        dropVisit(id)
        navSnapshots.remove(id)
        freshTabs.remove(id)
    }

    /**
     * Starts or stops each pending visit's dwell by whether its page is what
     * the user is looking at: the tab is current, the page (not the switcher,
     * a sheet or a destination) is showing, and the app is in front. Called
     * wherever one of those changes. Time on screen ACCUMULATES across
     * interruptions, so a sheet opened over the page pauses the visit rather
     * than restarting it.
     */
    private fun syncVisitClock() {
        if (pendingVisits.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        // A copy: a dwell already served commits inline, and removes itself.
        for ((id, visit) in pendingVisits.entries.toList()) {
            val onScreen = hostResumed && pageOnScreen(id)
            if (onScreen && visit.shownSince < 0) {
                visit.shownSince = now
                visit.job = viewModelScope.launch {
                    delay((VISIT_DWELL_MS - visit.shownMs).coerceAtLeast(0))
                    commitVisit(id)
                }
            } else if (!onScreen && visit.shownSince >= 0) {
                visit.shownMs += now - visit.shownSince
                visit.shownSince = -1
                visit.job?.cancel()
                visit.job = null
            }
        }
    }

    /** Counts [id]'s pending visit if its dwell is served and the page actually arrived. */
    private fun commitVisit(id: Long) {
        val visit = pendingVisits[id] ?: return
        // A little slack: the dwell's delay and this clock round differently.
        if (visit.shownFor(SystemClock.elapsedRealtime()) < VISIT_DWELL_MS - 50) return
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return dropVisit(id)
        // Still arriving: onPageFinished asks again.
        if (tab.loading) return
        pendingVisits.remove(id)
        if (tab.loadFailed) return
        // The live view rather than the tab: a pushState route moves one and
        // not the other.
        val view = views[id]
        val liveUrl = view?.url ?: tab.url
        if (visitKey(liveUrl) != visitKey(visit.url)) return
        countVisit(
            url = visit.url,
            title = view?.title?.takeIf { it.isNotBlank() } ?: tab.label,
            host = UrlUtils.registrableDomain(visit.url),
        )
    }

    private fun countVisit(url: String, title: String, host: String) {
        val key = visitKey(url)
        val now = System.currentTimeMillis()
        var visits = 1
        _visits.update { map ->
            val next = map[key]?.visitedAgain(url, title, host, now) ?: VisitTally.firstVisit(url, title, host, now)
            visits = next.visits
            (map + (key to next)).pruneTallies(now)
        }
        _history.update { list -> list.map { if (visitKey(it.url) == key) it.copy(visitCount = visits) else it } }
        markDirty()
    }

    /**
     * Keeps the tally table bounded. Anything decayed to [EXPIRED_SCORE] goes,
     * and past [MAX_TALLIES] the lowest CURRENT scores go — never by lifetime
     * total, which would keep a stale habit forever and lock new pages out once
     * the table filled, and never by recency alone, which would drop the
     * frequent pages this list exists to find.
     */
    private fun Map<String, VisitTally>.pruneTallies(now: Long): Map<String, VisitTally> {
        val live = filterValues { it.scoreAt(now) >= EXPIRED_SCORE }
        if (live.size <= MAX_TALLIES) return live
        return live.entries
            .map { it to it.value.scoreAt(now) }
            .sortedWith(
                compareByDescending<Pair<Map.Entry<String, VisitTally>, Double>> { it.second }
                    .thenByDescending { it.first.value.scoredAt }
            )
            .take(MAX_TALLIES)
            .associate { it.first.key to it.first.value }
    }

    // ------------------------------------------------- web platform answers

    /** The download has been confirmed or declined; stop offering it. */
    fun consumeDownloadRequest() {
        _downloadRequest.value = null
    }

    /**
     * The picker came back. [data] is null for a cancel, which still has to
     * be reported — a file input whose callback never fires is dead for the
     * rest of the page's life.
     */
    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        val request = _fileChooser.value ?: return
        _fileChooser.value = null
        val uris = runCatching {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        }.getOrNull()
        request.callback.onReceiveValue(uris)
    }

    /** No picker could be opened, or the user backed out before it appeared. */
    fun cancelFileChooser() {
        val request = _fileChooser.value ?: return
        _fileChooser.value = null
        request.callback.onReceiveValue(null)
    }

    /**
     * The USER leaving fullscreen — back, or the gesture. The page is told,
     * which is what makes the video's own controls agree; the state is
     * cleared first, since [WebChromeClient.CustomViewCallback.onCustomViewHidden]
     * comes straight back as `onHideCustomView`.
     */
    fun exitFullscreen() {
        val fullscreen = _fullscreen.value ?: return
        _fullscreen.value = null
        runCatching { fullscreen.callback.onCustomViewHidden() }
    }

    /**
     * Answers the script. [text] is the prompt's value and is ignored by
     * every other kind; a cancel of any kind is `cancel()`, which is what
     * `confirm()` returning false and `prompt()` returning null both are.
     */
    fun answerJsDialog(confirmed: Boolean, text: String? = null) {
        val dialog = _jsDialog.value ?: return
        _jsDialog.value = null
        val result = dialog.result
        when {
            !confirmed -> result.cancel()
            result is JsPromptResult -> result.confirm(text.orEmpty())
            else -> result.confirm()
        }
    }

    /** Declines whatever the tab had up, without waiting for an answer. */
    private fun cancelJsDialogFor(tabId: Long) {
        val dialog = _jsDialog.value ?: return
        if (dialog.tabId != tabId) return
        _jsDialog.value = null
        dialog.result.cancel()
    }

    /**
     * Proceeds through, or backs out of, a bad certificate — and remembers
     * which for the host, so the rest of the page's requests to it are
     * answered the same way instead of asking again per subresource.
     */
    fun answerSslPrompt(proceed: Boolean) {
        val prompt = _sslPrompt.value ?: return
        _sslPrompt.value = null
        sslDecisions[prompt.host] = proceed
        if (proceed) prompt.handler.proceed() else prompt.handler.cancel()
    }

    /** How [httpAuthCredentials] is filed. See it for why the space is in the key. */
    private fun httpAuthKey(isPrivate: Boolean, host: String, realm: String): String =
        "${if (isPrivate) "p" else "n"}|$host|$realm"

    /**
     * Answers a challenge, and remembers the answer for the rest of this
     * process so the page's other protected requests go through without
     * asking again. A rejected login is dropped by the challenge it comes
     * back as — see [WebViewClient.onReceivedHttpAuthRequest] in [create].
     */
    fun answerHttpAuth(username: String, password: String) {
        val prompt = _httpAuth.value ?: return
        _httpAuth.value = null
        httpAuthCredentials[prompt.key] = username to password
        prompt.handler.proceed(username, password)
    }

    /** Backs out of a challenge. The page is left as the server's own 401. */
    fun cancelHttpAuth() {
        val prompt = _httpAuth.value ?: return
        _httpAuth.value = null
        prompt.handler.cancel()
    }

    /** Declines whatever the tab had up, without waiting for an answer. */
    private fun cancelHttpAuthFor(tabId: Long) {
        val prompt = _httpAuth.value ?: return
        if (prompt.tabId != tabId) return
        _httpAuth.value = null
        prompt.handler.cancel()
    }

    /**
     * Sends the form again, or doesn't. Exactly one of the two messages is
     * sent, exactly once — see [FormResubmission].
     */
    fun answerFormResubmission(resend: Boolean) {
        val request = _formResubmission.value ?: return
        _formResubmission.value = null
        if (resend) request.resend.sendToTarget() else request.dontResend.sendToTarget()
    }

    /** The safe half of [answerFormResubmission], for a question overtaken by events. */
    private fun cancelFormResubmissionFor(tabId: Long) {
        val request = _formResubmission.value ?: return
        if (request.tabId != tabId) return
        _formResubmission.value = null
        request.dontResend.sendToTarget()
    }

    /** Taken by the UI once it has said so. See [PageCrash]. */
    fun consumePageCrash() {
        _pageCrash.value = null
    }

    /**
     * Clears away a WebView whose renderer has died, so the tab can be given
     * a live one.
     *
     * Nothing about the dead view is recoverable: its `saveState` is gone
     * with the renderer, so unlike [parkAndDrop] there is no page state to
     * park — the tab keeps its URL and its thumbnail, and the rebuild loads
     * the URL again, which is the same path a tab evicted by [trimViews]
     * takes. Every live handle the dead document was holding is answered on
     * its way out for the same reason those are answered at [onPageStarted]:
     * the document they belong to no longer exists to answer them.
     *
     * Dropping it from [views] is what makes the rebuild happen: the host's
     * `AndroidView` asks [webViewFor] for this tab on its next run, and a
     * miss there is a fresh WebView. Touching the tab's own state is what
     * gets that run — a background tab needs none, since being selected is
     * already a state change.
     */
    private fun handleRenderProcessGone(id: Long, web: WebView, didCrash: Boolean) {
        val current = id == _currentTabId.value
        if (current) finishPageDarkFade()
        if (views[id] === web) views.remove(id)
        endFindFor(id)
        cancelJsDialogFor(id)
        cancelHttpAuthFor(id)
        cancelFormResubmissionFor(id)
        forgetPasswordState(id)
        setPageBottomBar(id, 0)
        // A picture of a page whose renderer is gone is a picture of nothing
        // coming back; the rebuilt view gets the ordinary cover instead.
        dropHold(id)
        pendingHolds.remove(id)
        // Detached and destroyed directly rather than through [destroy]:
        // past this callback the only defined calls on the view are these
        // two, and the `stopLoading` that one does first is a call into a
        // renderer that is no longer there.
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        if (current) {
            if (didCrash) _pageCrash.value = PageCrash(id)
            update(id) { it.copy(loading = true, progress = 0, canGoBack = false, canGoForward = false) }
        }
    }

    // ------------------------------------------------------ site permissions

    /**
     * The origin as this app keys it: whatever WebView handed over, minus the
     * trailing slash a [PermissionRequest]'s Uri carries and the geolocation
     * callback's does not. Two spellings of one origin would be two rows in
     * Settings and two questions for the user.
     */
    private fun originOf(raw: String): String = raw.trimEnd('/')

    /** This site's standing answer, or [PermissionRule.Ask] if it has none. */
    private fun standingAnswer(origin: String, permission: SitePermission): PermissionRule {
        val key = SitePermissionGrant.key(origin, permission)
        // The session's own answers first: "Allow this time" is the more
        // recent statement, and a site blocked in Settings that the user then
        // allowed once has to actually be allowed once.
        sessionGrants[key]?.let { return if (it) PermissionRule.Allow else PermissionRule.Block }
        _sitePermissions.value.firstOrNull { it.key == key }
            ?.let { return if (it.allowed) PermissionRule.Allow else PermissionRule.Block }
        return _permissionRules.value[permission] ?: permission.defaultRule
    }

    /**
     * The one way a page's request reaches the user, and the one way it is
     * answered. [reply] is handed the subset that was granted — possibly
     * empty, never null, and called exactly once, because the callbacks
     * behind it are live handles into the renderer.
     */
    private fun askSitePermission(
        tabId: Long,
        origin: String,
        wanted: List<SitePermission>,
        reply: (List<SitePermission>) -> Unit,
    ) {
        // An opaque origin — a data: or about: document, a sandboxed frame —
        // has nothing to attribute the answer to and nothing to show the
        // user. Refused, rather than asked about under a blank name.
        if (origin.isBlank() || wanted.isEmpty()) {
            reply(emptyList())
            return
        }
        val answers = wanted.associateWith { standingAnswer(origin, it) }
        if (answers.values.none { it == PermissionRule.Ask }) {
            grantWithSystem(wanted.filter { answers[it] == PermissionRule.Allow }, reply)
            return
        }
        // One question at a time, the same rule the js dialogs follow: a page
        // can raise these in a loop, and queuing them is handing the browser
        // over to it. The second is refused, which is what a user looking at
        // the first would have said anyway.
        if (_permissionAsk.value != null) {
            reply(emptyList())
            return
        }
        pendingPermission = reply
        _permissionAsk.value = PermissionAsk(
            tabId = tabId,
            origin = origin,
            permissions = wanted,
            private = _tabs.value.firstOrNull { it.id == tabId }?.isPrivate == true,
        )
    }

    /** The user's answer to the card. */
    fun answerPermissionAsk(answer: PermissionAnswer) {
        val ask = _permissionAsk.value ?: return
        _permissionAsk.value = null
        val reply = pendingPermission ?: { }
        pendingPermission = null
        when (answer) {
            PermissionAnswer.Block -> {
                rememberAnswer(ask, allowed = false, persist = true)
                reply(emptyList())
            }
            PermissionAnswer.AllowOnce -> {
                rememberAnswer(ask, allowed = true, persist = false)
                grantWithSystem(ask.permissions, reply)
            }
            PermissionAnswer.Allow -> {
                rememberAnswer(ask, allowed = true, persist = true)
                grantWithSystem(ask.permissions, reply)
            }
        }
    }

    /**
     * A private tab's answers never reach disk — the same rule its tabs, its
     * previews and its history already follow — so every one of them is a
     * session answer however the button was labelled.
     */
    private fun rememberAnswer(ask: PermissionAsk, allowed: Boolean, persist: Boolean) {
        if (!persist || ask.private) {
            ask.permissions.forEach { sessionGrants[SitePermissionGrant.key(ask.origin, it)] = allowed }
            refreshNotificationScripts()
            return
        }
        // A standing answer supersedes anything this session said about the
        // same site, or "Block" after an earlier "Allow this time" would be
        // read straight back out of the session map.
        ask.permissions.forEach { sessionGrants.remove(SitePermissionGrant.key(ask.origin, it)) }
        _sitePermissions.update { list ->
            val keys = ask.permissions.map { SitePermissionGrant.key(ask.origin, it) }.toSet()
            list.filterNot { it.key in keys } +
                ask.permissions.map { SitePermissionGrant(ask.origin, it, allowed) }
        }
        markDirty()
        refreshNotificationScripts()
    }

    /**
     * The second half of every allow: the app's own access to the device.
     *
     * The site having permission and this app having permission are different
     * questions with different answers, and a page allowed the camera by a
     * user who never granted the browser one gets a stream that does not
     * exist. The system's dialog is raised only here, AFTER the site has been
     * allowed — asking first would put a camera prompt in front of someone
     * who had not yet decided to give the site a camera.
     */
    private fun grantWithSystem(allowed: List<SitePermission>, reply: (List<SitePermission>) -> Unit) {
        if (allowed.isEmpty()) {
            reply(emptyList())
            return
        }
        val missing = allowed.filterNot(::heldBySystem).flatMap { it.androidPermissions }.distinct()
        if (missing.isEmpty()) {
            // Filtered rather than trusted: below API 33 notifications have
            // no permission to ask for, and a user who switched them off in
            // system settings is not held however the site was answered.
            reply(allowed.filter(::heldBySystem))
            return
        }
        // A system dialog already up means an answer is already being waited
        // on; a second is refused rather than stacked.
        if (_permissionOsRequest.value != null) {
            reply(emptyList())
            return
        }
        pendingOsResume = { reply(allowed.filter(::heldBySystem)) }
        _permissionOsRequest.value = missing
    }

    /**
     * The system dialog is closed. What it returned is deliberately not read:
     * the permissions actually held afterwards are re-checked instead, which
     * is the same answer for a grant and the right answer for the paths that
     * never reach a result at all (a permission permanently denied, a dialog
     * dismissed by rotation).
     */
    fun onOsPermissionResult() {
        _permissionOsRequest.value = null
        refreshNotificationScripts()
        val resume = pendingOsResume ?: return
        pendingOsResume = null
        resume()
    }

    /**
     * Whether the APP may reach this. Any one of the Android permissions is
     * enough: location has two, and a user who granted only the coarse one
     * has granted location.
     */
    private fun heldBySystem(permission: SitePermission): Boolean =
        if (permission == SitePermission.Notifications) {
            // Covers POST_NOTIFICATIONS on 33+, and the app's own switch in
            // system settings everywhere — the one that exists below 33.
            NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()
        } else {
            permission.androidPermissions.any {
                ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
            }
        }

    /** The global default for a capability, from Settings. */
    fun setPermissionRule(permission: SitePermission, rule: PermissionRule) {
        _permissionRules.update { it + (permission to rule) }
        markDirty()
        refreshNotificationScripts()
    }

    /** Puts one site back to asking. */
    fun clearSitePermission(grant: SitePermissionGrant) {
        _sitePermissions.update { list -> list.filterNot { it.key == grant.key } }
        sessionGrants.remove(grant.key)
        markDirty()
        refreshNotificationScripts()
    }

    /** Puts every site back to asking. The global rules are left alone. */
    fun clearSitePermissions() {
        _sitePermissions.value = emptyList()
        sessionGrants.clear()
        markDirty()
        refreshNotificationScripts()
    }

    // --------------------------------------------------- site notifications
    // WebView has no `window.Notification`; WebNotifications supplies one and
    // this is the Kotlin half. The question itself goes through the same
    // askSitePermission as the camera's, so the card, the three answers,
    // Settings' rule and its list of sites are all the ordinary ones.
    //
    // Private tabs and the link overlay are refused outright, never asked:
    // the notification shade is outside the private space (and on the lock
    // screen), and the overlay is gone by the time anything could be tapped.

    /** Whether notifications are refused for this tab without asking. */
    private fun notificationsRefused(tabId: Long): Boolean =
        ephemeral || _tabs.value.firstOrNull { it.id == tabId }?.isPrivate != false

    /** What `Notification.permission` should say for [origin]. */
    private fun notificationStateFor(origin: String): WebNotifications.State =
        when (standingAnswer(origin, SitePermission.Notifications)) {
            // Allowed but not postable is "default", not "granted": that is
            // what gets a page to ask, and asking is what reaches Android's
            // own dialog (grantWithSystem). "granted" would have it post into
            // a shade the app cannot reach, silently, for good.
            PermissionRule.Allow ->
                if (heldBySystem(SitePermission.Notifications)) WebNotifications.State.Granted
                else WebNotifications.State.Default
            PermissionRule.Block -> WebNotifications.State.Denied
            PermissionRule.Ask -> WebNotifications.State.Default
        }

    private fun attachNotifications(web: WebView, tab: Tab) {
        val tabId = tab.id
        WebNotifications.attach(web, object : WebNotifications.Listener {
            override fun onRequestPermission(
                origin: String,
                mainFrame: Boolean,
                reply: (WebNotifications.State) -> Unit,
            ) {
                // A frame embedded in the page is not the site the user is
                // looking at, and a card naming it would be a question about
                // an origin that appears nowhere on screen.
                if (notificationsRefused(tabId) || !mainFrame) {
                    reply(WebNotifications.State.Denied)
                    return
                }
                askSitePermission(tabId, originOf(origin), listOf(SitePermission.Notifications)) { granted ->
                    reply(
                        if (granted.isNotEmpty()) WebNotifications.State.Granted
                        else notificationStateFor(originOf(origin)),
                    )
                }
            }

            override fun onShow(
                origin: String,
                request: WebNotifications.Request,
                proxy: JavaScriptReplyProxy,
            ): Boolean {
                if (notificationsRefused(tabId)) return false
                if (notificationStateFor(origin) != WebNotifications.State.Granted) return false
                // The page says where it is; only believed inside its own origin.
                val pageUrl = request.pageUrl.takeIf { it == origin || it.startsWith("$origin/") } ?: origin
                val androidTag = if (request.tag.isNotBlank()) {
                    "$origin#${request.tag}"
                } else {
                    "$origin#~${System.currentTimeMillis()}-${notificationSeq++}"
                }
                val shown = WebNotifications.post(
                    context = getApplication(),
                    androidTag = androidTag,
                    origin = origin,
                    request = request.copy(pageUrl = pageUrl),
                    tabId = tabId,
                    icon = faviconStore.read(UrlUtils.registrableDomain(pageUrl)),
                )
                if (shown) {
                    notificationRoutes.remove(androidTag)
                    notificationRoutes[androidTag] = NotificationRoute(tabId, origin, request.jsId, proxy)
                    while (notificationRoutes.size > MAX_NOTIFICATION_ROUTES) {
                        notificationRoutes.remove(notificationRoutes.keys.first())
                    }
                }
                return shown
            }

            override fun onClose(origin: String, jsId: Int, proxy: JavaScriptReplyProxy) {
                // Newest first: ids restart with every document, so an older
                // page's notification can share one.
                val key = notificationRoutes.entries.lastOrNull {
                    it.value.tabId == tabId && it.value.origin == origin && it.value.jsId == jsId
                }?.key ?: return
                notificationRoutes.remove(key)
                WebNotifications.cancel(getApplication(), key)
            }
        })
        installNotificationScript(web, tab.isPrivate)
    }

    /** (Re-)registers one view's script, if its answers have changed. */
    private fun installNotificationScript(web: WebView, private: Boolean) {
        val refused = ephemeral || private
        val fallback = if (refused) {
            WebNotifications.State.Denied
        } else {
            notificationStateFor("")
        }
        val sites = if (refused) {
            emptyMap()
        } else {
            val suffix = "|${SitePermission.Notifications.id}"
            val origins = _sitePermissions.value
                .filter { it.permission == SitePermission.Notifications }
                .map { it.origin } +
                sessionGrants.keys.filter { it.endsWith(suffix) }.map { it.removeSuffix(suffix) }
            origins.distinct().associateWith(::notificationStateFor).filterValues { it != fallback }
        }
        val signature = "${fallback.js}|${sites.entries.sortedBy { it.key }.joinToString(",")}"
        if (notificationScriptState[web] == signature && notificationScripts.containsKey(web)) return
        WebNotifications.install(web, notificationScripts.remove(web), fallback, sites)
            ?.let { notificationScripts[web] = it }
        notificationScriptState[web] = signature
    }

    /**
     * After any answer, rule or system permission changes. Reaches the NEXT
     * document in each tab; the ones on screen keep what they started with,
     * apart from a page that asked, which was told directly.
     */
    private fun refreshNotificationScripts() {
        if (!WebNotifications.supported) return
        val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }.toSet()
        views.forEach { (id, web) -> installNotificationScript(web, id in privateIds) }
    }

    /** The notifications card for a document that is leaving. */
    private fun cancelNotificationAskFor(id: Long) {
        val ask = _permissionAsk.value ?: return
        if (ask.tabId != id || ask.permissions != listOf(SitePermission.Notifications)) return
        _permissionAsk.value = null
        pendingPermission?.invoke(emptyList())
        pendingPermission = null
    }

    /**
     * A notification was tapped. Its own tab if that tab is still the same
     * site (ids are reused across launches), a new one on its page if not —
     * and the page, if it is still the document that raised it, hears the
     * click.
     */
    fun openFromNotification(tabId: Long, url: String, key: String) {
        val route = notificationRoutes.remove(key)
        val tab = _tabs.value.firstOrNull { it.id == tabId && !it.isPrivate }
        if (tab != null && url.isNotBlank() && siteKeyOf(tab.url) == siteKeyOf(url)) {
            if (_privateMode.value) setPrivateMode(false)
            selectTab(tab.id)
            route?.takeIf { it.tabId == tabId }?.let { WebNotifications.dispatch(it.proxy, it.jsId, "click") }
        } else if (url.isNotBlank()) {
            newTab(private = false, url = url)
        }
    }

    // -------------------------------------------------------- site settings

    /**
     * The key a URL is filed under: its registrable domain, blank for a tab
     * with no page. See [SiteSettings] for why that and not the origin.
     */
    private fun siteKeyOf(url: String): String =
        if (url.isBlank()) "" else UrlUtils.registrableDomain(url)

    private fun siteOfTab(id: Long): String =
        siteKeyOf(_tabs.value.firstOrNull { it.id == id }?.url.orEmpty())

    /** One site's record, or the ordinary treatment where it has none. */
    fun settingsForSite(site: String): SiteSettings =
        if (site.isBlank()) SiteSettings() else _siteSettings.value[site] ?: SiteSettings()

    private fun siteDarkFor(site: String): Boolean? =
        if (site.isBlank()) null else _siteSettings.value[site]?.dark

    private fun filterRulesFor(site: String): AdBlocker.Rules {
        val settings = settingsForSite(site)
        return AdBlocker.Rules(ads = settings.blockAds, trackers = settings.blockTrackers)
    }

    /** What a tab's pages are scaled by: its site's answer, else the setting. */
    private fun zoomFor(id: Long): Int =
        _siteSettings.value[siteOfTab(id)]?.zoom ?: _pageZoom.value

    private fun cookieBannersFor(id: Long): Boolean =
        settingsForSite(siteOfTab(id)).hideCookieBanners

    /**
     * Puts the site's record onto a tab's live view for the page it has just
     * started loading.
     *
     * Called from `onPageStarted`, which is the one moment a tab's site can
     * change, and cheap on the ordinary navigation where it hasn't: every
     * write below is guarded by a comparison first.
     */
    private fun syncSiteState(id: Long, web: WebView, url: String) {
        val settings = settingsForSite(siteKeyOf(url))
        tabFilters[id] = AdBlocker.Rules(ads = settings.blockAds, trackers = settings.blockTrackers)
        val zoom = settings.zoom ?: _pageZoom.value
        if (web.settings.textZoom != zoom) web.settings.textZoom = zoom
        setCosmeticEnabled(web, settings.hideCookieBanners)
        // The tab tile's override belongs to the page the user was looking
        // at, not to the one arriving — a site pinned dark must not be light
        // because the tab it was opened in had been flipped.
        val siteDark = settings.dark
        if (siteDark != null) pageDarkOverrides.remove(id)
        val want = pageDarkFor(id)
        if (id == _currentTabId.value) _pageDarkActive.value = want
        // Filter only. See [darkApplied]: the context half cannot move on a
        // view that exists, and rebuilding one mid-navigation would park and
        // restore a page that is still loading.
        if (darkApplied[web] != want) applyDarkening(web, want)
    }

    /**
     * Banner hiding for one view, both halves: the registration, which is
     * what every page from here on gets, and the document already on screen,
     * which has the stylesheet in it already.
     */
    private fun setCosmeticEnabled(web: WebView, enabled: Boolean) {
        applyCosmetic(web, enabled)
        if (cosmeticRules.genericCss.isBlank()) return
        web.evaluateJavascript(
            if (enabled) CosmeticFilters.enableScript() else CosmeticFilters.disableScript(),
            null,
        )
    }

    /**
     * The Site settings sheet's one writer.
     *
     * A record that has come back to ordinary is REMOVED rather than stored
     * as a row of defaults — see [SiteSettings.isDefault]. In the private
     * space the previous value is shadowed instead of overwritten, so the
     * change is live for as long as the space is and nothing of it is written
     * down; see [privateSiteSettings].
     */
    fun setSiteSettings(site: String, settings: SiteSettings) {
        if (site.isBlank()) return
        val before = settingsForSite(site)
        if (before == settings) return
        if (_privateMode.value && site !in privateSiteSettings) {
            privateSiteSettings[site] = _siteSettings.value[site]
        }
        _siteSettings.update { map ->
            if (settings.isDefault) map - site else map + (site to settings)
        }
        pushSiteSettings(site, before, settings)
        markDirty()
    }

    /** The Site settings sheet's zoom rung, by index into [ZOOM_STEPS]. */
    fun setSiteZoomStep(site: String, index: Int) {
        val percent = ZOOM_STEPS[index.coerceIn(0, ZOOM_STEPS.lastIndex)]
        // Landing back on the app's own setting is not a per-site zoom, it is
        // this site rejoining the setting — which is also what the readout
        // being tapped means.
        val zoom = if (percent == _pageZoom.value) null else percent
        setSiteSettings(site, settingsForSite(site).copy(zoom = zoom))
    }

    /** Puts one site back to being treated like every other. */
    fun clearSiteSettings(site: String) = setSiteSettings(site, SiteSettings())

    /** Puts every site back. The app's own settings are left alone. */
    fun clearAllSiteSettings() {
        val sites = _siteSettings.value.keys.toList()
        if (sites.isEmpty()) return
        sites.forEach { site ->
            if (_privateMode.value && site !in privateSiteSettings) {
                privateSiteSettings[site] = _siteSettings.value[site]
            }
        }
        val before = _siteSettings.value
        _siteSettings.value = emptyMap()
        sites.forEach { site -> pushSiteSettings(site, before[site] ?: SiteSettings(), SiteSettings()) }
        markDirty()
    }

    /**
     * Applies a changed record to the tabs that are on that site right now.
     *
     * Each of the five moves separately, because each costs something
     * different: the filters need the page fetched again, banner hiding is a
     * stylesheet being switched off in place, zoom is one setting write, and
     * darkening is the whole cross-fading rebuild the menu tile does — so
     * none of them is done unless that part of the record actually changed.
     */
    private fun pushSiteSettings(site: String, before: SiteSettings, after: SiteSettings) {
        val affected = _tabs.value.filter { siteKeyOf(it.url) == site }
        if (affected.isEmpty()) return
        affected.forEach { tab ->
            tabFilters[tab.id] = AdBlocker.Rules(ads = after.blockAds, trackers = after.blockTrackers)
            val web = views[tab.id] ?: return@forEach
            val zoom = after.zoom ?: _pageZoom.value
            if (web.settings.textZoom != zoom) web.settings.textZoom = zoom
            if (after.hideCookieBanners != before.hideCookieBanners) {
                setCosmeticEnabled(web, after.hideCookieBanners)
            }
        }
        if (after.dark != before.dark) {
            val ids = affected.map { it.id }.toSet()
            // A standing answer about the site supersedes the tile's passing
            // one, exactly as a change to the app setting does.
            ids.forEach { pageDarkOverrides.remove(it) }
            _pageDarkActive.value = pageDarkFor(_currentTabId.value)
            val stale = ids.filter { id -> views[id]?.let { builtDark[it] != pageDarkFor(id) } == true }.toSet()
            if (stale.isNotEmpty()) rebuildForPageDark(stale)
        }
        // What is already in the document was fetched under the old rules, so
        // the change is only visible on a fresh load. The current tab is
        // fetched again — it is the page the user is looking at and the
        // reason they opened the sheet; the rest pick it up on their next
        // load, rather than having a form thrown away in a tab nobody asked
        // about.
        if (after.blockAds != before.blockAds || after.blockTrackers != before.blockTrackers) {
            val currentId = _currentTabId.value
            if (affected.any { it.id == currentId }) views[currentId]?.reload()
        }
    }

    // ------------------------------------------------------------ page zoom

    /**
     * Text scale, in the rungs of [ZOOM_STEPS]. This is WebView's
     * `textZoom` rather than a page zoom: there is no page-zoom API on
     * WebView at all (`setInitialScale` sets a starting scale and is undone
     * by the first pinch or by any page declaring its own viewport), and
     * textZoom is honoured by responsive layouts, which reflow to it instead
     * of growing a scrollbar sideways.
     *
     * The setting here is the browser's, for every tab and every site that
     * has not been answered about specifically — a zoom that only applied to
     * the tab it was set in is a zoom the user has to set again on the next
     * link they follow. One site at a time can override it from the Site
     * settings sheet; see [setSiteZoomStep].
     *
     * Set by the rung at [index] of [ZOOM_STEPS] rather than by a percentage,
     * so that what the slider reports back can never be a value the ladder
     * doesn't have — an index out of range is clamped, where a percentage
     * would have to be searched for and might miss.
     */
    fun setZoomStep(index: Int) = setZoom(ZOOM_STEPS[index.coerceIn(0, ZOOM_STEPS.lastIndex)])

    private fun setZoom(percent: Int) {
        if (_pageZoom.value == percent) return
        _pageZoom.value = percent
        // Every live view, not just the current one: the setting is the
        // browser's, and a background tab that kept the old scale would
        // change size under the user when they switched to it. A tab on a
        // site with a zoom of its own keeps that one — the site was answered
        // about specifically, and this is the answer for everything else.
        views.forEach { (id, web) -> web.settings.textZoom = zoomFor(id) }
        markDirty()
    }

    // ------------------------------------------------- clearing browsing data

    /**
     * Everything a browser can be asked to forget, in one pass. Each part is
     * separate because they answer different questions: history is what the
     * user did, cookies are who the sites think they are, the cache is only
     * what was quicker to keep, and site data is what pages stored themselves.
     *
     * Not included on purpose: the open tabs and their back/forward stacks.
     * Clearing what has been visited should not close what is being read —
     * and a page still on screen is not private information the browser is
     * holding, it is the thing the user is looking at.
     *
     * Saved passwords are not here either. They live in their own encrypted
     * vault with their own list and their own delete-everything row, behind
     * the device lock; sweeping them up in a general clear would delete them
     * from behind that door without ever opening it.
     */
    fun clearBrowsingData(
        history: Boolean,
        cookies: Boolean,
        cache: Boolean,
        siteData: Boolean,
    ) {
        if (history) clearHistory()
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (cache) {
            // `true` includes the disk cache, not only the RAM one. Form data
            // goes with it: what was typed into a page is the same kind of
            // leftover, and it has no switch of its own worth adding.
            views.values.forEach {
                it.clearCache(true)
                it.clearFormData()
            }
        }
        // localStorage, IndexedDB, the Web SQL leftovers and the rest of what
        // a page can put on the device itself — the half of "cookies" that
        // clearing cookies doesn't touch.
        if (siteData) WebStorage.getInstance().deleteAllData()
    }

    companion object {
        /**
         * A session for [com.yuku.browser.CustomTabActivity]: the user's
         * settings, none of their state, nothing written back. See the
         * [ephemeral] parameter.
         */
        val EphemeralFactory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BrowserViewModel(this[APPLICATION_KEY]!!, ephemeral = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterComponentCallbacks(memoryCallbacks)
        finishPageDarkFade()
        persistNow()
        flushThumbnails()
        savePageStates(sync = true)
        views.values.forEach(::destroy)
        views.clear()
        previewViews.values.forEach(::destroy)
        previewViews.clear()
    }
}

/**
 * Is this request a SPECULATION rather than the page the user is on — a
 * prefetch or a prerender the engine fired off on its own?
 *
 * WebView reports one of these as `isForMainFrame`, because that is what it
 * is: a navigation-class request for a document. It is not, however, a
 * navigation THIS tab is making, and nothing it answers with can fail the
 * page already on screen. Cloudflare's Speed Brain is the case that made
 * this load-bearing — a site carrying its `speculation-rules` header
 * (keddr.com does) has the engine prefetch every same-site link the moment a
 * finger lands on one, and Cloudflare answers a prefetch it does not want to
 * serve with a bare **503**. Without this test the first touch anywhere near
 * a link — the swipe that turns a carousel is one — replaced a perfectly
 * loaded page with "the site is unavailable", for a document the tab never
 * navigated to and whose address never appeared in the bar.
 *
 * Asked of the request's own headers rather than of the tab's state: the
 * engine marks a speculative fetch with `Sec-Purpose` (`Purpose` on the
 * older path), and the answer is then true whatever the tab happens to be
 * doing at the time.
 */
private fun isSpeculative(request: WebResourceRequest): Boolean =
    request.requestHeaders.orEmpty().any { (name, value) ->
        (name.equals("Sec-Purpose", ignoreCase = true) ||
            name.equals("Purpose", ignoreCase = true) ||
            name.equals("X-Moz", ignoreCase = true)) &&
            (value.contains("prefetch", ignoreCase = true) ||
                value.contains("prerender", ignoreCase = true))
    }

/**
 * Did this document come back with NOTHING to read — answers `"1"` if so.
 *
 * Two ways for that to be true, and the second is the one that matters
 * here: an empty body, or the engine's own error page standing where the
 * site's answer would be. That page is recognised by the `net::ERR_…` code
 * printed on it, which is not translated, and by the comment Chromium's
 * template carries above its upside-down Android — either alone would do,
 * and neither is worth trusting on its own across WebView versions.
 *
 * `innerText` rather than `textContent` for the emptiness test: it is what
 * is rendered, so a body holding only a `<script>` or a hidden template
 * still reads as empty, which is what it looks like.
 */
private const val NO_PAGE_JS = """
(function () {
  var b = document.body;
  if (!b) return '1';
  var t = (b.innerText || '').trim();
  if (/net::ERR_[A-Z_]+/.test(t) || b.innerHTML.indexOf('Upside down Android') >= 0) return '1';
  if (t.length > 0) return '0';
  return b.querySelector('img,svg,video,canvas,iframe,form,input,button,a[href]') ? '0' : '1';
})()
"""
