"""Patient editor; shared draft and callbacks remain caller-owned."""
from __future__ import annotations
import copy
from datetime import datetime
import json
from desktop_widgets import messagebox
import desktop_widgets as ctk
from desktop_protocol import ZoneInfo, packed, require
from desktop_model import LABELS, GENDER, YES, DIAGNOSIS, BADGE_PRIORITY, badges_decode, badges_encode, validate_patient
from desktop_form_controls import button, dialog_actions, section_heading, entry, combo, modal, guarded
from desktop_doctor_picker import doctor_picker
from desktop_task_editor import inline_tasks


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
    section_heading(areas['السريرية'],'الخطة والمتابعة')
    for key in ['treatmentPlan','followUp','labs']:
        widgets[key]=entry(areas['السريرية'],LABELS[key],row.get(key,''),True)
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
    read_task_draft = inline_tasks(owner, task_area, tasks)
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
    refresh_badges()
    saving = [False]
    draft_timer = [None]
    last_form = [None]
    def capture():
        result = copy.deepcopy(row)
        for key,widget in widgets.items():
            result[key] = widget.get('1.0','end-1c') if key in ['initialDiagnosis','treatmentPlan','followUp','labs'] else widget.get().strip()
        for key,getter in getters.items(): result[key] = getter()
        result['tasksJson'] = packed(read_task_draft())
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
            require(is_new or existing is not None, 'المريض لم يعد في المسودة؛ أعد فتحه')
            if existing is None:
                require(len(draft['patients'])<200,'الحد الأقصى 200 مريض')
                if position is not None and position.get().strip():
                    from desktop_sort import insert_patient
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

