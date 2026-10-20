package com.hos.rushdpatients.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.DashboardFilter
import com.hos.rushdpatients.ui.ward.PatientSortOptions
import com.hos.rushdpatients.ui.ward.WardViewOption
import com.hos.rushdpatients.ui.ward.sortReviewLabel

internal enum class DemoDestination(val title: String) {
    PATIENTS("المرضى"), DASHBOARD("لوحة تسليم المناوبة"),
    TEAM("أطباء المناوبة"), REPORT("معاينة التقرير"), SETTINGS("الإعدادات")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoDrawer(
    state: DemoWardState, selected: DemoDestination,
    onNavigate: (DemoDestination) -> Unit,
    onRecycle: () -> Unit, onReset: () -> Unit, onExit: () -> Unit
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppSection("مشفى تجريبي · تقرير المناوبة") {
                Text(state.actor.fullName, style = MaterialTheme.typography.titleMedium)
                Text("${state.date} · جلسة مؤقتة", style = MaterialTheme.typography.bodySmall)
            }
            Text("اكتشف التطبيق ببيانات وهمية. التغييرات هنا لا تؤثر في مشروعك.",
                style = MaterialTheme.typography.bodySmall)
            DemoDestination.entries.forEach { destination ->
                NavigationDrawerItem(selected = selected == destination,
                    label = { Text(destination.title) },
                    icon = { Icon(when (destination) {
                        DemoDestination.PATIENTS -> Icons.Filled.ViewAgenda
                        DemoDestination.DASHBOARD -> Icons.Filled.Dashboard
                        DemoDestination.TEAM -> Icons.Filled.Groups
                        DemoDestination.REPORT -> Icons.Filled.Description
                        DemoDestination.SETTINGS -> Icons.Filled.Settings
                    }, contentDescription = null) }, onClick = { onNavigate(destination) })
            }
            Divider()
            AppTextButton(onClick = onRecycle, modifier = Modifier.fillMaxWidth()) {
                Text("سلة المحذوفات (${uiNumber(state.deleted.size)})")
            }
            AppTextButton(onClick = onReset, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text("إعادة ضبط البيانات التجريبية")
            }
            AppOutlinedButton(onClick = onExit, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text("إنهاء التجربة")
            }
        }
    }
}

internal fun DashboardFilter.demoLabel(): String = when (this) {
    DashboardFilter.ALL -> "الكل"
    DashboardFilter.MINE -> "مرضاي ومهامي"
    DashboardFilter.URGENT -> "عاجل أو أولوية"
    DashboardFilter.WARNINGS -> "مع شارة"
    DashboardFilter.UNASSIGNED -> "بحاجة إلى تعيين طبيب"
    DashboardFilter.NEW_ADMISSIONS -> "دخول اليوم"
    DashboardFilter.TASK_PENDING -> "مهام معلقة"
    DashboardFilter.TASK_OVERDUE -> "مهام متأخرة"
    DashboardFilter.TASK_UNASSIGNED -> "مهام غير معيّنة"
}

/** A reduced version of the ward's options, with the same fields and saved-sort editor. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DemoViewOptions(
    filter: DashboardFilter, onFilter: (DashboardFilter) -> Unit,
    expanded: Boolean, onExpanded: (Boolean) -> Unit,
    compact: Boolean, onCompact: (Boolean) -> Unit,
    twoColumn: Boolean, onTwoColumn: (Boolean) -> Unit,
    sort: SortSpec, onSort: (SortSpec) -> Unit,
    onReset: () -> Unit, onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(WardViewOption.FILTERS) }
    val sections = listOf(WardViewOption.FILTERS, WardViewOption.CARDS, WardViewOption.SORT)
    AlertDialog(onDismissRequest = onDismiss, title = {
        Column {
            Text("خيارات العرض")
            AppSectionIndex(sections, selected, label = { it.label }, onSelect = { selected = it })
        }
    }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            when (selected) {
                WardViewOption.FILTERS -> {
                    Text("التصفية للعرض فقط؛ يبقى التقرير شاملاً لكل المرضى.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        DashboardFilter.entries.forEach { value ->
                            FilterChip(selected = filter == value, onClick = { onFilter(value) },
                                label = { Text(value.demoLabel()) })
                        }
                    }
                }
                WardViewOption.CARDS -> {
                    AppToggleRow("عرض التفاصيل", checked = expanded, onCheckedChange = onExpanded)
                    AppToggleRow("بطاقة بعمودين عند توفر المساحة", checked = twoColumn, onCheckedChange = onTwoColumn)
                    AppToggleRow("بطاقات مضغوطة", checked = compact, onCheckedChange = onCompact)
                }
                WardViewOption.SORT -> {
                    Text("ترتيب الجلسة التجريبية؛ يستخدم أيضاً في PDF.", style = MaterialTheme.typography.bodySmall)
                    Text(sortReviewLabel(sort), style = MaterialTheme.typography.bodySmall)
                    PatientSortOptions(initial = sort, initialRevision = 0,
                        onApply = { spec, _, _ -> onSort(spec) })
                }
                WardViewOption.GROUPING -> Unit
            }
        }
    }, confirmButton = { AppDialogDismissAction(onClick = onDismiss, text = "تم") },
        dismissButton = { AppTextButton(onClick = onReset) { Text("مسح التصفية") } })
}
