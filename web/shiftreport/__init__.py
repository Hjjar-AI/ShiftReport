"""Flask application factory. All mutable demo data stays in server memory."""

import secrets
from urllib.parse import urlsplit

from flask import Flask, abort, g, render_template, request, session

from .demo import DemoSessions


def create_app():
    app = Flask(__name__)
    app.config.update(
        SECRET_KEY=secrets.token_bytes(32),
        SESSION_COOKIE_NAME="shiftreport_web_demo",
        SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SAMESITE="Strict",
        MAX_CONTENT_LENGTH=64 * 1024,
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
        return {"csrf": workspace.csrf if workspace else ""}

    @app.errorhandler(400)
    @app.errorhandler(403)
    @app.errorhandler(404)
    @app.errorhandler(413)
    def request_error(error):
        return render_template("error.html", code=error.code), error.code

    from .views import pages

    app.register_blueprint(pages)
    return app
