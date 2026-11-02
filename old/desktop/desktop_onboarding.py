"""Desktop creation dialogs; protocol authorization lives in desktop_project."""
from __future__ import annotations
from pathlib import Path
from tkinter import filedialog
import uuid
import desktop_widgets as ctk
from desktop_widgets import messagebox, simpledialog
from desktop_forms import button, combo, entry, guarded, modal, dialog_actions
from desktop_model import GENDER
from desktop_project import ProjectCreator, export_join
from desktop_protocol import ZoneInfo, fingerprint, require
from desktop_settings import raw_config


def protected_join_form(owner, selected_path=None):
    """File/password stay in this dialog; unlock precedes project review."""
    import tkinter as tk
    from desktop_protocol import open_join
    window=modal(owner,'الانضمام إلى مشروع','740x580')
    actions=ctk.CTkFrame(window);actions.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    ctk.CTkLabel(area,text='اختر ملف الانضمام المحمي وكلمة مروره بأي ترتيب، ثم افتحه لمراجعة المشروع.',
        anchor='e',justify='right',wraplength=600).pack(fill='x',padx=10,pady=8)
    chosen=[Path(selected_path) if selected_path else None]
    path_label=ctk.CTkLabel(area,text=chosen[0].name if chosen[0] else 'لم يُحدد ملف',anchor='e',wraplength=600)
    password=tk.StringVar()
    def refresh(*args):
        if not window.winfo_exists():return
        unlock.configure(state='normal' if chosen[0] and password.get() else 'disabled')
    def pick():
        path=filedialog.askopenfilename(parent=window,title='ملف الانضمام المحمي',filetypes=[('ShiftReport join','*.srjoin.json')])
        if path:chosen[0]=Path(path);path_label.configure(text=chosen[0].name)
        refresh()
    file_button=button(area,'اختيار ملف الانضمام',pick)
    path_label.pack(fill='x',padx=10,pady=4)
    ctk.CTkLabel(area,text='كلمة مرور ملف الانضمام',anchor='e').pack(fill='x',padx=10,pady=(8,2))
    password_entry=ctk.CTkEntry(area,textvariable=password,show='*',height=48,justify='right')
    password_entry.pack(fill='x',padx=10,pady=4)
    result=[];opening=[False]
    def close():
        if not opening[0]:window.destroy()
    def unlock_file():
        require(chosen[0] is not None and password.get(),'اختر الملف وأدخل كلمة مروره')
        secret=password.get();opening[0]=True
        file_button.configure(state='disabled');password_entry.configure(state='disabled');unlock.configure(state='disabled')
        try:config=owner.io(lambda:open_join(chosen[0],secret))
        finally:
            secret=None;opening[0]=False;password.set('')
            if window.winfo_exists():
                file_button.configure(state='normal');password_entry.configure(state='normal')
        if not window.winfo_exists():return
        review='المشفى: '+config['hospitalName']+'\nتشفير بيانات تليجرام: '+('مفعّل' if config.get('encryptTelegram') else 'غير مفعّل')
        review+='\nوجهة المقيمين: '+('مجموعة منفصلة' if config.get('residentsChatId') else 'مجموعة المشروع')
        review+='\n\nستُفحص هوية المشروع والمفتاح والمواضيع قبل استبدال الاتصال القائم.'
        if not owner.text_dialog('مراجعة مشروع الانضمام',review,approve=True,approval_label='متابعة الاتصال بالمشروع'):
            if window.winfo_exists():window.grab_set()
            return
        result.append(config);window.destroy()
    buttons=dialog_actions(actions,primary=('فتح الملف ومراجعة المشروع',guarded(unlock_file)),cancel=('إلغاء',close))
    window.protocol('WM_DELETE_WINDOW',close)
    unlock=buttons['فتح الملف ومراجعة المشروع']
    trace=password.trace_add('write',refresh)
    refresh()
    try:owner.root.wait_window(window)
    finally:
        password.trace_remove('write',trace);password.set('');chosen.clear()
    return result[0] if result else None


