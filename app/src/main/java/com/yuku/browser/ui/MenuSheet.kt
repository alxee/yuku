package com.yuku.browser.ui

import com.yuku.browser.ui.theme.tuiSoftOutlineIf
import com.yuku.browser.ui.theme.tuiBloomIf
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Share
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.yuku.browser.ui.theme.LocalChromeDarkness
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.Tab
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.ui.theme.FieldBg
import androidx.compose.ui.graphics.compositeOver
import com.yuku.browser.ui.theme.FROSTED_ELEMENT_ALPHA
import com.yuku.browser.ui.theme.LocalFrosted
import com.yuku.browser.ui.theme.frostedIf
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkFaint
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.Secure
import com.yuku.browser.ui.theme.specialCorner
import com.yuku.browser.ui.theme.Bevel
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.LocalAero
import com.yuku.browser.ui.theme.Ninety8Switch
import com.yuku.browser.ui.theme.Glassy
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroGlareIf
import com.yuku.browser.ui.theme.aeroPopIf
import com.yuku.browser.ui.theme.AeroPop
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.LocalTui
import com.yuku.browser.ui.theme.TuiIconGlyphs
import com.yuku.browser.ui.theme.TuiMonoFamily
import com.yuku.browser.ui.theme.NothingSansFamily
import com.yuku.browser.ui.theme.accentWash
import com.yuku.browser.ui.theme.SpecialCircle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box

