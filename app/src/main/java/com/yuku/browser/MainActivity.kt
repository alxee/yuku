package com.yuku.browser

import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.ActionMode
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yuku.browser.core.AccentTheme
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.SpecialTheme
import com.yuku.browser.core.WebNotifications
import com.yuku.browser.ui.ActionModeOwner
import com.yuku.browser.ui.BrowserScreen
import com.yuku.browser.ui.theme.BrowserTheme
import com.yuku.browser.ui.theme.ThemeCrossfadeHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The splash never outlives startup by more than [SPLASH_MIN_MS] — but it also
 * never outlives this, whatever the restore is doing. It's held by refusing to
 * draw the first frame, so this cap is what keeps a slow disk from turning
 * into a launch that looks hung.
 */
private const val SPLASH_MAX_MS = 3_000L

/**
 * How long the COLD-START splash stays up at minimum, measured from process
 * start rather than from `onCreate` — the splash is already on screen by the
 * time any of this runs, so the wait the user sees is the process's, not the
 * Activity's.
 *
 * It exists because that splash has twenty cats cycling at 280ms and a fast
 * start would show one and a half of them: a thing that appears and leaves
 * before it has visibly done anything reads as a flash, not as an animation.
 * A second is three or four cats, which is enough to see that it IS a cycle.
 *
 * The FALLBACK splash gets none of this. It has nothing to cycle — one still
 * face, and one nobody chose — so holding it back would be a second spent on
 * a picture that has already said everything it has to say.
 */
private const val SPLASH_MIN_MS = 1_000L

/**
 * A [FragmentActivity] rather than a plain ComponentActivity for exactly one
 * reason: `BiometricPrompt` accepts nothing else, and the saved-password list
 * is behind it. Nothing here uses fragments.
 */
class MainActivity : FragmentActivity(), ActionModeOwner {

    private val vm: BrowserViewModel by viewModels()

    /**
     * The page's text-selection toolbar while it is up — see [ActionModeOwner]
     * for why the back chain needs to know.
     */
    override var activeActionMode: ActionMode? by mutableStateOf(null)
        private set

    override fun onActionModeStarted(mode: ActionMode) {
        activeActionMode = mode
        super.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode) {
        // Identity-checked: a mode replacing another one starts before the
        // one it replaces finishes, so clearing unconditionally would drop
        // the live one and leave back with nothing to clear.
        if (activeActionMode === mode) activeActionMode = null
        super.onActionModeFinished(mode)
    }

    /**
     * While true, the first frame is refused and the system splash screen
     * (see values-v31/themes.xml) stays on screen with its cats cycling.
     */
    private var holdSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before anything can compose a WebView: the tabs' shared context has
        // to be pointing at this Activity by the time one is constructed, or
        // the page's forms are invisible to the platform autofill service and
        // the keyboard has no saved logins to suggest. See
        // BrowserViewModel.webHostContext.
        vm.attachHost(this)
        keepSplashUpWhileRestoring(savedInstanceState)
        followThemeOnSplash()

