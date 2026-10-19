"""Report options and logical text; encrypted transport remains in the protocol."""
from __future__ import annotations
import copy
from desktop_model import shift_text
from desktop_protocol import require

DEFAULTS = dict(format='pdf', layout='table', paper='A4', orientation='portrait', color='teal',
                dark=False, separate=False, ward='')


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


def options_dialog(owner, export_only=False, assignments=False, force_pdf=False):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, modal, guarded
    from desktop_pdf_content import normalize_options
    previous = normalize_options(dict(DEFAULTS, **owner.io(owner.store.report_preferences)))
    window = modal(owner, 'توزيع المرضى بالأسماء فقط' if assignments else 'خيارات التقرير', '740x680')
    actions = ctk.CTkFrame(window); actions.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True)
    if not assignments:
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
            names = ' / '.join(doctor_label(owner.client.doctors, key)
                               for key in shift['meta'].get('doctors', '').split(',') if key)
            roster.configure(text='أطباء المناوبة: ' + (names or 'لم تحدد بعد') + '\n' + readiness_notes(shift['patients']))
            return shift
        def choose_roster():
            update_roster()
            owner.roster()
            if window.winfo_exists():
                window.grab_set()
                update_roster()
        button(area, 'اختيار أطباء المناوبة', guarded(choose_roster),
               state='normal' if source['meta']['shiftDate'] == current_date(owner.client.zone) else 'disabled')
        update_roster()
    from desktop_layout import Disclosure
    formats = {'pdf':'PDF', 'text':'نص'}
    format_get = (lambda:'pdf') if assignments or force_pdf or getattr(owner,'demo',False) else combo(
        area, 'صيغة التقرير', formats, previous['format'], on_change=lambda:refresh_options())
    pdf_area=ctk.CTkFrame(area)
    layout = (lambda:previous['layout']) if assignments else combo(pdf_area, 'نمط PDF',
        {'table':'جدول كلاسيكي', 'cards':'بطاقات ملونة مع فهرس', 'paragraphs':'فقرات'},
        previous['layout'], on_change=lambda:refresh_options())
    advanced=Disclosure(pdf_area,'خيارات PDF المتقدمة')
    advanced.pack(fill='x',padx=8,pady=4)
    page_area=ctk.CTkFrame(advanced.content,fg_color='transparent')
    page_area.pack(fill='x')
    paper = (lambda:'A4') if assignments else combo(page_area, 'حجم الورق', {key:key for key in ('A4','A5','A3','Letter')}, previous['paper'])
    orientation = (lambda:'portrait') if assignments else combo(page_area, 'الاتجاه', {'portrait':'عمودي', 'landscape':'أفقي'}, previous['orientation'])
    appearance_area=ctk.CTkFrame(advanced.content,fg_color='transparent')
    appearance_area.pack(fill='x')
    color = combo(appearance_area, 'اللون', {'teal':'فيروزي', 'blue':'أزرق', 'green':'أخضر', 'purple':'بنفسجي', 'pastel':'باستيل'}, previous['color'])
    dark = combo(appearance_area, 'مظهر PDF', {False:'فاتح', True:'داكن'}, previous['dark'])
    separate = combo(appearance_area, 'ملف PDF لكل مشرف عند الحفظ المحلي', {False:'ملف واحد', True:'ملف لكل مشرف'}, previous['separate']) if export_only and not assignments else lambda:previous['separate']
    description=ctk.CTkLabel(area,text='',wraplength=620,justify='right',anchor='e')
    description.pack(fill='x',padx=10,pady=8)
    def refresh_options():
        if format_get()=='pdf':
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
        description.configure(text=text+('' if export_only else '\nإرسال التقرير يتطلب معاينة وموافقة صريحة.'))
    refresh_options()
    result = []
    def submit():
        if not assignments: update_roster()
        options = dict(format=format_get(), layout=layout(), paper=paper(), orientation=orientation(), color=color(),
                       dark=dark(), separate=separate(), ward=previous['ward'])
        preferences = dict(previous, color=options['color'],dark=options['dark'],ward=options['ward']) if assignments else dict(options)
        if force_pdf:preferences['format']=previous['format']
        owner.io(lambda:owner.store.save_report_preferences(preferences))
        if not window.winfo_exists():return
        result.append(options)
        window.destroy()
    button(actions, 'متابعة إلى الحفظ' if export_only else 'متابعة إلى المعاينة', guarded(submit))
    button(actions, 'إلغاء', window.destroy)
    owner.root.wait_window(window)
    return result[0] if result else None


def report_destination(client, doctors, supervisor=None):
    if supervisor is None:
        return client.config['chatId'], client.config.get('reportsTopicId')
    doctor = doctors.get(supervisor)
    require(doctor and doctor['clinicalRole'] == 'SUPERVISOR' and doctor.get('destination'),
            'وجهة المشرف غير متاحة')
    return doctor['destination'], None


