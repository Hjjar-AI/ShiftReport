package com.hos.rushdpatients.sync

import java.util.Locale
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.domain.doctor.DoctorNaming

/**
 * Encodes/decodes the doctors registry in the pipe-and-comma format used by
 * the VBA workbook.
 *
 * Wire format:
 *   #,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId|...@lastId
 *
 * `options` string:
 *   - "a<rank>"  → regular admin at rank <rank>
 *   - "A<rank>"  → permanent admin at rank <rank>
 *   - ""         → regular doctor
 *
 * `id` column is the Telegram user id (numeric), blank if the doctor has not
 * linked an account yet.
 */
object DoctorsRegistryCodec {

    private const val HEADER =
        "#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId"
    private const val ROW_SEP = "|"
    private const val ID_SEP = "@"

    // ---------------- Encoding ----------------

    fun encode(doctors: List<Doctor>): String {
        val body = buildString {
            append(HEADER)
            for (d in doctors) {
                if (d.isDeleted) continue
                append(ROW_SEP)
                append(encodeRow(d))
            }
        }
        val lastId = doctors.asSequence()
            .filter { !it.isDeleted }
            .map { it.rank }
            .filter { it > 0 }
            .maxOrNull()
            ?: 0
        return "$body$ID_SEP$lastId"
    }

    private fun encodeRow(d: Doctor): String {
        require(d.id.isNotBlank() && d.id.none { it in ",|@\n\r\"" }) {
            "معرّف الطبيب غير صالح لصيغة سجل الأطباء"
        }
        require(
            d.supervisorGroupChatId == null ||
                (d.clinicalRole == ClinicalRole.SUPERVISOR && d.supervisorGroupChatId < 0L)
        ) { "معرف مجموعة المشرف غير صالح للطبيب ${d.fullName}" }
        val fields = listOf(
            d.rank.toString(),
            d.id,
            escapeField(d.fullName),
            d.gender.code,
            d.telegramId?.toString().orEmpty(),
            escapeField(d.customTitle.orEmpty()),
            encodeOptions(d),
            d.clinicalRole.code,
            d.supervisorGroupChatId?.toString().orEmpty()
        )
        return fields.joinToString(",")
    }

    private fun encodeOptions(d: Doctor): String {
        if (d.rank <= 0) return ""
        val prefix = if (d.isPermanentAdmin) "A" else "a"
        return "$prefix${d.rank}"
    }

