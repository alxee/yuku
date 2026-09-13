package com.yuku.browser.core

/**
 * What one site gets that the rest of the web does not.
 *
 * The three switches the ad blocker owns are here as EXCEPTIONS rather than
 * as settings of their own: each one is on unless this site turned it off, so
 * the whole record is "the ways in which this site is not ordinary". That is
 * deliberate and it is what makes the defaults safe — a site the user has
 * never opened this sheet for is filtered exactly like every other, and a
 * build that adds a filter does not have to go back and answer for every site
 * saved before it existed. It also matches what the switch is FOR: the reason
 * anyone reaches for a per-site ad blocker toggle is a page that broke.
 *
 * [dark] and [zoom] are the other kind, and they are nullable for it. Those
 * two have a global answer that is a preference rather than a protection, and
 * both directions of override are worth having — pin this one site dark under
 * a light setting, or one site at 150% because its body text is 12px. Null is
 * "follow the app", which is not the same as either value.
 *
 * Keyed by registrable domain ([UrlUtils.registrableDomain]), like history,
 * bookmarks and the password vault, and unlike [SitePermissionGrant], which
 * is keyed by origin. The difference is what is at stake: a permission hands
 * a capability to whatever is running on the page, so `ads.example.com` must
 * not inherit `maps.example.com`'s camera. Nothing here is a capability —
 * these are how a site is drawn and filtered — and a user who turns the
 * blocker off for a site means the site, not one of its subdomains.
 */
data class SiteSettings(
    val blockAds: Boolean = true,
    val blockTrackers: Boolean = true,
    val hideCookieBanners: Boolean = true,
    /** Darken this site's pages, whatever the app setting says. Null: follow it. */
    val dark: Boolean? = null,
    /** Text scale for this site, as a percentage from [ZOOM_STEPS]. Null: follow. */
    val zoom: Int? = null,
) {
    /**
     * Whether this record says anything at all. A site that has come back to
     * ordinary is REMOVED rather than stored as a row of defaults — the list
     * in Settings is meant to be the sites that differ, and a row that
     * describes the same treatment every other site gets is one the user has
     * to read before finding out it means nothing.
     */
    val isDefault: Boolean
        get() = blockAds && blockTrackers && hideCookieBanners && dark == null && zoom == null
}

/** One site's record, as it is stored and as the settings list draws it. */
data class SiteSettingsEntry(val site: String, val settings: SiteSettings)
