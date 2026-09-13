package com.yuku.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.READER_TEXT_STEPS
import com.yuku.browser.core.ReaderFont
import com.yuku.browser.core.ReaderSettings
import com.yuku.browser.core.ReaderSpacing
import com.yuku.browser.core.ReaderTheme
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.Text
import kotlin.math.roundToInt

/**
 * How the reader sets an article, as the sheet the menu's Reader mode row
 * leads to.
 *
 * A sheet rather than a full-screen destination, and this is the point of it:
 * every control here changes the article LIVE (see
 * BrowserViewModel.updateReaderSettings), and the sheet stands two thirds up
 * the screen, so the top of the page the user was reading is still showing
 * above it while they slide the size or change the paper. A destination would
 * cover the one thing being adjusted, and the user would be choosing a text
 * size from memory.
 *
 * That is also why the reader's own switch is repeated here at the top, and
 * why it too leaves the sheet standing: the pane is where the reader is being
 * set up, and having to close it to turn the thing on — or to turn it back
 * off after seeing what it did — is a step with nothing in it.
 */
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    readerAvailable: Boolean,
    readerActive: Boolean,
    onToggleReader: () -> Unit,
    onSetTextStep: (Int) -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetFont: (ReaderFont) -> Unit,
    onSetSpacing: (ReaderSpacing) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        PaneHeader("Reader mode", onBack)

        MenuRow(
            Icons.AutoMirrored.Filled.MenuBook,
            "Show article",
            onClick = onToggleReader,
            enabled = readerAvailable || readerActive,
            toggledTo = !readerActive,
        ) {
            MenuSwitch(
                checked = readerActive,
                enabled = readerAvailable || readerActive,
                onToggle = onToggleReader,
            )
        }
        Hint(
            if (readerAvailable || readerActive) {
                "The article, without the page around it."
            } else {
                // The settings below are still worth setting on a page with
                // nothing to read — they are the answer for the next article,
                // not for this one — so the pane says why the switch is off
                // rather than looking broken.
                "Nothing to read on this page. The settings below still apply."
            },
        )

        Spacer(Modifier.height(4.dp))
        TextSizeRow(settings.textScale, onSetTextStep)

        PickerLabel("Background")
        Picker(ReaderTheme.entries, settings.theme, { it.label }, onSetTheme)
        // Auto is the default and the only one of the four that is not simply
        // a colour, so it is the one that has to explain itself.
        Hint("Auto follows the browser's dark mode.")

        PickerLabel("Typeface")
        Picker(ReaderFont.entries, settings.font, { it.label }, onSetFont)

        PickerLabel("Line spacing")
        Picker(ReaderSpacing.entries, settings.spacing, { it.label }, onSetSpacing)
    }
}

/**
 * The reader's text size, in the rungs of [READER_TEXT_STEPS].
 *
 * The same row Settings gives the page's text zoom, down to the tick per rung
 * passed under the finger — this is the same gesture setting the same kind of
 * thing, and the two should not be two different controls. It carries a
 * readout the page's does not, because there is no percentage anywhere else
 * on this sheet to read the value off.
 */
@Composable
private fun TextSizeRow(scale: Int, onSetStep: (Int) -> Unit) {
    val haptics = rememberHaptics()
    // Derived from the value rather than held as its own state, so a restore
    // moves the thumb without the two being able to disagree about where it is.
    val index = READER_TEXT_STEPS.indexOfFirst { it >= scale }.let {
        if (it < 0) READER_TEXT_STEPS.lastIndex else it
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Text size",
            color = InkStrong,
            style = MaterialTheme.typography.bodyLarge,
        )
        // Fired on the CHANGE, not on every callback — a drag reports
        // continuously and most of those land on the rung the thumb is
        // already on.
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val next = it.roundToInt()
                if (next != index) {
                    haptics.tick()
                    onSetStep(next)
                }
            },
            valueRange = 0f..READER_TEXT_STEPS.lastIndex.toFloat(),
            steps = READER_TEXT_STEPS.size - 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        // A readout and not a control: 100% is a rung like any other and can
        // simply be slid back to, so there is nothing for a tap on it to undo.
        Text(
            text = "$scale%",
            color = InkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
