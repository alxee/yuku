package com.yuku.browser.core

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * On-disk cache of site icons, one PNG per host.
 *
 * Keyed by host rather than by tab or URL because that's the key everything
 * else already uses: a tab's icon, a history row's, a bookmark's and
 * [Favicons]' network fallback all describe the same site, so one file serves
 * all of them and survives the tab that fetched it being closed.
 *
 * PNG, not WEBP like [ThumbnailStore]: these are already tiny, and lossy
 * compression on an icon this size is visible.
 *
 * The directory carries the size icons are fetched at ([Favicons.ICON_PX]),
 * so raising it retires the old cache instead of serving a sharper request's
 * blurrier answer out of it for the life of the install. The previous
 * directories are deleted on the way past — there is nothing in them worth
 * migrating, and they are one refetch each to replace.
 *
 * Every call here touches the filesystem — call from a background dispatcher.
 */
class FaviconStore(app: Application) {

    private val dir = File(app.filesDir, DIR_NAME)

    init {
        runCatching {
            app.filesDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(DIR_PREFIX) && it.name != DIR_NAME }
                ?.forEach { old -> old.deleteRecursively() }
        }
    }

    fun read(host: String): Bitmap? {
        if (host.isBlank()) return null
        val file = fileFor(host)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun write(host: String, icon: Bitmap) {
        if (host.isBlank()) return
        runCatching {
            dir.mkdirs()
            // Temp-then-rename, same reasoning as ThumbnailStore: a process
            // death mid-write leaves the previous icon rather than a
            // truncated file.
            val tmp = File(dir, "${fileName(host)}.tmp")
            tmp.outputStream().use { out -> icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!tmp.renameTo(fileFor(host))) tmp.delete()
        }
    }

    /**
     * Drops one host's file, for an icon that read back blank: a cache with
     * nothing in it answers every later fetch and no refetch is ever made,
     * so the file has to go for the host to get another chance.
     */
    fun forget(host: String) {
        if (host.isBlank()) return
        runCatching { fileFor(host).delete() }
    }

    /** Drops icons for hosts nothing open, bookmarked or in history refers to any more. */
    fun prune(keep: Set<String>) {
        val names = keep.mapTo(mutableSetOf()) { "${fileName(it)}.png" }
        runCatching {
            dir.listFiles()?.forEach { file -> if (file.name !in names) file.delete() }
        }
    }

    private fun fileFor(host: String) = File(dir, "${fileName(host)}.png")

    /**
     * Hosts are very nearly filename-safe already, but a malformed or
     * punycode-adjacent one shouldn't be able to escape the directory — so
     * anything outside the hostname alphabet becomes '_'.
     */
    private fun fileName(host: String) =
        host.lowercase().map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '_' }
            .joinToString("")
            .trim('.')
            .ifEmpty { "_" }

    companion object {
        private const val DIR_PREFIX = "favicons"
        private val DIR_NAME = "$DIR_PREFIX-${Favicons.ICON_PX}"
    }
}