@Composable
fun MenuSheet(
    open: Boolean,
    // Shared with the + sheet's search bar: see aeroPopIf.
    omniboxPop: AeroPop? = null,
    tab: Tab?,
    pageDark: Boolean,
    // Whether the page in front of the user has an article in it at all, and
    // whether it is currently being shown as one. The first is what the row
    // is enabled by: the harvest either found something to read or it did
    // not, and a switch that would flip to no visible effect is worse than
    // one that is plainly not offered here.
    readerAvailable: Boolean,
    readerActive: Boolean,
    desktopMode: Boolean,
    /** The registrable domain the Site settings row is scoped to. */
    siteLabel: String,
    isBookmarked: Boolean,
    linkStripperEnabled: Boolean,
    // Tapping the address turns this sheet into the + sheet with the current
    // URL in its field — see BrowserScreen's addressEdit. Handled up there
    // because it is a change of SHEET, and because back has to be able to
    // undo it before it means "close the sheet".
    onEditAddress: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onTogglePageDark: () -> Unit,
    onToggleReader: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenSiteSettings: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    // Something is downloading right now: the Downloads row's glyph moves.
    downloading: Boolean,
    onOpenFind: () -> Unit,
    onOpenSettings: () -> Unit,
    // How tall this sheet's contents actually are, in px, reported from the
    // layout phase. The menu is the one sheet whose height is DERIVED rather
    // than chosen — see `sheetRestHeightPx` in BrowserScreen — so it has to
    // say. Fired once per real change (the rows are all fixed-height), not
    // per frame.
    onContentHeight: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // An empty tab has no page behind it, and everything in this sheet that
    // acts ON the page is disabled rather than left live: copy, reload and
    // share would hand out an empty string (a share chooser with a blank
    // body, a clipboard wipe, a reload of nothing), and the three toggles —
    // bookmark, desktop, dark — would each pin a preference about a page
    // that is not there. A tile that can be pressed and changes nothing is
    // worse than one that is plainly not on offer.
    val hasPage = !tab?.url.isNullOrBlank()

    Column(
        Modifier
            .fillMaxWidth()
            // MenuSheet's natural content (address bar, tiles, every menu
            // row) is much taller than SheetContentHeight in BrowserScreen —
            // scrolling here is what lets it share that same fixed sheet
            // height with NewTabSheet instead of growing past it.
            .verticalScroll(rememberScrollState())
            // INSIDE the scroll modifier, which measures its child with an
            // unbounded height — so this is the contents' own height, not
            // the height of the box they were poured into. That is the
            // number BrowserScreen sizes the sheet to; measuring it out here
            // is what removes the strip of dead sheet that used to sit under
            // the last row when the fraction was guessed instead.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                onContentHeight(placeable.height)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            // Room for the address bar's drop shadow. It is drawn with
            // clip = false and reaches above the bar's top edge, and the bar
            // is the first thing in the scroll — which clips to its own
            // bounds, so at y = 0 the shadow's top band, and with it the edge
            // it wraps, was simply cut off. (The + sheet's identical bar has
            // no scroll around it, which is why only this one showed it.)
            // The drag strip above is shortened by the same amount, so the
            // bar lands exactly where it always did.
            .padding(top = SheetBarShadowRoom, bottom = 12.dp)
    ) {
        // ---- address bar -------------------------------------------------
        // Small glyphs pinned to the edges; the domain is the largest element,
        // because it is the only phishing signal the user gets.
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
                .aeroPopIf(omniboxPop, yieldToChildren = true)
                // Same shape, size and shadow treatment as the search bar on
                // the + sheet — these two are the omnibox in two different
                // states, and should read as one consistent element.
                //
                // Except under 98, where a blurred drop shadow is the one
                // thing the look does not have: an address bar there is a
                // hole cut in the toolbar with white paper behind it, and it
                // is the SUNKEN bevel that says so. The shadow is dropped
                // rather than squared off, because a hard shadow under a
                // control that is set INTO its surface would be a control
                // floating above and sunk into the same toolbar at once.
                .then(
                    // No drop shadow under the TUI either: a tube lights
                    // things, it does not cast shadows under them.
                    // Nor on frosted glass, where it shows through the field.
                    if (LocalNothing.current || LocalNinety8.current || LocalAero.current || LocalTui.current || LocalFrosted.current) Modifier
                    else Modifier.shadow(elevation = 6.dp, shape = specialCorner(24.dp), clip = false)
                )
                .clip(specialCorner(24.dp))
                .background(if (LocalAero.current) FieldBg.copy(alpha = 0.22f) else FieldBg.frostedIf(FROSTED_ELEMENT_ALPHA))
                .then(if (LocalNothing.current) Modifier.border(1.dp, HairLine, specialCorner(24.dp)) else Modifier)
                .bevel98If(Bevel.Sunken)
                // Aero agrees with 98 about what an address bar IS — a well
                // set into the surface rather than a pill floating on it —
                // and says it with the other language: the shadow inside its
                // top edge, the bounce along the bottom. It keeps the drop
                // shadow above, unlike 98, because a pane of glass with a
                // well in it is still a pane held over the page.
                .aeroGlassIf(24.dp, Glassy.Field)
                .aeroGlareIf(24.dp)
                // The field's own fill covers the sheet's halo (drawn under
                // content), so the address and its buttons glow on their own.
                .tuiBloomIf()
                // Terminal controls are ruled in ink; the selected phosphor is
                // reserved for their bloom and the canvas beneath them.
                .tuiSoftOutlineIf(InkMuted.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Go back", enabled = tab?.canGoBack == true, iconSize = 28.dp, onClick = onBack)
            BarButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Go forward", enabled = tab?.canGoForward == true, iconSize = 28.dp, onClick = onForward)

            // Only once the host is actually too long for the bar. fadeEdges
            // composites its content OFFSCREEN, and this row is unplaced and
            // re-placed every time the sheet hands over to the + sheet and
            // back (see placedWhen in BrowserScreen) — which meant a layer
            // torn down and reallocated around the address, and a dark frame
            // over it on the way back in. A domain that fits has nothing to
            // fade, so it no longer asks for a layer at all; the same reason
            // NewTabSheet's rows only take one when their text overflows.
            var domainOverflows by remember { mutableStateOf(false) }
            // Rebuilt whenever the sheet opens or closes, which is what
            // discards the press behind it. The tap that hands this sheet
            // over to the + sheet unplaces this row in the same frame it
            // fires: the row stays COMPOSED, so nothing ever arrives to end
            // that press, and the ripple it armed was still waiting to play
            // the next time the menu was drawn — the long-press-on-a-link
            // animation, seconds late, over an address nobody was touching.
            // A source with no interactions in it has nothing to replay.
            val addressPress = remember(open) { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(specialCorner(18.dp))
                    .clickable(
                        interactionSource = addressPress,
                        indication = ripple(),
                        onClick = onEditAddress,
                    )
                    .then(if (domainOverflows) fadeEdges(edge = 10.dp) else Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab?.secure == true) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Secure connection",
                        tint = Secure,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    // The whole host, with the subdomain muted in front of
                    // the registrable domain — see [addressLabel]. With no
                    // page loaded there is no domain to show, so the bar
                    // says what tapping it does — the same words the + sheet's
                    // search field uses, and in the same muted placeholder tone.
                    text = addressLabel(tab?.url.orEmpty(), tab?.host.orEmpty()),
                    color = if (tab?.host.isNullOrEmpty()) InkMuted else InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    onTextLayout = { domainOverflows = it.hasVisualOverflow },
                )
            }

            BarButton(Icons.Default.ContentCopy, "Copy link", enabled = hasPage, iconSize = 18.dp) {
                val url = tab?.url.orEmpty()
                clipboard.setText(AnnotatedString(if (linkStripperEnabled) UrlUtils.stripTrackingParams(url) else url))
                // No toast: Android 13+ shows its own clipboard confirmation,
                // and adding one gives the user a duplicate.
            }
            BarButton(Icons.Default.Refresh, "Reload page", enabled = hasPage, onClick = onReload)
        }

        Spacer(Modifier.height(12.dp))

        // ---- four quick-toggle tiles, as separate squares -----------------
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tile(
                icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = "Bookmark",
                active = isBookmarked,
                enabled = hasPage,
                onClick = onToggleBookmark,
            )
            Tile(
                icon = if (desktopMode) Icons.Filled.DesktopWindows else Icons.Outlined.DesktopWindows,
                label = "Desktop",
                active = desktopMode,
                enabled = hasPage,
                onClick = onToggleDesktop,
            )
            // Flips the whole browser — the page's rendering and the chrome
            // around it — between light and dark, pinning both settings (the
            // two are separately configurable in Settings; this is the one
            // gesture that means "all of it"). Its state follows the PAGE,
            // since that's what the tile is over.
            Tile(
                icon = if (pageDark) Icons.Filled.DarkMode else Icons.Outlined.DarkMode,
                label = "Dark",
                active = pageDark,
                enabled = hasPage,
                onClick = onTogglePageDark,
            )
            Tile(icon = Icons.Outlined.Share, label = "Share", toggles = false, enabled = hasPage) {
                val url = tab?.url.orEmpty()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, if (linkStripperEnabled) UrlUtils.stripTrackingParams(url) else url)
                }
                context.startActivity(Intent.createChooser(send, null))
                onDismiss()
            }
        }

        Spacer(Modifier.height(8.dp))

        // Closes the sheet on the way: the find bar is over the page, and
        // the page is the thing being searched — which is also why it is
        // disabled without one. The find bar over an empty tab is a field
        // that can be typed into and can never match anything.
        MenuRow(Icons.Default.Search, "Search on page", enabled = hasPage, onClick = {
            onDismiss()
            onOpenFind()
        }) { Chevron() }
        // Text and pictures, with the page's furniture — and whatever a
        // metered site put over its article — left behind. See ReaderMode.
        //
        // Split the way the Ad blocker row below is, and for the same reason:
        // the switch does the thing, the row leads to how the thing is set.
        // The row is enabled wherever there is a page — a text size is worth
        // choosing on a page with no article in it, for the next page that
        // has one, but not on an empty tab, where there is no next page yet
        // and nothing on screen for a change to show itself against. The
        // switch answers to the harvest's verdict on top of that.
        // Hence no `toggledTo` on the row itself: it navigates, and
        // navigation is silent everywhere else in this menu.
        MenuRow(
            Icons.AutoMirrored.Filled.MenuBook,
            "Reader mode",
            enabled = hasPage,
            onClick = onOpenReaderSettings,
        ) {
            MenuSwitch(
                checked = readerActive,
                enabled = hasPage && (readerAvailable || readerActive),
                onToggle = onToggleReader,
            )
        }
        // Where the Ad blocker row used to be, and deliberately: what that
        // row led to is a set of lists (which feeds, how often, the user's
        // own rules), none of which is about the page in front of the user,
        // which is the only thing a menu over a page is for. Those have moved
        // to Settings; this is the question the menu was actually being
        // opened for — this page is broken, or too small, or too bright.
        // The blocker's own switch went with them: an exception for the site
        // is one row inside, and it is the honest version of what tapping a
        // shield over a broken page was reaching for.
        MenuRow(
            Icons.Default.Tune,
            "Site settings",
            // Nothing to scope to on an empty tab. The row stays in place
            // rather than disappearing, like every other row here that can
            // have nothing to act on.
            enabled = hasPage,
            onClick = onOpenSiteSettings,
        ) {
            // The site, not a chevron: it is what every switch behind this
            // row is scoped to, and naming it here is what stops the sheet
            // being opened in the belief that it is the browser's settings.
            if (hasPage) RowValue(siteLabel) else Chevron()
        }

        // The library, under the page. Everything above this line acts on
        // what is in front of the user and greys out when nothing is; these
        // three are the browser's own shelves and are always live. The menu
        // is opened for the page far more often than for the shelves, so
        // the page's rows are the ones under the thumb.
        MenuRow(Icons.Default.Bookmark, "Bookmarks", onClick = onOpenBookmarks) { Chevron() }
        MenuRow(Icons.Default.History, "History", onClick = onOpenHistory) { Chevron() }
        MenuRow(
            label = "Downloads",
            onClick = onOpenDownloads,
            leadingGap = !LocalTui.current,
            leading = { if (!LocalTui.current) DownloadsGlyph(downloading) },
        ) { Chevron() }

        // App settings used to sit apart at the foot of the sheet, behind a
        // 16dp gap and a divider, on the grounds that it is the only row
        // that leaves the browser for the app around it. It reads as the
        // seventh row of seven now: the break bought a distinction nobody
        // was looking for, and the rhythm of a list is broken by any gap in
        // it — every other row in this menu is one row-height from the last,
        // and this one is too.
        MenuRow(Icons.Default.Settings, "App settings", onClick = onOpenSettings) { Chevron() }
    }
}

