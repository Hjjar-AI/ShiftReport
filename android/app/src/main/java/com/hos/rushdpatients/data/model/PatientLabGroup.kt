package com.hos.rushdpatients.data.model

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class PatientLabGroup(
    val id: String,
    val title: String = "",
    val date: String? = null,
    val results: String = ""
)

/** Same ordered groups in storage, drafts and the current CSV format. */
object PatientLabGroups {
    private val json = Json { encodeDefaults = true }
    fun requireValid(groups: List<PatientLabGroup>) {
        require(groups.map { it.id }.distinct().size == groups.size && groups.all { it.id.isNotBlank() }) {
            "معرّفات مجموعات التحاليل فارغة أو مكررة"
        }
        groups.forEach { group ->
            group.date?.let { value ->
                require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) { "تاريخ التحاليل غير صالح" }
                val date = LocalDate.parse(value)
                require(date.year in 1..9999 && date.toString() == value) { "تاريخ التحاليل غير صالح" }
            }
        }
    }
    fun encode(groups: List<PatientLabGroup>): String {
        requireValid(groups)
        return json.encodeToString(groups)
    }
    fun decode(value: String): List<PatientLabGroup> =
        (if (value.isBlank()) emptyList() else json.decodeFromString<List<PatientLabGroup>>(value)).also(::requireValid)
    fun heading(group: PatientLabGroup): String =
        listOfNotNull(group.title.takeIf(String::isNotBlank), group.date).joinToString(" • ")
    fun text(groups: List<PatientLabGroup>): String = groups.mapNotNull { group ->
        listOf(heading(group), group.results).filter(String::isNotEmpty).joinToString("\n").takeIf(String::isNotEmpty)
    }.joinToString("\n\n")
    fun fromText(text: String, id: String): List<PatientLabGroup> =
        if (text.isEmpty()) emptyList() else listOf(PatientLabGroup(id = id, results = text))
}
