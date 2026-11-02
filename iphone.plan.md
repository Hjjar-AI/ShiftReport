# iPhone client — remaining implementation

Originally planned 2026-10-26; revised 2026-11-03 after reading the implemented `web/` client. Status: initial dummy-only Pyto host source now exists in [`iphone/`](iphone/README.md), following the owner's request to start creation. Installed-runtime decisions and later adaptations remain unfinished; no iPhone execution is verified. This document records requirements and remaining implementation. It does not authorize builds, package installation, migrations, tests or changes to existing web/old/desktop/Android behavior.

[Documentation index](docs/README.md) · [Web setup](web/README.md) · [Web capabilities](docs/reference/web-capabilities.md) · [Current implementation](docs/status/current-state.md) · [Shared protocol](docs/reference/telegram-protocol.md) · [Remaining-check owner](docs/status/testing-handoff.md)

## Intended experience and scope

Provide an Arabic, offline-first ShiftReport client on iPhone with quick startup and existing protected project/Telegram compatibility. Use ordinary `.py` files, accompanying modules and bundled web assets; a single launcher is the entry point, not a promise that the whole application fits in one file.

Use the existing local Flask client as the implementation base. It already supplies the clinical workflows and responsive Arabic presentation that the original proposal expected to create. The remaining iPhone work is a Pyto host and narrowly scoped platform adaptations, with shared screens/controllers retained. This reuse recommendation follows source inspection; Windows/Linux evidence does not establish iPhone compatibility.

The owner expects sessions of up to approximately three hours, potentially shorter after saving. Continuous or indefinite background execution is unnecessary. Three hours is a design target, not demonstrated reliability and not a mandatory session timeout. Do not automatically close or discard work after three hours or after an ordinary Save.

Daily flow: tap a Home Screen shortcut → Pyto opens → launcher starts/reuses the local server → the interface opens inside Pyto → user unlocks and works. No daily terminal commands, dependency installation or manual browser address entry. One-time installation/import/shortcut setup remains necessary.

## Recommended architecture

Use Pyto with a lightweight Flask application and `pyto_ui.WebView` displaying its local URL. Keep the host foregrounded during ordinary interaction. Run server, storage, crypto, PDF and network work away from the native UI thread; use threads rather than depending on process spawning or Flask's development reloader. Disable debug mode and automatic reloading.

Reuse `web/shiftreport/` directly: its Flask factory, Jinja templates, buildless assets, browser workspace and current-format core are independent of native desktop runtime imports. Keep one shared web implementation with explicit host capabilities; avoid copying it into a second iPhone clinical application. The archived CustomTkinter client remains in `old/desktop/`. Inspect reused imports and platform assumptions; independence from Tkinter does not establish iOS portability.

Keep platform adapters for private storage, secure key access, file import/export, PDF preview/share, lifecycle and launch coordination. The web launcher and key-selection branches currently target Windows/Linux and cannot simply be used unchanged on iOS. Do not change shared wire formats, stored identifiers or schemas as a shortcut to portability.

Bundle fonts, icons, styles and scripts locally; no CDN requirement or JavaScript build step for users. Bind the server only to `127.0.0.1`. Authenticate each launch with an unpredictable session credential, validate Host/Origin and protect mutations against CSRF. Keep credentials out of URLs, access logs and browser history; avoid persisting clinical content or secrets in browser storage/caches. The encrypted Python workspace owns durable clinical state.

### Existing web owners and remaining adaptations

