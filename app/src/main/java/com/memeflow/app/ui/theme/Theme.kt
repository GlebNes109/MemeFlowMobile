package com.memeflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    secondary = Teal,
    tertiary = Amber,
    background = Ink,
    surface = ColorPalette.darkSurface,
    surfaceVariant = ColorPalette.darkSurfaceVariant,
    onPrimary = Mist,
    onSecondary = Mist,
    onTertiary = Ink,
    onBackground = Mist,
    onSurface = Mist,
    onSurfaceVariant = Cloud
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    secondary = Teal,
    tertiary = Moss,
    background = Mist,
    surface = Sand,
    surfaceVariant = Cloud,
    onPrimary = Mist,
    onSecondary = Mist,
    onTertiary = Mist,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Slate
)

private object ColorPalette {
    val darkSurface = Ink.copy(alpha = 0.92f)
    val darkSurfaceVariant = Slate.copy(alpha = 0.45f)
}

@Composable
fun MemeFlowTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme && dynamicColor) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
