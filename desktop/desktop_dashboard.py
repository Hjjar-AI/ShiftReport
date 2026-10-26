"""Read-only handover summary using the ward's existing visibility rules."""
import time
from desktop_patient_filters import matches_patient
from desktop_tasks import pending_count, read_tasks
from desktop_clinical import local_identity
from desktop_protocol import require, current_date


# Each action resets all three filter dimensions, so an old filter cannot hide matches.
FILTERS = {
    'all': ('إجمالي المرضى', 'الكل', 'كل المهام', False),
    'mine': ('مرضاي ومهامي', 'الكل', 'كل المهام', True),
    'urgent': ('عاجل أو أولوية', 'عاجل', 'كل المهام', False),
    'badges': ('مرضى لديهم شارات', 'شارات', 'كل المهام', False),
    'unassigned': ('بحاجة إلى تعيين طبيب', 'غير مسند', 'كل المهام', False),
    'new': ('دخول اليوم', 'دخول اليوم', 'كل المهام', False),
    'pending': ('المهام المعلقة', 'الكل', 'مهام معلقة', False),
}


def summary(patients, actor=None, zone='Asia/Damascus', now=None):
    now = int(time.time()*1000) if now is None else now
    pending = pending_count([task for patient in patients for task in read_tasks(patient)])
    result = {}
    for key, (_, clinical, tasks, mine) in FILTERS.items():
        result[key] = sum(matches_patient(patient, clinical=clinical, tasks=tasks,
                                         mine=mine, actor=actor, zone=zone, now=now)
                          for patient in patients)
    # Task tiles count records; opening a tile shows the patients containing those tasks.
    result.update(pending=pending)
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
    shift_label = ctk.CTkLabel(area, text='', anchor='e', justify='right', wraplength=600)
    shift_label.pack(fill='x', padx=10, pady=6)
    section_heading(area, 'يحتاج انتباهك')
    attention = ctk.CTkFrame(area, fg_color='transparent')
    attention.pack(fill='x')
    attention.grid_columnconfigure(0, weight=1)
    clear_label = ctk.CTkLabel(attention, text='لا توجد حالات عاجلة', anchor='e')
    clear_label.grid(row=0, column=0, sticky='ew', padx=10, pady=4)
    tiles = {}
    for index, key in enumerate(('urgent',)):
        tile = ctk.CTkButton(attention, text='', height=48, command=guarded(lambda k=key: select(k)))
        tile.grid(row=index+1, column=0, sticky='ew', padx=10, pady=4)
        tiles[key] = tile
    section_heading(area, 'ملخص المناوبة')
    for key in ('all', 'mine', 'pending', 'badges', 'unassigned', 'new'):
        tiles[key] = button(area, '', guarded(lambda k=key: select(k)))
    section_heading(area, 'جاهزية التقرير')
    readiness = ctk.CTkLabel(area, text='', anchor='e', justify='right', wraplength=600)
    readiness.pack(fill='x', padx=10, pady=8)
    section_heading(area, 'حفظ البيانات ونشرها')
    workflow = ctk.CTkLabel(area, text='', anchor='e', justify='right', wraplength=600)
    workflow.pack(fill='x', padx=10, pady=6)
    status_text = ['']
    button(area, 'تفاصيل حفظ البيانات ونشرها', lambda: owner.text_dialog('تفاصيل البيانات', status_text[0]))
    previous = {}

    def update(widget, text):
        if previous.get(widget) != text:
            widget.configure(text=text)
            previous[widget] = text

    def show():
        shift = current()
        actor = local_identity(owner.store, owner.client)
        values = summary(shift['patients'], (actor or {}).get('uid'), owner.client.zone)
        update(shift_label, 'مناوبة {} · الملخص يشمل جميع المرضى قبل التصفية'.format(current_date(owner.client.zone)))
        for key, tile in tiles.items():
            update(tile, '{}: {}'.format(FILTERS[key][0], values[key]))
            if key in {'urgent'}:
                if values[key]:tile.grid()
                else:tile.grid_remove()
        if not values['urgent']:clear_label.grid()
        else:clear_label.grid_remove()
        update(readiness, 'أعداد المهام تحسب المهام نفسها؛ الضغط يعرض المرضى المعنيين.\n' + readiness_notes(shift['patients']))
        status, _ = workflow_status(owner.client, owner.store, owner.demo)
        status_text[0] = status
        update(workflow, status.split("\n", 1)[0])
    button(controls, 'تحديث الملخص', guarded(show))
    button(controls, 'إغلاق', window.destroy)
    show()
    owner.root.wait_window(window)
