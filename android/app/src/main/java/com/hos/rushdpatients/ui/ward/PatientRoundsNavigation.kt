package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
    onNavigate: (String) -> Unit,
    centerAction: (@Composable (Modifier) -> Unit)? = null
) {
    val uiText = rememberUiStrings()
    val counter: @Composable (Modifier) -> Unit = { modifier ->
        Text(ArabicNumbers.toAscii("${navigation.index + 1} / ${navigation.total}"),
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier.semantics { contentDescription = uiText(R.string.ui_patient_rounds_navigation_002, navigation.index + 1, navigation.total) })
    }
    Column(Modifier.fillMaxWidth().padding(UiPadding.content),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny), horizontalAlignment = Alignment.CenterHorizontally) {
        if (centerAction != null) counter(Modifier)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            AppOutlinedButton(onClick = { navigation.previousId?.let(onNavigate) },
                enabled = enabled && navigation.previousId != null, modifier = Modifier.weight(1f)) { Text(uiText(R.string.ui_previous)) }
            if (centerAction != null) centerAction(Modifier.weight(1f)) else counter(Modifier)
            AppOutlinedButton(onClick = { navigation.nextId?.let(onNavigate) },
                enabled = enabled && navigation.nextId != null, modifier = Modifier.weight(1f)) { Text(if (navigation.nextId == null) uiText(R.string.ui_patient_rounds_navigation_003) else uiText(R.string.ui_next)) }
        }
    }
}

/** Horizontal content drags supplement the same guarded Previous/Next actions. */
@Composable
internal fun Modifier.patientRoundsSwipe(
    patientId: String, navigation: PatientRoundsNavigation?, enabled: Boolean,
    onNavigate: (String) -> Unit
): Modifier {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val navigate by androidx.compose.runtime.rememberUpdatedState(onNavigate)
    return if (navigation == null || !enabled) this else pointerInput(patientId, navigation, rtl) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onHorizontalDrag = { _, amount -> distance += amount },
            onDragEnd = {
                val threshold = maxOf(com.hos.rushdpatients.ui.theme.UiLayout.roundsSwipeDistance.toPx(), size.width * .2f)
                if (kotlin.math.abs(distance) >= threshold) {
                    val next = if (rtl) distance > 0f else distance < 0f
                    (if (next) navigation.nextId else navigation.previousId)?.let(navigate)
                }
                distance = 0f
            }
        )
    }
}
