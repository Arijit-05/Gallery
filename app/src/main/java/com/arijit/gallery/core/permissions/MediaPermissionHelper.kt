package com.arijit.gallery.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccessLevel {
    NONE,
    PARTIAL,
    FULL
}

object MediaPermissionHelper {

    fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        }
    }

    fun getAccessLevel(context: Context): MediaAccessLevel {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasImages = context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideo = context.hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
                val hasPartial = context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    hasImages && hasVideo -> MediaAccessLevel.FULL
                    hasPartial || hasImages || hasVideo -> MediaAccessLevel.PARTIAL
                    else -> MediaAccessLevel.NONE
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideo = context.hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
                when {
                    hasImages && hasVideo -> MediaAccessLevel.FULL
                    hasImages || hasVideo -> MediaAccessLevel.PARTIAL
                    else -> MediaAccessLevel.NONE
                }
            }
            else -> {
                if (context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    MediaAccessLevel.FULL
                } else {
                    MediaAccessLevel.NONE
                }
            }
        }
    }

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

