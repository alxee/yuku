package com.yuku.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.R
import com.yuku.browser.core.Favicons
import com.yuku.browser.core.SearchEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * A search engine's mark, shipped with the app for the built-ins and fetched
 * for the user's own.
 *
 * The engines the app offers are the one place where a favicon service is not
 * good enough. Everything else here is a site the user happened to visit, and
 * a missing icon there costs a lettered square; these five are chrome — the
 * strip in the omnibox IS them, and one blank in that row is a control the
 * user cannot read. What was actually being served makes the point: s2's
 * answer for bing.com is a 32px WHITE magnifier on a transparent ground,
 * which is invisible on the light bar it is drawn on and stayed invisible on
 * a light chip under it. These are each engine's own icon at the size they
 * are drawn ([Favicons.ICON_PX]), so there is nothing to fetch, nothing to
 * cache and nothing to miss.
 *
 * A CUSTOM engine keeps the ordinary path: it is whatever URL the user pasted,
 * so its host's favicon — network-fetched and cached on disk like every other
 * site's — is the only icon there is, with [SiteIcon]'s lettered square behind
 * it when even that comes back with nothing in it.
 */
private val BUNDLED = mapOf(
    SearchEngine.DuckDuckGo.id to R.drawable.engine_duckduckgo,
    SearchEngine.Google.id to R.drawable.engine_google,
    SearchEngine.Bing.id to R.drawable.engine_bing,
    SearchEngine.Brave.id to R.drawable.engine_brave,
    SearchEngine.Startpage.id to R.drawable.engine_startpage,
)

/** Decoded once per process: five bitmaps, drawn from a dozen call sites. */
private val decoded = ConcurrentHashMap<Int, Bitmap>()

/**
 * [SiteIcon] for a search engine — same tile, same corner, same lettered
 * fallback, with the bundled mark handed in as an already-captured icon so the
 * ground logic there (a mark gets a chip, a tile does not) applies to these
 * exactly as it does to a site's own.
 */
@Composable
fun EngineIcon(
    engine: SearchEngine,
    size: Dp = 28.dp,
    corner: Dp = 7.dp,
    letterSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bundled = remember(engine.id) {
        BUNDLED[engine.id]?.let { res ->
            decoded.getOrPut(res) { BitmapFactory.decodeResource(context.resources, res) }
        }
    }
    SiteIcon(
        host = engine.host,
        captured = bundled,
        size = size,
        corner = corner,
        letterSize = letterSize,
        // The bundled marks are trimmed to their own ink and scaled to one
        // optical size, on nothing — no white square under Bing's b, and the
        // Google G no longer twice everything else because it happened to be
        // delivered edge to edge. A chip under them would put back exactly
        // the ground that was stripped out; a custom engine's fetched
        // favicon still gets one, being an unknown quantity.
        glyphGround = bundled == null,
        modifier = modifier,
    )
}
