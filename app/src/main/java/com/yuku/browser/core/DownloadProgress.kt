package com.yuku.browser.core

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the downloads in flight add up to, as the chrome draws it: how many
 * are still going and how far along they are together.
 *
 * [fraction] is null when any of them has not said how big it is (no
 * `Content-Length`, or DownloadManager has not connected yet) — a sum over a
 * total that is partly unknown is a number that runs backwards when the
 * missing size arrives, so the answer is "going" rather than a guess.
 * A [count] of zero with a [fraction] of 1 is the finish, held briefly so the
 * ring has a full circle to land on before it goes.
 */
data class DownloadActivity(val count: Int, val fraction: Float?)

/**
 * Reads progress back out of [DownloadManager], which owns the transfer in
 * another process and tells nobody how it is going — its broadcast is for the
 * END of a download only. So it is polled, and only while something is in
 * flight (see `BrowserViewModel.trackDownload`).
 *
 * Every query here sees only this app's own rows: the provider scopes a
 * caller without `ACCESS_ALL_DOWNLOADS` to the downloads it enqueued, which is
 * what lets [inFlight] resume tracking after the process was killed.
 */
object DownloadProgress {

    data class Row(
        val id: Long,
        val title: String,
        val status: Int,
        val bytes: Long,
        val total: Long,
    ) {
        /** Null until the server has said how big the file is. */
        val fraction: Float?
            get() = if (total > 0) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
        val paused: Boolean get() = status == DownloadManager.STATUS_PAUSED

        /** Paused counts: waiting for a network is still a download. */
        val ongoing: Boolean
            get() = status == DownloadManager.STATUS_RUNNING ||
                status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_PAUSED
        val succeeded: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    }

    /** The rows for [ids]. An id with no row was cancelled and removed. */
    suspend fun query(context: Context, ids: LongArray): List<Row> =
        if (ids.isEmpty()) emptyList()
        else read(context, DownloadManager.Query().setFilterById(*ids))

    /** Everything of ours DownloadManager is still working on. */
    suspend fun inFlight(context: Context): List<Row> = read(
        context,
        DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PENDING or
                DownloadManager.STATUS_PAUSED,
        ),
    )

    /** Stops the transfer and deletes its partial file. */
    suspend fun cancel(context: Context, id: Long) = withContext(Dispatchers.IO) {
        runCatching { context.getSystemService(DownloadManager::class.java)?.remove(id) }
        Unit
    }

    fun aggregate(rows: List<Row>): DownloadActivity {
        val live = rows.filter { it.ongoing }
        val known = live.isNotEmpty() && live.all { it.total > 0 }
        val fraction = if (known) {
            (live.sumOf { it.bytes }.toDouble() / live.sumOf { it.total }).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
        return DownloadActivity(count = live.size, fraction = fraction)
    }

    private suspend fun read(context: Context, query: DownloadManager.Query): List<Row> =
        withContext(Dispatchers.IO) {
            val manager = context.getSystemService(DownloadManager::class.java)
                ?: return@withContext emptyList()
            val rows = mutableListOf<Row>()
            runCatching {
                manager.query(query)?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                    val status = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val bytes = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val total = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    while (cursor.moveToNext()) {
                        rows += Row(
                            id = cursor.getLong(id),
                            title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "File",
                            status = cursor.getInt(status),
                            bytes = cursor.getLong(bytes),
                            total = cursor.getLong(total),
                        )
                    }
                }
            }
            rows
        }
}
