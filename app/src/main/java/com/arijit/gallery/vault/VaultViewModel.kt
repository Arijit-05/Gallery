package com.arijit.gallery.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.VaultDao
import com.arijit.gallery.core.database.entity.VaultItemEntity
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.vault.crypto.VaultCryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@Immutable
data class VaultUiState(
    val isUnlocked: Boolean = false,
    val items: ImmutableList<VaultItemEntity> = persistentListOf(),
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
    private val cryptoManager: VaultCryptoManager,
    private val mutationManager: MediaMutationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _isUnlocked = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoading = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    // Decrypted bitmap thumbnail cache in memory (never written to disk)
    val thumbnailCache = LruCache<String, Bitmap>(60)

    val uiState: StateFlow<VaultUiState> = combine(
        _isUnlocked,
        vaultDao.observeAll(),
        _selectedIds,
        _isLoading,
        _statusMessage
    ) { unlocked, items, selected, loading, msg ->
        VaultUiState(
            isUnlocked = unlocked,
            items = if (unlocked) items.toImmutableList() else persistentListOf(),
            selectedIds = selected.toImmutableSet(),
            isSelectionMode = selected.isNotEmpty(),
            isLoading = loading,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value
        val updated = if (id in current) current - id else current + id
        _selectedIds.value = updated
    }

    fun selectAll() {
        val allIds = uiState.value.items.map { it.id }.toSet()
        _selectedIds.value = allIds
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    suspend fun getDecryptedBytes(item: VaultItemEntity): ByteArray? {
        return cryptoManager.decryptToBytes(item.encryptedFileName, item.originalDisplayName)
    }

    suspend fun getDecryptedThumbnail(item: VaultItemEntity): Bitmap? = withContext(ioDispatcher) {
        thumbnailCache.get(item.id)?.let { return@withContext it }
        val bytes = getDecryptedBytes(item) ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bmp != null) {
            thumbnailCache.put(item.id, bmp)
        }
        bmp
    }

    fun addMediaItemsToVault(items: List<MediaItem>, onComplete: () -> Unit) {
        if (items.isEmpty()) return
        _isLoading.value = true
        viewModelScope.launch(ioDispatcher) {
            var addedCount = 0
            for (media in items) {
                runCatching {
                    context.contentResolver.openInputStream(media.contentUri.toUri())?.use { input ->
                        val encFileName = cryptoManager.encryptStreamToFile(input, media.displayName)
                        val vaultItem = VaultItemEntity(
                            id = UUID.randomUUID().toString(),
                            originalMediaId = media.id,
                            encryptedFileName = encFileName,
                            originalDisplayName = media.displayName,
                            mimeType = media.mimeType,
                            originalRelativePath = media.relativePath.ifBlank { Environment.DIRECTORY_PICTURES },
                            originalDateTakenMs = media.effectiveTimestampMs,
                            sizeBytes = media.sizeBytes,
                            width = media.width,
                            height = media.height,
                            durationMs = media.durationMs,
                            hiddenAtMs = System.currentTimeMillis()
                        )
                        vaultDao.insert(vaultItem)
                        addedCount++
                    }
                }
            }

            // Move original items to trash or delete from MediaStore
            mutationManager.requestTrash(items)

            _isLoading.value = false
            _statusMessage.value = "Moved $addedCount item(s) to Private Safe"
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun restoreSelectedToGallery() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        val currentItems = uiState.value.items.filter { it.id in selected }
        _isLoading.value = true

        viewModelScope.launch(ioDispatcher) {
            var restoredCount = 0
            for (item in currentItems) {
                val bytes = cryptoManager.decryptToBytes(item.encryptedFileName, item.originalDisplayName)
                if (bytes != null) {
                    val isVideo = item.mimeType.startsWith("video/")
                    val collection = if (isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val targetDir = if (isVideo) "${Environment.DIRECTORY_MOVIES}/Restored"
                    else "${Environment.DIRECTORY_PICTURES}/Restored"

                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalDisplayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetDir)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val destUri = context.contentResolver.insert(collection, values)
                    if (destUri != null) {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        val doneValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(destUri, doneValues, null, null)

                        // Clean up vault
                        vaultDao.deleteById(item.id)
                        cryptoManager.deleteEncryptedFile(item.encryptedFileName)
                        thumbnailCache.remove(item.id)
                        restoredCount++
                    }
                }
            }

            _isLoading.value = false
            _selectedIds.value = emptySet()
            _statusMessage.value = "Restored $restoredCount item(s) to Gallery"
        }
    }

    fun deleteSelectedPermanently() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        val currentItems = uiState.value.items.filter { it.id in selected }
        _isLoading.value = true

        viewModelScope.launch(ioDispatcher) {
            var deletedCount = 0
            for (item in currentItems) {
                vaultDao.deleteById(item.id)
                cryptoManager.deleteEncryptedFile(item.encryptedFileName)
                thumbnailCache.remove(item.id)
                deletedCount++
            }
            _isLoading.value = false
            _selectedIds.value = emptySet()
            _statusMessage.value = "Permanently deleted $deletedCount item(s)"
        }
    }
}
