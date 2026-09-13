package com.yuku.browser.core

/**
 * Just the identity of each option — kept free of any Compose/Color
 * dependency so core stays framework-agnostic. ui/theme maps these to
 * actual colors (and, for [Dynamic], to Android's own Material You scheme).
 */
enum class AccentTheme(val label: String) {
    /** Follows the system's wallpaper-derived Material You palette (Android 12+). */
    Dynamic("Auto"),
    Graphite("Graphite"),
    Red("Red"),
    Orange("Orange"),
    Yellow("Yellow"),
    Green("Green"),
    Teal("Teal"),
    Blue("Blue"),
    Purple("Purple"),
    Pink("Pink"),
}
