package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
                .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
        ) {
            Text(
                text = "تجميع المرضى",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = UiSpacing.small)
            )

            GroupByMode.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = option }
                        .padding(UiPadding.compact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)
                ) {
                    RadioButton(
                        selected = selected == option,
                        onClick = { selected = option }
                    )
                    Text(
                        text = option.arabicLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
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