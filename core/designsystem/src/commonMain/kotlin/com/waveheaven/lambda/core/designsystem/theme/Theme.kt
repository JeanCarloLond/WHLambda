package com.waveheaven.lambda.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors =
    lightColorScheme(
        primary = BlueOnLight,
        onPrimary = Color.White,
        primaryContainer = BlueContainerLight,
        onPrimaryContainer = OnBlueContainerLight,
        secondary = PurpleOnLight,
        onSecondary = Color.White,
        secondaryContainer = PurpleContainerLight,
        onSecondaryContainer = OnPurpleContainerLight,
        tertiary = WarmLight,
        onTertiary = Color.White,
        tertiaryContainer = WarmContainerLight,
        onTertiaryContainer = OnWarmContainerLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = ContainerHighestLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        surfaceContainerLowest = ContainerLowestLight,
        surfaceContainerLow = ContainerLowLight,
        surfaceContainer = ContainerLight,
        surfaceContainerHigh = ContainerHighLight,
        surfaceContainerHighest = ContainerHighestLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = BlueOnDark,
        onPrimary = SurfaceDark,
        primaryContainer = BlueContainerDark,
        onPrimaryContainer = OnBlueContainerDark,
        secondary = PurpleOnDark,
        onSecondary = SurfaceDark,
        secondaryContainer = PurpleContainerDark,
        onSecondaryContainer = OnPurpleContainerDark,
        tertiary = WarmDark,
        onTertiary = SurfaceDark,
        tertiaryContainer = WarmContainerDark,
        onTertiaryContainer = OnWarmContainerDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = ContainerHighestDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        surfaceContainerLowest = ContainerLowestDark,
        surfaceContainerLow = ContainerLowDark,
        surfaceContainer = ContainerDark,
        surfaceContainerHigh = ContainerHighDark,
        surfaceContainerHighest = ContainerHighestDark,
    )

/** Rounder than Material's defaults: cards at 20 dp, dialogs and sheets at 28 dp. */
private val WhShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Root theme for every WaveHeaven Lambda screen, on every platform. */
@Composable
fun WaveHeavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = whTypography(),
        shapes = WhShapes,
        content = content,
    )
}
