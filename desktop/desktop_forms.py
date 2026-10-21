"""CustomTkinter patient/task editors. Edits remain private until Save."""
from __future__ import annotations
import copy
from datetime import datetime
import json
import tkinter as tk
from desktop_widgets import messagebox
from desktop_input import ArabicEntry, ArabicTextbox
import uuid
import desktop_widgets as ctk
from desktop_protocol import BridgeError, ZoneInfo, packed, require
from desktop_tasks import task_text
from desktop_model import (LABELS, GENDER, YES, DIAGNOSIS, TASK_PRIORITY, BADGE_PRIORITY,
                           doctor_label, badges_decode, badges_encode, validate_patient)


def button(parent, text, command, **kwargs):
    widget = ctk.CTkButton(parent, text=text, command=command, height=48, **kwargs)
    widget.pack(fill='x', padx=8, pady=4)
    return widget


def dialog_actions(parent, primary=None, cancel=None, secondary=()):
    """Fixed dialog footer: primary on the right, cancel on the left, extras above.

    Long labels use a full row; callbacks retain the caller's validation/review.
    """
    widgets={}
    quiet=dict(fg_color='transparent',border_width=1,text_color=('gray20','gray90'))
    for index,(label,command) in enumerate(secondary):
        widget=ctk.CTkButton(parent,text=label,command=command,height=48,**quiet)
        widget.grid(row=index,column=0,columnspan=2,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    row=len(secondary)
    stacked=bool(primary and len(primary[0])>28)
    parent.grid_columnconfigure(0,weight=1,uniform='dialog-actions')
    parent.grid_columnconfigure(1,weight=1,uniform='dialog-actions')
    if primary:
        label,command=primary
        widget=ctk.CTkButton(parent,text=label,command=command,height=48)
        widget.grid(row=row,column=0 if stacked else 1,columnspan=2 if stacked else 1,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    if cancel:
        label,command=cancel
        widget=ctk.CTkButton(parent,text=label,command=command,height=48,**quiet)
        widget.grid(row=row+int(stacked),column=0,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    return widgets


def section_heading(parent, title, summary=None):
    ctk.CTkLabel(parent,text=title,font=('Arial',16,'bold'),anchor='e').pack(fill='x',padx=10,pady=(10,3))
    if summary:
        ctk.CTkLabel(parent,text=summary,anchor='e',justify='right',wraplength=560).pack(fill='x',padx=10,pady=2)


def destination_row(parent, title, summary, command):
    return button(parent,title+'  ‹\n'+summary,command,anchor='e',fg_color='transparent',
                  border_width=1,text_color=('gray20','gray90'))


def doctor_picker(parent, label, doctors, value='', role=None, undefined='غير معيّن'):
    from desktop_doctor_choices import doctor_groups
    from desktop_patient_filters import normalize_search
    selected=[value or '']
    ctk.CTkLabel(parent,text=label,anchor='e').pack(fill='x',padx=10,pady=(6,0))
    current=ctk.CTkLabel(parent,text=doctor_label(doctors,value) if value else undefined,
                       anchor='e',justify='right',wraplength=520)
    current.pack(fill='x',padx=10,pady=2)
    def choose():
        previous=parent.winfo_toplevel()
        window=ctk.CTkToplevel(previous);window.title(label);window.geometry('660x560');window.minsize(580,430)
        window.transient(previous);window.grab_set()
        controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
        query=tk.StringVar()
        ArabicEntry(window,textvariable=query,justify='right',height=48,placeholder_text='بحث باسم الطبيب').pack(fill='x',padx=10,pady=6)
        area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
        def commit(key):
            selected[0]=key
            current.configure(text=doctor_label(doctors,key) if key else undefined)
            window.destroy()
        def render(*args):
            for child in area.winfo_children():child.destroy()
            button(area,('✓ ' if not selected[0] else '')+undefined,lambda:commit(''))
            keys=[key for key,doctor in doctors.items() if (role is None or doctor['clinicalRole']==role)
                  and normalize_search(query.get()) in normalize_search(doctor_label(doctors,key))]
            if not keys:ctk.CTkLabel(area,text='لا توجد أسماء مطابقة',anchor='e').pack(fill='x',padx=10,pady=8)
            for title,members in doctor_groups(doctors,keys).items():
                section_heading(area,title)
                for key in members:
                    button(area,('✓ ' if key==selected[0] else '')+doctor_label(doctors,key),lambda key=key:commit(key))
        query.trace_add('write',render)
        dialog_actions(controls,cancel=('رجوع',window.destroy))
        render();window.wait_window()
        if previous.winfo_exists():previous.grab_set()
    button(parent,'اختيار / تغيير الطبيب',choose,fg_color='transparent',border_width=1,text_color=('gray20','gray90'))
    return lambda:selected[0]


def discard_guard(window, dirty, message='لم تُحفظ التغييرات. هل تريد الخروج دون حفظ؟'):
    def close():
        if not dirty() or messagebox.askyesno('تجاهل التغييرات؟',message,default='no',parent=window):window.destroy()
    window.protocol('WM_DELETE_WINDOW',close)
    window.bind('<Escape>',lambda event:close())
    return close


def reveal_field(widget):
    """Keep keyboard-focused entries visible in the nearest existing scroll frame."""
    if not widget.winfo_exists():return
    container=widget.master
    while container is not None:
        canvas=getattr(container,'_parent_canvas',None)
        if canvas is not None:
            bounds=canvas.bbox('all')
            if not bounds:return
            top=widget.winfo_rooty()-canvas.winfo_rooty()
            bottom=top+widget.winfo_height()
            delta=top-8 if top<8 else bottom-canvas.winfo_height()+8 if bottom>canvas.winfo_height()-8 else 0
            if delta:
                height=max(1,bounds[3]-bounds[1])
                canvas.yview_moveto(max(0,min(1,(canvas.canvasy(0)+delta-bounds[1])/height)))
            return
        container=getattr(container,'master',None)


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
    widget.bind('<FocusIn>',lambda event:widget.after_idle(lambda:reveal_field(widget)),add='+')
    return widget


def combo(parent, label, mapping, value, on_change=None):
    ctk.CTkLabel(parent, text=label, anchor='e').pack(fill='x', padx=10, pady=(6, 0))
    # Labels include stable doctor IDs so duplicate names remain distinguishable.
    mapping = dict(mapping)
    if value not in mapping:
        mapping[value] = 'قيمة محفوظة [' + str(value) + ']'
    variable = tk.StringVar(value=mapping[value])
    widget = ctk.CTkComboBox(parent, values=list(mapping.values()), variable=variable, state='readonly', justify='right', height=48,
                           command=(lambda value:on_change()) if on_change else None)
    widget.pack(fill='x', padx=10, pady=2)
    return lambda: next(key for key, label in mapping.items() if label == variable.get())


def modal(owner, title, geometry='820x680'):
    window = ctk.CTkToplevel(owner.root)
    width,height=(int(value) for value in geometry.split('x'))
    width=min(width,max(580,owner.root.winfo_screenwidth()-60))
    height=min(height,max(430,owner.root.winfo_screenheight()-100))
    if not hasattr(owner,'dialog_sizes'):owner.dialog_sizes={}
    saved=owner.dialog_sizes.get(title)
    if saved:
        width=min(saved[0],max(580,owner.root.winfo_screenwidth()-60))
        height=min(saved[1],max(430,owner.root.winfo_screenheight()-100))
    window.title(title); window.geometry('{}x{}'.format(width,height)); window.minsize(580,430)
    def remember_size(event):
        if event.widget is window and event.width>=580 and event.height>=430:
            owner.dialog_sizes[title]=(event.width,event.height)
    window.bind('<Configure>',remember_size,add='+')
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
    section_heading(area,'موعد المهمة')
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
    section_heading(area,'تفاصيل إضافية')
    owner_value = doctor_picker(area, 'مسؤول المهمة (اختياري)', owner.client.doctors, task.get('ownerDoctorId') or '')
    priority = combo(area, 'الأولوية', TASK_PRIORITY, task.get('priority', 'NORMAL'))
    done = combo(area, 'مكتملة', {False:'لا', True:'نعم'}, bool(task.get('done')))
    ctk.CTkLabel(area,text='اعتماد المهمة يضيفها إلى المسودة؛ اضغط حفظ في صفحة المريض لحفظها.',anchor='e',justify='right',wraplength=540).pack(fill='x',padx=10,pady=4)
    if task.get('completedByName'):
        ctk.CTkLabel(area, text='أتمها: ' + task['completedByName'], anchor='e').pack(fill='x', padx=10)
    def draft_values():
        return (description.get('1.0','end-1c'),owner_value(),priority(),done(),due_enabled.get(),
                tuple(value.get() for value in spinners),due_shift.get())
    initial_values=draft_values()
    cancel=discard_guard(window,lambda:draft_values()!=initial_values,
        'لم تُعتمد تعديلات المهمة في مسودة المريض. هل تريد تجاهلها؟')
    def commit():
        result = copy.deepcopy(task)
        result.update(description=description.get('1.0','end-1c').strip(), ownerDoctorId=owner_value() or None,
                      priority=priority(), done=done(), dueShiftDate=due_shift.get().strip() or None)
        values = [int(v.get()) for v in spinners]
        result['dueAtEpochMillis'] = int(datetime(*values, tzinfo=ZoneInfo(owner.client.zone)).timestamp()*1000) if due_enabled.get() else None
        # Existing seconds/milliseconds are preserved unless the displayed time was changed.
        if due_enabled.get() and values == [due.year, due.month, due.day, due.hour, due.minute] and task.get('dueAtEpochMillis'):
            result['dueAtEpochMillis'] = task['dueAtEpochMillis']
        if not result['done']:
            result.update(completedByDoctorId=None, completedByName=None, completedAtEpochMillis=None)
        from desktop_tasks import validate as validate_tasks
        validate_tasks([result], require_completion=False)
        save(result); window.destroy(); parent.grab_set()
    dialog_actions(controls,primary=('اعتماد المهمة في المسودة',guarded(commit)),cancel=('إلغاء',cancel))
    owner.root.wait_window(window)
    if parent.winfo_exists(): parent.grab_set()


def edit_patient(owner, source, is_new=False, allow_next=False):
    from desktop_patient_draft import recover_form, save_form
    from desktop_protocol import fingerprint
    recovered = recover_form(owner, source, is_new)
    if recovered is None:return
    row, expected, is_new = recovered
    shift_id = owner.editable()['meta']['shiftId']
    insertion_source=owner.store.read()
    insertion_expected=fingerprint(insertion_source)
    tasks = json.loads(row.get('tasksJson') or '[]')
    badges = badges_decode(row.get('warningDetails', ''))
    window = modal(owner, 'مريض جديد' if is_new else 'تحرير المريض')
    controls = ctk.CTkFrame(window); controls.pack(side='bottom', fill='x')
    save_status=ctk.CTkLabel(controls,text='',anchor='e',justify='right',wraplength=500)
    save_status.grid(row=0,column=0,columnspan=2,sticky='ew',padx=10,pady=4)
    advance=[False]
    identity=ctk.CTkLabel(window,text='',anchor='e',justify='right',font=('Arial',18,'bold'),wraplength=700)
    identity.pack(fill='x',padx=12,pady=4)
    section_hint=ctk.CTkLabel(window,text='',anchor='e')
    section_hint.pack(fill='x',padx=12)
    from desktop_sections import SectionPages
    tabs = SectionPages(window); tabs.pack(fill='both', expand=True, padx=8, pady=4)
    order = ['البيانات الشخصية', 'السريرية', 'المهام', 'الشارات'] if is_new else ['السريرية', 'المهام', 'الشارات', 'البيانات الشخصية']
    areas = {}
    for title in order:
        tabs.add(title,'البيانات' if title=='البيانات الشخصية' else title)
        areas[title] = ctk.CTkScrollableFrame(tabs.tab(title)); areas[title].pack(fill='both', expand=True)
    def show_section():
        selected=tabs.get()
        section_hint.configure(text='القسم الحالي: '+selected)
    tabs.on_change=show_section
    show_section()
    def size_heading(event):
        if event.widget is window:
            identity.configure(wraplength=max(240,event.width-28))
            save_status.configure(wraplength=max(240,event.width-28))
    window.bind('<Configure>',size_heading,add='+')
    widgets, getters = {}, {}
    section_heading(areas['السريرية'],'التشخيص والعلاج')
    widgets['initialDiagnosis']=entry(areas['السريرية'],LABELS['initialDiagnosis'],row.get('initialDiagnosis',''),True)
    from desktop_clinical_fields import ClinicalField
    section_heading(areas['السريرية'],'الخطة والمتابعة')
    fields={}
    for key in ['treatmentPlan','followUp','labs']:
        fields[key]=ClinicalField(areas['السريرية'],LABELS[key],row.get(key,''),allow_date=key=='labs',
                                  today=datetime.now(ZoneInfo(owner.client.zone)).date())
        widgets[key]=fields[key]
    section_heading(areas['السريرية'],'الفريق المسؤول')
    for key, role in [('responsibleResidentId','RESIDENT'),('responsibleSupervisorId','SUPERVISOR')]:
        getters[key] = doctor_picker(areas['السريرية'],LABELS[key],owner.client.doctors,row.get(key,''),role=role)
    ctk.CTkLabel(areas['السريرية'],text='يجوز اختيار مقيم من فريق آخر للتغطية الفردية. لا يغيّر ذلك فريقه الثابت أو مشرف المريض.',justify='right',wraplength=440).pack(fill='x',padx=10,pady=4)
    section_heading(areas['السريرية'],'الأولوية والشارات')
    getters['isPriority'] = combo(areas['السريرية'], LABELS['isPriority'], YES, row.get('isPriority') or 'N')
    for key in ['name','admitNum','admitDate','birthDate']:
        label=LABELS[key] + ' أو يوم/شهر/سنة' if key=='admitDate' else LABELS[key]
        widgets[key] = entry(areas['البيانات الشخصية'], label, row.get(key,''))
    def update_heading(event=None):
        identity.configure(text=widgets['name'].get().strip() or 'مريض جديد')
    widgets['name'].bind('<KeyRelease>',update_heading,add='+')
    widgets['name'].bind('<FocusOut>',update_heading,add='+')
    update_heading()
    position=None
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
    if is_new:
        from desktop_layout import Disclosure
        insertion=Disclosure(areas['البيانات الشخصية'],'موضع الإدراج — اختياري')
        insertion.pack(fill='x',padx=8,pady=4)
        position=entry(insertion.content,'رقم الموضع ضمن القائمة الكاملة')
        position.configure(validate='key',validatecommand=numeric)
        ctk.CTkLabel(insertion.content,text='اتركه فارغاً للإضافة المعتادة. اختيار موضع يمسح الترتيب التلقائي ويحافظ على الأولوية أولاً.',
            anchor='e',justify='right',wraplength=440).pack(fill='x',padx=10,pady=4)
        def update_insertion_title(event=None):
            insertion.refresh('موضع الإدراج — '+(position.get().strip() or 'إضافة معتادة'))
        position.bind('<KeyRelease>',update_insertion_title,add='+')
        position.bind('<FocusOut>',update_insertion_title,add='+')
    task_area = areas['المهام']; badge_area = areas['الشارات']
    def refresh_tasks():
        for child in task_area.winfo_children(): child.destroy()
        button(task_area, 'إضافة مهمة', lambda: open_task(None))
        ctk.CTkLabel(task_area, text='الإتمام ينسب إلى الطبيب المسجل محلياً عند حفظ المريض؛ النشر يحفظ الهوية والوقت', wraplength=440).pack(padx=10,pady=4)
        for index, task in enumerate(tasks):
            frame = ctk.CTkFrame(task_area); frame.pack(fill='x', padx=6, pady=4)
            ctk.CTkLabel(frame, text=task_text(task, owner.client.doctors, owner.client.zone),
                         anchor='e', justify='right', wraplength=440).pack(fill='x',padx=8)
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
    draft_timer = [None]
    last_form = [None]
    def capture():
        result = copy.deepcopy(row)
        for key,widget in widgets.items():
            result[key] = widget.get('1.0','end-1c') if key in ['initialDiagnosis','treatmentPlan','followUp','labs'] else widget.get().strip()
        for key,getter in getters.items(): result[key] = getter()
        result['tasksJson'] = packed(tasks)
        result['warningDetails'] = badges_encode(badges)
        result['warningFlags'] = '|'.join(level for _,level in badges if level!='none')
        return result
    def retain_form():
        candidate = capture()
        save_status.configure(text='تعديلات غير معتمدة — اضغط حفظ المريض لاعتمادها' if candidate != expected
                              else 'لا توجد تعديلات غير محفوظة')
        marker = fingerprint(candidate)
        if marker != last_form[0]:
            if candidate != expected:
                owner.io(lambda:save_form(owner,shift_id,candidate,expected,is_new))
            else:
                owner.io(lambda:owner.store.save_patient_form(None))
            last_form[0] = marker
    def autosave():
        if not window.winfo_exists():return
        if not saving[0] and not owner.locked:
            try:retain_form()
            except Exception:owner.status.set('تعذر حفظ مسودة الإدخال تلقائياً؛ أبق النافذة مفتوحة وأعد الحفظ.')
        draft_timer[0] = window.after(500,autosave)
    def finish():
        if draft_timer[0] is not None:window.after_cancel(draft_timer[0])
        window.destroy()
    def close():
        if saving[0]:return
        choice = messagebox.askyesnocancel('إغلاق تحرير المريض',
            'نعم: إبقاء مسودة الإدخال لاستئنافها. لا: تجاهلها. إلغاء: متابعة التحرير.',default='cancel',parent=window)
        if choice is None:return
        if choice:retain_form()
        else:owner.io(lambda:owner.store.save_patient_form(None))
        finish()
    def save(next_patient=False):
        nonlocal expected
        if saving[0]: return
        result = capture()
        from desktop_dates import admission_date
        result['admitDate']=admission_date(result['admitDate'])
        for key in ['admitNum','birthDate']:
            import unicodedata
            result[key] = ''.join(str(unicodedata.digit(c)) if c.isdecimal() else c for c in result[key])
        validate_patient(result,owner.client.doctors,owner.client.zone)
        saving[0] = True
        try:
            draft = owner.store.read()
            draft_expected = fingerprint(draft)
            require(draft['meta']['shiftId']==shift_id,'تغيرت المناوبة؛ لن تحفظ المسودة في مناوبة أخرى')
            existing = next((i for i,p in enumerate(draft['patients']) if p['externalId']==row['externalId']),None)
            require(not is_new or existing is None,'معرف المريض الجديد موجود؛ لن يُستبدل سجل آخر')
            if not is_new and existing is not None and fingerprint(draft['patients'][existing])!=fingerprint(expected):
                current = draft['patients'][existing]
                from desktop_model import patient_text
                text = 'النسخة المحفوظة الحالية:\n'+patient_text(current,owner.client.doctors,owner.client.zone)+'\n\nمسودة الإدخال:\n'+patient_text(result,owner.client.doctors,owner.client.zone)
                if owner.text_dialog('تغير سجل المريض؛ راجع النسختين',text,True,'اعتماد المراجعة والعودة إلى التحرير'):
                    expected = copy.deepcopy(current)
                    last_form[0] = None
                    retain_form()
                    owner.status.set('راجعت النسخة الحالية؛ اضغط حفظ المريض مجدداً لتطبيق مسودتك صراحةً.')
                return
            require(is_new or existing is not None, 'المريض لم يعد في المسودة؛ أعد فتحه')
            if existing is None:
                require(len(draft['patients'])<200,'الحد الأقصى 200 مريض')
                if position is not None and position.get().strip():
                    from desktop_sort import insert_patient
                    require(draft_expected==insertion_expected,'تغيرت القائمة؛ أعد فتح الإدراج لتحديد الموضع')
                    insert_patient(draft,result,int(position.get().strip()))
                else:draft['patients'].append(result)
            else: draft['patients'][existing] = result
            owner.save_draft(draft,draft_expected)
            owner.io(lambda:owner.store.save_patient_form(None))
            advance[0]=next_patient
            finish()
        finally: saving[0] = False
    draft_timer[0] = window.after(500,autosave)
    action_area=ctk.CTkFrame(controls)
    action_area.grid(row=1,column=0,columnspan=2,sticky='ew')
    controls.grid_columnconfigure(0,weight=1)
    controls.grid_columnconfigure(1,weight=1)
    save_status.configure(text='تعديلات غير معتمدة — اضغط حفظ المريض لاعتمادها' if capture()!=expected
                          else 'لا توجد تعديلات غير محفوظة')
    dialog_actions(action_area,primary=('حفظ المريض',guarded(save)),cancel=('إلغاء',close),
        secondary=[('حفظ والتالي',guarded(lambda:save(True)))] if allow_next and not is_new else [])
    window.protocol('WM_DELETE_WINDOW',close)
    owner.root.wait_window(window)
    return advance[0]
