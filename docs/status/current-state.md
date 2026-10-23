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

The newest test-only expansion adds **72 JVM methods in five classes**, bringing
source totals to 337 JVM methods/60 classes and 110 device methods/25 classes.
It covers delivery concurrency/cancellation/storage faults and persisted retry
guards, ward editor transitions, demo controller mutation safety, deterministic
task invariants and complex clinical CSV payloads. Authorized offline production/JVM
compilation, the focused **72-case run**, and the full **337-case JVM run across
60 classes** all passed on 23 October 2026, with zero failures/errors/skips confirmed
from JUnit XML. No production code, dependencies, versions or schemas changed.
[Suite details](../guides/android-testing.md#additional-safety-coverage).

Preflight found no active builds/compilers; available RAM and swap/pressure were
checked against the 2 GB Gradle heap before each attempt. Verification used the
installed Java 21/SDK 34 and pinned Gradle 8.7 offline, one worker, in-process Kotlin,
no persistent daemon and `-Pshiftreport.autoVersion=false`. No APK was packaged or
installed. Ignored evidence in `local/verification/expanded-tests-2026-10-23/`
includes `compile.log`, `focused-tests.log`, `full-jvm.log`, `focused-results/`,
`full-jvm-results/` and `results.json`. Whitespace, source counts and unique test
names were also checked. Existing nonfatal Gradle deprecation/JVM class-sharing
notices remain. Device tests were not rerun; the earlier device evidence follows.

On 23 October 2026, authorized offline production/JVM/device compilation and the
then-current Android regression suites passed on the preceding source snapshot:

- **JVM: 265 tests across 55 classes**, zero failures/errors/skips.
- **Connected device: 110 tests across 25 classes**, zero failures/errors/skips,
  on the owner's PPA-LX2 running Android 10 (API 29).

Counts were confirmed from JUnit XML reports, not inferred from source methods.
This supersedes the earlier 227-case JVM and 39/41 device results, including the
two corrected Room upsert expectations. The first expanded device run passed
109/110: the new Undo fixture omitted required admission fields and CSV decoding
correctly rejected it. After supplying a complete synthetic patient, the complete
110-case device suite passed again. Production/JVM source stayed unchanged between
the successful JVM run and that device-only fixture correction.

Verification used pinned Gradle 8.7, installed Java 21/SDK 34, one worker,
in-process Kotlin, no persistent daemon and `-Pshiftreport.autoVersion=false`.
Visible build activity and RAM/swap pressure were checked before attempts.
Missing existing pinned AndroidX/AGP test-runner artifacts were populated once in
the durable shared cache using the owner's cache authorization, then verification
ran offline. A session interruption stopped an earlier build and cleared `/tmp`;
four confirmed zero-byte transform entries owned by this account were reversibly
quarantined, and Gradle regenerated them. No unrelated caches/processes were cleared
or stopped. Kotlin test compilation is clean; nonfatal Gradle deprecation and JVM
class-sharing notices remain.

Durable ignored evidence is under `local/verification/full-tests-2026-10-23/`:
`jvm-and-device-compile.log`, `device-tests.log`, copied `jvm-results/` and
`device-results/` XML, and `results.json`. The earlier failed fixture run is also
retained. Debug app/test APKs were packaged and installed for the authorized
connected run; `android.injected.androidTest.leaveApksInstalledAfterRun=true` kept
the installations, confirmed afterward. No release/distribution ran, and no
versions, schemas or migration files changed. Source whitespace, untracked Kotlin
whitespace/device method names and updated documentation links were checked.

The device suite exercises real Room transactions, isolated SQLCipher close/reopen
and wrong-key rejection, encrypted sessions/join files/staging, password cleanup,
report confirmation, native multipage PDF rendering, editor/PIN/splash
interactions, and intercepted download/doctor-sync/Undo workflows. All clinical
fixtures are synthetic and HTTP interceptors prevent live Telegram requests.
[Coverage and commands](../guides/android-testing.md).

These results are not measured complete line/branch coverage. Recreating managers
and reopening storage do not simulate process death. PDF page rasterization does
not establish visual approval of every glyph/clipping boundary. Physical biometric
success, TalkBack operation, foldable/IME configurations, Activity splash-deadline
recreation, performance baselines, third-party file-provider failures, real token
revocation and competing multi-device publication remain manual/external checks.
The synthetic 33-row import passed through the real repository; it does not
reproduce the owner's original unavailable CSV exception. That exception and
production signing remain in the implementation roadmap. Production previews
remain unrendered.
