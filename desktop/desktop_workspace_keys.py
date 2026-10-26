"""Device-local workspace key protection; retain DPAPI/PBKDF2/AES-GCM behavior."""
from __future__ import annotations
import hashlib
import os
import secrets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_protocol import BridgeError, require

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
