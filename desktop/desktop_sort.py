"""Stored patient ordering and presentation-only ward groups."""
from __future__ import annotations
from datetime import datetime, date
from functools import cmp_to_key
import json
from desktop_protocol import ZoneInfo, packed, require, fingerprint
from desktop_model import DIAGNOSIS, GENDER

FIELDS = {'NAME':'الاسم', 'GENDER':'الجنس', 'DIAGNOSIS':'التصنيف والتشخيص',
          'DAYS_OF_ADMITTANCE':'أيام القبول', 'SUPERVISOR':'المشرف'}
GROUPS = {'NONE':'دون تجميع', 'SUPERVISOR':'حسب المشرف', 'RESIDENT':'حسب المقيم',
          'DIAGNOSIS':'حسب التصنيف', 'GENDER':'حسب الجنس', 'ADMISSION_DATE':'حسب تاريخ القبول'}
WORKBOOK_LEVELS = [dict(field=field,direction='DESC' if field in {'GENDER','DAYS_OF_ADMITTANCE'} else 'ASC') for field in
                   ('GENDER','DIAGNOSIS','DAYS_OF_ADMITTANCE','NAME')]


def levels_for(shift):
    try:
        levels = json.loads(shift['meta'].get('sortSpec') or '{"levels":[]}')['levels']
        require(isinstance(levels, list) and len(levels) <= len(FIELDS), 'ترتيب غير صالح')
        require(len({level['field'] for level in levels}) == len(levels), 'حقل ترتيب مكرر')
        for level in levels:
            require(level['field'] in FIELDS and level.get('direction', 'ASC') in {'ASC','DESC'}, 'ترتيب غير صالح')
        return levels
    except (KeyError, ValueError, TypeError):
        require(False, 'إعداد ترتيب محفوظ غير صالح؛ اختر الترتيب من جديد')


def ordered(shift, doctors, zone, levels=None):
    levels = levels_for(shift) if levels is None else levels
    today = datetime.now(ZoneInfo(zone)).date()
    def value(row, field):
        if field == 'NAME': return row['name'].lower()
        if field == 'GENDER': return 0 if row['gender']=='M' else 1
        if field == 'DIAGNOSIS':
            return ({'PSYCHIATRIC':0,'ADDICTION':1,'DUAL':2}.get(row['diagnosisType'],2**31-1),row['initialDiagnosis'].lower())
        if field == 'DAYS_OF_ADMITTANCE': return (today-date.fromisoformat(row['admitDate'])).days if row.get('admitDate') else -1
        return doctors.get(row.get('responsibleSupervisorId'),{}).get('name','').lower()
    def compare(a,b):
        priority = (b.get('isPriority')=='Y')-(a.get('isPriority')=='Y')
        if priority: return priority
        for level in levels:
            x,y=value(a,level['field']),value(b,level['field'])
            result=(x>y)-(x<y)
            if result:return -result if level.get('direction')=='DESC' else result
        if levels:
            x,y=(a['name'].lower(),a['externalId']),(b['name'].lower(),b['externalId'])
            return (x>y)-(x<y)
        return 0  # Python's stable ordering retains stored manual positions.
    shift['patients'].sort(key=cmp_to_key(compare))
    shift['meta']['sortSpec']=packed(dict(levels=levels))
    return shift


def groups(rows, mode, doctors):
    result={}
    for index,row in enumerate(rows):
        if mode in {'SUPERVISOR','RESIDENT'}:
            identity=row.get('responsibleSupervisorId' if mode=='SUPERVISOR' else 'responsibleResidentId')
            key=identity or ''
            label=doctors.get(identity,{}).get('name','طبيب محفوظ' if identity else 'دون طبيب مسؤول')
        elif mode=='DIAGNOSIS':key=row['diagnosisType'];label=DIAGNOSIS.get(key,key)
        elif mode=='GENDER':key=row['gender'];label=GENDER.get(key,key)
        elif mode=='ADMISSION_DATE':key=row['admitDate'];label=key
        else:key='';label=''
        result.setdefault(key,[label,[]])[1].append((index,row))
    return list(result.values())


def sort_description(shift):
    levels=levels_for(shift)
    return 'الترتيب المحفوظ: '+(' ← '.join(FIELDS[level['field']]+' '+('↓' if level.get('direction')=='DESC' else '↑') for level in levels) if levels else 'يدوي')+' · الأولوية أولاً'


def sorting_editor(owner, body, controls, on_saved, embedded=False):
    """Preset selection changes stored order; imported custom specifications remain readable."""
    from desktop_forms import combo, guarded, dialog_actions
    draft=owner.editable()
    expected=fingerprint(draft)
    existing=levels_for(draft)
    for parent in (body,controls):
        for child in parent.winfo_children():child.destroy()
    import desktop_widgets as ctk
    ctk.CTkLabel(body,text='يحفظ الترتيب للعرض وCSV والتقارير. مرضى الأولوية أولاً.',
                 justify='right',wraplength=440).pack(fill='x',padx=10,pady=8)
    presets={
        'manual': ('الترتيب اليدوي', []),
        'name': ('حسب الاسم', [dict(field='NAME',direction='ASC')]),
        'admission': ('مدة القبول — الأطول أولاً', [dict(field='DAYS_OF_ADMITTANCE',direction='DESC'),dict(field='NAME',direction='ASC')]),
        'workbook': ('ترتيب ملف Excel', WORKBOOK_LEVELS),
    }
    selected=next((key for key,(_,levels) in presets.items() if levels==existing),None)
    if selected is None:
        presets['current']=('الترتيب الحالي المستورد',existing)
        selected='current'
    choice=combo(body,'الترتيب',{key:value[0] for key,value in presets.items()},selected)
    def save():
        levels=[dict(level) for level in presets[choice()][1]]
        ordered(draft,owner.client.doctors,owner.client.zone,levels)
        owner.save_draft(draft,expected);on_saved()
    callback=guarded(lambda:owner.run(save)) if embedded else guarded(save)
    dialog_actions(controls,primary=('حفظ الترتيب',callback))


def sorting_dialog(owner):
    import desktop_widgets as ctk
    from desktop_forms import modal,button
    owner.editable()
    window=modal(owner,'ترتيب المرضى','680x480')
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    body=ctk.CTkScrollableFrame(window);body.pack(fill='both',expand=True)
    sorting_editor(owner,body,controls,window.destroy)
    button(window,'إلغاء',window.destroy)
    owner.root.wait_window(window)


def manual_move_allowed(grouping, filter_label, query):
    return grouping==GROUPS['NONE'] and filter_label=='الكل' and not query.strip()


def insert_patient(shift, patient, position):
    """One-based stored position; priority-first remains an explicit invariant."""
    require(type(position) is int and 1<=position<=len(shift['patients'])+1,'موضع المريض خارج القائمة')
    require(not any(row['externalId']==patient['externalId'] for row in shift['patients']),'معرّف المريض موجود')
    priorities=sum(row.get('isPriority')=='Y' for row in shift['patients'])
    require(position<=priorities+1 if patient.get('isPriority')=='Y' else position>priorities,
            'مرضى الأولوية أولاً؛ اختر موضعاً ضمن مجموعة أولوية المريض')
    shift['patients'].insert(position-1,patient)
    shift['meta']['sortSpec']=packed(dict(levels=[]))
