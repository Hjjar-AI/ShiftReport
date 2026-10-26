#!/usr/bin/env python3
"""Standalone ShiftReport desktop client; Windows 7 SP1 / Linux; Python 3.8-compatible source."""
from __future__ import annotations
import argparse
import os
import sys
from pathlib import Path
import threading
import time
from concurrent.futures import ThreadPoolExecutor
import tkinter as tk
from tkinter import filedialog
from desktop_widgets import messagebox, simpledialog
import desktop_widgets as ctk
from desktop_protocol import BridgeError, MissingProjectIndex, Client, ZoneInfo, current_date, fingerprint, is_admin, open_join, require
from desktop_model import doctor_label
from desktop_settings import raw_config
from desktop_connection_files import discover_connections
from desktop_store import Workspace
from desktop_platform import data_directory, acquire_instance_lock
from desktop_forms import dialog_actions, button, modal
from desktop_sort import ordered
from desktop_access import LocalAccess, lock_screen
from desktop_workflow import WorkflowAction, workflow_status
from desktop_appearance import apply as apply_appearance, preferences as appearance_preferences

BASE = Path(sys.executable).resolve().parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent


import desktop_patient_actions
import desktop_patient_list
import desktop_data_actions
import desktop_report_actions
import desktop_shell_layout

