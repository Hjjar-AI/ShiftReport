"""Clinical editors and registry administration; saves resolve current records."""
import copy
import json
import secrets
import uuid
from datetime import datetime
from flask import g, redirect, render_template, request, url_for
from .features import features, home, uploaded
from .core.protocol import BridgeError, require, packed, fingerprint, is_admin, current_date
from .core.model import new_patient, badges_decode
from .core.labs import read_groups
from .core.tasks import read_tasks


def form_values(form, validate=True):
    value = {key:form.get(key,'').strip() for key in ('name','admitNum','admitDate','birthDate','gender','diagnosisType','initialDiagnosis','treatmentPlan','followUp','responsibleResidentId','responsibleSupervisorId')}
    value['age'] = form.get('age','')
    value['position'] = form.get('position','')
    if validate and form.get('age','').strip() and not value['birthDate']:
        age = form['age'].strip()
        require(age.isascii() and age.isdigit() and 0 <= int(age) <= 120, 'العمر غير صالح')
        from .core.protocol import ZoneInfo
        value['birthDate'] = str(datetime.now(ZoneInfo(g.workspace.client.zone)).year-int(age))
    for key in ('hasCompanion','isPriority'):
        value[key] = 'Y' if form.get(key)=='Y' else 'N'
    ids, descriptions = form.getlist('task_id'), form.getlist('task_description')
    require(len(ids)==len(descriptions), 'حقول المهام غير متطابقة')
    completed = set(form.getlist('task_done'))
    value['tasks'] = [dict(id=key or str(uuid.uuid4()),description=text,done=key in completed)
                      for key,text in zip(ids,descriptions)]
    arrays = [form.getlist(key) for key in ('lab_id','lab_title','lab_date','lab_results')]
    require(len({len(a) for a in arrays})==1, 'حقول التحاليل غير متطابقة')
    value['lab_groups'] = [dict(id=key or str(uuid.uuid4()),title=title,date=date or None,results=results)
                          for key,title,date,results in zip(*arrays)]
    labels, priorities = form.getlist('badge_text'), form.getlist('badge_priority')
    require(len(labels)==len(priorities), 'حقول الشارات غير متطابقة')
    value['badges'] = list(zip(labels,priorities))
    return value


# Store only clinical form fields; never persist CSRF/session values.
FORM_FIELDS = ('name','admitNum','admitDate','birthDate','age','gender','diagnosisType',
    'initialDiagnosis','treatmentPlan','followUp','responsibleResidentId','responsibleSupervisorId',
    'hasCompanion','isPriority','position','task_id','task_description','task_done',
    'lab_id','lab_title','lab_date','lab_results','badge_text','badge_priority')


def editor_page(w, token, state, values, error=None):
    sequence = w.rounds.get(state['round'], {}).get('ids', [])
    index = sequence.index(state['id']) if state['id'] in sequence else len(sequence)
    return render_template('patient_edit.html',patient=values,doctors=w.client.doctors,
        form_id=token,is_new=state['new'],successor=not state['new'] and index+1<len(sequence),
        error=error,page='ward')


