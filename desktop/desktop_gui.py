#!/usr/bin/env python3
"""Standalone ShiftReport desktop client; Windows 7 SP1 / Linux; Python 3.8-compatible source."""
from __future__ import annotations
import argparse
import copy
from datetime import datetime
import os
import sys
from pathlib import Path
import threading
import time
from concurrent.futures import ThreadPoolExecutor
import tkinter as tk
from tkinter import filedialog
from desktop_widgets import messagebox, simpledialog, set_readonly_text
import uuid
import desktop_widgets as ctk
from desktop_protocol import (BridgeError, Client, ZoneInfo, current_date, doctor_pointer,
                              fingerprint, is_admin, open_join, packed, pointer, require)
from desktop_model import (LABELS, DIAGNOSIS, carry_forward, doctor_label, new_patient, task_counts, patient_text, shift_text)
from desktop_settings import read_settings, raw_config
from desktop_store import Workspace
from desktop_platform import data_directory, open_pdf, acquire_instance_lock
from desktop_forms import button, combo, edit_patient, modal
from desktop_input import ArabicEntry

BASE = Path(sys.executable).resolve().parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent


class Window:
    def __init__(self, root, settings):
        self.root, self.settings = root, settings
        self.client = self.store = self.plan = self.raw_draft = None
        self.executor = ThreadPoolExecutor(max_workers=1)
        self.busy = False
        self.actor_ids, self.destinations = [], [None]
        root.title('ShiftReport — Telegram')
        root.geometry('{}x{}'.format(min(1150,max(760,root.winfo_screenwidth()-50)),min(760,max(500,root.winfo_screenheight()-100))))
        root.minsize(760,500)
        root.report_callback_exception = lambda *args: messagebox.showerror('ShiftReport','تعذر إتمام العملية المحلية. إذا بدأ إرسال فراجع سجل العمليات قبل الإعادة.')
        self.status = tk.StringVar(value='ابدأ الاتصال بالمشروع. تحفظ المسودات محليا بصورة مشفرة.')
        self.zone = tk.StringVar(value=settings.get('TIMEZONE') or 'Asia/Damascus')
        ctk.CTkLabel(root,text='تقرير المناوبة — تليجرام',font=('Arial',23,'bold'),anchor='e').pack(fill='x',padx=14,pady=8)
        self.status_label = ctk.CTkLabel(root,textvariable=self.status,anchor='e',justify='right',wraplength=1000)
        self.status_label.pack(side='bottom',fill='x',padx=14,pady=8)
        body = ctk.CTkFrame(root); body.pack(fill='both',expand=True,padx=8,pady=4)
        sidebar = ctk.CTkScrollableFrame(body,width=295); sidebar.pack(side='right',fill='y',padx=5,pady=5)
        self.content = ctk.CTkFrame(body); self.content.pack(side='left',fill='both',expand=True,padx=5,pady=5)
        button(sidebar,'الاتصال بالمشروع / إعادة الاتصال',lambda:self.run(self.connect))
        ctk.CTkLabel(sidebar,text='المنطقة الزمنية نفسها في Android',anchor='e').pack(fill='x',padx=10)
        ctk.CTkEntry(sidebar,textvariable=self.zone,justify='right').pack(fill='x',padx=8,pady=4)
        self.actor = ctk.CTkComboBox(sidebar,values=['اختر الطبيب'],state='readonly',justify='right'); self.actor.pack(fill='x',padx=8,pady=4)
        for label,action in [('تحديث سجل الأطباء',self.refresh_identity),('إثبات الهوية برمز تليجرام',self.login),
                             ('جلب / اختيار مناوبة',self.download),('أطباء المناوبة',self.roster),
                             ('مراجعة المسودة والتعارضات',self.review),('نشر النسخة المراجعة',self.publish)]:
            button(sidebar,label,lambda f=action:self.run(f))
        self.supervisor = ctk.CTkComboBox(sidebar,values=['التقرير العام'],state='readonly',justify='right'); self.supervisor.pack(fill='x',padx=8,pady=6)
        button(sidebar,'إرسال تقرير — معاينة وتأكيد',lambda:self.run(self.report))
        button(sidebar,'سجل العمليات والتسليم',lambda:self.run(self.resolve))
        button(sidebar,'حفظ نسخة مسودة مشفرة',lambda:self.run(self.export_draft))
        button(sidebar,'استعادة مسودة مشفرة',lambda:self.run(self.import_draft))
        button(sidebar,'حفظ PDF محليا',lambda:self.run(lambda:self.report(export_only=True)))
        ctk.CTkLabel(sidebar,text='إدارة الأطباء والأدوار والمشروع من Android.\nالنشر يتطلب اتصالا وهوية متحققة.',wraplength=275,justify='right').pack(padx=8,pady=8)
        tools = ctk.CTkFrame(self.content); tools.pack(fill='x',padx=5,pady=4)
        self.search = tk.StringVar()
        ctk.CTkLabel(tools,text='بحث',anchor='e').pack(side='right',padx=5)
        ArabicEntry(tools,textvariable=self.search,justify='right').pack(side='right',fill='x',expand=True,padx=5,pady=5)
        self.search.trace_add('write',lambda *args:self.render())
        self.filter = ctk.CTkComboBox(tools,values=['الكل','أولوية','مهام معلقة','مهام متأخرة'],state='readonly',width=140,command=lambda value:self.render())
        self.filter.pack(side='left',padx=5)
        actions = ctk.CTkFrame(self.content); actions.pack(fill='x',padx=5,pady=4)
        for label,action in [('إضافة مريض',self.add_patient),('ترتيب بالاسم',lambda:self.sort_patients('name')),('ترتيب بالقبول',lambda:self.sort_patients('admitDate'))]:
            ctk.CTkButton(actions,text=label,height=48,width=130,command=lambda f=action:self.run(f)).pack(side='right',padx=3,pady=5)
        self.metrics = ctk.CTkLabel(self.content,text='',anchor='e',justify='right',wraplength=650); self.metrics.pack(fill='x',padx=12,pady=6)
        self.patients = ctk.CTkScrollableFrame(self.content); self.patients.pack(fill='both',expand=True,padx=5,pady=4)
        self.content.bind('<Configure>',self.resize_content)
        root.protocol('WM_DELETE_WINDOW',self.close)
        root.after(200,lambda:self.run(self.connect))

    def resize_content(self,event):
        width=max(240,event.width-40)
        self.metrics.configure(wraplength=width)
        self.status_label.configure(wraplength=max(300,self.root.winfo_width()-40))
        for frame in self.patients.winfo_children():
            for child in frame.winfo_children():
                if isinstance(child,ctk.CTkLabel):child.configure(wraplength=width)

    def close(self):
        if self.busy:
            self.status.set('العملية جارية؛ انتظر اكتمالها قبل الإغلاق.'); return
        self.executor.shutdown(wait=False)
        if self.client: self.client.telegram._http.close()
        self.root.destroy()

    def run(self,action):
        if self.busy: return
        self.busy=True; self.root.configure(cursor='watch'); self.root.update_idletasks()
        try:
            action()
        except BridgeError as error:
            messagebox.showerror('ShiftReport',str(error))
        except Exception:
            messagebox.showerror('ShiftReport','تعذر إتمام العملية. تحقّق من الملفات والاتصال. إذا بدأ إرسال فراجع سجل العمليات قبل الإعادة.')
        finally:
            self.busy=False; self.root.configure(cursor=''); self.render()

    def io(self,action):
        if threading.current_thread() is not threading.main_thread(): return action()
        future=self.executor.submit(action)
        while not future.done():
            self.root.update(); time.sleep(.02)
        return future.result()

    def ready(self, online=True):
        require(self.client is not None and self.store is not None,'اتصل بالمشروع أولا')
        require(self.zone.get()==self.client.zone,'تغيرت المنطقة الزمنية؛ أعد فتح الاتصال')
        if online: require(self.client._actor is not None,'أثبت هوية الطبيب عبر تليجرام أولا')

    def editable(self):
        self.ready(False)
        draft=self.store.read()
        require(draft['meta']['shiftDate']==current_date(self.client.zone),'المناوبات السابقة للعرض فقط؛ الانتقال الساعة 08:30')
        return draft

    def save_draft(self,draft):
        self.editable()
        self.io(lambda:self.store.save_draft(draft))
        self.plan=None; self.raw_draft=None
        self.status.set('حفظت المسودة المشفرة محليا؛ راجعها قبل النشر.')
        self.render()

    def set_doctors(self):
        doctors=self.client.doctors
        self.actor_ids=[key for key,d in doctors.items() if d.get('telegram_id')
                        and (not self.client.admin_only or is_admin(d))]
        self.actor.configure(values=[doctor_label(doctors,key) for key in self.actor_ids] or ['لا يوجد طبيب مرتبط'])
        selected=self.client._actor if self.client._actor in self.actor_ids else (self.actor_ids[0] if self.actor_ids else None)
        self.actor.set(doctor_label(doctors,selected) if selected else 'لا يوجد طبيب مرتبط')
        self.refresh_destinations(doctors)

    def connect(self):
        raw = self.client is None and self.settings.get('CONNECTION_MODE') == 'raw'
        if raw:
            config = raw_config(self.settings)
        else:
            path=self.settings.get('JOIN_FILE') if self.client is None else None
            if path:
                path=Path(path); path=path if path.is_absolute() else BASE/path
            if not path or not Path(path).is_file():
                path=filedialog.askopenfilename(title='ملف الانضمام الحالي',filetypes=[('ShiftReport join','*.srjoin.json')])
            if not path:return
            password=simpledialog.askstring('ملف الانضمام','كلمة مرور ملف الانضمام:',show='*',parent=self.root)
            if password is None:return
            try: config=self.io(lambda:open_join(path,password))
            finally: password=None
        zone=self.zone.get().strip(); ZoneInfo(zone)
        candidate=Client(config,zone,admin_only=raw); candidate.telegram.runner=self.io
        if raw:
            try:
                if not self.verify_raw_admin(candidate):
                    candidate.telegram._http.close();return
            except Exception:
                candidate.telegram._http.close();raise
        if self.client:
            require(candidate.project_id==self.client.project_id,'تغيير المشروع/المفتاح/المواضيع ممنوع داخل الجلسة؛ افتح جلسة منفصلة')
            snapshot=self.io(candidate.fetch)
            bot=candidate.telegram.call('getMe')
            member=candidate.telegram.call('getChatMember',{'chat_id':config['chatId'],'user_id':bot['id']})
            require(member.get('status') in {'administrator','creator'},'البوت ليس مديرا في المشروع')
            # Protected-file reconnect is available before login. No manual token replacement.
            # The old live client is closed only after the candidate and local workspace validate.
        directory=Path(self.settings.get('DATA_DIR') or data_directory())
        if not directory.is_absolute(): directory=BASE/directory
        local_passphrase=None
        if os.name!='nt' and not candidate.config.get('telegramDataKey'):
            key_exists=self.io(lambda:(directory/(candidate.project_id+'.linux-key')).exists())
            prompt='كلمة مرور المسودة المحلية:' if key_exists else 'اختر كلمة مرور للمسودة المحلية (10 أحرف على الأقل):'
            local_passphrase=simpledialog.askstring('حماية المسودة المحلية',prompt,show='*',parent=self.root)
            if local_passphrase is None:
                candidate.telegram._http.close();return
            if not key_exists:
                confirmation=simpledialog.askstring('حماية المسودة المحلية','أعد كلمة مرور المسودة المحلية:',show='*',parent=self.root)
                matches=confirmation==local_passphrase;confirmation=None
                if not matches:
                    local_passphrase=None;candidate.telegram._http.close()
                    raise BridgeError('لم تتطابق كلمتا مرور المسودة المحلية')
        live_doctors=candidate.doctors
        try:
            workspace=self.io(lambda:Workspace(directory,candidate,local_passphrase))
        except Exception:
            candidate.telegram._http.close();raise
        finally:
            local_passphrase=None
        if self.client:self.client.telegram._http.close()
        self.client,self.store=candidate,workspace
        self.plan=None; self.raw_draft=None
        if raw:
            self.client.doctors=live_doctors
        self.set_doctors()
        self.status.set('المشروع: '+config['hospitalName']+' — المسودة المحلية متاحة. حدّث الأطباء وأثبت الهوية قبل النشر.')
        if not self.client.doctors:
            self.refresh_identity()

    def verify_raw_admin(self, candidate):
        snapshot=self.io(candidate.fetch)
        admins=[key for key,doctor in snapshot['doctors'].items()
                if is_admin(doctor) and doctor.get('telegram_id')]
        require(admins,'لا يوجد مدير مرتبط بتليجرام؛ انشر سجل الأطباء من Android أولا')
        choice=self.choose('اختر المدير للتحقق من الاتصال المباشر',
                           [doctor_label(snapshot['doctors'],key) for key in admins])
        if choice is None:return False
        self.io(lambda:candidate.begin_login(admins[choice]))
        for _ in range(5):
            code=simpledialog.askstring('التحقق من المدير','رمز التحقق المرسل إلى تليجرام المدير:',show='*',parent=self.root)
            if code is None:return False
            try: doctor=self.io(lambda:candidate.check_login(code))
            finally: code=None
            if doctor:
                candidate.doctors=snapshot['doctors']
                return True
            messagebox.showerror('رمز التحقق','الرمز غير صحيح')
        return False

    def refresh_identity(self):
        self.ready(False)
        snapshot=self.io(self.client.fetch)
        self.client.doctors=snapshot['doctors']; self.set_doctors()
        self.plan=None
        self.status.set('حدّث سجل الأطباء. اختر طبيبك ثم أثبت الهوية.')

    def refresh_destinations(self,doctors):
        self.destinations=[None]+[key for key,d in doctors.items() if d['clinicalRole']=='SUPERVISOR' and d.get('destination')]
        self.supervisor.configure(values=['التقرير العام']+[doctor_label(doctors,key) for key in self.destinations[1:]])
        self.supervisor.set('التقرير العام')

    def login(self):
        self.ready(False)
        selected=self.actor.get()
        actor=next((key for key in self.actor_ids if doctor_label(self.client.doctors,key)==selected),None)
        require(actor is not None,'حدّث السجل واختر الطبيب')
        self.plan=None; self.io(lambda:self.client.begin_login(actor))
        for _ in range(5):
            code=simpledialog.askstring('رمز تليجرام','رمز تسجيل الدخول الذي وصل إلى حسابك على البوت:',show='*',parent=self.root)
            if code is None:return
            doctor=self.io(lambda:self.client.check_login(code)); code=None
            if doctor:
                self.status.set('هوية مؤكدة: '+doctor['name']+' — اجلب أو راجع المسودة المحلية.');return
            messagebox.showerror('رمز التحقق','الرمز غير صحيح')

    def choose(self,title,values):
        window=modal(self,title,'760x500')
        area=ctk.CTkScrollableFrame(window); area.pack(fill='both',expand=True,padx=8,pady=8)
        result=[]
        def commit(index): result.append(index); window.destroy()
        for index,value in enumerate(values):
            button(area,str(value),lambda i=index:commit(i))
        button(window,'إلغاء',window.destroy)
        self.root.wait_window(window)
        return result[0] if result else None

    def download(self):
        self.ready()
        snapshot=self.io(self.client.fetch); self.io(lambda:self.client.authorize(snapshot['doctors']))
        shifts=sorted(snapshot['bundle'],key=lambda s:s['meta']['shiftDate'],reverse=True)
        today=current_date(self.client.zone)
        if not any(s['meta']['shiftDate']==today for s in shifts):
            shifts.insert(0,{'meta':{'schemaVersion':'7','shiftDate':today,'shiftId':str(uuid.uuid4()),'doctors':''},'patients':[]})
        choice=self.choose('المناوبة المطلوبة',[s['meta']['shiftDate']+' — '+str(len(s['patients']))+' مريض' for s in shifts])
        if choice is None:return
        if self.store.state['draft'] is not None and not messagebox.askyesno('استبدال المسودة','هل تستبدل المسودة المحلية؟ تحفظ نسخة مشفرة سابقة تلقائيا. استخدم حفظ نسخة للاحتفاظ بنسخ إضافية.',default='no'):return
        selected=copy.deepcopy(shifts[choice])
        new_shift=not any(s['meta']['shiftId']==selected['meta']['shiftId'] for s in snapshot['bundle'])
        if new_shift and snapshot['bundle'] and messagebox.askyesno('افتتاح مناوبة','هل تنقل مرضى آخر مناوبة والمهام المعلقة للمراجعة؟',default='no'):
            keep=messagebox.askyesno('مسؤوليات الأطباء','هل تحتفظ بالأطباء المسؤولين ومالكي المهام؟',default='no')
            previous=max(snapshot['bundle'],key=lambda s:s['meta']['shiftDate'])
            require(previous['meta']['shiftDate']<today,'توجد مناوبة مستقبلية؛ صحح تاريخ الأجهزة')
            selected=carry_forward(previous,self.client.zone,keep)
        previous=self.client.baseline
        self.client.baseline=snapshot
        try:self.io(lambda:self.store.write(self.client,selected))
        except Exception:
            self.client.baseline=previous; raise
        self.client.doctors=snapshot['doctors']; self.set_doctors(); self.plan=None
        self.status.set('جلبت '+selected['meta']['shiftDate']+' — تحفظ التعديلات محليا حتى مراجعتها ونشرها.')

    def render(self):
        if not hasattr(self,'patients'):return
        for child in self.patients.winfo_children():child.destroy()
        if self.store is None or self.store.state['draft'] is None:
            self.metrics.configure(text='افتح المشروع واجلب مناوبة، أو استعد مسودة مشفرة.');return
        draft=self.store.read(); rows=draft['patients']; pending,overdue=task_counts(rows)
        roster=' / '.join(self.client.doctors.get(key,{}).get('name','طبيب محفوظ') for key in draft['meta'].get('doctors','').split(',') if key)
        self.metrics.configure(text='{} · {} مريض · مهام معلقة {} (منها متأخرة {})\nأطباء المناوبة: {}'.format(draft['meta']['shiftDate'],len(rows),pending,overdue,roster or 'لم تحدد'))
        query=self.search.get().strip().casefold(); mode=self.filter.get()
        for index,row in enumerate(rows):
            if query and query not in ' '.join(str(row.get(k,'')) for k in ['name','admitNum','initialDiagnosis','treatmentPlan','followUp','labs']).casefold():continue
            counts=task_counts([row])
            if mode=='أولوية' and row.get('isPriority')!='Y':continue
            if mode=='مهام معلقة' and not counts[0]:continue
            if mode=='مهام متأخرة' and not counts[1]:continue
            frame=ctk.CTkFrame(self.patients); frame.pack(fill='x',padx=3,pady=4)
            ctk.CTkLabel(frame,text='{} — {} · قبول {}\n{} · مهام {} (متأخرة {})'.format(index+1,row['name'],row['admitNum'],DIAGNOSIS.get(row['diagnosisType'],''),*counts),font=('Arial',16),anchor='e',justify='right',wraplength=max(240,self.content.winfo_width()-40)).pack(fill='x',padx=10,pady=5)
            actions=ctk.CTkFrame(frame,fg_color='transparent'); actions.pack(fill='x')
            for label,action,width in [('عرض',lambda p=copy.deepcopy(row):self.details(p),70),('تحرير',lambda p=copy.deepcopy(row):self.edit(p),70),('حذف',lambda i=row['externalId']:self.delete_patient(i),65),('↑',lambda i=index:self.move(i,-1),40),('↓',lambda i=index:self.move(i,1),40)]:
                ctk.CTkButton(actions,text=label,width=width,height=48,command=lambda f=action:self.run(f)).pack(side='right',padx=3,pady=4)

    def details(self,row):
        self.text_dialog('تفاصيل المريض',patient_text(row,self.client.doctors,self.client.zone))

    def add_patient(self):
        self.editable(); edit_patient(self,new_patient(self.client.zone),True)

    def edit(self,row):
        self.editable(); edit_patient(self,row)

    def delete_patient(self,key):
        draft=self.editable()
        if messagebox.askyesno('حذف مريض','هل تؤكد حذف المريض ومهامه وشاراته من المسودة؟ ستراجع الحذف قبل النشر.',default='no'):
            draft['patients']=[p for p in draft['patients'] if p['externalId']!=key];self.save_draft(draft)

    def move(self,index,delta):
        draft=self.editable(); target=index+delta
        if 0<=target<len(draft['patients']):
            draft['patients'][index],draft['patients'][target]=draft['patients'][target],draft['patients'][index]
            self.save_draft(draft)

    def sort_patients(self,key):
        draft=self.editable()
        if messagebox.askyesno('ترتيب محفوظ','هل تحفظ هذا الترتيب للعرض والنشر والتقارير؟',default='no'):
            draft['patients'].sort(key=lambda p:p.get(key,'')); self.save_draft(draft)

    def roster(self):
        draft=self.editable(); doctors=self.client.doctors
        window=modal(self,'أطباء المناوبة','700x560')
        controls=ctk.CTkFrame(window); controls.pack(side='bottom',fill='x')
        area=ctk.CTkScrollableFrame(window); area.pack(fill='both',expand=True)
        selected=draft['meta'].get('doctors','').split(','); variables={}
        for key in list(dict.fromkeys(selected+list(doctors))):
            if not key:continue
            variable=tk.BooleanVar(value=key in selected);variables[key]=variable
            ctk.CTkCheckBox(area,text=doctor_label(doctors,key),variable=variable,height=48).pack(anchor='e',padx=10,pady=3)
        def save():
            values=[key for key,variable in variables.items() if variable.get()]
            if not values or any(key not in doctors for key in values):
                messagebox.showerror('المناوبة','حدد أطباء نشطين للمناوبة');return
            draft['meta']['doctors']=','.join(values); self.save_draft(draft);window.destroy()
        button(controls,'حفظ أطباء المناوبة',save);button(controls,'إلغاء',window.destroy)
        self.root.wait_window(window)

    def export_draft(self):
        self.ready(False); self.store.read()
        path=filedialog.asksaveasfilename(title='نسخة مشفرة',defaultextension='.srdraft',filetypes=[('Encrypted draft','*.srdraft')])
        if path:self.io(lambda:self.store.export(path,replace=Path(path).exists()))

    def import_draft(self):
        self.ready(False)
        path=filedialog.askopenfilename(title='استعادة مسودة للمشروع نفسه',initialdir=str(self.store.directory),filetypes=[('Encrypted draft','*.srdraft')])
        if path and messagebox.askyesno('استعادة','هل تستبدل المسودة المحلية؟ سجل التسليم الحالي يبقى محفوظا.',default='no'):
            self.io(lambda:self.store.import_draft(path,self.client));self.plan=None;self.set_doctors()
            self.status.set('استعدت المسودة؛ أعد المراجعة مع تليجرام قبل النشر.')

    def text_dialog(self,title,text,approve=False):
        window=modal(self,title,'920x680')
        controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
        widget=ctk.CTkTextbox(window,wrap='word',font=('Arial',15));widget.pack(fill='both',expand=True,padx=8,pady=8)
        copy_original=set_readonly_text(widget,text)
        button(controls,'نسخ النص الكامل',copy_original)
        accepted=[]
        def accept():accepted.append(True);window.destroy()
        if approve:button(controls,'راجعت النسخة المعروضة وأوافق على النشر',accept)
        button(controls,'إغلاق / إلغاء',window.destroy)
        self.root.wait_window(window);return bool(accepted)

    def conflict_choice(self,conflict):
        text='المريض / المناوبة: '+conflict['id']+'\nالحقل: '+LABELS.get(conflict['field'],conflict['field'])
        for label,key in [('نسخة الأساس','base'),('المسودة المحلية','local'),('نسخة تليجرام','remote')]:
            value=conflict[key]
            if conflict['field']=='__record__':
                value=patient_text(value,self.client.doctors,self.client.zone)
            elif conflict['field']=='doctors':
                value=' / '.join(doctor_label(self.client.doctors,d) for d in (value or '').split(',') if d)
            elif conflict['field']=='order':
                value='\n'.join(str(i+1)+' — '+self.conflict_patients.get(key,key) for i,key in enumerate(value))
            elif conflict['field']=='tasksJson':
                temporary=new_patient(self.client.zone);temporary['tasksJson']=value or '[]'
                value=patient_text(temporary,self.client.doctors,self.client.zone)
            text+='\n\n'+label+':\n'+str(value)
        self.text_dialog('مراجعة التعارض',text)
        choice=self.choose('اختر قيمة هذا الحقل / السجل',['اعتماد المحلي','اعتماد تليجرام'])
        return ['local','remote'][choice] if choice is not None else None

    def show_review(self,text):
        return self.text_dialog('مراجعة قبل النشر',text,approve=True)

    def review(self):
        self.ready();self.plan=None
        local=self.store.read(self.client);draft_hash=fingerprint(local)
        plan=self.io(lambda:self.client.plan(local))
        choices={}
        while plan.conflicts:
            self.conflict_patients={p['externalId']:p['name'] for s in plan.bundle+[local] for p in s['patients']}
            for conflict in plan.conflicts:
                value=self.conflict_choice(conflict)
                if value is None:return
                choices[(conflict['id'],conflict['field'])]=value
            next_plan=self.io(lambda:self.client.plan(local,choices))
            if next_plan.baseline_pointer!=plan.baseline_pointer or next_plan.registry_pointer!=plan.registry_pointer:
                choices={}
                messagebox.showinfo('تغيرت النسخة','وصلت نسخة أحدث أثناء مراجعة التعارضات؛ ستعاد المقارنة.')
                next_plan=self.io(lambda:self.client.plan(local))
            plan=next_plan
        plan=self.io(lambda:self.client.prepare(plan))
        require(fingerprint(self.store.read(self.client))==draft_hash,'تغير المسودة أثناء المراجعة؛ أعدها')
        before=next((s for s in plan.bundle if s['meta']['shiftId']==plan.shift['meta']['shiftId']),{'patients':[]})
        old={p['externalId']:p for p in before['patients']};new={p['externalId']:p for p in plan.shift['patients']}
        lines=['ترتيب المرضى المعروض سيصبح ترتيب التقرير والنشر.']
        for key in dict.fromkeys(list(old)+list(new)):
            if old.get(key)==new.get(key):continue
            lines+=['\nالمريض: '+new.get(key,old.get(key,{})).get('name',''), '\nقبل:\n'+patient_text(old.get(key),plan.remote['doctors'],self.client.zone), '\nبعد:\n'+patient_text(new.get(key),plan.remote['doctors'],self.client.zone)]
        lines+=['\nالنسخة الكاملة التي ستنشر:\n'+shift_text(plan.shift,plan.remote['doctors'],self.client.zone)]
        if self.show_review('\n'.join(lines)):
            self.client.approve(plan);self.plan=plan;self.raw_draft=draft_hash
            self.status.set('المراجعة جاهزة. اضغط نشر؛ أي تعديل أو نسخة أحدث سيوقف هذا النشر.')

    def publish(self):
        self.ready();require(self.plan is not None,'راجع المسودة أولا')
        require(fingerprint(self.store.read(self.client))==self.raw_draft,'تغيرت المسودة؛ أعد مراجعتها')
        plan=self.plan
        key=fingerprint({'operation':'publish','project':self.client.project_id,'draft':self.raw_draft,'remote':plan.baseline_pointer})
        result=self.io(lambda:self.client.publish(plan,lambda:self.store.receipt_start(key,'publish',self.client.config['chatId'],self.client.config.get('csvTopicId'),self.client._actor,snapshot=plan.shift['meta']['snapshotId'])))
        self.plan=None
        self.status.set('تليجرام أكد نشر النسخة. لا تعد العملية إذا فشل حفظ العرض المحلي لاحقا.')
        try:
            self.io(lambda:self.store.receipt_finish(result))
            self.io(lambda:self.store.write(self.client,plan.shift,backup=False))
        except Exception:
            raise BridgeError('تم النشر على تليجرام؛ تعذر تحديث المسودة محليا. لا تعد الإرسال. اجلب النسخة الحالية بعد حفظ مسودتك') from None
        messagebox.showinfo('تم النشر','نشر مؤكد؛ بقيت بيانات الأطباء والإعلانات والاستعادة في الفهرس.')

    def report(self,export_only=False):
        self.ready(not export_only);local=self.store.read(self.client)
        baseline=next((s for s in self.client.baseline['bundle'] if s['meta']['shiftId']==local['meta']['shiftId']),None)
        if export_only:
            from desktop_pdf import render_report
            font=self.settings.get('PDF_FONT')
            if font and not Path(font).is_absolute():font=str(BASE/font)
            supervisor=self.selected_supervisor()
            path=self.io(lambda:render_report(self.client,local,supervisor,font))
            destination=filedialog.asksaveasfilename(defaultextension='.pdf',filetypes=[('PDF','*.pdf')])
            if destination:
                def save_pdf():
                    with open(destination,'wb') as stream:stream.write(path.read_bytes())
                self.io(save_pdf)
            return
        require(baseline is not None,'انشر المناوبة أولا')
        def content(shift):
            return {'meta':{'shiftId':shift['meta']['shiftId'],'doctors':shift['meta'].get('doctors','')},'patients':shift['patients']}
        require(fingerprint(content(local))==fingerprint(content(baseline)),'المسودة يحمل تعديلات؛ راجعها وانشرها قبل إنشاء التقرير')
        snapshot=self.io(self.client.fetch);self.io(lambda:self.client.authorize(snapshot['doctors']))
        require(pointer(snapshot['state'])==pointer(self.client.baseline['state']) and doctor_pointer(snapshot['state'])==doctor_pointer(self.client.baseline['state']),'وصلت نسخة أحدث؛ اجلبها وراجع قبل التقرير')
        supervisor=self.selected_supervisor()
        destination='موضوع التقارير العام' if supervisor is None else doctor_label(snapshot['doctors'],supervisor)
        from desktop_pdf import render_report
        font=self.settings.get('PDF_FONT')
        if font and not Path(font).is_absolute():font=str(BASE/font)
        path=self.io(lambda:render_report(self.client,baseline,supervisor,font))
        raw=self.io(path.read_bytes);digest=fingerprint({'bytes':__import__('hashlib').sha256(raw).hexdigest()})
        self.io(lambda:open_pdf(path))
        if not messagebox.askyesno('تأكيد إرسال التقرير','افحص PDF الذي فتح.\nالوجهة: '+destination+'\nالتاريخ: '+baseline['meta']['shiftDate']+'\nهل توافق على إرسال هذا الملف؟',default='no'):return
        require(fingerprint({'bytes':__import__('hashlib').sha256(self.io(path.read_bytes)).hexdigest()})==digest,'تغير PDF بعد المعاينة')
        require(fingerprint(self.store.read(self.client))==fingerprint(local),'تغيرت المسودة بعد المعاينة')
        key=fingerprint({'operation':'report','project':self.client.project_id,'pointer':pointer(snapshot['state']),'shift':baseline['meta']['shiftId'],'supervisor':supervisor})
        repeat=False
        if self.store.receipts(status='SENT',key=key):
            repeat=messagebox.askyesno('نسخة إضافية','هذا التقرير سلم سابقا إلى الوجهة نفسها. هل تؤكد إرسال نسخة إضافية بعد المراجعة؟',default='no')
            if not repeat:return
        result=self.io(lambda:self.client.send_pdf(raw,baseline,supervisor,pointer(snapshot['state']),doctor_pointer(snapshot['state']),lambda chat,topic:self.store.receipt_start(key,'report',chat,topic,self.client._actor,repeat=repeat)))
        self.status.set('تليجرام أكد تسليم PDF. لا تعاود الإرسال إذا تعذر حفظ سجل المسودة.')
        try:self.io(lambda:self.store.receipt_finish(result))
        except Exception:raise BridgeError('تم تسليم التقرير؛ تعذر حفظ إيصال المسودة. لا تعد إرسال التقرير') from None
        messagebox.showinfo('تم التسليم','تم تسليم PDF مع حقول المرضى والمهام والشارات إلى الوجهة المعروضة.')

    def resolve(self):
        self.ready(False)
        records=self.store.receipts(status='UNKNOWN')
        if not records:
            self.text_dialog('سجل العمليات','\n'.join(' | '.join(row[:8]) for _,row in self.store.receipts()) or 'لا توجد عمليات');return
        choice=self.choose('اختر عملية واحدة للمراجعة',[str(index)+' | '+row[0]+' | '+row[6]+' | '+row[4]+' | '+row[1][:12] for index,row in records])
        if choice is None:return
        index,row=records[choice]
        if row[0]=='publish' and row[7].startswith('snapshot='):
            self.ready()
            state=self.io(self.client.fetch)['state']
            match=next((entry for entry in state.get('publicationJournal',[]) if entry['snapshotId']==row[7][9:]),None)
            if match:
                self.io(lambda:self.store.confirm_indexed_receipt(index,match['messageId']))
                messagebox.showinfo('تأكيد النشر','سجل الفهرس المثبت يؤكد نشر هذه النسخة؛ لا تكررها.');return
        confirmation=messagebox.askyesnocancel('تحقق يدوي','افحص العملية المختارة على تليجرام أولا. في النشر تحقق من الفهرس والمرضى، وليس مجرد وجود ملف في موضوع CSV. غيابها عن السجل الحالي ليس إثبات عدم وصولها.\nنعم: تؤكد أنها لم تصل وتسمح بمحاولتها.\nلا: وصلت أو لا تريد تكرارها.\nإلغاء: إبقاء الحظر.',default='cancel')
        if confirmation is not None:self.io(lambda:self.store.resolve_unknown(records[choice][0],confirmation))

    def selected_supervisor(self):
        value=self.supervisor.get()
        if value=='التقرير العام':return None
        key=next((key for key in self.destinations[1:] if doctor_label(self.client.doctors,key)==value),None)
        require(key is not None,'اختر وجهة التقرير');return key



def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env',type=Path,default=BASE/'.env',help='Optional literal settings file; defaults beside script')
    args=parser.parse_args()
    lock=None
    try:
        if os.name!='nt':os.umask(0o077)
        lock=acquire_instance_lock()
        settings=read_settings(args.env)
        ctk.configure_rendering(settings.get('TEXT_RENDERING') or 'auto')
        ctk.set_appearance_mode(settings.get('APPEARANCE') or 'System')
        ctk.set_default_color_theme('blue')
        root=ctk.CTk()
        Window(root,settings)
        root.mainloop()
    except BridgeError as error:
        messagebox.showerror('ShiftReport',str(error));return 1
    finally:
        if lock:
            lock()
    return 0


if __name__=='__main__':raise SystemExit(main())
