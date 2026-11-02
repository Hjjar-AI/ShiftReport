"""Browser-owned sessions over current-format clinical services; no native UI imports."""
import copy
import hashlib
import io
import json
import secrets
import threading
import time
import uuid
from pathlib import Path

from .core.protocol import Client, BridgeError, require, packed, fingerprint, current_date, is_admin
from .core.store import Workspace as EncryptedStore
from .core.model import new_patient, validate_patient, badges_encode
from .core.labs import apply as apply_labs, read_groups
from .core.tasks import read_tasks, validate as validate_tasks
from .core.sort import ordered
from .core.access import LocalAccess


PROJECT_LOCKS = {}
PROJECT_LOCKS_LOCK = threading.RLock()


def project_lock(project):
    with PROJECT_LOCKS_LOCK:
        return PROJECT_LOCKS.setdefault(project, threading.RLock())


class DemoClient:
    demo = True
    zone = 'Asia/Damascus'
    project_id = 'demo'
    _actor = 'demo-doctor-3'
    _verified_telegram = None
    _expires = float('inf')
    baseline = None
    config = {'hospitalName': 'بيانات تجريبية', 'telegramDataKey': ''}

    def __init__(self):
        self.doctors = {}
        for i, name in enumerate(('أرز', 'بلوط', 'صنوبر', 'زيتون', 'صفصاف', 'نخيل')):
            uid = 'demo-doctor-' + str(i)
            self.doctors[uid] = dict(uid=uid, name='د. '+name, gender='M' if i % 2 == 0 else 'F',
                clinicalRole='RESIDENT' if i < 3 else 'SUPERVISOR', telegram_id=None,
                destination=None, title='', options='A1' if i == 3 else '',
                teamSupervisorId='demo-doctor-'+str(i+3) if i < 3 else '',
                **{'#': '1' if i == 3 else '0', 'id': '', 'supervisorGroupChatId': ''})

    def require_local_admin(self):
        return self.doctors[self._actor]

    def authorize(self, doctors):
        return doctors[self._actor]


class MemoryStore(EncryptedStore):
    def __init__(self, client):
        from .demo import sample_patients
        self.client = client
        self.project = 'demo'
        self.zone = client.zone
        self.locked = False
        self.local_actor = client._actor
        self._receipt = None
        patients = []
        from .core.protocol import COLUMNS
        for source in sample_patients():
            row = {key: source.get(key, '') for key in COLUMNS}
            row['externalId'] = source['id']
            patients.append(row)
        shift = dict(meta=dict(schemaVersion='8', shiftDate=current_date(client.zone),
            shiftId=str(uuid.uuid4()), doctors=','.join(client.doctors), sortSpec='{"levels":[]}',
            shiftRevision='0', snapshotId='', baseSnapshotId='', deviceId=''), patients=patients)
        self.state = dict(project='demo', baseline=None, draft=shift, receipts=[])
        self.published = copy.deepcopy(shift)

    def commit(self, **changes):
        self.state = dict(copy.deepcopy(self.state), **copy.deepcopy(changes))

    def export(self, *args, **kwargs):
        require(False, 'الوضع التجريبي يحفظ PDF وهمياً فقط؛ البيانات الأخرى تبقى في الذاكرة')