/**
 * The Downloads row's glyph, which moves while a download is in flight: the
 * arrow drops out through the bottom of its box, comes back in from the top,
 * and rests — over and over, the shape of things going down into a tray.
 *
 * Clipped to its own 24dp so the fall is an exit rather than an arrow sliding
 * across the row's label. Whatever the look's icon set draws for Download is
 * what falls, so it holds under Nothing and 98 without a glyph of its own.
 * The arrival decelerates rather than overshooting: this settles at a
 * position of 0 against a clip, where a carry past it shows as a nick taken
 * out of the top of the glyph (see Motion.kt).
 */
@Composable
private fun DownloadsGlyph(downloading: Boolean) {
    val cycle = if (downloading) {
        rememberInfiniteTransition(label = "downloadsRow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(DOWNLOADS_ROW_CYCLE_MS, easing = LinearEasing)),
            label = "downloadsRowCycle",
        )
    } else {
        null
    }
    Icon(
        Icons.Default.Download,
        contentDescription = null,
        tint = Ink,
        modifier = Modifier
            .size(24.dp)
            .clipToBounds()
            .graphicsLayer {
                val p = cycle?.value ?: return@graphicsLayer
                translationY = size.height * when {
                    p < 0.3f -> Accelerate.transform(p / 0.3f)
                    p < 0.6f -> -1f + PaneDecelerate.transform((p - 0.3f) / 0.3f)
                    else -> 0f
                }
            },
    )
}

