package com.yuku.browser.core

/**
 * How the reader sets an article: the paper, the face, the size and the
 * leading. See [ReaderMode] for what it is setting.
 *
 * A preference and not a per-tab state, unlike whether the reader is OPEN.
 * The reader being on is something done to the page in front of you and is
 * spent by navigating away from it; how big the text is is the same answer
 * for every article the user will ever read, and one they should not have to
 * give twice. So this is in [BrowserStore] with the rest of the settings,
 * while `readerOpen` stays in memory.
 *
 * Everything here crosses into the page as CSS (see [ReaderMode.styleJson]),
 * so each value carries the string it becomes rather than being mapped in a
 * `when` at the call site — the enum is the one place that has to change when
 * a rung or a face is added.
 */
data class ReaderSettings(
    /** Text scale as a percentage of the reader's own base size. A rung of [READER_TEXT_STEPS]. */
    val textScale: Int = 100,
    val theme: ReaderTheme = ReaderTheme.Auto,
    val font: ReaderFont = ReaderFont.Serif,
    val spacing: ReaderSpacing = ReaderSpacing.Normal,
)

/**
 * The rungs the reader's text size is set in, as percentages.
 *
 * Wider at the top than [ZOOM_STEPS] and reaching further: this is a surface
 * whose entire content is one column of prose, so there is no layout to break
 * and nothing to reflow around — the only question is how big the user wants
 * to read, and someone who wants it big wants it much bigger than a page's
 * text zoom dares go.
 */
internal val READER_TEXT_STEPS = intArrayOf(85, 100, 115, 130, 150, 175, 200)

const val DEFAULT_READER_TEXT_SCALE = 100

/**
 * The paper. [Auto] is the default and follows the same verdict the page
 * itself is darkened by (the tab's page-dark override, else the app's
 * setting), so turning the browser dark turns the reader dark with it; the
 * other three pin it, because "I read at night on paper" is a real answer
 * and one the page-dark setting has no way to express.
 */
enum class ReaderTheme(val label: String) {
    Auto("Auto"),
    Light("Light"),
    Sepia("Sepia"),
    Dark("Dark"),
}

/**
 * The face. Serif by default — this is a surface for long-form prose, which
 * is what a serif is for, and it is also what distinguishes the reader from
 * the page it replaced at a glance.
 *
 * The families are stacks rather than single names: a WebView has no
 * guaranteed serif beyond the system's, and the first name is a hint at what
 * was wanted rather than a font that will certainly be there.
 */
enum class ReaderFont(val label: String, val css: String) {
    Serif("Serif", "\"Noto Serif\",Georgia,\"Times New Roman\",serif"),
    Sans("Sans", "system-ui,-apple-system,\"Roboto\",\"Helvetica Neue\",sans-serif"),
    Mono("Mono", "ui-monospace,\"Roboto Mono\",\"DejaVu Sans Mono\",monospace"),
}

/** The leading, as the multiple of the font size that a line box gets. */
enum class ReaderSpacing(val label: String, val lineHeight: Float) {
    Tight("Tight", 1.42f),
    Normal("Normal", 1.65f),
    Loose("Loose", 1.95f),
}