class Window:
    def __init__(self, root, settings, demo=False, connection_candidates=None, startup_notice=''):
        self.root, self.settings = root, settings
        self.connection_candidates = list(connection_candidates or [])
        self.client = self.store = self.plan = self.raw_draft = None
        self.executor = ThreadPoolExecutor(max_workers=1)
        self.busy = False
        self.closed=False;self.demo=demo;self.locked=False;self.access=None;self.local_actor=None
        self.last_activity=time.monotonic();self.io_depth=0
        self.clock_after_id = None
        self.recycle_cleanup_store = None
        self.recycle_cleanup_due = 0
        self.rendered_day = None
        self.actor_ids, self.destinations = [], [None]
        self.project_creator = None
        desktop_shell_layout.build_window(self, root, settings, demo)
        if startup_notice:self.status.set(startup_notice)

    def tool_window(self,title):
        window=ctk.CTkToplevel(self.root)
        window.title(title)
        window.geometry('{}x{}'.format(min(640,self.root.winfo_screenwidth()-60),min(680,self.root.winfo_screenheight()-100)))
        window.minsize(480,430);window.transient(self.root)
        window.protocol('WM_DELETE_WINDOW',window.withdraw)
        area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True,padx=8,pady=8)
        window.withdraw()
        return window,area

    def open_tool_window(self,window):
        if self.locked or self.busy:return
        window.deiconify();window.lift();window.focus_set()

    def open_project_tools(self):
        if self.locked or self.busy:return
        self.project_window.deiconify();self.project_window.lift();self.project_window.focus_set()

    def open_diagnostics(self):
        if self.locked or self.busy:return
        import platform
        draft=self.store.read() if self.store and self.store.state.get('draft') else None
        text='النظام: {}\nPython: {}\nالوضع: {}\nالمشروع متصل: {}\nسجلات المناوبة المحلية: {}'.format(
            platform.system(),platform.python_version(),'تجريبي' if self.demo else 'فعلي',
            'نعم' if self.client else 'لا',len(draft['patients']) if draft else 0)
        self.diagnostics_text.configure(text=text)
        self.diagnostics_window.deiconify();self.diagnostics_window.lift();self.diagnostics_window.focus_set()

    def resize_data_tools(self,event):
        if event.widget is self.data_window:
            width=max(240,event.width-48)
            self.data_description.configure(wraplength=width)
            self.data_status_label.configure(wraplength=width)

    def open_data_tools(self):
        if self.locked or self.busy:return
        self.data_window.deiconify()
        self.data_window.lift()
        self.data_window.focus_set()

    def notify_success(self,message):
        self.status.set(message)
        def clear():
            if not self.closed and self.status.get()==message:
                self.status.set('DEMO — بيانات في الذاكرة' if self.demo else 'التحرير محلي؛ راجع البيانات قبل النشر أو إرسال التقرير.')
        self.root.after(5_000,clear)

    def open_settings(self):
        if self.locked or self.busy:return
        self.settings_window.deiconify()
        self.settings_window.lift()
        self.settings_window.focus_set()

    def cleanup_recycle_bin(self):
        if self.demo or self.store is None:return
        if self.recycle_cleanup_store is not self.store:
            self.recycle_cleanup_store=self.store
            self.recycle_cleanup_due=0
        if time.monotonic()<self.recycle_cleanup_due:return
        self.busy=True
        try:
            self.io(self.store.purge_recycle_bin)
            self.recycle_cleanup_due=time.monotonic()+24*60*60
        except Exception:
            # A failed sweep preserves the encrypted workspace; retry when unlocked and idle.
            self.recycle_cleanup_due=time.monotonic()+60*60
            self.status.set('تعذّر تنظيف سلة المحذوفات؛ ستُعاد المحاولة لاحقاً.')
        finally:self.busy=False

    def clock_tick(self):
        try:
            if not self.closed and not self.locked and not self.busy and not self.io_depth:
                self.cleanup_recycle_bin()
                day=(self.client.project_id,current_date(self.client.zone)) if self.client else None
                if day!=self.rendered_day:self.render()
        finally:
            if not self.closed:self.clock_after_id=self.root.after(60_000,self.clock_tick)

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
        if getattr(self, "_last_content_wrap_width", None) == width:return
        self._last_content_wrap_width = width
        self.operation_notice_text.configure(wraplength=width)
        self.workflow.configure(wraplength=max(150,width-260))
        self.workflow_details.configure(wraplength=width)
        self.metrics.configure(wraplength=width)
        self.sort_status.configure(wraplength=width)
        self.view_summary.configure(wraplength=width)
        self.view_help.configure(wraplength=max(200,width-36))
        self.status_label.configure(wraplength=max(300,self.root.winfo_width()-40))
        for frame in self.patients.winfo_children():
            for child in frame.winfo_children():
                if isinstance(child,ctk.CTkLabel):child.configure(wraplength=width)

    def close(self):
        if self.busy:
            self.status.set('العملية جارية؛ انتظر اكتمالها قبل الإغلاق.'); return
        if self.clock_after_id is not None:self.root.after_cancel(self.clock_after_id)
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
        return draft

    def save_draft(self,draft,expected):
        self.editable()
        ordered(draft,self.client.doctors,self.client.zone)
        self.io(lambda:self.store.save_clinical_draft(self.client,draft,expected))
        self.plan=None; self.raw_draft=None
        self.notify_success('DEMO — حُفظ التعديل في الذاكرة فقط.' if self.demo else 'تم الحفظ محلياً؛ التعديلات تحتاج إلى مراجعة ونشر.')
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
        if self.client is None and self.connection_candidates:
            candidates = self.connection_candidates
            choice = 0 if len(candidates) == 1 else self.choose(
                'عُثر على أكثر من ملف اتصال؛ اختر المشروع',
                [('ملف انضمام محمي: ' if kind == 'join' else 'إعدادات اتصال: ') + path.name
                 for kind, path, values in candidates] + ['اختيار ملف انضمام آخر…'])
            if choice is None:return
            if choice < len(candidates):
                kind, path, values = candidates[choice]
                if kind == 'join':
                    self.settings = dict(self.settings, JOIN_FILE=str(path), CONNECTION_MODE='join')
                else:
                    self.settings = dict(self.settings, **values)
                    self.zone.set(self.settings.get('TIMEZONE') or 'Asia/Damascus')
            self.connection_candidates = []
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
        apply_appearance(self,appearance_preferences(self))
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
                self.store.local_actor=None;self.local_actor=actor
                self.status.set('هوية مؤكدة: '+doctor['name']+' — اجلب أو راجع المسودة المحلية.');return
            messagebox.showerror('رمز التحقق','الرمز غير صحيح')

    def choose(self,title,values,notice=None):
        from desktop_input import ArabicEntry
        from desktop_patient_filters import normalize_search
        window=modal(self,title,'760x500')
        if notice:ctk.CTkLabel(window,text=notice,anchor='e',justify='right',wraplength=680).pack(fill='x',padx=10,pady=6)
        controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
        query=tk.StringVar()
        ArabicEntry(window,textvariable=query,justify='right',height=48,placeholder_text='بحث في الخيارات').pack(fill='x',padx=10,pady=6)
        area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True,padx=8,pady=8)
        result=[]
        def commit(index):result.append(index);window.destroy()
        def render(*args):
            for child in area.winfo_children():child.destroy()
            found=False
            for index,value in enumerate(values):
                if normalize_search(query.get()) not in normalize_search(str(value)):continue
                found=True;button(area,str(value),lambda i=index:commit(i))
            if not found:ctk.CTkLabel(area,text='لا توجد نتائج مطابقة',anchor='e').pack(fill='x',padx=8,pady=8)
        query.trace_add('write',render)
        dialog_actions(controls,cancel=('رجوع',window.destroy))
        render();self.root.wait_window(window)
        return result[0] if result else None

    def download(self):
        return desktop_data_actions.download(self)

    def undo_download(self):
        return desktop_data_actions.undo_download(self)

    def render(self):
        desktop_patient_list.render(self)
        self.rendered_day=(self.client.project_id,current_date(self.client.zone)) if self.client else None

    def update_patient_list(self,display,movement_allowed,draft):
        return desktop_patient_list.update_patient_list(self, display, movement_allowed, draft)

    def reset_patient_filters(self):
        return desktop_patient_list.reset_patient_filters(self)

    def patient_display(self,draft):
        return desktop_patient_list.patient_display(self, draft)

    def toggle_card(self,key,pin=False):
        return desktop_patient_list.toggle_card(self, key, pin)

    def expand_cards(self,expand):
        return desktop_patient_list.expand_cards(self, expand)

    def manual_movement_allowed(self):
        return desktop_patient_list.manual_movement_allowed(self)

    def workflow_next(self):
        if self.client is None:return self.connect()
        if self.client._actor is None or time.time()>=self.client._expires:return self.login()
        self.ready(False)
        _,action=workflow_status(self.client,self.store,self.demo)
        if action is WorkflowAction.DELIVERY_HISTORY:self.resolve()
        elif action is WorkflowAction.REVIEW_PUBLICATION:self.report()
        else:self.download()

    def details(self,row):
        return desktop_patient_actions.details(self, row)

    def add_patient(self):
        return desktop_patient_actions.add_patient(self)

    def edit(self,row):
        return desktop_patient_actions.edit(self, row)

    def delete_patient(self,key):
        return desktop_patient_actions.delete_patient(self, key)

    def recycle_bin(self):
        return desktop_patient_actions.recycle_bin(self)

    def move(self,index,delta):
        return desktop_patient_actions.move(self, index, delta)

    def sort_patients(self,key):
        return desktop_patient_actions.sort_patients(self, key)

    def roster(self):
        return desktop_patient_actions.roster(self)

    def export_draft(self):
        return desktop_data_actions.export_draft(self)

    def import_draft(self):
        return desktop_data_actions.import_draft(self)

    def text_dialog(self,title,text,approve=False,approval_label='راجعت النسخة المعروضة وأوافق على النشر',publication_plan=None):
        return desktop_data_actions.text_dialog(self, title, text, approve, approval_label, publication_plan)

    def show_review(self,text,publication_plan=None):
        return desktop_data_actions.show_review(self, text, publication_plan)

    def review(self):
        return desktop_data_actions.review(self)

    def publish(self, notify=True):
        return desktop_data_actions.publish(self, notify)

    def export_assignments(self):
        return desktop_report_actions.export_assignments(self)

    def show_report_attempt(self):
        return desktop_report_actions.show_report_attempt(self)

    def report_batch(self):
        return desktop_report_actions.report_batch(self)

    def report(self,export_only=False,separate=False):
        return desktop_report_actions.report(self, export_only, separate)

    def _report(self,export_only=False,separate=False):
        return desktop_report_actions._report(self, export_only, separate)

    def resolve(self):
        return desktop_data_actions.resolve(self)

    def selected_supervisor(self):
        return desktop_report_actions.selected_supervisor(self)



def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env',type=Path,help='Explicit literal settings file; otherwise discover beside script or bundled executable')
    parser.add_argument('--demo',action='store_true',help='Disposable offline demo; no workspace or Telegram connection')
    args=parser.parse_args()
    lock=None
    try:
        if os.name!='nt':os.umask(0o077)
        if not args.demo:lock=acquire_instance_lock()
        settings,candidates,notice = ({}, [], '') if args.demo else discover_connections(BASE,args.env)
        ctk.configure_rendering(settings.get('TEXT_RENDERING') or 'auto')
        ctk.set_appearance_mode(settings.get('APPEARANCE') or 'Light')
        from desktop_appearance import set_palette_theme
        set_palette_theme()
        root=ctk.CTk()
        Window(root,settings,demo=args.demo,connection_candidates=candidates,startup_notice=notice)
        root.mainloop()
    except BridgeError as error:
        messagebox.showerror('ShiftReport',str(error));return 1
    finally:
        if lock:
            lock()
    return 0


if __name__=='__main__':raise SystemExit(main())
