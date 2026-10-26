"""Encrypted local workspace. Manual receipt resolution requires live client authorization."""
from __future__ import annotations
import copy
import json
import os
from pathlib import Path
import secrets
import sqlite3
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol import BridgeError, packed, require

from desktop_workspace_keys import local_state_key, linux_state_key
import desktop_workspace_receipts

MAGIC = b'SRDESKTOP1'

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
        # Retain old records without creating new audit/completion evidence.
        changes.pop("clinicalActivity", None)
        changes.pop("localCompletions", None)
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
        current=self.read()
        require(self.project==client.project_id,'تغير المشروع')
        undo=self.state.get('downloadUndo')
        require(undo and undo.get('before') is not None,'لا يوجد تنزيل محلي للتراجع عنه')
        require(fingerprint(current)==expected==undo['after'],
                'تغيرت البيانات منذ التنزيل؛ لن يستبدل التراجع تعديلات أو نشرًا لاحقًا')
        self.export(self.path.with_suffix('.previous.srdraft'),replace=True)
        require(fingerprint(self.read())==expected,'تغيرت المسودة أثناء التراجع')
        before=copy.deepcopy(undo['before'])
        self.commit(draft=before,downloadUndo=None)

    def has_pending_changes(self):
        """Compare saved clinical/order/roster content with this shift's known baseline."""
        require(not self.locked, 'الجلسة مقفلة')
        if self.state.get('draft') is None:return False
        from desktop_reports import publication_content
        from desktop_protocol import fingerprint
        draft=self.read()
        known=next((shift for shift in (self.state.get('baseline') or {}).get('bundle',[])
                    if shift['meta']['shiftId']==draft['meta']['shiftId']),None)
        empty=(not draft['patients'] and not draft['meta'].get('doctors') and
               json.loads(draft['meta'].get('sortSpec') or '{"levels":[]}')=={'levels':[]})
        return not ((known is not None and fingerprint(publication_content(known))==fingerprint(publication_content(draft))) or
                    (known is None and empty))

    def require_downloadable(self):
        """Match Android: a complete download must not discard saved pending work."""
        require(not self.has_pending_changes(), 'توجد تعديلات محلية غير منشورة؛ راجعها وانشرها قبل جلب نسخة كاملة')

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

    def patient_form_draft(self):
        require(not self.locked, 'الجلسة مقفلة')
        return copy.deepcopy(self.state.get('patientForm'))

    def save_patient_form(self, value):
        require(not self.locked, 'الجلسة مقفلة')
        if value is not None:
            require(self.read()['meta']['shiftId'] == value['shiftId'], 'تغيرت المناوبة؛ احتفظ بالمسودة السابقة')
        self.commit(patientForm=copy.deepcopy(value))

    def purge_recycle_bin(self, now_epoch_millis=None):
        """Device-local weekly retention; old undated entries receive a full grace week."""
        import time
        require(not self.locked, 'الجلسة مقفلة')
        now=int(time.time()*1000) if now_epoch_millis is None else now_epoch_millis
        cutoff=now-7*24*60*60*1000
        retained=[];removed=0;changed=False
        for entry in copy.deepcopy(self.state.get('recycle', [])):
            deleted_at=entry.get('deletedAtEpochMillis')
            if type(deleted_at) is not int or deleted_at<=0:
                deleted_at=now
                entry['deletedAtEpochMillis']=now
                changed=True
            if deleted_at<=cutoff:
                removed+=1;changed=True
            else:retained.append(entry)
        if changed:self.commit(recycle=retained)
        return removed

    def deleted_patients(self):
        shift = self.read()
        return copy.deepcopy([item for item in self.state.get('recycle', [])
                              if item['shiftId'] == shift['meta']['shiftId']])

    def delete_patient(self, patient_id, expected):
        shift = self.read()
        position = next((i for i, row in enumerate(shift['patients']) if row['externalId'] == patient_id), None)
        require(position is not None, 'المريض لم يعد في المسودة')
        recycled = copy.deepcopy(self.state.get('recycle', []))
        recycled = [item for item in recycled if not (item['shiftId'] == shift['meta']['shiftId'] and
                                                      item['patient']['externalId'] == patient_id)]
        import time
        recycled.append(dict(shiftId=shift['meta']['shiftId'], position=position,
                             deletedAtEpochMillis=int(time.time()*1000), patient=shift['patients'].pop(position)))
        self.commit(draft=shift, recycle=recycled)

    def restore_patient(self, patient_id, expected):
        shift = self.read()
        require(len(shift['patients']) < 200, 'الحد الأقصى 200 مريض')
        require(not any(row['externalId'] == patient_id for row in shift['patients']),
                'يوجد مريض بالمعرّف نفسه؛ لن تُستبدل بياناته')
        recycled = copy.deepcopy(self.state.get('recycle', []))
        index = next((i for i, item in enumerate(recycled) if item['shiftId'] == shift['meta']['shiftId'] and
                      item['patient']['externalId'] == patient_id), None)
        require(index is not None, 'السجل لم يعد في سلة المحذوفات')
        item = recycled[index]
        # Restore the saved draft exactly, including unpublished task changes.
        # Live doctor assignments are checked during review/publication.
        shift['patients'].insert(min(max(0, item['position']), len(shift['patients'])), item['patient'])
        recycled.pop(index)
        self.commit(draft=shift, recycle=recycled)

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
        self.commit(baseline=value['baseline'], draft=value['draft'], recycle=value.get('recycle', []), restored=value.get('restored', []))
        client.baseline = copy.deepcopy(value['baseline'])
        client.doctors = copy.deepcopy(client.baseline['doctors'])

    def receipts(self, status=None, key=None):
        return desktop_workspace_receipts.receipts(self, status, key)

    def check_report_delivery(self, shift_id, chat, topic):
        return desktop_workspace_receipts.check_report_delivery(self, shift_id, chat, topic)

    def receipt_start(self, key, operation, chat, topic, actor, repeat=False, snapshot=None, report_shift=None):
        return desktop_workspace_receipts.receipt_start(self, key, operation, chat, topic, actor, repeat, snapshot, report_shift)

    def receipt_finish(self, message):
        return desktop_workspace_receipts.receipt_finish(self, message)

    def confirm_indexed_receipt(self, index, message_id):
        return desktop_workspace_receipts.confirm_indexed_receipt(self, index, message_id)

    def require_receipt_resolution(self, index, client):
        return desktop_workspace_receipts.require_receipt_resolution(self, index, client)

    def resolve_unknown(self, index, not_sent, client):
        return desktop_workspace_receipts.resolve_unknown(self, index, not_sent, client)
