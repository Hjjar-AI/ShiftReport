"""Read-only handover summary using the ward's existing visibility rules."""
import time
from desktop_patient_filters import matches_patient
from desktop_tasks import counts, read_tasks
from desktop_clinical import local_identity
from desktop_protocol import require


# Each action resets all three filter dimensions, so an old filter cannot hide matches.
FILTERS = {
    'all': ('إجمالي المرضى', 'الكل', 'كل المهام', False),
    'mine': ('مرضاي ومهامي', 'الكل', 'كل المهام', True),
    'urgent': ('عاجل أو أولوية', 'عاجل', 'كل المهام', False),
    'badges': ('مرضى لديهم شارات', 'شارات', 'كل المهام', False),
    'unassigned': ('بحاجة إلى تعيين طبيب', 'غير مسند', 'كل المهام', False),
    'new': ('دخول اليوم', 'دخول اليوم', 'كل المهام', False),
    'pending': ('المهام المعلقة', 'الكل', 'مهام معلقة', False),
    'overdue': ('المهام المتأخرة (ضمن المعلقة)', 'الكل', 'مهام متأخرة', False),
    'task_unassigned': ('مهام معلقة غير معيّنة', 'الكل', 'مهام غير مسندة', False),
}


def summary(patients, actor=None, zone='Asia/Damascus', now=None):
    now = int(time.time()*1000) if now is None else now
    totals = counts([task for patient in patients for task in read_tasks(patient)], now)
    result = {}
    for key, (_, clinical, tasks, mine) in FILTERS.items():
        result[key] = sum(matches_patient(patient, clinical=clinical, tasks=tasks,
                                         mine=mine, actor=actor, zone=zone, now=now)
                          for patient in patients)
    # Task tiles count records; opening a tile shows the patients containing those tasks.
    result.update(pending=totals.pending, overdue=totals.overdue, task_unassigned=totals.unassigned)
    return result


def apply_filter(owner, key):
    _, clinical, tasks, mine = FILTERS[key]
    owner.filter.set(clinical)
    owner.task_filter.set(tasks)
    owner.owner_filter.set('مرضاي ومهامي' if mine else 'كل المرضى')
    owner.search.set('')
    owner.render()


def handover_dashboard(owner):
    import desktop_widgets as ctk
    from desktop_forms import button, guarded, modal, section_heading
    from desktop_reports import readiness_notes
    from desktop_workflow import workflow_status
    owner.ready(False)
    source = owner.store.read()
    project, shift_id = owner.client.project_id, source['meta']['shiftId']
    window = modal(owner, 'لوحة تسليم المناوبة', '740x700')
    controls = ctk.CTkFrame(window); controls.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True)
    def current():
        owner.ready(False)
        shift = owner.store.read()
        require(owner.client.project_id == project and shift['meta']['shiftId'] == shift_id,
                'تغير المشروع أو المناوبة؛ أعد فتح لوحة التسليم')
        return shift
    def select(key):
        current()
        apply_filter(owner, key)
        window.destroy()
    def show():
        shift = current()
        actor = local_identity(owner.store, owner.client)
        values = summary(shift['patients'], (actor or {}).get('uid'), owner.client.zone)
        for child in area.winfo_children(): child.destroy()
        ctk.CTkLabel(area, text='مناوبة {} · الملخص يشمل جميع المرضى قبل التصفية'.format(shift['meta']['shiftDate']),
                     anchor='e', justify='right', wraplength=600).pack(fill='x', padx=10, pady=6)
        section_heading(area,'يحتاج انتباهك')
        if not values['urgent'] and not values['overdue']:
            ctk.CTkLabel(area,text='لا توجد حالات عاجلة أو مهام متأخرة',anchor='e').pack(fill='x',padx=10,pady=4)
        for key in ('urgent', 'overdue', 'all', 'mine', 'pending', 'badges', 'unassigned', 'task_unassigned', 'new'):
            if key in {'urgent','overdue'} and not values[key]:continue
            if key=='all':section_heading(area,'ملخص المناوبة')
            button(area, '{}: {}'.format(FILTERS[key][0], values[key]), guarded(lambda k=key: select(k)))
        section_heading(area,'جاهزية التقرير')
        ctk.CTkLabel(area, text='أعداد المهام تحسب المهام نفسها؛ الضغط يعرض المرضى المعنيين.\n' + readiness_notes(shift['patients']),
                     anchor='e', justify='right', wraplength=600).pack(fill='x', padx=10, pady=8)
        section_heading(area,'حفظ البيانات ونشرها')
        status,_=workflow_status(owner.client,owner.store,owner.demo)
        ctk.CTkLabel(area,text=status.split("\n",1)[0],anchor='e',justify='right',wraplength=600).pack(fill='x',padx=10,pady=6)
        button(area,'تفاصيل حفظ البيانات ونشرها',lambda:owner.text_dialog('تفاصيل البيانات',status))
    button(controls, 'تحديث الملخص', guarded(show))
    button(controls, 'إغلاق', window.destroy)
    timer = [None]
    def tick():
        if not window.winfo_exists(): return
        if not owner.locked and not owner.io_depth:
            guarded(show)()
        timer[0] = window.after(60_000, tick)
    def destroyed(event):
        if event.widget is window and timer[0] is not None:
            window.after_cancel(timer[0])
    window.bind('<Destroy>', destroyed, add='+')
    show()
    timer[0] = window.after(60_000, tick)
    owner.root.wait_window(window)
