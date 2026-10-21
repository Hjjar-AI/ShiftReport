# Current implementation

Updated 2026-10-21. [Roadmap](roadmap.md) · [Architecture](architecture.md) ·
[Changelog](changelog.md) · [Troubleshooting](troubleshooting.md)

## Product and source layout

ShiftReport is an Arabic-first RTL Android ward/handoff app. Each device works
from encrypted local Room data; Telegram stores immutable documents and shared
pointers. Users retrieve complete snapshots, edit, review and publish complete replacements. Competing remote
pointer writes remain last-successful-write-wins; Telegram is not an atomic
multi-writer database and no separate backend is required.

| Layer | Responsibility |
|---|---|
| `android/app/src/main/java/com/hos/rushdpatients/ui/` | Compose screens and Hilt ViewModels; dedicated ward navigation, forms, dashboard and review components |
| `android/app/src/main/java/com/hos/rushdpatients/domain/` | Clinical rules; `report/ReportReadiness.kt`, `task/PatientTasks.kt`, sorting, backups and exports |
| `android/app/src/main/java/com/hos/rushdpatients/data/repository/` | Persistence, expected revisions, transactional mutations and audit |
| `android/app/src/main/java/com/hos/rushdpatients/network/telegram/` | Bot API transport |
| `android/app/src/main/java/com/hos/rushdpatients/sync/` | CSV/bundles, merging, synchronization and shared pinned-state coordination |
| `desktop/` | Standalone CustomTkinter Windows/Linux client, encrypted drafts and Office-free PDFs |

The repository is split into `android/` (the entire Android Studio/Gradle
project), `desktop/` (the Python Windows/Linux client), and shared `docs/`.
Android-local SDK/signing/import files and IDE/build caches were moved intact under
`android/`, with ignore rules updated before moving private files. The old ignored
Excel workspace is now `local/excel/`. Open `android/` in Android Studio and run
Gradle there; the existing command list is preserved in the root README.
Portable IDE mappings now resolve the Android modules and parent Git root.

The reorganization preserved hashes of 274 non-test/non-migration Android source,
resource and Gradle configuration files, plus the filesystem identities of moved
private artifacts. Source tests/migrations were not inspected or edited. No Gradle,
compilation, build or packaging task ran. Historical protected review documents
still show the old repository-root `app/` source paths: resolve those under
`android/app/`. Their file permissions prevent updating those historical links.
Current documentation and entry points use the platform folders.

### Source ownership after logical separation

The owner's subsequent Android build reached Kotlin compilation and reported four
missing Material 3 experimental opt-ins in extracted security/report settings.
`SettingsSecurityContent` and `SettingsReportContent` now carry their own
`ExperimentalMaterial3Api` opt-in alongside the layout opt-in. Other extracted
Material chip callers were inspected for the same omission. This correction
received source/whitespace checks only; compilation of the corrected snapshot has
not been rerun. The supplied log does not establish a successful build or device run.

The existing entry points remain, with implementation split by responsibility:

- Settings uses `SettingsPreferenceSections`, `SettingsReportSection`,
  `SettingsDataSections`, `SettingsProjectSections` and `SettingsSectionLayout`.
  `SettingsViewModel` owns the single UI state/lifecycle and delegates to
  `SettingsPreferences`, `SettingsDataOperations`, `SettingsBackupOperations` and
  `SettingsProjectOperations`. The data operation owns the publication-review token;
  project operations invalidate it through an explicit callback at the original
  reset step. Export staging IDs retain their original keys in `SettingsExportKeys`.
- `WardScreen` retains navigation, saveable filter/modal state, edit/save-next and
  return-to-patient coordination. `WardPatientFiltering`, `WardPatientGrid`,
  `WardViewOptionsDialog` and `WardDataReviewDialogs` contain filtering, grouped/list
  rendering, controlled options and retrieval review. Grouped/ungrouped lists reuse
  one patient-card slot, with the same keys, header item and return indices. Filter
  callbacks update the current screen-owned values, avoiding stale snapshot writes.
  `WardNavigationChrome` renders the controlled header/bottom navigation;
  `WardPatientDialogs` renders edit/copy/delete dialogs. Search/workflow effects,
  rendered-patient Save-next/stale-edit handling and delete/undo coordination stay
  in the screen, with the same patient keys and callbacks.
- `WardViewModel` retains the shared state, online/shift/workflow observers and
  public action signatures. `WardPreferences`, `WardPatientActions`,
  `WardShiftOperations` and `WardActivityOperations` use its scope/state.
  `WardSaveGuard` is one shared instance for patient and rollover saves, including
  stale revisions, validation, error handling and cancellation. Draft/activity jobs
  move with their respective operations; the shift-observation job stays in the
  ViewModel. `WardPatientOrdering` contains the unchanged pure sorting/grouping.
- `PatientFormDialog` retains the one draft, autosave, discard checks and fixed
  actions. `PatientDemographicFields` renders controlled demographic fields;
  `PatientFormValidation` and `PatientFormSubmission` hold the existing validation
  and saved-patient conversion. Clinical fields/tasks keep their existing shared
  editors and section order.
- `ReportPdfWorkspace` owns the single reviewed-artifact map, exact-file hashing,
  preview checks, PDF options/rendering and local export helpers. The report
  ViewModel retains publication/delivery ordering, authorization and the reviewed
  report. `ReportLocalActions` handles local preview/share/export operations using
  that same ViewModel state/scope and PDF workspace. Existing invalidation paths
  reference the same artifact map; local actions cannot bypass reviewed sending.
- `SyncService` remains the singleton public facade. `PatientRetrievalCoordinator`
  and `PatientPublicationCoordinator` receive its original shared patient mutex and
  the existing singleton remote store. Undo shares that mutex too; no additional
  synchronization scope/lock was created. CSV result models and shared content
  comparison live in `CsvSyncModels` and `PatientSyncComparison`.
- Desktop `Window` remains the single workspace/executor/session owner and retains
  its method signatures. `desktop_shell_layout`, `desktop_patient_list`,
  `desktop_patient_actions`, `desktop_data_actions` and `desktop_report_actions`
  receive that owner explicitly; callbacks and initialization order are preserved.
- Desktop `desktop_protocol` remains the public facade and `Client` remains the
  sole session/baseline owner. Shared constants/errors/utilities, encryption/join
  decoding, Telegram transport, wire codecs, reviewed publication and report
  delivery are separated into `desktop_protocol_common`, `desktop_crypto`,
  `desktop_telegram_transport`, `desktop_wire`, `desktop_publication` and
  `desktop_report_transport`. Compatibility exports preserve existing imports and
  the common exception/Review definitions. Formats, keys and review rules do not change.
- Desktop `desktop_forms` re-exports its original functions from
  `desktop_form_controls`, `desktop_doctor_picker`, `desktop_task_editor` and
  `desktop_patient_editor`. The complete patient-editing function moves intact:
  it retains one draft, autosave timer, stale-save review, Save-next and discard
  handling. Pickers and reusable controls do not persist patients independently.
- Desktop `Workspace` retains its public methods, clinical state, encrypted
  encode/decode and single SQLite commit path. `desktop_workspace_keys` holds the
  unchanged Windows DPAPI and Linux PBKDF2/AES-GCM device-key functions, re-exported
  through `desktop_store`. `desktop_workspace_receipts` receives the existing
  workspace owner; live authorization, uncertainty checks, receipt cursor and
  persistence still use that owner's state/methods. No new key or receipt store is
  created and no storage format/SQL/schema change is made.

Verification for this separation: source-token comparison of 68 moved Kotlin
operations (the explicit review-invalidation callback is the sole substitution),
public facade signature/default checks, AST comparison of 31 desktop actions and
shell construction order, Python 3.8 grammar/global-binding inspection, exact
patient validation/submission comparison, Kotlin delimiter/import and focused
type/callback checks, shared-state/lock/artifact ownership checks, and staged plus
unstaged Git whitespace checks. The remaining Ward/protocol/forms split also
preserves 41 Kotlin operation token sequences (shared-guard calls qualified), 18
desktop protocol definitions, 13 form functions and six delegated Client methods;
Client/default signatures and the other Client methods are unchanged. Python 3.8
grammar/global bindings, existing facade imports and acyclic top-level import
paths were checked without importing/executing the UI or transport. These establish
source-level preservation only. The report/ward-presentation/workspace split also
preserves nine local report actions (PDF workspace calls qualified), nine existing
report publication/delivery/review operations, the ward search/workflow/Save-next/
delete-undo callback bodies, two device-key functions and seven receipt actions.
The other Workspace methods, signatures, encryption and commit bodies are
unchanged; Python syntax/global bindings, import paths, Kotlin delimiters/imports
and single-state/artifact ownership received source checks too.
No Gradle, compilation, build, packaging, test-suite work, GUI/device execution or
live Telegram operation ran. No schema, version or toolchain changes were made.

Current stack: Java 17, Kotlin 1.9.0, AGP 8.5.0, Compose BOM 2023.10.01,
Hilt, WorkManager, coroutines and Room/SQLCipher. Android minimum API 23;
compile/target SDK 34. Application version `1.1.1.20261007`, code 2; debug adds
`-debug`. No toolchain or format versions were changed for Excel integration.

## Startup and navigation

`MainActivity` shows the RTL splash before resolving app repositories, then
project setup if needed, clinician login/PIN unlock, and ward navigation.

- Splash: dark gradient, unchanged emblem on a 240dp white field, grouped
  credit cards with 440ms entrances staggered by 160ms, consistent names and
  automatic app version. A quick tap skips;
  the saved monotonic deadline runs for three seconds, including while held.
  A hold/scroll only prevents automatic dismissal: release closes immediately if
  time has expired, otherwise the original countdown continues. Pointer input
  observes initial events without consuming scrolling and releases its hold on
  cancellation. Activity recreation retains the deadline and clears the physical
  hold rather than restarting three seconds. Accessibility actions offer skip and
  keep-open/end-reading controls.
  Existing About content and logo are preserved. The duration, emblem size and
  animation changes were checked in source; no Android build or device check was run.
- Setup: in-memory Demo (selected by default), protected-file Join, or Create.
  A centered welcome/choice area uses large labelled radio cards with prominent
  selected styling; wide screens show three columns and narrow/large-font screens
  stack them. Mode-specific forms follow the choices, optional help follows forms,
  and the fixed keyboard-safe action/status area stays within the same centered
  content width. Demo is selected without being launched automatically; creation
  and protected-file validation still require their existing deliberate actions.
  Source/delimiter and Git whitespace checks passed; a disposable timer arithmetic
  probe covered expiry during a hold, early/late release and recreation. Android compilation, touch rendering
  and accessibility on devices remain unverified; no build/test-suite work ran.
  Demo uses
  hot-beverage patients/tree-named doctors, real offline forms/cards and explicit
  per-page-labelled local PDF export; it writes no clinical database or Telegram
  data. Reset/exit discards it; process death starts a new demo.
  The [organized Android demo](android-demo.md) now shares the real Patients/
  Dashboard bar/rail, dashboard/filter controls, fold-aware content, cards/forms,
  roster and sort editor. Secondary tools live in a drawer; report review and
  temporary settings are separate destinations. The selected dummy actor drives
  “My patients” and completion attribution; report export remains local PDF only.
  PDF options survive the file picker in the ViewModel without a settings store.
  Source/whitespace checks passed; this demo reorganization has no build/device
  verification.
- Ward: Patients/Dashboard, plus Activity only for admins across all entry points.
  The compact floating bottom dock uses equal-width destinations, icons above
  always-visible labels, outlined inactive/filled active icons, and a small tonal
  icon indicator with a 160ms color transition. Full items retain 48dp minimum
  targets, selectable-tab semantics and wrapping labels. System-navigation padding
  and transparent side gutters remain; list content scrolls behind them with end
  clearance. The demo shares the same dock. From 600dp a rail
  is used. Drawer has compact groups and a two-column hospital/user/shift header.
- Adaptive panes: list/detail at usable 760×360dp or larger; a 48dp accessible
  resizable divider, fold-safe regions and retained selection/scroll state.
  Compact layouts use full-screen details/support sheets. Phone card taps edit;
  pane taps select details.
- Rounds follow filtered/pinned/grouped order with Previous/Next, identity and
  position. Editors/save operations guard navigation. Successful edits retain
  filters and reveal/highlight the patient; filter-hidden results are explained.

## Visual design system

Shared fields, pickers, actions, notices, section indexes, searches, whole-row
choices/toggles, loading/metric/comparison rows and PDF-style controls use theme
shapes, typography and `UiSpacing`. Busy form state also gates locally enabled
controls. Login retry is an ordinary button and never creates or pins a registry.
Settings content and shortcuts share an ordered definition; filters apply live
and retain patient scope. Arabic number/time/role terminology is shared.

Appearance (System/Light/Dark/AMOLED) is independent of the accent. Fresh settings
and demo now default to Light/Turquoise, with white/light-neutral surfaces. Stored
choices remain respected. The eight stable palette IDs now display Lapis, Emerald,
Iris, Saffron, Dragonfruit, Turquoise, Graphite and Poppy; desktop uses the same
names/primary colors and Light/Turquoise startup defaults. Severity still uses
shared `patientBadgeColors`; diagnosis categories do not use error colors. Source
contrast calculations covered 32 accent/text pairs (minimum 6.08:1), without
establishing rendered accessibility.

Input fills contrast subtly with the background in every state. `AppField` paints
fill behind the measured native container while its decoration stays transparent,
so the floating-label cutout clips the border without a rectangular fill patch.
Native semantics, errors, helper text, shape and minimum constraints remain.

Cards keep horizontal header actions, pinning in the menu/accessibility actions,
concise collapsed summaries and nonzero task counts. Expansion retains full
clinical content. Individual transitions are brief; bulk expansion is immediate.
The missing `gridItems` import alias reported in a refresh build was restored.

## Patients, tasks and persistence

- Full-screen add/edit/copy retains one draft with free text and optional per-field
  item tools, including labs,
  validation links, explicit discard, and keyboard-safe Save/Cancel. Editing starts
  with clinical/tasks; new entry starts with demographics. Saved identity and
  centered compact Clinical/Labs/Tasks/Personal shortcuts remain visible.
- Age first, «أو», birth year share a row; admission number/date, sex/companion,
  and both responsible-team pickers are paired. Numeric admission/PIN keyboards,
  diagnosis-specific placeholders and task time pickers are retained. Mode labels:
  «حقول إدخال عامة» / «حقول إدخال مفصلة».
- Default-off Save and next runs ordinary validation/revision checks and advances
  to the captured displayed successor only after successful save. Failed/stale
  saves retain the editor; missing successors return normally. Demo stays in memory.
- Room schema 5 stores tasks with the patient. CSV schema 7 has 22 clinical columns;
  doctor spreadsheet CSV is schema 1. Patient/shift revisions protect stale drafts.
- `WardMutationRepository` commits clinical changes, pending state and audit in
  one IO transaction. Sort writes positions and changed revisions together;
  local CSV, bundles, text and PDF share ordering. Rollover rejects nonempty targets.
- Stale patient/roster/sort edits retain a draft for explicit saved/draft review;
  choosing values does not save. A subsequent save uses the reviewed revision.
- Tasks have stable IDs, description, optional owner, priority, deadline, state and
  completion identity/time. New tasks start unassigned. Pending includes overdue;
  cards count per patient, dashboards ward-wide. No acknowledgment workflow exists.
- Repository save stamps completion and validates task owners; reopening clears
  completion. Narrative notes never become tasks. Rollover retains unfinished tasks
  and deadlines, clearing owners when reassigning; copies start without tasks.
- Tasks persist through backup, CSV, sync, conflict review and reports. Shared display
  puts overdue first/completed last without rewriting stored order. Deadlines use
  absolute instants and the 08:30 local shift boundary.

## Project connection and authorization

Encrypted device preferences hold connection, topic IDs and any shared data key;
credentials are absent from APK source, clinical exports and backups. Android
application backup is disabled. PINs use salted PBKDF2 and constant-time comparison.

Join uses a password-protected `.srjoin.json`. Create alone accepts manual
connection/topic entry and can generate a random shared 256-bit data key. Join
export stages complete ciphertext privately; Android 10+ primarily saves a
pending MediaStore row to `Downloads/ShiftReport`, verifies before/after publication
and deletes failed new files. Sharing/optional picker remain; older Android uses
sharing. Only random staging IDs enter saved state. The reported empty-file cause
has not been reproduced on the owner's device.

Same-project reconnect is accessible before login. Candidate bot/group/admin/
pinning and encrypted pinned-text checks run before committing the replacement;
manual token entry requires live admin authorization. Only the token changes:
clinical records, pending edits, topics, bases and shared key remain. Different
project/key/topic files are blocked. BotFather revokes old tokens; distribute a
fresh protected join file afterward. Join files are reusable, not one-time access.

Android Settings gates project administration against both navigation access and
the current session/active doctor registry. Non-admin Settings state excludes
supervisor destination records; token replacement and join-file export controls
are admin-only. Credential/passphrase dialogs close and clear their input when
admin access changes. File-based reconnect remains available to non-admins and
before login. Tokens and shared keys are not displayed in Settings. These UI
changes were checked by source inspection and whitespace checks only; no Android
build or device verification was performed.

