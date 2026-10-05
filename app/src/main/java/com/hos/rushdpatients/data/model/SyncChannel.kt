package com.hos.rushdpatients.data.model

enum class SyncChannel(val key: String) {
    CSV("csv"),
    DOCTORS("doctors");

    companion object {
        fun fromKey(key: String): SyncChannel? = values().firstOrNull { it.key == key }
    }
}
