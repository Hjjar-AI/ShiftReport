"""Desktop presentation and draft rules for the existing Android CSV model."""
from __future__ import annotations
import base64
import copy
from datetime import date, datetime
import json
import re
import time
import uuid
from desktop_protocol import COLUMNS, ZoneInfo, current_date, packed, require

GENDER = {'M': 'ذكر', 'F': 'أنثى'}
YES = {'Y': 'نعم', 'N': 'لا'}
DIAGNOSIS = {'PSYCHIATRIC': 'نفسي', 'ADDICTION': 'إدمان', 'DUAL': 'نفسي + إدمان'}
TASK_PRIORITY = {'LOW': 'منخفضة', 'NORMAL': 'عادية', 'HIGH': 'عالية'}
BADGE_PRIORITY = {'none': 'دون مستوى', 'low': 'منخفضة', 'medium': 'متوسطة', 'high': 'عالية'}
LABELS = {'name': 'الاسم', 'admitNum': 'رقم القبول', 'admitDate': 'تاريخ القبول YYYY-MM-DD',
          'birthDate': 'سنة الميلاد', 'gender': 'الجنس', 'hasCompanion': 'مرافق', 'diagnosisType': 'الحالة المرضية',
          'initialDiagnosis': 'التشخيص', 'treatmentPlan': 'العلاج والحقن المديدة', 'followUp': 'المتابعة', 'labs': 'التحاليل',
          'responsibleResidentId': 'الطبيب المقيم', 'responsibleSupervisorId': 'الطبيب المشرف', 'isPriority': 'مريض ذو أولوية',
          'tasksJson': 'المهام', 'warningDetails': 'الشارات', 'warningFlags': 'مستويات الشارات', 'doctors': 'أطباء المناوبة', '__record__': 'سجل المريض / الحذف', 'order': 'ترتيب المرضى'}


def doctor_label(doctors, key):
    return doctors.get(key, {}).get('name', 'طبيب محفوظ') + ' [' + key + ']' if key else 'بدون'


def task_counts(patients):
    tasks = [t for p in patients for t in json.loads(p.get('tasksJson') or '[]')]
    pending = [t for t in tasks if not t.get('done')]
    return len(pending), sum(bool(t.get('dueAtEpochMillis')) and t['dueAtEpochMillis'] <= time.time()*1000 for t in pending)


def badges_decode(value):
    if not value:
        return []
    require(value.startswith('badges:'), 'تمثيل الشارات غير مدعوم؛ لن يستبدل تلقائيا')
    result = []
    for item in value[7:].split(';'):
        level, encoded = item.split(':', 1)
        require(level in BADGE_PRIORITY, 'مستوى شارة غير صالح')
        result.append((base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)).decode('utf-8'), level))
    return result


def badges_encode(badges):
    return 'badges:' + ';'.join(level + ':' + base64.urlsafe_b64encode(text.encode('utf-8')).decode('ascii').rstrip('=')
                              for text, level in badges) if badges else ''


def new_patient(zone):
    row = {key: '' for key in COLUMNS}
    row.update(externalId=str(uuid.uuid4()), admitDate=datetime.now(ZoneInfo(zone)).date().isoformat(),
               gender='M', hasCompanion='N', diagnosisType='PSYCHIATRIC', isPriority='N', tasksJson='[]', revision='0')
    return row


def validate_patient(row, doctors, zone):
    require(row['name'].strip() and row['admitNum'].strip(), 'الاسم ورقم القبول مطلوبان')
    require(all(row.get(key, '').strip() for key in ['initialDiagnosis', 'treatmentPlan', 'followUp']),
            'التشخيص والعلاج والمتابعة مطلوبة لحفظ المريض')
    today = datetime.now(ZoneInfo(zone)).date()
    require(re.fullmatch(r'[0-9]{4}', row['birthDate']), 'أدخل سنة الميلاد بأربعة أرقام')
    require(1900 <= int(row['birthDate']) <= today.year and today.year-int(row['birthDate']) <= 120, 'سنة الميلاد خارج المجال')
    require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', row['admitDate']), 'تاريخ القبول YYYY-MM-DD')
    try:
        require(date.fromisoformat(row['admitDate']) <= today, 'تاريخ قبول مستقبلي')
    except ValueError:
        require(False, 'تاريخ القبول غير صالح')
    for key, role in [('responsibleResidentId', 'RESIDENT'), ('responsibleSupervisorId', 'SUPERVISOR')]:
        value = row.get(key)
        require(not value or value in doctors and doctors[value]['clinicalRole'] == role, 'اختر طبيبا نشطا بالدور الصحيح')
    tasks = json.loads(row.get('tasksJson') or '[]')
    require(len({t['id'] for t in tasks}) == len(tasks), 'معرف مهمة مكرر')
    for task in tasks:
        require(task['description'].strip(), 'وصف المهمة مطلوب')
        require(task.get('priority', 'NORMAL') in TASK_PRIORITY, 'أولوية المهمة غير صالحة')
        require(task.get('dueAtEpochMillis') is None or task['dueAtEpochMillis'] > 0, 'موعد المهمة غير صالح')
        if task.get('dueShiftDate'):
            try:
                date.fromisoformat(task['dueShiftDate'])
            except ValueError:
                require(False, 'مناوبة استحقاق غير صالحة')
            require(task.get('dueAtEpochMillis'), 'موعد مطلوب مع مناوبة الاستحقاق')
        require(not task.get('ownerDoctorId') or task['ownerDoctorId'] in doctors or task.get('done'), 'مسؤول المهمة غير نشط')


