"""Task semantics shared by draft validation, publication, ward views and exports.

Completion identity is stamped by the authenticated clinical save layer; validating
a completed task here does not authorize a completion or provide trusted evidence.
"""
import copy
from datetime import date, datetime
import json
import re
import time
from typing import NamedTuple

TASK_PRIORITY = {'LOW': 'منخفضة', 'NORMAL': 'عادية', 'HIGH': 'عالية'}
COMPLETION = ('completedByDoctorId', 'completedByName', 'completedAtEpochMillis')
FIELDS = {'id', 'description', 'ownerDoctorId', 'ownerName', 'priority', 'dueAtEpochMillis',
          'dueShiftDate', 'done', *COMPLETION}


class TaskCounts(NamedTuple):
    pending: int
    overdue: int
    unassigned: int


def read_tasks(patient):
    return json.loads(patient.get('tasksJson') or '[]')


def overdue(task, now):
    return False  # Legacy deadline fields are compatible storage only.


def pending(tasks):
    return [task for task in tasks if not task.get('done')]


def counts(tasks, now=None):
    now = int(time.time()*1000) if now is None else now
    entries = pending(tasks)
    return TaskCounts(len(entries), 0, 0)


def display_key(task, now):
    return bool(task.get('done'))


def ordered_for_display(tasks, now=None):
    now = int(time.time()*1000) if now is None else now
    return sorted(tasks, key=lambda task: display_key(task, now))


def status_label(task, now):
    return 'مكتملة' if task.get('done') else 'معلقة'


def metadata(task, doctors, zone, include_unassigned=True, include_routine_priority=True):
    """Shared presentation rules matching Android PatientTasks.metadata.

    Legacy owner/deadline/priority fields do not affect checklist presentation.
    Formatting preserves completion details without supplying completion evidence.
    """
    from desktop_protocol import ZoneInfo
    parts = []
    if task.get('done'):
        parts.append('أتمها ' + task['completedByName'] if task.get('completedByName') else 'إتمام بانتظار الحفظ')
        if task.get('completedAtEpochMillis'):
            parts.append('أُتمت في ' + datetime.fromtimestamp(task['completedAtEpochMillis']/1000,
                                                           ZoneInfo(zone)).strftime('%Y-%m-%d %H:%M'))
    return ' · '.join(parts)


def task_text(task, doctors, zone, now=None, include_unassigned=True, include_routine_priority=True):
    now = int(time.time()*1000) if now is None else now
    details = metadata(task, doctors, zone, include_unassigned, include_routine_priority)
    return status_label(task, now) + ': ' + task['description'] + (' · ' + details if details else '')


def carry_forward(tasks, clear_owners=False):
    result = copy.deepcopy(pending(tasks))
    if clear_owners:
        for task in result: task.update(ownerDoctorId=None, ownerName=None)
    return result


def validate(tasks, require_completion=True):
    from desktop_protocol import require
    require(isinstance(tasks, list) and all(isinstance(task, dict) for task in tasks), 'قائمة مهام غير صالحة')
    for task in tasks:
        require(set(task) <= FIELDS and isinstance(task.get('id'), str) and task['id'].strip() and
                isinstance(task.get('description'), str) and task['description'].strip(), 'مهمة غير صالحة')
        require(task.get('priority', 'NORMAL') in TASK_PRIORITY and isinstance(task.get('done', False), bool),
                'حالة مهمة غير صالحة')
        for key in ('ownerDoctorId', 'ownerName', 'dueShiftDate', 'completedByDoctorId', 'completedByName'):
            require(task.get(key) is None or isinstance(task[key], str), 'هوية أو نص مهمة غير صالح')
        require(task.get('dueAtEpochMillis') is None or type(task['dueAtEpochMillis']) is int,
                'صيغة موعد قديم غير صالحة')
        if require_completion:
            completion = [task.get(key) for key in COMPLETION]
            if task.get('done'):
                require(all(completion) and completion[0].strip() and completion[1].strip() and
                        type(completion[2]) is int and completion[2] > 0, 'سجل إتمام المهمة غير مكتمل')
            else:
                require(all(value is None for value in completion), 'مهمة معلقة تحمل سجل إتمام')
    require(len({task['id'] for task in tasks}) == len(tasks), 'معرف مهمة مكرر')


def resolve_owner(task, doctors, previous):
    """Legacy ownership is preserved on import, without clinical authority checks."""
    return
