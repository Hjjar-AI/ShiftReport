"""Patient actions; the Window remains the single workspace/state owner."""
from __future__ import annotations
import tkinter as tk
from desktop_widgets import messagebox
import desktop_widgets as ctk
from desktop_protocol import fingerprint, require
from desktop_model import doctor_label, new_patient, patient_text
from desktop_forms import dialog_actions, edit_patient, modal, guarded, section_heading, discard_guard
from desktop_sort import ordered
from desktop_details import patient_details


def details(owner,row):
    scope=owner.cards.scope
    canvas=owner.patients._parent_canvas
    position=canvas.yview()[0]
    try:patient_details(owner,row)
    finally:
        def restore():
            if not owner.closed and not owner.locked and owner.cards.scope==scope and owner.patients.winfo_exists():
                canvas.yview_moveto(position)
        if not owner.closed:owner.root.after_idle(restore)


def add_patient(owner):
    owner.editable(); edit_patient(owner,new_patient(owner.client.zone),True)


def edit(owner,row):
    draft=owner.editable()
    sequence=[patient['externalId'] for _,_,patient in owner.patient_display(draft)]
    key=row['externalId']
    while key is not None:
        current=owner.editable()
        require(current['meta']['shiftId']==draft['meta']['shiftId'],'تغيرت المناوبة؛ أعد فتح التحرير')
        source=next((patient for patient in current['patients'] if patient['externalId']==key),None)
        if source is None:return
        position=sequence.index(key) if key in sequence else len(sequence)
        successor=sequence[position+1] if position+1<len(sequence) else None
        advance=edit_patient(owner,source,allow_next=successor is not None)
        if not advance:return
        key=successor


def delete_patient(owner,key):
    draft=owner.editable()
    if messagebox.askyesno('حذف مريض','هل تنقل المريض ومهامه وشاراته إلى السلة المحلية؟ ستراجع الحذف قبل النشر.',default='no'):
        owner.io(lambda:owner.store.delete_patient(key,fingerprint(draft)))
        owner.plan=None;owner.raw_draft=None
        owner.status.set('نُقل المريض إلى السلة المشفرة على هذا الجهاز؛ راجع الحذف قبل النشر.')


def recycle_bin(owner):
    draft=owner.editable()
    if not owner.demo:owner.io(owner.store.purge_recycle_bin)
    records=owner.io(owner.store.deleted_patients)
    require(records,'لا توجد سجلات محذوفة محليا لهذه المناوبة؛ الحذف على أجهزة أخرى لا يظهر في هذه السلة')
    choice=owner.choose('استعادة مريض محذوف',
        [item['patient']['name']+' · '+item['patient']['admitNum'] for item in records],
        notice=None if owner.demo else 'تُحذف عناصر السلة محلياً بعد نحو أسبوع. قد يتأخر التنظيف إذا كان التطبيق مغلقاً.')
    if choice is None:return
    row=records[choice]['patient']
    if not owner.text_dialog('مراجعة الاستعادة','استعادة إلى المسودة فقط؛ يلزم مراجعة النسخة الكاملة والنشر لاحقاً.\n\n'+patient_text(row,owner.client.doctors,owner.client.zone),True,'استعادة إلى المسودة'):return
    owner.io(lambda:owner.store.restore_patient(row['externalId'],fingerprint(draft)))
    owner.plan=None;owner.raw_draft=None
    owner.status.set('استُعيد المريض وكامل مهامه وشاراته محليا؛ راجع المسؤوليات والمسودة قبل النشر.')


def move(owner,index,delta):
    require(owner.manual_movement_allowed(),
            'لتحريك المرضى يدوياً اختر دون تجميع والكل وامسح البحث أولاً')
    draft=owner.editable(); expected=fingerprint(draft); target=index+delta
    if 0<=target<len(draft['patients']):
        draft['patients'][index],draft['patients'][target]=draft['patients'][target],draft['patients'][index]
        draft['meta']['sortSpec']='{"levels":[]}'
        owner.save_draft(draft,expected)


def sort_patients(owner,key):
    draft=owner.editable();expected=fingerprint(draft)
    if messagebox.askyesno('ترتيب محفوظ','هل تحفظ هذا الترتيب للعرض والنشر والتقارير؟',default='no'):
        ordered(draft,owner.client.doctors,owner.client.zone,[dict(field='NAME' if key=='name' else 'DAYS_OF_ADMITTANCE',direction='ASC')]); owner.save_draft(draft,expected)


def roster(owner):
    draft=owner.editable(); doctors=owner.client.doctors
    expected=fingerprint(draft)
    window=modal(owner,'أطباء المناوبة','700x560')
    controls=ctk.CTkFrame(window); controls.pack(side='bottom',fill='x')
    from desktop_input import ArabicEntry
    from desktop_patient_filters import normalize_search
    from desktop_doctor_choices import doctor_groups
    query=tk.StringVar()
    ArabicEntry(window,textvariable=query,justify='right',height=48,placeholder_text='بحث باسم الطبيب').pack(fill='x',padx=10,pady=6)
    counter=ctk.CTkLabel(window,text='',anchor='e');counter.pack(fill='x',padx=10,pady=3)
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    selected=[key for key in draft['meta'].get('doctors','').split(',') if key]
    variables={key:tk.BooleanVar(value=key in selected) for key in dict.fromkeys(selected+list(doctors))}
    def update_count():counter.configure(text='المختارون: '+str(sum(value.get() for value in variables.values())))
    def render(*args):
        for child in area.winfo_children():child.destroy()
        keys=[key for key in variables if normalize_search(query.get()) in normalize_search(doctor_label(doctors,key))]
        if not keys:ctk.CTkLabel(area,text='لا توجد أسماء مطابقة',anchor='e').pack(fill='x',padx=10,pady=6)
        for title,members in doctor_groups(doctors,keys).items():
            section_heading(area,title)
            for key in members:
                ctk.CTkCheckBox(area,text=doctor_label(doctors,key),variable=variables[key],height=48,
                    command=update_count).pack(anchor='e',padx=10,pady=3)
        update_count()
    query.trace_add('write',render)
    cancel=discard_guard(window,lambda:{key for key,value in variables.items() if value.get()}!=set(selected))
    render()
    def save():
        owner.editable()
        values=[key for key,variable in variables.items() if variable.get()]
        if not values or any(key not in owner.client.doctors for key in values):
            messagebox.showerror('المناوبة','حدد أطباء نشطين للمناوبة');return
        draft['meta']['doctors']=','.join(values); owner.save_draft(draft,expected);window.destroy()
    dialog_actions(controls,primary=('حفظ أطباء المناوبة',guarded(save)),cancel=('إلغاء',cancel))
    owner.root.wait_window(window)

