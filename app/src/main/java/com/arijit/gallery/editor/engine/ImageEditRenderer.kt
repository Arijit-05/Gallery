package com.arijit.gallery.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.arijit.gallery.editor.model.AdjustmentType
import com.arijit.gallery.editor.model.CropState
import com.arijit.gallery.editor.model.DoodlePath
import com.arijit.gallery.editor.model.DoodleText
import com.arijit.gallery.editor.model.PhotoFilter
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object ImageEditRenderer {

    /**
     * Builds a single 4x5 ColorMatrix combining Brightness, Contrast, Saturation, Warmth,
     * and the selected PhotoFilter (blended with identity based on filter intensity).
     */
    fun buildColorMatrix(
        adjustments: Map<AdjustmentType, Float>,
        filter: PhotoFilter,
        filterIntensity: Float
    ): ColorMatrix {
        val finalMatrix = ColorMatrix()

        // 1. Brightness (-100 to 100) -> scale to offset
        val brightness = adjustments[AdjustmentType.Brightness] ?: 0f
        if (brightness != 0f) {
            val bOffset = brightness * 2.55f
            val bMat = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, bOffset,
                    0f, 1f, 0f, 0f, bOffset,
                    0f, 0f, 1f, 0f, bOffset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(bMat)
        }

        // 2. Contrast (-100 to 100) -> scale around 128
        val contrast = adjustments[AdjustmentType.Contrast] ?: 0f
        if (contrast != 0f) {
            val scale = (contrast + 100f) / 100f
            val cScale = scale * scale
            val cOffset = 128f * (1f - cScale)
            val cMat = ColorMatrix(
                floatArrayOf(
                    cScale, 0f, 0f, 0f, cOffset,
                    0f, cScale, 0f, 0f, cOffset,
                    0f, 0f, cScale, 0f, cOffset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(cMat)
        }

        // 3. Saturation (-100 to 100) -> 0 to 2
        val saturation = adjustments[AdjustmentType.Saturation] ?: 0f
        if (saturation != 0f) {
            val satFactor = (saturation + 100f) / 100f
            val sMat = ColorMatrix()
            sMat.setSaturation(satFactor.coerceIn(0f, 3f))
            finalMatrix.postConcat(sMat)
        }

        // 4. Warmth (-100 to 100) -> adjust Red & Blue channels
        val warmth = adjustments[AdjustmentType.Warmth] ?: 0f
        if (warmth != 0f) {
            val rShift = (warmth * 0.4f)
            val bShift = (-warmth * 0.4f)
            val wMat = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, rShift,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, bShift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(wMat)
        }

        // 5. Highlights and Shadows approximation
        val highlights = adjustments[AdjustmentType.Highlights] ?: 0f
        val shadows = adjustments[AdjustmentType.Shadows] ?: 0f
        if (highlights != 0f || shadows != 0f) {
            val hOffset = highlights * 0.5f
            val sOffset = shadows * 0.5f
            val hsMat = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, (hOffset + sOffset) / 2f,
                    0f, 1f, 0f, 0f, (hOffset + sOffset) / 2f,
                    0f, 0f, 1f, 0f, (hOffset + sOffset) / 2f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(hsMat)
        }

        // 6. Selected Filter (blend matrix with identity based on intensity)
        val filterArray = filter.matrix
        if (filterArray != null) {
            val intensity = filterIntensity.coerceIn(0f, 1f)
            val blended = FloatArray(20)
            val identity = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            for (i in 0 until 20) {
                blended[i] = identity[i] + (filterArray[i] - identity[i]) * intensity
            }
            val fMat = ColorMatrix(blended)
            finalMatrix.postConcat(fMat)
        }

        return finalMatrix
    }

    /**
     * Loads a downsampled Bitmap suitable for high-performance live editing in Compose.
     */
    fun loadPreviewBitmap(context: Context, uri: Uri, maxDimension: Int = 1920): Bitmap? {
        return runCatching {
            var input = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            var sampleSize = 1
            while (w / (sampleSize * 2) >= maxDimension || h / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            input = context.contentResolver.openInputStream(uri) ?: return null
            val rawBitmap = BitmapFactory.decodeStream(input, null, decodeOpts)
            input.close()

            if (rawBitmap == null) return null

            // Read EXIF orientation to orient preview bitmap upright
            val exifInput = context.contentResolver.openInputStream(uri)
            val orientation = exifInput?.use { s ->
                val exif = ExifInterface(s)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            rotateBitmapByExif(rawBitmap, orientation)
        }.getOrNull()
    }

    /**
     * Renders the complete, full-resolution edited image and writes it out to [outputStream].
     */
    fun renderFullResolution(
        context: Context,
        sourceUri: Uri,
        cropState: CropState,
        adjustments: Map<AdjustmentType, Float>,
        filter: PhotoFilter,
        filterIntensity: Float,
        markupPaths: List<DoodlePath>,
        markupTexts: List<DoodleText>,
        outputStream: OutputStream
    ): Boolean {
        return runCatching {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return false
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val sourceBitmap = BitmapFactory.decodeStream(input, null, options)
            input.close()
            if (sourceBitmap == null) return false

            // Read EXIF orientation to orient source bitmap upright
            val exifInput = context.contentResolver.openInputStream(sourceUri)
            val orientation = exifInput?.use { s ->
                val exif = ExifInterface(s)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val uprightBitmap = rotateBitmapByExif(sourceBitmap, orientation)

            // Step 1: Apply Geometry (Rotation, Flip, Straighten, Crop)
            val transformedBitmap = applyGeometry(uprightBitmap, cropState)

            // Step 2: Draw on output canvas applying ColorMatrix
            val resultBitmap = Bitmap.createBitmap(
                transformedBitmap.width,
                transformedBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(resultBitmap)

            val colorMatrix = buildColorMatrix(adjustments, filter, filterIntensity)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            canvas.drawBitmap(transformedBitmap, 0f, 0f, paint)

            // Step 3: Vignette (if > 0)
            val vignetteAmount = adjustments[AdjustmentType.Vignette] ?: 0f
            if (vignetteAmount > 0f) {
                drawVignette(canvas, transformedBitmap.width.toFloat(), transformedBitmap.height.toFloat(), vignetteAmount)
            }

            // Step 4: Draw Markup paths (scaled to canvas size)
            if (markupPaths.isNotEmpty()) {
                drawMarkupPaths(canvas, markupPaths, transformedBitmap.width.toFloat(), transformedBitmap.height.toFloat())
            }

            // Step 5: Draw Markup texts
            if (markupTexts.isNotEmpty()) {
                drawMarkupTexts(canvas, markupTexts, transformedBitmap.width.toFloat(), transformedBitmap.height.toFloat())
            }

            // Compress to JPEG with 95% quality
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()

            if (uprightBitmap != sourceBitmap) uprightBitmap.recycle()
            if (transformedBitmap != uprightBitmap) transformedBitmap.recycle()
            resultBitmap.recycle()
            sourceBitmap.recycle()
            true
        }.getOrElse { false }
    }

    private fun rotateBitmapByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyGeometry(source: Bitmap, crop: CropState): Bitmap {
        val matrix = Matrix()

        if (crop.flipHorizontal) matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        if (crop.flipVertical) matrix.postScale(1f, -1f, source.width / 2f, source.height / 2f)

        val totalRotation = (crop.rotationDegrees.toFloat() + crop.straightenDegrees)
        if (totalRotation != 0f) {
            matrix.postRotate(totalRotation, source.width / 2f, source.height / 2f)
        }

        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        // Apply normalized crop rect
        val rect = crop.cropRect
        val left = (rect.left.coerceIn(0f, 1f) * rotated.width).roundToInt()
        val top = (rect.top.coerceIn(0f, 1f) * rotated.height).roundToInt()
        val right = (rect.right.coerceIn(0f, 1f) * rotated.width).roundToInt()
        val bottom = (rect.bottom.coerceIn(0f, 1f) * rotated.height).roundToInt()

        val cropW = max(1, right - left)
        val cropH = max(1, bottom - top)

        val cropped = Bitmap.createBitmap(rotated, left, top, cropW, cropH)
        if (rotated != source && rotated != cropped) {
            rotated.recycle()
        }
        return cropped
    }

    private fun drawVignette(canvas: Canvas, width: Float, height: Float, amount: Float) {
        val radius = max(width, height) * 0.75f
        val alpha = ((amount / 100f) * 200).roundToInt().coerceIn(0, 255)
        val gradient = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(0x00000000, 0x00000000, (alpha shl 24)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawMarkupPaths(canvas: Canvas, paths: List<DoodlePath>, canvasWidth: Float, canvasHeight: Float) {
        for (doodle in paths) {
            if (doodle.points.size < 2) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = doodle.color.toInt()
                strokeWidth = doodle.strokeWidth * (canvasWidth / 1080f).coerceAtLeast(1f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            val first = doodle.points.first()
            path.moveTo(first.x * canvasWidth, first.y * canvasHeight)
            for (i in 1 until doodle.points.size) {
                val pt = doodle.points[i]
                path.lineTo(pt.x * canvasWidth, pt.y * canvasHeight)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawMarkupTexts(canvas: Canvas, texts: List<DoodleText>, canvasWidth: Float, canvasHeight: Float) {
        for (item in texts) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color.toInt()
                textSize = item.fontSize * (canvasWidth / 1080f).coerceAtLeast(1f)
                isFakeBoldText = true
            }
            val x = item.xPercent * canvasWidth
            val y = item.yPercent * canvasHeight
            canvas.drawText(item.text, x, y, paint)
        }
    }

    /**
     * Copies EXIF attributes from source to destination file.
     */
    fun copyExif(sourceInput: InputStream, destFilePath: String) {
        runCatching {
            val sourceExif = ExifInterface(sourceInput)
            val destExif = ExifInterface(destFilePath)

            val tagsToPreserve = arrayOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF
            )

            for (tag in tagsToPreserve) {
                val value = sourceExif.getAttribute(tag)
                if (value != null) {
                    destExif.setAttribute(tag, value)
                }
            }
            destExif.saveAttributes()
        }
    }
}

