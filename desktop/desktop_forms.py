"""CustomTkinter patient/task editors. Edits remain private until Save."""
from __future__ import annotations
import copy
from datetime import datetime
import json
import tkinter as tk
from desktop_widgets import messagebox, visual_text
from desktop_input import ArabicEntry, ArabicTextbox
import uuid
import desktop_widgets as ctk
from desktop_protocol import BridgeError, ZoneInfo, packed, require
from desktop_model import (LABELS, GENDER, YES, DIAGNOSIS, TASK_PRIORITY, BADGE_PRIORITY,
                           doctor_label, badges_decode, badges_encode, validate_patient)


def button(parent, text, command, **kwargs):
    widget = ctk.CTkButton(parent, text=text, command=command, height=48, **kwargs)
    widget.pack(fill='x', padx=8, pady=4)
    return widget


def entry(parent, label, value='', multiline=False):
    ctk.CTkLabel(parent, text=label, anchor='e').pack(fill='x', padx=10, pady=(6, 0))
    if multiline:
        widget = ArabicTextbox(parent, height=115, wrap='word', font=('Arial', 15))
        widget.insert('1.0', value)
        widget.tag_config('rtl', justify='right'); widget.tag_add('rtl', '1.0', 'end')
        widget.bind('<KeyRelease>', lambda event: widget.tag_add('rtl', '1.0', 'end'))
    else:
        widget = ArabicEntry(parent, justify='right', height=48)
        widget.insert(0, value)
    if hasattr(widget, 'reset_undo'):
        widget.reset_undo()
    widget.pack(fill='x', padx=10, pady=2)
    return widget


def combo(parent, label, mapping, value):
    ctk.CTkLabel(parent, text=label, anchor='e').pack(fill='x', padx=10, pady=(6, 0))
    # Labels include stable doctor IDs so duplicate names remain distinguishable.
    mapping = dict(mapping)
    if value not in mapping:
        mapping[value] = 'قيمة محفوظة [' + str(value) + ']'
    variable = tk.StringVar(value=mapping[value])
    widget = ctk.CTkComboBox(parent, values=list(mapping.values()), variable=variable, state='readonly', justify='right', height=48)
    widget.pack(fill='x', padx=10, pady=2)
    return lambda: next(key for key, label in mapping.items() if label == variable.get())


def modal(owner, title, geometry='820x680'):
    window = ctk.CTkToplevel(owner.root)
    width,height=(int(value) for value in geometry.split('x'))
    width=min(width,max(580,owner.root.winfo_screenwidth()-60))
    height=min(height,max(430,owner.root.winfo_screenheight()-100))
    window.title(title); window.geometry('{}x{}'.format(width,height)); window.minsize(580,430)
    window.transient(owner.root); window.grab_set()
    return window


def guarded(action):
    def wrapped():
        try:
            action()
        except BridgeError as error:
            messagebox.showerror('ShiftReport', str(error))
        except (ValueError, KeyError, TypeError):
            messagebox.showerror('ShiftReport', 'تحقق من القيم والتواريخ المطلوبة')
    return wrapped


