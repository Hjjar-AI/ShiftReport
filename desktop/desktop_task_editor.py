"""Task editor; shared draft and callbacks remain caller-owned."""
from __future__ import annotations
import copy
from datetime import datetime
import tkinter as tk
import desktop_widgets as ctk
from desktop_protocol import ZoneInfo
from desktop_model import TASK_PRIORITY
from desktop_form_controls import dialog_actions, section_heading, discard_guard, entry, combo, guarded
from desktop_doctor_picker import doctor_picker


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

