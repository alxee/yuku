package com.yuku.browser.core

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLDecoder

/**
 * What happens when a page hands the browser a file instead of a page — the
 * far side of [android.webkit.WebView.setDownloadListener].
 *
 * Everything lands in the device's own `Downloads` folder, the same place
 * [ImageSaver] puts a saved image and the same collection [DownloadsStore]
 * reads back, so there is one answer to "where did that go" rather than one
 * per feature.
 *
 * Three kinds of address arrive here and only one of them is a download in
 * the ordinary sense:
 *
 * - `http(s)` goes to the system's [DownloadManager], which owns the retries,
 *   the progress notification and the write. It runs in another process, so
 *   it does NOT inherit the page's session — the cookies, `Referer` and
 *   user-agent WebView would have sent are attached by hand, or a file behind
 *   a login comes back as somebody's sign-in page saved under a .pdf name.
 * - `data:` is already the bytes. Nothing goes over the network and
 *   DownloadManager can't take it at all, so it is decoded and written here.
 * - `blob:` exists only inside the page's own renderer and cannot be read
 *   from outside it. That's a plain explained failure, not a mystery.
 */
object FileDownloads {

    /**
     * A download as the page described it. Assembled where the WebView is
     * (see `BrowserViewModel.create`) because that is the only place the
     * page's cookies and its own URL are known; carried out wherever the
     * storage permission can be asked for.
     */
    data class Request(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?,
        val contentLength: Long,
        /** The page the download was started from, sent as `Referer`. */
        val referer: String?,
        /**
         * The page's own cookies for [url] — read from the private profile's
         * cookie jar for a private tab, since the default one holds a
         * different user's session or none at all.
         */
        val cookie: String?,
    ) {
        /** What the file will be called, as WebView's own heuristic names it. */
        val fileName: String
            get() = runCatching {
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            }.getOrDefault("download")
    }

    /** True when this needs `WRITE_EXTERNAL_STORAGE` first — API 28 and below. */
    fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < 29

    /**
     * A download that got going. [managerId] is DownloadManager's id for an
     * http(s) transfer still in flight, and null for one already written
     * (a `data:` URI), which has no progress left to follow.
     */
    data class Started(val name: String, val managerId: Long?)

    /**
     * Starts (or performs) the download. Returns the name it is landing
     * under, for the caller to say so; throws [IOException] with a message
     * fit to show.
     */
    suspend fun start(context: Context, request: Request): Started {
        val url = request.url
        val name = request.fileName
        return when {
            url.startsWith("blob:", ignoreCase = true) ->
                throw IOException("This file only exists inside the page and can't be downloaded")

            url.startsWith("data:", ignoreCase = true) -> {
                val (bytes, mime) = decodeDataUri(url)
                saveBytes(context, name, request.mimeType ?: mime, bytes)
                Started(name, managerId = null)
            }

            url.startsWith("http://", true) || url.startsWith("https://", true) ->
                Started(name, managerId = enqueue(context, request, name))

            else -> throw IOException("Can't download from that address")
        }
    }

    private suspend fun enqueue(context: Context, request: Request, name: String): Long =
        withContext(Dispatchers.IO) {
            val manager = context.getSystemService(DownloadManager::class.java)
                ?: throw IOException("No download service on this device")
            val download = DownloadManager.Request(Uri.parse(request.url)).apply {
                // The three headers WebView would have sent itself. Without
                // them a great many downloads are a login page or a 403.
                request.cookie?.takeIf { it.isNotBlank() }
                    ?.let { addRequestHeader("Cookie", it) }
                request.referer?.takeIf { it.isNotBlank() }
                    ?.let { addRequestHeader("Referer", it) }
                request.userAgent?.takeIf { it.isNotBlank() }
                    ?.let { addRequestHeader("User-Agent", it) }
                request.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                setTitle(name)
                // Downloading is something the user asked for and then walked
                // away from — the notification is the only place progress and
                // failure are visible once they have.
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                // DownloadManager makes the name unique itself if this one is
                // taken, rather than overwriting or failing.
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            try {
                manager.enqueue(download)
            } catch (e: Exception) {
                throw IOException("Couldn't start the download")
            }
        }

    /**
     * Writes bytes we already hold into the public Downloads collection.
     * Shared with [ImageSaver], which reaches this the same way for the same
     * reason: on API 29+ the resolver owns the path and `IS_PENDING` keeps a
     * half-written file out of every file manager; below that there is no
     * Downloads collection at all, so the file is written directly (which is
     * what `WRITE_EXTERNAL_STORAGE`, maxSdkVersion 28 in the manifest, is
     * for) and the media scanner is told, since nothing else will.
     */
    suspend fun saveBytes(context: Context, name: String, mime: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Couldn't create a file in Downloads")
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IOException("Couldn't open the new file")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } else {
                val dir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, name)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mime),
                    null,
                )
            }
        }

    /** `data:[<mime>][;base64],<payload>` — everything before the comma is metadata. */
    private fun decodeDataUri(url: String): Pair<ByteArray, String> {
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("Malformed file data")
        val meta = url.substring("data:".length, comma)
        val payload = url.substring(comma + 1)
        val bytes = if (meta.contains("base64", ignoreCase = true)) {
            // Standard alphabet first, url-safe only as a fallback: the two
            // flags can't be ORed — URL_SAFE swaps `+/` for `-_` rather than
            // accepting both. See ImageSaver, where the same mistake was made
            // and fixed.
            try {
                Base64.decode(payload, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                Base64.decode(payload, Base64.URL_SAFE)
            }
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
        if (bytes.isEmpty()) throw IOException("Malformed file data")
        val mime = meta.substringBefore(';').trim().takeIf { it.isNotBlank() }
        return bytes to (mime ?: "application/octet-stream")
    }

    /** The extension for a mime type, for callers naming a file themselves. */
    fun extensionFor(mime: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
}
