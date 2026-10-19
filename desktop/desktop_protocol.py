"""Current-format desktop client for the existing Android Telegram protocol.

No network activity occurs on import. Secrets remain in memory. Mutations require
an authenticated actor and an explicit review token bound to the exact plan.
"""
from __future__ import annotations
import base64
import copy
import csv
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
import gzip
import hashlib
import io
import json
import re
import secrets
import time
import uuid
from typing import Optional
try:
    from zoneinfo import ZoneInfo
except ImportError:  # Python 3.8 / Windows 7 distribution.
    from backports.zoneinfo import ZoneInfo
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import requests

COLUMNS = 'externalId,admitNum,admitDate,gender,name,birthDate,hasCompanion,diagnosisType,initialDiagnosis,treatmentPlan,followUp,labs,responsibleResidentId,responsibleSupervisorId,warningFlags,warningDetails,isPriority,lastEditedByDoctorId,lastEditedBy,updatedAt,revision,tasksJson'.split(',')
EDITABLE = [key for key in COLUMNS[:17] if key != 'warningFlags'] + ['tasksJson']
BOUNDARY = '#==RUSHD_SHIFT_BOUNDARY_7F3A9C=='
BUNDLE = '# rushdShiftBundle=1'
MAGIC = b'SRDATA1'
LIMIT = 8 * 1024 * 1024
csv.field_size_limit(LIMIT)


class BridgeError(Exception):
    """Message is safe to display; never include a URL, token, key or payload."""


class UncertainDelivery(BridgeError):
    pass


class MissingProjectIndex(BridgeError):
    pass


def require(ok, message):
    if not ok:
        raise BridgeError(message)


def packed(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), sort_keys=True)


def fingerprint(value):
    return hashlib.sha256(packed(value).encode()).hexdigest()


def current_date(zone):
    now = datetime.now(ZoneInfo(zone))
    return (now - timedelta(days=1) if (now.hour, now.minute) < (8, 30) else now).date().isoformat()


def open_join(path, password):
    try:
        with open(path, 'rb') as stream:
            raw = stream.read(1024 * 1024 + 1)
        require(len(raw) <= 1024 * 1024, 'ملف الانضمام كبير جدا')
        envelope = json.loads(raw.decode('utf-8-sig'))
        require(envelope['format'] == 'ShiftReportJoin', 'مطلوب ملف الانضمام JSON الحالي')
        raw = base64.b64decode(envelope['encryptedPayload'], validate=True)
        magic = b'RUSHDJOIN1'
        require(raw.startswith(magic) and len(password.encode('utf-16-le'))//2 >= 10, 'ملف أو كلمة مرور غير صالحين')
        offset = len(magic)
        key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), raw[offset:offset + 16], 210000, 32)
        plain = AESGCM(key).decrypt(raw[offset + 16:offset + 28], raw[offset + 28:], None)
        config = json.loads(plain.decode())
        require(config['version'] == 1, 'صيغة الانضمام غير مدعومة')
        require(config['chatId'] < 0 and isinstance(config['botToken'], str) and ':' in config['botToken'], 'إعداد مشروع غير صالح')
        key = config.get('telegramDataKey', '')
        require(not config.get('encryptTelegram') or key, 'مفتاح المشروع مفقود')
        require(not key or len(base64.b64decode(key, validate=True)) == 32, 'مفتاح المشروع غير صالح')
        return config
    except BridgeError:
        raise
    except Exception:
        raise BridgeError('تعذر فتح ملف الانضمام؛ تحقق من كلمة المرور والملف') from None


class Cipher:
    def __init__(self, config):
        self._key = base64.b64decode(config['telegramDataKey']) if config.get('telegramDataKey') else None

    def encrypt(self, plain, purpose):
        if self._key is None:
            return plain
        nonce = secrets.token_bytes(12)
        return MAGIC + nonce + AESGCM(self._key).encrypt(nonce, plain, MAGIC + purpose.encode('ascii'))

    def decrypt(self, raw, purpose):
        if not raw.startswith(MAGIC):
            require(self._key is None, 'وصلت بيانات غير مشفرة إلى مشروع مشفر')
            return raw
        require(self._key is not None, 'بيانات مشفرة دون مفتاح المشروع')
        try:
            return AESGCM(self._key).decrypt(raw[7:19], raw[19:], MAGIC + purpose.encode('ascii'))
        except Exception:
            raise BridgeError('تعذر فك البيانات؛ المفتاح خاطئ أو المحتوى تالف') from None

    def encode_text(self, text):
        return 'SRMSG1:' + base64.b64encode(self.encrypt(gzip.compress(text.encode()), 'text')).decode() if self._key else text

    def decode_text(self, text):
        if not text.startswith('SRMSG1:'):
            require(self._key is None, 'فهرس غير مشفر في مشروع مشفر')
            return text
        try:
            compressed = self.decrypt(base64.b64decode(text[7:], validate=True), 'text')
            with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
                plain = stream.read(LIMIT + 1)
            require(len(plain) <= LIMIT, 'النص المفكوك أكبر من الحد')
            return plain.decode('utf-8')
        except BridgeError:
            raise
        except Exception:
            raise BridgeError('النص المشفر غير صالح') from None


