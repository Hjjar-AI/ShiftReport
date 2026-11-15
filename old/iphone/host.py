"""Own one dummy-only loopback server; never adopt or stop another listener."""

import hashlib
import http.client
import json
import secrets
import tempfile
import threading
import time
from pathlib import Path

from .app import create_demo_app
from .artifacts import ArtifactOperations
from .launch_gate import LaunchGate, STATUS
from .runtime import ROOT, StartupError, require_runtime, shared_source


_lock = threading.RLock()
_host = None


class LocalHost:
    def __init__(self):
        self.presentation = threading.Lock()
        self.stopping = threading.Event()
        self.finished = threading.Event()
        self.socket_map = {}
        self.server = None
        self.instance_lock = None
        self.failed = False
        self.artifacts = ArtifactOperations()

    def start(self):
        self.runtime = require_runtime()
        shared_source()
        from shiftreport.instance import acquire
        from waitress import create_server

        # Coordination only. Real data/private key storage has no adapter yet.
        identity = hashlib.sha256(str(ROOT).encode("utf-8")).hexdigest()[:24]
        self.directory = Path(tempfile.gettempdir()) / ("shiftreport-iphone-demo-" + identity)
        try:
            self.instance_lock = acquire(self.directory)
        except SystemExit:
            raise StartupError("توجد جلسة أخرى تملك مشغّل iPhone. عد إليها أو أغلقها من Pyto ثم أعد المحاولة؛ لم يُنهَ أي مستمع آخر.") from None
        try:
            self.gate = LaunchGate(None)
            app = create_demo_app(self.directory, self.gate.cookie_name + "_session", self.artifacts)
            self.gate.application = app
            self.server = create_server(
                self.gate, host="127.0.0.1", port=0, threads=4,
                map=self.socket_map, asyncore_loop_timeout=0.2,
                log_socket_errors=False, expose_tracebacks=False,
            )
            self.port = int(self.server.effective_port)
            self.origin = "http://127.0.0.1:{}".format(self.port)
            self.gate.origin = self.origin
            self.thread = threading.Thread(target=self._serve, name="ShiftReport-iPhone", daemon=True)
            self.thread.start()
        except BaseException:
            if self.server is not None:
                self.server.task_dispatcher.shutdown()
                self.server.asyncore.close_all(self.socket_map)
            self.instance_lock.close()
            raise

    def _serve(self):
        try:
            while not self.stopping.is_set():
                if not self.socket_map:
                    self.failed = True
                    break
                self.server.asyncore.loop(timeout=0.2, count=1, map=self.socket_map)
        except Exception:
            self.failed = True
        finally:
            # A stopped socket loop must not release ownership under active work.
            with self.gate.condition:
                self.gate.closing = True
                while self.gate.active:
                    self.gate.condition.wait(0.2)
            try:
                self.server.task_dispatcher.shutdown()
                self.server.asyncore.close_all(self.socket_map)
            finally:
                self.instance_lock.close()
                self.finished.set()

    def ready(self, timeout=8):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline and not self.finished.is_set() and not self.failed:
            connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=min(0.5, max(0.01, deadline-time.monotonic())))
            try:
                challenge = secrets.token_hex(32)
                connection.request("GET", STATUS, headers={
                    "X-ShiftReport-Challenge": challenge,
                    "X-ShiftReport-Proof": self.gate.proof(challenge, "request"),
                })
                response = connection.getresponse()
                body = response.read(1025)
                if response.status == 200 and len(body) <= 1024:
                    result = json.loads(body)
                    proof = result.get("proof") if isinstance(result, dict) else None
                    if (isinstance(proof, str) and result.get("instance") == self.gate.instance
                            and secrets.compare_digest(proof.encode("utf-8"), self.gate.proof(challenge).encode("ascii"))):
                        return True
            except (OSError, ValueError, http.client.HTTPException):
                pass
            finally:
                connection.close()
            self.finished.wait(min(0.1, max(0, deadline-time.monotonic())))
        return False

    def stop(self):
        if self.finished.is_set():
            return True
        if self.artifacts.pending():
            return False
        if not self.gate.drain(timeout=8):
            return False
        if self.artifacts.pending():
            with self.gate.condition:
                self.gate.closing = False
            return False
        self.stopping.set()
        self.thread.join(timeout=7)
        return self.finished.is_set()


def open_host():
    global _host
    with _lock:
        if _host is not None and not _host.finished.is_set():
            if _host.ready(timeout=2):
                return _host
            raise StartupError("تعذّر تأكيد الجلسة القائمة؛ لم يبدأ خادم ثانٍ. عد إلى نافذة Pyto أو أغلق الجلسة ثم أعد المحاولة.")
        host = LocalHost()
        host.start()
        _host = host
        if not host.ready():
            host.stop()
            raise StartupError("لم يكتمل بدء الخادم المحلي ضمن المهلة. لم تُعد أي عملية أو رسالة تلقائياً.")
        return host
