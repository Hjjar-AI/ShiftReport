package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.util.ArabicNumbers

internal data class PatientRoundsNavigation(
    val index: Int,
    val total: Int,
    val previousId: String?,
    val nextId: String?
)

/** Consume the displayed filtered order; never reorder or mutate clinical records here. */
internal fun patientRoundsNavigation(orderedIds: List<String>, selectedId: String): PatientRoundsNavigation? {
    val index = orderedIds.indexOf(selectedId)
    if (index < 0) return null
    return PatientRoundsNavigation(index, orderedIds.size,
        orderedIds.getOrNull(index - 1), orderedIds.getOrNull(index + 1))
}

@Composable
internal fun PatientRoundsBar(
    navigation: PatientRoundsNavigation,
    enabled: Boolean,
    onNavigate: (String) -> Unit
) {
    val uiText = rememberUiStrings()
    Row(Modifier.padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        AppOutlinedButton(onClick = { navigation.previousId?.let(onNavigate) },
            enabled = enabled && navigation.previousId != null, modifier = Modifier.weight(1f)) { Text(uiText(R.string.ui_previous)) }
        Text(ArabicNumbers.toArabicDigits("${navigation.index + 1} / ${navigation.total}"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { contentDescription = uiText(R.string.ui_patient_rounds_navigation_002, navigation.index + 1, navigation.total) })
        AppOutlinedButton(onClick = { navigation.nextId?.let(onNavigate) },
            enabled = enabled && navigation.nextId != null, modifier = Modifier.weight(1f)) { Text(if (navigation.nextId == null) uiText(R.string.ui_patient_rounds_navigation_003) else uiText(R.string.ui_next)) }
    }
}