private const val DOWNLOADS_ROW_CYCLE_MS = 1400

/**
 * The one switch used by every settings/menu row, so its on/off haptic (and
 * its accent) is defined once — tapping the switch itself has to feel the
 * same as tapping the row around it (see MenuRow's `toggledTo`).
 */
@Composable
internal fun MenuSwitch(checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val haptics = rememberHaptics()
    if (LocalAero.current) {
        AeroSwitch(checked, enabled) {
            haptics.toggle(!checked)
            onToggle()
        }
        return
    }
    // Material's Switch takes its corners from its own tokens, not from
    // MaterialTheme.shapes, so the TUI theme cannot square it off the way it
    // squares everything else — it gets a switch of its own instead. Same
    // size, same travel, same haptic; a rectangle sliding in a rectangle.
    if (LocalTui.current) {
        TuiSwitch(checked = checked, enabled = enabled) {
            haptics.toggle(!checked)
            onToggle()
        }
        return
    }
    // 98 has no switch to redraw — the control this system uses for a
    // setting that is on or off is a checkbox, and a sliding one would be a
    // scrollbar, which means position. See [Ninety8Switch].
    if (LocalNinety8.current) {
        Ninety8Switch(checked = checked, enabled = enabled) {
            haptics.toggle(!checked)
            onToggle()
        }
        return
    }
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = {
            haptics.toggle(it)
            onToggle()
        },
        colors = SwitchDefaults.colors(
            // Both tracks solid under translucent sheets: a switch thinned
            // over frost washed out to the page's colour, and its state is
            // the one thing it has to show.
            checkedTrackColor = AccentColor,
            // Nothing's off controls are still live controls, not disabled
            // decoration. Leave the track unfilled, like the default theme,
            // but give its border and thumb the same muted ink as the menu's
            // chevrons.
            uncheckedTrackColor = if (LocalNothing.current) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            // Material draws the checked thumb in `onPrimary` — right when
            // the accent is a dark colour, wrong for the Nothing theme's
            // light yellow, where it comes out as a black puck on a bright
            // track. The thumb is a thing sliding ON the track rather than a
            // mark drawn on the accent, so it takes the surface's own colour
            // instead: pale on the paper end, dark on the black one.
            checkedThumbColor = if (LocalNothing.current) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onPrimary,
            uncheckedThumbColor = if (LocalNothing.current) InkMuted
            else SwitchDefaults.colors().uncheckedThumbColor,
            uncheckedBorderColor = if (LocalNothing.current) InkMuted
            else SwitchDefaults.colors().uncheckedBorderColor,
        ),
    )
}

