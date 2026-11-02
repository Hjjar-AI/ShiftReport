# iPhone host — initial source

This folder starts the [iPhone plan](../iphone.plan.md): a Pyto-hosted, dummy-only interface using the real Flask application in `web/`. It is source under development, not an installed or device-verified iPhone release. No dependency installation, distribution bundle or native app package is included.

Keep `iphone/`, `web/` and `samples/data/patients.csv` in their repository-relative locations. Templates, scripts, Tajawal fonts/license and the emblem load directly from `web/shiftreport/`; clinical screens and rules are not copied here.

## Entry points

Open `runtime_report.py` in the installed Pyto to obtain a compatibility inventory. It reports Python, importable packages and available version metadata, required UI members, fonts/sample resources, Damascus timezone data and HTTPS certificate availability. It prints no workspace content, credentials or private device paths. Record the installed Pyto release and edition separately; importability does not establish cryptographic, PDF or device compatibility.

`run.py` is the application entry point for Pyto's editor or a **Run Script** shortcut with **Show Console** enabled. Paths resolve relative to the script, independent of the shortcut's working directory. The launcher never installs dependencies or changes web pins. Missing dependencies/APIs produce a startup message. Use `web/requirements.txt` as the compatibility inventory; do not install desktop wheels over Pyto's bundled native libraries. Installation and packaging remain separate authorization scopes.

Source targets Python 3.10+ in Pyto. Required imports include Flask/Jinja/Werkzeug/Waitress, cryptography AES-GCM/PBKDF2, cffi, requests/certifi, ReportLab/Pillow/Arabic shaping, SQLite, `fcntl` and Pyto's bundled Rubicon Objective-C bridge. No compatible installed Pyto edition or package set has been established yet.

Pyto's documented foreground shortcut option and upstream UI APIs inform this adapter: [Shortcuts](https://pyto.readthedocs.io/en/latest/automation.html), [UI source](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/pyto_ui.py), [main-thread implementation](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/mainthread.py), [bundled Rubicon](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/rubicon/objc/api.py), [Apple activity notification](https://developer.apple.com/documentation/uikit/uiapplication/willresignactivenotification). Published source is separate from the installed release's behavior.

## Current source behavior

- A worker serves only `127.0.0.1` on an OS-selected port. A bounded, credential-authenticated challenge/response identifies the owned server before opening or reusing it. Flask debug/reload are disabled.
- The embedded WebView loads a credential-free bootstrap URL. Native JavaScript evaluation supplies a memory-only launch credential to a dedicated same-origin POST, which sets a separate opaque HttpOnly/SameSite cookie. No native command/message bridge is exposed. The shared application's peer/Host/Origin/CSRF/session guards remain in force.
- An explicit iPhone controller allowlist permits the shared memory-only ward, Add/Edit/drafts, details, tasks, dashboard, sorting, roster/team controls, appearance, About and dummy Fetch/publication simulations. File uploads, real-project connections/creation/identity and report/artifact routes are blocked on the server. Unsupported navigation shows an Arabic explanation; no HTTP request can mark a PDF as viewed through this host.
- One process-local host is reused only after its authenticated readiness response. A per-source-directory OS lock prevents a second host in another interpreter; that case gives a recoverable error rather than adopting an unknown listener. An existing visible presentation stays owned by its first launcher.
- The native sheet supplies Back/Check controls, a DEMO/status label and an opaque cover above the WebView. Native app inactivity covers content immediately; page visibility handling separately covers the shared interface and requests an opportunistic draft flush from its existing owner. No background execution or final write after termination is promised.
- Returning or choosing Check validates the owned server and the page's existing process-local workspace through a bounded read-only request. The correlation marker grants no authority. The native cover stays until both checks succeed; stale asynchronous callbacks cannot uncover a later inactive/navigation state. Loading failures leave the page/input untouched and show status without reload or mutation replay. Native startup/shutdown errors use Pyto alerts where available, with a console fallback.
- An iPhone-only template extension supplies an RTL drawer with focus containment, first-layout privacy cover, safe-area spacing, visual-viewport keyboard offsets and fixed wrapping editor actions. The shared editor supplies a single draft-flush hook, host UUID/progress callbacks and its existing Save/Cancel queue. Task/lab IDs use secure `getRandomValues` when this WebView lacks `randomUUID`; clipboard retains the shared selection fallback. No clinical content or session material is added to browser storage.
- Local POST progress prevents a second form submission. If a page survives an interrupted operation, successful reconnection leaves its outcome explicitly uncertain and offers a read-only route to inspect current data; it never reports that connection recovery proves Save succeeded. Native PDF/share routes remain blocked.
- Ordinary Save keeps the view open and changes only the fictional in-memory workspace. Dismissing the sheet requests a drain of active application responses before stopping this host's dispatcher/sockets and releasing its lock. If draining times out, ownership is retained and reopening can recover the same process-local host. There is no three-hour timeout or background-audio mechanism.

The temporary `shiftreport-iphone-demo-*` directory contains host coordination only, outside the code tree. It is **not** a selected durable clinical/key-storage location. Closing the demo session or process discards its clinical changes and drafts; existing cosmetic preferences remain owned by the shared browser code. No real workspace, join/password or connection retention is enabled.

## Owners and next work

| File | Responsibility |
|---|---|
| `run.py` | Single foreground entry point, safe startup errors and presentation/shutdown ownership |
| `runtime.py`, `runtime_report.py` | Shared source resolution and secret-free installed-runtime/resource inventory |
| `host.py` | Owned Waitress worker, lock, readiness, reuse and drained shutdown |
| `launch_gate.py` | Exact loopback origin, native bootstrap/cookie and operation drain barrier |
| `app.py` | Shared Flask factory plus a dummy-only controller/upload boundary |
| `pyto_host.py`, `lifecycle.py` | Native presentation/errors/activity cover and serialized bootstrap/session-check worker |
| `templates/base.html`, `static/iphone.css`, `static/iphone.js` | Host template extension, drawer/safe-area/editor layout and page interruption/progress handling |

The shared `base.html` has empty-by-default host slots; the iPhone Jinja loader extends it through the `shared/` prefix. Default web pages retain their existing template and editor behavior. There is no second clinical template/form owner.

Only Python/JavaScript/Jinja source syntax, resource/controller references, local documentation links and whitespace have been checked. Native alerts/notifications/scene behavior, WebView cookies/JavaScript, focus, safe areas and keyboard geometry have not been exercised on iPhone. No server, browser, runtime inventory, crypto/PDF operation, build/package or test suite was run for this work.

Remaining implementation stays in the [plan](../iphone.plan.md#remaining-implementation-sequence): installed-runtime/dependency decisions, protected private storage/restart and live lock/reconnection, Files adapters, reviewed native artifacts/A4 and distribution. These are unfinished phases, not removed requirements. Completed source and actual evidence/limits belong to [current state](../docs/status/current-state.md#iphone-host) and the [changelog](../docs/status/changelog.md).
