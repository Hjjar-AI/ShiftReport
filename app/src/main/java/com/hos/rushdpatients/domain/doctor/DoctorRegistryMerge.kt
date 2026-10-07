package com.hos.rushdpatients.domain.doctor

import java.util.Locale
import com.hos.rushdpatients.data.model.Doctor

/** Only portable registry fields participate; PINs, aliases and timestamps stay device-local. */
enum class DoctorMergeChoice { LOCAL, REMOTE }

data class DoctorRegistryConflict(
    val key: String,
    val doctorName: String,
    val fieldLabel: String,
    val localValue: String,
    val remoteValue: String
)

data class DoctorRegistryMergeResult(
    val doctors: List<Doctor>,
    val conflicts: List<DoctorRegistryConflict>
)

object DoctorRegistryMerge {
    fun merge(
        base: List<Doctor>,
        local: List<Doctor>,
        remote: List<Doctor>,
        baseKnown: Boolean = true,
        choices: Map<String, DoctorMergeChoice> = emptyMap(),
        protectedDeletionIds: Set<String> = emptySet()
    ): DoctorRegistryMergeResult {
        val bases = base.associateBy { it.id }
        val locals = local.associateBy { it.id }
        val remotes = remote.associateBy { it.id }
        val conflicts = mutableListOf<DoctorRegistryConflict>()
        val merged = mutableListOf<Doctor>()
        fun key(id: String, field: String) = "${id.length}:$id:$field"
        fun <T> select(id: String, name: String, field: String, label: String, b: T?, l: T, r: T,
                       display: (T) -> String = { it.toString() }): T {
            if (l == r) return l
            if (baseKnown && bases[id] != null && l == b) return r
            if (baseKnown && bases[id] != null && r == b) return l
            val conflictKey = key(id, field)
            return when (choices[conflictKey]) {
                DoctorMergeChoice.LOCAL -> l
                DoctorMergeChoice.REMOTE -> r
                null -> {
                    conflicts += DoctorRegistryConflict(conflictKey, name, label, display(l), display(r))
                    l
                }
            }
        }
        (bases.keys + locals.keys + remotes.keys).sorted().forEach { id ->
            val b = bases[id]
            val l = locals[id]
            val r = remotes[id]
            if (l == null || r == null) {
                val survivor = l ?: r
                val unchanged = survivor != null && b != null && sameRecord(survivor, b)
                val selected = when {
                    l == null && r == null -> null
                    baseKnown && b == null -> survivor // An addition on one side.
                    baseKnown && unchanged && id !in protectedDeletionIds -> null // An unchanged record deleted on the other side.
                    else -> {
                        // Presence must be chosen even when the surviving record equals its base.
                        val conflictKey = key(id, "presence")
                        when (choices[conflictKey]) {
                            DoctorMergeChoice.LOCAL -> l
                            DoctorMergeChoice.REMOTE -> r
                            null -> {
                                conflicts += DoctorRegistryConflict(
                                    conflictKey, survivor?.fullName.orEmpty(),
                                    if (id in protectedDeletionIds) "حذف طبيب محمي أو مسؤول عن مرضى" else "حذف مقابل وجود أو تعديل",
                                    recordLabel(l), recordLabel(r)
                                )
                                l
                            }
                        }
                    }
                }
                selected?.let(merged::add)
            } else {
                val name = select(id, l.fullName, "name", "الاسم", b?.fullName, l.fullName, r.fullName)
                val gender = select(id, name, "gender", "الجنس", b?.gender, l.gender, r.gender) {
                    if (it.code == "F") "أنثى" else "ذكر"
                }
                val clinical = select(id, name, "clinical", "الدور السريري ومجموعة المشرف",
                    b?.let { it.clinicalRole to it.supervisorGroupChatId },
                    l.clinicalRole to l.supervisorGroupChatId, r.clinicalRole to r.supervisorGroupChatId) {
                    "${it.first.arabicLabel} / ${it.second ?: "دون مجموعة"}"
                }
                val identity = select(id, name, "telegram", "هوية تليجرام", b?.telegramId,
                    l.telegramId, r.telegramId) { it?.toString() ?: "غير مرتبط" }
                val title = select(id, name, "title", "اللقب", b?.customTitle, l.customTitle, r.customTitle) {
                    it ?: "دون لقب"
                }
                val admin = select(id, name, "admin", "رتبة المدير والصلاحية الدائمة",
                    b?.let { it.rank to it.isPermanentAdmin }, l.rank to l.isPermanentAdmin,
                    r.rank to r.isPermanentAdmin) { "رتبة ${it.first} / دائم: ${if (it.second) "نعم" else "لا"}" }
                merged += l.copy(
                    fullName = name,
                    firstName = DoctorNaming.extractFirstName(name),
                    lastName = DoctorNaming.extractLastName(name),
                    gender = gender,
                    clinicalRole = clinical.first,
                    supervisorGroupChatId = clinical.second,
                    telegramId = identity,
                    customTitle = title,
                    rank = admin.first,
                    isPermanentAdmin = admin.second
                )
            }
        }
        // Two devices may add different IDs for the same person. Choose the retained record explicitly.
        fun resolveDuplicates(field: String, label: String, value: (Doctor) -> String?) {
            merged.toList().groupBy(value).filterKeys { it != null }.values.filter { it.size > 1 }
                .forEach { group ->
                    var retained = group.sortedWith(compareByDescending<Doctor> { it.id in locals }.thenBy { it.id }).first()
                    group.filter { it.id != retained.id }.forEach { other ->
                        val conflictKey = key(retained.id, "$field:${other.id}")
                        when (choices[conflictKey]) {
                            DoctorMergeChoice.LOCAL -> merged.removeAll { it.id == other.id }
                            DoctorMergeChoice.REMOTE -> {
                                merged.removeAll { it.id == retained.id }
                                retained = other
                            }
                            null -> conflicts.add(DoctorRegistryConflict(
                                conflictKey, retained.fullName, label,
                                "الاحتفاظ بـ ${retained.fullName} (${retained.id}) وحذف السجل الآخر",
                                "الاحتفاظ بـ ${other.fullName} (${other.id}) وحذف السجل الآخر"
                            ))
                        }
                    }
                }
        }
        resolveDuplicates("duplicate_name", "سجلان للاسم نفسه", { it.fullName.lowercase(Locale.ROOT) })
        resolveDuplicates("duplicate_telegram", "سجلان لهوية تليجرام نفسها", { it.telegramId?.toString() })
        merged.filter { it.rank > 0 }.groupBy { it.rank }.values.filter { it.size > 1 }.forEach { group ->
            val ids = group.map { it.id }.sorted()
            val localOwner = group.firstOrNull { locals[it.id]?.rank == it.rank } ?: group.first()
            val remoteOwner = group.firstOrNull { remotes[it.id]?.rank == it.rank && it.id != localOwner.id }
                ?: group.first { it.id != localOwner.id }
            val conflictKey = "rank:${group.first().rank}:${ids.joinToString(":") }"
            fun label(owner: Doctor) = "${owner.fullName} يحتفظ بالرتبة؛ ${group.filter { it.id != owner.id }.joinToString("، ") { it.fullName }} يصبح بلا صلاحية مدير"
            when (choices[conflictKey]) {
                null -> conflicts += DoctorRegistryConflict(
                    conflictKey, group.joinToString("، ") { it.fullName }, "تعارض إسناد رتبة المدير",
                    label(localOwner), label(remoteOwner)
                )
                else -> {
                    val owner = if (choices[conflictKey] == DoctorMergeChoice.LOCAL) localOwner else remoteOwner
                    group.filter { it.id != owner.id }.forEach { other ->
                        val index = merged.indexOfFirst { it.id == other.id }
                        merged[index] = other.copy(rank = 0, isPermanentAdmin = false)
                    }
                }
            }
        }
        return DoctorRegistryMergeResult(merged, conflicts)
    }

    fun sameRecord(a: Doctor, b: Doctor): Boolean =
        a.id == b.id && a.fullName == b.fullName && a.gender == b.gender &&
            a.clinicalRole == b.clinicalRole && a.supervisorGroupChatId == b.supervisorGroupChatId &&
            a.telegramId == b.telegramId && a.customTitle == b.customTitle &&
            a.rank == b.rank && a.isPermanentAdmin == b.isPermanentAdmin

    private fun recordLabel(doctor: Doctor?): String = doctor?.let {
        "${it.fullName}؛ ${it.clinicalRole.arabicLabel}؛ تليجرام ${it.telegramId ?: "غير مرتبط"}؛ رتبة ${it.rank}"
    } ?: "محذوف"
}
