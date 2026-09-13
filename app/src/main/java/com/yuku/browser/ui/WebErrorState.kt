package com.yuku.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yuku.browser.core.ERROR_FADE_IN_MS
import com.yuku.browser.core.LoadError
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.PageBg

/** The face that stands in for the old "UH OH" headline — a sleeping cat. */
private const val ErrorFace = "/ᐠ - ˕ -マ ᶻ 𝗓 𐰁"

/**
 * How long the error screen stays up after the tab stops being failed, before
 * its fade begins. A failed page being retried stops being failed at the
 * retry's commit and fails again a moment later; inside this beat that return
 * reverses a fade that has not visibly started, instead of blinking the screen.
 */
private const val ERROR_EXIT_DELAY_MS = 120

/**
 * Overlaid on top of the (still-live, still-there) WebView when its main
 * frame fails to load — a network error or an HTTP error status. Unlike
 * [EmptyState] this is a message meant to be read, not a background
 * watermark, so its headline uses the accent color at full contrast. Same
 * [PlaceholderBlock] sizing as [EmptyState] otherwise — the two placeholders
 * are meant to read as one family of screens.
 *
 * The caption says WHICH failure this was ([LoadError.label] — a plain
 * phrase, not the engine's `net::` symbol or its number): "page down or
 * nonexistent" covered a name that doesn't resolve, a refused connection, a
 * timeout and a 500 alike, and named none of them. The generic line survives
 * only for a failure that arrived with no detail at all.
 */
@Composable
fun WebErrorState(error: LoadError? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        contentAlignment = Alignment.Center,
    ) {
        PlaceholderBlock(
            face = ErrorFace,
            faceColor = AccentColor,
            caption = error?.label ?: "page down or nonexistent",
            captionColor = InkMuted,
        )
    }
}

/**
 * [WebErrorState] faded in and out rather than cut — see [ERROR_EXIT_DELAY_MS]
 * for the wait before it leaves, and `BrowserViewModel.dismissCoversForError`
 * for why a cover over the page waits [ERROR_FADE_IN_MS] before lifting.
 *
 * Arrives on [PaneDecelerate], which is most of the way there in its first
 * frames: what is under it while it fades up is the engine's own error page.
 * The error is remembered for the way out, because by then the tab has
 * already forgotten it.
 */
@Composable
fun FadingWebErrorState(failed: Boolean, error: LoadError?) {
    val shown = remember { ErrorMemo() }
    if (failed) shown.error = error
    AnimatedVisibility(
        visible = failed,
        enter = fadeIn(tween(ERROR_FADE_IN_MS, easing = PaneDecelerate)),
        exit = fadeOut(
            tween(SURFACE_EXIT_FADE_MS, delayMillis = ERROR_EXIT_DELAY_MS, easing = Accelerate),
        ),
    ) {
        WebErrorState(shown.error)
    }
}

/** Not state: written by the composition that reads it. */
private class ErrorMemo {
    var error: LoadError? = null
}
