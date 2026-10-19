"""Arabic registry dialogs; all shared mutations are authorized below the UI."""
from __future__ import annotations
import desktop_widgets as ctk
from desktop_doctors import Administration, rank, permanent
from desktop_forms import button, combo, entry, guarded, modal
from desktop_model import GENDER, doctor_label


def doctor_form(owner, doctor=None):
    doctor = doctor or {}
    name = doctor.get('name', '')
    if name.startswith('د.'): name = name[2:].strip()
    parts = name.split(' ', 1)
    window = modal(owner, 'تحرير الطبيب' if doctor else 'إضافة طبيب')
    actions = ctk.CTkFrame(window); actions.pack(side='bottom', fill='x')
    body = ctk.CTkScrollableFrame(window); body.pack(fill='both', expand=True)
    fields = dict(first=entry(body, 'الاسم', parts[0]), last=entry(body, 'الكنية', parts[1] if len(parts)>1 else ''),
                  title=entry(body, 'اللقب — اختياري، حتى 16 محرفاً', doctor.get('title', '')),
                  telegram_id=entry(body, 'معرّف حساب تليجرام — اختياري', str(doctor.get('telegram_id') or '')),
                  destination=entry(body, 'مجموعة تقارير المشرف — معرّف سالب، اختياري', str(doctor.get('destination') or '')))
    gender = combo(body, 'الجنس', GENDER, doctor.get('gender', 'M'))
    role = combo(body, 'التصنيف السريري', {'RESIDENT':'مقيم', 'SUPERVISOR':'مشرف'}, doctor.get('clinicalRole', 'RESIDENT'))
    ctk.CTkLabel(body, text='الدخول من سطح المكتب برمز تليجرام. إعداد PIN محلي على جهاز Android يتم من Android.',
                 wraplength=620, justify='right').pack(fill='x', padx=10, pady=6)
    result = []
    def submit():
        values = {key:widget.get() for key,widget in fields.items()}
        values.update(gender=gender(), clinicalRole=role())
        from desktop_doctors import profile
        profile(values, doctor or None)
        result.append(values); window.destroy()
    button(actions, 'مراجعة التغيير', guarded(submit))
    button(actions, 'إلغاء', window.destroy)
    owner.root.wait_window(window)
    return result[0] if result else None


def registry_text(doctors):
    lines = []
    for key, doctor in doctors.items():
        lines.append('{}\n{} · {} · رتبة {}{}\nحساب تليجرام: {} · مجموعة التقارير: {}\nاللقب: {}'.format(
            doctor_label(doctors, key), GENDER[doctor['gender']],
            'مشرف' if doctor['clinicalRole']=='SUPERVISOR' else 'مقيم', rank(doctor),
            ' · مدير دائم' if permanent(doctor) else '', doctor.get('telegram_id') or 'غير مرتبط',
            doctor.get('destination') or 'بدون', doctor.get('title') or 'بدون'))
    return '\n\n'.join(lines)


def administer(owner):
    owner.ready()
    service = Administration(owner.client, owner.store)
    snapshot = owner.io(service.fetch)
    actions = ['إضافة طبيب', 'تحرير طبيب / مجموعة تقاريره', 'حذف طبيب', 'منح صلاحية مدير', 'إزالة صلاحية مدير']
    selected = owner.choose('إدارة الأطباء', actions)
    if selected is None: return
    action = ['add', 'edit', 'delete', 'promote', 'demote'][selected]
    key, values = None, None
    if action != 'add':
        keys = list(snapshot['doctors'])
        choice = owner.choose('اختر الطبيب', [doctor_label(snapshot['doctors'], key) for key in keys])
        if choice is None: return
        key = keys[choice]
    if action in {'add', 'edit'}:
        values = doctor_form(owner, snapshot['doctors'].get(key))
        if values is None: return
    plan = owner.io(lambda:service.review(snapshot, action, key, values))
    text = 'العملية: '+actions[selected]+'\n\nقبل:\n'+registry_text(snapshot['doctors'])+'\n\nبعد:\n'+registry_text(plan['doctors'])
    if not owner.show_review(text): return
    service.approve(plan)
    owner.plan = None; owner.raw_draft = None
    owner.io(lambda:service.publish(plan))
    owner.set_doctors()
    owner.status.set('نُشر سجل الأطباء وتأكد الفهرس؛ المسودة السريرية محفوظة. أعد مراجعتها قبل النشر.')
