package com.yuku.browser.core

import java.util.TimeZone
import kotlin.math.min
import kotlin.math.pow

/**
 * A page's visit record — what "Most visited" is ranked from.
 *
 * Kept apart from [HistoryEntry] on purpose: history is a 20-row *recency*
 * window, so a count carried on those rows is lost the moment a page falls out
 * of it. Tallies outlive that window ([MAX_TALLIES] of them).
 *
 * WHAT counts as a visit is decided upstream (BrowserViewModel's visit clock:
 * one per navigation the user made, after [VISIT_DWELL_MS] on screen). What is
 * kept here answers "is this a habit", which a lifetime total cannot:
 * - [score] decays with a [SCORE_HALF_LIFE_MS] half-life, so a site used daily
 *   last year sinks and a new daily one rises within a week or two. Stored as
 *   its value at [scoredAt] and decayed on read ([scoreAt]).
 * - [days] is a bitmask of the local days the page was visited on, bit 0 being
 *   [lastDay]. The list only admits a page seen on [MIN_VISIT_DAYS] separate
 *   days of the last [VISIT_WINDOW_DAYS] — twelve loads in one afternoon are
 *   not twelve days of coming back.
 */
data class VisitTally(
    val url: String,
    val title: String,
    val host: String,
    /** Counted visits, lifetime. A tiebreak for omnibox matches, never the ranking. */
    val visits: Int,
    val score: Double,
    /** Wall-clock time of the last COUNTED visit. */
    val scoredAt: Long,
    val days: Int,
    val lastDay: Int,
    /**
     * Wall-clock time of the last visit, counted or not — a repeat inside
     * [REPEAT_VISIT_MS] adds nothing to the score but is still the most recent
     * time the user was on the page. What "Recently visited" is ordered by.
     */
    val lastVisitAt: Long,
) {
    fun scoreAt(now: Long): Double =
        if (now <= scoredAt) score
        else score * 0.5.pow((now - scoredAt).toDouble() / SCORE_HALF_LIFE_MS)

    fun daysVisitedWithin(today: Int): Int {
        val gap = (today - lastDay).coerceAtLeast(0)
        if (gap >= VISIT_WINDOW_DAYS) return 0
        return Integer.bitCount((days shl gap) and WINDOW_MASK)
    }

    /**
     * The same page visited again at [now]. Inside [REPEAT_VISIT_MS] of the
     * last counted visit only the title and address are refreshed: reading,
     * going somewhere and coming back is one visit, not three.
     */
    fun visitedAgain(url: String, title: String, host: String, now: Long): VisitTally {
        if (now - scoredAt in 0L until REPEAT_VISIT_MS) {
            return copy(url = url, title = title, host = host, lastVisitAt = now)
        }
        val today = localDay(now)
        val gap = today - lastDay
        return copy(
            url = url,
            title = title,
            host = host,
            visits = visits + 1,
            score = scoreAt(now) + 1.0,
            scoredAt = now,
            days = when {
                gap <= 0 -> days or 1
                gap >= Int.SIZE_BITS -> 1
                else -> (days shl gap) or 1
            },
            lastDay = maxOf(today, lastDay),
            lastVisitAt = now,
        )
    }

    companion object {
        fun firstVisit(url: String, title: String, host: String, now: Long) = VisitTally(
            url = url,
            title = title,
            host = host,
            visits = 1,
            score = 1.0,
            scoredAt = now,
            days = 1,
            lastDay = localDay(now),
            lastVisitAt = now,
        )

        /**
         * A tally saved before scoring existed: a lifetime count and a time.
         * Those counts took in every reload and restore, so the score is capped
         * rather than trusted, and the page is given [MIN_VISIT_DAYS] days
         * ending at its last visit so a list that had it does not empty out on
         * the update. The window ages those days out within a month, by which
         * time real visits have taken over.
         */
        fun legacy(url: String, title: String, host: String, count: Int, lastVisitAt: Long) = VisitTally(
            url = url,
            title = title,
            host = host,
            visits = count,
            score = min(count, LEGACY_SCORE_CAP).coerceAtLeast(0).toDouble(),
            scoredAt = lastVisitAt,
            days = (1 shl min(count, MIN_VISIT_DAYS).coerceAtLeast(0)) - 1,
            lastDay = localDay(lastVisitAt),
            lastVisitAt = lastVisitAt,
        )
    }
}

/** How many pages keep a tally. Beyond this the lowest current scores are dropped. */
const val MAX_TALLIES = 300

/** How many rows "Most visited" shows at most — matches the recent-history cap. */
const val MOST_VISITED_LIMIT = 20

/** Separate days a page must have been visited on to count as visited often. */
const val MIN_VISIT_DAYS = 3

/** The window [MIN_VISIT_DAYS] is counted over. Must stay below 32 (an Int of days). */
const val VISIT_WINDOW_DAYS = 30
private const val WINDOW_MASK = (1 shl VISIT_WINDOW_DAYS) - 1

const val SCORE_HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000

/** A second visit this soon after a counted one is the same visit. */
const val REPEAT_VISIT_MS = 30L * 60 * 1000

/** How long a page must be on screen before its visit counts. A mis-tap or a pass-through page never is. */
const val VISIT_DWELL_MS = 5_000L

/** A score this low is one visit two months ago: gone, as far as ranking is concerned. */
const val EXPIRED_SCORE = 0.05

private const val LEGACY_SCORE_CAP = 5
private const val DAY_MS = 24L * 60 * 60 * 1000

/** The local calendar day [millis] falls on, as days since the epoch. */
fun localDay(millis: Long): Int =
    Math.floorDiv(millis + TimeZone.getDefault().getOffset(millis), DAY_MS).toInt()

/**
 * The identity two visits to "the same page" share. The fragment is dropped
 * (in-page anchors are the same document), as is a trailing slash, and the
 * scheme + host are lowercased — otherwise `example.com/a`, `example.com/a/`
 * and `Example.com/a#top` would each tally separately and none of them would
 * rank. The query string is kept: for search results and most web apps it *is*
 * the page.
 */
fun visitKey(url: String): String {
    val withoutFragment = url.substringBefore('#')
    val schemeEnd = withoutFragment.indexOf("://")
    if (schemeEnd < 0) return withoutFragment.trimEnd('/').ifEmpty { withoutFragment }
    val authorityEnd = withoutFragment.indexOfFirst(schemeEnd + 3) { it == '/' || it == '?' }
    val head = withoutFragment.substring(0, authorityEnd).lowercase()
    val tail = withoutFragment.substring(authorityEnd).trimEnd('/')
    return head + tail
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return length
}
