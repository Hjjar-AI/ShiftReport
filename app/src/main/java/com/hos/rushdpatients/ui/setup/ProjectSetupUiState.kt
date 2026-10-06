package com.hos.rushdpatients.ui.setup

enum class ProjectSetupMode { DEMO, JOIN, CREATE }

data class ProjectSetupUiState(
    val mode: ProjectSetupMode = ProjectSetupMode.JOIN,
    val hospitalName: String = "",
    val botToken: String = "",
    val chatId: String = "",
    val reportsTopicId: String = "",
    val announcementsTopicId: String = "",
    val csvTopicId: String = "",
    val doctorsTopicId: String = "",
    val provisioningPassphrase: String = "",
    val importedProvisioning: Boolean = false,
    val adminName: String = "",
    val adminTelegramId: String = "",
    val adminGenderCode: String = "M",
    val adminClinicalRoleCode: String = "RESIDENT",
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null
)
