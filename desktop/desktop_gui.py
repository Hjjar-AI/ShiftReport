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
from desktop_protocol import (BridgeError, MissingProjectIndex, Client, ZoneInfo, current_date, doctor_pointer,
                              fingerprint, is_admin, open_join, packed, pointer, require)
from desktop_model import (LABELS, DIAGNOSIS, carry_forward, doctor_label, new_patient, task_counts, patient_text, shift_text)
from desktop_settings import read_settings, raw_config
from desktop_store import Workspace
from desktop_platform import data_directory, open_pdf, acquire_instance_lock
from desktop_forms import button, combo, edit_patient, modal
from desktop_input import ArabicEntry
from desktop_onboarding import create_project, export_project_join
from desktop_admin_ui import administer
from desktop_reports import (options_dialog, report_text, export_supervisor_pdfs,
                             prepare_delivery, publication_review, report_content, report_destination, ReportAttempt)
from desktop_sort import GROUPS, groups, ordered, sorting_dialog, sort_description, manual_move_allowed
from desktop_csv import csv_tools
from desktop_access import LocalAccess, configure_pin, lock_screen
from desktop_workflow import workflow_status, task_board

BASE = Path(sys.executable).resolve().parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent


class Window:
    def __init__(self, root, settings, demo=False):
        self.root, self.settings = root, settings
        self.client = self.store = self.plan = self.raw_draft = None
        self.executor = ThreadPoolExecutor(max_workers=1)
        self.busy = False
        self.closed=False;self.demo=demo;self.locked=False;self.access=None;self.local_actor=None
        self.last_activity=time.monotonic();self.io_depth=0
        self.actor_ids, self.destinations = [], [None]
        self.project_creator = None
        root.title('ShiftReport — Telegram')
        root.geometry('{}x{}'.format(min(1150,max(760,root.winfo_screenwidth()-50)),min(760,max(500,root.winfo_screenheight()-100))))
        root.minsize(760,500)
        root.report_callback_exception = lambda *args: messagebox.showerror('ShiftReport','تعذر إتمام العملية المحلية. إذا بدأ إرسال فراجع سجل العمليات قبل الإعادة.')
        self.report_attempt=None
        self.report_summary=tk.StringVar(value='')
        self.status = tk.StringVar(value='ابدأ الاتصال بالمشروع. تحفظ المسودات محليا بصورة مشفرة.')
        self.zone = tk.StringVar(value=settings.get('TIMEZONE') or 'Asia/Damascus')
        ctk.CTkLabel(root,text='تقرير المناوبة — تليجرام',font=('Arial',23,'bold'),anchor='e').pack(fill='x',padx=14,pady=8)
        self.status_label = ctk.CTkLabel(root,textvariable=self.status,anchor='e',justify='right',wraplength=1000)
        self.status_label.pack(side='bottom',fill='x',padx=14,pady=8)
        body = ctk.CTkFrame(root); body.pack(fill='both',expand=True,padx=8,pady=4)
        sidebar = ctk.CTkScrollableFrame(body,width=295); sidebar.pack(side='right',fill='y',padx=5,pady=5)
        self.content = ctk.CTkFrame(body); self.content.pack(side='left',fill='both',expand=True,padx=5,pady=5)
        def section(title):
            frame=ctk.CTkFrame(sidebar)
            frame.pack(fill='x',padx=3,pady=3)
            ctk.CTkLabel(frame,text=title,font=('Arial',15,'bold'),anchor='e',justify='right',wraplength=265).pack(fill='x',padx=8,pady=3)
            return frame
        project=section('المشروع والهوية')
        if demo:
            button(project,'إعادة ضبط البيانات التجريبية',lambda:self.run(self.reset_demo))
        else:
            button(project,'الاتصال بالمشروع / إعادة الاتصال',lambda:self.run(self.connect))
            button(project,'فتح وضع تجريبي مستقل',self.open_demo)
            button(project,'إعداد PIN والقفل التلقائي',lambda:self.run(lambda:configure_pin(self)))
            button(project,'قفل الشاشة',lambda:self.run(lambda:lock_screen(self)))
        self.creation_button = None
        if settings.get('CONNECTION_MODE') == 'raw':
            self.creation_button = button(project,'إنشاء مشروع جديد',lambda:self.run(lambda:create_project(self)))
        self.join_export_button = ctk.CTkButton(project,text='تصدير ملف انضمام محمي',height=48,
            command=lambda:self.run(lambda:export_project_join(self)))
        self.admin_button = ctk.CTkButton(project,text='إدارة الأطباء والفرق والصلاحيات',height=48,
            command=lambda:self.run(lambda:administer(self)))
        ctk.CTkLabel(project,text='المنطقة الزمنية نفسها في Android',anchor='e').pack(fill='x',padx=10)
        ctk.CTkEntry(project,textvariable=self.zone,justify='right').pack(fill='x',padx=8,pady=4)
        self.actor = ctk.CTkComboBox(project,values=['اختر الطبيب'],state='readonly',justify='right'); self.actor.pack(fill='x',padx=8,pady=4)
        if not demo:
            button(project,'تحديث سجل الأطباء',lambda:self.run(self.refresh_identity))
            button(project,'إثبات الهوية برمز تليجرام',lambda:self.run(self.login))
        data=section('١ · البيانات: جلب ← تعديل وحفظ محلي ← مراجعة ونشر')
        ctk.CTkLabel(data,text='يحفظ تحرير المريض محلياً. النشر يستبدل النسخة الكاملة على تليجرام.',wraplength=265,justify='right').pack(fill='x',padx=8)
        if not demo:
            for label,action in [('جلب نسخة كاملة / اختيار مناوبة',self.download),
                                 ('بدء مناوبة اليوم من السابقة',self.start_shift),
                                 ('مراجعة النسخة الكاملة',self.review),('نشر النسخة المراجعة',self.publish)]:
                button(data,label,lambda f=action:self.run(f))
        button(data,'أطباء المناوبة',lambda:self.run(self.roster))
        button(data,'سلة المرضى المحذوفين',lambda:self.run(self.recycle_bin))
        reports=section('٢ · التقارير: حفظ محلي أو إرسال منشور')
        ctk.CTkLabel(reports,text='الحفظ المحلي يشمل التعديلات غير المنشورة. الإرسال يراجع البيانات وينشرها عند الحاجة قبل تسليم التقرير.',wraplength=265,justify='right').pack(fill='x',padx=8)
        ctk.CTkLabel(reports,textvariable=self.report_summary,wraplength=265,justify='right',anchor='e').pack(fill='x',padx=8,pady=3)
        self.supervisor = ctk.CTkComboBox(reports,values=['التقرير العام'],state='readonly',justify='right'); self.supervisor.pack(fill='x',padx=8,pady=6)
        button(reports,'حفظ تقرير المسودة محلياً',lambda:self.run(lambda:self.report(export_only=True)))
        if not demo:button(reports,'إرسال تقرير — معاينة وتأكيد',lambda:self.run(self.report))
        self.assignments_button = ctk.CTkButton(reports,text='حفظ توزيع الفرق بالأسماء فقط (PDF)',height=48,
            command=lambda:self.run(self.export_assignments))
        if not demo:
            files=section('الملفات والاسترداد')
            button(files,'أدوات CSV',lambda:self.run(lambda:csv_tools(self)))
            button(files,'حفظ نسخة مسودة مشفرة',lambda:self.run(self.export_draft))
            button(files,'استعادة مسودة مشفرة',lambda:self.run(self.import_draft))
            button(files,'سجل العمليات والتسليم',lambda:self.run(self.resolve))
        tools = ctk.CTkFrame(self.content); tools.pack(fill='x',padx=5,pady=4)
        self.search = tk.StringVar()
        ctk.CTkLabel(tools,text='بحث',anchor='e').pack(side='right',padx=5)
        ArabicEntry(tools,textvariable=self.search,justify='right').pack(side='right',fill='x',expand=True,padx=5,pady=5)
        self.search.trace_add('write',lambda *args:self.render())
        self.filter = ctk.CTkComboBox(tools,values=['الكل','أولوية','مهام معلقة','مهام متأخرة'],state='readonly',width=140,command=lambda value:self.render())
        self.filter.pack(side='left',padx=5)
        actions = ctk.CTkFrame(self.content); actions.pack(fill='x',padx=5,pady=4)
        for label,action in [('إضافة مريض',self.add_patient),('مهام الجناح',lambda:task_board(self)),('ترتيب متعدد المستويات',lambda:sorting_dialog(self))]:
            ctk.CTkButton(actions,text=label,height=48,width=130,command=lambda f=action:self.run(f)).pack(side='right',padx=3,pady=5)
        self.grouping=ctk.CTkComboBox(self.content,values=list(GROUPS.values()),state='readonly',justify='right',height=48,command=lambda value:self.render())
        self.grouping.pack(fill='x',padx=8,pady=3)
        self.sort_status=ctk.CTkLabel(self.content,text='',anchor='e',justify='right',wraplength=650)
        self.sort_status.pack(fill='x',padx=12,pady=3)
        self.workflow = ctk.CTkLabel(self.content,text='',anchor='e',justify='right',wraplength=650)
        self.workflow.pack(fill='x',padx=12,pady=3)
        self.workflow_action = ctk.CTkButton(self.content,text='جلب البيانات',height=48,command=lambda:self.run(self.workflow_next))
        self.workflow_action.pack(anchor='e',padx=8)
        self.metrics = ctk.CTkLabel(self.content,text='',anchor='e',justify='right',wraplength=650); self.metrics.pack(fill='x',padx=12,pady=6)
        self.patients = ctk.CTkScrollableFrame(self.content); self.patients.pack(fill='both',expand=True,padx=5,pady=4)
        self.content.bind('<Configure>',self.resize_content)
        root.protocol('WM_DELETE_WINDOW',self.close)
        if demo:
            from desktop_demo import DemoClient, DemoWorkspace
            self.client=DemoClient();self.store=DemoWorkspace(self.client)
            self.zone.set(self.client.zone);self.set_doctors()
            self.status.set('DEMO — وضع تجريبي في الذاكرة. الإغلاق يحذف التجربة. يسمح بحفظ PDF تجريبي محلي فقط.')
            root.title('ShiftReport — DEMO');self.render()
        else:
            root.after(200,lambda:self.run(self.connect))
            root.bind_all('<KeyPress>',self.note_activity,add='+')
            root.bind_all('<ButtonPress>',self.note_activity,add='+')
            root.bind_all('<Motion>',self.note_activity,add='+')
            root.after(1000,self.check_lock)

    def open_demo(self):
        if self.locked:return
        window=ctk.CTkToplevel(self.root)
        Window(window,{},demo=True)

    def reset_demo(self):
        require(self.demo,'هذا الإجراء للوضع التجريبي فقط')
        self.store.reset(self.client);self.plan=None;self.raw_draft=None

    def note_activity(self,event=None):
        if not self.locked:self.last_activity=time.monotonic()

    def check_lock(self):
        try:
            if self.access and self.access.records() and not self.locked and not self.io_depth and time.monotonic()-self.last_activity>=self.access.timeout():
                lock_screen(self)
        finally:
            if not self.closed:self.root.after(1000,self.check_lock)

    def resize_content(self,event):
        width=max(240,event.width-40)
        self.workflow.configure(wraplength=width)
        self.metrics.configure(wraplength=width)
        self.sort_status.configure(wraplength=width)
        self.status_label.configure(wraplength=max(300,self.root.winfo_width()-40))
        for frame in self.patients.winfo_children():
            for child in frame.winfo_children():
                if isinstance(child,ctk.CTkLabel):child.configure(wraplength=width)

    def close(self):
        if self.busy:
            self.status.set('العملية جارية؛ انتظر اكتمالها قبل الإغلاق.'); return
        self.executor.shutdown(wait=False)
        if self.client and not self.demo: self.client.telegram._http.close()
        if self.project_creator and self.project_creator.client is not self.client:
            self.project_creator.client.telegram._http.close()
        self.closed=True
        self.root.destroy()

    def run(self,action):
        if self.busy or self.locked: return
        self.busy=True; self.root.configure(cursor='watch'); self.root.update_idletasks()
        try:
            action()
        except BridgeError as error:
            if not self.closed:messagebox.showerror('ShiftReport',str(error))
        except Exception:
            if not self.closed:messagebox.showerror('ShiftReport','تعذر إتمام العملية. تحقّق من الملفات والاتصال. إذا بدأ إرسال فراجع سجل العمليات قبل الإعادة.')
        finally:
            self.busy=False
            if not self.closed:
                self.root.configure(cursor=''); self.render()

    def io(self,action):
        if threading.current_thread() is not threading.main_thread(): return action()
        self.io_depth+=1
        try:
            future=self.executor.submit(action)
            while not future.done():
                self.root.update(); time.sleep(.02)
            return future.result()
        finally:self.io_depth-=1

    def ready(self, online=True):
        require(not self.locked,'الجلسة مقفلة')
        require(not (self.demo and online),'الوضع التجريبي لا يتصل بتليجرام')
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
        ordered(draft,self.client.doctors,self.client.zone)
        self.io(lambda:self.store.save_draft(draft))
        self.plan=None; self.raw_draft=None
        self.status.set('DEMO — حُفظ التعديل في الذاكرة فقط.' if self.demo else 'حفظت المسودة المشفرة محليا؛ راجعها قبل النشر.')
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
        require(not self.demo,'الوضع التجريبي معزول عن المشاريع الحقيقية')
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
            except MissingProjectIndex as error:
                candidate.telegram._http.close()
                self.status.set(str(error));return
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
        live_doctors=candidate.doctors
        workspace=self.open_workspace(candidate)
        if workspace is None:return
        if self.client:self.client.telegram._http.close()
        self.client,self.store=candidate,workspace
        self.plan=None; self.raw_draft=None
        if raw:
            self.client.doctors=live_doctors
        self.set_doctors()
        self.access=LocalAccess(self.store,self.client)
        if self.access.records():lock_screen(self)
        self.status.set('المشروع: '+config['hospitalName']+' — المسودة المحلية متاحة. حدّث الأطباء وأثبت الهوية قبل النشر.')
        if not self.client.doctors:
            self.refresh_identity()

    def open_workspace(self, candidate):
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
        try:
            workspace=self.io(lambda:Workspace(directory,candidate,local_passphrase))
        except Exception:
            candidate.telegram._http.close();raise
        finally:
            local_passphrase=None
        return workspace

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
        previous=self.client.baseline
        self.client.baseline=snapshot
        try:
            self.io(lambda:self.store.write(self.client,selected,downloaded=True))
        except Exception:
            self.client.baseline=previous; raise
        self.client.doctors=snapshot['doctors']; self.set_doctors(); self.plan=None
        self.status.set('جلبت '+selected['meta']['shiftDate']+' — تحفظ التعديلات محليا حتى مراجعتها ونشرها.')

    def start_shift(self):
        self.ready()
        snapshot=self.io(self.client.fetch)
        self.io(lambda:self.client.authorize(snapshot['doctors']))
        today=current_date(self.client.zone)
        require(not any(s['meta']['shiftDate']==today and s['patients'] for s in snapshot['bundle']),
                'مناوبة اليوم المنشورة تحتوي مرضى؛ اجلبها بدلاً من نسخ السابقة')
        local=self.store.state['draft']
        require(local is None or local['meta']['shiftDate']!=today or not local['patients'],
                'مناوبة اليوم المحلية تحتوي مرضى؛ لن تُستبدل بنسخ السابقة')
        previous=[s for s in snapshot['bundle'] if s['meta']['shiftDate']<today]
        require(previous,'لا توجد مناوبة سابقة لنسخها')
        source=max(previous,key=lambda s:s['meta']['shiftDate'])
        checked_at=datetime.now(ZoneInfo(self.client.zone)).strftime('%Y-%m-%d %H:%M %Z')
        source_info='المصدر: مناوبة '+source['meta']['shiftDate']+' من حزمة تليجرام.\nتم جلب الحزمة لهذه العملية في '+checked_at+'\nعدد المرضى: '+str(len(source['patients']))
        require(source['patients'],'المناوبة السابقة فارغة؛ لا يوجد مرضى لنسخهم')
        keep=self.choose('بدء مناوبة جديدة — نسخ جميع المرضى والمهام غير المكتملة من '+source['meta']['shiftDate'],
                         ['نسخ الكل وإزالة مسؤوليات الأطباء ومالكي المهام','نسخ الكل والاحتفاظ بالمسؤوليات'])
        if keep is None:return
        if not messagebox.askyesno('تأكيد بدء مناوبة جديدة',
                source_info+'\nستُنشأ سجلات جديدة لجميع مرضى المناوبة السابقة مع المهام غير المكتملة فقط.\nهذا ليس جلباً لبيانات اليوم. تحفظ محلياً ثم تراجع وتنشر.\nهل تتابع؟',default='no'):return
        selected=carry_forward(source,self.client.zone,bool(keep))
        existing=next((s for s in snapshot['bundle'] if s['meta']['shiftDate']==today),None)
        if existing:selected['meta']=copy.deepcopy(existing['meta'])
        elif local and local['meta']['shiftDate']==today:selected['meta']=copy.deepcopy(local['meta'])
        selected['meta']['sortSpec']='{"levels":[]}'
        ordered(selected,snapshot['doctors'],self.client.zone)
        latest=self.io(self.client.fetch)
        self.io(lambda:self.client.authorize(latest['doctors']))
        require(pointer(latest['state'])==pointer(snapshot['state']) and
                doctor_pointer(latest['state'])==doctor_pointer(snapshot['state']),
                'وصلت نسخة جديدة أثناء التأكيد؛ أعد بدء المناوبة')
        old=self.client.baseline;self.client.baseline=snapshot
        try:self.io(lambda:self.store.write(self.client,selected))
        except Exception:
            self.client.baseline=old;raise
        self.client.doctors=snapshot['doctors'];self.set_doctors();self.plan=None;self.raw_draft=None
        self.status.set('نُسخت المناوبة كاملة محلياً؛ حدد أطباء المناوبة ثم راجع وانشر. المهام المكتملة بقيت في السابقة.')

    def render(self):
        if not hasattr(self,'patients') or self.locked:return
        if self.creation_button is not None:
            self.creation_button.configure(state='disabled' if self.client or self.busy else 'normal')
        actor=self.client.doctors.get(self.client._actor) if self.client else None
        if actor and is_admin(actor) and time.time()<self.client._expires:
            self.join_export_button.pack(fill='x',padx=8,pady=4)
            self.join_export_button.configure(state='disabled' if self.busy else 'normal')
            self.admin_button.pack(fill='x',padx=8,pady=4)
            self.admin_button.configure(state='disabled' if self.busy else 'normal')
            self.assignments_button.pack(fill='x',padx=8,pady=4)
            self.assignments_button.configure(state='disabled' if self.busy else 'normal')
        else:
            self.join_export_button.pack_forget()
            self.admin_button.pack_forget()
            self.assignments_button.pack_forget()
        summary,action=workflow_status(self.client,self.store,self.demo)
        self.workflow.configure(text=summary)
        self.workflow_action.configure(text=action, state='disabled' if self.busy or self.demo or self.store is None else 'normal')
        for child in self.patients.winfo_children():child.destroy()
        if self.store is None or self.store.state['draft'] is None:
            self.sort_status.configure(text='التجميع للعرض فقط؛ الترتيب المحفوظ يستخدم في التقارير وCSV.')
            self.metrics.configure(text='افتح المشروع واجلب مناوبة، أو ابدأ مناوبة اليوم من السابقة.');return
        draft=self.store.read(); rows=draft['patients']; pending,overdue=task_counts(rows)
        self.sort_status.configure(text=sort_description(draft)+'\nالتجميع للعرض فقط. الأسهم تمسح مستويات الترتيب وتُبقي مرضى الأولوية أولاً. تُعطل أثناء البحث أو التصفية أو التجميع.')
        roster=' / '.join(self.client.doctors.get(key,{}).get('name','طبيب محفوظ') for key in draft['meta'].get('doctors','').split(',') if key)
        self.metrics.configure(text='{} · {} مريض · مهام معلقة {} (منها متأخرة {})\nأطباء المناوبة: {}'.format(draft['meta']['shiftDate'],len(rows),pending,overdue,roster or 'لم تحدد'))
        query=self.search.get().strip().casefold(); mode=self.filter.get()
        mode_key=next(key for key,label in GROUPS.items() if label==self.grouping.get())
        movement_allowed=manual_move_allowed(self.grouping.get(),mode,self.search.get())
        display=[]
        for label,items in groups(rows,mode_key,self.client.doctors):
            for number,(index,row) in enumerate(items):display.append((label if number==0 else None,index,row))
        last_group=None
        for heading,index,row in display:
            if heading is not None:current_group=heading
            if query and query not in ' '.join(str(row.get(k,'')) for k in ['name','admitNum','initialDiagnosis','treatmentPlan','followUp','labs']).casefold():continue
            counts=task_counts([row])
            if mode=='أولوية' and row.get('isPriority')!='Y':continue
            if mode=='مهام معلقة' and not counts[0]:continue
            if mode=='مهام متأخرة' and not counts[1]:continue
            if current_group and current_group!=last_group:
                ctk.CTkLabel(self.patients,text=current_group,font=('Arial',18,'bold'),anchor='e',justify='right',wraplength=max(240,self.content.winfo_width()-40)).pack(fill='x',padx=10,pady=8)
                last_group=current_group
            frame=ctk.CTkFrame(self.patients); frame.pack(fill='x',padx=3,pady=4)
            ctk.CTkLabel(frame,text='{} — {} · قبول {}\n{} · مهام {} (متأخرة {})'.format(index+1,row['name'],row['admitNum'],DIAGNOSIS.get(row['diagnosisType'],''),*counts),font=('Arial',16),anchor='e',justify='right',wraplength=max(240,self.content.winfo_width()-40)).pack(fill='x',padx=10,pady=5)
            actions=ctk.CTkFrame(frame,fg_color='transparent'); actions.pack(fill='x')
            for label,action,width in [('عرض',lambda p=copy.deepcopy(row):self.details(p),70),('تحرير',lambda p=copy.deepcopy(row):self.edit(p),70),('حذف',lambda i=row['externalId']:self.delete_patient(i),65),('↑',lambda i=index:self.move(i,-1),40),('↓',lambda i=index:self.move(i,1),40)]:
                enabled=label not in {'↑','↓'} or movement_allowed and not (label=='↑' and index==0 or label=='↓' and index==len(rows)-1)
                ctk.CTkButton(actions,text=label,width=width,height=48,state='normal' if enabled else 'disabled',command=lambda f=action:self.run(f)).pack(side='right',padx=3,pady=4)

    def workflow_next(self):
        self.ready(False)
        _,action=workflow_status(self.client,self.store,self.demo)
        if action=='سجل التسليم':self.resolve()
        elif action=='مراجعة ونشر':self.review()
        else:self.download()

    def details(self,row):
        self.text_dialog('تفاصيل المريض',patient_text(row,self.client.doctors,self.client.zone))

    def add_patient(self):
        self.editable(); edit_patient(self,new_patient(self.client.zone),True)

    def edit(self,row):
        self.editable(); edit_patient(self,row)

    def delete_patient(self,key):
        draft=self.editable()
        if messagebox.askyesno('حذف مريض','هل تنقل المريض ومهامه وشاراته إلى السلة المحلية؟ ستراجع الحذف قبل النشر.',default='no'):
            self.io(lambda:self.store.delete_patient(key,fingerprint(draft)))
            self.plan=None;self.raw_draft=None
            self.status.set('نُقل المريض إلى السلة المشفرة على هذا الجهاز؛ راجع الحذف قبل النشر.')

    def recycle_bin(self):
        draft=self.editable()
        records=self.io(self.store.deleted_patients)
        require(records,'لا توجد سجلات محذوفة محليا لهذه المناوبة؛ الحذف على أجهزة أخرى لا يظهر في هذه السلة')
        choice=self.choose('استعادة مريض محذوف',[item['patient']['name']+' · '+item['patient']['admitNum'] for item in records])
        if choice is None:return
        row=records[choice]['patient']
        if not self.text_dialog('مراجعة الاستعادة','استعادة إلى المسودة فقط؛ يلزم مراجعة النسخة الكاملة والنشر لاحقاً.\n\n'+patient_text(row,self.client.doctors,self.client.zone),True,'استعادة إلى المسودة'):return
        self.io(lambda:self.store.restore_patient(row['externalId'],fingerprint(draft)))
        self.plan=None;self.raw_draft=None
        self.status.set('استُعيد المريض وكامل مهامه وشاراته محليا؛ راجع المسؤوليات والمسودة قبل النشر.')

    def move(self,index,delta):
        require(manual_move_allowed(self.grouping.get(),self.filter.get(),self.search.get()),
                'لتحريك المرضى يدوياً اختر دون تجميع والكل وامسح البحث أولاً')
        draft=self.editable(); target=index+delta
        if 0<=target<len(draft['patients']):
            draft['patients'][index],draft['patients'][target]=draft['patients'][target],draft['patients'][index]
            draft['meta']['sortSpec']='{"levels":[]}'
            self.save_draft(draft)

    def sort_patients(self,key):
        draft=self.editable()
        if messagebox.askyesno('ترتيب محفوظ','هل تحفظ هذا الترتيب للعرض والنشر والتقارير؟',default='no'):
            ordered(draft,self.client.doctors,self.client.zone,[dict(field='NAME' if key=='name' else 'DAYS_OF_ADMITTANCE',direction='ASC')]); self.save_draft(draft)

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
        require(not self.demo,'لا يمكن استيراد بيانات حقيقية إلى العرض التجريبي')
        path=filedialog.askopenfilename(title='استعادة مسودة للمشروع نفسه',initialdir=str(self.store.directory),filetypes=[('Encrypted draft','*.srdraft')])
        if path and messagebox.askyesno('استعادة','هل تستبدل المسودة المحلية؟ سجل التسليم الحالي يبقى محفوظا.',default='no'):
            self.io(lambda:self.store.import_draft(path,self.client));self.plan=None;self.set_doctors()
            self.status.set('استعدت المسودة؛ أعد المراجعة مع تليجرام قبل النشر.')

    def text_dialog(self,title,text,approve=False,approval_label='راجعت النسخة المعروضة وأوافق على النشر'):
        window=modal(self,title,'920x680')
        controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
        widget=ctk.CTkTextbox(window,wrap='word',font=('Arial',15));widget.pack(fill='both',expand=True,padx=8,pady=8)
        copy_original=set_readonly_text(widget,text)
        button(controls,'نسخ النص الكامل',copy_original)
        accepted=[]
        def accept():accepted.append(True);window.destroy()
        if approve:button(controls,approval_label,accept)
        button(controls,'إغلاق / إلغاء',window.destroy)
        self.root.wait_window(window);return bool(accepted)

    def show_review(self,text):
        return self.text_dialog('مراجعة قبل النشر',text,approve=True)

    def review(self):
        self.ready();self.plan=None
        local=self.store.read(self.client);draft_hash=fingerprint(local)
        plan=self.io(lambda:self.client.plan(local))
        plan=self.io(lambda:self.client.prepare(plan,self.store.restored_completions()))
        require(fingerprint(self.store.read(self.client))==draft_hash,'تغير المسودة أثناء المراجعة؛ أعدها')
        if self.show_review(publication_review(plan,self.client.zone)):
            self.client.approve(plan);self.plan=plan;self.raw_draft=draft_hash
            self.status.set('المراجعة جاهزة. اضغط نشر؛ أي تعديل أو نسخة أحدث سيوقف هذا النشر.')

    def publish(self, notify=True):
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
        if notify:messagebox.showinfo('تم النشر','نشر مؤكد؛ بقيت بيانات الأطباء والإعلانات والاستعادة في الفهرس.')

    def export_assignments(self):
        self.ready()
        actor_id=self.io(self.client.require_admin)['uid']
        local=self.store.read()
        options=options_dialog(self,export_only=True,assignments=True)
        if options is None:return
        font=self.settings.get('PDF_FONT')
        if font and not Path(font).is_absolute():font=str(BASE/font)
        destination=filedialog.asksaveasfilename(title='توزيع المرضى بالأسماء فقط',defaultextension='.pdf',filetypes=[('PDF','*.pdf')])
        if not destination:return
        from desktop_pdf import render_assignments
        def save():
            path=render_assignments(self.client,local,font_path=font,options=options)
            require(self.client.require_admin()['uid']==actor_id,'تغيّر المستخدم؛ أعد تصدير توزيع المرضى')
            raw=path.read_bytes()
            Path(destination).write_bytes(raw)
            require(Path(destination).read_bytes()==raw,'تعذر التحقق من حفظ PDF كاملاً')
        self.io(save)
        self.status.set('حُفظ توزيع المرضى: A4 عمودي، ثلاثة جداول في كل صف وترقيم متصل.')

    def show_report_attempt(self):
        if self.report_attempt:self.report_summary.set(self.report_attempt.summary())

    def report(self,export_only=False):
        if export_only:return self._report(export_only=True)
        self.report_attempt=ReportAttempt();self.show_report_attempt()
        try:
            return self._report()
        except Exception as error:
            self.report_attempt.failed(self.client.baseline if self.client else None)
            self.show_report_attempt()
            explanation=self.report_attempt.summary()+'\n'+self.report_attempt.next_action()
            self.status.set(explanation)
            detail=str(error) if isinstance(error,BridgeError) else 'تعذر إتمام العملية المحلية أو الاتصال.'
            raise BridgeError(explanation+'\n'+detail) from None
        finally:self.show_report_attempt()

    def _report(self,export_only=False):
        self.ready(not export_only);local=self.store.read(self.client)
        options=options_dialog(self,export_only)
        if options is None:return
        if self.demo:require(export_only and options['format']=='pdf','في الوضع التجريبي يسمح بحفظ PDF محلي فقط')
        supervisor=self.selected_supervisor()
        font=self.settings.get('PDF_FONT')
        if font and not Path(font).is_absolute():font=str(BASE/font)
        from desktop_pdf import render_report
        if export_only:
            if options['format']=='pdf' and options['separate']:
                export_supervisor_pdfs(self,local,supervisor,font,options)
                return
            if options['format']=='text':
                raw=report_text(self.client,local,supervisor).encode('utf-8')
                extension='.txt'
            else:
                path=self.io(lambda:render_report(self.client,local,supervisor,font,options))
                raw=self.io(path.read_bytes);extension='.pdf'
            destination=filedialog.asksaveasfilename(title='حفظ تقرير المسودة المحلية — قد يتضمن تعديلات غير منشورة',defaultextension=extension,filetypes=[('Report','*'+extension)])
            if destination:self.io(lambda:Path(destination).write_bytes(raw))
            return
        draft_hash=fingerprint(local)
        plan,baseline,needs_publish=self.io(lambda:prepare_delivery(self.client,local,self.store.restored_completions()))
        require(fingerprint(self.store.read())==draft_hash,'تغيرت المسودة؛ أعد مراجعة التقرير')
        snapshot=plan.remote
        chat,topic=report_destination(self.client,snapshot['doctors'],supervisor)
        self.io(lambda:self.store.check_report_delivery(local['meta']['shiftId'],chat,topic))
        self.report_attempt.publication='NOT_STARTED' if needs_publish else 'CONFIRMED'
        self.show_report_attempt()
        self.client.doctors=snapshot['doctors']
        destination='موضوع التقارير العام' if supervisor is None else doctor_label(snapshot['doctors'],supervisor)
        if needs_publish and not self.text_dialog('مراجعة البيانات قبل النشر والإرسال',
                publication_review(plan,self.client.zone)+'\n\nلم تُنشر البيانات بعد. ستعاين التقرير ثم تؤكد النشر والإرسال.',
                True,'متابعة إلى معاينة التقرير'):return
        text=path=raw=None
        if options['format']=='text':
            text=report_text(self.client,baseline,supervisor)
            if not self.text_dialog('معاينة التقرير','الوجهة: '+destination+'\n\n'+text,True,'متابعة إلى التأكيد'):return
            confirmation='هل تؤكد إرسال النص الذي راجعته؟'
            if self.client.config.get('telegramDataKey'):confirmation+='\nسيُرسل كملف مشفر بمفتاح المشروع.'
        else:
            path=self.io(lambda:render_report(self.client,baseline,supervisor,font,options))
            raw=self.io(path.read_bytes)
            self.io(lambda:open_pdf(path))
            confirmation='افحص PDF الذي فتح. هل توافق على إرسال هذا الملف؟'
        if needs_publish:confirmation+='\nستُنشر النسخة الكاملة التي راجعتها أولاً، ثم يُرسل التقرير. إذا فشل الإرسال تبقى البيانات منشورة.'
        if not messagebox.askyesno('تأكيد النشر وإرسال التقرير' if needs_publish else 'تأكيد إرسال التقرير',confirmation+'\nالوجهة: '+destination+'\nالتاريخ: '+baseline['meta']['shiftDate'],default='no'):return
        if path:require(self.io(path.read_bytes)==raw,'تغير PDF بعد المعاينة')
        require(fingerprint(self.store.read(self.client))==fingerprint(local),'تغيرت المسودة بعد المعاينة')
        if needs_publish:
            self.report_attempt.publication='IN_PROGRESS';self.report_attempt.snapshot=plan.shift['meta']['snapshotId']
            self.show_report_attempt()
            self.client.approve(plan);self.plan=plan;self.raw_draft=draft_hash
            self.publish(notify=False)
            self.report_attempt.publication='CONFIRMED';self.show_report_attempt()
            snapshot=self.client.baseline
            baseline=self.store.read()
            require(fingerprint(report_content(baseline))==fingerprint(report_content(plan.shift)),
                    'تغيرت البيانات المنشورة؛ أعد معاينة التقرير')
            local=baseline
        # Reusing an unchanged publication preserves report keys and UNKNOWN retry blocking.
        # Keep PDF receipt keys compatible with existing workspaces.
        identity={'operation':'report','project':self.client.project_id,'pointer':pointer(snapshot['state']),'shift':baseline['meta']['shiftId'],'supervisor':supervisor}
        if options['format']=='text':identity['format']='text'
        key=fingerprint(identity)
        if self.store.receipts(status='UNKNOWN',key=key):
            self.report_attempt.delivery='UNCERTAIN';self.show_report_attempt()
        require(not self.store.receipts(status='UNKNOWN',key=key),'نتيجة إرسال سابقة غير مؤكدة؛ افحص سجل العمليات قبل الإعادة')
        repeat=False
        if any(row[2] in {'SENT','CHECKED_DO_NOT_REPEAT'} for _,row in self.store.receipts(key=key)):
            repeat=messagebox.askyesno('نسخة إضافية','هذا التقرير سلم سابقا إلى الوجهة نفسها. هل تؤكد إرسال نسخة إضافية بعد المراجعة؟',default='no')
            if not repeat:return
        def before(chat,topic):
            self.store.receipt_start(key,'report',chat,topic,self.client._actor,repeat=repeat,report_shift=baseline['meta']['shiftId'])
            self.report_attempt.delivery='IN_PROGRESS';self.show_report_attempt()
        if text is not None:
            result=self.io(lambda:self.client.send_text(text,baseline,supervisor,pointer(snapshot['state']),doctor_pointer(snapshot['state']),before))
        else:
            result=self.io(lambda:self.client.send_pdf(raw,baseline,supervisor,pointer(snapshot['state']),doctor_pointer(snapshot['state']),before))
        self.report_attempt.delivery='CONFIRMED';self.show_report_attempt()
        self.status.set('تليجرام أكد تسليم التقرير. لا تعاود الإرسال إذا تعذر حفظ سجل المسودة.')
        try:self.io(lambda:self.store.receipt_finish(result))
        except Exception:raise BridgeError('تم تسليم التقرير؛ تعذر حفظ إيصال المسودة. لا تعد إرسال التقرير') from None
        messagebox.showinfo('تم التسليم','تم تسليم التقرير إلى الوجهة المعروضة.')

    def resolve(self):
        self.ready(False)
        records=self.store.receipts(status='UNKNOWN')
        if not records:
            self.text_dialog('سجل العمليات','\n'.join(' | '.join(row[:8]) for _,row in self.store.receipts()) or 'لا توجد عمليات');return
        choice=self.choose('اختر عملية واحدة للمراجعة',[str(index)+' | '+row[0]+' | '+row[6]+' | '+row[4]+' | '+row[1][:12] for index,row in records])
        if choice is None:return
        index,row=records[choice]
        if row[0]=='doctors' and row[7].startswith('snapshot='):
            self.ready()
            snapshot=self.io(self.client.fetch)
            self.io(lambda:self.client.authorize(snapshot['doctors']))
            if fingerprint(snapshot['doctors'])==row[7][9:]:
                self.io(lambda:self.store.confirm_indexed_receipt(index,snapshot['state']['doctorsMessageId']))
                messagebox.showinfo('تأكيد سجل الأطباء','السجل المثبت الحالي يطابق العملية؛ لا تكررها.')
                return
        if row[0]=='publish' and row[7].startswith('snapshot='):
            self.ready()
            state=self.io(self.client.fetch)['state']
            match=next((entry for entry in state.get('publicationJournal',[]) if entry['snapshotId']==row[7][9:]),None)
            if match:
                self.io(lambda:self.store.confirm_indexed_receipt(index,match['messageId']))
                messagebox.showinfo('تأكيد النشر','سجل الفهرس المثبت يؤكد نشر هذه النسخة؛ لا تكررها.');return
        confirmation=messagebox.askyesnocancel('تحقق يدوي','الحسم للمرسل أو لمدير نشط ويتطلب التحقق من الهوية عبر تليجرام.\nافحص العملية المختارة على تليجرام أولا. في النشر تحقق من الفهرس والمرضى، وليس مجرد وجود ملف في موضوع CSV. غيابها عن السجل الحالي ليس إثبات عدم وصولها.\nنعم: تؤكد أنها لم تصل وتسمح بمحاولتها.\nلا: وصلت أو لا تريد تكرارها.\nإلغاء: إبقاء الحظر.',default='cancel')
        if confirmation is not None:self.io(lambda:self.store.resolve_unknown(records[choice][0],confirmation,self.client))

    def selected_supervisor(self):
        value=self.supervisor.get()
        if value=='التقرير العام':return None
        key=next((key for key in self.destinations[1:] if doctor_label(self.client.doctors,key)==value),None)
        require(key is not None,'اختر وجهة التقرير');return key



def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env',type=Path,default=BASE/'.env',help='Optional literal settings file; defaults beside script')
    parser.add_argument('--demo',action='store_true',help='Disposable offline demo; no workspace or Telegram connection')
    args=parser.parse_args()
    lock=None
    try:
        if os.name!='nt':os.umask(0o077)
        if not args.demo:lock=acquire_instance_lock()
        settings={} if args.demo else read_settings(args.env)
        ctk.configure_rendering(settings.get('TEXT_RENDERING') or 'auto')
        ctk.set_appearance_mode(settings.get('APPEARANCE') or 'System')
        ctk.set_default_color_theme('blue')
        root=ctk.CTk()
        Window(root,settings,demo=args.demo)
        root.mainloop()
    except BridgeError as error:
        messagebox.showerror('ShiftReport',str(error));return 1
    finally:
        if lock:
            lock()
    return 0


if __name__=='__main__':raise SystemExit(main())