| Existing source | Reuse and iPhone-specific work |
|---|---|
| [`__init__.py`](web/shiftreport/__init__.py) | Reuse `create_app(data_directory)`, peer/Host/Origin/CSRF checks, opaque session cookie, response privacy policy and safe errors. Add host launch authorization through a deliberate bootstrap; existing session creation alone does not authenticate the native launcher. |
| [`workspace.py`](web/shiftreport/workspace.py), [`core/store.py`](web/shiftreport/core/store.py), [`core/access.py`](web/shiftreport/core/access.py) | Retain project mutex, encrypted SQLite state, local enrollment/PIN, clinical guards and durable drafts/receipts. Supply private data/key adapters and protected cold-start reconnection without treating browser sessions as durable configuration. |
| [`clinical_views.py`](web/shiftreport/clinical_views.py), [`views.py`](web/shiftreport/views.py) | Reuse ward/details/tasks/dashboard, Add/Edit, recovery/recycle, captured order, sorting/roster and reviewed doctor/team actions. Adapt presentation for iPhone keyboard/navigation rather than rebuilding clinical rules. |
| [`features.py`](web/shiftreport/features.py), [`data_views.py`](web/shiftreport/data_views.py) | Reuse protected creation/join/identity, CSV/RPB/SRDRAFT tools, Fetch/Undo, sync/rollback and administration. Native file selection/export must call the same guarded controllers/services. |
| [`report_views.py`](web/shiftreport/report_views.py), [`core/`](web/shiftreport/core/) report/PDF/transport modules | Reuse exact retained preview bytes, review invalidation, publication/delivery, artifact hashes and durable UNKNOWN/one-use copy permission. Connect native preview/share without bypassing authority or review. Current normalization forces A5; A4 remains a gap to the original A4/A5 target until the owner changes scope. |
| [`templates/`](web/shiftreport/templates/), [`static/`](web/shiftreport/static/) | Reuse RTL markup, Tajawal, emblem, appearance tokens, responsive cards and form autosave. Add iPhone safe-area/keyboard handling and capability fallbacks for the selected WebView; current narrow Firefox evidence is separate. |
| [`demo.py`](web/shiftreport/demo.py), [`samples/data/patients.csv`](samples/data/patients.csv) | Reuse memory-only fictional patients/tree doctors. Preserve resource paths or supply an explicit sample path when distributing; never replace the fixture with real data. |
| [`run.py`](web/run.py), [`instance.py`](web/shiftreport/instance.py) | Use as launcher reference: Waitress thread server, explicit data root and instance lock. Add a separate Pyto entry point for embedded UI, startup readiness, same-instance reuse and coordinated shutdown; `webbrowser.open`/Ctrl+C are desktop behavior. |

Ownership: [`iphone/`](iphone/README.md) contains the initial launcher/host boundary importing the shared `web/shiftreport/` package. Later required adapter hooks should stay beside their existing web owners. Native imports belong only in the iPhone host; Windows/Linux launchers, pins and default behavior stay intact. No hosted backend or new clinical persistence layer is needed for this architecture.

### Runtime and dependency prescriptions

| Component | Proposed choice / constraint |
|---|---|
| Python | Target the Python 3.10 runtime advertised by the Pyto listing rechecked on 3 November. The web source's Python 3.8.10 baseline is reusable; keep its existing support and dependency pins unchanged. Selecting older syntax does not lower Pyto's iOS requirement. |
| Device coverage | Current Pyto listing requires iOS 14 or later. Actual supported devices depend on App Store availability, installed Pyto release, resources and dependencies; no device matrix claimed. |
| Web framework/server | Reuse Flask/Jinja and prefer the existing threaded Waitress approach if supported by installed Pyto. The current web pins are Flask 3.0.3, Werkzeug 3.0.6, Jinja 3.1.6 and Waitress 3.0.0. Treat them as the compatibility reference, not proof that Pyto supplies them. Django remains unselected. |
| Desktop UI | Keep Tkinter/CustomTkinter out of the iPhone import path; use the existing web screens inside the Pyto host. |
| Pillow | Pyto source uses PIL and its widget documentation references PIL support. That is evidence of integration, not confirmation of every installed edition/version, image codec or optional feature. `ImageTk` still requires Tkinter and is unsuitable here. |
| Encryption | Preserve authenticated encryption and protected joins. The web pins `cryptography` 42.0.7 and cffi 1.16.0; Pyto advertises cryptography as a Full Version library, but published package lists differ from these pins. Resolve the installed AES-GCM/PBKDF2/native-library compatibility before porting real workspaces. |
| PDF | Establish compatibility for the web's ReportLab 4.0.9, Pillow 9.5.0, arabic-reshaper 3.0.0, python-bidi 0.4.2 and bundled Tajawal. Preserve both Regular/Bold fonts and their license. Do not silently downgrade encryption or omit PDF functionality to claim parity. |
| Standard library/data | Account for SQLite, threading, locks and the `zoneinfo`/tzdata resources used by report-day labels. Retain HTTPS certificate verification and requests/certifi resources. Resource availability is separate from Python syntax compatibility. |
| Installation | Pure Python packages can be installed in Pyto; native extensions generally must already be bundled. Do not assume desktop wheels or an ordinary `pip install` can supply missing native dependencies. |

