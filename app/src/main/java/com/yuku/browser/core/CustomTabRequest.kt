package com.yuku.browser.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSessionToken

/**
 * What another app asked for when it opened a link in us.
 *
 * Every launch arrives as an ordinary ACTION_VIEW intent — a Custom Tabs one
 * is that same intent with extras hung off it, which is why an app that has
 * never heard of Custom Tabs and an app using [CustomTabsIntent] both land in
 * the same Activity. [token] is the difference: its presence is the caller
 * saying it expects an overlay it can talk to, and it's the handle the
 * navigation callbacks below go back through.
 *
 * Deliberately NOT honoured: `EXTRA_TOOLBAR_COLOR` and friends. Painting the
 * header in the calling app's brand colour is what Chrome does; here the
 * chrome is a designed thing that follows the user's own accent and their
 * light/dark setting, and a Telegram-blue bar in a violet private session is
 * not continuity, it's a clash. The caller's *menu items* are honoured, since
 * those are function rather than decoration. Neither is
 * `EXTRA_TITLE_VISIBILITY_STATE`: the header carries a working address bar
 * rather than a page title, so there is no title for the caller to hide.
 */
data class CustomTabRequest(
    val url: String,
    val token: CustomTabsSessionToken?,
    val menuItems: List<MenuItem>,
) {
    /** One row the calling app added to the overflow menu. */
    data class MenuItem(val label: String, val action: PendingIntent)

    /** True when the caller used [CustomTabsIntent] rather than a bare VIEW intent. */
    val fromCustomTabsClient: Boolean get() = token != null

    /**
     * Fires one of the caller's own menu items. The URL currently on screen
     * goes with it as the intent's data, which is the contract those items are
     * written against ("open THIS in the app").
     */
    fun send(context: Context, item: MenuItem, currentUrl: String) {
        runCatching {
            item.action.send(context, 0, Intent().setData(Uri.parse(currentUrl)))
        }.onFailure { Log.w(TAG, "menu item ${item.label} refused: $it") }
    }

    companion object {
        private const val TAG = "CustomTabRequest"

        fun from(intent: Intent?): CustomTabRequest? {
            val url = intent?.dataString?.takeIf { it.isNotBlank() } ?: return null
            // Reading the token unpacks a Binder the caller put in the
            // intent; a malformed one throws rather than returning null.
            val token = runCatching {
                CustomTabsSessionToken.getSessionTokenFromIntent(intent)
            }.getOrNull()
            return CustomTabRequest(
                url = url,
                token = token,
                menuItems = menuItems(intent),
            )
        }

        private fun menuItems(intent: Intent): List<MenuItem> {
            val bundles = runCatching {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(CustomTabsIntent.EXTRA_MENU_ITEMS, Bundle::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Bundle>(CustomTabsIntent.EXTRA_MENU_ITEMS)
                }
            }.getOrNull() ?: return emptyList()
            return bundles.mapNotNull { bundle ->
                val label = bundle.getString(CustomTabsIntent.KEY_MENU_ITEM_TITLE)?.takeIf { it.isNotBlank() }
                @Suppress("DEPRECATION")
                val action = if (Build.VERSION.SDK_INT >= 33) {
                    bundle.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT, PendingIntent::class.java)
                } else {
                    bundle.getParcelable<PendingIntent>(CustomTabsIntent.KEY_PENDING_INTENT)
                }
                if (label == null || action == null) null else MenuItem(label, action)
            }.filterNot { it.label.duplicatesOurs() }
        }

        /**
         * Rows we already offer, by name. Telegram's Custom Tabs launch adds
         * its own "Copy link" — every client is written against a browser
         * whose menu it can't see, so offering the same thing twice is the
         * normal case rather than a Telegram quirk. The caller's genuinely
         * own actions ("Open in Telegram", "Save to…") are what this list is
         * shaped to let through.
         */
        private val OWN_ACTIONS = setOf(
            "copy", "copy link", "copy url", "copy address",
            "share", "share link", "share via", "share page",
            "open in browser", "open in default browser", "open in yuku",
            "reload", "refresh", "reload page",
            "find in page", "find on page",
            "bookmark", "add bookmark", "add to bookmarks",
            "desktop site", "request desktop site",
        )

        /** Trailing ellipses and case are the usual difference; nothing else is. */
        private fun String.duplicatesOurs(): Boolean =
            trim().trimEnd('.', '…').trim().lowercase() in OWN_ACTIONS
    }
}

/**
 * The events a Custom Tabs client expects back from the browser it handed a
 * link to. Apps use them for real things — dismissing their own spinner,
 * knowing the user has come back — so they're reported even though nothing
 * here depends on them.
 *
 * Every call is best-effort: the callback is a Binder into another process
 * that may be gone, and a dead client must not take the overlay down with it.
 */
object CustomTabEvents {
    private const val TAG = "CustomTabEvents"

    fun navigationStarted(token: CustomTabsSessionToken?) =
        report(token, CustomTabsCallback.NAVIGATION_STARTED)

    fun navigationFinished(token: CustomTabsSessionToken?) =
        report(token, CustomTabsCallback.NAVIGATION_FINISHED)

    fun navigationFailed(token: CustomTabsSessionToken?) =
        report(token, CustomTabsCallback.NAVIGATION_FAILED)

    fun tabShown(token: CustomTabsSessionToken?) =
        report(token, CustomTabsCallback.TAB_SHOWN)

    fun tabHidden(token: CustomTabsSessionToken?) =
        report(token, CustomTabsCallback.TAB_HIDDEN)

    private fun report(token: CustomTabsSessionToken?, event: Int) {
        val callback = token?.callback ?: return
        runCatching { callback.onNavigationEvent(event, null) }
            .onFailure { Log.w(TAG, "client gone: $it") }
    }
}
