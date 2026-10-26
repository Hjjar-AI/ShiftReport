package com.hos.rushdpatients.ui.ward

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.domain.task.WardTaskEntry
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.demo.DemoData
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.UiSpacing

/** In-memory design samples only; no Hilt, repositories, network or clinical storage. */
@Preview(name = "Arabic cards", locale = "ar", widthDp = 360, heightDp = 1600)
@Preview(name = "Arabic cards — large text", locale = "ar", widthDp = 360, heightDp = 2000, fontScale = 2f)
@Preview(name = "Arabic cards — dark", locale = "ar", widthDp = 360, heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WardDesignGallery() {
    val doctors = remember { DemoData.doctors() }
    val patient = remember { DemoData.patients(doctors).first().copy(
        name = "شاي بالنعناع والزنجبيل والقرفة — اسم تجريبي طويل", isPriority = true) }
    val names = remember(doctors) { doctors.associate { it.id to it.fullName } }
    RushdPatientsTheme(appearance = AppAppearance.SYSTEM) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                    PatientCard(
        state = PatientCardState(patient = patient,
            expanded = false,
            twoColumn = false,
            doctorNames = names),
        actions = PatientCardActions.None
    )
                    PatientCard(
        state = PatientCardState(patient = patient,
            expanded = true,
            twoColumn = false,
            doctorNames = names),
        actions = PatientCardActions.None
    )
                    AppNotice("تعذر جلب البيانات — أعد المحاولة", kind = NoticeKind.ERROR)
                    AppNotice("تم الحفظ على الجهاز", kind = NoticeKind.SUCCESS)
                    EmptyState("لا يوجد مرضى", actionLabel = "إضافة مريض", onAction = {})
                    WardTaskRow(WardTaskEntry(patient, patient.tasks.first()))
                }
            }
        }
    }
}
