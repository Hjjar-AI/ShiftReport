"""Logical PDF content and Android's current palette; no shaping or saved-data edits."""
from __future__ import annotations
from datetime import date, datetime
from desktop_model import patient_name, DIAGNOSIS, GENDER, badges_decode
from desktop_protocol import require, current_date
from desktop_tasks import read_tasks, ordered_for_display, task_text

PAPERS = {'A4': (595, 842), 'A5': (420, 595)}
DEFAULT_OPTIONS = dict(layout='cards', paper='A5', orientation='portrait', color='teal', dark=False,
                       separate=False, local_draft=False, ward='', rule_weight='medium', rule_contrast='medium', row_shading='auto')

RULE_WEIGHTS = {'thin': .75, 'medium': 1.5, 'thick': 2.25}
RULE_CONTRASTS = {'light': ('#CBD5E1', '#475569'), 'medium': ('#94A3B8', '#64748B'),
                  'strong': ('#64748B', '#94A3B8')}
PRESENTATION_PRESETS = {
    'balanced': dict(rule_weight='medium', rule_contrast='medium', row_shading='auto'),
    'clear': dict(rule_weight='thick', rule_contrast='strong', row_shading='zebra'),
    'print': dict(rule_weight='medium', rule_contrast='strong', row_shading='none', dark=False),
}


def normalize_options(options=None):
    result = dict(DEFAULT_OPTIONS, **(options or {}))
    if result['paper'] in {'A3','Letter'}:result['paper']='A4'
    if result['layout']=='workbook':result['layout']='table'
    require(result['layout'] in {'table','cards','paragraphs'} and result['paper'] in PAPERS and
            result['orientation'] in {'portrait','landscape'} and result['color'] in
            {'teal','blue','green','purple','pastel'}, 'خيارات PDF غير صالحة')
    require(type(result['dark']) is bool and type(result['separate']) is bool and type(result['local_draft']) is bool, 'خيارات PDF غير صالحة')
    require(result['rule_weight'] in RULE_WEIGHTS and result['rule_contrast'] in RULE_CONTRASTS and
            result['row_shading'] in {'auto','zebra','none'}, 'خيارات فواصل PDF غير صالحة')
    # Legacy overrides remain readable; PDFs are portrait/light with fixed separators.
    result.update(rule_weight='medium', rule_contrast='medium', separate=False, orientation='portrait', dark=False)
    return result


def roster_label(doctors):
    if len(doctors)==1:return 'طبيبة المناوبة' if doctors[0].get('gender')=='F' else 'طبيب المناوبة'
    return 'طبيبات المناوبة' if doctors and all(d.get('gender')=='F' for d in doctors) else 'أطباء المناوبة'


def row_backgrounds(patients, colors):
    """Contiguous supervisor groups use group/cream stripes; other orders use zebra."""
    if colors['shading']=='none':return [colors['page'] for _ in patients]
    if colors['shading']=='zebra':return [colors['page'] if i%2==0 else colors['zebra'] for i in range(len(patients))]
    seen=set();last=object();group=-1;position=0;grouped=True
    for patient in patients:
        key=patient.get('responsibleSupervisorId') or ''
        if key!=last:
            if key in seen:grouped=False;break
            seen.add(key);last=key
    if not grouped:return [colors['page'] if i%2==0 else colors['zebra'] for i in range(len(patients))]
    result=[];last=object()
    for patient in patients:
        key=patient.get('responsibleSupervisorId') or ''
        if key!=last:group+=1;position=0;last=key
        result.append(colors['groups'][group%len(colors['groups'])] if position%2==0 else colors['cream'])
        position+=1
    return result


