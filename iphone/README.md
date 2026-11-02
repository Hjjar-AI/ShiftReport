# iPhone host — initial source

This folder starts the [iPhone plan](../iphone.plan.md): a Pyto-hosted, dummy-only interface using the real Flask application in `web/`. It is source under development, not an installed or device-verified iPhone release. No dependency installation, distribution bundle or native app package is included.

Keep `iphone/`, `web/` and `samples/data/patients.csv` in their repository-relative locations. Templates, scripts, Tajawal fonts/license and the emblem load directly from `web/shiftreport/`; clinical screens and rules are not copied here.

## Entry points

Open `runtime_report.py` in the installed Pyto to obtain a compatibility inventory. It reports Python, importable packages and available version metadata, required UI members, fonts/sample resources, Damascus timezone data and HTTPS certificate availability. It prints no workspace content, credentials or private device paths. Record the installed Pyto release and edition separately; importability does not establish cryptographic, PDF or device compatibility.

`run.py` is the application entry point for Pyto's editor or a **Run Script** shortcut with **Show Console** enabled. Paths resolve relative to the script, independent of the shortcut's working directory. The launcher never installs dependencies or changes web pins. Missing dependencies/APIs produce a startup message. Use `web/requirements.txt` as the compatibility inventory; do not install desktop wheels over Pyto's bundled native libraries. Installation and packaging remain separate authorization scopes.

Source targets Python 3.10+ in Pyto. Required imports include Flask/Jinja/Werkzeug/Waitress, cryptography AES-GCM/PBKDF2, cffi, requests/certifi, ReportLab/Pillow/Arabic shaping, SQLite, `fcntl`, Pyto's bundled Rubicon Objective-C bridge and `file_system` preview/export/share helpers. No compatible installed Pyto edition or package set has been established yet.

Pyto's documented foreground shortcut option and upstream UI APIs inform this adapter: [Shortcuts](https://pyto.readthedocs.io/en/latest/automation.html), [UI source](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/pyto_ui.py), [main-thread implementation](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/mainthread.py), [bundled Rubicon](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/rubicon/objc/api.py), [Apple activity notification](https://developer.apple.com/documentation/uikit/uiapplication/willresignactivenotification). Published source is separate from the installed release's behavior.

## Current source behavior

- A worker serves only `127.0.0.1` on an OS-selected port. A bounded, credential-authenticated challenge/response identifies the owned server before opening or reusing it. Flask debug/reload are disabled.
- The embedded WebView loads a credential-free bootstrap URL. Native JavaScript evaluation supplies a memory-only launch credential to a dedicated same-origin POST, which sets a separate opaque HttpOnly/SameSite cookie. No native command/message bridge is exposed. The shared application's peer/Host/Origin/CSRF/session guards remain in force.
- An explicit iPhone controller allowlist permits the shared memory-only clinical workflows, report/assignment generation and dummy Fetch/publication simulations. File uploads, real-project connections/creation/identity and raw browser artifact/download routes stay blocked. Native file operations use separate guarded POSTs; a file request or HTTP load alone cannot mark a PDF as viewed.
- One process-local host is reused only after its authenticated readiness response. A per-source-directory OS lock prevents a second host in another interpreter; that case gives a recoverable error rather than adopting an unknown listener. An existing visible presentation stays owned by its first launcher.
- The native sheet supplies Back/Check controls, a DEMO/status label and an opaque cover above the WebView. Native app inactivity covers content immediately; page visibility handling separately covers the shared interface and requests an opportunistic draft flush from its existing owner. No background execution or final write after termination is promised.
- Returning or choosing Check validates the owned server and the page's existing process-local workspace through a bounded read-only request. The correlation marker grants no authority. The native cover stays until both checks succeed; stale asynchronous callbacks cannot uncover a later inactive/navigation state. Loading failures leave the page/input untouched and show status without reload or mutation replay. Native startup/shutdown errors use Pyto alerts where available, with a console fallback.
- An iPhone-only template extension supplies an RTL drawer with focus containment, first-layout privacy cover, safe-area spacing, visual-viewport keyboard offsets and fixed wrapping editor actions. The shared editor supplies a single draft-flush hook, host UUID/progress callbacks and its existing Save/Cancel queue. Task/lab IDs use secure `getRandomValues` when this WebView lacks `randomUUID`; clipboard retains the shared selection fallback. No clinical content or session material is added to browser storage.
- Local POST progress prevents a second form submission. If a page survives an interrupted operation, successful reconnection leaves its outcome explicitly uncertain and offers a read-only route to inspect current data; it never reports that connection recovery proves Save succeeded.
- Ordinary Save keeps the view open and changes only the fictional in-memory workspace. Dismissing the sheet requests a drain of active application responses before stopping this host's dispatcher/sockets and releasing its lock. If draining times out, ownership is retained and reopening can recover the same process-local host. There is no three-hour timeout or background-audio mechanism.

