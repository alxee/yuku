package com.yuku.browser.core

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Owns everything on disk that the ad blocker needs: which feeds the user has
 * turned on, their own entries and feed URLs, the merged domain list, and what
 * the last update produced.
 *
 * The merged list is a plain text file of one domain per line — not a database
 * and not a serialized index — because it has to stay something a person can
 * read, and because rebuilding the runtime form from it is a linear scan (see
 * [buildIndex]). It lives in its own directory rather than in [BrowserStore]'s
 * JSON blob for the reason thumbnails do: it dwarfs everything else in there,
 * and it's regenerable.
 *
 * Everything here blocks. Call it from [kotlinx.coroutines.Dispatchers.IO].
 */
class BlocklistStore(private val context: Context) {

    /** The user's choices. Small, so one JSON file. */
    data class Config(
        /**
         * CNAME-cloaked trackers, on top of the ads list. Its own switch
         * because it is a big list (~225k domains) buying one specific thing,
         * where the ads list is the blocker's whole reason to exist.
         */
        val blockTrackers: Boolean = true,
        val hideCookieBanners: Boolean = true,
        /** Feed URLs the user added themselves. Parsed with [FeedRule.Auto]. */
        val customFeeds: List<String> = emptyList(),
        /** Rules the user typed or pasted in, verbatim — one per line. */
        val customEntries: String = "",
        /**
         * Lists imported from a file on the device. The file itself is copied
         * into [dir] on import — a `content://` URI is a loan from whichever
         * app answered the picker and is worthless on the next launch — and
         * these are merged in at [buildIndex] time rather than at download
         * time, like [customEntries], so an import takes effect immediately
         * and survives a failed update.
         */
        val importedLists: List<ImportedList> = emptyList(),
        val autoUpdate: Boolean = true,
    )

    /** One file the user imported: its copy's name, what to call it, its size. */
    data class ImportedList(
        val id: String,
        val label: String,
        val count: Int,
    )

    /** One row of the last update, per feed, successes and failures alike. */
    data class FeedResult(
        val id: String,
        val label: String,
        val count: Int,
        val error: String? = null,
    )

    /** What the last update left behind. Absent until the first one runs. */
    data class Meta(
        val updatedAt: Long = 0L,
        val total: Int = 0,
        val results: List<FeedResult> = emptyList(),
    )

    private val dir = File(context.filesDir, "blocklist")
    private val hostsFile = File(dir, "hosts.txt")
    /**
     * The tracker feed's own contribution, on its own, so that a site the
     * user turned tracker blocking off for can have exactly that subtracted.
     *
     * [hostsFile] is one merged list and nothing in it remembers which feed
     * it came from, so per-site tracker control cannot be answered from it.
     * This file is written by the same pass, and holds the lines the tracker
     * feed ACCEPTED -- which, because [update] walks the ads feed first and
     * dedupes as it goes, are exactly the domains no other list already
     * covered. So the two sets are disjoint by construction, and
     * "block ads but not trackers" is `in hosts && !in trackers` rather than
     * a subtraction that would also unblock everything the two lists agree on.
     */
    private val trackersFile = File(dir, "trackers.txt")
    private val configFile = File(dir, "config.json")
    private val metaFile = File(dir, "meta.json")
    private val indexFile = File(dir, "index.bin")
    private val trackerIndexFile = File(dir, "trackers.bin")
    private val genericCssFile = File(dir, "cosmetic_generic.css")
    private val specificFile = File(dir, "cosmetic_specific.txt")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Per-read, not per-download: an XXL feed legitimately takes a while,
        // and a whole-call timeout would kill it halfway on a slow connection.
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------ config

