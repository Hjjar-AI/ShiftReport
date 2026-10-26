"""Construct the desktop shell using the existing Window state and callbacks."""
from __future__ import annotations
import tkinter as tk
from desktop_widgets import messagebox
import desktop_widgets as ctk
from desktop_forms import button, destination_row
from desktop_onboarding import create_project, export_project_join
from desktop_admin_ui import administer
from desktop_csv import csv_tools
from desktop_access import configure_pin, lock_screen
from desktop_patient_draft import resume_patient
from desktop_cards import PatientCards
from desktop_ui_events import debounce
from desktop_layout import Disclosure, build_ward_controls
from desktop_appearance import appearance_dialog, apply as apply_appearance, preferences as appearance_preferences
from desktop_backup import backup_dialog


def build_window(owner, root, settings, demo):
    root.title('ShiftReport — Telegram')
    root.geometry('{}x{}'.format(min(1150,max(760,root.winfo_screenwidth()-50)),min(760,max(500,root.winfo_screenheight()-100))))
    root.minsize(760,500)
    root.report_callback_exception = lambda *args: messagebox.showerror('ShiftReport','تعذر إتمام العملية المحلية. إذا بدأ إرسال فراجع سجل العمليات قبل الإعادة.')
    owner.cards=PatientCards()
    owner.report_attempt=None
    owner.report_summary=tk.StringVar(value='')
    owner.status = tk.StringVar(value='ابدأ الاتصال بالمشروع. تحفظ المسودات محليا بصورة مشفرة.')
    owner.zone = tk.StringVar(value=settings.get('TIMEZONE') or 'Asia/Damascus')
    ctk.CTkLabel(root,text='تقرير المناوبة — تليجرام',font=('Arial',23,'bold'),anchor='e').pack(fill='x',padx=14,pady=8)
    owner.status_label = ctk.CTkLabel(root,textvariable=owner.status,anchor='e',justify='right',wraplength=1000)
    owner.status_label.pack(side='bottom',fill='x',padx=14,pady=8)
    body = ctk.CTkFrame(root); body.pack(fill='both',expand=True,padx=8,pady=4)
    sidebar = ctk.CTkScrollableFrame(body,width=205); sidebar.pack(side='right',fill='y',padx=5,pady=5)
    owner.content = ctk.CTkFrame(body); owner.content.pack(side='left',fill='both',expand=True,padx=5,pady=5)
    def section(title, expanded=True, parent=sidebar):
        panel=Disclosure(parent,title,expanded=expanded)
        panel.pack(fill='x',padx=3,pady=3)
        return panel.content
    identity=ctk.CTkFrame(sidebar);identity.pack(fill='x',padx=3,pady=3)
    ctk.CTkLabel(identity,text='الطبيب الحالي',anchor='e').pack(fill='x',padx=8)
    owner.actor=ctk.CTkComboBox(identity,values=['اختر الطبيب'],state='readonly',justify='right',height=48)
    owner.actor.pack(fill='x',padx=8,pady=4)
    if not demo:
        button(identity,'الاتصال / إعادة الاتصال',lambda:owner.run(owner.connect))
        owner.login_button=button(identity,'تسجيل الدخول برمز تليجرام',lambda:owner.run(owner.login))
        button(identity,'قفل الشاشة',lambda:owner.run(lambda:lock_screen(owner)))

    from desktop_dashboard import handover_dashboard
    ward_navigation = section('لوحة الجناح',expanded=False)
    button(ward_navigation, 'لوحة تسليم المناوبة', lambda: owner.run(lambda: handover_dashboard(owner)))
    button(ward_navigation, 'أطباء المناوبة', lambda: owner.run(owner.roster))

    owner.settings_window,settings_area=owner.tool_window('إعداداتي')
    owner.security_window,security_area=owner.tool_window('الحساب وقفل التطبيق')
    owner.time_window,time_area=owner.tool_window('وقت المناوبة')
    owner.project_window,owner.project_area=owner.tool_window('اتصال المشروع')
    owner.team_window,team_area=owner.tool_window('الأطباء والفرق والصلاحيات')
    owner.data_window,data_area=owner.tool_window('جلب البيانات ونشرها')
    owner.local_data_window,local_data_area=owner.tool_window('المناوبة والمسودات')
    owner.files_window,files_area=owner.tool_window('ملفات المرضى')
    owner.backup_window,backup_area=owner.tool_window('النسخ الاحتياطي والاستعادة')
    owner.diagnostics_window,diagnostics_area=owner.tool_window('التشخيص ومعلومات الجهاز')

    destination_row(settings_area,'المظهر والعرض','الألوان والخط وبطاقات المرضى',lambda:owner.run(lambda:appearance_dialog(owner)))
    from desktop_pdf_settings import pdf_settings
    destination_row(settings_area,'تفضيلات التقارير','الصيغة وتخطيط PDF',lambda:owner.run(lambda:pdf_settings(owner)))
    destination_row(settings_area,'الحساب وقفل التطبيق','التحقق وPIN والقفل التلقائي',lambda:owner.open_tool_window(owner.security_window))
    destination_row(settings_area,'وقت المناوبة','المنطقة الزمنية لهذا الجهاز',lambda:owner.open_tool_window(owner.time_window))
    if not demo:
        button(security_area,'إعداد PIN والقفل التلقائي',lambda:owner.run(lambda:configure_pin(owner)))
        button(security_area,'إثبات الهوية برمز تليجرام',lambda:owner.run(owner.login))
    else:ctk.CTkLabel(security_area,text='الحسابات والقفل غير متاحين في العرض التجريبي.').pack(fill='x',padx=8,pady=8)
    ctk.CTkLabel(time_area,text='استخدم المنطقة الزمنية نفسها على Android والكمبيوتر.',anchor='e',wraplength=550).pack(fill='x',padx=8,pady=4)
    ctk.CTkEntry(time_area,textvariable=owner.zone,justify='right').pack(fill='x',padx=8,pady=4)

    project=owner.project_area
    owner.creation_button=None
    if demo:button(project,'إعادة ضبط البيانات التجريبية',lambda:owner.run(owner.reset_demo))
    else:
        button(project,'الاتصال بالمشروع / إعادة الاتصال',lambda:owner.run(owner.connect))
        owner.creation_button=button(project,'إنشاء مشروع جديد',lambda:owner.run(lambda:create_project(owner)))
        if settings.get('CONNECTION_MODE')!='raw':owner.creation_button.pack_forget()
    button(team_area,'مراجعة ونشر تغييرات الأطباء',lambda:owner.run(lambda:administer(owner)))
    if not demo:
        button(team_area,'جلب سجل الأطباء',lambda:owner.run(owner.refresh_identity))
        button(team_area,'تصدير سجل الأطباء CSV',lambda:owner.run(lambda:csv_tools(owner,choice=1)))
        button(team_area,'استيراد سجل الأطباء CSV',lambda:owner.run(lambda:csv_tools(owner,choice=2)))

    owner.data_description=ctk.CTkLabel(data_area,text='التحرير محفوظ على الجهاز. انشر نسختك لمشاركتها، أو اجلب النسخة المنشورة عند الحاجة.',wraplength=550,justify='right')
    owner.data_description.pack(fill='x',padx=8,pady=4)
    owner.data_status_label=ctk.CTkLabel(owner.data_window,textvariable=owner.status,anchor='e',justify='right',wraplength=550)
    owner.data_status_label.pack(side='bottom',fill='x',padx=10,pady=4)
    owner.data_window.bind('<Configure>',owner.resize_data_tools,add='+')
    if not demo:
        for label,action in [('جلب نسخة كاملة',owner.download),('مراجعة ونشر النسخة المحلية',owner.publish)]:
            button(data_area,label,lambda f=action:owner.run(f))
        recovery=section('استعادة العرض قبل آخر جلب',expanded=False,parent=data_area)
        ctk.CTkLabel(recovery,text='يعيد بيانات الجهاز قبل الجلب؛ لا ينشرها إلى تليجرام.',wraplength=500,justify='right').pack(fill='x',padx=8,pady=3)
        button(recovery,'استعادة ما قبل آخر جلب',lambda:owner.run(owner.undo_download))
    button(local_data_area,'استئناف مسودة إدخال المريض',lambda:owner.run(lambda:resume_patient(owner)))
    button(local_data_area,'سلة المرضى المحذوفين',lambda:owner.run(owner.recycle_bin))
    if not demo:
        button(files_area,'تصدير بيانات المرضى CSV',lambda:owner.run(lambda:csv_tools(owner,choice=0)))
        button(backup_area,'إنشاء نسخة مشفرة مشتركة (Android / PC)',lambda:owner.run(lambda:backup_dialog(owner)))
        button(backup_area,'استعادة نسخة مشفرة',lambda:owner.run(owner.import_draft))
        recovery=section('ملف استرداد لهذا الكمبيوتر فقط',expanded=False,parent=backup_area)
        button(recovery,'حفظ مسودة استرداد لهذا الكمبيوتر (.srdraft)',lambda:owner.run(owner.export_draft))

    reports=section('إرسال التقرير وحفظه')
    ctk.CTkLabel(reports,textvariable=owner.report_summary,wraplength=175,justify='right',anchor='e').pack(fill='x',padx=8,pady=3)
    ctk.CTkLabel(reports,text='وجهة التقرير',anchor='e').pack(fill='x',padx=8)
    owner.supervisor=ctk.CTkComboBox(reports,values=['التقرير العام'],state='readonly',justify='right',height=48)
    owner.supervisor.pack(fill='x',padx=8,pady=4)
    if not demo:
        owner.report_publish_button=button(reports,'رفع للشبكة وإرسال تقرير',lambda:owner.run(owner.report))
        owner.assignments_button=ctk.CTkButton(reports,text='تصدير توزيع المرضى (PDF)',height=48,command=lambda:owner.run(owner.export_assignments))
        button(reports,'تقارير المشرفين (PDF)',lambda:owner.run(owner.report_batch))
        button(reports,'سجل التسليم — تحقق من محاولة سابقة',lambda:owner.run(owner.resolve))
    exports=section('تصدير',expanded=False,parent=reports)
    button(exports,'معاينة تقرير وحفظه',lambda:owner.run(lambda:owner.report(export_only=True)))
    button(exports,'حفظ PDF مستقل لكل مشرف',lambda:owner.run(lambda:owner.report(export_only=True,separate=True)))
    if demo:
        owner.assignments_button=ctk.CTkButton(exports,text='تصدير توزيع المرضى (PDF)',height=48,command=lambda:owner.run(owner.export_assignments))
    patient_tools=section('بيانات المرضى',expanded=False)
    button(patient_tools,'جلب البيانات ونشرها',owner.open_data_tools)
    button(patient_tools,'المناوبة والمسودات',lambda:owner.open_tool_window(owner.local_data_window))
    if not demo:
        button(patient_tools,'ملفات المرضى',lambda:owner.open_tool_window(owner.files_window))
        button(patient_tools,'النسخ الاحتياطي والاستعادة',lambda:owner.open_tool_window(owner.backup_window))
    owner.settings_button=button(sidebar,'إعداداتي',owner.open_settings)
    button(sidebar,'اتصال المشروع',owner.open_project_tools)
    owner.admin_sidebar=Disclosure(sidebar,'إدارة الفريق والمشروع',expanded=False)
    admin_area=owner.admin_sidebar.content
    owner.admin_button=ctk.CTkButton(admin_area,text='الأطباء والفرق والصلاحيات',height=48,
        command=lambda:owner.open_tool_window(owner.team_window))
    owner.join_export_button=ctk.CTkButton(admin_area,text='ضم جهاز جديد',height=48,
        command=lambda:owner.run(lambda:export_project_join(owner)))
    from desktop_empty_start import empty_start_dialog
    advanced_admin=section('إدارة متقدمة',expanded=False,parent=admin_area)
    owner.empty_start_button=ctk.CTkButton(advanced_admin,text='بداية فارغة — المدير الدائم',height=48,
        command=lambda:owner.run(lambda:empty_start_dialog(owner)))
    owner.help_sidebar=Disclosure(sidebar,'المساعدة ومعلومات التطبيق',expanded=False)
    owner.help_sidebar.pack(fill='x',padx=3,pady=3)
    button(owner.help_sidebar.content,'التشخيص ومعلومات الجهاز',owner.open_diagnostics)
    if not demo:button(owner.help_sidebar.content,'فتح عرض تجريبي مستقل',owner.open_demo)
    owner.diagnostics_text=ctk.CTkLabel(diagnostics_area,text='',anchor='e',justify='right',wraplength=500)
    owner.diagnostics_text.pack(fill='x',padx=8,pady=4)
    build_ward_controls(owner)
    owner.patients = ctk.CTkScrollableFrame(owner.content); owner.patients.pack(fill='both',expand=True,padx=5,pady=4)
    owner.content.bind('<Configure>', debounce(owner.content, 120, owner.resize_content))
    def focus_search(event):
        if not owner.locked and not owner.busy:
            owner.search_entry.focus_set()
        return 'break'
    def add_patient(event):
        owner.run(owner.add_patient)
        return 'break'
    owner.search_entry.bind('<Control-f>', focus_search, add='+')
    owner.search_entry.bind('<Control-n>', add_patient, add='+')
    root.bind('<Control-f>', focus_search)
    root.bind('<Control-n>', add_patient)
    root.protocol('WM_DELETE_WINDOW',owner.close)
    if demo:
        from desktop_demo import DemoClient, DemoWorkspace
        owner.client=DemoClient();owner.store=DemoWorkspace(owner.client)
        apply_appearance(owner,appearance_preferences(owner))
        owner.zone.set(owner.client.zone);owner.set_doctors()
        owner.status.set('DEMO — وضع تجريبي في الذاكرة. الإغلاق يحذف التجربة. يسمح بحفظ PDF تجريبي محلي فقط.')
        root.title('ShiftReport — DEMO');owner.render()
    else:
        owner.render()
        root.after(200,lambda:owner.run(owner.connect))
        root.bind_all('<KeyPress>',owner.note_activity,add='+')
        root.bind_all('<ButtonPress>',owner.note_activity,add='+')
        root.bind_all('<Motion>',owner.note_activity,add='+')
        root.after(1000,owner.check_lock)
    owner.clock_after_id=root.after(60_000,owner.clock_tick)
