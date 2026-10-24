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
- Android creation in an existing group offers explicit confirmation when a pin exists.
  Confirmation is bound to the reviewed configuration and pin; seeding checks live
  Telegram-group admin membership before upload and pinning, and rechecks pin/config.
  The new registry becomes the project reference; existing files/messages/pins remain
  and old project data is not imported. Existing local enrollment cannot be replaced
  from creation. Members need new protected join files; old devices do not switch
  automatically. Telegram still provides no atomic compare-and-pin guarantee.
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
  missing keys fail. Existing APK/key certificates match; the cached offline
  debug build/device update passed on 24 October. Second-account login remains
  unverified. Release has protected local/CI inputs
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
removed and «إرسال تقرير» remains prominent with the roster selector first.
Temporary privacy/PIN screens retain the signed-in navigation controller and editor
ViewModels and NavHost saveable state while removing clinical screens from
composition; sign-out discards them. A session-owned `SaveableStateHolder` keeps
document-picker registration keys stable across the temporary privacy screen.
Settings
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
Oversized text-report blocks now continue within the configured message limit,
preserving escaped text, Unicode code points, bold/underline and whole doctor links.
An indivisible oversized link is rejected explicitly before sending. The chunking
boundaries passed focused JVM verification; live Telegram/device rendering remains
outside that evidence.

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

Source: **683 JVM methods/85 classes, 288 device methods/55 classes, 60 desktop
methods**. Counts are not line/branch coverage. All results below passed on their
recorded snapshots; focused runs overlap and must not be added together.

| Snapshot / scope | Result | Retained evidence under ignored `local/verification/` |
|---|---|---|
| Earlier complete Android run, 24 October | 625 JVM passed; 253 device cases: 251 passed, two expected skips. Owner-assisted fingerprint success passed separately. Original private-CSV device case remained skipped. | `android-tests-2026-10-24/{unit-final-results,device-final-results}/`; biometric outcome in cleanup manifest |
| Report/Telegram expansion | 98 JVM passed; 51 distinct device cases passed across focused runs, including all 30 additions, 22 affected UI reruns and assisted native-picker selection. | `coverage-2026-10-24/` XML/artifacts and cleanup manifest |
| Existing-pin override | Production/JVM compilation and 56 cases passed, including opt-in live seeder; original active pin restored. | `pin-override-2026-10-24/jvm-results/` and private live receipt |
| Delivery fixture consolidation | Production/JVM compilation and 38 cases/four classes passed, including six new regressions. | `ledger-consolidation-2026-10-24/{compile.log,tests.log,jvm-results}/` |
| Setup warning fix, latest device snapshot | Production/device-test compilation and all five cases passed on PPA-LX2/Android 10/API 29; zero failures/errors/skips. | `setup-device-2026-10-24/{fix-compile.log,fix-tests.log,results-02,captures-02}/` |
| Desktop / shared files, 23 October | 60 passed without skips, including four reverse Android file checks; seven selected mutants detected; three synthetic live checks passed using their own index. | `deep-tests-2026-10-23/` reports/XML and cleanup manifest |

Device execution verified fixes for rollover busy guards, false pin receipts,
card-animation timing, background draft/picker-state retention and setup-warning
clipping at actual 200% typography. The last uses a local scrollable body/fixed
actions; enlarged size, no layout overflow, 48dp actions and scroll-to-end passed.
Fresh normal/enlarged/scrolled captures were visually inspected. Selected Room,
process-kill, provider, independent-client, PDF and adaptive-screen cases passed;
these do not prove every hardware/fault/viewer configuration. Native-clock debug
200-card samples were 9.47/17.79 ms median/p95 (normal) and 9.09/14.88 ms (reduced),
not a release baseline.

Verification used cached Gradle 8.7, installed Java 21 (target 17), SDK 34, offline
mode, one worker, in-process Kotlin and automatic version reservation disabled.
Debug APKs remain installed; no release artifact was distributed. Fixtures use
isolated storage and intercepted HTTP except explicitly authorized synthetic live
checks. The latest setup run excluded Desktop APK export.

[Remaining checks](testing-handoff.md) is authoritative; [commands](../guides/android-testing.md)
and [device procedures](../guides/device-coverage.md) describe execution. Older
successful `.log` files were pruned at owner request; XML, reports, private receipts,
captures, failed/interrupted logs and the latest JVM/device success logs remain.
`local/verification/log-cleanup-2026-10-24.json` records removed log names, hashes and
outcomes. This documentation cleanup ran source/link/whitespace checks only.
