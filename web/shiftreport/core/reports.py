"""Report options and logical text; encrypted transport remains in the protocol."""
from __future__ import annotations
import copy
from .model import shift_text
from .protocol import require

from .pdf_content import DEFAULT_OPTIONS

DEFAULTS = dict(DEFAULT_OPTIONS, format='pdf')


def readiness_warnings(patients):
    """One note counting each incomplete patient once, including follow-up."""
    fields=('responsibleSupervisorId','responsibleResidentId','initialDiagnosis','treatmentPlan','followUp')
    count=sum(any(not (patient.get(field) or '').strip() for field in fields) for patient in patients)
    return ['{} مريض بحاجة إلى استكمال البيانات؛ راجع الفريق والتشخيص والعلاج والمتابعة.'.format(count)] if count else []


def readiness_notes(patients):
    warnings = readiness_warnings(patients)
    return ('ملاحظات اكتمال غير مانعة:\n' + '\n'.join(warnings)) if warnings else 'لا توجد ملاحظات اكتمال للحقول الأساسية.'




def report_destination(client, doctors, supervisor=None):
    if supervisor is None:
        if client.config.get('residentsChatId'):
            return client.config['residentsChatId'], client.config.get('residentsTopicId') or None
        return client.config['chatId'], client.config.get('reportsTopicId') or None
    doctor = doctors.get(supervisor)
    require(doctor and doctor['clinicalRole'] == 'SUPERVISOR' and doctor.get('destination'),
            'وجهة المشرف غير متاحة')
    return doctor['destination'], None


def report_text(client, shift, supervisor=None, report_day=None):
    selected = copy.deepcopy(shift)
    selected['patients'] = [row for row in shift['patients'] if not supervisor or row.get('responsibleSupervisorId') == supervisor]
    require(selected['patients'], 'لا يوجد مرضى في التقرير المختار')
    return ('المشفى: '+client.config['hospitalName']+'\nعدد المرضى: '+str(len(selected['patients']))+
            '\nعدد المرافقين: '+str(sum(row.get('hasCompanion')=='Y' for row in selected['patients']))+
            '\n'+shift_text(selected, client.doctors, client.zone, report_day))




def text_chunks(text, limit=3500):
    """Telegram counts UTF-16 units; never split a Unicode code point."""
    require(isinstance(text, str) and text.strip(), 'تقرير نصي فارغ')
    parts, current, size = [], [], 0
    for character in text:
        units = len(character.encode('utf-16-le'))//2
        if size+units > limit:
            parts.append(''.join(current)); current, size = [], 0
        current.append(character); size += units
    if current: parts.append(''.join(current))
    return parts


def report_content(shift):
    """Clinical/order identity, excluding publication bookkeeping."""
    return dict(meta={key:shift['meta'].get(key,'') for key in ('shiftId','shiftDate','doctors','sortSpec')},
                patients=[{key:value for key,value in row.items() if key!='assignmentChangedOn'}
                          for row in shift['patients']])


def publication_patient_content(row):
    from .model import patient_edit_content
    value=patient_edit_content(row)
    value['tasks']=[dict(id=task['id'],description=task['description'],done=bool(task.get('done')))
                    for task in value['tasks']]
    return dict(id=row['externalId'],content=value,assignment=row.get('assignmentChangedOn') or '')


def publication_content(shift):
    """Clinical/order comparison; stored bookkeeping cannot create pending work.

    Delivery receipt identity remains owned by report_content.
    """
    return dict(meta={key:shift['meta'].get(key,'') for key in ('shiftId','shiftDate','doctors','sortSpec')},
                patients=[publication_patient_content(row) for row in shift['patients']])


def prepare_delivery(client, local):
    from .protocol import fingerprint
    plan=client.prepare(client.plan(local))
    remote=max(plan.bundle,key=lambda shift:shift['meta']['shiftDate'],default=None)
    needs_publish=remote is None or fingerprint(publication_content(remote))!=fingerprint(publication_content(plan.shift))
    return plan,plan.shift if needs_publish else remote,needs_publish


def publication_review(plan, zone):
    from .model import patient_text, patient_name
    before=max(plan.bundle,key=lambda s:s['meta']['shiftDate'],default={'patients':[]})
    old={p['externalId']:p for p in before['patients']};new={p['externalId']:p for p in plan.shift['patients']}
    lines=['ستستبدل هذه النسخة الكاملة بيانات المناوبة المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.',
           readiness_notes(plan.shift['patients'])]
    if plan.force_required:
        lines.insert(0,'القسم المنشور مختلف أو أزيلت نسخة الأساس. تأكيد هذه المراجعة يستبدله بالنسخة المحلية المعروضة.')
    for key in dict.fromkeys(list(old)+list(new)):
        if key in old and key in new and publication_patient_content(old[key])==publication_patient_content(new[key]):continue
        lines+=['\nالمريض: '+patient_name(new.get(key,old.get(key,{}))),
                '\nقبل:\n'+patient_text(old.get(key),plan.remote['doctors'],zone),
                '\nبعد:\n'+patient_text(new.get(key),plan.remote['doctors'],zone)]
    lines+=['\nالنسخة الكاملة التي ستنشر:\n'+shift_text(plan.shift,plan.remote['doctors'],zone)]
    return '\n'.join(lines)


class ReportAttempt:
    """Session-local outcomes; durable receipts remain the delivery authority."""
    def __init__(self):
        self.publication='NOT_STARTED'
        self.delivery='NOT_STARTED'
        self.snapshot=None

    def failed(self, baseline):
        if self.publication=='IN_PROGRESS':
            confirmed=self.snapshot and any(shift['meta'].get('snapshotId')==self.snapshot
                                            for shift in (baseline or {}).get('bundle',[]))
            self.publication='CONFIRMED' if confirmed else 'UNCERTAIN'
        if self.delivery=='IN_PROGRESS':self.delivery='UNCERTAIN'

    def summary(self):
        publication={'NOT_STARTED':'لم يبدأ النشر','IN_PROGRESS':'جار النشر',
                     'CONFIRMED':'تم نشر النسخة المحلية','UNCERTAIN':'لم يتأكد النشر'}
        delivery={'NOT_STARTED':'لم يتأكد التسليم في هذه المحاولة','IN_PROGRESS':'جار الإرسال',
                  'CONFIRMED':'تم تأكيد التسليم','UNCERTAIN':'لم يتأكد التسليم؛ افحص تليجرام قبل الإعادة'}
        text='بيانات المرضى: '+publication[self.publication]+'\nتقرير المناوبة: '+delivery[self.delivery]
        if self.publication=='CONFIRMED' and self.delivery=='NOT_STARTED':
            text+='\nبيانات المرضى منشورة بالفعل؛ عدم تسليم التقرير لا يلغي نشرها.'
        return text

    def next_action(self):
        if self.delivery=='UNCERTAIN':return 'افحص تليجرام وسجل التسليم قبل إعادة الإرسال.'
        if self.delivery=='CONFIRMED':return 'التسليم مؤكد؛ لا تكرر الإرسال إذا تعذر حفظ الإيصال محلياً.'
        if self.publication=='UNCERTAIN':return 'لم يتأكد نشر البيانات؛ افحص تليجرام وسجل العمليات ثم اجلب الحالة الحالية.'
        if self.publication=='CONFIRMED':return 'البيانات منشورة؛ لم تبدأ محاولة تسليم جديدة. عالج السبب ثم راجع وأرسل.'
        return 'لم تبدأ محاولة تسليم التقرير. عالج السبب ثم أعد المراجعة.'
