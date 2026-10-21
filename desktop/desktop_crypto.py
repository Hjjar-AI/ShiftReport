"""Crypto for the current shared protocol."""
from __future__ import annotations
import base64
import gzip
import hashlib
import io
import json
import secrets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol_common import MAGIC, LIMIT, BridgeError, require


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