def edit_task(owner, parent, task, save):
    window = ctk.CTkToplevel(parent); window.title('المهمة')
    window.geometry('660x{}'.format(min(650,max(430,owner.root.winfo_screenheight()-100))))
    window.minsize(580,430)
    window.transient(parent); window.grab_set()
    controls = ctk.CTkFrame(window); controls.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True)
    description = entry(area, 'وصف المهمة', task.get('description', ''), True)
    owners = {'': 'بدون'}
    owners.update({key: doctor_label(owner.client.doctors, key) for key in owner.client.doctors})
    owner_value = combo(area, 'مسؤول اختياري', owners, task.get('ownerDoctorId') or '')
    priority = combo(area, 'الأولوية', TASK_PRIORITY, task.get('priority', 'NORMAL'))
    done = combo(area, 'مكتملة', {False:'لا', True:'نعم'}, bool(task.get('done')))
    due_enabled = tk.BooleanVar(value=bool(task.get('dueAtEpochMillis')))
    ctk.CTkCheckBox(area, text='تحديد موعد', variable=due_enabled).pack(anchor='e', padx=10, pady=10)
    due = datetime.fromtimestamp(task['dueAtEpochMillis']/1000, ZoneInfo(owner.client.zone)) if task.get('dueAtEpochMillis') else datetime.now(ZoneInfo(owner.client.zone))
    ctk.CTkLabel(area, text='الموعد: سنة / شهر / يوم — ساعة : دقيقة', anchor='e').pack(fill='x', padx=10)
    date_frame = ctk.CTkFrame(area); date_frame.pack(fill='x', padx=10)
    spinners = []
    # Native numeric pickers preserve editable keyboard input and avoid a free-text time field.
    for value, lower, upper, width in [(due.year,1900,2200,6),(due.month,1,12,3),(due.day,1,31,3),(due.hour,0,23,3),(due.minute,0,59,3)]:
        variable = tk.StringVar(value=str(value))
        spin = tk.Spinbox(date_frame, from_=lower, to=upper, width=width, textvariable=variable, font=('Arial', 14))
        spin.pack(side='left', expand=True, padx=3, pady=8); spinners.append(variable)
    due_shift = entry(area, 'نهاية مناوبة YYYY-MM-DD (اختياري)', task.get('dueShiftDate') or '')
    if task.get('completedByName'):
        ctk.CTkLabel(area, text='أتمها: ' + task['completedByName'], anchor='e').pack(fill='x', padx=10)
    def commit():
        result = copy.deepcopy(task)
        result.update(description=description.get('1.0','end-1c').strip(), ownerDoctorId=owner_value() or None,
                      priority=priority(), done=done(), dueShiftDate=due_shift.get().strip() or None)
        require(result['description'], 'وصف المهمة مطلوب')
        values = [int(v.get()) for v in spinners]
        result['dueAtEpochMillis'] = int(datetime(*values, tzinfo=ZoneInfo(owner.client.zone)).timestamp()*1000) if due_enabled.get() else None
        # Existing seconds/milliseconds are preserved unless the displayed time was changed.
        if due_enabled.get() and values == [due.year, due.month, due.day, due.hour, due.minute] and task.get('dueAtEpochMillis'):
            result['dueAtEpochMillis'] = task['dueAtEpochMillis']
        if result['dueShiftDate']:
            datetime.strptime(result['dueShiftDate'], '%Y-%m-%d')
            require(result['dueAtEpochMillis'], 'حدد الموعد مع مناوبة الاستحقاق')
        if not result['done']:
            result.update(completedByDoctorId=None, completedByName=None, completedAtEpochMillis=None)
        save(result); window.destroy(); parent.grab_set()
    button(controls, 'حفظ المهمة', guarded(commit))
    button(controls, 'إلغاء', window.destroy)
    owner.root.wait_window(window)
    if parent.winfo_exists(): parent.grab_set()