class Telegram:
    def __init__(self, config):
        self.config = config
        self.cipher = Cipher(config)
        self._http = requests.Session()
        self.runner = None
        self._http.trust_env = False  # Do not send credentials through an inherited proxy.

    def call(self, method, params=None, files=None):
        return self.runner(lambda:self._call(method,params,files)) if self.runner else self._call(method,params,files)

    def _call(self, method, params=None, files=None):
        mutating = method in {'sendDocument', 'sendMessage', 'editMessageText', 'pinChatMessage'}
        try:
            response = self._http.post('https://api.telegram.org/bot' + self.config['botToken'] + '/' + method,
                                       data=params or {}, files=files, timeout=(10, 35), allow_redirects=False)
            if response.status_code >= 500 and mutating:
                raise UncertainDelivery('نتيجة العملية غير مؤكدة. افحص تليجرام قبل أي إعادة إرسال')
            payload = response.json()
            require(response.ok and payload.get('ok') is True, 'رفض تليجرام العملية (' + str(payload.get('error_code', response.status_code)) + ')')
            return payload['result']
        except BridgeError:
            raise
        except Exception:
            if mutating:
                raise UncertainDelivery('انقطع الاتصال أثناء عملية إرسال؛ لا تعدها قبل التحقق من تليجرام') from None
            raise BridgeError('تعذر الاتصال بتليجرام') from None

    def download(self, file_id):
        return self.runner(lambda:self._download(file_id)) if self.runner else self._download(file_id)

    def _download(self, file_id):
        data = self.call('getFile', {'file_id': file_id})
        path = data.get('file_path', '')
        require(path and not path.startswith('/') and '..' not in path.split('/'), 'مسار ملف غير صالح')
        try:
            with self._http.get('https://api.telegram.org/file/bot' + self.config['botToken'] + '/' + path,
                                timeout=(10, 35), allow_redirects=False, stream=True) as response:
                require(response.ok, 'تعذر تنزيل الملف')
                raw = bytearray()
                for chunk in response.iter_content(65536):
                    raw.extend(chunk)
                    require(len(raw) <= LIMIT, 'الملف أكبر من الحد المدعوم')
            return self.cipher.decrypt(bytes(raw), 'file')
        except BridgeError:
            raise
        except Exception:
            raise BridgeError('تعذر تنزيل الملف') from None

    def state(self):
        chat = self.call('getChat', {'chat_id': self.config['chatId']})
        message = chat.get('pinned_message', {})
        try:
            state = json.loads(self.cipher.decode_text(message.get('text', '')))
            require(state.get('kind') == 'shift-report-sync-state' and state.get('v') == 4, 'الرسالة المثبتة ليست فهرس المشروع الحالي؛ راجع المشروع والتثبيت')
            return message['message_id'], state
        except BridgeError:
            raise
        except Exception:
            raise MissingProjectIndex('لا يوجد فهرس مشروع صالح مثبت. للمجموعة الجديدة استخدم «إنشاء مشروع جديد»؛ للمشروع القائم راجع تثبيت الفهرس أو مزامنة سجل الأطباء') from None

    def upload(self, raw, filename, topic=None, caption=None, chat=None, pdf=False):
        params = {'chat_id': chat or self.config['chatId'], 'disable_notification': 'true'}
        if topic:
            params['message_thread_id'] = topic
        if caption:
            params['caption'] = caption
        if not pdf:
            raw = self.cipher.encrypt(raw, 'file')
            if self.cipher._key:
                filename = 'project_data.srdata'
                params['caption'] = 'بيانات مشروع مشفرة'
        result = self.call('sendDocument', params, {'document': (filename, raw, 'application/pdf' if pdf else 'application/octet-stream')})
        require(result.get('message_id') and result.get('document', {}).get('file_id'), 'لم يرجع تليجرام هوية المستند')
        return result

    def write_state(self, message_id, state):
        state=copy.deepcopy(state)
        text = self.cipher.encode_text(packed(state))
        while len(text.encode('utf-16-le'))//2>4096 and len(state.get('publicationJournal',[]))>1:
            state['publicationJournal'].pop()
            text=self.cipher.encode_text(packed(state))
        require(len(text.encode('utf-16-le')) // 2 <= 4096, 'فهرس المشروع أطول من حد الرسالة')
        try:
            self.call('editMessageText', {'chat_id': self.config['chatId'], 'message_id': message_id, 'text': text})
        except BridgeError:
            # A timeout can follow a successful edit. Read back before declaring failure.
            actual_id, actual = self.state()
            if actual_id != message_id or pointer(actual) != pointer(state) or doctor_pointer(actual) != doctor_pointer(state):
                raise
        actual_id, actual = self.state()
        require(actual_id == message_id and pointer(actual) == pointer(state) and doctor_pointer(actual) == doctor_pointer(state),
                'وصل نشر متزامن؛ لم يتم تأكيد نسختك كنسخة حالية. اجلب وراجع التعارضات')
        return actual


def parse_doctors(raw):
    try:
        body, last = raw.decode('utf-8-sig').strip().rsplit('@', 1)
        rows = body.split('|')
        header = rows.pop(0).split(',')
        expected = '#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId'.split(',')
        require(header == expected, 'مطلوب سجل الأطباء الحالي')
        result = {}
        for line in rows:
            values = line.split(',')
            require(len(values) == len(header), 'سطر أطباء ناقص')
            row = dict(zip(header, values))
            require(row['uid'] and row['uid'] not in result and row['name'].strip(), 'هوية طبيب مكررة أو ناقصة')
            require(row['clinicalRole'] in {'RESIDENT', 'SUPERVISOR'}, 'دور طبيب غير صالح')
            row['telegram_id'] = int(row['id']) if row['id'] else None
            require(row['telegram_id'] is None or row['telegram_id'] > 0, 'هوية تليجرام غير صالحة')
            row['destination'] = int(row['supervisorGroupChatId']) if row['supervisorGroupChatId'] else None
            require(row['destination'] is None or row['clinicalRole'] == 'SUPERVISOR' and row['destination'] < 0, 'وجهة مشرف غير صالحة')
            result[row['uid']] = row
        require(result, 'سجل الأطباء فارغ')
        return result
    except BridgeError:
        raise
    except Exception:
        raise BridgeError('سجل الأطباء غير صالح') from None


def validate_wire_patient(patient):
    """Reject malformed shared records before replacing any saved local draft."""
    require(all(patient.get(k, '').strip() for k in ['externalId', 'name', 'admitNum']), 'هوية مريض ناقصة')
    require(patient['gender'] in {'M', 'F'} and patient['hasCompanion'] in {'Y', 'N'} and
            patient['diagnosisType'] in {'PSYCHIATRIC', 'ADDICTION', 'DUAL'} and
            patient.get('isPriority', 'N') in {'Y', 'N'}, 'تصنيف مريض غير صالح')
    date.fromisoformat(patient['admitDate'])
    require(re.fullmatch(r'[0-9]{4}', patient['birthDate']), 'مطلوب عام الميلاد الحالي')
    require(1900 <= int(patient['birthDate']) <= 2100, 'سنة الميلاد غير صالحة')
    require(int(patient.get('revision') or 0) >= 0, 'نسخة مريض غير صالحة')
    tasks = json.loads(patient.get('tasksJson') or '[]')
    require(isinstance(tasks, list) and all(isinstance(t, dict) for t in tasks), 'قائمة مهام غير صالحة')
    require(len({t['id'] for t in tasks}) == len(tasks), 'معرف مهمة مكرر')
    allowed = {'id', 'description', 'ownerDoctorId', 'ownerName', 'priority', 'dueAtEpochMillis', 'dueShiftDate',
               'done', 'completedByDoctorId', 'completedByName', 'completedAtEpochMillis'}
    for task in tasks:
        require(set(task) <= allowed and isinstance(task.get('id'), str) and task['id'].strip() and
                isinstance(task.get('description'), str) and task['description'].strip(), 'مهمة غير صالحة')
        require(task.get('priority', 'NORMAL') in {'LOW', 'NORMAL', 'HIGH'} and
                isinstance(task.get('done', False), bool), 'حالة مهمة غير صالحة')
        for key in ['ownerDoctorId', 'ownerName', 'dueShiftDate', 'completedByDoctorId', 'completedByName']:
            require(task.get(key) is None or isinstance(task[key], str), 'هوية أو نص مهمة غير صالح')
        require(task.get('ownerDoctorId') is None or task['ownerDoctorId'].strip(), 'مسؤول مهمة غير صالح')
        due = task.get('dueAtEpochMillis')
        require(due is None or type(due) is int and due > 0, 'موعد مهمة غير صالح')
        if task.get('dueShiftDate') is not None:
            date.fromisoformat(task['dueShiftDate'])
            require(due is not None, 'موعد المهمة مطلوب مع مناوبة الاستحقاق')
        completion = [task.get(k) for k in ['completedByDoctorId', 'completedByName', 'completedAtEpochMillis']]
        if task.get('done'):
            require(all(completion) and type(completion[2]) is int and completion[2] > 0, 'سجل إتمام المهمة غير مكتمل')
        else:
            require(all(v is None for v in completion), 'مهمة معلقة تحمل سجل إتمام')
    from desktop_model import badges_decode
    badges_decode(patient.get('warningDetails', ''))


def parse_bundle(raw):
    require(len(raw) <= LIMIT, 'حزمة المرضى كبيرة جدا')
    try:
        text = raw.decode('utf-8-sig')
        require(text.splitlines()[0] == BUNDLE, 'مطلوب تنسيق حزمة المناوبات الحالي')
        parts = text.split('\n', 1)[1].split('\n' + BOUNDARY + '\n')
        require(1 <= len(parts) <= 3, 'عدد المناوبات في الحزمة غير صالح')
        result = []
        for part in parts:
            rows = list(csv.reader(io.StringIO(part.strip(), newline='')))
            meta = dict(token.split('=', 1) for token in rows[0][0].lstrip('# ').split(' ') if '=' in token)
            require(meta.get('schemaVersion') == '7' and rows[1] == COLUMNS, 'مطلوب CSV المرضى الحالي بإصدار 7')
            require(all(key in meta for key in ['shiftDate','shiftId','doctors','sortSpec','shiftRevision','snapshotId','baseSnapshotId','deviceId']), 'بيانات تعريف CSV ناقصة؛ أعد النشر من Android بعد إصلاح اقتباس metadata')
            require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}',meta['shiftDate']) is not None,'تاريخ المناوبة يجب أن يكون YYYY-MM-DD')
            date.fromisoformat(meta['shiftDate'])
            require(meta.get('shiftId'), 'معرف المناوبة مفقود')
            patients = []
            for row in rows[2:]:
                if not row or not any(row):
                    continue
                require(len(row) == len(COLUMNS), 'سطر مرضى غير مكتمل')
                patient = dict(zip(COLUMNS, row))
                validate_wire_patient(patient)
                patient['tasksJson'] = packed(json.loads(patient['tasksJson'] or '[]'))
                patients.append(patient)
            require(len({r['externalId'] for r in patients}) == len(patients), 'معرف مريض مكرر')
            result.append({'meta': meta, 'patients': patients})
        require(len({s['meta']['shiftId'] for s in result}) == len(result), 'معرف مناوبة مكرر')
        return result
    except BridgeError:
        raise
    except Exception:
        raise BridgeError('حزمة المرضى غير صالحة') from None


