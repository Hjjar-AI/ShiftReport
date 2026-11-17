package com.hos.rushdpatients.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Stacked أ/ي from bundled Tajawal Bold; fixed orientation keeps Arabic glyphs unmirrored. */
internal val ArabicSortIcon: ImageVector = ImageVector.Builder(
    name = "ArabicSort",
    defaultWidth = com.hos.rushdpatients.ui.theme.UiSize.icon,
    defaultHeight = com.hos.rushdpatients.ui.theme.UiSize.icon,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M5 3.5 L1.75 6.75 L3 8 L4.1 6.9 L4.1 17.1 L3 16 L1.75 17.25 L5 20.5 L8.25 17.25 L7 16 L5.9 17.1 L5.9 6.9 L7 8 L8.25 6.75 Z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    )
    addPath(
        pathData = PathParser().parsePathString(
            "m 14.788001,3.088 0.384,-0.696 h 0.36 q -0.132,-0.24 -0.132,-0.492 0,-0.384 0.276,-0.66 0.288,-0.276 0.684,-0.276 0.36,0 0.672,0.252 v 0.696 q -0.288,-0.252 -0.576,-0.252 -0.396,0 -0.396,0.36 0,0.18 0.156,0.372 h 1.188 l -0.384,0.696 z m 0.456,8.412 V 4.564 l 1.512,-0.804 v 7.74 z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    )
    addPath(
        pathData = PathParser().parsePathString(
            "m 19.186005,15.212 q -0.864,-0.792 -1.656,-0.792 -0.372,0 -0.612,0.216 -0.228,0.204 -0.228,0.552 0,0.432 0.276,0.672 0.288,0.24 1.128,0.54 0.84,0.276 1.176,0.552 0.54,0.444 0.54,1.236 0,1.236 -1.14,1.956 -1.02,0.636 -2.616,0.636 -1.992,0 -3,-1.14 -0.744,-0.852 -0.744,-2.088 0,-0.948 0.444,-1.656 l 1.44,-0.3 q -0.48,0.816 -0.48,1.716 0,0.948 0.6,1.476 0.6,0.54 1.74,0.54 1.152,0 1.752,-0.36 0.504,-0.312 0.504,-0.792 0,-0.252 -0.216,-0.408 -0.204,-0.168 -0.852,-0.396 -1.044,-0.372 -1.44,-0.768 -0.54,-0.54 -0.54,-1.428 0,-1.056 0.732,-1.644 0.552,-0.456 1.284,-0.456 1.224,0 1.908,0.852 z m -3.936,8.22 q -0.36,0 -0.576,-0.228 -0.216,-0.216 -0.216,-0.576 0,-0.348 0.216,-0.564 0.216,-0.216 0.576,-0.216 0.36,0 0.564,0.204 0.216,0.216 0.216,0.576 0,0.804 -0.78,0.804 z m 1.848,0 q -0.36,0 -0.576,-0.216 -0.216,-0.216 -0.216,-0.588 0,-0.78 0.792,-0.78 0.36,0 0.564,0.204 0.216,0.216 0.216,0.576 0,0.804 -0.78,0.804 z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    )
}.build()
