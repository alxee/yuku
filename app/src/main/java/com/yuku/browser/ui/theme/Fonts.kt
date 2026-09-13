package com.yuku.browser.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.R

/** Used for both placeholder screens' big headline — [EmptyState] and [WebErrorState]. */
val WorkbenchFontFamily = FontFamily(Font(R.font.workbench))

/**
 * Shared sizing between the two full-screen placeholders so they read as one
 * family of screens rather than two independently tuned ones — same
 * headline size, same gap to the caption underneath.
 */
// Sized to fit the wider of the two headlines ("UH-OH", 6 monospace glyph
// slots incl. the dash) within typical phone screen widths without
// overflowing — "204" (3 slots) ends up comfortably smaller than it could
// be, but the two need to share one number, and this is the constraint
// that binds.
val PlaceholderHeadlineSize = 128.sp
val PlaceholderCaptionSize = 16.sp
val PlaceholderHeadlineToCaptionGap = 8.dp
val PlaceholderCaptionNudge = 0.dp
