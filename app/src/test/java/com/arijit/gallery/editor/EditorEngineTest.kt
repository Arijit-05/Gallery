package com.arijit.gallery.editor

import com.arijit.gallery.editor.model.AdjustmentType
import com.arijit.gallery.editor.model.CropAspectRatio
import com.arijit.gallery.editor.model.PhotoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEngineTest {

    @Test
    fun photoFilters_allFiltersContainsExpectedColorOSPresets() {
        val filters = PhotoFilter.ALL_FILTERS
        assertTrue(filters.size >= 10)

        val names = filters.map { it.name }
        assertTrue(names.contains("Original"))
        assertTrue(names.contains("Vivid"))
        assertTrue(names.contains("Warm"))
        assertTrue(names.contains("Cool"))
        assertTrue(names.contains("B&W"))
        assertTrue(names.contains("Film"))
        assertTrue(names.contains("Drama"))
        assertTrue(names.contains("Sunset"))
        assertTrue(names.contains("Fade"))
        assertTrue(names.contains("Cyber"))
        assertTrue(names.contains("Crisp"))
        assertTrue(names.contains("Muted"))
    }

    @Test
    fun photoFilters_matrixDimensionsAre20() {
        for (filter in PhotoFilter.ALL_FILTERS) {
            val matrix = filter.matrix
            if (matrix != null) {
                assertEquals("Filter ${filter.name} matrix must be 20 elements (4x5)", 20, matrix.size)
            }
        }
    }

    @Test
    fun adjustmentTypes_defaultValuesAreWithinBounds() {
        for (adj in AdjustmentType.entries) {
            assertTrue(
                "Adjustment ${adj.label} default value must be between min and max",
                adj.defaultValue in adj.min..adj.max
            )
        }
    }

    @Test
    fun cropAspectRatios_haveProperLabels() {
        val free = CropAspectRatio.Free
        assertEquals("Free", free.label)

        val square = CropAspectRatio.Square
        assertEquals("1:1", square.label)
        assertEquals(1f, square.ratioX)
        assertEquals(1f, square.ratioY)
    }
}
