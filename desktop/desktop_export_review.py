"""Previewed local exports: confirmed data publication or explicitly marked drafts.

Standalone exports are always drafts. Normal copies are an explicit option
in the reviewed publish/send flow.
"""
import copy
from pathlib import Path
from tkinter import filedialog
import desktop_widgets as ctk
from desktop_widgets import messagebox
from desktop_forms import modal, button, dialog_actions
from desktop_report_labels import report_filename
from desktop_model import patient_edit_content
from desktop_protocol import require, fingerprint, current_date
from desktop_reports import options_dialog, report_text


DRAFT_LABEL = 'مسودة محلية · لم يُعتمد نشرها'


def export_content(shift, doctors, all_doctors=False):
    ids=set(doctors) if all_doctors else set(filter(None,shift['meta'].get('doctors','').split(',')))
    for row in shift['patients']:
        ids.update(filter(None,(row.get('responsibleResidentId'),row.get('responsibleSupervisorId'))))
    return dict(meta={key:shift['meta'].get(key,'') for key in ('shiftId','shiftDate','doctors','sortSpec')},
        patients=[dict(id=row['externalId'],content=patient_edit_content(row),
                       order=row.get('sortOrder',''),assignment=row.get('assignmentChangedOn') or '')
                  for row in shift['patients']],
        doctors={key:doctors.get(key) for key in sorted(ids)})


