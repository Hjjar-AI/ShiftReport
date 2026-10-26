"""Desktop presentation and draft rules for the existing Android CSV model."""
from __future__ import annotations
import base64
import copy
from datetime import date, datetime
import json
import re
import uuid
from desktop_protocol import COLUMNS, EDITABLE, ZoneInfo, current_date, packed, require
from desktop_tasks import (read_tasks, pending_count, validate as validate_tasks,
                           ordered_for_display, task_text)

GENDER = {'M': 'ذكر', 'F': 'أنثى'}
YES = {'Y': 'نعم', 'N': 'لا'}
DIAGNOSIS = {'PSYCHIATRIC': 'نفسي', 'ADDICTION': 'إدمان', 'DUAL': 'نفسي + إدمان'}
BADGE_PRIORITY = {'none': 'دون مستوى', 'low': 'منخفضة', 'medium': 'متوسطة', 'high': 'عالية'}
LABELS = {'name': 'الاسم', 'admitNum': 'رقم القبول', 'admitDate': 'تاريخ القبول YYYY-MM-DD',
          'birthDate': 'سنة الميلاد', 'gender': 'الجنس', 'hasCompanion': 'مرافق', 'diagnosisType': 'الحالة المرضية',
          'initialDiagnosis': 'التشخيص', 'treatmentPlan': 'العلاج والحقن المديدة', 'followUp': 'المتابعة', 'labs': 'التحاليل',
          'responsibleResidentId': 'الطبيب المقيم', 'responsibleSupervisorId': 'الطبيب المشرف', 'isPriority': 'مريض ذو أولوية',
          'tasksJson': 'المهام', 'warningDetails': 'الشارات', 'warningFlags': 'مستويات الشارات', 'doctors': 'أطباء المناوبة', '__record__': 'سجل المريض / الحذف', 'order': 'ترتيب المرضى'}


def doctor_label(doctors, key):
    return doctors.get(key, {}).get('name', 'طبيب محفوظ') + ' [' + key + ']' if key else 'بدون'


def pending_task_count(patients):
    return pending_count([task for patient in patients for task in read_tasks(patient)])


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


def patient_name(row):
    return row.get('name') or 'مريض بلا اسم · '+row['externalId'][:8]


def new_patient(zone):
    row = {key: '' for key in COLUMNS}
    row.update(externalId=str(uuid.uuid4()), admitDate=datetime.now(ZoneInfo(zone)).date().isoformat(),
               gender='M', hasCompanion='N', diagnosisType='PSYCHIATRIC', isPriority='N', tasksJson='[]', revision='0')
    return row


def validate_patient(row, doctors, zone):
    today = datetime.now(ZoneInfo(zone)).date()
    if row['birthDate']:
        require(re.fullmatch(r'[0-9]{4}', row['birthDate']), 'أدخل سنة الميلاد بأربعة أرقام')
        require(1900 <= int(row['birthDate']) <= today.year and today.year-int(row['birthDate']) <= 120, 'سنة الميلاد خارج المجال')
    if row['admitDate']:
        require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', row['admitDate']), 'تاريخ القبول YYYY-MM-DD')
        try:
            require(date.fromisoformat(row['admitDate']) <= today, 'تاريخ قبول مستقبلي')
        except ValueError:
            require(False, 'تاريخ القبول غير صالح')
    for key, role in [('responsibleResidentId', 'RESIDENT'), ('responsibleSupervisorId', 'SUPERVISOR')]:
        value = row.get(key)
        require(not value or value in doctors and doctors[value]['clinicalRole'] == role, 'اختر طبيبا نشطا بالدور الصحيح')
    tasks = read_tasks(row)
    validate_tasks(tasks)


def patient_text(row, doctors, zone, clinical_only=False):
    if row is None:
        return 'محذوف / غير موجود'
    lines = []
    keys = (['initialDiagnosis','treatmentPlan','followUp','labs','responsibleResidentId','responsibleSupervisorId','isPriority'] if clinical_only else
            ['name','admitNum','admitDate','birthDate','gender','hasCompanion','diagnosisType','initialDiagnosis','treatmentPlan','followUp','labs','responsibleResidentId','responsibleSupervisorId','isPriority'])
    for key in keys:
        value = row.get(key, '')
        if clinical_only and (not value or key=='isPriority' and value!='Y'):continue
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
    for task in ordered_for_display(read_tasks(row)):
        lines.append('مهمة: ' + task_text(task))
    return '\n'.join(lines)


def shift_text(shift, doctors, zone, report_day=None):
    lines = ['تاريخ المناوبة: ' + (report_day or current_date(zone)),
             'أطباء المناوبة: ' + ' / '.join(doctor_label(doctors,key) for key in shift['meta'].get('doctors','').split(',') if key)]
    for index,row in enumerate(shift['patients'],1):
        lines += ['\n──────── {} ────────'.format(index), patient_text(row,doctors,zone)]
    return '\n'.join(lines)


def patient_edit_content(row):
    """Saved clinical identity excluding publication bookkeeping and derived doctor labels."""
    value = {key:row.get(key,'') for key in EDITABLE if key!='tasksJson'}
    value['tasks'] = [{key:value for key,value in task.items() if key not in {'ownerName','completedByDoctorId','completedByName','completedAtEpochMillis'}}
                      for task in json.loads(row.get('tasksJson') or '[]')]
    return value
