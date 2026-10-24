# Current implementation

[Docs](../README.md) · [Architecture](../reference/architecture.md) · [Remaining work](roadmap.md) · [Completed work](changelog.md) · [Optional UI](../decisions/android-ui-options.md)

## Product and source layout

Arabic offline-first handover; manually reviewed Telegram snapshots/reports. Android `android/`: Kotlin 1.9/Java 17, Compose BOM 2023.10.01, SDK 34/min23; desktop `desktop/`: CustomTkinter. Versions unchanged. Android `ui/` → Hilt ViewModels → `domain/`/repositories (Room/SQLCipher); `network/telegram/` transport, `sync/` orchestration. [Protocol](../reference/telegram-protocol.md).

## Security and project access

- Protected file-only join; manual group/topics at creation. Optional authenticated Telegram encryption with protected shared key; PDFs readable.
- Android existing-pin override binds reviewed pin/config and live first-admin authority; rechecks before seeding. New reference preserves old history, imports no old data, cannot replace initialized local enrollment; other devices need new join files. Pinning remains non-atomic.
- Pre-login same-project reconnect validates candidate credentials, retains clinical/pending/topic/key state and blocks substitutions. BotFather revokes tokens.
- Clinician PIN/throttling/optional biometrics; admin checks below UI. Secrets excluded from saved state/logs/exports. Missing DB key preserves DB/WAL/SHM/journals; IO checks precede repository ViewModels. Demo never opens clinical DB.
- `FLAG_SECURE` excludes external PDFs. Private previews use best-effort cleanup; clipboard codes carry sensitive metadata and matching-nonce expiry/disposal, subject to background restrictions.
- Protected selected debug key/cert and offline device update passed; second-account login unverified. Release has no debug fallback; owner production key/certificate outstanding. [Signing/cache](../guides/android-build.md).

## Synchronization and publication

Retrieve → local edit → review → complete-snapshot publish; last successful pinned write wins. No automatic sync, field merge, atomic multi-writer guarantee or required backend. Timestamps record events, not current freshness. Preserve revisions/identity/assignment/registry-pointer/uncertain-delivery guards; unrelated registry edits need not block patient publication.

`SyncService`/`DoctorSyncCoordinator` share singleton `RemoteSyncStateStore` mutex; tokens/journals/artifacts retain one owner. Snapshot equality differs from device-wide pending state. Fetch Undo/history/rollover stay separate. Permanent-superadmin empty-start retains accounts/config/key/audit/receipts/Telegram history; other devices retrieve replacement.

## Patients and tasks

Revision-checked Save stamps editor/audit/pending; recycle/restore and stored export-consistent sorting. Rollover copies unfinished deadlines with reviewed keep/clear assignments, rejecting nonempty/stale targets. Team membership and patient cover are independent; CSV retains omitted active doctors.

`ReportReadiness`/`PatientTasks` own rules: explicit tasks, optional ownership, overdue ⊂ pending, completion stamped at local Save. Board completion/Undo use the same revision-safe mutation path. Demo saves in memory.

Fullscreen creation starts demographics; editing starts clinical/tasks. Labs/per-field/nested task tools share one draft and fixed keyboard-safe actions. `WardEditorState` retains exclusive Add/Edit/Copy/Delete plus original revisions across Activity recreation; secondary overlays have one separate owner. Edit/copy process-death recovery is not promised.

## Startup and navigation

Monotonic five-second splash continues during hold, dismisses on expired release/quick tap, retains deadline across recreation. Branding unchanged. Bootstrap uses system appearance until encrypted settings load; window owners manage bars, embedded previews do not; API23–25 navigation stays dark.

Patients/Dashboard use dock/rail; Activity admin-only; compact drawer and prominent «إرسال تقرير» with roster first. Privacy/PIN removes clinical composition while retaining signed-in controller/ViewModels/NavHost state; session `SaveableStateHolder` stabilizes document-picker keys. Sign-out discards owners; secrets never saved.

Settings search respects role visibility, searches names/descriptions, opens existing workspaces without unmounting forms. RTL/48dp/wrapping/fold-safe panes; details/rounds follow visible filtered/grouped/pinned IDs. Guarded RTL swipes supplement buttons; Save-next only after success, saved-card reveal preserves viewport/filters.

## Shared Android presentation

- Shared theme/components/semantic tokens; accent-independent severity, neutral diagnoses. [Catalog](../reference/design-catalog.md).
- Encrypted local profiles current/relaxed/quiet/clear, density/control size/reduced motion/compact Add/header/dock choices; demo temporary. Appearance/accent/PDF separate. Bundled Tajawal400/500/700, small labels13/20sp, combined scale0.92–2.0.
- Sticky unpadded 48dp-growing filter row, counts/RTL hints/options/reset; expandable search, full filter context, visible UNKNOWN/read-only and accessible fetch advice.
- Cards: age/sex/tasks/severity stripes, stable grid/content types, shared motion; bulk/reduced motion immediate. Dock translation preserves clearance; upward/boundary scrolling restores, touch exploration keeps visible. Rail ≥600×480dp.
- Adaptive dashboard; one minute clock for tasks/overdue. IME settling before visibility adjustment. Deliberate pull opens confirmation only; demo/read-only/busy/editor/details disable it. No gesture automatically fetches/publishes.

## Reports and exports

