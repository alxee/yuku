package com.yuku.browser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.Favicons
import com.yuku.browser.core.measureInk
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.specialCorner

/**
 * A site's favicon, with the accent-coloured initial as the fallback.
 *
 * [captured] is the icon a live WebView already handed us (history entries and
 * tabs carry one) — preferred when present because it's free and exact. Only
 * when there isn't one do we go to the network via [Favicons], keyed on [host]
 * so scrolling a list back and forth re-reads the cache instead of refetching.
 */
@Composable
fun SiteIcon(
    host: String,
    captured: Bitmap? = null,
    size: Dp = 28.dp,
    corner: Dp = 7.dp,
    letterSize: TextUnit = 14.sp,
    /**
     * Whether a mark on a transparent ground is given a chip to sit on. True
     * for a site's own icon, where the fetched thing could be anything at all
     * and a dark mark on the dark theme would simply be gone. False for an
     * icon the app ships and has looked at (see [EngineIcon]): those read on
     * either theme as they are, and a chip under one of five marks in a row
     * would be the only box in it.
     */
    glyphGround: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var fetched by remember(host) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(host, captured) {
        if (captured == null) fetched = Favicons.fetch(host)
    }
    val fetchedOrCaptured = captured ?: fetched
    // How much of the icon is actually drawn, measured once per bitmap rather
    // than per frame. Two answers come out of the one scan.
    //
    // An icon with nothing in it is no icon at all: that is what "the icon is
    // missing" actually looks like, and a ground under it only makes the
    // blank whiter — the lettered fallback is the honest answer.
    //
    // And an icon that is a mark on a TRANSPARENT ground has no tile of its
    // own, so it is only ever as visible as whatever it happens to be drawn
    // on: Bing's is a blue magnifier on nothing, which reads as a gap in a
    // row of full-bleed tiles, and a dark-ink one (GitHub's, and half the
    // web's) disappears outright in the dark theme. Those get the light
    // ground the icon was drawn for; a tile that carries its own is left
    // exactly as it is.
    val ink = remember(fetchedOrCaptured) { fetchedOrCaptured?.measureInk() }
    val icon = fetchedOrCaptured?.takeIf { ink != null && ink.coverage >= INK_MIN }
    val ground = when {
        icon == null || ink == null -> AccentColor
        !glyphGround || ink.coverage >= GLYPH_COVERAGE -> Color.Transparent
        // A light mark needs a dark chip and a dark mark a light one, which
        // is the whole point of measuring: Bing's white magnifier on a white
        // chip is exactly as invisible as it was on the bar.
        ink.luminance > LIGHT_INK -> DarkGround
        else -> LightGround
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(specialCorner(corner))
            .background(ground),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                // These arrive well over the size they are drawn at (see
                // Favicons.ICON_PX), and the default filter is a bilinear
                // sample of a much larger bitmap — which on a mark made of
                // hairlines is what makes one look soft next to the same
                // mark in a browser's own chrome.
                filterQuality = FilterQuality.High,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = host.take(1).uppercase(),
                color = Color.White,
                fontSize = letterSize,
            )
        }
    }
}

/** The ground a dark mark on a transparent background is given. */
private val LightGround = Color.White

/** And the one a light mark gets, so a white glyph is not white on white. */
private val DarkGround = Color(0xFF2B2B2B)

/** Above this average brightness a mark counts as light ink. */
private const val LIGHT_INK = 0.62f

/** Below this share of drawn pixels an icon is a mark, not a tile. */
private const val GLYPH_COVERAGE = 0.55f

/** And below this there is nothing in it to show: the letter serves better. */
private const val INK_MIN = 0.005f
