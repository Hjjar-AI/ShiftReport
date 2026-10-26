"""Current-format desktop client for the existing Android Telegram protocol.

No network activity occurs on import. Secrets remain in memory. Mutations require
an authenticated actor and an explicit review token bound to the exact plan.
"""
from __future__ import annotations
import base64
import hashlib
import secrets
import time
import uuid

from desktop_protocol_common import COLUMNS, EDITABLE, BOUNDARY, BUNDLE, MAGIC, LIMIT, BridgeError, UncertainDelivery, MissingProjectIndex, require, packed, fingerprint, current_date, pointer, doctor_pointer, is_admin, ZoneInfo
from desktop_crypto import Cipher, open_join
from desktop_telegram_transport import Telegram
from desktop_wire import parse_doctors, validate_wire_patient, parse_bundle, encode_bundle
from desktop_publication import Review
import desktop_publication
import desktop_report_transport

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

    def fetch_registry(self):
        """Read project authority without depending on the current patient file."""
        index_id, state = self.telegram.state()
        require(state.get('doctorsFileId'), 'انشر سجل الأطباء الحالي من Android أولا')
        doctors = parse_doctors(self.telegram.download(state['doctorsFileId']))
        return {'index_id': index_id, 'state': state, 'doctors': doctors}

    def fetch(self):
        snapshot = self.fetch_registry()
        state = snapshot['state']
        bundle = parse_bundle(self.telegram.download(state['csvFileId'])) if state.get('csvFileId') else []
        return dict(snapshot, bundle=bundle)

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

    def require_local_identity(self):
        snapshot = self.fetch()
        return self.authorize(snapshot['doctors'])

    def require_local_admin(self):
        """Authorize a local names-only export from the authenticated cached roster."""
        require(self._actor is not None and time.time() < self._expires, 'أثبت هويتك بتليجرام مجددا')
        actor = self.doctors.get(self._actor)
        require(actor is not None and actor.get('telegram_id') == self._verified_telegram,
                'تغير حساب الطبيب؛ سجل الدخول مجددا')
        require(is_admin(actor), 'هذا الإجراء متاح للمدير فقط')
        return actor

    def require_admin(self):
        snapshot = self.fetch()
        actor = self.authorize(snapshot['doctors'])
        require(is_admin(actor), 'هذا الإجراء متاح للمدير فقط')
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
        return desktop_publication.plan(self, shift, choices)

    def prepare(self, plan):
        return desktop_publication.prepare(self, plan)

    def approve(self, plan):
        return desktop_publication.approve(self, plan)

    def publish(self, plan, before_upload):
        return desktop_publication.publish(self, plan, before_upload)

    def send_pdf(self, raw, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day=None):
        return desktop_report_transport.send_pdf(self, raw, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day)

    def send_text(self, text, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day=None):
        return desktop_report_transport.send_text(self, text, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day)
