"""Encrypted, device-local recovery of an unfinished patient form; never published."""
import copy
from desktop_widgets import messagebox
from desktop_protocol import require
from desktop_clinical import local_identity


def recover_form(owner, source, is_new):
    pending = owner.io(owner.store.patient_form_draft)
    if not pending:return copy.deepcopy(source), copy.deepcopy(source), is_new
    current = owner.store.read()
    choice = messagebox.askyesnocancel('مسودة مريض غير محفوظة',
        'توجد مسودة إدخال غير محفوظة.\nنعم: استئناف المسودة.\nلا: حذف المسودة وفتح المريض المطلوب.\nإلغاء: إبقاء المسودة دون فتح التحرير.', default='cancel')
    if choice is None:return None
    if not choice:
        owner.io(lambda:owner.store.save_patient_form(None))
        return copy.deepcopy(source), copy.deepcopy(source), is_new
    require(pending['shiftId']==current['meta']['shiftId'],
            'المسودة تخص مناوبة أخرى؛ اختر تلك المناوبة أولاً أو احذف المسودة صراحةً')
    actor = (local_identity(owner.store,owner.client) or {}).get('uid')
    require(not pending.get('actor') or pending['actor']==actor, 'المسودة تخص مستخدماً محلياً آخر؛ سجل دخوله لاستئنافها')
    return copy.deepcopy(pending['patient']), copy.deepcopy(pending['expected']), pending['isNew']


def save_form(owner, shift_id, patient, expected, is_new):
    actor = (local_identity(owner.store,owner.client) or {}).get('uid')
    pending = owner.store.patient_form_draft()
    require(not pending or not pending.get('actor') or pending['actor']==actor,
            'مسودة الإدخال تخص مستخدماً آخر أو انتهت جلسته؛ سجل دخوله أو احذف المسودة صراحةً')
    owner.store.save_patient_form(dict(shiftId=shift_id, patient=copy.deepcopy(patient), expected=copy.deepcopy(expected),
                                       isNew=is_new, actor=actor))


def resume_patient(owner):
    from desktop_forms import edit_patient
    owner.editable()
    pending = owner.io(owner.store.patient_form_draft)
    require(pending is not None, 'لا توجد مسودة مريض غير محفوظة')
    edit_patient(owner, pending['patient'], pending['isNew'])
