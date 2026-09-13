package com.yuku.browser.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView

/**
 * Runs a theme change under a picture of the screen as it was, and fades that
 * picture out — so a switch of special theme is a crossfade rather than a cut.
 *
 * A picture, not an interpolation, because most of what a special theme
 * changes has no in-between: a typeface, an icon set, a corner rule, a bevel.
 * Colours alone could be lerped the way light/dark is, but type snapping while
 * colours glide reads as two changes. One fade carries all of it.
 *
 * `PixelCopy` of the WINDOW, not `View.draw` — the latter re-renders through a
 * path that loses WebView/compositor content (see CLAUDE.md). The copy is
 * async (a frame or two), so the change itself is deferred until it lands and
 * then applied in the same frame the picture goes up: nothing new is ever on
 * screen uncovered.
 */
val LocalThemeCrossfade = staticCompositionLocalOf<(apply: () -> Unit) -> Unit> { { it() } }

private const val THEME_CROSSFADE_MS = 420

@Composable
fun ThemeCrossfadeHost(content: @Composable () -> Unit) {
    val view = LocalView.current
    var shot by remember { mutableStateOf<Bitmap?>(null) }
    // Plain state set to 1 in the SAME callback that puts the picture up. An
    // Animatable snapped inside the effect lands a frame late, and that frame
    // showed the new theme uncovered before the old picture came back over it.
    val fade = remember { mutableFloatStateOf(0f) }
    // A plain latch, not state: only the gesture path reads it.
    val copying = remember { booleanArrayOf(false) }

    val crossfade: (() -> Unit) -> Unit = remember(view) {
        crossfade@{ apply ->
            val window = view.context.findActivity()?.window
            if (copying[0] || window == null || !view.isAttachedToWindow || view.width == 0) {
                apply()
                return@crossfade
            }
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val source = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            val frame = try {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                apply()
                return@crossfade
            }
            copying[0] = true
            try {
                PixelCopy.request(window, source, frame, { result ->
                    copying[0] = false
                    if (result == PixelCopy.SUCCESS) {
                        fade.floatValue = 1f
                        shot = frame
                    } else {
                        frame.recycle()
                    }
                    apply()
                }, Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                copying[0] = false
                frame.recycle()
                apply()
            }
        }
    }

    val current = shot
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        animate(1f, 0f, animationSpec = tween(THEME_CROSSFADE_MS, easing = FastOutSlowInEasing)) { value, _ ->
            fade.floatValue = value
        }
        // Not recycled: the last display list drawn with it can still hold it
        // for a frame, and a recycled bitmap there is a crash. GC takes it.
        shot = null
    }

    CompositionLocalProvider(LocalThemeCrossfade provides crossfade) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val bitmap = shot ?: return@drawWithContent
                    // Read in draw, so the fade costs no recomposition.
                    val alpha = fade.floatValue
                    if (alpha <= 0f || bitmap.isRecycled) return@drawWithContent
                    drawImage(bitmap.asImageBitmap(), alpha = alpha)
                }
        ) {
            content()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
