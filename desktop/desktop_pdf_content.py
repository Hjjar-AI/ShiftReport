"""Logical PDF content and Android's current palette; no shaping or saved-data edits."""
from __future__ import annotations
from datetime import datetime
import json
from desktop_model import DIAGNOSIS, GENDER, badges_decode
from desktop_protocol import ZoneInfo, require

PAPERS = {'A4': (595, 842), 'Letter': (612, 792), 'A5': (420, 595), 'A3': (842, 1191)}
DEFAULT_OPTIONS = dict(layout='table', paper='A4', orientation='portrait', color='teal', dark=False,
                       separate=False, ward='')


def normalize_options(options=None):
    result = dict(DEFAULT_OPTIONS, **(options or {}))
    require(result['layout'] in {'table','cards','paragraphs'} and result['paper'] in PAPERS and
            result['orientation'] in {'portrait','landscape'} and result['color'] in
            {'teal','blue','green','purple','pastel'}, 'خيارات PDF غير صالحة')
    require(type(result['dark']) is bool and type(result['separate']) is bool, 'خيارات PDF غير صالحة')
    return result


def palette(options):
    # Same colors and group sequence as Android PdfPalette.resolve.
    title, header = {'teal':('#0D5663','#146474'), 'blue':('#123F70','#174A7E'),
                     'green':('#214D39','#285943'), 'purple':('#493269','#563D7C'),
                     'pastel':('#8F2450','#4E568F')}[options['color']]
    if options['dark']:
        return dict(title=title, header=header, summary='#343B42', page='#202428', text='#F7F9FA',
                    border='#B8C0C8', zebra='#2B3137', cream='#272C31',
                    groups=['#304850','#344A40','#4A4632','#4D3936','#3D3E52'])
    groups = ['#CFE8F0','#D8EEE2','#F0F0D2','#FAE3DC','#E0E2F4']
    if options['color'] == 'pastel':
        groups = ['#FFADAD','#FFD6A5','#FDFFB6','#CAFFBF','#9BF6FF','#A0C4FF','#BDB2FF','#FFC6FF','#FFFFFC']
    return dict(title=title, header=header, summary='#FFFDFC' if options['color']=='pastel' else '#E0F2FE',
                page='#FFFFFF', text='#000000', border='#000000', zebra='#F2F2F2', cream='#FFFDF5', groups=groups)


def context_lines(client, shift, options, now):
    return ['المشفى: '+client.config['hospitalName'], 'الجناح: '+(options['ward'].strip() or 'غير محدد'),
            'تاريخ المناوبة: '+shift['meta']['shiftDate'], 'أُنشئ في: '+now.strftime('%Y-%m-%d %H:%M %Z')]


def assignment_groups(shift, doctors):
    """A supervisor's residents come from the explicit assignments on that team's patients."""
    groups = {}
    for patient in shift['patients']:
        supervisor = patient.get('responsibleSupervisorId') or None
        group = groups.setdefault(supervisor, dict(supervisorId=supervisor,
            supervisor=doctors.get(supervisor, {}).get('name', 'مشرف محفوظ' if supervisor else 'بدون مشرف'),
            residentIds=[], residents=[], patients=[]))
        resident = patient.get('responsibleResidentId')
        if resident and resident not in group['residentIds']:
            group['residentIds'].append(resident)
            group['residents'].append(doctors.get(resident, {}).get('name', 'مقيم محفوظ'))
        group['patients'].append(patient['name'])
    return list(groups.values())


def patient_content(row, doctors, zone, now):
    def doctor(key): return doctors.get(row.get(key), {}).get('name', 'طبيب محفوظ') if row.get(key) else ''
    personal = ('[أولوية] ' if row.get('isPriority')=='Y' else '')+row['name']
    personal += '\n{} · {} سنة · {}'.format(row['birthDate'], now.year-int(row['birthDate'][:4]), GENDER[row['gender']])
    if row['hasCompanion']=='Y': personal+='\n(مرافق)'
    admit = '{}\n---\nيوم {}\n{}'.format(row['admitNum'], max(0,(now.date()-datetime.fromisoformat(row['admitDate']).date()).days),row['admitDate'])
    diagnosis = [DIAGNOSIS[row['diagnosisType']]]
    for text, priority in badges_decode(row.get('warningDetails','')):
        level = {'low':'منخفضة','medium':'متوسطة','high':'عالية'}.get(priority)
        diagnosis.append('تنبيه: '+text+(' · أولوية '+level if level else ''))
    diagnosis.append(row.get('initialDiagnosis',''))
    team = '\n---\n'.join(filter(None,[doctor('responsibleSupervisorId'),doctor('responsibleResidentId')]))
    tasks = json.loads(row.get('tasksJson') or '[]')
    def task_key(task):
        overdue = not task.get('done') and task.get('dueAtEpochMillis') and task['dueAtEpochMillis']<=now.timestamp()*1000
        return (0 if overdue else 2 if task.get('done') else 1,
                float('inf') if task.get('done') else task.get('dueAtEpochMillis') or float('inf'),
                0 if task.get('done') else -{'LOW':0,'NORMAL':1,'HIGH':2}.get(task.get('priority','NORMAL'),1))
    task_lines=[]
    for task in sorted(tasks,key=task_key):
        status='مكتملة' if task.get('done') else 'متأخرة' if task.get('dueAtEpochMillis') and task['dueAtEpochMillis']<=now.timestamp()*1000 else 'معلقة'
        details=status+': '+task['description']
        if task.get('priority')=='HIGH':details+=' · أولوية عالية'
        if task.get('ownerDoctorId'):details+=' · '+doctors.get(task['ownerDoctorId'],{}).get('name',task.get('ownerName') or 'طبيب محفوظ')
        for key,label in [('dueAtEpochMillis','الموعد'),('completedAtEpochMillis','أُتمت في')]:
            if task.get(key):details+=' · '+label+' '+datetime.fromtimestamp(task[key]/1000,ZoneInfo(zone)).strftime('%Y-%m-%d %H:%M')
        if task.get('dueShiftDate'):details+=' · نهاية مناوبة '+task['dueShiftDate']
        if task.get('done') and task.get('completedByName'):details+=' · أتمها '+task['completedByName']
        task_lines.append(details)
    notes = '\n'.join(label+': '+value for label,value in
                     [('المتابعة',row.get('followUp','')),('المهام','\n'.join(task_lines)),('التحاليل',row.get('labs',''))] if value)
    return dict(personal=personal, admit=admit, diagnosis='\n'.join(filter(None,diagnosis)),team=team,
                treatment=row.get('treatmentPlan',''),followUp=row.get('followUp',''),labs=row.get('labs',''),
                tasks=task_lines,notes=notes)
