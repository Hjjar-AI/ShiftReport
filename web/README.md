# Local Flask client

Independent **Python 3.8.10+ / Flask / Jinja** browser-client foundation for Windows and Linux. No imports from `desktop/`, no Node toolchain, no CDN assets. The launchers use a separate `web/.venv` and bind Waitress to `127.0.0.1:8765` only. Keep the repository's `samples/` folder beside `web/`.

This increment is a **fictional-data demo**, not a production clinical client or full desktop feature replacement. It provides the Arabic RTL ward, full sample clinical/lab/task details, search, supervisor filters, source/name/admission-duration sorting, bulk/individual expansion, team cards, task completion/undo and isolated cosmetic preferences. The 50 food/drink patients and ordered lab groups come directly from the shared CSV8 fixture; only demo IDs/team links are remapped into three linked teams (17/17/16).

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
```

Browse to `http://127.0.0.1:8765/` (or your selected port). Internet access is needed for initial package installation only. The app has no service worker; the Python process serves all pages locally.

## Python compatibility

`requirements.txt` pins the full dependency set, including Flask's `importlib-metadata`/`zipp` on Python <3.10 and Click's Windows-only `colorama`. [Flask 3.0.3](https://pypi.org/project/Flask/3.0.3/) and [Werkzeug 3.0.6](https://pypi.org/project/Werkzeug/3.0.6/) declare Python >=3.8; [Jinja 3.1.6](https://pypi.org/project/Jinja2/3.1.6/) supports >=3.7. [Waitress 3.0.0](https://pypi.org/project/waitress/3.0.0/) supports Python 3.8 and Windows/Linux; later 3.0.1/3.0.2 require Python 3.9. These are compatibility pins, not a claim of current upstream security maintenance. Keep this demo loopback-only; do not expose it as a shared server.

No existing app, wire, database, SDK or toolchain version is changed. The pinned set installed successfully in an isolated **CPython3.8.10** environment; dependency consistency, focused Flask requests and the Linux `run.sh`/Waitress real-HTTP workflow passed. Windows CPython3.8.10 x64 wheels resolved, including `colorama`; Windows execution and browser visuals remain unverified. See [remaining checks](../docs/status/testing-handoff.md).

## Data boundaries and extension points

- `shiftreport/__init__.py`: application factory, loopback peer/Host checks, same-origin/CSRF guards, no-cache and content-security policy. No debugger or development server in the launcher.
- `shiftreport/demo.py`: sample loading and bounded, locked, process-memory workspaces. Each browser session gets a separate workspace; task changes preserve source ownership/deadline/priority/stamps. Reset or server stop discards changes. Idle sessions expire after eight hours; at most sixteen are retained. Cookies contain only a signed opaque ID; no clinical data or credentials are stored in cookies.
- `shiftreport/views.py`: page routes and explicit desired-state task mutations, kept apart from storage and presentation. Clinical fields are read-only in this foundation. There is no real-project file picker or CSV upload route.
- `shiftreport/templates/`, `static/`: Jinja pages, buildless CSS/JS, bundled Tajawal and the original white-field emblem. Fonts/license and emblem are copied from Android assets, not from `desktop/`. About credits follow `android/app/src/main/res/values/strings_about.xml`.

Only demo appearance/accent/text size persist in browser storage under `shiftreport.web.demo.*`. Search, filters, spacing and expansion are presentation state. 08:30 Damascus time changes the displayed report-day label only, never patient rows or stored dates.

Real-project provisioning/protected join, encrypted clinical persistence/drafts, identity/PIN/admin/assignment guards, Add/Edit, recycle, backup/restore, reviewed A5 PDF artifacts and Telegram snapshot/receipt workflows are **not implemented**. Any future real-project adapter must preserve the existing below-UI guards, current formats, encryption, last-save-wins rules and reviewed exact-artifact/final-confirmation/UNKNOWN behavior. Do not connect a real project through this demo memory store. A future Vue client can use the same Python service boundary; this increment deliberately uses Jinja only.
