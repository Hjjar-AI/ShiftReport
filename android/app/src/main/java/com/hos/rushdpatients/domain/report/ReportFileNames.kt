package com.hos.rushdpatients.domain.report

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Public artifact names use the captured report day, never device time or internal route IDs. */
object ReportFileNames {
    fun pdf(date: LocalDate, route: String = "general", draft: Boolean = false,
        supervisorName: String? = null, demo: Boolean = false): String = buildString {
        append(if (route == "assignments") "Assignments" else "Report")
        append('_').append(date.format(DateTimeFormatter.BASIC_ISO_DATE))
        when {
            route == "unassigned" -> append("_Unassigned")
            route.startsWith("supervisor:") -> {
                val name = supervisorName.orEmpty().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                    .trim('_', '-').take(60).ifBlank { "Unnamed" }
                append("_Supervisor_").append(name)
            }
        }
        if (draft) append("_DRAFT")
        if (demo) append("_DEMO")
        append(".pdf")
    }
}
