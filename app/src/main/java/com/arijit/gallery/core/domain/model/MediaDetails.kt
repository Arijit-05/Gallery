package com.arijit.gallery.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MediaDetails(
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val dateTakenFormatted: String,
    val sizeFormatted: String,
    val resolutionFormatted: String,
    val durationFormatted: String? = null,
    val cameraMakeModel: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val shutterSpeed: String? = null,
    val focalLength: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null
)

