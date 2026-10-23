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

## Retained source findings

Rechecked on 23 October 2026; documentation consolidation did not fix these:

1. [Backup restore](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt)
   reuses backed-up patient revisions and existing shift revision through
   [patient](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/PatientRepository.kt)/
   [shift upserts](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/ShiftRepository.kt).
   Replacement can leave a stale draft's revision valid.
2. [WardSaveGuard](../../android/app/src/main/java/com/hos/rushdpatients/ui/ward/WardSaveGuard.kt)
   checks UI-held clinician roles; [WardMutationRepository](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/WardMutationRepository.kt)
   checks task owners but lacks live responsible-resident/supervisor role checks
   at save/recycle-restore/rollover boundaries.
3. Backup restore checks referenced doctor existence before its transaction, without
   responsible-role checks or a transactional registry recheck.

These retained concerns are separate from the selected work in [Roadmap](roadmap.md).

## Verification and limits

On 23 October 2026, the continued test expansion passed authorized offline
`:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin`, followed by
`:app:testDebugUnitTest`: **227 tests across 47 classes**, zero failures/errors/
skips, confirmed from the JUnit XML reports. Production and JVM test compilation
ran with no Kotlin compiler warnings. This supersedes the earlier 146-case JVM
snapshot. The continuation adds 81 JVM cases across local PIN lockout/live identity,
role-use-case rejection/cancellation, recovery/publication/index guards, editor
retention/submission/validation, ward save/filter/list-return behavior and text
reports. It also adds 11 source-only device transaction safety cases.

Verification used pinned Gradle 8.7, installed Java 21/SDK 34, one worker,
in-process Kotlin, no persistent daemon and disabled automatic version reservation.
The first offline attempt stopped before compilation because the pinned
`kotlinx-coroutines-test-jvm:1.7.3` JAR was missing. With owner authorization,
existing pinned JVM test dependencies were resolved into the durable shared Gradle
cache; the subsequent compile and test run were offline. No versions changed.
Before each compilation/test run, visible build processes and RAM/swap pressure
were checked. Nonfatal Gradle deprecation/JVM class-sharing notices remain.
Logs: `/tmp/shiftreport-expanded-test-compile.log`,
`/tmp/shiftreport-expanded-unit-tests.log`, `/tmp/shiftreport-test-dependencies.log`.

The suite now contains **52 device tests across 11 classes** for fresh Room
databases/repository transactions, project encryption, password hashing, backup
and RTL report confirmation. The 11 new `WardMutationSafetyTest` cases cover task
attribution/owner handling and rejected anonymous completion, duplicate patient
addition, cross-shift deletion, invalid/stale rosters, stale sorting and partial
rollover. They have source/whitespace checks only; instrumentation compilation
and device execution were not authorized or run in this continuation. For the
earlier 41-case device snapshot, after renaming 13 spaced coroutine test methods
to camelCase and correcting a duplicate-name lookup fixture, the owner's
connected run built successfully through D8 and executed all 41 cases on PPA-LX2
(Android 10): **39 passed, 2 failed, 0 skipped**. Both failures were obsolete
expectations that `DoctorDao.@Upsert` throws for conflicting unique names/Telegram
IDs. Those two tests now assert that the original row stays unchanged, the
conflicting ID is absent and exactly one row remains; the name case uses distinct
Telegram IDs to isolate the name constraint. These latest assertions have source/
whitespace checks only and have not been rebuilt or rerun by the agent.
JVM coverage includes current clinical/task rules, doctors/import, admin authority,
same-project reconnect, codecs/mappers/sorting, sync comparisons, report review/
delivery guards, intercepted Telegram requests, PDF content and UI state rules.
[Coverage map and commands](../guides/android-testing.md). Source counts are not
measured line/branch coverage or complete end-to-end coverage. Git whitespace,
whitespace/final newlines in untracked Kotlin additions, device method naming and
updated documentation links were checked. No production source, migration files,
versions or schemas changed. Agent verification did not package/install APKs;
the owner's connected run built/installed the test artifacts. No distribution ran.

Owner `assembleDebug` passed on 22 October 2026 for `1.1.23.20261022` (code 24):
37s, 44 tasks, no Kotlin warning; that APK predates later UI/shared-signing changes.
The current JVM verification does not establish device rendering, persistence on
SQLCipher, Keystore/biometric lifecycle, TalkBack, keyboard/large-font/fold behavior,
search draft retention, gestures, completion/Undo UI, performance or live Telegram.
Production previews remain unrendered. Retained production findings above, the
CSV device exception and production signing remain outstanding.
