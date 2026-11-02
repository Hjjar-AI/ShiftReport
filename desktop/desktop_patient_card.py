"""Compact RTL patient cards; mutations and expansion remain workspace-owned."""
import copy
from datetime import date
from desktop_tokens import ButtonStyle, button_options, CLINICAL_URGENT, CLINICAL_WARNING, CLINICAL_PENDING, STATUS_EDGE
import desktop_widgets as ctk
from desktop_model import patient_name, DIAGNOSIS, GENDER, pending_task_count, badges_decode
from desktop_protocol import current_date


def patient_card(owner, parent, row, index, preview_style=None):
    row=copy.deepcopy(row)
    width=ctk.logical_width(owner.content,padding=40,minimum=180)
    key=row['externalId'];pending=pending_task_count([row])
    style=preview_style or {};scale=style.get('scale',1.0)
    comfortable=style.get('density',getattr(owner,'card_density',None).get() if hasattr(owner,'card_density') else 'عادية')=='مريحة'
    inset,gap=(10,5) if comfortable else (6,2)
    expanded=owner.cards.expanded(key) if preview_style is None else True
    from desktop_appearance import LIGHT_CARD, LIGHT_BORDER, preferences
    mode=preferences(owner)['mode'] if preview_style is None else 'System'
    foreground=style.get('foreground',('#202832','#E8EDF4'))
    muted=style.get('muted',('#485360','#C8CFD8'))
    frame=ctk.CTkFrame(parent,fg_color=style.get('background',(LIGHT_CARD,'#000000' if mode=='AMOLED' else '#252F3A')),
        border_color=(LIGHT_BORDER,'#465359'),border_width=1)
    levels={level for _,level in badges_decode(row.get('warningDetails',''))}
    edge=(CLINICAL_URGENT if row.get('isPriority')=='Y' or 'high' in levels else
          CLINICAL_WARNING if 'medium' in levels else CLINICAL_PENDING if pending else None)
    if edge:
        ctk.CTkFrame(frame,width=STATUS_EDGE,corner_radius=0,fg_color=edge).place(relx=1,rely=0,relheight=1,anchor='ne')
    heading=ctk.CTkFrame(frame,fg_color='transparent');heading.pack(fill='x',padx=inset,pady=(gap,2))
    if preview_style is None:
        def expand():
            owner.ready(False);owner.cards.toggle_expansion(key);owner.render()
        ctk.CTkButton(heading,text='طي' if expanded else 'توسيع',height=48,width=80,
            command=lambda:owner.run(expand),**button_options(ButtonStyle.OUTLINED)).pack(side='left',padx=2)
    ctk.CTkLabel(heading,text='{} • {}'.format(index+1,patient_name(row)),font=(ctk.UI_FONT_FAMILY,round(18*scale),'bold'),
        text_color=foreground,anchor='e',justify='right',wraplength=max(120,width-90)).pack(side='right',fill='x',expand=True)
    metadata=GENDER.get(row.get('gender'),'')
    if row.get('birthDate'):metadata+=' • '+row['birthDate']
    metadata+=' • قبول '+row.get('admitNum','')
    if row.get('admitDate'):
        days=max(0,(date.fromisoformat(current_date(owner.client.zone))-date.fromisoformat(row['admitDate'])).days)
        metadata+=' • {} يوم • {}'.format(days,row['admitDate'])
    metadata+=' • '+DIAGNOSIS.get(row['diagnosisType'],'')
    if row.get('hasCompanion')=='Y':metadata+=' • مرافق'
    if key in owner.cards.pinned:metadata+=' • مثبت للعرض'
    ctk.CTkLabel(frame,text=metadata,font=(ctk.UI_FONT_FAMILY,round(13*scale)),text_color=muted,
        anchor='e',justify='right',wraplength=width).pack(fill='x',padx=inset,pady=1)
    for field,label in [('responsibleSupervisorId','المشرف'),('responsibleResidentId','المقيم')]:
        if row.get(field):
            line=ctk.CTkFrame(frame,fg_color='transparent');line.pack(fill='x',padx=inset,pady=1)
            ctk.CTkLabel(line,text=label+':',font=(ctk.UI_FONT_FAMILY,round(13*scale),'bold'),anchor='e',text_color=muted).pack(side='right',padx=2)
            ctk.CTkLabel(line,text=owner.client.doctors.get(row[field],{}).get('name','طبيب محفوظ'),
                font=(ctk.UI_FONT_FAMILY,round(13*scale)),text_color=foreground,anchor='e',justify='right',
                wraplength=max(120,width-90)).pack(side='right',fill='x',expand=True)
    alerts=(['أولوية'] if row.get('isPriority')=='Y' else [])+[
        text for text,level in badges_decode(row.get('warningDetails','')) if expanded or level!='low']
    if alerts:
        ctk.CTkLabel(frame,text=' • '.join(alerts),anchor='e',justify='right',wraplength=width,
            font=(ctk.UI_FONT_FAMILY,round(14*scale),'bold'),text_color=foreground).pack(fill='x',padx=inset,pady=gap)
    if preview_style is None:
        primary=ctk.CTkFrame(frame,fg_color='transparent');primary.pack(fill='x',padx=inset,pady=gap)
        actions=[('تحرير',lambda:owner.edit(copy.deepcopy(row))),('التفاصيل',lambda:owner.details(copy.deepcopy(row))),
                 ('إلغاء التثبيت' if key in owner.cards.pinned else 'تثبيت للعرض',lambda:owner.toggle_card(key)),
                 ('حذف',lambda:owner.delete_patient(key))]
        for column,(label,action) in enumerate(actions):
            primary.grid_columnconfigure(column,weight=1,uniform='patient-actions')
            kwargs={} if column==0 else button_options(ButtonStyle.OUTLINED)
            if column==3:kwargs.update(text_color=CLINICAL_URGENT,border_color=CLINICAL_URGENT)
            ctk.CTkButton(primary,text=label,width=65,height=48,command=lambda f=action:owner.run(f),**kwargs
                ).grid(row=0,column=3-column,sticky='ew',padx=2,pady=2)
    if pending:
        ctk.CTkLabel(frame,text='مهام معلّقة {}'.format(pending),anchor='e',justify='right',wraplength=width,
            font=(ctk.UI_FONT_FAMILY,round(13*scale),'bold'),text_color=foreground).pack(fill='x',padx=inset,pady=1)
    from desktop_labs import read_groups,heading as lab_heading
    from desktop_tasks import read_tasks,ordered_for_display,task_text
    sections=[('التشخيص',row.get('initialDiagnosis','')),('الخطة العلاجية',row.get('treatmentPlan','')),
              ('المتابعة',row.get('followUp',''))]
    sections += [(lab_heading(group),group['results']) for group in read_groups(row)]
    sections += [('المهام','\n'.join(task_text(task) for task in ordered_for_display(read_tasks(row))))]
    for label,value in sections if expanded else sections[:1]:
        if not value.strip():continue
        if expanded:
            ctk.CTkLabel(frame,text=label,font=(ctk.UI_FONT_FAMILY,round(14*scale),'bold'),anchor='e',justify='right',
                text_color=foreground,fg_color=('#E8EEF2','#15171B' if mode=='AMOLED' else '#394655'),
                corner_radius=4,wraplength=width).pack(fill='x',padx=inset,pady=(gap,1))
        ctk.CTkLabel(frame,text=value if expanded else value.split('\n')[0][:180]+('…' if len(value)>180 or '\n' in value else ''),anchor='e',justify='right',
            wraplength=width,font=(ctk.UI_FONT_FAMILY,round(14*scale)),text_color=foreground).pack(fill='x',padx=inset,pady=gap)
    return frame