The Pyto website advertises a forthcoming runtime update. Base implementation choices on the installed shipping release, not upcoming features or repository `main` alone. Keep desktop dependency files and environments unchanged.

Use [`web/requirements.txt`](web/requirements.txt) as the dependency inventory, not an unconditional iOS installation command. Pyto's bundled native libraries may be older than its web equivalents; do not overwrite them or assume desktop wheels will load. If a separate iPhone dependency set is necessary, document the differences for owner approval without changing the existing web set. The app factory's workspace import reaches crypto even in demo, so a working HTML shell alone cannot establish that the actual application can start.

### Alternatives and lifecycle evidence

External Safari can display the local interface, but foregrounding Safari backgrounds Pyto. Pyto's `background.BackgroundTask` intentionally supports long-running scripts using repeated audio playback, silent by default. Its native implementation uses an audio playback session and loops the audio indefinitely. This makes Safari technically plausible; it does not establish uninterrupted operation on every iOS release or under calls, interruptions and memory pressure.

Prefer the embedded WebView because ordinary interaction then keeps Pyto foregrounded without requiring that audio mechanism. A background thread is not itself permission to run while iOS suspends the app. Locking the screen, leaving Pyto or terminating it must be treated as possible interruption. A three-hour maximum does not remove this distinction. Background fetch is discretionary scheduled work, not a continuously available local server.

iSH/Node.js is an unselected fallback: it adds an emulated Linux environment and separate package setup, loses direct Python reuse and retains iOS lifecycle constraints. A standalone native app is a possible later distribution decision, with a separate packaging/signing scope.

### Host integration requirements

**Launch and session.** Start the local server on a worker thread, wait for bounded readiness, then show the embedded view through Pyto's supported UI dispatch. Repeated launches must identify the same owned instance before reuse; an open port or existing `.server.lock` file is insufficient evidence. The current lock rejects a second process and has Windows/`fcntl` branches; it provides neither a Pyto relaunch handoff nor a readiness handshake. Never kill another listener or start another writer against the same data root. Keep the existing per-project mutex and PDF rendering lock.

The factory generates a fresh Flask signing key at startup, and browser workspace/configuration/reviews are process-local. A process restart invalidates the cookie and loses that attachment even though the encrypted clinical database survives. Implement protected connection retention/reopening before promising the daily “tap → unlock” flow: use an available secure iOS adapter, or explicitly select protected-join/password reentry. Preserve original keys, enrollment and pending work. Native launch authorization and doctor PIN/live Telegram authority are separate gates; neither grants the other's permissions. Bootstrap credentials must travel through a supported private host-to-WebView mechanism, not a query string or weakened Origin/CSRF checks.

**Storage and keys.** Pass an absolute private on-device data root to the factory, outside replaceable code, exported Files folders and temporary preview locations. The existing store uses the project data key when present; otherwise it selects Windows DPAPI or the non-Windows passphrase-wrapped `.linux-key` path. iOS must have an explicit adapter decision, including protection/backup/recovery behavior for both local keys and retained connections. A low-entropy PIN is an enrollment unlock mechanism, not a replacement encryption key. Missing protection material must leave existing records untouched. Do not assume Pyto's generic value-storage API is Keychain storage or conceal the Linux branch behind an iOS label.

