"""Jinja pages; demo mutations are separate from presentation and fixture loading."""

from flask import Blueprint, abort, current_app, g, redirect, render_template, request, session, url_for

from .demo import report_day


pages = Blueprint("pages", __name__)


def patient_context():
    patients, doctors = g.workspace.snapshot()
    doctor_names = {doctor["id"]: doctor["name"] for doctor in doctors}
    for patient in patients:
        patient["resident"] = doctor_names.get(patient["responsibleResidentId"], "")
        patient["supervisor"] = doctor_names.get(patient["responsibleSupervisorId"], "")
        patient["pending"] = sum(not task["done"] for task in patient["tasks"])
    return patients, doctors


@pages.get("/")
def ward():
    patients, doctors = patient_context()
    query = request.args.get("q", "").strip()[:200]
    team = request.args.get("team", "")
    pending_only = request.args.get("pending") == "1"
    from .core.patient_filters import matches_patient, ward_summary, CLINICAL_FILTERS
    from .core.sort import groups
    w = g.workspace
    totals = ward_summary(patients,w.actor,w.client.zone)
    totals['teams'] = sum(d['role']=='اختصاصي' for d in doctors)
    clinical = list(dict.fromkeys(value for value in request.args.getlist('clinical')
                                 if value in CLINICAL_FILTERS and value != 'الكل'))
    mine = request.args.get('mine')=='1'
    patients = [p for p in patients if (not team or p['responsibleSupervisorId']==team) and
                matches_patient(p,query,clinical,'مهام معلقة' if pending_only else 'كل المهام',
                                mine,w.actor,w.client.zone)]
    w.pinned.intersection_update(p['externalId'] for p in w.store.read()['patients'])
    grouped = groups(patients,w.preferences['grouping'],w.client.doctors)
    # Pin only within presentation groups; clinical/export ordering is untouched.
    display = []
    for label,items in grouped:
        items.sort(key=lambda pair:pair[1]['id'] not in w.pinned)
        rows = [row for _,row in items]
        display.append((label,rows))
    return_to=url_for('pages.ward',q=query or None,team=team or None,clinical=clinical,
                      pending='1' if pending_only else None,mine='1' if mine else None)
    round_token,_ = w.sequence(ids=[row['id'] for _,rows in display for row in rows],return_to=return_to)
    from .core.protocol import current_date
    return render_template("ward.html",patients=patients,doctors=doctors,totals=totals,
        query=query,team=team,pending_only=pending_only,clinical=clinical,mine=mine,
        clinical_filters=CLINICAL_FILTERS[1:],grouped=display,round_token=round_token,
        filters_active=bool(query or team or pending_only or clinical or mine),
        shift_id=w.store.read()['meta']['shiftId'],
        local_pending=w.pending_publication(),
        day=current_date(w.client.zone),page="ward")



@pages.get("/tasks")
def tasks():
    patients,doctors = patient_context()
    supervisor=request.args.get('supervisor','')
    resident=request.args.get('resident','')
    status=request.args.get('status','pending')
    if status not in ('all','pending','done'):
        status='pending'
    entries=[(p,t) for p in patients for t in p['tasks']
             if (not supervisor or p['responsibleSupervisorId']==supervisor) and
                (not resident or p['responsibleResidentId']==resident) and
                (status=='all' or t['done']==(status=='done'))]
    entries.sort(key=lambda entry:entry[1]['done'])
    return render_template('tasks.html',entries=entries,doctors=doctors,supervisor=supervisor,
        resident=resident,status=status,shift_id=g.workspace.store.read()['meta']['shiftId'],page='tasks')



@pages.post("/patients/<patient_id>/tasks/<task_id>")
def update_task(patient_id, task_id):
    done = request.form.get("done")
    if done not in ("0", "1"):
        abort(400)
    if not g.workspace.set_task(patient_id, task_id, done == "1",request.form.get("shift_id")):
        abort(404)
    return redirect(url_for("pages.tasks",supervisor=request.form.get('supervisor',''),
        resident=request.form.get('resident',''),status=request.form.get('status','pending')),code=303)


@pages.route("/doctors",methods=['GET','POST'])
def doctors():
    patients, roster = patient_context()
    from .core.patient_filters import normalize_search
    from .core.protocol import is_admin
    # Search may contain a Telegram account ID: keep it out of navigation URLs.
    values = request.form if request.method == 'POST' else request.args
    query = values.get('q','').strip()[:200]
    role = values.get('role','all')
    if role not in ('all','RESIDENT','SUPERVISOR','admin'):
        role = 'all'
    for doctor in roster:
        doctor['admin'] = is_admin(doctor)
        doctor["patients"] = sum(doctor["id"] in (p["responsibleResidentId"], p["responsibleSupervisorId"])
                                 for p in patients)
    visible = [doctor for doctor in roster if any(normalize_search(query) in normalize_search(value)
               for value in (doctor['name'],str(doctor.get('telegram_id') or ''))) and
               (role == 'all' or role == 'admin' and is_admin(doctor) or doctor['clinicalRole'] == role)]
    visible.sort(key=lambda doctor:(not is_admin(doctor),doctor['clinicalRole']!='SUPERVISOR',doctor['name']))
    return render_template("doctors.html", doctors=roster, visible=visible, query=query, role=role, page="doctors")


@pages.get("/about")
def about():
    return render_template("about.html", page="about")


@pages.get('/guide')
def guide():
    return render_template('guide.html',page='guide')


@pages.post("/demo/reset")
def reset():
    current_app.extensions["demo_sessions"].discard(session.get("sid"))
    session.clear()
    return redirect(url_for("pages.ward"), code=303)


@pages.post('/ward/presentation')
def presentation():
    from .core.protocol import require
    from .core.sort import GROUPS
    w=g.workspace
    w.ready()
    group=request.form.get('grouping','NONE')
    spacing=request.form.get('spacing','normal')
    require(group in GROUPS and spacing in ('normal','comfortable'),'خيار عرض غير صالح')
    w.preferences.update(grouping=group,spacing=spacing,card_controls=request.form.get('controls')=='1',
                         pinning=request.form.get('pinning')=='1')
    return redirect(url_for('pages.ward'),code=303)


@pages.post('/patients/<key>/pin')
def pin(key):
    from .core.protocol import require
    w=g.workspace
    w.require_patient(key)
    require(w.preferences['pinning'],'فعّل تثبيت البطاقات من خيارات العرض')
    if key in w.pinned:
        w.pinned.remove(key)
    else:
        w.pinned.add(key)
    return redirect(url_for('pages.ward',_anchor=key),code=303)
