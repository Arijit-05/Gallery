package com.arijit.gallery.trash

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.domain.model.TrashedMediaItem
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.core.media.MutationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class TrashUiState(
    val items: ImmutableList<TrashedMediaItem> = persistentListOf(),
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = true,
    val pendingIntentSender: IntentSender? = null,
    val statusMessage: String? = null
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val mutationManager: MediaMutationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private var pendingConfirmAction: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            mediaRepository.observeMediaChanges().onStart { emit(Unit) }.collect {
                refreshTrash()
            }
        }
    }

    private suspend fun refreshTrash() {
        val trashed = albumRepository.getTrashedItems()
        _uiState.update { state ->
            val valid = trashed.asSequence().map { it.item.id }.toSet()
            val retained = state.selectedIds.filter { it in valid }.toImmutableSet()
            state.copy(
                items = trashed.toImmutableList(),
                selectedIds = retained,
                isSelectionMode = state.isSelectionMode && retained.isNotEmpty(),
                isLoading = false
            )
        }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(
                selectedIds = next.toImmutableSet(),
                isSelectionMode = next.isNotEmpty()
            )
        }
    }

    fun enterSelection(id: Long) {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = true,
                selectedIds = (state.selectedIds + id).toImmutableSet()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val all = state.items.map { it.item.id }.toImmutableSet()
            state.copy(
                selectedIds = all,
                isSelectionMode = all.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                selectedIds = persistentSetOf(),
                isSelectionMode = false
            )
        }
    }

    fun restoreSelected() {
        val targets = _uiState.value.items
            .filter { it.item.id in _uiState.value.selectedIds }
            .map { it.item }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            handleMutation(mutationManager.restoreFromTrash(targets))
        }
    }

    fun deleteSelectedPermanently() {
        val targets = _uiState.value.items
            .filter { it.item.id in _uiState.value.selectedIds }
            .map { it.item }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            handleMutation(mutationManager.deletePermanently(targets))
        }
    }

    fun emptyTrash() {
        val targets = _uiState.value.items.map { it.item }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            handleMutation(mutationManager.deletePermanently(targets))
        }
    }

    fun onIntentSenderResult(confirmed: Boolean) {
        val action = pendingConfirmAction
        pendingConfirmAction = null
        _uiState.update { it.copy(pendingIntentSender = null) }
        if (confirmed) {
            viewModelScope.launch {
                action?.invoke()
                clearSelection()
                refreshTrash()
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun handleMutation(outcome: MutationOutcome) {
        when (outcome) {
            is MutationOutcome.Success -> {
                clearSelection()
                viewModelScope.launch { refreshTrash() }
            }
            is MutationOutcome.RequiresConfirmation -> {
                pendingConfirmAction = outcome.onConfirmed
                _uiState.update { it.copy(pendingIntentSender = outcome.intentSender) }
            }
            is MutationOutcome.Error -> {
                _uiState.update { it.copy(statusMessage = outcome.message) }
            }
        }
    }
}

