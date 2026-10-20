"""Arabic registry dialogs; all shared mutations are authorized below the UI."""
from __future__ import annotations
import desktop_widgets as ctk
from desktop_doctors import Administration, rank, permanent
from desktop_forms import button, combo, entry, guarded, modal, dialog_actions
from desktop_model import GENDER, doctor_label


def doctor_form(owner, doctor=None, registry=None):
    doctor = doctor or {}
    registry=owner.client.doctors if registry is None else registry
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
    team_mapping={'':'دون فريق'}
    team_mapping.update({key:doctor_label(registry,key) for key,item in registry.items() if item['clinicalRole']=='SUPERVISOR' and key!=doctor.get('uid')})
    team=combo(body,'مشرف فريق المقيم — يُتجاهل عند اختيار دور مشرف',team_mapping,doctor.get('teamSupervisorId',''))
    role = combo(body, 'التصنيف السريري', {'RESIDENT':'مقيم', 'SUPERVISOR':'مشرف'}, doctor.get('clinicalRole', 'RESIDENT'))
    ctk.CTkLabel(body, text='إثبات الهوية للنشر برمز تليجرام. إعداد PIN لهذا الكمبيوتر من القائمة الجانبية.',
                 wraplength=620, justify='right').pack(fill='x', padx=10, pady=6)
    result = []
    def submit():
        values = {key:widget.get() for key,widget in fields.items()}
        values.update(gender=gender(), clinicalRole=role(),teamSupervisorId=team() if role()=='RESIDENT' else '')
        from desktop_doctors import profile
        profile(values, doctor or None)
        result.append(values); window.destroy()
    dialog_actions(actions,primary=('مراجعة التغيير',guarded(submit)),cancel=('إلغاء',window.destroy))
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
        if doctor.get('teamSupervisorId'):lines[-1]+='\nمشرف الفريق: '+doctor_label(doctors,doctor['teamSupervisorId'])
    return '\n\n'.join(lines)


def administer(owner):
    owner.ready()
    service = Administration(owner.client, owner.store)
    snapshot = owner.io(service.fetch)
    actions = ['إدارة الفرق معاً', 'إضافة طبيب', 'تحرير طبيب / مجموعة تقاريره', 'حذف طبيب', 'منح صلاحية مدير', 'إزالة صلاحية مدير']
    selected = owner.choose('إدارة الأطباء', actions)
    if selected is None: return
    action = ['teams', 'add', 'edit', 'delete', 'promote', 'demote'][selected]
    key, values = None, None
    if action == 'teams':
        values=team_form(owner,snapshot['doctors'])
        if not values:return
    elif action != 'add':
        keys = list(snapshot['doctors'])
        choice = owner.choose('اختر الطبيب', [doctor_label(snapshot['doctors'], key) for key in keys])
        if choice is None: return
        key = keys[choice]
    if action in {'add', 'edit'}:
        values = doctor_form(owner, snapshot['doctors'].get(key),snapshot['doctors'])
        if values is None: return
    plan = owner.io(lambda:service.review(snapshot, action, key, values))
    text = 'العملية: '+actions[selected]+'\n\nقبل:\n'+registry_text(snapshot['doctors'])+'\n\nبعد:\n'+registry_text(plan['doctors'])
    if not owner.show_review(text): return
    service.approve(plan)
    owner.plan = None; owner.raw_draft = None
    owner.io(lambda:service.publish(plan))
    owner.set_doctors()
    owner.status.set('نُشر سجل الأطباء وتأكد الفهرس؛ المسودة السريرية محفوظة. أعد مراجعتها قبل النشر.')


def team_form(owner,doctors):
    """Review several resident memberships together; patient assignments do not change."""
    window=modal(owner,'إدارة الفرق معاً','740x680')
    actions=ctk.CTkFrame(window);actions.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    ctk.CTkLabel(area,text='اختر مشرف كل مقيم، ثم راجع جميع التغييرات قبل النشر. تعيينات المرضى والتغطية الفردية تبقى كما هي.',wraplength=620,justify='right').pack(fill='x',padx=8,pady=5)
    supervisors={key:d for key,d in doctors.items() if d['clinicalRole']=='SUPERVISOR'}
    for key,doctor in supervisors.items():
        names=[d['name'] for d in doctors.values() if d.get('teamSupervisorId')==key]
        ctk.CTkLabel(area,text=doctor['name']+': '+('، '.join(names) or 'دون مقيمين'),wraplength=620,justify='right').pack(fill='x',padx=8,pady=3)
    choices={'':'دون فريق'};choices.update({key:doctor_label(doctors,key) for key in supervisors})
    fields={key:combo(area,d['name'],choices,d.get('teamSupervisorId','')) for key,d in doctors.items() if d['clinicalRole']=='RESIDENT'}
    result=[]
    def submit():
        changes={key:get() for key,get in fields.items() if get()!=(doctors[key].get('teamSupervisorId') or '')}
        result.append(changes);window.destroy()
    dialog_actions(actions,primary=('مراجعة تغييرات الفرق',guarded(submit)),cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
    return result[0] if result else None
