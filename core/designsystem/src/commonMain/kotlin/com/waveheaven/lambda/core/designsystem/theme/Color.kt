package com.waveheaven.lambda.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * Brand palette, inherited from the original WaveHeaven (blue = trust/calm, purple = music/wellbeing).
 * The original hues fail WCAG AA as text on white, so text-bearing roles use darker variants on
 * light surfaces and lighter variants on dark ones. Contrast ratios are noted against the surface.
 *
 * Surfaces are neutral greys with a touch of blue, so cards read as part of the brand instead of Material's default
 * lavender. Warnings use a warm tone (tertiary), errors stay red.
 */

/** Original brand blue. Use for large graphics and illustrations, not for text. */
val BrandBlue = Color(0xFF2878F0)

/** Original brand purple. Use for large graphics and illustrations, not for text. */
val BrandPurple = Color(0xFF805DDB)

internal val BlueOnLight = Color(0xFF1F66D1) // 5.4:1 on white
internal val PurpleOnLight = Color(0xFF6B46C8) // 6.3:1 on white
internal val BlueOnDark = Color(0xFF9DC2FF) // 10.2:1 on #121318
internal val PurpleOnDark = Color(0xFFC9B3FF) // 10.1:1 on #121318

internal val SurfaceLight = Color(0xFFFBFBFE)
internal val SurfaceDark = Color(0xFF121318)
internal val OnSurfaceLight = Color(0xFF1A1B20)
internal val OnSurfaceDark = Color(0xFFE3E2E9)

// Light tonal roles.
internal val BlueContainerLight = Color(0xFFDCE8FF)
internal val OnBlueContainerLight = Color(0xFF0B2A5C) // 12.6:1 on its container
internal val PurpleContainerLight = Color(0xFFEDE6FF)
internal val OnPurpleContainerLight = Color(0xFF2E1868)
internal val WarmLight = Color(0xFF9C4320) // 6.0:1 on white
internal val WarmContainerLight = Color(0xFFFFE6DA)
internal val OnWarmContainerLight = Color(0xFF4A1A06) // 12.9:1 on its container
internal val OnSurfaceVariantLight = Color(0xFF4A4E5A) // 8.3:1 on white
internal val OutlineLight = Color(0xFF767B88)
internal val OutlineVariantLight = Color(0xFFD6D9E1)
internal val ContainerLowestLight = Color(0xFFFFFFFF)
internal val ContainerLowLight = Color(0xFFF7F8FA)
internal val ContainerLight = Color(0xFFF3F4F7)
internal val ContainerHighLight = Color(0xFFEEF0F4)
internal val ContainerHighestLight = Color(0xFFE9EBF0)

// Dark tonal roles.
internal val BlueContainerDark = Color(0xFF1B3763)
internal val OnBlueContainerDark = Color(0xFFDCE8FF)
internal val PurpleContainerDark = Color(0xFF3A2F66)
internal val OnPurpleContainerDark = Color(0xFFEDE6FF)
internal val WarmDark = Color(0xFFFFB59B) // 10.3:1 on #121318
internal val WarmContainerDark = Color(0xFF4B2617)
internal val OnWarmContainerDark = Color(0xFFFFDCCC)
internal val OnSurfaceVariantDark = Color(0xFFC4C7D2) // 11.0:1 on #121318
internal val OutlineDark = Color(0xFF8D92A0)
internal val OutlineVariantDark = Color(0xFF353946)
internal val ContainerLowestDark = Color(0xFF0D0E12)
internal val ContainerLowDark = Color(0xFF17191F)
internal val ContainerDark = Color(0xFF1B1D25)
internal val ContainerHighDark = Color(0xFF1F222A)
internal val ContainerHighestDark = Color(0xFF252832)
