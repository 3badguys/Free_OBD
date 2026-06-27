package com.freeobd.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Free OBD Material3 theme.
 *
 * Uses a dark-only color scheme optimized for in-vehicle use:
 * - High contrast for daytime visibility
 * - Dark backgrounds to reduce glare at night
 * - Automotive-inspired teal accent color
 */

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryVariant,
    secondary = StatusBlue,
    onSecondary = OnPrimary,
    tertiary = StatusGreen,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = StatusRed,
    onError = OnBackground
)

@Composable
fun FreeOBDTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to match the background
            window.statusBarColor = Background.toArgb()
            window.navigationBarColor = Background.toArgb()
            // Light status bar icons on dark background
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
