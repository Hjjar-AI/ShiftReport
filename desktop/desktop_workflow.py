"""Local workflow status and read-only ward tasks; no automatic synchronization."""
from __future__ import annotations
import copy
import json
import time
from datetime import datetime
from desktop_protocol import fingerprint, ZoneInfo, current_date
from desktop_reports import report_content


def workflow_status(client, store, demo=False):
    if store is None or store.state.get('draft') is None:
        return 'اختر المشروع والمناوبة؛ لا توجد بيانات معروضة.', 'جلب البيانات'
    shift=store.read();key=shift['meta']['shiftId']
    baseline=store.state.get('baseline') or {}
    remote=next((s for s in baseline.get('bundle',[]) if s['meta']['shiftId']==key),None)
    dirty=remote is None or fingerprint(report_content(shift))!=fingerprint(report_content(remote))
    unknown=[row for _,row in store.receipts(status='UNKNOWN') if row[0]=='report' and (len(row)<=9 or not row[9] or row[9]==key)]
    downloaded=store.state.get('downloads',{}).get(key,{})
    stamp='غير معروف على هذا الجهاز'
    if downloaded.get('at'):
        stamp=datetime.fromtimestamp(downloaded['at']/1000,ZoneInfo(client.zone)).strftime('%Y-%m-%d %H:%M')
        if downloaded.get('snapshot')!=shift['meta'].get('snapshotId',''):stamp+=' (لنسخة سابقة)'
    label='DEMO — بيانات في الذاكرة' if demo else ('تعديلات غير منشورة' if dirty else 'مطابق لآخر نسخة منشورة معروفة لهذا الجهاز')
    text='مناوبة {} · {}\nآخر تنزيل: {} · تسليم غير مؤكد: {}'.format(shift['meta']['shiftDate'],label,stamp,len(unknown))
    return text, 'عرض تجريبي محلي' if demo else 'سجل التسليم' if unknown else 'مراجعة ونشر' if dirty and shift['meta']['shiftDate']==current_date(client.zone) else 'جلب البيانات'


def pending_tasks(shift, supervisor=None, resident=None, now=None):
    now=int(time.time()*1000) if now is None else now
    rows=[]
    for patient in shift['patients']:
        if supervisor and patient.get('responsibleSupervisorId')!=supervisor:continue
        if resident and patient.get('responsibleResidentId')!=resident:continue
        for task in json.loads(patient.get('tasksJson') or '[]'):
            if not task.get('done'):rows.append((patient,task))
    return sorted(rows,key=lambda pair:(0 if pair[1].get('dueAtEpochMillis') and pair[1]['dueAtEpochMillis']<=now else 1,
                  pair[1].get('dueAtEpochMillis') or float('inf'),-{'LOW':0,'NORMAL':1,'HIGH':2}.get(pair[1].get('priority','NORMAL'),1)))


def task_board(owner):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, modal
    from desktop_model import doctor_label
    owner.ready(False)
    owner.store.read();doctors=copy.deepcopy(owner.client.doctors)
    window=modal(owner,'مهام الجناح — المهام المعلقة','820x720')
    controls=ctk.CTkFrame(window);controls.pack(fill='x')
    supervisor=combo(controls,'مشرف المريض',dict({'':'جميع المشرفين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='SUPERVISOR'}),'')
    resident=combo(controls,'مقيم المريض',dict({'':'جميع المقيمين'},**{key:doctor_label(doctors,key) for key,d in doctors.items() if d['clinicalRole']=='RESIDENT'}),'')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    def show():
        for child in area.winfo_children():child.destroy()
        current=owner.store.read()
        now=int(time.time()*1000)
        rows=pending_tasks(current,supervisor(),resident(),now)
        ctk.CTkLabel(area,text='{} مهمة معلقة · الأقدم موعداً أولاً؛ الإتمام من ملف المريض بعد المراجعة والحفظ.'.format(len(rows)),wraplength=680,justify='right').pack(fill='x',padx=8,pady=5)
        for patient,task in rows:
            overdue=task.get('dueAtEpochMillis') and task['dueAtEpochMillis']<=now
            due=datetime.fromtimestamp(task['dueAtEpochMillis']/1000,ZoneInfo(owner.client.zone)).strftime('%Y-%m-%d %H:%M') if task.get('dueAtEpochMillis') else 'دون موعد'
            owner_name=doctors.get(task.get('ownerDoctorId'),{}).get('name') or task.get('ownerName') or 'غير معيّنة'
            label='{} — {}\n{} · {} · {}'.format(patient['name'],task['description'],'متأخرة' if overdue else 'معلقة',due,owner_name)
            def open_patient(key=patient['externalId']):
                # Resolve current identity rather than opening an old copied patient snapshot.
                current=owner.store.read()
                row=next((p for p in current['patients'] if p['externalId']==key),None)
                if row is None:show();return
                window.destroy()
                if current['meta']['shiftDate']==current_date(owner.client.zone):owner.edit(row)
                else:owner.details(row)
            frame=ctk.CTkFrame(area);frame.pack(fill='x',padx=6,pady=4)
            ctk.CTkLabel(frame,text=label,wraplength=650,justify='right',anchor='e').pack(fill='x',padx=8,pady=5)
            button(frame,'فتح ملف المريض',open_patient)
    button(controls,'تطبيق التصفية / تحديث',show);button(controls,'إغلاق',window.destroy)
    show();owner.root.wait_window(window)
