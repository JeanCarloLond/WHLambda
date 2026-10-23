package com.waveheaven.lambda.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * Brand palette, inherited from the original WaveHeaven (blue = trust/calm, purple = music/wellbeing).
 * The original hues fail WCAG AA as text on white, so text-bearing roles use darker variants on
 * light surfaces and lighter variants on dark ones. Contrast ratios are noted against the surface.
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
