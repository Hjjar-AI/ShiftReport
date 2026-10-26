"""Compact ward layout; presentation controls never mutate clinical ordering."""
import tkinter as tk
import desktop_widgets as ctk
from desktop_input import ArabicEntry
from desktop_patient_filters import CLINICAL_FILTERS, OWNER_FILTERS, TASK_FILTERS
from desktop_sort import GROUPS, sorting_editor
from desktop_workflow import task_board
from desktop_tokens import WARNING_CONTAINER
from desktop_ui_events import debounce


class Disclosure(ctk.CTkFrame):
    def __init__(self, parent, title, expanded=False, scroll_height=None, toggle_parent=None):
        super().__init__(parent)
        self.expanded = expanded
        self.title = title
        self.toggle_button = ctk.CTkButton(toggle_parent if toggle_parent is not None else self, text='', height=48,
            width=120, anchor='e', command=self.toggle,fg_color='transparent',
            text_color=('gray25','gray85'),hover_color=('gray88','gray22'))
        if toggle_parent is None:self.toggle_button.pack(fill='x', padx=4, pady=2)
        else:self.toggle_button.pack(side='left',padx=3,pady=2)
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
    window.operation_notice=ctk.CTkFrame(parent,fg_color=WARNING_CONTAINER)
    window.operation_notice_text=ctk.CTkLabel(window.operation_notice,text='',anchor='e',justify='right',wraplength=550)
    window.operation_notice_text.pack(fill='x',padx=8,pady=3)
    ctk.CTkButton(window.operation_notice,text='مراجعة سجل العمليات والتسليم',height=48,
        command=lambda:window.run(window.resolve)).pack(fill='x',padx=8,pady=4)
    if not window.demo:
        actions = ctk.CTkFrame(parent)
        actions.pack(fill='x',padx=5,pady=4)
        for column in range(2):actions.grid_columnconfigure(column,weight=1,uniform='daily')
        window.fetch_button=ctk.CTkButton(actions,text='جلب البيانات',height=48,
            command=lambda:window.run(window.download))
        window.fetch_button.grid(row=0,column=1,sticky='ew',padx=3,pady=4)
        window.send_report_button=ctk.CTkButton(actions,text='رفع للشبكة وإرسال تقرير',height=48,
            command=lambda:window.run(window.report))
        window.send_report_button.grid(row=0,column=0,sticky='ew',padx=3,pady=4)
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

    window.show_workflow_details=False
    window.workflow_details=ctk.CTkLabel(status,text='',anchor='e',justify='right',wraplength=650)
    def toggle_details():
        window.show_workflow_details=not window.show_workflow_details
        details.configure(text='طي التفاصيل' if window.show_workflow_details else 'التفاصيل')
        window.render()
    details=ctk.CTkButton(status,text='التفاصيل',height=48,width=80,command=toggle_details,
        fg_color='transparent',text_color=('gray25','gray85'))
    details.grid(row=0,column=2,padx=4,pady=3)
    toolbar = ctk.CTkFrame(parent)
    window.ward_toolbar=toolbar
    toolbar.pack(fill='x', padx=5, pady=3, before=status)
    ctk.CTkButton(toolbar,text='إضافة مريض',height=48,width=110,
        command=lambda:window.run(window.add_patient)).pack(side='right',padx=3,pady=3)
    window.search = tk.StringVar()
    window.view_controls = Disclosure(parent, 'خيارات العرض', scroll_height=220, toggle_parent=toolbar)
    window.view_controls.pack(fill='x', padx=5, pady=0)
    window.search_entry = ArabicEntry(toolbar, textvariable=window.search, justify='right', height=48,
        placeholder_text='بحث عن مريض')
    window.search_entry.pack(side='right', fill='x', expand=True, padx=4)
    options = window.view_controls.content
    from desktop_sections import SectionPages
    window.options_tabs=SectionPages(options)
    window.options_tabs.pack(fill='x',expand=True)
    sections={}
    for label in ['التصفية','التجميع','البطاقات','الترتيب']:
        window.options_tabs.add(label)
        sections[label]=window.options_tabs.tab(label)
    for name, label, values in [
        ('filter', 'الحالة السريرية', CLINICAL_FILTERS),
        ('owner_filter', 'المسؤولية', OWNER_FILTERS),
        ('task_filter', 'المهام', TASK_FILTERS),
        ('grouping', 'التجميع للعرض فقط', list(GROUPS.values()))
    ]:
        parent=sections['التجميع'] if name=='grouping' else sections['التصفية']
        row = ctk.CTkFrame(parent, fg_color='transparent')
        row.pack(fill='x', pady=2)
        ctk.CTkLabel(row, text=label, anchor='e', width=110).pack(side='right', padx=4)
        control = ctk.CTkComboBox(row, values=values, state='readonly', justify='right', height=48,
            command=lambda value:window.render())
        control.pack(side='left', fill='x', expand=True, padx=4)
        setattr(window, name, control)
    ctk.CTkButton(sections['التصفية'],text='مهام الجناح',height=48,
        command=lambda:window.run(lambda:task_board(window))).pack(fill='x',padx=4,pady=2)
    window.reset_view_button = ctk.CTkButton(sections['التصفية'],text='مسح التصفية',height=48,
        command=lambda:window.run(window.reset_patient_filters))
    window.reset_view_button.pack(fill='x',padx=4,pady=2)
    window.card_density=tk.StringVar(value='عادية')
    from desktop_appearance import set_density
    ctk.CTkLabel(sections['البطاقات'],text='مسافات البطاقات · تفضيل محلي',anchor='e').pack(fill='x',padx=8)
    ctk.CTkComboBox(sections['البطاقات'],values=['عادية','مضغوطة'],variable=window.card_density,
        state='readonly',justify='right',height=48,
        command=lambda value:window.run(lambda:set_density(window,value))).pack(fill='x',padx=4,pady=2)
    for label, expand in [('توسيع الكل', True),('طي الكل',False)]:
        ctk.CTkButton(sections['البطاقات'], text=label, height=48,
            command=lambda value=expand:window.run(lambda:window.expand_cards(value))).pack(fill='x',padx=4,pady=2)
    sort_area=ctk.CTkFrame(sections['الترتيب']);sort_area.pack(fill='x')
    sort_controls=ctk.CTkFrame(sections['الترتيب']);sort_controls.pack(fill='x')
    def edit_sort():
        sorting_editor(window,sort_area,sort_controls,edit_sort,embedded=True)
    ctk.CTkLabel(sort_area,text='الترتيب محفوظ مع المناوبة ويستخدم في التقارير وCSV.',
        anchor='e',justify='right',wraplength=420).pack(fill='x',padx=8)
    ctk.CTkButton(sort_controls,text='تحرير الترتيب الحالي',height=48,
        command=lambda:window.run(edit_sort)).pack(fill='x',padx=4,pady=2)
    window.view_help = ctk.CTkLabel(options, text='التصفية والتجميع والتثبيت للعرض فقط. الترتيب يُحفظ في التقارير وCSV. تحريك المريض يمسح مستويات الترتيب ويحافظ على الأولوية أولاً.',
        anchor='e', justify='right', wraplength=300)
    window.view_help.pack(fill='x', padx=8, pady=4)
    window.view_summary = ctk.CTkLabel(parent, text='', anchor='e', justify='right', wraplength=650, height=1)
    window.view_summary.pack(fill='x', padx=12)
    window.sort_status = ctk.CTkLabel(parent, text='', anchor='e', justify='right', wraplength=650)
    window.sort_status.pack(fill='x', padx=12, pady=2)
    refresh_search = debounce(window.root, 150, lambda *args: window.render())
    window.search.trace_add('write', refresh_search)


def filter_summary(window):
    labels = []
    for value, default in [(window.filter.get(), CLINICAL_FILTERS[0]),
                           (window.owner_filter.get(), OWNER_FILTERS[0]),
                           (window.task_filter.get(), TASK_FILTERS[0])]:
        if value != default:labels.append(value)
    if window.search.get().strip():labels.append('بحث: '+window.search.get().strip())
    return labels
