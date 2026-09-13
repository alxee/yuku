package com.yuku.browser.core

/**
 * What a long press on a page landed on — the input to the link/image context
 * menu. Plain data, no Compose (see the `core/` rule in CLAUDE.md): the UI
 * decides which rows a target earns from which of [linkUrl] / [imageUrl] is
 * present.
 *
 * [x] / [y] are the finger's raw screen coordinates at the moment of the
 * press. The app is edge-to-edge, so its Compose root spans the whole window
 * and those are directly usable as layout coordinates for anchoring the menu
 * to where the press happened.
 */
data class WebContextTarget(
    val tabId: Long,
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    /** The anchor's text/title, when the page gave one — shown as the menu's header. */
    val label: String? = null,
    /**
     * What the image request has to carry to be answered like the page's own
     * was, read off the tab's WebView at the press: its user-agent (hosts
     * such as upload.wikimedia.org refuse a library's default one with a 403)
     * and the cookies for [imageUrl] from the jar that tab actually uses — a
     * private tab's is not `CookieManager.getInstance()`.
     */
    val userAgent: String? = null,
    val cookies: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
)
