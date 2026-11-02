package com.example.drowsydrive.ui.main

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC1F1DF),
    onPrimaryContainer = Color(0xFF002C23),
    secondaryContainer = Color(0xFFE1EDE5),
    onSecondaryContainer = Color(0xFF20372C),
    surface = Color(0xFFF6F8F4),
    surfaceContainer = Color(0xFFEEF2EC),
    surfaceContainerLow = Color(0xFFF0F4EF),
    onSurface = Color(0xFF17251F),
    onSurfaceVariant = Color(0xFF53645B),
    outlineVariant = Color(0xFFD5DED6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D6B9),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFFC1F1DF),
    secondaryContainer = Color(0xFF324B3F),
    onSecondaryContainer = Color(0xFFD7E9DC),
    surface = Color(0xFF101A15),
    surfaceContainer = Color(0xFF1D2922),
    surfaceContainerLow = Color(0xFF18241E),
    onSurface = Color(0xFFE1EAE1),
    onSurfaceVariant = Color(0xFFB6C9BC),
    outlineVariant = Color(0xFF3E5045),
)

@Composable
internal fun DriveGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