def encode_bundle(shifts):
    parts = []
    for shift in sorted(shifts, key=lambda s: s['meta']['shiftDate'], reverse=True)[:3]:
        meta = shift['meta']
        require(not any(any(ch.isspace() for ch in str(v)) for v in meta.values()), 'بيانات تعريف المناوبة غير صالحة')
        stream = io.StringIO(newline='')
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['# ' + ' '.join(k + '=' + str(v) for k, v in meta.items())])
        writer.writerow(COLUMNS)
        for row in shift['patients']:
            require(not any('\n' + BOUNDARY in str(v) for v in row.values()), 'نص يتعارض مع فاصل الحزمة')
            writer.writerow([row.get(k, '') for k in COLUMNS])
        parts.append(stream.getvalue().rstrip('\n'))
    result = (BUNDLE + '\n' + ('\n' + BOUNDARY + '\n').join(parts) + '\n').encode()
    require(len(result) <= LIMIT, 'الحزمة الناتجة أكبر من الحد')
    return result


@dataclass
class Review:
    baseline_pointer: tuple
    registry_pointer: tuple
    shift: dict
    bundle: list
    remote: dict
    conflicts: list = field(default_factory=list)
    _approval: Optional[str] = field(default=None, repr=False)
    actor_id: Optional[str] = field(default=None, repr=False)


