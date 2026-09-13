package com.yuku.browser.core

/**
 * Where a link opened in a new tab actually goes: onto the screen, or into
 * the row behind the page the user is still reading.
 *
 * Only ever asked about links leaving a page the user is ON — the context
 * menu's "Open in new tab" and the link preview's "New tab". A tab opened
 * from the new-tab sheet, a bookmark or an external intent is a tab the user
 * asked to be taken to, and none of those read this.
 */
enum class NewTabPlacement(val label: String) {
    /** Switch to it — what every other way of opening a page here does. */
    Foreground("Switch to it"),

    /** Leave it in the row and stay on the page the link was on. */
    Background("In background"),
}
