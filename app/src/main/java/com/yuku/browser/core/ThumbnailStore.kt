package com.yuku.browser.core

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.File

/**
 * On-disk cache of tab preview bitmaps, one compressed file per tab id.
 *
 * The rest of the persisted state is one JSON blob in SharedPreferences (see
 * [BrowserStore]) — thumbnails deliberately don't go in there: they're orders
 * of magnitude bigger than everything else combined, and they're disposable
 * (a missing one just means the switcher card is blank until the tab is
 * captured again), so they get their own files that can be dropped
 * individually without risking the real state.
 *
 * Every call here touches the filesystem — call from a background dispatcher.
 * Private tabs are never written, same principle as [BrowserStore] excluding
 * them from the saved tab list.
 */
class ThumbnailStore(app: Application) {

    private val dir = File(app.filesDir, DIR_NAME)

    /**
     * Decoded at [Bitmap.Config.RGB_565], half the bytes of the ARGB_8888 the
     * live capture produces: 1.2MB rather than 2.5MB per tab on a 1080x2392
     * screen, and a restored session holds one of these for EVERY tab, so on
     * sixteen tabs it is the difference between 20MB and 40MB of the process.
     *
     * Affordable because of what is on the other end of it: these files are
     * lossy WEBP at quality [QUALITY], so the picture coming back has already
     * been through a far coarser reduction than dropping two bits per channel,
     * and a preview has no alpha to lose (the page behind it is opaque — see
     * `applyDarkening`). The live capture stays at full depth: at most a
     * handful of tabs have one, and it is the one blown up to fill the screen
     * when a card is opened.
     *
     * `inPreferredConfig` is a request, not an instruction — a decoder that
     * finds alpha ignores it — which is exactly the right behaviour here.
     */
    fun read(id: Long): Bitmap? {
        val file = fileFor(id)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    fun write(id: Long, bitmap: Bitmap) {
        runCatching {
            dir.mkdirs()
            // Written to a temp file and renamed, so a process death
            // mid-write leaves the previous thumbnail intact instead of a
            // truncated file that decodes to null (or worse, to garbage).
            val tmp = File(dir, "$id.tmp")
            tmp.outputStream().use { out -> bitmap.compress(FORMAT, QUALITY, out) }
            if (!tmp.renameTo(fileFor(id))) tmp.delete()
        }
    }

    fun delete(id: Long) {
        runCatching { fileFor(id).delete() }
        deleteFull(id)
        deleteTop(id)
    }

    /** The strip of page under the status bar, kept beside the preview. */
    fun readTop(id: Long): Bitmap? {
        val file = topFileFor(id)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    fun writeTop(id: Long, bitmap: Bitmap) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$id.top.tmp")
            tmp.outputStream().use { out -> bitmap.compress(FORMAT, QUALITY, out) }
            if (!tmp.renameTo(topFileFor(id))) tmp.delete()
        }
    }

    fun deleteTop(id: Long) {
        runCatching { topFileFor(id).delete() }
    }

    /**
     * The FULL-SCREEN grab (page plus the strip under the glass toolbar), kept
     * beside the page-box preview so a restored session's cards zoom out of
     * the whole screen rather than a page box with a blank strip under it.
     */
    fun readFull(id: Long): Bitmap? {
        val file = fullFileFor(id)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    fun writeFull(id: Long, bitmap: Bitmap) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$id.full.tmp")
            tmp.outputStream().use { out -> bitmap.compress(FORMAT, QUALITY, out) }
            if (!tmp.renameTo(fullFileFor(id))) tmp.delete()
        }
    }

    fun deleteFull(id: Long) {
        runCatching { fullFileFor(id).delete() }
    }

    /** Drops thumbnails for tabs that no longer exist (closed while the app wasn't running). */
    fun prune(keep: Set<Long>) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                val id = file.name.substringBefore('.').toLongOrNull()
                if (id == null || id !in keep) file.delete()
            }
        }
    }

    private fun fileFor(id: Long) = File(dir, "$id.webp")
    private fun fullFileFor(id: Long) = File(dir, "$id.full.webp")
    private fun topFileFor(id: Long) = File(dir, "$id.top.webp")

    companion object {
        private const val DIR_NAME = "thumbnails"
        private const val QUALITY = 75

        @Suppress("DEPRECATION")
        private val FORMAT =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
            else Bitmap.CompressFormat.WEBP
    }
}
