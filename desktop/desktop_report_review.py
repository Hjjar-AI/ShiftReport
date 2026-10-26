"""One options/change-review/preview workspace followed by explicit send confirmation."""
from pathlib import Path
import desktop_widgets as ctk
from desktop_widgets import messagebox, set_readonly_text
from desktop_forms import dialog_actions
from desktop_protocol import BridgeError, fingerprint, require
from desktop_reports import publication_review, readiness_notes, report_text
from desktop_delivery import ReportDelivery


def install_send_review(owner, window, area, actions, read_options, read_targets, save_options, result):
    from desktop_pdf import render_report
    from desktop_platform import open_pdf
    from desktop_model import doctor_label
    review_area=ctk.CTkFrame(area)
    review_area.pack(fill='x',padx=6,pady=4)
    ctk.CTkLabel(review_area,text='مراجعة البيانات والتقرير',anchor='e').pack(fill='x',padx=8,pady=4)
    text=ctk.CTkTextbox(review_area,height=260,wrap='word',font=('Arial',15))
    text.pack(fill='x',padx=8,pady=4)
    set_readonly_text(text,'اختر الوجهة والخيارات، ثم اضغط معاينة. ستظهر تغييرات النشر هنا قبل التأكيد النهائي.')
    ctk.CTkLabel(review_area,text='تأكيد الإرسال ينشر النسخة المحلية الكاملة مكان المنشورة؛ آخر نشر ناجح هو المعتمد.',
        wraplength=640,justify='right').pack(fill='x',padx=8,pady=3)
    files_area=ctk.CTkFrame(review_area,fg_color='transparent');files_area.pack(fill='x')
    prepared={};working=[False];buttons={}
    from tkinter import BooleanVar
    export_pdf=BooleanVar(value=True)
    export_choice=ctk.CTkCheckBox(review_area,text='حفظ ملف PDF على الجهاز بعد إرساله إلى تليجرام (اختياري)',
        variable=export_pdf,height=48)
    export_choice.pack(fill='x',padx=8,pady=4)

    def signature():
        return fingerprint(dict(project=owner.client.project_id,actor=owner.client._actor,
            local=owner.store.read(),options=read_options(),targets=read_targets()))

    def name(target):
        return 'مجموعة المشروع — التقرير العام' if target is None else 'مجموعة '+doctor_label(owner.client.doctors,target)

    def view_file(target):
        require(prepared and signature()==prepared['signature'],'تغيرت البيانات أو الوجهة أو الخيارات؛ اضغط معاينة مجدداً')
        path=prepared['files'][target]
        require(owner.io(path.read_bytes)==prepared['payloads'][target], 'تغير PDF؛ اضغط معاينة مجدداً')
        owner.io(lambda:open_pdf(path))
        if window.winfo_exists():prepared['viewed'].add(target)

    def preview():
        options=read_options();targets=read_targets()
        require(targets,'اختر مجموعة واحدة على الأقل')
        before=signature()
        prepared.clear()
        for child in files_area.winfo_children():child.destroy()
        local=owner.store.read()
        delivery=ReportDelivery(owner,local)
        delivery.check_targets(targets)
        font=owner.settings.get('PDF_FONT')
        if font and not Path(font).is_absolute():
            import sys
            base=Path(sys.executable).resolve().parent if getattr(sys,'frozen',False) else Path(__file__).resolve().parent
            font=str(base/font)
        payloads={};files={};notes=[]
        for target in targets:
            rows=[row for row in delivery.reviewed['patients'] if target is None or row.get('responsibleSupervisorId')==target]
            notes.append(name(target)+'\n'+readiness_notes(rows))
            if options['format']=='pdf':
                path=owner.io(lambda t=target:render_report(owner.client,delivery.reviewed,t,font,options,report_day=delivery.report_day))
                files[target]=path;payloads[target]=owner.io(path.read_bytes)
            else:payloads[target]=report_text(owner.client,delivery.reviewed,target,report_day=delivery.report_day)
        require(signature()==before,'تغيرت البيانات أو الخيارات أثناء إعداد المعاينة؛ أعدها')
        if not window.winfo_exists():return
        save_options(options)
        prepared.update(delivery=delivery,signature=before,options=options,targets=tuple(targets),
                        payloads=payloads,files=files,viewed=set())
        review=publication_review(delivery.plan,owner.client.zone) if delivery.needs_publish else 'بيانات المرضى مطابقة للنسخة المنشورة التي جرى فحصها؛ لا يلزم نشر جديد.'
        content=review+'\n\n'+'\n\n'.join(notes)
        if options['format']=='text':content+='\n\nمعاينة النص الذي سيُرسل:\n'+ '\n\n'.join(payloads[t] for t in targets)
        else:content+='\n\nافتح وافحص PDF لكل وجهة أدناه. بعد ذلك اضغط «رفع للشبكة وإرسال تقرير» للتأكيد النهائي.'
        set_readonly_text(text,content)
        for target in files:
            ctk.CTkButton(files_area,text='معاينة PDF — '+name(target),height=48,
                command=lambda t=target:invoke(lambda:view_file(t))).pack(fill='x',padx=8,pady=3)
        if files:view_file(targets[0])

    def send():
        require(prepared and signature()==prepared['signature'],'اضغط معاينة بعد آخر تغيير للبيانات أو الوجهة أو الخيارات')
        delivery=prepared['delivery']
        delivery.require_unchanged()
        delivery.check_targets(prepared['targets'])
        for target,path in prepared['files'].items():
            require(target in prepared['viewed'],'عاين ملف كل وجهة مختارة أولاً')
            require(owner.io(path.read_bytes)==prepared['payloads'][target],'تغير ملف PDF بعد المعاينة؛ أعدها')
        confirmation='هل تؤكد إرسال التقارير التي عاينتها إلى:\n'+'\n'.join(name(t) for t in prepared['targets'])
        confirmation+='\nالمناوبة: '+delivery.report_day
        if export_pdf.get() and prepared['files']:confirmation+='\nحفظ ملف PDF على الجهاز بعد تأكيد إرساله إلى تليجرام.'
        if delivery.needs_publish:confirmation+='\nستُنشر النسخة الكاملة التي راجعتها أولاً، ثم يُرسل التقرير. فشل التسليم لا يلغي نشر البيانات.'
        if delivery.needs_publish:confirmation+='\nتأكيدك يستبدل بيانات المناوبة المنشورة بالنسخة المحلية الكاملة التي راجعتها؛ آخر نشر ناجح هو المعتمد.'
        if not messagebox.askyesno('تأكيد رفع للشبكة وإرسال تقرير',confirmation,default='no'):return
        delivery.plan.force_local=True
        require(signature()==prepared['signature'],'تغيرت البيانات بعد المراجعة؛ أعد المعاينة')
        result.append(dict(delivery=delivery,exportPdf=export_pdf.get() and bool(prepared['files']),
            payloads=[(t,prepared['options']['format'],prepared['payloads'][t]) for t in prepared['targets']]))
        window.destroy()

    def cancel():
        if not working[0]:window.destroy()

    def invoke(action):
        if working[0]:return
        working[0]=True
        for control in buttons.values():control.configure(state='disabled')
        export_choice.configure(state='disabled')
        try:action()
        except BridgeError as error:messagebox.showerror('مراجعة التقرير',str(error))
        except Exception:messagebox.showerror('مراجعة التقرير','تعذر إعداد التقرير؛ تحقق من الملفات والاتصال ثم أعد المعاينة. لم يبدأ إرسال جديد من هذه النافذة.')
        finally:
            working[0]=False
            if window.winfo_exists():
                for control in buttons.values():control.configure(state='normal')
                export_choice.configure(state='normal')

    buttons.update(dialog_actions(actions,primary=('رفع للشبكة وإرسال تقرير',lambda:invoke(send)),
        cancel=('إلغاء',cancel),secondary=[('معاينة التقرير',lambda:invoke(preview))]))
    window.protocol('WM_DELETE_WINDOW',cancel)
