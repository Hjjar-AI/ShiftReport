"""Inline checklist values stay inside the patient form until its Save."""
from __future__ import annotations
import copy
import tkinter as tk
import uuid
import desktop_widgets as ctk
from desktop_form_controls import button, entry


def inline_tasks(owner, parent, tasks):
    rows = ctk.CTkFrame(parent); rows.pack(fill='x')
    new_text = entry(parent, 'مهمة جديدة', '', True)
    widgets = {}
    new_id = [str(uuid.uuid4())]

    def collect_existing():
        for task in tasks:
            text, done = widgets[task['id']]
            completed = bool(done.get())
            if completed != bool(task.get('done')):
                task.update(completedByDoctorId=None, completedByName=None, completedAtEpochMillis=None)
            task.update(description=text.get('1.0', 'end-1c'), done=completed)

    def read():
        collect_existing()
        result = copy.deepcopy(tasks)
        description = new_text.get('1.0', 'end-1c')
        if description.strip():
            result.append(dict(id=new_id[0], description=description, done=False))
        return result

    def remove(task_id):
        collect_existing()
        tasks[:] = [task for task in tasks if task['id'] != task_id]
        refresh()

    def refresh():
        for child in rows.winfo_children():child.destroy()
        widgets.clear()
        for task in tasks:
            frame = ctk.CTkFrame(rows); frame.pack(fill='x', padx=6, pady=4)
            done = tk.BooleanVar(value=bool(task.get('done')))
            ctk.CTkCheckBox(frame, text='مكتملة', variable=done).pack(anchor='e', padx=10, pady=4)
            text = entry(frame, 'وصف المهمة', task.get('description', ''), True)
            widgets[task['id']] = (text, done)
            button(frame, 'حذف المهمة', lambda key=task['id']:remove(key))

    def add():
        if not new_text.get('1.0', 'end-1c').strip():return
        tasks[:] = read()
        new_text.delete('1.0', 'end')
        new_id[0] = str(uuid.uuid4())
        refresh()

    button(parent, 'إضافة مهمة', add)
    ctk.CTkLabel(parent, text='تُحفظ المهام مع حفظ المريض.', anchor='e').pack(fill='x', padx=10, pady=4)
    refresh()
    return read
