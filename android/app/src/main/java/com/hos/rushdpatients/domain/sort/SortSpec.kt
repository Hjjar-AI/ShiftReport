package com.hos.rushdpatients.domain.sort

import kotlinx.serialization.Serializable

@Serializable
enum class SortField {
    GENDER,
    DIAGNOSIS,
    DAYS_OF_ADMITTANCE,
    NAME,
    SUPERVISOR
}

@Serializable
enum class SortDirection {
    ASC,
    DESC
}

@Serializable
data class SortLevel(
    val field: SortField,
    val direction: SortDirection = SortDirection.ASC
)

@Serializable
data class SortSpec(
    val levels: List<SortLevel> = emptyList()
) {
    val isEmpty: Boolean get() = levels.isEmpty()

    companion object {
        val DEFAULT: SortSpec = SortSpec(
            listOf(SortLevel(SortField.NAME, SortDirection.ASC))
        )

        val EMPTY: SortSpec = SortSpec(emptyList())
    }
}