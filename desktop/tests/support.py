import base64
import copy
import json
from types import SimpleNamespace

KEY = bytes(range(32))


def client(project='a' * 64, key=KEY):
    actor = dict(uid='cedar', name='أرز', telegram_id=123, **{'#': '10', 'options': 'A10'})
    return SimpleNamespace(project_id=project, zone='Asia/Damascus', baseline=None,
                           config={'telegramDataKey': base64.b64encode(key).decode()},
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
               isPriority='N', lastEditedByDoctorId='cedar', lastEditedBy='أرز',
               updatedAt='1767225600000', revision='7', tasksJson=json.dumps([task(), task(True, 'done')], ensure_ascii=False))
    return dict(meta=dict(schemaVersion='7', shiftDate='2026-01-01', shiftId='synthetic-shift',
                          doctors='cedar', sortSpec='{"levels":[]}', shiftRevision='9',
                          snapshotId='snapshot', baseSnapshotId='base', deviceId='synthetic-device'), patients=[row])
