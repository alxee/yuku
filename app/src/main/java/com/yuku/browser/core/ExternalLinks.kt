package com.yuku.browser.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * What happens when a page navigates somewhere this browser is not the
 * obvious owner of: a `steam://` link, an `intent://` URL, or an ordinary
 * https address that an installed app has claimed for itself.
 *
 * There is one user-facing decision behind all of it — Settings > Behavior >
 * "Open links in apps" — and one rule that is NOT the user's to make: a
 * scheme the browser has nothing to render ([ALWAYS_EXTERNAL] — mail, phone,
 * SMS, maps, the store) always goes out. Refusing those would not keep the
 * link in the browser, it would drop it on the floor.
 *
 * Everything here is called from `shouldOverrideUrlLoading`, i.e. on the main
 * thread inside a navigation, so the package-manager work is kept to the case
 * that needs it: a user-driven main-frame navigation with the setting on.
 */
object ExternalLinks {

    /** What the WebView should do with a navigation. */
    sealed interface Route {
        /** Not ours to intercept — let the WebView load it. */
        data object Browser : Route

        /** Dealt with (launched, or deliberately dropped): the WebView must not load it. */
        data object Consumed : Route

        /** Load this instead — an `intent://` URL's `browser_fallback_url`. */
        data class LoadInstead(val url: String) : Route
    }

    /**
     * Schemes that leave whatever the setting says. A browser cannot show a
     * mail composer or dial a number, so "keep it here" is not one of the two
     * options — the only alternatives are handing it over or losing it.
     */
    private val ALWAYS_EXTERNAL = setOf("mailto", "tel", "sms", "smsto", "mms", "geo", "market")

    /** Schemes WebView renders itself; never a candidate for handing over. */
    private val WEB_INTERNAL = setOf("http", "https", "javascript", "about", "data", "blob", "file", "content")

    /**
     * Packages that answer for a URL no app can have claimed, i.e. the
     * general-purpose browsers on the device — this one included. They are
     * what an "is there an APP for this link" test has to subtract, or every
     * https link would look like it had a handler waiting.
     *
     * Cached for the life of the process: a browser being installed is rare,
     * and this is read inside a navigation.
     */
    private var browserPackages: Set<String>? = null

    fun route(
        context: Context,
        url: String,
        hasGesture: Boolean,
        isMainFrame: Boolean,
        openLinksInApps: Boolean,
    ): Route {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return Route.Browser

        if (scheme == "intent") return routeIntentUri(context, url, openLinksInApps)

        if (scheme !in WEB_INTERNAL) {
            // An app scheme. Nothing here can render it either way, so the
            // setting decides between handing it over and dropping it.
            //
            // Either way the WebView must not see it: with nothing installed
            // to answer, loading it would replace the page the user is
            // reading with WebView's own "webpage not available", which says
            // less than staying put.
            if (openLinksInApps || scheme in ALWAYS_EXTERNAL) {
                launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            return Route.Consumed
        }

        if (scheme != "http" && scheme != "https") return Route.Browser
        // Only a link the USER followed, and only the page itself: an iframe
        // or a redirect chain hopping into another app is a hijack, not a
        // choice. (`hasGesture` is false for every scripted navigation.)
        if (!openLinksInApps || !hasGesture || !isMainFrame) return Route.Browser

        return if (openInApp(context, url)) Route.Consumed else Route.Browser
    }

    /**
     * `intent://…#Intent;…;end` — the encoding an app uses to say "open me,
     * and here is a web page to fall back to if I'm not installed". The
     * fallback is honoured in BOTH directions: it is what the setting turned
     * off means, as well as what a missing app means.
     */
    private fun routeIntentUri(context: Context, url: String, openLinksInApps: Boolean): Route {
        val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?: return Route.Consumed
        val fallback = intent.getStringExtra("browser_fallback_url")
            ?.takeIf { Uri.parse(it).scheme?.lowercase() in setOf("http", "https") }

        if (openLinksInApps) {
            // A page composes this string, so it is untrusted input. The
            // selector can point the launch at something entirely different
            // from what the URI says, and the extra component/flags are the
            // page's, not ours — strip them to exactly a browsable VIEW.
            intent.selector = null
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.flags = 0
            if (launch(context, intent)) return Route.Consumed
        }
        return fallback?.let(Route::LoadInstead) ?: Route.Consumed
    }

    /**
     * Hand an https URL to the app that owns it, if there is one.
     *
     * From API 30 this CANNOT be answered by asking the package manager
     * first. Package visibility hides an app whose intent filter names a
     * host (`youtube.com`) from a `<queries>` entry that names only a scheme,
     * and there is no way to name every host a link might have — so
     * `queryIntentActivities` came back with the owner missing. On API 31+
     * `MATCH_DEFAULT_ONLY` makes it worse still: a web intent resolves only
     * to a handler APPROVED for the domain, so the list was empty outright.
     *
     * [Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER] asks the platform the
     * question instead of reconstructing its answer: start this, but only if
     * something other than a browser takes it. Nothing matching is an
     * [ActivityNotFoundException], i.e. "keep it here" — which is why this
     * is a launch attempt and not a query. It is also what enforces the
     * user's own choice: an app the user has not approved for the domain is
     * not a match, and the link stays in the browser.
     *
     * Below API 30 there is no such flag and no visibility filtering either,
     * so the package manager can still be asked, and the launch is pinned to
     * the package it named — an implicit VIEW would raise a chooser holding
     * every browser on the device, which is not what "open in the app" means.
     */
    private fun openInApp(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return launch(context, intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER))
        }
        val pkg = appHandlerFor(context, url) ?: return false
        return launch(context, intent.setPackage(pkg))
    }

    /**
     * The first installed app that claims this URL and is not a browser.
     * Pre-API-30 only; see [openInApp].
     *
     * `setPackage` on the launch rather than letting the system resolve it:
     * an implicit VIEW would put every browser on the device (and this one)
     * into a chooser, which is not what "open in the app" means.
     */
    private fun appHandlerFor(context: Context, url: String): String? {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val browsers = browserPackages(pm)
        return runCatching {
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
            ?.map { it.activityInfo.packageName }
            ?.firstOrNull { it != context.packageName && it !in browsers }
    }

    private fun browserPackages(pm: PackageManager): Set<String> = browserPackages ?: run {
        // A host in the reserved `.invalid` TLD: no app link can be verified
        // against it, so whatever answers here handles the web in general.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://link.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val found = runCatching {
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        found.also { browserPackages = it }
    }

    /** True if something took it. Every failure here is somebody else's app. */
    private fun launch(context: Context, intent: Intent): Boolean = try {
        // Started from the Application context in the ephemeral/overlay case
        // as well as from an Activity, so this flag is not optional.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        // An exported-but-permission-guarded activity, and anything else the
        // target app decided we may not start.
        Log.w("ExternalLinks", "refused: ${intent.data}", e)
        false
    }
}
