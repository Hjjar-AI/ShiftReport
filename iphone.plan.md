# iPhone client proposal

Planning date: 2026-10-26. Status: proposed; no iPhone client implemented or device behavior verified. This document records requirements, implementation prescriptions and suggestions. It does not authorize builds, package installation, migrations, tests or changes to existing desktop/Android behavior.

[Documentation index](docs/README.md) · [Current implementation](docs/status/current-state.md) · [Shared protocol](docs/reference/telegram-protocol.md) · [Remaining-check owner](docs/status/testing-handoff.md)

## Intended experience and scope

Provide an Arabic, offline-first ShiftReport client on iPhone with quick startup and existing protected project/Telegram compatibility. Use ordinary `.py` files, accompanying modules and bundled web assets; a single launcher is the entry point, not a promise that the whole application fits in one file.

The owner expects sessions of up to approximately three hours, potentially shorter after saving. Continuous or indefinite background execution is unnecessary. Three hours is a design target, not demonstrated reliability and not a mandatory session timeout. Do not automatically close or discard work after three hours or after an ordinary Save.

Daily flow: tap a Home Screen shortcut → Pyto opens → launcher starts/reuses the local server → the interface opens inside Pyto → user unlocks and works. No daily terminal commands, dependency installation or manual browser address entry. One-time installation/import/shortcut setup remains necessary.

## Recommended architecture

Use Pyto with a lightweight Flask application and `pyto_ui.WebView` displaying its local URL. Keep the host foregrounded during ordinary interaction. Run server, storage, crypto, PDF and network work away from the native UI thread; use threads rather than depending on process spawning or Flask's development reloader. Disable debug mode and automatic reloading.

Retain CustomTkinter for the desktop client. Build mobile HTML/CSS/JavaScript screens separately, with reusable core operations beneath both interfaces. Inspect each reused module's imports and platform assumptions; a non-UI filename alone does not establish portability.

Keep platform adapters for private storage, secure key access, file import/export, PDF preview/share, lifecycle and launch coordination. Existing desktop adapters explicitly target Windows/Linux and cannot simply be used unchanged on iOS. Do not change shared wire formats, stored identifiers or schemas as a shortcut to portability.

Bundle fonts, icons, styles and scripts locally; no CDN requirement or JavaScript build step for users. Bind the server only to `127.0.0.1`. Authenticate each launch with an unpredictable session credential, validate Host/Origin and protect mutations against CSRF. Keep credentials out of URLs, access logs and browser history; avoid persisting clinical content or secrets in browser storage/caches. The encrypted Python workspace owns durable clinical state.

### Runtime and dependency prescriptions

| Component | Proposed choice / constraint |
|---|---|
| Python | Target the Python 3.10 runtime advertised by current Pyto. Existing desktop Python 3.8-compatible source is a useful reuse baseline; selecting older syntax does not lower Pyto's iOS requirement. |
| Device coverage | Current Pyto listing requires iOS 14 or later. Actual supported devices depend on App Store availability, installed Pyto release, resources and dependencies; no device matrix claimed. |
| Web framework | Prefer Flask for this local client. Django remains an unselected alternative; its extra machinery has no established need here. Choose exact dependency versions only after checking the installed runtime and bundled libraries. |
| Desktop UI | Tkinter/CustomTkinter cannot supply the intended native Pyto interface; replace screens for iPhone. |
| Pillow | Pyto source uses PIL and its widget documentation references PIL support. That is evidence of integration, not confirmation of every installed edition/version, image codec or optional feature. `ImageTk` still requires Tkinter and is unsuitable here. |
| Encryption | Preserve authenticated encryption and protected joins. Existing code uses `cryptography`/AES-GCM; the App Store lists `cryptography` among Full Version libraries. Treat edition availability and API compatibility as prerequisites. |
| PDF | Establish ReportLab, Pillow, Arabic shaping/bidi and bundled Tajawal compatibility before promising existing exports. Do not silently downgrade encryption or omit PDF functionality to claim parity. |
| Installation | Pure Python packages can be installed in Pyto; native extensions generally must already be bundled. Do not assume desktop wheels or an ordinary `pip install` can supply missing native dependencies. |

The Pyto website advertises a forthcoming runtime update. Base implementation choices on the installed shipping release, not upcoming features or repository `main` alone. Keep desktop dependency files and environments unchanged.

### Alternatives and lifecycle evidence

External Safari can display the local interface, but foregrounding Safari backgrounds Pyto. Pyto's `background.BackgroundTask` intentionally supports long-running scripts using repeated audio playback, silent by default. Its native implementation uses an audio playback session and loops the audio indefinitely. This makes Safari technically plausible; it does not establish uninterrupted operation on every iOS release or under calls, interruptions and memory pressure.

