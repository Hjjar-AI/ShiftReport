package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.theme.UiSize

/** Shared untinted bitmap, with its white glyph outline and natural wide proportions. */
@Composable
fun AppBrandMark(modifier: Modifier = Modifier) {
    val painter = painterResource(R.drawable.app_wordmark)
    Image(
        painter = painter,
        contentDescription = stringResource(R.string.about_app_title),
        contentScale = ContentScale.Fit,
        modifier = modifier.widthIn(max = UiSize.appWordmark).fillMaxWidth()
            .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height)
    )
}