Sign-out retains project, registry and local PINs. Enrolled doctors can re-enter
through `LocalPinLoginManager`, which rechecks active identity/hash and persists
attempt limits: five failures block for five minutes. First enrollment and forgotten
PIN verification use Telegram. PINs/passphrases never enter logs or saved UI state.
Biometric/automatic locking is optional. `AdminAuthorizer` rechecks the session and
live registry below navigation for privileged mutations.

Doctor mutation/import authorization, reviewed snapshots, protected identities,
reference guards, pending state and audit commit together. CSV import previews the
full proposed merge, rechecks actor/registry/references at commit, excludes PINs and
preserves local aliases/PINs for unchanged identities. Telegram-identity changes
invalidate old PINs. The initial manual patient importer is not the shared-update
path; historical data-integrity findings are tracked separately in
[the review record](reviews/data-integrity.md).

## Synchronization and encryption

Patient/doctor channels share `RemoteSyncStateStore` and its mutex for pinned IO;
doctor orchestration lives in `DoctorSyncCoordinator`. Both platforms use manual
full-snapshot downloads and publication. Patient records, deletions, stored order
and roster are taken from the reviewed local snapshot; remote fields/rows are not
merged into it. Android doctor fetch replaces the full portable registry, preserving device-only
PINs/aliases and protected identities/references. Fetch and Publish are separate,
explicit actions: neither selects the opposite direction or publishes on a missing
remote registry. Ordinary doctor/team/permission/destination edits save locally;
the registry page shows pending portable changes and reviews the exact registry
before publishing. CSV import retains its explicitly labelled save-and-publish
confirmation. Report sending validates published identities/registry without
applying or uploading doctors implicitly. An actionable typed error identifies
Fetch versus Publish; fetch from report invalidates previews and reloads review.
Permissions and pending local edits remain enforced below navigation.
Android derives doctor pending state from the canonical portable registry compared
with its last confirmed local base. PIN/alias/timestamp-only writes and unchanged
saves do not create shared changes. Pending checks reconcile an outdated boolean
flag inside the Room transaction; a missing/invalid base preserves the recorded
flag. Full doctor fetch and patient-publication guards use that same check, retain
in-flight snapshot/revision protections. Destructive doctor fetch still blocks
actual unpublished registry edits; explicitly confirmed local patient publication
allows unrelated registry changes while validating published doctor assignments. This corrects an evidenced source path for repeated local-doctor-change
warnings; the user's device warning has not been reproduced or checked in a build.
Production-source/whitespace checks only; no test-suite or runtime work ran.
Pending saved local work blocks a destructive download on both platforms until
publication; desktop also keeps encrypted recovery before replacing its view. In-flight edits,
local revisions and report confirmation remain protected. Immutable documents,
three-shift bundles, recovery and the publication journal remain unchanged.

Android no longer schedules background synchronization. The worker, scheduling
hooks, interval and auto/Wi-Fi controls were removed. Startup cancels the old unique
job. A manual publish performs one attempt, without automatically re-uploading on
a publication race. Reviewed patient publication explicitly authorizes complete
local replacement under last-successful-write-wins, as described below.
The last successful pinned write remains authoritative, not an atomic transaction.

Local revisions and pointer rechecks detect stale work; remote competing writers
can still race. A new backend or atomic compare-and-swap is outside accepted scope.

Optional AES-GCM encrypts patient bundles, doctor registries, pinned state,
announcements and text reports. Fresh nonces, authentication tags/purpose bytes and
explicit decryption failures are retained; compressed text uses `SRMSG1:` and
files `SRDATA1`. The shared key travels only in encrypted settings/protected join
files. PDFs/captions intentionally stay readable; Telegram metadata is visible.
No automatic plaintext fallback, key rotation or existing-project conversion exists.

### Explicit local-copy publication

Android report confirmation, Android standalone patient publication and desktop
publication/send reviews explain that confirmation replaces the published shift
with the complete reviewed local snapshot. There is no separate force checkbox.
Confirmation uses the existing local-copy publication path (and journal `forced`
field); it does not download patients over local edits. Pending or different registry fields do not impose a blanket
block on confirmed patient publication. Published active roster/task owners and
resident/supervisor assignments must remain readable against the published registry;
new/unpublished or clinically incompatible assignments need registry publication or
correction first. Doctor-account changes are never uploaded implicitly.
Current clinician authorization, registry-pointer freshness, local stale-review checks, validation and
uncertain/duplicate-delivery guards remain enforced. The last successful pinned
write is authoritative; Telegram still offers no atomic multi-writer transaction.
Read-back uncertainty remains explicit and no automatic publication retry is added.
Desktop review can start without a downloaded baseline and replaces the same day's
published entry while retaining other bundled shifts. A different same-day shift ID
is explained in the replacement review rather than requiring a separate mode.
Android report publication now uses the CSV review token to bind local CSV, actor
and registry through upload, in addition to matching the report preview. The final
button explicitly says «نشر نسختي وإرسال التقرير» and explains that unrelated doctor
changes remain unpublished. Assignment checks run during preparation and before
publication, including after file transport. This correction received source-level
call-path/guard inspection and whitespace checks only; no build or live send ran.

Verification: Python AST parsing and focused production-source/call-site inspection,
plus `git diff --check`. No test-suite/migration inspection or work, builds,
compilation, packaging, GUI/device execution or live Telegram verification was
performed for this change.

### Permanent-superadmin empty start

Project Management on Android and desktop now offers a separately reviewed empty
start, visible only to the permanent superadmin. The service rechecks the signed-in
actor against the published doctor registry and rejects ordinary admins. It uploads
one empty current-shift snapshot (the actor is its initial roster), replaces the
current/recovery patient pointers and starts a fresh publication journal. It never
deletes Telegram messages/files or sends a report. Doctor accounts/PINs, connection,
topics, encryption key, preferences, audit and delivery records remain intact.
No database/interchange version changes were made.

After confirmed remote publication, Android transactionally removes local shifts
and their cascading patients/recycle rows, clears the saved patient form/download
undo/freshness/baselines, and installs the empty shift. Desktop similarly replaces
its clinical workspace and keeps a durable reset receipt. Both recheck the reviewed
local state before removal; a racing local change is retained with an explicit
remote-success/local-failure message. Android's ward observer rebinds to the new
shift. Review tokens are held in memory and consumed before transport. Normal
publication keeps its existing history-merging behavior; only the reviewed reset
uses the explicit history-replacement path.

This is a new empty working snapshot, not a project-generation barrier. Other
devices must retrieve it before continuing; publishing an older local copy can
restore its contents under the accepted last-successful-write-wins model. Telegram
history and separately exported backups remain available for deliberate recovery.
The feature was implemented but never run on the owner's data.

Fresh start does not require ordinary patient publication/conflict review or
publishing pending patient edits. Its own deletion confirmation includes those
edits; changes made after that confirmation still invalidate it. Android already
reads only project state and the doctor registry. Desktop now uses
`fetch_registry()` for reset preparation and authorization rather than downloading
and parsing the previous patient bundle, so an unavailable/invalid old patient
file cannot block reset. Ordinary full fetch behavior is unchanged. Settings
wording distinguishes deletion confirmation from report review. Python AST and
source call-path inspection plus Git whitespace checks cover this correction;
no build, GUI/device run, or live reset was performed.

### Focused settings and navigation

The patient editor repeats its complete existing unsaved-draft comparison beside
Save/Save-next; desktop's retained-form loop updates a matching footer label while
keeping retained drafts distinct from saved patients. Android Save/Save-next and
report Preview/Send reuse `AppFieldPair`'s width/font-scale stacking. Existing IME
insets, focused-field scrolling, exact drafts, validation and discard handling remain.
The shift-doctor picker's bounded body scrolls search/count/results together, so
the search header cannot consume the available results area on short/large-font
layouts. Confirmation actions remain outside that scroll body.

Android report outcomes use separate semantic success/warning notices for patient
publication and delivery, explicitly explain that delivery failure does not undo
confirmed publication, and offer history for uncertainty/partial delivery or return
to patients after complete success. Errors and their specific retry/registry action
share one section; repeated confirmed-count text and redundant uncertainty notices
are reduced. Desktop outcome wording makes the same two results explicit.
Fresh-start confirmation on both platforms labels erased clinical records and
retained doctors/accounts/project configuration separately. Pinned delivery warnings
show the warning/action without the routine freshness row; routine reminders/status
still scroll with the patient list. These are presentation changes, with existing
authorization, review, local revision, publication and delivery guards retained.
Verification: Python 3.8 AST parsing, focused Kotlin delimiter/import and source
call-site/guard inspection, and staged/unstaged Git whitespace checks. No build,
test suite, GUI/device execution, large-font/keyboard rendering or live operation ran.

Android patient lists include a compact, Telegram-style fetch reminder before
the list status/controls, including the empty-ward view. Its paper-plane badge,
theme-aware rounded surface, fetch action and labelled 48dp close target keep the
reminder advisory and dismissible. Dismissal uses saveable UI state keyed by shift
and current doctor, survives Activity recreation, and resets for a different
doctor/shift; no settings/database write is made. Archived read-only lists omit
it. Fetch opens the existing confirmation and retains unpublished-edit checks;
there is no automatic download or editing block. Source call-site/state/guard
inspection and Git whitespace checks only; no build or rendered device check ran.

Android's «إعداداتي» is now a personal-preference menu only: Appearance/display,
Rounds/shift preferences, Report preferences, and Account/lock. Each opens its own
route; single-purpose pages show their controls immediately. Theme and patient-card
presentation stay together on Appearance. Sign-out belongs to Account/lock.
Patient publishing, patient CSV files, encrypted backups/restoration, connection,
protected device invitations, supervisor report destinations, diagnostics and
permanent-superadmin empty-start each have a separate page. Shared ViewModels and
existing mutations/export/picker/review implementations remain in use. Connections
still preserve clinical state/key/topic boundaries; reset still requires explicit
review and live permanent-admin authorization. Administrative sections disappear
when the live role is unavailable; reset additionally requires permanent status.

The Android drawer separates daily ward work, patient-data tools, personal settings,
connection, admin-only team/project actions, and help/app information. Publishing,
files and backups no longer lead to the same catch-all page. Team roles/accounts,
announcements, invitations and report destinations have accurately named entries.
Activity remains admin-only and reset is shown only to the permanent admin.
The Android drawer pins its compact two-column identity header above the navigation
scroll area. The header has a separate, height-capped viewport (up to 40% of usable
drawer height) so short windows/large fonts leave room for navigation; overflowing
identity text can be scrolled within that header. Status is a compact text line.
All five categories use the same softly tinted expandable group, icon/chevron,
indented child rows and announced open/closed state. Current shift is expanded by
default; preferences/connection joins the same group structure as data, admin and
help. There are no standalone rows or highlighted report button between groups.
Selected rows retain their tonal fill, 48dp targets and wrapping labels. The report
row keeps its label, review destination and availability guard. Source checks cover
all 22 actions, role/availability guards and separated header/body scroll scopes;
no build or rendered/device/accessibility verification ran for this refinement.
Existing route aliases remain valid; focused settings use validated enum-based
routes. About/logo and pre-login reconnect behavior are unchanged. The settings
section-position tracker is now created only when more than one visible section
needs an index. The preferences hub has no sections, and permission-filtered admin
pages can also have none; neither now reaches the tracker's nonempty-list lookup.
This fixes an evidenced source crash path when opening Settings; the reported
device closure has not been checked against a crash log or rerun on a device.

Desktop follows the same separation: personal preferences link to focused appearance,
PDF preferences, account/lock and shift-time windows. Connection holds only setup/
reconnect. Patient transfer/publication, local shift/drafts, patient CSV files and
backups have distinct windows. Doctor CSV import/export stays with team/role
administration and bypasses the old mixed patient/doctor file chooser through
explicit choices, retaining its service authorization. Invites, announcements,
Activity and reset are admin sidebar actions rather than connection settings;
report delivery history lives with Reports. The admin group is hidden for ordinary
members (and live-project administration is hidden in demo); local admins retain
Activity access without bypassing live authorization for privileged mutations.

Verification: source route/action and settings-branch mapping, shared-component
imports, Kotlin delimiters, Python 3.8 AST parsing and Git whitespace checks.
The drawer and bottom-dock visual refreshes received source action/guard, delimiter
and whitespace checks only; their rendered layouts and accessibility remain
unverified on a device. Bottom navigation retains the existing callbacks, admin-only
Activity entry, system insets, fold-aware placement and report-review action.
No Gradle/build/compilation/packaging, GUI/device interaction or live Telegram
verification ran. No clinical/schema/version changes were required.

### Daily workflow organization

The coordinated UI pass keeps existing operation owners and clinical models:

- Android's ward header uses one compact neutral toolbar with screen title/date
  and 48dp menu/expand/add/search controls. Hospital context stays in the drawer;
  normal publication details scroll with the patient list instead of adding pinned
  header rows. Busy/uncertain-delivery notices remain above the content. Patient
  counts and active filters have separate visual roles; no-match results offer reset.
  The empty ward has one full-width «إضافة أول مريض» action and two bordered source
  rows with icons/explanations: Fetch team data and Copy previous shift. Duplicate
  add/search/expand/report controls are hidden on the empty patient destination.
  Read-only empty shifts expose no editing/source actions; busy/missing-shift guards
  and existing fetch/rollover reviews are retained. Ward tasks stay inside filters.
  Desktop retains its separate action/search row,
  now displays the search text with active filters, and labels today's empty choice.
- Both dashboards lead with urgent patients/overdue tasks, then ward totals,
  followed by readiness/publication information. Routine ward status has expandable
  timestamps/details on both clients and one visible next action; unresolved-delivery
  warnings stay visible without duplicating that action. Prior-publication view,
  local undo-fetch, portable backup and desktop-only recovery have purpose-specific
  labels; undo-fetch and local export/delivery tools are secondary disclosures.
- Personal settings use whole-row destinations with short summaries rather than
  repeated Open buttons. Android report setup orders roster, destination/format,
  summary and readiness, changes, then advanced styling and secondary local/export
  tools. The normal report screen shows saved PDF defaults, with layout/appearance
  together behind one optional customization control; desktop uses the same boundary.
  Preview/Send remain fixed; last-attempt publication and delivery outcomes
  are separate lines with access to the delivery log. Desktop shares that outcome
  vocabulary and its existing reviewed options/preview workspace.
- Patient cards place identity/alerts before clinical content and task details.
  Android expanded cards expose Edit separately from the full record; desktop
  collapsed cards show priority/high badges and a bounded diagnosis summary while
  expansion retains full content. Forms separate clinical content, responsible
  team, and priority/badges. Free text is the default; each treatment/follow-up/lab
  field has its own optional item/order/date tools over the same caller-owned draft.
  The screen-wide General/Advanced selector is removed on Android and desktop.
  Clinical-first editing, demographic-first creation, task persistence/completion
  stamping, and fixed patient actions remain intact.
- Task dialogs place description/deadline before optional owner/priority and state
  explicitly that accepting a task only changes the patient draft. Pending tasks
  are visible directly; only completed-task history is optionally collapsed.
  Settings use their section index with visible bodies, without a second accordion.
  Doctor choices
  are searchable, grouped by team/clinical role, and show selected state on both
  clients. Roster selection retains unlimited size, active-doctor validation and
  revision/fingerprint protection. Android ward roster shortcuts open report preview's
  first section and existing picker; desktop report/toolbar use its one roster picker.
  Numeric ranks stay in permission management/review, not ordinary doctor cards.
  Doctor forms group personal details, clinical
  role/team and account verification separately; desktop administration first
  chooses Doctors, Teams or App permissions. Existing mutation authorization stays
  below the UI. Doctor/roster/task cancellation checks unsaved changes; Android
  stale/discard review replaces its underlying editor dialog rather than stacking
  another active editor. Patient draft/discard handling remains in use.
- File pages state selection/review/confirmation and whether the result is local
  or published. Doctor-import progress/cancellation and review wording distinguish
  reading a file from publishing its full registry. The original device-specific
  doctor CSV failure remains tracked separately; this pass does not establish its
  cause. Shared section/destination controls and quieter desktop disclosure headers
  reduce competing button emphasis. Android focused fields request visibility as
  IME insets change; desktop entries reveal themselves in their existing scroll
  container. Team fields stack on narrow layouts, identifiers use LTR numeric input,
  and existing Arabic shaping/RTL, full text expansion and growing targets remain.

Verification: production-source mapping of explicit doctor directions, pending-change
next actions, replacement-confirmation paths, per-field draft ownership, settings/
roster/PDF/recovery boundaries, state/permission and save/review guards. The combined
working-tree snapshot passed Python 3.8 AST parsing for 14 production modules,
Kotlin delimiter/import checks for 44 production files and Git unstaged/staged
whitespace checks. Report retries reopen destination-specific confirmation rather
than invoking a general send directly. These checks are not compilation or runtime
verification. No Gradle/build/packaging, test-suite work, GUI/device interaction or
live Telegram operations ran. The compact-header/empty-ward follow-up also received source callback/guard,
delimiter/import and whitespace checks only. Rendered Arabic wrapping, large fonts,
keyboard and fold behavior still require device/desktop review. No data/schema/toolchain/version
changes were made for this pass; unrelated working-tree edits were preserved.

### Report, navigation and doctor-import feedback refresh

