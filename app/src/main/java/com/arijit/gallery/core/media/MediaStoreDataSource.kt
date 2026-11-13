package com.arijit.gallery.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.TrashedMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val filesUri = MediaStore.Files.getContentUri("external")

    private val baseProjection: Array<String>
        get() {
            val cols = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.ORIENTATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cols += MediaStore.MediaColumns.IS_FAVORITE
            }
            return cols.toTypedArray()
        }

    /**
     * Emits whenever MediaStore Images or Video tables change (camera capture, screenshots,
     * WhatsApp media arrival, edits, or external deletions). Debounced to coalesce burst writes.
     */
    @OptIn(FlowPreview::class)
    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                trySend(Unit)
            }
        }

        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                contentResolver.registerContentObserver(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    true,
                    observer
                )
            }
            runCatching {
                contentResolver.registerContentObserver(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    true,
                    observer
                )
            }
        }

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.debounce(100L)

    fun queryMediaPage(
        limit: Int,
        offset: Int,
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem> {
        val all = queryAllMedia(sortOrder, hiddenBuckets)
        val fromIndex = offset.coerceIn(0, all.size)
        val toIndex = (offset + limit).coerceIn(0, all.size)
        if (fromIndex >= toIndex) return emptyList()
        return all.subList(fromIndex, toIndex).toList()
    }

    fun queryMediaCount(hiddenBuckets: Set<String>): Int {
        val countImages = queryCollectionCount(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, hiddenBuckets)
        val countVideos = queryCollectionCount(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, hiddenBuckets)
        return countImages + countVideos
    }

    fun queryAllMedia(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem> {
        val images = queryMediaCollection(
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            hiddenBuckets = hiddenBuckets
        )
        val videos = queryMediaCollection(
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            hiddenBuckets = hiddenBuckets
        )
        val combined = ArrayList<MediaItem>(images.size + videos.size)
        combined.addAll(images)
        combined.addAll(videos)

        val comparator = when (sortOrder) {
            SortOrder.DATE_TAKEN_DESC ->
                compareByDescending<MediaItem> { it.effectiveTimestampMs }
                    .thenByDescending { it.id }
            SortOrder.DATE_TAKEN_ASC ->
                compareBy<MediaItem> { it.effectiveTimestampMs }
                    .thenBy { it.id }
            SortOrder.DATE_MODIFIED_DESC ->
                compareByDescending<MediaItem> { it.dateModifiedSec }
                    .thenByDescending { it.id }
        }
        combined.sortWith(comparator)
        return combined
    }

    private fun queryMediaCollection(
        uri: Uri,
        isVideo: Boolean,
        hiddenBuckets: Set<String>
    ): List<MediaItem> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            clauses += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        }
        clauses += "${MediaStore.MediaColumns.SIZE} > 0"

        if (hiddenBuckets.isNotEmpty()) {
            val placeholders = hiddenBuckets.joinToString(",") { "?" }
            clauses += "${MediaStore.MediaColumns.BUCKET_ID} NOT IN ($placeholders)"
            args.addAll(hiddenBuckets)
        }

        val selection = clauses.joinToString(" AND ")
        val selectionArgs = args.toTypedArray()

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            contentResolver.query(uri, baseProjection, bundle, null)
        } else {
            contentResolver.query(uri, baseProjection, selection, selectionArgs, null)
        } ?: return emptyList()

        return cursor.use { c ->
            val items = ArrayList<MediaItem>(c.count)
            val indices = ColumnIndices(c)
            while (c.moveToNext()) {
                items += mapRow(c, indices, isVideoOverride = isVideo)
            }
            items
        }
    }

    private fun queryCollectionCount(uri: Uri, hiddenBuckets: Set<String>): Int {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            clauses += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        }
        clauses += "${MediaStore.MediaColumns.SIZE} > 0"

        if (hiddenBuckets.isNotEmpty()) {
            val placeholders = hiddenBuckets.joinToString(",") { "?" }
            clauses += "${MediaStore.MediaColumns.BUCKET_ID} NOT IN ($placeholders)"
            args.addAll(hiddenBuckets)
        }

        val selection = clauses.joinToString(" AND ")
        val selectionArgs = args.toTypedArray()
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            contentResolver.query(uri, projection, bundle, null)
        } else {
            contentResolver.query(uri, projection, selection, selectionArgs, null)
        }
        return cursor?.use { it.count } ?: 0
    }

    fun queryMediaByIds(ids: Collection<Long>): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val targetIds = ids.toSet()
        val cached = queryAllMedia(SortOrder.DATE_TAKEN_DESC, emptySet())
        val found = cached.filter { it.id in targetIds }
        if (found.size == targetIds.size) return found

        val missing = targetIds - found.map { it.id }.toSet()
        val result = ArrayList<MediaItem>(found)
        result.addAll(queryCollectionByIds(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, missing))
        val stillMissing = targetIds - result.map { it.id }.toSet()
        if (stillMissing.isNotEmpty()) {
            result.addAll(queryCollectionByIds(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, stillMissing))
        }
        return result
    }

    private fun queryCollectionByIds(uri: Uri, isVideo: Boolean, ids: Collection<Long>): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val result = ArrayList<MediaItem>(ids.size)
        ids.chunked(450).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.MediaColumns._ID} IN ($placeholders)"
            val argsArray = chunk.map { it.toString() }.toTypedArray()
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bundle = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, argsArray)
                }
                contentResolver.query(uri, baseProjection, bundle, null)
            } else {
                contentResolver.query(uri, baseProjection, selection, argsArray, null)
            }
            cursor?.use { c ->
                val indices = ColumnIndices(c)
                while (c.moveToNext()) {
                    result += mapRow(c, indices, isVideoOverride = isVideo)
                }
            }
        }
        return result
    }

    fun queryTrashedItems(): List<TrashedMediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()

        val projection = baseProjection + arrayOf(MediaStore.MediaColumns.DATE_EXPIRES)
        val selection = "${MediaStore.MediaColumns.IS_TRASHED} = 1"
        val sort = "${MediaStore.MediaColumns.DATE_EXPIRES} ASC"

        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val items = ArrayList<TrashedMediaItem>()
        val nowSec = System.currentTimeMillis() / 1000L

        fun readCursor(cursor: Cursor?, isVideo: Boolean) {
            cursor?.use { c ->
                val indices = ColumnIndices(c)
                val expiresIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val item = mapRow(c, indices, isVideoOverride = isVideo)
                    val expiresSec = if (expiresIdx >= 0) c.getLong(expiresIdx) else (nowSec + 30L * 86400L)
                    val remainingSec = (expiresSec - nowSec).coerceAtLeast(0L)
                    val daysRemaining = ((remainingSec + 86399L) / 86400L).toInt().coerceIn(0, 30)
                    items += TrashedMediaItem(
                        item = item,
                        dateExpiresSec = expiresSec,
                        daysRemaining = daysRemaining
                    )
                }
            }
        }

        readCursor(contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, bundle, null), false)
        readCursor(contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, bundle, null), true)
        items.sortBy { it.dateExpiresSec }
        return items
    }

    fun queryTrashCount(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        val selection = "${MediaStore.MediaColumns.IS_TRASHED} = 1"
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val imgCount = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, bundle, null)?.use { it.count } ?: 0
        val vidCount = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, bundle, null)?.use { it.count } ?: 0
        return imgCount + vidCount
    }

    fun resolveMediaIdFromUri(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY) {
            runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { if (it > 0L) return it }
            return contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }
        return null
    }

    fun queryTimelineDateAnchors(
        granularity: TimelineGranularity,
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<TimelineDateAnchor> {
        val items = queryAllMedia(sortOrder, hiddenBuckets)
        val anchors = ArrayList<TimelineDateAnchor>()
        var lastGroupKey: String? = null
        var rowIndex = 0
        var headerCount = 0

        for (item in items) {
            val tsMs = item.effectiveTimestampMs
            val key = Formatters.groupKey(tsMs, granularity)
            if (key != lastGroupKey) {
                anchors += TimelineDateAnchor(
                    groupKey = key,
                    label = Formatters.formatFastScrollBubble(tsMs, granularity),
                    itemOffset = rowIndex + headerCount,
                    timestampMs = tsMs
                )
                lastGroupKey = key
                headerCount++
            }
            rowIndex++
        }
        return anchors
    }

    fun queryMediaIdsForGroup(
        groupKey: String,
        granularity: TimelineGranularity,
        hiddenBuckets: Set<String>
    ): List<Long> {
        val items = queryAllMedia(SortOrder.DATE_TAKEN_DESC, hiddenBuckets)
        return items
            .filter { Formatters.groupKey(it.effectiveTimestampMs, granularity) == groupKey }
            .map { it.id }
    }

    private fun mapRow(
        cursor: Cursor,
        indices: ColumnIndices,
        isVideoOverride: Boolean? = null
    ): MediaItem {
        val id = cursor.getLong(indices.id)
        val mimeType = cursor.getString(indices.mimeType).orEmpty()
        val displayName = cursor.getString(indices.displayName).orEmpty()
        val isVideo = isVideoOverride ?: mimeType.startsWith("video/", ignoreCase = true)

        val contentUri = if (isVideo) {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        } else {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }

        val resolvedType = when {
            isVideo -> MediaType.VIDEO
            mimeType.equals("image/gif", ignoreCase = true) -> MediaType.GIF
            isLikelyMotionPhoto(displayName, mimeType) -> MediaType.MOTION_PHOTO
            else -> MediaType.IMAGE
        }

        val isFavorite = if (indices.isFavorite >= 0) {
            cursor.getInt(indices.isFavorite) == 1
        } else {
            false
        }

        val dateTaken = if (indices.dateTaken >= 0) cursor.getLong(indices.dateTaken) else 0L
        val dateAdded = if (indices.dateAdded >= 0) cursor.getLong(indices.dateAdded) else 0L
        val dateModified = if (indices.dateModified >= 0) cursor.getLong(indices.dateModified) else 0L

        return MediaItem(
            id = id,
            contentUri = contentUri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            mediaType = resolvedType,
            dateTakenMs = dateTaken,
            dateAddedSec = dateAdded,
            dateModifiedSec = dateModified,
            sizeBytes = if (indices.size >= 0) cursor.getLong(indices.size) else 0L,
            width = if (indices.width >= 0) cursor.getInt(indices.width) else 0,
            height = if (indices.height >= 0) cursor.getInt(indices.height) else 0,
            durationMs = if (indices.duration >= 0) cursor.getLong(indices.duration) else 0L,
            bucketId = if (indices.bucketId >= 0) cursor.getString(indices.bucketId).orEmpty() else "",
            bucketName = if (indices.bucketName >= 0) cursor.getString(indices.bucketName).orEmpty() else "",
            relativePath = if (indices.relativePath >= 0) cursor.getString(indices.relativePath).orEmpty() else "",
            isFavorite = isFavorite,
            orientation = if (indices.orientation >= 0) cursor.getInt(indices.orientation) else 0
        )
    }

    private fun isLikelyMotionPhoto(displayName: String, mimeType: String): Boolean {
        if (!mimeType.startsWith("image/")) return false
        val upper = displayName.uppercase()
        return upper.endsWith(".MP.JPG") ||
            upper.startsWith("MVIMG_") ||
            upper.contains("_MP.")
    }

    private class ColumnIndices(c: Cursor) {
        val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val mimeType = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val displayName = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val dateTaken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val dateAdded = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val dateModified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val width = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val height = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val bucketId = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val relativePath = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val orientation = c.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
        val isFavorite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            c.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
        } else {
            -1
        }
    }
}
