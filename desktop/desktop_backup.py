"""Android-compatible RUSHDBK1 single-shift recovery, separate from device drafts."""
import copy
import hashlib
import os
from pathlib import Path
import secrets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol import BUNDLE, LIMIT, BridgeError, fingerprint, parse_bundle, require
from desktop_csv import patient_csv

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
    from desktop_model import validate_patient
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


def backup_dialog(owner):
    from tkinter import filedialog
    from desktop_widgets import messagebox,simpledialog
    owner.ready(False)
    require(not owner.demo, 'الوضع التجريبي يسمح بحفظ PDF تجريبي فقط')
    shift=owner.store.read();expected=fingerprint(shift)
    password=simpledialog.askstring('نسخة احتياطية مشتركة','كلمة مرور النسخة (8 محارف على الأقل):',show='*',parent=owner.root)
    if password is None:return
    confirmation=simpledialog.askstring('تأكيد كلمة المرور','أعد كلمة مرور النسخة:',show='*',parent=owner.root)
    if confirmation is None:return
    require(password==confirmation,'لم تتطابق كلمتا المرور')
    raw=owner.io(lambda:encode(shift,password))
    path=filedialog.asksaveasfilename(title='نسخة مناوبة مشفرة لـ Android وسطح المكتب',defaultextension='.rpb',filetypes=[('Shift backup','*.rpb')])
    if not path:return
    def save():
        require(fingerprint(owner.store.read())==expected,'تغيرت المسودة؛ أعد إنشاء النسخة')
        destination=Path(path)
        temporary=destination.with_name(destination.name+'.'+secrets.token_hex(6)+'.tmp')
        try:
            with temporary.open('xb') as stream:
                stream.write(raw);stream.flush();os.fsync(stream.fileno())
            require(temporary.read_bytes()==raw,'تعذر التحقق من حفظ النسخة كاملة')
            os.replace(str(temporary),str(destination))
        finally:
            if temporary.exists():temporary.unlink()
    owner.io(save)
    messagebox.showinfo('حُفظت النسخة','نسخة .rpb للمناوبة كاملة. افتح المشروع نفسه وحدّث الأطباء قبل استعادتها على جهاز آخر.')


def restore_dialog(owner,path):
    from desktop_widgets import messagebox,simpledialog
    shift=owner.store.read();expected=fingerprint(shift)
    password=simpledialog.askstring('استعادة نسخة مشتركة','كلمة مرور ملف .rpb:',show='*',parent=owner.root)
    if password is None:return False
    def load():
        with Path(path).open('rb') as stream:raw=stream.read(LIMIT+65)
        candidate=decode(raw,password)
        validate_restore(owner.store,owner.client,candidate,expected)
        return candidate
    candidate=owner.io(load)
    from desktop_model import shift_text
    if not owner.text_dialog('مراجعة استعادة المناوبة',
            'ستستبدل هذه النسخة المسودة المحلية فقط. تبقى إيصالات التسليم محفوظة ويلزم نشر لاحق.\n\n'+
            shift_text(candidate,owner.client.doctors,owner.client.zone),True,'استعادة النسخة المراجعة'):return False
    if not messagebox.askyesno('تأكيد الاستعادة','استبدال بيانات المناوبة المحلية بالنسخة المراجعة؟ تحفظ نسخة استرداد لسطح المكتب أولاً.',default='no'):return False
    owner.io(lambda:restore(owner.store,owner.client,candidate,expected))
    return True
