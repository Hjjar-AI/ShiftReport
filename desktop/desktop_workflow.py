"""Local workflow status and read-only ward tasks; no automatic synchronization."""
from __future__ import annotations
import copy
import time
from enum import Enum
from datetime import datetime
from desktop_protocol import fingerprint, ZoneInfo, current_date, require
from desktop_reports import report_content
from desktop_tasks import read_tasks, pending, display_key, task_text


class WorkflowAction(Enum):
    DOWNLOAD = 'جلب البيانات'
    REVIEW_PUBLICATION = 'مراجعة ونشر'
    DELIVERY_HISTORY = 'سجل التسليم'
    DEMO = 'عرض تجريبي محلي'


def workflow_status(client, store, demo=False):
    if store is None or store.state.get('draft') is None:
        return 'اختر المشروع والمناوبة؛ لا توجد بيانات معروضة.', WorkflowAction.DOWNLOAD
    shift=store.read();key=shift['meta']['shiftId']
    baseline=store.state.get('baseline') or {}
    remote=next((s for s in baseline.get('bundle',[]) if s['meta']['shiftId']==key),None)
    dirty=remote is None or fingerprint(report_content(shift))!=fingerprint(report_content(remote))
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
    text='مناوبة {} · {}\nآخر نشر معروف: {} · آخر جلب: {}'.format(shift['meta']['shiftDate'],label,published,stamp)
    if unknown:text+=' · {} عمليات غير محسومة'.format(len(unknown))
    return text, WorkflowAction.DEMO if demo else WorkflowAction.DELIVERY_HISTORY if unknown else WorkflowAction.REVIEW_PUBLICATION if dirty and shift['meta']['shiftDate']==current_date(client.zone) else WorkflowAction.DOWNLOAD


def pending_tasks(shift, supervisor=None, resident=None, now=None):
    now=int(time.time()*1000) if now is None else now
    rows=[]
    for patient in shift['patients']:
        if supervisor and patient.get('responsibleSupervisorId')!=supervisor:continue
        if resident and patient.get('responsibleResidentId')!=resident:continue
        rows.extend((patient,task) for task in pending(read_tasks(patient)))
    return sorted(rows,key=lambda pair:display_key(pair[1],now))


def task_board(owner):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, modal, guarded
    from desktop_model import doctor_label
    owner.ready(False)
    source=owner.store.read();doctors=copy.deepcopy(owner.client.doctors)
    project,shift_id=owner.client.project_id,source['meta']['shiftId']
    window=modal(owner,'مهام الجناح — المهام المعلقة','820x720')
    controls=ctk.CTkFrame(window);controls.pack(fill='x')
    supervisor=combo(controls,'مشرف المريض',dict({'':'جميع المشرفين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='SUPERVISOR'}),'')
    resident=combo(controls,'مقيم المريض',dict({'':'جميع المقيمين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='RESIDENT'}),'')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    def current_shift():
        owner.ready(False)
        shift=owner.store.read()
        require(owner.client.project_id==project and shift['meta']['shiftId']==shift_id,
                'تغير المشروع أو المناوبة؛ أعد فتح مهام الجناح')
        return shift
    def show():
        current=current_shift()
        for child in area.winfo_children():child.destroy()
        now=int(time.time()*1000)
        rows=pending_tasks(current,supervisor(),resident(),now)
        ctk.CTkLabel(area,text='{} مهمة معلقة · الإتمام من ملف المريض بعد المراجعة والحفظ.'.format(len(rows)),wraplength=680,justify='right').pack(fill='x',padx=8,pady=5)
        for patient,task in rows:
            label=patient['name']+'\n'+task_text(task,owner.client.doctors,owner.client.zone,now)
            def open_patient(key=patient['externalId']):
                # Resolve current identity rather than opening an old copied patient snapshot.
                current=current_shift()
                row=next((p for p in current['patients'] if p['externalId']==key),None)
                if row is None:show();return
                window.destroy()
                if current['meta']['shiftDate']==current_date(owner.client.zone):owner.edit(row)
                else:owner.details(row)
            frame=ctk.CTkFrame(area);frame.pack(fill='x',padx=6,pady=4)
            ctk.CTkLabel(frame,text=label,wraplength=650,justify='right',anchor='e').pack(fill='x',padx=8,pady=5)
            button(frame,'فتح ملف المريض',guarded(open_patient))
    button(controls,'تطبيق التصفية / تحديث',guarded(show));button(controls,'إغلاق',window.destroy)
    show();owner.root.wait_window(window)
