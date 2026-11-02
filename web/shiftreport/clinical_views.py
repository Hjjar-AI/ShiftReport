"""Clinical editors and registry administration; saves resolve current records."""
import copy
import json
import secrets
import uuid
from datetime import datetime
from flask import g, redirect, render_template, request, url_for
from .features import features, home, uploaded
from .core.protocol import require, packed, fingerprint, is_admin, current_date
from .core.model import new_patient, badges_decode
from .core.labs import read_groups
from .core.tasks import read_tasks


def form_values(form):
    value = {key:form.get(key,'').strip() for key in ('name','admitNum','admitDate','birthDate','gender','diagnosisType','initialDiagnosis','treatmentPlan','followUp','responsibleResidentId','responsibleSupervisorId')}
    if form.get('age','').strip() and not value['birthDate']:
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


@features.route('/patients/new', methods=['GET','POST'])
@features.route('/patients/<key>/edit', methods=['GET','POST'])
def patient_edit(key=None):
    w = g.workspace
    w.ready()
    new = key is None
    if request.method == 'POST':
        token = request.form.get('form_id','')
        state = w.forms.get(token)
        require(state is not None and state['actor']==w.actor, 'أُغلقت هذه المسودة؛ أعد فتح المحرر')
        key = state['id']
        values = form_values(request.form)
        if request.form.get('action') == 'draft':
            require(state['shift']==w.store.read()['meta']['shiftId'],'تغير القسم؛ لن تحفظ مسودة في قسم آخر')
            if not state['new']:
                w.require_patient(key,True)
            existing = w.store.patient_form_draft()
            require(not existing or existing.get('actor')==w.actor, 'هناك مسودة لمستخدم آخر؛ لا يمكن استبدالها')
            w.store.save_patient_form(dict(actor=w.actor,shiftId=state['shift'],patientId=key,isNew=state['new'],values=values))
            return '', 204
        if request.form.get('action') == 'cancel':
            w.forms.pop(token,None)
            return home()
        values['tasks'] = [t for t in values['tasks'] if t['description'].strip()]
        values['badges'] = [(text.strip(),level) for text,level in values['badges'] if text.strip()]
        w.save_patient(key,values,state['shift'],state['new'],int(request.form['position']) if request.form.get('position') else None)
        w.forms.pop(token,None)
        if request.form.get('action')=='next':
            rows = w.store.read()['patients']
            index = next(i for i,row in enumerate(rows) if row['externalId']==key)
            if index+1 < len(rows):
                return redirect(url_for('features.patient_edit',key=rows[index+1]['externalId']),code=303)
        return home()
    shift = w.store.read()
    pending = w.store.patient_form_draft()
    if request.args.get('resume')=='1':
        require(pending is not None and pending.get('actor')==w.actor and pending['shiftId']==shift['meta']['shiftId'], 'المسودة غير متاحة لهذا المستخدم والقسم')
        key, new = pending['patientId'], pending['isNew']
    elif pending and (pending.get('patientId')!=key or pending.get('actor')!=w.actor):
        return render_template('draft_recovery.html', pending=pending,page='ward')
    if new:
        row = new_patient(w.client.zone)
        row['externalId'] = key or row['externalId']
    else:
        _, row = w.require_patient(key,True)
    values = dict(row,tasks=read_tasks(row),lab_groups=read_groups(row),badges=badges_decode(row.get('warningDetails','')))
    if pending and pending.get('patientId')==row['externalId']:
        require(pending.get('actor')==w.actor, 'المسودة تخص مستخدماً آخر')
        values.update(pending['values'])
    token = secrets.token_urlsafe(24)
    w.forms[token] = dict(id=row['externalId'],new=new,shift=shift['meta']['shiftId'],actor=w.actor)
    index = next((i for i,p in enumerate(shift['patients']) if p['externalId']==row['externalId']),len(shift['patients']))
    return render_template('patient_edit.html', patient=values,doctors=w.client.doctors,form_id=token,
                           is_new=new,successor=not new and index+1<len(shift['patients']),page='ward')


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
            values={request.form.get('resident',''):request.form.get('supervisor','')}
        else:
            values={field:request.form.get(field,'') for field in ('first','last','gender','clinicalRole','telegram_id','destination','teamSupervisorId')}
            values['title']=source['doctors'].get(key,{}).get('title','')
        if w.demo:
            proposed=service.propose(source,action,key,values)
            payload=dict(doctors=proposed,original=fingerprint(w.client.doctors))
        else:
            payload=service.review(source,action,key,values)
        summary='سجل الأطباء بعد العملية:\n'+'\n'.join(d['name']+' • '+d['clinicalRole'] for d in payload['doctors'].values())
        def commit():
            if w.demo:
                require(fingerprint(w.client.doctors)==payload['original'],'تغير السجل؛ أعد المراجعة')
                w.client.doctors=payload['doctors']
            else:
                service.approve(payload)
                service.publish(payload)
        token=w.review('doctors',payload,summary,commit)
        return redirect(url_for('features.confirm_review',token=token),code=303)
    return render_template('doctor_manage.html',doctors=w.client.doctors,page='doctors')


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
