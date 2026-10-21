package com.hos.rushdpatients.ui.components

import androidx.annotation.StringRes


import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Shared visible vocabulary. Consequential actions may add operation-specific context. */
enum class UiAction(@StringRes private val labelRes: Int, val icon: ImageVector) {
    EDIT(R.string.ui_edit, Icons.Filled.Edit), FULL_RECORD(R.string.ui_ui_action_002, Icons.Filled.Info),
    RESTORE(R.string.ui_restore, Icons.Filled.History), REVIEW(R.string.ui_review, Icons.Filled.Visibility),
    SEND_REPORT(R.string.ui_send_report, Icons.Filled.Send), SAVE(R.string.ui_save, Icons.Filled.Save), CANCEL(R.string.ui_cancel, Icons.Filled.Close);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}

@Composable
fun AppActionLabel(action: UiAction) {
    val uiText = rememberUiStrings()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
        Text(action.label(uiText))
    }
}