**Files and reports.** Keep multipart import/controller validation for protected joins/current CSV/backups; adapt Files access to the installed host API. Provide native PDF preview and Share/Save for the already reviewed bytes, through an authenticated artifact operation owned by `report_views.py`. Validate project/actor/hash/review/destination and local-copy permissions before exposing the file; pass only the minimum operation identifier through any native bridge. The bridge must accept only the owned local origin and allowed operations, never arbitrary Python, paths or remote-page commands. Existing `target="_blank"`, attachment downloads and `navigator.share`/blob fallback are browser implementations, not native iPhone acceptance. Loading an artifact route is not proof that native preview opened successfully; preserve review state and show preview failures explicitly.

Use a private owned temporary file for Quick Look/share only when required, preserving the exact filename/bytes and readable PDF. Retain it for the native consumer's lifetime, then remove only that owned copy; interrupted cleanup must not delete exported files or workspaces. Report generation stays on the Python side. Start with the web client's PDF-only report flow and current CSV synchronization; text-report delivery, biometrics and legacy Excel/VBA conversion are outside that reuse baseline. The original A4/A5 requirement remains explicit; adding A4 must be scoped to the iPhone capability without silently altering the current shared web normalization.

**Presentation and lifecycle.** Retain the existing responsive templates and adjust the host layout for safe areas, keyboard-visible Save/Cancel, compact navigation and large Arabic/English text. The current mobile navigation wraps above the content and the editor footer is CSS-sticky; those implementations do not establish the intended iPhone drawer or keyboard behavior. Use capability fallbacks for selected-runtime APIs such as `crypto.randomUUID`, clipboard and file sharing rather than inferring support from an iOS 14 installation floor. Keep cosmetic browser storage isolated; clinical drafts, connection material and receipts remain Python-owned.

Add foreground/background handling in the host and page together: cover private content before leaving, revalidate server/session/lock state before showing it again and present a recoverable connection-loss state. Server idle enforcement already exists, while the page currently uses activity timers; neither is a native lifecycle/privacy cover. The editor currently queues draft autosave after 600ms and serializes Save/Cancel behind pending requests. Reuse its single-form ownership and explicit durable-save semantics; save pending draft input opportunistically on interruption, without promising a final callback/write after termination. Show progress for long PDF/network operations, retain receipts and never automatically replay a mutation or Telegram send on reload/resume.

## Features to preserve

- Protected `.srjoin.json` import with password; online first enrollment and existing authorization rules; subsequent offline unlock. Reconnect/token replacement must validate project/bot/topics/key before replacement and retain local records and pending work.
- Continuous current ward, existing IDs/dates and 08:30 display/report-day semantics. No automatic rollover, date-based editing lock or new dated-list browser.
- Patient list, search/filter/sort, compact cards and expandable details; previous/next navigation follows visible ordering. Preserve expanded clinical content and existing dashboard/task totals.
- Add/Edit patients with plain multiline diagnosis, treatment, follow-up and labs; demographics first in Add, clinical/tasks first in Edit. Retain drafts including unfinished inline task text, fixed keyboard-safe Save/Cancel and Save and next only with a successor.
- Explicit text/done tasks, doctor/team/assignment controls and role-aware administration. Preserve legacy fields without introducing editor/completion stamps, task attribution or clinical conflict prompts. Clinical Save remains last-save-wins with existence/deletion/assignment checks.
- Encrypted offline workspace, recoverable drafts, backup/restore and Fetch Undo. New storage/key adapters must retain existing security guarantees; missing keys or corrupt data must produce explicit errors without resetting records.
- Reviewed complete-snapshot Telegram fetch/publication with current authority checks, existing pending-change guards and exact captured content/destinations. Preserve last-successful-write-wins behavior without claiming atomic multi-writer synchronization.
- Report flow begins with shift-doctor selection, then exact PDF preview and final confirmation. Keep durable UNKNOWN delivery outcomes, explicit extra-copy approval and existing receipts. Never automatically resend after an interruption.
- A4/A5 Arabic PDF, report/patient links, existing names/captions and every-page draft/demo labels where applicable. Ordinary Save/Share copies the previewed artifact without rerendering. Preserve the separate local assignments export and optional local copy after confirmed report delivery.
- Arabic-first RTL, 123 digits, mixed Arabic/English text, labelled generous touch targets, large-text wrapping, visible navigation, appearance/reduced-motion options, existing logo/emblem/About credits. Use mobile CSS tokens corresponding to the shared design rather than importing Tk widgets.

