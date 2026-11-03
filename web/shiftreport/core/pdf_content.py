"""Logical PDF content and Android's current palette; no shaping or saved-data edits."""
from __future__ import annotations
from datetime import date, datetime
from .model import patient_name, DIAGNOSIS, GENDER, badges_decode
from .protocol import require, current_date
from .tasks import read_tasks, ordered_for_display, task_text

PAPERS = {'A4': (595, 842), 'A5': (420, 595)}
DEFAULT_OPTIONS = dict(layout='cards', paper='A5', orientation='portrait', app_theme='VANILLA', dark=False,
                       separate=False, local_draft=False, ward='', rule_weight='medium', rule_contrast='medium', row_shading='zebra')

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
    require(result['layout'] in {'table','cards','paragraphs'} and result['paper'] in PAPERS and
            result['app_theme'] in {'VANILLA','SYSTEM','COASTAL','CREAM'}, 'خيارات PDF غير صالحة')
    require(type(result['local_draft']) is bool, 'خيارات PDF غير صالحة')
    # Ignore retired presentation overrides without changing stored clinical data.
    for key in ('two_column_labs','two_column_sections','color'):
        result.pop(key,None)
    result.update(paper='A5', rule_weight='medium', rule_contrast='medium', row_shading='zebra',
                  separate=False, orientation='portrait', dark=False)
    return result


def roster_label(doctors):
    if len(doctors)==1:return 'طبيبة المناوبة' if doctors[0].get('gender')=='F' else 'طبيب المناوبة'
    return 'طبيبات المناوبة' if doctors and all(d.get('gender')=='F' for d in doctors) else 'أطباء المناوبة'


def roster_lines(doctors, separator=' / '):
    """Android ReportRosterLabel: alphabetical residents, then named supervisors."""
    residents=sorted((d for d in doctors if d.get('clinicalRole')!='SUPERVISOR'),key=lambda d:d['name'])
    supervisors=sorted((d for d in doctors if d.get('clinicalRole')=='SUPERVISOR'),key=lambda d:d['name'])
    lines=[]
    if residents:
        lines.append(roster_label(residents)+': '+separator.join(d['name'] for d in residents))
    if supervisors:
        label=('مشرفة المناوبة' if supervisors[0].get('gender')=='F' else 'مشرف المناوبة') if len(supervisors)==1 else (
            'مشرفات المناوبة' if all(d.get('gender')=='F' for d in supervisors) else 'مشرفو المناوبة')
        lines.append(label+': '+separator.join(d['name'] for d in supervisors))
    return lines or [roster_label([])+': ']


def row_backgrounds(patients, colors):
    """Classic tables always alternate neutral rows, independent of team order."""
    return [colors['page'] if i%2==0 else colors['zebra'] for i in range(len(patients))]


def palette(options):
    # Android PdfPalette uses the app accent's light face in every appearance.
    options=normalize_options(options)
    rules=dict(rule=RULE_CONTRASTS[options['rule_contrast']][0],
               rule_width=RULE_WEIGHTS[options['rule_weight']],shading=options['row_shading'])
    from .tokens import ACCENTS
    title = ACCENTS[options['app_theme']][1]
    groups = ['#CFE8F0','#D8EEE2','#F0F0D2','#FAE3DC','#E0E2F4']
    return dict(title=title, header=title, summary='#F8FAFC',
                page='#FFFFFF', text='#1E293B', border='#E2E8F0', zebra='#F8FAFC', cream='#FFFFFF', groups=groups, **rules)


def context_lines(client, shift, options, now, include_shift_date=True, report_day=None):
    return ['تاريخ المناوبة: '+(report_day or current_date(client.zone,now))] if include_shift_date else []


def created_at(now):
    return 'أُنشئ في: '+now.strftime('%Y-%m-%d %H:%M %Z')


def assignment_groups(shift, doctors):
    """Teams are explicit registry membership, independent of patient assignments."""
    from .teams import resident_ids, validate_teams
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
        personal += '\n{} • {} سنة'.format(row['birthDate'], day.year-int(row['birthDate'][:4]))
    if row['hasCompanion']=='Y': personal+='\n(مرافق)'
    admit = row.get('admitNum','')
    if row.get('admitDate'):
        admit += '\n---\nيوم {}\n{}'.format(max(0,(day-datetime.fromisoformat(row['admitDate']).date()).days),row['admitDate'])
    diagnosis = [DIAGNOSIS[row['diagnosisType']]]
    for text, priority in badges_decode(row.get('warningDetails','')):
        level = {'low':'منخفضة','medium':'متوسطة','high':'عالية'}.get(priority)
        diagnosis.append('تنبيه: '+text+(' • أولوية '+level if level else ''))
    diagnosis.append(row.get('initialDiagnosis',''))
    team = '\n---\n'.join(filter(None,[doctor('responsibleSupervisorId'),doctor('responsibleResidentId')]))
    from .teams import is_cross_team_cover
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
