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
