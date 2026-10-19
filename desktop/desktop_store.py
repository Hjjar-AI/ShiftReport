"""Encrypted, transactional local workspace. No network or Tk access."""
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
from desktop_protocol import BridgeError, packed, require

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
        require(self.state['draft'] is not None, 'اجلب مناوبة أولا')
        return copy.deepcopy(self.state['draft'])

    def write(self, client, shift, backup=True):
        if backup and self.state['draft'] is not None:
            # One encrypted recovery copy before replacing a draft. Never contains credentials.
            self.export(self.path.with_suffix('.previous.srdraft'), replace=True)
        self.commit(baseline=copy.deepcopy(client.baseline), draft=copy.deepcopy(shift))

    def save_draft(self, shift):
        self.commit(draft=copy.deepcopy(shift))

    def deleted_patients(self):
        shift = self.read()
        return copy.deepcopy([item for item in self.state.get('recycle', [])
                              if item['shiftId'] == shift['meta']['shiftId']])

    def restored_completions(self):
        return copy.deepcopy(self.state.get('restored', []))

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
        # Retain already recovered history when a restored patient is deleted again.
        if not saved:
            completed = next((entry['tasks'] for entry in self.state.get('restored', [])
                              if entry['shiftId'] == shift['meta']['shiftId'] and entry['patientId'] == patient_id), [])
        recycled.append(dict(shiftId=shift['meta']['shiftId'], position=position, completedTasks=completed,
                             patient=shift['patients'].pop(position)))
        self.commit(draft=shift, recycle=recycled)

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
        restored.append(dict(shiftId=shift['meta']['shiftId'], patientId=patient_id, tasks=item.get('completedTasks', [])))
        self.commit(draft=shift, recycle=recycled, restored=restored)

    def export(self, path, replace=False):
        # Delivery records are not portable/restorable: rollback must not allow duplicate sends.
        state = {'project': self.project, 'baseline': self.state['baseline'], 'draft': self.state['draft'],
                 'recycle': self.state.get('recycle', []), 'restored': self.state.get('restored', []), 'receipts': []}
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
        with Path(path).open('rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024, 'ملف المسودة كبير جدا')
        value = self.decode(raw)
        require(value.get('baseline') is not None and value.get('draft') is not None, 'ملف مسودة فارغ')
        self.export(self.path.with_suffix('.previous.srdraft'), replace=True)
        self.commit(baseline=value['baseline'], draft=value['draft'], recycle=value.get('recycle', []), restored=value.get('restored', []))
        client.baseline = copy.deepcopy(value['baseline'])
        client.doctors = copy.deepcopy(client.baseline['doctors'])

    def receipts(self, status=None, key=None):
        return [(i, copy.deepcopy(row)) for i, row in enumerate(self.state['receipts'])
                if (status is None or row[2] == status) and (key is None or row[1] == key)]

    def receipt_start(self, key, operation, chat, topic, actor, repeat=False, snapshot=None):
        from datetime import datetime
        for _, row in self.receipts(key=key):
            require(row[2] == 'CONFIRMED_NOT_SENT' or repeat and row[2] == 'SENT',
                    'هذه العملية أرسلت أو نتيجتها غير مؤكدة؛ راجع سجل العمليات قبل التكرار')
        rows = copy.deepcopy(self.state['receipts'])
        rows.append([operation, key, 'UNKNOWN', '', str(chat), str(topic or ''), datetime.now().isoformat(timespec='seconds'),
                     'snapshot=' + snapshot if snapshot else 'تحقق من تليجرام قبل الإعادة', actor])
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

    def resolve_unknown(self, index, not_sent):
        rows = copy.deepcopy(self.state['receipts'])
        require(rows[index][2] == 'UNKNOWN', 'حالة العملية تغيرت')
        rows[index][2] = 'CONFIRMED_NOT_SENT' if not_sent else 'CHECKED_DO_NOT_REPEAT'
        self.commit(receipts=rows)