/** A hollow glass track and a rounded lens, with a full 48dp touch target. */
@Composable
private fun AeroSwitch(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val travel by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = arriveSoft(210),
        label = "aeroSwitchTravel",
    )
    val tint = if (checked) AccentColor else InkMuted
    Box(
        Modifier.size(58.dp, 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .toggleable(checked, enabled = enabled, role = Role.Switch) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(58.dp, 34.dp)
                .clip(specialCorner(17.dp))
                .background(Brush.verticalGradient(listOf(
                    tint.copy(alpha = if (checked) 0.28f else 0.10f),
                    tint.copy(alpha = 0.05f),
                )))
                .aeroGlassIf(17.dp, Glassy.Field),
        ) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = 4.dp + travel)
                    .size(28.dp)
                    .clip(SpecialCircle)
                    .background(Brush.linearGradient(listOf(
                        Color.White.copy(alpha = 0.72f),
                        tint.copy(alpha = 0.18f),
                        tint.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.48f),
                    )))
                    .aeroGlassIf(14.dp),
            )
        }
    }
}

/** The TUI theme's switch: see [MenuSwitch]. */
@Composable
private fun TuiSwitch(checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    /*
     * A terminal form is checked or blank; a sliding thumb implies a physical
     * switch rather than a text-mode boolean. Keep the touch target square
     * and replace the old track with a literal [x]/[ ] terminal field.
     */
    Box(
        modifier = Modifier
            .size(TUI_CHECKBOX)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // It stays in the text ink so toggling never swaps hue or drops
            // out of the sheet's shared glow.
            text = if (checked) "[x]" else "[ ]",
            color = if (!enabled) InkFaint else Ink,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = TuiMonoFamily,
            fontSize = 20.sp,
        )
    }
}

