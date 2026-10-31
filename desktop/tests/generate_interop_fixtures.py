"""Generate synthetic desktop-to-Android fixtures using the production Python codecs."""
from pathlib import Path
import base64
import hashlib
import json
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_backup import encode
from desktop_crypto import Cipher
from desktop_wire import encode_bundle
from tests.support import KEY, shift


def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    config = dict(version=1, hospitalName='مشروع تجريبي', botToken='12345:' + 'a' * 20,
                  chatId=-100, reportsTopicId=1, csvTopicId=3, doctorsTopicId=4, residentsChatId=-300, residentsTopicId=7,
                  encryptTelegram=True, telegramDataKey=base64.b64encode(KEY).decode())
    password = 'synthetic-password'
    cipher = Cipher(config)
    plain = encode_bundle([shift()])
    (destination / 'desktop.bundle.csv').write_bytes(plain)
    (destination / 'desktop.srdata').write_bytes(cipher.encrypt(plain, 'file'))
    (destination / 'desktop-text.txt').write_text(cipher.encode_text('شاي — نتيجة تجريبية ☕'))
    (destination / 'desktop.rpb').write_bytes(encode(shift(), password))
    salt, nonce = bytes(range(16)), bytes(range(12))
    key = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 210000, 32)
    payload = b'RUSHDJOIN1' + salt + nonce + AESGCM(key).encrypt(nonce, json.dumps(config).encode(), None)
    (destination / 'desktop.srjoin.json').write_text(json.dumps(dict(format='ShiftReportJoin',
        encryptedPayload=base64.b64encode(payload).decode())))
    (destination / 'synthetic-config.json').write_text(json.dumps(config, ensure_ascii=False))


if __name__ == '__main__':
    generate(Path(__file__).resolve().parents[2] / 'android/app/src/androidTest/assets/interop')
