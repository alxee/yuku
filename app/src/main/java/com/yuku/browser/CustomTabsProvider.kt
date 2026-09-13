package com.yuku.browser

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken

/**
 * What makes an app choose us for its in-app browser.
 *
 * An app that wants an overlay (Telegram, Slack, Reddit, anything using
 * `CustomTabsIntent`) first asks the package manager which browser exports a
 * service with this action; without one we're not a candidate at all and the
 * call degrades to a plain VIEW intent. So the *existence* of this service is
 * most of its job — [CustomTabActivity] is where the tab is actually drawn,
 * launched by the ordinary VIEW intent the client sends afterwards.
 *
 * Note this is only ever consulted for apps that ASK. An app with its own
 * embedded WebView never sends anything, and nothing here changes that — a
 * built-in browser is the app's decision to make, and we don't get a vote.
 */
class CustomTabsProvider : CustomTabsService() {

    /**
     * Loading Chromium's native library and starting a renderer is the
     * expensive half of the first page, and it happens in this process — so
     * doing it now, while the user is still looking at the app they'll tap
     * the link in, is exactly what the API is for. One WebView is enough to
     * pull the library in; it's dropped immediately, since what we want kept
     * is the process-wide initialisation it did, not the view.
     */
    override fun warmup(flags: Long): Boolean {
        Handler(Looper.getMainLooper()).post {
            if (warmed) return@post
            warmed = true
            runCatching {
                WebView(applicationContext).destroy()
                CookieManager.getInstance()
            }.onFailure { Log.w(TAG, "warmup failed: $it") }
        }
        return true
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean = true

    /**
     * Honest about its ceiling: there is no public WebView API to prerender or
     * even preconnect a URL from outside a live page, so the most this can do
     * is make sure the engine itself is up. Reported as `true` because that
     * work does happen and does make the launch faster — not as a claim that
     * the page itself is already being fetched.
     */
    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?,
    ): Boolean {
        warmup(0)
        return true
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? = null

    /**
     * The client asking to restyle a tab that is already open. Our header
     * doesn't take the caller's colours in the first place (see
     * [com.yuku.browser.core.CustomTabRequest]), so there is nothing here to
     * update, and saying so is better than silently ignoring it.
     */
    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean = false

    override fun requestPostMessageChannel(
        sessionToken: CustomTabsSessionToken,
        postMessageOrigin: Uri,
    ): Boolean = false

    override fun postMessage(
        sessionToken: CustomTabsSessionToken,
        message: String,
        extras: Bundle?,
    ): Int = RESULT_FAILURE_DISALLOWED

    override fun validateRelationship(
        sessionToken: CustomTabsSessionToken,
        relation: Int,
        origin: Uri,
        extras: Bundle?,
    ): Boolean = false

    override fun receiveFile(
        sessionToken: CustomTabsSessionToken,
        uri: Uri,
        purpose: Int,
        extras: Bundle?,
    ): Boolean = false

    private companion object {
        const val TAG = "CustomTabsProvider"

        /** Process-wide: the engine only needs pulling in once. */
        @Volatile
        var warmed = false
    }
}
