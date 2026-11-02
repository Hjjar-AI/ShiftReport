"""Receipt actions use the existing Workspace state/authorization and commit path."""
from __future__ import annotations
import copy
from desktop_protocol import BridgeError, is_admin, require

def receipts(owner, status=None, key=None):
    return [(i, copy.deepcopy(row)) for i, row in enumerate(owner.state['receipts'])
            if (status is None or row[2] == status) and (key is None or row[1] == key)]

def check_report_delivery(owner, shift_id, chat, topic):
    require(shift_id, 'معرف المناوبة مطلوب قبل الإرسال')
    for _, row in owner.receipts(status='UNKNOWN'):
        if row[0] != 'report' or row[4:6] != [str(chat), str(topic or '')]:
            continue
        require(len(row) > 9 and row[9], "سجل تسليم التقرير غير صالح")
        if row[9] == shift_id:
            raise BridgeError('نتيجة إرسال سابقة لهذه الوجهة غير مؤكدة؛ احسمها في سجل العمليات قبل أي إرسال جديد، حتى لو تغيرت الصيغة أو النسخة المنشورة')

def receipt_start(owner, key, operation, chat, topic, actor, repeat=False, snapshot=None, report_shift=None):
    from datetime import datetime
    if operation == 'report':
        owner.check_report_delivery(report_shift, chat, topic)
    for _, row in owner.receipts(key=key):
        require(row[2] == 'CONFIRMED_NOT_SENT' or repeat and row[2] in {'SENT', 'CHECKED_DO_NOT_REPEAT'},
                'هذه العملية أرسلت أو نتيجتها غير مؤكدة؛ راجع سجل العمليات قبل التكرار')
    rows = copy.deepcopy(owner.state['receipts'])
    row = [operation, key, 'UNKNOWN', '', str(chat), str(topic or ''), datetime.now().isoformat(timespec='seconds'),
           'snapshot=' + snapshot if snapshot else 'تحقق من تليجرام قبل الإعادة', actor]
    if operation == 'report': row.append(report_shift)
    rows.append(row)
    owner.commit(receipts=rows)
    owner._receipt = len(rows) - 1

def receipt_finish(owner, message):
    require(owner._receipt is not None, 'سجل العملية مفقود')
    owner.confirm_indexed_receipt(owner._receipt, message['message_id'])
    owner._receipt = None

def confirm_indexed_receipt(owner, index, message_id):
    rows = copy.deepcopy(owner.state['receipts'])
    require(rows[index][2] == 'UNKNOWN', 'حالة العملية تغيرت')
    rows[index][2:4] = ['SENT', str(message_id)]
    rows[index][7] = 'تأكيد تليجرام'
    owner.commit(receipts=rows)

def require_receipt_resolution(owner, index, client):
    require(not owner.locked, 'الجلسة مقفلة')
    require(client.project_id == owner.project, 'سجل العمليات يخص مشروعاً آخر')
    actor = client.require_local_identity()
    require(not owner.locked, 'الجلسة مقفلة')
    rows = owner.state['receipts']
    require(0 <= index < len(rows) and rows[index][2] == 'UNKNOWN', 'حالة العملية تغيرت')
    require(actor.get('uid') == rows[index][8] or is_admin(actor),
            'حسم العملية متاح لمن أرسلها أو لمدير نشط فقط')
    return copy.deepcopy(rows[index])

def resolve_unknown(owner, index, not_sent, client):
    owner.require_receipt_resolution(index,client)
    rows = copy.deepcopy(owner.state['receipts'])
    rows[index][2] = 'CONFIRMED_NOT_SENT' if not_sent else 'CHECKED_DO_NOT_REPEAT'
    owner.commit(receipts=rows)
