package com.yuku.browser.core

import android.net.Uri
import java.net.URLEncoder

/**
 * The omnibox's fallback destination for input that isn't itself a URL.
 *
 * A data class rather than the enum it used to be, because the user can now
 * add their own: a custom engine has to be exactly as much of an engine as
 * DuckDuckGo is — storable as the default, listable in the + sheet's picker,
 * and passable to [UrlUtils.toUrlOrSearch] — and an enum has no room for one.
 * The built-ins keep their old enum names as [id]s, which is what lets a save
 * written before this change be read back unchanged.
 */
data class SearchEngine(
    /** Stable and persisted. Never shown; [label] is what the user reads. */
    val id: String,
    val label: String,
    val host: String,
    /** The query URL, with `%s` where the encoded query goes. */
    val template: String,
) {
    /** True for anything the user added themselves — the deletable ones. */
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    fun searchUrl(query: String): String =
        template.replace(PLACEHOLDER, URLEncoder.encode(query, "UTF-8"))

    /**
     * Whether [url] is one of this engine's result pages: the template's host
     * (a leading `www.` aside), its path, and the query parameter the search
     * goes in carrying something. A template with the placeholder in its PATH
     * matches on the path before it instead. Used to keep results pages out
     * of the visit tallies — each query is a page of its own and none of them
     * is a habit.
     */
    fun isResultsPage(url: String): Boolean = runCatching {
        val shape = Uri.parse(template.replace(PLACEHOLDER, QUERY_MARK))
        val page = Uri.parse(url)
        if (shape.host?.removePrefix("www.") != page.host?.lowercase()?.removePrefix("www.")) return false
        val shapePath = shape.path.orEmpty()
        val pagePath = page.path.orEmpty()
        val param = shape.queryParameterNames.firstOrNull { shape.getQueryParameter(it)?.contains(QUERY_MARK) == true }
        if (param != null) {
            shapePath.trimEnd('/') == pagePath.trimEnd('/') && !page.getQueryParameter(param).isNullOrBlank()
        } else {
            val prefix = shapePath.substringBefore(QUERY_MARK, missingDelimiterValue = "")
            prefix.isNotEmpty() && pagePath.startsWith(prefix) && pagePath.length > prefix.length
        }
    }.getOrDefault(false)

    companion object {
        private const val PLACEHOLDER = "%s"
        private const val QUERY_MARK = "yukuquerymark"
        private const val CUSTOM_PREFIX = "custom:"

        val DuckDuckGo = SearchEngine("DuckDuckGo", "DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/?q=%s")
        val Google = SearchEngine("Google", "Google", "google.com", "https://www.google.com/search?q=%s")
        val Bing = SearchEngine("Bing", "Bing", "bing.com", "https://www.bing.com/search?q=%s")
        val Brave = SearchEngine("Brave", "Brave", "search.brave.com", "https://search.brave.com/search?q=%s")
        val Startpage =
            SearchEngine("Startpage", "Startpage", "startpage.com", "https://www.startpage.com/sp/search?query=%s")

        /** In the order they are offered. The first is the default default. */
        val BUILT_IN = listOf(DuckDuckGo, Google, Bing, Brave, Startpage)

        fun byId(id: String?, custom: List<SearchEngine> = emptyList()): SearchEngine? =
            id?.let { wanted -> (BUILT_IN + custom).firstOrNull { it.id == wanted } }

        /**
         * Builds an engine from what the user typed into Settings, or null if
         * there is nothing usable there.
         *
         * The query goes where `%s` is — `{searchTerms}` (OpenSearch's own
         * spelling, which is what a site's own description document uses) is
         * accepted as the same thing. With NO placeholder at all the query is
         * appended, since the shape people paste is almost always a search URL
         * ending in `?q=`; that is a guess, but a wrong one is visible and
         * fixable on the first search, where refusing the URL outright is a
         * dead end for the commonest input.
         */
        fun custom(label: String, url: String): SearchEngine? {
            val name = label.trim()
            var template = url.trim().replace("{searchTerms}", PLACEHOLDER)
            if (name.isEmpty() || template.isEmpty()) return null
            if (!template.startsWith("http://") && !template.startsWith("https://")) {
                template = "https://$template"
            }
            if (!template.contains(PLACEHOLDER)) template += PLACEHOLDER
            // Must survive being parsed as a URL once the placeholder is out
            // of the way — a bare word typed into the field is not a search
            // engine, and finding that out at search time means a tab opened
            // on nothing.
            val host = Uri.parse(template.replace(PLACEHOLDER, "q")).host.orEmpty()
            if (host.isEmpty() || !host.contains('.')) return null
            return SearchEngine("$CUSTOM_PREFIX${System.currentTimeMillis()}", name, host, template)
        }
    }
}
