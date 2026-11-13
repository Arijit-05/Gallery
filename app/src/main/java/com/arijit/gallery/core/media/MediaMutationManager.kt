package com.arijit.gallery.core.media

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.FavoriteDao
import com.arijit.gallery.core.database.entity.FavoriteOverrideEntity
import com.arijit.gallery.core.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MutationOutcome {
    data object Success : MutationOutcome
    data class RequiresConfirmation(
        val intentSender: IntentSender,
        val onConfirmed: suspend () -> Unit
    ) : MutationOutcome

    data class Error(val message: String) : MutationOutcome
}

@Singleton
open class MediaMutationManager(
    private val context: Context?,
    private val favoriteDao: FavoriteDao?,
    private val ioDispatcher: CoroutineDispatcher,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        favoriteDao: FavoriteDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(context, favoriteDao, ioDispatcher, forTesting = false)

    constructor() : this(null, null, Dispatchers.Unconfined, forTesting = true)

    private val contentResolver: ContentResolver
        get() = context?.contentResolver ?: error("Context not provided")

    /**
     * Requests MediaStore to move [items] to Trash (API 30+), or deletes with RecoverableSecurityException on API 29.
     */
    suspend fun requestTrash(items: List<MediaItem>): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        val uris = items.map { it.contentUri.toUri() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi: PendingIntent = MediaStore.createTrashRequest(contentResolver, uris, true)
            MutationOutcome.RequiresConfirmation(
                intentSender = pi.intentSender,
                onConfirmed = { /* MediaStore moves items to trash & fires ContentObserver */ }
            )
        } else {
            deleteLegacyItems(uris)
        }
    }

    /**
     * Marks or unmarks [items] as favorite instantly via persistent local database,
     * with background sync to MediaStore if permitted. Zero latency, zero permission dialog interruptions.
     */
    suspend fun requestFavorite(
        items: List<MediaItem>,
        favorite: Boolean
    ): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        val now = System.currentTimeMillis()
        val entities = items.map { FavoriteOverrideEntity(it.id, favorite, now) }
        favoriteDao?.upsertAll(entities)

        // Silently attempt background update to MediaStore (for owned or permitted items)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
                }
                items.forEach { item ->
                    contentResolver.update(item.contentUri.toUri(), values, null, null)
                }
            }
        }
        MutationOutcome.Success
    }

    /**
     * Renames a media item safely using scoped storage write request (API 30+) or direct update (API 29).
     */
    suspend fun requestRename(
        item: MediaItem,
        newDisplayName: String
    ): MutationOutcome = withContext(ioDispatcher) {
        val cleanName = newDisplayName.trim()
        if (cleanName.isEmpty()) return@withContext MutationOutcome.Error("Filename cannot be empty")

        val uri = item.contentUri.toUri()
        val applyRename: suspend () -> Unit = {
            withContext(ioDispatcher) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                }
                contentResolver.update(uri, values, null, null)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createWriteRequest(contentResolver, listOf(uri))
            MutationOutcome.RequiresConfirmation(
                intentSender = pi.intentSender,
                onConfirmed = applyRename
            )
        } else {
            try {
                applyRename()
                MutationOutcome.Success
            } catch (sec: RecoverableSecurityException) {
                MutationOutcome.RequiresConfirmation(
                    intentSender = sec.userAction.actionIntent.intentSender,
                    onConfirmed = applyRename
                )
            } catch (t: Throwable) {
                MutationOutcome.Error(t.message ?: "Rename failed")
            }
        }
    }

    suspend fun copyToAlbum(item: MediaItem, albumName: String): MutationOutcome =
        copyItemsToAlbum(listOf(item), albumName)

    /**
     * Copies one or more media items into Pictures/[albumName]/ or Movies/[albumName]/ via MediaStore.
     */
    suspend fun copyItemsToAlbum(
        items: List<MediaItem>,
        albumName: String,
        explicitRelativePath: String? = null
    ): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        val sanitizedAlbum = albumName.trim().replace("/", "_")
        if (sanitizedAlbum.isEmpty() && explicitRelativePath.isNullOrBlank()) {
            return@withContext MutationOutcome.Error("Invalid album name")
        }

        return@withContext runCatching {
            for (item in items) {
                val sourceUri = item.contentUri.toUri()
                val collection = if (item.isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val targetPath = resolveTargetRelativePath(
                    item = item,
                    albumName = sanitizedAlbum,
                    explicitRelativePath = explicitRelativePath
                )
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        item.mimeType.ifBlank { if (item.isVideo) "video/mp4" else "image/jpeg" }
                    )
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val destUri = contentResolver.insert(collection, values)
                    ?: error("Could not create destination entry for ${item.displayName}")

                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    } ?: error("Cannot open output stream")
                } ?: error("Cannot open source stream")

                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(destUri, doneValues, null, null)
            }
            MutationOutcome.Success
        }.getOrElse { t ->
            MutationOutcome.Error(t.message ?: "Copy failed")
        }
    }

    /**
     * Moves [items] in-place by updating MediaStore RELATIVE_PATH after scoped-storage write approval.
     */
    suspend fun moveItemsToAlbum(
        items: List<MediaItem>,
        albumName: String,
        explicitRelativePath: String? = null
    ): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        val sanitizedAlbum = albumName.trim().replace("/", "_")
        if (sanitizedAlbum.isEmpty() && explicitRelativePath.isNullOrBlank()) {
            return@withContext MutationOutcome.Error("Invalid album name")
        }

        val uris = items.map { it.contentUri.toUri() }
        val applyMove: suspend () -> Unit = {
            withContext(ioDispatcher) {
                for (item in items) {
                    val uri = item.contentUri.toUri()
                    val targetPath = resolveTargetRelativePath(
                        item = item,
                        albumName = sanitizedAlbum,
                        explicitRelativePath = explicitRelativePath
                    )
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
                    }
                    contentResolver.update(uri, values, null, null)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createWriteRequest(contentResolver, uris)
            MutationOutcome.RequiresConfirmation(
                intentSender = pi.intentSender,
                onConfirmed = applyMove
            )
        } else {
            try {
                applyMove()
                MutationOutcome.Success
            } catch (sec: RecoverableSecurityException) {
                MutationOutcome.RequiresConfirmation(
                    intentSender = sec.userAction.actionIntent.intentSender,
                    onConfirmed = applyMove
                )
            } catch (t: Throwable) {
                MutationOutcome.Error(t.message ?: "Move failed")
            }
        }
    }

    suspend fun restoreFromTrash(items: List<MediaItem>): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = items.map { it.contentUri.toUri() }
            val pi = MediaStore.createTrashRequest(contentResolver, uris, false)
            MutationOutcome.RequiresConfirmation(
                intentSender = pi.intentSender,
                onConfirmed = {}
            )
        } else {
            MutationOutcome.Error("System Trash requires Android 11+")
        }
    }

    suspend fun deletePermanently(items: List<MediaItem>): MutationOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext MutationOutcome.Success
        val uris = items.map { it.contentUri.toUri() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(contentResolver, uris)
            MutationOutcome.RequiresConfirmation(
                intentSender = pi.intentSender,
                onConfirmed = {}
            )
        } else {
            deleteLegacyItems(uris)
        }
    }

    private fun resolveTargetRelativePath(
        item: MediaItem,
        albumName: String,
        explicitRelativePath: String?
    ): String {
        val cleanedExplicit = explicitRelativePath?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            if (path.endsWith("/")) path else "$path/"
        }
        if (cleanedExplicit != null) {
            // MediaProvider requires video files to live in Movies/ or DCIM/, and images in Pictures/ or DCIM/.
            return when {
                cleanedExplicit.startsWith("DCIM/", ignoreCase = true) -> cleanedExplicit
                item.isVideo && cleanedExplicit.startsWith("Pictures/", ignoreCase = true) ->
                    "Movies/${cleanedExplicit.removePrefix("Pictures/")}"

                !item.isVideo && cleanedExplicit.startsWith("Movies/", ignoreCase = true) ->
                    "Pictures/${cleanedExplicit.removePrefix("Movies/")}"

                else -> cleanedExplicit
            }
        }
        val baseDir = if (item.isVideo) "Movies" else "Pictures"
        return "$baseDir/$albumName/"
    }

    private fun deleteLegacyItems(uris: List<Uri>): MutationOutcome {
        return try {
            for (uri in uris) {
                contentResolver.delete(uri, null, null)
            }
            MutationOutcome.Success
        } catch (sec: RecoverableSecurityException) {
            MutationOutcome.RequiresConfirmation(
                intentSender = sec.userAction.actionIntent.intentSender,
                onConfirmed = {
                    for (uri in uris) {
                        runCatching { contentResolver.delete(uri, null, null) }
                    }
                }
            )
        } catch (t: Throwable) {
            MutationOutcome.Error(t.message ?: "Delete failed")
        }
    }
}

