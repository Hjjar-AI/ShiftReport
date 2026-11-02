"""Sectioned read-only patient rounds; navigation state remains workspace-owned."""
from desktop_protocol import require, current_date
from desktop_model import patient_name


class PatientRounds:
    def __init__(self, project, shift_id, ids, selected):
        self.project,self.shift_id=project,shift_id
        self.ids=list(dict.fromkeys(ids))
        if selected not in self.ids:self.ids.insert(0,selected)
        self.selected=selected

    def patients(self,project,shift):
        require(project==self.project and shift['meta']['shiftId']==self.shift_id,
                'تغير المشروع أو المناوبة؛ أعد فتح تفاصيل المريض')
        by_id={patient['externalId']:patient for patient in shift['patients']}
        return [by_id[key] for key in self.ids if key in by_id]

    def navigate(self,project,shift,delta):
        patients=self.patients(project,shift)
        ids=[patient['externalId'] for patient in patients]
        if self.selected not in ids:return None
        target=ids.index(self.selected)+delta
        if not 0<=target<len(ids):return None
        self.selected=ids[target]
        return patients[target]


def patient_details(owner,row):
    import desktop_widgets as ctk
    from desktop_widgets import set_readonly_text
    from desktop_forms import modal,guarded,dialog_actions
    from desktop_model import patient_text, badges_decode, BADGE_PRIORITY
    from desktop_tasks import read_tasks, ordered_for_display, task_text
    from desktop_sections import SectionPages
    owner.ready(False)
    shift=owner.store.read()
    rounds=PatientRounds(owner.client.project_id,shift['meta']['shiftId'],
                        [patient['externalId'] for _,_,patient in owner.patient_display(shift)],row['externalId'])
    window=modal(owner,'ملف المريض الكامل — جولة قراءة','880x760')
    title=ctk.CTkLabel(window,text='',anchor='e',justify='right',wraplength=700)
    title.pack(fill='x',padx=10,pady=5)
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    sections=SectionPages(window);sections.pack(fill='both',expand=True,padx=8,pady=5)
    clinical,tasks_section,badges_section='الملف السريري','المهام','الشارة'
    contents={}
    for section in (clinical,tasks_section,badges_section):
        sections.add(section)
        content=ctk.CTkTextbox(sections.tab(section),wrap='word')
        content.pack(fill='both',expand=True,padx=4,pady=4)
        contents[section]=content
    edit_key=[None]
    scope=(owner.client.project_id,shift['meta']['shiftId'])
    if getattr(owner,'detail_scroll_scope',None)!=scope:
        owner.detail_scroll_scope=scope;owner.detail_scroll_positions={};owner.detail_selected_sections={}
    scroll_positions=owner.detail_scroll_positions
    selected_sections=owner.detail_selected_sections
    displayed=[None]

    def remember_position():
        if displayed[0] is None:return
        key,section=displayed[0]
        content=contents[section]
        if content.winfo_exists():scroll_positions[(key,section)]=content.yview()[0]

    def close_details():
        remember_position()
        window.destroy()

    def show(delta=0,remember=True):
        if owner.locked or not window.winfo_exists():return
        current=owner.store.read()
        patients=rounds.patients(owner.client.project_id,current)
        if remember:remember_position()
        if delta:
            rounds.navigate(owner.client.project_id,current,delta)
        ids=[patient['externalId'] for patient in patients]
        patient=next((patient for patient in patients if patient['externalId']==rounds.selected),None)
        if patient is None:
            displayed[0]=None
            for content in contents.values():
                set_readonly_text(content,'المريض لم يعد في المناوبة؛ أغلق التفاصيل وأعد اختيار مريض.')
            sections.set_label(tasks_section,tasks_section)
            sections.set_label(badges_section,badges_section)
            title.configure(text='المريض غير متاح')
            for widget in (previous,next_button,edit):widget.configure(state='disabled')
            return
        index=ids.index(rounds.selected)
        title.configure(text='{} — {} / {} • مناوبة {}\nللقراءة؛ ترتيب الجولة مأخوذ من العرض عند فتحها.'.format(patient_name(patient),index+1,len(ids),current_date(owner.client.zone)))
        tasks=read_tasks(patient)
        badges=badges_decode(patient.get('warningDetails',''))
        pending=sum(not task.get('done') for task in tasks)
        sections.set_label(tasks_section,tasks_section+(' '+str(pending) if pending else ''))
        sections.set_label(badges_section,badges_section+(' '+str(len(badges)) if badges else ''))
        set_readonly_text(contents[clinical],patient_text(patient,owner.client.doctors,owner.client.zone))
        set_readonly_text(contents[tasks_section],
            '\n'.join(task_text(task) for task in ordered_for_display(tasks)) or 'لا توجد مهام مسجلة')
        set_readonly_text(contents[badges_section],
            '\n'.join('• '+text+(' • أولوية '+BADGE_PRIORITY[level] if level!='none' else '')
                      for text,level in badges) or 'لا توجد شارات')
        selected=selected_sections.get(rounds.selected,clinical)
        sections.on_change=None
        sections.set(selected)
        sections.on_change=guarded(select_section)
        displayed[0]=(rounds.selected,selected)
        position=displayed[0]
        content=contents[selected]
        def scroll():
            if content.winfo_exists() and displayed[0]==position:
                content.yview_moveto(scroll_positions.get(position,0))
        content.after_idle(scroll)
        previous.configure(state='normal' if index>0 else 'disabled')
        next_button.configure(text='التالي' if index+1<len(ids) else 'نهاية الجولة',state='normal' if index+1<len(ids) else 'disabled')
        edit.configure(state='normal')

    def select_section():
        if owner.locked:return
        remember_position()
        selected_sections[rounds.selected]=sections.get()
        show(remember=False)


    def open_edit():
        current=owner.editable()
        rounds.patients(owner.client.project_id,current)
        require(any(patient['externalId']==rounds.selected for patient in current['patients']), 'المريض لم يعد متاحاً')
        edit_key[0]=rounds.selected
        close_details()

    previous=ctk.CTkButton(controls,text='السابق',height=48,width=100,command=guarded(lambda:show(-1)))
    next_button=ctk.CTkButton(controls,text='التالي',height=48,width=100,command=guarded(lambda:show(1)))
    for column,widget in enumerate((next_button,previous)):
        controls.grid_columnconfigure(column,weight=1,uniform='rounds')
        widget.grid(row=0,column=column,sticky='ew',padx=8,pady=4)
    footer=ctk.CTkFrame(controls,fg_color='transparent')
    footer.grid(row=1,column=0,columnspan=2,sticky='ew')
    actions=dialog_actions(footer,primary=('تحرير',guarded(open_edit)),cancel=('إغلاق',close_details))
    edit=actions['تحرير']
    window.bind('<Configure>',lambda event:title.configure(wraplength=max(240,event.width-24))
                if event.widget is window else None,add='+')
    window.protocol('WM_DELETE_WINDOW',close_details)
    show()
    owner.root.wait_window(window)
    if edit_key[0]:
        current=owner.editable()
        rounds.patients(owner.client.project_id,current)
        patient=next((patient for patient in current['patients'] if patient['externalId']==edit_key[0]),None)
        if patient is not None:owner.edit(patient)
