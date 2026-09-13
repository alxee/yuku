package com.yuku.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.SavedPassword
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkFaint
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong

/**
 * Settings > Passwords: whether logins are kept at all, who keeps them, and —
 * only when the answer to that is this browser — the door to the list.
 *
 * **"Who" is a choice between two apps, not a switch labelled with one of
 * them.** What the setting actually reaches is the platform autofill service,
 * whichever app the user has put in that role, so the row names the one they
 * actually chose ([AutofillProviders]) rather than assuming Google. The
 * choice itself lives in system settings — an app cannot appoint another
 * app's autofill service — so picking the external side when there is nobody
 * in the role sends them there instead of selecting a manager that doesn't
 * exist.
 *
 * The two are exclusive by construction rather than by luck: the same flag
 * that gives the fields to the service withholds them from it (see
 * `BrowserViewModel.applyAutofillImportance`), so the keyboard's strip and
 * this browser's own fill bar can never both be offering the same login.
 *
 * The list is one step further in, behind [DeviceLock] — everything on this
 * pane is a preference, and preferences are not secrets, so making the whole
 * pane ask for a fingerprint would put a prompt in front of flipping a
 * switch.
 */
@Composable
internal fun PasswordsPane(
    savePasswords: Boolean,
    externalManager: Boolean,
    savedCount: Int,
    addressCount: Int,
    cardCount: Int,
    fillAddresses: Boolean,
    fillPaymentMethods: Boolean,
    onToggleSavePasswords: () -> Unit,
    onSetExternalManager: (Boolean) -> Unit,
    onToggleFillAddresses: () -> Unit,
    onToggleFillPaymentMethods: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenCards: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Read once per entry into the pane, the way DefaultBrowserRow reads its
    // own answer and for the same reason: the user's route to changing this
    // is leaving for the system Settings app and coming straight back.
    val provider = remember { AutofillProviders.current(context) }

    PaneHeader("Passwords & autofill", onBack)

    MenuRow(
        Icons.Default.Key,
        "Save passwords",
        onClick = onToggleSavePasswords,
        toggledTo = !savePasswords,
    ) {
        MenuSwitch(checked = savePasswords, onToggle = onToggleSavePasswords)
    }

    HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

    SectionHeader("Managed by")
    ManagerRow(
        icon = Icons.Default.PhoneAndroid,
        label = "Yuku",
        detail = "Kept in this browser, on this device only",
        selected = !externalManager,
        onClick = { onSetExternalManager(false) },
    )
    ManagerRow(
        icon = Icons.Default.Cloud,
        label = provider?.label ?: if (provider != null) "Your password manager" else "No password manager",
        detail = when {
            provider == null -> "Tap to choose one in system settings"
            externalManager -> "Kept in this app, offered by the keyboard"
            else -> "Offered by the keyboard when you sign in"
        },
        selected = externalManager,
        // Nobody in the role means there is nothing to select: sending the
        // user to pick one is the only move that gets them anywhere.
        onClick = { if (provider == null) AutofillProviders.openPicker(context) else onSetExternalManager(true) },
    )

    if (externalManager) {
        MenuRow(
            Icons.AutoMirrored.Filled.OpenInNew,
            "Change password manager",
            onClick = { AutofillProviders.openPicker(context) },
        ) { Chevron() }
    }

    Caption(
        if (externalManager) {
            "Sign-in forms in Yuku are handed to your password manager, which saves and fills " +
                "them from the keyboard. Yuku keeps nothing of its own."
        } else {
            "Yuku saves logins itself and offers them above the keyboard. Your password manager " +
                "is kept out of this browser so the two don't offer the same login twice — " +
                "including for addresses and cards, which it also stops filling here."
        }
    )

    // Only where there is something to manage: with the logins kept
    // elsewhere, this browser holds nothing to show.
    if (!externalManager) {
        HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        MenuRow(
            Icons.Default.Lock,
            "Saved passwords",
            // The confirmation is asked for on the way IN rather than by the
            // pane once it is up: a list that appears and then covers itself
            // has already been on screen.
            onClick = {
                DeviceLock.confirm(context, "Confirm it's you to see your saved passwords") {
                    onOpenSaved()
                }
            },
        ) {
            RowValue(if (savedCount == 0) "None saved" else "$savedCount saved")
        }

        // The other half of what a form asks for, and the half the browser
        // had nothing to offer until now: under "Yuku manages passwords" the
        // page's fields are withheld from the platform service altogether
        // (see BrowserViewModel.applyAutofillImportance), so a checkout used
        // to get an offer from nobody at all. These close that.
        //
        // Two switches rather than one because they are two different amounts
        // of trust: a street the user has already handed to a courier is not
        // a card number.
        HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        SectionHeader("Autofill")
        MenuRow(
            Icons.Default.Home,
            "Fill addresses",
            onClick = onToggleFillAddresses,
            toggledTo = !fillAddresses,
        ) {
            MenuSwitch(checked = fillAddresses, onToggle = onToggleFillAddresses)
        }
        MenuRow(
            Icons.Default.CreditCard,
            "Fill payment methods",
            onClick = onToggleFillPaymentMethods,
            toggledTo = !fillPaymentMethods,
        ) {
            MenuSwitch(checked = fillPaymentMethods, onToggle = onToggleFillPaymentMethods)
        }
        Caption(
            "Offered above the keyboard when you land in a delivery or payment form, from the " +
                "same encrypted vault the logins are in. Nothing is ever taken off a page — you " +
                "type these in once, below."
        )
        MenuRow(
            Icons.Default.Home,
            "Addresses",
            onClick = {
                DeviceLock.confirm(context, "Confirm it's you to see your saved addresses") {
                    onOpenAddresses()
                }
            },
        ) {
            RowValue(if (addressCount == 0) "None saved" else "$addressCount saved")
        }
        MenuRow(
            Icons.Default.CreditCard,
            "Payment methods",
            onClick = {
                DeviceLock.confirm(context, "Confirm it's you to see your saved cards") {
                    onOpenCards()
                }
            },
        ) {
            RowValue(if (cardCount == 0) "None saved" else "$cardCount saved")
        }
    }

    Spacer(Modifier.height(24.dp))
}

