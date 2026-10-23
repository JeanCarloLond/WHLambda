package com.waveheaven.lambda.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons used across the app, drawn from Material Design icon paths (Apache-2.0) to avoid pulling in the
 * whole icon library. Tint them with `Icon(…, tint = …)`; the fill colour here is only a placeholder.
 */
object WhIcons {
    val ArrowBack: ImageVector by lazy {
        materialIcon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }

    val Info: ImageVector by lazy {
        materialIcon(
            "Info",
            "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z",
        )
    }
}

private const val MATERIAL_ICON_SIZE = 24f

private fun materialIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = MATERIAL_ICON_SIZE.dp,
            defaultHeight = MATERIAL_ICON_SIZE.dp,
            viewportWidth = MATERIAL_ICON_SIZE,
            viewportHeight = MATERIAL_ICON_SIZE,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()
