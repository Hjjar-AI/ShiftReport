# Current implementation

[Docs](../README.md) · [Architecture](../reference/architecture.md) · [Remaining work](roadmap.md) · [Completed work](changelog.md) · [Optional UI](../decisions/android-ui-options.md)

## Product and source layout

Arabic offline-first clinical handover, with manually reviewed Telegram snapshots/reports.
Android: `android/`, Kotlin 1.9/Java 17, Compose BOM 2023.10.01, SDK 34/minimum 23;
desktop: `desktop/`, CustomTkinter. Versions are unchanged. Android source below
`app/src/main/java/com/hos/rushdpatients/`: `ui/` delegates to Hilt ViewModels,
`domain/` owns rules, repositories wrap Room/SQLCipher, `network/telegram/` owns
transport, and `sync/` orchestrates transfer. [Protocol](../reference/telegram-protocol.md).

## Security and project access

- Join through protected `.srjoin.json` files; manual group/topics belong to creation.
  Optional authenticated Telegram-data encryption is selected at creation; the shared
  key travels in protected settings/join files. PDFs deliberately remain readable.
- Same-project reconnect validates candidate credentials before replacement, retaining
  clinical state, pending edits, topics and key. Cross-project/key/topic substitution
  is blocked. Reconnect works before login; BotFather handles token revocation.
- Clinician-bound numeric PINs, throttling, optional biometrics and service-level admin
  checks remain. Credentials/passphrases stay outside saved UI state, logs and exports.
  Missing database keys preserve existing DB/WAL/SHM/journal files; storage checks run
  on IO before repository-backed ViewModels. Demo never opens the clinical database.
- App windows use `FLAG_SECURE`; exported/opened PDFs are outside it. Private reviewed
  PDFs have best-effort session cleanup. Clipboard codes use sensitive metadata and
  matching-nonce expiry/disposal; background clipboard restrictions still apply.
- Debug can select a protected existing shared key independently of caches; selected
  missing keys fail. Existing APK/key certificates match; second-account login and
  new build/device update remain unverified. Release has protected local/CI inputs
  with no debug fallback; a production key remains an owner prerequisite.
  [Cache/signing setup and checks](../guides/android-build.md).

## Synchronization and publication

Retrieve → edit locally → review → publish; complete snapshots, last successful
pinned-state write wins. No automatic synchronization, remote field merge, atomic
multi-writer guarantee or required backend. Fetch/publication times are known events,
not proof of current remote freshness. Review preserves local revisions, clinician
identity/assignments, registry-pointer and uncertain-delivery checks.

`SyncService` owns patient transfer; `DoctorSyncCoordinator` owns doctor transfer.
Both share singleton `RemoteSyncStateStore` and its mutex. Review tokens, journals
and artifacts retain one owner. Snapshot equality is distinct from device-wide
pending state; unrelated local registry edits need not block reviewed patient
publication. Undo-fetch, downloaded history and whole-shift copy remain separate.
Permanent-superadmin empty-start preserves accounts, connection/key, audit/delivery
records and Telegram history; other devices must retrieve the replacement snapshot.

## Patients and tasks

Revision-checked saves retain audit/editor identity and pending state; deletion has
recycle/restore. Stored sorting agrees across reports/exports. Whole-shift rollover
copies unfinished tasks, offers keep/clear assignments and rejects nonempty targets
or stale source/target state. Independent resident/supervisor membership permits
explicit patient-level cross-team cover. Reviewed CSV retains omitted active doctors.

`ReportReadiness` and `PatientTasks` own shared rules. Tasks are explicit records;
overdue is a subset of pending, ownership is optional, and completion identity/time
is stamped at local Save. Board completion and Undo use the same mutation path;
Undo carries the saved revision and rejects intervening changes. Demo saves in memory.

Full-screen patient creation starts with demographics; editing starts with clinical/
task sections. Per-field tools, labs and nested task editors share one draft and
fixed keyboard-safe Save/Cancel. `WardEditorState` retains exclusive Add/Edit/Copy/
Delete sessions and original revisions across Activity recreation; another owner
holds secondary overlays. Edit/copy process-death recovery is not promised.

## Startup and navigation

Five-second monotonic splash: hold keeps reading while the timer continues, release
dismisses after expiry, quick tap skips, recreation retains the deadline. Emblem and
About/credits stay intact. Bootstrap uses system appearance until encrypted settings
load; saved appearance/accent then apply. Window owners control system bars; embedded
previews do not. API 23–25 retains a dark navigation background.

Patients/Dashboard use dock/rail; Activity remains admin-only. Drawer duplicates were
removed and «إرسال تقرير» remains prominent with the roster selector first. Settings
search uses the screen's admin/permanent-admin visibility, searches names/descriptions
only, opens existing workspaces and keeps underlying forms mounted. Fold-safe panes,
first-layout RTL and labelled 48dp targets remain. Details/rounds follow displayed
filtered/grouped/pinned IDs; RTL swipes supplement guarded Previous/Next buttons.
Save-next advances only after success; saved-card reveal preserves filters/viewport.

