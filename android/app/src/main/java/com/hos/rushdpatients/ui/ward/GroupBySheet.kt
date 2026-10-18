package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppChoiceRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectableGroup
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.sort.GroupByMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBySheet(
    initial: GroupByMode,
    onApply: (GroupByMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selected by remember { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .85f)
                .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
        ) {
            Text(
                text = "تجميع المرضى",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = UiSpacing.small)
            )

            Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()).selectableGroup()) {
                GroupByMode.entries.forEach { option ->
                    AppChoiceRow(label = option.arabicLabel, selected = selected == option,
                        onClick = { selected = option })
                }
            }

            AppButton(
                onClick = { onApply(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiSpacing.medium, bottom = UiSpacing.small)
            ) { Text("تطبيق") }
        }
    }
}