    /**
     * Strip characters that would break the wire format. Users who type
     * these characters into names or titles get them removed silently here,
     * matching VBA behaviour which forbade them at input time.
     */
    private fun escapeField(s: String): String =
        s.replace(",", " ")
            .replace("|", " ")
            .replace("@", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()

    // ---------------- Decoding ----------------

    data class Decoded(
        val doctors: List<Doctor>,
        val lastId: Int
    )

    fun decode(text: String): Decoded {
        val cleaned = text.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return Decoded(emptyList(), 0)

        val atIndex = cleaned.lastIndexOf(ID_SEP)
        val body: String
        val lastId: Int
        if (atIndex >= 0) {
            body = cleaned.substring(0, atIndex)
            lastId = cleaned.substring(atIndex + 1).trim().toIntOrNull() ?: 0
        } else {
            body = cleaned
            lastId = 0
        }

        val rows = body.split(ROW_SEP).filter { it.isNotBlank() }
        if (rows.size < 2) return Decoded(emptyList(), lastId)

        val header = rows.first().split(",").map { it.trim().lowercase(Locale.ROOT) }
        val nameIdx = header.indexOf("name")
        val uidIdx = header.indexOf("uid")
        val genderIdx = header.indexOf("gender")
        val idIdx = header.indexOf("id")
        val titleIdx = header.indexOf("title")
        val optionsIdx = header.indexOf("options")
        val rankIdx = header.indexOf("#")
        val clinicalRoleIdx = header.indexOf("clinicalrole")
        val supervisorGroupChatIdIdx = header.indexOf("supervisorgroupchatid")
        require(listOf(rankIdx, uidIdx, nameIdx, genderIdx, idIdx, titleIdx, optionsIdx,
            clinicalRoleIdx, supervisorGroupChatIdIdx).all { it >= 0 }) {
            "سجل الأطباء لا يحتوي جميع الأعمدة المطلوبة"
        }

        require(header.distinct().size == header.size) { "سجل الأطباء يحتوي أعمدة مكررة" }
        val doctors = rows.drop(1).map { row ->
            val cols = splitRow(row)
            require(cols.size == header.size) { "سجل الأطباء يحتوي صفاً ناقصاً أو غير صالح" }
            val name = cols[nameIdx]
            require(name.isNotBlank()) { "سجل الأطباء يحتوي اسماً فارغاً" }
            val gender = when (cols[genderIdx].uppercase(Locale.ROOT)) {
                "M", "MALE" -> Gender.MALE
                "F", "FEMALE" -> Gender.FEMALE
                else -> error("الجنس غير صالح في سجل الأطباء")
            }
            val telegramId = cols[idIdx].takeIf { it.isNotBlank() }?.toLongOrNull()
            require(cols[idIdx].isBlank() || telegramId != null && telegramId > 0) {
                "هوية تليجرام غير صالحة في سجل الأطباء"
            }
            val title = cols.getOrNull(titleIdx)?.takeIf { it.isNotBlank() }
            val options = cols.getOrNull(optionsIdx).orEmpty()
            val rankText = cols.getOrNull(rankIdx).orEmpty()

            require(options.isBlank() || Regex("[aA][1-9][0-9]*").matches(options)) {
                "خيارات صلاحية المدير غير صالحة في سجل الأطباء"
            }
            val parsedOptions = parseOptions(options)
            val parsedRank = rankText.toIntOrNull() ?: parsedOptions.rank
            require((rankText.isBlank() || rankText.toIntOrNull() != null) && parsedRank >= 0 &&
                (!parsedOptions.isPermanent || parsedRank > 0) &&
                (options.isBlank() || parsedOptions.rank == parsedRank)) { "رتبة المدير غير صالحة" }
            val clinicalRole = ClinicalRole.entries.firstOrNull {
                it.code.equals(cols[clinicalRoleIdx], ignoreCase = true)
            } ?: error("التصنيف السريري غير صالح في سجل الأطباء")
            val supervisorGroup = cols[supervisorGroupChatIdIdx].takeIf { it.isNotBlank() }?.toLongOrNull()
            require(cols[supervisorGroupChatIdIdx].isBlank() || supervisorGroup != null) {
                "معرّف مجموعة المشرف غير صالح"
            }

            val stableId = cols.getOrNull(uidIdx)?.takeIf { it.isNotBlank() }
                ?: DoctorNaming.stableId(name)

            Doctor(
                id = stableId,
                fullName = name,
                firstName = extractFirstName(name),
                lastName = extractLastName(name),
                gender = gender,
                clinicalRole = clinicalRole,
                supervisorGroupChatId = supervisorGroup,
                telegramId = telegramId,
                telegramUsername = null,
                customTitle = title,
                rank = parsedRank,
                isPermanentAdmin = parsedOptions.isPermanent,
                extraOptions = emptySet()
            )
        }

        require(doctors.map { it.id }.distinct().size == doctors.size) {
            "سجل الأطباء يحتوي معرّفات مكررة"
        }
        require(doctors.map { it.fullName.lowercase(Locale.ROOT) }.distinct().size == doctors.size) {
            "سجل الأطباء يحتوي أسماء مكررة"
        }
        val telegramIds = doctors.mapNotNull { it.telegramId }
        require(telegramIds.distinct().size == telegramIds.size) { "سجل الأطباء يحتوي هويات تليجرام مكررة" }
        require(doctors.all {
            it.supervisorGroupChatId == null ||
                (it.clinicalRole == ClinicalRole.SUPERVISOR && it.supervisorGroupChatId < 0L)
        }) {
            "معرفات مجموعات المشرفين غير صالحة"
        }

        return Decoded(doctors, lastId)
    }

    private data class ParsedOptions(val rank: Int, val isPermanent: Boolean)

    private fun parseOptions(s: String): ParsedOptions {
        if (s.isBlank()) return ParsedOptions(0, false)
        val perm = s.startsWith("A")
        val digits = s.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        val rank = digits.toIntOrNull() ?: 0
        return ParsedOptions(rank, perm)
    }

    /**
     * Split a row on commas, honouring double-quoted fields.
     */
    private fun splitRow(row: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in row) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        out += sb.toString().trim()
        return out
    }

    private fun extractFirstName(fullName: String): String {
        val cleaned = fullName.removePrefix("د.").trim()
        return cleaned.substringBefore(' ')
    }

    private fun extractLastName(fullName: String): String {
        val cleaned = fullName.removePrefix("د.").trim()
        val idx = cleaned.indexOf(' ')
        return if (idx < 0) "" else cleaned.substring(idx + 1)
    }
}
