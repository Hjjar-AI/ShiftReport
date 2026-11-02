"""Opt-in host boundary; shared clinical owners retain all mutations and drafts."""

import secrets

from .runtime import shared_source


# An explicit set keeps new shared controllers unavailable until reviewed here.
# Native artifacts stay pending: an HTTP load must not mark a PDF as previewed.
DEMO_ENDPOINTS = frozenset((
    "static", "pages.ward", "pages.tasks", "pages.update_task", "pages.doctors",
    "pages.about", "pages.reset", "pages.presentation", "pages.pin",
    "features.dismiss_intro", "features.appearance", "features.settings",
    "features.touch_session", "features.patient_edit", "features.discard_draft",
    "features.patient_delete", "features.recycle", "features.ward_options",
    "features.roster", "features.doctor_manage", "features.confirm_review",
    "features.dashboard", "features.patient_details", "features.diagnostics",
    "features.data", "features.sync",
    "iphone.static", "iphone.session_state",
))


def create_demo_app(directory, cookie_name):
    shared_source()
    from flask import Blueprint, g, jsonify, render_template, request
    from jinja2 import ChoiceLoader, FileSystemLoader, PrefixLoader
    from pathlib import Path
    from shiftreport import create_app

    app = create_app(directory)
    app.config.update(SESSION_COOKIE_NAME=cookie_name, DEBUG=False, TESTING=False)
    host_directory = Path(__file__).resolve().parent
    shared_templates = app.jinja_loader
    app.jinja_loader = ChoiceLoader([
        FileSystemLoader(str(host_directory / "templates")),
        PrefixLoader({"shared": shared_templates}), shared_templates,
    ])
    native = Blueprint("iphone", __name__, static_folder="static", url_prefix="/_iphone")

    def page_identity(workspace):
        if not hasattr(workspace, "iphone_page_identity"):
            # This correlation marker grants no authority and is never persisted.
            workspace.iphone_page_identity = secrets.token_hex(16)
        return workspace.iphone_page_identity

    @native.get("/session")
    def session_state():
        workspace = g.workspace
        supplied = request.headers.get("X-ShiftReport-Page", "").encode("utf-8")
        matches = secrets.compare_digest(supplied, page_identity(workspace).encode("ascii"))
        ready = matches and workspace.demo and workspace.unlocked and not workspace.store.locked
        return jsonify(ready=bool(ready)), 200 if ready else 409

    app.register_blueprint(native)

    @app.context_processor
    def host_context():
        workspace = getattr(g, "workspace", None)
        return {"iphone_page_identity": page_identity(workspace) if workspace else ""}

    @app.before_request
    def demo_boundary():
        workspace = getattr(g, "workspace", None)
        if (request.endpoint not in DEMO_ENDPOINTS
                or (workspace is not None and not workspace.demo) or request.files):
            return render_template(
                "operation_error.html",
                message="نسخة iPhone الأولية للتجربة الوهمية فقط. الاتصال بمشروع واستيراد الملفات ومعاينة التقارير ومشاركتها قيد التنفيذ.",
            ), 403

    return app
