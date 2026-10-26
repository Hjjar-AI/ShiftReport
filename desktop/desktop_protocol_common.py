"""Shared protocol constants, errors and identity/pointer utilities; no network I/O."""
from __future__ import annotations
import csv
from datetime import datetime, timedelta
import hashlib
import json
import re
try:
    from zoneinfo import ZoneInfo
except ImportError:  # Python 3.8 / Windows 7 distribution.
    from backports.zoneinfo import ZoneInfo

COLUMNS = 'externalId,admitNum,admitDate,gender,name,birthDate,hasCompanion,diagnosisType,initialDiagnosis,treatmentPlan,followUp,labs,responsibleResidentId,responsibleSupervisorId,warningFlags,warningDetails,isPriority,lastEditedByDoctorId,lastEditedBy,updatedAt,revision,tasksJson,assignmentChangedOn'.split(',')

EDITABLE = [key for key in COLUMNS[:17] if key != 'warningFlags'] + ['tasksJson']

BOUNDARY = '#==RUSHD_SHIFT_BOUNDARY_7F3A9C=='

BUNDLE = '# rushdShiftBundle=1'

MAGIC = b'SRDATA1'

LIMIT = 8 * 1024 * 1024

csv.field_size_limit(LIMIT)

class BridgeError(Exception):
    """Message is safe to display; never include a URL, token, key or payload."""


class UncertainDelivery(BridgeError):
    pass


class MissingProjectIndex(BridgeError):
    pass


def require(ok, message):
    if not ok:
        raise BridgeError(message)


def packed(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), sort_keys=True)


def fingerprint(value):
    return hashlib.sha256(packed(value).encode()).hexdigest()


def current_date(zone, now=None):
    now = datetime.now(ZoneInfo(zone)) if now is None else now.astimezone(ZoneInfo(zone))
    return (now - timedelta(days=1) if (now.hour, now.minute) < (8, 30) else now).date().isoformat()


def pointer(state):
    return state.get('csvFileId'), state.get('csvMessageId')


def doctor_pointer(state):
    return state.get('doctorsFileId'), state.get('doctorsMessageId'), state.get('doctorsUpdatedAt')


def is_admin(doctor):
    options = doctor.get('options', '')
    match = re.fullmatch(r'[aA]([1-9][0-9]*)', options)
    rank = doctor.get('#', '')
    if options and match is None:
        return False
    if rank and re.fullmatch(r'[0-9]+', rank) is None:
        return False
    number = int(rank) if rank else (int(match.group(1)) if match else 0)
    return 0 < number <= 2147483647 and (match is None or number == int(match.group(1)))

