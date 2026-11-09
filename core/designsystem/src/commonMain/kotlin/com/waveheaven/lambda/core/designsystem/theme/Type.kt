package com.waveheaven.lambda.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waveheaven.lambda.core.designsystem.resources.Res
import com.waveheaven.lambda.core.designsystem.resources.atkinson_hyperlegible_bold
import com.waveheaven.lambda.core.designsystem.resources.atkinson_hyperlegible_italic
import com.waveheaven.lambda.core.designsystem.resources.atkinson_hyperlegible_regular
import com.waveheaven.lambda.core.designsystem.resources.ibm_plex_sans_condensed_bold
import com.waveheaven.lambda.core.designsystem.resources.ibm_plex_sans_condensed_medium
import com.waveheaven.lambda.core.designsystem.resources.ibm_plex_sans_condensed_semibold
import org.jetbrains.compose.resources.Font

/**
 * Atkinson Hyperlegible for reading (designed by the Braille Institute for low-vision readers, WH-103) and IBM Plex Sans
 * Condensed for titles and numbers, the same pair as the download page. Both are under the SIL Open Font License,
 * bundled in `composeResources/font` with their licences in `core/designsystem/fonts`.
 */
@Composable
internal fun atkinson(): FontFamily =
    FontFamily(
        Font(Res.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(Res.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )

/** Titles and large numbers, such as a dose or a level. */
@Composable
fun condensedFamily(): FontFamily =
    FontFamily(
        Font(Res.font.ibm_plex_sans_condensed_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_sans_condensed_semibold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_sans_condensed_bold, FontWeight.Bold),
    )

@Composable
internal fun whTypography(): Typography {
    val text = atkinson()
    val display = condensedFamily()

    fun d(
        size: Int,
        line: Int,
        weight: FontWeight = FontWeight.Bold,
    ) = TextStyle(fontFamily = display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.01).em)

    fun t(
        size: Int,
        line: Int,
        weight: FontWeight = FontWeight.Normal,
    ) = TextStyle(fontFamily = text, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = d(57, 62),
        displayMedium = d(45, 50),
        displaySmall = d(36, 42),
        headlineLarge = d(32, 38),
        headlineMedium = d(28, 34),
        headlineSmall = d(24, 30, FontWeight.SemiBold),
        titleLarge = d(24, 30, FontWeight.SemiBold),
        titleMedium = t(17, 24, FontWeight.Bold),
        titleSmall = t(15, 21, FontWeight.Bold),
        bodyLarge = t(17, 25),
        bodyMedium = t(15, 22),
        bodySmall = t(13, 19),
        labelLarge = t(15, 20, FontWeight.Bold),
        labelMedium = t(13, 18, FontWeight.Bold),
        labelSmall = t(12, 16, FontWeight.Bold),
    )
}
