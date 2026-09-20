package com.yuku.browser.core

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists everything BrowserViewModel would otherwise lose to process death:
 * open tabs (private tabs excluded on principle), history, bookmarks, and the
 * in-memory settings that used to reset on every relaunch. One JSON blob in a
 * dedicated SharedPreferences file — this app's state is small, so there's no
 * need for per-field keys or a database.
 *
 * The bulky, per-tab parts of a session live in their own files rather than
 * in this blob: tab previews in [ThumbnailStore], favicons in [FaviconStore],
 * and each tab's WebView navigation state — back/forward list and scroll
 * offset — in [PageStateStore]. A tab therefore comes back as the page it
 * was, at the offset it was left at, not as a fresh load of its last URL.
 */
class BrowserStore(app: Application) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SavedTab(
        val id: Long,
        val url: String,
        val title: String,
        val host: String,
    )

    data class Settings(
        val themeMode: ThemeMode = ThemeMode.System,
        val pageDarkMode: PageDarkMode = PageDarkMode.System,
        val desktopMode: Boolean = false,
        val adBlockEnabled: Boolean = true,
        val searchEngine: SearchEngine = SearchEngine.DuckDuckGo,
        // The engines the user has switched OFF: they stay out of the + sheet's
        // picker and out of Settings' own default list, but are remembered by
        // id rather than deleted, so turning one back on restores it whole.
        // Stored as the exclusion rather than the inclusion so a build that
        // adds a built-in engine offers it to everyone instead of hiding it
        // from every save written before it existed.
        val disabledSearchEngines: Set<String> = emptySet(),
        // The user's own order, by id, across BOTH sections — an engine the
        // list doesn't name keeps its built-in position (see
        // BrowserViewModel.orderEngines), so a build that adds one doesn't
        // need every existing save rewritten.
        val searchEngineOrder: List<String> = emptyList(),
        val customSearchEngines: List<SearchEngine> = emptyList(),
        val searchSuggestionsEnabled: Boolean = true,
        // The system's own colour, not the app's neutral: a browser opened
        // for the first time on a phone the user has already themed should
        // look like it belongs to that phone. Falls back to a fixed seed
        // below API 31, where there is no wallpaper palette to read.
        val accentTheme: AccentTheme = AccentTheme.Dynamic,
        val specialTheme: SpecialTheme = SpecialTheme.Default,
        val autoFocusNewTabKeyboard: Boolean = true,
        val openNewTabSheetOnLaunch: Boolean = false,
        val newTabHistorySort: HistorySort = HistorySort.MostRecent,
        val tabManagerMode: TabManagerMode = TabManagerMode.Vertical,
        val linkStripperEnabled: Boolean = true,
        // Where a link tapped in ANOTHER app lands, and whether a link tapped
        // in here can leave for an app. See ExternalLinks.
        val openExternalLinksInOverlay: Boolean = true,
        val openLinksInApps: Boolean = true,
        val pullToRefreshEnabled: Boolean = true,
        // What a long press on a link raises: the preview card, or the
        // floating Open / New tab / Copy link menu. See BrowserViewModel's
        // openContextMenu.
        val linkPreviewEnabled: Boolean = true,
        // Experimental glass edges on the page. See ui/PageLens.kt.
        val pageLens: Boolean = false,
        // A slight, gradual blur where page content meets the status bar.
        val statusBarBlur: Boolean = true,
        // Frosted-glass sheets (Default and Nothing looks only).
        val translucentSheets: Boolean = false,
        // How solid those sheets are, 0 (nearly clear) to 1 (nearly solid).
        val translucency: Float = DEFAULT_TRANSLUCENCY,
        // Where "Open in new tab" puts the tab it opens. See NewTabPlacement.
        val newTabPlacement: NewTabPlacement = NewTabPlacement.Foreground,
        val doubleTapTabsSwitchesTab: Boolean = false,
        val swipeToSwitchTabs: Boolean = true,
        val flickToCloseTab: Boolean = true,
        val keepPrivateTabs: Boolean = false,
        // Only whether the features are ON lives here. The logins themselves
        // are in PasswordStore's encrypted vault, never in this plain blob.
        val savePasswords: Boolean = true,
        // Whether the device's own password manager owns logins here, or
        // this browser does. See BrowserViewModel.externalPasswordManager.
        val externalPasswordManager: Boolean = true,
        // Whether the vault's addresses and cards are offered into a form.
        // Two switches rather than one because they are two different amounts
        // of trust: a street the user has already given to a courier is not
        // a card number. Both only mean anything while this browser is the
        // one managing autofill — see externalPasswordManager.
        val fillAddresses: Boolean = true,
        val fillPaymentMethods: Boolean = true,
        /** Page text scale, as a percentage. See BrowserViewModel.setZoomStep. */
        val pageZoom: Int = 100,
        // How the reader draws an article — paper, face, size, leading. A
        // preference and not a per-tab state, unlike whether the reader is
        // open at all, which is in memory only. See ReaderSettings.
        val readerSettings: ReaderSettings = ReaderSettings(),
        // What a site that has never been answered about gets when it asks
        // for the camera, the microphone or the user's location. Absent keys
        // mean Ask, which is what a browser has to default to: Allow would be
        // handing the device out on the strength of a page having loaded, and
        // Block would make the switch look broken rather than protective.
        val permissionRules: Map<SitePermission, PermissionRule> = emptyMap(),
    )

    data class SavedState(
        val tabs: List<SavedTab>,
        val currentTabId: Long,
        val history: List<HistoryEntry>,
        val visits: List<VisitTally>,
        val bookmarks: List<BookmarkEntry>,
        // The sites the user has already answered about. Beside bookmarks
        // rather than inside Settings because it is per-site data the user
        // accumulated, not a preference they chose once.
        val sitePermissions: List<SitePermissionGrant>,
        // The sites the user has given a treatment of their own — the ad
        // blocker turned off, a zoom, a dark override. Beside the permissions
        // above and for the same reason: accumulated per-site facts, not
        // preferences chosen once.
        val siteSettings: List<SiteSettingsEntry>,
        val settings: Settings,
    )

    fun load(): SavedState? {
        val raw = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching { parse(JSONObject(raw)) }.getOrNull()
    }

    fun save(state: SavedState) {
        prefs.edit().putString(KEY_STATE, serialize(state).toString()).apply()
    }

    /**
     * Rewrites the bookmarks in the saved blob and leaves everything else in
     * it exactly as it was found.
     *
     * For the one writer that has no session of its own to save: the overlay
     * (see [BrowserViewModel]'s `ephemeral` mode) holds no tabs and no
     * history, so calling [save] from there would replace the user's real
     * session with an empty one. This reads what is on disk, changes the one
     * list, and writes it straight back.
     *
     * With nothing saved yet there is nothing to preserve, so the bookmark
     * seeds a state of its own rather than being dropped.
     */
    fun updateBookmarks(transform: (List<BookmarkEntry>) -> List<BookmarkEntry>) {
        val current = load() ?: SavedState(
            tabs = emptyList(),
            currentTabId = 0L,
            history = emptyList(),
            visits = emptyList(),
            bookmarks = emptyList(),
            sitePermissions = emptyList(),
            siteSettings = emptyList(),
            settings = Settings(),
        )
        save(current.copy(bookmarks = transform(current.bookmarks)))
    }

    private fun serialize(state: SavedState): JSONObject = JSONObject().apply {
        put("currentTabId", state.currentTabId)
        put("tabs", JSONArray().apply {
            state.tabs.forEach { tab ->
                put(JSONObject().apply {
                    put("id", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("host", tab.host)
                })
            }
        })
        put("history", JSONArray().apply {
            state.history.forEach { entry ->
                put(JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("host", entry.host)
                    put("visitCount", entry.visitCount)
                })
            }
        })
        put("visits", JSONArray().apply {
            state.visits.forEach { tally ->
                put(JSONObject().apply {
                    put("url", tally.url)
                    put("title", tally.title)
                    put("host", tally.host)
                    put("visitsCounted", tally.visits)
                    put("score", tally.score)
                    put("scoredAt", tally.scoredAt)
                    put("days", tally.days)
                    put("lastDay", tally.lastDay)
                    put("lastVisitAt", tally.lastVisitAt)
                })
            }
        })
        put("bookmarks", JSONArray().apply {
            state.bookmarks.forEach { entry ->
                put(JSONObject().apply {
                    put("url", entry.url)
                    put("title", entry.title)
                    put("host", entry.host)
                })
            }
        })
        put("sitePermissions", JSONArray().apply {
            state.sitePermissions.forEach { grant ->
                put(JSONObject().apply {
                    put("origin", grant.origin)
                    put("permission", grant.permission.id)
                    put("allowed", grant.allowed)
                })
            }
        })
        put("siteSettings", JSONArray().apply {
            state.siteSettings.forEach { entry ->
                put(JSONObject().apply {
                    put("site", entry.site)
                    put("blockAds", entry.settings.blockAds)
                    put("blockTrackers", entry.settings.blockTrackers)
                    put("hideCookieBanners", entry.settings.hideCookieBanners)
                    // Written only when set: absent is "follow the app", and
                    // JSON has no way to spell that with a boolean.
                    entry.settings.dark?.let { put("dark", it) }
                    entry.settings.zoom?.let { put("zoom", it) }
                })
            }
        })
        put("settings", JSONObject().apply {
            put("themeMode", state.settings.themeMode.name)
            put("pageDarkMode", state.settings.pageDarkMode.name)
            put("desktopMode", state.settings.desktopMode)
            put("adBlockEnabled", state.settings.adBlockEnabled)
            put("searchEngine", state.settings.searchEngine.id)
            put("disabledSearchEngines", JSONArray().apply {
                state.settings.disabledSearchEngines.forEach { put(it) }
            })
            put("searchEngineOrder", JSONArray().apply {
                state.settings.searchEngineOrder.forEach { put(it) }
            })
            put("customSearchEngines", JSONArray().apply {
                state.settings.customSearchEngines.forEach { engine ->
                    put(JSONObject().apply {
                        put("id", engine.id)
                        put("label", engine.label)
                        put("host", engine.host)
                        put("template", engine.template)
                    })
                }
            })
            put("searchSuggestionsEnabled", state.settings.searchSuggestionsEnabled)
            put("accentTheme", state.settings.accentTheme.name)
            put("specialTheme", state.settings.specialTheme.name)
            put("autoFocusNewTabKeyboard", state.settings.autoFocusNewTabKeyboard)
            put("openNewTabSheetOnLaunch", state.settings.openNewTabSheetOnLaunch)
            put("newTabHistorySort", state.settings.newTabHistorySort.name)
            put("tabManagerMode", state.settings.tabManagerMode.name)
            put("linkStripperEnabled", state.settings.linkStripperEnabled)
            put("openExternalLinksInOverlay", state.settings.openExternalLinksInOverlay)
            put("openLinksInApps", state.settings.openLinksInApps)
            put("pullToRefreshEnabled", state.settings.pullToRefreshEnabled)
            put("linkPreviewEnabled", state.settings.linkPreviewEnabled)
            put("pageLens", state.settings.pageLens)
            put("statusBarBlur", state.settings.statusBarBlur)
            put("translucentSheets", state.settings.translucentSheets)
            put("translucency", state.settings.translucency.toDouble())
            put("newTabPlacement", state.settings.newTabPlacement.name)
            put("doubleTapTabsSwitchesTab", state.settings.doubleTapTabsSwitchesTab)
            put("swipeToSwitchTabs", state.settings.swipeToSwitchTabs)
            put("flickToCloseTab", state.settings.flickToCloseTab)
            put("keepPrivateTabs", state.settings.keepPrivateTabs)
            put("savePasswords", state.settings.savePasswords)
            put("externalPasswordManager", state.settings.externalPasswordManager)
            put("fillAddresses", state.settings.fillAddresses)
            put("fillPaymentMethods", state.settings.fillPaymentMethods)
            put("pageZoom", state.settings.pageZoom)
            put("readerTextScale", state.settings.readerSettings.textScale)
            put("readerTheme", state.settings.readerSettings.theme.name)
            put("readerFont", state.settings.readerSettings.font.name)
            put("readerSpacing", state.settings.readerSettings.spacing.name)
            put("permissionRules", JSONObject().apply {
                state.settings.permissionRules.forEach { (permission, rule) ->
                    put(permission.id, rule.name)
                }
            })
        })
    }

    private fun parse(json: JSONObject): SavedState {
        val tabs = json.optJSONArray("tabs")?.let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedTab(
                    id = o.getLong("id"),
                    url = o.getString("url"),
                    title = o.optString("title"),
                    host = o.optString("host"),
                )
            }
        }.orEmpty()

        val history = json.optJSONArray("history")?.let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    title = o.optString("title"),
                    url = o.getString("url"),
                    host = o.optString("host"),
                    visitCount = o.optInt("visitCount", 1),
                )
            }
        }.orEmpty()

        // A tally with no "score" was written before scoring existed — a bare
        // lifetime count — and is carried over by VisitTally.legacy. Absent
        // altogether (older still) there is nothing worth carrying: history's
        // own counts from then would have expired on arrival anyway.
        val visits = json.optJSONArray("visits")?.let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val url = o.getString("url")
                val title = o.optString("title")
                val host = o.optString("host")
                if (o.has("score")) {
                    VisitTally(
                        url = url,
                        title = title,
                        host = host,
                        visits = o.optInt("visitsCounted", 1),
                        score = o.optDouble("score", 1.0),
                        scoredAt = o.optLong("scoredAt", 0L),
                        days = o.optInt("days", 0),
                        lastDay = o.optInt("lastDay", 0),
                        lastVisitAt = o.optLong("lastVisitAt", o.optLong("scoredAt", 0L)),
                    )
                } else {
                    VisitTally.legacy(url, title, host, o.optInt("count", 1), o.optLong("lastVisitAt", 0L))
                }
            }
        }.orEmpty()

        val bookmarks = json.optJSONArray("bookmarks")?.let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                BookmarkEntry(
                    url = o.getString("url"),
                    title = o.optString("title"),
                    host = o.optString("host"),
                )
            }
        }.orEmpty()

        // A grant naming a permission this build no longer has is dropped
        // rather than kept as an unreadable row: the switch it belonged to is
        // gone, so there is nowhere to show it and nothing to undo it with.
        val sitePermissions = json.optJSONArray("sitePermissions")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val permission = SitePermission.byId(o.optString("permission")) ?: return@mapNotNull null
                val origin = o.optString("origin").takeIf(String::isNotEmpty) ?: return@mapNotNull null
                SitePermissionGrant(origin, permission, o.optBoolean("allowed", false))
            }
        }.orEmpty()

        val siteSettings = json.optJSONArray("siteSettings")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val site = o.optString("site").takeIf(String::isNotEmpty) ?: return@mapNotNull null
                SiteSettingsEntry(
                    site = site,
                    settings = SiteSettings(
                        blockAds = o.optBoolean("blockAds", true),
                        blockTrackers = o.optBoolean("blockTrackers", true),
                        hideCookieBanners = o.optBoolean("hideCookieBanners", true),
                        dark = if (o.has("dark")) o.optBoolean("dark") else null,
                        zoom = if (o.has("zoom")) o.optInt("zoom") else null,
                    ),
                )
            }.filterNot { it.settings.isDefault }
        }.orEmpty()

        val s = json.optJSONObject("settings")
        // Read before the settings themselves: the saved default engine is an
        // id, and one of these may be what it names.
        val customEngines = s?.optJSONArray("customSearchEngines")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val template = o.optString("template")
                if (id.isEmpty() || template.isEmpty()) return@mapNotNull null
                SearchEngine(
                    id = id,
                    label = o.optString("label").ifEmpty { o.optString("host") },
                    host = o.optString("host"),
                    template = template,
                )
            }
        }.orEmpty()
        val settings = Settings(
            themeMode = enumOrNull<ThemeMode>(s?.optString("themeMode")) ?: ThemeMode.System,
            pageDarkMode = enumOrNull<PageDarkMode>(s?.optString("pageDarkMode")) ?: PageDarkMode.System,
            desktopMode = s?.optBoolean("desktopMode", false) ?: false,
            adBlockEnabled = s?.optBoolean("adBlockEnabled", true) ?: true,
            // Resolved against the custom list as well as the built-ins, so a
            // session whose default is one of the user's own comes back to it.
            // An id that no longer resolves (a custom engine deleted by a
            // hand-edited file, or a built-in dropped by a later build) falls
            // back rather than leaving the omnibox with nowhere to search.
            searchEngine = SearchEngine.byId(s?.optString("searchEngine"), customEngines)
                ?: SearchEngine.DuckDuckGo,
            disabledSearchEngines = s?.optJSONArray("disabledSearchEngines")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }.toSet()
            }.orEmpty(),
            customSearchEngines = customEngines,
            searchEngineOrder = s?.optJSONArray("searchEngineOrder")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
            }.orEmpty(),
            searchSuggestionsEnabled = s?.optBoolean("searchSuggestionsEnabled", true) ?: true,
            accentTheme = enumOrNull<AccentTheme>(s?.optString("accentTheme")) ?: AccentTheme.Dynamic,
            specialTheme = enumOrNull<SpecialTheme>(s?.optString("specialTheme")) ?: SpecialTheme.Default,
            autoFocusNewTabKeyboard = s?.optBoolean("autoFocusNewTabKeyboard", true) ?: true,
            openNewTabSheetOnLaunch = s?.optBoolean("openNewTabSheetOnLaunch", false) ?: false,
            newTabHistorySort = enumOrNull<HistorySort>(s?.optString("newTabHistorySort")) ?: HistorySort.MostRecent,
            tabManagerMode = enumOrNull<TabManagerMode>(s?.optString("tabManagerMode")) ?: TabManagerMode.Vertical,
            linkStripperEnabled = s?.optBoolean("linkStripperEnabled", true) ?: true,
            openExternalLinksInOverlay = s?.optBoolean("openExternalLinksInOverlay", true) ?: true,
            openLinksInApps = s?.optBoolean("openLinksInApps", true) ?: true,
            pullToRefreshEnabled = s?.optBoolean("pullToRefreshEnabled", true) ?: true,
            linkPreviewEnabled = s?.optBoolean("linkPreviewEnabled", true) ?: true,
            pageLens = s?.optBoolean("pageLens", false) ?: false,
            statusBarBlur = s?.optBoolean("statusBarBlur", true) ?: true,
            translucentSheets = s?.optBoolean("translucentSheets", false) ?: false,
            translucency = (s?.optDouble("translucency", DEFAULT_TRANSLUCENCY.toDouble())
                ?: DEFAULT_TRANSLUCENCY.toDouble()).toFloat().coerceIn(0f, 1f),
            newTabPlacement = enumOrNull<NewTabPlacement>(s?.optString("newTabPlacement"))
                ?: NewTabPlacement.Foreground,
            doubleTapTabsSwitchesTab = s?.optBoolean("doubleTapTabsSwitchesTab", false) ?: false,
            swipeToSwitchTabs = s?.optBoolean("swipeToSwitchTabs", true) ?: true,
            flickToCloseTab = s?.optBoolean("flickToCloseTab", true) ?: true,
            keepPrivateTabs = s?.optBoolean("keepPrivateTabs", false) ?: false,
            savePasswords = s?.optBoolean("savePasswords", true) ?: true,
            // Read under its old name too: this was "googlePasswords" while
            // the only external manager it could mean was Google's.
            externalPasswordManager = s?.optBoolean(
                "externalPasswordManager",
                s.optBoolean("googlePasswords", true),
            ) ?: true,
            fillAddresses = s?.optBoolean("fillAddresses", true) ?: true,
            fillPaymentMethods = s?.optBoolean("fillPaymentMethods", true) ?: true,
            pageZoom = s?.optInt("pageZoom", 100) ?: 100,
            // Field by field rather than as a nested object: each one falls
            // back on its own, so a save written before a face or a rung
            // existed comes back with the rest of the reader's look intact.
            readerSettings = ReaderSettings(
                textScale = s?.optInt("readerTextScale", DEFAULT_READER_TEXT_SCALE) ?: DEFAULT_READER_TEXT_SCALE,
                theme = enumOrNull<ReaderTheme>(s?.optString("readerTheme")) ?: ReaderTheme.Auto,
                font = enumOrNull<ReaderFont>(s?.optString("readerFont")) ?: ReaderFont.Serif,
                spacing = enumOrNull<ReaderSpacing>(s?.optString("readerSpacing")) ?: ReaderSpacing.Normal,
            ),
            permissionRules = s?.optJSONObject("permissionRules")?.let { o ->
                SitePermission.entries.mapNotNull { permission ->
                    enumOrNull<PermissionRule>(o.optString(permission.id))?.let { permission to it }
                }.toMap()
            }.orEmpty(),
        )

        return SavedState(
            tabs = tabs,
            currentTabId = json.optLong("currentTabId", 0L),
            history = history,
            visits = visits,
            bookmarks = bookmarks,
            sitePermissions = sitePermissions,
            siteSettings = siteSettings,
            settings = settings,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

    companion object {
        private const val PREFS_NAME = "browser_state"
        private const val KEY_STATE = "state_json"
    }
}

/** The Translucency slider's starting point: about the look it replaced. */
const val DEFAULT_TRANSLUCENCY = 0.6f
