"""Reviewed first-admin bootstrap and protected join export; no work on import."""
from __future__ import annotations
import base64
import copy
import hashlib
from pathlib import Path
import secrets
import time
import unicodedata
import uuid
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol import (BridgeError, Client, UncertainDelivery, fingerprint,
                              is_admin, open_join, packed, parse_doctors, require)


class ProjectCreator:
    """Bootstrap authority is verified Telegram group administration, not UI state."""
    def __init__(self, config, zone='Asia/Damascus'):
        self.client = Client(copy.deepcopy(config), zone, admin_only=True)
        self._configuration = fingerprint(self.client.config)
        self._profile = None
        self._challenge = None
        self._review = None
        self._attempted = False
        self._last_request = None

    @property
    def profile(self):
        return copy.deepcopy(self._profile)

    @property
    def verified(self):
        try:
            self._verified()
            return True
        except BridgeError:
            return False

    @staticmethod
    def validate_profile(profile):
        name = str(profile.get('name', '')).strip()
        require(name and len(name) <= 200 and not any(c in name for c in ',|@"\n\r'),
                'أدخل اسم المدير دون فواصل أو رموز | @ أو أسطر جديدة')
        identity = str(profile.get('telegram_id', '')).strip()
        require(identity.isdecimal() and 0 < int(identity) < 2**52,
                'أدخل معرّف حساب المدير الرقمي في تليجرام')
        require(profile.get('gender') in {'M', 'F'}, 'اختر الجنس')
        require(profile.get('clinicalRole') in {'RESIDENT', 'SUPERVISOR'}, 'اختر الدور السريري')
        return dict(name=name, telegram_id=int(identity), gender=profile['gender'],
                    clinicalRole=profile['clinicalRole'])

    def _available(self):
        config, telegram = self.client.config, self.client.telegram
        require(fingerprint(config) == self._configuration, 'تغيّرت إعدادات الإنشاء؛ ابدأ المراجعة مجدداً')
        bot = telegram.call('getMe')
        require(str(bot.get('id')) == config['botToken'].split(':', 1)[0], 'هوية البوت غير متطابقة')
        chat = telegram.call('getChat', {'chat_id': config['chatId']})
        require(chat.get('id') == config['chatId'] and chat.get('type') in {'group', 'supergroup'},
                'المشروع يحتاج مجموعة تليجرام')
        require(not chat.get('pinned_message'),
                'توجد رسالة مثبتة بالفعل. استخدم الانضمام لمشروع قائم، أو راجع التثبيت القديم يدوياً قبل إنشاء مشروع جديد')
        topics = [config.get(k, 0) for k in ('reportsTopicId', 'announcementsTopicId', 'csvTopicId', 'doctorsTopicId')]
        require(not any(topics) or chat.get('is_forum'), 'معرّفات المواضيع تتطلب مجموعة مفعّلة المواضيع')
        member = telegram.call('getChatMember', {'chat_id': config['chatId'], 'user_id': bot['id']})
        require(member.get('status') == 'administrator' and member.get('can_pin_messages'),
                'يجب أن يكون البوت مديراً بصلاحية تثبيت الرسائل')
        require(self._profile is not None, 'أدخل بيانات المدير الأول')
        actor = telegram.call('getChatMember', {'chat_id': config['chatId'], 'user_id': self._profile['telegram_id']})
        user = actor.get('user', {})
        require(actor.get('status') in {'creator', 'administrator'} and
                user.get('id') == self._profile['telegram_id'] and not user.get('is_bot'),
                'إنشاء المشروع متاح لحساب بشري يدير مجموعة تليجرام')
        return chat

    def begin(self, profile):
        require(not self._attempted, 'بدأت محاولة إنشاء بالفعل؛ افحص تليجرام وأعد الاتصال دون تكرار الإنشاء')
        profile = self.validate_profile(profile)
        now = time.time()
        if self._challenge and self._profile == profile and (self.verified or now-self._challenge['started'] < 180):
            self._available()
            return  # Reuse an outstanding challenge after a cancelled entry dialog.
        require(self._last_request is None or now-self._last_request >= 30, 'انتظر 30 ثانية قبل طلب رمز آخر')
        self._profile = profile
        self._review = None
        self._available()
        now = self._last_request = time.time()
        salt, code = secrets.token_bytes(16), str(secrets.randbelow(900000)+100000)
        self._challenge = dict(salt=salt, digest=hashlib.sha256(salt+code.encode()).digest(),
                               started=now, attempts=0, verified=False, profile=fingerprint(profile))
        try:
            self.client.telegram.call('sendMessage', {
                'chat_id': profile['telegram_id'], 'disable_notification': 'false',
                'text': 'ShiftReport Desktop\nرمز إنشاء مشروع جديد: '+code+
                        '\nصالح ثلاث دقائق. لا تشاركه مع أحد. لم يُنشأ المشروع بعد.'})
        except UncertainDelivery:
            raise BridgeError('قد يكون رمز الإنشاء وصل؛ أعد فتح الإنشاء لإدخاله دون إرسال رمز جديد') from None
        except BridgeError:
            self._challenge = None
            raise BridgeError('تعذر إرسال الرمز. افتح البوت واضغط Start ثم أعد طلب الإنشاء بعد 30 ثانية') from None

    def _verified(self):
        require(self._challenge is not None and self._challenge.get('verified') and
                self._challenge.get('profile') == fingerprint(self._profile) and
                time.time() < self._challenge.get('verified_until', 0),
                'تحقق من هوية المدير برمز تليجرام أولاً؛ جلسة الإنشاء غير صالحة أو منتهية')

    def check_code(self, code):
        challenge = self._challenge
        require(challenge is not None and time.time()-challenge['started'] < 180, 'انتهت صلاحية رمز الإنشاء')
        require(challenge.get('profile') == fingerprint(self._profile), 'تغيّرت بيانات المدير؛ اطلب رمزاً جديداً')
        require(challenge['attempts'] < 5, 'انتهت محاولات الرمز؛ انتظر انتهاءه ثم اطلب رمزاً جديداً')
        challenge['attempts'] += 1
        code = ''.join(str(unicodedata.digit(c)) if c.isdecimal() else c for c in code.strip())
        if not secrets.compare_digest(hashlib.sha256(challenge['salt']+code.encode()).digest(), challenge['digest']):
            return False
        self._available()
        challenge['verified'] = True
        challenge['verified_until'] = time.time()+1800
        return True

    def _unused_workspace(self, workspace):
        require(workspace.project == self.client.project_id, 'المسودة المحلية تخص مشروعاً آخر')
        require(not workspace.state['baseline'] and not workspace.state['draft'] and not workspace.receipts(),
                'هذا الجهاز يحتوي حالة أو محاولة سابقة لهذا المشروع؛ استخدم إعادة الاتصال وافحص سجل المحاولة دون إعادة الإنشاء')

    def review(self, workspace=None):
        self._verified()
        if workspace is not None: self._unused_workspace(workspace)
        chat = self._available()
        profile = self._profile
        self._review = (secrets.token_urlsafe(24), fingerprint(profile))
        return self._review[0], {
            'hospital': self.client.config['hospitalName'], 'group': chat.get('title', ''),
            'group_id': self.client.config['chatId'],
            'topics': {key: self.client.config.get(key, 0) for key in
                       ('reportsTopicId', 'announcementsTopicId', 'csvTopicId', 'doctorsTopicId')},
            'admin': profile['name'], 'role': profile['clinicalRole'],
            'encrypted': bool(self.client.config.get('telegramDataKey'))}

    def publish(self, approval, workspace):
        self._verified()
        require(not self._attempted and self._review is not None and approval == self._review[0] and
                fingerprint(self._profile) == self._review[1], 'راجع إنشاء المشروع ووافق عليه أولاً')
        self._unused_workspace(workspace)
        self._available()
        profile = self._profile
        uid = str(uuid.uuid4())
        fields = ['1', uid, profile['name'], profile['gender'], str(profile['telegram_id']),
                  '', 'A1', profile['clinicalRole'], '', '']
        registry = ('#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId,teamSupervisorId|'+
                    ','.join(fields)+'@1').encode('utf-8')
        parse_doctors(registry)
        self._review = None
        workspace.receipt_start('create:'+workspace.project, 'create', self.client.config['chatId'],
                                self.client.config.get('doctorsTopicId'), uid)
        self._attempted = True
        try:
            message = self.client.telegram.upload(registry, 'doctors_registry.csv',
                topic=self.client.config.get('doctorsTopicId'), caption='سجل الأطباء')
            state = dict(kind='shift-report-sync-state', v=4,
                         doctorsFileId=message['document']['file_id'], doctorsMessageId=message['message_id'],
                         doctorsUpdatedAt=int(time.time()*1000))
            self._available()  # Never replace an index/pin that arrived during upload.
            index = self.client.telegram.call('sendMessage', {
                'chat_id': self.client.config['chatId'], 'disable_notification': 'true',
                'text': self.client.telegram.cipher.encode_text(packed(state))})
            require(index.get('message_id'), 'لم يرجع تليجرام معرّف فهرس الإنشاء')
            self._available()
            self.client.telegram.call('pinChatMessage', {
                'chat_id': self.client.config['chatId'], 'message_id': index['message_id'],
                'disable_notification': 'true'})
            snapshot = self.client.fetch()
            require(snapshot['index_id'] == index['message_id'] and snapshot['state'] == state and
                    snapshot['doctors'] == parse_doctors(registry), 'تعذر تأكيد فهرس الإنشاء وسجل المدير')
            self.client._actor, self.client._verified_telegram = uid, profile['telegram_id']
            self.client._expires = time.time()+1800
            self.client.authorize(snapshot['doctors'])
            workspace.commit(baseline=copy.deepcopy(snapshot))
            workspace.receipt_finish(index)
            self.client.baseline, self.client.doctors = snapshot, snapshot['doctors']
            return snapshot
        except Exception:
            raise BridgeError('لم يتأكد اكتمال إنشاء المشروع. حُفظت المحاولة محلياً؛ افحص رسائل المجموعة والفهرس ثم أعد الاتصال. لا تكرر الإنشاء أو تحذف المسودة المحلية') from None