/**
 * One of the two candidates for the job, with what picking it means written
 * underneath. Two lines rather than a row and a caption because these are
 * alternatives being compared, and a difference the eye has to hold across
 * half a screen isn't one being compared at all.
 */
@Composable
private fun ManagerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.toggle(!selected)
                onClick()
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = label,
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = detail, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = AccentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The vault itself, reached only through [DeviceLock]. Sites the user
 * answered "Never" for live down here too: it is the same fact about the same
 * sites, and it is the one place undoing that answer belongs.
 */
@Composable
internal fun SavedPasswordsPane(
    entries: List<SavedPassword>,
    neverSaved: Set<String>,
    onRemove: (host: String, username: String) -> Unit,
    onAllowSaving: (host: String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Saved passwords", onBack)

    if (entries.isEmpty()) {
        Caption("Nothing saved yet. Sign in to a site and the offer to keep it appears here.")
    } else {
        // Grouped by site, sites alphabetical, so a user with two accounts on
        // one site sees them as two rows under one heading rather than as two
        // unrelated entries somewhere in a list.
        entries
            .groupBy { it.host }
            .toSortedMap(compareBy { it.lowercase() })
            .forEach { (host, forHost) ->
                forHost.sortedBy { it.username.lowercase() }.forEach { entry ->
                    SavedPasswordRow(entry = entry, onRemove = { onRemove(host, entry.username) })
                }
            }
        Spacer(Modifier.height(8.dp))
        DangerRow(
            label = "Delete all saved passwords",
            confirmLabel = "Tap again to delete ${entries.size}",
            onConfirm = onClearAll,
        )
    }

    if (neverSaved.isNotEmpty()) {
        HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        SectionHeader("Never saved")
        Caption("Sites you declined for good. Removing one here means you'll be asked again.")
        neverSaved.sortedBy { it.lowercase() }.forEach { host ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteIcon(host = host, size = 32.dp, corner = 10.dp, letterSize = 14.sp)
                Text(
                    text = host,
                    color = InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                val haptics = rememberHaptics()
                IconButton(onClick = {
                    haptics.confirm()
                    onAllowSaving(host)
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Ask again for $host",
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * One saved login. The password is a row of dots until the user asks for it,
 * and asking is per row and resets the moment the pane leaves composition —
 * a revealed password should not still be revealed when the phone is handed
 * to someone.
 */
@Composable
private fun SavedPasswordRow(entry: SavedPassword, onRemove: () -> Unit) {
    val haptics = rememberHaptics()
    var revealed by remember(entry.host, entry.username) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host = entry.host, size = 40.dp, corner = 12.dp, letterSize = 16.sp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = entry.host,
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.username.ifBlank { "No username" },
                color = InkMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Monospace and a fixed number of dots: a proportional row of
                // bullets leaks the length of the password to anyone looking.
                text = if (revealed) entry.password else "••••••••",
                color = if (revealed) Ink else InkFaint,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = {
            haptics.toggle(!revealed)
            revealed = !revealed
        }) {
            Icon(
                if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (revealed) "Hide password" else "Show password",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = {
            haptics.confirm()
            onRemove()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete saved password",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A destructive row that asks twice. Not a dialog: this pane is already a
 * full-screen destination, and the second tap is a smaller interruption than
 * a window over the list the user is looking at.
 */
@Composable
internal fun DangerRow(label: String, confirmLabel: String, onConfirm: () -> Unit) {
    val haptics = rememberHaptics()
    var armed by remember { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (armed) {
                    haptics.confirm()
                    armed = false
                    onConfirm()
                } else {
                    haptics.tap()
                    armed = true
                }
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Close, contentDescription = null, tint = error, modifier = Modifier.size(24.dp))
        Text(
            text = if (armed) confirmLabel else label,
            color = error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
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
