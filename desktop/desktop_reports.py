"""Report options and logical text; encrypted transport remains in the protocol."""
from __future__ import annotations
import copy
from desktop_model import shift_text
from desktop_protocol import require

from desktop_pdf_content import DEFAULT_OPTIONS

DEFAULTS = dict(DEFAULT_OPTIONS, format='pdf')


def readiness_warnings(patients):
    """Non-blocking notes matching Android domain/report/ReportReadiness.kt."""
    warnings = []
    for field, label in (('responsibleSupervisorId', 'اختصاصي'),
                         ('responsibleResidentId', 'مقيم'),
                         ('initialDiagnosis', 'تشخيص أولي'),
                         ('treatmentPlan', 'خطة علاجية')):
        count = sum(not (patient.get(field) or '').strip() for patient in patients)
        if count: warnings.append('{} مريض دون {}'.format(count, label))
    return warnings


def readiness_notes(patients):
    warnings = readiness_warnings(patients)
    return ('ملاحظات اكتمال غير مانعة:\n' + '\n'.join(warnings)) if warnings else 'لا توجد ملاحظات اكتمال للحقول الأساسية.'


def options_dialog(owner, export_only=False, assignments=False, force_pdf=False, settings_only=False, review_send=False):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, modal, guarded, dialog_actions
    from desktop_pdf_content import normalize_options
    if settings_only:export_only=True
    previous = normalize_options(dict(DEFAULTS, **owner.io(owner.store.report_preferences)))
    window = modal(owner, 'مراجعة التقرير وإرساله' if review_send else 'إعدادات PDF' if settings_only else 'توزيع المرضى بالأسماء فقط' if assignments else 'خيارات التقرير', '740x680')
    actions = ctk.CTkFrame(window); actions.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True)
    if not assignments and not settings_only:
        from desktop_protocol import current_date
        from desktop_model import doctor_label
        source = owner.store.read()
        project, shift_id = owner.client.project_id, source['meta']['shiftId']
        roster = ctk.CTkLabel(area, text='', anchor='e', justify='right', wraplength=600)
        roster.pack(fill='x', padx=10, pady=4)
        def update_roster():
            shift = owner.store.read()
            require(owner.client.project_id == project and shift['meta']['shiftId'] == shift_id,
                    'تغير المشروع أو المناوبة؛ أعد فتح خيارات التقرير')
            names=[doctor_label(owner.client.doctors,key) for key in shift['meta'].get('doctors','').split(',') if key]
            shown=names[:2]
            summary=' / '.join(shown)+(' · +'+str(len(names)-2) if len(names)>2 else '')
            roster.configure(text='أطباء المناوبة: '+(summary or 'لم تحدد بعد'))
            return shift
        def choose_roster():
            update_roster()
            owner.roster()
            if window.winfo_exists():
                window.grab_set()
                update_roster()
        button(area, 'اختيار أو تعديل أطباء المناوبة', guarded(choose_roster),
               state='normal')
        update_roster()
    scope_get=None
    batch_targets={}
    if not assignments and not settings_only and not force_pdf:
        scopes={None:'التقرير العام'}
        scopes.update({key:doctor_label(owner.client.doctors,key) for key in owner.destinations[1:]})
        scope_get=combo(area,'نطاق التقرير والوجهة',scopes,owner.selected_supervisor(),
                        on_change=lambda:refresh_options())
    if review_send and force_pdf:
        import tkinter as tk
        from desktop_model import doctor_label
        counts={}
        for row in owner.store.read()['patients']:
            key=row.get('responsibleSupervisorId')
            if key:counts[key]=counts.get(key,0)+1
        ctk.CTkLabel(area,text='مجموعات المشرفين — PDF مستقل لكل مجموعة',anchor='e').pack(fill='x',padx=10)
        for key,count in counts.items():
            doctor=owner.client.doctors.get(key,{})
            available=doctor.get('clinicalRole')=='SUPERVISOR' and bool(doctor.get('destination'))
            variable=tk.BooleanVar(value=available)
            ctk.CTkCheckBox(area,text=doctor_label(owner.client.doctors,key)+' — '+str(count)+' مريض'+('' if available else ' · لا توجد وجهة'),
                variable=variable,state='normal' if available else 'disabled',height=48).pack(fill='x',padx=10,pady=3)
            if available:batch_targets[key]=variable
    from desktop_layout import Disclosure
    formats = {'pdf':'PDF', 'text':'نص'}
    format_get = (lambda:'pdf') if assignments or force_pdf or settings_only or getattr(owner,'demo',False) else combo(
        area, 'صيغة التقرير', formats, previous['format'], on_change=lambda:refresh_options())
    pdf_area=ctk.CTkFrame(area)
    advanced=Disclosure(pdf_area,'تخصيص PDF (اختياري)',expanded=settings_only)
    advanced.pack(fill='x',padx=8,pady=4)
    layout = (lambda:previous['layout']) if assignments else combo(advanced.content, 'نمط PDF',
        {'table':'جدول كلاسيكي', 'cards':'بطاقات ملونة مع فهرس', 'paragraphs':'فقرات'},
        previous['layout'], on_change=lambda:refresh_options())
    page_area=ctk.CTkFrame(advanced.content,fg_color='transparent')
    page_area.pack(fill='x')
    paper = (lambda:'A4') if assignments else combo(page_area, 'حجم الورق', {key:key for key in ('A4','A5')}, previous['paper'])
    appearance_area=ctk.CTkFrame(advanced.content,fg_color='transparent')
    appearance_area.pack(fill='x')
    from desktop_pdf_settings import appearance_controls
    appearance = appearance_controls(appearance_area, previous)

    description=ctk.CTkLabel(area,text='',wraplength=620,justify='right',anchor='e')
    description.pack(fill='x',padx=10,pady=8)
    def refresh_options():
        if format_get()=='pdf':
            appearance.set_layout('assignments' if assignments else layout())
            pdf_area.pack(fill='x',padx=4,pady=3,before=description)
            if not assignments and layout()=='cards':page_area.pack_forget()
            elif not page_area.winfo_manager():page_area.pack(fill='x',before=appearance_area)
            if assignments:
                text='A4 عمودي · ثلاثة جداول من اليمين إلى اليسار · الترقيم العربي في العمود الأيمن · تتابع الصفحات دون حذف أسماء.'
            else:
                text={
                    'cards':'بطاقات A5 عمودي مع فهرس؛ حجم الورق والاتجاه ثابتان لهذا النمط.',
                    'table':'جدول كلاسيكي مضغوط يحافظ على كامل المحتوى.',
                    'paragraphs':'فقرات متتابعة تحافظ على كامل المحتوى.'
                }[layout()]
        else:
            pdf_area.pack_forget()
            text='تقرير نصي؛ يُرسل كملف مشفر عند تفعيل تشفير المشروع.'
        if scope_get is not None:
            supervisor=scope_get()
            destination=('حفظ محلي على هذا الجهاز' if export_only else
                         'موضوع التقارير العام' if supervisor is None else
                         'مجموعة المشرف: '+doctor_label(owner.client.doctors,supervisor))
            text='النطاق: '+('جميع المرضى' if supervisor is None else doctor_label(owner.client.doctors,supervisor))+'\nالوجهة: '+destination+'\n'+text
        description.configure(text=text+('\nتُحفظ الخيارات لهذا المشروع وتُستخدم افتراضياً في التقارير.' if settings_only else '' if export_only else '\nإرسال التقرير يتطلب معاينة وموافقة صريحة.'))
    refresh_options()
    result = []
    def read_options():
        return dict(format=format_get(), layout=layout(), paper=paper(), orientation='portrait',
                    separate=False, ward=previous['ward'], **appearance())
    def save_options(options):
        preferences = dict(previous, **appearance()) if assignments else dict(options)
        if force_pdf or settings_only:preferences['format']=previous['format']
        owner.io(lambda:owner.store.save_report_preferences(preferences))
        if scope_get is not None and window.winfo_exists():owner.supervisor.set(scopes[scope_get()])
    def submit():
        if not assignments and not settings_only:update_roster()
        options=read_options();save_options(options)
        if not window.winfo_exists():return
        result.append(options)
        window.destroy()
    if review_send:
        from desktop_report_review import install_send_review
        def targets():
            return [key for key,variable in batch_targets.items() if variable.get()] if force_pdf else [scope_get()]
        def reviewed_options():
            update_roster()
            return read_options()
        install_send_review(owner,window,area,actions,reviewed_options,targets,save_options,result)
    else:
        dialog_actions(actions,primary=('حفظ الإعدادات' if settings_only else 'متابعة إلى الحفظ' if export_only else 'متابعة إلى المعاينة',guarded(submit)),
                       cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
    return result[0] if result else None


def report_destination(client, doctors, supervisor=None):
    if supervisor is None:
        return client.config['chatId'], client.config.get('reportsTopicId')
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


def export_supervisor_pdfs(owner, shift, supervisor, font, options):
    # Keep callers on the same preview/publication guard as the main export action.
    from desktop_export_review import run_export
    return run_export(owner,separate=True)


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


def publication_content(shift):
    """Assignment highlighting travels with snapshots, independently of report delivery keys."""
    result = report_content(shift)
    result['assignmentChanges'] = [row.get('assignmentChangedOn') or '' for row in shift['patients']]
    return result


def prepare_delivery(client, local):
    from desktop_protocol import fingerprint
    plan=client.prepare(client.plan(local))
    remote=next((shift for shift in plan.bundle if shift['meta']['shiftId']==plan.shift['meta']['shiftId']),None)
    needs_publish=remote is None or fingerprint(publication_content(remote))!=fingerprint(publication_content(plan.shift))
    return plan,plan.shift if needs_publish else remote,needs_publish


def publication_review(plan, zone):
    from desktop_model import patient_text
    before=next((s for s in plan.bundle if s['meta']['shiftDate']==plan.shift['meta']['shiftDate']),{'patients':[]})
    old={p['externalId']:p for p in before['patients']};new={p['externalId']:p for p in plan.shift['patients']}
    lines=['ستستبدل هذه النسخة الكاملة بيانات المناوبة المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.',
           readiness_notes(plan.shift['patients'])]
    if plan.force_required:
        lines.insert(0,'معرّف مناوبة اليوم المنشورة مختلف أو أزيلت نسخة الأساس. تأكيد هذه المراجعة يستبدلها بالنسخة المحلية المعروضة.')
    for key in dict.fromkeys(list(old)+list(new)):
        if old.get(key)==new.get(key):continue
        lines+=['\nالمريض: '+new.get(key,old.get(key,{})).get('name',''),
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
