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
    totals = {"patients": len(patients), "tasks": sum(p["pending"] for p in patients), "teams": sum(d['role']=='اختصاصي' for d in doctors)}
    query = request.args.get("q", "").strip()[:200]
    team = request.args.get("team", "")
    pending_only = request.args.get("pending") == "1"
    sort = request.args.get("sort", "manual")
    if sort not in ("manual", "name", "admission"):
        sort = "manual"
    if query:
        patients = [p for p in patients if query.casefold() in " ".join(
            [p.get(field, "") for field in ("name", "admitNum", "initialDiagnosis", "treatmentPlan", "followUp", "labs")]
            + [task["description"] for task in p["tasks"]]
        ).casefold()]
    if team:
        patients = [p for p in patients if p["responsibleSupervisorId"] == team]
    if pending_only:
        patients = [p for p in patients if p["pending"]]
    return render_template("ward.html", patients=patients, doctors=doctors, totals=totals,
                           query=query, team=team, sort=sort, pending_only=pending_only,
                           day=report_day(), page="ward")


@pages.get("/tasks")
def tasks():
    patients, _ = patient_context()
    entries = [(p, task) for p in patients for task in p["tasks"]]
    entries.sort(key=lambda entry: entry[1]["done"])
    return render_template("tasks.html", entries=entries, page="tasks")


@pages.post("/patients/<patient_id>/tasks/<task_id>")
def update_task(patient_id, task_id):
    done = request.form.get("done")
    if done not in ("0", "1"):
        abort(400)
    if not g.workspace.set_task(patient_id, task_id, done == "1"):
        abort(404)
    return redirect(url_for("pages.tasks"), code=303)


@pages.get("/doctors")
def doctors():
    patients, roster = patient_context()
    for doctor in roster:
        doctor["patients"] = sum(doctor["id"] in (p["responsibleResidentId"], p["responsibleSupervisorId"])
                                 for p in patients)
    return render_template("doctors.html", doctors=roster, page="doctors")


@pages.get("/about")
def about():
    return render_template("about.html", page="about")


@pages.post("/demo/reset")
def reset():
    current_app.extensions["demo_sessions"].discard(session.get("sid"))
    session.clear()
    return redirect(url_for("pages.ward"), code=303)
