"""Read-only patient rounds over a captured view sequence; no clinical mutations."""
from desktop_protocol import require
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
    from desktop_model import patient_text
    owner.ready(False)
    shift=owner.store.read()
    rounds=PatientRounds(owner.client.project_id,shift['meta']['shiftId'],
                        [patient['externalId'] for _,_,patient in owner.patient_display(shift)],row['externalId'])
    window=modal(owner,'ملف المريض الكامل — جولة قراءة','880x760')
    title=ctk.CTkLabel(window,text='',anchor='e',justify='right',wraplength=700)
    title.pack(fill='x',padx=10,pady=5)
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    content=ctk.CTkTextbox(window,wrap='word');content.pack(fill='both',expand=True,padx=8,pady=5)
    edit_key=[None]
    scope=(owner.client.project_id,shift['meta']['shiftId'])
    if getattr(owner,'detail_scroll_scope',None)!=scope:
        owner.detail_scroll_scope=scope;owner.detail_scroll_positions={}
    scroll_positions=owner.detail_scroll_positions
    def close_details():
        if content.winfo_exists():scroll_positions[rounds.selected]=content.yview()[0]
        window.destroy()

    def show(delta=0):
        if owner.locked or not window.winfo_exists():return
        current=owner.store.read()
        patients=rounds.patients(owner.client.project_id,current)
        if delta:
            scroll_positions[rounds.selected]=content.yview()[0]
            rounds.navigate(owner.client.project_id,current,delta)
        ids=[patient['externalId'] for patient in patients]
        patient=next((patient for patient in patients if patient['externalId']==rounds.selected),None)
        if patient is None:
            set_readonly_text(content,'المريض لم يعد في المناوبة؛ أغلق التفاصيل وأعد اختيار مريض.')
            title.configure(text='المريض غير متاح')
            for widget in (previous,next_button,edit,activity):widget.grid_remove()
            return
        index=ids.index(rounds.selected)
        title.configure(text='{} — {} / {} · مناوبة {}\nللقراءة؛ ترتيب الجولة مأخوذ من العرض عند فتحها.'.format(patient_name(patient),index+1,len(ids),current['meta']['shiftDate']))
        set_readonly_text(content,patient_text(patient,owner.client.doctors,owner.client.zone))
        def scroll():
            if content.winfo_exists():content.yview_moveto(scroll_positions.get(rounds.selected,0))
        content.after_idle(scroll)
        previous.configure(state='normal' if index>0 else 'disabled')
        next_button.configure(text='التالي' if index+1<len(ids) else 'نهاية الجولة',state='normal' if index+1<len(ids) else 'disabled')
        edit.configure(state='normal')


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