These are intended capabilities, not a claim that the first prototype or initial release has full feature parity. Do not quietly drop features; record any proposed reduced scope for owner selection.

## Save, interruption and closing behavior

Explicit Save commits clinical changes durably before showing success. Draft preservation is separate: interrupted typing must not silently become a clinical Save or a Telegram publication. Restore recoverable drafts under the appropriate account/project after unlocking. Protect draft contents at rest and preserve the current discard semantics.

Ordinary Save keeps the application open. Suggested additional action: **Save and close**, which completes the intended local save before stopping this client session. If saving fails, retain the draft and show an error instead of reporting successful closure. Saving locally is distinct from publishing to Telegram. Do not stop a server mid-publication or convert an uncertain send into confirmed delivery.

Make startup idempotent: a second launch should reconnect to this application's existing server/session, or give a recoverable startup error; do not create competing writers or terminate unrelated listeners. Coordinate shutdown with outstanding operations and retain durable receipts before external side effects.

On return from background, cover private content until the lock decision is complete, refresh the connection/session safely and recover local state. A closed WebView or terminated process must not erase saved data. Surface server loss and save failures clearly; never leave a frozen page implying that a save succeeded. Do not claim guaranteed draft recovery before durable draft writes complete.

## Remaining implementation sequence

1. **Runtime feasibility and host boundary.** Obtain installed Pyto edition/release and the new runtime inventory; establish compatible native packages and required UI/file APIs. Resolve a dependency proposal and define the remaining adapters against the existing web owners. If crypto/PDF or secure storage is unavailable, report that blocker before committing to real-data portability; do not reduce functionality silently.
2. **Complete installed-host integration.** Resolve installed-release differences in the launcher/WebView/bootstrap/activity-notification/owned-instance adapters and connect the foreground shortcut on the selected host. The source currently reads repository sample/assets directly; distribution bundling remains phase 6. Keep artifact routes blocked until native preview succeeds through phase 5; retain all planned clinical capabilities. No clinical-screen rewrite is part of this phase.
3. **Protected restart and storage.** Add private paths, explicit key/connection retention and file import adapters. Reuse protected join review, first online enrollment, later offline PIN unlock and encrypted form recovery. A cold start must recover the correct saved workspace without clearing records or granting online authority. No schema or protocol change without separate authorization.
4. **Complete protected-workspace presentation and interruptions.** Connect the current dummy host's privacy/session revalidation and shared-owner draft/progress hooks to the selected secure storage/connection and clinical-lock adapters. Complete any selected-runtime presentation adapters required by its native UI APIs, retaining stale-form rejection, Save-next order, keyboard-safe actions and explicit Save behavior. Clinical recovery and durable UNKNOWN must survive interruptions without automatic mutation replay.
5. **Native artifacts and reviewed delivery.** Connect existing PDF/assignment/backup operations to native preview, Files and sharing. Preserve exact bytes, receipt recovery and one-use copy permission. Resolve the explicit A4 gap while preserving existing A5 geometry. Adapt the existing reviewed Telegram coordinator for interruptions; do not create a second publication workflow or automatic retry policy.
6. **Distribution and updates.** Prepare a distributable application folder and concise one-time setup/update instructions only under separate packaging/installation authorization. Include shared package/templates/static fonts/license/emblem, fictional CSV and required package data. Keep user data/keys/configuration outside replaceable assets and preserve installed dependency/support decisions; no app/toolchain/schema version change is authorized by this plan.