Android's «إرسال تقرير» drawer/status actions now open the four-stage flow described
in [Android review corrections](#android-review-corrections): Doctors → Destination
→ Preview → Send. The ward floating button now adds a patient. Readiness notes,
revision-protected roster editing, General/Supervisor selection, optional PDF
customization, local exports and delivery history remain. Final confirmation,
exact-file checks and delivery resolution still use the existing ViewModel paths.

Desktop normal and supervisor-batch sending now share one options/change-review/
preview workspace. Roster summaries expand on demand; batch destinations are
chosen inside that workspace. Preview prepares the complete publication review and
exact payloads without publishing. PDF batches expose one opener per selected
file and require every file to have been opened before the one final confirmation.
Changed clinical data, actor/project, destination or options invalidate the prepared
payloads; final confirmation rechecks file bytes and local state. The existing
`ReportDelivery` coordinator retains publish-before-delivery, live authorization,
UNKNOWN/repeat guards and per-destination outcomes. Standalone data publication,
local exports and settings retain their existing paths.

Patient status wording now distinguishes saved-on-device, unpublished changes,
last known publication and last fetch without claiming current online equality.
Android records actual successful publication time in a device-local per-shift
settings key; old unknown times remain unknown, and empty-start clears/replaces
that metadata with its new shift. Settings use plain patient-data/doctor-registry
labels instead of “CSV sync.” Desktop derives known publication time from the
existing snapshot journal. No clinical or interchange schema/version changes were
made.

The focused drawer/settings structure is described above. Activity remains
admin-only and report actions remain separate from preferences and data tools. Ward-task
actions remain inside filter controls only on both clients, with Android disabled
for an empty ward; per-patient task counts and dashboard totals remain.

The ignored `android/doctors-import.local.csv` contains 33 records (5 supervisors,
28 residents), all with 16 columns and the supported schema. Source-level field
checks found no missing names, forbidden name/title characters, duplicate IDs/names/
Telegram IDs, invalid role/gender/rank/permanent flags or malformed numeric/date
fields. The file was not changed or imported into a real project. The owner's
reported symptom is no visible result after file selection. Import failures
previously used a transient snackbar; Android now shows persistent error dialogs,
a modal progress state and explicit feedback if another operation blocks reading.
The underlying device-side failure is not yet identified; identity collisions,
clinical-reference and permission guards have intentionally not been bypassed.

Checks for this refresh: edited Python production modules parsed with Python 3.8
syntax rules; Kotlin delimiters/shared-component imports and affected call sites,
reset authority/retention paths, CSV fields, palette contrast, direct report routes
and desktop preview/publication/delivery guard paths were inspected. Git whitespace
checks passed. No test
suite work, Gradle, compilation, build, packaging, GUI/device or live Telegram
verification was performed. Source completion is not runtime verification.

## Reports and exports

The ward «إرسال تقرير» action opens report preview directly, with readiness notes
and explicit destination/format confirmation retained. Shift doctors are selectable first in preview,
with active-doctor/revision checks and no fixed maximum roster. Fresh snapshots
and supervisor partial-delivery checks prevent reusing stale review.

Classic tables and linked portrait A5 cards include saved clinical fields, badges
and tasks. Card indexes/name links/Previous–Index–Next support navigation; long
content paginates at readable sizes. Routine/unassigned labels and patient last-edit
metadata are omitted from PDF without dropping tasks or completion history.
Uncommon PDF settings remain under Advanced.

Doctor CSV and encrypted backup prepare encrypted private staged contents before
the picker; saved state holds only random IDs/counts. Writes close and verify bytes;
failed/cancelled exports discard staging and remove new destinations where possible.
Patient CSV verifies nonempty bytes before publishing. Backup passphrases are cleared
after preparation; PDF is separate from this CSV-specific hardening.

`CsvCodec.encode` now quotes the complete metadata as one CSV field, preserving
multi-doctor commas and sort-JSON quotes. The decoder, clinical-row codec, columns
and schema version remain unchanged. Rebuild Android and republish before shared use.

## Standalone desktop client

The [`desktop/` source](../desktop/README.md) supersedes the Excel helper with a
CustomTkinter client targeting Windows 7 SP1 / Python 3.8.10 and Linux desktop /
Python 3.8 or later. No workbook, Excel,
VBA, COM or pywin32 is used. The owner will bundle it separately; no executable
was built. Original clinical artifacts and Android formats/versions are unchanged.

The client provides protected join/password startup, optional literal `.env`
preferences, private-code identity, patient/task/badge editors, current/archive
shift selection, explicit carry-forward, unlimited active-doctor roster, search/
filters/counts and stored sorting. Offline edits are encrypted local drafts;
publication requires fresh verified identity/membership and complete
snapshot review. Desktop can create the first admin/project and export join files;
reviewed ongoing doctor/role/destination administration is also available.

The owner-authorized desktop exception `CONNECTION_MODE=raw` accepts literal local
bot/group/topic credentials plus the existing data key via `.env` or a separate
`--env` file; see the commented raw section in `desktop/.env.example`. Mode and encryption choice are
explicit; mixed join/raw settings, duplicate keys, malformed IDs and missing or
inconsistent encryption keys fail without exposing values. Raw startup verifies
a live-registry admin through the existing Telegram code flow before opening
SQLite. Protocol authorization rechecks admin role and membership for subsequent
shared actions. No admin identity/code/password is supplied by `.env`. Normal
join-file startup and protected-file reconnect remain available; Android is
unchanged. Raw files contain plaintext secrets and must stay private/out of
bundles; token possession still permits direct Telegram access outside the client.
Dummy configuration and authorization probes, Python 3.8 grammar inspection and
whitespace checks cover this change; live Telegram/admin startup and target
Windows behavior remain unverified. Later Linux dummy GUI probes are recorded below. No build or test-suite work was run.

Desktop project creation uses `desktop_project.py` for protocol rules and
`desktop_onboarding.py` for the Arabic dialogs, with common Windows/Linux paths.
Complete raw `.env` settings expose **إنشاء مشروع جديد** before login. The first
human admin must own/administer the Telegram group and verify a private code;
challenges expire after three minutes/five attempts with a resend cooldown.
Successful verification allows thirty minutes for review; every write still
rechecks live group-admin authority.
The bot must administer the group with pin rights. Any existing pin or local
baseline/draft/receipt blocks creation. Review shows hospital/group/topic mapping,
first admin/clinical role and encryption setting, without revealing the token/key.
Every group write rechecks authority and the absence of a pin. A sealed profile
and configuration prevent stale/reused review. A persisted encrypted UNKNOWN
receipt precedes the initial doctor upload. The app publishes one permanent-admin
registry, sends/pins the current JSON index in General and verifies both before
confirming the receipt/session; no patient or shift is created automatically.
Failures do not silently delete artifacts or retry. This cannot guarantee empty
chat history or atomic concurrent creation. Existing pin recovery remains manual.

Verified admins can export Android-compatible protected `.srjoin.json` files.
The live registry/session is rechecked below the UI before preparation/write;
output includes only connection/topics/key and uses the existing authenticated
password envelope. Existing files are refused and new files are reopened for
verification; passwords are never persisted. The key is preserved exactly.
Fake-transport probes passed for plaintext/encrypted bootstrap, initial wire/index
formats, review/authority/stale-data guards, challenge limits, concurrent-pin abort,
persisted UNKNOWN/restart blocking and protected join-file round trips/overwrite
refusal. Actual Linux first-admin/review/export dialogs, admin-only visibility and
screenshots passed with dummy data and all real network blocked. Python 3.8 grammar
and whitespace checks passed. Real Windows/DPAPI execution, Android-device join
and live Telegram creation/export were not performed. No build/package or
migration/test-suite work was run; existing versions are unchanged.

Local persistence is a project-fingerprint SQLite workspace containing an AES-GCM
blob: baseline, draft, registry cache and delivery ledger. No connection secrets
are persisted. Encrypted projects derive the local key from the stable shared
key; other projects use a random local key protected by Windows DPAPI CurrentUser
or a separately entered Linux passphrase. Linux key wrapping uses PBKDF2-HMAC-
SHA256 (600000 iterations, random 16-byte salt) and AES-GCM with the project
fingerprint as authenticated data; the passphrase is never persisted. Missing
keys for existing databases, tampering and wrong passphrases fail explicitly.
No SQLite or interchange schema/version changes were needed. Draft replacement
keeps one encrypted previous copy; explicit backups/restore preserve the live
ledger and invalidate review. File-based reconnect is available before login,
validates candidates before replacing an open connection and refuses different
projects/keys/topics. A Windows named mutex or Linux flock prevents a second local
instance. Linux uses XDG_DATA_HOME or ~/.local/share and creates owner-only
key/database files. Linux setup now installs unpinned binary dependencies in
`desktop/.venv`, accepts the owner's current system Python and keeps Windows
compatibility requirements separate.

The [wire contract](../desktop/PROTOCOL.md) retains state v4/bundle v1/CSV v7,
registry/join/AES-GCM envelopes, reviewed whole-shift replacement including
deletion/roster/order, revision and task-completion stamps, three-shift history, recovery
pointers and publication journals. Telegram remains last-successful-write-wins;
checks are not atomic multi-writer transactions. Complete Arabic PDFs are generated
using ReportLab/shaping/bidi and an embedded TTF, previewed through Windows
startfile or Linux xdg-open,
and sent only after destination/content confirmation. Local PDF export also works
offline. UNKNOWN receipts are durable before mutation and block blind retries;
receipts do not update Android's local sent/audit UI. PDFs remain deliberately
readable, including temporary previews; normal interpreter exit removes previews.

Python 3.8 grammar and pinned GUI imports, Unicode bundle round trips, encrypted
SQLite reopen/tamper rejection, UNKNOWN retry blocking, receipt-preserving draft
restoration, task completion stamps, five-doctor rosters, stale-publication rejection,
receipt-before-upload and remote/concurrent order handling passed on the host
interpreter. A long dummy report generated eight PDF pages; its first page was visually inspected. Python 3.8.10 x86/x64 binary-wheel
resolution passed for the pinned dependencies (timezone backport explicitly added
because cross-target pip evaluates markers against the host). No build, package,
Python 3.8.10 execution, Windows 7/DPAPI/Tk runtime or live Telegram/device round
trip occurred. Editable Arabic/bidi/cursor behavior needs target verification.

On 2026-10-19, Linux offline checks covered shared-key/passphrase SQLite reopening,
wrong/missing/tampered key rejection without overwriting the database, private file
modes, receipt-preserving restore, cross-process flock rejection/release, XDG paths,
automatic font selection and dummy Arabic PDF generation. Simulated xdg-open
handlers covered literal filename arguments and missing/failed handlers; no real
viewer was launched. Linux shell syntax, Python 3.8 grammar, new/changed file
whitespace and local documentation links were checked. The host is Python 3.14
using existing temporary dependency imports, not a fresh pinned Linux installation.
Full graphical Linux clinical editing, actual PDF opening and live Telegram/device
operation remain unverified. Windows 7 dependencies and DPAPI behavior were not changed or
executed; no builds/packages or test-suite work were performed.

Linux Arabic display now uses `desktop_widgets.py` for presentation-only shaping
and bidi ordering of labels, choices, status, tabs, prompts and readonly reviews.
Logical paragraphs wrap before line shaping. Choice variables/callbacks retain the
original Unicode/IDs; review copying returns the complete logical source. Form/search inputs use `desktop_input.py`: original Unicode is the editing
model, shaped physical lines are the display, and caret/mouse/selection positions
map between them. Typing, clipboard, deletion, wrapping, undo/redo, numeric-field
validation and bound variables operate on logical text. Actual patient Save was
checked to retain that text without presentation glyphs. The earlier separate
readback preview was removed. Passwords and native mode retain native widgets.
TEXT_RENDERING defaults to Linux shaping / Windows native rendering; native/shaped
overrides remain. Persistence and PDF/Telegram representations are unchanged.

Actual main-window, editor and Arabic input screenshots were inspected with dummy
data on 2026-10-19. Input probes covered mixed Arabic/English/numbers, cursor/mouse
placement, selection/copy/cut/paste, Backspace/Delete, undo/redo, multiline wrapping,
diacritics/zero-width marks, numeric validation, bound-variable updates and patient
Save. Setup_Linux.sh succeeded on the owner's current system Python; dependency
consistency, shell syntax, Python 3.8 grammar and whitespace checks passed. No real
`.env` or clinical data was read and no Telegram requests were made by the probes.
Full clinical/live Telegram workflows and Windows target rendering remain
unverified.

The desktop source and launchers live under `desktop/`. `.env` is the active
local settings file; `.env.example` is the single shared commented guide for both
join and raw startup. The redundant raw template and Android README were removed;
Android commands remain in the root README.

Obsolete Excel tools/guides, duplicate protocol and completed-review docs
were removed. Private original workbooks remain in ignored `local/excel/`.
Private workbook data in `local/excel/` and runtime credentials are preserved.
Six useful historical packaging/VBA/field-mapping references are hash-verified in
ignored `local/legacy-reference/`. The cleanup command preserves them before any
deletion and stops on an archive mismatch. These are reference sources, not a
runnable package of the current desktop app; no historical dependency pins were
installed and no packaging was run.

Cleanup verification used configuration parsing, documentation-link inspection
and Git whitespace checks only. No builds or test-suite work was run.

### Desktop recovery, richer reports and administration

The desktop now retains deleted patients and their original list positions in
optional encrypted workspace recovery records, with no SQLite/interchange schema
or version changes. Current-shift restoration requires an unchanged draft and a
missing patient ID, restores complete content and uses complete-snapshot review
before publication. Recovery survives reopen/publication and encrypted draft
backup; receipts still cannot be rolled back by restoring a backup. Previously
published task completions retain their identity/time through sealed recovery
history. Newly completed tasks are now stamped during local patient Save and
retain that identity/time through publication, as described below.

Reports support text and PDF; the Classic seven-column RTL table and linked
portrait A5 cards now follow Android's layouts, paper/orientation choices, five
palettes and dark appearance. Paragraph output remains available. Local PDF
export can save a separate file for each supervisor, including an unassigned group. Content
and stored patient order are retained. Local text export is explicit/readable.
Delivery keeps exact-file PDF preview or complete logical-text review followed by
explicit destination confirmation. Encrypted projects send text as a protected
document; plaintext projects use UTF-16-bounded Telegram messages. Partial or
uncertain multi-part delivery retains UNKNOWN and blocks an unreviewed retry.

`desktop_doctors.py` and `desktop_admin_ui.py` implement reviewed add/edit/delete,
supervisor destinations, promotion and eligible demotion. Protocol-level checks
require current Telegram identity/membership and registry admin authority, retain
permanent-admin/rank protections, reject stale registry/patient pointers and local
draft changes, and guard published/local patient/task references and current
rosters. New doctors use the current stable ID/wire representation; no Android
PINs are transported. Registry publication records UNKNOWN before upload, retains
unrelated index fields and confirms the exact registry/pointer by readback.
Updating the registry cache does not replace the private patient draft or its
patient review baseline. Telegram concurrency remains last-successful-write-wins.

Source/local verification, repeated against the applied production modules:
Python 3.8 grammar; dummy encrypted deletion/reopen/
restore/backup and ledger preservation; fake-transport add/promote/demote and
stale/permanent-admin/non-admin rejection; text chunking and encrypted/plain
transport; long Arabic table A4/A5, linked A5 card and landscape Letter paragraph
PDF generation. An A5 table page was visually inspected. New dialog interaction,
Windows 7, live Telegram and Android round trips remain unverified. No build,
compilation, packaging, migration or test-suite work ran.

### Names-only team overview and desktop PDF parity

Both platforms expose **حفظ توزيع المرضى بالأسماء فقط (PDF)** only to admins
(Android report preview; desktop sidebar). Android observes session/current-registry
admin status for visibility and uses `AdminAuthorizer` inside the renderer and
before final export. Desktop verifies the active Telegram session, current admin
registry role and membership before rendering and saving; it requires connectivity.
Actor changes or revoked authority stop the export even if its dialog was already open. Each supervisor gets one
separate table: supervisor heading, one comma-separated resident-name row, then
one patient name per row. Residents now come from independent one-supervisor
registry membership, including residents with no assigned patient. Empty active
supervisor teams are included; no membership is inferred from patient records.
Unknown/unassigned teams remain visible and every patient name is retained in
stored relative order within its group. The export is fixed A4 portrait, with three 56.7 mm supervisor tables per row.
Each table has a supervisor header, one resident-name row, then a patient-name
column on the left and an Arabic number column on the right. Global numbering
flows right to left across tables, then down to the next row. Overflow continues on A4 pages
with repeated team headers and no dropped names. It is a local
names-only overview; ordinary clinical report review/sending remains separate.

Android `PdfAssignmentExporter` measures team rows before laying out its
three-column A4 grid, uses the existing palette/settings and saves through the existing
PDF path on IO with byte verification. Its local snapshot does not require a
shift roster; normal clinical report building still does. The action works for saved shifts as other local exports do,
retains busy/conflict guards, and has a dedicated retry action. It does not write
clinical records, sync data or change any versions.

Desktop `desktop_pdf_content.py` shares report content, task display ordering and
the exact current Android palette values with the renderer. Classic tables use
Android's seven RTL columns and proportional widths with full-row continuation;
A5 cards use a linked index, repeated patient heading and Previous/Index/Next on
all continuation pages. Paper sizes/orientation, five palettes (including pastel),
dark backgrounds/text, report context and separate local supervisor files are
available. Per-supervisor files are fully prepared before writing a new set;
the combined names overview always remains one file. No clinical content, tasks,
completion history or saved badges are removed from full reports. PDF delivery
still requires actual-file preview and explicit confirmation.

Verification: desktop Python 3.8 grammar; dummy grouping/resident deduplication/
unassigned-name preservation; fixed-A4 three-table grid and 200-patient overflow;
Classic output for all five palettes in light/dark; long Classic/card/paragraph
PDF generation; visual inspection of the overview, Classic and a continuation card.
Separate local supervisor-file generation retained the unassigned group and left
the original shift unchanged; card output contained resolved internal navigation links.
Direct desktop renderer probes rejected non-admin, expired-session and mid-render
revocation attempts; verified admin generation succeeded with a fake transport.
Android authority-observer/renderer/final-save wiring and retry handling were inspected; no Kotlin compile,
Gradle/build, packaging, migration or test-suite work ran. Android rendering/saving,
new desktop dialogs, Windows and live Telegram remain unverified.

Current update checks: Python 3.8 grammar and full-snapshot proposal probes
confirmed local values, deletions, roster and order are used without remote field
merging. PDF metadata/text positions verified A4 portrait, three tables per row
and sequential 1–18 numbering flowing left to right; a 200-patient overflow PDF
was generated and the six-team grid was visually inspected. Android references,
removed scheduler/settings and full-download/publication paths were inspected at
source level. No Android build/compilation, packaging, migration or test-suite work
ran; Android rendering, live Telegram and device execution remain unverified.
Date-changing carry-forward is now one explicit whole-shift action on both
platforms, separate from retrieving a published snapshot (details below).

### Desktop grouping, local access, CSV and demo

- Ward grouping is presentation-only: none, supervisor, resident, diagnosis,
  gender or admission date. Stored numbers remain visible within groups.
  The five-level sort editor uses Android's existing sort-spec fields and
  ASC/DESC directions, keeps priority patients first and saves list positions and
  compact metadata together, reapplied with current doctor names before publication
  review. Manual arrows clear the sort levels. Text/PDF/CSV
  consume that same saved order; edits reapply the active sort.
- Device PIN enrollment/reset requires a current Telegram clinician session and
  fresh membership/account authorization. Salted PBKDF2-SHA256 hashes (600,000
  rounds), doctor/account binding and persistent attempt delays live inside the
  encrypted workspace, excluded from portable backups and both CSV formats.
  Arabic/ASCII digits are accepted. Reopening an enrolled workspace locks it;
  manual locking or 1/5/15-minute inactivity hides the ward and existing dialogs,
  preserving unsaved editors for unlock. Local workspace reads/saves/exports are
  blocked while locked. The active network operation finishes before the idle
  lock engages. Locking clears Telegram authority and reviewed publication;
  PIN opens local work only. Private Telegram verification remains available for
  recovery and is still required for shared publication/admin actions.
- Patient CSV export is a single Android schema-7 document in stored order,
  retaining tasks and complete clinical fields. Doctor CSV uses Android's existing
  schema-1 columns, omits local PINs and goes through live-admin before/after
  review and the existing pointer/draft/receipt publication checks. Import matches
  existing IDs/account/name without ambiguity; it retains omitted active doctors,
  permanent/current-actor privileges, reference guards and local access records.
  Deleted-only newcomers are ignored; an old deleted row cannot delete an active
  doctor. Unsupported portable extra options are rejected explicitly. Telegram's
  registry has no username/update/delete timestamp storage: export timestamps
  describe the export, and those CSV-only fields do not add registry fields.
- `--demo` starts without reading connection settings or opening a persistent
  workspace; the regular sidebar can also open a separate demo window. Demo uses
  the real forms, groups, sorting, tasks, roster, recycle and PDF renderer with an
  in-memory workspace and a client that has no Telegram transport. Network/admin,
  backup/import and CSV paths are blocked. Reset/exit discards the session.
  Beverage patients and tree doctors are dummy data; explicitly saved PDFs carry
  a DEMO footer on every page, including card continuations/index pages.

Checks for these additions: Python 3.8 grammar and module import; disposable
source probes for stable priority/multi-level order, group conservation,
patient/doctor CSV round-trips, non-admin/ambiguous/rank import rejection,
persistent PIN throttling/account rebinding, backup exclusion and locked local
operations; and in-memory demo reset/recycle/network/backup rejection. Generated
Classic, cards and paragraph demo PDFs (1/13/3 pages) had a DEMO marker on every
page; the Classic page was visually inspected. Desktop module import hit the
sandbox's read-only user-font path and CustomTkinter reported its fallback.
No graphical desktop/display was available: lock conceal/restore, native dialogs,
large-font interaction and Windows remain unverified. No build, compilation,
packaging, migration or test-suite work ran; live Telegram is unverified.

### Team membership and workflow consolidation

The owner authorized independent teams with at most one supervisor per resident.
Doctor editing on both platforms includes that link, with an explicit unassigned
state for setup. Lower administration/registry checks require active supervisor
references and block deleting/changing a linked supervisor until residents move.
Android stores `teamSupervisor:<id>` in its existing doctor options; Telegram adds
an optional tenth `teamSupervisorId` column, and doctor CSV retains its existing
schema-1 columns using portable `extra_options`. Retrieval accepts the previous
nine-column registry as unassigned teams; it never invents team links from patient
assignments. Both clients should be updated when publishing team data. No Room
schema/migration or numbered format/toolchain version changes were needed.

Android registry retrieval replaces the portable team link while retaining local
PINs/aliases. Existing ID normalization and CSV identity matching also remap team
references. CSV review on both platforms now names additions, changes, unchanged
imported rows and omitted active doctors retained as-is; Android excludes PINs,
aliases and incidental update times when classifying changes. Its active/admin
counts cover the resulting full registry. The existing stale-registry/live-admin
publication guards remain in place. Assignments PDFs use the independent team
roster and include empty active supervisor teams; individual patient resident fields
remain separate clinical assignments and no longer define PDF team membership.

Android's optional empty-shift prompt and explicit drawer action now offer the
same whole-shift copy as desktop: all patients, unfinished tasks with deadlines,
one keep/clear responsibility choice, fresh patient IDs, no completed-task copy.
Per-patient rollover decisions/review queues are removed. Android uses the previous
local shift (download to refresh it); the clinical transaction checks source IDs,
source revisions, target date/revision and target emptiness. The new shift uses
saved manual order with priority first. Desktop has a separate start-shift action,
keeps an existing empty current-shift identity/roster, refuses local/remote nonempty
targets and rechecks patient/doctor pointers after confirmation. Neither download
path creates copied patient records. Ordinary review/publication remains required.

Desktop navigation is grouped into project/identity, local data/publication,
reports and files/recovery, with explicit local-save → review/publish → send wording.
Local report exports are labelled as draft exports that may contain unpublished
edits. The ward shows the saved sort and explains that grouping is display-only
and arrows clear sort levels. Android reuses the sort-sheet description in its
filter dialog and explains grouping versus stored/export order.

Checks: Python 3.8 grammar; disposable team wire/CSV round-trips, absent-patient
resident inclusion, empty supervisor tables, invalid/deleted team-target rejection,
CSV stable-ID remapping and protected import; bulk copying with retained deadlines,
completed-task exclusion and keep/clear assignments; and sort-description probes.
The desktop start-shift handler was exercised with disposable encrypted storage,
confirming target identity/roster retention, all-record copying, assignment clearing
and nonempty-target rejection. An assignments PDF with explicit residents but no
per-patient resident assignments and an empty supervisor table was generated and
visually inspected. Desktop GUI import again reported its read-only user-font
fallback; no interactive desktop execution was performed. Android edits
were inspected at source level only. No Gradle/build, Kotlin compilation, packaging,
migration or test-suite work ran. Updated GUI workflows, Android rendering and
live mixed-device Telegram operation remain unverified.

### Cover, reviewed sending and source freshness

Cross-team cover is allowed using the existing patient resident/supervisor fields.
There is no automatic reassignment of permanent team membership or supervisor.
Desktop resident choices identify the permanent team and explain covering; its
text/full-PDF patient output marks a known cross-team assignment. Android's form
shows the policy and a live cross-team indication. Its text/PDF report context
explains that individual cover may differ from permanent membership. Role/active
physician validation remains unchanged; no new stored clinical flag is required.

Desktop report sending now prepares the same guarded full-shift publication used
by manual review/publish. If data differ from the current remote shift, the user
reviews the entire ward replacement (including deletions, even for a supervisor
report), previews the final text/PDF, then explicitly confirms publication and
delivery. No write occurs on cancellation before final confirmation. The existing
publication ledger, current actor/pointer checks and exact PDF/text checks remain.
After successful publication, sending uses that accepted shift/pointer. Identical
accepted data reuse the remote publication, preserving existing report receipt
identity and UNKNOWN blocking rather than creating a new snapshot for each retry.
Android already publishes the matched reviewed shift before sending; its shared
confirmation now also explains that delivery failure cannot undo publication.

Android successful bundle application records per-shift download time/message ID
in existing settings in the same transaction. Both automatic empty-shift and
explicit whole-shift copy prompts display the source date and that recorded time
only when it matches the source's CSV message ID. Older/local-only copies show an
explicit unknown time; the prompt says no Telegram freshness check occurred now
and tells the user to cancel/download first if needed. Desktop copy confirmation
shows source date/count and the client-zone time its remote bundle was fetched
for this operation, retaining its final pointer recheck. No database migration,
interchange/schema version change or background synchronization was added.

Checks: Python 3.8 grammar; disposable production-handler probes with encrypted
workspace storage/fake transport confirmed cancellation causes no publication,
final confirmation precedes publication/delivery, unchanged data avoid republishing,
and an ambiguous delivery remains UNKNOWN and blocked across a retry. Cross-team
classification, text/PDF content labels and assignment/membership preservation
passed local probes. Android download metadata, scrollable confirmation and
cover-policy wiring were inspected at source level. The desktop GUI import
reported its known read-only font-install fallback. No Android build/compilation,
packaging, migration or test-suite work ran. Graphical workflows, actual Android
rendering and live Telegram remain unverified.

### Explicit report outcomes and movement restrictions

Report screens now expose publication and delivery as separate outcomes for the
last send attempt. Android records publication-in-progress before upload and
confirmed publication before rebuilding/sending. ReportSender wraps failures at
the actual Telegram delivery boundary as uncertain delivery, preserving separate
cancellation handling; render/file/validation failures before delivery remain
not-started outcomes. Supervisor batches retain the confirmed destination count
and distinguish partial success from an uncertain next delivery. Uncertain
publication/delivery removes the shortcut retry action and instructs manual
Telegram review. Successful sending marks delivery confirmed independently of
publication. Attempt-stage displays are session-local; the durable Android delivery
ledger described below protects retries across recreation and restart. No database
or interchange schema/version changes were introduced.

Desktop has the same separate last-attempt display and actionable failure text.
It recognizes confirmed publication even if subsequent local bookkeeping fails,
and confirmed delivery before receipt bookkeeping. Existing durable UNKNOWN
receipts remain authoritative across restarts and retries. Local report export
is separate and does not reset the send attempt display.

Desktop manual movement is disabled while any grouping, filter or nonempty search
is active, with a visible explanation to clear these first. The move handler also
checks the condition, so a stale callback cannot move against hidden neighbours.
Boundary arrows are disabled in the full ungrouped list. Existing stored order,
priority-first behavior and sort-level clearing remain unchanged.

Android synchronization labels now say «تعديلات غير منشورة» and «منشور على تليجرام»;
the timestamp says last synchronization with Telegram, avoiding confusion with
local encrypted backup files. Internal enum/persistence names remain unchanged.

Checks: Python 3.8 grammar and disposable production-handler/source probes passed
for group/filter/search movement rejection, report cancellation, confirmed
publication before delivery, UNKNOWN retry blocking and outcome classification
before requests/after ambiguous requests/after confirmed bookkeeping failures.
Android network-boundary wrappers, stage updates, partial delivery and retry wiring
were inspected at source level; affected import checks passed. GUI import used the
known font fallback. No Gradle/build, Kotlin compilation, packaging, migrations or
test-suite work ran; interactive Android/desktop and live Telegram remain unverified.

### Durable Android deliveries and exact reviewed PDFs

Android stores local report-delivery metadata in its existing settings repository.
A singleton ledger persists UNKNOWN before a Telegram request, then records SENT
and message IDs after acknowledgement. An unresolved shift/destination blocks all
new formats/content for that route; identical confirmed content requires explicit
one-copy permission. Preflight checks run before publication, and the send boundary
rechecks under the ledger mutex. Network/cancellation failures retain UNKNOWN;
receipt-write failures after acknowledgement retain a successful delivery outcome
and leave the durable entry blocking retries. No patient text, credentials or raw
destination IDs are stored in the ledger; route/content identifiers are hashes.
This is a device-local guard, not Telegram idempotency or cross-device coordination.
Android supervisor delivery identity also includes the supervisor ID, so distinct
reviewed subsets can share a Telegram group without being mistaken for duplicates.
Preflight and send use that same identity; an unresolved route still blocks both.

Desktop now checks UNKNOWN report receipts by shift/chat/topic independently of
format and publication pointer, before review/publication and again before the
request. New report receipts append the shift ID to local metadata. Existing
unresolved rows without that label conservatively block their destination until
resolved; their contents are retained. The existing confirmed-report key and
explicit extra-copy confirmation remain. One shared destination selector is used
by preflight and both text/PDF transport paths. No database or interchange schema
or version changes were made.

Desktop manual receipt resolution checks the live project identity/membership and
requires the original sender or a current admin below the UI. Wrong-project,
locked, expired or unauthorized sessions cannot clear the block. Automatic receipt
confirmation still relies on Telegram acknowledgement or matching pinned evidence.

The report screen exposes dated attempts and explicit confirmation after inspecting
Telegram: received, not received, or one additional copy of a confirmed report.
Only the original sender or an active local admin can resolve an attempt, checked
below the UI with expected status. An executing request cannot be resolved; process
recreation releases that in-memory execution guard while preserving UNKNOWN. Any
subsequent send still requires ordinary report review/confirmation.

General and per-supervisor Android PDF previews retain the generated private-cache
file, SHA-256, reviewed report and frozen export options/style. Send requires that
same intact file, matching current clinical content/destinations and options. It
sends those bytes without regeneration. Changed data/options, lost files or viewer
launch failure require another preview; recreation without the in-memory reviewed
artifact also requires a new preview. A supervisor batch checks every selected
preview before publication. Local save/share/export remain separate actions.

Historical assignment PDFs on both clients explicitly say the resident membership
is the current roster, rather than a historical team snapshot; the context repeats
on every page. Patient assignments still come from the selected shift.

Checks: source inspection covered durable-before-request ordering, authenticated
resolution, active-request exclusion, cancellation cleanup, all three delivery
entry points, preview/data/options/hash checks and exact-file send wiring. Desktop
production modules parse with Python 3.8 grammar. Disposable dummy-data PDF probes
verified a one-page current shift and four-page historical overflow with repeated
context; the historical Arabic page was visually inspected. Git whitespace checks
passed. No Gradle/build, Kotlin compilation, packaging, migrations or test-suite
work ran. Android recreation/concurrency/viewer behavior, interactive workflows
and live Telegram delivery remain unverified.

Additional disposable production-handler probes confirmed persistent destination
blocking across format/publication changes, rejection before review/publication,
independent shift/chat/topic availability and preservation of unresolved earlier
rows. The real client authorization path with a fake transport rejected unrelated
clinicians, expired sessions, departed membership, locked workspaces and wrong
projects; sender/admin resolution passed. Existing review/cancellation/delivery
outcome probes also passed. Python 3.8 grammar, affected Kotlin delimiter/import,
local documentation-link and Git whitespace checks passed. GUI imports used the
known read-only font fallback. Android supervisor identity was inspected at source
level; no builds, compilation, migration or test-suite work, or live Telegram ran.

### Workflow status, ward tasks and batch teams

Both ward interfaces expose the selected shift, local publication state, recorded
last download and count of unresolved report deliveries. The next action leads to
delivery review, publication review or manual download. Both clients compare the
selected shift with a locally known snapshot; Android additionally labels its
existing device-wide pending flag separately. Neither display claims to
have checked Telegram now. Unknown download times remain explicit; a recorded time
for an earlier snapshot is labelled accordingly. Desktop records selected-shift
download metadata atomically with the local replacement, without changing shared
formats or versions. Demo status is explicitly in-memory and its network action is
disabled. Existing report review/confirmation remains required.

A ward task list on each platform shows individual unfinished tasks across all
patients, ordered overdue/deadline/priority, with patient-supervisor and
patient-resident filters. Cards retain task ownership/deadline information. Opening
a task resolves the current patient identity and uses the existing patient screen
or editor; the board does not complete tasks or silently save clinical changes.
Android reuses the PatientTasks display comparator. These are display filters and
do not change stored patient/task order or exported content.

The doctor-management screens now offer a batch team editor: supervisors and their
residents appear together, with resident membership choices and explicit review of
all changes before saving/publishing. Android checks live admin authority, the
complete expected registry and valid one-supervisor membership in the mutation
transaction, then writes the changed residents/audit/pending state together.
Desktop reuses Administration review/approval/publication and its live admin,
patient-draft and remote-registry checks. A single registry publication contains
the entire approved batch. Patient assignments, cover and task owners are retained.

Desktop manually confirmed arrival (CHECKED_DO_NOT_REPEAT) now qualifies for an
explicitly approved extra report copy. It still blocks ordinary repetition, and
UNKNOWN remains blocked independently of any extra-copy approval.

Verification: disposable production probes passed task ordering/filtering and
non-mutation, status/download persistence and next actions, multi-resident team
validation/admin checks, one-upload batch publication, stale-draft/registry
rejection and patient preservation. A dummy complete desktop handler sequence
covered download, edit, reviewed publication, report send, retrieval through the
shared Android CSV format, ambiguous delivery resolution and an approved repeat.
Actual desktop demo-window/task-filter/patient-opening/team-selection callbacks
ran with network prohibited; task/team/main Arabic screenshots were inspected.
Demo state remained in memory. ADB was reachable after its sandbox socket
restriction was lifted, but listed no connected devices. Android has source
inspection and delimiter/import checks only. Python 3.8 grammar, documentation links
and Git whitespace checks passed. No Gradle/build, compilation, packaging,
migration or test-suite work ran. Actual Android-to-desktop execution, live Telegram
and Android layout/accessibility remain unverified; dummy transport and wire
round-trips do not establish those runtime behaviors.

### Android synchronization consolidation

Android ward synchronization uses one `WardSynchronization` model for the existing
device-wide pending flag/last synchronization time, selected-shift snapshot comparison,
download metadata and delivery uncertainty. Drawer, dashboard, report entry and
workflow strip share its derived status and freshness wording. A timestamp is a
recorded local event, not a current remote check. Failed downloads
leave publication status derived from persisted metadata. Ledger read failure is
isolated so publication/download metadata still update. Switching shifts resets
selected-shift metadata and ignores late emissions for the previous selection.

One `WardWorkflowAction` decision supplies both the workflow button label and its
destination: delivery history first, review/publication for editable pending data,
otherwise manual download. Removed unused remote field-merge models, dialogs,
subscriptions and resolver methods across ward, doctors and reports. Expected-revision
checks, local stale-edit review and reviewed full-snapshot replacement remain.
This consolidation has source checks only; no Gradle, compilation, device execution,
live Telegram, migration or test-suite work was performed.

### Cross-platform workflow parity

Desktop supervisor PDF sending now supports selecting multiple groups, reviewing
every file and confirming the full destination list. The existing complete-shift
review/publication runs once when needed, followed by exact reviewed bytes and a
separate durable receipt per supervisor. Sending stops after a failed/uncertain
destination or a changed registry/publication; earlier deliveries are retained.
An extra copy still needs explicit approval. Cancellation before final confirmation
does not publish. Ordinary single-destination and local-export flows remain.

Desktop patient forms autosave an unfinished candidate and its expected saved
patient in the encrypted local workspace. Resume, explicit discard and keep-on-close
are separate from saving clinical records. Recovery checks shift and local identity;
a changed saved patient requires comparison and a second explicit Save. Form recovery
is device-local, excluded from portable draft exports and Telegram publication, and
stays in memory in demo mode. No new database table, shared field or version is used.

Desktop combines clinical filters (priority, badges, missing responsibility, urgent,
new admission), task filters (pending, overdue, unassigned), and own-patient/task
visibility. Arabic search matches Android normalization and includes task descriptions.
Filters do not change saved order; manual movement is disabled under any active filter.
Both clients use action identifiers independently of the Arabic button labels.

Android records per-shift known CSV content in existing settings after latest download or
publication, and compares patient content/order, roster and sort criteria off the main
thread. Selected-shift status is unknown, matching known content, or changed; the
device-wide pending flag remains separately visible. Change summaries use the same
selected-shift baseline rather than another shift's last snapshot. This is local
comparison metadata, with no migration or interchange-version change. Restoring a
previous bundle does not replace the known current baseline; missing baselines
remain explicitly unknown.

Desktop announcement administration is visible to admins and checks live authority
again below the UI. It previews text, requires approval of that exact plan, creates
or edits the existing announcement, and updates the existing announcement fields in
the dedicated synchronization index while preserving other pointers. A durable
UNKNOWN receipt precedes sending; uncertain publication blocks another announcement
until sender/admin resolution. Both clients review literal text and use the existing
authenticated text encryption when project encryption is enabled; PDFs retain their
deliberate readable-file exception.

Verification: Python 3.8 grammar, production imports, encrypted SQLite reopening and
form isolation/lock checks passed. Dummy production probes covered Arabic/filter
rules, workflow actions, announcement create/edit/admin/stale/approval checks, one
batch publication, exact reviewed payloads, per-destination receipts, partial failure
and registry-change stopping. Actual desktop demo windows exercised combined filters,
form autosave/resume, stale comparison followed by explicit Save, batch selection and
the announcement editor. Network requests were blocked; no real project data was
loaded. Android has source/delimiter/import checks only; no builds, compilation,
migration, test-suite work or live Telegram operations ran. Windows rendering and
cross-device runtime behavior remain unverified.

### Local clinical attribution, activity and desktop presentation

Desktop clinical Save now stamps newly completed tasks with the current authenticated
local clinician and time, matching Android. The mutation layer accepts a verified
local PIN session or valid Telegram identity, validates task owners, clears reopened
completion metadata and preserves existing completions. Saved completion evidence,
clinical changes and audit records commit together in the encrypted workspace.
Publication preserves that saved evidence or published completion history; it no
longer credits a new completion to the publisher. Arbitrary completed form values
without saved evidence cannot establish completion identity. Delete/restore and
encrypted `.srdraft` recovery preserve the evidence, including reopen/re-complete
cycles. No shared patient field, wire version or SQLite schema changed.

The desktop admin activity center records local patient additions/edits, task
completion/reopening, deletion/restoration, roster/order changes and draft recovery.
Entries include actor/time, patient, task description and changed field names,
without credentials or full before/after clinical narratives. Search, action and
shift filters are available; the view shows the latest 500 matches while retaining
the full encrypted history. Access checks the current local identity/registry below
the UI, including PIN use offline. Unlocking as a non-admin closes the activity
window. This is device-local history, not an audit of other devices; PINs and audit
history stay out of portable draft exports. Publication/delivery receipts retain
their separate existing workflow.

PDF/report preferences now persist in the encrypted project workspace, survive
reopening, and remain in memory in demo mode. Assignment export does not replace
ordinary report choices, and batch PDF sending preserves the preferred ordinary
report format. Desktop cards support inline full details, expand/collapse all and
per-patient view pinning. Pins move rows only within displayed groups, retain stored
numbering and disable manual arrows while active; they never change saved order or
exports. Card state resets across project/shift changes and prunes removed patients.

Verification: production dummy-data probes passed local completion attribution,
preservation under a different publisher, reopening, deletion/restoration, encrypted
backup and preference reopening, admin/stale/expired-session guards and card-state
isolation. Existing fake-transport batch delivery checks still passed. Actual dummy
desktop windows exercised card pinning/expansion, preference controls, local task
completion, admin activity and PIN unlock as a non-admin. The activity dialog also
handles closing during its background read. Network requests were blocked and GUI
data stayed in memory. Python 3.8 grammar and Git whitespace checks passed. No
Gradle, compilation, packaging, migration, test-suite work or live Telegram ran;
Windows rendering and live cross-device behavior remain unverified.

### Patient editor, clock refresh and reviewed announcements

Desktop patient metadata is now stamped at local clinical Save and preserved when
another clinician publishes. Publication checks the saved patient content, ignoring
derived doctor labels, rather than assigning the publisher as editor. Anonymous
local edits remain explicitly unattributed. Carry-forward stamps copied patients at
local creation; revisions still protect publication/recovery. No shared field changed.

Desktop ward displays and the open ward task list refresh every minute, without
network synchronization. Locked/busy ward views defer refresh; the task-list timer
respects the lock and is cancelled on closure. Overdue filters/counts and date-based
status therefore update without requiring another user action.

Android announcements now require preparation, exact-text preview and final
confirmation, with local-session/published-registry admin checks below the UI.
They send literal text with project encryption preserved and preflight wire-size
validation. The existing delivery ledger records UNKNOWN before the request and
keeps it until message/index publication is confirmed; cancellation retains the
guard. Optional private receipt metadata binds the previous announcement reference.
There is no Room migration, shared schema change or version bump.

Both clients let an authorized operator resolve uncertain announcements after
checking Telegram. Received-message resolution takes the actual message ID and
repairs an unchanged missing/older reference while preserving a subsequently changed
announcement. Android updates only an existing recognized index and refuses to
manufacture a replacement pin if it disappears. Ordinary report receipt guards
retain their active-request, sender/admin, duplicate and extra-copy checks.

Verification: production desktop dummy probes passed local/anonymous editor
attribution under another publisher, encrypted announcement round trips, uncertainty
blocking, reference repair/newer-reference preservation and clock refresh/lock
behavior. Existing completion/recovery and batch-delivery probes passed against the
updated save contract. An actual dummy GUI exercised overdue filtering and the
registered task-list timer, including cleanup. Network calls were blocked. Python
3.8 grammar, affected Kotlin delimiters/imports/API call inspection and Git whitespace
checks passed. Android announcement behavior has source checks only; no Gradle,
compilation, packaging, migrations, test-suite work or live Telegram operations ran.

### Shared desktop report delivery and task rules

Single PDF/text reports and multi-supervisor PDFs now use `ReportDelivery` for
draft freshness, destination preflight, one reviewed publication, compatible
receipt identities, explicit extra copies and per-destination UNKNOWN/confirmation
bookkeeping. Their existing previews and final confirmations remain in the UI.
Each transport still rechecks the live publication, registry and authorization;
a later batch failure stops subsequent destinations. Confirmed delivery followed
by failed local receipt saving retains the uncertainty guard and tells the user
not to repeat the send.

`desktop_tasks` supplies structural/deadline validation, pending/overdue/unassigned
counts, stable display ordering, status, unfinished-task rollover and historical
owner resolution. Draft forms, clinical saves, wire/publication validation, ward
filters/task lists and PDF content use those shared rules. Display sorting does
not change saved task order. Draft validation allows completion before its local
save stamp; saved/wire tasks require complete stamps. Authentication, completion
stamping and trusted publication evidence remain in their clinical/protocol layers.
No persistence format, schema or version changed.

Task presentation also uses `desktop_tasks` across patient details/cards, text
reports/publication review, PDFs, editor task rows and the ward task list. Shared
metadata includes priority, current/historical owner names, deadline/shift and
completion identity/time. Overdue text now agrees with PDF and ward status; editor
and ward rows expose priority and shift deadlines without opening the task editor.
PDFs still omit unassigned/routine-priority labels and retain all clinical content.
Unsaved completion is labelled as awaiting Save; formatting never stamps evidence.
Ward task-list refresh/open actions now check the lock and captured project/shift
before resolving a current patient, retaining archived-shift read-only routing.

Verification: focused production-source dummy probes passed single PDF/text
delivery, publication reuse, extra-copy consent/cancellation, stale-preview
rejection, failed receipt saving and retry blocking; batch probes passed exact
reviewed bytes, one publication, individual receipts, partial failure and changed
registry stops. Shared task probes passed deadline boundaries, filters/counts,
ward/PDF ordering, validation, rollover and historical-owner guards without order
mutation. Existing clinical attribution/offline PIN/recovery probes passed. All
network requests were blocked. Python 3.8 grammar and Git whitespace checks passed.
The single-report UI method was exercised with dummy callbacks; rendered desktop
interaction, Windows, live Telegram and Android build/device behavior remain
unverified. No builds, compilation, packaging, test-suite or migration work ran.

Additional task-presentation checks: Python 3.8 grammar passed for five affected
production modules. Dummy production probes passed shared text/PDF formatting,
deadline boundaries, priority/owner omission, historical names, pending completion
and non-mutation. Production ward-list callbacks with dummy widgets passed current/
archived/deleted-patient routing, lock/project/shift rejection and timer cleanup.
Git whitespace checks passed. No rendered GUI, live Telegram, Android build/device
or cross-device execution was performed for this consolidation.

### Download, editor, appearance and portable backup alignment

Desktop now matches Android's publish-before-download rule. Its workspace checks
saved clinical/order/roster content against the known shift before downloaded data
can replace it, including a second check at the write boundary. An untouched empty
shift can still download. Explicit recovery remains a separately reviewed action.

Desktop patient editing uses free text with optional per-field tools for treatment,
follow-up and labs, using the same draft and preserving blank lines/separators.
Detailed items can be edited, reordered or removed, with separators and a lab-date
picker using Android's existing date marker. Pending new text is included
when saving or switching a field's tools. Save and next captures the filtered/grouped/pinned
display sequence and advances only after successful save and draft cleanup.
Failed validation/stale review stays in the current editor; the next patient and
shift are resolved again before opening.

Desktop appearance controls offer light/dark/system mode, the eight Android accent
names/primary colors and proportional text/control scaling. Preferences remain in
the existing encrypted workspace, or memory in demo, and reapply on connection.
They are independent of PDF preferences and explicit clinical severity colors.
The generated theme stays in memory; platform widget rendering remains distinct.

Desktop can export/import Android's existing password-protected `.rpb` single-shift
backup: `RUSHDBK1`, 16-byte salt, PBKDF2-HMAC-SHA256 at 210000 iterations, 256-bit
AES-GCM with a 12-byte nonce, and current single-shift CSV7 plaintext. There is no
new backup format, interchange/database version or migration. Desktop `.srdraft`
remains device-workspace recovery and is labelled separately. Portable backups
include clinical data, task attribution, stored order and roster, without connection
credentials, local access settings or delivery receipts.

Since `.rpb` has no project identifier, desktop restoration requires the opened
project's matching selected shift ID/date and validates doctor references; users
must select that shift and update its registry first. Restore decrypts/validates
before review and final confirmation, rechecks the expected draft, preserves a
desktop recovery copy and retains the current delivery ledger. Imported completed
tasks become saved recovery evidence, preserving their actual clinician/time in
subsequent publication. Other shifts' evidence and local activity remain intact.
Restoration changes local data and still requires reviewed publication.

Verification: production dummy probes passed portable encryption/CSV round trips,
wrong-password/tamper rejection, task completion preservation through publication,
receipt retention, shift/stale/lock and pending-download guards. An actual dummy
desktop GUI passed general/detail draft and pending-text preservation, item order,
lab-date insertion, Save and next (including stale review before a second Save),
captured display sequence, palette/size switching and in-memory
preferences. Existing clinical/offline-PIN and single/batch fake-delivery probes
passed. Network calls were blocked. Python 3.8 grammar and Git whitespace checks
passed. Android `.rpb` compatibility was checked against production format/crypto
source; an actual Android-to-desktop file exchange, Windows rendering and live
Telegram remain unverified. No builds, compilation, packaging, migration or
test-suite work ran.

### Patient rounds and reviewed recovery publication

Desktop read-only patient details now support previous/next over the captured
filtered/grouped/pinned view sequence, retaining patient scroll positions and
resolving current records by ID. Deleted neighbors are skipped; project/shift
changes stop navigation. Editing is an explicit separate action and historical
shifts remain read-only. The reusable text pane now updates its logical clipboard
source without accumulating callbacks from previously displayed patients.

An admin can open this patient's local activity directly from details. Filtering
uses both patient and shift IDs below the dialog, with authorization rechecked on
every read. The activity dialog remains admin-only, and mapping the details window
after unlock refreshes activity-button visibility for the new role. The source
records remain device-local; this view does not claim another device's history.

Android Settings ordinary publication and post-backup publication now share a
full-snapshot review and explicit confirmation. Review lists added/changed/removed
patients and the proposed complete clinical report. Its private in-memory token
binds the exact local CSV, patient/doctor source pointers and published clinician
identity. Preparation downloads the published doctor registry and blocks pending
local registry changes; it does not publish those changes implicitly. Confirmation
rechecks the actor, local rows/roster/order and source pointers before upload and
before index publication. Retry prepares another review; it does not automatically
repeat a send. Clinical publication remains distinct from report delivery.

The separate force-upload API, flag and Settings retry/dialog paths were removed.
All CSV publication now retains the remote-change check; historical journal
representation and ordinary recovery rotation remain unchanged. No database,
interchange version or migration changed. Android runtime/compilation verification
for this change remains outstanding under the repository's build restriction.

Verification: production dummy probes passed captured rounds, deleted-neighbor
skipping, project/shift guards, patient/shift activity filtering and non-admin
rejection. An actual dummy Linux GUI passed previous/next, captured filter order,
scoped admin activity, current-patient copying, scroll retention and activity-button
concealment on non-admin remapping, without clinical mutations. Existing clinical
completion/recovery probes passed. Python 3.8 grammar, affected Kotlin delimiter/
new-import/API-reference inspection and Git whitespace checks passed. Network
requests were blocked. Android publication has source verification only; no build,
compilation, packaging, test-suite/migration work or live Telegram ran.

### Desktop handover and report setup alignment

Desktop now exposes a read-only «لوحة تسليم المناوبة» alongside the ward card
controls. Its actionable counts cover all patients, own patients/pending tasks,
urgent/priority patients, badges, missing clinician assignments, today's admissions
and pending/overdue/unassigned tasks. The existing ward filter rules supply patient
counts, while shared task rules count individual tasks; overdue remains a subset
of pending. Selecting a count clears search and replaces clinical/task/ownership
filters together, without changing stored order, grouping or pins. The summary
refreshes once per minute while unlocked and respects project/shift boundaries.
Ward ownership filtering and dashboard counts now share the verified local identity,
including offline PIN sessions, instead of preferring an expired Telegram actor.

Report setup starts with the shift roster and an actionable picker even when no
roster exists. Archived-shift selection remains read-only. Saving a roster rechecks
the expected draft and current doctor availability. Single and batch report paths
reload the local source after report options, retaining the roster chosen there.
Completeness notes mirror Android's four advisory fields: supervisor, resident,
initial diagnosis and treatment. They appear in setup, full publication review and
destination-specific text/PDF confirmations; they do not alter clinical report
payloads or make optional task owners mandatory. Existing review/confirmation and
delivery guards remain in place. No format, schema or version changed.

Verification: Python 3.8 grammar checks passed for the four affected production
modules. Production dummy-data probes covered all dashboard metrics/filter actions,
deadline boundaries, completed/optional tasks, non-mutation and Arabic completeness
notes. A production report-options callback probe with dummy widgets verified the
first-position picker, empty-roster selection and saved preferences/roster without
publication. Git whitespace checks passed. These are source and headless callback
checks; rendered Linux/Windows interaction, live Telegram and Android-to-desktop
runtime exchanges remain unverified. No builds, compilation, packaging, migrations
or test-suite work ran.

### Desktop stale saves, draft identity and rollover protection

Every desktop clinical snapshot save now carries the fingerprint captured before
that operation edits its copy. Patient, roster, manual-order and sort actions pass
it through the UI helper to the existing clinical mutation boundary. Both layers
reject a changed local snapshot; sorting no longer obtains a fresh fingerprint
for an old dialog copy. Rejected sort dialogs stay open and newer data is retained.

Unfinished patient-form saving and recovery use the same verified local identity
as clinical attribution: a current Telegram identity or valid local PIN session.
Cached actor IDs no longer satisfy draft ownership after expiry. Autosave cannot
overwrite another clinician's owned form or turn an expired owner's form anonymous.
Explicit discard remains available, and newly anonymous forms remain supported.

Starting a new shift shares the existing saved-content comparison used by download
protection. Pending clinical/order/roster changes require explicit acknowledgment
and a separate new encrypted `.srdraft` archive before replacing the local view.
The workspace writes and authenticates that archive, checking its exact draft,
then rechecks the original local fingerprint and current shift date before writing.
Existing files and the automatically rotated `.previous.srdraft` destination are
rejected for this archive, so later automatic backups cannot replace it. Cancellation,
stale state or archive failure leaves the current data intact. The new shift still
copies the reviewed Telegram source; archived unpublished changes are not silently
merged into it. Ordinary downloads retain their publish-before-replacement rule.
No schema, format or version changed.

Verification: Python 3.8 grammar and production save-call inspection passed.
Production dummy probes covered both UI/worker stale-save rejection, active/expired/
PIN/other/anonymous/locked draft identities and actual encrypted SQLite/recovery
files. Rollover probes covered clean/pending shifts, cancellation, stale state,
existing/automatic/corrupt archive rejection and shift-boundary rejection. The
separate saved archive remained readable after automatic-backup replacement.
Production sort-dialog callbacks with dummy widgets verified current-save success
and stale rejection without closing the dialog or overwriting newer clinical data.
Arabic presentation dependencies were shimmed for headless GUI-module import;
no rendered GUI was exercised. Git whitespace checks passed. No builds, compilation,
packaging, test-suite/migration work or live Telegram operations ran. Windows,
rendered Linux and cross-device runtime behavior remain unverified.

### Selected workbook workflow and report features

Both clients can optionally insert a new patient at a one-based position in the
complete saved list. Explicit insertion clears the automatic sort specification,
keeps priority patients first, and rejects a position outside that patient's
priority group. Ordinary addition retains its existing behavior. Android checks
the captured shift revision and patient ID/revision/order sequence in its mutation
transaction, then stamps changed stored positions with new revisions. Desktop
checks the captured draft fingerprint and uses its existing clinical-save boundary.
Filtering, grouping and view pins do not redefine insertion positions. The position
is an entry-session choice and must be selected again when reopening a form.

The additional Excel sort action uses female first, diagnosis ascending, longest
admission first, then name ascending. This preserves the workbook's date-column
meaning when expressed as duration. Existing default/manual sort actions and
priority-first ordering remain available; no sort field or wire format changed.

Download undo is an explicit local recovery action. Android records before/after
CSV snapshots for all affected shifts inside the download-application transaction,
using existing encrypted Room settings. Undo checks exact saved after-content
before restoring any rows, increases restored row/shift revisions, preserves
delivery/publication evidence and known publication baselines, and marks recovery
pending for reviewed publication. The record survives restart and is consumed only
after success. Desktop stores its prior view and downloaded fingerprint in its
existing encrypted workspace, preserves current known baselines and receipts, and
backs up the downloaded view before restoring. It also survives reopening. Both
paths reject later saved edits/publication and require confirmation; undo does not
contact Telegram or replace credentials/doctor registries. Restored content still
follows ordinary reviewed publication and download protection rules.

Classic PDFs now detect contiguous supervisor groups by doctor ID, alternating
each group's palette color with cream; interleaved groups use ordinary zebra rows.
Row coloring never changes saved patient order. The former separate «نمط Excel»
PDF preset is now merged into Classic, sharing its compact typography and existing
palette/dark controls. Android keeps ordinary rows together and continues oversized
rows at the fixed 9-point body size; desktop retains its existing table continuation.
Both retain full clinical/task content, report review and demo page labels.
PDF roster headings adapt to one doctor, one female doctor, all-female rosters or
mixed/male rosters. Font metrics and pagination remain platform-specific.

Desktop admission entry accepts ISO dates and day/month/four-digit-year input
using slashes or backslashes, including Arabic digits, normalizing only the saved
value to ISO. Impossible dates and future admissions retain ordinary validation;
two-digit years are not guessed. General/supervisor text reports now include the
selected report's companion total as well as patient count. PDFs already counted
companions. No database/interchange version, schema, migration or credential format
changed.

Verification: production desktop dummy probes passed insertion/priority/stored
order, date normalization/rejection, preset directions, destination-filtered
companion totals, roster grammar and light/dark grouped/interleaved striping.
Actual encrypted SQLite reopening verified undo persistence, receipt/current-
baseline retention, one-use behavior and stale/locked rejection. Headless production
callbacks verified preset selection, preference persistence and stale-sort rejection.
New light/dark Excel-style PDF samples and Classic comparison rendered successfully;
every sample page contains DEMO, and first pages were visually inspected. A long-row
Excel PDF retained all 100 unique content markers across continuation pages.
Python 3.8 grammar, affected Kotlin delimiters/import/enum/API wiring and Git
whitespace checks passed. The attempted native desktop GUI check could not connect
to display `:0`; no rendered application GUI interaction was verified. Android
has source checks only. No builds, compilation, packaging, migration, test-suite
work or live Telegram operations ran; Android/device, Windows and cross-device
runtime behavior remain unverified.

### Ward layout and view-control consolidation

Desktop now groups freshness/next action and ward totals together, followed by a
two-column patient/task/dashboard/roster action area. Search and reset stay visible;
clinical, owner and task filters, grouping, saved sorting and bulk expansion share
one collapsible, scrollable view panel. Active filters and grouping remain visible
when it is closed. The current doctor, login and lock remain accessible above
collapsible project settings; files/recovery are also collapsible. Data/publication
and reports retain their separate labelled groups and existing confirmation paths.
The patient list shows visible/total counts and an explicit empty-result message.

Desktop patient cards share `desktop_patient_card.py`: Edit, View and inline
expansion stay visible; pinning, deletion and manual movement sit under More.
Movement uses labelled 48px controls, retains filter/pin/group and boundary guards,
and explains when those view settings block it. Collapsed summaries omit zero task
counts while retaining positive pending/overdue counts and a visible pinned label.
Full task/clinical content remains in expanded details and the read-only view.

Android extracts the list header into `WardPatientListControls`, with four common
presets, removable active filters and one options entry. The scrollable options
dialog groups clinical/task/ownership filters separately from card density,
grouping and saved sorting; saved-sort controls are hidden for read-only shifts.
Filter labels can wrap while retaining 48dp targets. The workflow strip avoids
repeating the toolbar date and omits zero uncertain-delivery counts, while still
showing unavailable-ledger and uncertain-delivery notices.
The drawer shares the same view-options entry instead of separate sort/group rows;
its selected state includes task and ownership filters. Its report action uses
«إرسال تقرير» and retains report preview. The My patients drawer action uses the
same preset reset path as the patient list and dashboard.

All three Android reset entry points now clear ownership as well as search,
clinical and task filters. Dashboard/preset actions also clear old search text,
so their results agree with the chosen category. Desktop reset clears all filter
dimensions while retaining view grouping/pins and saved order. None of these view
changes alters clinical snapshots, report content or publication safeguards.

Verification: Python 3.8 grammar and production imports passed; headless production
callbacks verified the four primary desktop actions, disclosure state retention,
active-filter summaries, complete filter reset and unchanged dummy clinical data.
Patient-card callback probes also passed primary/secondary action routing, hidden
secondary controls, minimum control sizes, movement bounds and edit-candidate copy
isolation; drawer entry/preset/active-filter source wiring checks passed.
Affected Kotlin delimiter and source-wiring checks passed, including the new
component and shared reset/preset paths. Git whitespace checks passed. Desktop
imports reported the existing read-only user-font fallback. No usable graphical
display was available, so actual layout, font scaling and accessibility rendering
remain unverified. Android has source checks only; no builds, compilation,
packaging, test-suite work or live Telegram operations ran.

### Patient-entry and report-option presentation

Both clients place optional positioned insertion after demographic entry and under
an expandable heading. A selected position stays identified in that heading;
Android also offers an explicit clear-position action. Collapsing the section
retains its value, and the existing priority/order/stale-snapshot checks still
apply. Desktop limits the input to numeric characters. Ordinary append remains
the default; insertion intent is still separate from the recovered patient draft.

Desktop report setup now hides PDF-only controls for Text. Card layouts hide
paper/orientation choices and explain their fixed A5 portrait output. Other PDF
layouts restore those controls without losing selected values. Advanced PDF
options use the shared disclosure control, and each layout has its own short
description. The Android PDF style picker has matching explanatory text and
48dp minimum chip targets. Roster selection remains the first report-setup step.

Fixed a report-option aliasing issue discovered during these callback checks:
retaining the user's Text preference during a forced-PDF action previously changed
the returned output format as well. Preferences are now copied separately, so the
action returns PDF while the ordinary saved format remains Text.

Verification: headless production report callbacks passed text/PDF visibility,
card paper controls, retained paper/orientation/color selections, saved preferences,
forced-PDF behavior and names-only distribution defaults, with unchanged dummy
clinical snapshots. Python 3.8 grammar and affected Kotlin delimiter/source checks
passed, as did Git whitespace checks. No rendered GUI, Android build, compilation,
packaging, test-suite work or live Telegram operations ran; actual layout and
accessibility behavior remain unverified. Desktop imports retain the existing
read-only user-font fallback.

### Compact Classic header, page-fit card index and RTL assignment layout

Classic PDFs on both platforms now separate the roster title (11-point) from a
compact metadata line (8.5-point) beneath it. All roster names wrap without a
fixed limit; header pagination reserves the metadata height. The merged table
style uses this same compact hierarchy. Cards enforce
A5 portrait inside both renderers, regardless of selected table paper/orientation.
Card-name and footer Index links return to the containing index page using the
standard PDF `/Fit` destination; they no longer position a patient row at the top
of the viewport. Android's internal-link writer supports an explicit page-fit
destination while keeping existing positional links for forward patient navigation.
PDF viewers may override fit/zoom behavior; device-reader interaction is unverified.

Assignment PDFs now place the first team at the right of each row, with continuous
numbering down each team and then right to left across the row. Each patient row
has a narrow number column on the right, using Arabic digits, and a name column
to its left. Adjacent supervisor tables have a 3-point (about 1 mm) vertical gutter;
the fixed table widths and centered three-column grid are preserved on both clients.
Android name drawing reserves that number-column width; full-width
supervisor/resident headers retain their span. Overflow and empty teams remain
supported. Both platforms' report descriptions document the RTL arrangement.
Assignment has a distinct 12-point document title.
Saved-shift notices are shortened while still explaining that teams reflect the
current registry. Cards use an explicit patient-index title, compact metadata,
wrapped roster names and a shorter navigation hint. Desktop paragraph reports
likewise separate the document title from metadata. The generic team-cover
explanation has been removed from Android PDF context; clinical patient/team/task
content remains intact.

Hospital and ward names are omitted from all PDF headers, index context and
footers, including Classic, cards, paragraphs and assignment exports.
Assignment output also omits the shift date; its generation timestamp and the
saved-shift/current-team warning remain. Ordinary clinical PDFs retain shift dates.
Unused ward-name entry controls were removed from desktop PDF setup and Android
report settings. Existing stored configuration is preserved; no schema changes
are involved. Text reports and the project's identity elsewhere remain unchanged.

Verification: regenerated ten desktop sample PDFs from fabricated beverage/tree
data; every page retains DEMO. Visually inspected Classic and assignment first
pages. Card samples were A5 and contained page-fit return annotations. A separate
60-patient export produced three index pages; all page-fit destinations referenced
index pages, and A3-landscape input still yielded A5 portrait. Python 3.8 grammar,
affected Kotlin delimiters/source wiring and Git whitespace checks passed. No
Android builds, compilation, test-suite work or live operations ran. Viewer click
behavior and Android rendering remain unverified. These verification samples were subsequently removed at the owner’s request.
The later name-removal changes were checked with distinctive hospital/ward marker
values across all five desktop PDF layouts and an independent old shift date for
assignment output. Extracted PDF text contains none of those markers, and assignment
output omits the old date. Eleven regenerated samples retained DEMO on every page; these files have since
been removed. Report
option callbacks, Python grammar, Kotlin source and Git whitespace checks passed;
Android remains source-checked only.
The subsequent header hierarchy was checked with desktop samples (since removed):
Classic, Cards and Assignment first pages
were visually inspected, and all sample pages retained DEMO. Card sizes and
page-fit links, Python grammar, affected Kotlin source and Git whitespace were
checked; no Android build or viewer interaction was performed.

The creation timestamp is now omitted from every PDF header/index and rendered
once below the final content, separated by 30 points (about 1 cm). Desktop flow-based
reports reserve a small area above the ordinary footer and use a zero-height end
marker to draw the timestamp there after the last content; assignment grids reserve
the same space beneath the final grid row. Android Classic, Cards and
Assignment similarly reserve timestamp space during pagination, then draw it after
the final summary, patient content or grid respectively. Card navigation and page
footers remain below it. Reserving space during layout avoids timestamp-only pages.

Verification: eleven desktop samples (since removed at the owner’s request)
were extracted with page/line coordinates; each creation label occurred exactly
once on the last page, below the final report content. Python grammar, Kotlin
source/delimiter and Git whitespace checks passed. Demo page labels, A5 cards and
index return links were retained. Android rendering/builds remain unverified.
The later increase to 30-point spacing was checked through Python grammar and
focused source inspection, including matching pagination reservations; Git
whitespace checks passed. Updated rendering and Android builds were not performed.

### Unified compact table PDF and sample cleanup

Removed the separate Workbook PDF choice and rendering flag from Android and
desktop selectors/renderers. Classic uses one typography set: 11-point roster,
10-point totals, 9.5-point column headings and 9-point body, with existing palette,
dark mode and adaptive supervisor/zebra striping. Android now shares one table-row
pagination path: ordinary rows stay together; oversized rows continue with repeated
column headings and fixed readable body text instead of shrinking. Cards and the
separate assignment overview retain their current behavior. The Excel patient-sort
preset remains independent of PDF appearance.

An existing desktop `workbook` report preference is interpreted as `table`; Android
already falls back to Classic for removed style names. No stored data, schema or
version changes are required. Deleted 68 generated sample PDFs, their seven sample
folders and five ZIP archives from `reports/`. Historical verification evidence is
retained above, but those generated artifacts are no longer available.

Verification: Python 3.8 grammar, desktop option normalization and headless report
callbacks passed, including retained choices, forced-PDF behavior and assignment
defaults. Affected Kotlin delimiter/source checks and Git whitespace checks passed.
At cleanup completion the reports folder was empty. No replacement samples,
Android builds, compilation, test-suite work or live operations ran at that step.

At the owner's subsequent request, generated ten current-desktop samples in
`reports/pdf-samples-2026-10-19/`: light/dark Classic A4 portrait and landscape,
linked A5 cards, A4 paragraphs and A4 names-only team overviews. Fabricated beverage
patients/tree doctors include pending, overdue and completed tasks, badges and an
unassigned patient. PDF extraction confirmed DEMO on all 42 pages; Classic
landscape/light and team-overview/dark first pages were visually inspected.
The folder includes a sample index. No real settings, clinical storage, Telegram,
Android builds, compilation, packaging or test-suite work was used. Android output
and interactive viewer behavior remain unverified.

### Modern PDF presentation

Android and desktop PDF exporters now use bundled Tajawal Regular/Bold, with the
upstream SIL Open Font License in each font directory. Font files came from
[Google Fonts](https://github.com/google/fonts/tree/main/ofl/tajawal). Desktop
retains explicit `PDF_FONT` overrides and uses base Unicode for isolated Arabic
letters so Tajawal's glyph mapping renders correctly. Android loads the faces
during IO export. The faces were subsequently moved unchanged to Android font
resources and reused by shared app typography, as documented under
[Android review corrections](#android-review-corrections); the bundled license
remains under `assets/fonts/OFL.txt`.

Table grids default to 1.5-point horizontal rules (`#94A3B8` in light mode), with
bold centered white header text and neutral summary bands. Dark appearance keeps
contrasting text and subdued separators. Assignment overviews center each band
of equal-width columns, use a larger centered bold title, tinted team subheaders,
white/light-gray alternating rows and blank fillers to align column bottoms.
Continuous RTL team numbering and overflow headers remain.

A5 portrait cards now have equal 15 mm margins, an inset thin frame, admission
details on the left and patient identity on the right, followed by section
1.5-point dividers. The page frame stays thin. Long content continues at readable sizes. Every card/index page has
the generation date/time, ShiftReport and page X of Y in its footer; this replaces
the card report's previous single end timestamp. Both exporters use a pagination
pass before writing the final card PDF so page totals preserve index/previous/
next destinations and index-page-fit returns.

Regenerated the ten demo samples in `reports/pdf-samples-2026-10-19/` using only
fabricated beverage/tree records. PDF extraction confirmed DEMO on all 42 pages.
Visually inspected light Classic, light/dark assignment and first/continuation
card pages, including neutral summary/footer areas. Production-renderer probes
retained 100 unique clinical markers in long table/card/paragraph exports and all
200 names in an overflowing assignment overview. Card PDF destinations include
six distinct patient targets and page-fit returns to the index; paper is A5.
Sample Arabic glyph coverage, identical bundled font/license copies, Python 3.8
grammar and Git whitespace checks passed. Android was inspected at source level
only: no Gradle, builds, compilation, packaging, test-suite work or live Telegram
operations ran. Android rendering and actual viewer clicks remain unverified.

The requested thicker separators use 1.5 points across tables, assignments and
card sections on both platforms, preserving the thin outer card frame. The ten
desktop samples were regenerated; all 42 pages retain DEMO. The updated assignment
page was visually inspected, and Python grammar/Git whitespace checks passed.
Android remains source-checked only; no build or test-suite work ran.

### PDF appearance preferences

Android Settings → Report → Advanced PDF options now includes independent
separator thickness (0.75/1.5/2.25 points), contrast (light/medium/strong) and row
shading (automatic supervisor groups, neutral zebra or none). Balanced, Clear
separators and Print presets adjust those choices together; Print also selects
light appearance. A separator preview reflects the selected weight/contrast.
Shading controls are hidden for cards while retaining their saved choice. The
ordinary color, paper, orientation and report-layout choices remain available.
Horizontal rules now default to darker slate gray instead of the earlier pale
gray; the thin outer card frame retains its own color/weight.

Android stores the three choices in existing local key/value settings and loads
them into the reviewed PDF options for general, supervisor and assignment exports.
The demo uses the same controls in memory. Desktop adds «إعدادات PDF» under
project/settings and shares its appearance controls with report-review dialogs.
Preferences use the existing encrypted project workspace; Cancel does not save.
Opening PDF settings or an assignment export preserves the ordinary Text/PDF
preference, and assignment choices preserve the full-report paper/layout settings.
Desktop paragraph reports also use the selected horizontal separators between
patients. No clinical fields, schema/version or publication rules changed.

Regenerated 12 demo PDFs (46 pages), including Classic A4 portrait Clear/Print
comparisons; all pages retain DEMO. The darker Classic portrait sample and both
preset first pages were visually inspected. Production callback probes with
in-memory widget substitutes passed preset selection, preview callbacks, settings/
assignment save and Cancel, preference preservation, validation and shading.
Renderer probes confirmed the Clear preset's 2.25-point stroke and selected gray
in table/card/paragraph/assignment PDF drawing operators, retained 100 long
paragraph markers and round-tripped the new preferences through the real demo
workspace in memory.
Python 3.8 grammar, Kotlin delimiter/source wiring and Git whitespace checks
passed. The desktop display was unavailable, so actual dialog rendering/interaction
is unverified; Android rendering/compilation and live Telegram remain unverified.
No builds, packaging, migration or test-suite work ran.

### Ward interface and desktop workflow organization

Android ward controls now place search before the compact patient-count/options
row, use «خيارات العرض», retain quick filters and summarize active filters once, and
separate temporary filtering/grouping from saved sorting in the options dialog.
Patient cards keep the name prominent, show quieter team metadata, use a neutral
number surface and place pending/overdue task badges consistently before diagnosis.
Clinical severity colors, individual/bulk expansion and full saved content remain.

Report preview keeps the actionable shift roster first, compacts scope/format/
destination/count information, and collapses change details and PDF customization.
Customization reuses existing PDF preferences and appearance controls; saving is
excluded from concurrent report operations, clears reviewed PDF artifacts, and
retains the existing exact-file/options checks before sending. Report setup shows
roster first, followed by counts and scope/destination/format. The general report's
PDF layout stays directly available; paper/orientation and visual tuning are under
Advanced. Supervisor-group selection is exposed in setup and retains per-target
PDF preview and final confirmation.

Android preferences, Data Management, Project Management and Diagnostics now have
separate navigation routes using shared settings content and ViewModel operations.
Preferences contains appearance/font, patient cards, rounds/shifts, reports and app
lock. Project Management groups same-project reconnect with live-admin join export
and supervisor destinations; non-admin users retain file reconnect. Diagnostics
keeps storage/device details collapsed until opened. The drawer keeps patients,
report and data access direct, with preferences/project/About under a secondary
submenu. Excel import is accessible inside Data Management and remains admin-only.
Section definitions drive both visible categories and their shortcuts.

View Options contains live search/filters, inline grouping choices, card expansion,
two-column layout and density, plus the saved-sort editor in the same scrollable
surface. It distinguishes temporary filtering/grouping from persisted ordering.
Sorting retains expected revision, stale-edit review and export ordering; read-only
shifts hide sort mutation controls. The standalone demo sort host reuses the editor
with memory-only callbacks. Bulk card expansion clears per-card overrides immediately.
The patient editor exposes save-and-next beside rounds position, preserving the
shared draft, validation and expected-revision save path.

Collapsed settings headers place current values alongside their titles, wrapping on
narrow screens; appearance includes accent and font, and text reports omit irrelevant
PDF style. Appearance controls show a noninteractive sample rendered by the real
patient-card component: beverage patient/tree doctors, memory only, no repository or
sync calls. It sits beside controls when space/font size permit and below them on
narrow screens; card-layout settings also show the sample. Font scaling follows the
same app-wide density provider as the ward.

Patient forms keep the patient name and explicit current-section label above their
existing clinical/lab/task/personal shortcuts and fixed Save/Cancel footer. Patient
add/update success now reports «تم الحفظ محلياً» only after the guarded mutation
succeeds. Publication status remains visible alongside unresolved-delivery warnings.
View Options has four sections and restores its last selected section; filters and
collapsed group keys are saveable. Existing saved grid position and per-card
expansion are retained, and loading no longer prunes restored card state against an
empty initial patient list. Return-to-edited-patient behavior and conflict review remain.

Source checks confirmed retained Settings ViewModel callbacks, navigation/role
bindings, report confirmation paths and sort conflict-review callbacks. Refinement
checks also covered preview isolation, save-success placement and saveable UI state. The later
owner-run build passed resource tasks and reached Kotlin compilation, reporting
missing Material API opt-in at the appearance/font FilterChips. The appearance
section now opts into `ExperimentalMaterial3Api`; source/whitespace checks passed,
but no subsequent build has been run by the agent. Kotlin
delimiter/import and Git whitespace checks passed. These checks do not establish
Android compilation, rendered accessibility, device behavior or live Telegram
behavior; no builds or test-suite work ran for this reorganization.

Desktop uses a narrower right sidebar with a single data-management entry and
report actions. Retrieve, review/publish, undo, drafts, backup/restore, files and
operation receipts share a reusable data window. Device/workflow preferences now
contain appearance, PDF defaults, PIN/lock and timezone controls, with shortcuts to
Data Management, Project Management and collapsed Diagnostics. The separate
Project Management window groups reconnect, protected join export, doctor/team/
report destinations and project announcements. Diagnostics only displays OS/Python,
connection mode and local row counts; it does not print credentials. Existing
lock-screen hiding covers these child windows and live admin gating remains.
Search, Add and display options share a toolbar, with compact ward actions and
summary information below it. Cards emphasize patient names, quieten metadata and
use secondary detail/expansion buttons plus an Arabic-aware «المزيد» action menu.
Manual-movement availability updates with filtering without rebuilding the card.
The ordinary report dialog now contains scope/destination selection alongside
format and collapsed paper/appearance settings. Existing publication review,
preview, file identity checks and final delivery confirmation remain in place.

The desktop list reuses unchanged visible cards, retaining expansion by patient ID,
restoring the pre-filter scroll position when filters clear and discarding widgets
when project/shift scope changes. Changed content, doctor data and the periodic
minute refresh still rebuild affected cards so task/deadline summaries stay current.
The unselected desktop split-pane proposal was not implemented.

Desktop View Options uses retained sections for filtering, grouping, cards and
stored sorting. The sort editor now embeds in that panel, preserving the original
expected-content check, priority-first ordering and workbook defaults. Embedded
save callbacks pass through the busy/lock guard; stale saves leave the editor open.
Card spacing is a local Normal/Compact preference in the existing encrypted
appearance dictionary (memory only in demo), included in card reuse signatures.

Appearance offers a live dummy-card preview beside controls on wide windows and
below them on narrow windows. It reuses the real card renderer without action
buttons and supplies beverage/tree dummy identities. Mode/accent/font/spacing
changes update only the preview until Apply and Save; opening/canceling performs
no persistence or clinical mutation. Clinical severity and PDF preferences remain
separate. Patient forms use the same RTL section control with labelled 48px targets,
per-field optional item tools, a fixed patient/current-section heading and existing
Save/Cancel controls. Form drafts, lab access and conflict handling are preserved.

Patient saves briefly report «تم الحفظ محلياً» (five-second routine feedback), while
publication state remains in the ward workflow area. Main-list scroll is explicitly
restored after details when scope still matches; per-patient detail scroll is also
retained within the project/shift. Dialog dimensions, expanded cards and selected
View Options section persist during the running desktop session. Window/view state
is not saved across application restarts, and no clinical/interchange version changes
were made.

Disposable production-source probes with widget substitutes passed preview creation
with zero writes, dummy-card action exclusion, retained/bounded modal dimensions,
embedded sort save/stale rejection/busy routing, section controls and View Options
selection retention. Source parsing with Python 3.8 grammar, callback comparisons
and Git whitespace checks passed. No real display is available here; rendered
layout, keyboard/accessibility, Windows behavior and live Telegram remain unverified.
No builds, packaging, migrations or test-suite work ran for this desktop alignment.


Further consolidation groups Android retrieve/review/publish, CSV, confirmed
download undo and encrypted backup/restore on the «إدارة البيانات» screen, directly
reachable from the drawer and its status header. The separate backup category and
duplicate restored-data publication button are removed; publication still requires
review. Ward lifecycle, the confirmed previous-publication fetch/snapshot picker
and recycle-bin shortcuts remain available. Settings undo delegates to the existing
stale-state-protected service and keeps cancellation distinct from failure.

Common desktop patient/task, report, roster, sorting, doctor/team, onboarding,
appearance, review and full-details dialogs share a fixed footer: primary action
on the right, quieter Cancel/Close on the left, secondary actions above, and long
primary labels spanning a full row. Callbacks, validation, explicit review and
minimum 48px controls are preserved. Android retains its shared dialog conventions.

Expanded desktop cards now render the complete clinical fields, doctor assignments,
badges and tasks without repeating the personal record; «الملف الكامل» opens the
complete read-only record and rounds controls. Full report/text exports retain
their original complete representation. Android removes the extra overview tab,
opens the expanded clinical record directly, avoids repeating the patient-name
header, and links list-card expansion explicitly to the full record. Full clinical
content, task/warning/history tabs and stale-edit guards remain accessible.

Android delivery uncertainty/log failures use one persistent actionable ward notice.
Desktop unresolved local operation receipts likewise display an actionable notice,
including uncertain publication/registry operations; the notice clears when receipts
are resolved. Routine patient-save and single-report success use a short-lived
status message. Existing conflict review and uncertain-delivery restrictions remain.

The consolidation was checked with disposable source probes and fabricated records:
shared footer callback/position/long-label handling, full-details/data-window
construction, clinical field/task retention, report scope/preferences, patient-widget
reuse, filter/reset scroll and unresolved-operation/cleared-receipt behavior passed.
Python 3.8 grammar, affected Kotlin delimiters/navigation/action bindings and Git
whitespace checks passed. The desktop display remains unavailable; rendered layout,
keyboard/accessibility, Android builds/device behavior and live Telegram are still
unverified. No builds, packaging, migrations or test-suite work ran.

Verification: disposable production-source probes with fabricated beverage/tree
records and widget substitutes passed main-window/sidebar/settings construction,
unchanged-card reuse, filter/reset scroll, expansion, empty results, shift isolation,
report scope/destination/submission and preference separation. Existing disposable
PDF-settings probes passed preset callbacks and settings/assignment save/cancel
preference preservation. Changed Python sources parse with Python 3.8 grammar;
focused Kotlin API/delimiter inspection and Git whitespace checks passed. A real
Tk probe could not connect to the available display; rendered desktop layout,
keyboard/accessibility behavior, Android compilation/device rendering and live
Telegram interaction remain unverified. No builds, packaging, migrations or
test-suite work ran.

### Android visual polish and shorter controls

Patient-form navigation uses «السريري»، «التحاليل»، «المهام» and «البيانات» with a
stronger selected label; input modes are «عام»/«متقدم». Section order, the shared
draft, labs in both modes and fixed keyboard-safe Save/Cancel remain unchanged.

Patient cards use roughly 2dp more vertical separation between cards/sections,
slightly larger local padding and neutral section labels/dividers. Normal task
counts and diagnosis categories are quieter; clinical badge/overdue colors,
selection feedback, complete expanded clinical fields and task content remain.
Compact mode retains a smaller spacing than normal mode. Demo cards use the same
renderer and updated list spacing.

Ward and demo filters use one wrapping Arabic-numbered count/active-filter summary
and a single clear-filter action, retaining quick presets. Grouping is labelled
separately as a display choice; clearing filters does not change saved ordering or
grouping. Individual filters are changed through the existing display-options
controls, with no duplicated removable-chip row. Duplicate empty-result reset
buttons were removed because the header reset remains available.

Patient actions use shorter labels such as «تحرير»، «الملف الكامل» and
«تثبيت للعرض». Routine retrieve, restore, patient/doctor deletion, demo reset and
draft-discard confirmations name their concrete action. Destructive confirmations
use the shared theme error color; report/publication/conflict review paths retain
their detailed confirmation and existing authorization/revision checks.

Verification: affected Kotlin delimiters, shared component parameters, filter-label
and reset wiring, compact/normal spacing, retained clinical fields/severity mapping,
form section/mode/save controls and routine confirmation call sites were inspected.
Git whitespace checks passed. No Android build, compilation, device rendering,
accessibility interaction or test-suite work ran; large-font wrapping and final
appearance remain unverified on-device.

### Android semantic UI tokens

`ui/theme/UiTokens.kt` centralizes patient-card density and list gaps (`UiPatientCard`),
neutral/selected/focused borders (`UiBorder`), surface elevation (`UiElevation`),
disabled content/container opacity (`UiOpacity`), individual expansion/collapse and
return-highlight timing (`UiMotion`), dialog caps (`UiDialog`) and purpose-specific
field/card/form/navigation/pane thresholds (`UiLayout`). Shared fields, choices,
tabs/buttons, patient cards and ward/demo layouts consume those values. The existing
card spacing, border widths, opacity, animation timing and layout thresholds are
preserved; badge/overdue severity colors remain separately mapped.

`ui/components/AppDialogLayout.kt` supplies `Modifier.appDialogBody` to scrollable
report/review/picker/settings/task dialogs and sort/group/support sheets. It measures
a finite cap against 85% of the actual available parent height, obeys explicit
incoming minimums and retains the caller's width constraints, scrolling and action
callbacks. Standard bodies share a 420dp cap; compact lists, longer reviews and
sheets retain semantic 260/440/640dp caps. Former 400dp picker caps use the standard
420dp role. The full-screen patient editor keeps its keyboard-safe fixed actions;
fold coordinate handling and distinct pane thresholds are unchanged. Immediate
bulk expansion remains independent of the individual-motion tokens.

Verification: delimiter and token/member/import checks covered 28 affected Kotlin
sources. Source inspection covered card content/severity preservation, shared
density, existing motion values/instant bulk behavior, fold thresholds and dialog
constraint handling. Sampled height-cap arithmetic stayed within incoming bounds,
including explicit minima. Git whitespace checks passed. No Gradle, Android builds,
compilation, device/keyboard/fold/accessibility interaction, packaging or test-suite
work ran. Static checks do not establish compilation or rendered behavior.

### Shared Android presentation components and design catalog

Routine confirmations, shift-doctor/doctor pickers and sort/group sheets share
`AppDialogHeader`, `AppDialogConfirmAction` and `AppDialogDismissAction`. Primary
save/apply and quieter cancel actions retain their original validation, stale-state
review and busy guards; sort/group sheets now expose an explicit Cancel action.
Specialized publication/report/conflict review flows retain their own content and
callbacks.

Patient cards and details share `PatientIdentityHeading`/`PatientIdentityText`,
and the editor's saved-name context uses the same `PatientName`. Admission numbers,
optional personal metadata, priority/pin flags and freshness are composed for the
view's purpose. Clinical card sections and task/warning/history detail sections
use `AppClinicalSection`; lab text direction/monospace and date-marker display
formatting are centralized without rewriting patient fields or export formatting.
`UiAction` pairs the common Arabic action labels and icons, including the unchanged
«إرسال تقرير» label across drawer, ward and report preview.

`AppBusyIndicator` shares the inline/page progress style. `AppActionContent` reserves
its inline progress slot whether idle or busy, keeping button text from shifting.
Loading buttons, long-press report actions, shared dialog actions and project setup
reuse it; login/connection/loading screens reuse the same indicator. Callers still
own busy flags and their operation lifecycle.

The [design catalog](design-catalog.md) lists current spacing, density, typography,
shapes, borders, motion, opacity, dialog/layout roles and component/action vocabulary.
`UiStyleCatalog` adds the current profile plus spare «أوسع»، «هادئ» and «أوضح»
profiles as typed design samples. No runtime theme reads those profiles. A possible
advanced local personalisation picker is documented as a proposal only: no picker,
new preference key, schema change, synchronization or clinical mutation was added.
The existing local appearance controls and clinical severity palette remain active.

Verification: affected Kotlin delimiters/imports, shared-component references,
patient/clinical content mappings, original validation/stale callbacks, consistent
report label, reserved loading slots and inactive catalog boundaries were inspected.
Catalog values/links and Git whitespace were checked. No Android build/compilation,
rendered device/large-font/keyboard/accessibility interaction, packaging, live
Telegram or test-suite work ran. Source checks do not establish compilation or
rendered usability.

### Windows EXE packaging documentation

[Windows EXE packaging guide](windows-exe-packaging.md) documents an isolated
CPython 3.8.10/PyInstaller 5.13.2 environment, recommended folder output, resource
collection, an explicitly experimental single-file variant, dummy-data checks,
distribution, private runtime settings/workspaces, updates and diagnostic output.
It preserves the existing Windows dependency file and README build instructions.
Windows 7 compatibility is explicitly unverified; vendor requirements and the
project target are distinguished. Local links, source paths, command structure
and Git whitespace were checked. No packaging commands, EXE/installer creation,
Android builds or test-suite tasks were run for this documentation change.

### Workstation APK export and app build versions

The owner's 20 October early `java.io.IOException: Permission denied` occurred
after version reservation. Read-only host checks found the automatically selected
shared cache writable at its root but its `daemon/8.7` directory owned by the other
account with mode `0700`; the reporting account's personal cache has executable
Gradle 8.7 already installed. The launcher now checks access to existing shared
daemon directories and falls back to the personal cache when inaccessible, while
preserving custom user-home overrides. Shell syntax and six disposable selection
checks passed. No real cache permissions or reserved version metadata were
changed, and no agent-run Gradle/build ran. The owner's next build reached Kotlin
compilation and stored its configuration cache, confirming progress past that
permission failure.


The owner's export-task configuration-cache error was traced to the custom rename
callback reading a Kotlin script property. APK export now uses a built-in regex
rename rule and a destination resolved during configuration; configuration caching
remains enabled. The POSIX wrapper reserves an increasing app patch/version code
for normal packaging invocations through a Python 3 helper and tracked project
counter. It locks the shared metadata directory and replaces the counter atomically;
Gradle properties carry that command's reserved pair to Android and APK naming.
The original major/minor/patch/date format and debug suffix are retained; the date
is regenerated at reservation from the build-start date in Asia/Damascus, using
the host timezone database rather than its local timezone or a fixed offset.
Same-day rebuilds still advance patch/code. No toolchain or
database/interchange version changes are involved. Failed attempts consume a code;
help/dry runs skip reservation. IDE/direct Gradle/Windows use the recorded version
without a new reservation. Sharing the latest counter between PCs remains explicit.

Source/shell and disposable temporary-counter probes passed task-selection/override
cases, concurrent reservations and malformed-counter rejection. The actual project
counter was left at its seed during verification. No Gradle/build or packaging ran;
configuration-cache storage and the produced APK still require an owner-run build.
See [build fixes and reusable preferences](android-shared-workstation-build-fixes.md).

### Android review corrections

The supplied Android review was checked finding by finding against current source;
[the disposition record](reviews/android-ui-reliability-review.md) distinguishes
confirmed defects, implemented presentation changes, existing behavior, optional
redesign proposals and excluded work.

Login no longer exposes registry seeding. New-project creation retains its seeder
with pre-upload/pre-pin empty-pin checks; Telegram still provides no atomic pin
transaction. Database key creation refuses existing database/WAL/SHM/journal files
without a stored key. After the unchanged splash, StorageAccessGate resolves
encrypted key/project/session storage on IO and opens an existing live database
before repository-backed ViewModels. Failure gives a scrollable Arabic retry and
preservation screen; no automatic deletion, key replacement, restore/refetch over
unsent records or claim to recover a lost key. Demo does not open the clinical
database through this check. MainActivity sets FLAG_SECURE before content;
external PDF viewers and deliberate exports are outside that window's control.

Private PDF previews are retained for the existing exact-file/hash-checked send,
then asynchronously deleted when replaced, invalidated or their ViewModel ends.
Failed/cancelled previews clean temporary files. Startup cleanup completes before
normal content; sign-out also clears private preview/share caches. Cleanup is best
effort and leaves explicit exported files alone. Sharing file work runs on IO and
propagates cancellation. Release now reads ignored local/CI signing inputs with no
debug fallback; the owner must still provide production credentials. See
[release signing](android-shared-workstation-build-fixes.md#production-release-signing).
No database/interchange/toolchain/app versions changed.

Grid filtering/pin ordering uses remembered ID sets/groups; focused IME observation
uses snapshotFlow outside composition. Arabic count resources cover card badges,
pending/overdue tasks and task-board totals, independent of system locale. Real
field validation errors reach semantics. Card names gain space from a 32dp ordinal
while actions retain 48dp targets; priority has a text label. Shared Arabic line
spacing, normal card spacing, diagnosis outline, neutral dark foundations,
distinct Material color roles, quieter metrics and error notices are updated in the
[design catalog](design-catalog.md). The owner also chose bundled Arabic typography:
Tajawal Regular/Bold now live once in `res/font`, preserving their original bytes,
and supply shared Compose text styles through asynchronous font loading. PDF
export loads the same resources through ResourcesCompat during its existing IO
path. The original SIL license remains bundled under `assets/fonts/OFL.txt`.
Explicit monospace overrides, font-scale controls, text sizes/line heights and
About/credit content are retained; no download/dependency/version change was added.
Font table/weight/Arabic-and-Latin-coverage, byte-identity and resource-reference
checks passed; Android shaping, startup font loading and final wrapping have not
been rendered or verified on a device.

Rail selected icons match the dock; its
indicator padding is shorter. The existing splash/logo/About, report review,
clinical severity and fixed editor controls remain.

Ward tasks have sticky deadline groups, overdue emphasis, optional My tasks and
filter chips, with unchanged shared task rules and patient-file completion. Details
use a growing title, horizontally scrolling section chips and actual matching
history counts. Login search is remembered, unavailable doctors are dimmed/disabled,
the nonce reads LTR and countdown/group/task/detail numbers use Arabic digits.
The owner retained 4–8-digit PINs and selected Add patient for the floating action.
Ward/demo now open the existing patient form; ward hides Add for read-only shifts
and disables it during loading/save/sync. A single inline Add remains in empty
wards, and toolbar Add appears only where the floating Add is absent. Measured
clearance follows the Add button. Report access stays labelled «إرسال تقرير» in
the drawer and continues through publication/delivery review.

The owner also selected Doctors → Destination → Preview → Send. ReportFlowStep
provides a wrapping Arabic progress indicator; fixed Previous/Next controls and
per-stage scroll positions retain the screen's selections. The first stage keeps
the revision-checked roster picker available before a report exists. Destination
chooses General/Supervisor targets and format; Preview retains optional PDF
customization, readiness/publication notes and actual text/PDF openers. Advancing
requires the text or every selected current PDF to have been opened. PDF UI state
maps each destination to its current private preview URI; re-rendering, failure,
option/roster reload and unavailable viewers invalidate readiness. A generation
check rejects previews completed after review invalidation. Opening status stays
in memory and is cleared by recreation, requiring preview again. Backend file
hash/content/revision and delivery checks remain authoritative.

The final stage lists roster/destinations and opens the existing explicit
publication/delivery confirmation. Retry returns to the appropriate review stage;
no stage transition publishes or sends. Busy state blocks step navigation, and
historical reports retain local preview/export with sending disabled. Demo keeps
its deliberate local-only report export. An in-memory preview gallery covers real
card/task-row rendering, notices and empty state in RTL/dark/2× text; it has not
been rendered.

Verification: focused source checks covered the four stage transitions, retained
selections/scroll states, per-destination opened-file requirements, invalidation,
confirmation-only send calls, guarded Add buttons, retained report route and 4–8
PIN limits. Other checks covered production call sites/parameters, storage guards,
publication entry points, cleanup/cancellation ownership, XML plural forms,
resource references, signing inputs, delimiters, contrast arithmetic and Git
whitespace checks. No Gradle, build, compilation, packaging, test-suite/migration
inspection, device/rendered-preview or live Telegram verification ran. Lost-key
recovery, file cleanup, screenshots/Recents, Arabic wrapping, keyboard/fold/TalkBack
and actual production signing remain runtime limitations, not completed checks.

### Android UI string resources

Owner-approved extraction moves remaining active-screen Arabic wording into
`android/app/src/main/res/values/strings_ui.xml`: 1,291 deduplicated resources for
labels, notices, validation, confirmation, accessibility descriptions and UI state
messages. Existing six-form Arabic plurals remain in `strings.xml`. Numbered
placeholders retain interpolation order and callers retain their number formatting.

`UiStrings` resolves resources for composables, captured callbacks and injected UI
state producers; `rememberUiStrings` observes configuration changes. Composable
default arguments use `stringResource`. UI-specific enums store resource IDs;
13 exhaustive `uiLabel` bridges resolve model/PDF labels for display without
changing portable enum names, model definitions or export wording.

About/credits, demo clinical fixtures, inactive style-catalog samples, shared
clinical/export/protocol values and PDF filename components remain unchanged.
This is wording extraction; patient mutations, report review and authorization
continue through their existing handlers.

Source checks verified all 1,291 values against the original literal parts,
including whitespace and escapes; 1,457 direct calls match numbered placeholder
arity. Resource references, resolver scopes, Kotlin delimiter nesting and Git
whitespace checks passed. No Gradle, compilation, packaging, test-suite/migration
inspection, rendered preview or device checks ran for this extraction. Compilation,
Android resource processing and rendered Arabic wrapping remain unverified.

## Verification and limits

- Owner-supplied 22 October debug build `1.1.17.20261022` (code 18) failed
  while reading the KSP incremental symbol lookup cache with
  `NegativeArraySizeException`. Moved only the reporting account's generated
  debug KSP cache to a preserved sibling backup; confirmed the original path
  is absent. No Gradle, compilation or packaging ran during recovery. Cache
  regeneration and successful build remain unverified. See
  [cache recovery details](troubleshooting.md#ksp-lookup-cache-cannot-be-read).

- Owner-supplied 22 October debug build `1.1.16.20261022` (code 17)
  progressed past KSP and failed during Kotlin compilation on the workflow-strip
  label. Resource extraction had changed `WardWorkflowAction.label` into a
  function; corrected its remaining property-style use to `label(uiText)`.
  Workflow-action references and resource/source checks passed. No agent-run
  Gradle or compilation followed; successful compilation and packaging remain
  unverified.

- Owner-supplied 22 October debug build `1.1.15.20261022` (code 16) failed
  during KSP incremental-cache persistence: modular-JDK access rules blocked
  reflection on `java.io.FilterOutputStream.out`. Added the specific `java.io`
  opening to `kotlin.daemon.jvmargs`; versions, incremental settings and caches
  remain intact. Configuration and Git whitespace were checked without Gradle,
  compilation or packaging. The subsequent owner-run code 17 build progressed
  past KSP to Kotlin compilation. See
  [diagnosis and configuration](troubleshooting.md#ksp-cannot-access-filteroutputstreamout).

- Owner-supplied 22 October debug build `1.1.14.20261022` (code 15) failed
  during KSP because `StorageAccessEntryPoint` imported `dagger.EntryPoint`
  instead of Hilt's `dagger.hilt.EntryPoint`. Corrected the annotation import;
  the interface, singleton installation and storage-preservation behavior remain.
  Production entry-point imports and Git whitespace were checked. No agent-run
  Gradle or compilation followed; the subsequent owner-run code 17 build
  progressed past KSP. APK generation remains unverified.

- Owner-supplied 20 October debug build `1.1.4.20261020` (code 5) reached Kotlin
  compilation, then failed on the ward-filter task button's missing
  `AppOutlinedButton` import. Added the existing shared component import; the
  accompanying composable-context diagnostic is consistent with that unresolved
  call. The run stored its configuration cache and reported the SQLCipher strip
  warning. The corrected import/call signature and Git whitespace were checked;
  compilation after this fix and APK generation remain unverified. No agent-run
  Gradle, compilation, packaging or test-suite work was performed.

- Latest owner-supplied Android debug build on 2026-10-19 succeeded in 2m 51s,
  with 43 tasks (13 executed, 30 up-to-date), after correcting the unsupported
  doctor-conflict-dialog argument. Its two warnings concerned unused arguments in
  disabled partial-merge entry points, now removed by the consolidation above.
  That later consolidation has not been built; the successful build applies to
  its earlier snapshot. Device and live synchronization remain unverified.
- Python 3.14.4 Linux compatibility probe on 2026-10-19 passed in an isolated
  `/tmp/shiftreport-python314-check` venv with CustomTkinter 6.0.0, Pillow 13.0.0,
  ReportLab 5.0.1, cryptography 50.0.2, Requests 2.34.2, arabic-reshaper 3.0.1 and
  python-bidi 0.6.11. All nine desktop modules imported; Arabic patient/task bundle
  round trips, AES-GCM wrong-key rejection, SQLite reopening/receipt retention,
  Linux local-passphrase rejection, actual main-window/patient-editor construction,
  logical Arabic choices and Tk callbacks passed. A dummy Arabic PDF and editor
  screenshot were visually inspected. No real `.env` or clinical data was loaded;
  Telegram requests were blocked in the probe. The environment's exact dependency
  snapshot is `/tmp/shiftreport-python314-requirements.txt`; it is temporary.
  At that snapshot, Linux setup rejected the interpreter and binary resolution
  failed on the old Pillow pin. The subsequent owner-authorized Linux change
  removed its pins and upper-version gate; actual setup and Arabic editing probes
  now pass as documented above. System Python and Windows requirements are intact.
  These checks do not establish live Telegram login/publication/delivery, complete
  clinical workflows, or a supported production Python 3.14 installation.
- Historical authorized Android build on 2026-10-07: success in 2m 55s, 43 tasks
  (21 executed, 22 up-to-date); APK metadata confirmed debug version/code above.
- Earlier owner-supplied success on 2026-10-18: 2m 15s, 43 tasks (10 executed,
  33 up-to-date). The newer build evidence and subsequent source-only cleanup
  are recorded above.
- Offline checks covered Python parsing, Unicode/protected-join crypto, CSV/bundle
  metadata, baseline/token-replacement and merge/conflict choices. Workbook ZIP/XML,
  VBA source round trips, clinical-cell preservation, reference/hash/credential
  scans and Git whitespace checks passed for the earlier package snapshot.
- Historical Excel/COM/VBA execution and current Windows 7/DPAPI/Tk execution,
  live Telegram identity/download/publication/
  recovery/report sends, Android compilation of later edits, device/provider/fold/
  keyboard/accessibility behavior and rendered PDF/Arabic extraction remain
  unverified. ADB previously listed no connected devices. No release build was run.
- Documentation cleanup validated local Markdown targets/headings, preserved README
  build instructions and deferred roadmap content, and confirmed private docs remain
  ignored. The updated Windows package passed manifest/CRC/link checks using the
  existing workbook bytes; no workbook rebuild or macro execution was performed.
- Release uses protected local/CI signing inputs with no debug-key fallback.
  Supplying the production key remains a [roadmap](roadmap.md) item; no signed
  release was produced or verified. Voice/templates remain deferred. Source
  completion does not imply runtime verification.
