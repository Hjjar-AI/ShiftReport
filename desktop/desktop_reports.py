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
    format_get = (lambda:'pdf') if assignments else combo(area, 'صيغة التقرير', formats, previous['format'])
    layout = (lambda:previous['layout']) if assignments else combo(area, 'نمط PDF',
        {'table':'جدول كلاسيكي', 'cards':'بطاقات ملونة مع فهرس', 'paragraphs':'فقرات'}, previous['layout'])
    ward = entry(area, 'اسم الجناح — اختياري', previous['ward'])
    advanced = ctk.CTkFrame(area)
    def toggle():
        if advanced.winfo_manager(): advanced.pack_forget()
        else: advanced.pack(fill='x', padx=8, pady=4)
    button(area, 'خيارات PDF المتقدمة', toggle)
    paper = combo(advanced, 'حجم الورق', {key:key for key in ('A4','A5','A3','Letter')}, previous['paper'])
    orientation = combo(advanced, 'الاتجاه', {'portrait':'عمودي', 'landscape':'أفقي'}, previous['orientation'])
    color = combo(advanced, 'اللون', {'teal':'فيروزي', 'blue':'أزرق', 'green':'أخضر', 'purple':'بنفسجي', 'pastel':'باستيل'}, previous['color'])
    dark = combo(advanced, 'مظهر PDF', {False:'فاتح', True:'داكن'}, previous['dark'])
    separate = combo(advanced, 'ملف PDF لكل مشرف عند الحفظ المحلي', {False:'ملف واحد', True:'ملف لكل مشرف'}, previous['separate']) if export_only and not assignments else lambda:previous['separate']
    description = ('صفحة طولية واحدة وجدول مستقل لكل مشرف: أسماء المقيمين في صف واحد ثم أسماء المرضى فقط.\n'
                   'يزداد طول الصفحة عند الحاجة دون حذف أسماء؛ اتجاه الصفحة عمودي دائماً.' if assignments else
                  'البطاقات تستخدم A5 عمودي وتحافظ على كامل المحتوى.\n'
                 'التقرير النصي يُرسل كملف مشفر عند تفعيل تشفير المشروع.\n'
                 'إرسال التقرير يتطلب معاينة وموافقة صريحة.')
    ctk.CTkLabel(area, text=description, wraplength=620, justify='right').pack(fill='x', padx=10, pady=8)
    result = []
    def submit():
        options = dict(format=format_get(), layout=layout(), paper=paper(), orientation=orientation(), color=color(),
                       dark=dark(), separate=separate(), ward=ward.get())
        result.append(options); owner.report_options = options; window.destroy()
    button(actions, 'متابعة إلى الحفظ' if export_only else 'متابعة إلى المعاينة', submit)
    button(actions, 'إلغاء', window.destroy)
    owner.root.wait_window(window)
    return result[0] if result else None


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
