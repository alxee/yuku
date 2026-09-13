package com.yuku.browser.ui

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.yuku.browser.core.DownloadProgress
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.HairLine
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Image
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuku.browser.core.DownloadEntry
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yuku.browser.ui.theme.specialCorner

/** How wide a thumbnail is decoded, so a 4000px photo isn't loaded to draw at 48dp. */
private const val THUMBNAIL_PX = 192

/**
 * Full-screen, reached from the menu's Downloads row — same pattern as
 * Bookmarks and History, except the list isn't the app's: it's whatever this
 * app has actually put in the device's Downloads folder right now (see
 * `DownloadsStore`).
 */
@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    // What DownloadManager is still fetching. Not in `downloads`: MediaStore
    // keeps a file pending until it is complete, so these are only ever
    // known from the transfer's side (see DownloadProgress).
    active: List<DownloadProgress.Row>,
    onCancel: (Long) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()

    fun open(entry: DownloadEntry) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.uri, entry.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(view, null))
        } catch (e: Exception) {
            // Nothing on the device opens this type, or it refused — the
            // row's other two buttons still work, so there's nothing to say
            // here, and certainly nothing worth crashing over.
        }
    }

    // Android's own downloads UI, which is where these files are. Building a
    // documents-provider folder URI by hand and viewing it looks like it
    // should work and doesn't: DocumentsUI's provider is only readable by
    // holders of MANAGE_DOCUMENTS, so that intent came back as a
    // SecurityException and took the app down with it. ACTION_VIEW_DOWNLOADS
    // is the public way to ask for the same screen — no URI, no permission.
    fun showInFiles(entry: DownloadEntry) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: Exception) {
            // No downloads UI on this device (or it refused) — opening the
            // file itself is the nearest true thing.
            open(entry)
        }
    }

    ListScreenScaffold(title = "Downloads", onBack = onBack) {
        // In flight first: the one row here that is still changing, and the
        // one a user opens this screen mid-download to find.
        active.forEach { row ->
            ActiveDownloadRow(
                row = row,
                onCancel = {
                    haptics.confirm()
                    onCancel(row.id)
                },
            )
        }
        if (downloads.isEmpty() && active.isEmpty()) {
            EmptyListMessage(
                "Nothing downloaded yet. Long-press an image on a page and choose " +
                    "Download image to save it to the device's Downloads folder.",
            )
        } else {
            downloads.forEach { entry ->
                DownloadRow(
                    entry = entry,
                    onClick = { open(entry) },
                    onShowInFiles = { showInFiles(entry) },
                    onDelete = {
                        haptics.confirm()
                        onDelete(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onClick: () -> Unit,
    onShowInFiles: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(entry)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = entry.name,
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle(LocalContext.current, entry),
                color = InkMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        IconButton(onClick = onShowInFiles) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = "Show in file manager",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete file",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A download still coming in: its name, a bar, how much has arrived, and a
 * way to stop it. Laid out on the finished row's grid — same leading square,
 * same text column — so the row does not jump when the file lands and it is
 * replaced by the ordinary one.
 */
@Composable
private fun ActiveDownloadRow(row: DownloadProgress.Row, onCancel: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(specialCorner(12.dp))
                .background(FieldBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = row.title,
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            DownloadProgressBar(fraction = row.fraction, paused = row.paused)
            Spacer(Modifier.height(4.dp))
            Text(
                text = activeSubtitle(context, row),
                color = InkMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Cancel download",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The row's progress bar. Every poll animated toward, like the toolbar's
 * ring, so four reports a second read as a bar moving rather than stepping.
 * With no size to measure against, a segment sweeps the track instead of a
 * fill claiming ground it can't know; a paused download stops sweeping and
 * greys its fill, because nothing is arriving.
 */
@Composable
private fun DownloadProgressBar(fraction: Float?, paused: Boolean) {
    val shown = remember { Animatable(fraction ?: 0f) }
    LaunchedEffect(fraction) {
        if (fraction == null) return@LaunchedEffect
        if (fraction < shown.value) shown.snapTo(fraction)
        else shown.animateTo(fraction, tween(PROGRESS_STEP_MS, easing = LinearEasing))
    }
    val sweep = if (fraction == null && !paused) {
        rememberInfiniteTransition(label = "downloadRowSweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PROGRESS_SWEEP_MS, easing = LinearEasing)),
            label = "downloadRowSweepPosition",
        )
    } else {
        null
    }
    val track = HairLine
    val fill = if (paused) InkMuted else AccentColor
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(specialCorner(2.dp))
            .background(track)
            .drawBehind {
                if (sweep != null) {
                    val segment = size.width * 0.3f
                    val x = (size.width + segment) * sweep.value - segment
                    drawRect(fill, topLeft = Offset(x, 0f), size = Size(segment, size.height))
                } else if (fraction != null) {
                    drawRect(fill, size = Size(size.width * shown.value, size.height))
                }
            },
    )
}

/** "1.2 MB of 4.5 MB · 27%", or what is holding it up. */
private fun activeSubtitle(context: android.content.Context, row: DownloadProgress.Row): String {
    val done = Formatter.formatShortFileSize(context, row.bytes)
    val amount = if (row.total > 0) {
        "$done of ${Formatter.formatShortFileSize(context, row.total)} · ${((row.fraction ?: 0f) * 100).roundToInt()}%"
    } else {
        done
    }
    return when {
        row.paused -> "Paused, waiting to resume · $amount"
        row.status == DownloadManager.STATUS_PENDING && row.bytes == 0L -> "Starting…"
        else -> amount
    }
}

private const val PROGRESS_STEP_MS = 300
private const val PROGRESS_SWEEP_MS = 1100

/**
 * The saved image itself, decoded small. Loaded per row rather than up front
 * so opening the screen costs one query, not one full-size decode per file.
 */
@Composable
private fun Thumbnail(entry: DownloadEntry) {
    val context = LocalContext.current
    var bitmap by remember(entry.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                // Two passes: bounds first, so inSampleSize can be chosen
                // without ever allocating the full-size bitmap.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(entry.uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = if (longest > 0) maxOf(1, longest / THUMBNAIL_PX) else 1
                }
                context.contentResolver.openInputStream(entry.uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(specialCorner(12.dp))
            .background(FieldBg),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "PNG · 248 kB · 2 hours ago" — what the file is, how big, and how old. */
private fun subtitle(context: android.content.Context, entry: DownloadEntry): String = buildString {
    append(entry.mime.substringAfterLast('/').uppercase())
    append(" · ")
    append(Formatter.formatShortFileSize(context, entry.sizeBytes))
    append(" · ")
    append(
        DateUtils.getRelativeTimeSpanString(
            entry.addedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ),
    )
}
