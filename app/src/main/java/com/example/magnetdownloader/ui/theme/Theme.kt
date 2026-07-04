package com.example.magnetdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFF90CAF9),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0D47A1),
    onTertiaryContainer = Color(0xFFBBDEFB),
    error = Color(0xFFEF9A9A),
    errorContainer = Color(0xFFB71C1C),
    onError = Color(0xFF000000),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF616161)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFF1E88E5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1E8FF),
    onTertiaryContainer = Color(0xFF0D3B66),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFCDD2),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFBDBDBD)
)

@Composable
fun MagnetDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
