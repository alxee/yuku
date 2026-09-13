package com.yuku.browser.core

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One saved file, as MediaStore knows it. */
data class DownloadEntry(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    /** Milliseconds, converted from MediaStore's seconds. */
    val addedAt: Long,
    val mime: String,
)

/**
 * What the browser has saved, read back out of MediaStore rather than kept in
 * a list of our own. The user can delete these files from any gallery or file
 * manager on the device, so any list we maintained would be a list of things
 * that may no longer exist — querying the same place the files live means the
 * Downloads screen is right by construction.
 *
 * The query is the whole `Downloads` collection with no filter, which under
 * scoped storage returns exactly this app's own contributions: on API 29+ an
 * app sees only the files it wrote there unless it holds media permissions we
 * neither have nor want. So "everything in Downloads we're allowed to see" and
 * "everything this browser downloaded" are the same list.
 */
class DownloadsStore(private val context: Context) {

    suspend fun list(): List<DownloadEntry> = withContext(Dispatchers.IO) {
        val legacy = Build.VERSION.SDK_INT < 29
        // No Downloads collection before API 29 — the generic Files table,
        // narrowed to the public Download folder by path, is the equivalent.
        val collection = if (legacy) {
            MediaStore.Files.getContentUri("external")
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val selection = if (legacy) "${MediaStore.MediaColumns.DATA} LIKE ?" else null
        val arguments = if (legacy) arrayOf("%/${Environment.DIRECTORY_DOWNLOADS}/%") else null
        val entries = mutableListOf<DownloadEntry>()
        try {
            context.contentResolver.query(
                collection,
                columns,
                selection,
                arguments,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    entries += DownloadEntry(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameColumn) ?: "File",
                        sizeBytes = cursor.getLong(sizeColumn),
                        addedAt = cursor.getLong(dateColumn) * 1000L,
                        mime = cursor.getString(mimeColumn) ?: "*/*",
                    )
                }
            }
        } catch (e: Exception) {
            // A missing storage permission on API 28 and below is the ordinary
            // case here (nothing has been saved yet, so it was never asked
            // for) — an empty list, not a crash.
            return@withContext emptyList()
        }
        entries
    }

    /** True when the row is gone afterwards. */
    suspend fun delete(entry: DownloadEntry): Boolean = withContext(Dispatchers.IO) {
        try {
            // The app inserted these rows, so it owns them and needs no
            // further permission to remove them, scoped storage included.
            context.contentResolver.delete(entry.uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
}