@features.route('/patients/new', methods=['GET','POST'])
@features.route('/patients/<key>/edit', methods=['GET','POST'])
def patient_edit(key=None):
    w = g.workspace
    w.ready()
    new = key is None
    shift = w.store.read()
    pending = w.store.patient_form_draft()
    if request.method == 'POST':
        token = request.form.get('form_id','')
        state = w.forms.get(token)
        require(state is not None and state['actor']==w.actor and
                pending and pending.get('formId')==token,
                'أُغلقت هذه المسودة أو فُتحت في نافذة أخرى؛ أعد فتح المحرر')
        require(state['shift']==shift['meta']['shiftId'],'تغير القسم؛ لن تحفظ مسودة في قسم آخر')
        require(new==state['new'] and (new or key==state['id']),'المحرر لا يخص هذا المريض')
        key = state['id']
        if not state['new']:
            w.require_patient(key,True)
        action = request.form.get('action','save')
        require(action in ('draft','cancel','save','next'),'عملية حفظ غير صالحة')
        raw = {field:request.form.getlist(field) for field in FORM_FIELDS}
        # Capture unfinished and invalid input before clinical validation, including
        # the final keystroke supplied with Save/Cancel. One durable form owns it.
        saved = dict(pending, raw=raw)
        from werkzeug.datastructures import MultiDict
        display = form_values(MultiDict(raw),validate=False)
        try:
            w.store.save_patient_form(saved)
            if action=='draft':
                return '',204
            if action=='cancel':
                w.forms.pop(token,None)
                return home()
            values = form_values(request.form)
            values['tasks'] = [t for t in values['tasks'] if t['description'].strip()]
            values['badges'] = [(text.strip(),level) for text,level in values['badges'] if text.strip()]
            position = request.form.get('position','')
            require(not position or position.isascii() and position.isdigit(),'موضع الإدراج غير صالح')
            w.save_patient(key,values,state['shift'],state['new'],int(position) if position else None)
        except BridgeError as error:
            return editor_page(w,token,state,display,str(error)),400
        except Exception:
            return editor_page(w,token,state,display,'تعذّر حفظ الإدخال. أبقِ الصفحة مفتوحة وأعد الحفظ.'),500
        w.forms.pop(token,None)
        if action=='next':
            _, sequence = w.sequence(state['round'])
            index = sequence.index(key) if key in sequence else len(sequence)
            if index+1<len(sequence):
                return redirect(url_for('features.patient_edit',key=sequence[index+1],round=state['round']),code=303)
        return home()
    if pending and request.args.get('resume')!='1':
        return render_template('draft_recovery.html',pending=pending,page='ward')
    if request.args.get('resume')=='1':
        require(pending is not None and pending.get('actor')==w.actor and
                pending['shiftId']==shift['meta']['shiftId'],'المسودة غير متاحة لهذا المستخدم والقسم')
        key,new = pending['patientId'],pending['isNew']
        # Resume always uses its own canonical endpoint, including Add recovery.
        if new != (request.view_args.get('key') is None) or (not new and request.view_args.get('key')!=key):
            return redirect(url_for('features.patient_edit',key=None if new else key,resume='1'),code=303)
    if new:
        row = new_patient(w.client.zone)
        row['externalId'] = key or row['externalId']
    else:
        _,row = w.require_patient(key,True)
    values = dict(row,tasks=read_tasks(row),lab_groups=read_groups(row),badges=badges_decode(row.get('warningDetails','')))
    if pending:
        if 'raw' in pending:
            from werkzeug.datastructures import MultiDict
            values.update(form_values(MultiDict(pending['raw']),validate=False))
        else:
            values.update(pending['values'])
    round_hint = (pending or {}).get('round')
    round_token,_ = w.sequence(request.args.get('round') or (round_hint if round_hint in w.rounds else None))
    token = secrets.token_urlsafe(24)
    state = dict(id=row['externalId'],new=new,shift=shift['meta']['shiftId'],actor=w.actor,round=round_token)
    w.store.save_patient_form(dict(actor=w.actor,shiftId=state['shift'],patientId=state['id'],
        isNew=new,values=values,formId=token,round=round_token))
    w.forms.clear()
    w.forms[token] = state
    return editor_page(w,token,state,values)


@features.post('/draft/discard')
def discard_draft():
    w = g.workspace
    w.ready()
    draft = w.store.patient_form_draft()
    require(not draft or draft.get('actor')==w.actor or is_admin(w.ready()), 'المسودة تخص مستخدماً آخر')
    w.store.save_patient_form(None)
    w.forms.clear()
    return home()


@features.post('/patients/<key>/delete')
def patient_delete(key):
    w = g.workspace
    shift,row = w.require_patient(key,True)
    w.store.delete_patient(key,fingerprint(shift))
    for token in [token for token,value in w.forms.items() if value['id']==key]:
        del w.forms[token]
    pending=w.store.patient_form_draft()
    if pending and pending.get('patientId')==key:
        w.store.save_patient_form(None)
    return home()


