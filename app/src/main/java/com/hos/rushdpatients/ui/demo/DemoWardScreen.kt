package com.hos.rushdpatients.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.ui.ward.PatientCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoWardScreen(onExit: () -> Unit) {
    val patients = remember { demoPatients() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("الوضع التجريبي")
                        Text(
                            "بيانات وهمية · لا اتصال بتليجرام",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "إنهاء العرض التجريبي")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(onClick = {}, label = { Text("عرض فقط") })
                    Text(
                        "جرّب شكل قائمة المرضى والبطاقات. الأسماء نباتات وفواكه عشوائية ولا تُحفظ أي بيانات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(patients, key = { it.id }) { patient ->
                PatientCard(
                    patient = patient,
                    expanded = true,
                    twoColumn = false,
                    doctorNames = emptyMap(),
                    readOnly = true,
                    onClick = {},
                    onDelete = {}
                )
            }
        }
    }
}

private fun demoPatients(): List<Patient> {
    val names = listOf("ياسمين", "زيتون", "رمان", "تفاح", "ورد", "ريحان", "مشمش", "لوز")
        .shuffled()
        .take(4)
    return names.mapIndexed { index, name ->
        Patient(
            id = "demo-$index-$name",
            admittanceNumber = "D-${100 + index}",
            admittanceDate = LocalDate.now().minusDays(index.toLong()),
            gender = if (index % 2 == 0) Gender.FEMALE else Gender.MALE,
            name = name,
            birthDate = LocalDate.of(1985 + index * 5, 1, 1),
            diagnosisType = DiagnosisType.entries[index % DiagnosisType.entries.size],
            initialDiagnosis = listOf(
                "مثال تعليمي لحالة مستقرة",
                "مثال لمراجعة الخطة العلاجية",
                "مثال لتسليم متابعة المناوبة",
                "مثال لحالة تحتاج تنسيق الفريق"
            )[index],
            treatmentPlan = "خطة تجريبية غير طبية — للعرض فقط",
            followUp = "☐ مهمة تجريبية للمناوبة القادمة",
            labs = "Demo = ${index + 1}",
            badges = if (index == 1) {
                listOf(PatientBadge("خطر سقوط", PatientBadgePriority.HIGH))
            } else emptyList(),
            isPriority = index == 2,
            sortOrder = index + 1,
            lastEditedByName = "مستخدم تجريبي"
        )
    }
}