private val TUI_CHECKBOX = 36.dp

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    // Optical, not nominal. Material glyphs fill their 24-unit viewport by
    // wildly different amounts — Refresh's circle spans 16 units, Close 14,
    // a chevron only 12, while ContentCopy's page stack runs 22 tall — so
    // drawing them all at one size makes copy look oversized next to
    // reload and the chevrons look shrunken. Each call site instead passes
    // the size that lands its glyph's ink at Refresh's ~16 units (its own
    // 24.dp is the baseline the rest are matched to); the 40.dp touch
    // target is unchanged either way.
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // Its own pop, claimed from the address bar's (see aeroPopIf).
        modifier = Modifier.size(40.dp).aeroPopIf(claim = true),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Ink else InkFaint,
            // ...and those per-call-site corrections are switched OFF under
            // the Nothing theme, because `NothingIcons` has already made
            // them. Every glyph in that set is drawn to one 20-unit live
            // area, which is the whole point of a set; correcting each one
            // AGAIN by a number derived from how much of its box Material's
            // glyph happened to fill is a second correction applied to
            // something already square. It is what made reload tower over
            // copy in the address bar — the same 17 units of ink drawn at
            // 24dp beside 18dp.
            //
            // 98 switches them off for the same reason and at its own size:
            // Open Iconic is drawn to one EIGHT-unit grid, and those glyphs
            // fill more of their box than Nothing's fill theirs, so the
            // number that lands them at the same ink is the bar's ordinary
            // 24dp rather than a reduced one. (This was briefly back on
            // while 98 borrowed Material's Sharp cut, which is the set the
            // per-call-site numbers were measured against.)
            modifier = Modifier.size(
                when {
                    LocalNothing.current -> NOTHING_BAR_ICON
                    LocalNinety8.current -> 24.dp
                    // And the TUI, for the third time and its own version of
                    // the reason. Its bar is half characters (`<` `>`, drawn
                    // in the interface's face at a fixed fraction of their
                    // box by TuiGlyphIcon) and half Material Sharp — but the
                    // calibration between a character's ink and a
                    // silhouette's is already made ONCE, in GLYPH_FILL, for
                    // the whole set. Correcting on top of it per call site is
                    // a second correction, and it is what left the chevrons
                    // towering over copy in this row while reload sat between
                    // them at a third size.
                    //
                    // But only for the CHARACTERS. The rest of the row is
                    // Material Sharp, the same drawings the per-call-site
                    // numbers were measured against, so they keep them — at
                    // a flat 24 copy's 22-unit page stack towered over the
                    // row.
                    LocalTui.current -> if (icon in TuiIconGlyphs) 24.dp else iconSize
                    else -> iconSize
                }
            ),
        )
    }
}

/**
 * The one size every glyph in the menu's address bar is drawn at under the
 * Nothing theme — see [BarButton]. 20dp of box is ~14dp of ink for a set
 * drawn to a 20-unit live area, which is the size the bar's smallest
 * Material glyph used to come out at; at 24 they were the biggest thing in
 * the row and outweighed the address itself.
 */
private val NOTHING_BAR_ICON = 20.dp

