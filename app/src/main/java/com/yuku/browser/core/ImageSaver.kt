package com.yuku.browser.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The two things the image context menu can do with what's under the finger:
 * put it in the gallery, or put it on the clipboard. Both need the bytes, and
 * getting the bytes is the interesting part —
 *
 * - `http(s)` images are re-requested rather than read out of WebView's cache
 *   (there's no API for that), so the request carries the page's cookies and
 *   its URL as `Referer`: plenty of hosts serve a placeholder, or a 403, to a
 *   bare request for an image that renders fine inside the page.
 * - `data:` URIs are already the bytes; nothing goes over the network.
 * - `blob:` URLs only exist inside the page's own renderer and can't be
 *   fetched from here at all — that's a plain, explained failure rather than
 *   a mystery.
 *
 * No Compose and no Android UI: callers own the toast, this owns the work.
 */
object ImageSaver {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Where clipboard copies are staged for [FileProvider] to hand out. */
    private const val CLIP_DIR = "shared"

    /** How long a staged clipboard image is kept before the next copy sweeps it. */
    private val CLIP_TTL_MS = TimeUnit.HOURS.toMillis(1)

    private class Image(val bytes: ByteArray, val mime: String)

    private const val SVG_MIME = "image/svg+xml"

    /** The long side of an SVG rendered for the clipboard, in pixels. */
    private const val SVG_RASTER_PX = 1024f

    /**
     * Renders an SVG to a PNG scaled so its long side is [SVG_RASTER_PX],
     * keeping its transparency. The document's own width/height are only its
     * aspect ratio here (they are often absent, or percentages), so the view
     * box is filled to the bitmap instead.
     */
    private fun rasterizeSvg(bytes: ByteArray): Image {
        val svg = try {
            SVG.getFromInputStream(bytes.inputStream())
        } catch (e: SVGParseException) {
            throw IOException("Couldn't read that image")
        }
        val box = svg.documentViewBox
        val docWidth = svg.documentWidth.takeIf { it > 0f } ?: box?.width() ?: SVG_RASTER_PX
        val docHeight = svg.documentHeight.takeIf { it > 0f } ?: box?.height() ?: SVG_RASTER_PX
        if (docWidth <= 0f || docHeight <= 0f) throw IOException("Couldn't read that image")
        if (box == null) svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
        val scale = SVG_RASTER_PX / maxOf(docWidth, docHeight)
        val width = (docWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (docHeight * scale).roundToInt().coerceAtLeast(1)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, width.toFloat(), height.toFloat()))
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return Image(out.toByteArray(), "image/png")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Saves the image into the device's own Downloads folder — the place every
     * file manager opens to and where a browser's downloads are expected to
     * be, not a private corner of the gallery. Returns the file name it landed
     * under; throws [IOException] with a message fit to show.
     */
    suspend fun saveToDownloads(context: Context, target: WebContextTarget, referer: String?): String =
        withContext(Dispatchers.IO) {
            val url = target.imageUrl ?: throw IOException("Unsupported image address")
            val image = load(target, referer)
            val name = fileName(url, image.mime)
            // The same write a page-initiated download does, in the same
            // collection — see FileDownloads.saveBytes for why it is shaped
            // the way it is on either side of API 29.
            FileDownloads.saveBytes(context, name, image.mime, image.bytes)
            name
        }

    /**
     * Puts the image itself — not its address — on the clipboard, staged in
     * the cache dir and handed out through the app's [FileProvider] so the
     * pasting app can actually read it.
     */
    suspend fun copyToClipboard(context: Context, target: WebContextTarget, referer: String?) =
        withContext(Dispatchers.IO) {
            val url = target.imageUrl ?: throw IOException("Unsupported image address")
            // A pasting app decodes the clipboard with BitmapFactory or its
            // like, and none of those draw SVG — Wikimedia's logos and
            // diagrams pasted as an empty image. So a vector is rendered to
            // pixels here; a download keeps the real file.
            val image = load(target, referer).let { if (it.mime == SVG_MIME) rasterizeSvg(it.bytes) else it }
            val dir = File(context.cacheDir, CLIP_DIR).apply { mkdirs() }
            // Old stages are swept on the way in rather than after a copy:
            // whatever is on the clipboard right now must stay readable, and
            // nothing here knows when the user is done pasting it.
            val cutoff = System.currentTimeMillis() - CLIP_TTL_MS
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            val file = File(dir, fileName(url, image.mime))
            file.writeBytes(image.bytes)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            )
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                    ?: throw IOException("No clipboard on this device")
                clipboard.setPrimaryClip(
                    ClipData.newUri(context.contentResolver, "Image", uri),
                )
            }
        }

    private fun load(target: WebContextTarget, referer: String?): Image {
        val url = target.imageUrl ?: throw IOException("Unsupported image address")
        return when {
            url.startsWith("data:", ignoreCase = true) -> decodeDataUri(url)
            url.startsWith("http://", true) || url.startsWith("https://", true) ->
                fetch(url, referer, target)
            url.startsWith("blob:", true) ->
                throw IOException("This image only exists inside the page and can't be saved")
            else -> throw IOException("Unsupported image address")
        }
    }

    private fun fetch(url: String, referer: String?, target: WebContextTarget): Image {
        val request = Request.Builder()
            .url(url)
            .apply {
                referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) }
                // The WebView's own user-agent, never OkHttp's `okhttp/x.y`:
                // Wikimedia's upload servers answer a library UA with a 403
                // ("Please set a user-agent…") for an image the page showed.
                target.userAgent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                target.cookies?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The server said ${response.code}")
            val body = response.body ?: throw IOException("The server sent nothing")
            val bytes = body.bytes()
            if (bytes.isEmpty()) throw IOException("The server sent nothing")
            val mime = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.startsWith("image/") }
            return Image(bytes, mime ?: guessMime(url))
        }
    }

    /** `data:[<mime>][;base64],<payload>` — everything before the first comma is metadata. */
    private fun decodeDataUri(url: String): Image {
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("Malformed image data")
        val meta = url.substring("data:".length, comma)
        val payload = url.substring(comma + 1)
        val bytes = if (meta.contains("base64", ignoreCase = true)) {
            // Standard alphabet first, url-safe only as a fallback — the two
            // flags can't be combined: URL_SAFE swaps `+/` for `-_` rather
            // than accepting both, so ORing them made every ordinary data URI
            // (which is standard base64) fail with "bad base-64".
            try {
                Base64.decode(payload, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                Base64.decode(payload, Base64.URL_SAFE)
            }
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
        if (bytes.isEmpty()) throw IOException("Malformed image data")
        val mime = meta.substringBefore(';').trim().takeIf { it.startsWith("image/") }
        return Image(bytes, mime ?: "image/png")
    }

    private fun guessMime(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mime = extension
            ?.takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
        return mime?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    }

    /**
     * A name from the URL's last path segment where there is one, stamped with
     * the time so a second save of the same image doesn't collide, and always
     * carrying the extension for the mime type actually being written.
     */
    private fun fileName(url: String, mime: String): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val stem = url.toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(48)
            ?: "image"
        return "$stem-${System.currentTimeMillis()}.$extension"
    }
}
