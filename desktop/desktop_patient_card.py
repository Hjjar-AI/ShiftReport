"""Patient-card presentation and action placement, with mutations owned by Window."""
import copy
import desktop_widgets as ctk
from desktop_layout import Disclosure
from desktop_model import DIAGNOSIS, patient_text, task_counts


def patient_card(owner, parent, row, index, total, movement_allowed):
    row=copy.deepcopy(row)
    width=max(240,owner.content.winfo_width()-40)
    key=row['externalId']
    pending,overdue=task_counts([row])
    summary='{} — {} · قبول {}\n{}'.format(index+1,row['name'],row['admitNum'],DIAGNOSIS.get(row['diagnosisType'],''))
    if pending:summary+=' · مهام معلقة {}'.format(pending)
    if overdue:summary+=' (متأخرة {})'.format(overdue)
    if key in owner.cards.pinned:summary+=' · مثبت للعرض'
    frame=ctk.CTkFrame(parent)
    frame.pack(fill='x',padx=3,pady=4)
    ctk.CTkLabel(frame,text=summary,font=('Arial',16),anchor='e',justify='right',wraplength=width).pack(fill='x',padx=10,pady=5)
    expanded=key in owner.cards.expanded
    if expanded:
        ctk.CTkLabel(frame,text=patient_text(row,owner.client.doctors,owner.client.zone),
            anchor='e',justify='right',wraplength=width).pack(fill='x',padx=10,pady=5)
    primary=ctk.CTkFrame(frame,fg_color='transparent')
    primary.pack(fill='x')
    for column in range(3):primary.grid_columnconfigure(column,weight=1)
    for column,(label,action) in enumerate([
        ('تحرير',lambda:owner.edit(copy.deepcopy(row))),
        ('عرض',lambda:owner.details(copy.deepcopy(row))),
        ('طي التفاصيل' if expanded else 'توسيع التفاصيل',lambda:owner.toggle_card(key,False))
    ]):
        ctk.CTkButton(primary,text=label,width=80,height=48,command=lambda f=action:owner.run(f)
            ).grid(row=0,column=2-column,sticky='ew',padx=3,pady=3)
    more=Disclosure(frame,'المزيد: تثبيت · نقل · حذف')
    more.pack(fill='x',padx=3,pady=2)
    for column in range(2):more.content.grid_columnconfigure(column,weight=1)
    for position,(label,action,enabled) in enumerate([
        ('إلغاء التثبيت' if key in owner.cards.pinned else 'تثبيت للعرض',lambda:owner.toggle_card(key,True),True),
        ('حذف المريض',lambda:owner.delete_patient(key),True),
        ('نقل لأعلى',lambda:owner.move(index,-1),movement_allowed and index>0),
        ('نقل لأسفل',lambda:owner.move(index,1),movement_allowed and index<total-1)
    ]):
        ctk.CTkButton(more.content,text=label,width=100,height=48,state='normal' if enabled else 'disabled',
            command=lambda f=action:owner.run(f)).grid(row=position//2,column=1-position%2,sticky='ew',padx=3,pady=3)
    if not movement_allowed:
        ctk.CTkLabel(more.content,text='للنقل اليدوي، امسح البحث والتصفية وألغِ التجميع والتثبيت.',
            anchor='e',justify='right',wraplength=width).grid(row=2,column=0,columnspan=2,sticky='ew',padx=8,pady=3)
    return frame
