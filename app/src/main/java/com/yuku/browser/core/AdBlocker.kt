package com.yuku.browser.core

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hostname-level blocking — the pragmatic 90/10 of ad blocking, and the same
 * thing a DNS-level blocker (pi-hole, OpenWrt's adblock) does, applied inside
 * the browser instead of on the network.
 *
 * shouldBlock() is called by WebView on a BACKGROUND thread for every single
 * subresource on every page, so it must be thread-safe and sub-millisecond.
 * The list is therefore held as a **sorted array of 64-bit hashes** rather
 * than a set of strings: a few hundred thousand `String`s is tens of megabytes
 * of heap for the life of the process, where their hashes are a couple, and a
 * binary search over them is ~18 array reads. Nothing allocates on the hot
 * path — the suffix walk hashes in place rather than calling `substring`.
 *
 * The list itself comes from [BlocklistStore], which downloads and merges the
 * feeds; this class only ever receives a finished index via [apply].
 */
class AdBlocker {

    /** Sorted, deduplicated FNV-1a hashes of blocked domains. */
    @Volatile
    private var hosts: LongArray = LongArray(0)

    /**
     * The tracker feed's own domains, disjoint from the rest of [hosts] by
     * construction (see [BlocklistStore.buildTrackerIndex]). Searched only for
     * a site that has turned tracker blocking off on its own — everywhere
     * else [hosts] answers alone and this costs nothing.
     */
    @Volatile
    private var trackers: LongArray = LongArray(0)

    /**
     * Whether [apply] has ever run. Until it has, this blocker does not yet
     * know anything and an empty [hosts] means "not loaded", not "nothing to
     * block" -- see [awaitIndex].
     */
    @Volatile
    private var indexed = false

    /** Counted down by the first [apply]. */
    private val indexLatch = CountDownLatch(1)

    /**
     * Set when a wait has timed out, so the cost of an index that never
     * arrives is paid once rather than on every subresource of every page for
     * the life of the process.
     */
    @Volatile
    private var waitAbandoned = false

    @Volatile
    var enabled: Boolean = true

    private val pageBlocks = AtomicInteger(0)

    /**
     * Which of the two lists apply to the page a request belongs to.
     *
     * Per PAGE, not per request: what a subresource is filtered by is a
     * property of the document that asked for it, which is why the caller
     * hands this in rather than this class looking anything up — it is called
     * on a background thread for every subresource of every page, and a map
     * lookup keyed by a parsed host is the kind of work that is not affordable
     * here. See [SiteSettings] for where the answer comes from.
     */
    data class Rules(val ads: Boolean, val trackers: Boolean) {
        val blocksNothing: Boolean get() = !ads && !trackers

        companion object {
            /** What every site gets until it says otherwise. */
            val All = Rules(ads = true, trackers = true)
        }
    }

    /** Swap in a freshly built index. Cheap: one reference assignment. */
    fun apply(index: LongArray) {
        hosts = index
        indexed = true
        // Releases anything parked in awaitIndex(). Counting down an
        // already-zero latch is a no-op, so a rebuild costs nothing.
        indexLatch.countDown()
    }

    /** The tracker-only index. Not latched: [apply] is what pages wait on. */
    fun applyTrackers(index: LongArray) {
        trackers = index
    }

    /**
     * Blocks the calling resource thread until there IS an index, for at most
     * [READY_WAIT_MS].
     *
     * The index is built on an IO thread from the ViewModel's `init`, while
     * the first WebView is built by the first composition and starts fetching
     * as soon as the splash lets go. That was a race the page won: measured on
     * device, an 11.3s parse against a page whose subresources went out at
     * ~3s, so a cold start's first page loaded its trackers unfiltered. It is
     * the worst possible page to miss, because it is the restored tab -- the
     * one page every single launch loads.
     *
     * [BlocklistStore.buildIndex] is what actually fixed that (a cached index
     * makes it ~10ms), so this is the belt to its braces: the one build that
     * is still slow is the reparse after an update, and nothing guarantees a
     * page won't be loading during it.
     *
     * Waiting is the cheap way out of it, because of what is NOT waiting:
     * the main document returns above, so the page itself is never held up,
     * and by the time a subresource exists the parser has already run, which
     * is most of the time the index needed. The alternative -- building the
     * index before the first WebView -- puts the same milliseconds in front
     * of every launch instead of only the ones that race.
     *
     * Fails OPEN. A blocker that hasn't loaded blocks nothing; it must never
     * turn into a browser that loads nothing.
     */
    private fun awaitIndex(): Boolean {
        if (indexed) return true
        if (waitAbandoned) return false
        val arrived = runCatching {
            indexLatch.await(READY_WAIT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        // Self-healing either way: `indexed` is what later calls read, so a
        // late index still blocks normally on the next page.
        if (!arrived) waitAbandoned = true
        return arrived
    }

    /** How many domains are currently loaded — shown on the ad blocker screen. */
    fun ruleCount(): Int = hosts.size

    fun shouldBlock(request: WebResourceRequest, rules: Rules = Rules.All): Boolean {
        if (!enabled) return false
        // A site that has turned both off never reaches the index at all.
        if (rules.blocksNothing) return false
        // Never block the page the user actually asked for. Checked before
        // the wait as well as before the search: the main document is what
        // the user is looking at, and it is never held up for us.
        if (request.isForMainFrame) return false
        if (!awaitIndex()) return false
        val host = request.url.host ?: return false
        val merged = hosts
        val trackerOnly = trackers
        val blocked = when {
            // The ordinary case, and the only one that costs a single search:
            // the merged index already contains the trackers.
            rules.ads && rules.trackers -> merged.isNotEmpty() && matches(merged, host)
            // Everything the merged list has EXCEPT what the tracker feed
            // brought. The second search only happens for a domain the first
            // one matched, so an ordinary page pays nothing for it.
            rules.ads -> merged.isNotEmpty() && matches(merged, host) &&
                (trackerOnly.isEmpty() || !matches(trackerOnly, host))
            else -> trackerOnly.isNotEmpty() && matches(trackerOnly, host)
        }
        if (!blocked) return false
        pageBlocks.incrementAndGet()
        return true
    }

    /**
     * Match the host and each parent domain: ads.a.doubleclick.net ->
     * a.doubleclick.net -> doubleclick.net. A list entry always covers its own
     * subdomains, which is what makes a 300k-domain list usable at all.
     */
    private fun matches(sorted: LongArray, host: String): Boolean {
        val lower = if (host.any { it in 'A'..'Z' }) host.lowercase() else host
        var index = 0
        while (index < lower.length) {
            // A single-label suffix ("net") is never an entry — BlocklistStore
            // rejects dotless rules — so the walk stops one label early rather
            // than spending a search on every TLD in the world.
            val dot = lower.indexOf('.', index)
            if (dot == -1) return false
            if (contains(sorted, BlocklistStore.hash(lower, index))) return true
            index = dot + 1
        }
        return false
    }

    private fun contains(sorted: LongArray, key: Long): Boolean {
        var low = 0
        var high = sorted.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = sorted[mid]
            when {
                value < key -> low = mid + 1
                value > key -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    fun resetPageCount() = pageBlocks.set(0)

    fun pageCount(): Int = pageBlocks.get()

    companion object {
        /**
         * The cap on [awaitIndex]. Measured on device with 441k domains: a
         * cached index is ready ~40-100ms into the process, comfortably before
         * the first subresource, so this is not reached at all on an ordinary
         * launch; a full reparse takes ~3s, and against it the first page's
         * requests were held 1.03s and then answered. So it is sized to cover
         * a real overrun and no more -- past this the page is visibly stalled
         * for something it is allowed to do without.
         */
        private const val READY_WAIT_MS = 1500L

        /** A fresh stream each time — WebResourceResponse consumes it. */
        fun blockedResponse(): WebResourceResponse =
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
