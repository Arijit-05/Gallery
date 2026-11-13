package com.arijit.gallery.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryStoryCache @Inject constructor() {
    private val storiesMap = mutableMapOf<String, MemoryStory>()

    fun put(story: MemoryStory) {
        storiesMap[story.id] = story
    }

    fun get(id: String): MemoryStory? = storiesMap[id]

    fun putAll(stories: List<MemoryStory>) {
        stories.forEach { put(it) }
    }
}

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val storyCache: MemoryStoryCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _stories = MutableStateFlow<List<MemoryStory>>(emptyList())
    val stories: StateFlow<List<MemoryStory>> = _stories.asStateFlow()

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch(ioDispatcher) {
            val allMedia = mediaRepository.getAllMedia(SortOrder.DATE_TAKEN_DESC, emptySet())
            val extracted = MemoriesExtractor.extractMemories(allMedia)
            storyCache.putAll(extracted)
            _stories.value = extracted
        }
    }

    fun getStory(id: String): MemoryStory? = storyCache.get(id)
}
