"""Patient list; the Window remains the single workspace/state owner."""
from __future__ import annotations
import copy
import time
import desktop_widgets as ctk
from desktop_protocol import is_admin, current_date
from desktop_model import pending_task_count
from desktop_sort import GROUPS, groups, sort_description, manual_move_allowed
from desktop_workflow import WorkflowAction, workflow_status
from desktop_layout import filter_summary
from desktop_patient_card import patient_card
from desktop_clinical import local_identity
from desktop_patient_filters import CLINICAL_FILTERS, TASK_FILTERS, OWNER_FILTERS, matches_patient


def render(owner):
    if not hasattr(owner,'patients') or owner.locked:return
    if owner.creation_button is not None:
        if owner.settings.get('CONNECTION_MODE')=='raw':owner.creation_button.pack(fill='x',padx=8,pady=4)
        else:owner.creation_button.pack_forget()
        owner.creation_button.configure(state='disabled' if owner.client or owner.busy else 'normal')
    if not owner.demo:
        connected = owner.client is not None and owner.store is not None
        authenticated = connected and owner.client._actor is not None and time.time()<owner.client._expires
        has_shift = connected and owner.store.state.get('draft') is not None
        owner.login_button.configure(state='normal' if connected and not owner.busy else 'disabled')
        owner.fetch_button.configure(state='normal' if authenticated and not owner.busy else 'disabled')
        owner.send_report_button.configure(state='normal' if authenticated and has_shift and not owner.busy else 'disabled')
    actor=owner.client.doctors.get(owner.client._actor) if owner.client else None
    if actor and is_admin(actor) and time.time()<owner.client._expires:
        if not owner.demo and not owner.admin_sidebar.winfo_manager():owner.admin_sidebar.pack(fill='x',padx=3,pady=3,before=owner.help_sidebar)
        owner.admin_button.pack(fill='x',padx=8,pady=4)
        owner.admin_button.configure(state='disabled' if owner.busy else 'normal')
        owner.join_export_button.pack(fill='x',padx=8,pady=4)
        owner.join_export_button.configure(state='disabled' if owner.busy else 'normal')
        if not owner.demo:
            owner.assignments_button.pack(fill='x',padx=8,pady=4,after=owner.report_publish_button)
            owner.assignments_button.configure(state='disabled' if owner.busy else 'normal')
    else:
        owner.admin_sidebar.pack_forget()
        owner.team_window.withdraw()
        owner.join_export_button.pack_forget()
        owner.admin_button.pack_forget()
        owner.assignments_button.pack_forget()
    from desktop_doctors import permanent
    if actor and permanent(actor) and time.time()<owner.client._expires and not owner.demo:
        owner.empty_start_button.pack(fill='x',padx=8,pady=4)
        owner.empty_start_button.configure(state='disabled' if owner.busy else 'normal')
    else:owner.empty_start_button.pack_forget()
    active=filter_summary(owner)
    grouping=owner.grouping.get()
    view_summary=' · '.join(active + ([grouping] if grouping!=GROUPS['NONE'] else []))
    owner.view_controls.refresh('خيارات العرض' + (' ({})'.format(len(active)) if active else ''))
    owner.view_summary.configure(text=view_summary)
    owner.reset_view_button.configure(state='normal' if active and not owner.busy else 'disabled')
    summary,action=workflow_status(owner.client,owner.store,owner.demo)
    owner.workflow.configure(text=summary.split('\n',1)[0])
    owner.workflow_details.configure(text=summary)
    if owner.show_workflow_details:owner.workflow_details.grid(row=2,column=0,columnspan=3,sticky='ew',padx=8,pady=4)
    else:owner.workflow_details.grid_remove()
    if action is WorkflowAction.DELIVERY_HISTORY and not owner.demo:
        owner.operation_notice_text.configure(text='توجد عملية غير محسومة. افحص تليجرام وسجل العمليات قبل إعادة النشر أو الإرسال.')
        if not owner.operation_notice.winfo_manager():owner.operation_notice.pack(fill='x',padx=5,pady=4,before=owner.ward_toolbar)
    else:owner.operation_notice.pack_forget()
    next_label = action.value
    if not owner.demo:
        if not connected:
            next_label='اتصال بالمشروع'
            owner.workflow.configure(text='ابدأ هنا: اتصل بالمشروع، ثم اختر اسمك وسجّل الدخول.')
        elif not authenticated:
            next_label='تسجيل الدخول'
            owner.workflow.configure(text='اختر اسمك من قائمة الطبيب الحالي، ثم سجّل الدخول. بيانات الجهاز تبقى محفوظة.')
    owner.workflow_action.configure(text=next_label, state='disabled' if owner.busy or owner.demo else 'normal')
    if action is WorkflowAction.DELIVERY_HISTORY and not owner.demo:owner.workflow_action.grid_remove()
    else:owner.workflow_action.grid(row=0,column=0,sticky='n',padx=8,pady=3)
    if owner.store is None or owner.store.state['draft'] is None:
        owner.update_patient_list([],False,None)
        owner.sort_status.configure(text='التجميع للعرض فقط؛ الترتيب المحفوظ يستخدم في التقارير وCSV.')
        owner.metrics.configure(text='اجلب بيانات الجناح المنشورة للبدء.');return
    draft=owner.store.read(); rows=draft['patients']; pending=pending_task_count(rows)
    owner.cards.select((owner.client.project_id,draft['meta']['shiftId']),{row['externalId'] for row in rows})
    owner.sort_status.configure(text=sort_description(draft))
    display=owner.patient_display(draft)
    roster_names=[owner.client.doctors.get(key,{}).get('name','طبيب محفوظ') for key in draft['meta'].get('doctors','').split(',') if key]
    roster=' / '.join(roster_names[:2])+(' · +'+str(len(roster_names)-2) if len(roster_names)>2 else '')
    owner.metrics.configure(text='{} · {} من {} مريض · مهام الجناح المعلقة {}\nأطباء المناوبة: {}'.format(current_date(owner.client.zone),len(display),len(rows),pending,roster or 'لم تحدد'))
    movement_allowed=owner.manual_movement_allowed()
    owner.update_patient_list(display,movement_allowed,draft)


