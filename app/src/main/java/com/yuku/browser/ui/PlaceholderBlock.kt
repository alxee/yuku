package com.yuku.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import com.yuku.browser.ui.theme.Text
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import com.yuku.browser.ui.theme.LocalNothing
import com.yuku.browser.ui.theme.NothingMonoFamily
import com.yuku.browser.ui.theme.PlaceholderCaptionNudge
import com.yuku.browser.ui.theme.PlaceholderHeadlineSize
import com.yuku.browser.ui.theme.PlaceholderHeadlineToCaptionGap

/** The face is set to exactly this fraction of the screen's width. */
private const val BlockWidthFraction = 0.5f

/**
 * The caption is NOT width-matched to the face — its size is derived from this
 * one reference string instead, so every placeholder's caption comes out at
 * the same point size no matter how long its own wording is. It's [EmptyState]'s
 * caption, sized to [BlockWidthFraction] like the faces are.
 */
private const val CaptionMetric = "nothing to see here"

/**
 * The shared body of [EmptyState] and [WebErrorState]: a kaomoji over a line
 * of caption, centered.
 *
 * The face is sized by WIDTH, not by point size — measured at a base size and
 * scaled so it renders exactly [BlockWidthFraction] of the screen across, which
 * keeps faces of wildly different glyph counts the same size on screen. The
 * caption is sized off [CaptionMetric] rather than its own width, so captions
 * stay consistent between screens instead of shrinking as the wording grows.
 *
 * The face is deliberately NOT set in `WorkbenchFontFamily`: none of its
 * glyphs live in that font, so it would come out a row of tofu.
 * `FontFamily.Default` lets per-glyph fallback pick them up from the system
 * fonts.
 */
@Composable
internal fun PlaceholderBlock(
    face: String,
    faceColor: Color,
    // Null for a face on its own — the caption row is left out entirely
    // rather than set to "", which would still take its gap and its line box
    // and leave the face sitting off-centre.
    caption: String?,
    captionColor: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        // Under Nothing the caption takes that theme's own monospace; every
        // other theme keeps the platform's. The face only — it is NOT cased
        // up with the theme's labels, see the caption below.
        val captionFamily = if (LocalNothing.current) NothingMonoFamily else FontFamily.Monospace
        val target = with(LocalDensity.current) { maxWidth.toPx() } * BlockWidthFraction

        fun sizeToFit(text: String, family: FontFamily): TextUnit {
            val style = TextStyle(fontSize = PlaceholderHeadlineSize, fontFamily = family)
            val natural = measurer.measure(text, style, softWrap = false).size.width
            return if (natural > 0) {
                PlaceholderHeadlineSize * (target / natural)
            } else {
                PlaceholderHeadlineSize
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlaceholderHeadlineToCaptionGap),
        ) {
            Text(
                text = face,
                color = faceColor,
                fontSize = sizeToFit(face, FontFamily.Default),
                fontFamily = FontFamily.Default,
                softWrap = false,
            )
            if (caption == null) return@Column
            // MaterialText, not the theme's `Text`, for the ONE thing that
            // wrapper would do here: under Nothing it cases up whatever is
            // set in the mono face, because that face IS the label face
            // (see `SpecialText`). This caption is set in it for the shape
            // of the letters, not because it is a label — it is a sentence
            // in the same quiet voice on every screen this block draws, and
            // NOTHING TO SEE HERE is that sentence shouted. Every caption
            // that reaches here is authored lowercase already (LoadError's
            // labels included), so the TUI's own lowercasing has nothing
            // left to do and 98 wants none.
            MaterialText(
                text = caption,
                color = captionColor,
                // Short captions are all sized off the metric, so they
                // agree between screens; a caption LONGER than it is sized
                // off itself instead, since one that overflowed would be cut
                // off at the screen's edge (softWrap is off) rather than
                // wrapped.
                fontSize = sizeToFit(
                    if (caption.length > CaptionMetric.length) caption else CaptionMetric,
                    captionFamily,
                ),
                fontFamily = captionFamily,
                softWrap = false,
                modifier = Modifier.offset(y = PlaceholderCaptionNudge),
            )
        }
    }
}
