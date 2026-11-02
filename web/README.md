# Local Flask client

Independent **Python 3.8.10 / Flask / Jinja** local browser client for Windows and Linux. No runtime imports from `desktop/`, no Node toolchain and no CDN assets. Launchers use a separate `web/.venv` and bind Waitress to `127.0.0.1:8765` only. Keep the repository's `samples/` folder beside `web/`.

The recorded functional follow-up is implemented. The [functional map](../docs/reference/flask-capabilities.md) maps native desktop functions to browser routes; [current behavior/evidence](../docs/status/current-state.md#local-flask-client) and [remaining verification](../docs/status/testing-handoff.md#local-flask-client) distinguish source coverage from platform/live acceptance. Standalone live Telegram transport with dummy CSV/PDF/text and exact downloaded bytes passed. Normal live encrypted-project creation/private-code verification/protected recovery download/new index pin also passed on3November; Windows execution and cross-client Telegram acceptance are not claimed.

The original manual client remains at **http://127.0.0.1:8765/**. The new encrypted `TEST ONLY — Flask Dummy Project` runs at **http://127.0.0.1:8766/** on exact Python3.8.10, with one fictional administrator profile and no patients. Keep the setup browser session or reconnect using its downloaded protected join and normal identity verification. Its isolated data directory is ignored `web/instance/dummy-project-2026-11-03/`. It includes patient forms/recovery/recycle, task/team/roster administration, stored sort and presentation grouping, encrypted local storage/protected project setup/identity, CSV/backups/Fetch/Undo, exact A5 report preview/Save/Share and reviewed publication/delivery/receipts. Extra-copy approvals are durable and single use; local assignment export is independent of report roster changes.

Reports are PDF-only for general/supervisor delivery and local drafts; CSV data synchronization and current CSV tools remain. Text report creation, biometric unlock and legacy Excel/VBA import/export are outside the owner-confirmed Flask scope; Android can handle the one-time legacy import. The PDF-only source passed focused offline checks but has not been loaded into the running manual processes; restart deliberately with the protected join/password available, then reconnect normally.

Demo needs no credentials and starts with50 fictional food/drink patients,138 ordered lab groups and six tree doctors. Demo clinical changes stay in server memory. Real-project connections require the protected file/private identity workflow; no default or embedded real credentials are provided.

## Windows setup

From `web/`, with CPython 3.8.10 installed:

```bat
py -3.8 -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
run.cmd
```

If `py` is unavailable, use the full path to your Python 3.8.10 executable for the first command. No virtual-environment activation or PowerShell execution-policy change is needed.

## Linux setup

From `web/`, with Python 3.8.10 and its `venv` module installed:

```sh
python3.8 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
./run.sh
```

Both launchers open the default browser. Stop the server with **Ctrl+C** in its terminal; closing a browser tab leaves the server running. The launchers never install packages or change the existing desktop environment.

```sh
# From web/, after activating its environment (Windows: .venv\Scripts\python.exe):
python run.py --no-browser
python run.py --port 8766
# Optional separate local storage, including for a disposable check:
python run.py --no-browser --data-dir /path/to/disposable-directory
```

Browse to `http://127.0.0.1:8765/` (or your selected port). Internet access is needed for initial package installation only. The app has no service worker; the Python process serves all pages locally. The new launcher acquires one instance lock per data directory. The current manual8765server was launched from the expanded source and health-checked. Browser refresh does not reload Python code; after future source changes restart the server deliberately.

## Python compatibility

`requirements.txt` pins the full dependency set, including Flask's `importlib-metadata`/`zipp` on Python <3.10 and Click's Windows-only `colorama`. [Flask 3.0.3](https://pypi.org/project/Flask/3.0.3/) and [Werkzeug 3.0.6](https://pypi.org/project/Werkzeug/3.0.6/) declare Python >=3.8; [Jinja 3.1.6](https://pypi.org/project/Jinja2/3.1.6/) supports >=3.7. [Waitress 3.0.0](https://pypi.org/project/waitress/3.0.0/) supports Python 3.8 and Windows/Linux; later 3.0.1/3.0.2 require Python 3.9. These are compatibility pins, not a claim of current upstream security maintenance. Keep the client loopback-only; do not expose it as a shared server.

No existing native app, wire, database, SDK or toolchain version is changed. The full pinned dependency set installed as binary wheels on exact CPython3.8.10 and Windows x64 wheels resolved. Focused offline Flask/crypto/storage/backup/PDF/simulated-transport checks passed for their recorded source snapshots. The final expanded Linux launcher/instance-lock/port/shutdown checks, Python/Jinja/JS/shell/dependency checks, focused Firefox forms/presentation and simulated delivery/recovery checks passed. Full50-patient A5 outputs passed page-bound/DEMO checks. Native OS sharing and Windows execution remain outside those offline passes. Later live transport and encrypted creation/pin have separate evidence; full clinical publication/report delivery and cross-client acceptance remain unverified. [Actual evidence/limits](../docs/status/current-state.md#verification-and-limits).

## Source ownership and data boundaries

| Owner | Responsibility |
|---|---|
| `shiftreport/__init__.py` | Application factory, loopback peer/Host/origin/CSRF guards, server session setup, response policy and safe error handling |
| `shiftreport/workspace.py` | Browser-owned session/identity, per-project lock, encrypted-store refresh, patient mutations and ephemeral review/form owners |
| `shiftreport/features.py` | Protected connection, creation/private-code/PIN/lock and appearance/activity routes |
| `shiftreport/clinical_views.py` | Patient editor/draft/recycle, stored sorting/roster, doctor/team reviews and dashboard/details/diagnostics |
| `shiftreport/data_views.py` | CSV/RPB/SRDRAFT recovery, Fetch/Undo, publication/rollback/advanced administration and receipt resolution |
| `shiftreport/report_views.py` | Exact preview bytes, report/publication coordinator, downloads, original-artifact recovery, one-use additional-copy approvals and independent assignment exports |
| `shiftreport/core/` | Current-format headless services adapted from native protocol/crypto/store/PDF code, with native UI functions removed; independent copies, not a runtime dependency on `desktop/` |
| `shiftreport/demo.py`, `templates/`, `static/` | Shared fictional sample, bounded memory sessions, Jinja and buildless CSS/JS; bundled Tajawal/license and original emblem copied from Android |
| `run.py`, `shiftreport/instance.py` | Python version gate, local Waitress process and per-data-directory instance lock |

Real-project data defaults to ignored `web/instance/workspaces/`. Clinical state, drafts, enrollment and durable receipts use authenticated encryption; connection keys/tokens are not stored in clinical exports or browser clinical storage. Protected joins carry runtime connections. Unencrypted-network projects still need a protected local storage key: DPAPI on Windows or a Linux passphrase. Runtime passwords/connection secrets are not retained in form drafts/logs.

Demo uses no clinical database or transport. Only accent/appearance use browser storage, separately under `shiftreport.web.demo.*` and `shiftreport.web.live.*`. Demo text/motion/time/report settings stay in server memory; live text/motion/time/report defaults are encrypted locally. Demo reset/server stop discards clinical changes. Browser cookies hold an opaque signed session ID; mutable session/clinical/review state stays on the server. Idle sessions expire after eight hours; at most sixteen workspaces are retained in memory.

08:30 Damascus time affects report-day labels only. Source paths use current single-ward formats; no old-app conversion or schema/version change was added. PDF files remain readable, every dummy page is marked DEMO and Save uses retained preview bytes. Additional-copy permission is created for a displayed review and consumed durably with the new UNKNOWN receipt before transport. Replaying confirmation cannot create a fresh permission; the original UNKNOWN remains unresolved.

About wording follows `android/app/src/main/res/values/strings_about.xml`. Native source and unrelated existing edits were preserved. No persistent test suite was created or existing test files changed. The documented live harness was read for scope during the owner-authorized standalone Flask transport check, which sent only labelled dummy data and preserved the existing pin. Native build, packaging, staging and commit were not performed. See the evidence and remaining acceptance checklist above.