    fun loadConfig(): Config {
        val raw = runCatching { configFile.readText() }.getOrNull() ?: return Config()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return Config()
        val custom = json.optJSONArray("customFeeds")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        // An imported list whose file is gone is a row that can't be turned
        // back into rules, so it is dropped rather than shown.
        val imported = json.optJSONArray("importedLists")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                if (!importedFile(id).exists()) return@mapNotNull null
                ImportedList(id, o.optString("label").ifBlank { id }, o.optInt("count"))
            }
        }.orEmpty()
        return Config(
            blockTrackers = json.optBoolean("blockTrackers", true),
            hideCookieBanners = json.optBoolean("hideCookieBanners", true),
            customFeeds = custom,
            customEntries = json.optString("customEntries"),
            importedLists = imported,
            autoUpdate = json.optBoolean("autoUpdate", true),
        )
    }

    fun saveConfig(config: Config) {
        dir.mkdirs()
        val json = JSONObject().apply {
            put("blockTrackers", config.blockTrackers)
            put("hideCookieBanners", config.hideCookieBanners)
            put("customFeeds", JSONArray(config.customFeeds))
            put("customEntries", config.customEntries)
            put("importedLists", JSONArray().apply {
                config.importedLists.forEach { list ->
                    put(JSONObject().apply {
                        put("id", list.id)
                        put("label", list.label)
                        put("count", list.count)
                    })
                }
            })
            put("autoUpdate", config.autoUpdate)
            put("schema", SCHEMA)
        }
        runCatching { configFile.writeText(json.toString()) }
    }

    fun loadMeta(): Meta {
        val raw = runCatching { metaFile.readText() }.getOrNull() ?: return Meta()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return Meta()
        // A report written by an older build describes feeds that no longer
        // exist -- ids the screen can match against nothing, and a timestamp
        // that says the list is fresh when what is on disk was produced by a
        // different set of sources. It is discarded whole rather than shown:
        // "never updated" is both true and self-healing, since the age gate
        // in BrowserViewModel.loadBlocklist then fetches the current feeds.
        if (json.optInt("schema") != SCHEMA) return Meta()
        val results = json.optJSONArray("results")?.let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                FeedResult(
                    id = o.optString("id"),
                    label = o.optString("label"),
                    count = o.optInt("count"),
                    error = o.optString("error").takeIf(String::isNotBlank),
                )
            }
        }.orEmpty()
        return Meta(
            updatedAt = json.optLong("updatedAt"),
            total = json.optInt("total"),
            results = results,
        )
    }

    private fun saveMeta(meta: Meta) {
        dir.mkdirs()
        val json = JSONObject().apply {
            put("schema", SCHEMA)
            put("updatedAt", meta.updatedAt)
            put("total", meta.total)
            put("results", JSONArray().apply {
                meta.results.forEach { result ->
                    put(JSONObject().apply {
                        put("id", result.id)
                        put("label", result.label)
                        put("count", result.count)
                        result.error?.let { put("error", it) }
                    })
                }
            })
        }
        runCatching { metaFile.writeText(json.toString()) }
    }

    fun hasDownloadedList(): Boolean = hostsFile.exists() && hostsFile.length() > 0

    /**
     * Throws away what a killed or half-finished run can leave behind, so the
     * next launch starts from a state the rest of this class describes
     * honestly. Chiefly an EMPTY `hosts.txt`: [update] renames its temp file
     * into place only when something was fetched, but a build before that
     * rule -- or a rename that landed on a zero-length download -- leaves a
     * file that exists, blocks nothing, and would otherwise be treated as a
     * downloaded list forever.
     */
    fun heal() {
        if (hostsFile.exists() && hostsFile.length() == 0L) hostsFile.delete()
        if (trackersFile.exists() && trackersFile.length() == 0L) trackersFile.delete()
        runCatching { dir.listFiles()?.forEach { if (it.name.endsWith(".tmp")) it.delete() } }
        migrateConfig()
    }

    /**
     * Rewrites a config written by an older build in the current shape.
     *
     * [loadConfig] already reads one safely -- every key it no longer knows is
     * ignored and every key that isn't there defaults -- so this is not what
     * keeps the app working. What it fixes is that the file then sits on disk
     * describing a world that is gone (an `enabledFeeds` list naming feeds
     * this build has no id for) until the user happens to touch a switch, and
     * anyone reading it to find out what the browser is doing is told
     * something untrue. Round-tripping it through [saveConfig] stamps the
     * schema and drops the dead keys, keeping the three answers that still
     * mean the same thing: the user's own feeds, their own rules, and whether
     * to auto-update.
     *
     * Only from [heal], so only the real session ever writes here -- an
     * overlay reads this directory and must not race the browser proper for it.
     */
    private fun migrateConfig() {
        if (!configFile.exists()) return
        val schema = runCatching { JSONObject(configFile.readText()).optInt("schema") }.getOrNull()
        if (schema == SCHEMA) return
        saveConfig(loadConfig())
    }

    // ---------------------------------------------------------- imported

    private val importedDir = File(dir, "imported")

    fun importedFile(id: String): File = File(importedDir, "$id.txt")

    /**
     * Copies a list the user picked out of the file picker into our own
     * storage and reports what was in it.
     *
     * The copy is the point: what the picker hands back is a `content://` URI
     * whose read grant belongs to this process and this launch, so keeping it
     * would give a list that works today and is gone tomorrow, with no way to
     * tell the user why. What lands in [importedDir] is the PARSED form -- one
     * domain per line, exactly like `hosts.txt` -- so the copy is the same
     * shape everything else here reads, and a file that is half comments
     * doesn't cost its size twice. The sniff is [FeedRule.Auto], the same one
     * the user's own feed URLs get: a picked file is a hosts file, a bare
     * domain list or an AdGuard-syntax one, and nothing declares which.
     *
     * Throws [IOException] if the file is too big to be a blocklist or holds
     * no rules at all -- an empty row on the screen would be indistinguishable
     * from a working one.
     */
    fun importList(label: String, input: java.io.InputStream): ImportedList {
        importedDir.mkdirs()
        val id = "list_" + System.currentTimeMillis().toString(36) + "_" +
            (0..0xffff).random().toString(16)
        val file = importedFile(id)
        var count = 0
        var bytes = 0L
        try {
            file.bufferedWriter().use { out ->
                BufferedReader(input.reader(), 1 shl 16).forEachLine { line ->
                    bytes += line.length + 1
                    if (bytes > MAX_IMPORT_BYTES) throw IOException("File is too large")
                    if (count >= MAX_RULES) return@forEachLine
                    val domain = extractDomain(line, FeedRule.Auto) ?: return@forEachLine
                    out.write(domain)
                    out.write("\n")
                    count++
                }
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        if (count == 0) {
            file.delete()
            throw IOException("No rules found in this file")
        }
        return ImportedList(id = id, label = label, count = count)
    }

    fun deleteImportedList(id: String) {
        runCatching { importedFile(id).delete() }
    }

    // ------------------------------------------------------------ update

    /**
     * Downloads every subscribed list, merges and writes them. [onProgress] is
     * called with the label of the list about to be fetched and its position,
     * for the screen's progress line.
     *
     * A feed that fails is recorded as a failed row rather than aborting the
     * run — one dead host shouldn't cost the user the others. If *every* feed
     * fails, the existing files are left exactly as they were: a blocklist
     * that empties itself because the train wifi ate a DNS lookup is worse
     * than a stale one.
     */
    fun update(config: Config, onProgress: (label: String, index: Int, total: Int) -> Unit = { _, _, _ -> }): Meta {
        dir.mkdirs()
        val sources = buildList {
            add(Feeds.ADS)
            if (config.blockTrackers) add(Feeds.TRACKERS)
            if (config.hideCookieBanners) add(Feeds.COOKIES)
            config.customFeeds.forEach { url ->
                add(BlocklistFeed(id = url, title = shortLabel(url), url = url, rule = FeedRule.Auto))
            }
        }

        val seen = LongHashSet(1 shl 16)
        val temp = File(dir, "hosts.txt.tmp")
        val trackerTemp = File(dir, "trackers.txt.tmp")
        val results = ArrayList<FeedResult>(sources.size)
        // Cookie-banner selectors, collected as the same pass goes by. Only
        // the cookie list carries them, and only while its switch is on.
        val generic = LinkedHashSet<String>()
        val specific = HashMap<String, MutableList<String>>()
        var wrote = 0
        var anySucceeded = false

        temp.bufferedWriter().use { out ->
          trackerTemp.bufferedWriter().use { trackerOut ->
            sources.forEachIndexed { index, feed ->
                // The tracker feed's accepted lines go to a second file as
                // well as into the merge -- see [trackersFile].
                val alsoTracker = feed.id == Feeds.TRACKERS.id
                onProgress(feed.title, index, sources.size)
                var accepted = 0
                // Cosmetic rules are the cookie list's real output, so they
                // count as work done even when it contributes no domains.
                var cosmetics = 0
                val error = runCatching {
                    readLines(feed.url) { line ->
                        val domain = extractDomain(line, feed.rule)
                        if (domain != null) {
                            if (wrote < MAX_RULES && seen.add(hash(domain))) {
                                out.write(domain)
                                out.write("\n")
                                if (alsoTracker) {
                                    trackerOut.write(domain)
                                    trackerOut.write("\n")
                                }
                                wrote++
                                accepted++
                            }
                        } else if (feed.cosmetic) {
                            // Only lines the domain parser rejected can be
                            // cosmetic rules, so this costs nothing on the
                            // lines that mattered to the index.
                            CosmeticFilters.parseCosmetic(line)?.let { rule ->
                                cosmetics++
                                if (rule.domains.isEmpty()) {
                                    generic.add(rule.selector)
                                } else {
                                    rule.domains.forEach { host ->
                                        specific.getOrPut(host) { ArrayList(2) }.add(rule.selector)
                                    }
                                }
                            }
                        }
                    }
                }.exceptionOrNull()
                // Accepting nothing is not succeeding. A feed that answers
                // 200 with a body this parser cannot read (a redesigned list,
                // an error page served as text, a body we failed to decode)
                // must not be allowed to promote the run and overwrite a good
                // list with an empty one -- which is exactly how an empty
                // `hosts.txt` came to look like a completed update.
                if (error == null && (accepted > 0 || cosmetics > 0)) anySucceeded = true
                results += FeedResult(feed.id, feed.title, accepted, error?.let(::describe))
            }
          }
        }

        val meta = Meta(
            updatedAt = System.currentTimeMillis(),
            total = wrote,
            results = results,
        )
        if (anySucceeded) {
            // Rename, not write-in-place: a kill mid-download must leave the
            // previous list intact rather than a half-written one.
            if (hostsFile.exists()) hostsFile.delete()
            if (!temp.renameTo(hostsFile)) temp.copyTo(hostsFile, overwrite = true).also { temp.delete() }
            // Renamed even when it is empty -- the tracker feed being off is
            // a real state, and leaving the previous run's tracker file in
            // place would have this build subtracting a list it is no longer
            // blocking by.
            if (trackersFile.exists()) trackersFile.delete()
            if (!trackerTemp.renameTo(trackersFile)) {
                trackerTemp.copyTo(trackersFile, overwrite = true).also { trackerTemp.delete() }
            }
            writeCosmetic(config, generic, specific)
            saveMeta(meta)
            return meta
        }
        temp.delete()
        trackerTemp.delete()
        // Nothing was replaced, so the previous run's totals still describe
        // what's actually loaded — only the failures are news.
        val previous = loadMeta()
        val kept = previous.copy(results = results)
        saveMeta(kept)
        return kept
    }

    /**
     * The CSS is built here, at update time, rather than every time the app
     * starts: it is the same ~300KB stylesheet either way, and parsing fifteen
     * thousand selectors into chunked rules is work that belongs on the
     * download that produced them.
     */
    private fun writeCosmetic(
        config: Config,
        generic: Set<String>,
        specific: Map<String, List<String>>,
    ) {
        if (!config.hideCookieBanners) {
            // Off means gone, not merely unused -- a stale 300KB stylesheet
            // sitting around to be picked up by a later toggle is a bug
            // waiting to be filed.
            genericCssFile.delete()
            specificFile.delete()
            return
        }
        runCatching { genericCssFile.writeText(CosmeticFilters.buildCss(generic)) }
        runCatching {
            specificFile.bufferedWriter().use { out ->
                specific.forEach { (host, selectors) ->
                    selectors.forEach { selector ->
                        // Tab-separated: a selector can contain nearly
                        // anything, but never a tab or a newline.
                        out.write(host)
                        out.write("\t")
                        out.write(selector)
                        out.write("\n")
                    }
                }
            }
        }
    }

    /**
     * The cookie-banner rules in the form the injector wants. The bundled
     * [CosmeticFilters.FALLBACK_SELECTORS] are always folded in, so the major
     * consent platforms are covered before the first download and still
     * covered if a later one fails.
     */
    fun loadCosmetic(config: Config): CosmeticFilters.Rules {
        if (!config.hideCookieBanners) return CosmeticFilters.Rules()
        val downloaded = runCatching { genericCssFile.readText() }.getOrNull().orEmpty()
        val fallback = CosmeticFilters.buildCss(CosmeticFilters.FALLBACK_SELECTORS)
        val specific = HashMap<String, MutableList<String>>()
        runCatching {
            specificFile.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0 && tab < line.length - 1) {
                    specific.getOrPut(line.substring(0, tab)) { ArrayList(2) }
                        .add(line.substring(tab + 1))
                }
            }
        }
        val css = if (downloaded.isBlank()) fallback else fallback + "\n" + downloaded
        return CosmeticFilters.Rules(
            genericCss = css,
            specific = specific,
            // Each chunked rule is `s1,s2,...{…}`, so selectors are commas
            // plus one per rule.
            genericCount = css.count { it == ',' } + css.lineSequence().count { it.isNotBlank() },
        )
    }

    private fun readLines(url: String, onLine: (String) -> Unit) {
        val request = Request.Builder()
            .url(url)
            // Several of these feeds 403 a bare OkHttp UA.
            .header("User-Agent", USER_AGENT)
            // Accept-Encoding is deliberately NOT set. OkHttp adds `gzip` and
            // decompresses the body itself ONLY while the caller has left the
            // header alone; asking for it by hand makes the app responsible
            // for inflating, and every one of these feeds serves gzip. Setting
            // it fed the raw DEFLATE stream to `charStream()`, where no line
            // parsed as a domain and the whole run reported success with zero
            // rules -- a blocker that downloads 1.6MB and blocks nothing.
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty response")
            BufferedReader(body.charStream(), 1 shl 16).forEachLine(onLine)
        }
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    // ------------------------------------------------------------- index

    /**
     * The runtime form: every domain's 64-bit hash, sorted, for the binary
     * search in [AdBlocker]. Hashes rather than the strings themselves because
     * a quarter-million `String`s is tens of megabytes of heap held for the
     * life of the process, where the same set of hashes is two.
     *
     * Falls back to the bundled asset when nothing has been downloaded yet, so
     * a first launch still blocks the obvious offenders.
     */
    fun buildIndex(config: Config): LongArray {
        val key = indexKey(config)
        readIndexCache(key)?.let { return it }
        val hashes = LongArrayBuilder(if (hasDownloadedList()) 1 shl 17 else 64)
        if (hasDownloadedList()) {
            runCatching {
                hostsFile.bufferedReader(bufferSize = 1 shl 16).forEachLine { line ->
                    // Fast path first: this file is OUR OWN output, written by
                    // [update] through [extractDomain], so its lines are
                    // already bare validated lowercase domains. Re-deriving
                    // that costs a `split` list and up to three strings PER
                    // LINE, which at 441k lines was measured at 11.3 seconds
                    // on device -- long enough that the first page of every
                    // launch finished loading unfiltered.
                    val clean = cleanDomainHash(line)
                    if (clean != null) {
                        hashes.add(clean)
                    } else {
                        val domain = extractDomain(line, FeedRule.Domains) ?: return@forEachLine
                        hashes.add(hash(domain))
                    }
                }
            }
        } else {
            runCatching {
                context.assets.open(SEED_ASSET).bufferedReader().forEachLine { line ->
                    val domain = extractDomain(line, FeedRule.Domains) ?: return@forEachLine
                    hashes.add(hash(domain))
                }
            }
        }
        // The user's own entries are applied here, not at download time, so
        // editing them takes effect without re-fetching anything.
        config.customEntries.lineSequence().forEach { line ->
            extractDomain(line, FeedRule.Auto)?.let { hashes.add(hash(it)) }
        }
        // Imported files, for the same reason and at the same moment: they are
        // already on disk in the parsed form, so there is nothing to download
        // and an import blocks its first request on the next page load.
        config.importedLists.forEach { list ->
            runCatching {
                importedFile(list.id).bufferedReader(bufferSize = 1 shl 16).forEachLine { line ->
                    extractDomain(line, FeedRule.Domains)?.let { hashes.add(hash(it)) }
                }
            }
        }
        val index = hashes.sortedArray()
        writeIndexCache(key, index)
        return index
    }

    /**
     * The tracker feed's domains alone, as the same sorted hash array.
     *
     * Read by [AdBlocker] only for the sites that turned tracker blocking off
     * — everywhere else the merged index answers on its own and this is never
     * searched. Empty is the right answer for a build that has not downloaded
     * since [trackersFile] existed, and for a user with the tracker switch
     * off: in both cases there is nothing to subtract, which is exactly what
     * an empty array means here.
     */
    fun buildTrackerIndex(): LongArray {
        if (!trackersFile.exists() || trackersFile.length() == 0L) return LongArray(0)
        val key = trackerIndexKey()
        readCache(trackerIndexFile, key)?.let { return it }
        val hashes = LongArrayBuilder(1 shl 16)
        runCatching {
            trackersFile.bufferedReader(bufferSize = 1 shl 16).forEachLine { line ->
                // Our own output, exactly like hosts.txt — same fast path.
                val clean = cleanDomainHash(line)
                if (clean != null) {
                    hashes.add(clean)
                } else {
                    val domain = extractDomain(line, FeedRule.Domains) ?: return@forEachLine
                    hashes.add(hash(domain))
                }
            }
        }
        val index = hashes.sortedArray()
        writeCache(trackerIndexFile, key, index)
        return index
    }

    private fun trackerIndexKey(): Long {
        var key = hash("trackers$SCHEMA")
        key = mix(key, trackersFile.length())
        return mix(key, trackersFile.lastModified())
    }

    /**
     * What the index was built FROM, as one number: the merged list's size and
     * timestamp plus the two things folded in at build time. Anything that
     * changes the answer changes the key, so a stale cache can never be read
     * as a fresh one -- and an [update] renames a new `hosts.txt` into place,
     * which moves both halves of the file's half of it.
     */
    private fun indexKey(config: Config): Long {
        var key = hash("schema$SCHEMA")
        key = mix(key, hostsFile.length())
        key = mix(key, hostsFile.lastModified())
        key = mix(key, hash(config.customEntries))
        config.importedLists.forEach { list ->
            key = mix(key, hash(list.id))
            key = mix(key, list.count.toLong())
        }
        return key
    }

    /**
     * The sorted index as it was left by the last build. Parsing eleven
     * megabytes of text to arrive at the same array every launch is the
     * expensive way to read a file that never changed between them; this is
     * the array itself, and reading it is one allocation and one bulk copy.
     *
     * Any disagreement at all -- wrong key, short file, a length that doesn't
     * match the count in the header -- is answered with null, which costs a
     * rebuild and nothing else.
     */
    private fun readIndexCache(key: Long): LongArray? = readCache(indexFile, key)

    private fun readCache(file: File, key: Long): LongArray? = runCatching {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size < HEADER_BYTES) return null
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        if (buffer.long != key) return null
        val count = buffer.int
        if (count < 0 || buffer.remaining() != count * 8) return null
        val index = LongArray(count)
        buffer.asLongBuffer().get(index)
        index
    }.getOrNull()

    private fun writeIndexCache(key: Long, index: LongArray) = writeCache(indexFile, key, index)

    private fun writeCache(file: File, key: Long, index: LongArray) {
        runCatching {
            val buffer = java.nio.ByteBuffer.allocate(HEADER_BYTES + index.size * 8)
            buffer.putLong(key)
            buffer.putInt(index.size)
            buffer.asLongBuffer().put(index)
            // Rename for the same reason `hosts.txt` gets one: a half-written
            // cache that still had a valid header would be read as an index.
            val temp = File(dir, "${'$'}{file.name}.tmp")
            temp.writeBytes(buffer.array())
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    companion object {
        /**
         * Bumped whenever the shape of what is written here changes. Files
         * stamped with anything else are treated as absent -- see [loadMeta].
         *
         * 3 added `trackers.txt`, which only an [update] can produce: a
         * discarded meta reads as "never updated", which is what makes the
         * age gate in BrowserViewModel.loadBlocklist fetch once and fill it
         * in. Until it does, [buildTrackerIndex] is empty and a per-site
         * tracker exception simply has nothing to subtract -- the ads list
         * still blocks, which is the safe direction to be wrong in.
         */
        const val SCHEMA = 3

        /**
         * A picked file this big is not a blocklist, and reading it into the
         * index would cost more than every default feed combined.
         */
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

        private const val SEED_ASSET = "blocklist.txt"
        private const val USER_AGENT = "Mozilla/5.0 (Android) yuku-browser blocklist updater"

        /**
         * A ceiling on the merged list. Six figures of domains is already more
         * than every default feed combined; past this the memory and the
         * update time stop being worth it, and an accidental subscription to
         * four XXL compilations shouldn't be able to hurt the browser.
         */
        const val MAX_RULES = 600_000

        private fun shortLabel(url: String): String =
            runCatching { url.toHttpUrl().host }.getOrNull() ?: url

        /**
         * Pulls the domain out of one line, or null for comments, blanks and
         * anything that isn't a plain hostname rule (regex/CSS filters,
         * exceptions, wildcards, `$`-modified rules).
         */
        fun extractDomain(raw: String, rule: FeedRule): String? {
            val line = raw.trim()
            if (line.isEmpty()) return null
            when (line[0]) {
                // '#' comments hosts files, '!' comments adblock lists, '[' is
                // an adblock header line ("[Adblock Plus 2.0]").
                '#', '!', '[', ';' -> return null
            }
            // An exception rule un-blocks a domain; this blocker has no
            // allowlist, so honoring one by *adding* it would invert it.
            if (line.startsWith("@@")) return null

            val effective = if (rule != FeedRule.Auto) rule else when {
                line.startsWith("||") -> FeedRule.AdblockSyntax
                line.substringBefore(' ').let { it == "0.0.0.0" || it == "127.0.0.1" || it == "::1" } ->
                    FeedRule.Hosts
                else -> FeedRule.Domains
            }

            val candidate = when (effective) {
                FeedRule.AdblockSyntax -> {
                    if (!line.startsWith("||")) return null
                    // Everything an AdGuard DNS rule can carry after the
                    // domain: the `^` separator, a `$` modifier list, a path,
                    // a wildcard. Only `||example.com^` (or the bare form)
                    // means "this whole domain, always"; the rest are
                    // conditional on context a hostname filter cannot see, so
                    // they are skipped rather than over-applied.
                    val end = line.indexOfFirst(2) { it == '^' || it == '$' || it == '/' || it == '*' }
                    if (end <= 2) return null
                    if (end < line.length) {
                        if (line[end] != '^') return null
                        if (line.substring(end + 1).isNotBlank()) return null
                    }
                    line.substring(2, end)
                }
                FeedRule.Hosts -> {
                    // Field 2, per adblock's rule — `0.0.0.0 ads.example.com`.
                    val parts = line.split(' ', '\t').filter(String::isNotEmpty)
                    if (parts.size < 2) return null
                    parts[1]
                }
                // First field: the rest of the line is a trailing comment on
                // several of these feeds.
                FeedRule.Domains, FeedRule.Auto -> line.split(' ', '\t')[0]
            }

            return normalize(candidate)
        }

        /** Lowercased and stripped of the decorations lists put around a domain. */
        private fun normalize(candidate: String): String? {
            var host = candidate.trim().trimEnd('.').lowercase()
            // oisd's `domainswild` variants and several pi-hole lists prefix a
            // wildcard; the suffix walk in AdBlocker already covers subdomains.
            if (host.startsWith("*.")) host = host.substring(2)
            if (host.length !in 4..253) return null
            var dots = 0
            for (c in host) {
                when {
                    c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' -> Unit
                    c == '.' -> dots++
                    else -> return null
                }
            }
            if (dots == 0) return null
            if (host.startsWith('.') || host.startsWith('-') || host.contains("..")) return null
            // Every hosts file in the world redirects these to the null route.
            if (host == "local.localhost" || host.endsWith(".localhost")) return null
            return host
        }

        private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
            for (i in from until length) if (predicate(this[i])) return i
            return length
        }

        /**
         * FNV-1a, 64-bit, over the host's ASCII bytes — cheap, allocation-free
         * and well distributed. The blocker hashes suffixes of a request's
         * host with the same function, so the two must stay identical.
         */
        fun hash(host: String): Long = hash(host, 0)

        private const val HEADER_BYTES = 12

        /** FNV-1a again, over a long, for stirring [indexKey] together. */
        private fun mix(accumulator: Long, value: Long): Long {
            var h = accumulator
            for (shift in 0 until 64 step 8) {
                h = h xor ((value ushr shift) and 0xFF)
                h *= 0x100000001b3L
            }
            return h
        }

        /**
         * The hash of a line that is ALREADY a clean domain, without
         * allocating anything, or null for one that needs the full parser.
         *
         * Deliberately strict -- lowercase letters, digits, `-`, `_` and at
         * least one dot, nothing else -- so it accepts only what [normalize]
         * would have returned unchanged. Anything it turns down is not
         * rejected, just handed to the slow path.
         */
        private fun cleanDomainHash(line: String): Long? {
            if (line.length !in 4..253) return null
            var dots = 0
            for (i in line.indices) {
                when (val c = line[i]) {
                    in 'a'..'z', in '0'..'9', '-', '_' -> Unit
                    '.' -> {
                        // `..` and a leading dot are what normalize refuses;
                        // a trailing one it would have trimmed.
                        if (i == 0 || i == line.length - 1 || line[i - 1] == '.') return null
                        dots++
                    }
                    else -> return null
                }
            }
            if (dots == 0 || line[0] == '-') return null
            if (line.endsWith(".localhost")) return null
            return hash(line, 0)
        }

        fun hash(host: String, from: Int): Long {
            var h = -0x340d631b7bdddcdbL // FNV offset basis
            for (i in from until host.length) {
                h = h xor (host[i].code.toLong() and 0xFF)
                h *= 0x100000001b3L // FNV prime
            }
            return h
        }
    }
}