def pointer(state):
    return state.get('csvFileId'), state.get('csvMessageId')


def doctor_pointer(state):
    return state.get('doctorsFileId'), state.get('doctorsMessageId'), state.get('doctorsUpdatedAt')


def merge_patients(base, local, remote, choices):
    maps = [{r['externalId']: r for r in rows} for rows in [base, local, remote]]
    order = list(maps[1]) + [key for key in maps[2] if key not in maps[1]]
    conflicts, result = [], []
    for key in list(dict.fromkeys(order + list(maps[0]))):
        b, l, r = (m.get(key) for m in maps)
        if l is None or r is None or b is None:
            if l == b: selected = r
            elif r == b or l == r: selected = l
            else:
                conflict = (key, '__record__')
                if conflict not in choices:
                    conflicts.append({'id': key, 'field': '__record__', 'base': b, 'local': l, 'remote': r}); continue
                selected = l if choices[conflict] == 'local' else r
            if selected is not None: result.append(copy.deepcopy(selected))
            continue
        row = copy.deepcopy(r)
        for col in EDITABLE:
            if l.get(col) == b.get(col): row[col] = r.get(col, '')
            elif r.get(col) == b.get(col) or l.get(col) == r.get(col): row[col] = l.get(col, '')
            else:
                conflict = (key, col)
                if conflict not in choices:
                    conflicts.append({'id': key, 'field': col, 'base': b.get(col), 'local': l.get(col), 'remote': r.get(col)})
                else: row[col] = l[col] if choices[conflict] == 'local' else r[col]
        result.append(row)
    return result, conflicts


