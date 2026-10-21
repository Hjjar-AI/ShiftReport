"""Doctor picker; shared draft and callbacks remain caller-owned."""
from __future__ import annotations
import tkinter as tk
from desktop_input import ArabicEntry
import desktop_widgets as ctk
from desktop_model import doctor_label
from desktop_form_controls import button, dialog_actions, section_heading


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

