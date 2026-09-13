package com.yuku.browser.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Fetches omnibox autocomplete suggestions while the user is typing. */
object SearchSuggestions {

    private val client = OkHttpClient()

    /**
     * Google's suggest endpoint returns results for any query regardless of
     * which engine the user has picked for the search itself — there's no
     * per-engine equivalent worth wiring up separately.
     */
    suspend fun fetch(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://suggestqueries.google.com/complete/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("client", "firefox")
                    .addQueryParameter("q", query)
                    .build()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val suggestions = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
                    (0 until suggestions.length()).map { suggestions.getString(it) }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