def palette(options):
    # Same colors and group sequence as Android PdfPalette.resolve.
    options=normalize_options(options)
    rules=dict(rule=RULE_CONTRASTS[options['rule_contrast']][0],
               rule_width=RULE_WEIGHTS[options['rule_weight']],shading=options['row_shading'])
    title, header = {'teal':('#0D5663','#146474'), 'blue':('#123F70','#174A7E'),
                     'green':('#214D39','#285943'), 'purple':('#493269','#563D7C'),
                     'pastel':('#8F2450','#4E568F')}[options['color']]
    groups = ['#CFE8F0','#D8EEE2','#F0F0D2','#FAE3DC','#E0E2F4']
    if options['color'] == 'pastel':
        groups = ['#FFADAD','#FFD6A5','#FDFFB6','#CAFFBF','#9BF6FF','#A0C4FF','#BDB2FF','#FFC6FF','#FFFFFC']
    return dict(title=title, header=header, summary='#F8FAFC',
                page='#FFFFFF', text='#1E293B', border='#E2E8F0', zebra='#F8FAFC', cream='#FFFFFF', groups=groups, **rules)


def context_lines(client, shift, options, now, include_shift_date=True, report_day=None):
    return ['تاريخ المناوبة: '+(report_day or current_date(client.zone,now))] if include_shift_date else []


def created_at(now):
    return 'أُنشئ في: '+now.strftime('%Y-%m-%d %H:%M %Z')


def assignment_groups(shift, doctors):
    """Teams are explicit registry membership, independent of patient assignments."""
    from desktop_teams import resident_ids, validate_teams
    validate_teams(doctors)
    groups={}
    highlight_dates={}
    for patient in shift['patients']:
        supervisor=patient.get('responsibleSupervisorId') or None
        groups.setdefault(supervisor,[]).append(patient_name(patient))
        highlight_dates.setdefault(supervisor,[]).append(patient.get('assignmentChangedOn') or '')
    for key,doctor in doctors.items():
        if doctor['clinicalRole']=='SUPERVISOR':groups.setdefault(key,[])
    return [dict(supervisorId=supervisor,
                 supervisor=doctors.get(supervisor,{}).get('name','مشرف محفوظ' if supervisor else 'بدون مشرف'),
                 residentIds=resident_ids(doctors,supervisor),
                 residents=[doctors[key]['name'] for key in resident_ids(doctors,supervisor)],patients=names,
                 assignmentDates=highlight_dates.get(supervisor,[]))
            for supervisor,names in groups.items()]


def patient_content(row, doctors, zone, now, report_day=None):
    day=date.fromisoformat(report_day or current_date(zone,now))
    def doctor(key): return doctors.get(row.get(key), {}).get('name', 'طبيب محفوظ') if row.get(key) else ''
    personal = ('[أولوية] ' if row.get('isPriority')=='Y' else '')+patient_name(row)
    personal += '\n'+GENDER[row['gender']]
    if row.get('birthDate'):
        personal += '\n{} · {} سنة'.format(row['birthDate'], day.year-int(row['birthDate'][:4]))
    if row['hasCompanion']=='Y': personal+='\n(مرافق)'
    admit = row.get('admitNum','')
    if row.get('admitDate'):
        admit += '\n---\nيوم {}\n{}'.format(max(0,(day-datetime.fromisoformat(row['admitDate']).date()).days),row['admitDate'])
    diagnosis = [DIAGNOSIS[row['diagnosisType']]]
    for text, priority in badges_decode(row.get('warningDetails','')):
        level = {'low':'منخفضة','medium':'متوسطة','high':'عالية'}.get(priority)
        diagnosis.append('تنبيه: '+text+(' · أولوية '+level if level else ''))
    diagnosis.append(row.get('initialDiagnosis',''))
    team = '\n---\n'.join(filter(None,[doctor('responsibleSupervisorId'),doctor('responsibleResidentId')]))
    from desktop_teams import is_cross_team_cover
    if is_cross_team_cover(row,doctors):team+='\nتغطية من فريق آخر'
    tasks = read_tasks(row)
    task_lines=[]
    for task in ordered_for_display(tasks):
        task_lines.append(task_text(task))
    notes = '\n'.join(label+': '+value for label,value in
                     [('المتابعة',row.get('followUp','')),('المهام','\n'.join(task_lines)),('التحاليل',row.get('labs',''))] if value)
    return dict(personal=personal, admit=admit, diagnosis='\n'.join(filter(None,diagnosis)),team=team,
                treatment=row.get('treatmentPlan',''),followUp=row.get('followUp',''),labs=row.get('labs',''),
                tasks=task_lines,notes=notes)
