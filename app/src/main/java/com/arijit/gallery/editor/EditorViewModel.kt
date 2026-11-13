package com.arijit.gallery.editor

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.editor.engine.ImageEditRenderer
import com.arijit.gallery.editor.model.AdjustmentType
import com.arijit.gallery.editor.model.CropAspectRatio
import com.arijit.gallery.editor.model.CropState
import com.arijit.gallery.editor.model.DoodlePath
import com.arijit.gallery.editor.model.DoodleText
import com.arijit.gallery.editor.model.EditorSnapshot
import com.arijit.gallery.editor.model.EditorTab
import com.arijit.gallery.editor.model.EditorUiState
import com.arijit.gallery.editor.model.PhotoFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mutationManager: MediaMutationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<Long>("mediaId") ?: -1L

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    var previewBitmap: Bitmap? = null
        private set

    init {
        loadMedia()
    }

    private fun loadMedia() {
        if (mediaId <= 0L) return
        viewModelScope.launch(ioDispatcher) {
            val item = mediaRepository.getMediaByIds(listOf(mediaId)).firstOrNull()
            if (item != null) {
                previewBitmap = ImageEditRenderer.loadPreviewBitmap(context, item.contentUri.toUri(), 1440)
                _uiState.update {
                    it.copy(
                        item = item,
                        cropState = CropState(
                            aspectRatio = CropAspectRatio.Original,
                            cropRect = RectF(0f, 0f, 1f, 1f)
                        )
                    )
                }
            }
        }
    }

    private fun recordSnapshot() {
        val current = _uiState.value
        val snapshot = EditorSnapshot(
            cropState = current.cropState,
            adjustments = current.adjustments,
            selectedFilter = current.selectedFilter,
            filterIntensity = current.filterIntensity,
            markupPaths = current.markupPaths,
            markupTexts = current.markupTexts
        )
        undoStack.addLast(snapshot)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value
        val currentSnapshot = EditorSnapshot(
            cropState = current.cropState,
            adjustments = current.adjustments,
            selectedFilter = current.selectedFilter,
            filterIntensity = current.filterIntensity,
            markupPaths = current.markupPaths,
            markupTexts = current.markupTexts
        )
        redoStack.addLast(currentSnapshot)

        val previous = undoStack.removeLast()
        _uiState.update {
            it.copy(
                cropState = previous.cropState,
                adjustments = previous.adjustments,
                selectedFilter = previous.selectedFilter,
                filterIntensity = previous.filterIntensity,
                markupPaths = previous.markupPaths,
                markupTexts = previous.markupTexts,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value
        val currentSnapshot = EditorSnapshot(
            cropState = current.cropState,
            adjustments = current.adjustments,
            selectedFilter = current.selectedFilter,
            filterIntensity = current.filterIntensity,
            markupPaths = current.markupPaths,
            markupTexts = current.markupTexts
        )
        undoStack.addLast(currentSnapshot)

        val next = redoStack.removeLast()
        _uiState.update {
            it.copy(
                cropState = next.cropState,
                adjustments = next.adjustments,
                selectedFilter = next.selectedFilter,
                filterIntensity = next.filterIntensity,
                markupPaths = next.markupPaths,
                markupTexts = next.markupTexts,
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun resetAll() {
        recordSnapshot()
        _uiState.update {
            it.copy(
                cropState = CropState(),
                adjustments = AdjustmentType.entries.associateWith { adj -> adj.defaultValue },
                selectedFilter = PhotoFilter.Original,
                filterIntensity = 1f,
                markupPaths = emptyList(),
                markupTexts = emptyList()
            )
        }
    }

    fun setActiveTab(tab: EditorTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setActiveAdjustment(type: AdjustmentType) {
        _uiState.update { it.copy(activeAdjustment = type) }
    }

    fun updateAdjustment(type: AdjustmentType, value: Float) {
        recordSnapshot()
        _uiState.update {
            val updated = it.adjustments.toMutableMap()
            updated[type] = value
            it.copy(adjustments = updated)
        }
    }

    fun selectFilter(filter: PhotoFilter) {
        recordSnapshot()
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setFilterIntensity(intensity: Float) {
        _uiState.update { it.copy(filterIntensity = intensity) }
    }

    fun rotate90() {
        recordSnapshot()
        _uiState.update {
            val currentRot = it.cropState.rotationDegrees
            val nextRot = (currentRot + 90) % 360
            it.copy(cropState = it.cropState.copy(rotationDegrees = nextRot))
        }
    }

    fun flipHorizontal() {
        recordSnapshot()
        _uiState.update {
            it.copy(cropState = it.cropState.copy(flipHorizontal = !it.cropState.flipHorizontal))
        }
    }

    fun flipVertical() {
        recordSnapshot()
        _uiState.update {
            it.copy(cropState = it.cropState.copy(flipVertical = !it.cropState.flipVertical))
        }
    }

    fun setStraighten(angle: Float) {
        _uiState.update {
            it.copy(cropState = it.cropState.copy(straightenDegrees = angle))
        }
    }

    fun setCropAspectRatio(ratio: CropAspectRatio) {
        recordSnapshot()
        _uiState.update {
            it.copy(cropState = it.cropState.copy(aspectRatio = ratio))
        }
    }

    fun setCropRect(rect: RectF) {
        _uiState.update {
            it.copy(cropState = it.cropState.copy(cropRect = rect))
        }
    }

    fun addMarkupPath(path: DoodlePath) {
        recordSnapshot()
        _uiState.update {
            it.copy(markupPaths = it.markupPaths + path)
        }
    }

    fun addMarkupText(text: DoodleText) {
        recordSnapshot()
        _uiState.update {
            it.copy(markupTexts = it.markupTexts + text)
        }
    }

    fun setActiveColor(color: Long) {
        _uiState.update { it.copy(activeColor = color) }
    }

    fun setActiveBrushSize(size: Float) {
        _uiState.update { it.copy(activeBrushSize = size) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /**
     * Saves the edited image as a new copy in Pictures/Gallery/.
     */
    fun saveAsCopy(onSuccess: (Uri) -> Unit) {
        val item = _uiState.value.item ?: return
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch(ioDispatcher) {
            val successUri = runCatching {
                val tempFile = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(tempFile)
                val rendered = ImageEditRenderer.renderFullResolution(
                    context = context,
                    sourceUri = item.contentUri.toUri(),
                    cropState = state.cropState,
                    adjustments = state.adjustments,
                    filter = state.selectedFilter,
                    filterIntensity = state.filterIntensity,
                    markupPaths = state.markupPaths,
                    markupTexts = state.markupTexts,
                    outputStream = fos
                )
                fos.close()

                if (!rendered) error("Failed to render high-resolution image")

                // Preserve EXIF metadata from source to temp file
                context.contentResolver.openInputStream(item.contentUri.toUri())?.use { srcIn ->
                    ImageEditRenderer.copyExif(srcIn, tempFile.absolutePath)
                }

                // Insert into MediaStore Pictures/Gallery
                val baseName = item.displayName.substringBeforeLast(".")
                val ext = "jpg"
                val newDisplayName = "${baseName}_edited_${System.currentTimeMillis() % 10000}.$ext"

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Gallery")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val destUri = context.contentResolver.insert(collection, values)
                    ?: error("Could not create MediaStore entry")

                context.contentResolver.openOutputStream(destUri)?.use { destOut ->
                    tempFile.inputStream().use { fileIn ->
                        fileIn.copyTo(destOut)
                    }
                }

                val finishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, finishValues, null, null)

                tempFile.delete()
                destUri
            }.getOrNull()

            _uiState.update { it.copy(isSaving = false) }
            if (successUri != null) {
                _uiState.update { it.copy(statusMessage = "Saved copy to Gallery") }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(successUri)
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to save edited photo") }
            }
        }
    }

    /**
     * Overwrites the original file via Scoped Storage write permission.
     */
    fun overwrite(
        onRequiresConfirmation: (IntentSender) -> Unit,
        onSuccess: () -> Unit
    ) {
        val item = _uiState.value.item ?: return
        val state = _uiState.value
        val uri = item.contentUri.toUri()

        viewModelScope.launch(ioDispatcher) {
            val renderAndSave: suspend () -> Boolean = {
                runCatching {
                    val tempFile = File(context.cacheDir, "overwrite_${System.currentTimeMillis()}.jpg")
                    val fos = FileOutputStream(tempFile)
                    val rendered = ImageEditRenderer.renderFullResolution(
                        context = context,
                        sourceUri = uri,
                        cropState = state.cropState,
                        adjustments = state.adjustments,
                        filter = state.selectedFilter,
                        filterIntensity = state.filterIntensity,
                        markupPaths = state.markupPaths,
                        markupTexts = state.markupTexts,
                        outputStream = fos
                    )
                    fos.close()

                    if (rendered) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { destOut ->
                            tempFile.inputStream().use { fileIn ->
                                fileIn.copyTo(destOut)
                            }
                        }
                    }
                    tempFile.delete()
                    rendered
                }.getOrElse { false }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi: PendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                pendingOverwriteAction = renderAndSave
                pendingOverwriteSuccessCallback = onSuccess
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onRequiresConfirmation(pi.intentSender)
                }
            } else {
                _uiState.update { it.copy(isSaving = true) }
                val ok = renderAndSave()
                _uiState.update { it.copy(isSaving = false) }
                if (ok) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onSuccess() }
                } else {
                    _uiState.update { it.copy(statusMessage = "Failed to overwrite photo") }
                }
            }
        }
    }

    private var pendingOverwriteAction: (suspend () -> Boolean)? = null
    private var pendingOverwriteSuccessCallback: (() -> Unit)? = null

    fun onIntentSenderFinished(confirmed: Boolean) {
        if (!confirmed) {
            pendingOverwriteAction = null
            pendingOverwriteSuccessCallback = null
            _uiState.update { it.copy(statusMessage = "Overwrite cancelled") }
            return
        }

        val action = pendingOverwriteAction ?: return
        val callback = pendingOverwriteSuccessCallback
        pendingOverwriteAction = null
        pendingOverwriteSuccessCallback = null

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }
            val ok = action()
            _uiState.update { it.copy(isSaving = false) }
            if (ok) {
                _uiState.update { it.copy(statusMessage = "Photo updated") }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    callback?.invoke()
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to save changes") }
            }
        }
    }
}
