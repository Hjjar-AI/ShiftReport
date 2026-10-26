"""Patient-card presentation and action placement, with mutations owned by Window."""
import copy
import tkinter as tk
from desktop_tokens import ButtonStyle, button_options, CLINICAL_URGENT, CLINICAL_WARNING, CLINICAL_PENDING, STATUS_EDGE
import desktop_widgets as ctk
from desktop_model import patient_name, DIAGNOSIS, patient_text, pending_task_count, badges_decode


def patient_card(owner, parent, row, index, total, movement_allowed, preview_style=None):
    row=copy.deepcopy(row)
    width=max(240,owner.content.winfo_width()-40)
    key=row['externalId']
    pending=pending_task_count([row])
    style=preview_style or {}
    scale=style.get('scale',1.0)
    compact=style.get('density',getattr(owner,'card_density',None).get() if hasattr(owner,'card_density') else 'عادية')=='مضغوطة'
    inset=6 if compact else 10
    gap=2 if compact else 5
    foreground=style.get('foreground',('gray10','gray90'))
    muted=style.get('muted',('gray35','gray75'))
    frame=ctk.CTkFrame(parent,**({'fg_color':style['background'],'border_color':style['accent'],'border_width':1} if style else {}))
    levels = {level for _, level in badges_decode(row.get('warningDetails', ''))}
    edge = (CLINICAL_URGENT if row.get('isPriority') == 'Y' or 'high' in levels
            else CLINICAL_WARNING if 'medium' in levels else CLINICAL_PENDING if pending else None)
    if edge:
        stripe = ctk.CTkFrame(frame, width=STATUS_EDGE, corner_radius=0, fg_color=edge)
        stripe.place(relx=1, rely=0, relheight=1, anchor='ne')
    ctk.CTkLabel(frame,text='{} · {}'.format(index+1,patient_name(row)),font=('Arial',round(18*scale),'bold'),text_color=foreground,
        anchor='e',justify='right',wraplength=width).pack(fill='x',padx=inset,pady=(gap,1))
    alerts=(["أولوية"] if row.get('isPriority')=='Y' else [])+[text for text,level in badges_decode(row.get('warningDetails','')) if level=='high']
    if alerts:
        ctk.CTkLabel(frame,text=' · '.join(alerts),anchor='e',justify='right',wraplength=width,
            font=('Arial',round(14*scale),'bold'),text_color=foreground).pack(fill='x',padx=inset,pady=gap)
    metadata='قبول {} · {}'.format(row['admitNum'],DIAGNOSIS.get(row['diagnosisType'],''))
    if row.get('hasCompanion')=='Y':metadata+=' · مرافق'
    if key in owner.cards.pinned:metadata+=' · مثبت للعرض'
    ctk.CTkLabel(frame,text=metadata,font=('Arial',round(13*scale)),text_color=muted,
        anchor='e',justify='right',wraplength=width).pack(fill='x',padx=inset,pady=1)
    expanded=key in owner.cards.expanded
    if expanded:
        # Render the existing full clinical formatter once; it includes all tasks and badges.
        ctk.CTkLabel(frame,text=patient_text(row,owner.client.doctors,owner.client.zone,clinical_only=True),
            anchor='e',justify='right',wraplength=width,font=('Arial',round(14*scale)),text_color=foreground).pack(fill='x',padx=inset,pady=gap)
    elif row.get('initialDiagnosis'):
        diagnosis=row['initialDiagnosis']
        ctk.CTkLabel(frame,text='التشخيص: '+diagnosis[:180]+('…' if len(diagnosis)>180 else ''),
            anchor='e',justify='right',wraplength=width,font=('Arial',round(14*scale)),text_color=foreground).pack(fill='x',padx=inset,pady=gap)
    if pending:
        tasks='مهام معلقة {}'.format(pending)
        ctk.CTkLabel(frame,text=tasks,anchor='e',justify='right',wraplength=width,
            font=('Arial',round(13*scale),'bold'),text_color=foreground).pack(fill='x',padx=inset,pady=1)
    if preview_style is not None:return frame
    primary=ctk.CTkFrame(frame,fg_color='transparent')
    primary.pack(fill='x')
    for column in range(4):primary.grid_columnconfigure(column,weight=1 if column==3 else 0)
    for column,(label,action) in enumerate([
        ('تحرير',lambda:owner.edit(copy.deepcopy(row))),
        ('الملف الكامل',lambda:owner.details(copy.deepcopy(row))),
        ('طي' if expanded else 'توسيع',lambda:owner.toggle_card(key,False))
    ]):
        kwargs={} if column==0 else button_options(ButtonStyle.OUTLINED)
        ctk.CTkButton(primary,text=label,width=105 if column==1 else 75,height=48,command=lambda f=action:owner.run(f),**kwargs
            ).grid(row=0,column=3-column,sticky='ew',padx=3,pady=3)
    actions={
        'إلغاء التثبيت' if key in owner.cards.pinned else 'تثبيت للعرض':lambda:owner.toggle_card(key,True),
        'حذف المريض':lambda:owner.delete_patient(key)
    }
    popup = tk.Menu(primary, tearoff=False)
    def show_menu():
        popup.delete(0, 'end')
        for label, action in actions.items():
            popup.add_command(label=ctk.visual_text(label), command=lambda f=action: owner.run(f))
        try:
            popup.tk_popup(menu.winfo_rootx(), menu.winfo_rooty()+menu.winfo_height())
        finally:
            popup.grab_release()
    menu = ctk.CTkButton(primary, text='المزيد', width=105, height=48, command=show_menu,
                         **button_options(ButtonStyle.OUTLINED))
    menu.grid(row=0, column=0, padx=3, pady=3)
    def refresh_movement(allowed):
        actions.pop('نقل لأعلى',None);actions.pop('نقل لأسفل',None)
        if allowed and index>0:actions['نقل لأعلى']=lambda:owner.move(index,-1)
        if allowed and index<total-1:actions['نقل لأسفل']=lambda:owner.move(index,1)
    frame.refresh_movement=refresh_movement
    refresh_movement(movement_allowed)
    return frame
