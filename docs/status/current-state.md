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

The existing-group pin override adds 18 focused JVM regression cases plus one
opt-in production-seeder check against the owner's designated disposable Telegram
group. Offline production/JVM compilation passed on 24 October 2026, followed by
**56 cases across four classes**, zero failures/errors/skips. The live check rejected
a changed pin, seeded a freshly reviewed replacement, downloaded/decoded its registry,
and confirmed cleanup restored the original active pin. Only synthetic pins were
removed; messages/files remain. Logs/XML and the private receipt are in ignored
`local/verification/pin-override-2026-10-24/`.

The first isolated phone run passed five cases on PPA-LX2/API 29, with no failures
or skips: confirm/cancel, changed pin, recreation and a nominal 200% text case.
Debug app/test APKs were installed and retained; installed clinical storage was
not opened by the fixtures. Visual review of all four fresh dialog captures found
identical hashes: the dialog's separate Android composition owner reset the fixture's
LocalDensity override. Thus **four behavior cases are verified; large-text coverage
is not yet verified**. The fixture now scales Material typography across the dialog
boundary and asserts rendered text size plus no text-layout overflow. Its targeted
rerun was stopped at the owner's request during `kspDebugAndroidTestKotlin` (exit
130), before compilation finished or a phone result. No production layout defect
has yet been established. Normal-size warning/actions were visually readable.

