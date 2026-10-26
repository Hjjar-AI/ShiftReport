"""Publication using the existing Client session and reviewed snapshot."""
from __future__ import annotations
import copy
from dataclasses import dataclass, field
import uuid
from typing import Optional
from desktop_protocol_common import require, packed, fingerprint, pointer, doctor_pointer
from desktop_wire import encode_bundle, validate_wire_patient


@dataclass
class Review:
    baseline_pointer: tuple
    registry_pointer: tuple
    shift: dict
    bundle: list
    remote: dict
    conflicts: list = field(default_factory=list)
    force_local: bool = False
    force_required: bool = False
    _approval: Optional[str] = field(default=None, repr=False)
    actor_id: Optional[str] = field(default=None, repr=False)


def plan(owner, shift, choices=None):
    require(len({p['externalId'] for p in shift['patients']}) == len(shift['patients']), 'معرف مريض مكرر في الجدول')
    remote = owner.fetch()
    owner.authorize(remote['doctors'])
    selected_id = shift['meta']['shiftId']
    other_ids = {p['externalId'] for s in remote['bundle'] if s['meta']['shiftDate'] != shift['meta']['shiftDate'] for p in s['patients']}
    require(not any(p['externalId'] in other_ids for p in shift['patients']), 'معرف مريض مستخدم في مناوبة أخرى')
    base_shift = next((s for s in (owner.baseline or {}).get('bundle', []) if s['meta']['shiftId'] == selected_id), None)
    latest = next((s for s in remote['bundle'] if s['meta']['shiftDate'] == shift['meta']['shiftDate']), None)
    same_date = [s for s in remote['bundle'] if s['meta']['shiftDate'] == shift['meta']['shiftDate']]
    # The reviewed local shift is the entire replacement, including deletions,
    # roster and stored order. Remote fields/additions are never silently merged.
    output = copy.deepcopy(shift)
    plan = Review(pointer(remote['state']), doctor_pointer(remote['state']), output, remote['bundle'], remote, [])
    plan.force_required = any(s['meta']['shiftId'] != selected_id for s in same_date) or (base_shift is not None and latest is None)
    return plan


def prepare(owner, plan):
    require(not plan.conflicts, 'راجع كل التعارضات أولا')
    owner.authorize(plan.remote['doctors'])
    doctors = plan.remote['doctors']
    shift = copy.deepcopy(plan.shift)
    from desktop_sort import ordered
    ordered(shift, doctors, owner.zone)
    latest = next((s for s in plan.bundle if s['meta']['shiftDate'] == shift['meta']['shiftDate']), None)
    previous = {row['externalId']: row for row in latest['patients']} if latest else {}
    require(len(shift['patients']) <= 200, 'الحد الأقصى للمناوبة 200 مريض')
    roster = shift['meta'].get('doctors', '').split(',')
    require(roster and all(d in doctors for d in roster), 'حدد أطباء مناوبة نشطين')
    require(len(set(roster)) == len(roster), 'طبيب مكرر في المناوبة')
    from desktop_model import validate_patient, badges_decode
    from desktop_tasks import read_tasks, COMPLETION
    for row in shift['patients']:
        validate_patient(row, doctors, owner.zone)
        tasks = read_tasks(row)
        for task in tasks:
            task.update(dict.fromkeys(COMPLETION))
        row['tasksJson'] = packed(tasks)
        row['warningFlags'] = '|'.join(level for _,level in badges_decode(row.get('warningDetails', '')) if level != 'none')
        validate_wire_patient(row)
        old = previous.get(row['externalId'], {})
        row.update(lastEditedByDoctorId='', lastEditedBy='', updatedAt='0',
                   revision=str(max(int(row.get('revision') or 0), int(old.get('revision') or 0)+1)))
    meta = shift['meta']
    meta.update(schemaVersion='7', snapshotId=str(uuid.uuid4()), baseSnapshotId=latest['meta'].get('snapshotId', '') if latest else '',
                shiftRevision=str(int(latest['meta'].get('shiftRevision') or 0) + 1) if latest else '1', deviceId=owner.device,
                sortSpec=meta.get('sortSpec') or '{"levels":[]}')
    plan.shift = shift
    plan.actor_id = owner._actor
    plan._approval = None
    return plan


def approve(owner, plan):
    plan._approval = fingerprint(dict(shift=plan.shift, force_local=plan.force_local))


def publish(owner, plan, before_upload):
    require(plan.actor_id == owner._actor, 'تغير الطبيب بعد المراجعة؛ أعدها')
    require(plan._approval == fingerprint(dict(shift=plan.shift, force_local=plan.force_local)), 'تغيرت البيانات بعد المراجعة؛ راجعها مجددا')
    require(not plan.force_required or plan.force_local, 'راجع النسخة المحلية وأكد نشرها لاستبدال مناوبة اليوم المنشورة')
    current = owner.fetch()
    owner.authorize(current['doctors'])
    require((plan.force_local or pointer(current['state']) == plan.baseline_pointer) and doctor_pointer(current['state']) == plan.registry_pointer,
            'وصلت بيانات أحدث بعد المراجعة؛ أعد المراجعة')
    bundle = [plan.shift]
    raw = encode_bundle(bundle)
    before_upload()  # Persist an UNKNOWN receipt before a potentially ambiguous send.
    uploaded = owner.telegram.upload(raw, 'ShiftReport.csv', owner.config.get('csvTopicId'), 'نسخة المناوبات من سطح المكتب')
    # Preserve other pointers by re-reading and never manufacturing a fresh empty index.
    index_id, state = owner.telegram.state()
    require((plan.force_local or pointer(state) == plan.baseline_pointer) and doctor_pointer(state) == plan.registry_pointer,
            'وصل نشر أثناء الرفع؛ الملف رفع لكن الفهرس لم يتغير. أعد المراجعة واختر نشر نسختي المحلية دون تنزيل المرضى')
    owner.authorize(current['doctors'])
    at = int(uploaded['date']) * 1000
    previous = copy.deepcopy(state)
    rotate = True  # Keep the immediate previous publication for confirmed rollback.
    if rotate:
        for old, new in [('csvFileId','previousCsvFileId'),('csvMessageId','previousCsvMessageId'),('csvDate','previousCsvDate'),('csvUpdatedAt','previousCsvUpdatedAt')]:
            state[new] = previous.get(old, 0 if old == 'csvUpdatedAt' else None)
    state.update(csvFileId=uploaded['document']['file_id'], csvMessageId=uploaded['message_id'], csvDate=plan.shift['meta']['shiftDate'], csvUpdatedAt=at)
    journal = {'snapshotId': plan.shift['meta']['snapshotId'], 'parentSnapshotId': plan.shift['meta'].get('baseSnapshotId') or None,
               'fileId': state['csvFileId'], 'messageId': state['csvMessageId'], 'at': at, 'deviceId': owner.device, 'forced': plan.force_local}
    state['publicationJournal'] = sorted(state.get('publicationJournal', []) + [journal], key=lambda v: v['messageId'], reverse=True)[:12]
    committed = owner.telegram.write_state(index_id, state)
    owner.baseline = {'index_id': index_id, 'state': committed, 'doctors': current['doctors'], 'bundle': bundle}
    owner.doctors = current['doctors']
    return uploaded