class ExportReview:
    def __init__(self, owner, options, separate=False, assignments=False):
        self.owner,self.options=owner,dict(options)
        self.separate,self.assignments=separate,assignments
        self.report_day=current_date(owner.client.zone)
        self.prepared=None

    def identity(self, shift=None, doctors=None):
        owner=self.owner
        owner.ready(False)
        value=dict(project=owner.client.project_id,config=copy.deepcopy(owner.client.config),actor=owner.client._actor,
            content=export_content(shift if shift is not None else owner.store.read(),
                                   doctors if doctors is not None else owner.client.doctors, self.assignments))
        return owner.io(lambda:fingerprint(value))

    def require_current(self, draft):
        require(self.prepared and self.prepared['draft']==draft and self.identity()==self.prepared['identity'],
                'تغيرت البيانات أو الحساب أو المشروع أو وضع المسودة؛ أعد المعاينة')
        for item in self.prepared['files']:
            if item['path'] is not None:
                require(self.owner.io(item['path'].read_bytes)==item['raw'],'تغير ملف المعاينة؛ أعد إعداد التقرير')
        if not draft and not self.assignments:
            require(getattr(self.owner,'_confirmed_pdf_export',None)==self.prepared['identity'],
                    'عاين التقرير ثم أكّد رفع بياناته قبل التصدير الرسمي، أو اختر مسودة محلية')

    def preview(self, draft, font):
        require(draft or self.assignments,'التصدير المستقل متاح للمسودات فقط؛ النسخة العادية اختيارية ضمن رفع التقرير وإرساله')
        from desktop_pdf import render_report, render_assignments
        owner=self.owner
        identity=self.identity();local=owner.store.read();files=[]
        supervisor=owner.selected_supervisor() if not self.assignments else None
        groups=list(dict.fromkeys(p.get('responsibleSupervisorId') or None for p in local['patients'])) if self.separate else [supervisor]
        require(groups,'لا يوجد مرضى للتصدير')
        options=dict(self.options,local_draft=draft)
        for group in groups:
            subset=copy.deepcopy(local)
            if self.separate:
                subset['patients']=[p for p in local['patients'] if (p.get('responsibleSupervisorId') or None)==group]
                if group:subset['meta']['doctors']=group
            if options['format']=='text' and not self.assignments:
                value=report_text(owner.client,subset,None if self.separate else group,self.report_day)
                if draft:value=DRAFT_LABEL+'\n\n'+value
                raw=value.encode('utf-8');path=None
            else:
                path=owner.io(lambda s=subset,g=group:render_assignments(owner.client,s,font,options,report_day=self.report_day)
                    if self.assignments else render_report(owner.client,s,None if self.separate else g,font,options,report_day=self.report_day))
                raw=owner.io(path.read_bytes)
            label='توزيع الأسماء' if self.assignments else 'التقرير العام' if group is None and not self.separate else owner.client.doctors.get(group,{}).get('name','دون مشرف')
            name=report_filename(self.report_day,supervisor=group,supervisor_name=owner.client.doctors.get(group,{}).get('name'),
                assignments=self.assignments,unassigned=self.separate and group is None,draft=draft,demo=bool(owner.demo),
                extension='.txt' if path is None else '.pdf')
            if path is not None:
                def rename_preview(source=path,display_name=name):
                    target=source.with_name(display_name)
                    if target!=source:source.rename(target)
                    return target
                path=owner.io(rename_preview)
            files.append(dict(label=label,name=name,path=path,raw=raw,opened=False))
        require(identity==self.identity(),'تغيرت البيانات أثناء إعداد المعاينة؛ أعدها')
        self.prepared=dict(identity=identity,draft=draft,local=local,files=files)
        return files

    def require_viewed(self, draft):
        # Viewing a regular PDF is allowed before publication; saving it is not.
        require(self.prepared and self.prepared['draft']==draft and self.identity()==self.prepared['identity'],
                'عاين الملفات بعد آخر تعديل للبيانات أو وضع المسودة')
        require(all(item['opened'] for item in self.prepared['files']),'افتح معاينة كل ملف مختار أولاً')
        for item in self.prepared['files']:
            if item['path'] is not None:require(self.owner.io(item['path'].read_bytes)==item['raw'],'تغير PDF؛ أعد المعاينة')

    def save(self, draft):
        require(draft or self.assignments,'التصدير المستقل يحفظ مسودة معلّمة فقط')
        self.require_viewed(draft);self.require_current(draft)
        files=self.prepared['files'];extension='.txt' if self.options['format']=='text' and not self.assignments else '.pdf'
        if len(files)>1:
            folder=filedialog.askdirectory(title='حفظ الملفات المعاينة')
            if not folder:return
            def available_names():
                destinations=[]
                for item in files:
                    requested=Path(folder)/item['name'];target=requested;number=2
                    while target.exists() or target in destinations:
                        target=requested.with_name(requested.stem+'_'+str(number)+requested.suffix);number+=1
                    destinations.append(target)
                return destinations
            destinations=self.owner.io(available_names)
        else:
            name=filedialog.asksaveasfilename(title='حفظ المسودة المعاينة' if draft else 'حفظ توزيع المرضى المعاين',
                initialfile=files[0]['name'],defaultextension=extension,filetypes=[('Report','*'+extension)])
            if not name:return
            destinations=[Path(name)]
        for item,path in zip(files,destinations):
            self.require_current(draft)
            if self.assignments:
                self.owner.io(self.owner.client.require_local_admin)
                self.require_current(draft)
            def write(p=path,raw=item['raw']):
                with p.open('xb' if len(files)>1 else 'wb') as stream:stream.write(raw)
                require(p.read_bytes()==raw,'تعذر التحقق من حفظ الملف كاملاً')
            self.owner.io(write)
        self.owner.status.set('تم حفظ توزيع المرضى محلياً.' if self.assignments else 'تم حفظ المسودة المحلية المعلّمة؛ لم يحدث رفع.' if draft else 'تم حفظ الملفات المعاينة بعد تأكيد رفع بياناتها.')


