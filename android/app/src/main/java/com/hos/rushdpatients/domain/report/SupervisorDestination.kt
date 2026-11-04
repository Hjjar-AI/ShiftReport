package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor

/** Topic-only supervisor routes use the residents' group, never its general-report topic. */
data class SupervisorDestination(val chatId: Long, val topicId: Long?) {
    companion object {
        fun validate(doctor: Doctor) {
            require(doctor.supervisorGroupChatId == null ||
                doctor.clinicalRole == ClinicalRole.SUPERVISOR && doctor.supervisorGroupChatId < 0L) {
                "معرّف مجموعة المشرف يجب أن يكون عدداً سالباً ولمشرف فقط"
            }
            val topics = doctor.options.supervisorTopics
            require(topics.size <= 1 && topics.all {
                doctor.clinicalRole == ClinicalRole.SUPERVISOR && it.toLongOrNull()?.let { id -> id > 0L } == true
            }) { "معرّف موضوع المشرف يجب أن يكون عدداً موجباً واحداً ولمشرف فقط" }
        }

        fun resolve(doctor: Doctor, residentsChatId: Long): SupervisorDestination? {
            validate(doctor)
            if (doctor.clinicalRole != ClinicalRole.SUPERVISOR ||
                doctor.supervisorGroupChatId == null && doctor.supervisorTopicId == null) return null
            val chatId = doctor.supervisorGroupChatId ?: residentsChatId
            require(chatId < 0L) { "مجموعة المقيمين غير صالحة" }
            return SupervisorDestination(chatId, doctor.supervisorTopicId)
        }
    }
}
