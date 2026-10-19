"""Device-local clinical mutations and trusted completion evidence, separate from publication."""
import copy
import json
import time
from desktop_protocol import current_date, fingerprint, packed, require
from desktop_model import LABELS, validate_patient, patient_edit_content

from desktop_tasks import COMPLETION, read_tasks, validate as validate_tasks, resolve_owner


def local_identity(workspace, client):
    require(not workspace.locked, 'الجلسة مقفلة')
    require(workspace.project == client.project_id, 'مساحة العمل تخص مشروعاً آخر')
    if getattr(client,'demo',False):return next(iter(client.doctors.values()),None)
    key = getattr(workspace,'local_actor',None)
    record = workspace.state.get('localAccess',{}).get('pins',{}).get(key)
    doctor = client.doctors.get(key)
    if doctor and record and record.get('telegram') == doctor.get('telegram_id'):return doctor
    key = client._actor
    doctor = client.doctors.get(key)
    if (doctor and time.time() < getattr(client,'_expires',0) and doctor.get('telegram_id') and
            doctor['telegram_id'] == getattr(client,'_verified_telegram',None)):return doctor
    return None


def event(shift_id, actor, action, patient=None, fields=None, task=None):
    return dict(at=int(time.time()*1000),shiftId=shift_id,action=action,
                actorId=actor.get('uid') if actor else None,actorName=actor.get('name') if actor else None,
                patientId=patient.get('externalId') if patient else None,patientName=patient.get('name') if patient else None,
                fields=list(fields or []),taskId=task.get('id') if task else None,
                taskDescription=task.get('description') if task else None)


def save_clinical(workspace, client, candidate, expected):
    actor = local_identity(workspace,client)
    before = workspace.read()
    require(fingerprint(before)==expected, 'تغيرت المسودة؛ أعد فتح التحرير')
    require(candidate['meta']['shiftId']==before['meta']['shiftId'] and candidate['meta']['shiftDate']==before['meta']['shiftDate'],
            'تغيرت المناوبة؛ لن تحفظ بيانات في مناوبة أخرى')
    require(before['meta']['shiftDate']==current_date(client.zone), 'المناوبات السابقة للعرض فقط')
    after = copy.deepcopy(candidate);key=after['meta']['shiftId']
    require(len(after['patients'])<=200 and len({p['externalId'] for p in after['patients']})==len(after['patients']), 'عدد مرضى أو معرّفات غير صالحة')
    old_rows = {p['externalId']:p for p in before['patients']}
    activity = copy.deepcopy(workspace.state.get('clinicalActivity',[]))
    evidence = [entry for entry in workspace.state.get('localCompletions',[]) if entry['shiftId']!=key]
    for row in after['patients']:
        old = old_rows.get(row['externalId'])
        old_tasks = {t['id']:t for t in json.loads((old or {}).get('tasksJson') or '[]')}
        tasks = read_tasks(row)
        validate_tasks(tasks, require_completion=False)
        for task in tasks:
            previous = old_tasks.get(task['id'],{})
            resolve_owner(task,client.doctors,previous)
            task['description'] = task['description'].strip()
            if not task.get('done'):
                task.update(dict.fromkeys(COMPLETION))
            elif previous.get('done'):
                require(all(previous.get(field) for field in COMPLETION), 'سجل الإتمام ناقص؛ أعد فتح المهمة واحفظ إتمامها بهوية محلية')
                task.update({field:previous[field] for field in COMPLETION})
            else:
                require(actor is not None, 'سجّل الدخول محلياً أو عبر تليجرام قبل إتمام المهمة')
                task.update(completedByDoctorId=actor['uid'],completedByName=actor['name'],completedAtEpochMillis=int(time.time()*1000))
            if bool(task.get('done')) != bool(previous.get('done')):
                activity.append(event(key,actor,'task_completed' if task.get('done') else 'task_reopened',row,task=task))
        validate_tasks(tasks)
        row['tasksJson'] = packed(tasks)
        validate_patient(row,client.doctors,client.zone)
        completed = [t for t in tasks if t.get('done')]
        if completed:evidence.append(dict(shiftId=key,patientId=row['externalId'],tasks=copy.deepcopy(completed),local=True))
        fields = [LABELS.get(k,k) for k in LABELS if (old or {}).get(k,'')!=row.get(k,'') and k not in {'warningFlags','doctors','__record__','order'}]
        changed = old is None or patient_edit_content(old)!=patient_edit_content(row)
        if changed:
            row.update(lastEditedByDoctorId=actor['uid'] if actor else '',lastEditedBy=actor['name'] if actor else '',
                       updatedAt=str(int(time.time()*1000)),revision=str(int((old or {}).get('revision') or 0)+1))
            activity.append(event(key,actor,'patient_added' if old is None else 'patient_edited',row,fields))
    for old in before['patients']:
        if not any(p['externalId']==old['externalId'] for p in after['patients']):activity.append(event(key,actor,'patient_deleted',old))
    if before['meta'].get('doctors','')!=after['meta'].get('doctors',''):
        activity.append(event(key,actor,'roster_changed'))
    if ([p['externalId'] for p in before['patients']] != [p['externalId'] for p in after['patients']] or
            before['meta'].get('sortSpec','')!=after['meta'].get('sortSpec','')):
        activity.append(event(key,actor,'order_changed'))
    workspace.commit(draft=after,localCompletions=evidence,clinicalActivity=activity)


def completion_evidence(workspace):
    """Only match stamps already saved in the encrypted workspace, never arbitrary form values."""
    result = copy.deepcopy(workspace.state.get('restored',[]))
    draft = workspace.read();key=draft['meta']['shiftId']
    patients = {p['externalId']:p for p in draft['patients']}
    for entry in workspace.state.get('localCompletions',[]):
        if entry['shiftId']!=key or entry['patientId'] not in patients:continue
        tasks={t['id']:t for t in json.loads(patients[entry['patientId']].get('tasksJson') or '[]')}
        valid=[t for t in entry['tasks'] if tasks.get(t['id'],{}).get('done') and
               all(tasks[t['id']].get(field)==t.get(field) for field in COMPLETION)]
        if valid:result.append(dict(shiftId=key,patientId=entry['patientId'],tasks=copy.deepcopy(valid),local=True))
    return result
