package com.yuku.browser.ui

import android.view.ActionMode

/**
 * An Activity that publishes the [ActionMode] currently up over it — in
 * practice always the page's own text-selection toolbar, since that is the
 * only one anything here starts.
 *
 * It exists for the back chain. The selection toolbar is a floating
 * `PopupWindow` and is deliberately NOT focusable, so back is delivered to
 * the Activity rather than to the toolbar: without this, back on a page with
 * text selected navigates (or closes the app) and leaves the selection and
 * its toolbar standing over whatever arrives next. Every other browser
 * clears the selection first, and so does every ordinary text field on the
 * platform.
 *
 * Backed by a Compose `mutableStateOf` in the implementations, so reading it
 * from a composable subscribes to it like any other state.
 */
interface ActionModeOwner {
    val activeActionMode: ActionMode?
}