@Composable
private fun RowScope.Tile(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    // False for the tiles that go somewhere (Appearance, Share) rather than
    // flipping the thing they're showing the state of.
    toggles: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    // Four independent squares, not one merged strip — state is carried by
    // the icon itself (filled vs. outlined, passed in by the caller) plus a
    // faint tonal wash, matching Android's own quick-settings tiles rather
    // than a single divided block.
    //
    // SQUARE, and it stays square: shortening them to buy the sheet some
    // height pulled the row up under the address bar, and the gap between
    // those two is what separates "where you are" from "what you can do to
    // it". The sheet takes whatever height they need instead — it is
    // measured from these contents, see `onContentHeight`.
    // The Nothing theme draws the tile as an outlined MODULE rather than as a
    // filled block, and carries its state on that outline: a hairline while
    // it is off, the accent itself while it is on. The fill under it barely
    // separates from the sheet in a monochrome palette, so the tile needs an
    // edge to be a thing at all — and once it has one, the edge is the
    // cheapest place in the whole look to spend the yellow. A 1dp accent rule
    // is a MARK, which this theme allows anywhere; it is only the accent
    // spread thinly as a wash that gives way to ink (see `accentWash`), and
    // that wash is what the outline is standing in for here.
    // 98 draws the tile as what it is in that system: a TOOLBAR BUTTON, and
    // a toolbar button carries "on" by being pressed IN. So the state is the
    // bevel's direction — raised while it is off, sunken while it is on —
    // which is the whole of that look's grammar applied to the one control
    // in this app that has a state to show. It also takes the face colour
    // rather than the field's white: a button is made of the same silver as
    // the sheet it sits on and is told apart from it by its edges alone,
    // which is exactly what the bevel is for.
    val nothing = LocalNothing.current
    val ninety8 = LocalNinety8.current
    val tileShape = specialCorner(16.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .aeroPopIf()
            .clip(tileShape)
            .background(
                when {
                    LocalAero.current -> if (active) AccentColor.copy(alpha = 0.18f) else FieldBg.copy(alpha = 0.16f)
                    ninety8 && !active -> MaterialTheme.colorScheme.surface
                    // An enabled terminal action is a lit key: its fill is
                    // the phosphor itself, while the rule stays terminal ink.
                    LocalTui.current && active -> AccentColor.copy(alpha = 0.26f)
                    // On frost a thin wash alone is washed out by the page;
                    // an ON tile is laid on a solid field instead.
                    active && LocalFrosted.current ->
                        accentWash(0.16f).compositeOver(FieldBg)
                    active -> accentWash(0.16f)
                    else -> FieldBg.frostedIf(FROSTED_ELEMENT_ALPHA)
                }
            )
            .bevel98If(if (active) Bevel.Sunken else Bevel.Raised)
            // The same state, in the third language: a tile that is on is a
            // well with the accent standing in it, and one that is off is a
            // bead of glass sitting on the sheet. 98 carries this on the
            // bevel's direction and Aero on which way the light runs, which
            // is the same idea drawn with the material each look is made of.
            .aeroGlassIf(16.dp, if (active) Glassy.Field else Glassy.Pane)
            .aeroGlareIf(16.dp)
            // The tile's own fill covers the sheet's halo, so its label
            // glows on its own, above that fill — at a fraction while the
            // tile is disabled: its label is faint grey then, and a full
            // halo round dim type reads as broken, not lit.
            // A coloured phosphor belongs in the glow, while the tile itself
            // stays monochrome like a physical terminal control.
            .tuiBloomIf(
                when {
                    !enabled -> 0.3f
                    active -> 1f
                    else -> 0.7f
                }
            )
            // The rule follows that same ink grammar. It is stronger while
            // active, but never borrows the accent from the phosphor.
            .tuiSoftOutlineIf(
                when {
                    !enabled -> HairLine
                    active -> InkStrong.copy(alpha = 0.75f)
                    else -> InkMuted.copy(alpha = 0.60f)
                }
            )
            .then(
                if (nothing) Modifier.border(
                    width = 1.dp,
                    color = if (active && enabled) AccentColor else HairLine,
                    shape = tileShape,
                ) else Modifier
            )
            .clickable(enabled = enabled) {
                // Only a tile that actually flips something reports back;
                // the ones that merely go somewhere (Appearance, Share) are
                // ordinary navigation.
                if (toggles) haptics.toggle(!active)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Under the TUI theme the tile is its word and nothing else — a
        // terminal has no glyphs to draw. The tile still says what state it
        // is in through its ink wash, rule, and phosphor halo; the icon's
        // fill would only repeat that.
        if (!LocalTui.current) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (!enabled) InkFaint else if (active) AccentColor else Ink,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = label,
            color = when {
                !enabled -> InkFaint
                // Under the TUI the word stays near-white either way: an
                // accent label on the accent wash, with the glow over it,
                // blew out to an unreadable smear. The wash, the outline and
                // the halo already say the tile is on.
                LocalTui.current -> InkStrong
                active -> InkStrong
                else -> InkMuted
            },
            style = MaterialTheme.typography.labelLarge,
            // A tile's label is a NAME — "Desktop site", "Find in page" —
            // not the one-word status a label style is otherwise for, and the
            // Nothing theme cases its label face up. Two capitalised words in
            // a tracked mono face do not fit a tile this size, so the tile
            // asks for the body face by name, which is also what leaves the
            // words cased as they were written (see `SpecialText`).
            fontFamily = if (nothing) NothingSansFamily else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {},
    // False for a row whose action has nothing to act on — the reader over a
    // page with no article. The row stays in place rather than disappearing:
    // where a control lives is part of knowing the menu, and a row that comes
    // and goes has to be hunted for every time.
    enabled: Boolean = true,
    // Non-null on a row whose whole job is flipping something on or off (the
    // ones carrying a MenuSwitch): the state it will be in AFTER the tap, so
    // the row feels like the switch it drives rather than like a plain row.
    toggledTo: Boolean? = null,
    trailing: @Composable () -> Unit,
) = MenuRow(
    label = label,
    onClick = onClick,
    enabled = enabled,
    toggledTo = toggledTo,
    // The icon goes entirely under the TUI theme — and with it the gap it
    // was holding, or every row would start at an indent explained by
    // something that is no longer drawn.
    leadingGap = !LocalTui.current,
    leading = {
        if (!LocalTui.current) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Ink else InkFaint,
                modifier = Modifier.size(24.dp),
            )
        }
    },
    trailing = trailing,
)