/** Grow-on-demand `LongArray`, so the index isn't built through boxed `Long`s. */
private class LongArrayBuilder(initial: Int) {
    private var data = LongArray(initial.coerceAtLeast(16))
    private var size = 0

    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    /** Sorted, and deduplicated in the same pass — the search wants both. */
    fun sortedArray(): LongArray {
        val sorted = data.copyOf(size)
        sorted.sort()
        var unique = 0
        for (i in 0 until size) {
            if (i == 0 || sorted[i] != sorted[i - 1]) sorted[unique++] = sorted[i]
        }
        return sorted.copyOf(unique)
    }
}

/**
 * Open-addressed set of longs, used only to deduplicate during an update.
 * A `HashSet<Long>` would box every one of a few hundred thousand entries;
 * this is one flat array.
 */
private class LongHashSet(expected: Int) {
    private var keys = LongArray(tableSizeFor(expected))
    private var mask = keys.size - 1
    private var size = 0

    fun add(key: Long): Boolean {
        // 0 is the empty marker, so the (vanishingly rare) real zero hash
        // moves aside rather than reading as an empty slot forever.
        val k = if (key == 0L) ZERO_SUBSTITUTE else key
        var i = index(k)
        while (true) {
            val existing = keys[i]
            if (existing == 0L) {
                keys[i] = k
                size++
                if (size * 2 > keys.size) grow()
                return true
            }
            if (existing == k) return false
            i = (i + 1) and mask
        }
    }

    private fun index(key: Long): Int {
        var h = key * -0x61c8864680b583ebL
        h = h xor (h ushr 32)
        return h.toInt() and mask
    }

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2)
        mask = keys.size - 1
        for (key in old) {
            if (key == 0L) continue
            var i = index(key)
            while (keys[i] != 0L) i = (i + 1) and mask
            keys[i] = key
        }
    }

    private companion object {
        const val ZERO_SUBSTITUTE = 1L

        fun tableSizeFor(expected: Int): Int {
            var capacity = 16
            while (capacity < expected * 2) capacity = capacity shl 1
            return capacity
        }
    }
}
