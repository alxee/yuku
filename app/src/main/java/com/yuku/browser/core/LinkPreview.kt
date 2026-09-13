package com.yuku.browser.core

/**
 * A link being looked at without being gone to — what a long press on an
 * anchor raises instead of a menu (see `ui/LinkPreviewOverlay.kt`).
 *
 * Plain data, no Compose, per the `core/` rule in CLAUDE.md. The page itself
 * is a real WebView the ViewModel owns for the length of the preview, which is
 * the second live WebView on screen: the tab's own is still there underneath.
 *
 * Deliberately thin. The card shows nothing but the page — no address bar, no
 * title, no progress — so the only thing worth holding here is WHERE the
 * preview has got to, which the actions under it act on. Everything else a
 * page reports about itself has no reader, and state with no reader is state
 * that goes wrong quietly.
 */
data class LinkPreview(
    /**
     * Identity for this one preview, so its WebView, and the callbacks coming
     * back off it, can never be confused with the previous preview's — a card
     * dismissed and another raised inside its own exit animation is two live
     * views for a few frames.
     */
    val token: Long,
    /** The tab the link was pressed on, so a tab switch can take the preview with it. */
    val tabId: Long,
    /** The link that was pressed. Never changes, unlike [url]. */
    val requestedUrl: String,
    /** Private tabs preview in their own profile; nothing here reaches the ordinary jar. */
    val isPrivate: Boolean = false,
    /**
     * Where the preview is now — not the same as [requestedUrl] the moment a
     * link inside the card is followed. Open, open in a new tab and copy all
     * act on this, so browsing inside the preview and then promoting it lands
     * on the page actually being read.
     */
    val url: String = requestedUrl,
)
