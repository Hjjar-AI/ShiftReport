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
    due = task.get('dueAtEpochMillis')
    return not task.get('done') and due is not None and due <= now


def pending(tasks):
    return [task for task in tasks if not task.get('done')]


def counts(tasks, now=None):
    now = int(time.time()*1000) if now is None else now
    entries = pending(tasks)
    return TaskCounts(len(entries), sum(overdue(task, now) for task in entries),
                      sum(not task.get('ownerDoctorId') for task in entries))


def display_key(task, now):
    done = bool(task.get('done'))
    return (0 if overdue(task, now) else 2 if done else 1,
            float('inf') if done else task.get('dueAtEpochMillis') or float('inf'),
            0 if done else -{'LOW': 0, 'NORMAL': 1, 'HIGH': 2}.get(task.get('priority', 'NORMAL'), 1))


def ordered_for_display(tasks, now=None):
    now = int(time.time()*1000) if now is None else now
    return sorted(tasks, key=lambda task: display_key(task, now))


def status_label(task, now):
    return 'مكتملة' if task.get('done') else 'متأخرة' if overdue(task, now) else 'معلقة'


def metadata(task, doctors, zone, include_unassigned=True, include_routine_priority=True):
    """Shared presentation rules matching Android PatientTasks.metadata.

    Compact PDFs omit routine priorities and unassigned labels, retaining every
    deadline and completion detail. Formatting never supplies completion evidence.
    """
    from desktop_protocol import ZoneInfo
    parts = []
    priority = task.get('priority', 'NORMAL')
    if include_routine_priority or priority == 'HIGH':
        parts.append('أولوية ' + TASK_PRIORITY[priority])
    owner = task.get('ownerDoctorId')
    if owner:
        parts.append(doctors.get(owner, {}).get('name') or task.get('ownerName') or 'طبيب محفوظ')
    elif include_unassigned:
        parts.append('غير معيّنة')
    if task.get('dueAtEpochMillis'):
        parts.append('الموعد ' + datetime.fromtimestamp(task['dueAtEpochMillis']/1000,
                                                       ZoneInfo(zone)).strftime('%Y-%m-%d %H:%M'))
    if task.get('dueShiftDate'):
        parts.append('نهاية مناوبة ' + task['dueShiftDate'])
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
        require(task.get('ownerDoctorId') is None or task['ownerDoctorId'].strip(), 'مسؤول مهمة غير صالح')
        due = task.get('dueAtEpochMillis')
        require(due is None or type(due) is int and due > 0, 'موعد مهمة غير صالح')
        if task.get('dueShiftDate') is not None:
            value = task['dueShiftDate']
            require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', value) is not None,
                    'مناوبة استحقاق المهمة يجب أن تكون YYYY-MM-DD')
            try:
                date.fromisoformat(value)
            except ValueError:
                require(False, 'مناوبة استحقاق غير صالحة')
            require(due is not None, 'موعد المهمة مطلوب مع مناوبة الاستحقاق')
        if require_completion:
            completion = [task.get(key) for key in COMPLETION]
            if task.get('done'):
                require(all(completion) and completion[0].strip() and completion[1].strip() and
                        type(completion[2]) is int and completion[2] > 0, 'سجل إتمام المهمة غير مكتمل')
            else:
                require(all(value is None for value in completion), 'مهمة معلقة تحمل سجل إتمام')
    require(len({task['id'] for task in tasks}) == len(tasks), 'معرف مهمة مكرر')


def resolve_owner(task, doctors, previous):
    """Retain historical owners only for already completed/unchanged assignments."""
    from desktop_protocol import require
    owner = task.get('ownerDoctorId')
    require(not owner or owner in doctors or task.get('done') and owner == previous.get('ownerDoctorId'),
            'مسؤول المهمة غير نشط')
    task['ownerName'] = doctors[owner]['name'] if owner in doctors else previous.get('ownerName') if owner else None