@features.route('/recycle', methods=['GET','POST'])
def recycle():
    w = g.workspace
    w.ready()
    w.store.purge_recycle_bin()
    if request.method == 'POST':
        w.store.restore_patient(request.form.get('key',''),fingerprint(w.store.read()))
        return home()
    return render_template('recycle.html',records=w.store.deleted_patients(),page='recycle')


@features.route('/ward/options', methods=['GET','POST'])
def ward_options():
    w = g.workspace
    w.ready()
    shift = w.store.read()
    from .core.sort import FIELDS, WORKBOOK_LEVELS, levels_for, ordered
    if request.method == 'POST':
        preset = request.form.get('preset')
        presets = dict(manual=[],name=[dict(field='NAME',direction='ASC')],admission=[dict(field='DAYS_OF_ADMITTANCE',direction='DESC')],
                       supervisor=[dict(field='SUPERVISOR',direction='ASC'),dict(field='NAME',direction='ASC')],workbook=WORKBOOK_LEVELS)
        if preset == 'custom':
            fields,directions=request.form.getlist('field'),request.form.getlist('direction')
            require(len(fields)==len(directions),'ترتيب غير صالح')
            levels=[dict(field=f,direction=d) for f,d in zip(fields,directions) if f]
        else:
            require(preset in presets,'ترتيب غير صالح')
            levels=presets[preset]
        shift['meta']['sortSpec'] = packed(dict(levels=levels))
        levels_for(shift)
        ordered(shift,w.client.doctors,w.client.zone)
        w.store.commit(draft=shift)
        return home()
    return render_template('sorting.html',fields=FIELDS,levels=levels_for(shift),page='ward')


@features.route('/ward/roster', methods=['GET','POST'])
def roster():
    w = g.workspace
    w.ready()
    shift=w.store.read()
    if request.method == 'POST':
        selected=request.form.getlist('doctor')
        require(len(set(selected))==len(selected) and all(k in w.client.doctors for k in selected),'اختر أطباء نشطين')
        shift['meta']['doctors']=','.join(selected)
        w.store.commit(draft=shift)
        return home()
    return render_template('roster.html',doctors=w.client.doctors,selected=shift['meta'].get('doctors','').split(','),page='doctors')


@features.route('/doctors/manage', methods=['GET','POST'])
def doctor_manage():
    w=g.workspace
    w.ready(online=not w.demo,admin=True)
    from .core.doctors import Administration
    service=Administration(w.client,w.store)
    if request.method=='POST':
        action=request.form.get('action','add')
        key=request.form.get('key') or None
        if w.demo:
            source=dict(doctors=copy.deepcopy(w.client.doctors),bundle=[w.store.read()])
        else:
            source=service.fetch()
        if action=='import':
            from .core.csv import decode_doctors
            values=decode_doctors(uploaded(4*1024*1024))
        elif action=='teams':
            values={key:request.form.get('team_'+key,'') for key,d in source['doctors'].items()
                    if d['clinicalRole']=='RESIDENT' and 'team_'+key in request.form and
                    request.form['team_'+key]!=(d.get('teamSupervisorId') or '')}
        else:
            values={field:request.form.get(field,'') for field in ('first','last','gender','clinicalRole','telegram_id','destination','teamSupervisorId')}
            values['title']=source['doctors'].get(key,{}).get('title','')
        if w.demo:
            proposed=service.propose(source,action,key,values)
            payload=dict(doctors=proposed,original=fingerprint(w.client.doctors))
        else:
            payload=service.review(source,action,key,values)
        summary='سجل الأطباء بعد العملية:\n'+'\n'.join(d['name']+' • '+d['clinicalRole'] for d in payload['doctors'].values())
        if action=='teams':
            names={uid:d['name'] for uid,d in source['doctors'].items()}
            summary='تغييرات عضوية الفرق؛ لا تتغير تعيينات المرضى:\n'+'\n\n'.join(
                names[uid]+'\n'+names.get(source['doctors'][uid].get('teamSupervisorId'),'دون فريق')+
                ' ← '+names.get(supervisor,'دون فريق') for uid,supervisor in values.items())
        def commit():
            if w.demo:
                require(fingerprint(w.client.doctors)==payload['original'],'تغير السجل؛ أعد المراجعة')
                w.client.doctors=payload['doctors']
            else:
                service.approve(payload)
                service.publish(payload)
        token=w.review('doctors',payload,summary,commit)
        return redirect(url_for('features.confirm_review',token=token),code=303)
    chosen=request.args.get('key')
    existing=w.client.doctors.get(chosen,{})
    name=existing.get('name','')
    if name.startswith('د.'):
        name=name[2:].strip()
    parts=name.split(' ',1)
    return render_template('doctor_manage.html',doctors=w.client.doctors,existing=existing,
                           first=parts[0],last=parts[1] if len(parts)>1 else '',page='doctors')


