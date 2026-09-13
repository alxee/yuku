package com.yuku.browser.core

/** Identified by [url] — that's what "this page is bookmarked" checks against. */
data class BookmarkEntry(
    val url: String,
    val title: String,
    val host: String,
)
