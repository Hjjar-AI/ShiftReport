"""Local workflow status and read-only ward tasks; no automatic synchronization."""
from __future__ import annotations
import copy
from enum import Enum
from datetime import datetime
from desktop_protocol import ZoneInfo, require, current_date
from desktop_tasks import read_tasks, pending, task_text


class WorkflowAction(Enum):
    DOWNLOAD = 'جلب البيانات'
    REVIEW_PUBLICATION = 'رفع للشبكة وإرسال تقرير'
    DELIVERY_HISTORY = 'سجل التسليم'
    DEMO = 'عرض تجريبي محلي'


def workflow_status(client, store, demo=False):
    if store is None or store.state.get('draft') is None:
        return 'اختر المشروع والمناوبة؛ لا توجد بيانات معروضة.', WorkflowAction.DOWNLOAD
    shift=store.read();key=shift['meta']['shiftId']
    baseline=store.state.get('baseline') or {}
    remote=next((s for s in baseline.get('bundle',[]) if s['meta']['shiftId']==key),None)
    dirty=False if demo else store.has_pending_changes()
    unknown=[row for _,row in store.receipts(status='UNKNOWN') if row[0]!='report' or len(row)<=9 or not row[9] or row[9]==key]
    downloaded=store.state.get('downloads',{}).get(key,{})
    stamp='غير معروف على هذا الجهاز'
    if downloaded.get('at'):
        stamp=datetime.fromtimestamp(downloaded['at']/1000,ZoneInfo(client.zone)).strftime('%Y-%m-%d %H:%M')
        if downloaded.get('snapshot')!=shift['meta'].get('snapshotId',''):stamp+=' (لنسخة سابقة)'
    label='DEMO — بيانات في الذاكرة' if demo else ('محفوظ على الجهاز · حالة النشر غير معروفة' if remote is None else 'تعديلات لم تُنشر' if dirty else 'مطابق لآخر نسخة معروفة على هذا الجهاز')
    entry=next((item for item in baseline.get('state',{}).get('publicationJournal',[])
                if remote and item.get('snapshotId')==remote['meta'].get('snapshotId')),None)
    published=datetime.fromtimestamp(entry['at']/1000,ZoneInfo(client.zone)).strftime('%Y-%m-%d %H:%M') if entry and entry.get('at') else 'غير معروف'
    text='مناوبة {} · {}\nآخر نشر معروف: {} · آخر جلب: {}'.format(current_date(client.zone),label,published,stamp)
    if unknown:text+=' · {} عمليات غير محسومة'.format(len(unknown))
    return text, WorkflowAction.DEMO if demo else WorkflowAction.DELIVERY_HISTORY if unknown else WorkflowAction.REVIEW_PUBLICATION if dirty else WorkflowAction.DOWNLOAD


def pending_tasks(shift, supervisor=None, resident=None):
    rows=[]
    for patient in shift['patients']:
        if supervisor and patient.get('responsibleSupervisorId')!=supervisor:continue
        if resident and patient.get('responsibleResidentId')!=resident:continue
        rows.extend((patient,task) for task in pending(read_tasks(patient)))
    return rows


def set_task_done(owner,project,shift_id,patient_id,task_id,done,expected):
    """Resolve the current record; completion never saves an old patient snapshot."""
    from desktop_protocol import fingerprint, packed
    shift=owner.editable()
    require(owner.client.project_id==project and shift['meta']['shiftId']==shift_id,
            'تغير المشروع أو المناوبة؛ أعد فتح المهام')
    patient=next((row for row in shift['patients'] if row['externalId']==patient_id),None)
    require(patient is not None,'المريض لم يعد في القائمة')
    tasks=read_tasks(patient)
    task=next((row for row in tasks if row['id']==task_id),None)
    require(task is not None and bool(task.get('done'))==expected,'تغيرت المهمة؛ حدّث القائمة قبل تعديلها')
    before=fingerprint(shift)
    task.update(done=done,completedByDoctorId=None,completedByName=None,completedAtEpochMillis=None)
    patient['tasksJson']=packed(tasks)
    owner.save_draft(shift,before)


def task_board(owner):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, modal, guarded
    from desktop_model import doctor_label, patient_name
    owner.ready(False)
    source=owner.store.read();doctors=copy.deepcopy(owner.client.doctors)
    project,shift_id=owner.client.project_id,source['meta']['shiftId']
    window=modal(owner,'مهام الجناح','820x720')
    controls=ctk.CTkFrame(window);controls.pack(fill='x')
    supervisor=combo(controls,'مشرف المريض',dict({'':'جميع المشرفين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='SUPERVISOR'}),'')
    resident=combo(controls,'مقيم المريض',dict({'':'جميع المقيمين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='RESIDENT'}),'')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    settle_timers=[]
    def cancel_timers(event=None):
        if event is not None and event.widget is not window:return
        for key in settle_timers:window.after_cancel(key)
        settle_timers.clear()
    window.bind('<Destroy>',cancel_timers,add='+')
    def current_shift():
        owner.ready(False)
        shift=owner.store.read()
        require(owner.client.project_id==project and shift['meta']['shiftId']==shift_id,
                'تغير المشروع أو المناوبة؛ أعد فتح مهام الجناح')
        return shift
    def show():
        current=current_shift()
        cancel_timers()
        for child in area.winfo_children():child.destroy()
        rows=pending_tasks(current,supervisor(),resident())
        ctk.CTkLabel(area,text='{} مهمة معلقة'.format(len(rows)),wraplength=680,justify='right').pack(fill='x',padx=8,pady=5)
        groups={}
        for patient,task in rows:
            def open_patient(key=patient['externalId']):
                # Resolve current identity rather than opening an old copied patient snapshot.
                current=current_shift()
                row=next((p for p in current['patients'] if p['externalId']==key),None)
                if row is None:show();return
                window.destroy()
                owner.edit(row)
            key=patient['externalId']
            if key not in groups:
                groups[key]=ctk.CTkFrame(area);groups[key].pack(fill='x',padx=6,pady=4)
                ctk.CTkLabel(groups[key],text=patient_name(patient),wraplength=650,justify='right',anchor='e',font=('Arial',16,'bold')).pack(fill='x',padx=8,pady=5)
                button(groups[key],'تحرير المريض',guarded(open_patient))
            frame=ctk.CTkFrame(groups[key],fg_color='transparent');frame.pack(fill='x',padx=4,pady=3)
            ctk.CTkLabel(frame,text=task['description'],wraplength=650,justify='right',anchor='e').pack(fill='x',padx=8,pady=3)
            action=[None]
            def complete(patient_id=key,task_id=task['id'],control=action):
                set_task_done(owner,project,shift_id,patient_id,task_id,True,False)
                def undo():
                    set_task_done(owner,project,shift_id,patient_id,task_id,False,True)
                    if window.winfo_exists():show()
                control[0].configure(text='تراجع عن الإتمام',command=guarded(undo))
                def settle():
                    if window.winfo_exists() and not owner.locked:show()
                settle_timers.append(window.after(2000,guarded(settle)))
            action[0]=button(frame,'إتمام المهمة',guarded(complete))
    button(controls,'تطبيق التصفية / تحديث',guarded(show));button(controls,'إغلاق',window.destroy)
    show();owner.root.wait_window(window)
