package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.ui.theme.LocalClinicalColors
import com.hos.rushdpatients.util.ArabicNumbers
import java.time.ZoneId
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun PatientCard(
    patient: Patient,
    expanded: Boolean,
    twoColumn: Boolean,
    doctorNames: Map<String, String>,
    readOnly: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    onEdit: () -> Unit = onClick,
    onBadgeEdit: () -> Unit = onEdit,
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onPriorityChange: (Boolean) -> Unit = {},
    pinned: Boolean = false,
    onPinToggle: () -> Unit = {},
    onExpandToggle: () -> Unit = {},
    showViewControls: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    // The live ward uses one predictable clinical hierarchy. Legacy style values are
    // retained only so older stored preferences continue to decode safely.
    val effectiveStyle = PatientCardStyle.BADGE_HEADER
    val clinicalColors = LocalClinicalColors.current
    val admissionCount = remember(patient.admittanceNumber) {
        patient.admittanceNumber.toIntOrNull() ?: 0
    }
    val residentName = patient.responsibleResidentId?.let(doctorNames::get)
    val supervisorName = patient.responsibleSpecialistId?.let(doctorNames::get)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                contentDescription = buildString {
                    append(patient.name)
                    append("، رقم القبول الحالي ").append(patient.admittanceNumber)
                    append("، ").append(patient.diagnosisType.arabicLabel)
                    patient.responsibleResidentId?.let { id ->
                        doctorNames[id]?.let { append("، المقيم ").append(it) }
                    }
                    if (patient.badges.isNotEmpty()) {
                        append("، ").append(patient.badges.size).append(" شارات")
                    }
                    if (pinned) append("، مثبت مؤقتاً")
                    append("، ").append(relativeFreshness(patient.updatedAt))
                }
                stateDescription = buildString {
                    append(if (patient.badges.isEmpty()) "دون شارات" else "شارات فعالة")
                    if (patient.isPriority) append("، أولوية")
                    append(if (expanded) "، التفاصيل موسعة" else "، التفاصيل مطوية")
                }
                customActions = buildList {
                    if (showViewControls) {
                        add(CustomAccessibilityAction(if (expanded) "طي تفاصيل المريض" else "توسيع تفاصيل المريض") { onExpandToggle(); true })
                        add(CustomAccessibilityAction(if (pinned) "إلغاء التثبيت المؤقت" else "تثبيت مؤقت") { onPinToggle(); true })
                        add(CustomAccessibilityAction("فتح تفاصيل المريض") { onLongClick(); true })
                        if (!readOnly) {
                            add(CustomAccessibilityAction("تعديل بيانات المريض") { onEdit(); true })
                            add(CustomAccessibilityAction("تعديل شارات المريض") { onBadgeEdit(); true })
                            add(CustomAccessibilityAction(if (patient.isPriority) "إلغاء الأولوية" else "تحديد أولوية") { onPriorityChange(!patient.isPriority); true })
                            add(CustomAccessibilityAction("نسخ كمريض جديد") { onCopy(); true })
                            add(CustomAccessibilityAction("حذف المريض مع التأكيد") { onDelete(); true })
                        }
                    }
                }
            }
            .combinedClickable(enabled = showViewControls, onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected || patient.badges.isNotEmpty()) 2.dp else 1.dp,
            color = when {
                patient.badges.any { it.priority == PatientBadgePriority.HIGH } -> clinicalColors.urgent
                patient.badges.any { it.priority == PatientBadgePriority.MEDIUM } -> clinicalColors.warning
                patient.badges.any { it.priority == PatientBadgePriority.LOW } -> MaterialTheme.colorScheme.tertiary
                patient.badges.isNotEmpty() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
        ) {
            PatientIdentityHeader(
                patient = patient,
                style = effectiveStyle,
                admissionCount = admissionCount,
                readOnly = readOnly,
                expanded = expanded,
                pinned = pinned,
                showViewControls = showViewControls,
                onExpandToggle = onExpandToggle,
                onPinToggle = onPinToggle,
                onPriorityChange = onPriorityChange,
                onCopy = onCopy,
                onDelete = onDelete
            )

            if (effectiveStyle != PatientCardStyle.AVATAR_CHIPS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosisChip(patient.diagnosisType)
                    if (patient.hasCompanion) {
                        SmallChip(
                            text = "مرافق",
                            container = MaterialTheme.colorScheme.surface,
                            content = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // -------- Doctor chips row --------
            if (supervisorName != null || residentName != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    supervisorName?.let {
                        SmallChip(
                            text = it,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    residentName?.let {
                        SmallChip(
                            text = it,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (patient.badges.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    patient.badges.forEach { badge ->
                        SmallChip(
                            text = buildString {
                                append(badge.text)
                                badge.priority?.let { append(" · ").append(it.arabicLabel) }
                            },
                            container = when (badge.priority) {
                                PatientBadgePriority.HIGH -> clinicalColors.urgentContainer
                                PatientBadgePriority.MEDIUM -> clinicalColors.warningContainer
                                PatientBadgePriority.LOW -> MaterialTheme.colorScheme.tertiaryContainer
                                null -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            content = when (badge.priority) {
                                PatientBadgePriority.HIGH -> clinicalColors.onUrgentContainer
                                PatientBadgePriority.MEDIUM -> clinicalColors.onWarningContainer
                                PatientBadgePriority.LOW -> MaterialTheme.colorScheme.onTertiaryContainer
                                null -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }

            if (patient.initialDiagnosis.isNotBlank()) {
                StyledField(
                    style = effectiveStyle,
                    code = "Dx",
                    monogram = "ت",
                    title = "التشخيص",
                    body = patient.initialDiagnosis,
                    accent = MaterialTheme.colorScheme.primary,
                    onAccent = MaterialTheme.colorScheme.onPrimary
                )
            }

            // -------- Expanded detail --------
            if (expanded) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                if (effectiveStyle == PatientCardStyle.ICON_ROWS ||
                    effectiveStyle == PatientCardStyle.FIELD_MONOGRAMS
                ) {
                    StyledExpandedDetail(
                        patient = patient,
                        style = effectiveStyle,
                        twoColumn = twoColumn
                    )
                } else if (twoColumn) {
                    TwoColumnDetail(patient = patient)
                } else {
                    SingleColumnDetail(patient = patient)
                }
            }
            patient.lastEditedByName?.let { editor ->
                val time = remember(patient.updatedAt) {
                    DateTimeFormatter.ofPattern("MM-dd HH:mm")
                        .format(patient.updatedAt.atZone(ZoneId.systemDefault()))
                }
                Text(
                    text = "آخر تعديل: $editor · $time · ${relativeFreshness(patient.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- Expanded layouts ----------------

@Composable
private fun PatientIdentityHeader(
    patient: Patient,
    style: PatientCardStyle,
    admissionCount: Int,
    readOnly: Boolean,
    expanded: Boolean,
    pinned: Boolean,
    showViewControls: Boolean,
    onExpandToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onPriorityChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val initial = patient.name.trim().firstOrNull()?.toString() ?: "م"
    val meta = listOf(metaLine(patient), admitLine(patient))
        .filter(String::isNotBlank)
        .joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (style) {
            PatientCardStyle.BANNER -> Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdentityName(patient.name)
                    Text(meta, style = MaterialTheme.typography.labelSmall)
                }
            }

            PatientCardStyle.ALERT -> Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        patient.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(meta, style = MaterialTheme.typography.labelSmall)
                }
            }

            PatientCardStyle.COLOR_BAR -> {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(diagnosisAccent(patient.diagnosisType))
                )
                IdentityText(patient.name, meta, Modifier.weight(1f))
            }

            PatientCardStyle.WRISTBAND -> Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        IdentityName(patient.name)
                        Text(meta, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        contentColor = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            patient.admittanceNumber.ifBlank { "#${patient.sortOrder}" },
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            PatientCardStyle.INITIALS,
            PatientCardStyle.AVATAR_CHIPS,
            PatientCardStyle.BADGE_HEADER -> {
                val badgeText = if (style == PatientCardStyle.BADGE_HEADER) {
                    patient.sortOrder.toString()
                } else initial
                Surface(
                    color = if (style == PatientCardStyle.INITIALS) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (style == PatientCardStyle.INITIALS) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(if (style == PatientCardStyle.BADGE_HEADER) 40.dp else 50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(badgeText, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    IdentityName(patient.name)
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (style == PatientCardStyle.AVATAR_CHIPS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DiagnosisChip(patient.diagnosisType)
                            if (patient.hasCompanion) {
                                SmallChip(
                                    "مرافق",
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            PatientCardStyle.ICON_ROWS,
            PatientCardStyle.FIELD_MONOGRAMS -> IdentityText(
                patient.name,
                meta,
                Modifier.weight(1f)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showViewControls) Column {
                IconButton(onClick = onPinToggle) {
                    Icon(
                        if (pinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (pinned) "إلغاء التثبيت المؤقت" else "تثبيت مؤقت"
                    )
                }
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "طي التفاصيل" else "توسيع التفاصيل"
                    )
                }
            }
            if (showViewControls && !readOnly) {
                PatientMenu(
                    priority = patient.isPriority,
                    onPriorityChange = onPriorityChange,
                    onCopy = onCopy,
                    onDelete = onDelete
                )
            }
            if (patient.isPriority) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "مريض ذو أولوية",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (style != PatientCardStyle.BADGE_HEADER) {
                OrderBadge(patient.sortOrder, admissionCount)
            }
        }
    }
}

private fun relativeFreshness(updatedAt: Instant): String {
    val minutes = Duration.between(updatedAt, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "تم التحديث الآن"
        minutes < 60 -> "تم التحديث منذ $minutes دقيقة"
        minutes < 24 * 60 -> "تم التحديث منذ ${minutes / 60} ساعة"
        else -> "تم التحديث منذ ${minutes / (24 * 60)} يوم"
    }
}

@Composable
private fun IdentityName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun IdentityText(name: String, meta: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        IdentityName(name)
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StyledExpandedDetail(
    patient: Patient,
    style: PatientCardStyle,
    twoColumn: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val useTwoColumns = twoColumn && maxWidth >= 440.dp
        if (!useTwoColumns) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StyledTreatment(patient, style)
                StyledFollowUp(patient, style)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) { StyledTreatment(patient, style) }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { StyledFollowUp(patient, style) }
            }
        }
    }
}

@Composable
private fun StyledTreatment(patient: Patient, style: PatientCardStyle) {
    StyledField(style, "Rx", "ع", "الخطة العلاجية", patient.treatmentPlan,
        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
}

@Composable
private fun StyledFollowUp(patient: Patient, style: PatientCardStyle) {
    StyledField(style, "F/U", "م", "المتابعة", patient.followUp,
        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    StyledField(style, "Labs", "خ", "التحاليل", patient.labs,
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
}

@Composable
private fun StyledField(
    style: PatientCardStyle,
    code: String,
    monogram: String,
    title: String,
    body: String,
    accent: Color,
    onAccent: Color
) {
    if (body.isBlank()) return
    if (style == PatientCardStyle.ICON_ROWS || style == PatientCardStyle.FIELD_MONOGRAMS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = if (style == PatientCardStyle.ICON_ROWS) {
                    MaterialTheme.colorScheme.primaryContainer
                } else accent,
                contentColor = if (style == PatientCardStyle.ICON_ROWS) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else onAccent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (style == PatientCardStyle.ICON_ROWS) code else monogram,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = accent)
                Text(
                    prettifyLabText(body),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = if (code == "Labs") {
                            TextDirection.ContentOrLtr
                        } else {
                            TextDirection.ContentOrRtl
                        },
                        fontFamily = if (code == "Labs") FontFamily.Monospace else null
                    )
                )
            }
        }
    } else {
        SectionBlock(title, prettifyLabText(body))
    }
}

@Composable
private fun diagnosisAccent(type: DiagnosisType): Color = when (type) {
    DiagnosisType.PSYCHIATRIC -> MaterialTheme.colorScheme.tertiary
    DiagnosisType.ADDICTION -> MaterialTheme.colorScheme.error
    DiagnosisType.DUAL -> MaterialTheme.colorScheme.primary
}

@Composable
private fun SingleColumnDetail(patient: Patient) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (patient.treatmentPlan.isNotBlank()) {
            SectionBlock("الخطة العلاجية", prettifyLabText(patient.treatmentPlan))
        }
        if (patient.followUp.isNotBlank()) {
            SectionBlock("المتابعة", prettifyLabText(patient.followUp))
        }
        if (patient.labs.isNotBlank()) {
            SectionBlock("التحاليل", prettifyLabText(patient.labs))
        }
    }
}

@Composable
private fun TwoColumnDetail(patient: Patient) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 440.dp) {
            SingleColumnDetail(patient)
            return@BoxWithConstraints
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            if (patient.treatmentPlan.isNotBlank()) {
                SectionBlock("الخطة العلاجية", prettifyLabText(patient.treatmentPlan))
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (patient.followUp.isNotBlank()) {
                SectionBlock("المتابعة", prettifyLabText(patient.followUp))
            }
            if (patient.labs.isNotBlank()) {
                SectionBlock("التحاليل", prettifyLabText(patient.labs))
            }
            if (patient.followUp.isBlank() && patient.labs.isBlank()) {
                Text(
                    text = "لا توجد متابعة أو تحاليل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }
    }
}

// ---------------- Sub-composables ----------------

@Composable
private fun PatientMenu(
    priority: Boolean,
    onPriorityChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menu = true },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "خيارات المريض",
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (priority) "إلغاء الأولوية" else "تثبيت كأولوية") },
                onClick = {
                    menu = false
                    onPriorityChange(!priority)
                }
            )
            DropdownMenuItem(
                text = { Text("نسخ كمريض جديد") },
                onClick = {
                    menu = false
                    onCopy()
                }
            )
            DropdownMenuItem(
                text = { Text("حذف") },
                onClick = {
                    menu = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun OrderBadge(order: Int, admissionCount: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = ArabicNumbers.toArabicDigits(order.toString()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (admissionCount > 1) {
                Text(
                text = ArabicNumbers.toArabicDigits("$admissionCount"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 6.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DiagnosisChip(type: DiagnosisType) {
    val container = when (type) {
        DiagnosisType.PSYCHIATRIC -> MaterialTheme.colorScheme.tertiaryContainer
        DiagnosisType.ADDICTION -> MaterialTheme.colorScheme.secondaryContainer
        DiagnosisType.DUAL -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (type) {
        DiagnosisType.PSYCHIATRIC -> MaterialTheme.colorScheme.onTertiaryContainer
        DiagnosisType.ADDICTION -> MaterialTheme.colorScheme.onSecondaryContainer
        DiagnosisType.DUAL -> MaterialTheme.colorScheme.onErrorContainer
    }
    SmallChip(text = type.arabicLabel, container = container, content = content)
}

@Composable
private fun SmallChip(text: String, container: Color, content: Color) {
    Surface(
        color = container,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionBlock(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall.copy(
                textDirection = if (title == "التحاليل") {
                    TextDirection.ContentOrLtr
                } else {
                    TextDirection.ContentOrRtl
                },
                fontFamily = if (title == "التحاليل") FontFamily.Monospace else null
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------- Helpers ----------------

private val LAB_DATE_MARKER = Regex("""@date:(\d{4})-(\d{2})-(\d{2})""")

private fun prettifyLabText(text: String): String =
    LAB_DATE_MARKER.replace(text) { m ->
        val (y, mo, d) = m.destructured
        "📅 $d/$mo/$y"
    }

private fun metaLine(patient: Patient): String = buildString {
    append(if (patient.gender == Gender.MALE) "ذكر" else "أنثى")
    patient.age?.let { append(" · ${ArabicNumbers.toArabicDigits(it.toString())} سنة") }
}

/**
 * Compact admission summary for the card header:
 *   رقم 1 · دخول 2026-09-20 · 70 يوم
 *
 * Empty pieces are omitted. If the patient has neither an admission number
 * nor an admission date, the line collapses to "".
 */
private fun admitLine(patient: Patient): String {
    val parts = mutableListOf<String>()
    if (patient.admittanceNumber.isNotBlank()) {
        parts += "رقم ${ArabicNumbers.toArabicDigits(patient.admittanceNumber)}"
    }
    patient.admittanceDate?.let { date ->
        parts += "دخول $date"
        patient.admittanceDays?.let { days ->
            parts += "${ArabicNumbers.toArabicDigits(days.toString())} يوم"
        }
    }
    return parts.joinToString(" · ")
}