Remaining: compile/run the corrected large-text case, inspect its newly captured
image and rendered text assertions, fix any evidenced clipping, then rerun affected
cases. [Resume details](testing-handoff.md#pin-override-phone-check--stopped-at-owner-request).
Earlier full runs below apply to their preceding source snapshots.

On 24 October 2026 the final full Android verification **succeeded**:

- **625 JVM tests/81 classes passed**, zero failures/errors/skips.
- **253 device cases/50 classes: 251 passed, zero failures/errors, two expected
  skips** on PPA-LX2/Android 10. Physical fingerprint success passed separately
  with the owner's touch: **252 distinct device cases verified**. Only the optional
  private-original-CSV case remains unrun because its fixture was not provisioned.
- The final test compilation has no `AccessibilityNodeInfo.recycle()` deprecation
  warnings. Debug build and device installation succeeded; no device setting changed.

Final logs/XML are in ignored `local/verification/android-tests-2026-10-24/`:
`all-final.log`, `unit-final-results/`, `device-final-results/` and
`physical-biometric-02.log`. XML counts are authoritative; the Gradle console prints
duplicate skip notifications. Earlier failures/focused reruns remain archived.
[Handoff](testing-handoff.md) · [Coverage map/commands](../guides/android-testing.md) ·
[External boundaries](../guides/deep-testing.md).

Source contains **677 JVM methods/85 classes, 288 device methods/55 classes and
60 desktop methods**. Counts do not establish complete line/branch coverage.
The preceding coverage expansion added **33 JVM and 30 device methods** after the full run
above (22 report cases, then 41 additional cases). It covers review/fingerprint
scope and real report assembly, long-report limits/Markdown, deterministic rate
limiting, transaction-consistent snapshots, revoked authority through open UI
workflows, export/uncertain-delivery process death, task-heavy PDF continuations,
200% font/48dp controls and actual-window recreation. A real system-picker test is
opt-in with the committed synthetic CSV. The debug crash worker's export files
and preferences are isolated by its validated test namespace.

The owner authorized offline compilation and focused JVM execution. Production,
JVM and device-test Kotlin compilation **passed** with the pinned cached toolchain.
The report/Telegram JVM run **passed 98 cases/12 classes**, zero failures/errors/
skips, including all **33 new JVM cases**. The focused JVM run needed no source
corrections. Logs, summary and copied JUnit XML are in ignored
`local/verification/coverage-2026-10-24/`: `compile.log`, `jvm.log`, `jvm-summary.txt`
and `jvm-results/`. Full 658-case JVM execution for that coverage snapshot was not requested or rerun.

After the owner connected the PPA-LX2/Android 10 and approved debug packaging,
installation and focused execution, the main device run **passed 49 cases with
zero failures/errors and one expected opt-in skip** (50 cases/9 classes in XML).
Actual native selection exposed a distinct lost-callback boundary: hiding NavHost
for background privacy discarded its saveable ActivityResult registration keys,
despite retaining its controller/ViewModels. A session-owned saveable-state holder
now retains these keys while clinical content remains out of composition when
locked; sign-out still discards it. All **22 affected UI cases passed** after the
fix, including the added background-result regression. The real system-picker
case then **passed separately** with assisted native selection of the exact
synthetic CSV, real URI/grant delivery, preview and isolated commit.

Across the focused runs, **51 distinct device cases passed**, including all 30
new device cases. The main run's skip is superseded for that same real-provider
case by its separate pass; these are not 51 cases in a single Gradle run. Full
283-case device execution was not rerun. Earlier native selection/result timeouts
remain in `real-provider-01.log` through `real-provider-05.log` and the available
failed XML; successful evidence is `device-root-fix-01.log`,
`device-root-fix-01-results/` and `real-provider-06.log` (`OK (1 test)`). The 11 new
task-heavy PDF pages and native report screenshots were visually reviewed; this
sample does not establish every glyph/options combination. Artifacts are copied
to `device-artifacts/`. Both temporary CSV copies and the new empty fixture folder
were removed, preserving pre-existing device files. No device setting changed.
The installed debug APKs are retained; no release artifact was produced. Earlier
full-run results describe their prior snapshot, not a full run of this expansion.
[Suite map](../guides/android-testing.md#additional-failuredevice-boundaries--focused-jvm-verification)
and [focused commands/external procedures](../guides/device-coverage.md) distinguish
automated/sampled evidence from the original private-file failure, two physical
phones, other OS/vendors, full TalkBack/IME and physical fold/split-window checks
that remain unperformed. The original unknown exception is not established as
the same issue as the now-verified callback loss. No versions/dependencies or
schema definitions changed; no migration files were inspected.

The first full JVM run confirmed rollover's nested busy-guard conflict and a false
initial pin receipt being treated as success. After correction and two extra
rollover cases, **625 JVM tests passed with zero failures/errors/skips**.

The first complete device run on PPA-LX2/Android 10 had **252 cases: 242 passed,
8 failed, 2 skipped**. Focused reruns verified the corrections: first individual
expansion/collapse now uses the current animation duration while bulk changes stay
immediate; background privacy locking retains navigation/editor ViewModels while
clinical screens leave composition, and sign-out discards navigation. Test fixtures
now honor actual workspace/text, Activity focus, foreground-authorization and lazy
item visibility contracts. The version-aware native biometric cleanup compiled
without its old deprecation warnings, and native cancellation passed. Physical
fingerprint success passed separately with the owner's touch. The private-original-
CSV case was not provisioned and skips; no private file was recopied to the phone.

The corrected **8 process-death cases** passed, including explicit boundary markers,
real receipt durability, backup restore and download Undo. This supersedes the old
fixture run whose missing birth date made postcommit tests fail and precommit
results unreliable. **14 independent-client, 5 controlled picker and 3 synthetic
fold-layout cases** passed in the final full run, together with scrolling and selected constrained
production UI. The protected join → PIN login → edit → review → explicit publication
journey, recreation and background draft checks passed the final full run through the
actual root/navigation graph with isolated production models. Every HTTP request
is intercepted; no real report is sent or installed clinical database opened.

The original unknown picker exception remains unreproduced; controlled OpenDocument
results do not exercise OEM picker UI/grant behavior. Independent clients and
synthetic hinges/faults do not establish two-phone, physical fold/split-screen,
power-loss or actual disk-full behavior. The complete send journey requires
validated device connectivity for the production gate despite intercepted HTTP;
a missing-prerequisite skip is not verification. Clock/DST cases cover selected
boundaries, not arbitrary clock changes in every workflow. Full TalkBack, other OS/
device versions, every PDF glyph/options combination and release performance remain
outside this evidence. Optional initial encryption and project/clinical safeguards
remain; no dependency, toolchain, version, schema or migration changes were made.

The final native-clock 200-card profile recorded normal motion
**9.47 ms median/17.79 ms p95** (236 frames), reduced motion
**9.09 ms/14.88 ms** (232 frames). These single-device debug samples do not
prove a release baseline or speedup. Earlier samples varied; old Compose-clock
gesture totals included unknown delay and are unsuitable for responsiveness
comparisons. Raw reports are retained separately for each full run.

Historical 23 October evidence remains separate: **60 desktop tests passed without
skips**, including four reverse Android file checks; seven selected temporary-source
mutations were detected. Three live synthetic Telegram checks passed using their
own index, leaving existing pinned state untouched. The original private doctor CSV
passed production preview and isolated commit; a synthetic 33-row provider import
also passed. Native PDF wrapping/rasterization and selected Classic/A5 pages were
checked; this is not every-glyph approval. Desktop/live checks were not rerun for
this Android-only request. Older evidence is in ignored
`local/verification/deep-tests-2026-10-23/`.

Verification uses cached pinned Gradle 8.7, installed Java 21/SDK 34, offline mode,
one worker, in-process Kotlin and `-Pshiftreport.autoVersion=false`. Host build and
compiler activity plus RAM/swap pressure are checked before every Gradle attempt.
Authorized debug app/test APKs are retained after connected execution; no release
artifact was exported. Device settings were not changed during the current run.

No build/test remains running after final verification.