def first_admin_form(owner, previous=None):
    previous = previous or {}
    window = modal(owner, 'إنشاء مشروع جديد — المدير الأول', '780x650')
    actions = ctk.CTkFrame(window); actions.pack(side='bottom', fill='x')
    area = ctk.CTkScrollableFrame(window); area.pack(fill='both', expand=True, padx=8, pady=8)
    ctk.CTkLabel(area, text='للمجموعات الجديدة فقط. لا تُحذف الرسائل أو البيانات القائمة.\n'
        'يجب ألا توجد رسالة مثبتة. سنفحص صلاحياتك ونرسل رمزاً خاصاً؛\n'
        'لن يُنشر سجل الأطباء أو الفهرس حتى تراجع وتوافق.',
        wraplength=640, justify='right').pack(fill='x', padx=10, pady=8)
    name = entry(area, 'اسم المدير الأول', previous.get('name', ''))
    identity = entry(area, 'معرّف حساب المدير في تليجرام — يجب أن يدير المجموعة',
                     str(previous.get('telegram_id', '')))
    identity.configure(validate='key', validatecommand=(window.register(lambda value:not value or value.isdecimal()), '%P'))
    gender = combo(area, 'الجنس', GENDER, previous.get('gender', 'M'))
    role = combo(area, 'الدور السريري', {'RESIDENT':'طبيب مقيم', 'SUPERVISOR':'طبيب مشرف'},
                 previous.get('clinicalRole', 'RESIDENT'))
    ctk.CTkLabel(area, text='افتح محادثة البوت واضغط Start قبل طلب الرمز.\n'
        'لا تستخدم الإنشاء لإصلاح مشروع قائم أو لتغيير مفتاحه.',
        wraplength=640, justify='right').pack(fill='x', padx=10, pady=8)
    result = []
    def submit():
        result.append(ProjectCreator.validate_profile(dict(name=name.get(), telegram_id=identity.get(),
                      gender=gender(), clinicalRole=role())))
        window.destroy()
    dialog_actions(actions,primary=('طلب رمز تليجرام للتحقق',guarded(submit)),cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
    return result[0] if result else None


def create_project(owner):
    require(owner.client is None, 'أغلق المشروع الحالي قبل إعداد مشروع جديد؛ لا يمكن خلط بيانات مشروعين')
    require(owner.settings.get('CONNECTION_MODE') == 'raw',
            'املأ إعدادات المشروع الجديد في .env واختر CONNECTION_MODE=raw أولاً')
    config = raw_config(owner.settings)
    zone = owner.zone.get().strip(); ZoneInfo(zone)
    creator = owner.project_creator
    if creator is None or fingerprint(creator.client.config) != fingerprint(config) or creator.client.zone != zone:
        if creator is not None: creator.client.telegram._http.close()
        creator = owner.project_creator = ProjectCreator(config, zone)
        creator.client.telegram.runner = owner.io
    profile = first_admin_form(owner, creator.profile)
    if profile is None: return
    owner.io(lambda:creator.begin(profile))
    if not creator.verified:
        for _ in range(5):
            code = simpledialog.askstring('رمز إنشاء المشروع', 'أدخل الرمز الذي وصل إلى حساب المدير في البوت:',
                                          show='*', parent=owner.root)
            if code is None: return
            try: verified = owner.io(lambda:creator.check_code(code))
            finally: code = None
            if verified: break
            messagebox.showerror('رمز التحقق', 'الرمز غير صحيح')
        else: return
    workspace = owner.open_workspace(creator.client)
    if workspace is None: return
    approval, review = owner.io(lambda:creator.review(workspace))
    details = ('المشفى: '+review['hospital']+'\nمجموعة تليجرام: '+review['group']+
        '\nمعرّف المجموعة: '+str(review['group_id'])+
        '\nالمواضيع — التقارير: '+str(review['topics']['reportsTopicId'])+
        '، بيانات المناوبات: '+str(review['topics']['csvTopicId'])+
        '، سجل الأطباء: '+str(review['topics']['doctorsTopicId'])+
        '\nوجهة تقارير المقيمين: '+('مجموعة منفصلة مع موضوع اختياري' if config.get('residentsChatId') else 'مجموعة المشروع')+
        '\nالمدير الأول: '+review['admin']+
        '\nالدور السريري: '+('طبيب مشرف' if review['role']=='SUPERVISOR' else 'طبيب مقيم')+
        '\nتشفير بيانات تليجرام: '+('مفعّل بالمفتاح المحدد' if review['encrypted'] else 'غير مفعّل')+
        '\n\nسيُنشر سجل يضم المدير الأول، ثم رسالة فهرس في General وتثبَّت.\n'
        'لن تُنشأ بيانات مرضى أو مناوبة تلقائياً.\n'
        'استخدم جهازاً واحداً للإنشاء، ولا تبدأ العملية من جهاز آخر في الوقت نفسه.\n'
        'هل توافق على إنشاء هذا المشروع الجديد؟')
    if not owner.text_dialog('مراجعة إنشاء المشروع', details, approve=True): return
    owner.io(lambda:creator.publish(approval, workspace))
    owner.client, owner.store = creator.client, workspace
    owner.plan = owner.raw_draft = None
    owner.set_doctors()
    owner.status.set('تم إنشاء المشروع وتأكيد سجل المدير. اجلب/اختر مناوبة للبدء، وصدّر ملف انضمام لإضافة أجهزة أخرى.')


def export_project_join(owner):
    owner.ready()
    password = simpledialog.askstring('حماية ملف الانضمام', 'اختر عبارة مرور لا تقل عن 10 محارف:',
                                     show='*', parent=owner.root)
    if password is None: return
    try:
        require(len(password.encode('utf-16-le'))//2 >= 10, 'عبارة المرور قصيرة؛ اختر 10 محارف على الأقل')
        confirmation = simpledialog.askstring('تأكيد عبارة المرور', 'أعد عبارة مرور ملف الانضمام:',
                                              show='*', parent=owner.root)
        try:
            if confirmation is None: return
            require(password == confirmation, 'لم تتطابق عبارتا المرور')
        finally: confirmation = None
        path = filedialog.asksaveasfilename(title='حفظ ملف انضمام جديد', defaultextension='.srjoin.json',
                    initialfile='ShiftReport_Join_'+str(uuid.uuid4())+'.srjoin.json',
                    filetypes=[('ShiftReport join','*.srjoin.json')])
        if not path: return
        owner.io(lambda:export_join(owner.client, Path(path), password))
        owner.status.set('حُفظ ملف الانضمام المحمي وتم التحقق منه. أرسل عبارة المرور عبر قناة منفصلة.')
    finally: password = None
