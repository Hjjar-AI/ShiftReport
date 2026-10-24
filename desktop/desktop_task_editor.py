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
    done = combo(area, 'مكتملة', {False:'لا', True:'نعم'}, bool(task.get('done')))
    ctk.CTkLabel(area,text='اعتماد المهمة يضيفها إلى المسودة؛ اضغط حفظ في صفحة المريض لحفظها.',anchor='e',justify='right',wraplength=540).pack(fill='x',padx=10,pady=4)
    if task.get('completedByName'):
        ctk.CTkLabel(area, text='أتمها: ' + task['completedByName'], anchor='e').pack(fill='x', padx=10)
    def draft_values():
        return (description.get('1.0','end-1c'), done())
    initial_values=draft_values()
    cancel=discard_guard(window,lambda:draft_values()!=initial_values,
        'لم تُعتمد تعديلات المهمة في مسودة المريض. هل تريد تجاهلها؟')
    def commit():
        result = copy.deepcopy(task)
        result.update(description=description.get('1.0','end-1c').strip(), done=done())
        if not result['done']:
            result.update(completedByDoctorId=None, completedByName=None, completedAtEpochMillis=None)
        from desktop_tasks import validate as validate_tasks
        validate_tasks([result], require_completion=False)
        save(result); window.destroy(); parent.grab_set()
    dialog_actions(controls,primary=('اعتماد المهمة في المسودة',guarded(commit)),cancel=('إلغاء',cancel))
    owner.root.wait_window(window)
    if parent.winfo_exists(): parent.grab_set()

