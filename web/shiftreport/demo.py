"""Read the repository's fictional CSV8 fixture, never real project files."""

import base64
import copy
import csv
import json
import secrets
import threading
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path


SAMPLE = Path(__file__).resolve().parents[2] / "samples" / "data" / "patients.csv"


def report_day():
    # Current Damascus civil time is UTC+03:00; day labels change at 08:30 only.
    now = datetime.now(timezone(timedelta(hours=3)))
    return (now - timedelta(hours=8, minutes=30)).date().isoformat()


def sample_patients():
    with SAMPLE.open(encoding="utf-8-sig", newline="") as source:
        metadata = next(csv.reader(source))
        if not metadata[0].startswith("# schemaVersion=8 "):
            raise ValueError("The web demo requires the current fictional CSV8 sample.")
        rows = list(csv.DictReader(source))
    patients = []
    for index, row in enumerate(rows):
        # Keep all source columns and task metadata. Identity/team links are demo-local.
        patient = dict(row)
        patient["id"] = "demo-patient-{}".format(index)
        patient["responsibleResidentId"] = "demo-doctor-{}".format(index % 3)
        patient["responsibleSupervisorId"] = "demo-doctor-{}".format(3 + index % 3)
        patient["tasks"] = json.loads(row["tasksJson"])
        patient["lab_groups"] = json.loads(row["labGroupsJson"])
        patient["badges"] = []
        if row["warningDetails"].startswith("badges:"):
            for entry in row["warningDetails"][7:].split(";"):
                priority, encoded = entry.split(":", 1)
                label = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)).decode("utf-8")
                patient["badges"].append({"priority": priority, "text": label})
        patients.append(patient)
    return patients


def demo_doctors():
    names = ("أرز", "بلوط", "صنوبر", "زيتون", "صفصاف", "نخيل")
    return [
        {"id": "demo-doctor-{}".format(index), "name": "د. " + name,
         "role": "مقيم" if index < 3 else "اختصاصي",
         "supervisor_id": "demo-doctor-{}".format(index + 3) if index < 3 else None}
        for index, name in enumerate(names)
    ]


from .workspace import BrowserWorkspace as DemoWorkspace


class DemoSessions:
    """Bounded process-local sessions; cookies contain only an opaque session ID."""

    def __init__(self):
        self.lock = threading.RLock()
        self.items = {}

    def get(self, sid):
        with self.lock:
            self._expire()
            workspace = self.items.get(sid)
            if workspace:
                workspace.last_used = time.monotonic()
            return workspace

    def create(self):
        workspace = DemoWorkspace()
        with self.lock:
            self._expire()
            if len(self.items) >= 16:
                oldest = min(self.items, key=lambda key: self.items[key].last_used)
                del self.items[oldest]
            sid = secrets.token_hex(32)
            self.items[sid] = workspace
            return sid, workspace

    def discard(self, sid):
        with self.lock:
            self.items.pop(sid, None)

    def _expire(self):
        cutoff = time.monotonic() - 8 * 60 * 60
        expired = [sid for sid, item in self.items.items() if item.last_used < cutoff]
        for sid in expired:
            del self.items[sid]
