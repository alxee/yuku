package com.yuku.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.SavedAddress
import com.yuku.browser.core.SavedCard
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.Icon
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.Text
import com.yuku.browser.ui.theme.specialCorner
import java.util.UUID

/**
 * Settings › Passwords › Addresses, behind the same device lock the saved
 * logins are behind — it is the same vault and the same kind of secret.
 *
 * Rows are added and edited HERE rather than captured from a page, which is
 * the deliberate difference from passwords: see [SavedAddress] for why a
 * checkout form is not something this browser takes a copy of. Editing opens
 * the same dialog adding does, seeded with the row — an address is eight
 * short fields, and a pane of its own for them would be a screen the user has
 * to come back out of to see whether they got it right.
 */
@Composable
internal fun SavedAddressesPane(
    entries: List<SavedAddress>,
    onSave: (SavedAddress) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    // Non-null while the dialog is up: the row being edited, or a fresh one.
    var editing by remember { mutableStateOf<SavedAddress?>(null) }

    PaneHeader("Addresses", onBack)

    if (entries.isEmpty()) {
        Caption(
            "Nothing saved yet. Add an address here and it is offered when you land in a " +
                "delivery or billing form."
        )
    } else {
        entries.sortedBy { it.title.lowercase() }.forEach { entry ->
            VaultRow(
                icon = Icons.Default.Home,
                title = entry.title,
                detail = entry.summary.ifBlank { entry.name },
                onClick = { editing = entry },
                onRemove = { onRemove(entry.id) },
            )
        }
    }

    MenuRow(Icons.Default.Add, "Add address", onClick = { editing = SavedAddress(id = newId()) }) {}

    if (entries.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        DangerRow(
            label = "Delete all addresses",
            confirmLabel = "Tap again to delete ${entries.size}",
            onConfirm = onClearAll,
        )
    }
    Spacer(Modifier.height(24.dp))

    editing?.let { draft ->
        AddressEditorDialog(
            initial = draft,
            onSave = {
                onSave(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * The same pane for payment methods. The list shows a card as the last four
 * digits and nothing else — the full number is only ever visible in the
 * editor the user opened on purpose, and the security code is not stored at
 * all (see [SavedCard]).
 */
@Composable
internal fun SavedCardsPane(
    entries: List<SavedCard>,
    onSave: (SavedCard) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<SavedCard?>(null) }

    PaneHeader("Payment methods", onBack)

    Caption(
        "The security code is never saved — no browser should keep the one number that proves " +
            "you have the card. You will still type those three digits at the checkout."
    )

    entries.sortedBy { it.title.lowercase() }.forEach { entry ->
        VaultRow(
            icon = Icons.Default.CreditCard,
            title = entry.masked,
            detail = listOf(entry.title, entry.expiry).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = { editing = entry },
            onRemove = { onRemove(entry.id) },
        )
    }

    MenuRow(Icons.Default.Add, "Add card", onClick = { editing = SavedCard(id = newId()) }) {}

    if (entries.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        DangerRow(
            label = "Delete all cards",
            confirmLabel = "Tap again to delete ${entries.size}",
            onConfirm = onClearAll,
        )
    }
    Spacer(Modifier.height(24.dp))

    editing?.let { draft ->
        CardEditorDialog(
            initial = draft,
            onSave = {
                onSave(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun newId(): String = UUID.randomUUID().toString()

/** One stored record: what it is, what it says, and the way to remove it. */
@Composable
private fun VaultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.material3.IconButton(onClick = {
            haptics.confirm()
            onRemove()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A dialog rather than a pane, like [AddEngineDialog]: this is a short form
 * being filled in, not a place in the app, and the list behind it is what the
 * user is checking the new row against.
 *
 * Saving is refused with nothing in it at all and accepted otherwise —
 * partial is normal here. Half the world's addresses have no region, plenty
 * of forms never ask for a company, and a browser that insisted on every box
 * would be a worse form than the ones it is filling in.
 */
@Composable
private fun AddressEditorDialog(
    initial: SavedAddress,
    onSave: (SavedAddress) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val haptics = rememberHaptics()
    val usable = listOf(
        draft.name, draft.street, draft.city, draft.postalCode,
        draft.country, draft.email, draft.phone,
    ).any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.yuku.browser.ui.theme.BarBg,
        shape = specialCorner(24.dp),
        title = { Text(if (initial.updatedAt == 0L) "Add address" else "Edit address", color = InkStrong) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VaultField(draft.label, "Label (Home, Work)", KeyboardType.Text) { draft = draft.copy(label = it) }
                VaultField(draft.name, "Full name", KeyboardType.Text) { draft = draft.copy(name = it) }
                VaultField(draft.organization, "Company", KeyboardType.Text) { draft = draft.copy(organization = it) }
                VaultField(draft.street, "Street address", KeyboardType.Text, singleLine = false) {
                    draft = draft.copy(street = it)
                }
                VaultField(draft.city, "City", KeyboardType.Text) { draft = draft.copy(city = it) }
                VaultField(draft.region, "State or province", KeyboardType.Text) { draft = draft.copy(region = it) }
                VaultField(draft.postalCode, "Postcode", KeyboardType.Text) { draft = draft.copy(postalCode = it) }
                VaultField(draft.country, "Country", KeyboardType.Text) { draft = draft.copy(country = it) }
                VaultField(draft.email, "Email", KeyboardType.Email) { draft = draft.copy(email = it) }
                VaultField(draft.phone, "Phone", KeyboardType.Phone) { draft = draft.copy(phone = it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = usable,
                onClick = {
                    haptics.confirm()
                    onSave(draft)
                },
            ) {
                Text("Save", color = if (usable) AccentColor else InkMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) }
        },
    )
}

@Composable
private fun CardEditorDialog(
    initial: SavedCard,
    onSave: (SavedCard) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    // Held as text rather than as the stored Int: a half-typed year is "20",
    // and a field backed by a number would keep rewriting it to 20 under the
    // user's finger.
    var month by remember(initial.id) {
        mutableStateOf(if (initial.expiryMonth in 1..12) "%02d".format(initial.expiryMonth) else "")
    }
    var year by remember(initial.id) {
        mutableStateOf(if (initial.expiryYear > 0) initial.expiryYear.toString() else "")
    }
    val haptics = rememberHaptics()
    // A card is its number. Everything else on it is optional in the sense
    // that a form can be filled without it.
    val usable = draft.digits.length >= 12

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.yuku.browser.ui.theme.BarBg,
        shape = specialCorner(24.dp),
        title = { Text(if (initial.updatedAt == 0L) "Add card" else "Edit card", color = InkStrong) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VaultField(draft.label, "Label (Personal, Work)", KeyboardType.Text) {
                    draft = draft.copy(label = it)
                }
                VaultField(draft.cardholder, "Name on card", KeyboardType.Text) {
                    draft = draft.copy(cardholder = it)
                }
                VaultField(draft.number, "Card number", KeyboardType.Number) {
                    draft = draft.copy(number = it)
                }
                Row {
                    Box(Modifier.weight(1f)) {
                        VaultField(month, "MM", KeyboardType.Number) { month = it.take(2) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        VaultField(year, "YYYY", KeyboardType.Number) { year = it.take(4) }
                    }
                }
                Text(
                    "The security code is not saved.",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = usable,
                onClick = {
                    haptics.confirm()
                    onSave(
                        draft.copy(
                            expiryMonth = month.toIntOrNull()?.takeIf { it in 1..12 } ?: 0,
                            // Two digits typed where four were asked for is
                            // this century: 30 is 2030, and it is what the
                            // card itself is printed with.
                            expiryYear = year.toIntOrNull()?.let { if (it in 0..99) 2000 + it else it } ?: 0,
                        )
                    )
                },
            ) {
                Text("Save", color = if (usable) AccentColor else InkMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) }
        },
    )
}

/**
 * One box of a vault editor. [EngineField] with a keyboard type and an
 * optional second line — the same materials, since these are the same kind of
 * thing being typed into the same kind of dialog.
 */
@Composable
private fun VaultField(
    value: String,
    hint: String,
    keyboardType: KeyboardType,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
        cursorBrush = SolidColor(InkStrong),
        keyboardOptions = KeyboardOptions(
            // A name, a street and a city are all capitalised, and a keyboard
            // that starts lowercase makes the user reach for shift on every
            // one of them. Addresses are the one place in this app where
            // autocorrect is off but capitals are wanted.
            capitalization = when (keyboardType) {
                KeyboardType.Text -> KeyboardCapitalization.Words
                else -> KeyboardCapitalization.None
            },
            autoCorrect = false,
            keyboardType = keyboardType,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(specialCorner(14.dp))
            .background(FieldBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(hint, color = InkMuted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                inner()
            }
        },
    )
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
