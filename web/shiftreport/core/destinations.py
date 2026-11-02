"""Optional residents routing, separate from clinical identity and draft exports."""
import os
import secrets
from .protocol import fingerprint, is_admin, require

FIELDS = ('residentsChatId', 'residentsTopicId')


def parse_destination(group, topic):
    def number(raw, negative=False):
        raw = str(raw).strip()
        if not raw or raw == '0': return 0
        try: value = int(raw)
        except (ValueError, TypeError):
            raise ValueError('أدخل معرّفات المجموعة والموضوع كأعداد صحيحة') from None
        require(-(2**63) <= value < 2**63 and (value < 0 if negative else value > 0),
                'معرّف المجموعة سالب ومعرّف الموضوع موجب')
        return value
    group, topic = number(group, True), number(topic)
    require(group or not topic, 'أدخل مجموعة المقيمين عند تحديد موضوع لها')
    return group, topic


def validate_destination(client, destination=None):
    group, topic = destination if destination is not None else (client.config.get(key, 0) for key in FIELDS)
    require(isinstance(group, int) and not isinstance(group, bool) and -(2**63) <= group <= 0 and
            isinstance(topic, int) and not isinstance(topic, bool) and 0 <= topic < 2**63 and (group or not topic),
            'وجهة المقيمين غير صالحة')
    if not group: return
    chat = client.telegram.call('getChat', dict(chat_id=group))
    require(chat.get('id') == group and chat.get('type') in {'group', 'supergroup'}, 'وجهة المقيمين تحتاج مجموعة تليجرام')
    require(not topic or chat.get('is_forum'), 'اترك الموضوع فارغاً إذا كانت المجموعة بلا مواضيع')
    bot = client.telegram.call('getMe')
    member = client.telegram.call('getChatMember', dict(chat_id=group, user_id=bot['id']))
    require(member.get('status') in {'administrator', 'creator'}, 'اجعل البوت مديراً في مجموعة المقيمين أولاً')


def load_destination(workspace, client):
    """Restore local routing without adding a network gate to offline workspace access."""
    # Bind a local override to its source connection; a newly redistributed join file supersedes it.
    source = [client.config.get(key, 0) for key in FIELDS]
    client._residents_source = source
    path = workspace.path.with_suffix('.connection')
    if not path.is_file(): return
    record = workspace.decode(path.read_bytes())
    if record.get('source') == source:
        values = record['residents']
        group, topic = values
        require(isinstance(group, int) and isinstance(topic, int) and group <= 0 and topic >= 0 and (group or not topic), 'وجهة محفوظة غير صالحة')
        client.config.update(zip(FIELDS, values))


def save_destination(client, workspace, expected, group, topic):
    require(not workspace.locked, 'الجلسة مقفلة')
    def authorize():
        require(fingerprint(client.config) == expected, 'تغير الاتصال؛ أعد فتح الوجهة')
        actor = client.authorize(client.fetch_registry()['doctors'])
        require(is_admin(actor), 'تعديل الوجهة متاح للمدير فقط')
        return actor
    actor = authorize()
    validate_destination(client, (group, topic))
    require(authorize() == actor, 'تغير المستخدم؛ أعد المراجعة')
    path = workspace.path.with_suffix('.connection')
    temporary = path.with_name(path.name + '.' + secrets.token_hex(8) + '.tmp')
    record = dict(project=workspace.project, receipts=[],
                  source=getattr(client, '_residents_source', [client.config.get(key, 0) for key in FIELDS]),
                  residents=[group, topic])
    raw = workspace.encode(record)
    try:
        with temporary.open('xb') as stream:
            stream.write(raw); stream.flush(); os.fsync(stream.fileno())
        if os.name != 'nt': temporary.chmod(0o600)
        os.replace(str(temporary), str(path))
    finally:
        if temporary.exists(): temporary.unlink()
    client.config.update(residentsChatId=group, residentsTopicId=topic)


