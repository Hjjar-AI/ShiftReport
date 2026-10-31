"""Literal local preferences and the optional, explicit admin credential path."""
import base64
import re
from desktop_protocol import require

RAW_FIELDS = {'HOSPITAL_NAME', 'BOT_TOKEN', 'CHAT_ID', 'REPORTS_TOPIC_ID',
              'CSV_TOPIC_ID', 'DOCTORS_TOPIC_ID',
              'ENCRYPT_TELEGRAM', 'TELEGRAM_DATA_KEY', 'RESIDENTS_CHAT_ID', 'RESIDENTS_TOPIC_ID'}


def read_settings(path):
    allowed = {'JOIN_FILE', 'TIMEZONE', 'DATA_DIR', 'PDF_FONT', 'APPEARANCE',
               'TEXT_RENDERING', 'CONNECTION_MODE'} | RAW_FIELDS
    result = {}
    if path.is_file():
        require(path.stat().st_size <= 65536, '.env كبير جدا')
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            require('=' in line, 'سطر .env يجب أن يكون KEY=VALUE')
            key, value = (part.strip() for part in line.split('=', 1))
            require(key in allowed, 'إعداد .env غير مدعوم')
            require(key not in result, 'إعداد .env مكرر')
            if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "\'"}:
                value = value[1:-1]
            result[key] = value
    mode = result.get('CONNECTION_MODE') or 'join'
    require(mode in {'join', 'raw'}, 'CONNECTION_MODE يجب أن يكون join أو raw')
    require(mode == 'raw' or not any(result.get(key) for key in RAW_FIELDS),
            'استخدم CONNECTION_MODE=raw لتفعيل بيانات الاتصال المباشرة')
    if mode == 'raw':
        require(not result.get('JOIN_FILE'), 'اختر بيانات مباشرة أو ملف انضمام، وليس كليهما')
        raw_config(result)
    return result


def raw_config(settings):
    """Validate without echoing input values; never infer or generate a data key."""
    require(settings.get('HOSPITAL_NAME', '').strip(), 'HOSPITAL_NAME مطلوب')
    require(re.fullmatch(r'[1-9][0-9]*:[A-Za-z0-9_-]+', settings.get('BOT_TOKEN', '')),
            'BOT_TOKEN غير صالح')
    config = {'version': 1, 'hospitalName': settings['HOSPITAL_NAME'],
              'botToken': settings['BOT_TOKEN']}
    for field, target in [('CHAT_ID', 'chatId'), ('REPORTS_TOPIC_ID', 'reportsTopicId'),
                          ('CSV_TOPIC_ID', 'csvTopicId'), ('DOCTORS_TOPIC_ID', 'doctorsTopicId')]:
        value = settings.get(field) or ('0' if field != 'CHAT_ID' else '')
        require(re.fullmatch(r'-?[0-9]{1,20}', value), field + ' مطلوب كعدد صحيح')
        number = int(value)
        require(-(2**63) <= number < 2**63 and (number < 0 if field == 'CHAT_ID' else number >= 0),
                field + ' خارج النطاق المسموح')
        config[target] = number
    from desktop_destinations import parse_destination
    config['residentsChatId'], config['residentsTopicId'] = parse_destination(settings.get('RESIDENTS_CHAT_ID', ''), settings.get('RESIDENTS_TOPIC_ID', ''))
    encrypted = settings.get('ENCRYPT_TELEGRAM', '').lower()
    require(encrypted in {'true', 'false'}, 'حدد ENCRYPT_TELEGRAM=true أو false حسب المشروع')
    key = settings.get('TELEGRAM_DATA_KEY', '')
    require(bool(key) == (encrypted == 'true'), 'حالة التشفير ومفتاح المشروع غير متطابقين')
    if key:
        try:
            valid = len(base64.b64decode(key, validate=True)) == 32
        except Exception:
            valid = False
        require(valid, 'TELEGRAM_DATA_KEY يجب أن يكون مفتاح المشروع الأصلي Base64 بطول 32 بايت')
    config.update(encryptTelegram=encrypted == 'true', telegramDataKey=key)
    return config
