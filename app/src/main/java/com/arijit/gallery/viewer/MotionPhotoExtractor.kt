package com.arijit.gallery.viewer

import android.content.Context
import android.net.Uri
import com.arijit.gallery.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MotionPhotoExtractor(
    private val context: Context?,
    private val ioDispatcher: CoroutineDispatcher,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(context, ioDispatcher, forTesting = false)

    constructor() : this(null, Dispatchers.Unconfined, forTesting = true)

    /**
     * Checks whether [uri] contains an embedded Motion Photo MP4 trailer and extracts it
     * into a temporary cache file so ExoPlayer can play it cleanly on long-press.
     */
    suspend fun extractMotionVideoUri(mediaId: Long, uri: Uri): Uri? = withContext(ioDispatcher) {
        val ctx = context ?: return@withContext null
        val cacheDir = File(ctx.cacheDir, "motion_cache").apply { mkdirs() }
        val cachedFile = File(cacheDir, "motion_$mediaId.mp4")
        if (cachedFile.exists() && cachedFile.length() > 1024L) {
            return@withContext Uri.fromFile(cachedFile)
        }

        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null

        val offset = findMotionVideoStartOffset(bytes) ?: return@withContext null
        if (offset <= 0 || offset >= bytes.size - 16) return@withContext null

        return@withContext runCatching {
            FileOutputStream(cachedFile).use { out ->
                out.write(bytes, offset, bytes.size - offset)
            }
            Uri.fromFile(cachedFile)
        }.getOrNull()
    }

    private fun findMotionVideoStartOffset(bytes: ByteArray): Int? {
        // 1. Check XMP MicroVideoOffset / MotionPhoto length markers in the first 128 KB
        val headerLimit = minOf(bytes.size, 131_072)
        val headerText = String(bytes, 0, headerLimit, Charsets.ISO_8859_1)

        val microVideoRegex = Regex("""MicroVideoOffset="(\d+)"""")
        microVideoRegex.find(headerText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { tailOffset ->
            val candidate = bytes.size - tailOffset
            if (isMp4HeaderAt(bytes, candidate)) return candidate
        }

        // 2. Fallback: scan for ISO BMFF 'ftyp' box signature (4-byte size + "ftyp")
        for (i in 4 until bytes.size - 12) {
            if (bytes[i] == 'f'.code.toByte() &&
                bytes[i + 1] == 't'.code.toByte() &&
                bytes[i + 2] == 'y'.code.toByte() &&
                bytes[i + 3] == 'p'.code.toByte()
            ) {
                val boxStart = i - 4
                if (boxStart > 1024 && isMp4HeaderAt(bytes, boxStart)) {
                    return boxStart
                }
            }
        }
        return null
    }

    private fun isMp4HeaderAt(bytes: ByteArray, index: Int): Boolean {
        if (index < 0 || index + 12 >= bytes.size) return false
        return bytes[index + 4] == 'f'.code.toByte() &&
            bytes[index + 5] == 't'.code.toByte() &&
            bytes[index + 6] == 'y'.code.toByte() &&
            bytes[index + 7] == 'p'.code.toByte()
    }
}

