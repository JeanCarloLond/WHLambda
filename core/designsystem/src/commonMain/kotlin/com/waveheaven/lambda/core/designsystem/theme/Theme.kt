package com.waveheaven.lambda.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = BlueOnLight,
        onPrimary = Color.White,
        secondary = PurpleOnLight,
        onSecondary = Color.White,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = BlueOnDark,
        onPrimary = SurfaceDark,
        secondary = PurpleOnDark,
        onSecondary = SurfaceDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
    )

/** Root theme for every WaveHeaven Lambda screen, on every platform. */
@Composable
fun WaveHeavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
