"""Flask application factory. All mutable demo data stays in server memory."""

import secrets
from urllib.parse import urlsplit

from flask import Flask, abort, g, render_template, request, session, redirect, url_for

from .demo import DemoSessions


def create_app(data_directory=None):
    app = Flask(__name__)
    app.config.update(
        SECRET_KEY=secrets.token_bytes(32),
        SESSION_COOKIE_NAME="shiftreport_web_demo",
        SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SAMESITE="Strict",
        MAX_CONTENT_LENGTH=65 * 1024 * 1024,
        DATA_DIRECTORY=data_directory,
    )
    app.extensions["demo_sessions"] = DemoSessions()

    @app.before_request
    def local_session():
        # Check the actual peer and Host; never trust forwarded client headers.
        if request.remote_addr not in ("127.0.0.1", "::1"):
            abort(403)
        try:
            hostname = urlsplit("http://" + request.host).hostname
        except ValueError:
            abort(403)
        if hostname not in ("127.0.0.1", "localhost", "::1"):
            abort(403)
        if request.method not in ("GET", "HEAD", "OPTIONS"):
            origin = request.headers.get("Origin")
            if origin and origin != request.host_url.rstrip("/"):
                abort(403)
        if request.endpoint == "static":
            return None
        store = app.extensions["demo_sessions"]
        sid = session.get("sid")
        g.workspace = store.get(sid)
        if g.workspace is None:
            if request.method not in ("GET", "HEAD", "OPTIONS"):
                abort(400, description="انتهت الجلسة. افتح قائمة المرضى مجدداً.")
            sid, g.workspace = store.create()
            session.clear()
            session["sid"] = sid
        if request.method not in ("GET", "HEAD", "OPTIONS"):
            supplied = request.form.get("csrf", "")
            if not secrets.compare_digest(supplied.encode("utf-8"), g.workspace.csrf.encode("ascii")):
                abort(400, description="تعذّر تأكيد الطلب. أعد فتح الصفحة.")
        g.workspace_lock = g.workspace.lock
        g.workspace_lock.acquire()
        g.workspace.refresh()
        if not g.workspace.demo and not g.workspace.unlocked and request.endpoint not in (
            'features.auth', 'features.connect', 'features.create_project', 'features.creation',
            'features.enter_demo', 'features.join_review', 'pages.about', 'static'):
            return redirect(url_for('features.auth'), code=303)

    @app.teardown_request
    def release_workspace(error):
        held = getattr(g, 'workspace_lock', None)
        if held is not None:
            held.release()
            g.workspace_lock = None

    @app.after_request
    def response_policy(response):
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self'; "
            "img-src 'self'; font-src 'self'; connect-src 'self'; "
            "base-uri 'none'; frame-ancestors 'none'; form-action 'self'; object-src 'none'"
        )
        return response

    @app.context_processor
    def page_context():
        workspace = getattr(g, "workspace", None)
        lock_seconds=0
        if workspace and not workspace.demo and workspace.unlocked:
            from .core.access import LocalAccess
            lock_seconds=LocalAccess(workspace.store,workspace.client).timeout()
        return {"csrf": workspace.csrf if workspace else "", "workspace": workspace,
                "is_demo": workspace.demo if workspace else True,
                "notice": workspace.notice if workspace else '',"lock_seconds":lock_seconds}

    @app.errorhandler(400)
    @app.errorhandler(403)
    @app.errorhandler(404)
    @app.errorhandler(413)
    def request_error(error):
        return render_template("error.html", code=error.code), error.code

    from .views import pages
    from .features import features
    from .core.protocol import BridgeError

    @app.errorhandler(BridgeError)
    def workflow_error(error):
        return render_template('operation_error.html', message=str(error)), 400

    @app.errorhandler(Exception)
    def unexpected_error(error):
        from werkzeug.exceptions import HTTPException
        if isinstance(error, HTTPException):
            return error
        # Exception text/tracebacks can include transport URLs and credentials.
        app.logger.error('Workflow failed: %s', type(error).__name__)
        return render_template('operation_error.html', message='تعذّر إكمال العملية. لم تبدأ إعادة تلقائية؛ راجع سجل العمليات قبل محاولة إرسال جديدة.'), 500

    app.register_blueprint(pages)
    app.register_blueprint(features)
    return app
