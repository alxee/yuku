package com.yuku.browser.core

import android.app.Application
import android.os.Bundle
import android.os.Parcel
import androidx.webkit.WebViewCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * On-disk cache of each tab's WebView navigation state — the bundle
 * [android.webkit.WebView.saveState] fills in, which carries the back/forward
 * list AND the current entry's scroll position.
 *
 * Without it a restored tab is a bare `loadUrl` of its last URL: the page
 * comes back at the top with no history behind it, so the session looks
 * restored but doesn't behave restored. With it, [BrowserViewModel] hands the
 * bundle to `restoreState` and the page picks up where it was left — which is
 * also what makes the page cover honest (see `coveredTabIds`): the
 * still image the cover holds up is a picture of the page at that scroll
 * offset, and the live page underneath now arrives at the same one instead of
 * jumping to the top when the cover lifts.
 *
 * Files, not the JSON blob in [BrowserStore], for the same reason as
 * [ThumbnailStore]: these are far bigger than the rest of the state, they're
 * disposable (a missing one just costs the scroll offset), and they're
 * per-tab, so one can be dropped without risking the real state.
 *
 * A [Bundle] is written by marshalling a [Parcel], which is explicitly NOT a
 * general-purpose serialization format — its layout is only guaranteed to be
 * readable by the code that wrote it. Two guards make that safe here rather
 * than merely likely: every file carries a header with a format version and
 * the version of the WebView implementation that produced it, and anything
 * that doesn't match exactly is discarded unread. A WebView update (which is
 * a Play Store component, and moves on its own schedule) therefore costs the
 * parked states once, instead of feeding a foreign parcel to `restoreState`.
 *
 * Every call here touches the filesystem — call from a background dispatcher
 * (the one exception is [read] for the current tab at startup, which is
 * deliberately synchronous; see BrowserViewModel.restoreSavedState).
 * Private tabs are never written, same rule as [BrowserStore] and
 * [ThumbnailStore].
 */
class PageStateStore(private val app: Application) {

    private val dir = File(app.filesDir, DIR_NAME)

    /** Null whenever there's nothing usable on disk — missing, stale, or unreadable. */
    fun read(id: Long): Bundle? = runCatching {
        val file = fileFor(id)
        if (!file.exists()) return null
        val bytes = DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return null
            if (input.readInt() != FORMAT_VERSION) return null
            if (input.readUTF() != webViewVersion()) return null
            input.readBytes()
        }
        if (bytes.isEmpty()) return null
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            // Explicitly the framework's loader: the bundle's contents are
            // WebView's own classes, none of which are in this app.
            parcel.readBundle(Bundle::class.java.classLoader)
        } finally {
            parcel.recycle()
        }
    }.getOrNull()

    fun write(id: Long, state: Bundle) {
        runCatching {
            val parcel = Parcel.obtain()
            val bytes = try {
                parcel.writeBundle(state)
                parcel.marshall()
            } finally {
                parcel.recycle()
            }
            if (bytes.size > MAX_BYTES) {
                // A history long enough to blow past this is worth less than
                // the disk it would take; drop what's there rather than
                // leaving an older state to be restored over a newer page.
                delete(id)
                return
            }
            dir.mkdirs()
            // Temp file then rename, same as ThumbnailStore: a kill mid-write
            // must not leave a truncated parcel behind, which is the one
            // input `restoreState` has no way to be told apart from a good one.
            val tmp = File(dir, "$id.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeUTF(webViewVersion())
                out.write(bytes)
            }
            if (!tmp.renameTo(fileFor(id))) tmp.delete()
        }
    }

    fun delete(id: Long) {
        runCatching { fileFor(id).delete() }
    }

    /** Drops states for tabs that no longer exist (closed while the app wasn't running). */
    fun prune(keep: Set<Long>) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                val id = file.name.substringBeforeLast('.').toLongOrNull()
                if (id == null || id !in keep) file.delete()
            }
        }
    }

    private fun fileFor(id: Long) = File(dir, "$id.bin")

    /**
     * Identifies the WebView implementation that produced (or is about to
     * read) a parcel. Empty when it can't be determined, which is itself a
     * consistent value — the guard only cares that the two match.
     */
    private fun webViewVersion(): String =
        runCatching { WebViewCompat.getCurrentWebViewPackage(app)?.versionName.orEmpty() }
            .getOrDefault("")

    companion object {
        private const val DIR_NAME = "pagestate"
        private const val MAGIC = 0x59554B55 // "YUKU"
        private const val FORMAT_VERSION = 1

        /** Roughly a long-lived tab's worth of history; past this it isn't worth keeping. */
        private const val MAX_BYTES = 512 * 1024
    }
}
