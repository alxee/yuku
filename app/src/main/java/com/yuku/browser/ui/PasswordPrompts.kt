package com.yuku.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.SavedAddress
import com.yuku.browser.core.SavedCard
import com.yuku.browser.core.SavedPassword
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.accentWash
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.specialCorner
import com.yuku.browser.ui.theme.SpecialCircle

/**
 * The two things saved passwords put on screen while a page is in front of
 * the user: the offer to keep a login that was just submitted, and the offer
 * to fill one that is already kept.
 *
 * Both are cards above the toolbar rather than sheets or dialogs. A login form
 * is a thing the user is in the middle of — a modal over it takes the page
 * away at exactly the wrong moment, and a bottom sheet would cover the field
 * being filled. Neither ever appears with the other: [SavePasswordCard] is
 * raised after a submission, by which point there is no form left to suggest
 * into.
 */
@Composable
fun SavePasswordCard(
    prompt: BrowserViewModel.PasswordPrompt,
    onSave: () -> Unit,
    onNotNow: () -> Unit,
    onNever: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = specialCorner(24.dp), clip = false)
            .clip(specialCorner(24.dp))
            .background(BarBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Key, contentDescription = null, tint = AccentColor, modifier = Modifier.size(22.dp))
            Text(
                text = if (prompt.update) "Update saved password?" else "Save password?",
                color = InkStrong,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteIcon(host = prompt.host, size = 32.dp, corner = 10.dp, letterSize = 14.sp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = prompt.host,
                    color = InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // A site that never asked for one — a PIN, a single-field
                    // passphrase — genuinely has no username, and saying so
                    // is better than an empty line the user has to interpret.
                    text = prompt.username.ifBlank { "No username" },
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "Never" is deliberately the quiet one and deliberately first:
            // it is the only button here that changes anything permanently.
            CardButton(label = "Never", tint = InkMuted) {
                haptics.confirm()
                onNever()
            }
            Spacer(Modifier.width(4.dp))
            CardButton(label = "Not now", tint = Ink) {
                haptics.tap()
                onNotNow()
            }
            Spacer(Modifier.width(4.dp))
            CardButton(label = if (prompt.update) "Update" else "Save", tint = AccentColor, strong = true) {
                haptics.confirm()
                onSave()
            }
        }
    }
}

@Composable
private fun CardButton(
    label: String,
    tint: Color,
    strong: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(specialCorner(20.dp))
            .background(if (strong) accentWash(0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The fill offer: one chip per login this browser has saved for the site.
 *
 * This exists only in the arrangement where the browser keeps the passwords
 * itself. In the other one the keyboard is doing this job, from the user's
 * Google account and scoped to the site properly, and the two never appear
 * together — the fields are withheld from autofill for exactly as long as
 * this bar is the one answering (see
 * `BrowserViewModel.applyAutofillImportance`), so there is no moment where a
 * chip here and a suggestion in the strip are both offering the same login.
 */
@Composable
fun PasswordSuggestionBar(
    suggestion: BrowserViewModel.PasswordSuggestion,
    onFill: (SavedPassword) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = specialCorner(24.dp), clip = false)
            .clip(specialCorner(24.dp))
            .background(BarBg)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            suggestion.matches.forEach { entry ->
                Chip(
                    label = entry.username.ifBlank { suggestion.host },
                    detail = "Saved in this browser",
                ) {
                    haptics.tap()
                    onFill(entry)
                }
            }
        }
        IconButton(onClick = {
            haptics.tap()
            onDismiss()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The other fill offer: one chip per address or card the vault holds.
 *
 * The same bar as [PasswordSuggestionBar] and deliberately identical to it —
 * these are one gesture (the browser offering what it already knows into the
 * field the cursor is in), and drawing them as two different things would
 * make the second one look like something else happening. Never both at
 * once: BrowserViewModel.offerAutofill stands down while a password bar or
 * save card is up, since an email box above a password is a sign-in and not
 * a checkout.
 *
 * A card chip shows the last four digits and nothing else, which is all a
 * card is ever shown as anywhere — and the security code is not in the vault
 * to begin with, so the user still types three digits at the end. See
 * [com.yuku.browser.core.SavedCard].
 */
@Composable
fun AutofillSuggestionBar(
    suggestion: BrowserViewModel.AutofillSuggestion,
    onFillAddress: (SavedAddress) -> Unit,
    onFillCard: (SavedCard) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = specialCorner(24.dp), clip = false)
            .clip(specialCorner(24.dp))
            .background(BarBg)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            suggestion.addresses.forEach { entry ->
                Chip(
                    icon = Icons.Default.Home,
                    label = entry.title,
                    detail = entry.summary.ifBlank { "Saved in this browser" },
                ) {
                    haptics.tap()
                    onFillAddress(entry)
                }
            }
            suggestion.cards.forEach { entry ->
                Chip(
                    icon = Icons.Default.CreditCard,
                    label = entry.masked,
                    // The expiry rather than the label: it is the other thing
                    // being filled, and seeing it is how the user tells an
                    // expired card from the one they meant.
                    detail = listOf(entry.title, entry.expiry)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                ) {
                    haptics.tap()
                    onFillCard(entry)
                }
            }
        }
        IconButton(onClick = {
            haptics.tap()
            onDismiss()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Password,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(specialCorner(18.dp))
            .background(FieldBg)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier
                .size(18.dp)
                .clip(SpecialCircle),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = label,
                color = InkStrong,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = InkMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