def carry_forward(source, zone, keep):
    shift = {'meta': {'schemaVersion': '7', 'shiftDate': current_date(zone), 'shiftId': str(uuid.uuid4()), 'doctors': ''}, 'patients': []}
    for source_row in source['patients']:
        row = copy.deepcopy(source_row)
        row.update(externalId=str(uuid.uuid4()), revision='0', lastEditedBy='', lastEditedByDoctorId='', updatedAt='')
        tasks = [t for t in json.loads(row.get('tasksJson') or '[]') if not t.get('done')]
        if not keep:
            row.update(responsibleResidentId='', responsibleSupervisorId='')
            for task in tasks:
                task.update(ownerDoctorId=None, ownerName=None)
        row['tasksJson'] = packed(tasks)
        shift['patients'].append(row)
    return shift


def patient_text(row, doctors, zone):
    if row is None:
        return 'محذوف / غير موجود'
    lines = []
    for key in ['name','admitNum','admitDate','birthDate','gender','hasCompanion','diagnosisType','initialDiagnosis','treatmentPlan','followUp','labs','responsibleResidentId','responsibleSupervisorId','isPriority']:
        value = row.get(key, '')
        if key in ['responsibleResidentId','responsibleSupervisorId']:
            value = doctor_label(doctors, value)
        elif key == 'gender': value = GENDER.get(value,value)
        elif key == 'diagnosisType': value = DIAGNOSIS.get(value,value)
        elif key in ['hasCompanion','isPriority']: value = YES.get(value,value)
        lines.append(LABELS[key] + ': ' + value)
    from desktop_teams import is_cross_team_cover
    if is_cross_team_cover(row,doctors):lines.append('تغطية فردية من فريق آخر؛ عضوية الفريق الثابتة لا تتغير')
    for text, level in badges_decode(row.get('warningDetails', '')):
        lines.append('شارة: ' + text + ' · ' + BADGE_PRIORITY[level])
    for task in json.loads(row.get('tasksJson') or '[]'):
        parts = [('مكتملة' if task.get('done') else 'معلقة') + ': ' + task['description'],
                 'أولوية ' + TASK_PRIORITY.get(task.get('priority','NORMAL'), '')]
        if task.get('ownerDoctorId'):
            parts.append(doctors.get(task['ownerDoctorId'], {}).get('name', task.get('ownerName') or 'طبيب محفوظ'))
        for key, label in [('dueAtEpochMillis','الموعد'),('completedAtEpochMillis','أتمت في')]:
            if task.get(key): parts.append(label + ' ' + datetime.fromtimestamp(task[key]/1000,ZoneInfo(zone)).strftime('%Y-%m-%d %H:%M'))
        if task.get('dueShiftDate'): parts.append('نهاية مناوبة ' + task['dueShiftDate'])
        if task.get('completedByName'): parts.append('أتمها ' + task['completedByName'])
        lines.append('مهمة: ' + ' · '.join(parts))
    return '\n'.join(lines)


def shift_text(shift, doctors, zone):
    lines = ['تاريخ المناوبة: ' + shift['meta']['shiftDate'],
             'أطباء المناوبة: ' + ' / '.join(doctor_label(doctors,key) for key in shift['meta'].get('doctors','').split(',') if key)]
    for index,row in enumerate(shift['patients'],1):
        lines += ['\n──────── {} ────────'.format(index), patient_text(row,doctors,zone)]
    return '\n'.join(lines)