def is_admin(doctor):
    options = doctor.get('options', '')
    match = re.fullmatch(r'[aA]([1-9][0-9]*)', options)
    rank = doctor.get('#', '')
    if options and match is None:
        return False
    if rank and re.fullmatch(r'[0-9]+', rank) is None:
        return False
    number = int(rank) if rank else (int(match.group(1)) if match else 0)
    return 0 < number <= 2147483647 and (match is None or number == int(match.group(1)))


class Client:
    def __init__(self, config, zone='Asia/Damascus', admin_only=False):
        self.config = config
        self.admin_only = admin_only
        self.zone = zone
        self.telegram = Telegram(config)
        self._actor = None
        self._verified_telegram = None
        self._expires = 0
        self._nonce = None
        self.device = 'desktop-' + str(uuid.uuid4())
        self.baseline = None
        self.local_state_key = None
        self.doctors = {}

    @property
    def project_id(self):
        identity = {k: self.config.get(k) for k in ['chatId', 'reportsTopicId', 'csvTopicId', 'doctorsTopicId']}
        identity.update(bot=self.config['botToken'].split(':')[0],
                        key=hashlib.sha256(self.config.get('telegramDataKey', '').encode()).hexdigest())
        return fingerprint(identity)

    def fetch(self):
        index_id, state = self.telegram.state()
        require(state.get('doctorsFileId'), 'انشر سجل الأطباء الحالي من Android أولا')
        doctors = parse_doctors(self.telegram.download(state['doctorsFileId']))
        bundle = parse_bundle(self.telegram.download(state['csvFileId'])) if state.get('csvFileId') else []
        return {'index_id': index_id, 'state': state, 'doctors': doctors, 'bundle': bundle}

    def begin_login(self, doctor_id):
        self._actor=None;self._verified_telegram=None;self._expires=0
        snapshot = self.fetch()
        doctor = snapshot['doctors'].get(doctor_id)
        require(doctor is not None and doctor.get('telegram_id'), 'اختر طبيبا ذا حساب تليجرام مرتبط')
        if self._nonce and self._nonce['doctor']==doctor_id and time.time()-self._nonce['started']<180:
            return  # Reuse an unexpired code after a cancelled input dialog; do not spam DMs.
        require(not self._nonce or time.time()-self._nonce['started']>=30, 'انتظر 30 ثانية قبل طلب رمز جديد')
        code = str(secrets.randbelow(900000)+100000)
        salt = secrets.token_bytes(16)
        bot = self.telegram.call('getMe')
        self._nonce = {'salt':salt,'hash':hashlib.sha256(salt+code.encode()).digest(),'doctor':doctor_id,
                       'telegram':doctor['telegram_id'],'started':time.time(),'attempts':0}
        try:
            self.telegram.call('sendMessage', {'chat_id':doctor['telegram_id'],
                'text':'ShiftReport Desktop\nرمز تحقق تسجيل الدخول: '+code+'\nصالح ثلاث دقائق. لا تشاركه مع أحد.',
                'disable_notification':'false'})
        except UncertainDelivery:
            raise BridgeError('قد يكون رمز التحقق وصل؛ افحص البوت واضغط إثبات الهوية مجددا لإدخاله دون إرسال رمز آخر') from None
        except BridgeError:
            self._nonce=None
            raise BridgeError('تعذر إرسال رمز التحقق. افتح البوت @'+bot.get('username','')+' واضغط Start ثم أعد الطلب') from None
        # The desktop does not read/ack getUpdates, so it cannot consume Android login challenges.

    def check_login(self, code):
        challenge=self._nonce
        require(challenge is not None and time.time()-challenge['started']<180, 'انتهت صلاحية رمز التحقق')
        require(challenge['attempts']<5, 'انتهت محاولات الرمز؛ انتظر انتهاءه ثم اطلب رمزا جديدا')
        challenge['attempts']+=1
        import unicodedata
        code=''.join(str(unicodedata.digit(ch)) if ch.isdecimal() else ch for ch in code.strip())
        if not secrets.compare_digest(hashlib.sha256(challenge['salt']+code.encode()).digest(),challenge['hash']):
            return None
        self._actor,self._verified_telegram=challenge['doctor'],challenge['telegram']
        self._expires=time.time()+1800;self._nonce=None
        return self.authorize(self.fetch()['doctors'])

    def authorize(self, doctors):
        require(self._actor is not None and time.time() < self._expires, 'أثبت هويتك بتليجرام مجددا')
        actor = doctors.get(self._actor)
        require(actor is not None and actor.get('telegram_id') == self._verified_telegram, 'الطبيب أو حساب تليجرام المرتبط تغير؛ سجل الدخول مجددا')
        if self.admin_only:
            require(is_admin(actor), 'الاتصال المباشر متاح للمدير فقط')
        member = self.telegram.call('getChatMember', {'chat_id': self.config['chatId'], 'user_id': actor['telegram_id']})
        require(member.get('status') in {'creator', 'administrator', 'member'} or
                member.get('status') == 'restricted' and member.get('is_member'), 'الطبيب لم يعد عضوا في المشروع')
        return actor

    def require_admin(self):
        snapshot = self.fetch()
        actor = self.authorize(snapshot['doctors'])
        require(is_admin(actor), 'تصدير توزيع المرضى متاح للمدير فقط')
        return actor

    def download(self):
        snapshot = self.fetch()
        self.authorize(snapshot['doctors'])
        self.doctors = snapshot['doctors']
        self.baseline = snapshot
        return snapshot

    def baseline_key(self):
        seed=base64.b64decode(self.config['telegramDataKey']) if self.config.get('telegramDataKey') else self.local_state_key
        require(seed is not None and len(seed)==32, 'مفتاح حماية المسودة المحلية غير متاح')
        return hashlib.sha256(b'ShiftReportDesktopBaseline'+seed).digest()

    def plan(self, shift, choices=None):
        require(self.baseline is not None, 'اجلب نسخة أساس من تليجرام أولا')
        require(shift['meta']['shiftDate'] == current_date(self.zone), 'المناوبات السابقة للعرض فقط؛ وقت الانتقال 08:30')
        require(len({p['externalId'] for p in shift['patients']}) == len(shift['patients']), 'معرف مريض مكرر في الجدول')
        remote = self.fetch()
        self.authorize(remote['doctors'])
        selected_id = shift['meta']['shiftId']
        other_ids = {p['externalId'] for s in remote['bundle'] if s['meta']['shiftId'] != selected_id for p in s['patients']}
        require(not any(p['externalId'] in other_ids for p in shift['patients']), 'معرف مريض مستخدم في مناوبة أخرى')
        base_shift = next((s for s in self.baseline['bundle'] if s['meta']['shiftId'] == selected_id), None)
        latest = next((s for s in remote['bundle'] if s['meta']['shiftId'] == selected_id), None)
        same_date = [s for s in remote['bundle'] if s['meta']['shiftDate'] == shift['meta']['shiftDate']]
        require(not same_date or all(s['meta']['shiftId'] == selected_id for s in same_date), 'معرف المناوبة الحالية تغير؛ اجلبها قبل التعديل')
        require(base_shift is None or latest is not None, 'مناوبة الأساس لم تعد في الحزمة؛ أعد الجلب ولا تنشر فوق سجل آخر')
        merged, conflicts = merge_patients(base_shift['patients'] if base_shift else [], shift['patients'], latest['patients'] if latest else [], choices or {})
        output = copy.deepcopy(shift)
        output['patients'] = merged
        if latest and base_shift:
            b, l, r = (x['meta'].get('doctors', '') for x in [base_shift, shift, latest])
            if l == b: output['meta']['doctors'] = r
            elif r != b and l != r:
                choice = (choices or {}).get(('__shift__', 'doctors'))
                if choice: output['meta']['doctors'] = l if choice == 'local' else r
                else: conflicts.append({'id': '__shift__', 'field': 'doctors', 'base': b, 'local': l, 'remote': r})
        if latest and base_shift:
            b, l, r = ([p['externalId'] for p in s['patients']] for s in [base_shift, shift, latest])
            order = l
            if l == b:
                order = r
            elif r != b and l != r:
                choice = (choices or {}).get(('__shift__', 'order'))
                if choice:
                    order = l if choice == 'local' else r
                else:
                    conflicts.append({'id': '__shift__', 'field': 'order', 'base': b, 'local': l, 'remote': r})
            by_id = {p['externalId']: p for p in merged}
            ids = list(dict.fromkeys(order + list(by_id)))
            output['patients'] = [by_id[key] for key in ids if key in by_id]
        plan = Review(pointer(remote['state']), doctor_pointer(remote['state']), output, remote['bundle'], remote, conflicts)
        return plan

    def prepare(self, plan, restored_completions=None):
        require(not plan.conflicts, 'راجع كل التعارضات أولا')
        actor = self.authorize(plan.remote['doctors'])
        doctors = plan.remote['doctors']
        now = int(time.time() * 1000)
        shift = copy.deepcopy(plan.shift)
        latest = next((s for s in plan.bundle if s['meta']['shiftId'] == shift['meta']['shiftId']), None)
        previous = {r['externalId']: r for r in latest['patients']} if latest else {}
        require(len(shift['patients']) <= 200, 'الحد الأقصى للمناوبة 200 مريض')
        roster = shift['meta'].get('doctors', '').split(',')
        require(roster and all(d in doctors for d in roster), 'حدد أطباء مناوبة نشطين')
        require(len(set(roster)) == len(roster), 'طبيب مكرر في المناوبة')
        from desktop_model import validate_patient, badges_decode
        for row in shift['patients']:
            validate_patient(row, doctors, self.zone)
            old = previous.get(row['externalId'], {})
            old_tasks = {t['id']: t for t in json.loads(old.get('tasksJson') or '[]')}
            recovered = next((entry['tasks'] for entry in (restored_completions or [])
                              if entry['shiftId'] == shift['meta']['shiftId'] and entry['patientId'] == row['externalId']), [])
            # Only previously published completions from encrypted local recovery are retained.
            # Editable form completion fields still do not establish completion identity/time.
            for task in recovered:
                if task.get('done'): old_tasks.setdefault(task['id'], task)
            tasks = json.loads(row.get('tasksJson') or '[]')
            require(isinstance(tasks, list) and len({t['id'] for t in tasks}) == len(tasks), 'قائمة مهام غير صالحة')
            for task in tasks:
                require(task['id'] and str(task['description']).strip() and task.get('priority', 'NORMAL') in {'LOW', 'NORMAL', 'HIGH'}, 'مهمة ناقصة أو مكررة')
                task['description'] = task['description'].strip()
                owner = task.get('ownerDoctorId')
                before = old_tasks.get(task['id'], {})
                require(not owner or owner in doctors or task.get('done') and owner == before.get('ownerDoctorId'), 'مسؤول مهمة غير نشط')
                task['ownerName'] = doctors[owner]['name'] if owner in doctors else before.get('ownerName') if owner else None
                require(task.get('dueAtEpochMillis') is None or task['dueAtEpochMillis'] > 0, 'موعد مهمة غير صالح')
                if task.get('dueShiftDate'):
                    require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}',task['dueShiftDate']) is not None,'مناوبة استحقاق المهمة يجب أن تكون YYYY-MM-DD')
                    date.fromisoformat(task['dueShiftDate']); require(task.get('dueAtEpochMillis') is not None, 'موعد المهمة مطلوب مع مناوبة الاستحقاق')
                if not task.get('done'):
                    task.update(completedByDoctorId=None, completedByName=None, completedAtEpochMillis=None)
                elif before.get('done'):
                    for key in ['completedByDoctorId', 'completedByName', 'completedAtEpochMillis']: task[key] = before[key]
                else:
                    task.update(completedByDoctorId=self._actor, completedByName=actor['name'], completedAtEpochMillis=now)
            row['tasksJson'] = packed(tasks)
            row['warningFlags'] = '|'.join(level for _,level in badges_decode(row.get('warningDetails', '')) if level != 'none')
            validate_wire_patient(row)
            changed = not old or any(row.get(k, '') != old.get(k, '') for k in EDITABLE)
            if changed:
                row.update(lastEditedByDoctorId=self._actor, lastEditedBy=actor['name'], updatedAt=str(now), revision=str(int(old.get('revision') or 0) + 1))
        meta = shift['meta']
        meta.update(schemaVersion='7', snapshotId=str(uuid.uuid4()), baseSnapshotId=latest['meta'].get('snapshotId', '') if latest else '',
                    shiftRevision=str(int(latest['meta'].get('shiftRevision') or 0) + 1) if latest else '1', deviceId=self.device,
                    sortSpec='{"levels":[]}')
        plan.shift = shift
        plan.actor_id = self._actor
        plan._approval = None
        return plan

    def approve(self, plan):
        plan._approval = fingerprint(plan.shift)

    def publish(self, plan, before_upload):
        require(plan.actor_id == self._actor, 'تغير الطبيب بعد المراجعة؛ أعدها')
        require(plan._approval == fingerprint(plan.shift), 'تغيرت البيانات بعد المراجعة؛ راجعها مجددا')
        current = self.fetch()
        self.authorize(current['doctors'])
        require(pointer(current['state']) == plan.baseline_pointer and doctor_pointer(current['state']) == plan.registry_pointer,
                'وصلت بيانات أحدث بعد المراجعة؛ أعد المراجعة')
        require(plan.shift['meta']['shiftDate'] == current_date(self.zone), 'انتهت المناوبة الحالية')
        others = [s for s in current['bundle'] if s['meta']['shiftId'] != plan.shift['meta']['shiftId']]
        require(not any(s['meta']['shiftDate'] > plan.shift['meta']['shiftDate'] for s in others), 'توجد مناوبة مستقبلية في الحزمة؛ صحح تاريخ الأجهزة قبل النشر')
        bundle = sorted([plan.shift] + others, key=lambda s: s['meta']['shiftDate'], reverse=True)[:3]
        raw = encode_bundle(bundle)
        before_upload()  # Persist an UNKNOWN receipt before a potentially ambiguous send.
        uploaded = self.telegram.upload(raw, 'ShiftReport.csv', self.config.get('csvTopicId'), 'نسخة المناوبات من سطح المكتب')
        # Preserve other pointers by re-reading and never manufacturing a fresh empty index.
        index_id, state = self.telegram.state()
        require(pointer(state) == plan.baseline_pointer and doctor_pointer(state) == plan.registry_pointer,
                'وصل نشر أثناء الرفع؛ الملف رفع لكن الفهرس لم يتغير. اجلب وراجع قبل إعادة النشر')
        self.authorize(current['doctors'])
        at = int(uploaded['date']) * 1000
        previous = copy.deepcopy(state)
        rotate = not state.get('csvFileId') or not state.get('csvUpdatedAt') or at - state['csvUpdatedAt'] >= 10 * 3600000
        if rotate:
            for old, new in [('csvFileId','previousCsvFileId'),('csvMessageId','previousCsvMessageId'),('csvDate','previousCsvDate'),('csvUpdatedAt','previousCsvUpdatedAt')]:
                state[new] = previous.get(old, 0 if old == 'csvUpdatedAt' else None)
        state.update(csvFileId=uploaded['document']['file_id'], csvMessageId=uploaded['message_id'], csvDate=plan.shift['meta']['shiftDate'], csvUpdatedAt=at)
        journal = {'snapshotId': plan.shift['meta']['snapshotId'], 'parentSnapshotId': plan.shift['meta'].get('baseSnapshotId') or None,
                   'fileId': state['csvFileId'], 'messageId': state['csvMessageId'], 'at': at, 'deviceId': self.device, 'forced': False}
        state['publicationJournal'] = sorted(state.get('publicationJournal', []) + [journal], key=lambda v: v['messageId'], reverse=True)[:12]
        committed = self.telegram.write_state(index_id, state)
        self.baseline = {'index_id': index_id, 'state': committed, 'doctors': current['doctors'], 'bundle': bundle}
        self.doctors = current['doctors']
        return uploaded

    def send_pdf(self, raw, shift, supervisor, reviewed_pointer, reviewed_registry, before_send):
        require(raw.startswith(b'%PDF-'), 'مطلوب PDF معاين')
        current = self.fetch()
        self.authorize(current['doctors'])
        require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry, 'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
        require(shift['meta']['shiftDate'] == current_date(self.zone), 'لا ترسل تقرير مناوبة محفوظة')
        chat, topic = self.config['chatId'], self.config.get('reportsTopicId')
        if supervisor:
            doctor = current['doctors'].get(supervisor)
            require(doctor is not None and doctor['clinicalRole'] == 'SUPERVISOR' and doctor['destination'], 'وجهة المشرف غير متاحة')
            chat, topic = doctor['destination'], None
        before_send(chat, topic)
        return self.telegram.upload(raw, 'ShiftReport_' + shift['meta']['shiftDate'] + '.pdf', topic,
                                    self.config['hospitalName'] + ' — ' + shift['meta']['shiftDate'], chat, pdf=True)

    def send_text(self, text, shift, supervisor, reviewed_pointer, reviewed_registry, before_send):
        from desktop_reports import report_text, text_chunks
        def destination():
            current = self.fetch()
            self.authorize(current['doctors'])
            require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry,
                    'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
            require(shift['meta']['shiftDate'] == current_date(self.zone), 'لا ترسل تقرير مناوبة محفوظة')
            self.doctors = current['doctors']
            require(text == report_text(self, shift, supervisor), 'تغير النص بعد المعاينة')
            chat, topic = self.config['chatId'], self.config.get('reportsTopicId')
            if supervisor:
                doctor = current['doctors'].get(supervisor)
                require(doctor and doctor['clinicalRole'] == 'SUPERVISOR' and doctor.get('destination'), 'وجهة المشرف غير متاحة')
                chat, topic = doctor['destination'], None
            return chat, topic
        chat, topic = destination()
        before_send(chat, topic)  # One UNKNOWN receipt covers all parts, including partial delivery.
        if self.config.get('telegramDataKey'):
            return self.telegram.upload(text.encode('utf-8'), 'ShiftReport_'+shift['meta']['shiftDate']+'.txt', topic, chat=chat)
        parts = text_chunks(text)
        result = None
        for index, part in enumerate(parts, 1):
            destination()  # A mid-send failure retains UNKNOWN; never silently retry a partial report.
            params = dict(chat_id=chat, text='({}/{})\n'.format(index, len(parts))+part, disable_notification='true')
            if topic: params['message_thread_id'] = topic
            result = self.telegram.call('sendMessage', params)
            require(result.get('message_id'), 'لم يرجع تليجرام معرّف جزء التقرير؛ افحص التسليم قبل الإعادة')
        return result
