package com.yuku.browser.core

/**
 * Whether page CONTENT renders dark — distinct from [ThemeMode], which is
 * the app's own chrome. The two are deliberately separate settings: a dark
 * browser around a blinding white page is the common complaint, and someone
 * who pins the app to Light may still want dark pages (and vice versa).
 *
 * [System] ties page darkening to the app's resolved dark state, which for
 * ThemeMode.System is in turn the device's night setting.
 */
enum class PageDarkMode(val label: String) {
    System("System"),
    Off("Off"),
    On("On"),
}
