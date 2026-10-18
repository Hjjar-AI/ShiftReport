package com.hos.rushdpatients.data.model

enum class Gender(val code: String) {
    MALE("M"),
    FEMALE("F");

    companion object {
        fun fromCode(code: String?): Gender = when (code?.uppercase()) {
            "F", "FEMALE" -> FEMALE
            else -> MALE
        }
    }
}