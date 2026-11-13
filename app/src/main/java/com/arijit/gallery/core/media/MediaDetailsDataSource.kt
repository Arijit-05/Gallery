package com.arijit.gallery.core.media

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.MediaLocationDao
import com.arijit.gallery.core.database.entity.MediaLocationEntity
import com.arijit.gallery.core.domain.model.MediaDetails
import com.arijit.gallery.core.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

@Singleton
open class MediaDetailsDataSource(
    private val context: Context?,
    private val mediaLocationDao: MediaLocationDao?,
    private val ioDispatcher: CoroutineDispatcher,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        mediaLocationDao: MediaLocationDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(context, mediaLocationDao, ioDispatcher, forTesting = false)

    constructor() : this(null, null, Dispatchers.Unconfined, forTesting = true)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())

    suspend fun loadDetails(item: MediaItem): MediaDetails = withContext(ioDispatcher) {
        val uri = item.contentUri.toUri()
        val formattedDate = Instant.ofEpochMilli(item.effectiveTimestampMs)
            .atZone(ZoneId.systemDefault())
            .format(fullDateFormatter)

        val resolution = if (item.width > 0 && item.height > 0) {
            "${item.width} × ${item.height}"
        } else {
            "Unknown"
        }

        val durationFormatted = if (item.isVideo && item.durationMs > 0L) {
            Formatters.formatDuration(item.durationMs)
        } else {
            null
        }

        var cameraMakeModel: String? = null
        var aperture: String? = null
        var iso: String? = null
        var shutterSpeed: String? = null
        var focalLength: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var locationLabel: String? = null

        if (!item.isVideo) {
            val readableUri = resolveOriginalUri(uri)
            runCatching {
                context?.contentResolver?.openInputStream(readableUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    cameraMakeModel = when {
                        model.isNotEmpty() && make.isNotEmpty() && !model.startsWith(make, ignoreCase = true) ->
                            "$make $model"
                        model.isNotEmpty() -> model
                        make.isNotEmpty() -> make
                        else -> null
                    }

                    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0)
                        .takeIf { it > 0 }
                        ?.let { aperture = String.format(Locale.US, "f/%.1f", it) }

                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { iso = "ISO $it" }

                    exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
                        .takeIf { it > 0 }
                        ?.let { exp ->
                            shutterSpeed = if (exp < 1.0) {
                                "1/${(1.0 / exp).roundToInt()}s"
                            } else {
                                String.format(Locale.US, "%.1fs", exp)
                            }
                        }

                    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
                        .takeIf { it > 0 }
                        ?.let { focalLength = "${it.roundToInt()} mm" }

                    exif.latLong?.let { coords ->
                        lat = coords[0]
                        lon = coords[1]
                    }
                }
            }
        }

        if (lat != null && lon != null) {
            val geocoded = reverseGeocodeOnDevice(lat!!, lon!!)
            if (geocoded != null && item.id > 0L) {
                mediaLocationDao?.upsert(
                    MediaLocationEntity(
                        mediaId = item.id,
                        locationText = geocoded,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
            locationLabel = geocoded
                ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }

        MediaDetails(
            displayName = item.displayName,
            relativePath = item.relativePath.ifBlank { item.bucketName },
            mimeType = item.mimeType,
            dateTakenFormatted = formattedDate,
            sizeFormatted = Formatters.formatFileSize(item.sizeBytes),
            resolutionFormatted = resolution,
            durationFormatted = durationFormatted,
            cameraMakeModel = cameraMakeModel,
            aperture = aperture,
            iso = iso,
            shutterSpeed = shutterSpeed,
            focalLength = focalLength,
            latitude = lat,
            longitude = lon,
            locationLabel = locationLabel
        )
    }

    private fun resolveOriginalUri(uri: Uri): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else {
            uri
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeOnDevice(lat: Double, lon: Double): String? {
        val ctx = context ?: return null
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, Locale.getDefault())
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

