package com.arijit.gallery.search

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.MediaLocationDao
import com.arijit.gallery.core.database.dao.MediaSearchDao
import com.arijit.gallery.core.database.entity.MediaIndexStatusEntity
import com.arijit.gallery.core.database.entity.MediaLocationEntity
import com.arijit.gallery.core.database.entity.MediaSearchFtsEntity
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class PlaceSummary(
    val name: String,
    val count: Int
)

@Singleton
class SearchIndexManager(
    private val context: Context?,
    private val mediaSearchDao: MediaSearchDao,
    private val mediaLocationDao: MediaLocationDao,
    private val mediaRepository: MediaRepository,
    private val ioDispatcher: CoroutineDispatcher,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        mediaSearchDao: MediaSearchDao,
        mediaLocationDao: MediaLocationDao,
        mediaRepository: MediaRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(context, mediaSearchDao, mediaLocationDao, mediaRepository, ioDispatcher, forTesting = false)

    private val managerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val indexMutex = Mutex()
    private var isIndexWarm = false

    companion object {
        const val INDEX_VERSION = 1
    }

    /**
     * Incrementally index all media items into the Room FTS table.
     */
    suspend fun syncIndex(items: List<MediaItem>) = withContext(ioDispatcher) {
        indexMutex.withLock {
            val existingStatuses = mediaSearchDao.getAllStatuses().associateBy { it.mediaId }
            val cachedLocations = mediaLocationDao.getAll().associateBy { it.mediaId }

            val itemsToIndex = items.filter { item ->
                val status = existingStatuses[item.id]
                status == null || status.dateModifiedSec < item.dateModifiedSec || status.modelIndexVersion < INDEX_VERSION
            }

            if (itemsToIndex.isEmpty()) {
                isIndexWarm = true
                return@withLock
            }

            val ftsEntities = mutableListOf<MediaSearchFtsEntity>()
            val statusEntities = mutableListOf<MediaIndexStatusEntity>()
            val now = System.currentTimeMillis()

            for (item in itemsToIndex) {
                val loc = cachedLocations[item.id]?.locationText ?: ""
                val dateTokens = SearchQueryParser.generateDateTokens(item.effectiveTimestampMs)
                val mediaTypeTokens = SearchQueryParser.generateMediaTypeTokens(item)

                ftsEntities.add(
                    MediaSearchFtsEntity(
                        mediaId = item.id,
                        displayName = item.displayName,
                        bucketName = item.bucketName,
                        dateTokens = dateTokens,
                        placeName = loc,
                        mediaType = mediaTypeTokens,
                        labels = ""
                    )
                )

                statusEntities.add(
                    MediaIndexStatusEntity(
                        mediaId = item.id,
                        dateModifiedSec = item.dateModifiedSec,
                        textIndexedAtMs = now,
                        embeddingIndexedAtMs = 0L,
                        modelIndexVersion = INDEX_VERSION
                    )
                )
            }

            mediaSearchDao.insertAll(ftsEntities)
            mediaSearchDao.upsertAllStatus(statusEntities)
            isIndexWarm = true

            // Trigger lazy reverse-geocoding for items that don't have location yet
            managerScope.launch {
                lazyReverseGeocode(items)
            }
        }
    }

    /**
     * Executes smart search with full-text search, natural-language date filtering,
     * type filtering, and relevance ranking.
     */
    suspend fun search(
        query: String,
        filter: SearchMediaFilter?,
        sortOrder: SortOrder
    ): List<MediaItem> = withContext(ioDispatcher) {
        val allMedia = mediaRepository.getAllMedia(SortOrder.DATE_TAKEN_DESC, emptySet())
        if (!isIndexWarm || mediaSearchDao.count() == 0) {
            syncIndex(allMedia)
        }

        val parsed = SearchQueryParser.parse(query)
        val activeFilter = filter ?: parsed.inferredFilter

        // 1. Text token matching via FTS
        val textTokens = parsed.textTokens
        val matchingIds = if (textTokens.isNotEmpty()) {
            val ftsQuery = parsed.ftsQuery ?: textTokens.joinToString(" ") { "$it*" }
            var ids = runCatching { mediaSearchDao.searchMatchingIds(ftsQuery) }.getOrDefault(emptyList())

            // Fuzzy matching fallback if zero results and tokens exist
            if (ids.isEmpty()) {
                ids = runFuzzyFallback(textTokens, allMedia)
            }
            ids.toSet()
        } else {
            null // No text restriction
        }

        val allMediaMap = allMedia.associateBy { it.id }
        val candidateItems = if (matchingIds != null) {
            matchingIds.mapNotNull { allMediaMap[it] }
        } else {
            allMedia
        }

        // 2. Filter by Date range
        val dateFiltered = if (parsed.dateRange != null) {
            candidateItems.filter { it.effectiveTimestampMs in parsed.dateRange }
        } else {
            candidateItems
        }

        // 3. Filter by Media type / Favorites
        val typeFiltered = if (activeFilter != null) {
            dateFiltered.filter { SearchQueryParser.matchesFilter(it, activeFilter) }
        } else if (parsed.isFavoriteOnly) {
            dateFiltered.filter { it.isFavorite }
        } else {
            dateFiltered
        }

        // 4. Rank by Relevance or Sort Order
        if (sortOrder == SortOrder.DATE_TAKEN_ASC) {
            typeFiltered.sortedBy { it.effectiveTimestampMs }
        } else if (sortOrder == SortOrder.DATE_TAKEN_DESC && textTokens.isEmpty()) {
            typeFiltered.sortedByDescending { it.effectiveTimestampMs }
        } else {
            // Rank by composite relevance score
            val locationMap = mediaLocationDao.getAll().associate { it.mediaId to it.locationText }
            typeFiltered.sortedByDescending { item ->
                computeRelevanceScore(item, textTokens, locationMap[item.id])
            }
        }
    }

    private fun computeRelevanceScore(
        item: MediaItem,
        tokens: List<String>,
        placeName: String?
    ): Double {
        var score = 0.0
        val nameLower = item.displayName.lowercase()
        val bucketLower = item.bucketName.lowercase()
        val placeLower = placeName?.lowercase() ?: ""

        for (token in tokens) {
            val t = token.lowercase()
            when {
                nameLower.startsWith(t) -> score += 100.0
                nameLower.contains(t) -> score += 60.0
                bucketLower.startsWith(t) -> score += 80.0
                bucketLower.contains(t) -> score += 50.0
                placeLower.contains(t) -> score += 70.0
            }
        }

        // Slight recency bias as tie breaker
        score += (item.effectiveTimestampMs / 1_000_000_000.0) * 0.001
        return score
    }

    private suspend fun runFuzzyFallback(
        tokens: List<String>,
        allMedia: List<MediaItem>
    ): List<Long> {
        val candidates = mutableSetOf<String>()
        allMedia.take(1000).forEach {
            candidates.add(it.displayName.substringBeforeLast('.').lowercase())
            candidates.add(it.bucketName.lowercase())
        }
        val places = mediaSearchDao.getAllPlaceNames()
        places.forEach { candidates.add(it.lowercase()) }

        val correctedTokens = tokens.map { token ->
            val match = candidates.firstOrNull { candidate ->
                candidate.contains(token) || SearchQueryParser.levenshteinDistance(token, candidate) <= 2
            }
            match?.split(Regex("""\s+"""))?.firstOrNull() ?: token
        }

        val fuzzyFtsQuery = correctedTokens.joinToString(" ") { "$it*" }
        return runCatching { mediaSearchDao.searchMatchingIds(fuzzyFtsQuery) }.getOrDefault(emptyList())
    }

    /**
     * Returns top places with counts for the "Places" browse row.
     */
    suspend fun getTopPlaces(): List<PlaceSummary> = withContext(ioDispatcher) {
        val locations = mediaLocationDao.getAll()
        val counts = mutableMapOf<String, Int>()

        for (loc in locations) {
            val text = loc.locationText.trim()
            if (text.isNotBlank()) {
                val primaryPlace = text.split(",").firstOrNull()?.trim() ?: text
                if (primaryPlace.isNotBlank()) {
                    counts[primaryPlace] = (counts[primaryPlace] ?: 0) + 1
                }
            }
        }

        counts.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { PlaceSummary(it.key, it.value) }
    }

    /**
     * Returns years present in media for the "Years" browse row.
     */
    suspend fun getAvailableYears(): List<Int> = withContext(ioDispatcher) {
        val all = mediaRepository.getAllMedia(SortOrder.DATE_TAKEN_DESC, emptySet())
        val years = mutableSetOf<Int>()
        val zone = java.time.ZoneId.systemDefault()
        for (item in all) {
            val year = java.time.Instant.ofEpochMilli(item.effectiveTimestampMs).atZone(zone).year
            if (year in 2000..2040) {
                years.add(year)
            }
        }
        years.sortedDescending()
    }

    private suspend fun lazyReverseGeocode(items: List<MediaItem>) {
        val ctx = context ?: return
        if (!Geocoder.isPresent()) return

        val cachedIds = mediaLocationDao.getAll().map { it.mediaId }.toSet()
        val itemsNeedingGeocode = items.filter { it.id !in cachedIds }.take(50)

        for (item in itemsNeedingGeocode) {
            runCatching {
                val latLong = extractLatLong(ctx, item.contentUri.toUri())
                if (latLong != null) {
                    val place = reverseGeocode(ctx, latLong.first, latLong.second)
                    if (place != null) {
                        mediaLocationDao.upsert(
                            MediaLocationEntity(
                                mediaId = item.id,
                                locationText = place,
                                updatedAtMs = System.currentTimeMillis()
                            )
                        )
                        // Update FTS entry
                        val existing = mediaSearchDao.getById(item.id)
                        if (existing != null) {
                            mediaSearchDao.insertOrUpdate(existing.copy(placeName = place))
                        }
                    }
                }
            }
        }
    }

    private fun extractLatLong(context: Context, uri: Uri): Pair<Double, Double>? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.latLong?.let { it[0] to it[1] }
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        val addr = addresses.firstOrNull()
                        val text = listOfNotNull(
                            addr?.locality ?: addr?.subAdminArea,
                            addr?.adminArea,
                            addr?.countryName
                        ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
                        if (cont.isActive) cont.resume(text)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            runCatching {
                val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                listOfNotNull(
                    addr?.locality ?: addr?.subAdminArea,
                    addr?.adminArea,
                    addr?.countryName
                ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
}

