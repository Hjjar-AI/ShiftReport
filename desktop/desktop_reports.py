"""Report options and logical text; encrypted transport remains in the protocol."""
from __future__ import annotations
import copy
from desktop_model import shift_text
from desktop_protocol import require

DEFAULTS = dict(format='pdf', layout='table', paper='A4', orientation='portrait', color='teal',
                dark=False, separate=False, ward='')


def options_dialog(owner, export_only=False, assignments=False):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, entry, modal
    previous = dict(DEFAULTS, **getattr(owner, 'report_options', {}))
    window = modal(owner, 'توزيع المرضى بالأسماء فقط' if assignments else 'خيارات التقرير', '740x680')
    actions = ctk.CTkFrame(window); actions.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True)
    formats = {'pdf':'PDF', 'text':'نص'}
    format_get = (lambda:'pdf') if assignments or getattr(owner,'demo',False) else combo(area, 'صيغة التقرير', formats, previous['format'])
    layout = (lambda:previous['layout']) if assignments else combo(area, 'نمط PDF',
        {'table':'جدول كلاسيكي', 'cards':'بطاقات ملونة مع فهرس', 'paragraphs':'فقرات'}, previous['layout'])
    ward = entry(area, 'اسم الجناح — اختياري', previous['ward'])
    advanced = ctk.CTkFrame(area)
    def toggle():
        if advanced.winfo_manager(): advanced.pack_forget()
        else: advanced.pack(fill='x', padx=8, pady=4)
    button(area, 'خيارات PDF المتقدمة', toggle)
    paper = (lambda:'A4') if assignments else combo(advanced, 'حجم الورق', {key:key for key in ('A4','A5','A3','Letter')}, previous['paper'])
    orientation = (lambda:'portrait') if assignments else combo(advanced, 'الاتجاه', {'portrait':'عمودي', 'landscape':'أفقي'}, previous['orientation'])
    color = combo(advanced, 'اللون', {'teal':'فيروزي', 'blue':'أزرق', 'green':'أخضر', 'purple':'بنفسجي', 'pastel':'باستيل'}, previous['color'])
    dark = combo(advanced, 'مظهر PDF', {False:'فاتح', True:'داكن'}, previous['dark'])
    separate = combo(advanced, 'ملف PDF لكل مشرف عند الحفظ المحلي', {False:'ملف واحد', True:'ملف لكل مشرف'}, previous['separate']) if export_only and not assignments else lambda:previous['separate']
    description = ('A4 عمودي · ثلاثة جداول مشرفين في كل صف · عرض الجدول 56.7 مم.\n'
                   'ترقيم متصل من اليسار إلى اليمين؛ تتابع الصفحات عند الحاجة دون حذف أسماء.' if assignments else
                  'البطاقات تستخدم A5 عمودي وتحافظ على كامل المحتوى.\n'
                 'التقرير النصي يُرسل كملف مشفر عند تفعيل تشفير المشروع.\n'
                 'إرسال التقرير يتطلب معاينة وموافقة صريحة.')
    ctk.CTkLabel(area, text=description, wraplength=620, justify='right').pack(fill='x', padx=10, pady=8)
    result = []
    def submit():
        options = dict(format=format_get(), layout=layout(), paper=paper(), orientation=orientation(), color=color(),
                       dark=dark(), separate=separate(), ward=ward.get())
        result.append(options)
        owner.report_options = dict(previous, color=options['color'],dark=options['dark'],ward=options['ward']) if assignments else options
        window.destroy()
    button(actions, 'متابعة إلى الحفظ' if export_only else 'متابعة إلى المعاينة', submit)
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
    return 'المشفى: '+client.config['hospitalName']+'\nعدد المرضى: '+str(len(selected['patients']))+'\n'+shift_text(selected, client.doctors, client.zone)


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
    plan=client.prepare(client.plan(local),restored)
    remote=next((shift for shift in plan.bundle if shift['meta']['shiftId']==plan.shift['meta']['shiftId']),None)
    needs_publish=remote is None or fingerprint(report_content(remote))!=fingerprint(report_content(plan.shift))
    return plan,plan.shift if needs_publish else remote,needs_publish


def publication_review(plan, zone):
    from desktop_model import patient_text
    before=next((s for s in plan.bundle if s['meta']['shiftId']==plan.shift['meta']['shiftId']),{'patients':[]})
    old={p['externalId']:p for p in before['patients']};new={p['externalId']:p for p in plan.shift['patients']}
    lines=['ستستبدل هذه النسخة الكاملة بيانات المناوبة المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.']
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