@features.route('/review/<token>', methods=['GET','POST'])
def confirm_review(token):
    w=g.workspace
    w.ready()
    review=w.reviewed(token)
    if request.method=='POST':
        require(request.form.get('confirm')=='1','الموافقة النهائية مطلوبة')
        review['used']=True
        review['action']()
        w.notice='اكتملت العملية محلياً.' if w.demo else 'اكتملت العملية. راجع سجل العمليات عند أي نتيجة غير مؤكدة.'
        return home()
    return render_template('review.html',review=review,page='review')

@features.get('/dashboard')
def dashboard():
    w=g.workspace
    patients,doctors=w.snapshot()
    from .core.reports import readiness_warnings
    totals=dict(patients=len(patients),tasks=sum(sum(not t['done'] for t in p['tasks']) for p in patients),
                companions=sum(p.get('hasCompanion')=='Y' for p in patients),priority=sum(p.get('isPriority')=='Y' for p in patients))
    totals.update(badges=sum(bool(p['badges']) for p in patients),
        urgent=sum(p.get('isPriority')=='Y' or any(b['priority']=='high' for b in p['badges']) for p in patients),
        admissions=sum(p.get('admitDate')==current_date(w.client.zone) for p in patients))
    groups=[]
    for d in doctors:
        if d['clinicalRole']=='SUPERVISOR':
            rows=[p for p in patients if p.get('responsibleSupervisorId')==d['uid']]
            groups.append(dict(id=d['uid'],name=d['name'],patients=len(rows),tasks=sum(sum(not t['done'] for t in p['tasks']) for p in rows)))
    return render_template('dashboard.html',totals=totals,groups=groups,page='dashboard')


@features.get('/patients/<key>')
def patient_details(key):
    w=g.workspace
    shift,row=w.require_patient(key)
    rows,_=w.snapshot()
    patient=next(p for p in rows if p['id']==key)
    names={k:d['name'] for k,d in w.client.doctors.items()}
    patient.update(resident=names.get(patient.get('responsibleResidentId'),''),supervisor=names.get(patient.get('responsibleSupervisorId'),''),pending=sum(not t['done'] for t in patient['tasks']))
    round_token,sequence=w.sequence(request.args.get('round'))
    require(key in sequence,'المريض ليس ضمن هذه الجولة')
    index=sequence.index(key)
    return render_template('patient_details.html',patient=patient,previous=sequence[index-1] if index else None,
                           successor=sequence[index+1] if index+1<len(sequence) else None,round_token=round_token,page='ward')


@features.get('/diagnostics')
def diagnostics():
    import platform,sys
    w=g.workspace
    w.ready()
    import importlib.metadata
    versions={name:importlib.metadata.version(name) for name in ('Flask','Werkzeug','cryptography','reportlab','waitress')}
    return render_template('diagnostics.html',python=sys.version.split()[0],system=platform.system(),versions=versions,page='about')