class BrowserWorkspace:
    def __init__(self):
        self.csrf = secrets.token_hex(32)
        self.last_used = time.monotonic()
        self.lock = threading.RLock()
        self.client = DemoClient()
        self.store = MemoryStore(self.client)
        self.demo = True
        self.unlocked = True
        self.actor = self.client._actor
        self.reviews = {}
        self.forms = {}
        self.last_activity = time.time()
        self.notice = ''
        self.creator = None
        self.pending_config = None
        self.creation_store = None
        self.attempt = None
        self.print_accent = 'VANILLA'

    def ready(self, online=False, admin=False):
        require(self.unlocked and not self.store.locked, 'الجلسة مقفلة؛ افتحها من صفحة الهوية')
        if not self.demo:
            actor = self.client.doctors.get(self.actor)
            require(actor is not None, 'أثبت هوية الطبيب أولاً')
            if online:
                actor = self.client.require_local_identity()
                require(self.client._actor==self.actor and actor.get('uid')==self.actor,
                        'تغيرت هوية المنفذ؛ أعد تسجيل الدخول')
            else:
                record = self.store.state.get('localAccess', {}).get('pins', {}).get(self.actor)
                linked = record and record['telegram'] == actor.get('telegram_id')
                live = self.client._actor == self.actor and time.time() < self.client._expires and actor.get('telegram_id') == self.client._verified_telegram
                require(linked or live, 'أثبت الهوية أو افتح PIN المسجل لهذا الجهاز')
        else:
            actor = self.client.doctors[self.actor]
        if admin:
            require(is_admin(actor), 'هذا الإجراء متاح للمدير فقط')
        self.last_activity = time.time()
        return actor

    def attach(self, config, directory, passphrase=''):
        client = Client(copy.deepcopy(config))
        mutex = project_lock(client.project_id)
        with mutex:
            store = EncryptedStore(directory, client, passphrase or None)
            from .core.destinations import load_destination
            load_destination(store, client)
        self.client, self.store, self.lock = client, store, mutex
        self.client.require_local_admin=self.local_admin
        self.demo, self.unlocked, self.actor = False, False, None
        self.forms.clear()
        self.reviews.clear()

    def local_admin(self):
        # A locally enrolled PIN can authorize a local names-only PDF; it never
        # populates Client's separate live Telegram identity/session fields.
        return self.ready(admin=True)

    def refresh(self):
        if not self.demo:
            import sqlite3
            from contextlib import closing
            with closing(sqlite3.connect(str(self.store.path))) as db:
                row = db.execute('SELECT body FROM workspace WHERE id=1').fetchone()
            if row:
                self.store.state = self.store.decode(row[0])
                baseline = self.store.state.get('baseline')
                if baseline:
                    self.client.baseline = copy.deepcopy(baseline)
                    self.client.doctors = copy.deepcopy(baseline['doctors'])
            if self.unlocked and time.time() - self.last_activity > LocalAccess(self.store, self.client).timeout():
                self.lock_session()
            self.store.locked = not self.unlocked
            self.store.local_actor = self.actor if self.unlocked else None

    def lock_session(self):
        self.unlocked = False
        self.store.locked = True
        self.client._actor = None
        self.client._verified_telegram = None
        self.client._expires = 0
        self.reviews.clear()
        self.forms.clear()

    def snapshot(self):
        self.ready()
        shift = self.store.read()
        rows = []
        for row in shift['patients']:
            p = copy.deepcopy(row)
            p['id'] = p['externalId']
            p['tasks'] = read_tasks(row)
            p['lab_groups'] = read_groups(row)
            from .core.model import badges_decode
            p['badges'] = [dict(text=text, priority=level) for text, level in badges_decode(row.get('warningDetails', ''))]
            rows.append(p)
        doctors = [dict(d, id=key, role='مقيم' if d['clinicalRole']=='RESIDENT' else 'اختصاصي',
                        supervisor_id=d.get('teamSupervisorId')) for key, d in self.client.doctors.items()]
        return rows, doctors

    def require_patient(self, key, mutate=False):
        actor = self.ready()
        shift = self.store.read()
        row = next((p for p in shift['patients'] if p['externalId'] == key), None)
        require(row is not None, 'المريض لم يعد في القسم')
        if mutate and not self.demo and not is_admin(actor):
            require(self.actor in (row.get('responsibleResidentId'), row.get('responsibleSupervisorId')),
                    'التحرير متاح للطبيب المسؤول أو المدير')
        return shift, row

    def set_task(self, patient_id, task_id, done):
        shift, row = self.require_patient(patient_id, True)
        tasks = read_tasks(row)
        task = next((t for t in tasks if t['id'] == task_id), None)
        if task is None:
            return False
        task['done'] = done
        row['tasksJson'] = packed(tasks)
        self.store.commit(draft=shift)
        return True

    def save_patient(self, key, submitted, shift_id, is_new=False, position=None):
        self.ready()
        shift = self.store.read()
        require(shift_id == shift['meta']['shiftId'], 'تغير القسم؛ أعد فتح المحرر')
        if is_new:
            require(not any(p['externalId']==key for p in shift['patients']), 'المريض موجود بالفعل')
            old = new_patient(self.client.zone)
            old['externalId'] = key
        else:
            shift, old = self.require_patient(key, True)
        row = copy.deepcopy(old)
        for field in ('name','admitNum','admitDate','birthDate','gender','diagnosisType','initialDiagnosis','treatmentPlan','followUp','responsibleResidentId','responsibleSupervisorId','hasCompanion','isPriority'):
            row[field] = submitted.get(field, row.get(field, ''))
        tasks = copy.deepcopy(submitted['tasks'])
        previous = {t['id']: t for t in read_tasks(old)}
        for task in tasks:
            original = previous.get(task['id'])
            if original:
                task.update({k:v for k,v in original.items() if k not in ('description','done')})
        validate_tasks(tasks)
        row['tasksJson'] = packed(tasks)
        apply_labs(row, submitted['lab_groups'])
        require(all(level in ('none','low','medium','high') for _,level in submitted['badges']), 'مستوى شارة غير صالح')
        row['warningDetails'] = badges_encode(submitted['badges'])
        row['warningFlags'] = '|'.join(level for _, level in submitted['badges'] if level != 'none')
        validate_patient(row, self.client.doctors, self.client.zone)
        if is_new or row.get('responsibleSupervisorId') != old.get('responsibleSupervisorId'):
            row['assignmentChangedOn'] = current_date(self.client.zone)
        require(len(shift['patients']) < 200 or not is_new, 'الحد الأقصى 200 مريض')
        if is_new:
            if position:
                from .core.sort import insert_patient
                insert_patient(shift, row, position)
            else:
                shift['patients'].append(row)
        else:
            shift['patients'][next(i for i,p in enumerate(shift['patients']) if p['externalId']==key)] = row
        ordered(shift, self.client.doctors, self.client.zone)
        self.store.commit(draft=shift, patientForm=None)
        return row

    def review(self, kind, value, summary, action):
        self.ready()
        key = secrets.token_urlsafe(24)
        self.reviews[key] = dict(kind=kind, value=value, summary=summary, action=action,
            project=self.client.project_id, actor=self.actor, created=time.time(), used=False)
        return key

    def reviewed(self, key, kind=None):
        review = self.reviews.get(key)
        require(review is not None and not review['used'] and time.time()-review['created'] < 1800,
                'انتهت المراجعة؛ أعدها')
        require(review['project'] == self.client.project_id and review['actor'] == self.actor,
                'تغير المشروع أو المستخدم؛ أعد المراجعة')
        require(kind is None or review['kind']==kind, 'مراجعة غير مناسبة لهذه العملية')
        return review
