"""Android-compatible RUSHDBK1 single-shift recovery, separate from device drafts."""
import copy
import hashlib
import os
from pathlib import Path
import secrets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from .protocol import BUNDLE, LIMIT, BridgeError, fingerprint, parse_bundle, require
from .csv import patient_csv

MAGIC=b'RUSHDBK1'


def key(password,salt):
    require(isinstance(password,str) and len(password.encode('utf-16-le'))//2>=8,
            'كلمة مرور النسخة الاحتياطية يجب ألا تقل عن 8 محارف')
    return hashlib.pbkdf2_hmac('sha256',password.encode('utf-8'),salt,210_000,dklen=32)


def encode(shift,password):
    salt,nonce=secrets.token_bytes(16),secrets.token_bytes(12)
    plain=patient_csv(shift)
    parse_bundle((BUNDLE+'\n').encode()+plain)
    return MAGIC+salt+nonce+AESGCM(key(password,salt)).encrypt(nonce,plain,None)


def decode(raw,password):
    require(len(raw)<=LIMIT+64 and len(raw)>52 and raw.startswith(MAGIC), 'اختر نسخة .rpb صالحة من Android أو سطح المكتب')
    secret=key(password,raw[8:24])
    try:
        plain=AESGCM(secret).decrypt(raw[24:36],raw[36:],None)
        return parse_bundle((BUNDLE+'\n').encode()+plain)[0]
    except Exception:
        raise BridgeError('كلمة المرور خاطئة أو ملف النسخة تالف أو غير صالح') from None


def validate_restore(workspace,client,shift,expected):
    require(not workspace.locked and not getattr(client,'demo',False), 'الاستعادة غير متاحة في هذه الجلسة')
    require(workspace.project==client.project_id, 'النسخة لا تخص المشروع المفتوح')
    current=workspace.read()
    require(fingerprint(current)==expected, 'تغيرت المسودة؛ أعد مراجعة الاستعادة')
    # RUSHDBK1 has no project identifier: bind it to a known shift in this project.
    require(all(shift['meta'][k]==current['meta'][k] for k in ('shiftId','shiftDate')),
            'اختر أولاً المناوبة نفسها من المشروع؛ لن تخلط نسخة مناوبة أو مشروع آخر بالمسودة')
    doctors=client.doctors
    require(all(uid in doctors for uid in filter(None,shift['meta'].get('doctors','').split(','))),
            'تحتاج النسخة إلى أطباء غير موجودين؛ حدّث سجل الأطباء أولاً')
    from .model import validate_patient
    for row in shift['patients']:
        validate_patient(row,doctors,client.zone)
    require(len(shift['patients'])<=200, 'عدد المرضى أكبر من الحد')


def restore(workspace,client,shift,expected):
    validate_restore(workspace,client,shift,expected)
    candidate=copy.deepcopy(shift)
    workspace.export(workspace.path.with_suffix('.previous.srdraft'),replace=True)
    validate_restore(workspace,client,candidate,expected)
    form=workspace.state.get('patientForm')
    workspace.commit(draft=candidate,
                     patientForm=form if form and form['shiftId']!=candidate['meta']['shiftId'] else None)




