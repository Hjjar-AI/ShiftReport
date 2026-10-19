"""Encrypted local workspace. Manual receipt resolution requires live client authorization."""
from __future__ import annotations
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import secrets
import sqlite3
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol import BridgeError, is_admin, packed, require

MAGIC = b'SRDESKTOP1'


def local_state_key(directory, project):
    """DPAPI CurrentUser protection; copying an unencrypted project's DB is not portable."""
    require(os.name == 'nt', 'حماية المسودة المحلية تتطلب Windows')
    import ctypes
    from ctypes import wintypes
    class Blob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    def blob(raw):
        buffer = ctypes.create_string_buffer(raw)
        return Blob(len(raw), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte))), buffer
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    crypt.CryptProtectData.argtypes = [ctypes.POINTER(Blob), wintypes.LPCWSTR, ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    crypt.CryptUnprotectData.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    crypt.CryptProtectData.restype = crypt.CryptUnprotectData.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    entropy, keep_entropy = blob(project.encode('ascii'))
    path = directory / (project + '.key')
    def transform(raw, protect):
        source, keep_source = blob(raw)
        result = Blob()
        if protect:
            ok = crypt.CryptProtectData(ctypes.byref(source), 'ShiftReport local workspace', ctypes.byref(entropy), None, None, 1, ctypes.byref(result))
        else:
            ok = crypt.CryptUnprotectData(ctypes.byref(source), None, ctypes.byref(entropy), None, None, 1, ctypes.byref(result))
        require(ok, 'تعذر فتح حماية Windows؛ استخدم حساب Windows الأصلي')
        try:
            return ctypes.string_at(result.data, result.size)
        finally:
            kernel.LocalFree(result.data)
    if not path.exists():
        protected = transform(secrets.token_bytes(32), True)
        try:
            with path.open('xb') as stream:
                stream.write(protected)
                stream.flush()
                os.fsync(stream.fileno())
        except FileExistsError:
            pass
    key = transform(path.read_bytes(), False)
    require(len(key) == 32, 'مفتاح محلي غير صالح')
    return key


def linux_state_key(directory, project, passphrase):
    """Wrap a random workspace key with a user-entered passphrase; never save it."""
    require(isinstance(passphrase, str) and passphrase, 'أدخل كلمة مرور المسودة المحلية')
    path = directory / (project + '.linux-key')
    header = b'SRLINUXKEY'
    aad = project.encode('ascii')
    if path.exists():
        try:
            with path.open('rb') as stream:
                raw = stream.read(256)
            require(len(raw) == len(header) + 16 + 12 + 48 and raw.startswith(header), 'ملف حماية محلي غير صالح')
            offset = len(header)
            salt, nonce = raw[offset:offset+16], raw[offset+16:offset+28]
            wrapping_key = hashlib.pbkdf2_hmac('sha256', passphrase.encode('utf-8'), salt, 600000, 32)
            key = AESGCM(wrapping_key).decrypt(nonce, raw[offset+28:], aad)
            require(len(key) == 32, 'مفتاح محلي غير صالح')
            return key
        except Exception:
            raise BridgeError('تعذر فتح المسودة المحلية؛ كلمة المرور خاطئة أو ملف الحماية تالف') from None
    require(not (directory / (project + '.sqlite3')).exists(),
            'ملف حماية المسودة مفقود؛ استعد ملف .linux-key الأصلي ولا تستبدل قاعدة البيانات')
    require(len(passphrase) >= 10, 'كلمة مرور المسودة المحلية الجديدة يجب أن تكون 10 أحرف على الأقل')
    salt, nonce, key = secrets.token_bytes(16), secrets.token_bytes(12), secrets.token_bytes(32)
    wrapping_key = hashlib.pbkdf2_hmac('sha256', passphrase.encode('utf-8'), salt, 600000, 32)
    raw = header + salt + nonce + AESGCM(wrapping_key).encrypt(nonce, key, aad)
    try:
        descriptor = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError:
        # Do not replace another process's key if it was created during derivation.
        return linux_state_key(directory, project, passphrase)
    return key


class Workspace:
    def __init__(self, directory, client, local_passphrase=None):
        self.locked = False
        self.client = client
        self.local_actor = None
        self.directory = Path(directory)
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.project = client.project_id
        self.zone = client.zone
        if not client.config.get('telegramDataKey'):
            if os.name == 'nt':
                client.local_state_key = local_state_key(self.directory, self.project)
            else:
                client.local_state_key = linux_state_key(self.directory, self.project, local_passphrase)
        self.key = client.baseline_key()
        self.path = self.directory / (self.project + '.sqlite3')
        self.state = {'project': self.project, 'baseline': None, 'draft': None, 'receipts': []}
        self._receipt = None
        if os.name != 'nt':
            try:
                descriptor = os.open(str(self.path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                os.close(descriptor)
            except FileExistsError:
                pass
        with sqlite3.connect(str(self.path)) as db:
            db.execute('CREATE TABLE IF NOT EXISTS workspace (id INTEGER PRIMARY KEY CHECK(id=1), body BLOB NOT NULL)')
            row = db.execute('SELECT body FROM workspace WHERE id=1').fetchone()
        if row:
            self.state = self.decode(row[0])
        if self.state['baseline']:
            client.baseline = copy.deepcopy(self.state['baseline'])
            client.doctors = copy.deepcopy(client.baseline['doctors'])

    def encode(self, state):
        nonce = secrets.token_bytes(12)
        return MAGIC + nonce + AESGCM(self.key).encrypt(nonce, packed(state).encode('utf-8'), self.project.encode('ascii'))

    def decode(self, raw):
        try:
            require(raw.startswith(MAGIC), 'صيغة مسودة غير صالحة')
            offset = len(MAGIC)
            plain = AESGCM(self.key).decrypt(raw[offset:offset+12], raw[offset+12:], self.project.encode('ascii'))
            value = json.loads(plain.decode('utf-8'))
            require(value['project'] == self.project, 'المسودة تخص مشروعا آخر')
            require(isinstance(value['receipts'], list), 'سجل عمليات غير صالح')
            return value
        except Exception:
            raise BridgeError('تعذر فتح المسودة؛ الملف تالف أو يخص مشروعا أو مفتاحا آخر') from None

    def commit(self, **changes):
        updated = copy.deepcopy(self.state)
        updated.update(changes)
        raw = self.encode(updated)
        with sqlite3.connect(str(self.path)) as db:
            db.execute('PRAGMA synchronous=FULL')
            db.execute('INSERT OR REPLACE INTO workspace(id,body) VALUES(1,?)', (raw,))
        self.state = updated

    def read(self, client=None):
        require(not self.locked, 'الجلسة مقفلة')
        require(self.state['draft'] is not None, 'اجلب مناوبة أولا')
        return copy.deepcopy(self.state['draft'])

    def write(self, client, shift, backup=True, downloaded=False):
        require(not self.locked, 'الجلسة مقفلة')
        if downloaded:self.require_downloadable()
        if backup and self.state['draft'] is not None:
            # One encrypted recovery copy before replacing a draft. Never contains credentials.
            self.export(self.path.with_suffix('.previous.srdraft'), replace=True)
        downloads=copy.deepcopy(self.state.get('downloads',{}))
        if downloaded and any(s['meta']['shiftId']==shift['meta']['shiftId'] for s in (client.baseline or {}).get('bundle',[])):
            import time
            downloads[shift['meta']['shiftId']]=dict(at=int(time.time()*1000), snapshot=shift['meta'].get('snapshotId',''))
        changes=dict(baseline=copy.deepcopy(client.baseline), draft=copy.deepcopy(shift), downloads=downloads)
        if downloaded:
            from desktop_protocol import fingerprint
            changes['downloadUndo']=dict(before=copy.deepcopy(self.state.get('draft')),after=fingerprint(shift)) if self.state.get('draft') is not None else None
        self.commit(**changes)

    def undo_download(self, client, expected):
        from desktop_protocol import fingerprint
        from desktop_clinical import event,local_identity
        current=self.read()
        require(self.project==client.project_id,'تغير المشروع')
        undo=self.state.get('downloadUndo')
        require(undo and undo.get('before') is not None,'لا يوجد تنزيل محلي للتراجع عنه')
        require(fingerprint(current)==expected==undo['after'],
                'تغيرت البيانات منذ التنزيل؛ لن يستبدل التراجع تعديلات أو نشرًا لاحقًا')
        self.export(self.path.with_suffix('.previous.srdraft'),replace=True)
        require(fingerprint(self.read())==expected,'تغيرت المسودة أثناء التراجع')
        before=copy.deepcopy(undo['before'])
        self.commit(draft=before,downloadUndo=None,clinicalActivity=self.state.get('clinicalActivity',[])+
                    [event(before['meta']['shiftId'],local_identity(self,client),'download_undone')])

    def has_pending_changes(self):
        """Compare saved clinical/order/roster content with this shift's known baseline."""
        require(not self.locked, 'الجلسة مقفلة')
        if self.state.get('draft') is None:return False
        from desktop_reports import report_content
        from desktop_protocol import fingerprint
        draft=self.read()
        known=next((shift for shift in (self.state.get('baseline') or {}).get('bundle',[])
                    if shift['meta']['shiftId']==draft['meta']['shiftId']),None)
        empty=(not draft['patients'] and not draft['meta'].get('doctors') and
               json.loads(draft['meta'].get('sortSpec') or '{"levels":[]}')=={'levels':[]})
        return not ((known is not None and fingerprint(report_content(known))==fingerprint(report_content(draft))) or
                    (known is None and empty))

    def require_downloadable(self):
        """Match Android: a complete download must not discard saved pending work."""
        require(not self.has_pending_changes(), 'توجد تعديلات محلية غير منشورة؛ راجعها وانشرها قبل جلب نسخة كاملة')

    def start_shift(self, client, shift, expected, recovery_path=None):
        """Replace the view only after preserving explicitly acknowledged pending work."""
        from desktop_protocol import current_date, fingerprint
        require(not self.locked and self.project==client.project_id, 'الجلسة مقفلة أو تغير المشروع')
        def check():
            local=self.state.get('draft')
            require(fingerprint(local)==expected, 'تغيرت المسودة أثناء بدء المناوبة؛ أعد العملية')
            require(shift['meta']['shiftDate']==current_date(self.zone), 'تغير تاريخ المناوبة؛ أعد بدء المناوبة')
            require(local is None or local['meta']['shiftDate']!=shift['meta']['shiftDate'] or not local['patients'],
                    'مناوبة اليوم المحلية تحتوي مرضى؛ لن تُستبدل بنسخ السابقة')
        check()
        if self.has_pending_changes():
            require(recovery_path, 'احفظ التعديلات غير المنشورة في ملف استرداد مستقل قبل بدء المناوبة')
            destination=Path(recovery_path)
            require(destination.resolve()!=self.path.with_suffix('.previous.srdraft').resolve(),
                    'اختر ملف استرداد مستقلاً عن نسخة الاسترداد التلقائية')
            self.export(destination)
            # Reopen and authenticate the saved file before replacing the local view.
            saved=self.decode(destination.read_bytes())
            require(fingerprint(saved['draft'])==expected, 'تعذر التحقق من ملف الاسترداد؛ بقيت المناوبة الحالية محفوظة')
        check()
        self.write(client,shift)

    def save_draft(self, shift):
        require(not self.locked, 'الجلسة مقفلة')
        self.commit(draft=copy.deepcopy(shift))

    def save_clinical_draft(self, client, shift, expected):
        from desktop_clinical import save_clinical
        save_clinical(self,client,shift,expected)

    def report_preferences(self):
        require(not self.locked, 'الجلسة مقفلة')
        return copy.deepcopy(self.state.get('reportPreferences',{}))

    def save_report_preferences(self, value):
        require(not self.locked, 'الجلسة مقفلة')
        self.commit(reportPreferences=copy.deepcopy(value))

    def clinical_activity(self, patient_id=None, shift_id=None):
        from desktop_clinical import local_identity
        require(is_admin(local_identity(self,self.client) or {}), 'مركز النشاط متاح للمدير المسجل محلياً فقط')
        return copy.deepcopy([row for row in self.state.get('clinicalActivity',[])
                              if (patient_id is None or row.get('patientId')==patient_id) and
                                 (shift_id is None or row.get('shiftId')==shift_id)])

    def patient_form_draft(self):
        require(not self.locked, 'الجلسة مقفلة')
        return copy.deepcopy(self.state.get('patientForm'))

    def save_patient_form(self, value):
        require(not self.locked, 'الجلسة مقفلة')
        if value is not None:
            require(self.read()['meta']['shiftId'] == value['shiftId'], 'تغيرت المناوبة؛ احتفظ بالمسودة السابقة')
        self.commit(patientForm=copy.deepcopy(value))

    def deleted_patients(self):
        shift = self.read()
        return copy.deepcopy([item for item in self.state.get('recycle', [])
                              if item['shiftId'] == shift['meta']['shiftId']])

    def restored_completions(self):
        from desktop_clinical import completion_evidence
        return completion_evidence(self)

    def delete_patient(self, patient_id, expected):
        from desktop_protocol import current_date, fingerprint
        shift = self.read()
        require(fingerprint(shift) == expected, 'تغيرت المسودة؛ أعد فتح الحذف')
        require(shift['meta']['shiftDate'] == current_date(self.zone), 'المناوبات السابقة للعرض فقط')
        position = next((i for i, row in enumerate(shift['patients']) if row['externalId'] == patient_id), None)
        require(position is not None, 'المريض لم يعد في المسودة')
        recycled = copy.deepcopy(self.state.get('recycle', []))
        recycled = [item for item in recycled if not (item['shiftId'] == shift['meta']['shiftId'] and
                                                      item['patient']['externalId'] == patient_id)]
        baseline = self.state.get('baseline') or {}
        saved = next((row for saved_shift in baseline.get('bundle', [])
                      if saved_shift['meta']['shiftId'] == shift['meta']['shiftId']
                      for row in saved_shift['patients'] if row['externalId'] == patient_id), {})
        completed = [task for task in json.loads(saved.get('tasksJson') or '[]') if task.get('done')]
        # Preserve locally saved completion identity too, including a reopen/re-complete cycle.
        completed_by_id = {task['id']:task for task in completed}
        completed_local = False
        for entry in self.restored_completions():
            if entry['shiftId']!=shift['meta']['shiftId'] or entry['patientId']!=patient_id:continue
            for task in entry['tasks']:
                if task.get('done'):
                    if entry.get('local'):completed_by_id[task['id']]=task
                    else:completed_by_id.setdefault(task['id'],task)
            completed_local = completed_local or entry.get('local',False)
        recycled.append(dict(shiftId=shift['meta']['shiftId'], position=position, completedTasks=list(completed_by_id.values()),
                             completedLocal=completed_local,patient=shift['patients'].pop(position)))
        from desktop_clinical import event, local_identity
        activity = self.state.get('clinicalActivity',[]) + [event(shift['meta']['shiftId'],local_identity(self,self.client),'patient_deleted',recycled[-1]['patient'])]
        self.commit(draft=shift, recycle=recycled, clinicalActivity=activity)

    def restore_patient(self, patient_id, expected):
        from desktop_protocol import current_date, fingerprint
        shift = self.read()
        require(fingerprint(shift) == expected, 'تغيرت المسودة؛ أعد مراجعة الاستعادة')
        require(shift['meta']['shiftDate'] == current_date(self.zone), 'المناوبات السابقة للعرض فقط')
        require(len(shift['patients']) < 200, 'الحد الأقصى 200 مريض')
        require(not any(row['externalId'] == patient_id for row in shift['patients']),
                'يوجد مريض بالمعرّف نفسه؛ لن تُستبدل بياناته')
        recycled = copy.deepcopy(self.state.get('recycle', []))
        index = next((i for i, item in enumerate(recycled) if item['shiftId'] == shift['meta']['shiftId'] and
                      item['patient']['externalId'] == patient_id), None)
        require(index is not None, 'السجل لم يعد في سلة المحذوفات')
        item = recycled[index]
        # Restore the saved draft exactly, including unpublished task changes.
        # Live doctor assignments and completion stamps are checked during review/publication.
        shift['patients'].insert(min(max(0, item['position']), len(shift['patients'])), item['patient'])
        recycled.pop(index)
        restored = [entry for entry in self.state.get('restored', [])
                    if not (entry['shiftId'] == shift['meta']['shiftId'] and entry['patientId'] == patient_id)]
        restored.append(dict(shiftId=shift['meta']['shiftId'], patientId=patient_id, tasks=item.get('completedTasks', []),local=item.get('completedLocal',False)))
        from desktop_clinical import event, local_identity
        activity = self.state.get('clinicalActivity',[]) + [event(shift['meta']['shiftId'],local_identity(self,self.client),'patient_restored',item['patient'])]
        self.commit(draft=shift, recycle=recycled, restored=restored, clinicalActivity=activity)

    def export(self, path, replace=False):
        require(not self.locked, 'الجلسة مقفلة')
        # Delivery records are not portable/restorable: rollback must not allow duplicate sends.
        state = {'project': self.project, 'baseline': self.state['baseline'], 'draft': self.state['draft'],
                 'recycle': self.state.get('recycle', []), 'restored': self.state.get('restored', []),
                 'localCompletions': self.state.get('localCompletions', []), 'receipts': []}
        path = Path(path)
        require(path.suffix.lower() == '.srdraft', 'اختر ملف .srdraft للمسودة المشفرة')
        raw = self.encode(state)
        if replace:
            temporary = path.with_suffix('.tmp')
            with temporary.open('wb') as stream:
                stream.write(raw); stream.flush(); os.fsync(stream.fileno())
            os.replace(str(temporary), str(path))
        else:
            with path.open('xb') as stream:
                stream.write(raw); stream.flush(); os.fsync(stream.fileno())

    def import_draft(self, path, client):
        require(not self.locked, 'الجلسة مقفلة')
        with Path(path).open('rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024, 'ملف المسودة كبير جدا')
        value = self.decode(raw)
        require(value.get('baseline') is not None and value.get('draft') is not None, 'ملف مسودة فارغ')
        self.export(self.path.with_suffix('.previous.srdraft'), replace=True)
        from desktop_clinical import event, local_identity
        activity = self.state.get('clinicalActivity',[]) + [event(value['draft']['meta']['shiftId'],local_identity(self,client),'draft_restored')]
        self.commit(baseline=value['baseline'], draft=value['draft'], recycle=value.get('recycle', []), restored=value.get('restored', []), localCompletions=value.get('localCompletions', []), clinicalActivity=activity)
        client.baseline = copy.deepcopy(value['baseline'])
        client.doctors = copy.deepcopy(client.baseline['doctors'])

    def receipts(self, status=None, key=None):
        return [(i, copy.deepcopy(row)) for i, row in enumerate(self.state['receipts'])
                if (status is None or row[2] == status) and (key is None or row[1] == key)]

    def check_report_delivery(self, shift_id, chat, topic):
        require(shift_id, 'معرف المناوبة مطلوب قبل الإرسال')
        for _, row in self.receipts(status='UNKNOWN'):
            if row[0] != 'report' or row[4:6] != [str(chat), str(topic or '')]:
                continue
            # Older unresolved rows lack a shift label: keep their destination blocked.
            if len(row) <= 9 or not row[9] or row[9] == shift_id:
                raise BridgeError('نتيجة إرسال سابقة لهذه الوجهة غير مؤكدة؛ احسمها في سجل العمليات قبل أي إرسال جديد، حتى لو تغيرت الصيغة أو النسخة المنشورة')

    def receipt_start(self, key, operation, chat, topic, actor, repeat=False, snapshot=None, report_shift=None):
        from datetime import datetime
        if operation == 'report':
            self.check_report_delivery(report_shift, chat, topic)
        for _, row in self.receipts(key=key):
            require(row[2] == 'CONFIRMED_NOT_SENT' or repeat and row[2] in {'SENT', 'CHECKED_DO_NOT_REPEAT'},
                    'هذه العملية أرسلت أو نتيجتها غير مؤكدة؛ راجع سجل العمليات قبل التكرار')
        rows = copy.deepcopy(self.state['receipts'])
        row = [operation, key, 'UNKNOWN', '', str(chat), str(topic or ''), datetime.now().isoformat(timespec='seconds'),
               'snapshot=' + snapshot if snapshot else 'تحقق من تليجرام قبل الإعادة', actor]
        if operation == 'report': row.append(report_shift)
        rows.append(row)
        self.commit(receipts=rows)
        self._receipt = len(rows) - 1

    def receipt_finish(self, message):
        require(self._receipt is not None, 'سجل العملية مفقود')
        self.confirm_indexed_receipt(self._receipt, message['message_id'])
        self._receipt = None

    def confirm_indexed_receipt(self, index, message_id):
        rows = copy.deepcopy(self.state['receipts'])
        require(rows[index][2] == 'UNKNOWN', 'حالة العملية تغيرت')
        rows[index][2:4] = ['SENT', str(message_id)]
        rows[index][7] = 'تأكيد تليجرام'
        self.commit(receipts=rows)

    def require_receipt_resolution(self, index, client):
        require(not self.locked, 'الجلسة مقفلة')
        require(client.project_id == self.project, 'سجل العمليات يخص مشروعاً آخر')
        actor = client.require_local_identity()
        require(not self.locked, 'الجلسة مقفلة')
        rows = self.state['receipts']
        require(0 <= index < len(rows) and rows[index][2] == 'UNKNOWN', 'حالة العملية تغيرت')
        require(actor.get('uid') == rows[index][8] or is_admin(actor),
                'حسم العملية متاح لمن أرسلها أو لمدير نشط فقط')
        return copy.deepcopy(rows[index])

    def resolve_unknown(self, index, not_sent, client):
        self.require_receipt_resolution(index,client)
        rows = copy.deepcopy(self.state['receipts'])
        rows[index][2] = 'CONFIRMED_NOT_SENT' if not_sent else 'CHECKED_DO_NOT_REPEAT'
        self.commit(receipts=rows)
