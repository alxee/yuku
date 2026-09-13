package com.yuku.browser.core

/**
 * How the tab manager (switcher) presents the tabs. Named after the shape of
 * each tab's own card, not the scroll axis: Vertical is the zoomed carousel
 * (tall, portrait-shaped cards) and Horizontal is the scrolling list sheet
 * (wide, landscape-shaped rows). The setting is the whole of the answer — the
 * tabs button only ever opens and closes the manager, and there is no
 * in-place switch between the two presentations.
 */
enum class TabManagerMode(val label: String) {
    Vertical("Vertical"),
    Horizontal("Horizontal"),
}
