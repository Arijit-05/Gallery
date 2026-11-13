package com.arijit.gallery.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.arijit.gallery.core.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = GalleryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FC),
    onPrimaryContainer = Color(0xFF0A2E63),
    secondary = Color(0xFF4E5D78),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnBackground,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = GalleryBlueLight,
    onPrimary = Color(0xFF002B6B),
    primaryContainer = Color(0xFF183B73),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB5C4DF),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = AmoledBackground,
    surface = AmoledBackground,
    surfaceContainer = AmoledSurface,
    surfaceContainerLow = AmoledBackground,
    surfaceContainerHigh = AmoledSurfaceVariant,
    surfaceVariant = AmoledSurfaceVariant
)

private val GalleryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GalleryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val useAmoled = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (useAmoled) {
        baseScheme.copy(
            background = AmoledBackground,
            surface = AmoledBackground,
            surfaceContainerLow = AmoledBackground,
            surfaceContainer = AmoledSurface,
            surfaceContainerHigh = AmoledSurfaceVariant,
            surfaceVariant = AmoledSurfaceVariant
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDark
            insetsController.isAppearanceLightNavigationBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GalleryTypography,
        shapes = GalleryShapes,
        content = content
    )
}