Prefer the embedded WebView because ordinary interaction then keeps Pyto foregrounded without requiring that audio mechanism. A background thread is not itself permission to run while iOS suspends the app. Locking the screen, leaving Pyto or terminating it must be treated as possible interruption. A three-hour maximum does not remove this distinction. Background fetch is discretionary scheduled work, not a continuously available local server.

iSH/Node.js is an unselected fallback: it adds an emulated Linux environment and separate package setup, loses direct Python reuse and retains iOS lifecycle constraints. A standalone native app is a possible later distribution decision, with a separate packaging/signing scope.

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

1. Establish a disposable, dummy-data launcher using the chosen Pyto runtime: local server lifecycle, embedded WebView, foreground shortcut and native error presentation. Resolve package feasibility and platform API availability before committing to full portability.
2. Add private workspace and key-storage adapters, protected file import and encrypted draft recovery. Reuse security/domain operations where compatible; do not introduce a schema or protocol change without separate authorization.
3. Implement mobile patient/task/detail screens, doctor selection and navigation using existing domain rules. Add remaining administration/data-management screens within the selected release scope.
4. Integrate PDF generation, native preview/share/export and existing backup operations. Preserve exact artifact identity across review, sharing and sending.
5. Integrate reviewed Telegram workflows, durable delivery receipts and interruption recovery with the existing shared protocol.
6. Prepare a versioned distributable application folder and concise one-time setup/update instructions, preserving user data and configuration outside replaceable application assets. Packaging and installation remain separately authorized work.

This plan records implementation work only. Any subsequently authorized remaining verification cases belong in [testing-handoff.md](docs/status/testing-handoff.md); evidence and actual limits belong in [current-state.md](docs/status/current-state.md). No checks or passes are implied by these phases.

## Main unresolved decisions and suggestions

- Retain the embedded WebView as the preferred interface; select Safari only if its benefits justify reliance on the audio-based background mechanism.
- Decide exact supported Pyto edition/release and package versions from available runtime evidence. Do not promise a free-only installation while required native libraries may require Full Version.
- Choose an available secure key-storage adapter and deliberate iOS file-protection/backup behavior. App sandbox storage alone is not a substitute for authenticated encryption.
- Resolve Arabic PDF dependencies early. Browser printing is only an alternative proposal, with potentially different pagination, links and artifact-review semantics; no automatic renderer substitution or parity claim.
- Prefer a small reusable Python core plus separate mobile presentation, retaining existing desktop/Android clients. Defer a standalone iOS app, hosted backend, PWA conversion and iSH distribution unless the owner selects them.

## Sources and evidence limits

Research reflects public sources inspected on 2026-10-26. Source/API evidence does not establish installed-binary behavior, sustained three-hour operation, device coverage, launch reliability or PDF parity. No physical iPhone execution has been performed for this proposal. Some published Pyto documentation pages are incomplete; the FAQ's Django example link returned 404 during research.

- [Pyto App Store listing](https://apps.apple.com/us/app/pyto-ide/id1436650069): shipping runtime description, minimum OS and edition/library information.
- [Pyto website](https://pyto.app/): forthcoming update notice; distinguish advertised future runtime features from shipping capabilities.
- [Pyto WebView source](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/pyto_ui.py#L5659): embedded web content, HTTP/HTTPS/file loading, JavaScript and loading callbacks. Repository main is not proof of exact installed-release contents.
- [Pyto UI guidance](https://pyto.readthedocs.io/en/latest/library/pyto_ui.html): presenting native UI in the app.
- [Shortcuts](https://pyto.readthedocs.io/en/latest/automation.html): Run Script and Show Console foreground launch option.
- [Third-party package restrictions](https://pyto.readthedocs.io/en/latest/third_party.html), [PIL widget reference](https://pyto.readthedocs.io/en/latest/library/widgets.html): bundled native dependencies and PIL references.
- [Python background API](https://github.com/ColdGrub1384/Pyto/blob/main/Lib/background.py), [native background implementation](https://github.com/ColdGrub1384/Pyto/blob/main/Pyto/Model/Background/BackgroundTask.swift): audio-based task lifetime and separate discretionary fetch API.
- [Apple background execution guidance](https://developer.apple.com/videos/play/wwdc2025/227/), [audio interruptions](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/HandlingAudioInterruptions/HandlingAudioInterruptions.html): suspension/resource constraints and interruption behavior.
- [iSH architecture](https://ish.app/blog/ish-jit-and-eu): emulated execution environment.
