"""Device-local checklist mutations, separate from publication."""
import copy
import time
from desktop_protocol import current_date, packed, require
from desktop_model import validate_patient

from desktop_tasks import COMPLETION, read_tasks, validate as validate_tasks


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


def save_clinical(workspace, client, candidate, expected):
    before = workspace.read()
    require(not workspace.locked, 'الجلسة مقفلة')
    require(workspace.project == client.project_id, 'مساحة العمل تخص مشروعاً آخر')
    require(candidate['meta']['shiftId']==before['meta']['shiftId'] and candidate['meta']['shiftDate']==before['meta']['shiftDate'],
            'تغيرت المناوبة؛ لن تحفظ بيانات في مناوبة أخرى')
    after = copy.deepcopy(candidate)
    require(len(after['patients'])<=200 and len({p['externalId'] for p in after['patients']})==len(after['patients']), 'عدد مرضى أو معرّفات غير صالحة')
    old_rows = {p['externalId']:p for p in before['patients']}
    assignment_day = current_date(client.zone)
    for row in after['patients']:
        previous = old_rows.get(row['externalId'])
        row['assignmentChangedOn'] = (assignment_day if previous is None or
            row.get('responsibleSupervisorId') != previous.get('responsibleSupervisorId')
            else previous.get('assignmentChangedOn', ''))
        tasks = read_tasks(row)
        validate_tasks(tasks)
        previous_tasks={task['id']:task for task in read_tasks(previous or {})}
        for task in tasks:
            task['description'] = task['description'].strip()
            previous_task=previous_tasks.get(task['id'])
            if previous_task is None or bool(task.get('done'))!=bool(previous_task.get('done')):
                task.update(dict.fromkeys(COMPLETION))
            else:
                # Retain hidden stored stamps on unrelated saves, without
                # accepting new attribution from a candidate form/snapshot.
                for field in COMPLETION:
                    if field in previous_task:task[field]=previous_task[field]
                    else:task.pop(field,None)
        row['tasksJson'] = packed(tasks)
        validate_patient(row,client.doctors,client.zone)
        row.update(lastEditedByDoctorId='', lastEditedBy='', updatedAt='0',
                   revision=str(int(old_rows.get(row['externalId'],{}).get('revision') or 0)+1))
    workspace.commit(draft=after)
