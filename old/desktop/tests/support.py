import base64
import copy
import json
from types import SimpleNamespace

KEY = bytes(range(32))


def client(project='a' * 64, key=KEY):
    actor = dict(uid='cedar', name='أرز', telegram_id=123, gender='M',clinicalRole='SUPERVISOR',
                 destination=-200,teamSupervisorId='', **{'#': '10', 'options': 'A10'})
    return SimpleNamespace(project_id=project, zone='Asia/Damascus', baseline=None,
                           config={'telegramDataKey': base64.b64encode(key).decode(),'hospitalName':'Dummy ward','chatId':-100,'reportsTopicId':3},
                           doctors={'cedar': actor}, baseline_key=lambda: key,
                           require_local_identity=lambda: copy.deepcopy(actor), demo=False)


def task(done=False, identity='task-1'):
    return dict(id=identity, description='قياس, "تجريبي"\nمراجعة ☕', priority='HIGH', done=done,
                ownerDoctorId=None, ownerName=None, dueAtEpochMillis=1800000000000,
                dueShiftDate='2027-01-15', completedByDoctorId='cedar' if done else None,
                completedByName='أرز' if done else None, completedAtEpochMillis=1700000000000 if done else None)


def shift():
    from desktop_protocol_common import COLUMNS
    row = dict.fromkeys(COLUMNS, '')
    row.update(externalId='tea', admitNum='12', admitDate='2020-01-01', gender='F', name='شاي',
               birthDate='1990', hasCompanion='Y', diagnosisType='DUAL', initialDiagnosis='تشخيص تجريبي',
               treatmentPlan='خطة, "تجريبية"\nسطر', followUp='متابعة\r\nتجريبية', labs='نتيجة ☕',
               labGroupsJson='[]', isPriority='N', lastEditedByDoctorId='cedar', lastEditedBy='أرز',
               updatedAt='1767225600000', revision='7', tasksJson=json.dumps([task(), task(True, 'done')], ensure_ascii=False))
    return dict(meta=dict(schemaVersion='8', shiftDate='2026-01-01', shiftId='synthetic-shift',
                          doctors='cedar', sortSpec='{"levels":[]}', shiftRevision='9',
                          snapshotId='snapshot', baseSnapshotId='base', deviceId='synthetic-device'), patients=[row])


def lab_groups():
    return [dict(id='cbc', title='تعداد الدم', date='2024-02-29', results='Hb=12.5 g/dL\nWBC=6000 /µL'),
            dict(id='renal', title='الكلى', date=None, results='Na=140 mmol/L\nK=4.1 mmol/L')]


def lab_group_shift():
    from desktop_labs import apply
    result=shift()
    apply(result['patients'][0],lab_groups())
    return result
