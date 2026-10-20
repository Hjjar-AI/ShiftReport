package com.hos.rushdpatients.ui.components

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
enum class UiAction(val label: String, val icon: ImageVector) {
    EDIT("تحرير", Icons.Filled.Edit), FULL_RECORD("الملف الكامل", Icons.Filled.Info),
    RESTORE("استعادة", Icons.Filled.History), REVIEW("مراجعة", Icons.Filled.Visibility),
    SEND_REPORT("إرسال تقرير", Icons.Filled.Send), SAVE("حفظ", Icons.Filled.Save), CANCEL("إلغاء", Icons.Filled.Close)
}

@Composable
fun AppActionLabel(action: UiAction) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
        Text(action.label)
    }
}