def edit_patient(owner, source, is_new=False):
    row = copy.deepcopy(source)
    tasks = json.loads(row.get('tasksJson') or '[]')
    badges = badges_decode(row.get('warningDetails', ''))
    window = modal(owner, 'مريض جديد' if is_new else 'تحرير المريض')
    controls = ctk.CTkFrame(window); controls.pack(side='bottom', fill='x')
    tabs = ctk.CTkTabview(window); tabs.pack(fill='both', expand=True, padx=8, pady=4)
    order = ['البيانات الشخصية', 'السريرية', 'المهام', 'الشارات'] if is_new else ['السريرية', 'المهام', 'الشارات', 'البيانات الشخصية']
    areas = {}
    for title in order:
        tabs.add(visual_text(title)); areas[title] = ctk.CTkScrollableFrame(tabs.tab(visual_text(title))); areas[title].pack(fill='both', expand=True)
    widgets, getters = {}, {}
    for key in ['initialDiagnosis','treatmentPlan','followUp','labs']:
        widgets[key] = entry(areas['السريرية'], LABELS[key], row.get(key,''), True)
    for key, role in [('responsibleResidentId','RESIDENT'),('responsibleSupervisorId','SUPERVISOR')]:
        choices = {'': 'بدون'}
        choices.update({key:doctor_label(owner.client.doctors,key) for key,d in owner.client.doctors.items() if d['clinicalRole']==role})
        getters[key] = combo(areas['السريرية'], LABELS[key], choices, row.get(key,''))
    getters['isPriority'] = combo(areas['السريرية'], LABELS['isPriority'], YES, row.get('isPriority') or 'N')
    for key in ['name','admitNum','admitDate','birthDate']:
        widgets[key] = entry(areas['البيانات الشخصية'], LABELS[key], row.get(key,''))
    numeric = (window.register(lambda text: not text or all(ch.isdecimal() for ch in text)), '%P')
    for key in ['admitNum','birthDate']:
        widgets[key].configure(validate='key', validatecommand=numeric)
    for key, mapping in [('gender',GENDER),('hasCompanion',YES),('diagnosisType',DIAGNOSIS)]:
        getters[key] = combo(areas['البيانات الشخصية'], LABELS[key], mapping, row[key])
    ctk.CTkLabel(areas['البيانات الشخصية'], text='سنة الميلاد أو العمر (يحوّل العمر إلى سنة تقديرية)', anchor='e').pack(fill='x', padx=10)
    age = entry(areas['البيانات الشخصية'], 'العمر — اختياري')
    def apply_age():
        require(age.get().strip().isdecimal(), 'أدخل العمر بالأرقام')
        year = datetime.now(ZoneInfo(owner.client.zone)).year-int(age.get())
        widgets['birthDate'].delete(0,'end'); widgets['birthDate'].insert(0,str(year))
    button(areas['البيانات الشخصية'], 'استخدام العمر ومراجعة سنة الميلاد', guarded(apply_age))
    task_area = areas['المهام']; badge_area = areas['الشارات']
    def refresh_tasks():
        for child in task_area.winfo_children(): child.destroy()
        button(task_area, 'إضافة مهمة', lambda: open_task(None))
        ctk.CTkLabel(task_area, text='الإتمام ينسب إلى الطبيب المتحقق عند مراجعة النشر', wraplength=440).pack(padx=10,pady=4)
        for index, task in enumerate(tasks):
            frame = ctk.CTkFrame(task_area); frame.pack(fill='x', padx=6, pady=4)
            status = 'مكتملة' if task.get('done') else 'معلقة'
            ctk.CTkLabel(frame, text=status + ': ' + task['description'], anchor='e', wraplength=440).pack(fill='x',padx=8)
            button(frame, 'تحرير المهمة / الموعد / المسؤول', lambda i=index: open_task(i))
            button(frame, 'حذف المهمة', lambda i=index: delete_task(i))
    def open_task(index):
        value = tasks[index] if index is not None else {'id':str(uuid.uuid4()), 'description':'', 'ownerDoctorId':None, 'ownerName':None,
                 'priority':'NORMAL', 'done':False, 'dueAtEpochMillis':None, 'dueShiftDate':None,
                 'completedByDoctorId':None, 'completedByName':None, 'completedAtEpochMillis':None}
        def save_task(task):
            if index is None: tasks.append(task)
            else: tasks[index] = task
            refresh_tasks()
        edit_task(owner,window,value,save_task)
    def delete_task(index):
        if messagebox.askyesno('حذف مهمة','هل تؤكد حذف هذه المهمة من المسودة؟',default='no',parent=window):
            tasks.pop(index); refresh_tasks()
    def refresh_badges():
        for child in badge_area.winfo_children(): child.destroy()
        for index,(text,level) in enumerate(badges):
            frame = ctk.CTkFrame(badge_area); frame.pack(fill='x',padx=6,pady=4)
            ctk.CTkLabel(frame,text=text+' · '+BADGE_PRIORITY[level],wraplength=440,anchor='e').pack(fill='x',padx=8)
            button(frame,'حذف الشارة',lambda i=index: remove_badge(i))
        badge_text = entry(badge_area,'نص الشارة',multiline=True)
        level = combo(badge_area,'المستوى',BADGE_PRIORITY,'none')
        def add_badge():
            text = badge_text.get('1.0','end-1c').strip(); require(text,'نص الشارة مطلوب')
            badges.append((text,level())); refresh_badges()
        button(badge_area,'إضافة الشارة',guarded(add_badge))
    def remove_badge(index):
        if messagebox.askyesno('حذف شارة','هل تؤكد حذف الشارة؟',default='no',parent=window):
            badges.pop(index); refresh_badges()
    refresh_tasks(); refresh_badges()
    saving = [False]
    def close():
        if not saving[0] and messagebox.askyesno('إلغاء التحرير','هل تتجاهل التعديلات غير المحفوظة؟',default='no',parent=window): window.destroy()
    def save():
        if saving[0]: return
        result = copy.deepcopy(row)
        for key,widget in widgets.items():
            result[key] = widget.get('1.0','end-1c') if key in ['initialDiagnosis','treatmentPlan','followUp','labs'] else widget.get().strip()
        for key,getter in getters.items(): result[key] = getter()
        for key in ['admitNum','birthDate']:
            import unicodedata
            result[key] = ''.join(str(unicodedata.digit(c)) if c.isdecimal() else c for c in result[key])
        result['tasksJson'] = packed(tasks)
        result['warningDetails'] = badges_encode(badges)
        result['warningFlags'] = '|'.join(level for _,level in badges if level!='none')
        validate_patient(result,owner.client.doctors,owner.client.zone)
        saving[0] = True
        try:
            draft = owner.store.read()
            existing = next((i for i,p in enumerate(draft['patients']) if p['externalId']==row['externalId']),None)
            require(is_new or existing is not None, 'المريض لم يعد في المسودة؛ أعد فتحه')
            if existing is None:
                require(len(draft['patients'])<200,'الحد الأقصى 200 مريض'); draft['patients'].append(result)
            else: draft['patients'][existing] = result
            owner.save_draft(draft); window.destroy()
        finally: saving[0] = False
    button(controls,'حفظ المريض',guarded(save))
    button(controls,'إلغاء',close)
    window.protocol('WM_DELETE_WINDOW',close)
    owner.root.wait_window(window)
