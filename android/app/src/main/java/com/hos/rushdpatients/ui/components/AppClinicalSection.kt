package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Display formatting only: saved text and exported representations are never rewritten. */
enum class ClinicalTextKind { CLINICAL, LABS }

private val clinicalDateMarker = Regex("""@date:(\d{4})-(\d{2})-(\d{2})""")
internal fun clinicalDisplayText(value: String): String = clinicalDateMarker.replace(value) { match ->
    val (year, month, day) = match.destructured
    uiNumber("$day/$month/$year")
}

@Composable
fun AppClinicalSection(title: String, content: String, modifier: Modifier = Modifier,
    kind: ClinicalTextKind = ClinicalTextKind.CLINICAL, maxLines: Int = Int.MAX_VALUE,
    emptyText: String? = null, showHeading: Boolean = true) {
    val value = if (content.isBlank()) emptyText ?: return else content
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        if (showHeading) AppSectionHeader(title, titleColor = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, role = if (kind == ClinicalTextKind.LABS) AppTextRole.LABS else AppTextRole.CLINICAL,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
    }
}
