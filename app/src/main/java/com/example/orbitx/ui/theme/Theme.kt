package com.example.orbitx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalOrbitDark = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7D1F4),
    onPrimary = Color(0xFF161819),
    primaryContainer = Color(0xFF263139),
    onPrimaryContainer = Color(0xFFE2ECFA),
    tertiary = Color(0xFFB4E3CE),
    onTertiary = Color(0xFF122921),
    background = Color(0xFF161819),
    onBackground = Color(0xFFF6F7F7),
    surface = Color(0xFF222527),
    onSurface = Color(0xFFF6F7F7),
    surfaceVariant = Color(0xFF303438),
    onSurfaceVariant = Color(0xFFBCC1C4),
    surfaceContainer = Color(0xFF1F2123),
    surfaceContainerHigh = Color(0xFF303438),
    outline = Color(0xFF838A92),
    outlineVariant = Color(0xFF383C3F),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF161819),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF31577F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EDF8),
    onPrimaryContainer = Color(0xFF1C3C60),
    tertiary = Color(0xFF26634E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F5F6),
    onBackground = Color(0xFF1D2329),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D2329),
    surfaceVariant = Color(0xFFE4E9EE),
    onSurfaceVariant = Color(0xFF4B5661),
    surfaceContainer = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE9EDF1),
    outline = Color(0xFF677582),
    outlineVariant = Color(0xFFCDD5DD),
    inverseSurface = Color(0xFF202A34),
    inverseOnSurface = Color(0xFFFFFFFF),
)

@Composable
internal fun OrbitXTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalOrbitDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