This plan records implementation work only. Any subsequently authorized remaining verification cases belong in [testing-handoff.md](docs/status/testing-handoff.md); evidence and actual limits belong in [current-state.md](docs/status/current-state.md). No checks or passes are implied by these phases.

## Main unresolved decisions and suggestions

- Retain the embedded WebView as the preferred interface; select Safari only if its benefits justify reliance on the audio-based background mechanism.
- Decide exact supported Pyto edition/release and package versions from available runtime evidence. Do not promise a free-only installation while required native libraries may require Full Version.
- Choose an available secure key-storage adapter, deliberate iOS file-protection/backup behavior and protected cold-start connection retention. App sandbox storage alone is not a substitute for authenticated encryption; repeated protected-join entry remains the explicit fallback proposal if retention is unavailable.
- Resolve Arabic PDF dependencies early. Browser printing is only an alternative proposal, with potentially different pagination, links and artifact-review semantics; no automatic renderer substitution or parity claim.
- Reuse the existing Flask package, core and responsive presentation with a small iPhone host; retain existing web/old/desktop/Android clients. Keep the original A4 requirement visible until its implementation or an owner scope decision. Defer a standalone iOS app, hosted backend, PWA conversion and iSH distribution unless the owner selects them.

## Sources and evidence limits

The original external research dates to 2026-10-26. On 2026-11-03, the App Store listing, Pyto website, UI/WebView source, package restrictions, Shortcuts and file-system documentation were rechecked alongside production source in `web/`. Initial host creation additionally consulted upstream main-thread/UI and pinned Waitress source. The local ownership map above is the basis for reuse; existing web evidence remains in current state, not duplicated here. Initial iPhone work added production source/documentation only: no app launch, dependency installation, build/package, test or migration inspection/execution, or live Telegram operation. Actual source checks and limits are recorded in current state.

Source/API evidence does not establish installed-binary behavior, sustained three-hour operation, device coverage, launch reliability or PDF parity. No physical iPhone execution has been performed for this proposal. Some published Pyto documentation pages are incomplete; the FAQ's Django example link returned 404 during the original research.

- [Pyto App Store listing](https://apps.apple.com/us/app/pyto-ide/id1436650069): shipping runtime description, minimum OS and edition/library information.
- [Pyto website](https://pyto.app/): forthcoming update notice; distinguish advertised future runtime features from shipping capabilities.
- [Pyto WebView source](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/pyto_ui.py#L5659): embedded web content, HTTP/HTTPS/file loading, JavaScript and loading callbacks. Repository main is not proof of exact installed-release contents.
- [Pyto UI guidance](https://pyto.readthedocs.io/en/latest/library/pyto_ui.html): presenting native UI in the app.
- [Shortcuts](https://pyto.readthedocs.io/en/latest/automation.html): Run Script and Show Console foreground launch option.
- [Third-party package restrictions](https://pyto.readthedocs.io/en/latest/third_party.html), [PIL widget reference](https://pyto.readthedocs.io/en/latest/library/widgets.html): bundled native dependencies and PIL references.
- [File-system guidance](https://pyto.readthedocs.io/en/latest/library/file_system.html): import/export, Share Sheet, Quick Look and external-file bookmarks; this overview does not establish installed API signatures or successful artifact handling.
- [Python background API](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/background.py), [native background implementation](https://github.com/ColdGrub1384/Pyto/blob/main/Pyto/Model/Background/BackgroundTask.swift): audio-based task lifetime and separate discretionary fetch API.
- [Apple background execution guidance](https://developer.apple.com/videos/play/wwdc2025/227/), [audio interruptions](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/HandlingAudioInterruptions/HandlingAudioInterruptions.html): suspension/resource constraints and interruption behavior.
- [iSH architecture](https://ish.app/blog/ish-jit-and-eu): emulated execution environment.
