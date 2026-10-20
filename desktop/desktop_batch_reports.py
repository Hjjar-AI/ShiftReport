"""Reviewed per-supervisor PDF delivery; each destination has its own durable receipt."""
from pathlib import Path
import sys
from desktop_widgets import messagebox
from desktop_protocol import require
from desktop_reports import options_dialog, publication_review, readiness_notes
from desktop_delivery import ReportDelivery


def select_supervisors(owner, shift, doctors):
    import tkinter as tk
    import desktop_widgets as ctk
    from desktop_forms import button, modal, dialog_actions
    counts = {}
    for patient in shift['patients']:
        key = patient.get('responsibleSupervisorId')
        if key:counts[key] = counts.get(key,0)+1
    window = modal(owner,'اختيار مجموعات المشرفين','740x620')
    controls = ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    area = ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    variables = {}
    for key,count in counts.items():
        doctor = doctors.get(key,{})
        available = doctor.get('clinicalRole')=='SUPERVISOR' and bool(doctor.get('destination'))
        variable = tk.BooleanVar(value=bool(available))
        text = '{} — {} مريض{}'.format(doctor.get('name','مشرف محفوظ'),count,'' if available else ' · وجهة غير متاحة')
        ctk.CTkCheckBox(area,text=text,variable=variable,state='normal' if available else 'disabled',height=48).pack(anchor='e',padx=10,pady=4)
        if available:variables[key] = variable
    result = []
    def submit():
        selected = [key for key,v in variables.items() if v.get()]
        if not selected:return
        result.extend(selected);window.destroy()
    dialog_actions(controls,primary=('متابعة إلى معاينة كل PDF',submit),cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
    return result


def send_supervisor_reports(owner):
    from desktop_pdf import render_report
    from desktop_platform import open_pdf
    owner.ready()
    local = owner.store.read(owner.client)
    options = options_dialog(owner,force_pdf=True)
    if options is None:return
    local = owner.store.read(owner.client)
    delivery = ReportDelivery(owner,local)
    plan, reviewed, needs_publish = delivery.plan, delivery.reviewed, delivery.needs_publish
    selected = select_supervisors(owner,reviewed,owner.client.doctors)
    if not selected:return
    delivery.check_targets(selected)
    if needs_publish and not owner.text_dialog('مراجعة النسخة الكاملة قبل تقارير المشرفين',
            publication_review(plan,owner.client.zone),True,'متابعة إلى معاينة ملفات المشرفين',publication_plan=plan):return
    font = owner.settings.get('PDF_FONT')
    if font and not Path(font).is_absolute():
        base = Path(sys.executable).resolve().parent if getattr(sys,'frozen',False) else Path(__file__).resolve().parent
        font = str(base/font)
    files = {}
    for key in selected:
        name = owner.client.doctors[key]['name']
        path = owner.io(lambda k=key:render_report(owner.client,reviewed,k,font,options))
        raw = owner.io(path.read_bytes)
        owner.io(lambda p=path:open_pdf(p))
        notes = readiness_notes([row for row in reviewed['patients'] if row.get('responsibleSupervisorId')==key])
        if not messagebox.askyesno('مراجعة PDF — '+name,'افحص PDF المفتوح لهذه الوجهة. هل توافق على إرسال الملف نفسه إلى '+name+'؟\n'+notes,default='no'):return
        require(owner.io(path.read_bytes)==raw,'تغير PDF بعد المعاينة؛ أعدها')
        files[key] = raw
    names = '\n'.join(owner.client.doctors[key]['name'] for key in selected)
    confirmation = 'إرسال ملفات PDF المعاينة إلى:\n'+names+'\nالمناوبة: '+reviewed['meta']['shiftDate']
    if needs_publish:confirmation += '\nستنشر النسخة الكاملة أولاً. فشل تسليم تقرير لاحق لا يلغي النشر أو التقارير المسلمة.'
    if not messagebox.askyesno('تأكيد النشر وإرسال تقارير المشرفين',confirmation,default='no'):return
    outcomes = {key:'لم يبدأ' for key in selected}
    def summary():
        return '\n'.join(owner.client.doctors.get(key,{}).get('name','مشرف محفوظ')+': '+outcomes[key] for key in selected)
    def confirm_repeat(key):
        return messagebox.askyesno('نسخة إضافية',owner.client.doctors[key]['name']+' — سُلّم تقرير هذه النسخة سابقاً. هل تؤكد نسخة إضافية؟',default='no')
    def outcome(key,status):
        outcomes[key]=status
    try:
        delivery.send([(key,'pdf',files[key]) for key in selected],confirm_repeat,outcome)
    except Exception:
        owner.status.set(summary()+'\nتوقف الإرسال؛ راجع سجل التسليم قبل إعادة أي وجهة.')
        messagebox.showinfo('نتائج تقارير المشرفين',summary())
        raise
    owner.status.set(summary())
    messagebox.showinfo('نتائج تقارير المشرفين',summary())
