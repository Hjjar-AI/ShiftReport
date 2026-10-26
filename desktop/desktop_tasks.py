"""Task semantics shared by draft validation, publication, ward views and exports.

Tasks are plain text/done checklists; legacy fields remain readable.
"""
import json

TASK_PRIORITY = {'LOW': 'منخفضة', 'NORMAL': 'عادية', 'HIGH': 'عالية'}
COMPLETION = ('completedByDoctorId', 'completedByName', 'completedAtEpochMillis')
FIELDS = {'id', 'description', 'ownerDoctorId', 'ownerName', 'priority', 'dueAtEpochMillis',
          'dueShiftDate', 'done', *COMPLETION}


def read_tasks(patient):
    return json.loads(patient.get('tasksJson') or '[]')


def pending(tasks):
    return [task for task in tasks if not task.get('done')]


def pending_count(tasks):
    return len(pending(tasks))


def ordered_for_display(tasks):
    return sorted(tasks, key=lambda task: bool(task.get('done')))


def status_label(task):
    return 'مكتملة' if task.get('done') else 'معلقة'


def task_text(task):
    return status_label(task) + ': ' + task['description']


def validate(tasks):
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
    require(len({task['id'] for task in tasks}) == len(tasks), 'معرف مهمة مكرر')
