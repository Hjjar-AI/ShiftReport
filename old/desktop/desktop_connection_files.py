"""Discover adjacent connection files without decrypting or contacting a project."""
import base64
import json
from pathlib import Path
from desktop_protocol import BridgeError, require
from desktop_settings import read_settings


def environment_settings(path):
    path = Path(path).resolve()
    require(path.is_file(), 'ملف الإعدادات المحدد غير موجود')
    settings = read_settings(path)
    for key in ('JOIN_FILE', 'DATA_DIR', 'PDF_FONT'):
        if settings.get(key):
            value = Path(settings[key]).expanduser()
            settings[key] = str(value if value.is_absolute() else path.parent / value)
    return settings


def has_connection(settings):
    return settings.get('CONNECTION_MODE') == 'raw' or bool(settings.get('JOIN_FILE'))


def protected_join_candidate(path):
    # An envelope check only. open_join still authenticates/decrypts after a
    # password prompt; never treat filename or JSON metadata as trusted config.
    try:
        with path.open('rb') as stream:
            raw = stream.read(1024 * 1024 + 1)
        if len(raw) > 1024 * 1024:
            return False
        envelope = json.loads(raw.decode('utf-8-sig'))
        if envelope.get('format') != 'ShiftReportJoin':
            return False
        payload = base64.b64decode(envelope['encryptedPayload'], validate=True)
        return payload.startswith(b'RUSHDJOIN1') and len(payload) >= 10 + 16 + 12 + 16
    except (OSError, ValueError, TypeError, KeyError, AttributeError):
        return False


def discover_connections(directory, explicit_env=None):
    """Return preferences, selectable (kind,path,settings) candidates, and a notice.

    Explicit --env is authoritative. Otherwise configured .env wins; preference-
    only .env can accompany a nearby join or another named *.env. Ambiguity is
    left to the UI, never resolved by silently choosing a project.
    """
    directory = Path(directory).resolve()
    if explicit_env is not None:
        settings = environment_settings(explicit_env)
        if has_connection(settings):
            return settings, [], ''
    else:
        settings = {}
    notice = ''
    default = directory / '.env'
    if explicit_env is None and default.is_file():
        try:
            settings = environment_settings(default)
        except (BridgeError, OSError, UnicodeError):
            notice = 'تعذر قراءة .env المجاور؛ اختر ملف اتصال صالحاً أو صحح إعداداته.'
        if has_connection(settings):
            return settings, [], notice
    candidates = []
    try:
        files = sorted(directory.iterdir(), key=lambda path: path.name.casefold())
    except OSError:
        return settings, [], 'تعذر البحث في مجلد التطبيق؛ اختر ملف الانضمام يدوياً.'
    for path in files:
        if not path.is_file():
            continue
        if path.name.lower().endswith('.srjoin.json') and protected_join_candidate(path):
            candidates.append(('join', path, {}))
        elif explicit_env is None and path != default and path.name.lower().endswith('.env'):
            try:
                found = environment_settings(path)
                if has_connection(found):
                    candidates.append(('env', path, found))
            except (BridgeError, OSError, UnicodeError):
                notice = 'تم تجاهل ملف إعدادات غير صالح في مجلد التطبيق؛ يمكنك اختيار ملف الانضمام يدوياً.'
    return settings, candidates, notice
