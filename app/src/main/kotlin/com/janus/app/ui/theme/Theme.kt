package com.janus.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Project Janus is dark-first by design (industrial console aesthetic) — a
 * light theme is intentionally not offered in Phase 1. Appearance settings
 * (Phase 10) may introduce theme variants later without changing this
 * function's call sites.
 *
 * dynamicColor defaults to false: the app's identity (teal accent, deep
 * neutral background) should stay consistent across devices rather than
 * being overridden by Android 12+ wallpaper-based Material You colors.
 */
private val JanusDarkColorScheme = darkColorScheme(
    primary = JanusPrimary,
    onPrimary = JanusOnPrimary,
    primaryContainer = JanusPrimaryVariant,
    onPrimaryContainer = JanusOnPrimary,
    background = JanusBackground,
    onBackground = JanusOnBackground,
    surface = JanusSurface,
    onSurface = JanusOnSurface,
    surfaceVariant = JanusSurfaceVariant,
    onSurfaceVariant = JanusOnSurfaceMuted,
    outline = JanusOutline,
    error = JanusStatusRed,
    onError = JanusOnBackground
)

@Composable
fun ProjectJanusTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        JanusDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JanusTypography,
        shapes = JanusShapes,
        content = content
    )
}