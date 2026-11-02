"""Ordered dated lab groups, shared by editor, current wire format and reports."""
from datetime import date
import json
import re
from desktop_protocol_common import require, packed


def validate(groups):
    require(isinstance(groups, list), 'مجموعات التحاليل غير صالحة')
    ids = set()
    for group in groups:
        require(isinstance(group, dict) and set(group) == {'id', 'title', 'date', 'results'}, 'مجموعة تحاليل غير صالحة')
        require(all(isinstance(group[k], str) for k in ('id', 'title', 'results')), 'نص مجموعة التحاليل غير صالح')
        require(group['id'].strip() and group['id'] not in ids, 'معرّف مجموعة التحاليل فارغ أو مكرر')
        ids.add(group['id'])
        value = group['date']
        require(value is None or isinstance(value, str) and re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', value), 'تاريخ التحاليل غير صالح')
        if value is not None:date.fromisoformat(value)
    return groups


def read_groups(row):
    groups = validate(json.loads(row.get('labGroupsJson') or '[]'))
    if groups:require(row.get('labs', '') == text(groups), 'نص التحاليل لا يطابق المجموعات')
    if not groups and row.get('labs'):
        groups = [dict(id=row['externalId']+'-labs', title='', date=None, results=row['labs'])]
    return groups


def heading(group):
    return ' • '.join(value for value in (group['title'], group['date']) if value and value.strip())


def text(groups):
    return '\n\n'.join('\n'.join(value for value in (heading(group), group['results']) if value)
                       for group in groups if heading(group) or group['results'])


def apply(row, groups):
    validate(groups)
    row['labGroupsJson'] = packed(groups)
    row['labs'] = text(groups)