def report_text(client, shift, supervisor=None):
    selected = copy.deepcopy(shift)
    selected['patients'] = [row for row in shift['patients'] if not supervisor or row.get('responsibleSupervisorId') == supervisor]
    require(selected['patients'], 'لا يوجد مرضى في التقرير المختار')
    return ('المشفى: '+client.config['hospitalName']+'\nعدد المرضى: '+str(len(selected['patients']))+
            '\nعدد المرافقين: '+str(sum(row.get('hasCompanion')=='Y' for row in selected['patients']))+
            '\n'+shift_text(selected, client.doctors, client.zone))


def export_supervisor_pdfs(owner, shift, supervisor, font, options):
    """Prepare every PDF before writing a new set; never overwrite an existing file."""
    from datetime import datetime
    from pathlib import Path
    import re
    import uuid
    from tkinter import filedialog
    from desktop_pdf import render_report
    folder = filedialog.askdirectory(title='مجلد حفظ ملفات المشرفين')
    if not folder:return
    def prepare_and_save():
        groups = {}
        for patient in shift['patients']:
            key = patient.get('responsibleSupervisorId') or None
            if supervisor is None or key == supervisor:groups.setdefault(key, []).append(patient)
        require(groups, 'لا يوجد مرضى في التقرير المختار')
        stamp = datetime.now().strftime('%Y%m%d_%H%M%S')+'_'+uuid.uuid4().hex[:8]
        files = []
        for index,(key,patients) in enumerate(groups.items(),1):
            subset = copy.deepcopy(shift);subset['patients']=copy.deepcopy(patients)
            if key:subset['meta']['doctors']=key
            name = owner.client.doctors.get(key,{}).get('name','مشرف محفوظ' if key else 'بدون مشرف')
            safe = re.sub(r'[^\w-]+','_',name,flags=re.UNICODE)[:60]
            path = render_report(owner.client,subset,font_path=font,options=options)
            target = Path(folder)/('Rushd_Report_{}_{}_{}_{}.pdf'.format(shift['meta']['shiftDate'],stamp,index,safe))
            files.append((target,path.read_bytes()))
        created = []
        try:
            for path,raw in files:
                with path.open('xb') as stream:
                    created.append(path);stream.write(raw)
                require(path.read_bytes()==raw,'تعذر التحقق من حفظ PDF كاملاً')
        except Exception:
            for path in created:path.unlink(missing_ok=True)
            raise
        return len(created)
    count = owner.io(prepare_and_save)
    owner.status.set('حُفظ {} ملف PDF للمشرفين محلياً.'.format(count))


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
                patients=shift['patients'])


def prepare_delivery(client, local, restored):
    from desktop_protocol import fingerprint
    plan=client.prepare(client.plan(local),restored,saved_patients=local['patients'])
    remote=next((shift for shift in plan.bundle if shift['meta']['shiftId']==plan.shift['meta']['shiftId']),None)
    needs_publish=remote is None or fingerprint(report_content(remote))!=fingerprint(report_content(plan.shift))
    return plan,plan.shift if needs_publish else remote,needs_publish


def publication_review(plan, zone):
    from desktop_model import patient_text
    before=next((s for s in plan.bundle if s['meta']['shiftId']==plan.shift['meta']['shiftId']),{'patients':[]})
    old={p['externalId']:p for p in before['patients']};new={p['externalId']:p for p in plan.shift['patients']}
    lines=['ستستبدل هذه النسخة الكاملة بيانات المناوبة المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.',
           readiness_notes(plan.shift['patients'])]
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
        labels={'NOT_STARTED':'لم يبدأ','IN_PROGRESS':'جارٍ','CONFIRMED':'مؤكد','UNCERTAIN':'غير مؤكد'}
        return 'آخر محاولة إرسال — نشر البيانات: '+labels[self.publication]+'؛ تسليم التقرير: '+labels[self.delivery]

    def next_action(self):
        if self.delivery=='UNCERTAIN':return 'افحص تليجرام وسجل التسليم قبل إعادة الإرسال.'
        if self.delivery=='CONFIRMED':return 'التسليم مؤكد؛ لا تكرر الإرسال إذا تعذر حفظ الإيصال محلياً.'
        if self.publication=='UNCERTAIN':return 'لم يتأكد نشر البيانات؛ افحص تليجرام وسجل العمليات ثم اجلب الحالة الحالية.'
        if self.publication=='CONFIRMED':return 'البيانات منشورة؛ لم تبدأ محاولة تسليم جديدة. عالج السبب ثم راجع وأرسل.'
        return 'لم تبدأ محاولة تسليم التقرير. عالج السبب ثم أعد المراجعة.'
