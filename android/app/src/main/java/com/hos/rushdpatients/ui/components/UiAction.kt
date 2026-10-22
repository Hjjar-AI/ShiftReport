package com.hos.rushdpatients.ui.components

import androidx.annotation.StringRes


import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
    EDIT(R.string.ui_edit, AppIcons.Edit), FULL_RECORD(R.string.ui_ui_action_002, AppIcons.Info),
    RESTORE(R.string.ui_restore, AppIcons.History), REVIEW(R.string.ui_review, AppIcons.Visibility),
    SEND_REPORT(R.string.ui_send_report, AppIcons.Send), SAVE(R.string.ui_save, AppIcons.Save), CANCEL(R.string.ui_cancel, AppIcons.Close);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}

@Composable
fun AppActionLabel(action: UiAction) {
    val uiText = rememberUiStrings()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Icon(action.icon, contentDescription = null, modifier = if (action == UiAction.SEND_REPORT) Modifier.size(UiSize.iconMedium).directionalIcon() else Modifier.size(UiSize.iconMedium))
        Text(action.label(uiText))
    }
}
