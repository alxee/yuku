package com.yuku.browser.ui

import com.yuku.browser.ui.theme.aeroDroplet
import com.yuku.browser.ui.theme.tuiSoftOutlineIf
import com.yuku.browser.ui.theme.AccentColor
import androidx.compose.foundation.border
import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiCrtIf
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.yuku.browser.ui.theme.aeroGlareIf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkFaint
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.specialCorner

/**
 * Find on page: the omnibox's shape (same 56dp pill, same shadow, same
 * [FieldBg]) with a text field and up/down/close on the right, floating over
 * the page.
 *
 * It sits at the BOTTOM, riding the keyboard it comes up with — the caller
 * anchors it to `max(ime, toolbar)`, exactly like the password cards, so it
 * is always the thing directly above whatever the bottom of the screen
 * currently is. Same `adjustNothing` caveat as those: below API 30 the IME
 * reports no inset and the bar stays where the toolbar left it.
 *
 * The query lives in the ViewModel ([BrowserViewModel.FindState]) but the
 * *field* keeps its own [TextFieldValue] here, because that's where the
 * selection and composing region live; pushing the text back down from the
 * ViewModel on every keystroke would fight the IME for the cursor.
 */
@Composable
fun FindBar(
    state: BrowserViewModel.FindState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    // Where the bar is in root coordinates — Aero's page glass frosts under it.
    onBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val aero = com.yuku.browser.ui.theme.LocalAero.current
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    val focus = remember { FocusRequester() }

    // The bar is opened by a menu row, i.e. with no keyboard up and nothing
    // to type into yet — it asks for both itself, the moment it exists.
    LaunchedEffect(Unit) { focus.requestFocus() }

    val hasQuery = field.text.isNotEmpty()
    // A query that matched nothing is worth saying plainly; before anything
    // is typed there is nothing to report at all.
    val counted = hasQuery && state.matchCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(56.dp)
            .then(
                if (onBounds != null) Modifier.onGloballyPositioned {
                    onBounds(it.boundsInRoot())
                } else Modifier
            )
            // The 98 bar is framed but casts no shadow over the page.
            .then(
                if (LocalNinety8.current) Modifier
                // A tube lights things; it does not cast shadows under them.
                // A shadow under frosted glass shows through it.
                else if (LocalNothing.current || com.yuku.browser.ui.theme.LocalTui.current || aero || com.yuku.browser.ui.theme.LocalFrosted.current) Modifier
                else Modifier.shadow(elevation = 6.dp, shape = specialCorner(24.dp), clip = false)
            )
            .clip(specialCorner(24.dp))
            .background(
                if (LocalNinety8.current) MaterialTheme.colorScheme.surface
                // The sheets' glass, frosted by the page glass under it.
                // Under Aero, the toolbar's glass fill.
                else if (aero) com.yuku.browser.ui.theme.BarBg
                // Translucent sheets: the sheets' frosted material, the page
                // blurred under it by `frostedSheetGlass`.
                else if (com.yuku.browser.ui.theme.LocalFrosted.current) com.yuku.browser.ui.theme.frostedSheetFill(
                    FieldBg, com.yuku.browser.ui.theme.AccentColor, com.yuku.browser.ui.theme.LocalNothing.current,
                )
                else FieldBg
            )
            .then(if (LocalNothing.current) Modifier.border(1.dp, HairLine, specialCorner(24.dp)) else Modifier)
            .bevel98If()
            .tuiCrtIf()
            .tuiBloomIf()
            // Terminal controls are ruled in ink; the selected phosphor is
            // reserved for their bloom and the canvas beneath them.
            .tuiSoftOutlineIf(InkMuted.copy(alpha = 0.65f))
            // The one surface in the app that floats over a live page with
            // nothing between, which is the best backdrop a pane of glass in
            // this theme ever gets. It keeps the ordinary drop shadow above:
            // glass casts one, and here there is something for it to fall on.
            .then(
                if (aero) Modifier
                    .aeroDroplet(24.dp, glare = true)
                    .grain(AERO_BAR_GRAIN)
                else Modifier
            )
            // The bar floats over a live WebView. Without this, a tap that
            // lands on the pill but not on one of its controls goes straight
            // through to whatever the page has under it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.padding(start = 10.dp).size(20.dp),
        )
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onQueryChange(it.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = InkStrong,
                textAlign = TextAlign.Start,
            ),
            cursorBrush = SolidColor(InkStrong),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                imeAction = ImeAction.Search,
            ),
            // Enter is "next match", not "search again": the search has
            // already run on every keystroke, so re-running it would only
            // throw the user back to the first match.
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (!hasQuery) {
                        Text(
                            text = FIND_PLACEHOLDER,
                            color = InkMuted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    inner()
                }
            },
        )

        if (hasQuery) {
            Text(
                text = if (counted) "${state.activeMatch}/${state.matchCount}" else "0/0",
                color = if (counted) InkMuted else InkFaint,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
        }

        FindButton(Icons.Default.KeyboardArrowUp, "Previous match", enabled = counted) {
            haptics.tick()
            onPrevious()
        }
        FindButton(Icons.Default.KeyboardArrowDown, "Next match", enabled = counted) {
            haptics.tick()
            onNext()
        }
        FindButton(Icons.Default.Close, "Close find bar", iconSize = 22.dp) {
            haptics.tap()
            onClose()
        }
    }
}

/**
 * Narrower than MenuSheet's BarButton (36dp, not 40dp): three of these plus
 * the counter share the bar's right end with a field that still has to be
 * wide enough to read a query in.
 */
@Composable
private fun FindButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    iconSize: Dp = 26.dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Ink else InkFaint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private const val FIND_PLACEHOLDER = "Find on page"
