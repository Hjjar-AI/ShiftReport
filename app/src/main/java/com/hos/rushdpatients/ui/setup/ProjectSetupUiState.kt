package com.hos.rushdpatients.ui.setup

enum class ProjectSetupMode { CREATE, JOIN }

data class ProjectSetupUiState(
    val mode: ProjectSetupMode = ProjectSetupMode.CREATE,
    val hospitalName: String = "",
    val botToken: String = "",
    val chatId: String = "",
    val reportsTopicId: String = "",
    val announcementsTopicId: String = "",
    val csvTopicId: String = "",
    val doctorsTopicId: String = "",
    val adminName: String = "",
    val adminTelegramId: String = "",
    val adminGenderCode: String = "M",
    val adminClinicalRoleCode: String = "RESIDENT",
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null
)
