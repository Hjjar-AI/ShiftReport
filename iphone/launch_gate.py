"""Private native launch bootstrap in front of every shared WSGI route."""

import hashlib
import hmac
import json
import secrets
import threading
import time
from http.cookies import CookieError, SimpleCookie


BOOTSTRAP = "/_iphone/bootstrap"
AUTHORIZE = "/_iphone/authorize"
STATUS = "/_iphone/status"


def page(message):
    # Messages are fixed local strings, never transport exceptions or user input.
    return ('<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>تقرير المناوبة</title><body><p role="status">' + message + '</p></body></html>').encode("utf-8")


class LaunchGate:
    def __init__(self, application):
        self.application = application
        self.origin = None
        self.token = secrets.token_urlsafe(32)
        self.instance = secrets.token_hex(16)
        self.cookie_name = "shiftreport_iphone_" + self.instance
        self.cookie = secrets.token_urlsafe(32)
        self.condition = threading.Condition()
        self.active = 0
        self.closing = False

    def proof(self, challenge, purpose="response"):
        message = (purpose + ":" + challenge).encode("ascii")
        return hmac.new(self.token.encode("ascii"), message, hashlib.sha256).hexdigest()

    def _reply(self, start_response, status, body, content_type="text/html; charset=utf-8", extra=()):
        start_response(status, [
            ("Content-Type", content_type), ("Content-Length", str(len(body))),
            ("Cache-Control", "no-store"), ("X-Content-Type-Options", "nosniff"),
            ("Referrer-Policy", "no-referrer"), ("X-Frame-Options", "DENY"),
            ("Content-Security-Policy", "default-src 'none'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"),
        ] + list(extra))
        return [body]

    def __call__(self, environ, start_response):
        deny = lambda: self._reply(start_response, "403 Forbidden", page("افتح هذه الجلسة من مشغّل iPhone داخل Pyto."))
        if (self.origin is None or environ.get("REMOTE_ADDR") != "127.0.0.1"
                or "http://" + environ.get("HTTP_HOST", "") != self.origin
                or environ.get("wsgi.url_scheme") != "http"):
            return deny()
        origin = environ.get("HTTP_ORIGIN")
        if origin is not None and origin != self.origin:
            return deny()
        path, method = environ.get("PATH_INFO", ""), environ.get("REQUEST_METHOD", "")
        with self.condition:
            if self.closing:
                return self._reply(start_response, "503 Service Unavailable", page("يجري إغلاق الجلسة؛ لم تُعد أي عملية تلقائياً."))
        native = secrets.compare_digest(environ.get("HTTP_X_SHIFTREPORT_LAUNCH", "").encode("utf-8"), self.token.encode("ascii"))
        if path == BOOTSTRAP:
            if method != "GET" or environ.get("QUERY_STRING"):
                return deny()
            return self._reply(start_response, "200 OK", page("جارٍ فتح التجربة الوهمية داخل Pyto…"))
        if path == AUTHORIZE:
            if method != "POST" or not native or origin != self.origin or environ.get("QUERY_STRING"):
                return deny()
            cookie = "{}={}; HttpOnly; SameSite=Strict; Path=/".format(self.cookie_name, self.cookie)
            return self._reply(start_response, "204 No Content", b"", extra=(("Set-Cookie", cookie),))
        if path == STATUS:
            challenge = environ.get("HTTP_X_SHIFTREPORT_CHALLENGE", "")
            if (method != "GET" or environ.get("QUERY_STRING") or len(challenge) != 64
                    or any(c not in "0123456789abcdef" for c in challenge)):
                return deny()
            supplied = environ.get("HTTP_X_SHIFTREPORT_PROOF", "").encode("utf-8")
            if not secrets.compare_digest(supplied, self.proof(challenge, "request").encode("ascii")):
                return deny()
            body = json.dumps(dict(instance=self.instance, proof=self.proof(challenge))).encode("ascii")
            return self._reply(start_response, "200 OK", body, "application/json")
        try:
            cookies = SimpleCookie(environ.get("HTTP_COOKIE", ""))
            supplied = cookies[self.cookie_name].value if self.cookie_name in cookies else ""
        except CookieError:
            return deny()
        if not secrets.compare_digest(supplied.encode("utf-8"), self.cookie.encode("ascii")):
            return deny()
        return self._serve(environ, start_response)

    def _serve(self, environ, start_response):
        # Enter inside the iterator so a response abandoned before iteration
        # cannot leave a phantom in-flight operation. Recheck the drain barrier.
        with self.condition:
            admitted = not self.closing
            if admitted:
                self.active += 1
        if not admitted:
            yield from self._reply(start_response, "503 Service Unavailable", page("يجري إغلاق الجلسة."))
            return
        response = None
        try:
            response = self.application(environ, start_response)
            yield from response
        finally:
            try:
                if response is not None and hasattr(response, "close"):
                    response.close()
            finally:
                with self.condition:
                    self.active -= 1
                    self.condition.notify_all()

    def drain(self, timeout):
        deadline = time.monotonic() + timeout
        with self.condition:
            self.closing = True
            while self.active:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    self.closing = False
                    return False
                self.condition.wait(remaining)
            return True
