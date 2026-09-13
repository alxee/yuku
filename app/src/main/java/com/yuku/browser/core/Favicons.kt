package com.yuku.browser.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Site icons for hosts we have no live WebView for — history rows, bookmarks,
 * the search-engine list. Tabs get theirs from `WebViewClient.onReceivedIcon`
 * instead; this is only the fallback for pages that aren't currently open.
 *
 * Icons come from Google's s2 service rather than `https://<host>/favicon.ico`
 * because most sites now declare their icon in `<link rel="icon">` only, so the
 * well-known path 404s. Same third party the omnibox suggestions already talk
 * to, so this adds no new one.
 *
 * [ICON_PX] is asked for at four times the size any single icon is drawn at,
 * which is what a 26dp mark on a 3x screen actually needs before it stops
 * looking soft — 64px was under it, and s2 serves whichever of its own sizes
 * is nearest, so asking small is asking for a blurred upscale. The cache
 * directory carries the size in its name ([FaviconStore]), so the icons a
 * previous build saved at 64 are dropped rather than served forever.
 *
 * The in-memory cache is not evicted by age or size — it is bounded in
 * practice by the 20-entry history cap plus bookmarks, and every entry in it
 * is one an on-screen row is about to want. [releaseMemory] is the one way
 * out, under real memory pressure, and it is affordable exactly because
 * [FaviconStore] sits behind this: an icon dropped there is read back from
 * disk rather than re-requested. Same store is why an icon fetched in an
 * earlier session survives a cold start. A failed fetch caches `null` in
 * memory only: a host that has no icon today may have one tomorrow, and a
 * miss costs one request per process, not per recomposition.
 */
object Favicons {

    /** What is requested, and what the on-disk cache is keyed to. */
    const val ICON_PX = 128

    private val client = OkHttpClient()
    private val cache = ConcurrentHashMap<String, Optional>()
    private var store: FaviconStore? = null

    /** Called once from BrowserViewModel — this object outlives no process, so there's nothing to detach. */
    fun attach(store: FaviconStore) {
        this.store = store
    }

    /**
     * Seeds the cache with an icon a live WebView reported, so history and
     * bookmark rows for that host show it immediately instead of making their
     * own request for something we already have. Persisting it is the caller's
     * job (only non-private tabs' icons should reach disk).
     */
    fun remember(host: String, icon: Bitmap) {
        if (host.isNotBlank()) cache[host] = Optional(icon)
    }

    /**
     * Drops the whole cache when the system asks for memory back. Everything
     * here is either on disk or one request away, so this costs a re-read and
     * nothing else — except the icons a PRIVATE tab reported, which were
     * never written anywhere; those are re-fetched like any other miss.
     * Whether a bitmap here is still being drawn is not this object's
     * business: they are released, never recycled, so the last row holding
     * one keeps it until it goes away on its own.
     */
    fun releaseMemory() {
        cache.clear()
    }

    /** ConcurrentHashMap can't hold nulls, so misses need a box. */
    private class Optional(val bitmap: Bitmap?)

    suspend fun fetch(host: String): Bitmap? {
        if (host.isBlank()) return null
        cache[host]?.let { return it.bitmap }
        return withContext(Dispatchers.IO) {
            store?.read(host)?.let { saved ->
                // A file with nothing in it is not an answer. One got written
                // at some point (an empty body that still decoded, an icon
                // whose ink never arrived) and, being cached, it was then
                // served forever: the site showed a blank where its mark
                // should be and no refetch was ever attempted. Dropping it
                // here is the only place that loop can be broken, since the
                // read is what keeps it alive.
                if (saved.hasInk()) {
                    cache[host] = Optional(saved)
                    return@withContext saved
                }
                store?.forget(host)
            }
            val icon = try {
                val request = Request.Builder()
                    .url("https://www.google.com/s2/favicons?sz=$ICON_PX&domain=$host")
                    .build()
                client.newCall(request).execute().use { response ->
                    val bytes = response.takeIf { it.isSuccessful }?.body?.bytes()
                    if (bytes == null) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: Exception) {
                null
            }
            // Same test on the way in, so a blank answer is a miss rather
            // than something to keep: it caches null for this process (one
            // request, not one per recomposition) and leaves the host with
            // its lettered fallback, which is at least legible.
            val kept = icon?.takeIf { it.hasInk() }
            cache[host] = Optional(kept)
            kept?.let { store?.write(host, it) }
            kept
        }
    }
}

/**
 * What an icon will look like once it is drawn: how much of it there is, and
 * how light it is.
 *
 * Both questions are about the screen rather than the file. [coverage] says
 * whether there is anything in it at all, and whether what there is is a mark
 * on a transparent ground rather than a tile — a mark needs a ground drawn
 * under it or it is only ever as visible as whatever it lands on. [luminance]
 * says which ground: Bing's mark is a WHITE magnifier on nothing, so a light
 * chip hides it exactly as thoroughly as the light bar did.
 */
data class IconInk(val coverage: Float, val luminance: Float)

/**
 * Measures [IconInk] on a coarse grid — an icon arrives [Favicons.ICON_PX]
 * square and this is asked on the composition thread, once per bitmap.
 */
fun Bitmap.measureInk(): IconInk {
    if (width <= 0 || height <= 0) return IconInk(0f, 0f)
    val step = maxOf(1, width / 16)
    val opaqueGround = !hasAlpha()
    var opaque = 0
    var total = 0
    var light = 0.0
    var x = 0
    while (x < width) {
        var y = 0
        while (y < height) {
            val px = getPixel(x, y)
            if (opaqueGround || (px ushr 24) > ALPHA_FLOOR) {
                opaque++
                // Rec. 601 weights, which is close enough for a decision
                // between a light chip and a dark one.
                light += 0.299 * ((px shr 16) and 0xFF) +
                    0.587 * ((px shr 8) and 0xFF) +
                    0.114 * (px and 0xFF)
            }
            total++
            y += step
        }
        x += step
    }
    if (total == 0) return IconInk(0f, 0f)
    val coverage = if (opaqueGround) 1f else opaque.toFloat() / total
    val luminance = if (opaque == 0) 0f else (light / opaque / 255.0).toFloat()
    return IconInk(coverage, luminance)
}

/** Whether there is enough drawn in this icon to be worth showing at all. */
fun Bitmap.hasInk(): Boolean = measureInk().coverage >= INK_MIN

/** Below this, an icon is blank — a mark this sparse would not read anyway. */
private const val INK_MIN = 0.005f

/** Alpha under this counts as nothing drawn: antialiasing, not ink. */
private const val ALPHA_FLOOR = 32
