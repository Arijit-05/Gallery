package com.arijit.gallery

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.arijit.gallery.core.media.MediaStoreThumbnailFetcher
import com.arijit.gallery.core.media.MediaThumbnailKeyer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class GalleryApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val decoderDispatcher = Dispatchers.IO.limitedParallelism(3)
        return ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailKeyer())
                add(MediaStoreThumbnailFetcher.Factory(contentResolver))
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .fetcherCoroutineContext(decoderDispatcher)
            .decoderCoroutineContext(decoderDispatcher)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
