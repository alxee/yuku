package com.yuku.browser.core

/** Whether the app's own chrome (not page content) renders light or dark. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}
