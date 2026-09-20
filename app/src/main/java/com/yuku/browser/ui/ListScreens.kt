package com.yuku.browser.ui

import com.yuku.browser.ui.theme.tuiCrtIf
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.BookmarkEntry
import com.yuku.browser.core.HistoryEntry
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.Bevel
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.Glassy
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.aeroPopIf
import com.yuku.browser.ui.theme.aeroPageGlass
import com.yuku.browser.ui.theme.aeroGlareIf
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.yuku.browser.ui.theme.LocalAero
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkFaint
import com.yuku.browser.ui.theme.InkStrong
import kotlinx.coroutines.delay
import com.yuku.browser.ui.theme.specialCorner

/** Full-screen, reached from the menu's Bookmarks row — same pattern as Settings. */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntry>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var query by remember { mutableStateOf("") }
    // The same three fields History searches, for the same reason: what
    // someone remembers of a page they saved is as often a word from its
    // address as one from its title.
    val matches = remember(bookmarks, query) {
        val needle = query.trim()
        if (needle.isEmpty()) bookmarks else bookmarks.filter { entry ->
            entry.title.contains(needle, ignoreCase = true) ||
                entry.host.contains(needle, ignoreCase = true) ||
                entry.url.contains(needle, ignoreCase = true)
        }
    }
    ListScreenScaffold(
        title = "Bookmarks",
        onBack = onBack,
        // Pinned above the list and only once there is a list — see
        // HistoryScreen, which this is deliberately identical to: the two
        // screens are the same screen over two lists, and a field that
        // appeared on one of them would be a thing to remember rather than a
        // thing to reach for.
        header = if (bookmarks.isEmpty()) null else {
            {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search bookmarks",
                )
            }
        },
    ) {
        if (bookmarks.isEmpty()) {
            EmptyListMessage("No bookmarks yet. Tap the bookmark icon on a page to save it here.")
        } else if (matches.isEmpty()) {
            EmptyListMessage("No bookmarks match that.")
        } else {
            matches.forEach { entry ->
                EntryRow(
                    title = entry.title.ifEmpty { entry.host },
                    host = entry.host,
                    onClick = { onSelect(entry.url) },
                ) {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            onRemove(entry.url)
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove bookmark",
                            tint = InkMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen, reached from the menu's History row — same pattern as Settings. */
@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var query by remember { mutableStateOf("") }
    // Under Aero the button is a drop of glass over the ROWS, and the rows are
    // this screen's own layer, not the page's — so it is the list's layer that
    // frosts and bends the strip under the button (the same shader the page
    // pass uses for the toolbar), which is what the toolbar's buttons get from
    // the glass under them.
    var clearAllBounds by remember { mutableStateOf(Rect.Zero) }
    // Against the title, the host AND the full URL: what someone remembers of
    // a page they are trying to find again is as often a word from its
    // address (a repo name, an article slug) as one from its title.
    val matches = remember(history, query) {
        val needle = query.trim()
        if (needle.isEmpty()) history else history.filter { entry ->
            entry.title.contains(needle, ignoreCase = true) ||
                entry.host.contains(needle, ignoreCase = true) ||
                entry.url.contains(needle, ignoreCase = true)
        }
    }
    val searching = query.isNotBlank()
    // The button floats over the list rather than sitting at the end of it:
    // "clear all" is about the whole list, and at the end of a long history it
    // would be a scroll away from every row it applies to.
    Box(Modifier.fillMaxSize()) {
        ListScreenScaffold(
            title = "History",
            onBack = onBack,
            fadeContentTop = true,
            headerOverContent = true,
            contentModifier = Modifier.aeroPageGlass(
                paneInRoot = { Rect.Zero },
                dropletInRoot = { clearAllBounds },
            ),
            // Outside the scroll, so it stays put while the results move
            // under it — and only once there is something to search: a field
            // over "Nothing here yet" is an offer of nothing.
            header = if (history.isEmpty()) null else {
                {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search history",
                    )
                }
            },
        ) {
            if (history.isEmpty()) {
                EmptyListMessage("Nothing here yet.")
            } else if (matches.isEmpty()) {
                EmptyListMessage("Nothing in your history matches that.")
            } else {
                matches.forEach { entry ->
                    EntryRow(
                        title = entry.title.ifEmpty { entry.host },
                        host = entry.host,
                        onClick = { onSelect(entry.url) },
                        favicon = entry.favicon,
                    ) {
                        IconButton(
                            onClick = {
                                haptics.confirm()
                                onRemove(entry.url)
                            },
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove from history",
                                tint = InkMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                // Room under the last row for the floating button to sit over.
                Spacer(Modifier.height(88.dp))
            }
        }
        AnimatedVisibility(
            // Away while a search is on: the button clears the WHOLE history,
            // and offered under a filtered list it reads as clearing the
            // rows on screen.
            visible = history.isNotEmpty() && !searching,
            enter = slideInVertically(arrive(260)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(depart(180)) { it } + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            ClearAllButton(onConfirm = onClearAll, onBounds = { clearAllBounds = it })
        }
    }
}

/**
 * Two-step, not a dialog: the first tap arms it and the label says so, the
 * second clears. Nothing here is undoable, but a whole modal for a button the
 * user has to reach for anyway is more ceremony than the action is worth —
 * and the armed state disarms itself after [ARM_TIMEOUT_MS] so a stray tap
 * doesn't stay loaded.
 */
@Composable
private fun ClearAllButton(onConfirm: () -> Unit, onBounds: (Rect) -> Unit) {
    // The page-glass layer is outside this composable. Clear its target as
    // soon as the animated button leaves, rather than retaining a ghost lens
    // for the rest of the destination's exit.
    DisposableEffect(Unit) {
        onDispose { onBounds(Rect.Zero) }
    }
    val haptics = rememberHaptics()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARM_TIMEOUT_MS)
            armed = false
        }
    }
    val accent = AccentColor
    val aero = LocalAero.current
    fun activate() {
        if (armed) {
            haptics.confirm()
            armed = false
            onConfirm()
        } else {
            haptics.toggle(true)
            armed = true
        }
    }
    if (aero) {
        // The search bar's pill at button size: the omnibox's thin well of
        // light, field glass and glare, its pop, and no shadow (an elevation
        // shadow under translucent glass is a grey smudge seen through it).
        // The frost of the rows under it comes from the list's layer (see
        // HistoryScreen), fed these bounds. Armed, the well fills with the
        // accent. The press answer is the pop, not a ripple.
        val press = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .aeroPopIf()
                .clip(specialCorner(24.dp))
                .background(if (armed) accent else FieldBg.copy(alpha = 0.22f))
                .aeroGlassIf(24.dp, Glassy.Field)
                .aeroGlareIf(24.dp)
                .onGloballyPositioned { coordinates ->
                    onBounds(
                        Rect(
                            coordinates.localToRoot(Offset.Zero),
                            coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
                        ),
                    )
                }
                .clickable(interactionSource = press, indication = null, onClick = ::activate)
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = if (armed) MaterialTheme.colorScheme.onPrimary else Ink,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (armed) "Tap again to clear" else "Clear all",
                    color = if (armed) MaterialTheme.colorScheme.onPrimary else InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .shadow(6.dp, specialCorner(24.dp))
            .clip(specialCorner(24.dp))
            .background(if (armed) accent else BarBg)
            .clickable(onClick = ::activate)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = if (armed) MaterialTheme.colorScheme.onPrimary else Ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (armed) "Tap again to clear" else "Clear all",
            color = if (armed) MaterialTheme.colorScheme.onPrimary else InkStrong,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private const val ARM_TIMEOUT_MS = 3000L

@Composable
internal fun ListScreenScaffold(
    title: String,
    onBack: () -> Unit,
    // Between the title row and the scroll, and pinned: a search field that
    // scrolled away with its results would be unreachable from exactly where
    // it is wanted — a long way down a list that isn't the one being looked
    // for.
    header: (@Composable () -> Unit)? = null,
    // History's results visually recede under its pinned search field rather
    // than beginning at it with a hard cut.
    fadeContentTop: Boolean = false,
    // The History search field sits over its results, so entries can scroll
    // under it instead of stopping at a second, artificial boundary.
    headerOverContent: Boolean = false,
    // On the scrolling layer's viewport, outside the scroll — History's Aero
    // glass under its floating button.
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val aero = LocalAero.current
    Column(
        Modifier
            .fillMaxSize()
            .background(BarBg)
            // Aero's BarBg is deliberately translucent, and over the
            // Destination's own BarBg this is a SECOND layer of it plus the
            // pane's accent tint — denser and more coloured than a sheet, on
            // purpose: a full screen of rows over a live page needs the extra
            // body to stay readable.
            .aeroGlassIf(style = Glassy.Pane)
            // The screen's own ground covers the Destination's raster, so it
            // carries its own — full-screen, so no seam.
            .tuiCrtIf()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Ink)
            }
            Text(
                text = title,
                color = InkStrong,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        // Under Aero the field is the omnibox's 56dp bar, so its strip is taller.
        val headerHeight = if (aero) LIST_SEARCH_HEADER_HEIGHT_AERO else LIST_SEARCH_HEADER_HEIGHT
        val scroll = rememberScrollState()
        val scrollModifier = Modifier
            .fillMaxSize()
            .then(contentModifier)
            .verticalScroll(scroll)
            .navigationBarsPadding()
        if (headerOverContent && header != null) {
            Box(Modifier.fillMaxSize()) {
                // The fade is a MASK on the rows, not a band of ground painted
                // over them. A painted band only matches an opaque ground: over
                // Aero's translucent glass it was a second, differently coloured
                // sheet with the rows still showing through it, and under the
                // TUI it covered the raster. Rows that actually go transparent
                // reveal whatever the screen's own ground is, in every look.
                // Offscreen, so DstOut erases the rows and not the window.
                val fadeMask = if (fadeContentTop) Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val headerPx = headerHeight.toPx()
                        // Gone under the top half of the field, then receding
                        // through its lower half and a NARROW band below it.
                        // The band grows in with the scroll: at the top the
                        // first row starts at the field's bottom edge, and a
                        // fade over it there faded content nothing is under.
                        val fadePx = HISTORY_SEARCH_FADE.toPx() *
                            (scroll.value / HISTORY_SEARCH_FADE.toPx()).coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                (headerPx * 0.5f) / (headerPx + fadePx) to Color.Black,
                                1f to Color.Transparent,
                                startY = 0f,
                                endY = headerPx + fadePx,
                            ),
                            size = androidx.compose.ui.geometry.Size(size.width, headerPx + fadePx),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                else Modifier
                Column(fadeMask.then(scrollModifier)) {
                    // Part of the scrolling content: it leaves with the
                    // rows, allowing the first one to move under the pinned
                    // field and into its fade rather than stopping below it.
                    Spacer(Modifier.height(headerHeight))
                    content()
                }
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(headerHeight),
                ) { header() }
            }
        } else {
            header?.invoke()
            Column(scrollModifier) { content() }
        }
    }
}

private val LIST_SEARCH_HEADER_HEIGHT = 52.dp
private val LIST_SEARCH_HEADER_HEIGHT_AERO = 64.dp
private val HISTORY_SEARCH_FADE = 16.dp

@Composable
private fun EntryRow(
    title: String,
    host: String,
    onClick: () -> Unit,
    favicon: Bitmap? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(
            host = host,
            captured = favicon,
            size = 40.dp,
            corner = 12.dp,
            letterSize = 16.sp,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(text = title, color = InkStrong, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(text = host, color = InkMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/**
 * The list screens' search field: the omnibox's own materials at list scale —
 * same [FieldBg], same pill — with a leading magnifier and a clear button
 * that is only there once there is something to clear.
 *
 * Deliberately NOT focused on arrival. Opening History is as often browsing
 * it as searching it, and a keyboard that comes up by itself covers half the
 * list the screen was opened to show.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    val haptics = rememberHaptics()
    val aero = LocalAero.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Under Aero, the omnibox's own bar (MenuSheet's address bar,
            // NewTabSheet's search bar): 56dp, its corner, its pop, its thin
            // well of light and its glare — the one field in this look should
            // not come in two materials.
            .then(if (aero) Modifier.height(56.dp).aeroPopIf(yieldToChildren = true) else Modifier)
            .clip(specialCorner(if (aero) 24.dp else 22.dp))
            .background(if (aero) FieldBg.copy(alpha = 0.22f) else FieldBg)
            .bevel98If(Bevel.Sunken)
            // A field is a well in both of the looks that have an opinion
            // about depth — see the menu sheet's address bar.
            .aeroGlassIf(if (aero) 24.dp else 22.dp, Glassy.Field)
            .aeroGlareIf(24.dp)
            .heightIn(min = 44.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(18.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = InkFaint,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
                cursorBrush = SolidColor(InkStrong),
                // A URL fragment is as likely a query here as a word is, and
                // neither wants a capital in front of it.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isEmpty()) {
            // Holds the field's right-hand end still, so the text doesn't
            // shift sideways on the first and last character typed.
            Spacer(Modifier.width(40.dp))
        } else {
            IconButton(
                onClick = {
                    haptics.tap()
                    onQueryChange("")
                },
                modifier = Modifier.aeroPopIf(claim = true),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun EmptyListMessage(text: String) {
    Text(
        text = text,
        color = InkMuted,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
