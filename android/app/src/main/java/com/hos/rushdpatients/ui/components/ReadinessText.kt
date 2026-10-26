package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportReadiness

fun readinessWarnings(uiText: UiStrings, patients: List<Patient>): List<String> =
    ReportReadiness.incompletePatientIds(patients).let { ids ->
        if (ids.isEmpty()) emptyList() else listOf(uiText(R.string.readiness_incomplete_patients, uiNumber(ids.size)))
    }
