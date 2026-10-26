package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hos.rushdpatients.data.model.Patient

internal sealed interface WardDialog {
    class Add(val patients: List<Patient>) : WardDialog {
        val form = PatientFormStateStore()
        var position by mutableStateOf("")
    }
    class Edit(val patient: Patient) : WardDialog { val session = EditPatientState(patient) }
    class Copy(val source: Patient) : WardDialog {
        val initial = newPatientCopy(source)
        val form = PatientFormStateStore()
    }
    class Delete(val patient: Patient) : WardDialog
}

/** Exactly one clinical dialog with a retained draft per ward session. */
internal class WardEditorState {
    var dialog by mutableStateOf<WardDialog?>(null)
        private set
    val addForm get() = (dialog as WardDialog.Add).form
    val insertionPatients get() = (dialog as WardDialog.Add).patients
    var insertionPosition: String
        get() = (dialog as WardDialog.Add).position
        set(value) { (dialog as? WardDialog.Add)?.position = value }
    var showAdd: Boolean
        get() = dialog is WardDialog.Add
        set(value) { if (!value && dialog is WardDialog.Add) dialog = null }
    fun openAdd(patients: List<Patient>) {
        if (dialog !is WardDialog.Add) dialog = WardDialog.Add(patients.toList())
    }
    var editTarget: Patient?
        get() = (dialog as? WardDialog.Edit)?.patient
        set(value) {
            if (value != null) { if (editTarget != value) dialog = WardDialog.Edit(value) }
            else if (dialog is WardDialog.Edit) dialog = null
        }
    val editSession get() = (dialog as? WardDialog.Edit)?.session
    var copyTarget: Patient?
        get() = (dialog as? WardDialog.Copy)?.source
        set(value) {
            if (value != null) { if (copyTarget != value) dialog = WardDialog.Copy(value) }
            else if (dialog is WardDialog.Copy) dialog = null
        }
    val copyInitial get() = (dialog as? WardDialog.Copy)?.initial
    val copyForm get() = (dialog as? WardDialog.Copy)?.form
    var deleteTarget: Patient?
        get() = (dialog as? WardDialog.Delete)?.patient
        set(value) {
            if (value != null) dialog = WardDialog.Delete(value)
            else if (dialog is WardDialog.Delete) dialog = null
        }
    fun clear() { dialog = null }
}