def run_export(owner, separate=False, assignments=False):
    owner.ready(False)
    if assignments:owner.io(owner.client.require_local_admin)
    options=options_dialog(owner,export_only=True,assignments=assignments,force_pdf=separate or assignments)
    if options is None:return
    font=owner.settings.get('PDF_FONT')
    if font and not Path(font).is_absolute():
        import sys
        base=Path(sys.executable).resolve().parent if getattr(sys,'frozen',False) else Path(__file__).resolve().parent
        font=str(base/font)
    review=ExportReview(owner,options,separate,assignments)
    draft=not assignments
    title='تصدير توزيع المرضى (PDF)' if assignments else 'تصدير مسودة'
    preview_label='معاينة توزيع المرضى' if assignments else 'معاينة المسودة'
    save_label='حفظ توزيع المرضى المعاين' if assignments else 'حفظ المسودة المعاينة'
    window=modal(owner,title,'740x680')
    actions=ctk.CTkFrame(window);actions.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    working=[False]
    ctk.CTkLabel(area,text='توزيع المرضى بالأسماء فقط؛ معاينة وحفظ محلي دون رفع البيانات أو إرسال تقرير.' if assignments else 'مسودة محلية معلّمة في كل صفحة؛ لا تُرفع بيانات ولا يُرسل تقرير. عاين المسودة ثم احفظها.',
        wraplength=620,justify='right').pack(fill='x',padx=10,pady=6)
    files_area=ctk.CTkFrame(area);files_area.pack(fill='x')
    def current_preview():
        try:return bool(review.prepared and review.prepared['draft']==draft and review.identity()==review.prepared['identity'])
        except Exception:return False
    def refresh():
        same=current_preview()
        pending=next((item for item in review.prepared['files'] if not item['opened']),None) if same else None
        label=save_label if same and pending is None else preview_label+(' — '+pending['label'] if pending else '')
        primary.configure(text=label,state='disabled' if working[0] else 'normal')
    def invoke(action):
        if working[0]:return
        working[0]=True;primary.configure(state='disabled')
        try:action()
        except Exception as error:
            from desktop_protocol import BridgeError
            messagebox.showerror(title,str(error) if isinstance(error,(BridgeError,ValueError)) else 'تعذر إعداد الملف أو حفظه. لم يحدث رفع بيانات أو إرسال تقرير.')
        finally:
            working[0]=False
            if window.winfo_exists():refresh()
    def open_file(item):
        require(current_preview(),'تغيرت البيانات؛ أعد المعاينة')
        if item['path'] is None:owner.text_dialog('معاينة المسودة',item['raw'].decode('utf-8'))
        else:
            from desktop_platform import open_pdf
            require(owner.io(item['path'].read_bytes)==item['raw'],'تغير PDF؛ أعد المعاينة')
            owner.io(lambda:open_pdf(item['path']))
        item['opened']=True
    def advance():
        if not current_preview():
            files=review.preview(draft,font)
            for child in files_area.winfo_children():child.destroy()
            if len(files)>1:
                for item in files:
                    button(files_area,'معاينة المسودة — '+item['label'],lambda row=item:invoke(lambda:open_file(row)))
            open_file(files[0]);return
        pending=next((item for item in review.prepared['files'] if not item['opened']),None)
        if pending:open_file(pending)
        else:review.save(draft)
    primary=button(area,preview_label,lambda:invoke(advance))
    def close():
        if not working[0]:window.destroy()
    dialog_actions(actions,cancel=('إغلاق',close))
    window.protocol('WM_DELETE_WINDOW',close)
    refresh();owner.root.wait_window(window)


def save_published_pdfs(owner, delivery, payloads):
    """Optional normal copies, invoked only after confirmed Telegram delivery."""
    require(owner.report_attempt.publication=='CONFIRMED' and owner.report_attempt.delivery=='CONFIRMED',
            'يجب تأكيد إرسال التقرير إلى تليجرام قبل حفظ PDF العادي')
    files=[(target,raw) for target,format_name,raw in payloads if format_name=='pdf']
    if not files:return
    owner.ready(False)
    def identity():
        return owner.io(lambda:fingerprint(dict(project=owner.client.project_id,actor=owner.client._actor,
            content=export_content(owner.store.read(),owner.client.doctors))))
    confirmed=identity()
    named=[(report_filename(delivery.report_day,target,owner.client.doctors.get(target,{}).get('name')),raw)
           for target,raw in files]
    if len(named)==1:
        destination=filedialog.asksaveasfilename(title='حفظ PDF على الجهاز بعد الإرسال إلى تليجرام',initialfile=named[0][0],
            defaultextension='.pdf',filetypes=[('PDF','*.pdf')])
        if not destination:return
        targets=[(Path(destination),named[0][1])]
    else:
        folder=filedialog.askdirectory(title='حفظ ملفات PDF على الجهاز بعد الإرسال إلى تليجرام')
        if not folder:return
        targets=[]
        for name,raw in named:
            requested=Path(folder)/name;target=requested;number=2
            while owner.io(target.exists) or any(path==target for path,_ in targets):
                target=requested.with_name(requested.stem+'_'+str(number)+'.pdf');number+=1
            targets.append((target,raw))
    for path,raw in targets:
        require(identity()==confirmed,'تغيرت البيانات أو الحساب؛ أعد مراجعة التقرير قبل الحفظ')
        def save(destination=path,data=raw):
            with destination.open('xb' if len(targets)>1 else 'wb') as stream:stream.write(data)
            require(destination.read_bytes()==data,'تعذر التحقق من حفظ PDF كاملاً')
        owner.io(save)