        // chrome://inspect on the desktop gives you full DevTools against this WebView.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        // Only on a genuine start: a recreate (locale, density — orientation
        // and uiMode are in configChanges) arrives holding the same intent,
        // and acting on it again would open a second tab on the same link or
        // raise the shortcut's sheet over whatever the user had moved on to.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val accent by vm.accentTheme.collectAsStateWithLifecycle()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val special by vm.specialTheme.collectAsStateWithLifecycle()
            // The private space recolors the whole app (see BrowserTheme), so
            // it has to be known up here, above BrowserScreen, rather than at
            // each screen that wants to look private.
            val privateMode by vm.privateMode.collectAsStateWithLifecycle()
            val translucentSheets by vm.translucentSheets.collectAsStateWithLifecycle()
            val translucency by vm.translucency.collectAsStateWithLifecycle()
            // Default and Nothing only: TUI and 98 are opaque by nature, and
            // Aero's chrome is glass already.
            val frosted = translucentSheets && com.yuku.browser.ui.theme.aeroRefractionSupported &&
                (special == SpecialTheme.Default || special == SpecialTheme.Nothing)
            ThemeCrossfadeHost {
                BrowserTheme(accent = accent, themeMode = themeMode, special = special, privateMode = privateMode) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.yuku.browser.ui.theme.LocalFrosted provides frosted,
                        com.yuku.browser.ui.theme.LocalFrostOpacity provides translucency,
                    ) {
                        BrowserScreen(vm)
                    }
                }
            }
        }
    }

    /**
     * Holds the splash screen by vetoing draws of the content view until the
     * saved session is back (`BrowserViewModel.startupComplete`) — the same
     * trick androidx.core:core-splashscreen's `setKeepOnScreenCondition`
     * plays, done by hand rather than for one dependency. The splash is
     * dismissed by the system the moment the app's first frame lands, so
     * without this the app would draw a browser with no tabs in it and fill
     * them in a frame later.
     *
     * Everything the app does to start up is already running underneath —
     * this only withholds the picture, it doesn't delay the work. On a cold
     * start it then holds a little longer, to [SPLASH_MIN_MS] from process
     * start, because that splash has an animation worth seeing the middle of;
     * on the fallback path it releases the moment the session is back.
     *
     * WHICH SPLASH IS ON SCREEN CANNOT BE ASKED, only inferred, and
     * [savedInstanceState] is the inference. The theme registered through
     * `setSplashScreenTheme` is honoured only when the ActivityRecord is
     * created; a process killed with its task still in recents is relaunched
     * INTO the existing record and gets Theme.Browser's still face instead.
     * That record is also what holds the saved state bundle — so a non-null
     * bundle here means the record outlived the process, which is the same
     * condition, arrived at from the other side. It is a correlation and not
     * a read (the registration is write-only from here — see
     * [followThemeOnSplash]), but it is exact in both directions that matter:
     * a launcher start of a dead task has no bundle and no fallback, and a
     * relaunch into a live record has both. Getting it wrong costs a second
     * either way, never a wrong picture.
     *
     * A recreate also arrives with a bundle, and takes the early return above
     * rather than this branch.
     */
    private fun keepSplashUpWhileRestoring(savedInstanceState: Bundle?) {
        // Already restored means this is a recreate, not a cold start: the
        // ViewModel outlives the Activity, and there's no splash behind a
        // recreate to hold — vetoing draws there would just be a blank
        // second.
        if (vm.startupComplete.value) {
            holdSplash = false
            return
        }
        val cycling = savedInstanceState == null
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (holdSplash) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        )
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_MAX_MS) { vm.startupComplete.first { it } }
            if (cycling) {
                val shown = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                if (shown < SPLASH_MIN_MS) delay(SPLASH_MIN_MS - shown)
            }
            holdSplash = false
            // Nothing else is asking for a draw at this point — the veto ate
            // the pending one — so the pass that releases the splash has to
            // be requested here.
            content.invalidate()
        }
    }

    /**
     * Keeps the splash on the theme picked in Settings — the kaomoji's
     * colour, and under a special theme the ground behind it too.
     *
     * The splash is drawn by the system before a line of the app has run, so
     * it can't be told a colour at the time — the only handle is
     * [android.window.SplashScreen.setSplashScreenTheme], which registers the
     * theme to draw the *next* splash of this activity with. So a swatch
     * change shows up on the launch after the one that made it, and the
     * flow is collected (rather than read once) so that second launch is
     * already right whenever it comes.
     *
     * The same wire carries the one other thing about the splash that can't
     * be decided while it's on screen: WHICH ORDER the twenty cats cycle in.
     * A drawable can't be picked at splash time either — only a theme can —
     * so every order exists as a theme of its own (twenty shuffles crossed
     * with the twelve inks, generated into values-v31/splash_orders.xml by
     * tools/render_splash_cats.swift), and the choice is which one to name.
     * It's drawn fresh here, so the cat that opens the splash is a different
     * one each launch rather than always the first frame of one fixed list.
     *
     * ALL OF THAT IS THE COLD-START SPLASH, and it is the only one this
     * function can reach. The launch that ignores everything registered here
     * gets Theme.Browser's own splash instead — one still face, grey, on a
     * plain ground — which is a different splash on purpose rather than this
     * one with the colour missing. See values-v31/themes.xml, and
     * [keepSplashUpWhileRestoring] for the hold that goes with each.
     *
     * A swatch's theme differs from Theme.Browser in `splashInk` alone
     * (values/themes.xml), and an order theme adds the icon to that; the
     * arrays are how the pair is looked up without a 240-branch `when`.
     * [AccentTheme.Dynamic] has one of its own like every other accent, even
     * though its ink is the system palette: Theme.Browser is what the system
     * draws when NOTHING is registered — the first launch after install, and
     * any launch where the platform has dropped the registration (which it
     * does; the value lives in PackageManager's per-user state for this
     * package, and we can write it but never read it back) — and that
     * fallback has to be the app's own neutral. A user who picked Blue and
     * gets the wallpaper's accent is looking at a splash that belongs to no
     * app; the neutral at least belongs to this one.
     *
     * A [SpecialTheme] moves the splash GROUND as well (values-v31/themes.xml)
     * — the one thing a swatch never does — because that ground is the first
     * frame's canvas, and under TUI or Nothing that canvas is not the app's
     * own @color/splash_bg_app. The INK stays the accent's: a swatch tints those two
     * looks rather than being replaced by them (ColorScheme.tintedWith), so
     * the cat has to be the colour the app is about to open in and not the
     * look's own out-of-the-box accent, which is what it used to be. Hence
     * the (look, accent) cross product of arrays. [SpecialTheme.Ninety8] is
     * the exception at both ends — it replaces the accent instead of taking
     * it, so it keeps one splash of its own — and [SpecialTheme.Default] is
     * not a look of its own and is the accent's alone.
     *
     * API 31+ only, which is also the only place there's a splash screen to
     * theme: below it the splash is the window background, fixed at whatever
     * the app was built with.
     */
    private fun followThemeOnSplash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        lifecycleScope.launch {
            combine(vm.accentTheme, vm.specialTheme) { accent, special ->
                when (special) {
                    // 98 is the one look the accent does not reach, so it is
                    // the one splash that doesn't vary with the swatch.
                    SpecialTheme.Ninety8 -> R.array.splash_orders_98
                    SpecialTheme.Tui -> when (accent) {
                        AccentTheme.Dynamic -> R.array.splash_orders_tui_dynamic
                        AccentTheme.Graphite -> R.array.splash_orders_tui_graphite
                        AccentTheme.Red -> R.array.splash_orders_tui_red
                        AccentTheme.Orange -> R.array.splash_orders_tui_orange
                        AccentTheme.Yellow -> R.array.splash_orders_tui_yellow
                        AccentTheme.Green -> R.array.splash_orders_tui_green
                        AccentTheme.Teal -> R.array.splash_orders_tui_teal
                        AccentTheme.Blue -> R.array.splash_orders_tui_blue
                        AccentTheme.Purple -> R.array.splash_orders_tui_purple
                        AccentTheme.Pink -> R.array.splash_orders_tui_pink
                    }
                    SpecialTheme.Nothing -> when (accent) {
                        AccentTheme.Dynamic -> R.array.splash_orders_nothing_dynamic
                        AccentTheme.Graphite -> R.array.splash_orders_nothing_graphite
                        AccentTheme.Red -> R.array.splash_orders_nothing_red
                        AccentTheme.Orange -> R.array.splash_orders_nothing_orange
                        AccentTheme.Yellow -> R.array.splash_orders_nothing_yellow
                        AccentTheme.Green -> R.array.splash_orders_nothing_green
                        AccentTheme.Teal -> R.array.splash_orders_nothing_teal
                        AccentTheme.Blue -> R.array.splash_orders_nothing_blue
                        AccentTheme.Purple -> R.array.splash_orders_nothing_purple
                        AccentTheme.Pink -> R.array.splash_orders_nothing_pink
                    }
                    SpecialTheme.Aero -> when (accent) {
                        AccentTheme.Dynamic -> R.array.splash_orders_aero_dynamic
                        AccentTheme.Graphite -> R.array.splash_orders_aero_graphite
                        AccentTheme.Red -> R.array.splash_orders_aero_red
                        AccentTheme.Orange -> R.array.splash_orders_aero_orange
                        AccentTheme.Yellow -> R.array.splash_orders_aero_yellow
                        AccentTheme.Green -> R.array.splash_orders_aero_green
                        AccentTheme.Teal -> R.array.splash_orders_aero_teal
                        AccentTheme.Blue -> R.array.splash_orders_aero_blue
                        AccentTheme.Purple -> R.array.splash_orders_aero_purple
                        AccentTheme.Pink -> R.array.splash_orders_aero_pink
                    }
                    SpecialTheme.Default -> when (accent) {
                        AccentTheme.Dynamic -> R.array.splash_orders_dynamic
                        AccentTheme.Graphite -> R.array.splash_orders_graphite
                        AccentTheme.Red -> R.array.splash_orders_red
                        AccentTheme.Orange -> R.array.splash_orders_orange
                        AccentTheme.Yellow -> R.array.splash_orders_yellow
                        AccentTheme.Green -> R.array.splash_orders_green
                        AccentTheme.Teal -> R.array.splash_orders_teal
                        AccentTheme.Blue -> R.array.splash_orders_blue
                        AccentTheme.Purple -> R.array.splash_orders_purple
                        AccentTheme.Pink -> R.array.splash_orders_pink
                    }
                }
            }.collectLatest { orders -> splashScreen.setSplashScreenTheme(randomOrder(orders)) }
        }
    }

    /**
     * One of the twenty order themes in [orders], at random. Falls back to
     * [Resources.ID_NULL] — i.e. the manifest's own Theme.Browser and its
     * still fallback face — if the array can't be read, which is the same
     * thing this did before there were orders to pick from.
     */
    private fun randomOrder(orders: Int): Int {
        val styles = resources.obtainTypedArray(orders)
        try {
            if (styles.length() == 0) return Resources.ID_NULL
            return styles.getResourceId(Random.nextInt(styles.length()), Resources.ID_NULL)
        } finally {
            // Recycled by hand rather than with `use`: TypedArray only
            // became AutoCloseable in API 31.
            styles.recycle()
        }
    }

    // The switcher captures previews as it opens, but the last thing the user
    // looked at is usually a fullscreen page that was never captured — grab
    // every live tab on the way out so a restored session comes back with
    // previews of what was actually on screen, not of the last time the
    // switcher happened to be opened.
    //
    // The navigation states go with them, for the same window of time and the
    // same reason: the process can be killed at any point after this, and
    // between them these two are what a relaunch shows instead of a blank
    // page — the preview covers the load, and the parked state is what makes
    // the page arrive back at the same place the preview shows it.
    // An overlay opened from another app (CustomTabActivity) writes a
    // bookmark straight into the store, since its own session is ephemeral
    // and never saved. This session's copy is therefore stale on the way
    // back, and its next save would put that stale list back on disk.
    override fun onStart() {
        super.onStart()
        vm.reloadBookmarks()
    }

    // The last true picture of the page the user was actually on. By onStop
    // the window has no surface left to copy out of, so everything taken there
    // is a software re-draw — blank for a canvas or a composited page — and the
    // card of the page you were reading came back empty. Here the page is still
    // on screen and still uncovered, and the copy lands during the transition
    // out, in time for onStop's flush to write it.
    override fun onResume() {
        super.onResume()
        vm.setHostResumed(true)
    }

    override fun onPause() {
        super.onPause()
        vm.capturePageNow()
        vm.setHostResumed(false)
    }

    override fun onStop() {
        super.onStop()
        vm.captureAndFlushThumbnails()
        vm.savePageStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.detachHost(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return
                // Into whichever space is open: a tab created in the other one
                // would be invisible until the user happened to switch over to
                // it.
                vm.newTab(url = url)
            }
            // The launcher's long-press shortcuts (res/xml/shortcuts.xml).
            // Deliberately actions of our own rather than an extra on VIEW,
            // which already means "a link from another app" and is answered
            // with a tab on that URL.
            ACTION_NEW_TAB -> vm.requestNewTabSheet(private = false)
            ACTION_NEW_PRIVATE_TAB -> vm.requestNewTabSheet(private = true)
            // A site's notification, tapped. See core/WebNotifications.kt.
            WebNotifications.ACTION_OPEN -> vm.openFromNotification(
                tabId = intent.getLongExtra(WebNotifications.EXTRA_TAB_ID, 0L),
                url = intent.getStringExtra(WebNotifications.EXTRA_URL).orEmpty(),
                key = intent.getStringExtra(WebNotifications.EXTRA_KEY).orEmpty(),
            )
        }
    }

    private companion object {
        const val ACTION_NEW_TAB = "com.yuku.browser.action.NEW_TAB"
        const val ACTION_NEW_PRIVATE_TAB = "com.yuku.browser.action.NEW_PRIVATE_TAB"
    }
}