/**
 * Same row, but the leading slot is arbitrary content — for rows whose "icon"
 * isn't a vector (the search-engine list's favicons). The 24dp the vector
 * overload uses is the size to match.
 */
@Composable
internal fun MenuRow(
    label: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    toggledTo: Boolean? = null,
    // Whether the leading slot draws anything worth spacing the label away
    // from. False only for a row that dropped its icon (see the overload
    // above); a row whose "icon" is a favicon keeps the gap in every theme.
    leadingGap: Boolean = true,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                // A plain menu row is navigation and stays silent — only the
                // rows that carry a switch report the state they just set.
                if (toggledTo != null) haptics.toggle(toggledTo)
                onClick()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Navigation rows get the terminal's prompt marker on the leading
        // edge. Form rows already carry a checkbox, so they keep that as the
        // state marker rather than receiving a second one.
        if (LocalTui.current && toggledTo == null) {
            Text(text = ">", color = if (enabled) Ink else InkFaint, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
        }
        leading()
        Text(
            text = label,
            color = if (enabled) InkStrong else InkFaint,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leadingGap) 16.dp else 0.dp),
        )
        trailing()
    }
}

@Composable
internal fun Chevron() {
    // The one glyph the TUI theme keeps, as the character a terminal would
    // have used for it: it is not decoration on the row, it is the row
    // saying it leads somewhere.
    if (LocalTui.current) {
        // TUI navigation is marked at the row's leading edge; do not draw a
        // second chevron after its label.
        return
    }
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        // InkMuted, not InkFaint: faint is for hairline-quiet text, and on the
        // translucent sheets it left the one sign a row leads somewhere
        // nearly invisible.
        tint = InkMuted,
        modifier = Modifier
            .size(24.dp)
            .clip(SpecialCircle),
    )
}

/**
 * What both address bars draw for the page they are on: the full host, minus
 * `www.`, with everything in front of the registrable domain muted.
 *
 * The registrable domain alone is not enough to READ an address by —
 * auto.ria.com and ria.com are different services — but the raw host in one
 * even tone is the phishing aid the registrable domain was there to avoid, so
 * the half that decides whose page this is keeps the bar's own colour and the
 * subdomain steps back behind it. [host] is the fallback for a URL that does
 * not parse, and the placeholder stands in when there is no page at all.
 */
@Composable
internal fun addressLabel(
    url: String,
    host: String,
    placeholder: String = ADDRESS_PLACEHOLDER,
): AnnotatedString {
    if (host.isEmpty()) return AnnotatedString(placeholder)
    val parts = UrlUtils.displayHost(url)
    if (parts.isEmpty) return AnnotatedString(host)
    val subdomainTone = InkMuted
    return remember(parts, subdomainTone) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = subdomainTone)) { append(parts.subdomain) }
            append(parts.domain)
        }
    }
}

private const val ADDRESS_PLACEHOLDER = "Search or enter address"