def update_patient_list(owner,display,movement_allowed,draft):
    """Reuse unchanged cards; list state never changes clinical ordering."""
    scope=(owner.client.project_id,draft['meta']['shiftId']) if draft else None
    if getattr(owner,'list_scope',None)!=scope:
        for child in owner.patients.winfo_children():child.destroy()
        owner.patient_widgets={};owner.list_order=[];owner.list_scope=scope
        owner.list_filtered=False;owner.unfiltered_scroll=0
    if not hasattr(owner,'patient_widgets'):owner.patient_widgets={};owner.list_order=[]
    canvas=owner.patients._parent_canvas
    scroll=canvas.yview()[0]
    filtered=bool(filter_summary(owner))
    if filtered and not getattr(owner,'list_filtered',False):owner.unfiltered_scroll=scroll
    if not filtered and getattr(owner,'list_filtered',False):scroll=getattr(owner,'unfiltered_scroll',0)
    owner.list_filtered=filtered
    entries=[];last_group=None;current_group=None
    for heading,index,row in display:
        if heading is not None:current_group=heading
        if current_group and current_group!=last_group:
            entries.append((('group',current_group),current_group,None))
            last_group=current_group
        key=row['externalId']
        # A minute refresh keeps deadline/task summaries current.
        signature=(row,index,len(draft['patients']),key in owner.cards.expanded,
                   key in owner.cards.pinned,owner.client.doctors,owner.client.zone,owner.card_density.get(),int(time.time()//60))
        entries.append((('patient',key),signature,(row,index)))
    if not entries:
        text=('لا توجد نتائج مطابقة. امسح التصفية أو غيّر البحث.' if draft and draft['patients']
              else 'لا يوجد مرضى. أضف مريضاً للبدء.' if draft else 'افتح المشروع للبدء.')
        entries.append((('empty',),text,None))
    order=[key for key,_,_ in entries]
    changed=order!=owner.list_order
    for key in list(owner.patient_widgets):
        if key not in order:
            owner.patient_widgets.pop(key)[1].destroy()
    for key,signature,payload in entries:
        previous=owner.patient_widgets.get(key)
        if previous and previous[0]==signature and previous[1].winfo_exists():
            if payload:previous[1].refresh_movement(movement_allowed)
            continue
        if previous:previous[1].destroy()
        if payload:
            row,index=payload
            widget=patient_card(owner,owner.patients,row,index,len(draft['patients']),movement_allowed)
        else:
            widget=ctk.CTkLabel(owner.patients,text=signature,anchor='e',justify='right',
                wraplength=max(240,owner.content.winfo_width()-40),font=('Arial',18,'bold') if key[0]=='group' else ('Arial',14))
        owner.patient_widgets[key]=(copy.deepcopy(signature),widget)
        changed=True
    if changed:
        for _,widget in owner.patient_widgets.values():widget.pack_forget()
        for key in order:owner.patient_widgets[key][1].pack(fill='x',padx=3,pady=2 if owner.card_density.get()=='مضغوطة' else 4)
        owner.list_order=order
        def restore_scroll():
            if owner.patients.winfo_exists() and owner.list_scope==scope:
                canvas.yview_moveto(scroll)
        owner.root.after_idle(restore_scroll)


def reset_patient_filters(owner):
    owner.filter.set(CLINICAL_FILTERS[0])
    owner.owner_filter.set(OWNER_FILTERS[0])
    owner.task_filter.set(TASK_FILTERS[0])
    owner.search.set('')
    owner.render()


def patient_display(owner,draft):
    mode_key=next(key for key,label in GROUPS.items() if label==owner.grouping.get())
    actor=local_identity(owner.store,owner.client)
    display=[]
    for heading,items in groups(draft['patients'],mode_key,owner.client.doctors):
        for index,row in sorted(items,key=lambda pair:pair[1]['externalId'] not in owner.cards.pinned):
            if matches_patient(row,owner.search.get(),owner.filter.get(),owner.task_filter.get(),
                               owner.owner_filter.get()=='مرضاي ومهامي',(actor or {}).get('uid'),owner.client.zone):
                display.append((heading,index,row))
    return display


def toggle_card(owner,key,pin=False):
    owner.ready(False)
    owner.cards.toggle(key,pin)
    owner.render()


def expand_cards(owner,expand):
    owner.ready(False)
    draft=owner.store.read()
    owner.cards.expand_all([row['externalId'] for row in draft['patients']],expand)
    owner.render()


def manual_movement_allowed(owner):
    return (not owner.cards.pinned and owner.owner_filter.get()==OWNER_FILTERS[0] and owner.task_filter.get()==TASK_FILTERS[0] and
            manual_move_allowed(owner.grouping.get(),owner.filter.get(),owner.search.get()))

