package com.arijit.gallery.core.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.runtime.Immutable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/**
 * Lightweight immutable model for grid thumbnails.
 * Routed to [MediaStoreThumbnailFetcher] so scrolling hits MediaProvider's cached thumbnails
 * instead of decoding full-resolution 50MP photos or 4K videos on every scroll frame.
 */
@Immutable
data class MediaThumbnailRequest(
    val id: Long,
    val uri: Uri,
    val dateModifiedSec: Long,
    val sizePx: Int
)

class MediaThumbnailKeyer : Keyer<MediaThumbnailRequest> {
    override fun key(data: MediaThumbnailRequest, options: Options): String =
        "ms_thumb_${data.id}_${data.dateModifiedSec}_${data.sizePx}"
}

class MediaStoreThumbnailFetcher(
    private val contentResolver: ContentResolver,
    private val data: MediaThumbnailRequest
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val signal = CancellationSignal()
        val job = currentCoroutineContext().job
        val handle = job.invokeOnCompletion { signal.cancel() }

        return try {
            val bitmap = try {
                contentResolver.loadThumbnail(
                    data.uri,
                    Size(data.sizePx, data.sizePx),
                    signal
                )
            } catch (_: Throwable) {
                decodeSampledFallback(data.uri, data.sizePx)
            } ?: return null

            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } finally {
            handle.dispose()
        }
    }

    private fun decodeSampledFallback(uri: Uri, targetPx: Int) = runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > targetPx * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }.getOrNull()

    class Factory(
        private val contentResolver: ContentResolver
    ) : Fetcher.Factory<MediaThumbnailRequest> {
        override fun create(
            data: MediaThumbnailRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = MediaStoreThumbnailFetcher(contentResolver, data)
    }
}