The temporary `shiftreport-iphone-demo-*` directory contains host coordination only, outside the code tree. It is **not** a selected durable clinical/key-storage location. Closing the demo session or process discards its clinical changes and drafts; existing cosmetic preferences remain owned by the shared browser code. No real workspace, join/password or connection retention is enabled.

## Native PDF operations

Reports remain **A5 only**, as selected by the owner. Existing patient/assignment rendering, captured day, roster selection, DEMO/DRAFT labels and final dummy-publication confirmation stay with the shared controllers; no PDF renderer or page-size setting was changed.

The shared `report_views.reviewed_artifact` helper validates actor/project/review, current content/doctors/accent, exact bytes/hash, assignment authority and copy permissions. Native requests call it without marking viewed, enqueue only an opaque operation identifier for the native worker, and revalidate before exposing an immutable private temporary PDF. Page code cannot supply native paths, arbitrary Python or external URLs. Save/Share copy those retained bytes without rerendering.

Pyto's [file-system source](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/file_system.py) supplies `quick_look`, `save_as` and `share_files`. Its return does not establish that a preview was actually reviewed or a share delivered. After closing Quick Look, the user explicitly confirms either that the PDF appeared and was reviewed, or that it was not reviewed. Only the positive, still-valid confirmation marks the shared artifact viewed. Files export reports saved only after a separate returned copy can be read with the same hash; cancellation and uncertain outcomes remain separate. Share reports only that its dialog was requested, never delivery success.

Operations are memory-only, bounded to 32 per host session, serialized and never replayed by read-only status polling. Only a queued request can be cancelled. Pending native calls, preview acknowledgement and unresolved native windows hold host ownership; demo reset is blocked while one is open. Returning to the report lists its operations for recovery.

Owned `ShiftReport-iPhone-PDF-*` copies use private temporary directories/files. Preview copies are removed only after the user confirms the viewer closed; successful Files copies survive cleanup/bookkeeping failures. Share and uncertain native-consumer copies are deliberately retained because helper completion does not establish the consumer's lifetime. They are not automatically pruned on host stop, and exported files are never deleted. Callback-based native cleanup remains unfinished before real-data enablement. No file API or native presentation has been run on iPhone.

## Owners and next work

| File | Responsibility |
|---|---|
| `run.py` | Single foreground entry point, safe startup errors and presentation/shutdown ownership |
| `runtime.py`, `runtime_report.py` | Shared source resolution and secret-free installed-runtime/resource inventory |
| `host.py` | Owned Waitress worker, lock, readiness, reuse and drained shutdown |
| `launch_gate.py` | Exact loopback origin, native bootstrap/cookie and operation drain barrier |
| `app.py` | Shared Flask factory plus a dummy-only controller/upload boundary |
| `pyto_host.py`, `lifecycle.py` | Native presentation/errors/activity cover and serialized bootstrap/session-check worker |
| `artifacts.py`, `artifact_views.py`, `native_files.py` | Guarded operation queue, preview acknowledgement, exact-byte Files/Share adapters and owned temporary-copy lifetime |
| `templates/report_review.html`, `templates/artifact_operation.html`, `static/artifacts.js` | Native artifact controls and bounded read-only operation status |
| `templates/base.html`, `static/iphone.css`, `static/iphone.js` | Host template extension, drawer/safe-area/editor layout and page interruption/progress handling |

The shared `base.html` has empty-by-default host slots; the iPhone Jinja loader extends it through the `shared/` prefix. Default web pages retain their existing template and editor behavior. There is no second clinical template/form owner.

Only Python/JavaScript/Jinja source syntax, resource/controller references, local documentation links and whitespace have been checked. Native alerts/notifications/scene behavior, WebView cookies/JavaScript, focus, safe areas and keyboard geometry have not been exercised on iPhone. No server, browser, runtime inventory, crypto/PDF operation, build/package or test suite was run for this work.

Remaining implementation stays in the [plan](../iphone.plan.md#remaining-implementation-sequence): installed-runtime/dependency decisions, protected storage/restart/live lock, native consumer-completion cleanup, protected import/backup/live-artifact adapters and distribution. A4 is excluded by owner selection. Completed source and actual evidence/limits belong to [current state](../docs/status/current-state.md#iphone-host) and the [changelog](../docs/status/changelog.md).
