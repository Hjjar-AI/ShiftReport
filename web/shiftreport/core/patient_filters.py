"""Ward visibility rules matching Android; filters never change saved order."""
from datetime import date, datetime
import re
import time
import unicodedata
from .model import badges_decode
from .protocol import ZoneInfo, current_date
from .tasks import read_tasks, pending as pending_tasks

CLINICAL_FILTERS = ['الكل', 'أولوية', 'شارات', 'غير مسند', 'عاجل', 'دخول اليوم']
TASK_FILTERS = ['كل المهام', 'مهام معلقة']
OWNER_FILTERS = ['كل المرضى', 'مرضاي']


def normalize_search(value):
    value = unicodedata.normalize('NFKC', value or '').lower()
    value = re.sub('[\u064B-\u065F\u0670\u06D6-\u06ED]', '', value)
    value = value.translate(str.maketrans({'أ':'ا','إ':'ا','آ':'ا','ى':'ي','ؤ':'و','ئ':'ي','ة':'ه'}))
    return re.sub(r'\s+', ' ', value).strip()


def matches_patient(row, query='', clinical='الكل', tasks='كل المهام', mine=False, actor=None, zone='Asia/Damascus', now=None):
    now = int(time.time()*1000) if now is None else now
    entries = read_tasks(row)
    pending = pending_tasks(entries)
    if mine and (not actor or not (actor in (row.get('responsibleResidentId'), row.get('responsibleSupervisorId')))):return False
    badges = badges_decode(row.get('warningDetails', ''))
    clinical = (clinical,) if isinstance(clinical, str) else clinical
    if 'أولوية' in clinical and row.get('isPriority') != 'Y':return False
    if 'شارات' in clinical and not badges:return False
    if 'غير مسند' in clinical and row.get('responsibleResidentId') and row.get('responsibleSupervisorId'):return False
    if 'عاجل' in clinical and row.get('isPriority') != 'Y' and not any(level == 'high' for _, level in badges):return False
    if 'دخول اليوم' in clinical:
        today = date.fromisoformat(current_date(zone,datetime.fromtimestamp(now/1000, ZoneInfo(zone))))
        try:
            if date.fromisoformat(row.get('admitDate', '')) < today:return False
        except ValueError:return False
    if tasks == 'مهام معلقة' and not pending:return False
    needle = normalize_search(query)
    values = [row.get(key, '') for key in ('name','admitNum','initialDiagnosis','treatmentPlan','followUp','labs')]
    values.append(' '.join(t['description'] for t in entries))
    return not needle or any(needle in normalize_search(value) for value in values)


def ward_summary(rows, actor, zone):
    """The same predicates drive shortcuts, dashboard counts and visible rows."""
    return dict(patients=len(rows), tasks=sum(len(pending_tasks(read_tasks(row))) for row in rows),
                companions=sum(row.get('hasCompanion') == 'Y' for row in rows),
                mine=sum(matches_patient(row, mine=True, actor=actor, zone=zone) for row in rows),
                **{key:sum(matches_patient(row, clinical=clinical, zone=zone) for row in rows)
                   for key,clinical in [('priority','أولوية'),('badges','شارات'),('urgent','عاجل'),
                                        ('unassigned','غير مسند'),('admissions','دخول اليوم')]})
