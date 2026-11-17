package com.hos.rushdpatients.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.hos.rushdpatients.ui.theme.Tone
import com.hos.rushdpatients.ui.theme.toneColors

/** DEFAULT preserves the caller's inherited typography; explicit roles opt into text transforms. */
enum class AppTextRole {
    DEFAULT, CAPTION, SUPPORTING, BODY, BODY_STRONG, HEADING, LABEL, TITLE, NUMERIC, CLINICAL, LABS
}

@Composable
fun AppText(
    text: String, modifier: Modifier = Modifier, role: AppTextRole = AppTextRole.DEFAULT,
    tone: Tone? = null, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = TextStyle.Default
) {
    val typography = MaterialTheme.typography
    val base = when (role) {
        AppTextRole.DEFAULT -> LocalTextStyle.current
        AppTextRole.CAPTION -> typography.labelSmall
        AppTextRole.SUPPORTING -> typography.bodySmall
        AppTextRole.HEADING -> typography.titleSmall
        AppTextRole.LABEL -> typography.labelLarge
        AppTextRole.TITLE -> typography.titleMedium
        AppTextRole.NUMERIC -> typography.headlineSmall
        AppTextRole.BODY_STRONG -> typography.bodyMedium.copy(fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight)
        else -> typography.bodyMedium
    }
    val directed = when (role) {
        AppTextRole.DEFAULT -> base
        AppTextRole.LABS -> base.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.ContentOrLtr)
        AppTextRole.NUMERIC -> base.copy(textDirection = TextDirection.ContentOrLtr)
        else -> base.copy(textDirection = TextDirection.ContentOrRtl)
    }
    val foreground = when {
        color != Color.Unspecified -> color
        tone != null -> toneColors(tone).content
        role == AppTextRole.SUPPORTING || role == AppTextRole.CAPTION -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Unspecified
    }
    val value = when (role) {
        AppTextRole.CLINICAL, AppTextRole.LABS -> clinicalDisplayText(text)
        AppTextRole.NUMERIC -> uiNumber(text)
        else -> text
    }
    Text(value, modifier = if (role == AppTextRole.HEADING) modifier.semantics { heading() } else modifier,
        color = foreground, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = directed.merge(style))
}

/** Styled spans and inline content retain their exact caller-provided presentation. */
@Composable
fun AppText(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = LocalTextStyle.current
) {
    Text(text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing,
        textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
        inlineContent, onTextLayout, style)
}
