"""Memory-only native operations over the shared exact-artifact guards."""

import hashlib
import queue
import secrets
import threading
import time
from dataclasses import dataclass


@dataclass
class NativeOperation:
    workspace: object
    review: str
    index: int
    mode: str
    sha: str
    actor: str
    project: str
    created: float
    state: str = "queued"
    lease: object = None


class ArtifactOperations:
    OPEN_STATES = ('queued','running','review','share_requested','uncertain','failed_open')
    def __init__(self):
        self.lock = threading.RLock()
        self.queue = queue.Queue()
        self.items = {}
        self.worker_active = False

    def _require_owner(self, workspace, operation):
        from shiftreport.core.protocol import require
        workspace.ready()
        require(workspace.demo and workspace is operation.workspace
                and workspace.actor == operation.actor
                and workspace.client.project_id == operation.project,
                'العملية لا تخص جلسة التجربة المفتوحة')

    def payload(self, workspace, operation):
        from shiftreport.core.protocol import require
        from shiftreport.report_views import reviewed_artifact, artifact
        self._require_owner(workspace, operation)
        payload, name = reviewed_artifact(workspace, operation.review, operation.index,
            copying=operation.mode != "preview", mark_viewed=False)
        require(not artifact(workspace, operation.review)['value']['sending'],
                'واجهة الملفات الأولية لا تدعم تقارير الإرسال الحقيقي')
        require(payload['format'] == 'pdf' and isinstance(payload['raw'], bytes)
                and payload['raw'].startswith(b'%PDF-') and payload['sha'] == operation.sha,
                'الملف ليس نفس PDF المعاين')
        return payload['raw'], name

    def start(self, workspace, review, index, mode):
        from shiftreport.core.protocol import require
        from shiftreport.report_views import reviewed_artifact
        require(mode in ("preview", "save", "share"), 'عملية ملف غير صالحة')
        require(workspace.demo, 'عمليات الملفات الأولية للبيانات الوهمية فقط')
        payload, _ = reviewed_artifact(workspace, review, index,
            copying=mode != "preview", mark_viewed=False)
        operation = NativeOperation(workspace, review, index, mode, payload['sha'],
            workspace.actor, workspace.client.project_id, time.monotonic())
        self.payload(workspace, operation)
        with self.lock:
            require(self.worker_active, 'واجهة الملفات غير متاحة؛ أعد فتح المشغّل داخل Pyto')
            require(not any(item.state in self.OPEN_STATES for item in self.items.values()),
                    'أكمل أو ألغِ عملية الملف القائمة أولاً')
            # Retained Share leases are not discarded by this bounded history.
            require(len(self.items) < 32, 'بلغت الجلسة حد عمليات الملفات؛ أغلق الجلسة بعد إنهاء النوافذ الأصلية')
            key = secrets.token_hex(24)
            self.items[key] = operation
            self.queue.put(key)  # Only this opaque identifier reaches the native worker.
        return key

    def get(self, workspace, key):
        from shiftreport.core.protocol import require
        with self.lock:
            operation = self.items.get(key)
        require(operation is not None, 'عملية الملف غير متاحة')
        self._require_owner(workspace, operation)
        return operation

    def for_review(self, workspace, review):
        with self.lock:
            return [(key, item) for key, item in self.items.items()
                    if item.workspace is workspace and item.review == review]

    def claim(self):
        try:
            key = self.queue.get_nowait()
        except queue.Empty:
            return None
        with self.lock:
            operation = self.items[key]
            if operation.state != 'queued':
                return None
            operation.state = 'running'
            return key, operation

    def finish(self, key, state, lease=None):
        with self.lock:
            operation = self.items[key]
            operation.state = state
            operation.lease = lease

    def acknowledge(self, workspace, key, viewed):
        from shiftreport.core.protocol import require
        from shiftreport.report_views import reviewed_artifact
        operation = self.get(workspace, key)
        with self.lock:
            require(operation.mode == 'preview' and operation.state == 'review', 'انتهت مراجعة هذه المعاينة')
            if viewed:
                raw, _ = self.payload(workspace, operation)
                require(operation.lease is not None and operation.lease.digest() == hashlib.sha256(raw).hexdigest(),
                        'تغيرت النسخة الأصلية؛ أعد المعاينة')
                reviewed_artifact(workspace, operation.review, operation.index, mark_viewed=True)
            operation.state = 'reviewed' if viewed else 'failed'
            if operation.lease is not None:
                # The explicit acknowledgement also confirms native preview dismissal.
                try:
                    operation.lease.close()
                except OSError:
                    pass  # A cleanup failure does not undo an actual reviewed preview.
                else:
                    operation.lease = None

    def closed(self, workspace, key):
        from shiftreport.core.protocol import require
        operation = self.get(workspace, key)
        with self.lock:
            require(operation.state in ('share_requested','uncertain','failed_open'), 'هذه العملية لا تنتظر إغلاق نافذة أصلية')
            # A closed sheet is not delivery confirmation or proof that an
            # extension stopped reading. Keep its exact immutable temporary file.
            operation.state = 'closed'

    def cancel(self, workspace, key):
        from shiftreport.core.protocol import require
        operation = self.get(workspace, key)
        with self.lock:
            require(operation.state == 'queued', 'بدأت العملية الأصلية؛ لا يمكن إلغاؤها كطلب لم يبدأ')
            operation.state = 'cancelled'

    def pending(self):
        with self.lock:
            return any(item.state in self.OPEN_STATES for item in self.items.values())
