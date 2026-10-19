"""Admin-only local clinical history; no remote audit or credential claims."""
from datetime import datetime
from desktop_patient_filters import normalize_search
from desktop_protocol import ZoneInfo

ACTIONS = {'patient_added':'إضافة مريض','patient_edited':'تعديل مريض','patient_deleted':'حذف مريض',
           'patient_restored':'استعادة مريض','task_completed':'إتمام مهمة','task_reopened':'إعادة فتح مهمة',
           'roster_changed':'تغيير أطباء المناوبة','order_changed':'تغيير الترتيب','draft_restored':'استعادة مسودة مشفرة',
           'download_undone':'تراجع عن تنزيل محلي'}


def activity_center(owner, patient_id=None, shift_id=None):
    import desktop_widgets as ctk
    from desktop_forms import button, combo, entry, guarded, modal
    owner.ready(False)
    def read():return owner.store.clinical_activity(patient_id,shift_id)
    owner.io(read)  # Authorization and identity filtering live below the dialog.
    window=modal(owner,'نشاط المريض — سجل محلي' if patient_id else 'مركز النشاط — سجل محلي','820x720')
    window.admin_only = True
    controls=ctk.CTkFrame(window);controls.pack(side='top',fill='x')
    query=entry(controls,'بحث باسم المريض أو الطبيب أو الحقول')
    action=combo(controls,'نوع العملية',dict({'':'كل العمليات'},**ACTIONS),'')
    scope=(lambda:'all') if shift_id else combo(controls,'المناوبة',{'current':'المناوبة المعروضة','all':'كل المناوبات المحلية'},'current')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    def wrap_rows(event):
        for child in area.winfo_children():
            if isinstance(child,ctk.CTkLabel):child.configure(wraplength=max(240,event.width-30))
    area.bind('<Configure>',wrap_rows,add='+')
    def show():
        rows=owner.io(read)  # Recheck identity/role on refresh.
        if not window.winfo_exists():return
        current=owner.store.state.get('draft')
        current_shift_id=current['meta']['shiftId'] if current else None
        needle=normalize_search(query.get())
        selected=[]
        for row in reversed(rows):
            if scope()=='current' and row['shiftId']!=current_shift_id:continue
            if action() and row['action']!=action():continue
            values=' '.join([row.get('patientName') or '',row.get('actorName') or '',row.get('taskDescription') or '',ACTIONS.get(row['action'],row['action'])]+row.get('fields',[]))
            if needle and needle not in normalize_search(values):continue
            selected.append(row)
        for child in area.winfo_children():child.destroy()
        ctk.CTkLabel(area,text='{} عملية محلية مطابقة؛ يعرض آخر 500 منها. لا يشمل هذا السجل تعديلات الأجهزة الأخرى.'.format(len(selected)),wraplength=680,justify='right').pack(fill='x',padx=8,pady=5)
        for row in selected[:500]:
            stamp=datetime.fromtimestamp(row['at']/1000,ZoneInfo(owner.client.zone)).strftime('%Y-%m-%d %H:%M')
            text='{} · {}\n{} · {}'.format(stamp,ACTIONS.get(row['action'],row['action']),row.get('patientName') or 'المناوبة',row.get('actorName') or 'هوية غير مسجلة')
            if row.get('taskDescription'):text+='\nالمهمة: '+row['taskDescription']
            if row.get('fields'):text+='\nالحقول: '+', '.join(row['fields'])
            ctk.CTkLabel(area,text=text,wraplength=680,justify='right',anchor='e').pack(fill='x',padx=8,pady=5)
    button(controls,'تطبيق / تحديث',guarded(show));button(controls,'إغلاق',window.destroy)
    show()
    if window.winfo_exists():owner.root.wait_window(window)
