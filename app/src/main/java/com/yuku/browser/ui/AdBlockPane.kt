package com.yuku.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import kotlinx.coroutines.delay
import com.yuku.browser.core.BlocklistStore
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkFaint
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.specialCorner

/**
 * A pane of Settings, under Privacy, reached from the "Ad blocker" row there.
 *
 * It used to be a destination of its own off the browser MENU, and that was
 * the wrong door: everything on it — which lists, how often they refresh, the
 * user's own rules — is true of every site at once, where a menu over a page
 * is for the page in front of the user. What that row now leads to is the
 * per-site half instead ([SiteSettingsSheet]), which is what anybody actually
 * opens a menu to ask.
 *
 * Three switches and an update button, not a catalog: which lists are the
 * right ones is a judgement this app should be making, not an inventory it
 * hands the user. What's left below them is the part that genuinely is
 * personal — their own lists and their own rules.
 */
@Composable
internal fun AdBlockPane(
    enabled: Boolean,
    config: BlocklistStore.Config,
    meta: BlocklistStore.Meta,
    ruleCount: Int,
    cosmeticCount: Int,
    blockedOnPage: Int,
    updating: BrowserViewModel.BlocklistProgress?,
    importError: String?,
    onToggleEnabled: () -> Unit,
    onToggleTrackers: () -> Unit,
    onToggleCookieBanners: () -> Unit,
    onToggleAutoUpdate: () -> Unit,
    onUpdateNow: () -> Unit,
    onAddFeedUrl: (String) -> Unit,
    onRemoveFeedUrl: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onRemoveImportedList: (String) -> Unit,
    onSetCustomEntries: (String) -> Unit,
    onBack: () -> Unit,
) {
    // What the last update actually fetched, against what is subscribed now.
    // A list switched on has no rules until the next download, and one
    // switched off keeps blocking until the merged list is rebuilt without
    // it, so the difference is the honest thing to show rather than
    // pretending the tap took effect.
    val subscribed = buildSet {
        add("ads")
        if (config.blockTrackers) add("trackers")
        if (config.hideCookieBanners) add("cookies")
        addAll(config.customFeeds)
    }
    val fetched = meta.results.filter { it.error == null }.map { it.id }.toSet()
    val pending = subscribed != fetched && updating == null

    PaneHeader("Ad blocker", onBack)

    Column {
        MenuRow(
            Icons.Default.Block,
            "Block ads",
            onClick = onToggleEnabled,
            toggledTo = !enabled,
        ) {
            MenuSwitch(checked = enabled, onToggle = onToggleEnabled)
        }
        Caption(
            "Everything below is the same for every site. A page that only works with its ads " +
                "gets an exception of its own, from the menu's Site settings row while you're on it."
        )
        MenuRow(
            Icons.Default.Visibility,
            "Block trackers",
            onClick = onToggleTrackers,
            toggledTo = !config.blockTrackers,
        ) {
            MenuSwitch(checked = config.blockTrackers, onToggle = onToggleTrackers)
        }
        Caption(
            "Trackers disguised as part of the site you're on, which the ads list can't see. " +
                "Adds about 225,000 domains."
        )
        MenuRow(
            Icons.Default.Cookie,
            "Hide cookie banners",
            onClick = onToggleCookieBanners,
            toggledTo = !config.hideCookieBanners,
        ) {
            MenuSwitch(checked = config.hideCookieBanners, onToggle = onToggleCookieBanners)
        }
        Caption(
            if (config.hideCookieBanners) {
                "Consent banners are hidden before the page paints, and the scroll lock they " +
                    "leave behind is undone. ${grouped(cosmeticCount)} rules loaded. " +
                    "Nothing is accepted or refused on your behalf — the choice is just not " +
                    "put in front of you."
            } else {
                "Consent banners are left alone."
            }
        )

        StatusBlock(
            enabled = enabled,
            ruleCount = ruleCount,
            blockedOnPage = blockedOnPage,
            meta = meta,
            updating = updating,
            pending = pending,
            onUpdateNow = onUpdateNow,
        )

        MenuRow(
            Icons.Default.Refresh,
            "Update weekly",
            onClick = onToggleAutoUpdate,
            toggledTo = !config.autoUpdate,
        ) {
            MenuSwitch(checked = config.autoUpdate, onToggle = onToggleAutoUpdate)
        }
        Caption("Lists are re-downloaded in the background once they're more than a week old.")

        HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        SectionHeader("Your own lists")
        Caption(
            "Any URL serving a hosts file, a plain domain list or AdGuard-style ||domain^ rules. " +
                "The format is detected per line."
        )
        config.customFeeds.forEach { url ->
            CustomFeedRow(
                url = url,
                result = meta.results.firstOrNull { it.id == url },
                onRemove = { onRemoveFeedUrl(url) },
            )
        }
        AddFeedField(onAdd = onAddFeedUrl)

        config.importedLists.forEach { list ->
            ImportedListRow(
                label = list.label,
                detail = "${grouped(list.count)} rules · from a file",
                onRemove = { onRemoveImportedList(list.id) },
            )
        }
        ImportFileRow(onPicked = onImportFile)
        if (importError != null) {
            Text(
                text = importError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        } else {
            Caption(
                "A file is copied in and takes effect straight away — no update needed, and " +
                    "nothing depends on the file staying where it was."
            )
        }

        HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        SectionHeader("Your own rules")
        Caption(
            "One per line. These are merged in without a download, so they take effect as soon " +
                "as you stop typing. A domain covers its subdomains."
        )
        CustomEntriesField(value = config.customEntries, onCommit = onSetCustomEntries)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusBlock(
    enabled: Boolean,
    ruleCount: Int,
    blockedOnPage: Int,
    meta: BlocklistStore.Meta,
    updating: BrowserViewModel.BlocklistProgress?,
    pending: Boolean,
    onUpdateNow: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            text = if (ruleCount == 0) "No list loaded" else "${grouped(ruleCount)} domains",
            color = if (enabled) InkStrong else InkFaint,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = when {
                updating != null -> updating.label.ifBlank { "Starting…" }
                meta.updatedAt == 0L -> "Never updated — the built-in starter list is in use."
                else -> "Updated ${relativeTime(meta.updatedAt)}"
            },
            color = InkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (updating != null) {
            Spacer(Modifier.height(10.dp))
            // Indeterminate until the first list reports in — `total` is only
            // known once the run has assembled its source list.
            if (updating.total > 0) {
                LinearProgressIndicator(
                    progress = { (updating.index + 1f) / updating.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentColor,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentColor)
            }
        }
        if (enabled && blockedOnPage > 0 && updating == null) {
            Text(
                text = "${grouped(blockedOnPage)} ${plural(blockedOnPage.toLong(), "request")} " +
                    "blocked on the page you were on.",
                color = InkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val failures = meta.results.filter { it.error != null }
        if (failures.isNotEmpty() && updating == null) {
            Text(
                text = failures.joinToString("\n") { "${it.label}: ${it.error}" },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (pending) {
            Text(
                text = "Lists changed — update to apply.",
                color = AccentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onUpdateNow,
            enabled = updating == null,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) {
            if (updating != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.size(10.dp))
                Text("Updating…")
            } else {
                Text("Update now")
            }
        }
    }
}

/**
 * The file picker. `OpenDocument` rather than `GetContent`: this is a document
 * the user is handing over on purpose, and it is the contract that gives a
 * durable-enough read grant plus the system's own file browser rather than
 * whatever gallery-style app claims `GET_CONTENT`.
 *
 * The MIME filter is deliberately wide. A blocklist arrives as `text/plain`
 * from Files, as `application/octet-stream` from most download providers and
 * with no type at all from a few — a filter of text types greys out exactly
 * the hosts file the user came here to pick, so anything is offered and the
 * CONTENT is what decides: [BlocklistStore.importList] refuses a file with no
 * rules in it, which is a better answer than a picker that cannot see it.
 */
@Composable
private fun ImportFileRow(onPicked: (Uri) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPicked) }
    MenuRow(
        Icons.Default.FileOpen,
        "Import from a file",
        onClick = {
            // A device with no document provider at all -- and there are
            // stripped-down ones -- throws rather than returning nothing.
            runCatching { picker.launch(arrayOf("*/*")) }.onFailure {
                android.widget.Toast
                    .makeText(context, "No file picker on this device", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        },
    ) {}
}

@Composable
private fun ImportedListRow(label: String, detail: String, onRemove: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(24.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp)
        ) {
            Text(label, color = InkStrong, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = {
            haptics.confirm()
            onRemove()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove list",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CustomFeedRow(
    url: String,
    result: BlocklistStore.FeedResult?,
    onRemove: () -> Unit,
) {
    val haptics = rememberHaptics()
    val detail = when {
        result?.error != null -> "Failed: ${result.error}"
        result != null -> "${grouped(result.count)} domains"
        else -> "Not fetched yet"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(top = 10.dp, bottom = 10.dp, end = 12.dp)
        ) {
            Text(url, color = InkStrong, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                color = if (result?.error != null) MaterialTheme.colorScheme.error else InkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = {
            haptics.confirm()
            onRemove()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove list",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddFeedField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(specialCorner(24.dp))
                .background(FieldBg)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    "https://example.com/hosts.txt",
                    color = InkFaint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
                cursorBrush = SolidColor(InkStrong),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = submit) {
            Icon(Icons.Default.Add, contentDescription = "Add list", tint = Ink)
        }
    }
}

/**
 * Committed on a pause rather than on every keystroke: each commit writes the
 * config file and rebuilds the whole index, which is not something to do per
 * character. Local state is the source of truth while the field has the
 * user's attention, so a commit round-tripping back through the ViewModel
 * can't move the cursor.
 */
@Composable
private fun CustomEntriesField(value: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(text) {
        if (text != value) {
            delay(800)
            onCommit(text)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(specialCorner(16.dp))
            .background(FieldBg)
            .heightIn(min = 140.dp)
            .padding(16.dp),
    ) {
        if (text.isEmpty()) {
            Text(
                "ads.example.com\n||tracker.example^\n0.0.0.0 metrics.example.net",
                color = InkFaint,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = InkStrong,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(InkStrong),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Uri,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = InkMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    )
}

private fun grouped(count: Int): String {
    val digits = count.toString()
    return buildString {
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(c)
        }
    }
}

private fun relativeTime(at: Long): String {
    val elapsed = System.currentTimeMillis() - at
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
        hours < 24 -> "$hours ${plural(hours, "hour")} ago"
        else -> "$days ${plural(days, "day")} ago"
    }
}

private fun plural(count: Long, word: String) = if (count == 1L) word else "${word}s"