## Shared Android presentation

- Shared chips, buttons, dialogs, notices, cards, menus, sheets, state/loading hosts,
  icons and semantic text consume theme tokens. Clinical severity is accent-independent;
  diagnoses are not error states. [Component/style guide](../reference/design-catalog.md).
- Profiles current/relaxed/quiet/clear, density/control size, reduced motion, compact
  Add, optional header expansion and dock auto-hide are device-local encrypted
  preferences; demo uses temporary state. Appearance/accent/PDF choices stay separate.
  Genuine Tajawal 400/500/700 is bundled; small labels are 13/20sp and combined app/
  system font scaling is 0.92–2.0. No clinical/schema/interchange version changed.
- One unpadded filter row stays above the grid: 48dp baseline, growing for large text,
  preset counts, RTL edge hints and stable options/reset slots. Search expands on
  demand; full count/group/filter context is in the options label/sheet. Delivery
  uncertainty and read-only notices remain visible. Fetch advice stays accessible.
- Cards show age/sex, per-patient task counts and labelled severity stripes. Grid
  content types/placement, whole-card expansion, chevrons and tab feedback share
  profile motion. Bulk expansion/reduced motion are immediate. Dock translation
  keeps clearance stable; upward scrolling/boundaries restore it and touch exploration
  keeps it visible. Rail selection requires width ≥600dp and height ≥480dp.
- Dashboard totals adapt to pane/font width. A shared minute clock serves cards,
  details, board, drafts and overdue filtering. Input visibility waits for IME settling.
  A deliberate live-list pull opens existing fetch confirmation; demo/read-only/busy/
  editor/detail states disable it. No gesture publishes or fetches automatically.

## Reports and exports

Doctors → Destination → Preview → Send, with unlimited active-doctor roster,
revision checks, required current text/PDF opening and final confirmation.
`ReportFlowGate` controls presentation; delivery services enforce actor/date/revision/
destination, publish-before-delivery and exact reviewed PDF content/options/SHA-256.
UNKNOWN receipts persist before sending and require explicit resolution; confirmed
extra copies need permission. Publication and delivery outcomes remain independent,
including partial/uncertain results. No retry silently publishes/sends.

Classic tables, linked portrait A5 cards, advanced PDF options and local exports
retain full clinical/tasks and shared order. PDFs omit routine/unassigned task labels
and patient last-edit metadata. Admin-only names-only team PDFs use A4 portrait
supervisor tables/global numbering; historical output labels current-roster limits.

## Isolated demo

Injected `InMemoryDemoWardRepository` drives the real `WardWorkspace` through
`WardController`; no production clinical/settings/session/Telegram dependency.
Hot-beverage patients, tree-named doctors, temporary actor/roster/preferences,
recycle/reset/exit and explicitly requested PDF export remain. Every PDF page is
labelled demo. Reset/exit discards the session; process death starts fresh.
[Demo guide](../guides/android-demo.md).

## Standalone desktop

Protected join/reconnect, encrypted SQLite drafts/audit/local report preferences,
clinician PIN/idle lock, reviewed creation/registry administration, patient/tasks/
teams/CSV/recycle and durable delivery receipts follow the shared protocol.
Linux uses XDG/passphrase-wrapped keys/font discovery/native PDF opening; Windows
keeps DPAPI and its compatibility dependencies. Arabic editing stores logical
Unicode, shaping only display/PDF output. Local unfinished drafts remain separate
from saved records/portable exports. [Usage](../../desktop/README.md) · [Diagnostics](../guides/desktop-troubleshooting.md).

UI search/resize is debounced; unchanged cards and dashboard widgets are retained.
Dashboard/roster shortcuts live in the sidebar. Clinical stripes, native More menu,
selected tab styling and guarded Ctrl+F/Ctrl+N supplement existing controls. Platform
UI fonts remain unchanged. Desktop demo is memory-only with labelled local PDFs.

## Clinical write safety

Android [patient assignment rules](../../android/app/src/main/java/com/hos/rushdpatients/domain/patient/PatientDoctorAssignments.kt)
check optional responsible-resident/supervisor roles and unfinished-task owners
against the live registry. [Ward mutations](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/WardMutationRepository.kt)
apply these checks inside their existing transaction at Save, recycle restore and
whole-shift rollover. UI validation remains useful but is not the write boundary.
Historical completed-task ownership remains readable when a doctor is unavailable.

[Backup restore](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt)
checks the live roster/clinical assignments inside the replacement transaction.
Restored patient and shift revisions advance beyond the saved/current revisions,
invalidating open drafts. Rejected replacement rolls back the pending flag together
with clinical rows. Password arrays are wiped on all restore/join-file exits,
including malformed/missing files and precondition failures. Login cancellation
also propagates during the initial Telegram update drain. These changes address
the three previously retained Android source findings; no schema/version changed.

## Verification and limits

