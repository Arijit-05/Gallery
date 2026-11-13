package com.arijit.gallery.editor.model

import android.graphics.ColorMatrix
import android.graphics.RectF
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.arijit.gallery.core.domain.model.MediaItem

enum class EditorTab(val label: String) {
    Crop("Crop"),
    Adjust("Adjust"),
    Filters("Filters"),
    Markup("Markup")
}

enum class CropAspectRatio(val label: String, val ratioX: Float, val ratioY: Float) {
    Free("Free", 0f, 0f),
    Original("Original", -1f, -1f),
    Square("1:1", 1f, 1f),
    Ratio4_3("4:3", 4f, 3f),
    Ratio3_4("3:4", 3f, 4f),
    Ratio16_9("16:9", 16f, 9f),
    Ratio9_16("9:16", 9f, 16f),
    Ratio3_2("3:2", 3f, 2f),
    Ratio2_3("2:3", 2f, 3f)
}

enum class AdjustmentType(
    val label: String,
    val min: Float,
    val max: Float,
    val defaultValue: Float,
    val unit: String = ""
) {
    Brightness("Brightness", -100f, 100f, 0f),
    Contrast("Contrast", -100f, 100f, 0f),
    Saturation("Saturation", -100f, 100f, 0f),
    Warmth("Warmth", -100f, 100f, 0f),
    Highlights("Highlights", -100f, 100f, 0f),
    Shadows("Shadows", -100f, 100f, 0f),
    Vignette("Vignette", 0f, 100f, 0f),
    Sharpness("Sharpness", 0f, 100f, 0f)
}

data class PhotoFilter(
    val id: String,
    val name: String,
    val matrix: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoFilter) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        val Original = PhotoFilter("original", "Original", null)

        val Vivid = PhotoFilter(
            "vivid", "Vivid",
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 5f,
                0f, 1.25f, 0f, 0f, 5f,
                0f, 0f, 1.25f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Warm = PhotoFilter(
            "warm", "Warm",
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 12f,
                0f, 1.05f, 0f, 0f, 4f,
                0f, 0f, 0.90f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Cool = PhotoFilter(
            "cool", "Cool",
            floatArrayOf(
                0.90f, 0f, 0f, 0f, -10f,
                0f, 1.02f, 0f, 0f, 0f,
                0f, 0f, 1.20f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val BAndW = PhotoFilter(
            "bw", "B&W",
            floatArrayOf(
                0.299f * 1.15f, 0.587f * 1.15f, 0.114f * 1.15f, 0f, -5f,
                0.299f * 1.15f, 0.587f * 1.15f, 0.114f * 1.15f, 0f, -5f,
                0.299f * 1.15f, 0.587f * 1.15f, 0.114f * 1.15f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Film = PhotoFilter(
            "film", "Film",
            floatArrayOf(
                1.05f, 0.05f, 0.05f, 0f, 15f,
                0.02f, 1.00f, 0.02f, 0f, 12f,
                0.02f, 0.02f, 0.90f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Drama = PhotoFilter(
            "drama", "Drama",
            floatArrayOf(
                1.35f, 0f, 0f, 0f, -20f,
                0f, 1.35f, 0f, 0f, -20f,
                0f, 0f, 1.35f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Sunset = PhotoFilter(
            "sunset", "Sunset",
            floatArrayOf(
                1.25f, 0.1f, 0f, 0f, 15f,
                0f, 1.0f, 0.1f, 0f, 0f,
                0.1f, 0f, 0.85f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Fade = PhotoFilter(
            "fade", "Fade",
            floatArrayOf(
                0.85f, 0f, 0f, 0f, 30f,
                0f, 0.85f, 0f, 0f, 30f,
                0f, 0f, 0.85f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Cyber = PhotoFilter(
            "cyber", "Cyber",
            floatArrayOf(
                1.1f, 0f, 0.3f, 0f, 10f,
                0f, 0.9f, 0.2f, 0f, -5f,
                0.2f, 0.1f, 1.3f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Crisp = PhotoFilter(
            "crisp", "Crisp",
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 2f,
                0f, 1.12f, 0f, 0f, 2f,
                0f, 0f, 1.12f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val Muted = PhotoFilter(
            "muted", "Muted",
            floatArrayOf(
                0.85f, 0.1f, 0.1f, 0f, 8f,
                0.1f, 0.85f, 0.1f, 0f, 8f,
                0.1f, 0.1f, 0.85f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val ALL_FILTERS = listOf(
            Original, Vivid, Warm, Cool, BAndW, Film,
            Drama, Sunset, Fade, Cyber, Crisp, Muted
        )
    }
}

data class DoodlePath(
    val points: List<Offset>,
    val color: Long,
    val strokeWidth: Float
)

data class DoodleText(
    val id: String,
    val text: String,
    val xPercent: Float,
    val yPercent: Float,
    val color: Long,
    val fontSize: Float
)

data class CropState(
    val aspectRatio: CropAspectRatio = CropAspectRatio.Original,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val straightenDegrees: Float = 0f,
    val cropRect: RectF = RectF(0f, 0f, 1f, 1f)
)

@Immutable
data class EditorSnapshot(
    val cropState: CropState,
    val adjustments: Map<AdjustmentType, Float>,
    val selectedFilter: PhotoFilter,
    val filterIntensity: Float,
    val markupPaths: List<DoodlePath>,
    val markupTexts: List<DoodleText>
)

@Immutable
data class EditorUiState(
    val item: MediaItem? = null,
    val activeTab: EditorTab = EditorTab.Crop,
    val cropState: CropState = CropState(),
    val adjustments: Map<AdjustmentType, Float> = AdjustmentType.entries.associateWith { it.defaultValue },
    val activeAdjustment: AdjustmentType = AdjustmentType.Brightness,
    val selectedFilter: PhotoFilter = PhotoFilter.Original,
    val filterIntensity: Float = 1f,
    val markupPaths: List<DoodlePath> = emptyList(),
    val markupTexts: List<DoodleText> = emptyList(),
    val activeColor: Long = 0xFFFFFFFF,
    val activeBrushSize: Float = 12f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

