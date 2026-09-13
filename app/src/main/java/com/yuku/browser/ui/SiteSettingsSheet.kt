package com.yuku.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.SiteSettings
import com.yuku.browser.core.ZOOM_STEPS
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.Text
import kotlin.math.roundToInt

/**
 * What this one site gets that the rest of the web does not, as the sheet the
 * menu's Site settings row leads to.
 *
 * It replaced the Ad blocker row there, and the swap is the point: the ad
 * blocker's own screen is a set of LISTS — which feeds, how often, the user's
 * own rules — and none of that is about the page in front of them, which is
 * what a menu over a page is for. It has moved to Settings, where the other
 * things that are true of every site live. What belongs here is the opposite
 * question, and it is the one anybody actually opens a menu to ask: this page
 * is broken, or too small, or too bright — what can I do about THIS page?
 *
 * A sheet rather than a full-screen destination, for [ReaderSettingsSheet]'s
 * reason: every control here changes the page live, the page is still showing
 * in the third of the screen above the sheet, and a destination would cover
 * the one thing being adjusted.
 *
 * The three filter switches are exceptions rather than settings — see
 * [SiteSettings]. They are also disabled where the app is not doing that
 * filtering anywhere, because a switch that is on while nothing is being
 * blocked is a switch telling the user something untrue.
 *
 * They carry NO caption. The three of them plus the two settings below are
 * the whole sheet, and a paragraph under the switches was what pushed it past
 * its rest height — so the sheet scrolled, or grew, to explain a disabled
 * switch that a disabled switch already says. What the caption held that is
 * worth keeping is written down in [SiteSettings] instead.
 */
@Composable
fun SiteSettingsSheet(
    /** The registrable domain this is about. Blank while there is no page. */
    site: String,
    settings: SiteSettings,
    adBlockOn: Boolean,
    trackerBlockingOn: Boolean,
    cookieBannersOn: Boolean,
    /** The app's own text size, which a site with no zoom of its own follows. */
    appZoom: Int,
    onChange: (SiteSettings) -> Unit,
    onSetZoomStep: (Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        PaneHeader("Site settings", onBack)

        SiteHeading(site)

        MenuRow(
            Icons.Default.Block,
            "Block ads",
            enabled = adBlockOn,
            onClick = { onChange(settings.copy(blockAds = !settings.blockAds)) },
            toggledTo = !settings.blockAds,
        ) {
            MenuSwitch(
                checked = adBlockOn && settings.blockAds,
                enabled = adBlockOn,
                onToggle = { onChange(settings.copy(blockAds = !settings.blockAds)) },
            )
        }
        MenuRow(
            Icons.Default.Visibility,
            "Block trackers",
            enabled = adBlockOn && trackerBlockingOn,
            onClick = { onChange(settings.copy(blockTrackers = !settings.blockTrackers)) },
            toggledTo = !settings.blockTrackers,
        ) {
            MenuSwitch(
                checked = adBlockOn && trackerBlockingOn && settings.blockTrackers,
                enabled = adBlockOn && trackerBlockingOn,
                onToggle = { onChange(settings.copy(blockTrackers = !settings.blockTrackers)) },
            )
        }
        MenuRow(
            Icons.Default.Cookie,
            "Hide cookie banners",
            enabled = cookieBannersOn,
            onClick = { onChange(settings.copy(hideCookieBanners = !settings.hideCookieBanners)) },
            toggledTo = !settings.hideCookieBanners,
        ) {
            MenuSwitch(
                checked = cookieBannersOn && settings.hideCookieBanners,
                enabled = cookieBannersOn,
                onToggle = { onChange(settings.copy(hideCookieBanners = !settings.hideCookieBanners)) },
            )
        }

        // Three answers rather than a switch, because there are genuinely
        // three: this site dark, this site light, and this site doing
        // whatever the app is doing. A switch would have to spell the third
        // one as one of the other two, and a site pinned light under a light
        // setting is not the same fact as one that simply follows.
        PickerLabel("Dark pages")
        Picker(
            entries = DARK_CHOICES,
            selected = settings.dark,
            label = { choice ->
                when (choice) {
                    null -> "Follow app"
                    false -> "Off"
                    else -> "On"
                }
            },
            onSelect = { onChange(settings.copy(dark = it)) },
        )
        Hint(
            if (settings.dark == null) "Following the app's own setting for web pages."
            else "This site is always drawn ${if (settings.dark == true) "dark" else "light"}, " +
                "whatever the app is set to.",
        )

        SiteTextSizeRow(
            zoom = settings.zoom ?: appZoom,
            onSetStep = onSetZoomStep,
        )
        Hint(
            if (settings.zoom == null) "Following the app's own text size, $appZoom%."
            else "This site only. Slide back to $appZoom% to follow the app again.",
        )

        // Only where there is something to undo. A row that puts a site back
        // to ordinary on a site that already is would be a button for
        // nothing, and the sheet is short enough that its absence is legible.
        if (!settings.isDefault) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Default.Restore,
                "Reset this site",
                onClick = onReset,
            ) {}
        }
    }
}

/** The three answers [SiteSettings.dark] can hold, in the picker's order. */
private val DARK_CHOICES = listOf<Boolean?>(null, false, true)

/**
 * Which site this is all about, drawn as the site rather than written out:
 * every switch below is scoped to it, and a sheet of scoped switches whose
 * scope is a word in a caption is a sheet that gets read as global.
 */
@Composable
private fun SiteHeading(site: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host = site, size = 32.dp, corner = 10.dp, letterSize = 14.sp)
        Text(
            text = site.ifBlank { "This page" },
            color = InkStrong,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * The same slider Settings and the reader give their text sizes, with the
 * readout the reader's has — there is no percentage anywhere else on this
 * sheet to read the value off, and here the number is also how the user finds
 * their way back to the app's own.
 */
@Composable
private fun SiteTextSizeRow(zoom: Int, onSetStep: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val index = ZOOM_STEPS.indexOfFirst { it >= zoom }.let {
        if (it < 0) ZOOM_STEPS.lastIndex else it
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Text size", color = InkStrong, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val next = it.roundToInt()
                if (next != index) {
                    haptics.tick()
                    onSetStep(next)
                }
            },
            valueRange = 0f..ZOOM_STEPS.lastIndex.toFloat(),
            steps = ZOOM_STEPS.size - 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        Text(text = "$zoom%", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
