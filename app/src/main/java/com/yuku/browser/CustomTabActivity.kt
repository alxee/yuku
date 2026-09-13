package com.yuku.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.CustomTabEvents
import com.yuku.browser.core.CustomTabRequest
import com.yuku.browser.ui.ActionModeOwner
import com.yuku.browser.ui.CustomTabScreen
import com.yuku.browser.ui.theme.BrowserTheme

/**
 * One page from another app, drawn over that app.
 *
 * This is the whole of the "overlay browser": an Activity that lands in the
 * CALLING app's task (its affinity is empty and nothing here asks for a new
 * task, so `startActivity` from Telegram stacks it on Telegram's own back
 * stack) with a header instead of the full browser's chrome. Closing it pops
 * that stack and the user is back in the conversation they were in — no tab
 * of theirs was touched, and none of this is in the session the launcher
 * icon opens.
 *
 * The session behind it is [BrowserViewModel.EphemeralFactory]: their
 * settings, none of their state, and nothing written back. Both this and
 * [MainActivity] can be alive at once, and two ViewModels writing to one
 * store would be two browsers saving over each other.
 *
 * [MainActivity] keeps LAUNCHER and APP_BROWSER; the VIEW filter is here now,
 * so every link from outside opens this way. The way to the full browser is
 * "Open in Yuku" in the overflow, which hands the URL over and finishes.
 */
class CustomTabActivity : ComponentActivity(), ActionModeOwner {

    private val vm: BrowserViewModel by viewModels { BrowserViewModel.EphemeralFactory }

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

    /** The intent this overlay was opened for — null once it has been consumed. */
    private var request: CustomTabRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The overlay's page gets the keyboard's saved-login suggestions on
        // the same terms the browser's own tabs do — see
        // BrowserViewModel.webHostContext — and, like there, this has to be
        // in place before the WebView is built.
        vm.attachHost(this)

        val opened = CustomTabRequest.from(intent)
        if (opened == null) {
            // A VIEW intent with nothing to view. There is no page to put in
            // an overlay and no caller state to preserve, so hand the user
            // the browser proper rather than an empty header.
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // Settings > Behavior > "Links from other apps": the user can have
        // these open as an ordinary tab in the browser proper instead. Read
        // straight off the ephemeral session, whose settings are restored
        // synchronously in its init — so this is answered before anything is
        // drawn, and the overlay's own window never appears.
        if (!vm.openExternalLinksInOverlay.value) {
            openInBrowser(opened.url, animated = false)
            return
        }
        request = opened
        // Survives a recreation (rotation, theme change): the ViewModel
        // outlives the Activity, so its tab is the one already loading.
        if (vm.tabs.value.isEmpty()) vm.newTab(url = opened.url)

        setContent {
            val accent by vm.accentTheme.collectAsStateWithLifecycle()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val special by vm.specialTheme.collectAsStateWithLifecycle()
            BrowserTheme(accent = accent, themeMode = themeMode, special = special) {
                CustomTabScreen(
                    vm = vm,
                    request = opened,
                    onClose = { finish() },
                    onOpenInBrowser = ::openInBrowser,
                )
            }
        }
    }

    /**
     * A second link arriving while this overlay is up. Only reachable when
     * the system routes one here rather than stacking a fresh instance (see
     * the manifest's standard launch mode) — either way the page the user is
     * looking at should become the one they just tapped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = CustomTabRequest.from(intent) ?: return
        request = next
        vm.load(next.url)
    }

    override fun onStart() {
        super.onStart()
        CustomTabEvents.tabShown(request?.token)
    }

    override fun onStop() {
        super.onStop()
        CustomTabEvents.tabHidden(request?.token)
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.detachHost(this)
    }

    /**
     * Hands the page to the full browser as a new tab and gets out of the
     * way. A fresh task deliberately: the point of this row is to LEAVE the
     * caller's stack, so the browser must not be sitting on top of the chat
     * the link came from.
     */
    private fun openInBrowser(url: String, animated: Boolean = true) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        finish()
        // Handing the link straight on (the setting above) means this
        // Activity is a doorway nobody should see: its theme's fade would
        // otherwise play over the caller for a window that has no content in
        // it. From the row in the menu it IS a transition the user asked for,
        // so that one keeps the animation.
        @Suppress("DEPRECATION")
        if (!animated) overridePendingTransition(0, 0)
    }
}
