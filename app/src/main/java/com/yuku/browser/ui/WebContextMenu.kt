package com.yuku.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.core.WebContextTarget
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import kotlin.math.roundToInt
import com.yuku.browser.ui.theme.specialCorner

/** Gap between the finger and the nearest edge of the menu. */
private val MENU_GAP = 12.dp

/** How close the menu is ever allowed to get to a screen edge. */
private val MENU_MARGIN = 12.dp

/**
 * The link/image menu a long press on the page raises — an anchored card, not
 * a bottom sheet: it belongs to the exact thing under the finger, and the two
 * bottom sheets in this app are for actions on the tab as a whole.
 *
 * Rendered as a plain full-screen overlay inside BrowserScreen rather than a
 * `Popup`, so it sits in the same window (and the same theme) as everything
 * else and can be positioned by the coordinates the press reported — the app
 * is edge-to-edge, so those raw screen coordinates and this overlay's own
 * layout coordinates are the same numbers.
 *
 * [target] going null starts the exit animation, which is why the last
 * non-null one is held onto: the card has to keep drawing its rows (and stay
 * where it was) for the length of the fade.
 */
@Composable
fun WebContextMenu(
    target: WebContextTarget?,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadImage: (WebContextTarget) -> Unit,
    onCopyImage: (WebContextTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var last by remember { mutableStateOf<WebContextTarget?>(null) }
    LaunchedEffect(target) { target?.let { last = it } }
    val shown = target ?: last ?: return
    // No haptic fired here on open: View.performLongClick already performs
    // LONG_PRESS when a listener handles the press, which BrowserViewModel's
    // does — buzzing again from Compose just makes it a double tick.

    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    // Placed by hand rather than with an offset modifier so the clamping can
    // use the card's measured size: a menu raised near the bottom of the
    // screen flips above the finger, and one near an edge slides inward,
    // instead of being cut off.
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            AnimatedVisibility(
                visible = target != null,
                // The scale carries a touch past 1 and settles back — the
                // menu is small and appears right under the finger, so the
                // overshoot is a few pixels of pop rather than a bounce.
                enter = fadeIn(tween(120)) + scaleIn(arrive(200), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(depart(120), targetScale = 0.95f),
            ) {
                MenuCard(
                    target = shown,
                    onOpen = onOpen,
                    onOpenInNewTab = onOpenInNewTab,
                    onCopyLink = onCopyLink,
                    onDownloadImage = onDownloadImage,
                    onCopyImage = onCopyImage,
                )
            }
        },
    ) { measurables, constraints ->
        val gap = MENU_GAP.roundToPx()
        val margin = MENU_MARGIN.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // Nothing to place once the exit animation has finished — an
        // AnimatedVisibility that is fully hidden emits no child at all.
        val placeable = measurables.firstOrNull()?.measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (width - margin * 2).coerceAtLeast(0),
                maxHeight = (height - margin * 2).coerceAtLeast(0),
            ),
        ) ?: return@Layout layout(width, height) {}
        layout(width, height) {
            val x = (shown.x.roundToInt() - placeable.width / 2)
                .coerceIn(margin, (width - placeable.width - margin).coerceAtLeast(margin))
            val below = shown.y.roundToInt() + gap
            val y = if (below + placeable.height + margin <= height) {
                below
            } else {
                (shown.y.roundToInt() - gap - placeable.height).coerceAtLeast(margin)
            }
            placeable.place(x, y)
        }
    }
}

@Composable
private fun MenuCard(
    target: WebContextTarget,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadImage: (WebContextTarget) -> Unit,
    onCopyImage: (WebContextTarget) -> Unit,
) {
    val haptics = rememberHaptics()
    val link = target.linkUrl
    val image = target.imageUrl
    Surface(
        color = BarBg,
        shape = specialCorner(20.dp),
        shadowElevation = 12.dp,
        // Wide enough for "Open in new tab" on one line, never wider than a
        // comfortable reading measure — the header wraps instead.
        modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Header(target)
            if (link != null) {
                MenuRow(Icons.Filled.OpenInBrowser, "Open", onClick = { onOpen(link) }) {}
                MenuRow(Icons.Filled.Tab, "Open in new tab", onClick = { onOpenInNewTab(link) }) {}
                MenuRow(Icons.Filled.ContentCopy, "Copy link", onClick = {
                    // Taking something away with you: the same weight the
                    // menu's copy of a page address has.
                    haptics.confirm()
                    onCopyLink(link)
                }) {}
            }
            if (link != null && image != null) {
                HorizontalDivider(color = HairLine, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (image != null) {
                MenuRow(Icons.Filled.Download, "Download image", onClick = {
                    haptics.confirm()
                    onDownloadImage(target)
                }) {}
                MenuRow(Icons.Outlined.Image, "Copy image", onClick = {
                    haptics.confirm()
                    onCopyImage(target)
                }) {}
            }
        }
    }
}

/**
 * What was actually pressed, so a menu raised on a dense page isn't ambiguous
 * — the anchor's own text where the page provided one, otherwise the address,
 * with the registrable domain above it as the part worth reading first.
 */
@Composable
private fun Header(target: WebContextTarget) {
    val url = target.linkUrl ?: target.imageUrl ?: return
    val host = UrlUtils.registrableDomain(url)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = target.label?.takeIf { it.isNotBlank() } ?: host,
            color = InkStrong,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = url,
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = HairLine, modifier = Modifier.padding(bottom = 4.dp))
}
