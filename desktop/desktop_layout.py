"""Compact ward layout; presentation controls never mutate clinical ordering."""
import tkinter as tk
import desktop_widgets as ctk
from desktop_input import ArabicEntry
from desktop_patient_filters import CLINICAL_FILTERS, OWNER_FILTERS, TASK_FILTERS
from desktop_sort import GROUPS, sorting_dialog
from desktop_dashboard import handover_dashboard
from desktop_workflow import task_board


class Disclosure(ctk.CTkFrame):
    def __init__(self, parent, title, expanded=False, scroll_height=None):
        super().__init__(parent)
        self.expanded = expanded
        self.title = title
        self.toggle_button = ctk.CTkButton(self, text='', height=48, anchor='e', command=self.toggle)
        self.toggle_button.pack(fill='x', padx=4, pady=2)
        self.content = (ctk.CTkScrollableFrame(self, height=scroll_height, fg_color='transparent')
                        if scroll_height else ctk.CTkFrame(self, fg_color='transparent'))
        self.refresh()

    def refresh(self, title=None):
        if title is not None:self.title = title
        self.toggle_button.configure(text=('▾ ' if self.expanded else '▸ ') + self.title)
        if self.expanded:self.content.pack(fill='x', padx=4, pady=2)
        else:self.content.pack_forget()

    def toggle(self):
        self.expanded = not self.expanded
        self.refresh()


def build_ward_controls(window):
    parent = window.content
    status = ctk.CTkFrame(parent)
    status.pack(fill='x', padx=5, pady=4)
    window.workflow = ctk.CTkLabel(status, text='', anchor='e', justify='right', wraplength=650)
    window.workflow.grid(row=0, column=1, sticky='ew', padx=8, pady=3)
    window.workflow_action = ctk.CTkButton(status, text='جلب البيانات', height=48,
        command=lambda:window.run(window.workflow_next))
    window.workflow_action.grid(row=0, column=0, sticky='n', padx=8, pady=3)
    status.grid_columnconfigure(1, weight=1)
    window.metrics = ctk.CTkLabel(status, text='', anchor='e', justify='right', wraplength=650)
    window.metrics.grid(row=1, column=0, columnspan=2, sticky='ew', padx=8, pady=3)

    actions = ctk.CTkFrame(parent)
    actions.pack(fill='x', padx=5, pady=3)
    for column in range(2):actions.grid_columnconfigure(column, weight=1)
    for index, (label, action) in enumerate([
        ('إضافة مريض', window.add_patient), ('مهام الجناح', lambda:task_board(window)),
        ('لوحة تسليم المناوبة', lambda:handover_dashboard(window)), ('أطباء المناوبة', window.roster)
    ]):
        ctk.CTkButton(actions, text=label, height=48, width=100,
            command=lambda f=action:window.run(f)).grid(row=index//2, column=1-index%2, sticky='ew', padx=3, pady=3)

    search = ctk.CTkFrame(parent)
    search.pack(fill='x', padx=5, pady=3)
    window.search = tk.StringVar()
    ctk.CTkLabel(search, text='بحث', anchor='e').pack(side='right', padx=5)
    ArabicEntry(search, textvariable=window.search, justify='right', height=48).pack(side='right', fill='x', expand=True, padx=4)
    window.reset_view_button = ctk.CTkButton(search, text='مسح التصفية', height=48, width=100,
        command=lambda:window.run(window.reset_patient_filters))
    window.reset_view_button.pack(side='left', padx=4)
    window.view_controls = Disclosure(parent, 'التصفية والعرض والترتيب', scroll_height=180)
    window.view_controls.pack(fill='x', padx=5, pady=3)
    options = window.view_controls.content
    for name, label, values in [
        ('filter', 'الحالة السريرية', CLINICAL_FILTERS),
        ('owner_filter', 'المسؤولية', OWNER_FILTERS),
        ('task_filter', 'المهام', TASK_FILTERS),
        ('grouping', 'التجميع للعرض', list(GROUPS.values()))
    ]:
        row = ctk.CTkFrame(options, fg_color='transparent')
        row.pack(fill='x', pady=2)
        ctk.CTkLabel(row, text=label, anchor='e', width=110).pack(side='right', padx=4)
        control = ctk.CTkComboBox(row, values=values, state='readonly', justify='right', height=48,
            command=lambda value:window.render())
        control.pack(side='left', fill='x', expand=True, padx=4)
        setattr(window, name, control)
    for label, action in [('ترتيب المرضى المحفوظ', lambda:sorting_dialog(window)),
                          ('توسيع الكل', lambda:window.expand_cards(True)),
                          ('طي الكل', lambda:window.expand_cards(False))]:
        ctk.CTkButton(options, text=label, height=48, command=lambda f=action:window.run(f)).pack(fill='x', padx=4, pady=2)
    window.view_help = ctk.CTkLabel(options, text='التصفية والتجميع والتثبيت للعرض فقط. الترتيب يُحفظ في التقارير وCSV. تحريك المريض يمسح مستويات الترتيب ويحافظ على الأولوية أولاً.',
        anchor='e', justify='right', wraplength=300)
    window.view_help.pack(fill='x', padx=8, pady=4)
    window.view_summary = ctk.CTkLabel(parent, text='', anchor='e', justify='right', wraplength=650, height=1)
    window.view_summary.pack(fill='x', padx=12)
    window.sort_status = ctk.CTkLabel(parent, text='', anchor='e', justify='right', wraplength=650)
    window.sort_status.pack(fill='x', padx=12, pady=2)
    window.search.trace_add('write', lambda *args:window.render())


def filter_summary(window):
    labels = []
    for value, default in [(window.filter.get(), CLINICAL_FILTERS[0]),
                           (window.owner_filter.get(), OWNER_FILTERS[0]),
                           (window.task_filter.get(), TASK_FILTERS[0])]:
        if value != default:labels.append(value)
    if window.search.get().strip():labels.append('بحث')
    return labels