**Latest test expansion is source-only; builds and execution await owner permission.**
[Continuation record and exact commands](testing-handoff.md) ·
[Coverage boundaries](../guides/deep-testing.md).

Current source contains **623 JVM methods/81 classes, 252 device methods/50 classes
and 60 desktop methods**. Counts do not establish execution or complete branch
coverage. Unexecuted additions total **247 JVM and 77 device cases** across the
23–24 October expansions. The newest **5 JVM and 30 device cases** address selected
picker/provider, independent-client, failure/clock, adaptive UI and actual root/
navigation journey gaps. [Detailed suite map and prerequisites](../guides/android-testing.md#latest-source-expansion--awaiting-execution).
Only static source/resource-reference, manifest and whitespace checks were performed;
no build, test execution, device installation or live request was made.

A non-exported debug Activity supplies isolated production ViewModels before screen
lookup. The production root's only new seam is an optional composable navigation-
controller factory, invoked in its existing logged-in branch with the ordinary
remembered-controller default. Clinical/auth/sync rules are unchanged. The actual
WardAppRoot/WardNavHost journey and Hilt/store wiring still need runtime evidence.
Controlled OpenDocument results do not reproduce the original picker exception;
independent clients and synthetic hinges/faults do not establish two-phone,
physical fold/split-screen, power-loss or actual disk-full behavior. The complete
send journey needs validated device connectivity for the production gate, with all
HTTP intercepted; an absent-prerequisite skip is not verification.

Two new intended-behavior cases are expected to fail from source review: rollover
sets `saving` before entering a guard that rejects busy work; initial registry
seeding ignores a false Telegram pin receipt. These are source findings, not
executed failures. Those clinical/initialization paths are unchanged; [implementation fixes](roadmap.md)
remain. All new cases still need compilation/execution, which may expose fixture
issues. The earlier patient-scroll/stability changes, crash-boundary marker and
birth-date fixture corrections, and two first/bulk-expansion tests are **not yet
compiled or runtime verified**. Resume from the handoff rather than repeating
finished work or treating source completion as verification.

Verified on 23 October 2026, before those latest edits:

- **376 JVM tests across 64 classes passed**, zero failures/errors/skips.
- The full **166-case device run** on PPA-LX2/Android 10 (API 29) had **165 passes,
  zero failures/errors and one deliberate physical-success skip**. The owner then
  completed that physical biometric success check separately. Native cancellation
  also passed separately through the production cancel callback.
- **60 desktop tests passed without skips**, including four reverse checks of
  Android-produced encrypted files/text/backups/join files. Seven temporary-source
  mutations of selected safety guards were detected.
- **Three live synthetic Telegram checks passed**: exact file upload/download,
  ordering of edits by two independent clients and a synthetic report destination.
  They used their own synthetic index; existing project pinned state was untouched.
- The original private doctor CSV passed production ViewModel preview and commit
  into an isolated in-memory registry. A synthetic 33-row ContentProvider import
  also passed. The originally reported system-picker failure remains unreproduced.
- Native PDF rendering/wrapping checks passed; selected first/last Classic/A5 and
  content-heavy A5 rasterized pages were inspected. This is not every-glyph approval.

A later nine-case crash/profile run passed seven cases and failed backup/Undo
postcommit checks because its synthetic patient lacked a required birth date.
Precommit results from that run are **not credited**: an unrelated worker exception
could leave old data unchanged. Pending corrections require an IPC marker proving
that every intentional kill reached the selected production boundary. All eight
process-death cases must be rerun, alongside affected patient UI/scroll tests.

Native-clock 200-card profiling before the newest optimizations recorded normal
motion **16.65 ms median/61.35 ms p95** (175 frames), and reduced motion
**9.89 ms/21.22 ms** (206 frames). Earlier reduced-mode results varied. These are
single-device debug samples, not a release baseline or proof of a speedup. Earlier
Compose-clock gesture totals were dominated by unknown delay and cannot be used as
responsiveness measurements. Latest scrolling changes remain unmeasured.

Verification used cached pinned Gradle 8.7, installed Java 21/SDK 34, offline mode,
one worker, in-process Kotlin and `-Pshiftreport.autoVersion=false`. Visible build
activity and RAM/swap pressure were checked before every attempt. Debug app/test
APKs were installed only for authorized connected tests; no release/distribution,
version, dependency, schema or migration changes were made. Results/logs and failed
attempts are retained under ignored `local/verification/deep-tests-2026-10-23/`.
The private phone CSV copy was deleted and absence confirmed; the phone's original
USB stay-awake value was restored to 0 at pause. No build/test remains running.

Windows native GUI/DPAPI, second-device UI, TalkBack/foldable/IME/other-OS coverage,
physical privacy transitions, BotFather revocation and release/workload performance
still need external/manual evidence. Selected kill points do not cover every
network publication/download crash window or power loss. Complete active timezone
changes and every PDF content/glyph combination are not exhaustively verified.
See the coverage matrix for scenario-specific limits.
