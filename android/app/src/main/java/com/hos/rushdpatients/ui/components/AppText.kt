package com.hos.rushdpatients.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.ui.theme.Tone
import com.hos.rushdpatients.ui.theme.toneColors

/** Semantic presentation roles retain Arabic text and explicit lab/numeric direction. */
enum class AppTextRole { CAPTION, SUPPORTING, BODY, BODY_STRONG, HEADING, NUMERIC, CLINICAL, LABS }

@Composable
fun AppText(text: String, modifier: Modifier = Modifier, role: AppTextRole = AppTextRole.BODY,
    tone: Tone? = null, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null) {
    val typography = MaterialTheme.typography
    val style = when (role) {
        AppTextRole.CAPTION -> typography.labelSmall
        AppTextRole.SUPPORTING -> typography.bodySmall
        AppTextRole.HEADING -> typography.titleSmall
        AppTextRole.NUMERIC -> typography.headlineSmall
        AppTextRole.BODY_STRONG -> typography.bodyMedium.copy(fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight)
        else -> typography.bodyMedium
    }.let { base -> when (role) {
        AppTextRole.LABS -> base.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.ContentOrLtr)
        AppTextRole.NUMERIC -> base.copy(textDirection = TextDirection.ContentOrLtr)
        else -> base.copy(textDirection = TextDirection.ContentOrRtl)
    } }
    val foreground = if (color != Color.Unspecified) color else tone?.let { toneColors(it).content }
        ?: if (role == AppTextRole.SUPPORTING || role == AppTextRole.CAPTION) MaterialTheme.colorScheme.onSurfaceVariant
        else LocalContentColor.current
    val value = when (role) {
        AppTextRole.CLINICAL, AppTextRole.LABS -> clinicalDisplayText(text)
        AppTextRole.NUMERIC -> uiNumber(text)
        else -> text
    }
    Text(value, modifier = if (role == AppTextRole.HEADING) modifier.semantics { heading() } else modifier,
        color = foreground, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
}