def export_join(client, path, password):
    """Export the existing Android envelope, with live admin checks below the GUI."""
    def authorize():
        snapshot = client.fetch()
        require(is_admin(client.authorize(snapshot['doctors'])), 'تصدير ملف الانضمام متاح للمدير فقط')
    authorize()
    require(len(password.encode('utf-16-le'))//2 >= 10, 'عبارة مرور ملف الانضمام يجب ألا تقل عن 10 محارف')
    keys = ('hospitalName', 'botToken', 'chatId', 'reportsTopicId', 'announcementsTopicId',
            'csvTopicId', 'doctorsTopicId', 'telegramDataKey')
    config = {key: client.config.get(key, '' if key in {'hospitalName','botToken','telegramDataKey'} else 0) for key in keys}
    config.update(version=1, encryptTelegram=bool(config['telegramDataKey']))
    salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, 210000, 32)
    encrypted = b'RUSHDJOIN1'+salt+nonce+AESGCM(key).encrypt(nonce, packed(config).encode('utf-8'), None)
    document = packed(dict(format='ShiftReportJoin', encryptedPayload=base64.b64encode(encrypted).decode())).encode('utf-8')
    path = Path(path)
    require(path.name.endswith('.srjoin.json'), 'اختر اسماً ينتهي بـ .srjoin.json')
    authorize()
    created = False
    try:
        with path.open('xb') as stream:
            created = True
            stream.write(document)
        path.chmod(0o600)
        require(open_join(path, password) == config, 'تعذر التحقق من ملف الانضمام المحفوظ')
    except Exception:
        if created: path.unlink(missing_ok=True)
        raise BridgeError('تعذر حفظ ملف الانضمام والتحقق منه؛ اختر ملفاً جديداً قابلاً للكتابة') from None
