"""Telegram transport for the current shared protocol."""
from __future__ import annotations
import copy
import json
import requests
from desktop_protocol_common import LIMIT, BridgeError, UncertainDelivery, MissingProjectIndex, require, packed, pointer, doctor_pointer
from desktop_crypto import Cipher


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
        while len(text.encode('utf-16-le'))//2>3500 and state.get('publicationJournal'):
            state['publicationJournal'].pop()
            text=self.cipher.encode_text(packed(state))
        require(len(text.encode('utf-16-le')) // 2 <= 3500, 'فهرس المشروع أطول من الحد الآمن للرسالة')
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