Doctors → Destination → Preview → Send; unlimited active roster, revisions, exact current text/PDF opening and final confirmation. `ReportFlowGate` owns presentation; services enforce actor/date/revision/destination, publish-before-delivery and artifact options/content/SHA-256. Durable UNKNOWN blocks retry until authorized resolution; confirmed extra copy needs approval. Publication/delivery outcomes remain independent, including partial uncertainty.

Classic/paragraph/A5 cards/local exports retain clinical/tasks/order; omit routine/unassigned/last-edit labels. Cards: 420×595pt, sides15/top20/bottom10mm, all internal links fit whole destination page. Desktop `desktop_pdf_cards.py` follows Android geometry/Tajawal/sections/continuations. Admin names-only A4 team tables retain numbering and historical current-roster limits.

Text chunking preserves escaped Unicode, bold/underline and whole doctor links; indivisible oversize links fail before sending. Focused JVM boundaries passed, live Telegram rendering unverified.

## Isolated demo

`InMemoryDemoWardRepository` → real `WardWorkspace`/`WardController`, without production clinical/settings/session/transport dependencies. Beverage patients/tree doctors, temporary actor/roster/preferences, recycle/reset/exit and explicit every-page-labelled PDFs. Reset/exit discards; process death starts fresh. [Guide](../guides/android-demo.md).

## Standalone desktop

Protected join/reconnect, encrypted SQLite drafts/audit/preferences, clinician PIN/idle lock, reviewed creation/registry administration, clinical/tasks/teams/CSV/recycle and durable receipts follow shared protocol. Linux XDG/passphrase/font/native-opener adapters; Windows DPAPI/compatibility requirements. Logical Arabic editing is separate from shaping; unfinished drafts differ from saved/exported records. Debounced search/resize, retained widgets, sidebar shortcuts, clinical stripes/native menus and guarded keyboard shortcuts; platform fonts unchanged. Demo memory-only. [Usage](../../desktop/README.md) · [Diagnostics](../guides/desktop-troubleshooting.md).

## Clinical write safety

[Assignment rules](../../android/app/src/main/java/com/hos/rushdpatients/domain/patient/PatientDoctorAssignments.kt) validate responsible roles/pending-task owners against live registry inside [mutation transactions](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/WardMutationRepository.kt) for Save/recycle/rollover. Historical unavailable completed-task ownership remains readable.

[Backup replacement](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt) transaction checks roster/assignments, advances patient/shift revisions beyond saved/current values and rolls back rows plus pending state on rejection. Password arrays wiped on all join/restore exits; login cancellation propagates through initial update drain. No schema/version changes.

## Verification and limits

Inventory: **683 JVM methods/85 classes; 288 device methods/55 classes; 60 desktop methods**. Counts are not coverage percentages; reruns overlap.

| Snapshot/scope | Evidence/result |
|---|---|
| Latest card layout | Offline production/device compilation, **4 PDF device + 60 desktop cases passed**. Light/dark matching fixtures:17/8 pages,68/15 Fit links, matching sampled baselines. All 40 tasks/long treatment/labs retained; three-index-page/30-patient returns correct. Selected pages compared; slight engine glyph-shaping differences remain (layout parity, not pixel identity). Unaffected table/paragraph text and sampled pixels unchanged. No dependency/version/test-file changes. |
| Before card change: full JVM | **682 passed,0 failures/errors,1 live-seeder opt-in skip**. |
| Before card change: full phone | PPA-LX2/Android10/API29: **285 passed,0 failures/errors,3 skips** (private CSV absent,native provider opt-in,fingerprint success skipped by owner). Separate assisted native-provider pass → **286 distinct verified cases**. Search settled before exact-file selection; earlier navigation timeouts superseded, no import logic changed. |
| Full desktop/runtime | **60 passed,0 skips**, including 4 reverse-interop/7 mutation cases; fresh Android artifacts. Real Linux demo/Settings/reset/render/close and every-page-DEMO Classic/A5 generation/selected visuals passed; not complete GUI/viewer/print acceptance. |

Current evidence: ignored `local/verification/cards-parity-2026-10-24/` (latest PDF parity) and `full-current-2026-10-24/` (source/tool context, summaries, latest logs/XML, fresh interop, provider success, GUI/PDF summaries, selected visuals/native timing). Full Android suite was **not** rerun after card change. Unique historical live transport/seeder/fingerprint receipts remain prior-snapshot evidence; not repeated. Original private CSV phone failure remains unreproduced.

Full run covers authority/revision/cancellation/encryption, Room/process-kill, report/tasks, privacy/recreation/adaptive UI and setup200% scrolling. Simulated faults/clients/folds and debug200-card samples do not establish physical two-phone/fold, every crash/glyph or release performance. [Single remaining-check list](testing-handoff.md).

Tools: Gradle8.7, installed Java21(target17),SDK34,offline/one worker/in-process Kotlin/`-Pshiftreport.autoVersion=false`; APKs installed, Desktop export excluded. Existing Python3.14 `desktop/.venv`, `pip check` passed; ReportLab5.0.1/reshaper3.0.1/bidi0.6.11 already installed, no installation needed. HTTP intercepted/isolated storage; created synthetic phone CSV removed afterward.

At owner request, redundant old successful/fixed-run logs, old XML/generated diagnostics and superseded visuals were pruned; local manifests retain hashes/replacement proof. Keep current selected samples, reusable scripts and unique live receipts/configuration. No build/test remains running.
