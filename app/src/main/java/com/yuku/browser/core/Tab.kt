package com.yuku.browser.core

import android.graphics.Bitmap

/**
 * One browser tab. The WebView itself is deliberately NOT here — it lives in
 * BrowserViewModel's view map so it survives recomposition. This is state only.
 */
data class Tab(
    val id: Long,
    val url: String = HOME,
    val title: String = "",
    val host: String = "",
    val isPrivate: Boolean = false,
    /**
     * The tab this one was opened FROM — "Open in new tab" on a link, and
     * nothing else. It is what back falls through to once the child has no
     * page history of its own left to walk: the tab is closed and the one it
     * came from comes back, rather than the app closing under a link the user
     * only meant to glance at. In memory only, like everything else here that
     * isn't in the saved tab list — after a relaunch a tab stands alone.
     */
    val openerTabId: Long = 0L,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = true,
    val loading: Boolean = false,
    val progress: Int = 0,
    val favicon: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    /** Actual page pixels below the card crop; memory-only, never browser chrome. */
    val thumbnailFull: Bitmap? = null,
    /**
     * The page as it showed under the status bar when [thumbnail] was taken
     * (see PageTopInset.setStrip) — drawn above the preview in cards and in
     * the shrink, so they carry the page there rather than a colour.
     */
    val thumbnailTop: Bitmap? = null,
    // True when the MAIN FRAME request failed to load (network error, DNS
    // failure, or an HTTP error status) — never set for subresources, so one
    // broken image or blocked tracker doesn't blank out the whole page.
    val loadFailed: Boolean = false,
    /**
     * What went wrong, when [loadFailed] is true — the code and description
     * the engine itself reported, so the error screen can say which failure
     * this was rather than naming the two most likely ones. Null when the
     * failure carried nothing worth printing (or when nothing failed).
     */
    val loadError: LoadError? = null,
) {
    val label: String get() = title.ifEmpty { host.ifEmpty { "New tab" } }

    companion object {
        const val HOME = "https://grapheneos.org"
    }
}

/**
 * A main-frame load failure. Two shapes reach it: a NETWORK error, whose
 * [code] is one of `WebViewClient.ERROR_*` (negative, e.g. -2 for a host
 * that doesn't resolve), and an HTTP error STATUS (404, 500, …), which the
 * [http] flag is what tells apart — the two numberings overlap in neither
 * direction but nothing about the number itself says which it is.
 *
 * [description] is what the engine said (`net::ERR_NAME_NOT_RESOLVED`, or a
 * reason phrase). It is kept but not shown: see [label].
 */
data class LoadError(
    val code: Int,
    val description: String,
    val http: Boolean,
) {
    /**
     * What the error screen prints — one plain lowercase phrase, in the
     * caption voice of the rest of the placeholders.
     *
     * Chromium's own string is the honest answer and the wrong one to put
     * under a sleeping cat: `net::ERR_NAME_NOT_RESOLVED` is a symbol for
     * people who already know what it means, and to everyone else it is
     * noise where a sentence should be. The number is dropped with it — it
     * is a second name for the same fact, and the phrase is the one worth
     * the line. An UNMAPPED failure keeps the number, since there the phrase
     * would be "something went wrong" and the code is all that distinguishes
     * it.
     */
    val label: String get() = if (http) httpLabel() else networkLabel()

    private fun httpLabel(): String = when (code) {
        400 -> "bad request"
        401 -> "login required"
        402 -> "payment required"
        403 -> "access denied"
        404 -> "page not found"
        405 -> "not allowed here"
        408 -> "the site took too long"
        410 -> "page gone for good"
        418 -> "the site is a teapot"
        429 -> "too many requests"
        451 -> "blocked for legal reasons"
        500 -> "the site broke"
        502 -> "the site's server is unreachable"
        503 -> "the site is unavailable"
        504 -> "the site took too long"
        // Everything else in the two families says which family it was in,
        // which is the useful half: a 4xx is about the request, a 5xx is the
        // site's own fault and worth trying again.
        in 400..499 -> "the site refused ($code)"
        in 500..599 -> "the site broke ($code)"
        else -> "http $code"
    }

    private fun networkLabel(): String = when (code) {
        // WebViewClient's constants, spelled out rather than referenced:
        // core/ has no view dependency of its own and these numbers are
        // frozen API.
        -1 -> "something went wrong"        // ERROR_UNKNOWN
        -2 -> "site not found"              // ERROR_HOST_LOOKUP
        -3 -> "unsupported sign-in"         // ERROR_UNSUPPORTED_AUTH_SCHEME
        -4 -> "sign-in failed"              // ERROR_AUTHENTICATION
        -5 -> "the proxy wants a sign-in"   // ERROR_PROXY_AUTHENTICATION
        -6 -> "couldn't connect"            // ERROR_CONNECT
        -7 -> "the connection dropped"      // ERROR_IO
        -8 -> "the site took too long"      // ERROR_TIMEOUT
        -9 -> "the site keeps redirecting"  // ERROR_REDIRECT_LOOP
        -10 -> "the browser can't open this" // ERROR_UNSUPPORTED_SCHEME
        -11 -> "the secure connection failed" // ERROR_FAILED_SSL_HANDSHAKE
        -12 -> "that address doesn't work"  // ERROR_BAD_URL
        -13 -> "couldn't read the file"     // ERROR_FILE
        -14 -> "file not found"             // ERROR_FILE_NOT_FOUND
        -15 -> "too many requests"          // ERROR_TOO_MANY_REQUESTS
        -16 -> "blocked as unsafe"          // ERROR_UNSAFE_RESOURCE
        else -> "something went wrong ($code)"
    }
}

data class HistoryEntry(
    val title: String,
    val url: String,
    val host: String,
    val visitCount: Int = 1,
    val favicon: Bitmap? = null,
)
