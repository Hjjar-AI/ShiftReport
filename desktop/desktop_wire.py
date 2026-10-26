"""Wire for the current shared protocol."""
from __future__ import annotations
import csv
from datetime import date
import io
import json
import re
from desktop_protocol_common import COLUMNS, BOUNDARY, BUNDLE, LIMIT, BridgeError, require, packed


def parse_doctors(raw):
    try:
        body, last = raw.decode('utf-8-sig').strip().rsplit('@', 1)
        rows = body.split('|')
        header = rows.pop(0).split(',')
        expected = '#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId'.split(',')
        require(header in (expected, expected+['teamSupervisorId']), 'مطلوب سجل الأطباء الحالي')
        result = {}
        for line in rows:
            values = line.split(',')
            require(len(values) == len(header), 'سطر أطباء ناقص')
            row = dict(zip(header, values))
            require(row['uid'] and row['uid'] not in result and row['name'].strip(), 'هوية طبيب مكررة أو ناقصة')
            require(row['clinicalRole'] in {'RESIDENT', 'SUPERVISOR'}, 'دور طبيب غير صالح')
            row['telegram_id'] = int(row['id']) if row['id'] else None
            require(row['telegram_id'] is None or row['telegram_id'] > 0, 'هوية تليجرام غير صالحة')
            row['destination'] = int(row['supervisorGroupChatId']) if row['supervisorGroupChatId'] else None
            require(row['destination'] is None or row['clinicalRole'] == 'SUPERVISOR' and row['destination'] < 0, 'وجهة مشرف غير صالحة')
            row['teamSupervisorId'] = row.get('teamSupervisorId','')
            result[row['uid']] = row
        require(result, 'سجل الأطباء فارغ')
        from desktop_teams import validate_teams
        return validate_teams(result)
    except BridgeError:
        raise
    except Exception:
        raise BridgeError('سجل الأطباء غير صالح') from None


def validate_wire_patient(patient):
    """Reject malformed shared records before replacing any saved local draft."""
    require(patient.get('externalId', '').strip(), 'هوية مريض ناقصة')
    require(patient['gender'] in {'M', 'F'} and patient['hasCompanion'] in {'Y', 'N'} and
            patient['diagnosisType'] in {'PSYCHIATRIC', 'ADDICTION', 'DUAL'} and
            patient.get('isPriority', 'N') in {'Y', 'N'}, 'تصنيف مريض غير صالح')
    if patient['admitDate']:date.fromisoformat(patient['admitDate'])
    if patient.get('assignmentChangedOn'):
        require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', patient['assignmentChangedOn']), 'تاريخ تغيير توزيع المريض غير صالح')
        date.fromisoformat(patient['assignmentChangedOn'])
    if patient['birthDate']:
        require(re.fullmatch(r'[0-9]{4}', patient['birthDate']), 'سنة الميلاد غير صالحة')
        require(1900 <= int(patient['birthDate']) <= 2100, 'سنة الميلاد غير صالحة')
    require(int(patient.get('revision') or 0) >= 0, 'نسخة مريض غير صالحة')
    from desktop_tasks import read_tasks, validate as validate_tasks
    validate_tasks(read_tasks(patient))
    from desktop_model import badges_decode
    badges_decode(patient.get('warningDetails', ''))


def parse_bundle(raw):
    require(len(raw) <= LIMIT, 'حزمة المرضى كبيرة جدا')
    try:
        text = raw.decode('utf-8-sig')
        require(text.splitlines()[0] == BUNDLE, 'مطلوب تنسيق حزمة المناوبات الحالي')
        parts = text.split('\n', 1)[1].split('\n' + BOUNDARY + '\n')
        require(1 <= len(parts) <= 3, 'عدد المناوبات في الحزمة غير صالح')
        result = []
        for part in parts:
            rows = list(csv.reader(io.StringIO(part.strip(), newline='')))
            meta = dict(token.split('=', 1) for token in rows[0][0].lstrip('# ').split(' ') if '=' in token)
            require(meta.get('schemaVersion') == '7' and rows[1] in (COLUMNS, COLUMNS[:-1]), 'مطلوب CSV المرضى الحالي بإصدار 7')
            require(all(key in meta for key in ['shiftDate','shiftId','doctors','sortSpec','shiftRevision','snapshotId','baseSnapshotId','deviceId']), 'بيانات تعريف CSV ناقصة؛ أعد النشر من Android بعد إصلاح اقتباس metadata')
            require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}',meta['shiftDate']) is not None,'تاريخ المناوبة يجب أن يكون YYYY-MM-DD')
            date.fromisoformat(meta['shiftDate'])
            require(meta.get('shiftId'), 'معرف المناوبة مفقود')
            patients = []
            for row in rows[2:]:
                if not row or not any(row):
                    continue
                require(len(row) == len(rows[1]), 'سطر مرضى غير مكتمل')
                patient = dict(zip(rows[1], row))
                patient.setdefault('assignmentChangedOn', '')
                validate_wire_patient(patient)
                patient['tasksJson'] = packed(json.loads(patient['tasksJson'] or '[]'))
                patients.append(patient)
            require(len({r['externalId'] for r in patients}) == len(patients), 'معرف مريض مكرر')
            result.append({'meta': meta, 'patients': patients})
        require(len({s['meta']['shiftId'] for s in result}) == len(result), 'معرف مناوبة مكرر')
        return result
    except BridgeError:
        raise
    except Exception:
        raise BridgeError('حزمة المرضى غير صالحة') from None


def encode_bundle(shifts):
    parts = []
    for shift in sorted(shifts, key=lambda s: s['meta']['shiftDate'], reverse=True)[:3]:
        meta = shift['meta']
        require(not any(any(ch.isspace() for ch in str(v)) for v in meta.values()), 'بيانات تعريف المناوبة غير صالحة')
        stream = io.StringIO(newline='')
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['# ' + ' '.join(k + '=' + str(v) for k, v in meta.items())])
        writer.writerow(COLUMNS)
        for row in shift['patients']:
            require(not any('\n' + BOUNDARY in str(v) for v in row.values()), 'نص يتعارض مع فاصل الحزمة')
            writer.writerow([row.get(k, '') for k in COLUMNS])
        parts.append(stream.getvalue().rstrip('\n'))
    result = (BUNDLE + '\n' + ('\n' + BOUNDARY + '\n').join(parts) + '\n').encode()
    require(len(result) <= LIMIT, 'الحزمة الناتجة أكبر من الحد')
    return result

